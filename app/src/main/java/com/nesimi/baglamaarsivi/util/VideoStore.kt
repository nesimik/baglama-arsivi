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

    /** Videoyu siler. Galerideki dosyalar yalnızca açıkça istendiğinde silinir. */
    fun delete(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return if (isContent(path)) {
            try {
                context.contentResolver.delete(Uri.parse(path), null, null) > 0
            } catch (e: Exception) {
                Log.w(TAG, "silinemedi $path", e)
                false
            }
        } else FileManager.delete(path)
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
