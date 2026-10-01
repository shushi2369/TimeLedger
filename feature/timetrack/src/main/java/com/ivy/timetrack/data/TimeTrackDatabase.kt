package com.ivy.timetrack.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 时间管理独立小库（timetrack.db），与主账本库（ivy-wallet.db）完全隔离：
 * 不需要主库升级/迁移，也保证时间数据不污染记账数据。
 */
@Database(
    entities = [TimeActivityEntity::class, TimeEntryEntity::class],
    version = 1,
)
abstract class TimeTrackDatabase : RoomDatabase() {
    abstract fun timeActivityDao(): TimeActivityDao
    abstract fun timeEntryDao(): TimeEntryDao

    companion object {
        const val DB_NAME = "timetrack.db"
    }
}
