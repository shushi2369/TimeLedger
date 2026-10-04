package com.ivy.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ivy.legacy.arkui.ArkStaggeredIn
import com.ivy.legacy.arkui.arkPressScale
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

    var showDatePicker by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }

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
            item(key = "add_category") {
                QuickCategoryCell(
                    name = "新类别",
                    color = UI.colors.medium,
                    iconRes = R.drawable.ic_custom_category_s,
                    iconTint = UI.colors.primary,
                    selected = false
                ) {
                    showAddCategory = true
                }
            }
            itemsIndexed(
                items = state.categories,
                key = { _, category -> "${category.id.value}-${state.categoriesVersion}" }
            ) { index, category ->
                ArkStaggeredIn(index = index) {
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
            Spacer(Modifier.height(6.dp))

            // 日期选择：默认今天，可改（补记昨天的账）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val dateText = state.selectedDate?.let {
                    "${it.monthValue}月${it.dayOfMonth}日"
                } ?: "今天"
                Text(
                    text = dateText,
                    style = UI.typo.c.style(
                        fontWeight = FontWeight.Bold,
                        color = if (state.selectedDate != null) UI.colors.primary
                        else UI.colors.pureInverse.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(UI.colors.medium)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                singleLine = true,
                placeholder = { Text("备注（可选）", style = UI.typo.b2) },
                modifier = Modifier.fillMaxWidth()
            )

            // 标签多选（方舟风 chips，横滚）
            if (state.availableTags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.availableTags.forEach { tag ->
                        val selected = tag.id.value in state.selectedTagIds
                        Text(
                            text = "#${tag.name.value}",
                            style = UI.typo.c.style(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color.White else UI.colors.pureInverse
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) UI.colors.primary else UI.colors.medium
                                )
                                .clickable { viewModel.toggleTag(tag.id.value) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
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
                keys = listOf("4", "5", "6", "÷"),
                state = state,
                onKey = viewModel::onKey,
                onClear = viewModel::onClear
            )
            PadRow(
                keys = listOf("1", "2", "3", "×"),
                state = state,
                onKey = viewModel::onKey,
                onClear = viewModel::onClear
            )
            PadRow(
                keys = listOf("+", "−", "⌫", "完成"),
                state = state,
                onKey = viewModel::onKey,
                onClear = viewModel::onClear,
                onBackspace = viewModel::onBackspace,
                onFinish = { viewModel.finish { nav.back() } }
            )
            PadRow(
                keys = listOf(".", "0"),
                weights = listOf(2f, 2f),
                state = state,
                onKey = viewModel::onKey,
                onClear = viewModel::onClear
            )
        }

        if (showDatePicker) {
            QuickDatePickerDialog(
                initialMillis = java.lang.System.currentTimeMillis(),
                onConfirm = { date ->
                    viewModel.selectDate(date)
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }

        if (showAddCategory) {
            AddCategoryDialog(
                onAdd = { name, color ->
                    viewModel.addCategory(name, color)
                    showAddCategory = false
                },
                onDismiss = { showAddCategory = false }
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

/** 日期选择（今天起可选）——复用备忘录的日期选择器模式。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun QuickDatePickerDialog(
    initialMillis: Long,
    onConfirm: (java.time.LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        title = { Text("选择日期") },
        text = {
            androidx.compose.material3.DatePicker(
                state = state,
                title = {},
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                val date = java.time.Instant.ofEpochMilli(millis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                onConfirm(date)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private val CATEGORY_COLORS = listOf(
    0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFFE91E63,
    0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722, 0xFF607D8B,
)

/** 新建类别对话框：名称 + 8 色盘。 */
@Composable
private fun AddCategoryDialog(
    onAdd: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(CATEGORY_COLORS.first()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新类别") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("类别名称") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                // 8 色两行
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_COLORS.chunked(4).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .then(
                                            if (color == selectedColor) {
                                                Modifier.border(
                                                    3.dp, UI.colors.pureInverse, CircleShape
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { selectedColor = color }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(name, selectedColor)
            }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PadRow(
    keys: List<String>,
    state: QuickEntryState,
    onKey: (Char) -> Unit,
    onClear: () -> Unit,
    onBackspace: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
    weights: List<Float> = List(keys.size) { 1f },
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEachIndexed { index, key ->
            PadKey(
                modifier = Modifier.weight(weights.getOrElse(index) { 1f }),
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
