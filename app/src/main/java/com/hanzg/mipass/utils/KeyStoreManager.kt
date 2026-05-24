package com.hanzg.mipass.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyStoreManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_ALIAS = "mipass_kek"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val DEK_SIZE_BYTES = 32 // 256-bit DEK
    }

    /**
     * 生成或获取 TEE 保护的密钥加密密钥 (KEK)
     */
    fun getOrCreateKEK(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }

        // 首次安装，生成新的 KEK（TEE 内生成，永不离开安全硬件）
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * 生成随机的 256 位数据加密密钥 (DEK)
     */
    fun generateDEK(): ByteArray {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(DEK_SIZE_BYTES * 8)
        return keyGenerator.generateKey().encoded
    }

    /**
     * 用 KEK 加密 DEK
     */
    fun encryptDEK(kek: SecretKey, dek: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val encryptedDek = cipher.doFinal(dek)
        val iv = cipher.iv
        return Pair(encryptedDek, iv)
    }

    /**
     * 用 KEK 解密 DEK
     */
    fun decryptDEK(kek: SecretKey, encryptedDek: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encryptedDek)
    }

    /**
     * 创建仅初始化（不解密）的 Cipher 实例，用于 BiometricPrompt CryptoObject 绑定
     */
    fun getDecryptCipher(kek: SecretKey, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher
    }
}
