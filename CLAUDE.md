# MiPass - 纯本地移动端密码存储软件

## 项目概览

MiPass 是一款主打"绝对隐私、纯净无干扰、极致安全"的 Android 原生密码管理工具。采用纯本地离线存储架构，物理和代码层面彻底剥离网络权限，提供军工级本地加密、系统底层防御及符合现代直觉的交互体验。

- **平台**: Android 8.0+ (API Level 26+)，原生 Kotlin + Jetpack Compose
- **设计语言**: Material Design 3 (MD3)，极简几何风格，无阴影毛边线边框，单 accent 色系
- **图标系统**: Phosphor Icons (Linear/Regular)，全站无 emoji
- **包名**: `com.hanzg.mipass`
- **技术栈**: Kotlin 2.2.10 + Compose BOM 2026.02.01 + Room + SQLCipher + Hilt + MVVM-Clean Architecture

## 架构原则

1. **极度安全优先 (Security-First)**: 所有架构设计以"防泄露、防破解、防逆向"为最高准则，物理断网，零网络层组件
2. **清晰的职责分离**: UI 渲染 → 业务逻辑 → 底层加密数据操作，三层严格解耦
3. **单向数据流 (UDF)**: 数据修改只能通过预定义事件触发，界面状态通过 StateFlow 驱动
4. **性能与轻量化**: 无任何网络/统计 SDK，冷启动 ≤ 500ms，APK < 10MB

## 系统架构

```
表现层 (UI Layer)
  ├── Jetpack Compose UI (Screen Composables + Components)
  └── ViewModels (StateFlow 状态管理)
        ↓ Intent        ↑ State
领域层 (Domain Layer)
  ├── UseCases (GetPasswordTreeUseCase, GeneratePasswordUseCase 等)
  └── Repository 接口 (PasswordRepository)
        ↓ 数据请求       ↑ Flow
数据层 (Data Layer)
  ├── Repository 实现 (PasswordRepositoryImpl, 写操作自动触发快照)
  └── 本地数据源
      ├── Room DAO + SQLCipher (加密数据库)
      ├── EncryptedSharedPreferences (加密键值对)
      └── File System API (.mipass 导出文件、.snapshot 快照文件)
```

## 核心安全架构

### 多层认证体系

```
启动流程：
  无主密码 → OOBE 强制设置
  有主密码 + 重启/指纹库变更 → 强制主密码解锁
  有主密码 + 正常 → 生物识别（回退主密码）

主密码规则：
  - PBKDF2WithHmacSHA256，150,000 次迭代
  - ≥8 位，必须含字母+数字
  - 零明文存储（仅存盐+哈希）
  - 防爆破：梯次锁定（3次→30秒, 6次→2分钟, 10次→10分钟, 20次→1小时）
  - 重启/指纹库变更检测：/proc/sys/kernel/random/boot_id + settings_fingerprint.xml SHA-256
```

### 数据库加密 (信封加密 Envelope Encryption)

```
用户生物认证/主密码 → Keystore(TEE) 释放 KEK → KEK 解密 DEK → DEK 注入 SQLCipher 开启数据库
```

- **DEK (Data Encryption Key)**: 首次安装随机生成 256 位高熵密钥，SQLCipher 用于全盘透明加密 SQLite
- **KEK (Key Encryption Key)**: 基于 TEE 硬件级可信执行环境生成，需生物识别或主密码授权使用，存储于 Android Keystore
- DEK 经 KEK 加密后存储于 EncryptedSharedPreferences

### 运行时防护

- **防截屏/防多任务偷窥**: `WindowManager.LayoutParams.FLAG_SECURE` 全局启用
- **防窥模糊遮罩**: ON_STOP 时顶层覆盖黑色遮罩，ON_START 验证后撤销
- **剪贴板保护**: 复制时通过 `ClipManager.setPrimaryClip` 写入并标记 `IS_SENSITIVE` (API 33+)，防止被其他应用读取（Vivo OEM 剪贴板历史无法程序化清除）
- **内存脱敏**: ViewModel 持有的密码字段在非必要情况下保持脱敏状态
- **密码输入掩码**: 所有密码输入框默认显示 ······，眼睛图标切换明文
- **自毁机制**: 连续 10 次验证失败 → 擦除数据库/快照/DEK/KeyStore/偏好设置 → killProcess

### 新增安全组件

| 文件 | 功能 |
|------|------|
| `utils/MasterPasswordManager.kt` | 主密码 PBKDF2 哈希、验证、梯次锁定、重启/指纹检测 |
| `utils/SelfDestructManager.kt` | 自毁失败计数、触发擦除 |
| `utils/LocaleHelper.kt` | 运行时语言切换 |
| `data/local/AppPreferences.kt` | SharedPreferences 封装 + StateFlow 响应式读取 |
| `ui/screens/MasterPasswordSetupScreen.kt` | OOBE 设置/解锁界面 |

## 数据结构

### PasswordEntity (password_entries 表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String (UUID) | 主键 |
| entry_type | EntryType (APP/WEB) | 条目类型枚举 |
| name | String | 名称 |
| url | String? | 网址 (仅 WEB 类型有效) |
| account | String | 账号/手机/邮箱 |
| password | String | 密码 (依赖 SQLCipher 全局加密) |
| category | String | 分类 (工作/社交/金融等) |
| notes | String | 多行备注 |
| created_at | Long | 创建时间戳 |
| updated_at | Long | 更新时间戳 |

### 文件系统目录 (全部在私有沙盒内)

- `/data/data/.../databases/` → SQLCipher 加密的 mipass.db
- `/data/data/.../files/snapshots/` → FIFO 保留最近 5 份 .snapshot 加密快照
- `/data/data/.../cache/exports/` → 临时导出 .mipass 文件，分享后即刻销毁

## 功能模块

### 底部导航三分流架构

| Tab | 功能 | 路由 |
|-----|------|------|
| 密码库 | APP/WEB 双类密码统一管理，顶部类型下拉切换 | Vault |
| 生成器 | 随机密码生成工具 | Generator |
| 设置 | 安全、备份、个性化 | Settings |

### 密码库核心交互

- **平铺列表**: 搜索框 + 分类下拉联动，名称/账号模糊匹配，实时过滤，LazyColumn 交错入场动效（50ms 级联，MiPassEaseOut 400ms）
- **Card UI**: 1px 毛边线边框（outline），8dp 圆角，无阴影。左侧品牌/自定义图标 + 右侧名称/账号/密码/备注。密码默认掩码显示，眼睛切换
- **详情页**: 品牌图标（56dp，品牌色 15% 背景），Crossfade 200ms 密码显隐，全量展示备注，复制/编辑/删除操作
- **新增**: 全屏沉浸，App/Web 类型 Chip 切换，图标置于名称之上可点击自定义
- **编辑**: ModalBottomSheet 半屏抽屉，12dp 顶部圆角
- **删除**: 独立红色删除按钮 + AlertDialog 二次确认

### 密码生成器

- 4-64 位长度滑块，大小写/数字/符号复选框，任何调节秒级实时重算
- 微型生成器 [Shuffle 图标]: 常驻新增/编辑页密码输入框右侧，短按极速填充，长按呼出配置面板
- Fisher-Yates 洗牌算法，密码强度评分 (0-100)

### 设置控制中心

- **安全设置**: 修改主密码、生物识别验证、自动锁定延时、剪贴板清除延时、防截屏保护、安全自毁机制
- **数据与备份**: 数据导出、数据导入与恢复、数据快照管理、安全擦除所有数据
- **通用设置**: 主题风格、显示语言、密码生成偏好
- **关于**: 版本信息、隐私政策、应用权限说明
- **交互**: 简单选择（主题/语言/剪贴板延时/锁定延时）使用 ModalBottomSheet（12dp 顶部圆角），高风险操作保留 AlertDialog
- **代码组织**: 拆分为 `SettingsScreen.kt` + `SettingsDialogs.kt` + `SettingsBottomSheets.kt`

## 导入导出协议 (.mipass)

### 导出管线
UI 勾选数据 + 6 位提取码 → 序列化 JSON → PBKDF2 派生密钥 → AES-GCM 加密 → 写入 .mipass 文件 → FileProvider 唤起分享面板

### 导入管线
拦截 .mipass MIME type → 提取文件流 → 输入提取码解密 JSON → 反序列化 → 去重 (name+account / url+account) → insert

## 自动快照机制

- 观察者模式：PasswordRepository 写操作成功后触发 BackupEngine.createSilentSnapshot()
- 读取完整数据库，使用随机静默密钥加密打包
- FIFO 队列保留最近 5 份，支持一键回滚

## 非功能性需求

- 冷启动 ≤ 500ms（含生物识别拉起）
- 千条数据量级下树状模糊搜索响应 ≤ 100ms
- Release APK ≤ 10MB
- 适配刘海屏/打孔屏，主流生物识别硬件方案
- 支持 TalkBack 无障碍（所有操作图标设 contentDescription，脱敏密码朗读为"密码已隐藏"）

## 设计规范

### 色彩系统 — 石板海军蓝 (Slate Navy)

匹配应用图标色系（图标蓝 `#1565C0` / `#0D47A1`），降低饱和度避免 AI 蓝紫审美。

| Token | Light | Dark | 用途 |
|-------|-------|------|------|
| Primary | `#3D5A78` | `#7A9BB5` | 主强调色 |
| PrimaryContainer | `#D9E5F0` | `#1F3343` | 强调色容器 |
| Background | `#F6F7FA` | `#0C0E12` | 窗口/页面背景 |
| Surface | `#EEF0F5` | `#14171D` | 卡片表面 |
| SurfaceVariant | `#E2E5ED` | `#1C1F27` | 备选表面 |
| SurfaceContainerLow | `#EEF0F5` | `#161820` | NavigationBar 背景 |
| SurfaceContainer | `#E8EAF2` | `#1B1E26` | 标准容器 |
| SurfaceContainerHigh | `#E2E5ED` | `#1F222B` | 抬高容器 |
| SurfaceContainerHighest | `#DCE0E8` | `#232730` | 最高容器（对话框） |
| TextPrimary | `#1A1C20` | `#E5E7EF` | 正文 |
| TextSecondary | `#5A5D66` | `#9A9DA8` | 辅助文字 (onSurfaceVariant) |
| Outline | `#C9CCD6` | `#2F323A` | 边框 |
| OutlineVariant | `#DFE2EB` | `#22252D` | 弱边框（卡片） |
| Error (Coral Red) | `#D94A3A` | `#D94A3A` | 错误/危险操作 |
| ErrorContainer | `#FCE4E1` | `#4A1A15` | 错误背景 |
| Warning Amber | `#C8910A` | `#C8910A` | 警告 |
| Warning Orange | `#C8700A` | `#C8700A` | 严重警告 |
| Scrim | `#99000000` | `#CC000000` | 模态遮罩 |

- 全量 M3 语义 token 映射（`surfaceDim`/`surfaceBright`/`outlineVariant`/`scrim` + error/warning containers）
- 所有 surface 色蓝通道 > 红通道 ≥ 9，确保冷灰无粉底
- Primary、Secondary 映射同色（单一 accent）
- NavigationBar 使用 `surfaceContainerLow`

### 字体

| 用途 | 字体 | 权重 |
|------|------|------|
| Display / Headline | Inter | Regular/Medium/SemiBold/Bold |
| Body / Label / Title | System Default (Roboto + Noto Sans CJK) | 系统原生中英混排 |
| 密码明文 | JetBrains Mono | Regular/Medium |

- 字体文件存放 `res/font/`：inter_regular/medium/semibold/bold.ttf，jetbrains_mono_regular/medium.ttf
- Body/Label 使用系统默认字体的原因：Inter 无 CJK 字形，回退字体度量不匹配会导致中文重叠

### 形状与边框

- **圆角**: extraSmall=4dp, small=6dp, medium=8dp, large=12dp, extraLarge=16dp
- **卡片**: 无阴影（elevation=0），改用 `Surface` + `BorderStroke(1.dp, outlineVariant)` — 弱边框保持视觉层次但不喧宾夺主
- **BottomSheet**: 顶部圆角 12dp（`MaterialTheme.shapes.large`）
- **触控目标**: 全站最小 44dp（IconButton / 可点击元素），图标本体 18-24dp

### 动效

- **Duration Token**: `DurationMicro=150ms`（图标切换）、`DurationShort=200ms`（Crossfade）、`DurationMedium=300ms`（页面转场）、`DurationLong=400ms`（列表入场）
- **Stagger**: `StaggerDelay=50ms` 级联间隔
- **自定义缓动**:
  - `MiPassEaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)` — 进入曲线（Decelerate）
  - `MiPassEaseIn = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)` — 退出曲线（Accelerate，时长为进入的 60-70%）
  - `MiPassEaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)` — 对称曲线
- **Spring 预设**: `MiPassSpringGentle`（按钮回弹）、`MiPassSpringSnappy`（模态进出）、`MiPassSpringStiff`（进度条/滑块）
- **列表入场**: LazyColumn 交错入场（fadeIn + slideInVertically，StaggerDelay 级联，DurationLong + MiPassEaseOut），key 去重
- **密码显隐**: Crossfade(DurationShort + MiPassEaseInOut)
- **触觉**: Tick（复制/切换）、Heavy Click（长按/删除警告）、Buzz（滑块连续震动）

### 图标

- 全站采用 Phosphor Icons Regular 风格（`com.adamglin:phosphor-icon:1.0.0`）
- 导入模式：`import com.adamglin.PhosphorIcons` + `import com.adamglin.phosphoricons.Regular` + `import com.adamglin.phosphoricons.regular.*`
- 常用映射：LockSimple、Globe、Password、GearSix、Plus、MagnifyingGlass、X、CopySimple、Eye/EyeSlash、ArrowLeft、TrashSimple、Shuffle、PencilSimple、ShareNetwork、FileArrowUp、DeviceMobile、FloppyDisk

## 当前开发状态

### 已完成功能

**UI 重设计 (v3.0 — 基于 ui-ux-pro-max + mobile-adaptation-auditor 审计)**
- [x] 色彩系统完整重设计：新 6 级 Surface 层次、Light/Dark 全量 token 对、语义色容器、scrim、M3 surfaceDim/surfaceBright
- [x] 字体系统优化：bodySmall 地板提升到 13sp，line-height 1.5-1.75，letterSpacing 统一
- [x] 动效系统升级：Duration token 常量体系（4 级）、3 种 Spring 预设、退出曲线 MiPassEaseIn（exit-faster-than-enter）
- [x] Shapes 扩展：extraSmall(4dp) + small(6dp) + extraLarge(16dp)
- [x] 全站触控目标 ≥ 44dp（所有 IconButton / Shuffle 图标 / MiniPasswordGenerator）
- [x] 卡片 border 统一为 outlineVariant（弱边框），视觉层次更清晰
- [x] 导航栏白条排查：themes.xml windowBackground/navigationBarColor 修正 + NavigationBar windowInsets 接入，问题仍在
- [x] 所有 BottomSheet 添加 navigationBarsPadding（安全区适配）
- [x] 滑块范围全站统一为 4-64（AddPassword / EditSheet / SettingsDialogs / GeneratorScreen / MiniGenerator）
- [x] 备注对比度修复：移除 alpha hack，直接使用 onSurfaceVariant
- [x] 搜索栏组件精装：bodyMedium placeholder、44dp 清空按钮、surface 色容器

**UI 重设计 (v2.0)**
- [x] 色彩系统：Sage Green → 石板海军蓝完整迁移（Color.kt + Theme.kt + colors.xml + themes.xml）
- [x] 字体系统：Inter (display/headline) + System Default (body/label) + JetBrains Mono (password)
- [x] 图标系统：Material Icons → Phosphor Icons 全量替换，12 种图标，全站无 emoji
- [x] 组件重设计：PasswordCard/SearchBar/CategoryChipBar/MiniPasswordGenerator（无阴影毛边线边框）
- [x] 设置页：拆分为 3 文件（Screen/Dialogs/BottomSheets），简单选择改用 ModalBottomSheet
- [x] 动效系统：自定义 easing、LazyColumn 交错入场、NavHost SharedAxis 页面切换、Crossfade 统一
- [x] 边缘到边缘：从 enableEdgeToEdge() 迁移为手动 setDecorFitsSystemWindows + Scaffold contentWindowInsets 控制

**认证与安全**
- [x] 主密码系统：PBKDF2+SHA256 哈希，≥8位含字母+数字，OOBE 首次强制设置
- [x] 风险确认：设置主密码前勾选"遗忘将永久丢失数据"
- [x] 防爆破：梯次锁定（3次→30秒, 6次→2分钟, 10次→10分钟, 20次→1小时）
- [x] 重启/指纹库变更 → 强制要求主密码解锁
- [x] 生物识别解锁：冷启动生物认证，检查系统是否录入指纹/面容
- [x] 切后台锁定延时：ON_STOP 记录时间戳，ON_START 检查超时（即时/1分钟/5分钟/15分钟）
- [x] 即时锁定：lockTimeout==0 每次切后台强制重新认证
- [x] 自毁机制：连续输错 10 次擦除整库及快照
- [x] 隐私模糊遮罩：切后台时覆盖黑色遮罩
- [x] FLAG_SECURE 防截屏/录屏
- [x] 清除所有数据：生物验证 + 手打 DELETE 确认
- [x] 剪贴板保护：copyText() 使用 IS_SENSITIVE 标记 (API 33+)，自动清理已移除 (Vivo OEM 限制)
- [x] 启动/锁屏退出重入黑屏修复：hasAuthenticated 条件控制隐私遮罩
- [x] 隐私遮罩颜色跟随主题：浅色模式白色、深色模式黑色
- [x] 生物识别默认关闭 (biometricEnabled=false)，需用户手动开启
- [x] 生物识别竞态防护：generation counter 阻止过期回调污染新认证
- [x] 生物识别 errorCode 处理：5 (取消/后台) 保持遮罩，10/13 (用户返回) 显示解锁页
- [x] ON_START/解锁重试生物识别：biometricEnabled 时自动重试
- [x] Activity 重建保护：onSaveInstanceState 维持认证状态，配置变更无需重新认证
- [x] skipNextLockCheck：导入/导出文件选择器返回时跳过锁超时
- [x] 导出验证流程：生物识别优先，不可用时回退主密码

**数据管理**
- [x] CRUD：新增/编辑（ModalBottomSheet）/查看/删除
- [x] APP/WEB 双库独立展示，类型过滤生效
- [x] 平铺检索：搜索框 + 分类下拉联动，模糊匹配名称+账号，无分类分组头部
- [x] 密码生成器：Fisher-Yates 洗牌，长度 4-64，大小写/数字/符号勾选
- [x] 微型生成器：单击 Shuffle 图标刷新密码，长按打开配置面板
- [x] 导出 .mipass：身份验证 → 风险提示 → 设定提取码 → AES-GCM 加密全量导出
- [x] 导出保存方式：系统分享 + SAF 保存到本地文件夹
- [x] 导入 .mipass：SAF 选择文件 → 提取码 → 合并策略（合并/覆盖）
- [x] 自动快照：写操作后自动生成，FIFO 保留 5 份

**设置与个性化**
- [x] 所有设置持久化到 SharedPreferences（AppPreferences + StateFlow）
- [x] 主题：跟随系统/浅色/深色，即时切换
- [x] 语言：简体中文/English，"跟随系统"已移除，默认 zh，English 暂不可用提示
- [x] 生成器默认规则：长度+字符类型复选框，持久化
- [x] 生物识别解锁（独立开关，biometricEnabled 默认 false）
- [x] 切后台锁定延时（独立设置）
- [x] 修改主密码（验证当前密码 → 设置新密码）
- [x] 密码显隐控制：MasterPasswordSetupScreen 双密码字段、导出对话框、MasterPasswordDialog 三字段全部支持眼睛图标切换
- [x] 设置页剪贴板清理行及 ClipboardBottomSheet 已删除

**密码输入掩码**
- [x] 新增/编辑表单密码字段默认显示 ······，眼睛图标切换明暗文

**架构质量**
- [x] VaultViewModel 通过 GetPasswordTreeUseCase 构建数据，消除重复逻辑

**品牌图标系统**
- [x] 45 个主流软件/网站 Vector Drawable 图标（`res/drawable/ic_brand_*.xml`，24dp, white fillColor + tint）
- [x] `utils/IconMatcher.kt`：品牌名→资源名映射（中英文双索引），`getIconResource()` / `getIconLetter()` / `getIconColor()`
- [x] 未匹配品牌自动回退首字母：中文→拼音首字母 / 英文→首单词大写首字母 / 符号→第一个符号
- [x] 用户可自定义图标（图片选择器），自定义图标优先于品牌图标
- [x] 图标出现在：卡片、详情页、新增/编辑表单

**交互与文案优化**
- [x] 新增/编辑页图标移至名称之上，直接点击图标自定义
- [x] 卡片备注格式："备注：..."
- [x] "网址" 统一改为 "Web"
- [x] 分类为空时自动保存为"其他"
- [x] 全站名词与描述重审优化（直观、简洁、易懂）
- [x] 自毁机制设置行去除"上限n次"描述，仅显示开/关状态
- [x] 密码生成偏好显示格式："长度 / A-Z+a-z+0-9+!@#"
- [x] 密码库去除分类分组，直接平铺显示数据卡片（`buildFlatList()` 替代 `buildTreeData()`）
- [x] 占位符文案优化："搜索..."、"手机号 / 邮箱"、"点击右侧 Shuffle 生成"、"备注信息"

**UI 布局与样式审计 (两轮)**
- [x] VaultScreen 搜索行水平 padding 与卡片列表对齐（16dp → 20dp）
- [x] AddPasswordScreen / EditPasswordBottomSheet 表单字段间距统一为 12dp
- [x] EditPasswordBottomSheet 标题居中：`weight(2f)` → `Box` + `Alignment.Center`
- [x] PasswordCard 备注行上方增加 2dp 间距
- [x] DarkBackground 色值修正：`#131518` → `#15171B`（与 CLAUDE.md 一致）
- [x] SettingsScreen 首节上方 padding 扩大（16dp → 20dp）
- [x] 迷你生成器 DropdownMenu 包裹在 Box 中（正确锚定密码输入框）
- [x] GeneratorScreen 滑块范围 4-32 → 4-64（与 MiniPasswordGenerator 和规格一致）
- [x] MasterPasswordDialog 本地校验：6位 → 8位（与后端 PBKDF2 规则一致）
- [x] AddPasswordScreen 备注 maxLines 6 → 5（与编辑页一致）
- [x] SettingsBottomSheets 形状统一使用 `MaterialTheme.shapes.large`
- [x] GeneratorScreen StrengthBar 硬编码颜色提取为 `WarningAmber` / `WarningOrange`（Color.kt）
- [x] ImportProgressDialog 空 confirmButton 改为 Spacer + dismissButton = null
- [x] MasterPasswordSetupScreen 增加 `windowInsetsPadding(WindowInsets.systemBars)`
- [x] SettingsBottomSheets 移除未使用的 `RoundedCornerShape` import

### 已知问题

- [ ] **底部导航栏上方白条**：NavigationBar 与内容区域之间存在约 16-20dp 宽的白条，底色为白色/浅灰。已尝试：themes.xml windowBackground/navigationBarColor、NavigationBar windowInsets、Scaffold contentWindowInsets。待进一步排查。
- [ ] **English 本地化未完成**：所有 UI 文本为硬编码中文，语言切换仅影响系统组件，应用内仍显示中文
- [ ] **Vivo 剪贴板历史无法清除**：Vivo OEM ROM 限制，ClipManager 的 IS_SENSITIVE 标记仅阻止其他应用读取，无法清空系统剪贴板历史

### 待开发
- [ ] 密码过期提醒
- [ ] 主密码修改历史/密码强度计
- [ ] 暗码/伪密码（duress password）功能
- [ ] UI/集成测试
- [ ] CI/CD 配置
