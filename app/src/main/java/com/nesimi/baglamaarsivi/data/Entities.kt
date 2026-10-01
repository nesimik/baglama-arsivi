package com.nesimi.baglamaarsivi.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * ÖNEMLİ: turkuler / videolar / belgeler tabloları eski "Bağlama Arşivim" (AI Studio) uygulamasının
 * veritabanı şemasıyla (sürüm 3) birebir aynıdır. Böylece eski veritabanı dosyası bu uygulamaya
 * aktarıldığında hiçbir veri kaybolmadan açılır. Bu üç tablonun alanlarını değiştirmeyin;
 * yeni özellikler için yeni tablo + Migration ekleyin.
 */

@Entity(tableName = "turkuler")
data class Turku(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val region: String = "",
    val artist: String = "",
    val makam: String = "",
    val level: String = "Başlangıç",
    val status: String = StudyStatus.CALISIYORUM.name,
    val isFavorite: Boolean = false,
    val description: String = "",
    val personalNote: String = "",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val manualOrder: Int = 0,
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0
)

@Entity(
    tableName = "videolar",
    foreignKeys = [
        ForeignKey(
            entity = Turku::class,
            parentColumns = ["id"],
            childColumns = ["turkuId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["turkuId"]), Index(value = ["fileHash"])]
)
data class VideoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val turkuId: Long,
    val title: String,
    val localPath: String,
    val durationMs: Long = 0L,
    val lessonDate: String = "",
    val instructor: String = "",
    val description: String = "",
    val tags: String = "",
    val thumbnailPath: String? = null,
    val lastPlaybackPositionMs: Long = 0L,
    val manualOrder: Int = 0,
    val orderTag: String = "",
    val uploadOrder: Int = 0,
    val isFavorite: Boolean = false,
    val fileSize: Long = 0L,
    val fileHash: String = "",
    val backupStatus: String = "LOCAL_ONLY",
    val cloudUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    val displayOrderTag: String
        get() = if (orderTag.isNotBlank()) orderTag.trim() else if (manualOrder > 0) manualOrder.toString() else ""
}

@Entity(
    tableName = "belgeler",
    foreignKeys = [
        ForeignKey(
            entity = Turku::class,
            parentColumns = ["id"],
            childColumns = ["turkuId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["turkuId"]), Index(value = ["fileHash"])]
)
data class DocumentItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val turkuId: Long? = null,
    val title: String,
    val fileType: String = "PDF",
    val localPath: String,
    val category: String = DocumentCategory.NOTA.displayName,
    val fileSize: Long = 0L,
    val fileHash: String = "",
    val isFavorite: Boolean = false,
    val manualOrder: Int = 0,
    val backupStatus: String = "LOCAL_ONLY",
    val cloudUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

/** Yeni (v4): Video içinde zaman işaretleri – "1:23 sol el geçişi" gibi. */
@Entity(
    tableName = "video_isaretleri",
    foreignKeys = [
        ForeignKey(
            entity = VideoItem::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["videoId"])]
)
data class VideoMarker(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: Long,
    val positionMs: Long,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Yeni (v4): Çalışma oturumları – süre takibi ve seri (gün üst üste çalışma). */
@Entity(
    tableName = "calisma_kayitlari",
    foreignKeys = [
        ForeignKey(
            entity = Turku::class,
            parentColumns = ["id"],
            childColumns = ["turkuId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["turkuId"])]
)
data class PracticeSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val turkuId: Long? = null,
    val startedAt: Long,
    val durationSec: Long,
    @ColumnInfo(defaultValue = "")
    val note: String = ""
)

data class TurkuWithDetails(
    val turku: Turku,
    val videoCount: Int = 0,
    val documentCount: Int = 0,
    val lastVideoDate: String? = null,
    val practiceSec: Long = 0
)
