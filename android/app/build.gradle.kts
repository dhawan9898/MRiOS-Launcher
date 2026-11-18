plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from the environment (CI secrets) so the keystore is never committed.
val releaseKeystore: String? = System.getenv("MRIOS_KEYSTORE_PATH")

android {
    namespace = "com.mrios.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mrios.launcher"
        minSdk = 26          // Android 8.0
        targetSdk = 35
        // CI passes these from the git tag (v3.1 -> "3.1") and the workflow run number.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 3
        versionName = (project.findProperty("appVersionName") as String?) ?: "3.0"
        resourceConfigurations += listOf("en", "hi")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("MRIOS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MRIOS_KEY_ALIAS")
                keyPassword = System.getenv("MRIOS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real key when MRIOS_KEYSTORE_PATH is set (CI); debug key for local test builds.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
