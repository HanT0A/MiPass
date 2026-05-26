# MiPass 综合重构计划

**日期**: 2026-05-25
**项目**: MiPass Android 密码管理器
**基准**: 已完成 12 项关键/高危修复后的代码库

---

## 1. 优先级总览与依赖关系

| 级别 | 含义 | 建议时间线 |
|------|------|-----------|
| P0 | 安全关键，可能导致用户数据泄露或认证绕过 | 第 1 周 |
| P1 | 架构缺陷，影响可维护性和安全一致性 | 第 2-3 周 |
| P2 | 中等风险，功能缺失或代码质量 | 第 4 周 |
| P3 | 性能优化，当前可接受但需改进 | 第 5 周 |

### 依赖图

```
P0-生物识别绑定 ──────────────────────────────┐
P0-密钥存储加固 ──── 影响 P0-生物识别绑定 ──────┤
P0-导出提取码强化 (独立，无依赖)                 │
                                               ├── 完成后可进行 P1
P1-领域层解耦 ──── 影响 P1-SettingsViewModel ──┤
P1-SettingsViewModel ── 影响 P1-移除静态标志 ───┤
P1-移除静态标志 (依赖 SettingsViewModel)         │
                                               ├── 完成后可进行 P2
P2-死代码清理 (独立)                             │
P2-导出清理 (独立)                               │
P2-自动快照修复 ───────────────────────────────┤
                                               │
P3-批量导入 (独立)                               │
P3-FTS搜索 (独立)                                │
```

---

## 2. P0: 安全关键 — 生物识别绑定与 KEK 加固

### 问题

**A: 指纹库变更检测无效** (`MasterPasswordManager.kt:167-175`)
- 尝试读取 `/data/system/users/0/settings_fingerprint.xml`，非 root 设备永远不可访问
- 添加/删除指纹后不触发恢复密钥验证

**B: KEK 未绑定用户认证** (`KeyStoreManager.kt:40-48`)
- 缺少 `setUserAuthenticationRequired(true)` 和 `setInvalidatedByBiometricEnrollment(true)`
- Cipher 无需生物识别成功即可执行

**C: Cipher 无时效限制** (`KeyStoreManager.kt:90-94`)

### 实施

1. 修改 `KeyStoreManager.getOrCreateKEK()` 的 KeyGenParameterSpec：
   - `setUserAuthenticationRequired(true)`
   - `setInvalidatedByBiometricEnrollment(true)` (API 24+)
   - `setUserAuthenticationValidityDurationSeconds(5)`

2. 新增 `KeyStoreManager.isKEKAvailable()` 检测 KEK 存在性

3. 重写 `MasterPasswordManager.shouldRequireMasterPassword()`：
   - 保留 boot_id 检测
   - 移除 `getFingerprintDbHash()` / `recordFingerprintDbHash()`
   - 改为检测 `isKEKAvailable()`（指纹变更后 KEK 被 Keystore 自动销毁）

4. 注入 `KeyStoreManager` 到 `MasterPasswordManager`，更新 `AppModule`

5. 处理 KEK 重建：用户输入恢复密钥 → 重建 KEK → 重新加密 DEK

6. 移除全站 `recordFingerprintDbHash()` 调用

### 影响文件

| 文件 | 改动 |
|------|------|
| `utils/KeyStoreManager.kt` | 修改 — 添加认证绑定参数，新增 `isKEKAvailable()` |
| `utils/MasterPasswordManager.kt` | 修改 — 移除文件读取 hack，新增 keyStoreManager 依赖 |
| `di/AppModule.kt` | 修改 — 更新构造函数参数 |
| `MainActivity.kt` | 修改 — 更新调用点，处理 KEK 失效 |
| `data/local/MiPassDatabase.kt` | 审查 — 处理 `KeyPermanentlyInvalidatedException` |

### 风险

| 风险 | 缓解 |
|------|------|
| `setInvalidatedByBiometricEnrollment(true)` 后指纹变更需重设恢复密钥 | OOBE 已强制用户确认风险 |
| `setUserAuthenticationRequired(true)` 与 Cipher 使用模式冲突 | `BiometricPrompt.authenticate(info, crypto)` 自动授权 |
| KEK invalidated 后数据库崩溃 | `buildDatabase()` 中捕获 `KeyPermanentlyInvalidatedException` |

**工作量: L (3-4 天)**

---

## 3. P0: 安全关键 — 恢复密钥存储加固

### 问题

`MasterPasswordManager.kt:20-21` — PBKDF2 哈希存储在普通 `MODE_PRIVATE` SharedPreferences，ADB backup 或 root 可提取哈希用于离线暴力破解。

### 实施

1. 使用 `EncryptedSharedPreferences.create()` 替换 `context.getSharedPreferences()`：
   - MasterKey: `AES256_GCM`
   - PrefKey: `AES256_SIV`, PrefValue: `AES256_GCM`

2. 添加升级迁移逻辑：
   - 检测新存储是否为空
   - 若为空，从旧 `mipass_master_pwd` 读取盐+哈希
   - 迁移至新存储，验证成功后清除旧数据

3. 锁策略 SharedPreferences 保持普通模式（无密钥材料，需离线可用）

### 风险

| 风险 | 缓解 |
|------|------|
| EncryptedSharedPreferences 初始化失败 | try-catch + 降级策略 |
| 升级迁移失败 | 保留旧数据备份，验证成功后才清除 |

**工作量: M (1.5-2 天)**

---

## 4. P0: 安全关键 — 导出提取码强化

### 问题

`BackupEngine.kt:42, 222-224` — PBKDF2 仅 100K 迭代，提取码仅 6 位纯数字（1M 组合），离线暴力破解可在数分钟内完成。

### 实施

1. `PBKDF2_ITERATIONS = 100_000` → `600_000`
2. 提取码验证改为：长度 ≥ 8，至少含 1 个字母 + 1 个数字
3. 更新 UI 文案（`SettingsScreen.kt`, `BackupViewModel.kt`）
4. 更新 `PasscodeDialog` 键盘类型和占位符
5. 向后兼容：导入时优先用新参数解密，失败回退旧参数（6 位数字 + 100K iterations）

### 风险

| 风险 | 缓解 |
|------|------|
| 旧 .mipass 文件无法导入 | 双路径解密 fallback |
| 600K iterations 导出卡顿 | 已在 `Dispatchers.IO` 执行 |

**工作量: M (1.5 天)**

---

## 5. P1: 架构关键 — 领域层解耦 Room 实体

### 问题

`PasswordRepository` 接口返回 `PasswordEntity`（带 `@Entity` 注解的 Room 类），导致 ViewModel、UseCase、UI 层全部间接依赖 Room 框架。违反 Clean Architecture。

### 实施

1. 新建 `domain/model/Password.kt` — 纯 Kotlin data class，无任何 Room 注解

2. `PasswordRepository` 接口：`PasswordEntity` → `Password`

3. `PasswordRepositoryImpl`：添加 `PasswordEntity.toDomain()` / `Password.toEntity()` 映射

4. 更新全站引用（约 15 个文件）：`GetPasswordTreeUseCase`、`VaultViewModel`、`VaultScreen`、`PasswordDetailScreen`、`PasswordDetailViewModel`、`AddPasswordScreen`、`EditPasswordBottomSheet`、`PasswordFormViewModel`、`BackupViewModel`、`SnapshotManager`、`BackupEngine`

### 风险

| 风险 | 缓解 |
|------|------|
| 约 15 文件需同步修改 | 分批次提交，每批编译验证 |
| Flow map 增加对象创建 | 千条数据量级可接受 |

**工作量: L (3-4 天)**

---

## 6. P1: 架构关键 — SettingsViewModel 提取

### 问题

`SettingsScreen.kt` (940 行) — 24+ 个 `mutableStateOf` 标志、7 个 EntryPoint 依赖、所有商业逻辑内联在 Composable 中。严重违反 MVVM + UDF。

### 实施

1. 创建 `SettingsViewModel`，注入 7 个依赖（`AppPreferences`, `BiometricPromptManager`, `SnapshotManager`, `PasswordRepository`, `PasswordDao`, `MasterPasswordManager`, `BackupViewModel`）

2. 定义 `SettingsUiState` data class（所有对话框可见性 + 输入字段 + 计算属性）

3. 定义 `SettingsEvent` sealed interface（主题/语言/锁定/生成器/生物识别/密码修改/导出/导入/清除）

4. 逐块迁移商业逻辑（安全设置 → 数据备份 → 通用设置 → 危险操作）

5. 重构 SettingsScreen Composable：`hiltViewModel()` + `collectAsState()` + 事件驱动

### 风险

| 风险 | 缓解 |
|------|------|
| 940 行逻辑迁移遗漏边界情况 | 逐块迁移，每块手工验证 |
| BackupViewModel 协调 | 持有引用并代理调用 |

**工作量: L (3-4 天)**

---

## 7. P1: 架构关键 — 移除静态认证绕过标志

### 问题

`MainActivity.kt:56-61` — `companion object { @Volatile var skipAuthOnce; var skipNextLockCheck }` 被 SettingsScreen Composable 直接设置。任何代码可通过反射绕过认证。

### 实施

1. 定义 `AuthBypassToken`（reason + TTL 5 秒）

2. MainActivity 内部 `requestAuthBypass()` 方法

3. `onCreate` / `ON_START` 中使用 Token 替换静态字段

4. 通过 SettingsViewModel 的 `SharedFlow<SettingsEffect>` 传递 bypass 请求

5. 移除 companion object 公开可变字段

6. 清理 SettingsScreen 中 `MainActivity.skipAuthOnce = true` 直接引用

**工作量: M (1.5-2 天)** — 依赖问题 6

---

## 8. P2: 中等优先级

### 8.1 死 UseCase 清理

`GetPasswordTreeUseCase.kt` — 完整 53 行零引用。在 VaultViewModel 中注入并替换内联的 combine 块。
**工作量: S (0.5 天)**

### 8.2 导出目录清理

`BackupEngine.kt:150-155` — `cleanupExportDir()` 从未调用。在分享/保存成功后 + 应用启动时调用。
**工作量: S (0.5 天)**

---

## 9. P2: 自动快照机制修复

### 问题

CLAUDE.md 声明"写操作自动触发快照"，但 `PasswordRepositoryImpl` 中无任何快照逻辑。

### 实施

1. `PasswordRepositoryImpl` 注入 `SnapshotManager`，每个写方法末尾调用 `triggerAutoSnapshot()`
2. 去抖机制：10 秒内不重复快照
3. 更新 `AppModule.providePasswordRepository()` 传递 SnapshotManager

### 风险

| 风险 | 缓解 |
|------|------|
| 快照失败阻塞主写操作 | try-catch 包裹，失败仅记日志 |
| 批量导入触发 N 次快照 | 去抖 + 完成后统一触发 |

**工作量: S (0.5-1 天)**

---

## 10. P3: 性能优化

### 10.1 批量插入

`PasswordDao.kt:30-31` — 逐条插入导致 N 次事务。添加 `insertPasswords(List<PasswordEntity>)` 批量方法。
**工作量: S (0.5 天)**

### 10.2 搜索优化（方案 A）

ViewModel 使用内存遍历 `lowercase().contains()`。改用 `PasswordDao.searchPasswords()` (SQL LIKE)，利用 SQLite 查询。
**工作量: S (0.5 天)**

（方案 B: Room FTS 虚拟表 — 仅在数据量增长到万级时考虑）

---

## 11. 实施路线图

### 第 1 阶段: P0 安全修复 (第 1 周)

```
Day 1-2:  生物识别绑定与 KEK 加固 (问题 2)
Day 3-4:  恢复密钥 EncryptedSharedPreferences 存储 (问题 3)
Day 4-5:  导出提取码 PBKDF2 强化 + 兼容 (问题 4)
Day 5-7:  集成测试 + 回归测试
```

### 第 2 阶段: P1 架构重构 (第 2-3 周)

```
Day 1-4:  领域层解耦 — Password 模型 + 全部引用迁移 (问题 5)
Day 5-8:  SettingsViewModel 提取 (问题 6)
Day 8-10: 移除静态认证绕过标志 (问题 7)
```

### 第 3 阶段: P2 中等修复 (第 4 周)

```
Day 1: 死代码清理 + 导出文件清理 (问题 8)
Day 2: 自动快照机制修复 (问题 9)
```

### 第 4 阶段: P3 性能优化 (第 5 周)

```
Day 1: 批量插入 + 搜索优化 (问题 10)
```

---

## 12. 风险与回滚策略

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| KEK 加固导致生物识别失效 | 中 | 高 | 多款真机验证 |
| EncryptedSharedPreferences 创建失败 | 低 | 高 | try-catch + 降级 |
| 旧 .mipass 文件无法导入 | 中 | 中 | 双路径解密 fallback |
| 领域层解耦编译错误遗漏 | 高 | 中 | 分批次提交编译验证 |
| SettingsViewModel 迁移回归 | 高 | 中 | 逐块迁移手工验证 |

- 每项 P0 独立提交，可独立回滚
- 全部工作在 feature branch `refactor/2026-05-25-arch-improvements` 进行
- 每完成一个阶段创建 tag checkpoint

### 验证清单

- [ ] 冷启动 → 恢复密钥设置/验证 → 进入应用
- [ ] 重启设备 → 强制恢复密钥验证
- [ ] 添加/删除系统指纹 → 强制恢复密钥验证
- [ ] 系统验证开启/关闭
- [ ] 后台锁定超时重新认证
- [ ] 导出 .mipass → 新提取码格式 → 导入
- [ ] 旧 .mipass 文件 → 导入兼容
- [ ] 自动快照创建和恢复
- [ ] 设置页所有功能
- [ ] 密码 CRUD 操作
- [ ] 搜索和筛选
- [ ] 清除所有数据
- [ ] 配置变更（旋转屏幕）
