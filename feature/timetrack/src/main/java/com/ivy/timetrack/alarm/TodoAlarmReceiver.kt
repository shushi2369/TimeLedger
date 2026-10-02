package com.ivy.timetrack.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import com.ivy.timetrack.R
import com.ivy.timetrack.data.TodoDao
import com.ivy.timetrack.data.TimeTrackDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 备忘录提醒触发：到点查库确认该条仍未完成才发通知（完成过的到点不响）。
 * 通知点击打开 App（主界面）。
 */
@AndroidEntryPoint
class TodoAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var todoDao: TodoDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todo = todoDao.findById(id)
                android.util.Log.i("TodoAlarm", "fired id=$id found=${todo != null} done=${todo?.done}")
                if (todo != null && !todo.done) {
                    fireNotification(context, todo.id, todo.content)
                    android.util.Log.i("TodoAlarm", "notification posted")
                }
            } catch (e: Exception) {
                android.util.Log.e("TodoAlarm", "failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun fireNotification(context: Context, todoId: String, content: String) {
        ensureChannel(context)
        val tapIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val tap = PendingIntent.getActivity(
            context,
            todoId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(context.getString(R.string.todo_notification_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        NotificationManagerCompat.from(context).notify(todoId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "todo_reminder"
        const val ACTION_FIRE = "com.ivy.timetrack.TODO_FIRE"
        const val EXTRA_ID = "todo_id"
        const val EXTRA_CONTENT = "todo_content"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.todo_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
            manager.createNotificationChannel(channel)
        }
    }
}
