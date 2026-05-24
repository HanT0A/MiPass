package com.hanzg.mipass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*
import com.hanzg.mipass.utils.MasterPasswordManager
import com.hanzg.mipass.utils.SetResult
import com.hanzg.mipass.ui.components.PasswordTextField

enum class MasterPasswordScreenMode { SETUP, UNLOCK }

@Composable
fun MasterPasswordSetupScreen(
    mode: MasterPasswordScreenMode,
    masterPasswordManager: MasterPasswordManager,
    onSetupComplete: () -> Unit,
    onUnlockSuccess: () -> Unit,
    onExit: () -> Unit,
    onBiometricAuth: (() -> Unit)? = null,
    showBiometricHint: Boolean = false
) {
    val isSetup = mode == MasterPasswordScreenMode.SETUP

    if (isSetup) {
        SetupContent(
            masterPasswordManager = masterPasswordManager,
            onSetupComplete = onSetupComplete
        )
    } else {
        UnlockContent(
            masterPasswordManager = masterPasswordManager,
            onUnlockSuccess = onUnlockSuccess,
            onExit = onExit,
            hasBiometric = onBiometricAuth != null,
            onBiometricAuth = onBiometricAuth
        )
    }
}

@Composable
private fun SetupContent(
    masterPasswordManager: MasterPasswordManager,
    onSetupComplete: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var agreedRisk by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = PhosphorIcons.Regular.LockSimple,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "设置主密码",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "首次使用 MiPass 前，请设置一个强密码作为你的安全凭证",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        PasswordTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "设置主密码",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "至少 8 位，必须包含字母和数字",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        PasswordTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; error = null },
            label = "确认主密码",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = agreedRisk, onCheckedChange = { agreedRisk = it; error = null })
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "我已了解：遗忘主密码将永久丢失所有数据，无法找回",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    password.length < 8 -> error = "密码至少 8 位"
                    !password.any { it.isLetter() } || !password.any { it.isDigit() } ->
                        error = "必须包含字母和数字"
                    password != confirmPassword -> error = "两次密码不一致"
                    else -> when (masterPasswordManager.setMasterPassword(password)) {
                        SetResult.Success -> onSetupComplete()
                        SetResult.TooWeak -> error = "密码强度不足"
                    }
                }
            },
            enabled = password.length >= 8 && password == confirmPassword && agreedRisk,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("设置主密码")
        }
    }
}

@Composable
private fun UnlockContent(
    masterPasswordManager: MasterPasswordManager,
    onUnlockSuccess: () -> Unit,
    onExit: () -> Unit,
    hasBiometric: Boolean,
    onBiometricAuth: (() -> Unit)?
) {
    var useBiometric by remember { mutableStateOf(hasBiometric) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutRemaining by remember {
        mutableStateOf(masterPasswordManager.getLockoutRemainingSeconds())
    }

    if (useBiometric && onBiometricAuth != null) {
        // 生物识别验证
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PhosphorIcons.Regular.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "验证身份",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "使用指纹或面容解锁 MiPass",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBiometricAuth,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = PhosphorIcons.Regular.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("使用指纹/面容解锁")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { useBiometric = false }) {
                Text("使用主密码验证")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onExit) {
                Text("退出应用", color = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        // 主密码验证
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PhosphorIcons.Regular.LockSimple,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "输入主密码",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请输入主密码以解锁 MiPass",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            PasswordTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "主密码",
                modifier = Modifier.fillMaxWidth(),
                enabled = lockoutRemaining == 0
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "至少 8 位，必须包含字母和数字",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            if (lockoutRemaining > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "密码输入已冻结，请等待 ${lockoutRemaining} 秒",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    when (val result = masterPasswordManager.verifyMasterPassword(password)) {
                        is com.hanzg.mipass.utils.VerifyResult.Success -> onUnlockSuccess()
                        is com.hanzg.mipass.utils.VerifyResult.Failed -> {
                            error = "密码错误，已失败 ${result.attempts} 次"
                            password = ""
                        }
                        is com.hanzg.mipass.utils.VerifyResult.LockedOut -> {
                            lockoutRemaining = result.remainingSeconds
                            error = "密码输入已冻结 ${result.remainingSeconds} 秒"
                        }
                        is com.hanzg.mipass.utils.VerifyResult.Error -> error = "系统错误"
                    }
                },
                enabled = password.isNotEmpty() && lockoutRemaining == 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("解锁")
            }
            if (hasBiometric) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { useBiometric = true }) {
                    Text("使用指纹/面容解锁")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("退出应用", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
