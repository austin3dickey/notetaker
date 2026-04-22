package com.notetaker.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.Factory
import androidx.lifecycle.viewModelScope
import com.notetaker.data.ChecklistItem
import com.notetaker.data.ItemSnapshot
import com.notetaker.data.Note
import com.notetaker.data.NoteColor
import com.notetaker.data.NoteRepository
import com.notetaker.data.toSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteEditorViewModel(
    private val noteId: Long,
    private val repository: NoteRepository,
    private val externalScope: CoroutineScope,
) : ViewModel() {

    // Latches true the first time we successfully load the note. Lives on the
    // ViewModel (not composable-local state) so it survives configuration
    // changes: if the user confirms delete and rotates before Room commits, the
    // recreated composable still sees `wasLoaded=true` and pops on the NotFound
    // transition instead of stranding the user on the "not found" screen.
    private val _wasLoaded = MutableStateFlow(false)
    val wasLoaded: StateFlow<Boolean> = _wasLoaded.asStateFlow()

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
    }.onEach { next ->
        if (next is EditorState.Loaded) _wasLoaded.value = true
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = EditorState.Loading,
    )

    // In-memory undo/redo history, capped at [MAX_DEPTH]. Lives on the ViewModel so the
    // history naturally evaporates when the editor closes (per the spec's "clear on
    // close" requirement). [canUndo]/[canRedo] are kept in lockstep rather than derived
    // via stateIn so that their `.value` is always synchronously up to date — a
    // `stateIn` with WhileSubscribed would only materialize when someone is collecting.
    private var history: UndoHistory = UndoHistory()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun setTitle(title: String) {
        recordUndoPoint(UndoKey.TitleEdit)
        viewModelScope.launch { repository.updateNoteTitle(noteId, title) }
    }

    fun setColor(color: NoteColor) {
        recordUndoPoint(UndoKey.Structural)
        viewModelScope.launch { repository.setNoteColor(noteId, color) }
    }

    fun appendItem(text: String = "") {
        recordUndoPoint(UndoKey.Structural)
        viewModelScope.launch { repository.appendItem(noteId, text) }
    }

    fun addItemAfter(afterPosition: Int, text: String = "") {
        recordUndoPoint(UndoKey.Structural)
        viewModelScope.launch { repository.addItemAfter(noteId, afterPosition, text) }
    }

    fun updateItemText(item: ChecklistItem, text: String) {
        recordUndoPoint(UndoKey.ItemTextEdit(item.id))
        viewModelScope.launch { repository.updateItemText(item, text) }
    }

    fun toggleChecked(item: ChecklistItem) {
        recordUndoPoint(UndoKey.Structural)
        viewModelScope.launch { repository.setItemChecked(item, !item.checked) }
    }

    fun deleteItem(item: ChecklistItem) {
        recordUndoPoint(UndoKey.Structural)
        viewModelScope.launch { repository.deleteItem(item) }
    }

    /**
     * Pops the top of the undo stack and restores that snapshot, pushing the current
     * state onto the redo stack so [redo] can reapply it. No-op if nothing to undo.
     */
    fun undo() {
        val loaded = state.value as? EditorState.Loaded ?: return
        val target = history.undo.lastOrNull() ?: return
        val current = loaded.toSnapshot()
        updateHistory(
            UndoHistory(
                undo = history.undo.dropLast(1),
                redo = history.redo + current,
                // Reset coalescing so the next user edit pushes a fresh snapshot instead
                // of silently folding into a stack entry that's no longer the top.
                lastPushKey = null,
            ),
        )
        applySnapshot(target)
    }

    /**
     * Symmetric to [undo]: pops the top of the redo stack, restores it, and pushes the
     * pre-redo state onto the undo stack.
     */
    fun redo() {
        val loaded = state.value as? EditorState.Loaded ?: return
        val target = history.redo.lastOrNull() ?: return
        val current = loaded.toSnapshot()
        updateHistory(
            UndoHistory(
                undo = (history.undo + current).takeLast(MAX_DEPTH),
                redo = history.redo.dropLast(1),
                lastPushKey = null,
            ),
        )
        applySnapshot(target)
    }

    /**
     * Deletes the owning note. Runs in [externalScope] (the application scope) so
     * the write survives the ViewModel being cleared — if the user confirms delete
     * and then back-navigates before the transaction commits, [viewModelScope]
     * would cancel mid-delete and the note would still exist. Completion is
     * observed by the UI through the state flow flipping to [EditorState.NotFound]
     * once Room commits, so the screen can pop on that transition.
     */
    fun deleteNote() {
        externalScope.launch {
            repository.deleteNote(noteId)
        }
    }

    /**
     * Records the state *before* a pending mutation so [undo] can return to it. If the
     * new mutation is the same coalesceable action as the previous one (e.g. another
     * keystroke on the same item), we skip the push — the existing top-of-stack already
     * captures the state before this burst of edits. Any user mutation clears the redo
     * stack (standard "new edit branches history" behavior).
     *
     * No-op when state hasn't loaded yet: there's nothing sensible to snapshot, and any
     * mutations the user could trigger before Loaded are themselves ignored upstream
     * (the UI doesn't render editable widgets in Loading/NotFound).
     */
    private fun recordUndoPoint(key: UndoKey) {
        val loaded = state.value as? EditorState.Loaded ?: return
        val prev = history
        val next = if (key.coalescesWith(prev.lastPushKey)) {
            prev.copy(redo = emptyList())
        } else {
            val snapshot = loaded.toSnapshot()
            prev.copy(
                undo = (prev.undo + snapshot).takeLast(MAX_DEPTH),
                redo = emptyList(),
                lastPushKey = key,
            )
        }
        updateHistory(next)
    }

    private fun updateHistory(next: UndoHistory) {
        history = next
        _canUndo.value = next.undo.isNotEmpty()
        _canRedo.value = next.redo.isNotEmpty()
    }

    private fun applySnapshot(snapshot: EditorSnapshot) {
        viewModelScope.launch {
            repository.replaceNoteContents(
                noteId = noteId,
                title = snapshot.title,
                color = snapshot.color,
                items = snapshot.items,
            )
        }
    }

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        internal const val MAX_DEPTH: Int = 50

        fun factory(
            noteId: Long,
            repository: NoteRepository,
            externalScope: CoroutineScope,
        ): Factory = object : Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NoteEditorViewModel(noteId, repository, externalScope) as T
        }

        const val NOTE_ID_KEY: String = "noteId"

        fun noteIdFrom(savedStateHandle: SavedStateHandle): Long =
            requireNotNull(savedStateHandle.get<Long>(NOTE_ID_KEY)) {
                "noteId is required in editor navigation args"
            }
    }
}

/**
 * Captures every field the editor can mutate in a single snapshot. Used as the unit of
 * storage for the undo and redo stacks.
 */
internal data class EditorSnapshot(
    val title: String,
    val color: NoteColor,
    val items: List<ItemSnapshot>,
)

internal fun EditorState.Loaded.toSnapshot(): EditorSnapshot {
    val allItems = (unchecked + checked).sortedBy { it.position }.map { it.toSnapshot() }
    return EditorSnapshot(title = note.title, color = note.color, items = allItems)
}

internal data class UndoHistory(
    val undo: List<EditorSnapshot> = emptyList(),
    val redo: List<EditorSnapshot> = emptyList(),
    // The key of the last snapshot pushed onto [undo]. Null after an undo/redo, since
    // the stack top no longer represents a user edit we can extend.
    val lastPushKey: UndoKey? = null,
)

/**
 * Tag attached to each push so we can coalesce bursts of related edits (keystrokes in
 * the title field, keystrokes on the same item) into a single undo entry.
 * [Structural] never coalesces.
 */
internal sealed interface UndoKey {
    data object Structural : UndoKey
    data object TitleEdit : UndoKey
    data class ItemTextEdit(val itemId: Long) : UndoKey

    fun coalescesWith(other: UndoKey?): Boolean = this != Structural && this == other
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
