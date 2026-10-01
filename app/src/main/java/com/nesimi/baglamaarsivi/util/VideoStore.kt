package com.nesimi.baglamaarsivi.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Videoların telefonda nerede durduğunu yöneten katman.
 *
 * Android 10+ : Videolar telefonun ortak "Filmler/Bağlama Arşivi/<Türkü>" klasörüne kaydedilir
 *               (galeride görünür, uygulama silinse bile kalır). Veritabanında "content://..." adresi tutulur.
 * Android 7-9 : Eskisi gibi uygulamanın kendi klasörüne kaydedilir.
 *
 * Eski kayıtlarda (dosya yolu "/data/...") olan videolar da sorunsuz çalışır; istenirse
 * [moveToGallery] ile galeriye taşınabilir.
 */
object VideoStore {
    private const val TAG = "VideoStore"
    const val ROOT_FOLDER = "Bağlama Arşivi"

    val galleryEnabled: Boolean get() = Build.VERSION.SDK_INT >= 29

    /** Arttıkça kartlar "dosya hâlâ duruyor mu" kontrolünü yeniler (uygulama öne gelince vb.) */
    val checkTick = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun isContent(path: String?) = path != null && path.startsWith("content://")

    /** Oynatma / okuma için Uri; dosya yoksa null. */
    fun uri(context: Context, path: String?): Uri? {
        if (path.isNullOrBlank()) return null
        if (isContent(path)) return Uri.parse(path).takeIf { exists(context, path) }
        return FileManager.resolve(context, path)?.let { Uri.fromFile(it) }
    }

    fun exists(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        if (!isContent(path)) return FileManager.resolve(context, path) != null
        return try {
            context.contentResolver.query(Uri.parse(path), arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } == true
        } catch (_: Exception) {
            false
        }
    }

    fun openInput(context: Context, path: String?): InputStream? {
        if (path.isNullOrBlank()) return null
        return try {
            if (isContent(path)) context.contentResolver.openInputStream(Uri.parse(path))
            else FileManager.resolve(context, path)?.inputStream()
        } catch (_: Exception) {
            null
        }
    }

    fun displayName(context: Context, path: String): String {
        if (!isContent(path)) return File(path).name
        return try {
            context.contentResolver.query(Uri.parse(path), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "video.mp4"
        } catch (_: Exception) {
            "video.mp4"
        }
    }

    /**
     * Video dosyasını telefondan siler. Başka uygulamaya ait dosyada (ör. WhatsApp) izin yoksa false döner;
     * o durumda arayan taraf sistemin "Silinsin mi?" onayını istemelidir.
     */
    fun delete(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        if (!isContent(path)) {
            val f = File(path)
            if (f.isAbsolute && f.path.startsWith("/storage/")) {
                val ok = f.delete()
                if (ok) scanQuietly(context, f.path)
                return ok
            }
            return FileManager.delete(path)
        }
        val uri = Uri.parse(path)
        if (!exists(context, path)) return true
        try {
            if (context.contentResolver.delete(uri, null, null) > 0) return true
        } catch (e: Exception) {
            Log.w(TAG, "silinemedi $path", e)
        }
        if (hasAllFilesAccess()) {
            val dp = dataPath(context, uri)
            if (dp != null && File(dp).delete()) {
                scanQuietly(context, dp)
                return true
            }
        }
        return !exists(context, path)
    }

    /** "Tüm dosyalara erişim" izni (dosyaları gerçekten taşıyıp silebilmek için) */
    fun hasAllFilesAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> android.os.Environment.isExternalStorageManager()
        else -> false
    }

    /** Galeri kaydının telefondaki gerçek yolu (ör. /storage/emulated/0/Android/media/com.whatsapp/...). */
    @Suppress("DEPRECATION")
    fun dataPath(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    fun archiveDir(turkuName: String?): File =
        File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "$ROOT_FOLDER/${folderName(turkuName)}")

    /**
     * Orijinal dosyayı (ör. WhatsApp klasöründeki) Bağlama Arşivi klasörüne TAŞIR: kopya oluşmaz, galeride tek görünür.
     * "Tüm dosyalara erişim" izni gerekir. Başarılıysa yeni galeri adresini döner.
     */
    suspend fun moveIntoArchive(context: Context, originalUri: Uri, fileName: String, turkuName: String?): Saved? {
        if (!hasAllFilesAccess()) return null
        val srcPath = dataPath(context, originalUri) ?: return null
        val src = File(srcPath)
        if (!src.exists()) return null
        val dir = archiveDir(turkuName).apply { mkdirs() }
        val dest = uniqueFile(dir, fileName)
        val size = src.length()
        val moved = src.renameTo(dest) || run {
            try {
                src.inputStream().use { i -> FileOutputStream(dest).use { o -> i.copyTo(o, 256 * 1024) } }
                if (dest.length() == size) { src.delete(); true } else { dest.delete(); false }
            } catch (_: Exception) {
                dest.delete(); false
            }
        }
        if (!moved) return null
        scanQuietly(context, src.path) // eski konumu galeriden düşür
        val newUri = scan(context, dest.path) ?: return Saved(dest.path, size, "")
        return Saved(newUri.toString(), size, "")
    }

    private suspend fun scan(context: Context, path: String): Uri? =
        kotlinx.coroutines.withTimeoutOrNull(8000) {
            kotlinx.coroutines.suspendCancellableCoroutine<Uri?> { cont ->
                android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, uri ->
                    if (cont.isActive) cont.resumeWith(Result.success(uri))
                }
            }
        }

    fun scanQuietly(context: Context, path: String) {
        try { android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null, null) } catch (_: Exception) {}
    }

    /** Bağlama Arşivi klasöründe mi? */
    fun isInArchiveFolder(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        if (!isContent(path)) return path.contains("/$ROOT_FOLDER/")
        return try {
            context.contentResolver.query(Uri.parse(path), arrayOf(MediaStore.Video.Media.RELATIVE_PATH), null, null, null)?.use { c ->
                c.moveToFirst() && (c.getString(0) ?: "").startsWith("Movies/$ROOT_FOLDER")
            } == true
        } catch (_: Exception) {
            false
        }
    }

    fun folderName(turkuName: String?): String {
        val clean = (turkuName ?: "").replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(60)
        return if (clean.isBlank()) "Genel" else clean
    }

    data class Saved(val path: String, val size: Long, val hash: String)

    /** Uri'den gelen videoyu galeri klasörüne (veya eski cihazlarda uygulama klasörüne) yazar. */
    fun save(context: Context, source: Uri, fileName: String, turkuName: String?): Saved {
        val input = context.contentResolver.openInputStream(source) ?: throw IllegalStateException("Video açılamadı")
        return input.use { save(context, it, fileName, turkuName) }
    }

    /** Son çare: galeride GÖRÜNMEYEN, uygulamaya özel gizli kopya. */
    fun savePrivate(context: Context, source: Uri, fileName: String): Saved {
        val input = context.contentResolver.openInputStream(source) ?: throw IllegalStateException("Video açılamadı")
        val digest = MessageDigest.getInstance("SHA-256")
        val dir = File(context.filesDir, FileManager.DIR_VIDEOS).apply { mkdirs() }
        val dest = uniqueFile(dir, fileName)
        var total = 0L
        val buf = ByteArray(128 * 1024)
        try {
            input.use { inp ->
                FileOutputStream(dest).use { out ->
                    while (true) {
                        val r = inp.read(buf); if (r == -1) break
                        out.write(buf, 0, r); digest.update(buf, 0, r); total += r
                    }
                }
            }
        } catch (e: Exception) {
            dest.delete(); throw e
        }
        return Saved(dest.absolutePath, total, hex(digest.digest()))
    }

    fun save(context: Context, input: InputStream, fileName: String, turkuName: String?): Saved {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buf = ByteArray(128 * 1024)
        if (!galleryEnabled) {
            val dir = File(context.filesDir, FileManager.DIR_VIDEOS).apply { mkdirs() }
            val dest = uniqueFile(dir, fileName)
            try {
                FileOutputStream(dest).use { out ->
                    while (true) {
                        val r = input.read(buf); if (r == -1) break
                        out.write(buf, 0, r); digest.update(buf, 0, r); total += r
                    }
                }
            } catch (e: Exception) {
                dest.delete(); throw e
            }
            return Saved(dest.absolutePath, total, hex(digest.digest()))
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeFor(fileName))
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$ROOT_FOLDER/${folderName(turkuName)}")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("Galeriye kayıt oluşturulamadı")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IllegalStateException("Galeriye yazılamadı")
            out.use {
                while (true) {
                    val r = input.read(buf); if (r == -1) break
                    it.write(buf, 0, r); digest.update(buf, 0, r); total += r
                }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
        return Saved(uri.toString(), total, hex(digest.digest()))
    }

    /** Uygulama içindeki eski bir videoyu galeriye taşır; yeni content adresini döner. */
    fun moveToGallery(context: Context, path: String, turkuName: String?): String? {
        if (!galleryEnabled || isContent(path)) return null
        val f = FileManager.resolve(context, path) ?: return null
        val saved = f.inputStream().use { save(context, it, f.name.replace(Regex("_\\d{13}(?=\\.)"), ""), turkuName) }
        f.delete()
        return saved.path
    }

    /** Süre (ms) ve küçük resim (uygulama içinde jpg) üretir. */
    fun meta(context: Context, path: String): Pair<Long, String?> {
        val r = MediaMetadataRetriever()
        var dur = 0L
        var thumbPath: String? = null
        try {
            if (isContent(path)) r.setDataSource(context, Uri.parse(path))
            else r.setDataSource(FileManager.resolve(context, path)?.absolutePath ?: return 0L to null)
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bmp: Bitmap? = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.frameAtTime
            if (bmp != null) {
                val scaled = if (bmp.width > 640) Bitmap.createScaledBitmap(bmp, 640, (bmp.height * 640f / bmp.width).toInt().coerceAtLeast(1), true) else bmp
                val dir = File(context.filesDir, FileManager.DIR_THUMBS).apply { mkdirs() }
                val f = File(dir, "thumb_${System.nanoTime()}.jpg")
                FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                thumbPath = f.absolutePath
            }
        } catch (e: Exception) {
            Log.w(TAG, "meta okunamadı", e)
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        return dur to thumbPath
    }

    /**
     * Paylaşılan/seçilen orijinal videonun (ör. WhatsApp klasöründeki) galeri kaydını bulur.
     * Okuma izni gerekir. Kendi kaydettiğimiz kopyalar hariç tutulur.
     */
    /**
     * Seçilen/paylaşılan videonun telefondaki ASIL dosyasını (galeri kaydını) bulur. Bulunursa uygulama
     * kopya yapmadan doğrudan onu kullanır. Sıra:
     *  1) Zaten galeri adresi (content://media/...)
     *  2) Galeri seçicisi adresi (content://media/picker/.../ID)
     *  3) Dosya seçici adresi (com.android.providers.media.documents -> video:ID)
     *  4) Dosya seçici yol adresi (com.android.externalstorage.documents -> primary:yol)
     *  5) WhatsApp vb. paylaşım: ad + boyut, olmazsa yalnızca boyut ile galeride ara
     */
    fun resolveOriginal(context: Context, source: Uri): Uri? {
        if (!galleryEnabled) return null
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        fun byId(id: Long?): Uri? {
            if (id == null) return null
            val u = android.content.ContentUris.withAppendedId(collection, id)
            return u.takeIf { exists(context, it.toString()) }
        }
        try {
            val auth = source.authority ?: ""
            val segs = source.pathSegments
            if (auth == MediaStore.AUTHORITY) {
                if (segs.contains("picker") || segs.contains("picker_get_content")) byId(segs.lastOrNull()?.toLongOrNull())?.let { return it }
                else if (exists(context, source.toString())) return byId(source.lastPathSegment?.toLongOrNull()) ?: source
            }
            if (android.provider.DocumentsContract.isDocumentUri(context, source)) {
                val docId = android.provider.DocumentsContract.getDocumentId(source)
                if (auth == "com.android.providers.media.documents" && docId.startsWith("video:")) {
                    byId(docId.substringAfter(':').toLongOrNull())?.let { return it }
                }
                if (auth == "com.android.externalstorage.documents" && docId.startsWith("primary:")) {
                    val full = android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + docId.substringAfter(':')
                    byDataPath(context, full)?.let { return it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "adres çözülemedi", e)
        }
        val info = sourceInfo(context, source) ?: return null
        findOriginal(context, info.first, info.second)?.let { return it }
        // Ad farklı olabilir (WhatsApp paylaşırken adı değiştirir): boyutla ara
        val bySize = findOutsideArchiveBySize(context, info.second)
        return bySize.firstOrNull()
    }

    @Suppress("DEPRECATION")
    private fun byDataPath(context: Context, path: String): Uri? = try {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        context.contentResolver.query(collection, arrayOf(MediaStore.Video.Media._ID), "${MediaStore.MediaColumns.DATA} = ?", arrayOf(path), null)?.use { c ->
            if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }
    } catch (_: Exception) {
        null
    }

    /** Uygulamanın kendi kopyası mı? (eski sürümlerde Bağlama Arşivi klasörüne veya uygulama içine kopyalananlar) */
    fun isOwnCopy(context: Context, path: String?): Boolean =
        !path.isNullOrBlank() && (!isContent(path) && !path.startsWith("/storage/") || isInArchiveFolder(context, path))

    fun findOriginal(context: Context, displayName: String, size: Long): Uri? {
        if (!galleryEnabled) return null
        return try {
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.RELATIVE_PATH),
                "${MediaStore.Video.Media.DISPLAY_NAME} = ? AND ${MediaStore.Video.Media.SIZE} = ?",
                arrayOf(displayName, size.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val rel = c.getString(1) ?: ""
                    if (!rel.contains(ROOT_FOLDER)) {
                        return@use android.content.ContentUris.withAppendedId(collection, c.getLong(0))
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "orijinal aranamadı", e)
            null
        }
    }

    fun findOutsideArchiveBySize(context: Context, size: Long): List<Uri> {
        if (!galleryEnabled || size <= 0) return emptyList()
        val out = mutableListOf<Uri>()
        try {
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            context.contentResolver.query(
                collection, arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.RELATIVE_PATH),
                "${MediaStore.Video.Media.SIZE} = ?", arrayOf(size.toString()), null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (!(c.getString(1) ?: "").contains(ROOT_FOLDER)) out += android.content.ContentUris.withAppendedId(collection, c.getLong(0))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun sourceInfo(context: Context, uri: Uri): Pair<String, Long>? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                val n = it.getString(0)
                val s = if (it.isNull(1)) -1L else it.getLong(1)
                if (n != null && s > 0) n to s else null
            } else null
        }
    } catch (_: Exception) {
        null
    }

    /** content://media/.../video/media/123 -> 123 (farklı "volume" adlarına rağmen karşılaştırma için) */
    fun mediaId(path: String?): Long? {
        if (!isContent(path)) return null
        val u = Uri.parse(path)
        if (u.authority != MediaStore.AUTHORITY) return null
        return u.lastPathSegment?.toLongOrNull()
    }

    data class FolderVideo(val uri: Uri, val id: Long, val name: String, val size: Long, val subFolder: String)

    /** Movies/Bağlama Arşivi altındaki tüm videolar (kullanıcının elle koyduklarını görmek için okuma izni gerekir). */
    fun scanArchiveFolder(context: Context): List<FolderVideo> {
        if (!galleryEnabled) return emptyList()
        val out = mutableListOf<FolderVideo>()
        try {
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.RELATIVE_PATH),
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Video.Media.IS_PENDING} = 0",
                arrayOf("Movies/$ROOT_FOLDER/%"),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val rel = (c.getString(3) ?: "").trimEnd('/')
                    val sub = rel.removePrefix("Movies/$ROOT_FOLDER").trim('/').substringBefore('/')
                    out += FolderVideo(android.content.ContentUris.withAppendedId(collection, id), id, c.getString(1) ?: "video.mp4", c.getLong(2), sub)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "klasör taranamadı", e)
        }
        return out
    }

    /** Seçilen video zaten Bağlama Arşivi klasöründe duruyorsa onun galeri adresini döner (yeniden kopyalamamak için). */
    fun findInArchiveFolder(context: Context, source: Uri): Uri? {
        if (!galleryEnabled) return null
        // Doğrudan galeri adresi verildiyse klasörünü kontrol et
        try {
            if (source.authority == MediaStore.AUTHORITY) {
                context.contentResolver.query(source, arrayOf(MediaStore.Video.Media.RELATIVE_PATH), null, null, null)?.use { c ->
                    if (c.moveToFirst() && (c.getString(0) ?: "").startsWith("Movies/$ROOT_FOLDER")) {
                        val id = source.lastPathSegment?.toLongOrNull()
                        if (id != null) return android.content.ContentUris.withAppendedId(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), id)
                    }
                }
            }
        } catch (_: Exception) {
        }
        // Seçici/dosya yöneticisi adresi: ad + boyuta göre ara
        val info = sourceInfo(context, source) ?: return null
        return scanArchiveFolder(context).firstOrNull { it.name == info.first && it.size == info.second }?.uri
    }

    fun hasReadPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_VIDEO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // ---- Kullanıcının "arşivden çıkar ama telefonda kalsın" dediği videolar: klasör taramasında yeniden eklenmesin
    private const val IGNORE_PREFS = "video_klasor"
    fun ignore(context: Context, path: String?) {
        val id = mediaId(path) ?: return
        val p = context.getSharedPreferences(IGNORE_PREFS, Context.MODE_PRIVATE)
        val set = p.getStringSet("yoksay", emptySet())!!.toMutableSet()
        set += id.toString()
        p.edit().putStringSet("yoksay", set).apply()
    }

    fun ignored(context: Context): Set<Long> =
        context.getSharedPreferences(IGNORE_PREFS, Context.MODE_PRIVATE).getStringSet("yoksay", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun unignore(context: Context, id: Long) {
        val p = context.getSharedPreferences(IGNORE_PREFS, Context.MODE_PRIVATE)
        val set = p.getStringSet("yoksay", emptySet())!!.toMutableSet()
        if (set.remove(id.toString())) p.edit().putStringSet("yoksay", set).apply()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        while (f.exists()) {
            f = File(dir, name.substringBeforeLast('.') + "_$i." + name.substringAfterLast('.', "mp4"))
            i++
        }
        return f
    }

    private fun mimeFor(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "3gp" -> "video/3gpp"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        else -> "video/mp4"
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
