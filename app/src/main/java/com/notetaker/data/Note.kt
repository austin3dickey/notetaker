package com.notetaker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val color: NoteColor = NoteColor.NONE,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
