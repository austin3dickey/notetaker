package com.notetaker.data

import androidx.room.TypeConverter

internal class Converters {
    @TypeConverter
    fun fromNoteColor(value: NoteColor): String = value.name

    @TypeConverter
    fun toNoteColor(value: String): NoteColor = NoteColor.valueOf(value)
}
