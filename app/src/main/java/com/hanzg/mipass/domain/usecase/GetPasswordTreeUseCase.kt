package com.hanzg.mipass.domain.usecase

import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.domain.repository.PasswordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetPasswordTreeUseCase @Inject constructor(
    private val repository: PasswordRepository
) {
    fun buildFlatList(
        searchQuery: Flow<String>,
        selectedCategory: Flow<String>,
        filterType: Flow<EntryType?>
    ): Flow<List<PasswordEntity>> {
        return combine(
            repository.getAllPasswordsFlow(),
            searchQuery,
            selectedCategory,
            filterType
        ) { allData, query, category, type ->
            val typeFiltered = if (type != null) {
                allData.filter { it.type == type }
            } else allData

            val categoryFiltered = if (category == "全部") {
                typeFiltered
            } else {
                typeFiltered.filter { it.category == category }
            }

            if (query.isBlank()) {
                categoryFiltered
            } else {
                val lowerQuery = query.lowercase()
                categoryFiltered.filter {
                    it.name.lowercase().contains(lowerQuery) ||
                            it.account.lowercase().contains(lowerQuery)
                }
            }
        }
    }

    fun getDistinctCategories(allData: List<PasswordEntity>): List<String> {
        return listOf("全部") + allData.map { it.category }.distinct().sorted()
    }
}
