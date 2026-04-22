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

    // Item mutations are keyed by [itemId], not a full [ChecklistItem]. Between an
    // undo click and Room's emission of the restored state, the UI holds stale item
    // references from the pre-restore world. Column-targeted UPDATEs and a by-id DELETE
    // let those callbacks land cleanly on the restored row: we only carry the id and the
    // new value across, so the stale fields on the held ChecklistItem can't clobber the
    // restored ones.

    suspend fun updateItemText(itemId: Long, text: String) = db.withTransaction {
        val current = itemDao.findById(itemId) ?: return@withTransaction
        itemDao.updateText(itemId, text)
        touchNote(current.noteId)
    }

    suspend fun setItemChecked(itemId: Long, checked: Boolean) = db.withTransaction {
        val current = itemDao.findById(itemId) ?: return@withTransaction
        itemDao.updateChecked(itemId, checked)
        touchNote(current.noteId)
    }

    suspend fun deleteItem(itemId: Long) = db.withTransaction {
        val current = itemDao.findById(itemId) ?: return@withTransaction
        itemDao.deleteById(itemId)
        touchNote(current.noteId)
    }

    /**
     * Atomic split: truncate the item with [itemId] to [keepText] and insert a new row
     * with [remainderText] immediately after it. Used by the editor's "Enter splits the
     * row" path so a single keypress produces one write (and one undo entry) even when
     * the cursor was in the middle of the original text. Shifts later positions up by
     * one in descending order, matching the invariant in [addItemAfter]. No-op if the
     * item has since been deleted.
     */
    suspend fun splitItem(
        itemId: Long,
        keepText: String,
        remainderText: String,
    ): Long? = db.withTransaction {
        val current = itemDao.findById(itemId) ?: return@withTransaction null
        itemDao.updateText(itemId, keepText)
        val shifted = itemDao.getByNote(current.noteId)
            .asSequence()
            .filter { it.position > current.position }
            .map { it.copy(position = it.position + 1) }
            .sortedByDescending { it.position }
            .toList()
        if (shifted.isNotEmpty()) itemDao.updateAll(shifted)
        val newId = itemDao.insert(
            ChecklistItem(
                noteId = current.noteId,
                text = remainderText,
                position = current.position + 1,
            ),
        )
        touchNote(current.noteId)
        newId
    }

    /**
     * Overwrites a note's editable content with [title], [color], and [items] in one
     * transaction. Used by the editor's undo/redo stack to restore a prior state. Items
     * are reinserted with their snapshotted IDs so row identity survives the restore —
     * this matters because the UI may still hold `ChecklistItem` references from before
     * the restore, and we want those references to keep pointing at a real row. No-op
     * if the note has since been deleted, so a restore that lands after the note is
     * gone silently drops instead of resurrecting it.
     *
     * `ChecklistItem.noteId` has `ForeignKey.CASCADE` on note delete, so
     * [ChecklistItemDao.deleteAllForNote] is safe to run inside the same transaction as
     * the note lookup: if the note existed at the start, it still exists at commit.
     */
    suspend fun replaceNoteContents(
        noteId: Long,
        title: String,
        color: NoteColor,
        items: List<ItemSnapshot>,
    ) = db.withTransaction {
        val current = noteDao.findById(noteId) ?: return@withTransaction
        itemDao.deleteAllForNote(noteId)
        items.forEach { snap ->
            itemDao.insert(
                ChecklistItem(
                    id = snap.id,
                    noteId = noteId,
                    text = snap.text,
                    checked = snap.checked,
                    position = snap.position,
                    indent = snap.indent,
                ),
            )
        }
        noteDao.update(current.copy(title = title, color = color, updatedAt = clock()))
    }

    private suspend fun touchNote(noteId: Long) {
        val current = noteDao.findById(noteId) ?: return
        noteDao.update(current.copy(updatedAt = clock()))
    }
}
