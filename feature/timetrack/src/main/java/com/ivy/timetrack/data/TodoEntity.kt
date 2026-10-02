package com.ivy.timetrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 备忘录条目（时间 Tab TODO 模块）。
 * remindAt = null 表示不提醒（默认）；done = true 后沉到已完成折叠区。
 */
@Entity(tableName = "todo")
data class TodoEntity(
    @PrimaryKey val id: String,
    val content: String,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val remindAt: Long? = null,
    val createdAt: Long,
)
