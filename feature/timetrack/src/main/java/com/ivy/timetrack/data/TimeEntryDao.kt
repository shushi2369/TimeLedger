package com.ivy.timetrack.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** Room 投影：按活动聚合的总时长。 */
data class ActivityTotalRow(
    val activityId: String,
    val totalMs: Long,
)

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entry WHERE endedAt IS NULL LIMIT 1")
    suspend fun findRunning(): TimeEntryEntity?

    @Query(
        "SELECT * FROM time_entry WHERE startedAt >= :from AND startedAt < :to " +
                "ORDER BY startedAt DESC"
    )
    suspend fun findBetween(from: Long, to: Long): List<TimeEntryEntity>

    @Query(
        "SELECT activityId, SUM(endedAt - startedAt - pausedMs) AS totalMs FROM time_entry " +
                "WHERE endedAt IS NOT NULL AND startedAt >= :since " +
                "GROUP BY activityId ORDER BY totalMs DESC"
    )
    suspend fun totalsSince(since: Long): List<ActivityTotalRow>

    @Upsert
    suspend fun save(entry: TimeEntryEntity)

    @Query("DELETE FROM time_entry WHERE id = :id")
    suspend fun deleteById(id: String)
}
