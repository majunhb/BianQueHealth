# === BianQueHealth ProGuard Rules ===
# 健康数据应用 — 完整混淆规则，覆盖所有模块

# --- 保留注解 ---
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Signature
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# --- 保留项目核心类 ---
-keep class com.bianque.health.** { *; }

# ============ Hilt / Dagger ============
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <init>(...);
}
-dontwarn dagger.hilt.**

# ============ Room ============
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.bianque.health.base.data.local.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.**

# ============ SQLCipher ============
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ============ ML Kit ============
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ============ CameraX ============
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ============ TensorFlow Lite ============
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }
-dontwarn org.tensorflow.lite.**

# ============ MediaPipe ============
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-dontwarn com.google.mediapipe.**

# ============ OkHttp ============
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okhttp3.**
-dontwarn okio.**

# ============ Timber ============
-dontwarn timber.log.**

# ============ Coroutines ============
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============ Compose ============
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ============ Hilt Navigation Compose ============
-keep class androidx.hilt.navigation.compose.** { *; }
-dontwarn androidx.hilt.navigation.**

# ============ DataStore ============
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ============ Security Crypto ============
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ============ Coil ============
-keep class coil.** { *; }
-dontwarn coil.**

# ============ Gson / JSON ============
-keep class com.google.gson.** { *; }
-keep class org.json.** { *; }
-dontwarn com.google.gson.**

# ============ 移除日志 (Release) ============
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