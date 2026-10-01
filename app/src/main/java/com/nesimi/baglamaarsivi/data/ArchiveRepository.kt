package com.nesimi.baglamaarsivi.data

import android.content.Context
import android.net.Uri
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.Tr
import com.nesimi.baglamaarsivi.util.VideoOrder
import com.nesimi.baglamaarsivi.util.VideoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ArchiveRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val turkuDao = db.turkuDao()
    private val videoDao = db.videoDao()
    private val docDao = db.documentDao()
    private val markerDao = db.markerDao()
    private val practiceDao = db.practiceDao()

    val activeTurkus: Flow<List<Turku>> = turkuDao.activeTurkus()
    val activeVideos: Flow<List<VideoItem>> = videoDao.activeVideos()
    val activeDocs: Flow<List<DocumentItem>> = docDao.activeDocs()
    val recentlyWatched: Flow<List<VideoItem>> = videoDao.recentlyWatched(12)
    val practiceSessions: Flow<List<PracticeSession>> = practiceDao.all()
    val deletedTurkus: Flow<List<Turku>> = turkuDao.deletedTurkus()
    val deletedVideos: Flow<List<VideoItem>> = videoDao.deletedVideos()
    val deletedDocs: Flow<List<DocumentItem>> = docDao.deletedDocs()

    val turkusWithDetails: Flow<List<TurkuWithDetails>> =
        combine(activeTurkus, activeVideos, activeDocs, practiceSessions) { turkus, videos, docs, sessions ->
            val vByT = videos.groupBy { it.turkuId }
            val dByT = docs.groupBy { it.turkuId }
            val pByT = sessions.groupBy { it.turkuId }
            turkus.map { t ->
                val tv = vByT[t.id].orEmpty()
                TurkuWithDetails(
                    turku = t,
                    videoCount = tv.size,
                    documentCount = dByT[t.id].orEmpty().size,
                    lastVideoDate = tv.maxByOrNull { Tr.lessonDateKey(it.lessonDate) }?.lessonDate
                        ?.takeIf { it.isNotBlank() },
                    practiceSec = pByT[t.id].orEmpty().sumOf { it.durationSec }
                )
            }
        }

    // ---------------- Türkü
    fun turku(id: Long): Flow<Turku?> = turkuDao.turkuById(id)
    suspend fun turkuSync(id: Long) = turkuDao.turkuByIdSync(id)
    suspend fun insertTurku(t: Turku) = turkuDao.insert(t)
    suspend fun updateTurku(t: Turku) = turkuDao.update(t)
    suspend fun setTurkuFavorite(id: Long, fav: Boolean) = turkuDao.setFavorite(id, fav)
    suspend fun setTurkuStatus(id: Long, s: StudyStatus) = turkuDao.setStatus(id, s.name)
    suspend fun setTurkuNote(id: Long, note: String) = turkuDao.setNote(id, note)
    suspend fun softDeleteTurku(id: Long) = turkuDao.softDelete(id)
    suspend fun restoreTurku(id: Long) = turkuDao.restore(id)
    suspend fun reorderTurkus(ordered: List<Turku>) = ordered.forEachIndexed { i, t -> turkuDao.setOrder(t.id, i) }

    /** [deleteGalleryFiles]=false ise galerideki videolar telefonda kalır, sadece arşivden çıkar. */
    suspend fun deleteTurkuForever(id: Long, deleteGalleryFiles: Boolean = false) {
        for (v in videoDao.allVideosForTurkuSync(id)) removeVideoFiles(v, deleteGalleryFiles)
        for (d in docDao.allDocsForTurkuSync(id)) FileManager.delete(d.localPath)
        turkuDao.deleteForever(id)
    }

    // ---------------- Video
    fun videosForTurku(turkuId: Long): Flow<List<VideoItem>> =
        videoDao.videosForTurku(turkuId).map { it.sortedWith(VideoOrder) }

    fun video(id: Long): Flow<VideoItem?> = videoDao.videoById(id)
    suspend fun videoSync(id: Long) = videoDao.videoByIdSync(id)
    suspend fun updateVideo(v: VideoItem) = videoDao.update(v.copy(updatedAt = System.currentTimeMillis()))
    suspend fun setVideoPosition(id: Long, pos: Long) = videoDao.setPosition(id, pos)
    suspend fun setVideoDuration(id: Long, dur: Long) = videoDao.setDuration(id, dur)
    suspend fun setVideoFavorite(id: Long, fav: Boolean) = videoDao.setFavorite(id, fav)
    suspend fun softDeleteVideo(id: Long) = videoDao.softDelete(id)
    suspend fun restoreVideo(id: Long) = videoDao.restore(id)

    suspend fun setVideoOrderTag(id: Long, tag: String) {
        val clean = tag.trim().removePrefix("#").trim()
        val num = clean.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        videoDao.setOrderTag(id, clean, num)
    }

    suspend fun deleteVideoForever(v: VideoItem, deleteGalleryFile: Boolean = false) {
        removeVideoFiles(v, deleteGalleryFile)
        videoDao.deleteForever(v.id)
    }

    private fun removeVideoFiles(v: VideoItem, deleteGalleryFile: Boolean) {
        FileManager.delete(v.thumbnailPath)
        if (!VideoStore.isContent(v.localPath) || deleteGalleryFile) VideoStore.delete(context, v.localPath)
        else VideoStore.ignore(context, v.localPath) // telefonda kalsın ama klasör taramasında geri gelmesin
    }

    data class ImportResult(val id: Long, val duplicateOf: String?, val source: Pair<String, Long>? = null)

    suspend fun importVideo(
        uri: Uri, turkuId: Long, title: String, lessonDate: String, instructor: String,
        description: String, tags: String, orderTag: String, uploadOrder: Int, favorite: Boolean
    ): ImportResult = withContext(Dispatchers.IO) {
        val original = FileManager.originalName(context, uri)
        val source = VideoStore.sourceInfo(context, uri)
        var ext = original.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || ext.length > 4) ext = "mp4"
        val niceTitle = title.ifBlank { original.substringBeforeLast('.') }
        val fileName = niceTitle.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(80).ifBlank { "video" } + "." + ext
        val turkuName = turkuDao.turkuByIdSync(turkuId)?.name
        // Zaten Bağlama Arşivi klasöründe duruyorsa yeniden kopyalama, yerindekine bağla
        val existing = VideoStore.findInArchiveFolder(context, uri)
        val saved = if (existing != null) {
            VideoStore.mediaId(existing.toString())?.let { VideoStore.unignore(context, it) }
            VideoStore.Saved(existing.toString(), source?.second ?: 0L, "")
        } else VideoStore.save(context, uri, fileName, turkuName)
        val linked = existing != null
        val (dur, thumb) = VideoStore.meta(context, saved.path)
        val dup = if (linked) videoDao.allSync().firstOrNull { !it.isDeleted && VideoStore.mediaId(it.localPath) == VideoStore.mediaId(saved.path) }
            else videoDao.findByHash(saved.hash)
        val clean = orderTag.trim().removePrefix("#").trim()
        val id = videoDao.insert(
            VideoItem(
                turkuId = turkuId,
                title = niceTitle,
                localPath = saved.path,
                durationMs = dur,
                lessonDate = lessonDate.ifBlank { Tr.today() },
                instructor = instructor.trim(),
                description = description.trim(),
                tags = tags.trim(),
                thumbnailPath = thumb,
                fileSize = saved.size,
                fileHash = saved.hash,
                isFavorite = favorite,
                orderTag = clean,
                manualOrder = clean.takeWhile { it.isDigit() }.toIntOrNull() ?: 0,
                uploadOrder = uploadOrder
            )
        )
        ImportResult(id, dup?.title, if (linked) null else source)
    }

    /**
     * Movies/Bağlama Arşivi klasörünü tarar: oraya elle konan yeni videoları (alt klasör adı = türkü adı)
     * kopyalamadan arşive ekler. Eklenen video sayısını döner.
     */
    suspend fun syncArchiveFolder(): Int = withContext(Dispatchers.IO) {
        if (!VideoStore.galleryEnabled) return@withContext 0
        val found = VideoStore.scanArchiveFolder(context)
        if (found.isEmpty()) return@withContext 0
        val known = videoDao.allSync().mapNotNull { VideoStore.mediaId(it.localPath) }.toSet()
        val ignored = VideoStore.ignored(context)
        val fresh = found.filter { it.id !in known && it.id !in ignored }
        if (fresh.isEmpty()) return@withContext 0
        val turkus = turkuDao.allSync().toMutableList()
        var added = 0
        for (fv in fresh) {
            val folder = fv.subFolder.ifBlank { "Genel" }
            val match = turkus.firstOrNull { !it.isDeleted && Tr.norm(VideoStore.folderName(it.name)) == Tr.norm(folder) }
                ?: turkus.firstOrNull { it.isDeleted && Tr.norm(VideoStore.folderName(it.name)) == Tr.norm(folder) }?.also { turkuDao.restore(it.id) }
            val turkuId = match?.id ?: turkuDao.insert(Turku(name = folder, manualOrder = turkus.size + added)).also {
                // yeni oluşan türküyü listeye ekle ki aynı klasördeki diğer videolar da buna gitsin
                turkus.add(Turku(id = it, name = folder))
            }
            val (dur, thumb) = VideoStore.meta(context, fv.uri.toString())
            videoDao.insert(
                VideoItem(
                    turkuId = turkuId,
                    title = fv.name.substringBeforeLast('.'),
                    localPath = fv.uri.toString(),
                    durationMs = dur,
                    lessonDate = Tr.today(),
                    thumbnailPath = thumb,
                    fileSize = fv.size
                )
            )
            added++
        }
        added
    }

    /** Dosyası silinmiş bir videoyu yeni seçilen dosyaya bağlar (klasördeyse kopyalamaz). */
    suspend fun relinkVideo(v: VideoItem, uri: Uri) = withContext(Dispatchers.IO) {
        val existing = VideoStore.findInArchiveFolder(context, uri)
        val path: String
        val size: Long
        if (existing != null) {
            path = existing.toString()
            size = VideoStore.sourceInfo(context, uri)?.second ?: v.fileSize
        } else {
            var ext = FileManager.originalName(context, uri).substringAfterLast('.', "mp4").lowercase()
            if (ext.length > 4) ext = "mp4"
            val name = v.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(80).ifBlank { "video" } + "." + ext
            val saved = VideoStore.save(context, uri, name, turkuDao.turkuByIdSync(v.turkuId)?.name)
            path = saved.path
            size = saved.size
        }
        FileManager.delete(v.thumbnailPath)
        val (dur, thumb) = VideoStore.meta(context, path)
        videoDao.update(v.copy(localPath = path, fileSize = size, durationMs = if (dur > 0) dur else v.durationMs, thumbnailPath = thumb, updatedAt = System.currentTimeMillis()))
    }

    /** Uygulama içinde duran (eski) videoları telefonun galeri klasörüne taşır. */
    suspend fun moveInternalVideosToGallery(onProgress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val list = videoDao.allSync().filter { !VideoStore.isContent(it.localPath) && FileManager.resolve(context, it.localPath) != null }
        var n = 0
        list.forEachIndexed { i, v ->
            onProgress(i + 1, list.size)
            try {
                val newPath = VideoStore.moveToGallery(context, v.localPath, turkuDao.turkuByIdSync(v.turkuId)?.name)
                if (newPath != null) { videoDao.update(v.copy(localPath = newPath)); n++ }
            } catch (_: Exception) {
            }
        }
        n
    }

    suspend fun internalVideoCount(): Int = withContext(Dispatchers.IO) {
        videoDao.allSync().count { !VideoStore.isContent(it.localPath) && FileManager.resolve(context, it.localPath) != null }
    }

    // ---------------- Belge
    fun docsForTurku(turkuId: Long): Flow<List<DocumentItem>> = docDao.docsForTurku(turkuId)
    suspend fun updateDoc(d: DocumentItem) = docDao.update(d.copy(updatedAt = System.currentTimeMillis()))
    suspend fun setDocFavorite(id: Long, fav: Boolean) = docDao.setFavorite(id, fav)
    suspend fun softDeleteDoc(id: Long) = docDao.softDelete(id)
    suspend fun restoreDoc(id: Long) = docDao.restore(id)
    suspend fun deleteDocForever(d: DocumentItem) {
        FileManager.delete(d.localPath)
        docDao.deleteForever(d.id)
    }

    suspend fun importDocument(uri: Uri, turkuId: Long?, title: String, category: DocumentCategory, favorite: Boolean): ImportResult =
        withContext(Dispatchers.IO) {
            val info = FileManager.saveFromUri(context, uri, FileManager.DIR_DOCS, title, isVideo = false)
            val dup = docDao.findByHash(info.fileHash)
            val id = docDao.insert(
                DocumentItem(
                    turkuId = turkuId,
                    title = title.ifBlank { info.originalFileName.substringBeforeLast('.') },
                    fileType = info.fileExtension.ifBlank { "DOSYA" },
                    localPath = info.filePath,
                    category = category.displayName,
                    fileSize = info.fileSize,
                    fileHash = info.fileHash,
                    isFavorite = favorite
                )
            )
            ImportResult(id, dup?.title)
        }

    // ---------------- İşaretler
    fun markers(videoId: Long): Flow<List<VideoMarker>> = markerDao.markersForVideo(videoId)
    suspend fun addMarker(videoId: Long, pos: Long, label: String) = markerDao.insert(VideoMarker(videoId = videoId, positionMs = pos, label = label))
    suspend fun updateMarker(m: VideoMarker) = markerDao.update(m)
    suspend fun deleteMarker(id: Long) = markerDao.delete(id)

    // ---------------- Çalışma
    fun practiceForTurku(turkuId: Long): Flow<List<PracticeSession>> = practiceDao.forTurku(turkuId)
    suspend fun addPractice(turkuId: Long?, startedAt: Long, durationSec: Long, note: String) =
        practiceDao.insert(PracticeSession(turkuId = turkuId, startedAt = startedAt, durationSec = durationSec, note = note))
    suspend fun deletePractice(id: Long) = practiceDao.delete(id)

    // ---------------- Bakım
    /** 30 günden eski silinenleri kalıcı olarak temizler. */
    suspend fun purgeOldTrash(days: Int = 30) = withContext(Dispatchers.IO) {
        val before = System.currentTimeMillis() - days * 24L * 3600 * 1000
        videoDao.expiredDeleted(before).forEach { deleteVideoForever(it, deleteGalleryFile = false) }
        docDao.expiredDeleted(before).forEach { deleteDocForever(it) }
        turkuDao.expiredDeleted(before).forEach { deleteTurkuForever(it.id, deleteGalleryFiles = false) }
    }

    /** Küçük resmi veya süresi eksik videoları tamamlar (eski arşivden gelenler için). */
    suspend fun repairVideos() = withContext(Dispatchers.IO) {
        for (v in videoDao.allSync()) {
            val needThumb = v.thumbnailPath.isNullOrBlank() || FileManager.resolve(context, v.thumbnailPath) == null
            if (!needThumb && v.durationMs > 0) continue
            if (!VideoStore.exists(context, v.localPath)) continue
            val (dur, thumb) = VideoStore.meta(context, v.localPath)
            videoDao.update(
                v.copy(
                    durationMs = if (v.durationMs > 0) v.durationMs else dur,
                    thumbnailPath = if (needThumb) thumb ?: v.thumbnailPath else v.thumbnailPath
                )
            )
        }
    }

    suspend fun isArchiveEmpty(): Boolean = turkuDao.countAll() == 0
}
