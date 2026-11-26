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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `import_run` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`source` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `status` TEXT NOT NULL, " +
                    "`policy` TEXT NOT NULL, `added` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, " +
                    "`unmatched` INTEGER NOT NULL, `failed` INTEGER NOT NULL, `message` TEXT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `import_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`runId` INTEGER NOT NULL, `malKind` TEXT NOT NULL, `malId` INTEGER, `title` TEXT NOT NULL, " +
                    "`outcome` TEXT NOT NULL, `detail` TEXT, `mediaId` INTEGER, " +
                    "FOREIGN KEY(`runId`) REFERENCES `import_run`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_record_runId` ON `import_record` (`runId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
