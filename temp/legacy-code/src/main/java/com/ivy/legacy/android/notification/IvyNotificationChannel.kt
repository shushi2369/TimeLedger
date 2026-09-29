package com.ivy.wallet.android.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.ivy.ui.R

enum class IvyNotificationChannel(
    val channelId: String,
    val channelName: String,
    val description: String,
    val importance: Int = NotificationManager.IMPORTANCE_MAX,
    val bypassDnd: Boolean = true
) {
    TRANSACTION_REMINDER(
        channelId = "transaction_reminder",
        channelName = "记账提醒",
        description = "每天提醒你记录当天的交易。",
        importance = NotificationManager.IMPORTANCE_HIGH,
        bypassDnd = false
    ),
    PLANNED_PAYMENT(
        channelId = "planned_payment_reminder",
        channelName = "付款提醒",
        description = "房租、订阅等付款到期时自动提醒，点按即可去记账。",
        importance = NotificationManager.IMPORTANCE_HIGH,
        bypassDnd = false
    ),
    PAYMENT_DETECTED(
        channelId = "payment_detected_reminder",
        channelName = "付款后记账提醒",
        description = "检测到微信/支付宝/银行卡等付款通知后，提醒你立即记账。",
        importance = NotificationManager.IMPORTANCE_HIGH,
        bypassDnd = false
    );

    @SuppressLint("WrongConstant")
    fun create(context: Context): NotificationChannel {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        val colorPurple = ContextCompat.getColor(context, R.color.green)
        val channel = NotificationChannel(
            channelId,
            channelName,
            importance
        )
        channel.description = description
        channel.lightColor = colorPurple
        channel.enableLights(true)
        channel.enableVibration(true)
        channel.setBypassDnd(false)
        return channel
    }
}
