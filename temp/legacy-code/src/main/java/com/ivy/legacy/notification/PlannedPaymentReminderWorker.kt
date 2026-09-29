package com.ivy.wallet.domain.deprecated.logic.notification

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import com.ivy.data.model.IntervalType
import com.ivy.domain.AppStarter
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * 付款到期提醒：检查计划支付中"未来3天内到期（或30天内已到期仍未处理）"的条目，
 * 聚合为一条通知，点按通知回到应用记账。每次应用打开后也会即时检查一遍。
 */
@HiltWorker
class PlannedPaymentReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val notificationService: NotificationService,
    private val sharedPrefs: SharedPrefs,
    private val appStarter: AppStarter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        if (!sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)) {
            return@withContext Result.success()
        }

        val today = LocalDate.now()
        val messages = plannedPaymentRuleDao.findAll().mapNotNull { rule ->
            describeUpcoming(rule, today)
        }

        if (messages.isNotEmpty()) {
            val notification = notificationService
                .defaultIvyNotification(
                    channel = IvyNotificationChannel.PLANNED_PAYMENT,
                    priority = NotificationCompat.PRIORITY_HIGH
                )
                .setContentTitle("付款提醒 ⏰")
                .setContentText(messages.joinToString(separator = "；"))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(messages.joinToString(separator = "\n"))
                )
                .setContentIntent(
                    PendingIntent.getActivity(
                        applicationContext,
                        NOTIFICATION_ID,
                        appStarter.getRootIntent(),
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_UPDATE_CURRENT
                                or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)

            notificationService.showNotification(notification, NOTIFICATION_ID)
        }

        return@withContext Result.success()
    }

    /** 返回 null 表示该条目当前无需提醒。 */
    private fun describeUpcoming(rule: PlannedPaymentRuleEntity, today: LocalDate): String? {
        val dueDate = nextDueDate(rule, today) ?: return null
        val name = rule.title?.takeIf { it.isNotBlank() }
            ?: if (rule.type == TransactionType.EXPENSE) "一笔支出" else "一笔收入"

        val whenText = when {
            rule.oneTime && dueDate.isBefore(today) -> "已到期"
            dueDate == today -> "今天到期"
            dueDate == today.plusDays(1) -> "明天到期"
            else -> "${dueDate.monthValue}月${dueDate.dayOfMonth}日到期"
        }
        return "「$name」$whenText，${formatAmount(rule.amount)}"
    }

    private fun nextDueDate(rule: PlannedPaymentRuleEntity, today: LocalDate): LocalDate? {
        val start = rule.startDate
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
            ?: return null

        if (rule.oneTime) {
            val inWindow = !start.isBefore(today.minusDays(OVERDUE_LOOKBACK_DAYS)) &&
                    !start.isAfter(today.plusDays(ADVANCE_DAYS))
            return if (inWindow) start else null
        }

        val step = rule.intervalN?.takeIf { it > 0 } ?: 1
        var date = start
        var iterations = 0
        while (date.isBefore(today) && iterations < 1000) {
            date = when (rule.intervalType) {
                IntervalType.DAY -> date.plusDays(step.toLong())
                IntervalType.WEEK -> date.plusWeeks(step.toLong())
                IntervalType.MONTH -> date.plusMonths(step.toLong())
                IntervalType.YEAR -> date.plusYears(step.toLong())
                null -> return null
            }
            iterations++
        }
        return if (!date.isAfter(today.plusDays(ADVANCE_DAYS))) date else null
    }

    private fun formatAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) {
            String.format(Locale.CHINA, "￥%,.0f", amount)
        } else {
            String.format(Locale.CHINA, "￥%,.2f", amount)
        }

    companion object {
        private const val ADVANCE_DAYS = 3L
        private const val OVERDUE_LOOKBACK_DAYS = 30L
        const val NOTIFICATION_ID = 2001
    }
}
