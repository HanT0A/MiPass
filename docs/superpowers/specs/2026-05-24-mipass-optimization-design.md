# MiPass 全栈优化设计文档

**日期**: 2026-05-24
**状态**: 已确认
**范围**: 安全修复、性能优化、代码质量提升、主题色重设计

---

## 背景

MiPass 是一款纯本地离线 Android 密码管理器。经过两轮 UI 重设计后功能完善，但存在以下问题：
- 4 个高危安全漏洞（BiometricPrompt 未绑定 CryptoObject、KEK 未绑定生物认证、自毁机制吞异常、DEK 内存暴露）
- 5 处性能瓶颈（runBlocking 阻塞、Eagerly Flow 泄漏、硬编码 Color.White、缺少稳定性注解、R8 规则不全）
- 7 项代码质量问题（重复代码、魔数字符串、设计规范违反、强制解包、零测试等）
- 主题色偏暗沉，需更简约明亮的白+靛蓝配色

---

## Phase 1: 安全高危修复

### 1.1 BiometricPrompt 绑定 CryptoObject

**文件**: `utils/BiometricPromptManager.kt`, `MainActivity.kt`

- `BiometricPromptManager` 新增 `showPromptWithCrypto(cipher: Cipher, ...)` 方法
- `authenticate(promptInfo, cryptoObject)` 替代 `authenticate(promptInfo)`
- `MainActivity.performBiometricAuth()` 中：先获取 KEK → 初始化解密 Cipher → 传入 prompt
- 认证成功时 TEE 自动释放密钥，生物认证才真正有密码学意义

### 1.2 KEK 绑定用户认证

**文件**: `utils/KeyStoreManager.kt`

- `KeyGenParameterSpec.Builder` 添加：
  - `.setUserAuthenticationRequired(true)` — 每次使用 KEK 都需要生物认证
  - `.setUserAuthenticationValidityDurationSeconds(-1)` — 无时效缓存
- 已存在 KEK 的迁移：旧 KEK 解密 DEK → 删除旧 KEK → 创建新 KEK（带认证要求）→ 重加密 DEK

### 1.3 自毁机制修复

**文件**: `utils/SelfDestructManager.kt`

- `wipeAll()` 执行前先关闭数据库：`MiPassDatabase.destroyInstance()`
- 关键步骤失败记录到 `android.util.Log.e`，不静默吞异常
- 将自建 `CoroutineScope` 改为注入的 `@ApplicationScope` 或改用 suspend 函数模式

### 1.4 DEK 内存脱敏

**文件**: `data/local/MiPassDatabase.kt`

- `buildDatabase()` 中：`dek` 使用后立即 `Arrays.fill(dek, 0)` 覆盖
- Room 初始化完成后清除局部引用

---

## Phase 2: 性能优化

### 2.1 消除 runBlocking 主线程阻塞

**文件**: `MainActivity.kt`

- `onCreate()` 中 screenshotProtection/biometricEnabled 读取改为协程异步
- `AppLifecycleObserver` 扩展 `cachedBiometricEnabled` 缓存
- `showPrivacyOverlay()` 改为异步获取主题模式

### 2.2 VaultViewModel Flow 策略

**文件**: `ui/screens/VaultViewModel.kt`

- `SharingStarted.Eagerly` → `SharingStarted.WhileSubscribed(5000)`

### 2.3 Color.White 硬编码修复

**文件**: `ui/screens/VaultScreen.kt`, `ui/screens/AddPasswordScreen.kt`

- `containerColor = Color.White` → `containerColor = MaterialTheme.colorScheme.surfaceContainerHigh`

### 2.4 Compose 稳定性注解

- 数据类添加 `@Immutable`：`PasswordEntity`、`AppSettings`、`VaultUiState`
- `build.gradle.kts` 启用 Compose 编译器 metrics 报告

### 2.5 R8 + Baseline Profile

**文件**: `app/proguard-rules.pro`, `app/build.gradle.kts`

- 补充 Compose 专用 R8 keep 规则
- 添加 `androidx.profileinstaller` 依赖

---

## Phase 3: 代码质量

### 3.1 抽取 PasswordTextField 组件

**文件**: 新建 `ui/components/PasswordTextField.kt`

- 封装：OutlinedTextField + PasswordVisualTransformation + Eye/EyeSlash 切换
- 替换 10+ 处重复代码

### 3.2 魔数字符串常量化

**文件**: 新建 `utils/Constants.kt`, 修改 `MainActivity.kt`

- `AuthState` enum: OOBE, UNLOCK, BIOMETRIC, DONE
- `ThemeMode` enum: SYSTEM, LIGHT, DARK
- 中文文案集中到 `object UiText`

### 3.3 修复设计规范违反

- `SettingsCard`: `shadowElevation = 2.dp` → `0.dp`
- `MiPassShapes.extraLarge`: `24.dp` → `16.dp`

### 3.4 消除强制解包

**文件**: `ui/screens/VaultScreen.kt`

- `entityToEdit!!` → `entityToEdit?.let { ... }`

### 3.5 添加单元测试

- `VaultViewModelTest`: filter/query/category combine 逻辑
- `GeneratePasswordUseCaseTest`: 密码生成各配置组合
- `PasswordFormViewModelTest`: 表单校验逻辑

### 3.6 Compose 编译器 metrics

**文件**: `app/build.gradle.kts`

- 添加 `freeCompilerArgs` 输出稳定性报告

---

## Phase 4: 安全中低修复

### 4.1 CoroutineScope 托管化

**文件**: `utils/SelfDestructManager.kt`

- 改为 suspend 函数模式，由调用方管理 scope

### 4.2 导入 JSON 校验

**文件**: `data/local/BackupEngine.kt`

- 每个 entry 包裹 try-catch，跳过格式错误条目
- 校验 name/password 非空

### 4.3 boot_id 读取优化

**文件**: `utils/MasterPasswordManager.kt`

- `Runtime.exec()` → `File(...).readText()`

### 4.4 SecureRandom 单例化

**文件**: `utils/MasterPasswordManager.kt`, `data/local/BackupEngine.kt`

- `SecureRandom()` 提取为 `companion object val` 单例

---

## Phase 5: 主题色重设计

### 配色方案

新方案以 **白/白灰色为主导**，**靛蓝** 为高对比度强调色，**淡蓝** 为辅助色：

**Light Scheme**

| Token | 色值 | 说明 |
|-------|------|------|
| Primary | `#4F46E5` | 靛蓝主色 |
| PrimaryContainer | `#E0E7FF` | 极淡靛蓝容器 |
| OnPrimary | `#FFFFFF` | 主色上文字 |
| OnPrimaryContainer | `#1E1B4B` | 容器上文字 |
| Background | `#FAFAFA` | 近白背景 |
| Surface | `#FFFFFF` | 纯白卡片 |
| SurfaceVariant | `#F3F4F6` | 冷灰白表面 |
| SurfaceContainerLow | `#FFFFFF` | 导航栏 |
| SurfaceContainer | `#F9FAFB` | 标准容器 |
| SurfaceContainerHigh | `#F3F4F6` | 抬高容器 |
| SurfaceContainerHighest | `#EEEEF2` | 最高容器(对话框) |
| TextPrimary | `#111827` | 正文 |
| TextSecondary | `#6B7280` | 辅助文字 |
| Outline | `#D1D5DB` | 边框 |
| OutlineVariant | `#E5E7EB` | 弱边框(卡片) |
| Error | `#DC2626` | 错误红 |
| Scrim | `#66000000` | 遮罩 |

**Dark Scheme**

| Token | 色值 | 说明 |
|-------|------|------|
| Primary | `#818CF8` | 淡靛蓝 |
| PrimaryContainer | `#312E81` | 深靛蓝容器 |
| OnPrimary | `#0F0F11` | 深底主色文字 |
| OnPrimaryContainer | `#E0E7FF` | 深容器文字 |
| Background | `#0F0F11` | 近黑背景 |
| Surface | `#1A1A1E` | 暗灰卡片 |
| SurfaceVariant | `#25252B` | 暗灰表面 |
| SurfaceContainerLow | `#151519` | 导航栏 |
| SurfaceContainer | `#1A1A1E` | 标准容器 |
| SurfaceContainerHigh | `#1E1E23` | 抬高容器 |
| SurfaceContainerHighest | `#23232A` | 最高容器 |
| TextPrimary | `#F3F4F6` | 正文 |
| TextSecondary | `#9CA3AF` | 辅助文字 |
| Outline | `#3F3F46` | 边框 |
| OutlineVariant | `#2A2A30` | 弱边框 |
| Error | `#EF4444` | 错误红 |
| Scrim | `#B3000000` | 遮罩 |

### 文件修改

- `ui/theme/Color.kt`: 更新所有色值 token
- `ui/theme/Theme.kt`: lightColorScheme / darkColorScheme 参数映射更新

---

## 实施顺序

```
Phase 1 (安全高危) → Phase 2 (性能) → Phase 3 (代码质量) → Phase 4 (安全中低) → Phase 5 (主题色)
```

每 Phase 完成后构建验证，确保无编译错误。所有修改分批提交。

## 风险

- Phase 1 KEK 迁移：已存在 KEK 需要重新生成，需处理迁移失败场景
- Phase 3 shape extraLarge 从 24dp → 16dp：需全站检查依赖此 shape 的组件
- Phase 5 主题色：需同时在浅色/深色两种模式下验证对比度
