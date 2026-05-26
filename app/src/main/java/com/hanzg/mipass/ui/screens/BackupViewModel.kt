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
    val identityVerified: Boolean = false,
    val exportFormat: com.hanzg.mipass.data.local.ExportFormat = com.hanzg.mipass.data.local.ExportFormat.MIPASS,
    val pendingImportFormat: com.hanzg.mipass.data.local.ImportFormat = com.hanzg.mipass.data.local.ImportFormat.UNKNOWN
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
        _uiState.update { it.copy(passcode = passcode, passcodeError = null) }
    }

    fun verifyIdentityForExport() {
        _uiState.update { it.copy(identityVerified = true) }
    }

    fun setPendingImportUri(uri: Uri?) {
        _uiState.update { it.copy(pendingImportUri = uri) }
    }

    fun exportAll() {
        backupEngine.cleanupExportDir(context)
        val passcode = _uiState.value.passcode
        if (!backupEngine.isValidPasscode(passcode)) {
            _uiState.update { it.copy(passcodeError = "提取码至少8位，需包含字母和数字") }
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

    fun shareExportedFile() {
        val file = _uiState.value.exportFile ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = _uiState.value.exportFormat.mimeType
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModelScope.launch {
            _shareEvent.emit(Intent.createChooser(shareIntent, "保存 MiPass 备份"))
        }
    }

    fun importFromUri(uri: Uri) {
        val passcode = _uiState.value.passcode
        if (!backupEngine.isValidPasscode(passcode)) {
            _uiState.update { it.copy(passcodeError = "提取码至少8位，需包含字母和数字") }
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

    fun importReplace(uri: Uri) {
        val passcode = _uiState.value.passcode
        if (!backupEngine.isValidPasscode(passcode)) {
            _uiState.update { it.copy(passcodeError = "提取码至少8位，需包含字母和数字") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val entries = backupEngine.importPasswords(context, uri, passcode)
                passwordDao.deleteAll()
                passwordDao.insertPasswords(entries)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = ImportResultUi(
                            totalCount = entries.size,
                            importedCount = entries.size,
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
        backupEngine.cleanupExportDir(context)
        _uiState.update { it.copy(
            result = null,
            importResult = null,
            identityVerified = false,
            exportFormat = com.hanzg.mipass.data.local.ExportFormat.MIPASS,
            pendingImportFormat = com.hanzg.mipass.data.local.ImportFormat.UNKNOWN
        ) }
    }

    fun dismissImport() {
        _uiState.update { it.copy(pendingImportUri = null, passcode = "", passcodeError = null) }
    }

    fun detectImportFormat(uri: Uri): com.hanzg.mipass.data.local.ImportFormat {
        val fileName = backupEngine.getFileName(context, uri)
        return backupEngine.detectImportFormat(fileName)
    }

    // === 通用格式导出 ===

    fun setExportFormat(format: com.hanzg.mipass.data.local.ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    fun exportGeneric() {
        backupEngine.cleanupExportDir(context)
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val allPasswords = passwordDao.getAllPasswords().first()
                if (allPasswords.isEmpty()) {
                    _uiState.update {
                        it.copy(isExporting = false, result = BackupResult(false, "没有可导出的数据"))
                    }
                    return@launch
                }
                val format = _uiState.value.exportFormat
                val content = when (format) {
                    com.hanzg.mipass.data.local.ExportFormat.MIPASS -> return@launch
                    com.hanzg.mipass.data.local.ExportFormat.JSON -> backupEngine.exportToJsonString(allPasswords)
                    com.hanzg.mipass.data.local.ExportFormat.CSV -> backupEngine.exportToCsvString(allPasswords)
                }
                val exportDir = java.io.File(context.cacheDir, "exports").also { it.mkdirs() }
                val timestamp = System.currentTimeMillis()
                val fileName = "MiPass_Export_${timestamp}${format.extension}"
                val file = java.io.File(exportDir, fileName)
                file.writeText(content, Charsets.UTF_8)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportFileName = fileName,
                        exportFile = file,
                        result = BackupResult(true, "导出成功 (${allPasswords.size} 条)")
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExporting = false, result = BackupResult(false, "导出失败: ${e.message}"))
                }
            }
        }
    }

    // === 通用格式导入 ===

    fun setPendingImportFormat(format: com.hanzg.mipass.data.local.ImportFormat) {
        _uiState.update { it.copy(pendingImportFormat = format) }
    }

    fun importGenericFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val format = _uiState.value.pendingImportFormat
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw SecurityException("无法读取文件")
                val entries = when (format) {
                    com.hanzg.mipass.data.local.ImportFormat.JSON -> backupEngine.deserializeFromJsonString(content)
                    com.hanzg.mipass.data.local.ImportFormat.CSV -> backupEngine.importFromCsvString(content)
                    else -> throw IllegalArgumentException("不支持的导入格式")
                }
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
                        passcodeError = "导入失败：${e.message}",
                        passcode = ""
                    )
                }
            }
        }
    }

    fun importGenericReplaceFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val format = _uiState.value.pendingImportFormat
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw SecurityException("无法读取文件")
                val entries = when (format) {
                    com.hanzg.mipass.data.local.ImportFormat.JSON -> backupEngine.deserializeFromJsonString(content)
                    com.hanzg.mipass.data.local.ImportFormat.CSV -> backupEngine.importFromCsvString(content)
                    else -> throw IllegalArgumentException("不支持的导入格式")
                }
                passwordDao.deleteAll()
                passwordDao.insertPasswords(entries)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = ImportResultUi(
                            totalCount = entries.size,
                            importedCount = entries.size,
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
                        passcodeError = "导入失败：${e.message}",
                        passcode = ""
                    )
                }
            }
        }
    }
}
