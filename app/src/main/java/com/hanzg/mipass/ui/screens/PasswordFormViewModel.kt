package com.hanzg.mipass.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanzg.mipass.data.local.AppPreferences
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.domain.repository.PasswordRepository
import com.hanzg.mipass.domain.usecase.GeneratePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PasswordFormState(
    val id: String? = null,
    val type: EntryType = EntryType.APP,
    val name: String = "",
    val url: String = "",
    val account: String = "",
    val password: String = "",
    val category: String = "其他",
    val notes: String = "",
    val iconUri: String? = null,
    val isNew: Boolean = true
)

data class GeneratorDefaults(
    val length: Float = 8f,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true
)

@HiltViewModel
class PasswordFormViewModel @Inject constructor(
    private val repository: PasswordRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _formState = MutableStateFlow(PasswordFormState())
    val formState: StateFlow<PasswordFormState> = _formState.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    val generatorDefaults: StateFlow<GeneratorDefaults> = appPreferences.settingsFlow
        .map { settings ->
            GeneratorDefaults(
                length = settings.generatorLength.toFloat(),
                uppercase = settings.generatorUppercase,
                lowercase = settings.generatorLowercase,
                digits = settings.generatorDigits,
                symbols = settings.generatorSymbols
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GeneratorDefaults())

    init {
        viewModelScope.launch {
            _categories.value = repository.getAllCategories()
        }
    }

    fun initializeForNew(type: EntryType) {
        _formState.value = PasswordFormState(
            type = type,
            isNew = true
        )
    }

    fun loadForEdit(passwordId: String) {
        viewModelScope.launch {
            _categories.value = repository.getAllCategories()
            val entity = repository.getPasswordById(passwordId)
            if (entity != null) {
                _formState.value = PasswordFormState(
                    id = entity.id,
                    type = entity.type,
                    name = entity.name,
                    url = entity.url ?: "",
                    account = entity.account,
                    password = entity.password,
                    category = entity.category,
                    notes = entity.notes,
                    iconUri = entity.iconUri,
                    isNew = false
                )
            }
        }
    }

    fun onNameChanged(name: String) {
        _formState.update { it.copy(name = name) }
    }

    fun onUrlChanged(url: String) {
        _formState.update { it.copy(url = url) }
    }

    fun onAccountChanged(account: String) {
        _formState.update { it.copy(account = account) }
    }

    fun onPasswordChanged(password: String) {
        _formState.update { it.copy(password = password) }
    }

    fun onCategoryChanged(category: String) {
        _formState.update { it.copy(category = category) }
    }

    fun onNotesChanged(notes: String) {
        _formState.update { it.copy(notes = notes) }
    }

    fun onIconUriChanged(uri: String?) {
        _formState.update { it.copy(iconUri = uri) }
    }

    fun onTypeChanged(type: EntryType) {
        _formState.update { it.copy(type = type) }
    }

    fun save(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _formState.value
            if (state.name.isBlank() || state.password.isBlank()) return@launch
            val entity = PasswordEntity(
                id = state.id ?: java.util.UUID.randomUUID().toString(),
                type = state.type,
                name = state.name,
                url = state.url.ifBlank { null },
                account = state.account,
                password = state.password,
                category = state.category.ifBlank { "其他" },
                notes = state.notes,
                iconUri = state.iconUri,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            if (state.isNew) {
                repository.insertPassword(entity)
            } else {
                repository.updatePassword(entity)
            }
            onSuccess()
        }
    }

    fun delete(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _formState.value
            val entity = repository.getPasswordById(state.id ?: return@launch)
            if (entity != null) {
                repository.deletePassword(entity)
                onSuccess()
            }
        }
    }
}
