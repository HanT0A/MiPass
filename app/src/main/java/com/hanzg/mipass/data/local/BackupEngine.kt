package com.hanzg.mipass.data.local

import android.content.Context
import android.net.Uri
import com.hanzg.mipass.domain.model.EntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class ExportResult(
    val file: File,
    val fileName: String
)

data class ImportResult(
    val totalCount: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val entries: List<PasswordEntity>
)

@Singleton
class BackupEngine @Inject constructor() {

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100_000
        private const val AES_KEY_SIZE = 256
        private const val SALT_LENGTH = 16
        private const val EXPORT_DIR = "exports"
        private const val MIPASS_PREFIX = "MiPass_Backup_"
        private const val MIPASS_EXTENSION = ".mipass"
        private const val MIME_TYPE = "application/octet-stream"
    }

    /**
     * 导出管线：
     * JSON 序列化 → PBKDF2(提取码) → AES-GCM 加密 → .mipass 文件
     *
     * 文件格式: SALT(16) + IV(12) + CIPHERTEXT
     */
    suspend fun exportPasswords(
        context: Context,
        entries: List<PasswordEntity>,
        passcode: String
    ): ExportResult = withContext(Dispatchers.IO) {
        val json = serializeToJson(entries)
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val aesKey = deriveKey(passcode, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

        // 组装: SALT + IV + CIPHERTEXT
        val output = ByteArrayOutputStream()
        output.write(salt)
        output.write(iv)
        output.write(ciphertext)
        val encryptedData = output.toByteArray()

        // 写入缓存目录
        val exportDir = File(context.cacheDir, EXPORT_DIR).also { it.mkdirs() }
        val timestamp = System.currentTimeMillis()
        val fileName = "${MIPASS_PREFIX}${timestamp}${MIPASS_EXTENSION}"
        val file = File(exportDir, fileName)
        file.writeBytes(encryptedData)

        ExportResult(file, fileName)
    }

    /**
     * 导入管线：
     * 文件读取 → 提取 SALT + IV + CIPHERTEXT → PBKDF2 派生密钥 → AES-GCM 解密 → JSON 反序列化
     */
    suspend fun importPasswords(
        context: Context,
        uri: Uri,
        passcode: String
    ): List<PasswordEntity> = withContext(Dispatchers.IO) {
        val encryptedData = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes()
        } ?: throw SecurityException("无法读取文件")

        // 解析: SALT(16) + IV(12) + CIPHERTEXT
        val salt = encryptedData.copyOfRange(0, SALT_LENGTH)
        val iv = encryptedData.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, encryptedData.size)

        val aesKey = deriveKey(passcode, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val plaintext = cipher.doFinal(ciphertext)
        val json = String(plaintext, Charsets.UTF_8)

        deserializeFromJson(json)
    }

    /**
     * 去重合并算法：
     * 基于 (name + account) 或 (url + account) 比对
     * 命中 → 跳过，未命中 → 插入
     */
    suspend fun mergeWithDedup(
        imported: List<PasswordEntity>,
        existingDao: PasswordDao
    ): ImportResult = withContext(Dispatchers.IO) {
        var importedCount = 0
        var skippedCount = 0

        imported.forEach { entry ->
            val duplicate = if (entry.type == EntryType.WEB && !entry.url.isNullOrBlank()) {
                existingDao.findByUrlAndAccount(entry.url, entry.account)
            } else {
                existingDao.findByNameAndAccount(entry.name, entry.account)
            }

            if (duplicate != null) {
                skippedCount++
            } else {
                existingDao.insertPassword(entry)
                importedCount++
            }
        }

        ImportResult(
            totalCount = imported.size,
            importedCount = importedCount,
            skippedCount = skippedCount,
            entries = imported
        )
    }

    fun cleanupExportDir(context: Context) {
        val exportDir = File(context.cacheDir, EXPORT_DIR)
        if (exportDir.exists()) {
            exportDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun serializeToJson(entries: List<PasswordEntity>): String {
        val jsonArray = JSONArray()
        entries.forEach { entity ->
            val obj = JSONObject().apply {
                put("id", entity.id)
                put("entry_type", entity.type.name)
                put("name", entity.name)
                put("url", entity.url ?: "")
                put("account", entity.account)
                put("password", entity.password)
                put("category", entity.category)
                put("notes", entity.notes)
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
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    type = EntryType.valueOf(obj.getString("entry_type")),
                    name = obj.getString("name"),
                    url = obj.optString("url", "").ifBlank { null },
                    account = obj.getString("account"),
                    password = obj.getString("password"),
                    category = obj.getString("category"),
                    notes = obj.optString("notes", ""),
                    createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updated_at", System.currentTimeMillis())
                )
            )
        }
        return result
    }

    private fun deriveKey(passcode: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /** 校验提取码合法性：6 位数字 */
    fun isValidPasscode(passcode: String): Boolean {
        return passcode.length == 6 && passcode.all { it.isDigit() }
    }
}
