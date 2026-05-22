package com.hanzg.mipass.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.utils.IconMatcher
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.ui.theme.DurationShort
import com.hanzg.mipass.ui.theme.JetBrainsMonoFont
import com.hanzg.mipass.ui.theme.MiPassEaseInOut
import com.hanzg.mipass.utils.ClipboardUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailScreen(
    passwordId: String,
    onNavigateBack: () -> Unit,
    clipboardUtils: ClipboardUtils,
    viewModel: PasswordDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showEditSheet by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(passwordId) {
        viewModel.loadPassword(passwordId)
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定要删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePassword()
                    showDeleteConfirm = false
                    onNavigateBack()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("密码详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.ArrowLeft,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val entity = state.entity ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标 — 56dp display
            val iconLetter = IconMatcher.getIconLetter(entity.name)
            val iconColor = IconMatcher.getIconColor(entity.name)
            val iconResName = IconMatcher.getIconResource(entity.name)
            val context = LocalContext.current
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!entity.iconUri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(Uri.parse(entity.iconUri))
                                .crossfade(true)
                                .build(),
                            contentDescription = entity.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else if (iconResName != null) {
                        val resId = context.resources.getIdentifier(
                            iconResName, "drawable", context.packageName
                        )
                        if (resId != 0) {
                            Icon(
                                painter = painterResource(id = resId),
                                contentDescription = entity.name,
                                tint = iconColor,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Text(
                                text = iconLetter,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = iconColor
                            )
                        }
                    } else {
                        Text(
                            text = iconLetter,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = iconColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DetailRow("名称", entity.name)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (entity.type == EntryType.WEB && !entity.url.isNullOrBlank()) {
                        DetailRow("URL", entity.url) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            clipboardUtils.copyText("URL", entity.url)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    DetailRow("账号", entity.account) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        clipboardUtils.copyText("账号", entity.account)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "密码",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp)
                        )
                        Crossfade(
                            targetState = passwordVisible,
                            modifier = Modifier.weight(1f),
                            animationSpec = tween(DurationShort, easing = MiPassEaseInOut)
                        ) { visible ->
                            Text(
                                text = if (visible) entity.password else "••••••••",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = if (visible) JetBrainsMonoFont
                                        else MaterialTheme.typography.bodyLarge.fontFamily
                                )
                            )
                        }
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) PhosphorIcons.Regular.EyeSlash
                                    else PhosphorIcons.Regular.Eye,
                                contentDescription = if (passwordVisible) "隐藏明文密码" else "显示明文密码",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                clipboardUtils.copyText("密码", entity.password)
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Regular.CopySimple,
                                contentDescription = "复制密码",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow("分类", entity.category)
                }
            }

            // Notes card
            if (entity.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "备注",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = entity.notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showEditSheet = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    PhosphorIcons.Regular.PencilSimple,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("编辑此记录")
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    PhosphorIcons.Regular.TrashSimple,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("删除此记录", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showEditSheet && state.entity != null) {
        EditPasswordBottomSheet(
            entity = state.entity!!,
            onDismiss = { showEditSheet = false },
            onDelete = {
                viewModel.deletePassword()
                onNavigateBack()
            },
            onSaved = {
                showEditSheet = false
                viewModel.loadPassword(passwordId)
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = PhosphorIcons.Regular.CopySimple,
                    contentDescription = "复制$label",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
