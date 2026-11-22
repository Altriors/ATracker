package app.atracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `media_relation` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`mediaId` INTEGER NOT NULL, `provider` TEXT NOT NULL, `externalId` TEXT NOT NULL, " +
                    "`relatedType` TEXT, `title` TEXT NOT NULL, `relation` TEXT NOT NULL, " +
                    "FOREIGN KEY(`mediaId`) REFERENCES `media`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_relation_mediaId` ON `media_relation` (`mediaId`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_relation_mediaId_provider_externalId` " +
                    "ON `media_relation` (`mediaId`, `provider`, `externalId`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
