package com.hanzg.mipass.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*
import com.hanzg.mipass.domain.usecase.GeneratePasswordUseCase
import com.hanzg.mipass.ui.theme.JetBrainsMonoFont

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPasswordGenerator(
    onPasswordGenerated: (String) -> Unit,
    generatePasswordUseCase: GeneratePasswordUseCase
) {
    val haptic = LocalHapticFeedback.current
    var configExpanded by remember { mutableStateOf(false) }
    var length by remember { mutableFloatStateOf(16f) }
    var includeUpper by remember { mutableStateOf(true) }
    var includeLower by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }

    fun makeConfig() = GeneratePasswordUseCase.PasswordConfig(
        length = length.toInt(),
        includeUppercase = includeUpper,
        includeLowercase = includeLower,
        includeNumbers = includeNumbers,
        includeSymbols = includeSymbols
    )

    var preview by remember { mutableStateOf(generatePasswordUseCase.generate(makeConfig())) }

    // Wrap icon in 44dp touch target box
    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = PhosphorIcons.Regular.Shuffle,
            contentDescription = "生成随机密码",
            modifier = Modifier
                .size(24.dp)
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val newPwd = generatePasswordUseCase.generate(makeConfig())
                        preview = newPwd
                        onPasswordGenerated(newPwd)
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        configExpanded = true
                    }
                ),
            tint = MaterialTheme.colorScheme.primary
        )
    }

    DropdownMenu(
        expanded = configExpanded,
        onDismissRequest = { configExpanded = false }
    ) {
        DropdownMenuItem(
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFont
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("长度：${length.toInt()}")
                    Slider(
                        value = length,
                        onValueChange = { length = it },
                        valueRange = 4f..64f,
                        steps = 59,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeUpper, onCheckedChange = { includeUpper = it })
                            Text("A-Z", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeLower, onCheckedChange = { includeLower = it })
                            Text("a-z", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeNumbers, onCheckedChange = { includeNumbers = it })
                            Text("0-9", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeSymbols, onCheckedChange = { includeSymbols = it })
                            Text("!@#", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点击外部关闭 · 点击图标刷新密码",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = {
                val newPwd = generatePasswordUseCase.generate(makeConfig())
                preview = newPwd
                onPasswordGenerated(newPwd)
                configExpanded = false
            }
        )
    }
}
