package com.ivy.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.data.model.Category
import com.ivy.navigation.QuickEntryScreen
import com.ivy.navigation.navigation
import com.ivy.legacy.utils.currencyDisplay
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.components.getCustomIconIdS
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.toComposeColor
import com.ivy.ui.R
import timber.log.Timber
import java.text.DecimalFormat
import java.util.UUID

/**
 * fork 增补（2026-09-28）：鲨鱼式快速记账页。
 * 顶部类别网格（含"未分类"），底部加减计算键盘；
 * 表达式结果 ≥0 记支出、<0 记收入（取绝对值）；备注可选；无账户概念。
 */
@Composable
fun QuickEntryScreen(screen: QuickEntryScreen) {
    val viewModel: QuickEntryViewModel = viewModel()
    val state = viewModel.uiState()
    val nav = navigation()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .systemBarsPadding()
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.ivy.legacy.arkui.ArkBilingualTitle(cn = "记一笔", en = "ADD ENTRY")
            Spacer(Modifier.weight(1f))
            Text(
                modifier = Modifier.clickable { nav.back() },
                text = "取消",
                style = UI.typo.b2.style(color = UI.colors.gray)
            )
        }

        // 类别网格（第一格固定为"未分类"）
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            item(key = "unspecified") {
                QuickCategoryCell(
                    name = "未分类",
                    color = UI.colors.medium,
                    iconRes = R.drawable.ic_custom_category_s,
                    iconTint = UI.colors.pureInverse,
                    selected = state.selectedCategoryId == null
                ) {
                    viewModel.onCategoryClick(null)
                }
            }
            items(items = state.categories, key = { it.id.value }) { category ->
                QuickCategoryCell(
                    name = category.name.value,
                    color = category.color.value.toComposeColor(),
                    iconRes = getCustomIconIdS(
                        iconName = category.icon?.id,
                        defaultIcon = R.drawable.ic_custom_category_s
                    ),
                    iconTint = if (state.selectedCategoryId == category.id.value) {
                        findContrastTextColor(category.color.value.toComposeColor())
                    } else {
                        UI.colors.pureInverse
                    },
                    selected = state.selectedCategoryId == category.id.value
                ) {
                    viewModel.onCategoryClick(category.id.value)
                }
            }
        }

        // 金额 + 备注
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            state.evaluated?.let { evaluated ->
                val direction = if (evaluated >= 0) "支出" else "收入"
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    text = "将记为：$direction ${
                        DecimalFormat("#,##0.00").format(kotlin.math.abs(evaluated))
                    } ${currencyDisplay(state.baseCurrency)}",
                    style = UI.typo.nC.style(
                        color = if (evaluated >= 0) UI.colors.pureInverse else UI.colors.green
                    )
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                text = state.input.ifEmpty { "0" },
                style = UI.typo.nH1.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.pureInverse
                ).copy(fontSize = 40.sp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                singleLine = true,
                placeholder = { Text("备注（可选）", style = UI.typo.b2) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))

        // 计算键盘
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UI.colors.medium)
                .padding(vertical = 8.dp)
        ) {
            PadRow(
                keys = listOf("7", "8", "9", "C"),
                state = state,
                onKey = viewModel::onKey,
                onClear = viewModel::onClear
            )
            PadRow(
                keys = listOf("4", "5", "6", "+"),
                state = state,
                onKey = viewModel::onKey,
                onClear = viewModel::onClear
            )
            PadRow(
                keys = listOf("1", "2", "3", "−"),
                state = state,
                onKey = viewModel::onKey,
                onClear = viewModel::onClear
            )
            PadRow(
                keys = listOf(".", "0", "⌫", "完成"),
                state = state,
                onKey = viewModel::onKey,
                onClear = viewModel::onClear,
                onBackspace = viewModel::onBackspace,
                onFinish = { viewModel.finish { nav.back() } }
            )
        }
    }
}

@Composable
private fun QuickCategoryCell(
    name: String,
    color: Color,
    iconRes: Int,
    iconTint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = UI.colors.pureInverse,
                            shape = RoundedCornerShape(50)
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            IvyIcon(icon = iconRes, tint = iconTint)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = UI.typo.c.style(
                color = UI.colors.pureInverse,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PadRow(
    keys: List<String>,
    state: QuickEntryState,
    onKey: (Char) -> Unit,
    onClear: () -> Unit,
    onBackspace: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            PadKey(
                modifier = Modifier.weight(1f),
                label = key,
                state = state,
                onKey = onKey,
                onClear = onClear,
                onBackspace = onBackspace,
                onFinish = onFinish
            )
        }
    }
}

@Composable
private fun PadKey(
    modifier: Modifier,
    label: String,
    state: QuickEntryState,
    onKey: (Char) -> Unit,
    onClear: () -> Unit,
    onBackspace: (() -> Unit)?,
    onFinish: (() -> Unit)?,
) {
    val isFinish = label == "完成"
    val isBackspace = label == "⌫"
    val canFinish = !state.saving &&
            state.evaluated != null &&
            kotlin.math.abs(state.evaluated) >= 0.01
    val context = LocalContext.current

    val enabled = when {
        isFinish -> true
        isBackspace -> state.input.isNotEmpty()
        else -> true
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFinish && canFinish) {
                    com.ivy.wallet.ui.theme.Ivy
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = enabled) {
                Timber.d("PadKey tap: %s", label)
                when (label) {
                    "C" -> onClear()
                    "⌫" -> onBackspace?.invoke()
                    "+" -> onKey('+')
                    "−" -> onKey('-')
                    "完成" -> {
                        if (canFinish) {
                            onFinish?.invoke()
                        } else {
                            // 无效金额也给反馈，不让点击像"卡死"
                            android.widget.Toast.makeText(
                                context,
                                if (state.evaluated == null) "请先输入金额" else "金额太小啦",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    else -> label.firstOrNull()?.let(onKey)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = if (isFinish) FontWeight.Bold else FontWeight.Normal,
            color = if (isFinish && canFinish) {
                Color.White
            } else {
                UI.colors.pureInverse
            }
        )
    }
}
