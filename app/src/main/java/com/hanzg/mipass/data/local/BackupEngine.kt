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

enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    MIPASS(".mipass", "application/octet-stream", ".mipass 加密"),
    JSON(".json", "application/json", "JSON 明文"),
    CSV(".csv", "text/csv", "CSV 明文")
}

enum class ImportFormat(val isEncrypted: Boolean) {
    MIPASS(true),
    JSON(false),
    CSV(false),
    UNKNOWN(false)
}

private val CSV_HEADER = arrayOf("name", "account", "password", "url", "category", "notes", "type")
private val CSV_REQUIRED = setOf("name", "account", "password")

@Singleton
class BackupEngine @Inject constructor() {

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 600_000
        private const val PBKDF2_ITERATIONS_LEGACY = 100_000
        private const val FILE_VERSION_V2: Byte = 0x02
        private const val AES_KEY_SIZE = 256
        private const val SALT_LENGTH = 16
        private const val EXPORT_DIR = "exports"
        private const val MIPASS_PREFIX = "MiPass_Backup_"
        private const val MIPASS_EXTENSION = ".mipass"
        private const val MIME_TYPE = "application/octet-stream"
        private val secureRandom = SecureRandom()
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
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val aesKey = deriveKey(passcode, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

        // 组装: VERSION(1) + SALT(16) + IV(12) + CIPHERTEXT
        val output = ByteArrayOutputStream()
        output.write(FILE_VERSION_V2.toInt())
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

        // 检测版本：V2 以 0x02 开头，旧版无版本字节
        val isV2 = encryptedData.isNotEmpty() && encryptedData[0] == FILE_VERSION_V2
        val offset = if (isV2) 1 else 0
        val salt = encryptedData.copyOfRange(offset, offset + SALT_LENGTH)
        val iv = encryptedData.copyOfRange(offset + SALT_LENGTH, offset + SALT_LENGTH + GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(offset + SALT_LENGTH + GCM_IV_LENGTH, encryptedData.size)

        deserializeFromJson(decryptWithFallback(passcode, salt, iv, ciphertext, isV2))
    }

    /**
     * 解密数据，优先用新参数，失败回退旧参数以兼容旧文件
     */
    private fun decryptWithFallback(
        passcode: String, salt: ByteArray, iv: ByteArray,
        ciphertext: ByteArray, isV2: Boolean
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        return try {
            val aesKey = deriveKey(passcode, salt, PBKDF2_ITERATIONS)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            if (isV2) throw SecurityException("解密失败：提取码错误或文件已损坏")
            // 旧文件：回退 100K iterations
            val legacyKey = deriveKey(passcode, salt, PBKDF2_ITERATIONS_LEGACY)
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }
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
            try {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "")
                val password = obj.optString("password", "")
                val account = obj.optString("account", "")

                if (name.isBlank() || password.isBlank() || account.isBlank()) {
                    android.util.Log.w("BackupEngine", "跳过无效条目$i：缺少必填字段")
                    continue
                }

                result.add(
                    PasswordEntity(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        type = try { EntryType.valueOf(obj.getString("entry_type")) }
                            catch (_: Exception) { EntryType.APP },
                        name = name,
                        url = obj.optString("url", "").ifBlank { null },
                        account = account,
                        password = password,
                        category = obj.optString("category", "其他"),
                        notes = obj.optString("notes", ""),
                        createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updated_at", System.currentTimeMillis())
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w("BackupEngine", "跳过格式错误条目$i: ${e.message}")
            }
        }
        return result
    }

    private fun deriveKey(passcode: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, iterations, AES_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /** 校验提取码合法性：≥8 位，含字母和数字 */
    fun isValidPasscode(passcode: String): Boolean {
        return passcode.length >= 8 &&
                passcode.any { it.isLetter() } &&
                passcode.any { it.isDigit() }
    }

    // === 通用格式导出 ===

    fun exportToJsonString(entries: List<PasswordEntity>): String = serializeToJson(entries)

    fun exportToCsvString(entries: List<PasswordEntity>): String {
        val sb = StringBuilder()
        sb.appendLine(CSV_HEADER.joinToString(","))
        entries.forEach { entity ->
            sb.appendLine(toCsvRow(entity))
        }
        return sb.toString()
    }

    private fun toCsvRow(entity: PasswordEntity): String {
        val typeStr = entity.type.name
        val urlStr = entity.url ?: ""
        return listOf(
            entity.name, entity.account, entity.password,
            urlStr, entity.category, entity.notes, typeStr
        ).joinToString(",") { escapeCsv(it) }
    }

    private fun escapeCsv(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

    // === 通用格式导入 ===

    fun deserializeFromJsonString(json: String): List<PasswordEntity> = deserializeFromJson(json)

    fun importFromCsvString(csv: String): List<PasswordEntity> {
        val cleaned = csv.removePrefix("﻿")
        val lines = parseCsvLines(cleaned)
        if (lines.isEmpty()) return emptyList()

        val header = lines.first()
        val colIndex = mutableMapOf<String, Int>()
        header.forEachIndexed { i, col -> colIndex[col.lowercase().trim()] = i }

        for (required in CSV_REQUIRED) {
            if (required !in colIndex) {
                throw IllegalArgumentException("CSV缺少必填列: $required")
            }
        }

        val result = mutableListOf<PasswordEntity>()
        for (i in 1 until lines.size) {
            val row = lines[i]
            try {
                val name = field(row, colIndex["name"]!!).trim()
                val account = field(row, colIndex["account"]!!).trim()
                val password = field(row, colIndex["password"]!!).trim()
                if (name.isBlank() || account.isBlank() || password.isBlank()) continue

                val typeStr = field(row, colIndex["type"]).trim().uppercase()
                val entryType = if (typeStr == "WEB") EntryType.WEB else EntryType.APP

                result.add(
                    PasswordEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        type = entryType,
                        name = name,
                        url = field(row, colIndex["url"]).trim().ifBlank { null },
                        account = account,
                        password = password,
                        category = field(row, colIndex["category"]).trim().ifBlank { "其他" },
                        notes = field(row, colIndex["notes"]).trim(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) { }
        }
        return result
    }

    private fun field(row: List<String>, indexKey: Int?): String {
        return if (indexKey != null && indexKey < row.size) row[indexKey] else ""
    }

    private fun parseCsvLines(csv: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var i = 0
        while (i < csv.length) {
            val row = mutableListOf<String>()
            while (i < csv.length) {
                if (csv[i] == '"') {
                    val sb = StringBuilder()
                    i++
                    while (i < csv.length) {
                        if (csv[i] == '"') {
                            if (i + 1 < csv.length && csv[i + 1] == '"') {
                                sb.append('"')
                                i += 2
                            } else {
                                i++
                                break
                            }
                        } else {
                            sb.append(csv[i])
                            i++
                        }
                    }
                    row.add(sb.toString())
                } else {
                    val start = i
                    while (i < csv.length && csv[i] != ',' && csv[i] != '\n' && csv[i] != '\r') i++
                    row.add(csv.substring(start, i))
                }
                if (i < csv.length && csv[i] == ',') {
                    i++
                } else {
                    break
                }
            }
            while (i < csv.length && (csv[i] == '\r' || csv[i] == '\n')) i++
            if (row.isNotEmpty() && row.any { it.isNotBlank() }) {
                result.add(row)
            }
        }
        return result
    }

    /** 根据文件扩展名检测导入格式 */
    fun detectImportFormat(fileName: String?): ImportFormat {
        val lower = fileName?.lowercase() ?: ""
        return when {
            lower.endsWith(".mipass") -> ImportFormat.MIPASS
            lower.endsWith(".json") -> ImportFormat.JSON
            lower.endsWith(".csv") -> ImportFormat.CSV
            else -> ImportFormat.UNKNOWN
        }
    }

    /** 获取 URI 的显示文件名 */
    fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }
}
