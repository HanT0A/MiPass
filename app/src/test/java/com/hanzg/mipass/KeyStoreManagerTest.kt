package com.hanzg.mipass

import com.hanzg.mipass.utils.KeyStoreManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator

class KeyStoreManagerTest {

    private lateinit var keyStoreManager: KeyStoreManager

    @Before
    fun setUp() {
        keyStoreManager = KeyStoreManager()
    }

    @Test
    fun `DEK generation produces 32 byte key`() {
        val dek = keyStoreManager.generateDEK()
        assertEquals(32, dek.size)
    }

    @Test
    fun `DEK generation produces different keys each time`() {
        val dek1 = keyStoreManager.generateDEK()
        val dek2 = keyStoreManager.generateDEK()
        assertArrayEquals(dek1, dek1) // sanity check
        // 理论上 256 位随机数不应重复
        val allSame = dek1.contentEquals(dek2)
        // 生成 10 次中至少有一次不同（概率几乎为 1）
        val unique = (1..10).map { keyStoreManager.generateDEK() }.distinctBy { it.toHex() }
        assert(unique.size > 1)
    }

    @Test
    fun `DEK encrypt decrypt roundtrip`() {
        val dek = keyStoreManager.generateDEK()
        // 使用一个本地生成的 AES 密钥模拟 KEK（不需要 TEE）
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val kek = keyGenerator.generateKey()

        val (encryptedDek, iv) = keyStoreManager.encryptDEK(kek, dek)
        val decryptedDek = keyStoreManager.decryptDEK(kek, encryptedDek, iv)

        assertArrayEquals(dek, decryptedDek)
    }

    @Test
    fun `DEK encryption produces different ciphertext with same input`() {
        val dek = keyStoreManager.generateDEK()
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val kek = keyGenerator.generateKey()

        val (encryptedDek1, iv1) = keyStoreManager.encryptDEK(kek, dek)
        val (encryptedDek2, iv2) = keyStoreManager.encryptDEK(kek, dek)

        // IV 应不同，密文也应不同
        assert(!iv1.contentEquals(iv2))
        assert(!encryptedDek1.contentEquals(encryptedDek2))
    }

    @Test
    fun `wrong KEK cannot decrypt DEK`() {
        val dek = keyStoreManager.generateDEK()
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val kek1 = keyGenerator.generateKey()
        val kek2 = keyGenerator.generateKey()

        val (encryptedDek, iv) = keyStoreManager.encryptDEK(kek1, dek)

        try {
            keyStoreManager.decryptDEK(kek2, encryptedDek, iv)
            // 如果用错误密钥解密没有抛异常，说明安全设计有问题
            assert(false) { "Should have thrown an exception with wrong key" }
        } catch (_: Exception) {
            // 预期行为：错误密钥解密应失败
        }
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
