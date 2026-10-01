package com.ivy.timetrack

import android.content.Context

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.timetrack.data.TimeActivityDao
import com.ivy.timetrack.data.TimeActivityEntity
import com.ivy.timetrack.data.TimeEntryDao
import com.ivy.timetrack.data.TimeEntryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/** 今日时间线的一行，活动可能已被归档，故存名称与颜色快照。 */
data class TodayEntry(
    val entry: TimeEntryEntity,
    val activityName: String,
    val activityColor: Long,
)

/** 最近 7 天按活动聚合的时长。 */
data class WeekTotal(
    val activityName: String,
    val activityColor: Long,
    val totalMs: Long,
)

private data class LoadResult(
    val activities: List<TimeActivityEntity>,
    val running: TimeEntryEntity?,
    val today: List<TodayEntry>,
    val week: List<WeekTotal>,
)

private val DEFAULT_ACTIVITIES = listOf(
    "工作" to 0xFF2196F3,
    "学习" to 0xFF4CAF50,
    "运动" to 0xFFFF9800,
    "娱乐" to 0xFFE91E63,
)

private const val DELETED_ACTIVITY_NAME = "已删除活动"
private const val DELETED_ACTIVITY_COLOR = 0xFF9E9E9E

@HiltViewModel
class TimeTrackViewModel @Inject constructor(
    private val activityDao: TimeActivityDao,
    private val entryDao: TimeEntryDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    var activities by mutableStateOf<List<TimeActivityEntity>>(emptyList())
        private set
    var runningEntry by mutableStateOf<TimeEntryEntity?>(null)
        private set
    var todayEntries by mutableStateOf<List<TodayEntry>>(emptyList())
        private set
    var weekTotals by mutableStateOf<List<WeekTotal>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { seedIfEmpty() }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { load() }
            activities = result.activities
            runningEntry = result.running
            todayEntries = result.today
            weekTotals = result.week
        }
    }

    /** 串行化计时状态变更：连点/快速切换活动时避免并发读写"运行中记录"互相踩。 */
    private val timerMutex = Mutex()

    /**
     * 点击活动：无计时 → 开始；点进行中的活动 → 停止；点其他活动 → 停旧的并开始新的。
     * 单计时模式，避免并行计时的记账复杂度。
     * 副作用：同步通知栏计时常驻（TimerService）。
     */
    fun toggle(activityId: String) {
        viewModelScope.launch {
            var startedAt: Long? = null
            timerMutex.withLock {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val running = entryDao.findRunning()
                    if (running != null) {
                        entryDao.save(running.copy(endedAt = now, pausedAt = null))
                    }
                    if (running == null || running.activityId != activityId) {
                        val entry = TimeEntryEntity(
                            id = UUID.randomUUID().toString(),
                            activityId = activityId,
                            startedAt = now,
                            endedAt = null,
                        )
                        entryDao.save(entry)
                        startedAt = entry.startedAt
                    }
                }
            }
            startedAt?.let {
                TimerService.start(context, it)
            } ?: TimerService.stop(context)
            refresh()
        }
    }

    /** 暂停计时：记录暂停起点，通知栏常驻撤下。 */
    fun pauseTimer() {
        viewModelScope.launch {
            timerMutex.withLock {
                withContext(Dispatchers.IO) {
                    entryDao.findRunning()?.let { running ->
                        if (running.pausedAt == null) {
                            entryDao.save(running.copy(pausedAt = System.currentTimeMillis()))
                        }
                    }
                }
            }
            TimerService.stop(context)
            refresh()
        }
    }

    /** 继续计时：把暂停段累入 pausedMs，恢复通知栏常驻。 */
    fun resumeTimer() {
        viewModelScope.launch {
            var startedAt: Long? = null
            timerMutex.withLock {
                withContext(Dispatchers.IO) {
                    entryDao.findRunning()?.let { running ->
                        val pausedAt = running.pausedAt
                        if (pausedAt != null) {
                            val now = System.currentTimeMillis()
                            entryDao.save(
                                running.copy(
                                    pausedMs = running.pausedMs + (now - pausedAt),
                                    pausedAt = null,
                                )
                            )
                            startedAt = running.startedAt
                        }
                    }
                }
            }
            startedAt?.let { TimerService.start(context, it) }
            refresh()
        }
    }

    fun stopRunning() {
        viewModelScope.launch {
            timerMutex.withLock {
                withContext(Dispatchers.IO) {
                    entryDao.findRunning()?.let { running ->
                        entryDao.save(
                            running.copy(
                                endedAt = System.currentTimeMillis(),
                                pausedAt = null,
                            )
                        )
                    }
                }
            }
            TimerService.stop(context)
            refresh()
        }
    }

    /** 编辑历史记录：起止时间戳 + 备注。 */
    fun updateEntry(entryId: String, startedAt: Long, endedAt: Long?, note: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                entryDao.findBetween(Long.MIN_VALUE, Long.MAX_VALUE)
                    .firstOrNull { it.id == entryId }
                    ?.let {
                        entryDao.save(
                            it.copy(
                                startedAt = startedAt,
                                endedAt = endedAt,
                                note = note?.takeIf { n -> n.isNotBlank() },
                            )
                        )
                    }
            }
            refresh()
        }
    }

    /** 更新活动（名称/颜色/每日目标分钟）。 */
    fun updateActivity(activityId: String, name: String, colorArgb: Long, dailyGoalMin: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                activityDao.findAll().firstOrNull { it.id == activityId }?.let {
                    activityDao.save(
                        it.copy(
                            name = trimmed,
                            colorArgb = colorArgb,
                            dailyGoalMin = dailyGoalMin.coerceAtLeast(0),
                        )
                    )
                }
            }
            refresh()
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                entryDao.deleteById(entryId)
            }
            refresh()
        }
    }

    fun addActivity(name: String, colorArgb: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                activityDao.save(
                    TimeActivityEntity(
                        id = UUID.randomUUID().toString(),
                        name = trimmed,
                        colorArgb = colorArgb,
                        orderNum = (activityDao.maxOrder() ?: 0.0) + 1.0,
                    )
                )
            }
            refresh()
        }
    }

    fun renameActivity(activityId: String, name: String, colorArgb: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                activityDao.findAll().firstOrNull { it.id == activityId }?.let {
                    activityDao.save(it.copy(name = trimmed, colorArgb = colorArgb))
                }
            }
            refresh()
        }
    }

    /** 删除活动：归档保留记录可追溯；若正在计时先停掉。 */
    fun deleteActivity(activityId: String) {
        viewModelScope.launch {
            timerMutex.withLock {
                withContext(Dispatchers.IO) {
                    entryDao.findRunning()?.let { running ->
                        if (running.activityId == activityId) {
                            entryDao.save(
                                running.copy(endedAt = System.currentTimeMillis())
                            )
                        }
                    }
                    activityDao.archive(activityId)
                }
            }
            refresh()
        }
    }

    private suspend fun seedIfEmpty() {
        if (activityDao.count() > 0) return
        DEFAULT_ACTIVITIES.forEachIndexed { index, (name, color) ->
            activityDao.save(
                TimeActivityEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    colorArgb = color,
                    orderNum = (index + 1).toDouble(),
                )
            )
        }
    }

    private suspend fun load(): LoadResult = withContext(Dispatchers.IO) {
        val acts = activityDao.findAll()
        val actById = acts.associateBy { it.id }

        val dayStart = startOfDay(System.currentTimeMillis())
        val dayEnd = dayStart + DAY_MS
        val today = entryDao.findBetween(dayStart, dayEnd).map { entry ->
            val activity = actById[entry.activityId]
            TodayEntry(
                entry = entry,
                activityName = activity?.name ?: DELETED_ACTIVITY_NAME,
                activityColor = activity?.colorArgb ?: DELETED_ACTIVITY_COLOR,
            )
        }

        val weekStart = dayStart - 6 * DAY_MS
        val week = entryDao.totalsSince(weekStart).mapNotNull { row ->
            val activity = actById[row.activityId] ?: return@mapNotNull null
            WeekTotal(
                activityName = activity.name,
                activityColor = activity.colorArgb,
                totalMs = row.totalMs,
            )
        }

        LoadResult(
            activities = acts,
            running = entryDao.findRunning(),
            today = today,
            week = week,
        )
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        fun startOfDay(timeMillis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timeMillis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
