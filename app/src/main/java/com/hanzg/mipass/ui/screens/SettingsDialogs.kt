package com.hanzg.mipass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.hanzg.mipass.ui.theme.MiPassEaseInOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*
import com.hanzg.mipass.data.local.AppSettings
import com.hanzg.mipass.utils.BiometricPromptManager
import com.hanzg.mipass.utils.SelfDestructManager

@Composable
fun GeneratorRuleDialog(
    settings: AppSettings,
    onUpdate: (Int, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var length by remember { mutableStateOf(settings.generatorLength) }
    var uppercase by remember { mutableStateOf(settings.generatorUppercase) }
    var lowercase by remember { mutableStateOf(settings.generatorLowercase) }
    var digits by remember { mutableStateOf(settings.generatorDigits) }
    var symbols by remember { mutableStateOf(settings.generatorSymbols) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成器默认规则") },
        text = {
            Column {
                Text("长度：$length", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.toInt() },
                    valueRange = 4f..64f,
                    steps = 59
                )
                Spacer(modifier = Modifier.height(8.dp))
                CheckboxRow("大写字母 (A-Z)", uppercase) { uppercase = it }
                CheckboxRow("小写字母 (a-z)", lowercase) { lowercase = it }
                CheckboxRow("数字 (0-9)", digits) { digits = it }
                CheckboxRow("符号 (!@#\$)", symbols) { symbols = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onUpdate(length, uppercase, lowercase, digits, symbols)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun BiometricToggleDialog(
    lockEnabled: Boolean,
    biometricManager: BiometricPromptManager,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生物识别解锁") },
        text = {
            Column {
                Text(
                    if (lockEnabled) "当前已开启，使用系统指纹/面容解锁 MiPass"
                    else "开启后，打开 MiPass 时会使用系统指纹/面容进行身份验证",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                val canAuth = biometricManager.canAuthenticate()
                Text(
                    when (canAuth) {
                        is com.hanzg.mipass.utils.BiometricResult.Ready -> "系统已配置生物识别，可以使用"
                        is com.hanzg.mipass.utils.BiometricResult.NoHardware -> "设备不支持生物识别"
                        is com.hanzg.mipass.utils.BiometricResult.NotEnrolled -> "系统未录入指纹/面容，请先到系统设置中录入"
                        else -> "生物识别暂不可用"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (lockEnabled) onToggle(false) else onToggle(true)
                onDismiss()
            }) {
                Text(if (lockEnabled) "关闭" else "开启")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun SelfDestructDialog(
    enabled: Boolean,
    maxAttempts: Int,
    onToggle: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自毁机制") },
        text = {
            Column {
                CheckboxRow("启用自毁机制", enabled) { onToggle(it) }
                if (enabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "连续失败上限：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val options = listOf(5, 10, 15, 20)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        options.forEach { n ->
                            FilterChip(
                                selected = maxAttempts == n,
                                onClick = { onThresholdChange(n) },
                                label = { Text("${n}次") },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "开启后，连续输错 $maxAttempts 次主密码/生物认证将自动擦除所有本地数据及快照，不可恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun MasterPasswordDialog(
    hasPassword: Boolean,
    onSet: (String) -> Unit,
    onChange: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var currentPwdVisible by remember { mutableStateOf(false) }
    var newPwdVisible by remember { mutableStateOf(false) }
    var confirmPwdVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPassword) "修改主密码" else "设置主密码") },
        text = {
            Column {
                if (hasPassword) {
                    OutlinedTextField(
                        value = currentPwd,
                        onValueChange = { currentPwd = it; error = null },
                        label = { Text("当前密码") },
                        singleLine = true,
                        visualTransformation = if (currentPwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { currentPwdVisible = !currentPwdVisible }) {
                                Icon(
                                    imageVector = if (currentPwdVisible) PhosphorIcons.Regular.EyeSlash else PhosphorIcons.Regular.Eye,
                                    contentDescription = if (currentPwdVisible) "隐藏密码" else "显示密码",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it; error = null },
                    label = { Text("新密码") },
                    singleLine = true,
                    visualTransformation = if (newPwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newPwdVisible = !newPwdVisible }) {
                            Icon(
                                imageVector = if (newPwdVisible) PhosphorIcons.Regular.EyeSlash else PhosphorIcons.Regular.Eye,
                                contentDescription = if (newPwdVisible) "隐藏密码" else "显示密码",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPwd,
                    onValueChange = { confirmPwd = it; error = null },
                    label = { Text("确认新密码") },
                    singleLine = true,
                    visualTransformation = if (confirmPwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPwdVisible = !confirmPwdVisible }) {
                            Icon(
                                imageVector = if (confirmPwdVisible) PhosphorIcons.Regular.EyeSlash else PhosphorIcons.Regular.Eye,
                                contentDescription = if (confirmPwdVisible) "隐藏密码" else "显示密码",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    newPwd.length < 8 -> error = "密码至少 8 位，需包含字母和数字"
                    newPwd != confirmPwd -> error = "两次密码不一致"
                    else -> {
                        if (hasPassword) onChange(currentPwd, newPwd) else onSet(newPwd)
                        onDismiss()
                    }
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun PasscodeDialog(
    title: String,
    subtitle: String,
    passcode: String,
    passcodeError: String?,
    isLoading: Boolean,
    onPasscodeChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = passcode,
                    onValueChange = onPasscodeChanged,
                    label = { Text("6 位数字提取码") },
                    isError = passcodeError != null,
                    supportingText = passcodeError?.let { { Text(it) } },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(tween(200, easing = MiPassEaseInOut)),
                    exit = fadeOut(tween(200, easing = MiPassEaseInOut))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("处理中...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isLoading && passcode.length == 6) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("取消") }
        }
    )
}

@Composable
fun ScreenshotDialog(
    screenshotProtection: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("防截屏保护") },
        text = {
            Column {
                Text(
                    if (screenshotProtection) "已开启：禁止截图和录屏，多任务界面显示黑色遮罩。"
                    else "已关闭：允许截图和录屏。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onToggle(!screenshotProtection)
                onDismiss()
            }) {
                Text(if (screenshotProtection) "关闭" else "开启")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("隐私政策") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "MiPass 是一款纯本地离线密码管理工具。\n\n" +
                            "1. 本应用完全不联网，不会上传、收集或分享您的任何数据。\n\n" +
                            "2. 所有密码数据仅存储在您的设备本地，使用 AES-256 加密保护。\n\n" +
                            "3. 应用不包含任何第三方 SDK、广告、统计或追踪代码。\n\n" +
                            "4. 导出 .mipass 文件由您自行保管，应用不会上传至任何服务器。\n\n" +
                            "5. 生物识别数据仅使用系统 API 进行本地验证，应用不会读取或存储生物特征。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
fun UsageGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用说明") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "添加密码\n" +
                            "底部 App/Web 标签页，点右上角 + 新建。App 类记录应用账号，Web 类记录网站登录信息。\n\n" +
                            "密码生成器\n" +
                            "新建或编辑时，点密码框右侧 ⟳ 图标随机生成强密码；长按可调节长度和字符类型。底部「生成器」标签页可独立使用。\n\n" +
                            "搜索与分类\n" +
                            "顶部搜索框支持按名称/账号模糊搜索，分类下拉可快速筛选。\n\n" +
                            "数据备份\n" +
                            "设置 → 数据导出，设置 6 位提取码生成 .mipass 加密备份文件。导入时选择合并或覆盖。可在设置中手动保存数据快照（最多 5 份）。\n\n" +
                            "安全保护\n" +
                            "切后台自动模糊遮罩 · 防截屏录屏 · 剪贴板定时清理 · 连续输错密码可触发自毁擦除所有数据。主密码遗忘无法找回，请务必牢记。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
fun AppPermissionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("应用权限说明") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "MiPass 申请的权限及用途：\n\n" +
                            "生物识别\n" +
                            "使用指纹/面容解锁 App，数据仅存储在设备本地安全硬件中，MiPass 不会读取或存储生物特征。\n\n" +
                            "文件读写\n" +
                            "仅用于导出和导入 .mipass 加密备份文件，不访问其他任何文件。\n\n" +
                            "剪贴板\n" +
                            "用于临时复制账号/密码，超出设定时间后自动清空，防止其他应用读取。\n\n" +
                            "防截屏\n" +
                            "通过系统级 FLAG_SECURE 阻止截图和录屏，防止密码信息通过多任务界面泄露。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
fun ExportDisclaimerDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重要提示") },
        text = {
            Text("请妥善保管导出的 .mipass 文件及对应的导出密码。该密码无法找回，一旦遗失将无法恢复文件内容。请勿将其发送到不安全的第三方平台。")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("我已了解") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ImportStrategyDialog(
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入策略") },
        text = {
            Text("请选择数据处理方式：\n\n· 合并（推荐）：跳过重复项，保留现有数据\n· 覆盖：清空当前数据，完全替换为导入数据（不可恢复）")
        },
        confirmButton = {
            Row {
                TextButton(onClick = onMerge) { Text("合并", color = MaterialTheme.colorScheme.primary) }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onReplace) { Text("覆盖", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ImportProgressDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("导入中...") },
        text = {
            Column {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Text("正在解密导入数据...")
            }
        },
        confirmButton = { Spacer(modifier = Modifier.size(1.dp)) },
        dismissButton = null
    )
}

@Composable
fun ImportResultDialog(
    totalCount: Int,
    importedCount: Int,
    skippedCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入完成") },
        text = {
            Text("共 $totalCount 条 · 导入 $importedCount 条 · 跳过 $skippedCount 条")
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
fun ClearDataDialog(
    clearConfirmText: String,
    onTextChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认清除所有数据") },
        text = {
            Column {
                Text("此操作不可逆！请输入 DELETE 确认：")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = clearConfirmText,
                    onValueChange = onTextChanged,
                    label = { Text("输入 DELETE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = clearConfirmText == "DELETE"
            ) {
                Text(
                    "确认清除",
                    color = if (clearConfirmText == "DELETE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
