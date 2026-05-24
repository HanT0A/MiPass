package com.hanzg.mipass.domain.usecase

import org.junit.Assert.*
import org.junit.Test

class GeneratePasswordUseCaseTest {
    private val useCase = GeneratePasswordUseCase()

    @Test
    fun `generate with all char types produces correct length`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 12, includeUppercase = true, includeLowercase = true,
            includeNumbers = true, includeSymbols = true
        )
        val password = useCase.generate(config)
        assertEquals(12, password.length)
    }

    @Test
    fun `generate minimum length 4`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 4, includeUppercase = true, includeLowercase = true,
            includeNumbers = true, includeSymbols = false
        )
        val password = useCase.generate(config)
        assertEquals(4, password.length)
    }

    @Test
    fun `generate maximum length 64`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 64, includeUppercase = false, includeLowercase = true,
            includeNumbers = false, includeSymbols = false
        )
        val password = useCase.generate(config)
        assertEquals(64, password.length)
    }

    @Test
    fun `generate lowercase only contains no uppercase`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 20, includeUppercase = false, includeLowercase = true,
            includeNumbers = false, includeSymbols = false
        )
        val password = useCase.generate(config)
        assertFalse(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isLowerCase() })
    }

    @Test
    fun `consecutive calls produce different passwords`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 16, includeUppercase = true, includeLowercase = true,
            includeNumbers = true, includeSymbols = true
        )
        val p1 = useCase.generate(config)
        val p2 = useCase.generate(config)
        assertNotEquals(p1, p2)
    }
}
