import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

// 从 local.properties 读取签名配置（避免硬编码敏感信息）
val keystoreProperties = Properties()
val keystoreFile = rootProject.file("local.properties")
if (keystoreFile.exists()) {
    keystoreProperties.load(FileInputStream(keystoreFile))
}

android {
    namespace = "com.hanzg.mipass"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hanzg.mipass"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // 签名密钥路径和密码从 local.properties 读取
            val storeFile = keystoreProperties.getProperty("release.keystoreFile")
            val storePassword = keystoreProperties.getProperty("release.keystorePassword")
            val keyAlias = keystoreProperties.getProperty("release.keyAlias")
            val keyPassword = keystoreProperties.getProperty("release.keyPassword")

            if (storeFile != null) {
                this.storeFile = file(storeFile)
            }
            if (storePassword != null) {
                this.storePassword = storePassword
            }
            if (keyAlias != null) {
                this.keyAlias = keyAlias
            }
            if (keyPassword != null) {
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 如果有签名配置则使用
            val signingConfig = signingConfigs.getByName("release")
            if (signingConfig.storeFile?.exists() == true) {
                this.signingConfig = signingConfig
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose UI
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Phosphor Icons
    implementation("com.adamglin:phosphor-icon:1.0.0")

    // Coil (仅用于加载本地自定义图标)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Room + SQLCipher
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.net.zetetic.sqlcipher)
    implementation(libs.androidx.sqlite)

    // Security Crypto
    implementation(libs.androidx.security.crypto)
    implementation("com.google.errorprone:error_prone_annotations:2.36.0")


    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Biometric
    implementation(libs.androidx.biometric)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Instrumented Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
