package com.nesimi.baglamaarsivi.data

import android.content.Context
import android.net.Uri
import com.nesimi.baglamaarsivi.util.FileManager
import com.nesimi.baglamaarsivi.util.Tr
import com.nesimi.baglamaarsivi.util.VideoOrder
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

    suspend fun deleteTurkuForever(id: Long) {
        for (v in videoDao.allVideosForTurkuSync(id)) {
            FileManager.delete(v.localPath); FileManager.delete(v.thumbnailPath)
        }
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

    suspend fun deleteVideoForever(v: VideoItem) {
        FileManager.delete(v.localPath); FileManager.delete(v.thumbnailPath)
        videoDao.deleteForever(v.id)
    }

    data class ImportResult(val id: Long, val duplicateOf: String?)

    suspend fun importVideo(
        uri: Uri, turkuId: Long, title: String, lessonDate: String, instructor: String,
        description: String, tags: String, orderTag: String, uploadOrder: Int, favorite: Boolean
    ): ImportResult = withContext(Dispatchers.IO) {
        val info = FileManager.saveFromUri(context, uri, FileManager.DIR_VIDEOS, title, isVideo = true)
        val dup = videoDao.findByHash(info.fileHash)
        val clean = orderTag.trim().removePrefix("#").trim()
        val id = videoDao.insert(
            VideoItem(
                turkuId = turkuId,
                title = title.ifBlank { info.originalFileName.substringBeforeLast('.') },
                localPath = info.filePath,
                durationMs = info.durationMs,
                lessonDate = lessonDate.ifBlank { Tr.today() },
                instructor = instructor.trim(),
                description = description.trim(),
                tags = tags.trim(),
                thumbnailPath = info.thumbnailPath,
                fileSize = info.fileSize,
                fileHash = info.fileHash,
                isFavorite = favorite,
                orderTag = clean,
                manualOrder = clean.takeWhile { it.isDigit() }.toIntOrNull() ?: 0,
                uploadOrder = uploadOrder
            )
        )
        ImportResult(id, dup?.title)
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
        videoDao.expiredDeleted(before).forEach { deleteVideoForever(it) }
        docDao.expiredDeleted(before).forEach { deleteDocForever(it) }
        turkuDao.expiredDeleted(before).forEach { deleteTurkuForever(it.id) }
    }

    /** Küçük resmi veya süresi eksik videoları tamamlar (eski arşivden gelenler için). */
    suspend fun repairVideos() = withContext(Dispatchers.IO) {
        for (v in videoDao.allSync()) {
            val needThumb = v.thumbnailPath.isNullOrBlank() || FileManager.resolve(context, v.thumbnailPath) == null
            if (!needThumb && v.durationMs > 0) continue
            val f = FileManager.resolve(context, v.localPath) ?: continue
            val (dur, thumb) = FileManager.videoMeta(context, f.absolutePath)
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
