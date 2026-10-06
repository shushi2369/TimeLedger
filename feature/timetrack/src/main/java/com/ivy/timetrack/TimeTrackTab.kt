package com.ivy.timetrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.arkui.ArkStaggeredIn
import com.ivy.legacy.arkui.rememberArkPulse
import com.ivy.timetrack.data.TodoEntity
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
    var editEntryTarget by remember { mutableStateOf<TodayEntry?>(null) }
    var editTarget by remember { mutableStateOf<TimeActivityEntity?>(null) }
    var todoInput by remember { mutableStateOf("") }
    var todosDoneExpanded by remember { mutableStateOf(false) }
    var todoRemindTarget by remember { mutableStateOf<TodoEntity?>(null) }
    // fork 修复：日期选择器毫秒为 UTC 日历日语义，中间态改存 LocalDate 防时区偏移一天
    var todoTimePickTarget by remember { mutableStateOf<Pair<TodoEntity, java.time.LocalDate>?>(null) }
    var todoDatePickTarget by remember { mutableStateOf<TodoEntity?>(null) }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    // Android 13+ 通知运行时权限：此前从未请求，备忘录到点通知被系统静默丢弃
    val context = LocalContext.current
    val requestNotifPermIfNeeded = {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Unit
    }

    // fork 修复：进程被杀后前台服务不重启，计时中条目失去常驻提醒通知；
    // 进入时间页检测到计时中即重挂（startForegroundService 幂等，仅刷新通知）
    LaunchedEffect(Unit) {
        todayEntries.firstOrNull { it.entry.endedAt == null }?.let { running ->
            com.ivy.timetrack.TimerService.start(context, running.entry.startedAt)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))

        com.ivy.legacy.arkui.ArkBilingualTitle(
            cn = "时间",
            en = "TIME",
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "今日 ${formatDurationChinese(todayTotalMs)}",
            style = UI.typo.nB2.style(
                fontWeight = FontWeight.Bold,
                color = UI.colors.pureInverse.copy(alpha = 0.6f)
            ),
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // 每日目标达成率（设定了目标的活动才计入）
        val goalActivities = activities.filter { it.dailyGoalMin > 0 }
        if (goalActivities.isNotEmpty()) {
            val goalMinTotal = goalActivities.sumOf { it.dailyGoalMin.toLong() }
            val actualByActivity = todayEntries.groupBy { it.entry.activityId }
                .mapValues { (_, list) -> list.sumOf { it.entry.durationMs(nowMs) } }
            val achievedMin = goalActivities.sumOf { act ->
                val actualMin = (actualByActivity[act.id] ?: 0L) / 60000
                minOf(actualMin, act.dailyGoalMin.toLong())
            }
            val pct = (achievedMin * 100 / goalMinTotal).coerceAtMost(100)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "今日目标达成 $pct%",
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Bold,
                    color = UI.colors.primary
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        if (runningEntry != null && runningActivity != null) {
            RunningCard(
                activity = runningActivity,
                elapsedMs = runningEntry.durationMs(nowMs),
                paused = runningEntry.pausedAt != null,
                onPauseResume = {
                    if (runningEntry.pausedAt != null) viewModel.resumeTimer()
                    else viewModel.pauseTimer()
                },
                onStop = viewModel::stopRunning
            )
            Spacer(Modifier.height(16.dp))
        }

        ActivityGrid(
            activities = activities,
            runningActivityId = runningEntry?.activityId,
            onToggle = viewModel::toggle,
            onAddActivity = { showAddActivity = true },
            onEditActivity = { editTarget = it }
        )

        Spacer(Modifier.height(24.dp))

        // ── 备忘录 TODO ─────────────────────────────
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(14.dp)
                    .background(UI.colors.primary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "备忘录",
                style = UI.typo.b2.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.pureInverse.copy(alpha = 0.7f)
                )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "TODO",
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Bold,
                    color = UI.colors.gray
                ).copy(fontSize = 10.sp, letterSpacing = 0.08.em)
            )
        }

        Spacer(Modifier.height(8.dp))

        val todos = viewModel.todos
        val todosDone = viewModel.todosDone
        if (todos.isEmpty() && todosDone.isEmpty()) {
            Text(
                text = "今天要做点什么？",
                style = UI.typo.b2.style(color = UI.colors.pureInverse.copy(alpha = 0.35f)),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            todos.forEachIndexed { index, todo ->
                TodoRow(
                    todo = todo,
                    onToggle = { viewModel.toggleTodo(todo.id) },
                    onDelete = { viewModel.deleteTodo(todo.id) },
                    onRemind = { requestNotifPermIfNeeded(); todoRemindTarget = todo },
                    index = index,
                )
            }

            if (todosDone.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { todosDoneExpanded = !todosDoneExpanded }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已完成 ${todosDone.size}",
                        style = UI.typo.c.style(color = UI.colors.pureInverse.copy(alpha = 0.45f))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (todosDoneExpanded) "▴" else "▾",
                        style = UI.typo.c.style(color = UI.colors.pureInverse.copy(alpha = 0.45f))
                    )
                }
                if (todosDoneExpanded) {
                    todosDone.forEachIndexed { index, todo ->
                        TodoRow(
                            todo = todo,
                            onToggle = { viewModel.toggleTodo(todo.id) },
                            onDelete = { viewModel.deleteTodo(todo.id) },
                            onRemind = { requestNotifPermIfNeeded(); todoRemindTarget = todo },
                            index = index,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 输入行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(UI.shapes.r4)
                .background(UI.colors.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = todoInput,
                onValueChange = { todoInput = it.take(100) },
                placeholder = { Text("要做的事…", style = UI.typo.b2) },
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "＋",
                style = UI.typo.b2.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.primary
                ),
                modifier = Modifier
                    .clickable {
                        viewModel.addTodo(todoInput)
                        todoInput = ""
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        todoRemindTarget?.let { target ->
            RemindOptionsDialog(
                onPickTime = {
                    todoRemindTarget = null
                    todoDatePickTarget = target   // 先选日期，再选时间
                },
                onClear = {
                    viewModel.setTodoRemind(target.id, null)
                    todoRemindTarget = null
                },
                onDismiss = { todoRemindTarget = null }
            )
        }

        todoDatePickTarget?.let { target ->
            TodoDatePickerDialog(
                // fork 修复：初始值按 UTC 日历日语义转换（原 now 直传，凌晨会选中昨天）
                initialMillis = (target.remindAt?.let {
                    java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                } ?: java.time.LocalDate.now())
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                onConfirm = { dateMillis ->
                    todoDatePickTarget = null
                    val d = java.time.Instant.ofEpochMilli(dateMillis)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    todoTimePickTarget = target to d
                },
                onDismiss = { todoDatePickTarget = null }
            )
        }

        todoTimePickTarget?.let { (target, pickedDate) ->
            TimePickerDialog(
                initialMillis = target.remindAt ?: System.currentTimeMillis(),
                onConfirm = { (hour, minute) ->
                    // fork 修复：本地日期+时分直接组本地时间（原 UTC 零点当本地挂钟，西半球偏一天）
                    val remindAt = pickedDate.atTime(hour, minute)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                    viewModel.setTodoRemind(target.id, remindAt)
                    todoTimePickTarget = null
                },
                onDismiss = { todoTimePickTarget = null }
            )
        }

        // ── TODO 区结束 ─────────────────────────────

        Spacer(Modifier.height(16.dp))

        SectionTitle("今日记录")

        if (todayEntries.isEmpty()) {
            Text(
                text = if (runningEntry == null) "点击上方活动开始计时" else "计时中…",
                style = UI.typo.b2.style(color = UI.colors.pureInverse.copy(alpha = 0.4f)),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            todayEntries.forEach { item ->
                TodayEntryRow(item = item, onClick = { editEntryTarget = item })
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

    editTarget?.let { target ->
        EditActivityDialog(
            activity = target,
            onSave = { name, color, goalMin ->
                viewModel.updateActivity(target.id, name, color, goalMin)
                editTarget = null
            },
            onDelete = {
                viewModel.deleteActivity(target.id)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    editEntryTarget?.let { target ->
        EditEntryDialog(
            item = target,
            onSave = { startMs, endMs, note ->
                viewModel.updateEntry(
                    entryId = target.entry.id,
                    startedAt = startMs,
                    endedAt = endMs,
                    note = note,
                )
                editEntryTarget = null
            },
            onDelete = {
                viewModel.deleteEntry(target.entry.id)
                editEntryTarget = null
            },
            onDismiss = { editEntryTarget = null }
        )
    }
}

@Composable
private fun RunningCard(
    activity: TimeActivityEntity,
    elapsedMs: Long,
    paused: Boolean,
    onPauseResume: () -> Unit,
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
                .background(if (paused) UI.colors.medium else accent)
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = "${activity.name} · ${if (paused) "已暂停" else "计时中"}",
                style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatStopwatch(elapsedMs),
                style = UI.typo.nB1.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = if (paused) UI.colors.gray else accent
                )
            )
        }

        IvyCircleButton(
            icon = if (paused) UiR.drawable.ic_time_tracking_play else UiR.drawable.ic_time_tracking_pause,
            backgroundGradient = Gradient.solid(if (paused) UI.colors.gray else accent),
            tint = White,
            onClick = onPauseResume
        )

        Spacer(Modifier.width(10.dp))

        IvyCircleButton(
            icon = UiR.drawable.ic_popup_close,
            backgroundGradient = Gradient.solid(UI.colors.pureInverse),
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
    onEditActivity: (TimeActivityEntity) -> Unit,
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
                        onLongClick = { onEditActivity(activity) },
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ActivityCell(
    activity: TimeActivityEntity,
    running: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(activity.colorArgb)

    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(UI.shapes.r4)
            .background(if (running) accent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
            if (!item.entry.note.isNullOrBlank()) {
                Text(
                    text = item.entry.note,
                    style = UI.typo.c.style(color = UI.colors.pureInverse.copy(alpha = 0.4f)),
                    maxLines = 1
                )
            }
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

                ColorPalette(selectedColor = selectedColor, onSelect = { selectedColor = it })
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
private fun EditActivityDialog(
    activity: TimeActivityEntity,
    onSave: (String, Long, Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(activity.name) }
    var selectedColor by remember { mutableStateOf(activity.colorArgb) }
    var dailyGoal by remember { mutableStateOf(if (activity.dailyGoalMin > 0) activity.dailyGoalMin.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑活动") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("活动名称") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                ColorPalette(selectedColor = selectedColor, onSelect = { selectedColor = it })

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = dailyGoal,
                    onValueChange = { dailyGoal = it.filter { c -> c.isDigit() }.take(4) },
                    placeholder = { Text("每日目标（分钟，留空不设目标）") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, selectedColor, dailyGoal.toIntOrNull() ?: 0)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("删除", color = Color(0xFFE53935)) }
        }
    )
}

/** 编辑历史记录：当日起止时间 + 备注（note 字段激活）。 */
@Composable
private fun EditEntryDialog(
    item: TodayEntry,
    onSave: (Long, Long?, String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cal = java.util.Calendar.getInstance()
    fun setTime(base: Long, hour: Int, minute: Int): Long {
        cal.timeInMillis = base
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val running = item.entry.endedAt == null
    var startMillis by remember { mutableStateOf(item.entry.startedAt) }
    var endMillis by remember { mutableStateOf(item.entry.endedAt ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(item.entry.note.orEmpty()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录 · ${item.activityName}") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(UI.shapes.r4)
                        .background(UI.colors.medium)
                        .clickable(enabled = !running) { showStartPicker = true }
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("开始", style = UI.typo.b2.style())
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatTime(startMillis, fmt),
                        style = UI.typo.nB2.style(fontWeight = FontWeight.Bold)
                    )
                }

                if (!running) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(UI.shapes.r4)
                            .background(UI.colors.medium)
                            .clickable { showEndPicker = true }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("结束", style = UI.typo.b2.style())
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatTime(endMillis, fmt),
                            style = UI.typo.nB2.style(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (running) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "计时中的记录只能修改开始时间和备注",
                        style = UI.typo.c.style(color = UI.colors.gray)
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("备注（这段做了什么）") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validStart = if (!running && endMillis <= startMillis) {
                    // 结束早于开始：把开始调到结束前一天的同一时刻？简单起见取结束前至少 1 分钟
                    endMillis - 60_000
                } else startMillis
                onSave(validStart, if (running) null else endMillis, note)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("删除", color = Color(0xFFE53935)) }
        }
    )

    if (showStartPicker) {
        TimePickerDialog(
            initialMillis = startMillis,
            onConfirm = { startMillis = setTime(startMillis, it.first, it.second); showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMillis = endMillis,
            onConfirm = { endMillis = setTime(endMillis, it.first, it.second); showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }
}

private fun formatTime(millis: Long, fmt: SimpleDateFormat): String = fmt.format(java.util.Date(millis))

/** 备忘提醒日期选择（material3 DatePicker，今天起可选）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TodoDatePickerDialog(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // 今天 0 点（本地）起可选；DatePicker 的毫秒是 UTC 语义
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return utcTimeMillis >= cal.timeInMillis - java.util.TimeZone.getDefault().getOffset(cal.timeInMillis)
            }
        }
    )
    // DatePicker 网格较宽（约 360dp）：AlertDialog 默认宽度在窄屏会裁掉最后一列，
    // 需 usePlatformDefaultWidth=false 全宽 + 隐藏 DatePicker 内置标题（外层已有）
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        ),
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
                state.selectedDateMillis?.let { onConfirm(it) }
            }) { Text("下一步") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** material3 TimePicker + AlertDialog 包装（24 小时制）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMillis: Long,
    onConfirm: (Pair<Int, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = initialMillis
    val state = androidx.compose.material3.rememberTimePickerState(
        initialHour = cal.get(java.util.Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(java.util.Calendar.MINUTE),
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = {
            androidx.compose.material3.TimePicker(state = state)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour to state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ColorPalette(selectedColor: Long, onSelect: (Long) -> Unit) {
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
                        onClick = { onSelect(color) }
                    )
                }
            }
        }
    }
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

/** 备忘录单行：红/绿点 + 内容 +（提醒）+ 删除。 */
@Composable
private fun TodoRow(
    todo: TodoEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onRemind: () -> Unit,
    index: Int = 0,
) {
    val dim = todo.done
    val pulse = rememberArkPulse()
    ArkStaggeredIn(index = index) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 红(未完成，呼吸脉动)/绿(完成) 点，点击切换
        Box(
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer {
                    if (!todo.done) {
                        scaleX = pulse
                        scaleY = pulse
                    }
                }
                .clip(CircleShape)
                .background(
                    if (todo.done) Color(0xFF2FAC78) else Color(0xFFD83C3C)
                )
                .clickable(onClick = onToggle)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = todo.content,
            style = UI.typo.b2.style(
                fontWeight = if (todo.done) FontWeight.Normal else FontWeight.SemiBold,
                color = UI.colors.pureInverse.copy(alpha = if (dim) 0.4f else 1f)
            ),
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggle)
        )

        // 提醒：未设 → 淡"⏰"；已设 → 今天/明天/M月d日 + 时刻
        val remindText = todo.remindAt?.let {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it }
            val now = java.util.Calendar.getInstance()
            val sameDay = cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                    cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
            val tomorrowCal = (now.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            val isTomorrow = cal.get(java.util.Calendar.YEAR) == tomorrowCal.get(java.util.Calendar.YEAR) &&
                    cal.get(java.util.Calendar.DAY_OF_YEAR) == tomorrowCal.get(java.util.Calendar.DAY_OF_YEAR)
            val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(it))
            when {
                sameDay -> hm
                isTomorrow -> "明天 $hm"
                else -> "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日 $hm"
            }
        }
        Text(
            text = remindText ?: "⏰",
            style = UI.typo.c.style(
                fontWeight = FontWeight.Bold,
                color = if (remindText != null) UI.colors.primary
                else UI.colors.pureInverse.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .clickable(onClick = onRemind)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // 删除
        Text(
            text = "✕",
            style = UI.typo.c.style(color = UI.colors.pureInverse.copy(alpha = 0.35f)),
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
    }
}

/** 已设提醒的行点击：修改时间 / 清除提醒。 */
@Composable
private fun RemindOptionsDialog(
    onPickTime: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒") },
        text = {
            Column {
                Text(
                    text = "修改提醒时间",
                    style = UI.typo.b2.style(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickTime)
                        .padding(vertical = 12.dp)
                )
                Text(
                    text = "清除提醒",
                    style = UI.typo.b2.style(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE53935)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClear)
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
