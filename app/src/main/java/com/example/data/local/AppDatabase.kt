package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentEntity::class, PageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        /** Adds OCR line boxes and a cascading foreign key from pages to documents. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `document_pages_new` (" +
                        "`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, " +
                        "`imagePath` TEXT NOT NULL, `originalImagePath` TEXT NOT NULL, " +
                        "`rotationDegrees` INTEGER NOT NULL, `filterName` TEXT NOT NULL, " +
                        "`ocrText` TEXT NOT NULL, `ocrLinesJson` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO document_pages_new (id, documentId, pageIndex, imagePath, originalImagePath, " +
                        "rotationDegrees, filterName, ocrText, ocrLinesJson) " +
                        "SELECT id, documentId, pageIndex, imagePath, originalImagePath, rotationDegrees, " +
                        "filterName, ocrText, '' FROM document_pages WHERE documentId IN (SELECT id FROM documents)"
                )
                db.execSQL("DROP TABLE document_pages")
                db.execSQL("ALTER TABLE document_pages_new RENAME TO document_pages")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_pages_documentId` ON `document_pages` (`documentId`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null
        private var instanceKey: String? = null

        private const val LEGACY_NAME = "artify_scanner.db"

        /**
         * One database per account ("guest" keeps the original file). The first account to sign in on a phone
         * that already has scans adopts them, so nothing is lost when accounts were introduced.
         */
        fun getInstance(context: Context): AppDatabase = synchronized(this) {
            val app = context.applicationContext
            val key = com.example.auth.AccountScope.key()
            val current = INSTANCE
            if (current != null && instanceKey == key) return current
            current?.close()

            val dbName = if (key == "guest") LEGACY_NAME else "artify_$key.db"
            if (key != "guest") adoptLegacyIfFirstAccount(app, dbName)

            val instance = Room.databaseBuilder(app, AppDatabase::class.java, dbName)
                .addMigrations(MIGRATION_1_2).build() // never wipe user documents
            INSTANCE = instance
            instanceKey = key
            instance
        }

        private fun adoptLegacyIfFirstAccount(app: Context, dbName: String) {
            val prefs = app.getSharedPreferences("artify_db", Context.MODE_PRIVATE)
            if (prefs.getBoolean("legacy_claimed", false)) return
            val target = app.getDatabasePath(dbName)
            val legacy = app.getDatabasePath(LEGACY_NAME)
            if (!target.exists() && legacy.exists()) {
                for (suffix in listOf("", "-wal", "-shm")) {
                    val from = java.io.File(legacy.path + suffix)
                    if (from.exists()) from.renameTo(java.io.File(target.path + suffix))
                }
            }
            prefs.edit().putBoolean("legacy_claimed", true).apply()
        }
    }
}
