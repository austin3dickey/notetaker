package com.notetaker.data

/**
 * A checklist item as it looked at a point in time. Used as the unit of storage for
 * undo/redo snapshots. Preserves [id] so restoring a snapshot keeps row identity — any
 * `ChecklistItem` reference the UI still holds from before the restore (say, a user who
 * tapped a checkbox the instant after clicking undo) remains valid against the
 * restored row.
 */
data class ItemSnapshot(
    val id: Long,
    val text: String,
    val checked: Boolean,
    val position: Int,
    val indent: Int,
)

fun ChecklistItem.toSnapshot(): ItemSnapshot =
    ItemSnapshot(
        id = id,
        text = text,
        checked = checked,
        position = position,
        indent = indent,
    )
