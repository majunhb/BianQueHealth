plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bianque.health.base"
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
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(libs.datastore)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    // Lifecycle
    implementation(libs.lifecycle.runtime)
}