package com.ivy.wallet.domain.deprecated.logic.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Deprecated("Use FP style, look into `domain.fp` package")
class PlannedPaymentReminderLogic @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
) {
    /**
     * 每次打开应用后 10 秒做一次即时检查（也是调试时快速看到通知的入口）。
     */
    fun scheduleOnAppStart() {
        val request = OneTimeWorkRequestBuilder<PlannedPaymentReminderWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()

        WorkManager
            .getInstance(appContext)
            .enqueueUniqueWork(
                UNIQUE_WORK_ON_APP_START,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    /**
     * 每天早上 9 点定时检查一次到期付款。
     */
    fun scheduleDaily() {
        val now = LocalDateTime.now()
        var nextRun = now.withHour(REMIND_HOUR).withMinute(0).withSecond(0)
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }
        val initialDelay = Duration.between(now, nextRun)

        val request = PeriodicWorkRequestBuilder<PlannedPaymentReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.seconds, TimeUnit.SECONDS)
            .build()

        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    companion object {
        private const val UNIQUE_WORK_ON_APP_START = "planned_payment_reminder_on_app_start"
        private const val UNIQUE_WORK_DAILY = "planned_payment_reminder_daily"
        private const val REMIND_HOUR = 9
    }
}
