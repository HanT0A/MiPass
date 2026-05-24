package com.hanzg.mipass.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.domain.repository.PasswordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultUiState(
    val flatList: List<PasswordEntity> = emptyList(),
    val categories: List<String> = listOf("全部"),
    val searchQuery: String = "",
    val selectedCategory: String = "全部",
    val filterType: EntryType = EntryType.APP,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: PasswordRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow("全部")
    private val _filterType = MutableStateFlow<EntryType?>(EntryType.APP)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VaultUiState> = combine(
        repository.getAllPasswordsFlow(),
        _searchQuery,
        _selectedCategory,
        _filterType
    ) { allData, query, category, type ->
        val typeFiltered = if (type != null) {
            allData.filter { it.type == type }
        } else allData

        val categoryFiltered = if (category == "全部") {
            typeFiltered
        } else {
            typeFiltered.filter { it.category == category }
        }

        val flatList = if (query.isBlank()) {
            categoryFiltered
        } else {
            val lowerQuery = query.lowercase()
            categoryFiltered.filter {
                it.name.lowercase().contains(lowerQuery) ||
                        it.account.lowercase().contains(lowerQuery)
            }
        }

        VaultUiState(
            flatList = flatList,
            categories = listOf("全部") + typeFiltered.map { it.category }.distinct().sorted(),
            searchQuery = query,
            selectedCategory = category,
            filterType = type ?: EntryType.APP,
            isLoading = false,
            isEmpty = flatList.isEmpty()
        )
    }
    .catch { e ->
        Log.e("VaultViewModel", "Database flow error", e)
        emit(VaultUiState(isLoading = false, isEmpty = true))
    }
    .flowOn(Dispatchers.IO)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState(isLoading = true)
    )

    fun setFilterType(type: EntryType?) {
        _filterType.value = type
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun deletePassword(entity: PasswordEntity) {
        viewModelScope.launch {
            repository.deletePassword(entity)
        }
    }
}
