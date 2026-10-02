package com.ivy.timetrack.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivy.timetrack.data.TodoDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设备重启后重排所有未完成且有提醒的备忘录闹钟。
 */
@AndroidEntryPoint
class TodoBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var todoDao: TodoDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                todoDao.findAllWithReminder().forEach { todo ->
                    TodoAlarmScheduler.schedule(context, todo)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
