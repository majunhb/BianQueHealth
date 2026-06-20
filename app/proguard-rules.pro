# === BianQueHealth ProGuard Rules ===
# 健康数据应用 — 平衡混淆与功能保留

# --- 保留注解 ---
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# --- 保留项目核心类 ---
-keep class com.bianque.health.** { *; }

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.bianque.health.base.data.local.** { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**

# --- SQLCipher ---
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# --- ML Kit ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- CameraX ---
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- TensorFlow Lite ---
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# --- MediaPipe ---
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# --- Timber ---
-dontwarn timber.log.**

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- DataStore ---
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# --- Security Crypto ---
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# --- Compose ---
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# --- Retrofit / OkHttp ---
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Gson ---
-keep class com.google.gson.** { *; }
-keepattributes *Annotation
-dontwarn com.google.gson.**

# --- 移除日志 (Release) ---
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}