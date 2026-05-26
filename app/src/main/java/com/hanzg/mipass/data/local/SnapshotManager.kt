package com.hanzg.mipass.data.local

import android.content.Context
import com.hanzg.mipass.utils.KeyStoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapshotManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStoreManager: KeyStoreManager
) {

    companion object {
        private const val SNAPSHOTS_DIR = "snapshots"
        private const val MAX_SNAPSHOTS = 5
        private const val SNAPSHOT_PREFIX = "mipass_snapshot_"
        private const val SNAPSHOT_EXTENSION = ".snapshot"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val AES_KEY_SIZE = 256
        private const val SNAPSHOT_KEY_INFO = "mipass-snapshot-key-v1"
    }

    private val snapshotsDir: File
        get() = File(context.filesDir, SNAPSHOTS_DIR).also { it.mkdirs() }

    /**
     * 创建静默快照：全库数据 → JSON → AES-GCM 加密（密钥由 DEK 派生） → 写入文件
     */
    suspend fun createSnapshot(allPasswords: List<PasswordEntity>) = withContext(Dispatchers.IO) {
        val snapshotKey = deriveSnapshotKey()
        val jsonData = serializeToJson(allPasswords)
        val encrypted = encryptData(jsonData, snapshotKey)
        java.util.Arrays.fill(snapshotKey, 0.toByte())
        writeSnapshotFile(encrypted)
        enforceFifoLimit()
    }

    /**
     * 读取并解密快照（密钥由 DEK 派生）
     */
    suspend fun restoreSnapshot(snapshotFile: File): List<PasswordEntity> =
        withContext(Dispatchers.IO) {
            val snapshotKey = deriveSnapshotKey()
            val encrypted = snapshotFile.readBytes()
            val jsonData = decryptData(encrypted, snapshotKey)
            java.util.Arrays.fill(snapshotKey, 0.toByte())
            deserializeFromJson(jsonData)
        }

    /**
     * 获取所有快照文件（按时间倒序）
     */
    fun listSnapshots(): List<File> {
        return snapshotsDir.listFiles()
            ?.filter { it.name.endsWith(SNAPSHOT_EXTENSION) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun deriveSnapshotKey(): ByteArray {
        val dek = keyStoreManager.getDEK()
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(dek, "HmacSHA256")
        mac.init(keySpec)
        val derived = mac.doFinal(SNAPSHOT_KEY_INFO.toByteArray(Charsets.UTF_8))
        java.util.Arrays.fill(dek, 0.toByte())
        return derived
    }

    private fun serializeToJson(passwords: List<PasswordEntity>): String {
        val jsonArray = JSONArray()
        passwords.forEach { entity ->
            val obj = JSONObject().apply {
                put("id", entity.id)
                put("entry_type", entity.type.name)
                put("name", entity.name)
                put("url", entity.url ?: "")
                put("account", entity.account)
                put("password", entity.password)
                put("category", entity.category)
                put("notes", entity.notes)
                put("icon_uri", entity.iconUri ?: "")
                put("created_at", entity.createdAt)
                put("updated_at", entity.updatedAt)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun deserializeFromJson(json: String): List<PasswordEntity> {
        val jsonArray = JSONArray(json)
        val result = mutableListOf<PasswordEntity>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            result.add(
                PasswordEntity(
                    id = obj.getString("id"),
                    type = com.hanzg.mipass.domain.model.EntryType.valueOf(obj.getString("entry_type")),
                    name = obj.getString("name"),
                    url = obj.getString("url").ifBlank { null },
                    account = obj.getString("account"),
                    password = obj.getString("password"),
                    category = obj.getString("category"),
                    notes = obj.getString("notes"),
                    iconUri = obj.optString("icon_uri").ifBlank { null },
                    createdAt = obj.getLong("created_at"),
                    updatedAt = obj.getLong("updated_at")
                )
            )
        }
        return result
    }

    private fun encryptData(plainText: String, keyBytes: ByteArray): ByteArray {
        val secretKey: SecretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // 格式: IV(12 bytes) + EncryptedData（密钥由 DEK 派生，不存于文件）
        val iv = cipher.iv
        val result = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)
        return result
    }

    private fun decryptData(encryptedData: ByteArray, keyBytes: ByteArray): String {
        val iv = encryptedData.copyOfRange(0, 12)
        val actualEncrypted = encryptedData.copyOfRange(12, encryptedData.size)
        val secretKey: SecretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decrypted = cipher.doFinal(actualEncrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun writeSnapshotFile(encryptedData: ByteArray) {
        val timestamp = System.currentTimeMillis()
        val file = File(snapshotsDir, "${SNAPSHOT_PREFIX}${timestamp}${SNAPSHOT_EXTENSION}")
        FileOutputStream(file).use { it.write(encryptedData) }
    }

    private fun enforceFifoLimit() {
        val files = listSnapshots()
        if (files.size > MAX_SNAPSHOTS) {
            // 删除最旧的快照
            files.drop(MAX_SNAPSHOTS).forEach { it.delete() }
        }
    }
}
