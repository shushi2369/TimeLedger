package com.ivy.timetrack.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ivy.timetrack.data.TodoEntity

/**
 * 备忘录提醒闹钟：AlarmManager 精确闹钟（Android 12+ 无权限时降级 ±10 分钟窗口）。
 * requestCode 用 todoId.hashCode()，增/删/改均可精确 cancel。
 */
object TodoAlarmScheduler {

    fun schedule(context: Context, todo: TodoEntity) {
        cancel(context, todo.id)
        val remindAt = todo.remindAt ?: return
        if (todo.done || remindAt <= System.currentTimeMillis()) return

        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, todo.id, todo.content)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, remindAt, 10 * 60_000L, pi)
        }
    }

    fun cancel(context: Context, todoId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, todoId, ""))
    }

    private fun pendingIntent(context: Context, todoId: String, content: String): PendingIntent {
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            action = TodoAlarmReceiver.ACTION_FIRE
            putExtra(TodoAlarmReceiver.EXTRA_ID, todoId)
            putExtra(TodoAlarmReceiver.EXTRA_CONTENT, content)
        }
        return PendingIntent.getBroadcast(
            context,
            todoId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
