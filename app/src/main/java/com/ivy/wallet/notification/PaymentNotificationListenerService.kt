package com.ivy.wallet.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.ivy.base.model.TransactionType
import com.ivy.domain.AppStarter
import com.ivy.wallet.BuildConfig
import com.ivy.wallet.RootActivity
import com.ivy.wallet.RootViewModel
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/**
 * fork 增补（2026-09-27）：付款后自动提醒记账。
 *
 * 监听微信/支付宝/云闪付/银行短信等应用的付款通知，识别金额与收支方向，
 * 弹出"付款后记账提醒"，点按直达对应类型的记账页。
 * 需要用户在系统设置中授予本应用"通知使用权"（NotificationListenerService）。
 */
@AndroidEntryPoint
class PaymentNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    lateinit var appStarter: AppStarter

    @Inject
    lateinit var reminderSender: PaymentReminderSender

    private val seenKeys = object : LinkedHashMap<String, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
            size > 50
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (pkg !in watchedPackages()) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        ).joinToString(separator = " ")
        val full = "$title $text"
        if (full.isBlank()) return

        // 同一条通知更新时去重，避免重复提醒
        val dedupeKey = "${sbn.key}:$full"
        synchronized(seenKeys) {
            val now = System.currentTimeMillis()
            val last = seenKeys[dedupeKey]
            if (last != null && now - last < DEDUPE_WINDOW_MS) return
            seenKeys[dedupeKey] = now
        }

        Timber.tag(TAG).i("payment notification from %s: %s", pkg, full)

        val isIncome = incomeKeywords.any { full.contains(it) }
        val isExpense = expenseKeywords.any { full.contains(it) }
        if (!isIncome && !isExpense) return

        val amount = extractAmount(full) ?: return

        showReminder(
            type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
            amountText = amount,
            label = appLabel(pkg)
        )
    }

    private fun showReminder(
        type: TransactionType,
        amountText: String,
        label: String,
    ) {
        reminderSender.send(type = type, amountText = amountText, label = label)
    }

    private fun extractAmount(text: String): String? {
        for (regex in amountRegexes) {
            val match = regex.find(text) ?: continue
            val value = match.groupValues[1].replace(",", "")
            val normalized = value.toDoubleOrNull() ?: continue
            if (normalized > 0) {
                return if (normalized % 1.0 == 0.0) {
                    String.format(Locale.CHINA, "￥%,.0f", normalized)
                } else {
                    String.format(Locale.CHINA, "￥%,.2f", normalized)
                }
            }
        }
        return null
    }

    private fun watchedPackages(): Set<String> =
        if (BuildConfig.DEBUG) watchedBase + debugTestPackages else watchedBase

    private fun appLabel(pkg: String): String = when (pkg) {
        "com.tencent.mm" -> "微信支付"
        "com.eg.android.AlipayGphone" -> "支付宝"
        "com.unionpay" -> "云闪付"
        "com.android.mms", "com.google.android.apps.messaging" -> "银行短信"
        "android", "com.android.shell" -> "模拟付款"
        else -> pkg
    }

    companion object {
        private const val TAG = "PaymentListener"
        private const val DEDUPE_WINDOW_MS = 10 * 60 * 1000L
        const val NOTIFICATION_ID = 3001

        private val watchedBase = setOf(
            "com.tencent.mm",                      // 微信
            "com.eg.android.AlipayGphone",         // 支付宝
            "com.unionpay",                        // 云闪付
            "com.android.mms",                     // 系统短信（银行扣款短信）
            "com.google.android.apps.messaging"    // Google 短信
        )

        // 仅 debug 构建监听这些包，便于用 adb cmd notification post 模拟付款通知
        private val debugTestPackages = setOf("android", "com.android.shell")

        private val expenseKeywords = listOf(
            "支付成功", "付款成功", "已支付", "消费", "扣款", "支出", "交易成功", "转账成功", "付款"
        )
        private val incomeKeywords = listOf("收款", "到账", "入账", "收入")

        private val amountRegexes = listOf(
            Regex("[￥¥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"),
            Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元"),
            Regex("(?:金额|支付|消费|付款|扣款|支出)[:：]?\\s*[¥￥]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
        )
    }
}
