package com.notetaker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.notetaker.BuildConfig

/**
 * Schema policy, two phases:
 *
 * - **Debug (dev-phase).** Bump the version on every schema shape change and rely on
 *   `fallbackToDestructiveMigration` to drop the local DB instead of bricking the app.
 *   Room's identity-hash check only triggers destructive fallback on version mismatch,
 *   so holding the version fixed while the schema shape moves would defeat the fallback.
 * - **Release.** Destructive fallback is off. A schema change without a matching
 *   [androidx.room.migration.Migration] will fail loudly on open rather than silently
 *   delete user notes. Before shipping, add a `Migration` for every version bump since
 *   the last release.
 *
 * The split is anchored to `BuildConfig.DEBUG` (compile-time build variant) rather than
 * the manifest `debuggable` flag, so an accidentally-debuggable release build still
 * uses the strict policy.
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
                instance ?: build(
                    context = context,
                    dbName = DB_NAME,
                    allowDestructiveMigration = BuildConfig.DEBUG,
                ).also { instance = it }
            }
        }

        /**
         * Testable constructor. Production code uses [get] which defaults the fallback
         * flag to `BuildConfig.DEBUG`; tests can pass either value to exercise both
         * branches of the policy.
         */
        internal fun build(
            context: Context,
            dbName: String,
            allowDestructiveMigration: Boolean,
        ): NotetakerDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                NotetakerDatabase::class.java,
                dbName,
            )
            if (allowDestructiveMigration) {
                builder.fallbackToDestructiveMigration(dropAllTables = true)
            }
            return builder.build()
        }
    }
}
