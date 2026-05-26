package com.hanzg.mipass.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*
import com.hanzg.mipass.ui.theme.DurationShort
import com.hanzg.mipass.ui.theme.JetBrainsMonoFont
import com.hanzg.mipass.ui.theme.MiPassEaseInOut
import com.hanzg.mipass.ui.navigation.MiPassBottomBar
import com.hanzg.mipass.ui.navigation.NavRoutes
import com.hanzg.mipass.ui.theme.WarningAmber
import com.hanzg.mipass.ui.theme.WarningOrange
import com.hanzg.mipass.utils.ClipboardUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onNavigate: (String) -> Unit,
    clipboardUtils: ClipboardUtils,
    viewModel: GeneratorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

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
                    Text("密码生成器", style = MaterialTheme.typography.titleMedium)
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ① 密码显示卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        "生成的密码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = state.password,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                fadeIn(tween(DurationShort, easing = MiPassEaseInOut))
                                    .togetherWith(fadeOut(tween(DurationShort)))
                            }
                        ) { password ->
                            Text(
                                text = password,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = JetBrainsMonoFont
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                clipboardUtils.copyText("密码", state.password)
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                PhosphorIcons.Regular.CopySimple,
                                contentDescription = "复制密码",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.regenerate()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                PhosphorIcons.Regular.ArrowsClockwise,
                                contentDescription = "重新生成",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    StrengthBar(strength = state.strength)
                }
            }

            // ② 长度控制卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("密码长度", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${state.length.toInt()}", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                " 位", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Slider(
                        value = state.length,
                        onValueChange = { viewModel.onLengthChanged(it) },
                        valueRange = 4f..64f, steps = 59,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("4", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("64", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ③ 字符类型卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("字符类型", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CharTypeCard("A-Z", "大写", state.includeUppercase, { viewModel.onUppercaseChanged(!state.includeUppercase) }, Modifier.weight(1f).aspectRatio(1f))
                        CharTypeCard("a-z", "小写", state.includeLowercase, { viewModel.onLowercaseChanged(!state.includeLowercase) }, Modifier.weight(1f).aspectRatio(1f))
                        CharTypeCard("0-9", "数字", state.includeNumbers, { viewModel.onNumbersChanged(!state.includeNumbers) }, Modifier.weight(1f).aspectRatio(1f))
                        CharTypeCard("!@#", "符号", state.includeSymbols, { viewModel.onSymbolsChanged(!state.includeSymbols) }, Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }

            // ④ 小贴士
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shadowElevation = 0.dp
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(PhosphorIcons.Regular.ShieldCheck, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("小贴士", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "密码长度越长，包含的字符类型越多，安全性越高。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        MiPassBottomBar(
            currentRoute = NavRoutes.Generator.route,
            onNavigate = onNavigate
        )
    }
}

@Composable
private fun CharTypeCard(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shadowElevation = 0.dp,
        onClick = onClick
    ) {
        Box {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Surface(
                    modifier = Modifier.size(16.dp).align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            PhosphorIcons.Regular.Check,
                            contentDescription = "已选",
                            modifier = Modifier.size(8.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrengthBar(strength: Int) {
    val color = when {
        strength >= 80 -> MaterialTheme.colorScheme.primary
        strength >= 60 -> WarningAmber
        strength >= 40 -> WarningOrange
        else -> MaterialTheme.colorScheme.error
    }
    val label = when {
        strength >= 80 -> "极强"
        strength >= 60 -> "强"
        strength >= 40 -> "中等"
        else -> "弱"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(modifier = Modifier.fillMaxWidth().height(6.dp), shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Surface(modifier = Modifier.fillMaxWidth(strength / 100f).height(6.dp), shape = RoundedCornerShape(3.dp), color = color) {}
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(PhosphorIcons.Regular.ShieldCheck, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text("$label·${strength}分", style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
