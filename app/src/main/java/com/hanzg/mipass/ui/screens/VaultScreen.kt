package com.hanzg.mipass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.ui.components.PasswordCard
import com.hanzg.mipass.ui.components.SearchBar
import com.hanzg.mipass.ui.navigation.MiPassBottomBar
import com.hanzg.mipass.ui.navigation.NavRoutes
import com.hanzg.mipass.utils.ClipboardUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: (String) -> Unit,
    onNavigate: (String) -> Unit,
    clipboardUtils: ClipboardUtils,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var typeExpanded by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }
    var entityToDelete by remember { mutableStateOf<PasswordEntity?>(null) }
    var entityToEdit by remember { mutableStateOf<PasswordEntity?>(null) }

    if (entityToDelete != null) {
        AlertDialog(
            onDismissRequest = { entityToDelete = null },
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定要删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    entityToDelete?.let { viewModel.deletePassword(it) }
                    entityToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entityToDelete = null }) {
                    Text("取消")
                }
            }
        )
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
                    val catOptions = remember(state.categories) {
                        if (state.categories.contains("全部")) state.categories
                        else listOf("全部") + state.categories
                    }

                    // 左侧等宽 — 分类筛选
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        val catExpandedState = remember { MutableTransitionState(false) }
                        catExpandedState.targetState = catExpanded
                        var anchorHeightPx by remember { mutableIntStateOf(0) }

                        Box {
                            Surface(
                                modifier = Modifier
                                    .clickable { catExpanded = true }
                                    .onGloballyPositioned { anchorHeightPx = it.size.height },
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        PhosphorIcons.Regular.Funnel,
                                        contentDescription = "筛选",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = state.selectedCategory,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        PhosphorIcons.Regular.CaretDown,
                                        contentDescription = "展开分类",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (catExpandedState.currentState || catExpandedState.targetState) {
                                Popup(
                                    alignment = Alignment.TopStart,
                                    offset = IntOffset(0, anchorHeightPx),
                                    onDismissRequest = { catExpanded = false },
                                    properties = PopupProperties(focusable = true)
                                ) {
                                    Box {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visibleState = catExpandedState,
                                            enter = fadeIn(
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ) + scaleIn(
                                                transformOrigin = TransformOrigin(0.5f, 0f),
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ),
                                            exit = fadeOut(
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ) + scaleOut(
                                                transformOrigin = TransformOrigin(0.5f, 0f),
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                shadowElevation = 4.dp,
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .width(IntrinsicSize.Min)
                                                        .heightIn(max = 240.dp)
                                                        .verticalScroll(rememberScrollState())
                                                ) {
                                                    catOptions.forEach { cat ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    cat,
                                                                    color = if (cat == state.selectedCategory)
                                                                        MaterialTheme.colorScheme.primary
                                                                    else MaterialTheme.colorScheme.onSurface
                                                                )
                                                            },
                                                            onClick = {
                                                                viewModel.onCategorySelected(cat)
                                                                catExpanded = false
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }  // close left weight Box

                    // 居中标题
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("密码库", style = MaterialTheme.typography.titleMedium)
                    }

                    // 右侧等宽 — 图标
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        // 类型切换图标
                            Box {
                                IconButton(
                                    onClick = { typeExpanded = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.filterType == EntryType.APP)
                                            PhosphorIcons.Regular.DeviceMobile
                                        else PhosphorIcons.Regular.Globe,
                                        contentDescription = "切换类型",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 4.dp,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                                DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            PhosphorIcons.Regular.DeviceMobile,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (state.filterType == EntryType.APP)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "App",
                                            color = if (state.filterType == EntryType.APP)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setFilterType(EntryType.APP)
                                    typeExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            PhosphorIcons.Regular.Globe,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (state.filterType == EntryType.WEB)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Web",
                                            color = if (state.filterType == EntryType.WEB)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setFilterType(EntryType.WEB)
                                    typeExpanded = false
                                }
                            )
                        }
                    }

                            // 添加
                            IconButton(
                            onClick = {
                                onNavigateToAdd(if (state.filterType == EntryType.APP) "APP" else "WEB")
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                PhosphorIcons.Regular.Plus,
                                contentDescription = "新增密码",
                                    modifier = Modifier.size(18.dp)
                            )
                        }
                }  // close right Row
                }  // close right weight Box
                }
            }
            }
        },
        bottomBar = {
            MiPassBottomBar(
                currentRoute = NavRoutes.Vault.route,
                onNavigate = onNavigate
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
        ) {
            // 搜索框 — 独占一行
            SearchBar(
                query = state.searchQuery,
                onQueryChanged = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(50.dp)
            )

            // 数据内容
            if (state.isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.DeviceMobile,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "暂无密码",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击右上角 + 添加密码",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        count = state.flatList.size,
                        key = { state.flatList[it].id }
                    ) { index ->
                        val item = state.flatList[index]
                        PasswordCard(
                            entity = item,
                            onCardClick = { onNavigateToDetail(item.id) },
                            onCopyAccount = { clipboardUtils.copyText("账号", item.account) },
                            onCopyPassword = { clipboardUtils.copyText("密码", item.password) },
                            onCopyUrl = if (item.type == EntryType.WEB && !item.url.isNullOrBlank()) {
                                { clipboardUtils.copyText("URL", item.url) }
                            } else null,
                            onEdit = { entityToEdit = item },
                            onDelete = { entityToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // Scrim when dropdown is open
    if (typeExpanded || catExpanded) {
        Popup {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        typeExpanded = false
                        catExpanded = false
                    }
            ) {
            }
        }
    }

    entityToEdit?.let { entity ->
        EditPasswordBottomSheet(
            entity = entity,
            onDismiss = { entityToEdit = null },
            onDelete = {
                entityToEdit?.let { viewModel.deletePassword(it) }
                entityToEdit = null
            },
            onSaved = { entityToEdit = null }
        )
    }
}
