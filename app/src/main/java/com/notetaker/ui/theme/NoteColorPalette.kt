package com.notetaker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.notetaker.data.NoteColor

/**
 * Visual palette for [NoteColor]. Each option has a light and dark variant; M6 will
 * refine these for accessibility and dynamic-color support, but the shape of the
 * function is stable so screens can bind to it now.
 *
 * [NoteColor.NONE] returns the theme's surface color so a blank note blends into
 * the Scaffold's default background on both the editor and overview.
 */
@Composable
fun NoteColor.background(): Color {
    val dark = isSystemInDarkTheme()
    return when (this) {
        NoteColor.NONE -> MaterialTheme.colorScheme.surface
        NoteColor.YELLOW -> if (dark) Color(0xFF4D4620) else Color(0xFFFFF6C7)
        NoteColor.GREEN -> if (dark) Color(0xFF2A4730) else Color(0xFFD9F2D1)
        NoteColor.BLUE -> if (dark) Color(0xFF2A4054) else Color(0xFFD6E9F5)
        NoteColor.PURPLE -> if (dark) Color(0xFF41334F) else Color(0xFFE5D4F5)
        NoteColor.PINK -> if (dark) Color(0xFF4F2E3E) else Color(0xFFF5D4E5)
        NoteColor.GREY -> if (dark) Color(0xFF3D3D3D) else Color(0xFFE8E8E8)
    }
}

/** Human-readable label for the color picker menu. */
fun NoteColor.displayName(): String = when (this) {
    NoteColor.NONE -> "Default"
    NoteColor.YELLOW -> "Yellow"
    NoteColor.GREEN -> "Green"
    NoteColor.BLUE -> "Blue"
    NoteColor.PURPLE -> "Purple"
    NoteColor.PINK -> "Pink"
    NoteColor.GREY -> "Grey"
}
