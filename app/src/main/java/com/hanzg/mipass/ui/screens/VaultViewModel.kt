package com.hanzg.mipass.ui.screens

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanzg.mipass.domain.model.Password
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.domain.repository.PasswordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class VaultUiState(
    val flatList: List<Password> = emptyList(),
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
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _selectedCategory = MutableStateFlow("全部")
    private val _filterType = MutableStateFlow<EntryType?>(EntryType.APP)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VaultUiState> = combine(
        _searchQuery, _selectedCategory, _filterType
    ) { query, category, type ->
        Triple(query, category, type)
    }
        .flatMapLatest { (query, category, type) ->
            combine(
                repository.searchPasswords(query, category, null),
                repository.searchPasswords(query, "全部", null)
            ) { filteredEntries, allCatEntries ->
                val typeFiltered = type?.let { t -> filteredEntries.filter { it.type == t } } ?: filteredEntries
                val catsForType = type?.let { t ->
                    allCatEntries.filter { it.type == t }.map { it.category }.distinct().filter { it.isNotBlank() }.sorted()
                } ?: allCatEntries.map { it.category }.distinct().filter { it.isNotBlank() }.sorted()
                VaultUiState(
                    flatList = typeFiltered,
                    categories = listOf("全部") + catsForType,
                    searchQuery = query,
                    selectedCategory = category,
                    filterType = type ?: EntryType.APP,
                    isLoading = false,
                    isEmpty = typeFiltered.isEmpty()
                )
            }
        }
        .catch { e ->
            Log.e("VaultViewModel", "Database flow error", e)
            emit(VaultUiState(isLoading = false, isEmpty = true))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(30_000),
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

    fun deletePassword(entity: Password) {
        viewModelScope.launch {
            repository.deletePassword(entity)
        }
    }
}
