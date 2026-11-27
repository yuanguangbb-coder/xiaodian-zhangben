package com.example.ledger

import android.app.Application
import android.util.Log
import com.example.ledger.data.backup.BackupManager
import com.example.ledger.data.db.AppDatabase
import com.example.ledger.data.repository.LedgerRepository
import com.example.ledger.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 简单的手动依赖注入：整个 App 共用一个数据库、一个 Repository、一份设置。 */
class LedgerApplication : Application() {
    private val database by lazy { AppDatabase.create(this) }
    val repository by lazy { LedgerRepository(database.dao()) }
    val settings by lazy { AppSettings(this) }
    val backupManager by lazy { BackupManager(this, repository, settings) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        // 启动时做两件后台小事：清理过期回收站、每天自动备份一次
        appScope.launch {
            runCatching { repository.purgeDeletedOlderThan(LedgerRepository.RECYCLE_DAYS) }
            runCatching {
                if (settings.autoBackup.value) backupManager.autoBackup()
            }
        }
    }

    /**
     * 崩溃兜底：把堆栈写到 files/crash/last-crash.txt，设置页能看到。
     * 离线应用没有上报渠道，至少要留下现场。
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val dir = File(filesDir, "crash").apply { mkdirs() }
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                File(dir, "last-crash.txt").writeText(
                    "时间：$stamp\n线程：${thread.name}\n\n" + Log.getStackTraceString(error),
                    Charsets.UTF_8,
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun crashLogFile(filesDir: File): File = File(File(filesDir, "crash"), "last-crash.txt")
    }
}