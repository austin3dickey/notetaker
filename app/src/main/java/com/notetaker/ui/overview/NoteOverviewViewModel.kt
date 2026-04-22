package com.notetaker.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.Factory
import androidx.lifecycle.viewModelScope
import com.notetaker.data.ChecklistItem
import com.notetaker.data.Note
import com.notetaker.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the overview screen: active notes with unchecked-item previews and a parallel
 * list of archived notes.
 *
 * Both lists derive from a single `observeAllNotes()` stream and are partitioned in
 * memory so they always reflect the same database snapshot — combining two separate
 * active/archived flows would let an archive toggle briefly emit a state where a note
 * appears in both sections (or neither) depending on observer ordering.
 *
 * Active previews are derived per-note by observing each note's items flow. Archived
 * notes render title-only (no preview), so we skip the per-note item subscription for
 * them. For a handful of notes on-device the active N+1 is negligible; if note counts
 * grow large enough to matter, swap in a single JOIN query that returns note+preview
 * rows directly.
 */
class NoteOverviewViewModel(
    private val repository: NoteRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<OverviewState> = repository.observeAllNotes()
        .flatMapLatest { notes ->
            val (archived, active) = notes.partition { it.archived }
            summariesFlow(active).map { activeSummaries ->
                OverviewState.Loaded(
                    notes = activeSummaries,
                    archived = archived.map(::archivedSummary),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = OverviewState.Loading,
        )

    /** Creates a blank note and returns its id so the caller can navigate to it. */
    suspend fun createNote(): Long = repository.createNote()

    private fun summariesFlow(notes: List<Note>): Flow<List<NoteSummary>> {
        if (notes.isEmpty()) return flowOf(emptyList())
        val perNote = notes.map { note ->
            repository.observeItems(note.id).map { items -> summarize(note, items) }
        }
        return combine(perNote) { it.toList() }
    }

    private fun summarize(note: Note, items: List<ChecklistItem>): NoteSummary {
        val preview = items.asSequence()
            .filter { !it.checked }
            .take(PREVIEW_LINES)
            .map { it.text }
            .toList()
        return NoteSummary(id = note.id, title = note.title, previewLines = preview)
    }

    private fun archivedSummary(note: Note): NoteSummary =
        NoteSummary(id = note.id, title = note.title, previewLines = emptyList())

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        private const val PREVIEW_LINES = 2

        fun factory(repository: NoteRepository): Factory = object : Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NoteOverviewViewModel(repository) as T
        }
    }
}

sealed interface OverviewState {
    data object Loading : OverviewState

    data class Loaded(
        val notes: List<NoteSummary>,
        val archived: List<NoteSummary> = emptyList(),
    ) : OverviewState
}

data class NoteSummary(
    val id: Long,
    val title: String,
    val previewLines: List<String>,
)
