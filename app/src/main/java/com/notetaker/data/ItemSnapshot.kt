package com.notetaker.data

/**
 * Minimal, ID-free view of a checklist item used by undo/redo snapshots. Preserves the
 * fields a user can influence in the editor (text, checked state, position, indent).
 * Row IDs aren't snapshotted: restoring a snapshot re-inserts items with fresh IDs, so
 * identity naturally drifts across undo/redo cycles — the visible content is what
 * undo/redo is about.
 */
data class ItemSnapshot(
    val text: String,
    val checked: Boolean,
    val position: Int,
    val indent: Int,
)

fun ChecklistItem.toSnapshot(): ItemSnapshot =
    ItemSnapshot(text = text, checked = checked, position = position, indent = indent)
