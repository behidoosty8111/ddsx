package com.example.mfb.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mfb.data.local.room.dao.BuildDao
import com.example.mfb.data.local.room.dao.ProjectDao
import com.example.mfb.data.local.room.entity.BuildEntity
import com.example.mfb.data.local.room.entity.ProjectEntity

@Database(
    entities = [ProjectEntity::class, BuildEntity::class],
    version = 6,
    exportSchema = false
)
abstract class MfbDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildDao(): BuildDao

    companion object {
        @Volatile
        private var INSTANCE: MfbDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN projectUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN originalFileName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'ZIP'")
                db.execSQL("ALTER TABLE projects ADD COLUMN mainHtmlRelativePath TEXT NOT NULL DEFAULT 'index.html'")
                db.execSQL("ALTER TABLE projects ADD COLUMN internalProjectPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN fileCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE projects ADD COLUMN totalSizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN importStatus TEXT NOT NULL DEFAULT 'SUCCESS'")
                db.execSQL("ALTER TABLE projects ADD COLUMN thumbnailPath TEXT")
                db.execSQL("ALTER TABLE projects ADD COLUMN missingAssets TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN lastOpenedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN healthStatus TEXT NOT NULL DEFAULT 'CHECK_REQUIRED'")
                db.execSQL("ALTER TABLE projects ADD COLUMN healthMessage TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN projectCategory TEXT NOT NULL DEFAULT 'WEB_HTML'")
                db.execSQL("ALTER TABLE projects ADD COLUMN detectedTechSummary TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE builds ADD COLUMN buildProvider TEXT NOT NULL DEFAULT 'REMOTE'")
                db.execSQL("ALTER TABLE builds ADD COLUMN packageName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE builds ADD COLUMN buildLogs TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE builds ADD COLUMN failureReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE builds ADD COLUMN remoteJobId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE builds ADD COLUMN runtimeInfo TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): MfbDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MfbDatabase::class.java,
                    "mfb_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
