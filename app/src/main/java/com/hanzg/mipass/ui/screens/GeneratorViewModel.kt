package com.hanzg.mipass.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanzg.mipass.data.local.AppPreferences
import com.hanzg.mipass.domain.usecase.GeneratePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeneratorUiState(
    val password: String = "",
    val length: Float = 8f,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val strength: Int = 0
)

@HiltViewModel
class GeneratorViewModel @Inject constructor(
    private val generatePasswordUseCase: GeneratePasswordUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneratorUiState())
    val uiState: StateFlow<GeneratorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferences.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        length = settings.generatorLength.toFloat(),
                        includeUppercase = settings.generatorUppercase,
                        includeLowercase = settings.generatorLowercase,
                        includeNumbers = settings.generatorDigits,
                        includeSymbols = settings.generatorSymbols
                    )
                }
                regenerate()
            }
        }
    }

    fun onLengthChanged(length: Float) {
        _uiState.update { it.copy(length = length) }
        regenerate()
    }

    fun onUppercaseChanged(include: Boolean) {
        _uiState.update { it.copy(includeUppercase = include) }
        regenerate()
    }

    fun onLowercaseChanged(include: Boolean) {
        _uiState.update { it.copy(includeLowercase = include) }
        regenerate()
    }

    fun onNumbersChanged(include: Boolean) {
        _uiState.update { it.copy(includeNumbers = include) }
        regenerate()
    }

    fun onSymbolsChanged(include: Boolean) {
        _uiState.update { it.copy(includeSymbols = include) }
        regenerate()
    }

    fun regenerate() {
        val state = _uiState.value
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = state.length.toInt(),
            includeUppercase = state.includeUppercase,
            includeLowercase = state.includeLowercase,
            includeNumbers = state.includeNumbers,
            includeSymbols = state.includeSymbols
        )
        val password = generatePasswordUseCase.generate(config)
        val strength = generatePasswordUseCase.calculateStrength(password, config)
        _uiState.update { it.copy(password = password, strength = strength) }
    }
}
