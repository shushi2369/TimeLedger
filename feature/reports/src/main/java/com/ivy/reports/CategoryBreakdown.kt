package com.ivy.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.base.legacy.Transaction
import com.ivy.base.legacy.TransactionHistoryItem
import com.ivy.base.model.TransactionType
import com.ivy.data.model.Category
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.arkui.ArkStaggeredIn
import com.ivy.legacy.utils.format
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.ImmutableList
import java.time.LocalDate
import java.util.UUID

/**
 * 分类占比（环形扇形图 + 明细列表，收入/支出可切换）。
 * 数据来自报表页已筛选的交易历史。
 */
@Composable
internal fun CategoryBreakdownSection(
    history: ImmutableList<TransactionHistoryItem>,
    categories: ImmutableList<Category>,
    currency: String,
) {
    var incomeMode by remember { mutableStateOf(false) }

    val shares = remember(history, categories, incomeMode) {
        categoryShares(history, categories, if (incomeMode) TransactionType.INCOME else TransactionType.EXPENSE)
    }
    if (shares.isEmpty()) return

    val total = shares.sumOf { it.amount }
    val accent = if (incomeMode) UI.colors.green else UI.colors.primary

    ArkStaggeredIn(index = 1) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(UI.shapes.r4)
                .background(UI.colors.pure)
                .padding(vertical = 16.dp)
        ) {
            // 标题 + 收支切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "分类占比",
                    style = UI.typo.b2.style(
                        fontWeight = FontWeight.ExtraBold,
                        color = UI.colors.pureInverse
                    )
                )
                Spacer(Modifier.weight(1f))

                Text(
                    text = "支出",
                    style = UI.typo.c.style(
                        fontWeight = if (!incomeMode) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (!incomeMode) accent else UI.colors.gray
                    ),
                    modifier = Modifier
                        .clip(UI.shapes.rFull)
                        .background(if (!incomeMode) accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { incomeMode = false }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "收入",
                    style = UI.typo.c.style(
                        fontWeight = if (incomeMode) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (incomeMode) accent else UI.colors.gray
                    ),
                    modifier = Modifier
                        .clip(UI.shapes.rFull)
                        .background(if (incomeMode) accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { incomeMode = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // 环形扇形图（中心 = 总额）
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    var startAngle = -90f
                    shares.forEach { share ->
                        val sweep = (share.pct / 100f * 360f).coerceAtLeast(0.5f)
                        drawArc(
                            color = share.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 34.dp.toPx(), cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (incomeMode) "收入" else "支出",
                        style = UI.typo.c.style(color = UI.colors.gray)
                    )
                    Text(
                        text = total.format(currency),
                        style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 明细列表：色点 + 分类名 + 百分比 + 金额
            shares.forEach { share ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(share.color)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = share.name,
                        style = UI.typo.b2.style(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        text = "${"%.1f".format(share.pct)}%",
                        style = UI.typo.c.style(
                            fontWeight = FontWeight.Bold,
                            color = UI.colors.gray
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = share.amount.format(currency),
                        style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

internal data class CategoryShare(
    val name: String,
    val color: Color,
    val amount: Double,
    val pct: Float,
)

internal fun categoryShares(
    history: ImmutableList<TransactionHistoryItem>,
    categories: ImmutableList<Category>,
    type: TransactionType,
): List<CategoryShare> {
    val byId = categories.associateBy { it.id.value }
    val map = HashMap<UUID, Pair<Double, Color>>()
    history.forEach { item ->
        if (item is Transaction && item.type == type) {
            val cat = item.categoryId?.let { byId[it] }
            val key = item.categoryId ?: UUID(0, 0)
            val prev = map.getOrPut(key) {
                Pair(0.0, cat?.color?.value?.toComposeColor() ?: Color(0xFF9E9E9E))
            }
            map[key] = Pair(prev.first + item.amount.toDouble(), prev.second)
        }
    }
    val total = map.values.sumOf { it.first }
    if (total <= 0.0) return emptyList()
    return map.map { (key, agg) ->
        val cat = byId[key]
        CategoryShare(
            name = cat?.name?.value ?: "未分类",
            color = agg.second,
            amount = agg.first,
            pct = (agg.first / total * 100).toFloat(),
        )
    }.sortedByDescending { it.amount }
}
