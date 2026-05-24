package com.hanzg.mipass.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hanzg.mipass.utils.KeyStoreManager
import net.sqlcipher.database.SupportFactory
import javax.crypto.SecretKey

@Database(
    entities = [PasswordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MiPassDatabase : RoomDatabase() {

    abstract fun passwordDao(): PasswordDao

    companion object {
        private const val DATABASE_NAME = "mipass.db"
        private const val DEK_PREFS_NAME = "mipass_dek_prefs"
        private const val KEY_ENCRYPTED_DEK = "encrypted_dek"
        private const val KEY_DEK_IV = "dek_iv"

        @Volatile
        private var INSTANCE: MiPassDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE password_entries ADD COLUMN icon_uri TEXT")
            }
        }

        fun getInstance(context: Context, keyStoreManager: KeyStoreManager): MiPassDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, keyStoreManager).also {
                    INSTANCE = it
                }
            }
        }

        private fun buildDatabase(context: Context, keyStoreManager: KeyStoreManager): MiPassDatabase {
            return try {
                // 迁移旧 KEK（无用户认证绑定）到新 KEK（绑定用户认证）
                val prefs = createSecurePrefs(context)
                val encB64 = prefs.getString(KEY_ENCRYPTED_DEK, null)
                val ivB64 = prefs.getString(KEY_DEK_IV, null)
                if (encB64 != null && ivB64 != null) {
                    val migrated = keyStoreManager.migrateKEK(encB64, ivB64)
                    if (migrated != null) {
                        prefs.edit()
                            .putString(KEY_ENCRYPTED_DEK, migrated.first)
                            .putString(KEY_DEK_IV, migrated.second)
                            .commit()
                    }
                }

                val kek = keyStoreManager.getOrCreateKEK()
                val dek = loadOrCreateDEK(context, keyStoreManager, kek)
                val passphrase = Base64.encodeToString(dek, Base64.NO_WRAP)
                val factory = SupportFactory(passphrase.toByteArray())

                Room.databaseBuilder(context, MiPassDatabase::class.java, DATABASE_NAME)
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2)
                    .build()
            } catch (e: Exception) {
                android.util.Log.e("MiPassDatabase", "Failed to initialize encrypted database", e)
                throw RuntimeException("数据库初始化失败，请重启应用", e)
            }
        }

        private fun loadOrCreateDEK(
            context: Context,
            keyStoreManager: KeyStoreManager,
            kek: SecretKey
        ): ByteArray {
            val prefs = createSecurePrefs(context)

            // 检查是否有 biometric 解密后的临时 DEK
            val tempDekB64 = prefs.getString("temp_dek", null)
            if (tempDekB64 != null) {
                val tempDek = Base64.decode(tempDekB64, Base64.NO_WRAP)
                prefs.edit().remove("temp_dek").apply() // 用完即清除
                return tempDek
            }

            val encryptedDekB64 = prefs.getString(KEY_ENCRYPTED_DEK, null)
            val ivB64 = prefs.getString(KEY_DEK_IV, null)

            return if (encryptedDekB64 != null && ivB64 != null) {
                // 已有 DEK，解密后返回
                val encryptedDek = Base64.decode(encryptedDekB64, Base64.NO_WRAP)
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                keyStoreManager.decryptDEK(kek, encryptedDek, iv)
            } else {
                // 首次安装，生成新 DEK 并持久化
                val newDek = keyStoreManager.generateDEK()
                val (encryptedDek, iv) = keyStoreManager.encryptDEK(kek, newDek)
                prefs.edit()
                    .putString(KEY_ENCRYPTED_DEK, Base64.encodeToString(encryptedDek, Base64.NO_WRAP))
                    .putString(KEY_DEK_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .commit()
                newDek
            }
        }

        private fun createSecurePrefs(context: Context): SharedPreferences {
            // DEK 已被 TEE 保护的 KEK 加密，存普通 SharedPreferences 即足够安全
            return context.getSharedPreferences(DEK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
}
