package com.nesimi.baglamaarsivi.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TurkuDao {
    @Query("SELECT * FROM turkuler WHERE isDeleted = 0 ORDER BY manualOrder ASC, updatedAt DESC")
    fun activeTurkus(): Flow<List<Turku>>

    @Query("SELECT * FROM turkuler WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun deletedTurkus(): Flow<List<Turku>>

    @Query("SELECT * FROM turkuler WHERE id = :id LIMIT 1")
    fun turkuById(id: Long): Flow<Turku?>

    @Query("SELECT * FROM turkuler WHERE id = :id LIMIT 1")
    suspend fun turkuByIdSync(id: Long): Turku?

    @Query("SELECT COUNT(*) FROM turkuler")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(turku: Turku): Long

    @Update
    suspend fun update(turku: Turku)

    @Query("UPDATE turkuler SET isFavorite = :fav, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE turkuler SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE turkuler SET personalNote = :note, updatedAt = :now WHERE id = :id")
    suspend fun setNote(id: Long, note: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE turkuler SET manualOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Query("UPDATE turkuler SET isDeleted = 1, deletedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE turkuler SET isDeleted = 0, deletedAt = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM turkuler WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("SELECT * FROM turkuler WHERE isDeleted = 1 AND deletedAt > 0 AND deletedAt < :before")
    suspend fun expiredDeleted(before: Long): List<Turku>
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM videolar WHERE turkuId = :turkuId AND isDeleted = 0")
    fun videosForTurku(turkuId: Long): Flow<List<VideoItem>>

    @Query("SELECT * FROM videolar WHERE turkuId = :turkuId")
    suspend fun allVideosForTurkuSync(turkuId: Long): List<VideoItem>

    @Query("SELECT * FROM videolar WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun activeVideos(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videolar WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun deletedVideos(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videolar WHERE id = :id LIMIT 1")
    fun videoById(id: Long): Flow<VideoItem?>

    @Query("SELECT * FROM videolar WHERE id = :id LIMIT 1")
    suspend fun videoByIdSync(id: Long): VideoItem?

    @Query("SELECT * FROM videolar WHERE fileHash = :hash AND fileHash != '' AND isDeleted = 0 LIMIT 1")
    suspend fun findByHash(hash: String): VideoItem?

    @Query("SELECT * FROM videolar WHERE isDeleted = 0 AND lastPlaybackPositionMs > 3000 ORDER BY updatedAt DESC LIMIT :limit")
    fun recentlyWatched(limit: Int = 10): Flow<List<VideoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoItem): Long

    @Update
    suspend fun update(video: VideoItem)

    @Query("UPDATE videolar SET lastPlaybackPositionMs = :pos, updatedAt = :now WHERE id = :id")
    suspend fun setPosition(id: Long, pos: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE videolar SET durationMs = :dur WHERE id = :id")
    suspend fun setDuration(id: Long, dur: Long)

    @Query("UPDATE videolar SET isFavorite = :fav, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE videolar SET orderTag = :tag, manualOrder = :order WHERE id = :id")
    suspend fun setOrderTag(id: Long, tag: String, order: Int)

    @Query("UPDATE videolar SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE videolar SET isDeleted = 0, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM videolar WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("SELECT * FROM videolar WHERE isDeleted = 1 AND updatedAt < :before")
    suspend fun expiredDeleted(before: Long): List<VideoItem>

    @Query("SELECT * FROM videolar")
    suspend fun allSync(): List<VideoItem>
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM belgeler WHERE turkuId = :turkuId AND isDeleted = 0 ORDER BY manualOrder ASC, createdAt DESC")
    fun docsForTurku(turkuId: Long): Flow<List<DocumentItem>>

    @Query("SELECT * FROM belgeler WHERE turkuId = :turkuId")
    suspend fun allDocsForTurkuSync(turkuId: Long): List<DocumentItem>

    @Query("SELECT * FROM belgeler WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun activeDocs(): Flow<List<DocumentItem>>

    @Query("SELECT * FROM belgeler WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun deletedDocs(): Flow<List<DocumentItem>>

    @Query("SELECT * FROM belgeler WHERE fileHash = :hash AND fileHash != '' AND isDeleted = 0 LIMIT 1")
    suspend fun findByHash(hash: String): DocumentItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: DocumentItem): Long

    @Update
    suspend fun update(doc: DocumentItem)

    @Query("UPDATE belgeler SET isFavorite = :fav, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE belgeler SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE belgeler SET isDeleted = 0, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM belgeler WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("SELECT * FROM belgeler WHERE isDeleted = 1 AND updatedAt < :before")
    suspend fun expiredDeleted(before: Long): List<DocumentItem>

    @Query("SELECT * FROM belgeler")
    suspend fun allSync(): List<DocumentItem>
}

@Dao
interface MarkerDao {
    @Query("SELECT * FROM video_isaretleri WHERE videoId = :videoId ORDER BY positionMs ASC")
    fun markersForVideo(videoId: Long): Flow<List<VideoMarker>>

    @Insert
    suspend fun insert(marker: VideoMarker): Long

    @Update
    suspend fun update(marker: VideoMarker)

    @Query("DELETE FROM video_isaretleri WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PracticeDao {
    @Query("SELECT * FROM calisma_kayitlari ORDER BY startedAt DESC")
    fun all(): Flow<List<PracticeSession>>

    @Query("SELECT * FROM calisma_kayitlari WHERE turkuId = :turkuId ORDER BY startedAt DESC")
    fun forTurku(turkuId: Long): Flow<List<PracticeSession>>

    @Insert
    suspend fun insert(session: PracticeSession): Long

    @Query("DELETE FROM calisma_kayitlari WHERE id = :id")
    suspend fun delete(id: Long)
}
