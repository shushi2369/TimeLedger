package com.ivy.timetrack.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo WHERE done = 0 ORDER BY createdAt ASC")
    suspend fun findAllActive(): List<TodoEntity>

    @Query("SELECT * FROM todo WHERE done = 1 ORDER BY doneAt DESC")
    suspend fun findAllDone(): List<TodoEntity>

    @Query("SELECT * FROM todo WHERE done = 0 AND remindAt IS NOT NULL")
    suspend fun findAllWithReminder(): List<TodoEntity>

    @Query("SELECT * FROM todo WHERE id = :id")
    suspend fun findById(id: String): TodoEntity?

    @Upsert
    suspend fun save(todo: TodoEntity)

    @Query("DELETE FROM todo WHERE id = :id")
    suspend fun deleteById(id: String)
}
