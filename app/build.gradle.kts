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
        versionCode = 7
        versionName = "1.0.6"
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
                    [v1.0.5 Bug Fix / 修正說明]
                    - EN: Allow upper-case patient/institution IDs by removing automatic lowercase normalization during login and local persistence.
                    - CH: 修正登入時會將機構代碼與病患ID自動強制轉為小寫，導致伺服器查無病患（400 invalid_patient）的問題。
                    - EN: Fixed permanent login lockout (409 Error) after an offline logout by unconditionally revoking old active sessions.
                    - CH: 修正因無網路登出後，伺服器卡在登入狀態導致被永久鎖死（409錯誤）的問題。
                    - EN: Fixed app switching to wrong server after restart by persisting the selected Server URL.
                    - CH: 修正重新開啟 App 後，伺服器位置會跑掉變回預設的問題（新增伺服器 URL 紀錄機制）。
                    - EN: Allowed forced offline logout and thorough local data wipe when the server is unreachable.
                    - CH: 允許在無網路或伺服器異常時「強制登出」，並確實清空所有尚未上傳之標記資料。
                    - EN: Enforced immediate lock of Tag button when session ends; requires manual relogin to start new session to prevent data bleed.
                    - CH: 防止背景直接切換新量測紀錄。當紀錄結束時，Tag 按鈕會直接反灰凍結，使用者必須重新登入才能看到新局資料，確保新舊資料不互相污染。
                    - EN: Sync appVersion text on Profile Screen dynamically with build config.
                    - CH: 個人資料頁面的 App 版本號現在會自動同步最新版號。
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