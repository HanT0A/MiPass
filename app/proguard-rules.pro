# MiPass ProGuard / R8 Rules

# === Hilt / Dagger ===
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# === Room ===
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# === SQLCipher ===
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclassmembers class net.sqlcipher.CursorWindow {
    long nWindow;
}
-dontwarn net.sqlcipher.**

# === Kotlin Coroutines ===
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# === Android Keystore ===
-keep class javax.crypto.** { *; }
-keep class android.security.keystore.** { *; }

# === Compose ===
-dontwarn androidx.compose.**

# === DataStore ===
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# === Biometric ===
-keep class androidx.biometric.** { *; }

# === org.json ===
-keep class org.json.** { *; }

# === MiPass 数据实体 ===
-keep class com.hanzg.mipass.data.local.PasswordEntity { *; }
-keep class com.hanzg.mipass.domain.model.EntryType { *; }
-keep class com.hanzg.mipass.ui.navigation.NavRoutes { *; }

# === MiPass DI / 避免 Hilt 反射问题 ===
-keep,allowobfuscation,allowshrinking class com.hanzg.mipass.di.** { *; }

# === General ===
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# === Tink / Security Crypto ===
-dontwarn com.google.errorprone.annotations.**

# Remove debug logs in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# === Compose Runtime (additional) ===
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**
