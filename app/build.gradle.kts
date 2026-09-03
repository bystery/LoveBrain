import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 正式签名：仓库根目录的 keystore.properties（已 gitignore，不入库）提供 keystore 路径与密码。
// 贡献者本地没有该文件时，release 构建自动回退 debug 签名（仅供本地测试，不用于发布）。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.lovebrain.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lovebrain.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.1"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有 keystore.properties 时用正式签名发布；没有时回退 debug 签名（仅本地测试）。
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // JVM 单测放行 android.util.Log 等框架调用（返回默认值不抛异常）：
            // L.* 直调 Log，Linux CI 上删除路径触发未 mock 的 Log → RuntimeException（v1.1.0 CI 实测）
            isReturnDefaultValues = true
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 契约测试：把真资产目录挂进 test classpath——资产被改坏时测试必须红灯
    // （禁止副本/内联）
    sourceSets.getByName("test") {
        resources.srcDir("src/main/assets")
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 2026-08-17：icons-extended 已移除（6MB 全家桶），只保留 core；功能卡图标改用自制 drawable
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Lifecycle / ViewModel（2026-08-17：lifecycle-service 死依赖已删，零引用）
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // SavedState (ViewTree extensions for Compose in Service)
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Serialization (replaces Gson)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Networking + SSE
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Dependency Injection
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")

    // Secure storage for API key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // M6 单元测试（纯 JVM，无 Android 依赖）
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.11")
}
