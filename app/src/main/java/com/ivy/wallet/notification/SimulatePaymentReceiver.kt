package com.ivy.wallet.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivy.base.model.TransactionType
import com.ivy.wallet.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * fork 增补（仅 DEBUG 生效）：模拟付款通知，用于在真机/模拟器上验证
 * "付款后提醒记账"全流程，无需安装微信/支付宝。
 *
 * adb 示例：
 * adb shell am broadcast -a com.ivy.wallet.debug.SIMULATE_PAYMENT \
 *   --es amount "35.00" --es label "微信支付" --ez income false
 */
@AndroidEntryPoint
class SimulatePaymentReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sender: PaymentReminderSender

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        if (intent.action != ACTION_SIMULATE_PAYMENT) return

        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: "￥35.00"
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "模拟付款"
        val income = intent.getBooleanExtra(EXTRA_INCOME, false)

        sender.send(
            type = if (income) TransactionType.INCOME else TransactionType.EXPENSE,
            amountText = if (amount.startsWith("￥") || amount.startsWith("¥")) amount else "￥$amount",
            label = label
        )
    }

    companion object {
        const val ACTION_SIMULATE_PAYMENT = "com.ivy.wallet.debug.SIMULATE_PAYMENT"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_LABEL = "label"
        const val EXTRA_INCOME = "income"
    }
}
