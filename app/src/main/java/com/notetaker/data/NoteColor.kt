package com.notetaker.data

/**
 * Background color options for a note. Stored locally only — not synced when a note
 * is shared (see CLAUDE.md spec).
 */
enum class NoteColor {
    NONE,
    YELLOW,
    GREEN,
    BLUE,
    PURPLE,
    PINK,
    GREY,
}
