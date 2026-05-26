# MiPass

纯本地离线 Android 密码管理器 — 绝对隐私、纯净无干扰、极致安全。

## 设计理念

MiPass 是一款完全本地化的密码管理工具，物理和代码层面彻底剥离网络权限。不请求联网、不集成第三方 SDK、不上传任何数据。所有数据存储在设备加密沙盒内，你拥有对数据的完全控制权。

## 核心特性

- **纯本地离线** — 零网络权限，零网络组件，物理断网
- **军工级加密** — 信封加密 (Envelope Encryption)：DEK 全盘加密 SQLite + KEK 基于 TEE 硬件保护
- **系统生物识别** — BiometricPrompt (指纹/面容) + 设备凭证 (PIN/图案/密码) 回退
- **恢复密钥** — PBKDF2-SHA256 (150,000 次迭代)，梯次防爆破锁定
- **运行时防护** — 防截屏/录屏 (FLAG_SECURE)、剪贴板敏感标记、切后台自动锁定+隐私遮罩
- **数据可移植** — AES-GCM 加密导出 (.mipass) + JSON/CSV 通用格式导入导出
- **自动快照** — 每次写操作后自动生成加密快照，FIFO 保留最近 5 份，支持一键回滚
- **密码生成器** — SecureRandom 密码学安全随机，4-64 位，Fisher-Yates 洗牌
- **Material Design 3** — 石板海军蓝配色，Phosphor Icons 图标系统，JetBrains Mono 密码字体

## 技术栈

| 层次 | 技术 |
|------|------|
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose + Material Design 3 |
| 架构 | MVVM + Clean Architecture + UDF |
| 数据库 | Room + SQLCipher (全盘加密) |
| DI | Hilt |
| 加密 | Android Keystore (TEE) + AES-256-GCM + PBKDF2-SHA256 |
| 最低 API | Android 8.0 (API 26) |

## 系统架构

```
UI Layer (Compose Screens + ViewModels)
    ↕ Intent / StateFlow
Domain Layer (UseCases + Repository 接口)
    ↕ Flow
Data Layer (Repository 实现 + Room DAO + SQLCipher)
```

## 构建

```bash
# 克隆项目
git clone <repo-url> && cd MiPass

# 配置 local.properties（指向你的 Android SDK）
echo "sdk.dir=/path/to/Android/sdk" >> local.properties

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/`

## 项目结构

```
MiPass/
├── app/
│   └── src/main/java/com/hanzg/mipass/
│       ├── data/          # 数据层 (Repository, DAO, DataSource)
│       ├── domain/        # 领域层 (UseCase, Repository接口, Model)
│       ├── ui/            # 表现层 (Screen, ViewModel, Component, Theme)
│       ├── utils/         # 工具类 (加密, 生物识别, 图标匹配)
│       └── di/            # Hilt 依赖注入模块
├── gradle/
│   └── libs.versions.toml # 版本目录
├── build.gradle.kts       # 根构建脚本
├── settings.gradle.kts    # 项目设置
└── gradle.properties      # Gradle 配置
```

## 安全设计

详见 [CLAUDE.md](./CLAUDE.md) 中的安全架构章节，包括：

- 多层认证体系（系统验证 + 恢复密钥）
- 信封加密流程（KEK → DEK → SQLCipher）
- 运行时防护（防截屏、剪贴板保护、内存脱敏）
- 防爆破梯次锁定

## 许可证

MIT License
