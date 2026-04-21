package com.notetaker.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises [NotetakerDatabase.MIGRATION_1_2] against a raw v1-shaped database so the
 * dedup + index-creation steps are verified end-to-end. We avoid Room's
 * `MigrationTestHelper` here because that requires `exportSchema = true` and a committed
 * v1 schema JSON we never captured; the helper will come online the next time the schema
 * changes, once `room.schemaLocation` is wired up.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @Test
    fun `MIGRATION_1_2 dedupes colliding rows and creates the unique index`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Minimal v1 shape — only the columns the migration touches.
                        db.execSQL(
                            """
                            CREATE TABLE checklist_items (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                noteId INTEGER NOT NULL,
                                text TEXT NOT NULL,
                                checked INTEGER NOT NULL,
                                position INTEGER NOT NULL,
                                indent INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )

        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO checklist_items (noteId, text, checked, position, indent) VALUES (1, 'a', 0, 0, 0)")
            db.execSQL("INSERT INTO checklist_items (noteId, text, checked, position, indent) VALUES (1, 'b', 0, 0, 0)")
            db.execSQL("INSERT INTO checklist_items (noteId, text, checked, position, indent) VALUES (1, 'c', 0, 1, 0)")
            db.execSQL("INSERT INTO checklist_items (noteId, text, checked, position, indent) VALUES (2, 'd', 0, 0, 0)")

            NotetakerDatabase.MIGRATION_1_2.migrate(db)

            val rows = mutableListOf<String>()
            db.query("SELECT id, noteId, position, text FROM checklist_items ORDER BY id").use { c ->
                while (c.moveToNext()) {
                    rows += "${c.getLong(0)}|${c.getLong(1)}|${c.getInt(2)}|${c.getString(3)}"
                }
            }
            // Duplicate (noteId=1, position=0) row keeps the lowest id (1, 'a');
            // id=2 ('b') is deleted. The other two rows stand.
            assertThat(rows).containsExactly("1|1|0|a", "3|1|1|c", "4|2|0|d").inOrder()

            assertThrows(SQLiteConstraintException::class.java) {
                db.execSQL(
                    "INSERT INTO checklist_items (noteId, text, checked, position, indent) " +
                        "VALUES (1, 'x', 0, 0, 0)",
                )
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }
}
