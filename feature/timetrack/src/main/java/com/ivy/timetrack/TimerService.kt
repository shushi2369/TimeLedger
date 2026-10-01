package com.ivy.timetrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 通知栏计时常驻：开始计时时前置显示 Chronometer 通知（系统自动走秒，零轮询），
 * 停止/暂停时撤下。解决"忘记停止计时"与"App 在后台看不到在计时"两个痛点。
 */
class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val startedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
                startForeground(NOTIFICATION_ID, buildNotification(startedAt))
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(startedAt: Long): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(getString(R.string.time_tracking_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(startedAt)
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "time_tracking"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_START = "com.ivy.timetrack.START"
        private const val ACTION_STOP = "com.ivy.timetrack.STOP"
        private const val EXTRA_STARTED_AT = "started_at"

        fun start(context: Context, startedAt: Long) {
            ensureChannel(context)
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STARTED_AT, startedAt)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TimerService::class.java).apply { action = ACTION_STOP }
            )
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.time_tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
    }
}
