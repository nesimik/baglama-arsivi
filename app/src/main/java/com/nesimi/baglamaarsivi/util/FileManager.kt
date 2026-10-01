package com.nesimi.baglamaarsivi.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

data class SavedFileInfo(
    val filePath: String,
    val originalFileName: String,
    val fileSize: Long,
    val fileHash: String,
    val durationMs: Long = 0L,
    val thumbnailPath: String? = null,
    val fileExtension: String = ""
)

data class StorageStats(
    val videoBytes: Long = 0,
    val documentBytes: Long = 0,
    val cacheBytes: Long = 0,
    val totalBytes: Long = 0,
    val freeDeviceBytes: Long = 0
)

object FileManager {
    private const val TAG = "FileManager"
    const val DIR_VIDEOS = "videos"
    const val DIR_DOCS = "documents"
    const val DIR_THUMBS = "thumbnails"

    fun originalName(context: Context, uri: Uri): String {
        var name = "dosya_${System.currentTimeMillis()}"
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) it.getString(idx)?.takeIf { n -> n.isNotBlank() }?.let { n -> name = n }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "isim okunamadı: $uri", e)
        }
        if (name.startsWith("dosya_")) uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.contains('.') }?.let { name = it }
        return name
    }

    fun mimeType(context: Context, uri: Uri): String? = try {
        context.contentResolver.getType(uri)
    } catch (_: Exception) {
        null
    }

    /** Dosya adından/ başlıktan güvenli dosya adı */
    private fun cleanName(raw: String): String {
        val n = Tr.norm(raw).replace(Regex("[^a-z0-9_-]"), "_").replace(Regex("_+"), "_").trim('_')
        return n.take(60).ifBlank { "dosya" }
    }

    fun saveFromUri(context: Context, uri: Uri, subDir: String, suggestedTitle: String?, isVideo: Boolean): SavedFileInfo {
        val original = originalName(context, uri)
        var ext = original.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isBlank() || ext.length > 5) {
            val mime = mimeType(context, uri)
            ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (isVideo) "mp4" else "bin"
        }
        val dir = File(context.filesDir, subDir).apply { mkdirs() }
        val base = cleanName(suggestedTitle?.takeIf { it.isNotBlank() } ?: original.substringBeforeLast('.'))
        val dest = File(dir, "${base}_${System.currentTimeMillis()}.$ext")

        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Dosya açılamadı")
        try {
            input.use { inp ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val r = inp.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                        digest.update(buf, 0, r)
                        total += r
                    }
                    out.flush()
                }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        var dur = 0L
        var thumb: String? = null
        if (isVideo) {
            val meta = videoMeta(context, dest.absolutePath)
            dur = meta.first
            thumb = meta.second
        }
        return SavedFileInfo(dest.absolutePath, original, total, hash, dur, thumb, ext.uppercase(Locale.ROOT))
    }

    fun videoMeta(context: Context, path: String): Pair<Long, String?> {
        val r = MediaMetadataRetriever()
        var dur = 0L
        var thumbPath: String? = null
        try {
            r.setDataSource(path)
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bmp: Bitmap? = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.frameAtTime
            if (bmp != null) {
                val scaled = if (bmp.width > 640) {
                    val h = (bmp.height * 640f / bmp.width).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(bmp, 640, h, true)
                } else bmp
                val dir = File(context.filesDir, DIR_THUMBS).apply { mkdirs() }
                val f = File(dir, "thumb_${System.currentTimeMillis()}.jpg")
                FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                thumbPath = f.absolutePath
            }
        } catch (e: Exception) {
            Log.w(TAG, "video bilgisi okunamadı", e)
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        return dur to thumbPath
    }

    /** Kayıtlı yol yoksa (başka uygulamadan gelmiş olabilir) bizim klasörde aynı adlı dosyayı bulur. */
    fun resolve(context: Context, path: String?): File? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (f.exists()) return f
        val i = path.indexOf("/files/")
        if (i >= 0) {
            val alt = File(context.filesDir, path.substring(i + 7))
            if (alt.exists()) return alt
        }
        return null
    }

    fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSize(it) } ?: 0
    }

    fun stats(context: Context): StorageStats {
        val v = dirSize(File(context.filesDir, DIR_VIDEOS))
        val d = dirSize(File(context.filesDir, DIR_DOCS))
        val c = dirSize(context.cacheDir) + dirSize(File(context.filesDir, DIR_THUMBS))
        return StorageStats(v, d, c, v + d + c, context.filesDir.usableSpace)
    }

    fun delete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val f = File(path)
        return f.exists() && f.delete()
    }

    fun clearCache(context: Context): Long {
        var freed = 0L
        context.cacheDir.listFiles()?.forEach {
            freed += dirSize(it)
            it.deleteRecursively()
        }
        return freed
    }
}
