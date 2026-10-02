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
    entities = [TimeActivityEntity::class, TimeEntryEntity::class, TodoEntity::class],
    version = 3,
)
abstract class TimeTrackDatabase : RoomDatabase() {
    abstract fun timeActivityDao(): TimeActivityDao
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun todoDao(): TodoDao

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

        /** v3：备忘录 TODO（红绿点/完成/提醒）。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS todo (" +
                            "id TEXT NOT NULL PRIMARY KEY, " +
                            "content TEXT NOT NULL, " +
                            "done INTEGER NOT NULL DEFAULT 0, " +
                            "doneAt INTEGER, " +
                            "remindAt INTEGER, " +
                            "createdAt INTEGER NOT NULL)"
                )
            }
        }
    }
}
