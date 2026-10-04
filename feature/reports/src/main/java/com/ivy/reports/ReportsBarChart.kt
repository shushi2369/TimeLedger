package com.ivy.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.legacy.Transaction
import com.ivy.base.legacy.TransactionHistoryItem
import com.ivy.base.model.TransactionType
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.ui.theme.Green
import kotlinx.collections.immutable.ImmutableList
import java.time.ZoneId
import kotlin.math.max

/**
 * fork 增补：报表页"每日收入/支出"双柱状图（2026-09-27）。
 * 数据来自已按日期分组的交易历史，绿柱=收入，深柱=支出，按当日金额归一化高度。
 */
@Composable
fun ReportsBarChart(
    history: ImmutableList<TransactionHistoryItem>,
    modifier: Modifier = Modifier,
    monthlyGranularity: Boolean = false,
) {
    val days = if (monthlyGranularity) aggregateByMonth(history) else aggregateByDay(history)
    if (days.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "每日收入 / 支出",
                style = UI.typo.b2.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.pureInverse
                )
            )

            Spacer(Modifier.weight(1f))

            LegendDot(color = Green, label = "收入")
            Spacer(Modifier.width(12.dp))
            LegendDot(color = UI.colors.pureInverse, label = "支出")
        }

        Spacer(Modifier.height(12.dp))

        val maxAmount = days.maxOf { max(it.second.first, it.second.second) }
            .coerceAtLeast(1.0)
        val expenseBarColor = UI.colors.pureInverse

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val groupWidth = size.width / days.size
            val barWidth = minOf(groupWidth * 0.30f, 24.dp.toPx())
            val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            val usableHeight = size.height - 4.dp.toPx()

            days.forEachIndexed { index, entry ->
                val (income, expense) = entry.second
                val groupStart = groupWidth * index
                val groupCenter = groupStart + groupWidth / 2

                if (income > 0) {
                    val h = (income / maxAmount * usableHeight).toFloat().coerceAtLeast(4f)
                    drawRoundRect(
                        color = Green,
                        topLeft = Offset(groupCenter - barWidth - 2.dp.toPx(), size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = corner
                    )
                }
                if (expense > 0) {
                    val h = (expense / maxAmount * usableHeight).toFloat().coerceAtLeast(4f)
                    drawRoundRect(
                        color = expenseBarColor,
                        topLeft = Offset(groupCenter + 2.dp.toPx(), size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = corner
                    )
                }
            }
        }

        // x 轴日期标签：与柱组等宽对齐；天数多时隔 5 天标一个，首尾必标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        ) {
            days.forEachIndexed { index, entry ->
                val show = days.size <= 13 ||
                        index % 5 == 0 ||
                        index == days.size - 1
                Text(
                    text = if (show)
                        "${if (monthlyGranularity) entry.first.monthValue else entry.first.dayOfMonth}"
                    else "",
                    style = UI.typo.c.style(
                        color = UI.colors.pureInverse.copy(alpha = 0.4f)
                    ).copy(fontSize = 9.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(
    color: androidx.compose.ui.graphics.Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = UI.typo.c.style(color = UI.colors.pureInverse)
        )
    }
}

private fun aggregateByDay(
    history: ImmutableList<TransactionHistoryItem>
): List<Pair<java.time.LocalDate, Pair<Double, Double>>> {
    val map = sortedMapOf<java.time.LocalDate, Pair<Double, Double>>()
    history.forEach { item ->
        if (item is Transaction && item.type != TransactionType.TRANSFER) {
            val day = item.date
                ?: item.dateTime?.atZone(ZoneId.systemDefault())?.toLocalDate()
                ?: return@forEach
            val amount = item.amount.toDouble()
            val current = map[day] ?: Pair(0.0, 0.0)
            map[day] = when (item.type) {
                TransactionType.INCOME -> Pair(current.first + amount, current.second)
                else -> Pair(current.first, current.second + amount)
            }
        } else if (item is com.ivy.wallet.domain.data.TransactionHistoryDateDivider) {
            // 日期分隔条目只用于列表展示，不参与聚合
        }
    }
    return map.map { it.key to it.value }
}

/** 按年视图：按自然月聚合（12 根柱）。 */
private fun aggregateByMonth(
    history: ImmutableList<TransactionHistoryItem>
): List<Pair<java.time.LocalDate, Pair<Double, Double>>> {
    val map = sortedMapOf<java.time.LocalDate, Pair<Double, Double>>()
    history.forEach { item ->
        if (item is Transaction && item.type != TransactionType.TRANSFER) {
            val date = item.date
                ?: item.dateTime?.atZone(ZoneId.systemDefault())?.toLocalDate()
                ?: return@forEach
            val monthStart = date.withDayOfMonth(1)
            val amount = item.amount.toDouble()
            val current = map[monthStart] ?: Pair(0.0, 0.0)
            map[monthStart] = when (item.type) {
                TransactionType.INCOME -> Pair(current.first + amount, current.second)
                else -> Pair(current.first, current.second + amount)
            }
        }
    }
    return map.map { it.key to it.value }
}
