package com.ivy.wallet.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ivy.base.model.TransactionType
import com.ivy.domain.AppStarter
import com.ivy.wallet.BuildConfig
import com.ivy.wallet.RootActivity
import com.ivy.wallet.RootViewModel
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * fork 增补：统一的"付款后记账提醒"发送器。
 * 被 [PaymentNotificationListenerService]（真实付款通知）和
 * [SimulatePaymentReceiver]（debug 模拟付款）共用。
 */
class PaymentReminderSender @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
) {
    fun send(
        type: TransactionType,
        amountText: String,
        label: String,
    ) {
        val direction = if (type == TransactionType.INCOME) "收入" else "支出"
        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.PAYMENT_DETECTED,
                priority = NotificationCompat.PRIORITY_MAX
            )
            .setContentTitle("付款后提醒你记账 💰")
            .setContentText("检测到$direction $amountText（$label），点按去记账")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("检测到$direction $amountText（$label），点按去记账")
            )
            .setContentIntent(pendingIntent(type))
            .setAutoCancel(true)

        notificationService.showNotification(notification, NOTIFICATION_ID)
    }

    private fun pendingIntent(type: TransactionType): PendingIntent {
        val openApp = Intent(context, RootActivity::class.java).apply {
            putExtra(RootViewModel.EXTRA_ADD_TRANSACTION_TYPE, type.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openApp,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_UPDATE_CURRENT
                    or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val NOTIFICATION_ID = 3001
    }
}
