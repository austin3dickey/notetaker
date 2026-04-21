package com.notetaker.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.Factory
import androidx.lifecycle.viewModelScope
import com.notetaker.data.ChecklistItem
import com.notetaker.data.Note
import com.notetaker.data.NoteColor
import com.notetaker.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteEditorViewModel(
    private val noteId: Long,
    private val repository: NoteRepository,
) : ViewModel() {

    val state: StateFlow<EditorState> = combine(
        repository.observeNote(noteId),
        repository.observeItems(noteId),
    ) { note, items ->
        if (note == null) {
            EditorState.NotFound
        } else {
            EditorState.Loaded(
                note = note,
                unchecked = items.filter { !it.checked },
                checked = items.filter { it.checked },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = EditorState.Loading,
    )

    fun setTitle(title: String) {
        viewModelScope.launch { repository.updateNoteTitle(noteId, title) }
    }

    fun setColor(color: NoteColor) {
        viewModelScope.launch { repository.setNoteColor(noteId, color) }
    }

    fun appendItem(text: String = "") {
        viewModelScope.launch { repository.appendItem(noteId, text) }
    }

    fun addItemAfter(afterPosition: Int, text: String = "") {
        viewModelScope.launch { repository.addItemAfter(noteId, afterPosition, text) }
    }

    fun updateItemText(item: ChecklistItem, text: String) {
        viewModelScope.launch { repository.updateItemText(item, text) }
    }

    fun toggleChecked(item: ChecklistItem) {
        viewModelScope.launch { repository.setItemChecked(item, !item.checked) }
    }

    fun deleteItem(item: ChecklistItem) {
        viewModelScope.launch { repository.deleteItem(item) }
    }

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        fun factory(noteId: Long, repository: NoteRepository): Factory = object : Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NoteEditorViewModel(noteId, repository) as T
        }

        const val NOTE_ID_KEY: String = "noteId"

        fun noteIdFrom(savedStateHandle: SavedStateHandle): Long =
            requireNotNull(savedStateHandle.get<Long>(NOTE_ID_KEY)) {
                "noteId is required in editor navigation args"
            }
    }
}

sealed interface EditorState {
    data object Loading : EditorState

    data object NotFound : EditorState

    data class Loaded(
        val note: Note,
        val unchecked: List<ChecklistItem>,
        val checked: List<ChecklistItem>,
    ) : EditorState
}
