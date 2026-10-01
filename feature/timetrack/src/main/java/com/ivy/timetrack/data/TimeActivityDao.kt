package com.ivy.timetrack.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TimeActivityDao {
    @Query("SELECT * FROM time_activity WHERE archived = 0 ORDER BY orderNum ASC")
    suspend fun findAll(): List<TimeActivityEntity>

    @Query("SELECT COUNT(*) FROM time_activity")
    suspend fun count(): Int

    @Query("SELECT MAX(orderNum) FROM time_activity WHERE archived = 0")
    suspend fun maxOrder(): Double?

    @Upsert
    suspend fun save(activity: TimeActivityEntity)

    @Query("UPDATE time_activity SET archived = 1 WHERE id = :id")
    suspend fun archive(id: String)
}
