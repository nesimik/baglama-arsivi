package com.nesimi.baglamaarsivi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [Turku::class, VideoItem::class, DocumentItem::class, VideoMarker::class, PracticeSession::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun turkuDao(): TurkuDao
    abstract fun videoDao(): VideoDao
    abstract fun documentDao(): DocumentDao
    abstract fun markerDao(): MarkerDao
    abstract fun practiceDao(): PracticeDao

    companion object {
        const val DB_NAME = "baglama_arsivi.db"

        /** Eski uygulamanın (Bağlama Arşivim) geçmiş sürümleri */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videolar ADD COLUMN uploadOrder INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videolar ADD COLUMN orderTag TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4: video işaretleri + çalışma kayıtları (eski veriye dokunmaz) */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `video_isaretleri` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`videoId` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`videoId`) REFERENCES `videolar`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_isaretleri_videoId` ON `video_isaretleri` (`videoId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calisma_kayitlari` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`turkuId` INTEGER, `startedAt` INTEGER NOT NULL, `durationSec` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL DEFAULT '', FOREIGN KEY(`turkuId`) REFERENCES `turkuler`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calisma_kayitlari_turkuId` ON `calisma_kayitlari` (`turkuId`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        /** Geri yükleme öncesi veritabanını kapatır. */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun build(context: Context): AppDatabase {
            val filesDir = context.filesDir.absolutePath
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                // NOT: fallbackToDestructiveMigration KULLANILMIYOR -> veriler asla sessizce silinmez.
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        fixFilePaths(db, filesDir)
                    }
                })
                .build()
        }

        /**
         * Başka bir uygulamadan (veya başka telefondan) gelen veritabanındaki dosya yollarını
         * bu uygulamanın klasörüne çevirir. Örn:
         * /data/user/0/com.aistudio.baglamaarsivim.zpkrv/files/videos/a.mp4 -> <bizim files>/videos/a.mp4
         */
        fun fixFilePaths(db: SupportSQLiteDatabase, filesDir: String) {
            val prefix = "$filesDir/"
            val targets = listOf("videolar" to "localPath", "videolar" to "thumbnailPath", "belgeler" to "localPath")
            for ((table, col) in targets) {
                try {
                    db.execSQL(
                        "UPDATE `$table` SET `$col` = ? || substr(`$col`, instr(`$col`, '/files/') + 7) " +
                            "WHERE `$col` IS NOT NULL AND instr(`$col`, '/files/') > 0 AND substr(`$col`, 1, ?) != ?",
                        arrayOf<Any>(prefix, prefix.length, prefix)
                    )
                } catch (_: Exception) {
                }
            }
        }

        fun databaseFile(context: Context): File = context.getDatabasePath(DB_NAME)
    }
}
