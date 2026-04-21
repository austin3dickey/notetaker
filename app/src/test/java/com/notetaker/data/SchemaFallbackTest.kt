package com.notetaker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.notetaker.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Locks in the schema-mismatch policy in [NotetakerDatabase.build]: a debug build (flag
 * on) must recover destructively, a release build (flag off) must fail loudly instead of
 * silently erasing user notes. A regression that flips the condition inverse would
 * catastrophically wipe data in release, so this test is important to keep green.
 *
 * Setup is a tiny [LegacyDatabase] class with an unrelated table; writing a row to it
 * and then re-opening at the same file path with [NotetakerDatabase] gives Room a
 * guaranteed identity-hash mismatch on open.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaFallbackTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        NotetakerDatabase.resetForTest()
        context.deleteDatabase(NotetakerDatabase.DB_NAME)
    }

    @After
    fun tearDown() {
        NotetakerDatabase.resetForTest()
        context.deleteDatabase(NotetakerDatabase.DB_NAME)
    }

    @Test
    fun `allowDestructiveMigration=true recovers from a mismatched on-disk schema`() = runTest {
        val dbName = "fallback-on-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        try {
            seedLegacyDatabase(context, dbName)

            val db = NotetakerDatabase.build(
                context = context,
                dbName = dbName,
                allowDestructiveMigration = true,
            )
            try {
                // Destructive fallback fires on first access, wiping the incompatible
                // data and installing the current schema.
                assertThat(db.noteDao().observeActive().first()).isEmpty()
                val id = db.noteDao().insert(
                    Note(title = "fresh", createdAt = 0L, updatedAt = 0L),
                )
                assertThat(db.noteDao().observeActive().first().single().id).isEqualTo(id)
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `allowDestructiveMigration=false fails loudly on a mismatched on-disk schema`() = runTest {
        val dbName = "fallback-off-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        try {
            seedLegacyDatabase(context, dbName)

            val db = NotetakerDatabase.build(
                context = context,
                dbName = dbName,
                allowDestructiveMigration = false,
            )
            try {
                // Room opens the DB lazily; forcing a query surfaces the identity-hash
                // mismatch. Without the fallback, it must throw rather than wipe data.
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { db.noteDao().observeActive().first() }
                }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `get() wires the fallback through BuildConfig_DEBUG end-to-end`() = runTest {
        // Branches on the active build variant so the same test also runs against
        // `testReleaseUnitTest`, where BuildConfig.DEBUG is false. Together this locks
        // in *both* directions of the production wiring: a future regression that
        // hardcoded `true` in get() would fail the release run, and one that hardcoded
        // `false` would fail the debug run.
        seedLegacyDatabase(context, NotetakerDatabase.DB_NAME)

        val db = NotetakerDatabase.get(context)
        if (BuildConfig.DEBUG) {
            // Debug: destructive fallback fires, the incompatible DB is wiped.
            assertThat(db.noteDao().observeActive().first()).isEmpty()
            val id = db.noteDao().insert(Note(title = "fresh", createdAt = 0L, updatedAt = 0L))
            assertThat(db.noteDao().observeActive().first().single().id).isEqualTo(id)
        } else {
            // Release: strict mode, Room refuses to open an incompatible DB.
            assertThrows(IllegalStateException::class.java) {
                runBlocking { db.noteDao().observeActive().first() }
            }
        }
    }

    private fun seedLegacyDatabase(context: Context, dbName: String) {
        val legacy = Room.databaseBuilder(
            context.applicationContext,
            LegacyDatabase::class.java,
            dbName,
        ).build()
        try {
            runBlocking { legacy.dao().insert(LegacyRow(label = "stale")) }
        } finally {
            legacy.close()
        }
    }
}

@Entity(tableName = "legacy_rows")
internal data class LegacyRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
)

@Dao
internal interface LegacyDao {
    @Insert
    suspend fun insert(row: LegacyRow): Long
}

@Database(entities = [LegacyRow::class], version = 1, exportSchema = false)
internal abstract class LegacyDatabase : RoomDatabase() {
    abstract fun dao(): LegacyDao
}
