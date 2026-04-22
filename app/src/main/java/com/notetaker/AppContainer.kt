package com.notetaker

import android.content.Context
import com.notetaker.data.NoteRepository
import com.notetaker.data.NotetakerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    /**
     * Scope for writes that must complete even if the launching `ViewModel` is
     * cleared — e.g. note deletion, where a back-navigation mid-delete would
     * otherwise cancel the Room transaction before it commits. `SupervisorJob`
     * keeps one failing write from tearing the scope down for everything else.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
