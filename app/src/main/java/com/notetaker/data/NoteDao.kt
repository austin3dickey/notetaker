package com.notetaker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE archived = 0 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<Note?>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}
