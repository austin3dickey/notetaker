package com.notetaker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single row in a note's checklist.
 *
 * `position` is the stable, global order of items within the note and does **not** change
 * when an item is checked or unchecked — so check→uncheck returns an item to its original
 * spot ("idempotent of checking/unchecking" per the spec). The UI splits items into an
 * unchecked section and a checked section, each sorted by `position`.
 */
@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val noteId: Long,
    val text: String = "",
    val checked: Boolean = false,
    val position: Int,
    val indent: Int = 0,
)
