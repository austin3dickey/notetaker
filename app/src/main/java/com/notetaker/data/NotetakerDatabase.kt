package com.notetaker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Note::class, ChecklistItem::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class NotetakerDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun itemDao(): ChecklistItemDao

    companion object {
        private const val DB_NAME = "notetaker.db"

        /**
         * v1 -> v2 adds the unique (noteId, position) index on checklist_items. Any row
         * pair that already collides from the pre-index race is de-duped (keeping the
         * lowest id) before the index is created so the upgrade never aborts mid-way.
         */
        internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM checklist_items
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM checklist_items GROUP BY noteId, position
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_checklist_items_noteId_position` " +
                        "ON `checklist_items` (`noteId`, `position`)",
                )
            }
        }

        @Volatile
        private var instance: NotetakerDatabase? = null

        fun get(context: Context): NotetakerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotetakerDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
