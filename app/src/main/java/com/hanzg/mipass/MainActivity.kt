package com.hanzg.mipass

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import com.hanzg.mipass.utils.BiometricResult
import com.hanzg.mipass.utils.KeyStoreManager
import com.hanzg.mipass.utils.LocaleHelper
import com.hanzg.mipass.utils.AuthState
import com.hanzg.mipass.utils.MasterPasswordManager
import com.hanzg.mipass.utils.SelfDestructManager
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    fun keyStoreManager(): KeyStoreManager
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

    private var authState: AuthState = AuthState.OOBE
    private var biometricGeneration = 0

    private val mainEntryPoint by lazy {
        EntryPoints.get(applicationContext, MainActivityEntryPoint::class.java)
    }

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

        // FLAG_SECURE 默认开启（安全优先），异步读取设置后可按需关闭
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val screenshotProtection = try {
                prefs?.settingsFlow?.first()?.screenshotProtection ?: true
            } catch (_: Exception) { true }
            if (!screenshotProtection) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        handleIncomingIntent(intent)

        // 配置变更（深色模式切换等）恢复认证状态
        if (savedInstanceState?.getBoolean("auth_done", false) == true) {
            hasAuthenticated = true
            authState = AuthState.DONE
            pauseTimestamp = savedInstanceState.getLong("pause_timestamp", System.currentTimeMillis())
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            lifecycle.addObserver(AppLifecycleObserver())
            renderContent()
            return
        }

        if (MainActivity.skipAuthOnce) {
            MainActivity.skipAuthOnce = false
            hasAuthenticated = true
            authState = AuthState.DONE
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
            !masterPasswordManager.hasMasterPassword() -> AuthState.OOBE
            masterPasswordManager.shouldRequireMasterPassword() -> AuthState.UNLOCK
            biometricEnabled -> AuthState.BIOMETRIC
            else -> AuthState.UNLOCK
        }

        when (authState) {
            AuthState.OOBE -> renderSetupScreen(MasterPasswordScreenMode.SETUP)
            AuthState.UNLOCK -> renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
            AuthState.BIOMETRIC -> performBiometricAuth()
            AuthState.DONE -> renderContent()
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
            authState = AuthState.BIOMETRIC
            val myGen = ++biometricGeneration

            try {
                val keyStoreManager = mainEntryPoint.keyStoreManager()
                val kek = keyStoreManager.getOrCreateKEK()
                val dekPrefs = getSharedPreferences("mipass_dek_prefs", Context.MODE_PRIVATE)
                val ivB64 = dekPrefs.getString("dek_iv", null) ?: throw IllegalStateException("No DEK IV")
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                val cipher = keyStoreManager.getDecryptCipher(kek, iv)

                biometricPromptManager.showPromptWithCrypto(
                    activity = this,
                    cipher = cipher,
                    title = "身份验证",
                    subtitle = "验证身份以解锁 MiPass",
                    negativeButtonText = "使用主密码",
                    onSuccess = { resultCipher ->
                        if (myGen != biometricGeneration) return@showPromptWithCrypto
                        runBlocking(Dispatchers.IO) {
                            try {
                                // 使用 TEE 授权的 cipher 解密 DEK
                                val encryptedDekB64 = getSharedPreferences("mipass_dek_prefs", MODE_PRIVATE)
                                    .getString("encrypted_dek", null) ?: throw IllegalStateException("No encrypted DEK")
                                val encryptedDek = Base64.decode(encryptedDekB64, Base64.NO_WRAP)
                                val decryptedDek = resultCipher.doFinal(encryptedDek)
                                // 保存解密后的 DEK，供 MiPassDatabase 使用（避免再次访问未授权的 KEK）
                                getSharedPreferences("mipass_dek_prefs", MODE_PRIVATE).edit()
                                    .putString("temp_dek", Base64.encodeToString(decryptedDek, Base64.NO_WRAP))
                                    .commit()
                                java.util.Arrays.fill(decryptedDek, 0.toByte())
                                selfDestructManager.resetAttempts()
                            } catch (e: Exception) {
                                android.util.Log.e("MiPass", "DEK decryption failed after biometric", e)
                            }
                        }
                        runBlocking {
                            masterPasswordManager.recordBootId()
                            masterPasswordManager.recordFingerprintDbHash()
                        }
                        authState = AuthState.DONE
                        hasAuthenticated = true
                        removePrivacyOverlay()
                        renderContent()
                    },
                    onError = { errorCode, _ ->
                        if (myGen != biometricGeneration) return@showPromptWithCrypto
                        runBlocking(Dispatchers.IO) { selfDestructManager.recordFailedAttempt() }
                        authState = AuthState.UNLOCK
                        if (errorCode == 10 || errorCode == 13) {
                            removePrivacyOverlay()
                            renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
                        }
                    },
                    onFailed = {
                        if (myGen != biometricGeneration) return@showPromptWithCrypto
                        runBlocking(Dispatchers.IO) { selfDestructManager.recordFailedAttempt() }
                    }
                )
                return
            } catch (_: Exception) {
                // CryptoObject 设置失败，回退到主密码解锁
            }
        }

        authState = AuthState.UNLOCK
        removePrivacyOverlay()
        renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
    }

    private fun renderSetupScreen(mode: MasterPasswordScreenMode) {
        val mgr = masterPasswordManager
        val prefsRef = prefs
        val bioManager = biometricPromptManager
        val shouldRequirePwd = mgr.shouldRequireMasterPassword()
        val bioEnabled = runBlocking {
            try { prefs?.settingsFlow?.first()?.biometricEnabled ?: false }
            catch (_: Exception) { false }
        }
        val bioCapable = bioManager.canAuthenticate() is BiometricResult.Ready
        val canRetryBio = mode == MasterPasswordScreenMode.UNLOCK && bioEnabled && bioCapable && !shouldRequirePwd
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
                        authState = AuthState.DONE
                        hasAuthenticated = true
                        mgr.recordBootId()
                        mgr.recordFingerprintDbHash()
                        renderContent()
                    },
                    onUnlockSuccess = {
                        authState = AuthState.DONE
                        hasAuthenticated = true
                        mgr.recordBootId()
                        mgr.recordFingerprintDbHash()
                        renderContent()
                    },
                    onExit = { finish() },
                    onBiometricAuth = if (canRetryBio) { { performBiometricAuth() } } else null
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
        // 默认显示黑色遮罩（偏安全）
        privacyOverlay = View(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            alpha = 1f
        }
        (window.decorView as? android.view.ViewGroup)?.addView(
            privacyOverlay!!,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // 异步获取主题色
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val isDark = try {
                val themeMode = prefs?.settingsFlow?.first()?.themeMode ?: "system"
                when (themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> {
                        (resources.configuration.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                    }
                }
            } catch (_: Exception) { true }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                privacyOverlay?.setBackgroundColor(
                    if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
    }

    private fun removePrivacyOverlay() {
        privacyOverlay?.let { (window.decorView as? android.view.ViewGroup)?.removeView(it) }
        privacyOverlay = null
    }

    inner class AppLifecycleObserver : LifecycleEventObserver {
        // 缓存锁定时长和生物识别状态，避免 onResume 中 runBlocking
        private var cachedLockTimeout: Int = 60
        private var cachedBiometricEnabled: Boolean = false

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // 真正切后台时记录时间
                    pauseTimestamp = System.currentTimeMillis()
                    if (hasAuthenticated) showPrivacyOverlay()
                    // 读取最新的锁定时长和生物识别状态
                    runBlocking {
                        try {
                            val s = prefs?.settingsFlow?.first()
                            cachedLockTimeout = s?.lockTimeoutSeconds ?: 60
                            cachedBiometricEnabled = s?.biometricEnabled ?: false
                        } catch (_: Exception) {
                            cachedLockTimeout = 60
                            cachedBiometricEnabled = false
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // 同步系统生物识别状态：用户可能在设置中关闭了系统生物识别
                    if (cachedBiometricEnabled) {
                        val bioResult = biometricPromptManager.canAuthenticate()
                        if (bioResult !is BiometricResult.Ready) {
                            cachedBiometricEnabled = false
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                prefs?.setBiometricEnabled(false)
                            }
                        }
                    }
                    // 导入/导出文件选择器回来后不验证
                    if (skipNextLockCheck) {
                        skipNextLockCheck = false
                        removePrivacyOverlay()
                        return
                    }
                    if (!hasAuthenticated) {
                        // 验证中途退出再回来 → 重置解锁状态
                        when (authState) {
                            AuthState.OOBE -> { /* 设置界面已显示，无需额外操作 */ }
                            AuthState.BIOMETRIC -> {
                                showPrivacyOverlay()
                                performBiometricAuth()
                            }
                            AuthState.UNLOCK -> {
                                // 生物识别开启则优先重试，否则保持主密码解锁
                                val bioEnabled = cachedBiometricEnabled
                                if (bioEnabled && !masterPasswordManager.shouldRequireMasterPassword()) {
                                    showPrivacyOverlay()
                                    performBiometricAuth()
                                } else {
                                    removePrivacyOverlay()
                                    renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
                                }
                            }
                            AuthState.DONE -> { /* already authenticated */ }
                        }
                        return
                    }
                    if (masterPasswordManager.shouldRequireMasterPassword()) {
                        hasAuthenticated = false
                        authState = AuthState.UNLOCK
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
