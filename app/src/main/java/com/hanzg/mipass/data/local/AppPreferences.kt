package com.hanzg.mipass.data.local

import androidx.compose.runtime.Immutable
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class AppSettings(
    val themeMode: String = "system",
    val language: String = "zh",
    val generatorLength: Int = 8,
    val generatorUppercase: Boolean = true,
    val generatorLowercase: Boolean = true,
    val generatorDigits: Boolean = true,
    val generatorSymbols: Boolean = true,
    val biometricEnabled: Boolean = false,
    val lockEnabled: Boolean = true,
    val lockTimeoutSeconds: Int = 60,
    val selfDestructEnabled: Boolean = false,
    val selfDestructAttempts: Int = 0,
    val selfDestructThreshold: Int = 10,
    val screenshotProtection: Boolean = true
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mipass_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(readSettings())
    val settingsFlow: Flow<AppSettings> = _settingsFlow.asStateFlow()

    private fun readSettings(): AppSettings {
        return AppSettings(
            themeMode = prefs.getString("theme_mode", "system") ?: "system",
            language = prefs.getString("language", "zh") ?: "zh",
            generatorLength = prefs.getInt("generator_length", 8),
            generatorUppercase = prefs.getBoolean("generator_uppercase", true),
            generatorLowercase = prefs.getBoolean("generator_lowercase", true),
            generatorDigits = prefs.getBoolean("generator_digits", true),
            generatorSymbols = prefs.getBoolean("generator_symbols", true),
            biometricEnabled = prefs.getBoolean("biometric_enabled", false),
            lockEnabled = prefs.getBoolean("lock_enabled", true),
            lockTimeoutSeconds = prefs.getInt("lock_timeout_seconds", 60),
            selfDestructEnabled = prefs.getBoolean("self_destruct_enabled", false),
            selfDestructAttempts = prefs.getInt("self_destruct_attempts", 0),
            selfDestructThreshold = prefs.getInt("self_destruct_threshold", 10),
            screenshotProtection = prefs.getBoolean("screenshot_protection", true)
        )
    }

    private fun updateAndNotify(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply { block(); apply() }
        _settingsFlow.value = readSettings()
    }

    fun read(): AppSettings = readSettings()

    fun setThemeMode(mode: String) { updateAndNotify { putString("theme_mode", mode) } }
    fun setLanguage(lang: String) { updateAndNotify { putString("language", lang) } }
    fun setGeneratorLength(length: Int) { updateAndNotify { putInt("generator_length", length) } }
    fun setGeneratorUppercase(enabled: Boolean) { updateAndNotify { putBoolean("generator_uppercase", enabled) } }
    fun setGeneratorLowercase(enabled: Boolean) { updateAndNotify { putBoolean("generator_lowercase", enabled) } }
    fun setGeneratorDigits(enabled: Boolean) { updateAndNotify { putBoolean("generator_digits", enabled) } }
    fun setGeneratorSymbols(enabled: Boolean) { updateAndNotify { putBoolean("generator_symbols", enabled) } }
    fun setBiometricEnabled(enabled: Boolean) { updateAndNotify { putBoolean("biometric_enabled", enabled) } }
    fun setLockEnabled(enabled: Boolean) { updateAndNotify { putBoolean("lock_enabled", enabled) } }
    fun setLockTimeout(seconds: Int) { updateAndNotify { putInt("lock_timeout_seconds", seconds) } }
    fun setSelfDestructEnabled(enabled: Boolean) { updateAndNotify { putBoolean("self_destruct_enabled", enabled) } }
    fun setSelfDestructAttempts(attempts: Int) { updateAndNotify { putInt("self_destruct_attempts", attempts) } }
    fun setSelfDestructThreshold(threshold: Int) { updateAndNotify { putInt("self_destruct_threshold", threshold) } }
    fun setScreenshotProtection(enabled: Boolean) { updateAndNotify { putBoolean("screenshot_protection", enabled) } }

    fun clearAll() {
        prefs.edit().clear().apply()
        _settingsFlow.value = AppSettings()
    }
}
