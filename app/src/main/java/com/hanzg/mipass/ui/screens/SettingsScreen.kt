package com.hanzg.mipass.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*

import com.hanzg.mipass.data.local.PasswordDao
import com.hanzg.mipass.utils.BiometricPromptManager
import com.hanzg.mipass.utils.BiometricResult
import com.hanzg.mipass.ui.components.PasswordTextField
import com.hanzg.mipass.ui.navigation.MiPassBottomBar
import com.hanzg.mipass.ui.navigation.NavRoutes
import com.hanzg.mipass.utils.VerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    pendingImportUri: Uri? = null,
    backupViewModel: BackupViewModel = hiltViewModel(),
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showExportVerify by remember { mutableStateOf(false) }
    var showExportMasterPwd by remember { mutableStateOf(false) }
    var bioRetryKey by remember { mutableStateOf(0) }
    var showExportFormat by remember { mutableStateOf(false) }
    var showExportDisclaimer by remember { mutableStateOf(false) }
    var showExportPasscode by remember { mutableStateOf(false) }
    var showUnencryptedWarning by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showImportStrategy by remember { mutableStateOf(false) }
    var importPendingUri by remember { mutableStateOf<Uri?>(null) }

    var showLanguageSheet by remember { mutableStateOf(false) }
    var showLockTimeoutSheet by remember { mutableStateOf(false) }

    var showGeneratorRuleDialog by remember { mutableStateOf(false) }
    var showProtectionDialog by remember { mutableStateOf(false) }
    var showDisableBioVerify by remember { mutableStateOf(false) }
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var showScreenshotDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showAppPermissionDialog by remember { mutableStateOf(false) }
    var showUsageGuideDialog by remember { mutableStateOf(false) }
    var showSnapshotSheet by remember { mutableStateOf(false) }
    var showClearStep1 by remember { mutableStateOf(false) }
    var showClearStep2 by remember { mutableStateOf(false) }
    var showClearVerifyPwd by remember { mutableStateOf(false) }
    var clearConfirmText by remember { mutableStateOf("") }

    val backupState by backupViewModel.uiState.collectAsState()

    LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null) {
            backupViewModel.setPendingImportUri(pendingImportUri)
            showImportDialog = true
        }
    }

    LaunchedEffect(Unit) {
        backupViewModel.shareEvent.collect { intent ->
            context.startActivity(intent)
        }
    }

    val languageDisplay = "简体中文"

    val generatorDisplay = "${settings.generatorLength} / " + buildString {
        val parts = mutableListOf<String>()
        if (settings.generatorUppercase) parts.add("A-Z")
        if (settings.generatorLowercase) parts.add("a-z")
        if (settings.generatorDigits) parts.add("0-9")
        if (settings.generatorSymbols) parts.add("!@#")
        append(parts.joinToString("+"))
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f))
                    Text("设置", style = MaterialTheme.typography.titleMedium)
                    Box(modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Column(
            modifier = Modifier
                .weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)
        ) {
            SectionHeader("安全设置") {
                    Icon(PhosphorIcons.Regular.ShieldCheck, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            SettingsCard {
                SettingsRow(
                    "修改密钥",
                    if (viewModel.getMasterPasswordManager().hasMasterPassword()) "已设置" else "未设置"
                ) { showMasterPasswordDialog = true }
                HorizontalDivider()
                SettingsRow(
                    "系统验证",
                    if (settings.biometricEnabled) "已开启" else "已关闭"
                ) { showProtectionDialog = true }
                HorizontalDivider()
                SettingsRow(
                    "自动锁定",
                    when {
                        settings.lockTimeoutSeconds == -1 -> "永不"
                        settings.lockTimeoutSeconds == 0 -> "即时锁定"
                        settings.lockTimeoutSeconds < 60 -> "${settings.lockTimeoutSeconds} 秒"
                        else -> "${settings.lockTimeoutSeconds / 60} 分钟"
                    }
                ) { showLockTimeoutSheet = true }
                HorizontalDivider()
                SettingsRow(
                    "防截屏",
                    if (settings.screenshotProtection) "已开启" else "已关闭"
                ) { showScreenshotDialog = true }
                HorizontalDivider()
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("数据与备份") {
                    Icon(PhosphorIcons.Regular.Database, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            SettingsCard {
                SettingsRow("数据导出", "支持 .mipass / .json / .csv 文件") { showExportVerify = true }
                HorizontalDivider()
                SettingsRow("数据导入与恢复", "支持 .mipass / .json / .csv 文件") {
                    backupViewModel.setPendingImportUri(null)
                    showImportDialog = true
                }
                HorizontalDivider()
                SettingsRow(
                    "数据快照",
                    if (uiState.snapshotFiles.isEmpty()) "点击创建快照" else "已保存 ${uiState.snapshotFiles.size}/5 份"
                ) {
                    scope.launch(Dispatchers.IO) {
                        viewModel.refreshSnapshots()
                    }
                    showSnapshotSheet = true
                }
                HorizontalDivider()
                SettingsRow("清除所有数据", "验证身份后清除，不可恢复") { showClearStep1 = true }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("通用设置") {
                    Icon(PhosphorIcons.Regular.SlidersHorizontal, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            SettingsCard {
                SettingsRow("语言", languageDisplay) { showLanguageSheet = true }
                HorizontalDivider()
                SettingsRow("密码生成偏好", generatorDisplay) { showGeneratorRuleDialog = true }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("关于 MiPass") {
                    Icon(PhosphorIcons.Regular.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            SettingsCard {
                SettingsRow("版本信息", "v1.0") {
                    Toast.makeText(context, "MiPass v1.0 · 纯本地零网络密码管理", Toast.LENGTH_SHORT).show()
                }
                HorizontalDivider()
                SettingsRow("使用说明", "基本功能与操作") { showUsageGuideDialog = true }
                HorizontalDivider()
                SettingsRow("隐私政策", "你的数据只属于你") { showPrivacyPolicyDialog = true }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("隐私说明") {
                    Icon(PhosphorIcons.Regular.Eye, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            SettingsCard {
                SettingsRow("应用权限", "权限与用途") { showAppPermissionDialog = true }
            }

        }
        MiPassBottomBar(
            currentRoute = NavRoutes.Settings.route,
            onNavigate = onNavigate
        )
    }

    // === BottomSheets ===

    if (showLanguageSheet) {
        LanguageBottomSheet(
            selected = "zh",
            onSelect = { value ->
                showLanguageSheet = false
                if (value == "en") {
                    Toast.makeText(context, "English is not available yet", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showLanguageSheet = false }
        )
    }

    if (showLockTimeoutSheet) {
        LockTimeoutBottomSheet(
            current = settings.lockTimeoutSeconds,
            onSelect = { value ->
                viewModel.setLockTimeout(value)
                showLockTimeoutSheet = false
            },
            onDismiss = { showLockTimeoutSheet = false }
        )
    }

    if (showSnapshotSheet) {
        SnapshotBottomSheet(
            snapshotFiles = uiState.snapshotFiles,
            snapshotManager = viewModel.getSnapshotManager(),
            passwordDao = viewModel.getPasswordDao(),
            onDismiss = {
                showSnapshotSheet = false
                scope.launch(Dispatchers.IO) {
                    viewModel.refreshSnapshots()
                }
            }
        )
    }

    // === Dialogs ===

    if (showMasterPasswordDialog) {
        MasterPasswordDialog(
            hasPassword = viewModel.getMasterPasswordManager().hasMasterPassword(),
            onSet = { newPwd ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        viewModel.getMasterPasswordManager().setMasterPassword(newPwd)
                    }
                    if (result == com.hanzg.mipass.utils.SetResult.Success) {
                        Toast.makeText(context, "密钥设置成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "新密钥强度不足（需≥8位且包含字母+数字）", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onChange = { oldPwd, newPwd ->
                scope.launch {
                    val verifyResult = withContext(Dispatchers.IO) {
                        viewModel.getMasterPasswordManager().verifyMasterPassword(oldPwd)
                    }
                    when (verifyResult) {
                        is com.hanzg.mipass.utils.VerifyResult.Success -> {
                            val setResult = withContext(Dispatchers.IO) {
                                viewModel.getMasterPasswordManager().setMasterPassword(newPwd)
                            }
                            if (setResult == com.hanzg.mipass.utils.SetResult.Success) {
                                Toast.makeText(context, "密钥已修改", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "新密钥强度不足（需≥8位且包含字母+数字）", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> Toast.makeText(context, "当前密钥错误", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showMasterPasswordDialog = false }
        )
    }

    if (showGeneratorRuleDialog) {
        GeneratorRuleDialog(
            settings = settings,
            onUpdate = { length, upper, lower, digits, symbols ->
                viewModel.updateGeneratorDefaults(length, upper, lower, digits, symbols)
            },
            onDismiss = { showGeneratorRuleDialog = false }
        )
    }

    if (showProtectionDialog) {
        BiometricToggleDialog(
            lockEnabled = settings.biometricEnabled,
            biometricManager = viewModel.getBiometricManager(),
            onToggle = { enabled ->
                if (enabled) {
                    val canAuth = viewModel.getBiometricManager().canAuthenticate()
                    if (canAuth is com.hanzg.mipass.utils.BiometricResult.Ready) {
                        viewModel.toggleBiometric(true)
                    } else {
                        Toast.makeText(
                            context,
                            when (canAuth) {
                                is com.hanzg.mipass.utils.BiometricResult.NoHardware -> "设备不支持系统验证"
                                is com.hanzg.mipass.utils.BiometricResult.NotEnrolled -> "系统未设置屏幕锁，请在系统设置中设置后再开启"
                                else -> "系统验证暂不可用"
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    showProtectionDialog = false
                    showDisableBioVerify = true
                }
            },
            onDismiss = { showProtectionDialog = false }
        )
    }

    if (showDisableBioVerify) {
        var disablePwd by remember { mutableStateOf("") }
        var disableError by remember { mutableStateOf<String?>(null) }
        var disableVerifying by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDisableBioVerify = false },
            title = { Text("验证密钥") },
            text = {
                Column {
                    Text("关闭系统验证前需验证密钥", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordTextField(
                        value = disablePwd,
                        onValueChange = { disablePwd = it; disableError = null },
                        label = "密钥",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (disableError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(disableError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pwd = disablePwd
                        scope.launch {
                            disableVerifying = true
                            val result = withContext(Dispatchers.IO) {
                                viewModel.getMasterPasswordManager().verifyMasterPassword(pwd)
                            }
                            disableVerifying = false
                            when (result) {
                                is VerifyResult.Success -> {
                                    showDisableBioVerify = false
                                    viewModel.toggleBiometric(false)
                                }
                                is VerifyResult.Failed -> disableError = "密钥错误"
                                is VerifyResult.LockedOut -> disableError = "已锁定 ${result.remainingSeconds} 秒"
                                else -> disableError = "验证失败"
                            }
                        }
                    },
                    enabled = disablePwd.isNotEmpty() && !disableVerifying
                ) { Text(if (disableVerifying) "验证中..." else "确认关闭") }
            },
            dismissButton = { TextButton(onClick = { showDisableBioVerify = false }) { Text("取消") } }
        )
    }

    if (showScreenshotDialog) {
        ScreenshotDialog(
            screenshotProtection = settings.screenshotProtection,
            onToggle = { newValue ->
                viewModel.toggleScreenshotProtection(newValue)
                Toast.makeText(
                    context,
                    if (newValue) "防截屏已开启，重启应用后生效" else "防截屏已关闭，重启应用后生效",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showScreenshotDialog = false }
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }

    if (showAppPermissionDialog) {
        AppPermissionDialog(onDismiss = { showAppPermissionDialog = false })
    }

    if (showUsageGuideDialog) {
        UsageGuideDialog(onDismiss = { showUsageGuideDialog = false })
    }

    // === Export flow ===

    if (showExportVerify) {
        LaunchedEffect(bioRetryKey) {
            if (settings.biometricEnabled) {
                val bioResult = viewModel.getBiometricManager().canAuthenticate()
                if (bioResult is BiometricResult.Ready) {
                    viewModel.getBiometricManager().showPrompt(
                        activity = context as androidx.fragment.app.FragmentActivity,
                        title = "验证身份",
                        subtitle = "导出数据前需要验证身份",
                        onSuccess = { showExportVerify = false; showExportFormat = true },
                        onError = { _, _ -> showExportVerify = false },
                        onFailed = { }
                    )
                } else {
                    showExportVerify = false
                    showExportMasterPwd = true
                }
            } else {
                showExportVerify = false
                showExportMasterPwd = true
            }
        }
    }

    if (showExportMasterPwd) {
        var pwd by remember { mutableStateOf("") }
        var pwdError by remember { mutableStateOf<String?>(null) }
        var exportVerifying by remember { mutableStateOf(false) }
        val canSwitchBio = settings.biometricEnabled &&
            viewModel.getBiometricManager().canAuthenticate() is BiometricResult.Ready
        AlertDialog(
            onDismissRequest = { showExportMasterPwd = false },
            title = { Text("验证密钥") },
            text = {
                Column {
                    Text("导出数据前需验证密钥", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordTextField(
                        value = pwd,
                        onValueChange = { pwd = it; pwdError = null },
                        label = "密钥",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pwdError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(pwdError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pwdVal = pwd
                    scope.launch {
                        exportVerifying = true
                        val result = withContext(Dispatchers.IO) {
                            viewModel.getMasterPasswordManager().verifyMasterPassword(pwdVal)
                        }
                        exportVerifying = false
                        when (result) {
                            is VerifyResult.Success -> {
                                showExportMasterPwd = false
                                showExportDisclaimer = true
                            }
                            is VerifyResult.Failed -> pwdError = "密钥错误"
                            is VerifyResult.LockedOut -> pwdError = "已锁定 ${result.remainingSeconds} 秒"
                            else -> pwdError = "验证失败"
                        }
                    }
                }, enabled = pwd.isNotEmpty() && !exportVerifying) { Text(if (exportVerifying) "验证中..." else "确认") }
            },
            dismissButton = {
                Row {
                    if (canSwitchBio) {
                        TextButton(onClick = {
                            showExportMasterPwd = false
                            bioRetryKey++
                            showExportVerify = true
                        }) {
                            Icon(
                                PhosphorIcons.Regular.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("指纹/面容")
                        }
                    }
                    TextButton(onClick = { showExportMasterPwd = false }) { Text("取消") }
                }
            }
        )
    }

    if (showExportFormat) {
        ExportFormatBottomSheet(
            current = backupState.exportFormat,
            onConfirm = { format ->
                backupViewModel.setExportFormat(format)
                showExportFormat = false
                if (format == com.hanzg.mipass.data.local.ExportFormat.MIPASS) {
                    showExportDisclaimer = true
                } else {
                    showUnencryptedWarning = true
                }
            },
            onDismiss = { showExportFormat = false }
        )
    }

    if (showExportDisclaimer) {
        ExportDisclaimerDialog(
            onConfirm = { showExportDisclaimer = false; showExportPasscode = true },
            onDismiss = { showExportDisclaimer = false }
        )
    }

    if (showUnencryptedWarning) {
        UnencryptedExportWarningDialog(
            onConfirm = {
                showUnencryptedWarning = false
                backupViewModel.exportGeneric()
            },
            onDismiss = { showUnencryptedWarning = false }
        )
    }

    if (showExportPasscode) {
        PasscodeDialog(
            title = "设置导出提取码",
            subtitle = "请设置提取码（8位以上，含字母和数字）以加密导出文件",
            passcode = backupState.passcode,
            passcodeError = backupState.passcodeError,
            isLoading = backupState.isExporting,
            onPasscodeChanged = backupViewModel::onPasscodeChanged,
            onConfirm = { backupViewModel.exportAll() },
            onDismiss = { showExportPasscode = false; backupViewModel.clearResult() }
        )
    }

    val exportSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && backupState.exportFile != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    backupState.exportFile!!.inputStream().use { it.copyTo(output) }
                }
                Toast.makeText(context, "已保存到本地文件夹", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            backupViewModel.clearResult()
            showExportPasscode = false
        }
    }

    if (backupState.result != null) {
        val result = backupState.result!!
        AlertDialog(
            onDismissRequest = { backupViewModel.clearResult() },
            title = { Text(if (result.success) "导出成功" else "导出失败") },
            text = {
                Column {
                    Text(result.message)
                    if (result.success) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("选择保存方式：", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                if (result.success) {
                    Row {
                        TextButton(onClick = {
                            com.hanzg.mipass.MainActivity.requestAuthBypass()
                            backupViewModel.shareExportedFile()
                            backupViewModel.clearResult()
                            showExportPasscode = false
                        }) { Icon(PhosphorIcons.Regular.ShareNetwork, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("分享") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            com.hanzg.mipass.MainActivity.requestAuthBypass()
                            exportSaver.launch(backupState.exportFileName)
                        }) { Icon(PhosphorIcons.Regular.FloppyDisk, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("保存到文件夹") }
                    }
                } else {
                    TextButton(onClick = { backupViewModel.clearResult() }) { Text("确定") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    backupViewModel.clearResult()
                    if (result.success) showExportPasscode = false
                }) { Text("关闭") }
            }
        )
    }

    // === Import flow ===

    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val format = backupViewModel.detectImportFormat(uri)
            if (format == com.hanzg.mipass.data.local.ImportFormat.UNKNOWN) {
                Toast.makeText(context, "不支持的格式，请选择 .mipass / .json / .csv 文件", Toast.LENGTH_SHORT).show()
            } else {
                importPendingUri = uri
                backupViewModel.setPendingImportFormat(format)
                showImportDialog = true
            }
        }
    }

    if (showImportDialog && !backupState.isImporting) {
        if (importPendingUri != null) {
            if (backupState.pendingImportFormat == com.hanzg.mipass.data.local.ImportFormat.MIPASS) {
                PasscodeDialog(
                    title = "输入提取码",
                    subtitle = "请输入导出时设定的提取码以解密文件",
                    passcode = backupState.passcode,
                    passcodeError = backupState.passcodeError,
                    isLoading = backupState.isImporting,
                    onPasscodeChanged = backupViewModel::onPasscodeChanged,
                    onConfirm = {
                        showImportDialog = false
                        showImportStrategy = true
                    },
                    onDismiss = { showImportDialog = false; importPendingUri = null; backupViewModel.dismissImport() }
                )
            } else {
                showImportDialog = false
                showImportStrategy = true
            }
        } else {
            LaunchedEffect(Unit) {
                com.hanzg.mipass.MainActivity.requestAuthBypass()
                importFilePicker.launch(arrayOf("application/octet-stream", "*/*"))
                showImportDialog = false
            }
        }
    }

    if (showImportStrategy) {
        val isEncrypted = backupState.pendingImportFormat == com.hanzg.mipass.data.local.ImportFormat.MIPASS
        ImportStrategyDialog(
            onMerge = {
                val uri = importPendingUri
                showImportStrategy = false
                if (uri != null) {
                    if (isEncrypted) backupViewModel.importFromUri(uri)
                    else backupViewModel.importGenericFromUri(uri)
                }
            },
            onReplace = {
                val uri = importPendingUri
                showImportStrategy = false
                if (uri != null) {
                    if (isEncrypted) backupViewModel.importReplace(uri)
                    else backupViewModel.importGenericReplaceFromUri(uri)
                }
            },
            onDismiss = { showImportStrategy = false; importPendingUri = null; backupViewModel.dismissImport() }
        )
    }

    if (backupState.importResult != null) {
        val result = backupState.importResult!!
        ImportResultDialog(
            totalCount = result.totalCount,
            importedCount = result.importedCount,
            skippedCount = result.skippedCount,
            onDismiss = { backupViewModel.clearResult(); importPendingUri = null }
        )
    }

    if (backupState.isImporting) {
        ImportProgressDialog()
    }

    // === Clear data flow ===

    if (showClearStep1) {
        LaunchedEffect(Unit) {
            if (settings.biometricEnabled && viewModel.getBiometricManager().canAuthenticate() is BiometricResult.Ready) {
                viewModel.getBiometricManager().showPrompt(
                    activity = context as androidx.fragment.app.FragmentActivity,
                    title = "验证身份",
                    subtitle = "清除所有数据前需要验证身份",
                    onSuccess = { showClearStep1 = false; showClearStep2 = true },
                    onError = { _, _ -> showClearStep1 = false },
                    onFailed = { }
                )
            } else {
                showClearStep1 = false
                showClearVerifyPwd = true
            }
        }
    }

    if (showClearVerifyPwd) {
        var clearPwd by remember { mutableStateOf("") }
        var clearError by remember { mutableStateOf<String?>(null) }
        var clearVerifying by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showClearVerifyPwd = false },
            title = { Text("验证密钥") },
            text = {
                Column {
                    Text("清除所有数据前需验证密钥", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordTextField(
                        value = clearPwd,
                        onValueChange = { clearPwd = it; clearError = null },
                        label = "密钥",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (clearError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(clearError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pwd = clearPwd
                        scope.launch {
                            clearVerifying = true
                            val result = withContext(Dispatchers.IO) {
                                viewModel.getMasterPasswordManager().verifyMasterPassword(pwd)
                            }
                            clearVerifying = false
                            when (result) {
                                is VerifyResult.Success -> {
                                    showClearVerifyPwd = false
                                    showClearStep2 = true
                                }
                                is VerifyResult.Failed -> clearError = "密钥错误"
                                is VerifyResult.LockedOut -> clearError = "已锁定 ${result.remainingSeconds} 秒"
                                else -> clearError = "验证失败"
                            }
                        }
                    },
                    enabled = clearPwd.isNotEmpty() && !clearVerifying
                ) { Text(if (clearVerifying) "验证中..." else "确认") }
            },
            dismissButton = { TextButton(onClick = { showClearVerifyPwd = false }) { Text("取消") } }
        )
    }

    if (showClearStep2) {
        ClearDataDialog(
            clearConfirmText = clearConfirmText,
            onTextChanged = { clearConfirmText = it },
            onConfirm = {
                if (clearConfirmText == "DELETE") {
                    viewModel.performClearAll()
                    showClearStep2 = false
                    clearConfirmText = ""
                    (context as? android.app.Activity)?.finishAffinity()
                }
            },
            onDismiss = { showClearStep2 = false; clearConfirmText = "" }
        )
    }
}

// === Reusable components ===

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            PhosphorIcons.Regular.CaretRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
