package com.notetaker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistItemDao {
    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position ASC")
    fun observeByNote(noteId: Long): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position ASC")
    suspend fun getByNote(noteId: Long): List<ChecklistItem>

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun findById(id: Long): ChecklistItem?

    @Insert
    suspend fun insert(item: ChecklistItem): Long

    @Update
    suspend fun update(item: ChecklistItem)

    @Update
    suspend fun updateAll(items: List<ChecklistItem>)

    @Delete
    suspend fun delete(item: ChecklistItem)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM checklist_items WHERE noteId = :noteId")
    suspend fun nextPosition(noteId: Long): Int

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId")
    suspend fun deleteAllForNote(noteId: Long)

    // Column-targeted updates so callers that only know the id + new value don't need
    // to ferry a full ChecklistItem across the UI/VM boundary. Critical for undo/redo:
    // the UI can hold stale ChecklistItem objects while a restore is in flight, and
    // `update(item.copy(...))` would overwrite other columns with those stale values.
    @Query("UPDATE checklist_items SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String): Int

    @Query("UPDATE checklist_items SET checked = :checked WHERE id = :id")
    suspend fun updateChecked(id: Long, checked: Boolean): Int

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
