package com.hanzg.mipass.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterPasswordManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mipass_master_pwd", Context.MODE_PRIVATE)
    private val lockoutPrefs: SharedPreferences =
        context.getSharedPreferences("mipass_lockout", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val KEY_LAST_BOOT_ID = "last_boot_id"
        private const val KEY_FAIL_COUNT = "fail_count"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_FP_DB_HASH = "fp_db_hash"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 150_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16

        private val LOCKOUT_TIERS = intArrayOf(3, 6, 10, 20)
        private val LOCKOUT_SECONDS = longArrayOf(30, 120, 600, 3600)
        private val secureRandom = SecureRandom()
    }

    fun hasMasterPassword(): Boolean {
        return prefs.getString(KEY_HASH, null) != null
    }

    fun setMasterPassword(password: String): SetResult {
        if (!isPasswordValid(password)) return SetResult.TooWeak
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
        recordBootId()
        resetLockout()
        recordFingerprintDbHash()
        return SetResult.Success
    }

    fun verifyMasterPassword(password: String): VerifyResult {
        // Check if currently locked out
        val lockoutUntil = lockoutPrefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        if (lockoutUntil > 0) {
            if (System.currentTimeMillis() < lockoutUntil) {
                val remaining = ((lockoutUntil - System.currentTimeMillis()) / 1000).toInt()
                return VerifyResult.LockedOut(remaining)
            }
            resetLockout()
        }

        val saltB64 = prefs.getString(KEY_SALT, null) ?: return VerifyResult.Error
        val hashB64 = prefs.getString(KEY_HASH, null) ?: return VerifyResult.Error
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val storedHash = Base64.decode(hashB64, Base64.NO_WRAP)
        val computedHash = hashPassword(password, salt)

        if (java.util.Arrays.equals(storedHash, computedHash)) {
            resetLockout()
            recordFingerprintDbHash()
            return VerifyResult.Success
        }

        // Record failed attempt → check lockout
        val newFailCount = lockoutPrefs.getInt(KEY_FAIL_COUNT, 0) + 1
        lockoutPrefs.edit().putInt(KEY_FAIL_COUNT, newFailCount).apply()

        val freezeSeconds = getLockoutSeconds(newFailCount)
        if (freezeSeconds > 0) {
            setLockout(freezeSeconds)
            return VerifyResult.LockedOut(freezeSeconds.toInt())
        }
        return VerifyResult.Failed(newFailCount)
    }

    fun getLockoutRemainingSeconds(): Int {
        val until = lockoutPrefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        if (until <= System.currentTimeMillis()) return 0
        return ((until - System.currentTimeMillis()) / 1000).toInt()
    }

    fun getFailCount(): Int = lockoutPrefs.getInt(KEY_FAIL_COUNT, 0)

    fun resetLockout() {
        lockoutPrefs.edit()
            .putInt(KEY_FAIL_COUNT, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0)
            .apply()
    }

    fun clearMasterPassword() {
        prefs.edit().clear().apply()
        resetLockout()
    }

    /** Check if fingerprint database changed since last auth */
    fun shouldRequireMasterPassword(): Boolean {
        // Check 1: reboot
        val currentBootId = getBootId()
        val storedBootId = prefs.getString(KEY_LAST_BOOT_ID, null)
        if (storedBootId != currentBootId) return true
        // Check 2: fingerprint database changed
        val currentFpHash = getFingerprintDbHash()
        val storedFpHash = prefs.getString(KEY_FP_DB_HASH, null)
        if (storedFpHash != null && storedFpHash != currentFpHash) return true
        return false
    }

    fun recordBootId() {
        prefs.edit().putString(KEY_LAST_BOOT_ID, getBootId()).apply()
    }

    fun recordFingerprintDbHash() {
        prefs.edit().putString(KEY_FP_DB_HASH, getFingerprintDbHash()).apply()
    }

    fun isPasswordStrong(password: String): Boolean = isPasswordValid(password)

    private fun isPasswordValid(password: String): Boolean {
        return password.length >= 8 &&
                password.any { it.isLetter() } &&
                password.any { it.isDigit() }
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    }

    private fun getLockoutSeconds(failCount: Int): Long {
        for (i in LOCKOUT_TIERS.indices) {
            if (failCount <= LOCKOUT_TIERS[i]) return if (i == 0 && failCount < LOCKOUT_TIERS[0]) 0
            else LOCKOUT_SECONDS[i]
        }
        return LOCKOUT_SECONDS.last()
    }

    private fun setLockout(seconds: Long) {
        lockoutPrefs.edit().putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + seconds * 1000).apply()
    }

    private fun getBootId(): String {
        return try {
            java.io.File("/proc/sys/kernel/random/boot_id").readText().trim()
        } catch (_: Exception) { "unknown" }
    }

    private fun getFingerprintDbHash(): String {
        return try {
            val settingsDb = java.io.File("/data/system/users/0/settings_fingerprint.xml")
            if (!settingsDb.exists()) return "none"
            val bytes = settingsDb.readBytes()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            Base64.encodeToString(digest, Base64.NO_WRAP)
        } catch (_: Exception) { "unknown" }
    }
}

sealed class VerifyResult {
    data object Success : VerifyResult()
    data class Failed(val attempts: Int) : VerifyResult()
    data class LockedOut(val remainingSeconds: Int) : VerifyResult()
    data object Error : VerifyResult()
}

enum class SetResult {
    Success, TooWeak
}
