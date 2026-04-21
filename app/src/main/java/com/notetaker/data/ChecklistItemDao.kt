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
}
