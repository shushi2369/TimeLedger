package com.ivy.timetrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 时间活动（如"工作""学习"），对应技术指导文档 §3.3 time_activity。
 * colorArgb 存 ARGB Long，便于 Compose 直接 Color(value)。
 */
@Entity(tableName = "time_activity")
data class TimeActivityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Long,
    val orderNum: Double,
    val archived: Boolean = false,
)
