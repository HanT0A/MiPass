package com.hanzg.mipass

import com.hanzg.mipass.data.local.BackupEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.SecretKey

class BackupEngineTest {

    private lateinit var engine: BackupEngine

    @Before
    fun setUp() {
        engine = BackupEngine()
    }

    @Test
    fun `passcode validation - 6 digits is valid`() {
        assertTrue(engine.isValidPasscode("123456"))
        assertTrue(engine.isValidPasscode("000000"))
        assertTrue(engine.isValidPasscode("999999"))
    }

    @Test
    fun `passcode validation - invalid cases`() {
        assertFalse(engine.isValidPasscode("12345"))
        assertFalse(engine.isValidPasscode("1234567"))
        assertFalse(engine.isValidPasscode("abcdef"))
        assertFalse(engine.isValidPasscode("12 456"))
        assertFalse(engine.isValidPasscode(""))
    }

    @Test
    fun `PBKDF2 key derivation is deterministic`() {
        val passcode = "123456"
        val salt = ByteArray(16) { it.toByte() }

        val deriveMethod = BackupEngine::class.java.getDeclaredMethod(
            "deriveKey", String::class.java, ByteArray::class.java
        )
        deriveMethod.isAccessible = true

        val key1 = deriveMethod.invoke(engine, passcode, salt)
        val key2 = deriveMethod.invoke(engine, passcode, salt)

        val hex1 = (key1 as SecretKey).encoded.joinToString("") { "%02x".format(it) }
        val hex2 = (key2 as SecretKey).encoded.joinToString("") { "%02x".format(it) }

        assertEquals(hex1, hex2)
    }

    @Test
    fun `PBKDF2 different passcodes produce different keys`() {
        val salt = ByteArray(16) { it.toByte() }

        val deriveMethod = BackupEngine::class.java.getDeclaredMethod(
            "deriveKey", String::class.java, ByteArray::class.java
        )
        deriveMethod.isAccessible = true

        val key1 = deriveMethod.invoke(engine, "123456", salt) as SecretKey
        val key2 = deriveMethod.invoke(engine, "654321", salt) as SecretKey

        assertFalse(key1.encoded.contentEquals(key2.encoded))
    }

    @Test
    fun `PBKDF2 different salts produce different keys`() {
        val salt1 = ByteArray(16) { it.toByte() }
        val salt2 = ByteArray(16) { (it + 1).toByte() }

        val deriveMethod = BackupEngine::class.java.getDeclaredMethod(
            "deriveKey", String::class.java, ByteArray::class.java
        )
        deriveMethod.isAccessible = true

        val key1 = deriveMethod.invoke(engine, "123456", salt1) as SecretKey
        val key2 = deriveMethod.invoke(engine, "123456", salt2) as SecretKey

        assertFalse(key1.encoded.contentEquals(key2.encoded))
    }
}
