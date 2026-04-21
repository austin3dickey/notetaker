package com.notetaker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Schema is at v1 and will stay fluid until the first real release. The app has never
 * been installed on any device, so there is no persisted v1 DB whose shape we need to
 * preserve — any schema change during this phase can redefine v1 freely.
 *
 * `fallbackToDestructiveMigration(dropAllTables = true)` is the dev-phase safety net: if
 * a developer sideloads a debug APK and the schema later changes, Room drops the local
 * DB instead of bricking the app. **Before the first public release** we must remove the
 * destructive fallback and start real migrations.
 */
@Database(
    entities = [Note::class, ChecklistItem::class],
    version = 1,
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
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
