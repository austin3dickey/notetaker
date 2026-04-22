package com.notetaker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for reads/writes against notes and checklist items. The UI layer
 * never touches DAOs directly — this gives us one place to manage timestamps, enforce
 * invariants (e.g. the "can't delete a shared note" rule from the spec, coming in M5),
 * and swap storage later if needed.
 *
 * Every write that touches a checklist item runs inside a Room transaction so the item
 * mutation and the owning note's `updatedAt` bump happen atomically. That also closes
 * the read-then-write race on `nextPosition()` / `addItemAfter()` position shifts.
 */
class NoteRepository(
    private val db: NotetakerDatabase,
    private val noteDao: NoteDao,
    private val itemDao: ChecklistItemDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAllNotes(): Flow<List<Note>> = noteDao.observeAll()

    fun observeNote(noteId: Long): Flow<Note?> = noteDao.observeById(noteId)

    fun observeItems(noteId: Long): Flow<List<ChecklistItem>> = itemDao.observeByNote(noteId)

    suspend fun createNote(title: String = "", color: NoteColor = NoteColor.NONE): Long {
        val now = clock()
        return noteDao.insert(
            Note(title = title, color = color, createdAt = now, updatedAt = now),
        )
    }

    suspend fun updateNoteTitle(noteId: Long, title: String) = db.withTransaction {
        val current = noteDao.findById(noteId) ?: return@withTransaction
        noteDao.update(current.copy(title = title, updatedAt = clock()))
    }

    suspend fun setNoteColor(noteId: Long, color: NoteColor) = db.withTransaction {
        val current = noteDao.findById(noteId) ?: return@withTransaction
        noteDao.update(current.copy(color = color, updatedAt = clock()))
    }

    suspend fun setNoteArchived(noteId: Long, archived: Boolean) = db.withTransaction {
        val current = noteDao.findById(noteId) ?: return@withTransaction
        noteDao.update(current.copy(archived = archived, updatedAt = clock()))
    }

    suspend fun deleteNote(noteId: Long) = db.withTransaction {
        val current = noteDao.findById(noteId) ?: return@withTransaction
        noteDao.delete(current)
    }

    suspend fun appendItem(noteId: Long, text: String = ""): Long = db.withTransaction {
        val position = itemDao.nextPosition(noteId)
        val itemId = itemDao.insert(
            ChecklistItem(noteId = noteId, text = text, position = position),
        )
        touchNote(noteId)
        itemId
    }

    /**
     * Insert a new item immediately after the one at [afterPosition]. Shifts every later
     * item's position up by one so ordering stays dense and stable. The shift updates
     * happen from highest-position to lowest so the unique `(noteId, position)` index is
     * never temporarily violated mid-transaction.
     */
    suspend fun addItemAfter(noteId: Long, afterPosition: Int, text: String = ""): Long =
        db.withTransaction {
            val shifted = itemDao.getByNote(noteId)
                .asSequence()
                .filter { it.position > afterPosition }
                .map { it.copy(position = it.position + 1) }
                .sortedByDescending { it.position }
                .toList()
            if (shifted.isNotEmpty()) itemDao.updateAll(shifted)
            val itemId = itemDao.insert(
                ChecklistItem(noteId = noteId, text = text, position = afterPosition + 1),
            )
            touchNote(noteId)
            itemId
        }

    suspend fun updateItemText(item: ChecklistItem, text: String) = db.withTransaction {
        itemDao.update(item.copy(text = text))
        touchNote(item.noteId)
    }

    suspend fun setItemChecked(item: ChecklistItem, checked: Boolean) = db.withTransaction {
        itemDao.update(item.copy(checked = checked))
        touchNote(item.noteId)
    }

    suspend fun deleteItem(item: ChecklistItem) = db.withTransaction {
        itemDao.delete(item)
        touchNote(item.noteId)
    }

    private suspend fun touchNote(noteId: Long) {
        val current = noteDao.findById(noteId) ?: return
        noteDao.update(current.copy(updatedAt = clock()))
    }
}
