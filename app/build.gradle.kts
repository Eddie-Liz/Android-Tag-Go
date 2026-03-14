import com.google.devtools.ksp.gradle.KspExtension
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {

    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.appdistribution)
    id("com.google.devtools.ksp") version "2.3.2"
}

android {
    namespace = "com.rootilabs.wmeCardiac"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rootilabs.wmeCardiac"
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Firebase App Distribution configuration
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotes = """
                    [v1.0.7 UI Refactor & Login Logic]
                    - EN: Refactored Login UI to a modern flat rectangular design with bold text and simplified layout.
                    - CH: 重構登入介面為簡約平面直角設計，並優化字體加粗顯示與佈局。
                    - EN: Optimized login flow: Auto-login if only a single active recording device is detected.
                    - CH: 優化登入流程：偵測到單一裝置錄製時自動選取並登入。
                    - EN: Removed status text prompts during login for a cleaner user experience.
                    - CH: 移除登入過程中的文字提示，提升視覺簡潔度。
                    - EN: Added iOS-style bottom sheets for server and device selection.
                    - CH: 新增 iOS 風格的底部抽屜選單用於選擇伺服器與裝置。
                    - EN: Added vertical scroll support to prevent UI clipping on smaller screens.
                    - CH: 登入頁面新增垂直捲動支援，防止在小螢幕或開啟鍵盤時元件被擠壓。
                    - EN: Restyled warning alerts to match original dark theme.
                    - CH: 修改警告彈窗樣式以符合深色主題風格。
                """.trimIndent()
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
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-analytics")


    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Retrofit + Moshi (reflection, no codegen)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")

    // OkHttp
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.0-rc01")
    implementation("androidx.room:room-ktx:2.7.0-rc01")
    ksp("androidx.room:room-compiler:2.7.0-rc01")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // CameraX
    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // WorkManager (Background processing)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}