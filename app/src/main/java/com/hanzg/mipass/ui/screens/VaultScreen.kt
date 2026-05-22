package com.hanzg.mipass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.ui.components.PasswordCard
import com.hanzg.mipass.ui.components.SearchBar
import com.hanzg.mipass.ui.theme.DurationLong
import com.hanzg.mipass.ui.theme.MiPassEaseOut
import com.hanzg.mipass.ui.theme.StaggerDelay
import com.hanzg.mipass.utils.ClipboardUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: (String) -> Unit,
    clipboardUtils: ClipboardUtils,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var typeExpanded by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Column(
                            modifier = Modifier.clickable { typeExpanded = true }
                        ) {
                            Text("密码库", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.padding(top = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (state.filterType == EntryType.APP) "App" else "Web",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    PhosphorIcons.Regular.CaretDown,
                                    contentDescription = "切换类型",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            PhosphorIcons.Regular.LockSimple,
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
                },
                actions = {
                    FilledIconButton(onClick = {
                        onNavigateToAdd(if (state.filterType == EntryType.APP) "APP" else "WEB")
                    }) {
                        Icon(
                            PhosphorIcons.Regular.Plus,
                            contentDescription = "新增密码"
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 分类筛选 + 搜索框同行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var catExpanded by remember { mutableStateOf(false) }
                val catOptions = remember(state.categories) {
                    if (state.categories.contains("全部")) state.categories
                    else listOf("全部") + state.categories
                }

                // 胶囊按钮样式的分类筛选
                Box {
                    Surface(
                        modifier = Modifier.clickable { catExpanded = true },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                PhosphorIcons.Regular.Funnel,
                                contentDescription = "筛选",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.selectedCategory,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                PhosphorIcons.Regular.CaretDown,
                                contentDescription = "展开分类",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false }
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
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                SearchBar(
                    query = state.searchQuery,
                    onQueryChanged = viewModel::onSearchQueryChanged,
                    modifier = Modifier.weight(1f)
                )
            }

            // 数据内容
            if (state.isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.LockSimple,
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
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(
                        count = state.flatList.size,
                        key = { state.flatList[it].id }
                    ) { index ->
                        var entered by remember(state.flatList[index].id) { mutableStateOf(false) }
                        LaunchedEffect(state.flatList[index].id) {
                            delay(index * StaggerDelay.toLong())
                            entered = true
                        }
                        AnimatedVisibility(
                            visible = entered,
                            enter = fadeIn(animationSpec = tween(DurationLong, easing = MiPassEaseOut)) +
                                    slideInVertically(
                                        initialOffsetY = { it / 4 },
                                        animationSpec = tween(DurationLong, easing = MiPassEaseOut)
                                    )
                        ) {
                            val item = state.flatList[index]
                            PasswordCard(
                                entity = item,
                                onCardClick = { onNavigateToDetail(item.id) },
                                onCopyAccount = { account ->
                                    clipboardUtils.copyText("账号", account)
                                },
                                onCopyPassword = { password ->
                                    clipboardUtils.copyText("密码", password)
                                },
                                onCopyUrl = if (item.type == EntryType.WEB && !item.url.isNullOrBlank()) {
                                    { url -> clipboardUtils.copyText("URL", url) }
                                } else null,
                                onMoreClick = { /* 预留更多操作入口 */ }
                            )
                        }
                    }
                }
            }
        }
    }
}
