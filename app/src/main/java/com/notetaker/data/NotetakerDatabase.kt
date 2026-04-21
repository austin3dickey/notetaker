package com.notetaker.data

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Schema policy, two phases:
 *
 * - **Debug (dev-phase).** Bump the version on every schema shape change and rely on
 *   `fallbackToDestructiveMigration` to drop the local DB instead of bricking the app.
 *   Room's identity-hash check only triggers destructive fallback when the version
 *   mismatches, so holding the version fixed while the schema shape moves would defeat
 *   the fallback.
 * - **Release.** Destructive fallback is off. A schema change without a matching
 *   [androidx.room.migration.Migration] will fail loudly on open rather than silently
 *   delete user notes. Before shipping, add a `Migration` for every version bump since
 *   the last release.
 */
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

        @Volatile
        private var instance: NotetakerDatabase? = null

        fun get(context: Context): NotetakerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotetakerDatabase::class.java,
                    DB_NAME,
                )
                    .apply {
                        if (context.isDebuggable) {
                            fallbackToDestructiveMigration(dropAllTables = true)
                        }
                    }
                    .build()
                    .also { instance = it }
            }
        }

        private val Context.isDebuggable: Boolean
            get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
