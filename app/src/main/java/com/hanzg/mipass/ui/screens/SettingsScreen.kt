package com.hanzg.mipass.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
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
import com.hanzg.mipass.data.local.AppPreferences
import com.hanzg.mipass.data.local.AppSettings
import com.hanzg.mipass.data.local.PasswordDao
import com.hanzg.mipass.data.local.SnapshotManager
import com.hanzg.mipass.ui.navigation.MiPassBottomBar
import com.hanzg.mipass.ui.navigation.NavRoutes
import com.hanzg.mipass.utils.BiometricPromptManager
import com.hanzg.mipass.utils.LocaleHelper
import com.hanzg.mipass.utils.MasterPasswordManager
import com.hanzg.mipass.utils.SelfDestructManager
import com.hanzg.mipass.utils.VerifyResult
import com.hanzg.mipass.utils.BiometricResult
import com.hanzg.mipass.domain.repository.PasswordRepository
import com.hanzg.mipass.ui.components.PasswordTextField
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun appPreferences(): AppPreferences
    fun biometricPromptManager(): BiometricPromptManager
    fun selfDestructManager(): SelfDestructManager
    fun snapshotManager(): SnapshotManager
    fun passwordRepository(): PasswordRepository
    fun passwordDao(): PasswordDao
    fun masterPasswordManager(): MasterPasswordManager
    fun localeHelper(): LocaleHelper
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    pendingImportUri: Uri? = null,
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsEntryPoint::class.java
        )
    }
    val prefs = entryPoint.appPreferences()
    val biometricManager = entryPoint.biometricPromptManager()
    val selfDestructManager = entryPoint.selfDestructManager()
    val snapshotManager = entryPoint.snapshotManager()
    val passwordRepo = entryPoint.passwordRepository()
    val masterPasswordManager = entryPoint.masterPasswordManager()
    val localeHelper = entryPoint.localeHelper()

    val passwordDao = entryPoint.passwordDao()
    val settings by prefs.settingsFlow.collectAsState(initial = AppSettings())

    var showExportVerify by remember { mutableStateOf(false) }
    var showExportMasterPwd by remember { mutableStateOf(false) }
    var showExportDisclaimer by remember { mutableStateOf(false) }
    var showExportPasscode by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showImportStrategy by remember { mutableStateOf(false) }
    var importPendingUri by remember { mutableStateOf<Uri?>(null) }

    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showLockTimeoutSheet by remember { mutableStateOf(false) }

    var showGeneratorRuleDialog by remember { mutableStateOf(false) }
    var showProtectionDialog by remember { mutableStateOf(false) }
    var showSelfDestructDialog by remember { mutableStateOf(false) }
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var showScreenshotDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showAppPermissionDialog by remember { mutableStateOf(false) }
    var showUsageGuideDialog by remember { mutableStateOf(false) }
    var showSnapshotSheet by remember { mutableStateOf(false) }
    var showClearStep1 by remember { mutableStateOf(false) }
    var showClearStep2 by remember { mutableStateOf(false) }
    var clearConfirmText by remember { mutableStateOf("") }
    var snapshotFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }

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

    LaunchedEffect(Unit) {
        snapshotFiles = snapshotManager.listSnapshots()
    }

    val themeDisplay = when (settings.themeMode) {
        "light" -> "浅色"
        "dark" -> "深色"
        else -> "跟随系统"
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

    Scaffold(
        topBar = {
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
        },
        bottomBar = {
            MiPassBottomBar(
                currentRoute = NavRoutes.Settings.route,
                onNavigate = onNavigate
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)
        ) {
            SectionHeader("安全设置") {
                    Icon(PhosphorIcons.Regular.ShieldCheck, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            SettingsCard {
                SettingsRow(
                    "修改主密码",
                    if (masterPasswordManager.hasMasterPassword()) "已设置" else "未设置"
                ) { showMasterPasswordDialog = true }
                HorizontalDivider()
                SettingsRow(
                    "生物识别解锁",
                    if (settings.biometricEnabled) "已开启" else "已关闭"
                ) { showProtectionDialog = true }
                HorizontalDivider()
                SettingsRow(
                    "自动锁定延时",
                    when {
                        settings.lockTimeoutSeconds == -1 -> "永不"
                        settings.lockTimeoutSeconds == 0 -> "即时锁定"
                        settings.lockTimeoutSeconds < 60 -> "${settings.lockTimeoutSeconds} 秒"
                        else -> "${settings.lockTimeoutSeconds / 60} 分钟"
                    }
                ) { showLockTimeoutSheet = true }
                HorizontalDivider()
                SettingsRow(
                    "防截屏保护",
                    if (settings.screenshotProtection) "已开启" else "已关闭"
                ) { showScreenshotDialog = true }
                HorizontalDivider()
                SettingsRow(
                    "自毁机制",
                    if (settings.selfDestructEnabled) "已开启" else "已关闭"
                ) { showSelfDestructDialog = true }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("数据与备份") {
                    Icon(PhosphorIcons.Regular.Database, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            SettingsCard {
                SettingsRow("数据导出", "加密导出 .mipass 备份文件") { showExportVerify = true }
                HorizontalDivider()
                SettingsRow("数据导入与恢复", "导入 .mipass 备份文件") {
                    backupViewModel.setPendingImportUri(null)
                    showImportDialog = true
                }
                HorizontalDivider()
                SettingsRow(
                    "数据快照",
                    if (snapshotFiles.isEmpty()) "点击手动保存" else "已保存 ${snapshotFiles.size}/5 份"
                ) {
                    snapshotFiles = snapshotManager.listSnapshots()
                    showSnapshotSheet = true
                }
                HorizontalDivider()
                SettingsRow("清除所有数据", "需身份验证，不可恢复") { showClearStep1 = true }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("通用设置") {
                    Icon(PhosphorIcons.Regular.SlidersHorizontal, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            SettingsCard {
                SettingsRow("主题风格", themeDisplay) { showThemeSheet = true }
                HorizontalDivider()
                SettingsRow("显示语言", languageDisplay) { showLanguageSheet = true }
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
                SettingsRow("使用说明", "基本操作与功能介绍") { showUsageGuideDialog = true }
                HorizontalDivider()
                SettingsRow("隐私政策", "数据收集与隐私保护声明") { showPrivacyPolicyDialog = true }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("隐私说明") {
                    Icon(PhosphorIcons.Regular.Eye, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            SettingsCard {
                SettingsRow("应用权限说明", "权限列表及使用目的") { showAppPermissionDialog = true }
            }

        }
    }

    // === BottomSheets ===

    if (showThemeSheet) {
        ThemeBottomSheet(
            selected = settings.themeMode,
            onSelect = { value ->
                prefs.setThemeMode(value)
                showThemeSheet = false
            },
            onDismiss = { showThemeSheet = false }
        )
    }

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
                prefs.setLockTimeout(value)
                showLockTimeoutSheet = false
            },
            onDismiss = { showLockTimeoutSheet = false }
        )
    }

    if (showSnapshotSheet) {
        SnapshotBottomSheet(
            snapshotFiles = snapshotFiles,
            snapshotManager = snapshotManager,
            passwordDao = passwordDao,
            onDismiss = {
                showSnapshotSheet = false
                snapshotFiles = snapshotManager.listSnapshots()
            }
        )
    }

    // === Dialogs ===

    if (showMasterPasswordDialog) {
        MasterPasswordDialog(
            hasPassword = masterPasswordManager.hasMasterPassword(),
            onSet = { newPwd ->
                masterPasswordManager.setMasterPassword(newPwd)
                Toast.makeText(context, "主密码设置成功", Toast.LENGTH_SHORT).show()
            },
            onChange = { oldPwd, newPwd ->
                when (masterPasswordManager.verifyMasterPassword(oldPwd)) {
                    is com.hanzg.mipass.utils.VerifyResult.Success -> {
                        val setResult = masterPasswordManager.setMasterPassword(newPwd)
                        if (setResult == com.hanzg.mipass.utils.SetResult.Success) {
                            Toast.makeText(context, "主密码已修改", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "新密码强度不足（需≥8位且包含字母+数字）", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> Toast.makeText(context, "当前密码错误", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showMasterPasswordDialog = false }
        )
    }

    if (showGeneratorRuleDialog) {
        GeneratorRuleDialog(
            settings = settings,
            onUpdate = { length, upper, lower, digits, symbols ->
                prefs.setGeneratorLength(length)
                prefs.setGeneratorUppercase(upper)
                prefs.setGeneratorLowercase(lower)
                prefs.setGeneratorDigits(digits)
                prefs.setGeneratorSymbols(symbols)
            },
            onDismiss = { showGeneratorRuleDialog = false }
        )
    }

    if (showProtectionDialog) {
        BiometricToggleDialog(
            lockEnabled = settings.biometricEnabled,
            biometricManager = biometricManager,
            onToggle = { enabled ->
                if (enabled) {
                    val canAuth = biometricManager.canAuthenticate()
                    if (canAuth is com.hanzg.mipass.utils.BiometricResult.Ready) {
                        prefs.setBiometricEnabled(true)
                    } else {
                        Toast.makeText(
                            context,
                            when (canAuth) {
                                is com.hanzg.mipass.utils.BiometricResult.NoHardware -> "设备不支持生物识别"
                                is com.hanzg.mipass.utils.BiometricResult.NotEnrolled -> "系统未录入指纹/面容，请在系统设置中录入后再开启"
                                else -> "生物识别暂不可用"
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    prefs.setBiometricEnabled(false)
                }
            },
            onDismiss = { showProtectionDialog = false }
        )
    }

    if (showSelfDestructDialog) {
        SelfDestructDialog(
            enabled = settings.selfDestructEnabled,
            maxAttempts = settings.selfDestructThreshold,
            onToggle = { prefs.setSelfDestructEnabled(it) },
            onThresholdChange = { prefs.setSelfDestructThreshold(it) },
            onDismiss = { showSelfDestructDialog = false }
        )
    }

    if (showScreenshotDialog) {
        ScreenshotDialog(
            screenshotProtection = settings.screenshotProtection,
            onToggle = { newValue ->
                prefs.setScreenshotProtection(newValue)
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
        LaunchedEffect(Unit) {
            if (settings.biometricEnabled) {
                val bioResult = biometricManager.canAuthenticate()
                if (bioResult is BiometricResult.Ready) {
                    biometricManager.showPrompt(
                        activity = context as androidx.fragment.app.FragmentActivity,
                        title = "验证身份",
                        subtitle = "导出数据前需要验证身份",
                        onSuccess = { showExportVerify = false; showExportDisclaimer = true },
                        onError = { _, _ -> showExportVerify = false; showExportMasterPwd = true },
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
        val bioReady = settings.biometricEnabled &&
            biometricManager.canAuthenticate() is BiometricResult.Ready
        AlertDialog(
            onDismissRequest = { showExportMasterPwd = false },
            title = { Text("验证身份") },
            text = {
                Column {
                    Text("导出数据前需要验证身份", style = MaterialTheme.typography.bodyMedium)
                    if (bioReady) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                biometricManager.showPrompt(
                                    activity = context as androidx.fragment.app.FragmentActivity,
                                    title = "验证身份",
                                    subtitle = "导出数据前需要验证身份",
                                    onSuccess = {
                                        showExportMasterPwd = false
                                        showExportDisclaimer = true
                                    },
                                    onError = { _, _ -> },
                                    onFailed = { }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                PhosphorIcons.Regular.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("使用指纹/面容解锁")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "或使用主密码验证",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordTextField(
                        value = pwd,
                        onValueChange = { pwd = it; pwdError = null },
                        label = "主密码",
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
                    when (val result = masterPasswordManager.verifyMasterPassword(pwd)) {
                        is VerifyResult.Success -> {
                            showExportMasterPwd = false
                            showExportDisclaimer = true
                        }
                        is VerifyResult.Failed -> pwdError = "密码错误"
                        is VerifyResult.LockedOut -> pwdError = "已锁定 ${result.remainingSeconds} 秒"
                        else -> pwdError = "验证失败"
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showExportMasterPwd = false }) { Text("取消") }
            }
        )
    }

    if (showExportDisclaimer) {
        ExportDisclaimerDialog(
            onConfirm = { showExportDisclaimer = false; showExportPasscode = true },
            onDismiss = { showExportDisclaimer = false }
        )
    }

    if (showExportPasscode) {
        PasscodeDialog(
            title = "导出备份",
            subtitle = "请设置 6 位数字提取码以加密备份文件（全量导出）",
            passcode = backupState.passcode,
            passcodeError = backupState.passcodeError,
            isLoading = backupState.isExporting,
            onPasscodeChanged = backupViewModel::onPasscodeChanged,
            onConfirm = { backupViewModel.exportAll() },
            onDismiss = { showExportPasscode = false; backupViewModel.clearResult() }
        )
    }

    val exportSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
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
                            com.hanzg.mipass.MainActivity.skipAuthOnce = true
                            com.hanzg.mipass.MainActivity.skipNextLockCheck = true
                            backupViewModel.shareExportedFile()
                            backupViewModel.clearResult()
                            showExportPasscode = false
                        }) { Icon(PhosphorIcons.Regular.ShareNetwork, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("分享") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            com.hanzg.mipass.MainActivity.skipAuthOnce = true
                            com.hanzg.mipass.MainActivity.skipNextLockCheck = true
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
            importPendingUri = uri
            showImportDialog = true
        }
    }

    if (showImportDialog && !backupState.isImporting) {
        if (importPendingUri != null) {
            PasscodeDialog(
                title = "导入备份",
                subtitle = "请输入导出时设置的 6 位提取码",
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
            LaunchedEffect(Unit) {
                com.hanzg.mipass.MainActivity.skipAuthOnce = true
                com.hanzg.mipass.MainActivity.skipNextLockCheck = true
                importFilePicker.launch(arrayOf("application/octet-stream", "*/*"))
                showImportDialog = false
            }
        }
    }

    if (showImportStrategy) {
        ImportStrategyDialog(
            onMerge = {
                val uri = importPendingUri
                showImportStrategy = false
                if (uri != null) backupViewModel.importFromUri(uri)
            },
            onReplace = {
                val uri = importPendingUri
                showImportStrategy = false
                if (uri != null) backupViewModel.importReplace(uri)
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
            val bioResult = biometricManager.canAuthenticate()
            if (bioResult is com.hanzg.mipass.utils.BiometricResult.Ready) {
                biometricManager.showPrompt(
                    activity = context as androidx.fragment.app.FragmentActivity,
                    title = "验证身份",
                    subtitle = "清除所有数据前需要验证身份",
                    onSuccess = { showClearStep1 = false; showClearStep2 = true },
                    onError = { _, _ -> showClearStep1 = false },
                    onFailed = { }
                )
            } else {
                showClearStep1 = false
                showClearStep2 = true
            }
        }
    }

    if (showClearStep2) {
        ClearDataDialog(
            clearConfirmText = clearConfirmText,
            onTextChanged = { clearConfirmText = it },
            onConfirm = {
                if (clearConfirmText == "DELETE") {
                    scope.launch {
                        try {
                            passwordRepo.deleteAll()
                            prefs.clearAll()
                            Toast.makeText(context, "所有数据已清除", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "清除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
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
