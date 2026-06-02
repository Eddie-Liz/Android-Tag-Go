import com.google.devtools.ksp.gradle.KspExtension
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties

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

    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) load(f.inputStream())
    }

    signingConfigs {
        create("release") {
            val storeFileProp = keystoreProps["storeFile"] as? String
            if (storeFileProp != null) {
                storeFile = file(storeFileProp)
                storePassword = keystoreProps["storePassword"] as? String ?: ""
                keyAlias = keystoreProps["keyAlias"] as? String ?: ""
                keyPassword = keystoreProps["keyPassword"] as? String ?: ""
            }
        }
    }

    defaultConfig {
        applicationId = "com.rootilabs.wmeCardiac2"
        minSdk = 24
        targetSdk = 36
        versionCode = 23
        versionName = "1.0.14"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
            // Firebase App Distribution configuration
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotes = """
                    [v1.0.9 更新內容]
                    - 調整標記上傳機制：斷網時的標記改為需在歷史紀錄中手動重試上傳。
                    - 修正多國語系：補齊頭痛、噁心、心律不整等多國語言翻譯，確保正確顯示。
                    - 介面與排版優化：修正首頁字體大小與部分畫面的跑版問題。
                    
                    [v1.0.8 更新內容]
                    - 登出流程修正：無網路時將中斷登出，保留使用者的本機紀錄。
                    
                    [v1.0.7 更新內容]
                    - 登入介面大改版：採用全新平面簡約設計，並加入底部抽屜選單。
                    - 登入體驗優化：當只有一台測量裝置時，系統將自動完成選取並登入。
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
    implementation(platform("com.google.firebase:firebase-bom:33.8.0"))
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
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // CameraX
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // WorkManager (Background processing)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}