package com.ivy.timetrack.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一段时间记录，对应技术指导文档 §3.3 time_entry。
 * endedAt == null 表示正在计时中。
 * 时间戳均为 epochMillis，本地时区语义由查询侧用当日边界保证。
 */
@Entity(
    tableName = "time_entry",
    indices = [Index("activityId"), Index("startedAt")],
)
data class TimeEntryEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val note: String? = null,
) {
    fun durationMs(now: Long): Long = (endedAt ?: now) - startedAt
}
