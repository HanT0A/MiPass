package com.hanzg.mipass

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.hanzg.mipass.data.local.AppPreferences
import com.hanzg.mipass.data.local.AppSettings
import com.hanzg.mipass.ui.navigation.MiPassNavHost
import com.hanzg.mipass.ui.screens.MasterPasswordScreenMode
import com.hanzg.mipass.ui.screens.MasterPasswordSetupScreen
import com.hanzg.mipass.ui.theme.MiPassTheme
import com.hanzg.mipass.utils.BiometricPromptManager
import com.hanzg.mipass.utils.LocaleHelper
import com.hanzg.mipass.utils.MasterPasswordManager
import com.hanzg.mipass.utils.SelfDestructManager
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MainActivityEntryPoint {
    fun appPreferences(): AppPreferences
    fun masterPasswordManager(): MasterPasswordManager
    fun biometricPromptManager(): BiometricPromptManager
    fun selfDestructManager(): SelfDestructManager
    fun localeHelper(): LocaleHelper
}

@AndroidEntryPoint
class MainActivity : androidx.fragment.app.FragmentActivity() {

    companion object {
        @Volatile
        var skipAuthOnce = false
        @Volatile
        var skipNextLockCheck = false
    }

    @Inject lateinit var biometricPromptManager: BiometricPromptManager
    @Inject lateinit var selfDestructManager: SelfDestructManager
    @Inject lateinit var localeHelper: LocaleHelper
    @Inject lateinit var masterPasswordManager: MasterPasswordManager

    private var pendingImportUri: Uri? = null
    private var hasAuthenticated = false
    private var pauseTimestamp: Long = 0L
    private var privacyOverlay: View? = null
    private var prefs: AppPreferences? = null

    // Auth states: "oobe" | "unlock" | "biometric" | "done"
    private var authState = "oobe"
    private var biometricGeneration = 0

    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return super.attachBaseContext(null)
        val wrapped = try {
            EntryPoints.get(base.applicationContext, MainActivityEntryPoint::class.java)
                .localeHelper().wrapContext(base)
        } catch (_: Exception) { base }
        super.attachBaseContext(wrapped)
    }

    fun reapplyLocale() {
        skipAuthOnce = true
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            prefs = EntryPoints.get(applicationContext, MainActivityEntryPoint::class.java).appPreferences()
        } catch (_: Exception) {}

        // 防截屏保护（可从设置关闭，默认开启）
        val screenshotProtection = runBlocking {
            try { prefs?.settingsFlow?.first()?.screenshotProtection ?: true }
            catch (_: Exception) { true }
        }
        if (screenshotProtection) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        handleIncomingIntent(intent)

        // 配置变更（深色模式切换等）恢复认证状态
        if (savedInstanceState?.getBoolean("auth_done", false) == true) {
            hasAuthenticated = true
            authState = "done"
            pauseTimestamp = savedInstanceState.getLong("pause_timestamp", System.currentTimeMillis())
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            lifecycle.addObserver(AppLifecycleObserver())
            renderContent()
            return
        }

        if (MainActivity.skipAuthOnce) {
            MainActivity.skipAuthOnce = false
            hasAuthenticated = true
            authState = "done"
            pauseTimestamp = System.currentTimeMillis()
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            lifecycle.addObserver(AppLifecycleObserver())
            renderContent()
            return
        }

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycle.addObserver(AppLifecycleObserver())

        // Determine auth state
        val biometricEnabled = runBlocking {
            try { prefs?.settingsFlow?.first()?.biometricEnabled ?: false }
            catch (_: Exception) { false }
        }
        authState = when {
            !masterPasswordManager.hasMasterPassword() -> "oobe"
            masterPasswordManager.shouldRequireMasterPassword() -> "unlock"
            biometricEnabled -> "biometric"
            else -> "unlock"
        }

        when (authState) {
            "oobe" -> renderSetupScreen(MasterPasswordScreenMode.SETUP)
            "unlock" -> renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
            "biometric" -> performBiometricAuth()
        }
    }

    private fun performBiometricAuth() {
        val biometricEnabled = runBlocking {
            try { prefs?.settingsFlow?.first()?.biometricEnabled ?: false }
            catch (_: Exception) { false }
        }
        val result = biometricPromptManager.canAuthenticate()
        if (biometricEnabled &&
            result is com.hanzg.mipass.utils.BiometricResult.Ready &&
            !masterPasswordManager.shouldRequireMasterPassword()) {
            authState = "biometric"
            val myGen = ++biometricGeneration
            biometricPromptManager.showPrompt(
                activity = this,
                title = "身份验证",
                subtitle = "验证身份以解锁 MiPass",
                negativeButtonText = "使用主密码",
                onSuccess = {
                    if (myGen != biometricGeneration) return@showPrompt
                    runBlocking {
                        selfDestructManager.resetAttempts()
                        masterPasswordManager.recordBootId()
                        masterPasswordManager.recordFingerprintDbHash()
                    }
                    authState = "done"
                    hasAuthenticated = true
                    removePrivacyOverlay()
                    renderContent()
                },
                onError = { errorCode, _ ->
                    if (myGen != biometricGeneration) return@showPrompt
                    runBlocking { selfDestructManager.recordFailedAttempt() }
                    authState = "unlock"
                    // 10=用户按返回键, 13=用户点"使用主密码" → 立即显示解锁界面
                    // 5=通用取消(含切后台), 其他=硬件错误 → 保留遮罩等 ON_START 处理
                    if (errorCode == 10 || errorCode == 13) {
                        removePrivacyOverlay()
                        renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
                    }
                },
                onFailed = {
                    if (myGen != biometricGeneration) return@showPrompt
                    runBlocking { selfDestructManager.recordFailedAttempt() }
                }
            )
        } else {
            // No biometric → use master password
            authState = "unlock"
            removePrivacyOverlay()
            renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
        }
    }

    private fun renderSetupScreen(mode: MasterPasswordScreenMode) {
        val mgr = masterPasswordManager
        val prefsRef = prefs
        setContent {
            MiPassTheme(themeMode = prefsRef?.let {
                val settings by it.settingsFlow.collectAsState(initial = AppSettings())
                settings.themeMode
            } ?: "system") {
                MasterPasswordSetupScreen(
                    mode = mode,
                    masterPasswordManager = mgr,
                    onSetupComplete = {
                        // OOBE 完成 → 直接进入应用（生物识别默认关闭，需用户手动开启）
                        authState = "done"
                        hasAuthenticated = true
                        mgr.recordBootId()
                        mgr.recordFingerprintDbHash()
                        renderContent()
                    },
                    onUnlockSuccess = {
                        authState = "done"
                        hasAuthenticated = true
                        mgr.recordBootId()
                        mgr.recordFingerprintDbHash()
                        renderContent()
                    },
                    onExit = { finish() }
                )
            }
        }
    }

    private fun renderContent() {
        val settingsFlow = prefs?.settingsFlow
        setContent {
            val settings by (settingsFlow?.collectAsState(initial = AppSettings())
                ?: remember { mutableStateOf(AppSettings()) })
            MiPassTheme(themeMode = settings.themeMode) {
                MiPassNavHost(pendingImportUri = pendingImportUri) {
                    pendingImportUri = null
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
        if (hasAuthenticated) renderContent()
    }

    private fun handleIncomingIntent(intent: android.content.Intent) {
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { pendingImportUri = it }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (hasAuthenticated) {
            outState.putBoolean("auth_done", true)
            outState.putLong("pause_timestamp", pauseTimestamp)
        }
    }

    private fun showPrivacyOverlay() {
        if (privacyOverlay != null) return
        val isDark = runBlocking {
            val themeMode = try { prefs?.settingsFlow?.first()?.themeMode ?: "system" }
                catch (_: Exception) { "system" }
            when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> {
                    val nightMode = resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
        }
        privacyOverlay = View(this).apply {
            setBackgroundColor(if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            alpha = 1f
        }
        (window.decorView as? android.view.ViewGroup)?.addView(
            privacyOverlay!!,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun removePrivacyOverlay() {
        privacyOverlay?.let { (window.decorView as? android.view.ViewGroup)?.removeView(it) }
        privacyOverlay = null
    }

    inner class AppLifecycleObserver : LifecycleEventObserver {
        // 缓存锁定时长，避免 onResume 中 runBlocking
        private var cachedLockTimeout: Int = 60

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // 真正切后台时记录时间
                    pauseTimestamp = System.currentTimeMillis()
                    if (hasAuthenticated) showPrivacyOverlay()
                    // 读取最新的锁定时长
                    cachedLockTimeout = runBlocking {
                        try { prefs?.settingsFlow?.first()?.lockTimeoutSeconds ?: 60 }
                        catch (_: Exception) { 60 }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // 导入/导出文件选择器回来后不验证
                    if (skipNextLockCheck) {
                        skipNextLockCheck = false
                        removePrivacyOverlay()
                        return
                    }
                    if (!hasAuthenticated) {
                        // 验证中途退出再回来 → 重置解锁状态
                        when (authState) {
                            "oobe" -> { /* 设置界面已显示，无需额外操作 */ }
                            "biometric" -> {
                                showPrivacyOverlay()
                                performBiometricAuth()
                            }
                            "unlock" -> {
                                // 生物识别开启则优先重试，否则保持主密码解锁
                                val bioEnabled = runBlocking {
                                    try { prefs?.settingsFlow?.first()?.biometricEnabled ?: false }
                                    catch (_: Exception) { false }
                                }
                                if (bioEnabled && !masterPasswordManager.shouldRequireMasterPassword()) {
                                    showPrivacyOverlay()
                                    performBiometricAuth()
                                } else {
                                    removePrivacyOverlay()
                                    renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
                                }
                            }
                        }
                        return
                    }
                    if (masterPasswordManager.shouldRequireMasterPassword()) {
                        hasAuthenticated = false
                        authState = "unlock"
                        removePrivacyOverlay()
                        renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
                        return
                    }
                    val elapsed = (System.currentTimeMillis() - pauseTimestamp) / 1000
                    // lockTimeout == 0 表示即时锁定，每次切后台都需验证
                    val shouldLock = cachedLockTimeout == 0 || (cachedLockTimeout > 0 && elapsed >= cachedLockTimeout)
                    if (shouldLock) {
                        hasAuthenticated = false
                        // 保留隐私遮罩到生物识别成功，避免应用内容暴露
                        performBiometricAuth()
                    } else {
                        removePrivacyOverlay()
                    }
                }
                else -> {}
            }
        }
    }
}
