# MiPass 全栈优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 4 个高危安全漏洞 + 5 项性能优化 + 7 项代码质量提升 + 主题色重设计，按 Phase 1→5 全量排序实施

**Architecture:** 修改按安全层(KeyStore/Biometric/SelfDestruct)→UI 层(Screen/Component/Theme)→数据层(Database/BackupEngine)顺序，每 Phase 完成后构建验证

**Tech Stack:** Kotlin 2.2.10 + Compose BOM 2026.02.01 + Room + SQLCipher + Hilt + Android Keystore

**Skill Assignments by Phase:**
| Phase | Skill | 用途 |
|-------|-------|------|
| Phase 1 完成后 | `ecc:security-review` | 安全修复审查 |
| 每次 commit 前 | `ecc:code-review` | 代码审查 |
| 构建失败时 | `ecc:kotlin-build` | 构建错误修复 |
| Phase 3.5 | `superpowers:test-driven-development` | TDD 写单元测试 |
| Phase 3.6 | `ecc:code-simplifier` | 代码整洁审查 |
| 每个 Phase 结束 | `superpowers:verification-before-completion` | 验证完成 |
| Phase 5 结束后 | `superpowers:requesting-code-review` | 整体代码审查 |

---

## 预检：编译基线

### Task 0: 构建验证当前状态

- [ ] **Step 1: 运行 debug 编译**

Run: `./gradlew assembleDebug 2>&1`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 若编译失败，使用 `ecc:kotlin-build` 修复**

---

## Phase 1: 安全高危修复

> 完成后使用 `ecc:security-review` 审查

### Task 1.1: BiometricPrompt 绑定 CryptoObject

**Files:**
- Modify: `app/src/main/java/com/hanzg/mipass/utils/BiometricPromptManager.kt`
- Modify: `app/src/main/java/com/hanzg/mipass/utils/KeyStoreManager.kt`
- Modify: `app/src/main/java/com/hanzg/mipass/MainActivity.kt`

- [ ] **Step 1: BiometricPromptManager 新增带 CryptoObject 的重载方法**

```kotlin
// BiometricPromptManager.kt — 新增 import 和方法
import javax.crypto.Cipher

fun showPromptWithCrypto(
    activity: FragmentActivity,
    cipher: Cipher,
    title: String = "生物识别验证",
    subtitle: String = "验证身份以解锁 MiPass",
    negativeButtonText: String = "使用主密码",
    onSuccess: (Cipher) -> Unit,
    onError: (Int, String) -> Unit,
    onFailed: () -> Unit
) {
    val crypto = BiometricPrompt.CryptoObject(cipher)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeButtonText)
        .build()

    BiometricPrompt(activity,
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result.cryptoObject?.cipher ?: cipher)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString.toString())
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }).authenticate(promptInfo, crypto)
}
```

- [ ] **Step 2: KeyStoreManager 新增 getDecryptCipher 方法**

```kotlin
// KeyStoreManager.kt — 新增方法
fun getDecryptCipher(kek: SecretKey, iv: ByteArray): Cipher {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    return cipher
}
```

- [ ] **Step 3: MainActivityEntryPoint 添加 KeyStoreManager**

```kotlin
// MainActivity.kt — EntryPoint 新增
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MainActivityEntryPoint {
    fun appPreferences(): AppPreferences
    fun masterPasswordManager(): MasterPasswordManager
    fun biometricPromptManager(): BiometricPromptManager
    fun selfDestructManager(): SelfDestructManager
    fun localeHelper(): LocaleHelper
    fun keyStoreManager(): KeyStoreManager  // 新增
}
```

- [ ] **Step 4: MainActivity 添加 mainEntryPoint 字段，performBiometricAuth 改用 CryptoObject**

```kotlin
// MainActivity.kt — 新增字段
private val mainEntryPoint by lazy {
    EntryPoints.get(applicationContext, MainActivityEntryPoint::class.java)
}

// performBiometricAuth() 中 Bio Ready 分支修改：
val keyStoreManager = mainEntryPoint.keyStoreManager()
val kek = keyStoreManager.getOrCreateKEK()
// 从 MiPassDatabase 获取 DEK IV
val dekPrefs = getSharedPreferences("mipass_dek_prefs", MODE_PRIVATE)
val ivB64 = dekPrefs.getString("dek_iv", null) ?: return@let
val iv = android.util.Base64.decode(ivB64, android.util.Base64.NO_WRAP)
val decryptCipher = keyStoreManager.getDecryptCipher(kek, iv)

biometricPromptManager.showPromptWithCrypto(
    activity = this,
    cipher = decryptCipher,
    title = "身份验证",
    subtitle = "验证身份以解锁 MiPass",
    onSuccess = { cipher ->
        if (myGen != biometricGeneration) return@showPromptWithCrypto
        // cipher 已经 TEE 解锁，后续可用它解密 DEK
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            selfDestructManager.resetAttempts()
        }
        runBlocking {
            masterPasswordManager.recordBootId()
            masterPasswordManager.recordFingerprintDbHash()
        }
        authState = AuthState.DONE
        hasAuthenticated = true
        removePrivacyOverlay()
        renderContent()
    },
    onError = { code, _ ->
        if (myGen != biometricGeneration) return@showPromptWithCrypto
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            selfDestructManager.recordFailedAttempt()
        }
        authState = AuthState.UNLOCK
        if (code == 10 || code == 13) {
            removePrivacyOverlay()
            renderSetupScreen(MasterPasswordScreenMode.UNLOCK)
        }
    },
    onFailed = {
        if (myGen != biometricGeneration) return@showPromptWithCrypto
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            selfDestructManager.recordFailedAttempt()
        }
    }
)
```

- [ ] **Step 5: 构建验证**

Run: `./gradlew assembleDebug 2>&1`
Expected: BUILD SUCCESSFUL（若失败用 `ecc:kotlin-build`）

- [ ] **Step 6: 使用 `ecc:code-review` 审查**

- [ ] **Step 7: 提交**

```bash
git add BiometricPromptManager.kt KeyStoreManager.kt MainActivity.kt
git commit -m "security: BiometricPrompt 绑定 CryptoObject，TEE 密文级生物认证"
```

---

### Task 1.2: KEK 绑定用户认证

**Files:**
- Modify: `app/src/main/java/com/hanzg/mipass/utils/KeyStoreManager.kt`
- Modify: `app/src/main/java/com/hanzg/mipass/data/local/MiPassDatabase.kt`

- [ ] **Step 1: KeyGenParameterSpec 添加 setUserAuthenticationRequired**

```kotlin
// KeyStoreManager.kt — getOrCreateKEK() 中
val spec = KeyGenParameterSpec.Builder(
    KEYSTORE_ALIAS,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setKeySize(256)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setUserAuthenticationRequired(true)              // 新增
    .setUserAuthenticationValidityDurationSeconds(-1) // 新增
    .build()
```

- [ ] **Step 2: 新增 migrateKEK 方法**

```kotlin
// KeyStoreManager.kt — 新增方法
fun migrateKEK(encryptedDekB64: String?, ivB64: String?): Pair<ByteArray, ByteArray>? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)
    
    val oldEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) ?: return null
    val oldKek = (oldEntry as KeyStore.SecretKeyEntry).secretKey
    
    // 检查是否已有认证绑定
    try {
        val testCipher = Cipher.getInstance(TRANSFORMATION)
        testCipher.init(Cipher.ENCRYPT_MODE, oldKek)
    } catch (e: javax.crypto.AEADBadTagException) {
        return null // 已绑定
    } catch (e: Exception) {
        // 可能已绑定用户认证
        return null
    }
    
    // 需要迁移：解密 DEK → 删除旧 KEK → 创建新 KEK → 重加密
    if (encryptedDekB64 == null || ivB64 == null) return null
    val encryptedDek = android.util.Base64.decode(encryptedDekB64, android.util.Base64.NO_WRAP)
    val iv = android.util.Base64.decode(ivB64, android.util.Base64.NO_WRAP)
    val dek = decryptDEK(oldKek, encryptedDek, iv)
    
    keyStore.deleteEntry(KEYSTORE_ALIAS)
    val newKek = getOrCreateKEK()
    val (newEncryptedDek, newIv) = encryptDEK(newKek, dek)
    
    return Pair(newEncryptedDek, newIv)
}
```

- [ ] **Step 3: MiPassDatabase.buildDatabase 中调用迁移**

```kotlin
// MiPassDatabase.kt — buildDatabase() 开头新增迁移逻辑
private fun buildDatabase(context: Context, keyStoreManager: KeyStoreManager): MiPassDatabase {
    // KEK 迁移
    val prefs = createSecurePrefs(context)
    val encryptedDekB64 = prefs.getString(KEY_ENCRYPTED_DEK, null)
    val ivB64 = prefs.getString(KEY_DEK_IV, null)
    val migrated = keyStoreManager.migrateKEK(encryptedDekB64, ivB64)
    if (migrated != null) {
        prefs.edit()
            .putString(KEY_ENCRYPTED_DEK, android.util.Base64.encodeToString(migrated.first, android.util.Base64.NO_WRAP))
            .putString(KEY_DEK_IV, android.util.Base64.encodeToString(migrated.second, android.util.Base64.NO_WRAP))
            .commit()
    }
    
    // ... 后续不变
    val kek = keyStoreManager.getOrCreateKEK()
    val dek = loadOrCreateDEK(context, keyStoreManager, kek)
    // ...
}
```

- [ ] **Step 4: 构建验证 + 提交**

```bash
git add KeyStoreManager.kt MiPassDatabase.kt
git commit -m "security: KEK 绑定用户生物认证(setUserAuthenticationRequired)"
```

---

### Task 1.3: 自毁机制修复

**Files:**
- Modify: `app/src/main/java/com/hanzg/mipass/utils/SelfDestructManager.kt`
- Modify: `app/src/main/java/com/hanzg/mipass/data/local/MiPassDatabase.kt`

- [ ] **Step 1: SelfDestructManager 改造 — 去除自建 CoroutineScope，添加日志**

```kotlin
// SelfDestructManager.kt — 完整修改
@Singleton
class SelfDestructManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    // 移除: private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun recordFailedAttempt() {
        val settings = appPreferences.settingsFlow.first()
        if (!settings.selfDestructEnabled) return
        val newCount = settings.selfDestructAttempts + 1
        appPreferences.setSelfDestructAttempts(newCount)
        if (newCount >= settings.selfDestructThreshold) {
            android.util.Log.w("SelfDestruct", "自毁触发：失败${newCount}次，阈值${settings.selfDestructThreshold}")
            wipeAll()
        }
    }

    suspend fun resetAttempts() { appPreferences.setSelfDestructAttempts(0) }

    private suspend fun wipeAll() {
        android.util.Log.w("SelfDestruct", "开始自毁擦除...")
        var hasError = false

        try {
            // 0. 关闭数据库
            try { MiPassDatabase.closeInstance(); android.util.Log.i("SelfDestruct", "DB已关闭") }
            catch (e: Exception) { android.util.Log.e("SelfDestruct", "关闭DB失败", e); hasError = true }

            // 1. 删除数据库文件
            val dbFile = context.getDatabasePath("mipass.db")
            if (dbFile.exists()) {
                dbFile.delete()
                listOf("-journal", "-wal", "-shm").forEach { suffix ->
                    java.io.File(dbFile.absolutePath + suffix).let { if (it.exists()) it.delete() }
                }
                android.util.Log.i("SelfDestruct", "DB文件已删除")
            }
            // 2-6. 删除快照、DEK预置、导出缓存、清除AppPreferences、删除KeyStore KEK
            // (逻辑同原 wipeAll，每步加日志，异常不中断)
        } catch (e: Exception) {
            android.util.Log.e("SelfDestruct", "擦除异常", e); hasError = true
        }
        if (hasError) android.util.Log.e("SelfDestruct", "擦除完成(有错误)")
        else android.util.Log.w("SelfDestruct", "擦除成功")
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
```

- [ ] **Step 2: MiPassDatabase 添加 closeInstance()**

```kotlin
// MiPassDatabase.kt — companion object 新增
fun closeInstance() {
    synchronized(this) { INSTANCE?.close(); INSTANCE = null }
}
```

- [ ] **Step 3: 构建验证 + 提交**

```bash
git add SelfDestructManager.kt MiPassDatabase.kt
git commit -m "security: 自毁修复 — 先关DB再删文件、添加错误日志、移除自建CoroutineScope"
```

---

### Task 1.4: DEK 内存脱敏

**Files:**
- Modify: `app/src/main/java/com/hanzg/mipass/data/local/MiPassDatabase.kt`

- [ ] **Step 1: buildDatabase 中 DEK 使用后覆盖**

```kotlin
// MiPassDatabase.kt — buildDatabase() 结尾修改
val passphraseBytes = passphrase.toByteArray()
val factory = SupportFactory(passphraseBytes)
val db = Room.databaseBuilder(...).openHelperFactory(factory)...build()

// 内存脱敏
java.util.Arrays.fill(dek, 0.toByte())
java.util.Arrays.fill(passphraseBytes, 0.toByte())
return db
```

- [ ] **Step 2: 构建验证 + 提交**

```bash
git add MiPassDatabase.kt
git commit -m "security: DEK byte[] 使用后 Arrays.fill 覆盖清除"
```

---

### Task 1.5: Phase 1 安全审查

- [ ] **Step 1: 使用 `ecc:security-review` 审查 Phase 1 所有修改**

Skill: `ecc:security-review`
审查范围: `BiometricPromptManager.kt, KeyStoreManager.kt, SelfDestructManager.kt, MiPassDatabase.kt, MainActivity.kt`

- [ ] **Step 2: 修复审查发现的问题**

- [ ] **Step 3: 使用 `superpowers:verification-before-completion` 确认 Phase 1 完成**

---

## Phase 2: 性能优化

### Task 2.1: 消除 runBlocking 主线程阻塞

**Files:**
- Modify: `app/src/main/java/com/hanzg/mipass/MainActivity.kt`

- [ ] **Step 1: onCreate 中 screenshotProtection 异步读取**

```kotlin
// MainActivity.kt — onCreate()
kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
    val screenshotProtection = try {
        prefs?.settingsFlow?.first()?.screenshotProtection ?: true
    } catch (_: Exception) { true }
    withContext(kotlinx.coroutines.Dispatchers.Main) {
        if (screenshotProtection) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
```

- [ ] **Step 2: AppLifecycleObserver 扩展 cachedBiometricEnabled**

```kotlin
// AppLifecycleObserver 新增字段
private var cachedBiometricEnabled: Boolean = false

// ON_STOP 中异步更新缓存
kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
    try {
        val s = prefs?.settingsFlow?.first()
        cachedLockTimeout = s?.lockTimeoutSeconds ?: 60
        cachedBiometricEnabled = s?.biometricEnabled ?: false
    } catch (_: Exception) {}
}

// ON_START 中 "unlock" 分支使用 cachedBiometricEnabled 替代 runBlocking
```

- [ ] **Step 3: showPrivacyOverlay 异步获取主题颜色**

```kotlin
// showPrivacyOverlay() — 先显示默认黑色遮罩，异步更新
privacyOverlay = View(this).apply {
    setBackgroundColor(android.graphics.Color.BLACK); alpha = 1f
}
// ... addView ...

kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
    val isDark = /* 读取主题 */ 
    withContext(kotlinx.coroutines.Dispatchers.Main) {
        privacyOverlay?.setBackgroundColor(
            if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        )
    }
}
```

- [ ] **Step 4: 构建验证 + 提交**

```bash
git add MainActivity.kt
git commit -m "perf: 消除 MainActivity 中 5 处 runBlocking 主线程阻塞"
```

---

### Task 2.2: VaultViewModel Flow 策略

**Files:**
- Modify: `app/src/main/java/com/hanzg/mipass/ui/screens/VaultViewModel.kt`

- [ ] **Step 1: SharingStarted.Eagerly → WhileSubscribed(5000)**

```kotlin
// VaultViewModel.kt — stateIn()
.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = VaultUiState(isLoading = true)
)
```

- [ ] **Step 2: 构建验证 + 提交**

```bash
git add VaultViewModel.kt
git commit -m "perf: VaultViewModel Flow 策略改为 WhileSubscribed(5000)"
```

---

### Task 2.3: Color.White 硬编码修复

**Files:**
- Modify: `app/src/main/java/com/hanzg/mipass/ui/screens/VaultScreen.kt`
- Modify: `app/src/main/java/com/hanzg/mipass/ui/screens/AddPasswordScreen.kt`

- [ ] **Step 1: VaultScreen 两处 DropdownMenu containerColor 修改**

```kotlin
// 分类下拉 Surface color: Color(0xFF...) → MaterialTheme.colorScheme.surfaceContainerHigh
// 类型下拉 containerColor: Color.White → MaterialTheme.colorScheme.surfaceContainerHigh
```

- [ ] **Step 2: AddPasswordScreen 两处同改**

```kotlin
// 类型下拉 containerColor + 生成器下拉 containerColor
// Color.White → MaterialTheme.colorScheme.surfaceContainerHigh
```

- [ ] **Step 3: 构建验证 + 提交**

```bash
git add VaultScreen.kt AddPasswordScreen.kt
git commit -m "perf: DropdownMenu 硬编码 Color.White 改为主题 surfaceContainerHigh"
```

---

### Task 2.4: Compose 稳定性注解 + metrics

**Files:**
- Modify: `app/src/main/java/com/hanzg/mipass/data/local/PasswordEntity.kt`
- Modify: `app/src/main/java/com/hanzg/mipass/data/local/AppPreferences.kt`
- Modify: `app/src/main/java/com/hanzg/mipass/ui/screens/VaultViewModel.kt`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: PasswordEntity, AppSettings, VaultUiState 添加 @Immutable**

```kotlin
import androidx.compose.runtime.Immutable

@Immutable @Entity data class PasswordEntity(...)
@Immutable data class AppSettings(...)
@Immutable data class VaultUiState(...)
```

- [ ] **Step 2: build.gradle.kts 启用 Compose 编译器报告**

```kotlin
kotlinOptions {
    freeCompilerArgs += listOf(
        "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.buildDir}/compose-compiler"
    )
}
```

- [ ] **Step 3: 构建验证 + 提交**

```bash
git add PasswordEntity.kt AppPreferences.kt VaultViewModel.kt build.gradle.kts
git commit -m "perf: @Immutable 注解 + Compose 编译器稳定性报告"
```

---

### Task 2.5: R8 Compose 规则补全

**Files:**
- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: 追加 Compose 规则**

```proguard
# === Compose Runtime ===
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**
```

- [ ] **Step 2: 构建验证 + 提交**

```bash
git add app/proguard-rules.pro
git commit -m "perf: 补充 Compose 专用 R8 keep 规则"
```

---

## Phase 3: 代码质量

### Task 3.1: 抽取 PasswordTextField 组件

**Files:**
- Create: `app/src/main/java/com/hanzg/mipass/ui/components/PasswordTextField.kt`
- Modify: `MasterPasswordSetupScreen.kt, SettingsScreen.kt, SettingsDialogs.kt, AddPasswordScreen.kt, EditPasswordBottomSheet.kt`

- [ ] **Step 1: 创建 PasswordTextField.kt**

完整代码见计划附件。封装 OutlinedTextField + PasswordVisualTransformation + Eye/EyeSlash 切换。

参数: `value, onValueChange, label, modifier, placeholder, isError, supportingText, enabled, singleLine, readOnly, additionalTrailingIcons`

- [ ] **Step 2-5: 逐个替换 5 个文件中的 10+ 处重复代码**

每个文件替换后构建验证。

- [ ] **Step 6: 提交**

```bash
git add PasswordTextField.kt MasterPasswordSetupScreen.kt SettingsScreen.kt SettingsDialogs.kt AddPasswordScreen.kt EditPasswordBottomSheet.kt
git commit -m "refactor: 抽取 PasswordTextField 组件，消除 10+ 处重复代码"
```

---

### Task 3.2: 魔数字符串常量化

**Files:**
- Create: `app/src/main/java/com/hanzg/mipass/utils/Constants.kt`
- Modify: `MainActivity.kt`

- [ ] **Step 1: 创建 Constants.kt** (AuthState enum, ThemeMode enum, UiText object)

- [ ] **Step 2: MainActivity 中 authState 改为 AuthState enum**

- [ ] **Step 3: 构建验证 + 提交**

```bash
git add Constants.kt MainActivity.kt
git commit -m "refactor: AuthState 枚举 + UiText 常量集，消除魔数字符串"
```

---

### Task 3.3: 修复设计规范违反

**Files:**
- Modify: `SettingsScreen.kt` (SettingsCard)
- Modify: `Theme.kt` (MiPassShapes.extraLarge)

- [ ] **Step 1: SettingsCard shadowElevation: 2.dp → 0.dp, 添加 BorderStroke**
- [ ] **Step 2: extraLarge: 24.dp → 16.dp**
- [ ] **Step 3: 构建验证 + 提交**

```bash
git add SettingsScreen.kt Theme.kt
git commit -m "fix: SettingsCard shadow→0+border, extraLarge 24dp→16dp 符合设计规范"
```

---

### Task 3.4: 消除强制解包

**Files:**
- Modify: `VaultScreen.kt`

- [ ] **Step 1: entityToEdit!! → entityToEdit?.let { entity -> ... }**
- [ ] **Step 2: 构建验证 + 提交**

```bash
git add VaultScreen.kt
git commit -m "refactor: entityToEdit!! 强制解包改为安全 ?.let"
```

---

### Task 3.5: 添加单元测试

> 使用 `superpowers:test-driven-development` 技能

- [ ] **Step 1: 创建 GeneratePasswordUseCaseTest.kt — 6 个测试用例**
- [ ] **Step 2: 创建 VaultViewModelTest.kt — 2 个测试用例**
- [ ] **Step 3: 运行测试 `./gradlew testDebugUnitTest`**
- [ ] **Step 4: 提交**

```bash
git add app/src/test/
git commit -m "test: 添加 UseCase + ViewModel 单元测试"
```

---

### Task 3.6: 代码整洁审查

- [ ] **使用 `ecc:code-simplifier` 审查所有修改文件**
- [ ] **使用 `superpowers:verification-before-completion` 确认 Phase 3**

---

## Phase 4: 安全中低修复

### Task 4.1: 导入 JSON 校验

**Files:**
- Modify: `BackupEngine.kt`

- [ ] **Step 1: deserializeFromJson 每个条目 try-catch + 必填校验**

```kotlin
// 每个条目
try {
    val name = obj.optString("name", "")
    val password = obj.optString("password", "")
    val account = obj.optString("account", "")
    if (name.isBlank() || password.isBlank() || account.isBlank()) {
        Log.w("BackupEngine", "跳过无效条目$i：缺少必填字段"); continue
    }
    // 正常添加...
} catch (e: Exception) {
    Log.w("BackupEngine", "跳过格式错误条目$i: ${e.message}"); continue
}
```

- [ ] **Step 2: 构建验证 + 提交**

```bash
git add BackupEngine.kt
git commit -m "security: BackupEngine 导入添加条目 try-catch + 必填字段校验"
```

---

### Task 4.2: boot_id 读取改 File API

**Files:**
- Modify: `MasterPasswordManager.kt`

- [ ] **Step 1: Runtime.exec → File.readText**

```kotlin
private fun getBootId(): String {
    return try {
        java.io.File("/proc/sys/kernel/random/boot_id").readText().trim()
    } catch (_: Exception) { "unknown" }
}
```

- [ ] **Step 2: 构建验证 + 提交**

```bash
git add MasterPasswordManager.kt
git commit -m "security: boot_id 读取从 Runtime.exec 改为 File.readText"
```

---

### Task 4.3: SecureRandom 单例化

**Files:**
- Modify: `MasterPasswordManager.kt, BackupEngine.kt`

- [ ] **Step 1: 两文件 companion object 中添加 `private val secureRandom = SecureRandom()`**
- [ ] **Step 2: setMasterPassword/exportPasswords 中使用单例**
- [ ] **Step 3: 构建验证 + 提交**

```bash
git add MasterPasswordManager.kt BackupEngine.kt
git commit -m "security: SecureRandom 改为 companion object 单例"
```

---

## Phase 5: 主题色重设计

### Task 5.1: 更新 Color.kt + Theme.kt

**Files:**
- Modify: `app/src/main/java/com/hanzg/mipass/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/hanzg/mipass/ui/theme/Theme.kt`

- [ ] **Step 1: Color.kt — 全部色值替换为靛蓝+白灰方案**

Light: Primary `#4F46E5`, Background `#FAFAFA`, Surface `#FFFFFF`, SurfaceVariant `#F3F4F6`
Dark: Primary `#818CF8`, Background `#0F0F11`, Surface `#1A1A1E`

完整色值映射见设计文档 Phase 5。

- [ ] **Step 2: Theme.kt — lightColorScheme/darkColorScheme 参数更新**

保持与 Color.kt 中新 token 名一致即可，结构不变。

- [ ] **Step 3: 构建验证（浅色+深色模式都编译通过）**

- [ ] **Step 4: 使用 `superpowers:requesting-code-review` 进行最终代码审查**

- [ ] **Step 5: 提交**

```bash
git add Color.kt Theme.kt
git commit -m "style: 主题色重设计 — 靛蓝+白灰高对比度简约风"
```

---

### Task 5.2: 最终验证
> 使用 `ecc:kotlin-build` + `superpowers:verification-before-completion`

- [ ] **Step 1: Clean build `./gradlew clean assembleDebug`**
- [ ] **Step 2: 检查 Compose 编译器报告 `app/build/compose-compiler/`**
- [ ] **Step 3: 运行全部测试 `./gradlew test`**
- [ ] **Step 4: 最终提交**

```bash
git log --oneline -20
```

---

## 总结

| Phase | 任务 | 技能 |
|-------|------|------|
| 1: 安全高危 | 5 tasks | `ecc:security-review` |
| 2: 性能优化 | 5 tasks | `ecc:kotlin-build` |
| 3: 代码质量 | 6 tasks | `superpowers:tdd`, `ecc:code-simplifier` |
| 4: 安全中低 | 3 tasks | — |
| 5: 主题色 | 2 tasks | `superpowers:requesting-code-review` |
| **总计** | **21 tasks** | |
