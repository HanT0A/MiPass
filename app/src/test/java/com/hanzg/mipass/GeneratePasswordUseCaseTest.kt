package com.hanzg.mipass

import com.hanzg.mipass.domain.usecase.GeneratePasswordUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeneratePasswordUseCaseTest {

    private lateinit var useCase: GeneratePasswordUseCase

    @Before
    fun setUp() {
        useCase = GeneratePasswordUseCase()
    }

    @Test
    fun `default config generates 16 char password`() {
        val config = GeneratePasswordUseCase.PasswordConfig()
        val password = useCase.generate(config)
        assertEquals(16, password.length)
    }

    @Test
    fun `password respects length setting`() {
        for (len in listOf(4, 8, 16, 32, 64)) {
            val config = GeneratePasswordUseCase.PasswordConfig(length = len)
            assertEquals(len, useCase.generate(config).length)
        }
    }

    @Test
    fun `password contains at least one char from each enabled set`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 32,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true
        )
        // Generate multiple and verify each
        repeat(20) {
            val password = useCase.generate(config)
            assertTrue(
                "Missing uppercase in: $password",
                password.any { it in 'A'..'Z' }
            )
            assertTrue(
                "Missing lowercase in: $password",
                password.any { it in 'a'..'z' }
            )
            assertTrue(
                "Missing digit in: $password",
                password.any { it in '0'..'9' }
            )
            assertTrue(
                "Missing symbol in: $password",
                password.any { it in "!@#\$%^&*()_+-=[]{}|;:,.<>?" }
            )
        }
    }

    @Test
    fun `uppercase only password`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 20,
            includeUppercase = true,
            includeLowercase = false,
            includeNumbers = false,
            includeSymbols = false
        )
        val password = useCase.generate(config)
        assertEquals(20, password.length)
        assertTrue(password.all { it in 'A'..'Z' })
    }

    @Test
    fun `numbers only password`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 8,
            includeUppercase = false,
            includeLowercase = false,
            includeNumbers = true,
            includeSymbols = false
        )
        val password = useCase.generate(config)
        assertEquals(8, password.length)
        assertTrue(password.all { it in '0'..'9' })
    }

    @Test
    fun `empty charset returns empty string`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 16,
            includeUppercase = false,
            includeLowercase = false,
            includeNumbers = false,
            includeSymbols = false
        )
        assertEquals("", useCase.generate(config))
    }

    @Test
    fun `randomness - consecutive generations produce different passwords`() {
        val config = GeneratePasswordUseCase.PasswordConfig(length = 64)
        val passwords = (1..10).map { useCase.generate(config) }
        // All should be unique
        assertEquals(passwords.size, passwords.distinct().size)
    }

    @Test
    fun `strength - empty password is 0`() {
        val config = GeneratePasswordUseCase.PasswordConfig()
        assertEquals(0, useCase.calculateStrength("", config))
    }

    @Test
    fun `strength - max score for long mixed password`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 64,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true
        )
        val password = useCase.generate(config)
        val strength = useCase.calculateStrength(password, config)
        assertTrue("Expected strength >= 80, got $strength", strength >= 80)
    }

    @Test
    fun `strength - low score for short single-type password`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 4,
            includeUppercase = false,
            includeLowercase = true,
            includeNumbers = false,
            includeSymbols = false
        )
        val password = useCase.generate(config)
        val strength = useCase.calculateStrength(password, config)
        assertTrue("Expected strength < 40, got $strength", strength < 40)
    }

    @Test
    fun `strength capped at 100`() {
        val config = GeneratePasswordUseCase.PasswordConfig(
            length = 128,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true
        )
        val password = useCase.generate(config)
        assertTrue(useCase.calculateStrength(password, config) <= 100)
    }
}
