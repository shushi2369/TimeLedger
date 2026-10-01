package com.ivy.timetrack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.timetrack.data.TimeActivityEntity
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.IvyCircleButton
import com.ivy.ui.R as UiR
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 新增活动可选的颜色盘（ARGB）。 */
private val PRESET_COLORS = listOf(
    0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFFE91E63,
    0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722, 0xFF607D8B,
)

@Composable
fun TimeTrackTab(viewModel: TimeTrackViewModel = viewModel()) {
    val activities = viewModel.activities
    val runningEntry = viewModel.runningEntry
    val todayEntries = viewModel.todayEntries
    val weekTotals = viewModel.weekTotals

    // 计时中每秒刷新一次，用于秒表与今日累计
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(runningEntry?.id) {
        if (runningEntry != null) {
            while (true) {
                delay(1000)
                nowMs = System.currentTimeMillis()
            }
        } else {
            nowMs = System.currentTimeMillis()
        }
    }

    val runningActivity = runningEntry?.let { running ->
        activities.firstOrNull { it.id == running.activityId }
    }
    val todayTotalMs = todayEntries.sumOf { it.entry.durationMs(nowMs) }

    var showAddActivity by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TodayEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "时间",
            style = UI.typo.b1.style(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "今日 ${formatDurationChinese(todayTotalMs)}",
            style = UI.typo.b2.style(
                fontWeight = FontWeight.Bold,
                color = UI.colors.pureInverse.copy(alpha = 0.6f)
            ),
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(16.dp))

        if (runningEntry != null && runningActivity != null) {
            RunningCard(
                activity = runningActivity,
                elapsedMs = runningEntry.durationMs(nowMs),
                onStop = viewModel::stopRunning
            )
            Spacer(Modifier.height(16.dp))
        }

        ActivityGrid(
            activities = activities,
            runningActivityId = runningEntry?.activityId,
            onToggle = viewModel::toggle,
            onAddActivity = { showAddActivity = true }
        )

        Spacer(Modifier.height(24.dp))

        SectionTitle("今日记录")

        if (todayEntries.isEmpty()) {
            Text(
                text = if (runningEntry == null) "点击上方活动开始计时" else "计时中…",
                style = UI.typo.b2.style(color = UI.colors.pureInverse.copy(alpha = 0.4f)),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            todayEntries.forEach { item ->
                TodayEntryRow(item = item, onClick = { deleteTarget = item })
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionTitle("最近 7 天")

        if (weekTotals.isEmpty()) {
            Text(
                text = "暂无数据",
                style = UI.typo.b2.style(color = UI.colors.pureInverse.copy(alpha = 0.4f)),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            val maxTotal = weekTotals.maxOf { it.totalMs }
            weekTotals.forEach { total ->
                WeekTotalRow(total = total, maxTotal = maxTotal)
            }
        }

        Spacer(Modifier.height(150.dp))
    }

    if (showAddActivity) {
        AddActivityDialog(
            onAdd = { name, color ->
                viewModel.addActivity(name, color)
                showAddActivity = false
            },
            onDismiss = { showAddActivity = false }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条时间记录？") },
            text = {
                Text(
                    "${target.activityName} · ${formatDurationChinese(target.entry.durationMs(nowMs))}"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(target.entry.id)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RunningCard(
    activity: TimeActivityEntity,
    elapsedMs: Long,
    onStop: () -> Unit,
) {
    val accent = Color(activity.colorArgb)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .background(accent.copy(alpha = 0.12f))
            .padding(vertical = 16.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(accent)
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = "${activity.name} · 计时中",
                style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatStopwatch(elapsedMs),
                style = UI.typo.b1.style(fontWeight = FontWeight.ExtraBold, color = accent)
            )
        }

        IvyCircleButton(
            icon = UiR.drawable.ic_time_tracking_pause,
            backgroundGradient = Gradient.solid(accent),
            tint = White,
            onClick = onStop
        )
    }
}

@Composable
private fun ActivityGrid(
    activities: List<TimeActivityEntity>,
    runningActivityId: String?,
    onToggle: (String) -> Unit,
    onAddActivity: () -> Unit,
) {
    val rows = activities.chunked(4).toMutableList()
    val lastRow = rows.lastOrNull()
    if (lastRow == null || lastRow.size == 4) {
        rows.add(emptyList())
    }
    // 网格末尾固定一个"新增活动"格子

    Column(Modifier.padding(horizontal = 16.dp)) {
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(8.dp))
            Row {
                row.forEach { activity ->
                    ActivityCell(
                        activity = activity,
                        running = activity.id == runningActivityId,
                        onClick = { onToggle(activity.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size < 4) {
                    NewActivityCell(
                        onClick = onAddActivity,
                        modifier = Modifier.weight(1f)
                    )
                    repeat(4 - row.size - 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCell(
    activity: TimeActivityEntity,
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(activity.colorArgb)

    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(UI.shapes.r4)
            .background(if (running) accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent)
                .then(
                    if (running) {
                        Modifier.border(3.dp, accent.copy(alpha = 0.45f), CircleShape)
                    } else {
                        Modifier
                    }
                )
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = activity.name,
            style = UI.typo.c.style(
                fontWeight = if (running) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun NewActivityCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(UI.shapes.r4)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = UI.typo.b2.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.pureInverse
                )
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "新活动",
            style = UI.typo.c.style(color = UI.colors.pureInverse.copy(alpha = 0.6f)),
            maxLines = 1
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = UI.typo.b2.style(
            fontWeight = FontWeight.ExtraBold,
            color = UI.colors.pureInverse.copy(alpha = 0.5f)
        ),
        modifier = Modifier.padding(horizontal = 24.dp)
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TodayEntryRow(item: TodayEntry, onClick: () -> Unit) {
    val range = SimpleDateFormat("HH:mm", Locale.getDefault())
    val start = range.format(Date(item.entry.startedAt))
    val end = item.entry.endedAt?.let { range.format(Date(it)) } ?: "进行中"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(UI.shapes.r4)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(item.activityColor))
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.activityName,
                style = UI.typo.b2.style(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "$start - $end",
                style = UI.typo.c.style(color = UI.colors.pureInverse.copy(alpha = 0.5f))
            )
        }

        Text(
            text = formatDurationChinese(item.entry.durationMs(System.currentTimeMillis())),
            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun WeekTotalRow(total: WeekTotal, maxTotal: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = total.activityName,
            style = UI.typo.b2.style(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(64.dp)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(UI.shapes.rFull)
                .background(UI.colors.medium)
        ) {
            if (maxTotal > 0 && total.totalMs > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(total.totalMs.toFloat() / maxTotal)
                        .background(Color(total.activityColor))
                )
            }
        }

        Text(
            text = formatDurationChinese(total.totalMs),
            style = UI.typo.c.style(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(88.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun AddActivityDialog(
    onAdd: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(PRESET_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新活动") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("活动名称") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                // 8 色分两行，避免单行挤压变形
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESET_COLORS.chunked(4).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowColors.forEach { color ->
                                ColorDot(
                                    color = color,
                                    selected = color == selectedColor,
                                    onClick = { selectedColor = color }
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
                name = ""
            }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ColorDot(color: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (selected) {
                    Modifier.border(3.dp, UI.colors.pureInverse, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    )
}

private fun formatDurationChinese(ms: Long): String {
    val minutes = ms / 60000
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "$hours 小时 $mins 分"
        hours > 0 -> "$hours 小时"
        else -> "$mins 分钟"
    }
}

private fun formatStopwatch(ms: Long): String {
    val totalSec = ms / 1000
    return String.format(
        Locale.US, "%02d:%02d:%02d",
        totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60
    )
}
