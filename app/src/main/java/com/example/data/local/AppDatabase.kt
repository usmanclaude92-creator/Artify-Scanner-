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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "artify_scanner.db"
                ).addMigrations(MIGRATION_1_2).build() // never wipe user documents
                INSTANCE = instance
                instance
            }
        }
    }
}
