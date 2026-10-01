package com.ivy.timetrack

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
     */
    fun toggle(activityId: String) {
        viewModelScope.launch {
            timerMutex.withLock {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val running = entryDao.findRunning()
                    if (running != null) {
                        entryDao.save(running.copy(endedAt = now))
                    }
                    if (running == null || running.activityId != activityId) {
                        entryDao.save(
                            TimeEntryEntity(
                                id = UUID.randomUUID().toString(),
                                activityId = activityId,
                                startedAt = now,
                                endedAt = null,
                            )
                        )
                    }
                }
            }
            refresh()
        }
    }

    fun stopRunning() {
        viewModelScope.launch {
            timerMutex.withLock {
                withContext(Dispatchers.IO) {
                    entryDao.findRunning()?.let { running ->
                        entryDao.save(running.copy(endedAt = System.currentTimeMillis()))
                    }
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
