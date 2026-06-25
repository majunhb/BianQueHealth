plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.bianque.health.content"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":base"))
    // 依赖 health_engine 获取 LlmClient + ApiKeyProvider（统一通义千问入口）
    implementation(project(":module_health_engine"))

    // 本地 JSON 解析（冷启动阶段加载种子数据）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // 协程
    implementation(libs.coroutines.android)
    // Timber
    implementation(libs.timber)
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}