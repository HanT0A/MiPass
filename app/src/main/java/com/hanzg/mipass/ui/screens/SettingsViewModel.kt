package com.hanzg.mipass.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanzg.mipass.data.local.AppPreferences
import com.hanzg.mipass.data.local.AppSettings
import com.hanzg.mipass.data.local.PasswordDao
import com.hanzg.mipass.data.local.SnapshotManager
import com.hanzg.mipass.domain.repository.PasswordRepository
import com.hanzg.mipass.utils.BiometricPromptManager
import com.hanzg.mipass.utils.MasterPasswordManager
import com.hanzg.mipass.utils.SetResult
import com.hanzg.mipass.utils.VerifyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val showLanguageSheet: Boolean = false,
    val showLockTimeoutSheet: Boolean = false,
    val showGeneratorRuleDialog: Boolean = false,
    val showProtectionDialog: Boolean = false,
    val showDisableBioVerify: Boolean = false,
    val showMasterPasswordDialog: Boolean = false,
    val showScreenshotDialog: Boolean = false,
    val showPrivacyPolicyDialog: Boolean = false,
    val showAppPermissionDialog: Boolean = false,
    val showUsageGuideDialog: Boolean = false,
    val showSnapshotSheet: Boolean = false,
    val showExportVerify: Boolean = false,
    val showExportMasterPwd: Boolean = false,
    val showExportDisclaimer: Boolean = false,
    val showExportPasscode: Boolean = false,
    val showImportDialog: Boolean = false,
    val showImportStrategy: Boolean = false,
    val showClearStep1: Boolean = false,
    val showClearStep2: Boolean = false,
    val showClearVerifyPwd: Boolean = false,
    val clearConfirmText: String = "",
    val importPendingUri: Uri? = null,
    val snapshotFiles: List<File> = emptyList(),
    val exportVerifying: Boolean = false,
    val clearVerifyPwd: String = ""
)

sealed interface SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val biometricManager: BiometricPromptManager,
    private val snapshotManager: SnapshotManager,
    private val passwordRepo: PasswordRepository,
    private val passwordDao: PasswordDao,
    private val masterPasswordManager: MasterPasswordManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    val settings: StateFlow<AppSettings> = prefs.settingsFlow

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(snapshotFiles = snapshotManager.listSnapshots()) }
        }
    }

    fun canAuthenticateBio(): Boolean = biometricManager.canAuthenticate() is com.hanzg.mipass.utils.BiometricResult.Ready
    fun getBiometricManager() = biometricManager
    fun getSnapshotManager() = snapshotManager
    fun getMasterPasswordManager() = masterPasswordManager
    fun getPrefs() = prefs
    fun getPasswordDao() = passwordDao
    suspend fun verifyMasterPasswordSuspend(pwd: String) = masterPasswordManager.verifyMasterPassword(pwd)

    // 对话框可见性控制
    fun showLanguageSheet() = update { it.copy(showLanguageSheet = true) }
    fun hideLanguageSheet() = update { it.copy(showLanguageSheet = false) }
    fun showLockTimeoutSheet() = update { it.copy(showLockTimeoutSheet = true) }
    fun hideLockTimeoutSheet() = update { it.copy(showLockTimeoutSheet = false) }
    fun showGeneratorRuleDialog() = update { it.copy(showGeneratorRuleDialog = true) }
    fun hideGeneratorRuleDialog() = update { it.copy(showGeneratorRuleDialog = false) }
    fun showProtectionDialog() = update { it.copy(showProtectionDialog = true) }
    fun hideProtectionDialog() = update { it.copy(showProtectionDialog = false) }
    fun showDisableBioVerify() = update { it.copy(showDisableBioVerify = true) }
    fun hideDisableBioVerify() = update { it.copy(showDisableBioVerify = false) }
    fun showMasterPasswordDialog() = update { it.copy(showMasterPasswordDialog = true) }
    fun hideMasterPasswordDialog() = update { it.copy(showMasterPasswordDialog = false) }
    fun showScreenshotDialog() = update { it.copy(showScreenshotDialog = true) }
    fun hideScreenshotDialog() = update { it.copy(showScreenshotDialog = false) }
    fun showPrivacyPolicyDialog() = update { it.copy(showPrivacyPolicyDialog = true) }
    fun hidePrivacyPolicyDialog() = update { it.copy(showPrivacyPolicyDialog = false) }
    fun showAppPermissionDialog() = update { it.copy(showAppPermissionDialog = true) }
    fun hideAppPermissionDialog() = update { it.copy(showAppPermissionDialog = false) }
    fun showUsageGuideDialog() = update { it.copy(showUsageGuideDialog = true) }
    fun hideUsageGuideDialog() = update { it.copy(showUsageGuideDialog = false) }

    // 快照
    fun showSnapshotSheet() {
        update { it.copy(showSnapshotSheet = true, snapshotFiles = snapshotManager.listSnapshots()) }
    }
    fun hideSnapshotSheet() = update { it.copy(showSnapshotSheet = false) }
    fun refreshSnapshots() {
        update { it.copy(snapshotFiles = snapshotManager.listSnapshots()) }
    }

    // 导出流程
    fun startExportFlow() = update { it.copy(showExportVerify = true) }
    fun cancelExportFlow() = update { it.copy(showExportVerify = false) }
    fun onExportVerified() = update { it.copy(showExportVerify = false, showExportDisclaimer = true) }
    fun onExportDisclaimerAccepted() = update { it.copy(showExportDisclaimer = false, showExportPasscode = true) }
    fun cancelExportPasscode() = update { it.copy(showExportPasscode = false) }
    fun setExportVerifying(v: Boolean) = update { it.copy(exportVerifying = v) }

    // 导入流程
    fun showImportDialog(uri: Uri? = null) = update { it.copy(showImportDialog = true, importPendingUri = uri) }
    fun hideImportDialog() = update { it.copy(showImportDialog = false, importPendingUri = null) }
    fun showImportStrategy() = update { it.copy(showImportStrategy = true) }
    fun hideImportStrategy() = update { it.copy(showImportStrategy = false) }

    // 清除流程
    fun showClearStep1() = update { it.copy(showClearStep1 = true) }
    fun hideClearStep1() = update { it.copy(showClearStep1 = false) }
    fun showClearStep2() = update { it.copy(showClearStep1 = false, showClearStep2 = true) }
    fun hideClearStep2() = update { it.copy(showClearStep2 = false) }
    fun showClearVerifyPwd() = update { it.copy(showClearVerifyPwd = true) }
    fun hideClearVerifyPwd() = update { it.copy(showClearVerifyPwd = false) }
    fun onClearConfirmTextChanged(text: String) = update { it.copy(clearConfirmText = text) }
    fun onClearVerifyPwdChanged(pwd: String) = update { it.copy(clearVerifyPwd = pwd) }

    // 语言变更
    fun setLanguage(langCode: String) {
        viewModelScope.launch { prefs.setLanguage(langCode); update { it.copy(showLanguageSheet = false) } }
    }

    // 锁定延时变更
    fun setLockTimeout(seconds: Int) {
        viewModelScope.launch { prefs.setLockTimeout(seconds); update { it.copy(showLockTimeoutSheet = false) } }
    }

    // 生成器默认规则变更
    fun updateGeneratorDefaults(
        length: Int, uppercase: Boolean, lowercase: Boolean, digits: Boolean, symbols: Boolean
    ) {
        viewModelScope.launch {
            prefs.setGeneratorLength(length)
            prefs.setGeneratorUppercase(uppercase)
            prefs.setGeneratorLowercase(lowercase)
            prefs.setGeneratorDigits(digits)
            prefs.setGeneratorSymbols(symbols)
            update { it.copy(showGeneratorRuleDialog = false) }
        }
    }

    // 系统验证开关
    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBiometricEnabled(enabled)
            update { it.copy(showProtectionDialog = false, showDisableBioVerify = false) }
        }
    }

    // 防截屏开关
    fun toggleScreenshotProtection(protect: Boolean) {
        viewModelScope.launch { prefs.setScreenshotProtection(protect); update { it.copy(showScreenshotDialog = false) } }
    }

    // 清除所有数据
    fun performClearAll() {
        viewModelScope.launch {
            try {
                passwordDao.deleteAll()
                masterPasswordManager.clearMasterPassword()
                snapshotManager.listSnapshots().forEach { it.delete() }
                prefs.clearAll()
                _events.emit(SettingsEvent.ShowToast("所有数据已安全清除"))
                update { it.copy(showClearStep2 = false, showClearVerifyPwd = false, clearConfirmText = "") }
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("清除失败: ${e.message}"))
            }
        }
    }

    // 导出验证（后台 PBKDF2）
    fun verifyForExport(password: String) {
        viewModelScope.launch {
            update { it.copy(exportVerifying = true) }
            val result = withContext(Dispatchers.IO) { masterPasswordManager.verifyMasterPassword(password) }
            update { it.copy(exportVerifying = false, showExportMasterPwd = false) }
            when (result) {
                is VerifyResult.Success ->
                    update { it.copy(showExportDisclaimer = true) }
                is VerifyResult.Failed ->
                    _events.emit(SettingsEvent.ShowToast("密钥错误"))
                is VerifyResult.LockedOut ->
                    _events.emit(SettingsEvent.ShowToast("已锁定 ${result.remainingSeconds} 秒"))
                else -> _events.emit(SettingsEvent.ShowToast("验证失败"))
            }
        }
    }

    // 关闭系统验证的身份验证
    fun verifyForDisableBio(password: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { masterPasswordManager.verifyMasterPassword(password) }
            when (result) {
                is VerifyResult.Success -> {
                    prefs.setBiometricEnabled(false)
                    update { it.copy(showDisableBioVerify = false) }
                    _events.emit(SettingsEvent.ShowToast("系统验证已关闭"))
                }
                else -> handleVerifyError(result)
            }
        }
    }

    // 清除操作的身份验证
    fun verifyForClearAll(password: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { masterPasswordManager.verifyMasterPassword(password) }
            when (result) {
                is VerifyResult.Success ->
                    update { it.copy(showClearVerifyPwd = false, showClearStep2 = true) }
                else -> handleVerifyError(result)
            }
        }
    }

    // 修改密钥
    fun setMasterPassword(pwd: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { masterPasswordManager.setMasterPassword(pwd) }
            val msg = when (result) {
                SetResult.Success -> "密钥已设置"
                SetResult.TooWeak -> "密钥至少 8 位，需包含字母和数字"
            }
            _events.emit(SettingsEvent.ShowToast(msg))
            update { it.copy(showMasterPasswordDialog = false) }
        }
    }

    fun changeMasterPassword(oldPwd: String, newPwd: String) {
        viewModelScope.launch {
            val verify = withContext(Dispatchers.IO) { masterPasswordManager.verifyMasterPassword(oldPwd) }
            when (verify) {
                is VerifyResult.Success -> {
                    val setResult = withContext(Dispatchers.IO) { masterPasswordManager.setMasterPassword(newPwd) }
                    val msg = when (setResult) {
                        SetResult.Success -> "密钥已更新"
                        SetResult.TooWeak -> "密钥至少 8 位，需包含字母和数字"
                    }
                    _events.emit(SettingsEvent.ShowToast(msg))
                    update { it.copy(showMasterPasswordDialog = false) }
                }
                else -> handleVerifyError(verify)
            }
        }
    }

    // 快照操作
    fun createSnapshot() {
        viewModelScope.launch {
            try {
                val passwords = passwordRepo.getAllPasswordsFlow().first()
                snapshotManager.createSnapshot(passwords.map {
                    com.hanzg.mipass.data.local.PasswordEntity(
                        id = it.id, type = it.type, name = it.name, url = it.url,
                        account = it.account, password = it.password, category = it.category,
                        notes = it.notes, iconUri = it.iconUri,
                        createdAt = it.createdAt, updatedAt = it.updatedAt
                    )
                })
                update { it.copy(snapshotFiles = snapshotManager.listSnapshots()) }
                _events.emit(SettingsEvent.ShowToast("快照已保存"))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("快照创建失败: ${e.message}"))
            }
        }
    }

    fun restoreSnapshot(file: File) {
        viewModelScope.launch {
            try {
                val passwords = snapshotManager.restoreSnapshot(file)
                passwordDao.deleteAll()
                passwords.forEach { passwordDao.insertPassword(it) }
                update { it.copy(showSnapshotSheet = false) }
                _events.emit(SettingsEvent.ShowToast("快照已恢复 (${passwords.size} 条)"))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("恢复失败: ${e.message}"))
            }
        }
    }

    private fun handleVerifyError(result: com.hanzg.mipass.utils.VerifyResult) {
        viewModelScope.launch {
            val msg = when (result) {
                is VerifyResult.Failed -> "密钥错误"
                is VerifyResult.LockedOut -> "已锁定 ${result.remainingSeconds} 秒"
                else -> "验证失败"
            }
            _events.emit(SettingsEvent.ShowToast(msg))
        }
    }

    private inline fun update(crossinline block: (SettingsUiState) -> SettingsUiState) {
        _uiState.update { block(it) }
    }
}
