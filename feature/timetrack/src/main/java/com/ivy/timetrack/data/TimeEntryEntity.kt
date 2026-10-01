package com.ivy.timetrack.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一段时间记录，对应技术指导文档 §3.3 time_entry。
 * endedAt == null 表示正在计时中。
 * pausedMs/pausedAt 支持暂停：暂停时 pausedAt=now（该段不计时长），继续时把段长累入 pausedMs。
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
    val pausedMs: Long = 0,
    val pausedAt: Long? = null,
) {
    /** 有效计时时长（不含暂停中的段）。 */
    fun durationMs(now: Long): Long {
        val end = endedAt ?: now
        val runningPause = pausedAt?.let { now - it } ?: 0L
        return (end - startedAt - pausedMs - runningPause).coerceAtLeast(0)
    }
}
