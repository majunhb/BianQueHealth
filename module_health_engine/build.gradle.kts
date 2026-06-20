plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bianque.health.engine"
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
    implementation(project(":module_face"))
    implementation(project(":module_tongue"))
    implementation(project(":module_blood_pressure"))
    implementation(project(":module_pulse"))
    implementation(libs.coroutines.android)
    implementation(libs.timber)
}