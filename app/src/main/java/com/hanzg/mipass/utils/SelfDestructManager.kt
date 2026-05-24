package com.hanzg.mipass.utils

import com.hanzg.mipass.data.local.AppPreferences
import com.hanzg.mipass.data.local.MiPassDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Singleton
class SelfDestructManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
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
            android.util.Log.w("SelfDestruct", "自毁触发：失败${newCount}次，阈值$threshold")
            wipeAll()
        }
    }

    suspend fun resetAttempts() {
        appPreferences.setSelfDestructAttempts(0)
    }

    private suspend fun wipeAll() {
        android.util.Log.w("SelfDestruct", "开始执行自毁擦除...")
        var hasError = false

        // 0. 关闭数据库
        try {
            MiPassDatabase.closeInstance()
            android.util.Log.i("SelfDestruct", "数据库已关闭")
        } catch (e: Exception) {
            android.util.Log.e("SelfDestruct", "关闭数据库失败", e)
            hasError = true
        }

        // 1. 删除数据库文件
        try {
            val dbFile = context.getDatabasePath("mipass.db")
            if (dbFile.exists()) {
                dbFile.delete()
                listOf("-journal", "-wal", "-shm").forEach { suffix ->
                    File(dbFile.absolutePath + suffix).let {
                        if (it.exists()) it.delete()
                    }
                }
                android.util.Log.i("SelfDestruct", "数据库文件已删除")
            }
        } catch (e: Exception) {
            android.util.Log.e("SelfDestruct", "删除数据库文件失败", e)
            hasError = true
        }

        // 2. 删除快照目录
        try {
            val snapshotsDir = File(context.filesDir, "snapshots")
            if (snapshotsDir.exists()) {
                snapshotsDir.listFiles()?.forEach { it.delete() }
                snapshotsDir.delete()
                android.util.Log.i("SelfDestruct", "快照目录已删除")
            }
        } catch (e: Exception) {
            android.util.Log.e("SelfDestruct", "删除快照失败", e)
            hasError = true
        }

        // 3. 删除 DEK 偏好设置
        try {
            val dekPrefs = context.getSharedPreferences("mipass_dek_prefs", Context.MODE_PRIVATE)
            dekPrefs.edit().clear().commit()
            android.util.Log.i("SelfDestruct", "DEK 已清除")
        } catch (e: Exception) {
            android.util.Log.e("SelfDestruct", "清除 DEK 失败", e)
            hasError = true
        }

        // 4. 删除导出缓存
        try {
            val exportsDir = File(context.cacheDir, "exports")
            if (exportsDir.exists()) {
                exportsDir.listFiles()?.forEach { it.delete() }
                exportsDir.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("SelfDestruct", "清除导出缓存失败", e)
            hasError = true
        }

        // 5. 清除 AppPreferences
        try {
            appPreferences.clearAll()
            android.util.Log.i("SelfDestruct", "AppPreferences 已清除")
        } catch (e: Exception) {
            android.util.Log.e("SelfDestruct", "清除 AppPreferences 失败", e)
            hasError = true
        }

        // 6. 删除 Android KeyStore KEK
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias("mipass_kek")) {
                keyStore.deleteEntry("mipass_kek")
                android.util.Log.i("SelfDestruct", "KEK 已删除")
            }
        } catch (e: Exception) {
            android.util.Log.e("SelfDestruct", "删除 KEK 失败", e)
            hasError = true
        }

        if (hasError) {
            android.util.Log.e("SelfDestruct", "自毁擦除完成（存在错误），终止进程")
        } else {
            android.util.Log.w("SelfDestruct", "自毁擦除成功完成")
        }

        // 7. 退出应用
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
