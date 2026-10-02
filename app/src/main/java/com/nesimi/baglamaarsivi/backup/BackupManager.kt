package com.nesimi.baglamaarsivi.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.nesimi.baglamaarsivi.data.AppDatabase
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.VideoStore
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupProgress(val label: String, val done: Long, val total: Long) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

data class RestoreResult(val turkuCount: Int, val videoCount: Int, val documentCount: Int, val fileCount: Int)

/**
 * TAM YEDEK: veritabanı + tüm videolar + belgeler + küçük resimler tek bir .zip dosyasına.
 *
 * Geri yükleme şu kaynakları kabul eder:
 *  1) Bu uygulamanın aldığı yedek (.zip)
 *  2) Eski "Bağlama Arşivim" uygulamasından çıkarılmış veri (.zip veya .tar) –
 *     içinde databases/baglama_arsivim.db ve files/videos, files/documents klasörleri olan her arşiv.
 */
object BackupManager {
    private val MEDIA_DIRS = listOf(FileManager.DIR_VIDEOS, FileManager.DIR_DOCS, FileManager.DIR_THUMBS)
    private val KNOWN_DB_NAMES = listOf("baglama_arsivi.db", "baglama_arsivim.db")
    private const val STAGING = "_geri_yukleme"

    // ------------------------------------------------------------------ YEDEKLE
    fun export(context: Context, outUri: Uri, onProgress: (BackupProgress) -> Unit): Int {
        val db = AppDatabase.get(context)
        try {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        } catch (_: Exception) {
        }
        val dbFile = AppDatabase.databaseFile(context)
        val mediaFiles = MEDIA_DIRS.flatMap { d ->
            File(context.filesDir, d).walkTopDown().filter { it.isFile }.map { d to it }.toList()
        }
        // Galerideki (telefonun Filmler klasöründeki) videolar da yedeğe girer
        data class GalleryVideo(val id: Long, val path: String, val size: Long, val entry: String)
        val gallery = mutableListOf<GalleryVideo>()
        try {
            db.openHelper.readableDatabase.query("SELECT id, localPath, fileSize FROM videolar WHERE localPath LIKE 'content://%' AND isDeleted = 0").use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val path = c.getString(1)
                    if (!VideoStore.exists(context, path)) continue
                    val name = VideoStore.displayName(context, path).replace('/', '_')
                    gallery += GalleryVideo(id, path, c.getLong(2), "g${id}_$name")
                }
            }
        } catch (_: Exception) {
        }
        val total = dbFile.length() + mediaFiles.sumOf { it.second.length() } + gallery.sumOf { it.size }
        var done = 0L
        var count = 0

        val out: OutputStream = context.contentResolver.openOutputStream(outUri, "wt")
            ?: context.contentResolver.openOutputStream(outUri)
            ?: throw IllegalStateException("Yedek dosyası oluşturulamadı")

        ZipOutputStream(out.buffered(256 * 1024)).use { zip ->
            val manifest = JSONObject().apply {
                put("app", "Bağlama Arşivi")
                put("format", 1)
                put("dbName", AppDatabase.DB_NAME)
                put("createdAt", System.currentTimeMillis())
                put("fileCount", mediaFiles.size + gallery.size)
                put("gallery", JSONObject().apply { gallery.forEach { g -> put(g.id.toString(), g.entry) } })
                put("totalBytes", total)
            }
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray())
            zip.closeEntry()

            onProgress(BackupProgress("Veritabanı", done, total))
            zip.putNextEntry(ZipEntry("databases/${AppDatabase.DB_NAME}"))
            dbFile.inputStream().use { done += copy(it, zip) { } }
            zip.closeEntry()

            zip.setLevel(Deflater.NO_COMPRESSION) // videolar zaten sıkıştırılmış; hızlı olsun
            for ((dir, f) in mediaFiles) {
                val rel = f.relativeTo(File(context.filesDir, dir)).path.replace('\\', '/')
                zip.putNextEntry(ZipEntry("files/$dir/$rel").apply { time = f.lastModified() })
                val start = done
                f.inputStream().use { input ->
                    copy(input, zip) { n ->
                        done = start + n
                        onProgress(BackupProgress(f.name, done, total))
                    }
                }
                zip.closeEntry()
                count++
            }
            for (g in gallery) {
                val input = VideoStore.openInput(context, g.path) ?: continue
                zip.putNextEntry(ZipEntry("files/${FileManager.DIR_VIDEOS}/${g.entry}"))
                val start = done
                input.use { copy(it, zip) { n -> done = start + n; onProgress(BackupProgress(g.entry.substringAfter('_'), done, total)) } }
                zip.closeEntry()
                count++
            }
        }
        onProgress(BackupProgress("Tamamlandı", total, total))
        return count
    }

    // ------------------------------------------------------------------ GERİ YÜKLE
    fun restore(context: Context, inUri: Uri, onProgress: (BackupProgress) -> Unit): RestoreResult {
        val staging = File(context.filesDir, STAGING)
        staging.deleteRecursively()
        staging.mkdirs()
        val totalSize = try {
            context.contentResolver.openAssetFileDescriptor(inUri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) {
            -1L
        }
        var fileCount = 0
        try {
            val raw = context.contentResolver.openInputStream(inUri) ?: throw IllegalStateException("Dosya açılamadı")
            BufferedInputStream(raw, 256 * 1024).use { input ->
                val counting = CountingInputStream(input)
                val header = ByteArray(512)
                counting.mark(1024)
                val read = readFully(counting, header)
                counting.reset()
                val isZip = read >= 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
                val isTar = read >= 262 && String(header, 257, 5, Charsets.US_ASCII) == "ustar"
                val sink: (String, InputStream) -> Unit = { path, data ->
                    val target = mapEntry(path, staging)
                    if (target != null) {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { o ->
                            copy(data, o) { onProgress(BackupProgress(target.name, counting.count, totalSize)) }
                        }
                        fileCount++
                    }
                }
                when {
                    isZip -> ZipInputStream(counting).use { zip ->
                        while (true) {
                            val e = zip.nextEntry ?: break
                            if (!e.isDirectory) sink(e.name, zip)
                            zip.closeEntry()
                        }
                    }
                    isTar -> TarReader.read(counting, sink)
                    else -> throw IllegalArgumentException("Bu dosya tanınmadı. Bağlama Arşivi yedeği (.zip) seçin.")
                }
            }

            val stagedDb = File(staging, "databases/${AppDatabase.DB_NAME}")
            if (!stagedDb.exists()) throw IllegalArgumentException("Seçilen dosyada arşiv veritabanı bulunamadı.")
            if (!isSqlite(stagedDb)) throw IllegalArgumentException("Veritabanı dosyası bozuk görünüyor.")

            // WAL'ı birleştir, sürümü ve sayıları kontrol et
            var turkus = 0; var videos = 0; var docs = 0
            val manifest = try { File(staging, "manifest.json").takeIf { it.exists() }?.readText()?.let { JSONObject(it) } } catch (_: Exception) { null }
            val sdb = SQLiteDatabase.openDatabase(stagedDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                if (sdb.version > 4) throw IllegalArgumentException("Bu yedek uygulamanın daha yeni bir sürümüyle alınmış. Önce uygulamayı güncelleyin.")
                // Galeri videoları: bu telefonda hâlâ duruyorsa yedekteki kopyayı at, yoksa yedekteki kopyayı kullan
                manifest?.optJSONObject("gallery")?.let { g ->
                    val keys = g.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        val entry = g.optString(id)
                        val staged = File(staging, "files/${FileManager.DIR_VIDEOS}/$entry")
                        val path = sdb.rawQuery("SELECT localPath FROM videolar WHERE id = ?", arrayOf(id)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        if (path != null && VideoStore.exists(context, path)) {
                            staged.delete()
                        } else if (staged.exists()) {
                            val finalPath = File(File(context.filesDir, FileManager.DIR_VIDEOS), entry).absolutePath
                            sdb.execSQL("UPDATE videolar SET localPath = ? WHERE id = ?", arrayOf<Any>(finalPath, id.toLong()))
                        }
                    }
                }
                turkus = count(sdb, "turkuler")
                videos = count(sdb, "videolar")
                docs = count(sdb, "belgeler")
                try { sdb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() } } catch (_: Exception) {}
            } finally {
                sdb.close()
            }

            // --- Buradan sonrası mevcut veriyi değiştirir ---
            AppDatabase.closeInstance()
            val liveDb = AppDatabase.databaseFile(context)
            liveDb.parentFile?.mkdirs()
            for (suffix in listOf("", "-wal", "-shm", "-journal")) File(liveDb.path + suffix).delete()
            for (suffix in listOf("", "-wal", "-shm")) {
                val s = File(stagedDb.path + suffix)
                if (s.exists()) moveFile(s, File(liveDb.path + suffix))
            }
            for (d in MEDIA_DIRS) {
                val src = File(staging, "files/$d")
                if (src.exists()) {
                    val dst = File(context.filesDir, d)
                    // var olan dosyalarla birleştir (aynı ad varsa yenisi yazılır)
                    src.walkTopDown().filter { it.isFile }.forEach { f ->
                        moveFile(f, File(dst, f.relativeTo(src).path))
                    }
                }
            }
            return RestoreResult(turkus, videos, docs, fileCount)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Arşiv içindeki yolu staging klasöründeki hedefe çevirir; ilgisiz dosyalar için null. */
    private fun mapEntry(rawPath: String, staging: File): File? {
        var p = rawPath.replace('\\', '/').trimStart('/')
        while (p.startsWith("./")) p = p.substring(2)
        if (p.split('/').any { it == ".." }) return null
        if (p == "manifest.json") return File(staging, "manifest.json")
        // "data/data/paket/databases/x.db" gibi uzun yolları da kabul et
        val dbIdx = if (p.startsWith("databases/")) 0 else p.indexOf("/databases/").let { if (it >= 0) it + 1 else -1 }
        val fIdx = if (p.startsWith("files/")) 0 else p.indexOf("/files/").let { if (it >= 0) it + 1 else -1 }
        if (dbIdx >= 0 && (fIdx < 0 || dbIdx <= fIdx)) {
            val name = p.substring(dbIdx + "databases/".length)
            if (name.contains('/')) return null
            for (known in KNOWN_DB_NAMES) {
                for (suffix in listOf("", "-wal", "-shm")) {
                    if (name == known + suffix) return File(staging, "databases/${AppDatabase.DB_NAME}$suffix")
                }
            }
            return null
        }
        if (fIdx >= 0) {
            val rest = p.substring(fIdx + "files/".length)
            val top = rest.substringBefore('/')
            if (top in MEDIA_DIRS && rest.contains('/')) return File(staging, "files/$rest")
        }
        return null
    }

    private fun count(db: SQLiteDatabase, table: String): Int = try {
        db.rawQuery("SELECT COUNT(*) FROM `$table` WHERE isDeleted = 0", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    } catch (_: Exception) {
        0
    }

    private fun isSqlite(f: File): Boolean = try {
        f.inputStream().use { val b = ByteArray(16); readFully(it, b) == 16 && String(b, 0, 15, Charsets.US_ASCII) == "SQLite format 3" }
    } catch (_: Exception) {
        false
    }

    private fun moveFile(src: File, dst: File) {
        dst.parentFile?.mkdirs()
        if (dst.exists()) dst.delete()
        if (!src.renameTo(dst)) {
            src.inputStream().use { i -> FileOutputStream(dst).use { o -> i.copyTo(o, 256 * 1024) } }
            src.delete()
        }
    }

    internal fun readFully(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r <= 0) break
            off += r
        }
        return off
    }

    /** Kopyalar; her ~1MB'de ilerleme bildirir. Kopyalanan bayt sayısını döner. */
    private fun copy(input: InputStream, out: OutputStream, onBytes: (Long) -> Unit): Long {
        val buf = ByteArray(128 * 1024)
        var total = 0L
        var lastReport = 0L
        while (true) {
            val r = input.read(buf)
            if (r == -1) break
            out.write(buf, 0, r)
            total += r
            if (total - lastReport > 1_000_000) {
                lastReport = total
                onBytes(total)
            }
        }
        onBytes(total)
        return total
    }
}

private class CountingInputStream(private val inner: BufferedInputStream) : InputStream() {
    var count = 0L
        private set
    private var markCount = 0L
    override fun read(): Int = inner.read().also { if (it >= 0) count++ }
    override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) count += it }
    override fun skip(n: Long): Long = inner.skip(n).also { count += it }
    override fun available(): Int = inner.available()
    override fun markSupported(): Boolean = true
    override fun mark(readlimit: Int) { inner.mark(readlimit); markCount = count }
    override fun reset() { inner.reset(); count = markCount }
    override fun close() = inner.close()
}

/** Basit tar okuyucu (ustar + GNU uzun ad + pax yol başlıkları). Android "tar" çıktısını okur. */
private object TarReader {
    fun read(input: InputStream, sink: (String, InputStream) -> Unit) {
        val header = ByteArray(512)
        var longName: String? = null
        var paxPath: String? = null
        while (true) {
            val r = BackupManager.readFully(input, header)
            if (r < 512) return
            if (header.all { it.toInt() == 0 }) return
            var name = cstr(header, 0, 100)
            val prefix = if (String(header, 257, 5, Charsets.US_ASCII) == "ustar") cstr(header, 345, 155) else ""
            if (prefix.isNotEmpty()) name = "$prefix/$name"
            val size = parseSize(header)
            val type = header[156].toInt().toChar()
            val padded = ((size + 511) / 512) * 512
            when (type) {
                'L' -> {
                    val b = ByteArray(size.toInt())
                    BackupManager.readFully(input, b)
                    skipFully(input, padded - size)
                    longName = String(b, Charsets.UTF_8).trimEnd('\u0000')
                }
                'x' -> {
                    val b = ByteArray(size.toInt())
                    BackupManager.readFully(input, b)
                    skipFully(input, padded - size)
                    paxPath = parsePaxPath(String(b, Charsets.UTF_8))
                }
                'g' -> skipFully(input, padded)
                '0', '\u0000', '7' -> {
                    val path = paxPath ?: longName ?: name
                    longName = null; paxPath = null
                    val limited = LimitedInputStream(input, size)
                    sink(path, limited)
                    skipFully(limited, Long.MAX_VALUE)
                    skipFully(input, padded - size)
                }
                else -> {
                    longName = null; paxPath = null
                    skipFully(input, padded)
                }
            }
        }
    }

    private fun cstr(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end].toInt() != 0) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun parseSize(h: ByteArray): Long {
        if (h[124].toInt() and 0x80 != 0) { // base-256 (8GB+)
            var v = 0L
            for (i in 125 until 136) v = (v shl 8) or (h[i].toLong() and 0xff)
            return v
        }
        val s = cstr(h, 124, 12).trim()
        return if (s.isEmpty()) 0 else s.toLong(8)
    }

    private fun parsePaxPath(s: String): String? {
        var i = 0
        while (i < s.length) {
            val sp = s.indexOf(' ', i)
            if (sp < 0) break
            val len = s.substring(i, sp).toIntOrNull() ?: break
            val rec = s.substring(sp + 1, minOf(s.length, i + len)).trimEnd('\n')
            if (rec.startsWith("path=")) return rec.substring(5)
            i += len
        }
        return null
    }

    private fun skipFully(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(64 * 1024)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r <= 0) return
            left -= r
        }
    }
}

private class LimitedInputStream(private val inner: InputStream, private var left: Long) : InputStream() {
    override fun read(): Int {
        if (left <= 0) return -1
        val r = inner.read()
        if (r >= 0) left--
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        val r = inner.read(b, off, minOf(len.toLong(), left).toInt())
        if (r > 0) left -= r
        return r
    }

    override fun close() {}
}
