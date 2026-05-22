package com.hanzg.mipass.utils

import com.hanzg.mipass.data.local.AppPreferences
import com.hanzg.mipass.data.local.MiPassDatabase
import com.hanzg.mipass.data.local.SnapshotManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelfDestructManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val DEFAULT_MAX_FAILED_ATTEMPTS = 10
    }

    suspend fun recordFailedAttempt() {
        val settings = appPreferences.settingsFlow.first()
        if (!settings.selfDestructEnabled) return

        val newCount = settings.selfDestructAttempts + 1
        appPreferences.setSelfDestructAttempts(newCount)

        val threshold = settings.selfDestructThreshold
        if (newCount >= threshold) {
            wipeAll()
        }
    }

    suspend fun resetAttempts() {
        appPreferences.setSelfDestructAttempts(0)
    }

    fun getAttempts(): Int {
        // 快速读取（非 Flow，用于 UI 提示）
        return 0 // 由调用方通过 Flow 读取
    }

    private suspend fun wipeAll() {
        try {
            // 1. 删除数据库文件
            val dbFile = context.getDatabasePath("mipass.db")
            if (dbFile.exists()) {
                dbFile.delete()
                // 删除 SQLCipher 相关文件
                listOf("-journal", "-wal", "-shm").forEach { suffix ->
                    File(dbFile.absolutePath + suffix).delete()
                }
            }

            // 2. 删除快照目录
            val snapshotsDir = File(context.filesDir, "snapshots")
            if (snapshotsDir.exists()) {
                snapshotsDir.listFiles()?.forEach { it.delete() }
                snapshotsDir.delete()
            }

            // 3. 删除 DEK 偏好设置
            val dekPrefs = context.getSharedPreferences("mipass_dek_prefs", Context.MODE_PRIVATE)
            dekPrefs.edit().clear().commit()

            // 4. 删除导出缓存
            val exportsDir = File(context.cacheDir, "exports")
            if (exportsDir.exists()) {
                exportsDir.listFiles()?.forEach { it.delete() }
                exportsDir.delete()
            }

            // 5. 清除 AppPreferences (DataStore)
            appPreferences.clearAll()

            // 6. 删除 Android KeyStore 中的 KEK
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry("mipass_kek")
            } catch (_: Exception) {
                // KeyStore 删除失败不算致命错误
            }

            // 7. 退出应用
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (_: Exception) {
            // 静默执行，不抛异常
        }
    }
}
