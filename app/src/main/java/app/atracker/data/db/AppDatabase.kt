package app.atracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema version history (add a Migration for every bump; never use destructive migration,
 * the database holds the user's only copy of their progress):
 *   1 - initial schema
 */
@Database(
    entities = [
        MediaEntity::class, MediaSourceEntity::class, MediaTitleEntity::class, MediaGenreEntity::class,
        MediaCreditEntity::class, ReleaseInfoEntity::class, CatalogueCacheEntity::class,
        FranchiseEntity::class, FranchiseMemberEntity::class,
        LibraryEntryEntity::class, ActivityEventEntity::class,
        CustomListEntity::class, CustomListItemEntity::class, UserTagEntity::class, LibraryTagEntity::class,
        AppSettingEntity::class
    ],
    version = 1,
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

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                // Migrations are added here as the schema evolves. No fallbackToDestructiveMigration.
                .build()
                .also { instance = it }
        }
    }
}
