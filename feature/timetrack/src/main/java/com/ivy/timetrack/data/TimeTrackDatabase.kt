package com.ivy.timetrack.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 时间管理独立小库（timetrack.db），与主账本库（ivy-wallet.db）完全隔离：
 * 不需要主库升级/迁移，也保证时间数据不污染记账数据。
 */
@Database(
    entities = [TimeActivityEntity::class, TimeEntryEntity::class],
    version = 2,
)
abstract class TimeTrackDatabase : RoomDatabase() {
    abstract fun timeActivityDao(): TimeActivityDao
    abstract fun timeEntryDao(): TimeEntryDao

    companion object {
        const val DB_NAME = "timetrack.db"

        /** v2：计时暂停（entry.pausedMs/pausedAt）+ 活动每日目标（activity.dailyGoalMin）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE time_entry ADD COLUMN pausedMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE time_entry ADD COLUMN pausedAt INTEGER")
                db.execSQL("ALTER TABLE time_activity ADD COLUMN dailyGoalMin INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
