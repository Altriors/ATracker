package app.atracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema version history (add a Migration for every bump; never use destructive migration,
 * the database holds the user's only copy of their progress):
 *   1 - initial schema
 *   2 - media_relation table (provider-reported relations, used for franchise suggestions)
 */
@Database(
    entities = [
        MediaEntity::class, MediaSourceEntity::class, MediaTitleEntity::class, MediaGenreEntity::class,
        MediaCreditEntity::class, MediaRelationEntity::class, ReleaseInfoEntity::class, CatalogueCacheEntity::class,
        FranchiseEntity::class, FranchiseMemberEntity::class,
        LibraryEntryEntity::class, ActivityEventEntity::class,
        CustomListEntity::class, CustomListItemEntity::class, UserTagEntity::class, LibraryTagEntity::class,
        AppSettingEntity::class
    ],
    version = AppDatabase.VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun libraryDao(): LibraryDao
    abstract fun activityDao(): ActivityDao
    abstract fun franchiseDao(): FranchiseDao
    abstract fun listDao(): ListDao
    abstract fun cacheDao(): CacheDao
    abstract fun settingDao(): SettingDao

    companion object {
        const val FILE_NAME = "atracker.db"
        const val VERSION = 2

        @Volatile private var instance: AppDatabase? = null

        /** Closes and forgets the open database so the file can be replaced (restore). */
        fun reset() = synchronized(this) { instance?.close(); instance = null }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                // Every schema change needs a Migration. No fallbackToDestructiveMigration: the database
                // is the only copy of the user's progress.
                .addMigrations(*Migrations.ALL)
                .build()
                .also { instance = it }
        }
    }
}
