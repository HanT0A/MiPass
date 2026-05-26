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

import androidx.compose.ui.unit.dp

import com.hanzg.mipass.data.local.AppSettings
import com.hanzg.mipass.utils.BiometricPromptManager

import com.hanzg.mipass.ui.components.PasswordTextField

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
                CheckboxRow("大写字母 A-Z", uppercase) { uppercase = it }
                CheckboxRow("小写字母 a-z", lowercase) { lowercase = it }
                CheckboxRow("数字 0-9", digits) { digits = it }
                CheckboxRow("符号 !@#\$", symbols) { symbols = it }
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
        title = { Text("系统验证") },
        text = {
            Column {
                Text(
                    if (lockEnabled) "当前已开启，使用系统指纹/面容/设备密码解锁 MiPass"
                    else "开启后，打开 MiPass 时会使用系统生物识别或设备密码进行身份验证",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                val canAuth = biometricManager.canAuthenticate()
                Text(
                    when (canAuth) {
                        is com.hanzg.mipass.utils.BiometricResult.Ready -> "系统验证可用"
                        is com.hanzg.mipass.utils.BiometricResult.NoHardware -> "设备不支持系统验证"
                        is com.hanzg.mipass.utils.BiometricResult.NotEnrolled -> "系统未设置屏幕锁，请先到系统设置中设置"
                        else -> "系统验证暂不可用"
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
fun MasterPasswordDialog(
    hasPassword: Boolean,
    onSet: (String) -> Unit,
    onChange: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPassword) "修改密钥" else "设置密钥") },
        text = {
            Column {
                if (hasPassword) {
                    PasswordTextField(
                        value = currentPwd,
                        onValueChange = { currentPwd = it; error = null },
                        label = "当前密钥",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                PasswordTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it; error = null },
                    label = "新密钥",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                PasswordTextField(
                    value = confirmPwd,
                    onValueChange = { confirmPwd = it; error = null },
                    label = "确认新密钥",
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
                    newPwd.length < 8 -> error = "密钥至少 8 位，需包含字母和数字"
                    newPwd != confirmPwd -> error = "两次密钥不一致"
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
                    label = { Text("提取码") },
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
            TextButton(onClick = onConfirm, enabled = !isLoading && passcode.length >= 8 && passcode.any { it.isLetter() } && passcode.any { it.isDigit() }) { Text("确认") }
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
        title = { Text("防截屏") },
        text = {
            Column {
                Text(
                    if (screenshotProtection) "禁止截图和录屏，多任务界面显示黑色遮罩。"
                    else "允许截图和录屏。",
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
                    "MiPass 是一款纯本地离线的密码管理工具，你的数据只属于你。\n\n" +
                            "零网络\n" +
                            "本应用不含任何网络通信代码，不会也无法上传、收集或分享你的任何数据。\n\n" +
                            "本地加密\n" +
                            "所有密码数据仅保存在你的设备上，使用 AES-256 信封加密，解密密钥由设备安全硬件保管。\n\n" +
                            "无第三方\n" +
                            "不含任何第三方 SDK、广告、统计或追踪代码。\n\n" +
                            "导出文件\n" +
                            ".mipass 导出文件由你自行保管，应用无法上传至任何服务器。\n\n" +
                            "生物识别\n" +
                            "指纹/面容验证仅调用系统接口，生物特征始终存储在设备安全硬件中，应用不会读取。",
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
                    "添加与编辑\n" +
                            "添加   右上角 + → 选择 App（应用账号）或 Web（网站登录）→ 填写保存\n" +
                            "编辑   点击条目修改，图标可自定义（支持本地图片）\n" +
                            "删除   编辑页底部删除按钮，需确认\n\n" +
                            "密码生成器\n" +
                            "快速生成   编辑页密码框右侧 Shuffle 图标，点击生成，长按调整长度和字符类型\n" +
                            "独立使用   底部「生成器」Tab，调节范围 4-64，实时预览强度\n\n" +
                            "数据导出\n" +
                            "   设置 → 数据导出 → 验证身份 → 选择格式\n" +
                            "   · .mipass 加密 — 需设提取码，AES-256 加密\n" +
                            "   · JSON / CSV 通用 — 明文，适合迁移至其他工具\n\n" +
                            "数据导入\n" +
                            "   设置 → 数据导入与恢复 → 选择文件\n" +
                            "   支持 .mipass / .json / .csv 格式\n" +
                            "   合并 — 保留现有数据，仅导入不重复的新条目\n" +
                            "   覆盖 — 清空当前数据后导入（不可恢复）\n\n" +
                            "数据快照\n" +
                            "   设置 → 数据快照 → 保存当前快照\n" +
                            "   最多保留 5 份历史版本，点击任一版本恢复\n\n" +
                            "安全机制\n" +
                            "   恢复密钥   访问应用必需，遗忘不可找回\n" +
                            "   系统验证   指纹 / 面容解锁（可选），生物特征不离开设备\n" +
                            "   自动锁定   切后台超时可设即时 / 1 / 3 / 5 分钟 / 永不\n" +
                            "   防截屏 + 后台模糊遮罩 + 剪贴板敏感标记",
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
        title = { Text("应用权限") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "生物识别\n" +
                            "用于指纹或面容解锁。生物特征仅存于设备安全硬件，应用不读取。\n\n" +
                            "存储读写\n" +
                            "仅用于导出和导入 .mipass 备份文件，不访问其他文件。\n\n" +
                            "剪贴板\n" +
                            "用于临时复制账号和密码，复制内容标记为敏感信息，防止被其他应用读取。",
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
            Text("请妥善保管导出文件及提取码。提取码无法找回，遗失将无法恢复文件。请勿将文件发送到不安全的平台。")
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
        title = { Text("导入方式") },
        text = {
            Text("合并：保留现有数据，仅导入不重复的新条目\n\n覆盖：清空当前数据后导入（不可恢复）")
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
        title = { Text("正在导入...") },
        text = {
            Column {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Text("正在处理...")
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
fun UnencryptedExportWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安全警告") },
        text = {
            Text("导出文件未加密，包含所有密码明文，任何人可读取。建议优先使用 .mipass 加密格式。\n\n请妥善保管，导出后建议立即删除。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("我已了解，继续导出") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
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
