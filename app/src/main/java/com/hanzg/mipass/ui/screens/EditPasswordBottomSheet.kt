package com.hanzg.mipass.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*
import com.hanzg.mipass.domain.model.Password
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.domain.usecase.GeneratePasswordUseCase
import com.hanzg.mipass.utils.IconMatcher
import com.hanzg.mipass.ui.components.PasswordTextField

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditPasswordBottomSheet(
    entity: Password,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PasswordFormViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(entity.id) {
        viewModel.loadForEdit(entity.id)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        )
        // 固定弹窗 — 定位底部，内容内部滚动
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.67f),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 固定标题行
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            PhosphorIcons.Regular.X,
                            contentDescription = "取消",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "编辑",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = {
                            if (state.name.isBlank()) {
                                Toast.makeText(context, "请输入名称", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            viewModel.save { onSaved() }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            PhosphorIcons.Regular.Check,
                            contentDescription = "保存",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 可滚动内容
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // 图标
            val editIconPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    viewModel.onIconUriChanged(uri.toString())
                }
            }
            val iconLetter = IconMatcher.getIconLetter(state.name.ifBlank { "?" })
            val iconColor = IconMatcher.getIconColor(state.name.ifBlank { "?" })
            val iconResName = IconMatcher.getIconResource(state.name.ifBlank { "?" })
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { editIconPickerLauncher.launch("image/*") },
                    shape = RoundedCornerShape(8.dp),
                    color = iconColor.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!state.iconUri.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(Uri.parse(state.iconUri))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "自定义图标",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else if (iconResName != null) {
                            val resId = context.resources.getIdentifier(
                                iconResName, "drawable", context.packageName
                            )
                            if (resId != 0) {
                                Icon(
                                    painter = painterResource(id = resId),
                                    contentDescription = state.name,
                                    tint = iconColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Text(
                                    text = iconLetter,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = iconColor
                                )
                            }
                        } else {
                            Text(
                                text = iconLetter,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = iconColor
                            )
                        }
                    }
                }
                if (!state.iconUri.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(onClick = { viewModel.onIconUriChanged(null) }) {
                        Text("重置", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.type == EntryType.WEB) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChanged,
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.account,
                onValueChange = viewModel::onAccountChanged,
                label = { Text("账号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))


            val haptic = LocalHapticFeedback.current
            val genDefaults by viewModel.generatorDefaults.collectAsState()
            var genLength by remember { mutableFloatStateOf(genDefaults.length) }
            var genUpper by remember { mutableStateOf(genDefaults.uppercase) }
            var genLower by remember { mutableStateOf(genDefaults.lowercase) }
            var genDigits by remember { mutableStateOf(genDefaults.digits) }
            var genSymbols by remember { mutableStateOf(genDefaults.symbols) }
            var genExpanded by remember { mutableStateOf(false) }
            val genUseCase = remember { GeneratePasswordUseCase() }

            LaunchedEffect(entity.id) {
                genLength = genDefaults.length
                genUpper = genDefaults.uppercase
                genLower = genDefaults.lowercase
                genDigits = genDefaults.digits
                genSymbols = genDefaults.symbols
            }

            fun genConfig() = GeneratePasswordUseCase.PasswordConfig(
                length = genLength.toInt(),
                includeUppercase = genUpper,
                includeLowercase = genLower,
                includeNumbers = genDigits,
                includeSymbols = genSymbols
            )

            fun doGenerate() {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.onPasswordChanged(genUseCase.generate(genConfig()))
            }

            Box {
                PasswordTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = "密码",
                    modifier = Modifier.fillMaxWidth(),
                    additionalTrailingIcons = {
                        Icon(
                            imageVector = PhosphorIcons.Regular.Shuffle,
                            contentDescription = "生成随机密码",
                            modifier = Modifier
                                .size(20.dp)
                                .combinedClickable(
                                    onClick = { doGenerate() },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        genExpanded = true
                                    }
                                ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                )

                DropdownMenu(
                    expanded = genExpanded,
                    onDismissRequest = { genExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp
                ) {
                    DropdownMenuItem(
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("长度：${genLength.toInt()}")
                                Slider(
                                    value = genLength,
                                    onValueChange = { genLength = it },
                                    valueRange = 4f..64f,
                                    steps = 59,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = genUpper, onCheckedChange = { genUpper = it })
                                        Text("A-Z", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = genLower, onCheckedChange = { genLower = it })
                                        Text("a-z", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = genDigits, onCheckedChange = { genDigits = it })
                                        Text("0-9", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = genSymbols, onCheckedChange = { genSymbols = it })
                                        Text("!@#", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        },
                        onClick = { }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CategoryDropdownField(
                value = state.category,
                onValueChange = viewModel::onCategoryChanged,
                categories = viewModel.categories.collectAsState().value
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(20.dp))

                Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

}

@Composable
fun CategoryDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    categories: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = categories.filter { it.isNotBlank() }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("分类") },
            placeholder = { Text("如：社交、工作") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        PhosphorIcons.Regular.CaretDown,
                        contentDescription = "选择分类",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        if (filtered.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(120.dp).height(200.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                filtered.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                category,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onValueChange(category)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
