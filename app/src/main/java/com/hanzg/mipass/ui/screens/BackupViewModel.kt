package com.hanzg.mipass.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanzg.mipass.data.local.BackupEngine
import com.hanzg.mipass.data.local.PasswordDao
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.utils.MasterPasswordManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportFileName: String = "",
    val passcode: String = "",
    val passcodeError: String? = null,
    val result: BackupResult? = null,
    val pendingImportUri: Uri? = null,
    val importResult: ImportResultUi? = null,
    val exportFile: File? = null,
    val identityVerified: Boolean = false
)

data class ImportResultUi(
    val totalCount: Int,
    val importedCount: Int,
    val skippedCount: Int
)

data class BackupResult(
    val success: Boolean,
    val message: String = ""
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupEngine: BackupEngine,
    private val passwordDao: PasswordDao,
    private val masterPasswordManager: MasterPasswordManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _shareEvent = MutableSharedFlow<Intent>()
    val shareEvent: SharedFlow<Intent> = _shareEvent.asSharedFlow()

    fun onPasscodeChanged(passcode: String) {
        if (passcode.length <= 6) {
            _uiState.update { it.copy(passcode = passcode, passcodeError = null) }
        }
    }

    fun verifyIdentityForExport() {
        _uiState.update { it.copy(identityVerified = true) }
    }

    fun setPendingImportUri(uri: Uri?) {
        _uiState.update { it.copy(pendingImportUri = uri) }
    }

    /**
     * 导出全部（总是全量导出，移除范围选择）
     */
    fun exportAll() {
        val passcode = _uiState.value.passcode
        if (!backupEngine.isValidPasscode(passcode)) {
            _uiState.update { it.copy(passcodeError = "请输入 6 位数字提取码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val allPasswords = passwordDao.getAllPasswords().first()
                if (allPasswords.isEmpty()) {
                    _uiState.update {
                        it.copy(isExporting = false, result = BackupResult(false, "没有可导出的数据"), passcode = "")
                    }
                    return@launch
                }
                val exportResult = backupEngine.exportPasswords(context, allPasswords, passcode)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportFileName = exportResult.fileName,
                        exportFile = exportResult.file,
                        result = BackupResult(true, "导出成功 (${allPasswords.size} 条)"),
                        passcode = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExporting = false, result = BackupResult(false, "导出失败: ${e.message}"), passcode = "")
                }
            }
        }
    }

    /** 分享导出文件 */
    fun shareExportedFile() {
        val file = _uiState.value.exportFile ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModelScope.launch {
            _shareEvent.emit(Intent.createChooser(shareIntent, "保存 MiPass 备份"))
        }
    }

    /**
     * 导入 .mipass 文件
     */
    fun importFromUri(uri: Uri) {
        val passcode = _uiState.value.passcode
        if (!backupEngine.isValidPasscode(passcode)) {
            _uiState.update { it.copy(passcodeError = "请输入 6 位数字提取码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val entries = backupEngine.importPasswords(context, uri, passcode)
                val mergeResult = backupEngine.mergeWithDedup(entries, passwordDao)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = ImportResultUi(
                            totalCount = mergeResult.totalCount,
                            importedCount = mergeResult.importedCount,
                            skippedCount = mergeResult.skippedCount
                        ),
                        pendingImportUri = null,
                        passcode = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        passcodeError = "导入失败：提取码错误或文件已损坏",
                        passcode = ""
                    )
                }
            }
        }
    }

    /**
     * 导入并替换（先清空再导入）
     */
    fun importReplace(uri: Uri) {
        val passcode = _uiState.value.passcode
        if (!backupEngine.isValidPasscode(passcode)) {
            _uiState.update { it.copy(passcodeError = "请输入 6 位数字提取码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val entries = backupEngine.importPasswords(context, uri, passcode)
                // 清空现有数据
                passwordDao.deleteAll()
                // 全量导入
                var imported = 0
                entries.forEach { entry ->
                    passwordDao.insertPassword(entry)
                    imported++
                }
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = ImportResultUi(
                            totalCount = entries.size,
                            importedCount = imported,
                            skippedCount = 0
                        ),
                        pendingImportUri = null,
                        passcode = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        passcodeError = "导入失败：提取码错误或文件已损坏",
                        passcode = ""
                    )
                }
            }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(result = null, importResult = null, identityVerified = false) }
    }

    fun dismissImport() {
        _uiState.update { it.copy(pendingImportUri = null, passcode = "", passcodeError = null) }
    }
}
