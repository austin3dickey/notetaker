package com.notetaker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Single entry point for reads/writes against notes and checklist items. The UI layer
 * never touches DAOs directly — this gives us one place to manage timestamps, enforce
 * invariants (e.g. the "can't delete a shared note" rule from the spec, coming in M5),
 * and swap storage later if needed.
 */
class NoteRepository(
    private val noteDao: NoteDao,
    private val itemDao: ChecklistItemDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeActive(): Flow<List<Note>> = noteDao.observeActive()

    fun observeArchived(): Flow<List<Note>> = noteDao.observeArchived()

    fun observeNote(noteId: Long): Flow<Note?> = noteDao.observeById(noteId)

    fun observeItems(noteId: Long): Flow<List<ChecklistItem>> = itemDao.observeByNote(noteId)

    suspend fun createNote(title: String = "", color: NoteColor = NoteColor.NONE): Long {
        val now = clock()
        return noteDao.insert(
            Note(title = title, color = color, createdAt = now, updatedAt = now),
        )
    }

    suspend fun updateNoteTitle(noteId: Long, title: String) {
        val current = noteDao.observeById(noteId).first() ?: return
        noteDao.update(current.copy(title = title, updatedAt = clock()))
    }

    suspend fun setNoteColor(noteId: Long, color: NoteColor) {
        val current = noteDao.observeById(noteId).first() ?: return
        noteDao.update(current.copy(color = color, updatedAt = clock()))
    }

    suspend fun setNoteArchived(noteId: Long, archived: Boolean) {
        val current = noteDao.observeById(noteId).first() ?: return
        noteDao.update(current.copy(archived = archived, updatedAt = clock()))
    }

    suspend fun deleteNote(noteId: Long) {
        val current = noteDao.observeById(noteId).first() ?: return
        noteDao.delete(current)
    }

    suspend fun appendItem(noteId: Long, text: String = ""): Long {
        val position = itemDao.nextPosition(noteId)
        touchNote(noteId)
        return itemDao.insert(
            ChecklistItem(noteId = noteId, text = text, position = position),
        )
    }

    /**
     * Insert a new item immediately after the one at [afterPosition]. Shifts every later
     * item's position up by one so ordering stays dense and stable.
     */
    suspend fun addItemAfter(noteId: Long, afterPosition: Int, text: String = ""): Long {
        val existing = itemDao.observeByNote(noteId).first()
        val shifted = existing
            .filter { it.position > afterPosition }
            .map { it.copy(position = it.position + 1) }
        if (shifted.isNotEmpty()) itemDao.updateAll(shifted)
        touchNote(noteId)
        return itemDao.insert(
            ChecklistItem(noteId = noteId, text = text, position = afterPosition + 1),
        )
    }

    suspend fun updateItemText(item: ChecklistItem, text: String) {
        itemDao.update(item.copy(text = text))
        touchNote(item.noteId)
    }

    suspend fun setItemChecked(item: ChecklistItem, checked: Boolean) {
        itemDao.update(item.copy(checked = checked))
        touchNote(item.noteId)
    }

    suspend fun deleteItem(item: ChecklistItem) {
        itemDao.delete(item)
        touchNote(item.noteId)
    }

    private suspend fun touchNote(noteId: Long) {
        val current = noteDao.observeById(noteId).first() ?: return
        noteDao.update(current.copy(updatedAt = clock()))
    }
}
