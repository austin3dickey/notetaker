package com.notetaker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
                ).build().also { instance = it }
            }
        }
    }
}
