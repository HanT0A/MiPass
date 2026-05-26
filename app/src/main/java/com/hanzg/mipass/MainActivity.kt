package com.hanzg.mipass

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.hanzg.mipass.data.local.AppPreferences
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
import javax.inject.Inject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MainActivityEntryPoint {
    fun appPreferences(): AppPreferences
    fun masterPasswordManager(): MasterPasswordManager
    fun biometricPromptManager(): BiometricPromptManager
    fun localeHelper(): LocaleHelper
    fun keyStoreManager(): KeyStoreManager
}

@AndroidEntryPoint
class MainActivity : androidx.fragment.app.FragmentActivity() {

    companion object {
        @Volatile
        private var _skipAuthOnce = false
        @Volatile
        private var _skipNextLockCheck = false

        fun requestAuthBypass() {
            _skipAuthOnce = true
            _skipNextLockCheck = true
        }

        fun consumeSkipAuthOnce(): Boolean {
            val v = _skipAuthOnce
            _skipAuthOnce = false
            return v
        }

        fun consumeSkipNextLockCheck(): Boolean {
            val v = _skipNextLockCheck
            _skipNextLockCheck = false
            return v
        }
    }

    @Inject lateinit var biometricPromptManager: BiometricPromptManager
    @Inject lateinit var localeHelper: LocaleHelper
    @Inject lateinit var masterPasswordManager: MasterPasswordManager

    private var pendingImportUri: Uri? = null
    private var hasAuthenticated = false
    private var pauseTimestamp: Long = 0L
    private var privacyOverlay: View? = null
    private var prefs: AppPreferences? = null

    private var authState: AuthState = AuthState.OOBE
    private var biometricGeneration = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
        requestAuthBypass()
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

        if (consumeSkipAuthOnce()) {
            hasAuthenticated = true
            authState = AuthState.DONE
            pauseTimestamp = System.currentTimeMillis()
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            lifecycle.addObserver(AppLifecycleObserver())
            renderContent()
            return
        }

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // 读取当前设置用于初始化观察者缓存
        val settings = prefs?.read()
        val biometricEnabled = settings?.biometricEnabled ?: false
        val lockTimeout = settings?.lockTimeoutSeconds ?: 60
        val observer = AppLifecycleObserver()
        observer.cachedBiometricEnabled = biometricEnabled
        observer.cachedLockTimeout = lockTimeout
        lifecycle.addObserver(observer)
        when {
            !masterPasswordManager.hasMasterPassword() -> {
                authState = AuthState.OOBE
                renderSetupScreen(MasterPasswordScreenMode.SETUP)
            }
            else -> {
                authState = AuthState.UNLOCK
                removePrivacyOverlay()
                val bioReady = biometricPromptManager.canAuthenticate() is BiometricResult.Ready
                val requirePwd = masterPasswordManager.shouldRequireMasterPassword()
                if (biometricEnabled && bioReady && !requirePwd) {
                    showPrivacyOverlay()
                    postSystemVerification()
                } else if (requirePwd) {
                    renderSetupScreen(MasterPasswordScreenMode.UNLOCK, "检测到系统安全变更，请使用恢复密钥解锁")
                } else if (!biometricEnabled) {
                    renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
                } else {
                    renderSetupScreen(MasterPasswordScreenMode.UNLOCK, "系统验证暂不可用，请检查是否已设置屏幕锁")
                }
            }
        }
    }

    private fun performSystemVerification() {
        authState = AuthState.BIOMETRIC
        showPrivacyOverlay()
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
                subtitle = "使用系统验证解锁 MiPass",
                onSuccess = { resultCipher ->
                    if (myGen != biometricGeneration) return@showPromptWithCrypto
                    var dekOk = false
                    runBlocking(Dispatchers.IO) {
                        try {
                            val encryptedDekB64 = getSharedPreferences("mipass_dek_prefs", MODE_PRIVATE)
                                .getString("encrypted_dek", null) ?: throw IllegalStateException("No encrypted DEK")
                            val encryptedDek = Base64.decode(encryptedDekB64, Base64.NO_WRAP)
                            val decryptedDek = resultCipher.doFinal(encryptedDek)
                            getSharedPreferences("mipass_dek_prefs", MODE_PRIVATE).edit()
                                .putString("temp_dek", Base64.encodeToString(decryptedDek, Base64.NO_WRAP))
                                .commit()
                            java.util.Arrays.fill(decryptedDek, 0.toByte())
                            dekOk = true
                        } catch (e: Exception) {
                            android.util.Log.e("MiPass", "DEK decryption failed after system verification", e)
                        }
                    }
                    if (!dekOk) {
                        authState = AuthState.UNLOCK
                        removePrivacyOverlay()
                        renderSetupScreen(MasterPasswordScreenMode.UNLOCK, "系统错误：无法解密数据，请尝试恢复密钥解锁")
                        return@showPromptWithCrypto
                    }
                    runBlocking {
                        masterPasswordManager.recordBootId()
                    }
                    authState = AuthState.DONE
                    hasAuthenticated = true
                    removePrivacyOverlay()
                    renderContent()
                },
                onError = { errorCode, _ ->
                    if (myGen != biometricGeneration) return@showPromptWithCrypto
                    if (errorCode == 10 || errorCode == 13) finish()
                },
                onFailed = {
                    if (myGen != biometricGeneration) return@showPromptWithCrypto
                    // 生物识别失败，系统自行处理
                }
            )
        } catch (_: Exception) {
            authState = AuthState.UNLOCK
            removePrivacyOverlay()
            renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
        }
    }

    private fun renderSetupScreen(mode: MasterPasswordScreenMode, unlockHint: String? = null) {
        val mgr = masterPasswordManager
        setContent {
            MiPassTheme {
                MasterPasswordSetupScreen(
                    mode = mode,
                    masterPasswordManager = mgr,
                    onSetupComplete = {
                        authState = AuthState.DONE
                        hasAuthenticated = true
                        mgr.recordBootId()
                        renderContent()
                    },
                    onUnlockSuccess = {
                        authState = AuthState.DONE
                        hasAuthenticated = true
                        mgr.recordBootId()
                        renderContent()
                    },
                    onExit = { finish() },
                    unlockHint = unlockHint
                )
            }
        }
    }

    private fun renderContent() {
        setContent {
            MiPassTheme {
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
        val isDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        privacyOverlay = View(this).apply {
            setBackgroundColor(
                if (isDark) android.graphics.Color.parseColor("#FF0C0E12")
                else android.graphics.Color.parseColor("#FFF6F7FA")
            )
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

    private fun postSystemVerification() {
        mainHandler.post { performSystemVerification() }
    }

    inner class AppLifecycleObserver : LifecycleEventObserver {
        var cachedLockTimeout: Int = 60
        var cachedBiometricEnabled: Boolean = false

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    pauseTimestamp = System.currentTimeMillis()
                    if (hasAuthenticated) showPrivacyOverlay()
                    val s = prefs?.read()
                    if (s != null) {
                        cachedLockTimeout = s.lockTimeoutSeconds
                        cachedBiometricEnabled = s.biometricEnabled
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (cachedBiometricEnabled) {
                        val bioResult = biometricPromptManager.canAuthenticate()
                        if (bioResult !is BiometricResult.Ready) {
                            cachedBiometricEnabled = false
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                prefs?.setBiometricEnabled(false)
                            }
                        }
                    }
                    if (consumeSkipNextLockCheck()) {
                        removePrivacyOverlay()
                        return
                    }
                    if (!hasAuthenticated) {
                        when (authState) {
                            AuthState.OOBE -> { }
                            AuthState.BIOMETRIC -> postSystemVerification()
                            AuthState.UNLOCK -> {
                                val bioReady = biometricPromptManager.canAuthenticate() is BiometricResult.Ready
                                val requirePwd = masterPasswordManager.shouldRequireMasterPassword()
                                if (cachedBiometricEnabled && bioReady && !requirePwd) {
                                    postSystemVerification()
                                } else if (requirePwd) {
                                    removePrivacyOverlay()
                                    renderSetupScreen(MasterPasswordScreenMode.UNLOCK, "检测到系统安全变更，请使用恢复密钥解锁")
                                } else if (!cachedBiometricEnabled) {
                                    removePrivacyOverlay()
                                    renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
                                } else {
                                    removePrivacyOverlay()
                                    renderSetupScreen(MasterPasswordScreenMode.UNLOCK, "系统验证暂不可用，请检查是否已设置屏幕锁")
                                }
                            }
                            AuthState.DONE -> { }
                        }
                        return
                    }
                    if (masterPasswordManager.shouldRequireMasterPassword()) {
                        hasAuthenticated = false
                        authState = AuthState.UNLOCK
                        removePrivacyOverlay()
                        renderSetupScreen(MasterPasswordScreenMode.UNLOCK, "检测到系统安全变更，请使用恢复密钥解锁")
                        return
                    }
                    val elapsed = (System.currentTimeMillis() - pauseTimestamp) / 1000
                    val shouldLock = cachedLockTimeout == 0 || (cachedLockTimeout > 0 && elapsed >= cachedLockTimeout)
                    if (shouldLock) {
                        hasAuthenticated = false
                        val bioReady = biometricPromptManager.canAuthenticate() is BiometricResult.Ready
                        val requirePwd = masterPasswordManager.shouldRequireMasterPassword()
                        if (cachedBiometricEnabled && bioReady && !requirePwd) {
                            postSystemVerification()
                        } else if (requirePwd) {
                            removePrivacyOverlay()
                            renderSetupScreen(MasterPasswordScreenMode.UNLOCK, "检测到系统安全变更，请使用恢复密钥解锁")
                        } else if (!cachedBiometricEnabled) {
                            removePrivacyOverlay()
                            renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
                        } else {
                            removePrivacyOverlay()
                            renderSetupScreen(MasterPasswordScreenMode.UNLOCK, "系统验证暂不可用，请检查是否已设置屏幕锁")
                        }
                    } else {
                        removePrivacyOverlay()
                    }
                }
                else -> {}
            }
        }
    }
}
