package com.ivy.wallet

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ivy.base.legacy.appContext
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

/**
 * Created by iliyan on 24.02.18.
 */
@HiltAndroidApp
class IvyAndroidApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationService: NotificationService

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appContext = this

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }

        maybePromptNotificationAccess()
    }

    /**
     * fork 增补：未授予"通知使用权"时，每 3 天提醒一次去开启付款后记账提醒。
     */
    private fun maybePromptNotificationAccess() {
        try {
            if (NotificationManagerCompat.getEnabledListenerPackages(this)
                    .contains(packageName)
            ) return

            val prefs = getSharedPreferences("payment_listener_prefs", MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - prefs.getLong(KEY_LAST_PROMPT, 0L) < PROMPT_INTERVAL_MS) return
            prefs.edit().putLong(KEY_LAST_PROMPT, now).apply()

            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendingIntent = PendingIntent.getActivity(
                this,
                PROMPT_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_UPDATE_CURRENT
                        or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = notificationService
                .defaultIvyNotification(
                    channel = IvyNotificationChannel.PAYMENT_DETECTED,
                    priority = androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
                )
                .setContentTitle("开启付款后自动提醒记账")
                .setContentText("授予通知使用权后，付款后自动提醒你记账")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            notificationService.showNotification(notification, PROMPT_NOTIFICATION_ID)
        } catch (e: Exception) {
            Timber.w(e, "notification access prompt failed")
        }
    }

    companion object {
        private const val KEY_LAST_PROMPT = "last_notification_access_prompt"
        private const val PROMPT_INTERVAL_MS = 3 * 24 * 60 * 60 * 1000L
        private const val PROMPT_NOTIFICATION_ID = 3002
    }
}
