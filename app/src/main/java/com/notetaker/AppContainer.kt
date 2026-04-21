package com.notetaker

import android.content.Context
import com.notetaker.data.NoteRepository
import com.notetaker.data.NotetakerDatabase

/**
 * Poor-man's DI: one place that wires the app graph. Accessed through
 * [NotetakerApp.container]. Swap for Hilt/Koin only if/when the graph outgrows this.
 */
class AppContainer(context: Context) {
    private val database = NotetakerDatabase.get(context)

    val noteRepository: NoteRepository = NoteRepository(
        db = database,
        noteDao = database.noteDao(),
        itemDao = database.itemDao(),
    )
}
