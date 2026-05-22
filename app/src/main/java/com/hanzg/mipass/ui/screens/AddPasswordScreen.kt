package com.hanzg.mipass.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.domain.usecase.GeneratePasswordUseCase
import com.hanzg.mipass.utils.IconMatcher

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddPasswordScreen(
    entryType: String,
    onNavigateBack: () -> Unit,
    viewModel: PasswordFormViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsState()
    val title = "新增密码"

    LaunchedEffect(entryType) {
        viewModel.initializeForNew(EntryType.valueOf(entryType))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.ArrowLeft,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save { onNavigateBack() } }
                    ) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = state.type == EntryType.APP,
                    onClick = { viewModel.onTypeChanged(EntryType.APP) },
                    label = { Text("App") }
                )
                Spacer(modifier = Modifier.width(12.dp))
                FilterChip(
                    selected = state.type == EntryType.WEB,
                    onClick = { viewModel.onTypeChanged(EntryType.WEB) },
                    label = { Text("Web") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 图标 — 48dp touch target
            val iconPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    viewModel.onIconUriChanged(uri.toString())
                }
            }
            val iconLetter = IconMatcher.getIconLetter(state.name.ifBlank { "?" })
            val iconColor = IconMatcher.getIconColor(state.name.ifBlank { "?" })
            val iconResName = IconMatcher.getIconResource(state.name.ifBlank { "?" })
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { iconPickerLauncher.launch("image/*") },
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
                placeholder = { Text("如：微信、淘宝") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.type == EntryType.WEB) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChanged,
                    label = { Text("URL") },
                    placeholder = { Text("如：www.taobao.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.account,
                onValueChange = viewModel::onAccountChanged,
                label = { Text("账号") },
                placeholder = { Text("手机号 / 邮箱") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            var passwordVisible by remember { mutableStateOf(false) }
            val haptic = LocalHapticFeedback.current
            val genDefaults by viewModel.generatorDefaults.collectAsState()
            var genLength by remember { mutableFloatStateOf(genDefaults.length) }
            var genUpper by remember { mutableStateOf(genDefaults.uppercase) }
            var genLower by remember { mutableStateOf(genDefaults.lowercase) }
            var genDigits by remember { mutableStateOf(genDefaults.digits) }
            var genSymbols by remember { mutableStateOf(genDefaults.symbols) }
            var genExpanded by remember { mutableStateOf(false) }
            val genUseCase = remember { GeneratePasswordUseCase() }

            LaunchedEffect(entryType) {
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
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = { Text("密码") },
                    placeholder = { Text("点击右侧 Shuffle 生成") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) PhosphorIcons.Regular.EyeSlash
                                        else PhosphorIcons.Regular.Eye,
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )

                DropdownMenu(
                    expanded = genExpanded,
                    onDismissRequest = { genExpanded = false }
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
                placeholder = { Text("备注信息") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
