package com.ivy.domain.autobackup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivy.data.backup.BackupDataUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * fork 增补（2026-09-29）：自动本地备份。
 * 用户在设置中选择一个 SAF 文件夹（持久授权）后：
 * - 每天定时 + 打开应用时（距上次超过 20 小时）自动生成 JSON 备份 zip
 * - 文件名 timeledger-backup-yyyy-MM-dd-HHmm.zip，保留最近 7 份
 * 备份可用设置里的"导入"功能直接恢复。
 */
object AutoBackup {
    const val PREFS = "auto_backup_prefs"
    const val KEY_ENABLED = "enabled"
    const val KEY_TREE_URI = "tree_uri"
    const val KEY_LAST = "last_backup_at"

    const val WORK_DAILY = "auto_backup_daily"
    const val WORK_ON_START = "auto_backup_on_start"

    private const val STALE_MS = 20 * 60 * 60 * 1000L
    private const val KEEP_MS = 7 * 24 * 60 * 60 * 1000L
    private const val FILE_PREFIX = "timeledger-backup-"

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false) &&
                prefs(context).getString(KEY_TREE_URI, null) != null

    /** 确保每日任务已注册；若备份过期则补一次立即备份。应用启动时调用。 */
    fun maybeSchedule(context: Context) {
        if (!isEnabled(context)) return
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WORK_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS).build()
        )
        if (System.currentTimeMillis() - prefs(context).getLong(KEY_LAST, 0L) > STALE_MS) {
            wm.enqueueUniqueWork(
                WORK_ON_START,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
            )
        }
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_DAILY)
        wm.cancelUniqueWork(WORK_ON_START)
    }

    /** 执行一次备份，成功返回备份文件 Uri。 */
    suspend fun runNow(context: Context, backupDataUseCase: BackupDataUseCase): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val treeUriString = prefs(context).getString(KEY_TREE_URI, null)
                    ?: throw IllegalStateException("未选择备份文件夹")
                val treeUri = Uri.parse(treeUriString)

                val fileName = FILE_PREFIX +
                        SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date()) + ".zip"
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val target = DocumentsContract.createDocument(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                    "application/zip",
                    fileName
                ) ?: throw IllegalStateException("无法在备份文件夹中创建文件")

                backupDataUseCase.exportToFile(target)
                prefs(context).edit()
                    .putLong(KEY_LAST, System.currentTimeMillis())
                    .apply()
                pruneOld(context, treeUri)
                target
            }
        }

    /** 删除 7 天前的自动备份文件。 */
    private fun pruneOld(context: Context, treeUri: Uri) {
        runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cutoff = System.currentTimeMillis() - KEEP_MS
            val resolver = context.contentResolver
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    val modified = cursor.getLong(2)
                    if (name.startsWith(FILE_PREFIX) && modified in 1..cutoff) {
                        runCatching {
                            DocumentsContract.deleteDocument(
                                resolver,
                                DocumentsContract.buildDocumentUriUsingTree(
                                    treeUri, cursor.getString(0)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupDataUseCase: BackupDataUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = AutoBackup.runNow(applicationContext, backupDataUseCase)
        if (result.isSuccess) Result.success() else Result.retry()
    }
}

/** 兼容旧引用：Intent 常量占位（自动备份不依赖 BOOT 广播，WorkManager 自带持久化）。 */
val AUTO_BACKUP_REFRESH_INTENT = Intent.ACTION_BOOT_COMPLETED
