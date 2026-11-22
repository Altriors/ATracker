package app.atracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.atracker.data.db.AppDatabase
import app.atracker.data.db.Migrations
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Builds a genuine version-1 database from the schema file Room exported for v1, fills it with
 * personal data, and opens it with the current code. Room validates the migrated schema on open,
 * so a wrong migration fails here instead of on the user's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @After
    fun tearDown() { context.deleteDatabase(dbName) }

    private fun createVersion1Database() {
        val schema = File("schemas/app.atracker.data.db.AppDatabase/1.json")
        assertTrue("v1 schema must be committed: ${schema.absolutePath}", schema.exists())
        val db = JSONObject(schema.readText()).getJSONObject("database")
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        try {
            val entities = db.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = db.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
            raw.version = 1

            raw.execSQL("INSERT INTO media (id, mediaType, title, pubStatus, createdAt, metadataUpdatedAt, isManual) VALUES (7, 'WEB_NOVEL', 'Old Novel', 'ONGOING', 1, 1, 0)")
            raw.execSQL("INSERT INTO library_entry (mediaId, status, progress, progressVolumes, rating, favorite, repeatCount, addedAt, updatedAt) VALUES (7, 'CURRENT', 321, 0, 85, 1, 0, 5, 6)")
            raw.execSQL("INSERT INTO activity_event (mediaId, type, unit, delta, runNumber, timestamp) VALUES (7, 'PROGRESS', 'CHAPTER', 321, 1, 9)")
        } finally {
            raw.close()
        }
    }

    @Test
    fun migratingFromV1KeepsAllPersonalDataAndAddsRelationsTable() = runBlocking {
        createVersion1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val entry = db.libraryDao().getEntry(7)
            assertNotNull(entry)
            assertEquals(321, entry!!.progress)
            assertEquals(85, entry.rating)
            assertTrue(entry.favorite)
            assertEquals(1, db.activityDao().allForMedia(7).size)
            assertEquals("Old Novel", db.mediaDao().getById(7)!!.title)

            // the new table works, including its cascade from media
            db.mediaDao().insertRelations(
                listOf(app.atracker.data.db.MediaRelationEntity(mediaId = 7, provider = "anilist", externalId = "1", title = "X", relation = "ADAPTATION"))
            )
            assertEquals(1, db.mediaDao().relationsFor(7).size)
        } finally {
            db.close()
        }
    }
}
