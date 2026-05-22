package com.hanzg.mipass.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.domain.repository.PasswordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PasswordDetailState(
    val entity: PasswordEntity? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class PasswordDetailViewModel @Inject constructor(
    private val repository: PasswordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordDetailState())
    val uiState: StateFlow<PasswordDetailState> = _uiState.asStateFlow()

    fun loadPassword(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val entity = repository.getPasswordById(id)
            _uiState.update { it.copy(entity = entity, isLoading = false) }
        }
    }

    fun deletePassword() {
        viewModelScope.launch {
            _uiState.value.entity?.let { repository.deletePassword(it) }
        }
    }
}
