import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 正式签名：仓库根目录的 keystore.properties（已 gitignore，不入库）提供 keystore 路径与密码。
// 贡献者本地没有该文件时，release 构建产出 unsigned APK（仍可验证 R8/资源裁剪/编译），
// 绝不静默回退 debug 签名冒充正式发布。
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
// 2026-11-21：1.5.0 / versionCode 12。提示词六段收敛、进攻/进行中/表达偏好/经验注入全删、
// 契约守卫接管输出解析、A/B 粘贴解析、memoryRefs 断链修复、参考区紧凑化。签名沿用同一把发布钥匙。
versionCode = 12
versionName = "1.5.0"
        // : Compose UI test runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // F03: 构建可追溯性——注入 git SHA 和构建类型
        val gitSha = try {
            val process = Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short", "HEAD"))
            val text = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (text.length >= 7) text else "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "BUILD_TYPE", "\"debug\"")
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
            // 有 keystore.properties 时用正式签名发布；没有时不指定 signingConfig，
            // 产出 unsigned release APK（可用于 R8/资源裁剪/CI 编译验证），绝不回退 debug key。
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // F03: Release 构建覆盖 BUILD_TYPE
            buildConfigField("String", "BUILD_TYPE", "\"release\"")
        }
        debug {
            // F03: Debug 构建显式标记
            buildConfigField("String", "BUILD_TYPE", "\"debug\"")
            // 2026-10-06：debug 包也签正式证书。以前只有 release 走 `signingConfigs.release`，
            // 于是本机构建出来的 app-debug.apk 带的是 `CN=Android Debug` 那把本机 debug key，
            // 与 GitHub Release 上钉的 `cert_sha256`（scripts/signing-baseline.txt）不是同一把
            // ⇒ 覆盖安装被 Android 拒（签名不一致），只能卸了重装。
            // 没有 keystore.properties 时**不指定** signingConfig（保持 AGP 默认 debug key，
            // 干净检出与 CI 不受影响）；绝不用别的钥匙、也不让 debug 悄悄产出 unsigned。
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
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
            // Robolectric（ / ）要读合并后的 manifest 与资源才能组合 Compose 树，
            // 没有这一行 createComposeRule() 在 JVM 上直接起不来。
            isIncludeAndroidResources = true
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
    implementation("androidx.security:security-crypto:1.1.0")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    // M6 单元测试（纯 JVM，无 Android 依赖）
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.11")

    //  + ：在 JVM 上真跑 Compose 语义树。
    // 为什么要在 unitTest 里再来一条 UI 测试通道：instrumentation 只在 CI 的 emulator 上跑，
    // 本机没有 system image → "48dp / TalkBack 属性"这类断言在本机永远是"没测过"，
    // 而复核  第 4 条禁止用源码 grep 顶替它。Robolectric 让同一批断言在 CI 之前就能红。
    testImplementation(platform(composeBom))
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // ui-test-manifest 走下面已有的 debugImplementation：Robolectric 读的是 debug 变体
    // 合并后的 manifest，再声明一份 testImplementation 会被 lint 判成配置放错
    //（TestManifestGradleConfiguration，本机实测）。

    // : Compose UI interaction test（需要 emulator）
    androidTestImplementation(platform(composeBom))
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// CI 精简（原文口径）：`-Pci-lite` 只由默认 push/PR 门禁传（ci.yml「Unit tests」步骤）。
// 规范点名"移到已有手动工作流"的两族在这里排除：
//   ① 视觉快照族 `ui.visual.*`——整族测试已删除，专用比对入口（manual job 的
//      视觉基线比对脚本）一并下线，此档不再命中任何类；
//   ② 源码文本/字符串预算/形状所有权扫描族 `architecture.*`——原句点名，测试仍在，
//      其发布前运行入口原挂在 manual 的 visual-baseline job，该 job 已随视觉基线族一并删除；
//   ③【本轮补】昂贵的全量 JVM UI 渲染 / 多尺寸多字号**矩阵循环族**——原移两颗
//      （ui.matrix 全量扫描档 + ui.home 字号族档）已整类删除，类名档 exclude 一并撤销；
//      ⚠ 保留清单（不动）：ui.visual.* / architecture.* 之外的**大量 UiMatrix 用点属"单档挂载
//      夹具"**——只 `UiMatrix(360, …).RenderIn` 钉一次密度/字号，是核心语义回归的载体，
//      一律保留。带"1 颗矩阵循环 + N 颗单档语义"混合的类也不整类移出（移一颗会连带杀其语义覆盖，
//      违）：HomeScaffoldFrameSemanticsTest / LbAsyncStateTest / KbOnboardingWizardSemanticsTest /
//      EmptyStateOwnershipSemanticsTest / PanelHeaderTouchTargetsTest / UsageStatBarSemanticsTest /
//      MessageListEmptyStateTest / HomeScreenStructureTest。本轮落地的行为测试（含带 FULL 循环的
//      CaptureAppRowSemanticsTest / CaptureAppsScreenStatesTest）按保护清单一律保留，矩阵循环不作移出面。
// architecture.* 的发布前运行入口原挂在 manual.yml 的 visual-baseline job（--tests 并列），
// 该 job 已随视觉基线族一并删除——此族测试仍在，但当前没有 CI 运行入口。
// ⚠ 黑名单制：excludeTestsMatching 只**追加排除**，新写的测试默认仍在 push 跑；不用包名一把梭
//   （一把梭会把同包里大量"单档挂载夹具"语义测试一并静默漏跑＝假绿）。本地不带开关的全量跑一档不跳。
// 要求保留的核心回归族（知识库生命周期与隔离、生成解析与取消、配置/连接状态、范围和意图
// 装配、本轮直接修复的输入/列表行为）全部留在 push——套件"几千格"的本体是这些快断言族，
// 不是被移走的渲染/扫描族。本地不带开关的全量跑一档不跳，读数不变。
// 懒配置（configureEach）+ 名字判断：`tasks.named("testDebugUnitTest")` 在脚本位置急切执行，
// 那时 AGP 还没注册变体任务，整条配置链直接炸（本机实测）；`withType<Test>` 不带名字判断
// 又会把 roborazzi 的 verify/record 同型任务一起排掉——那是"静默漏跑＝假绿"的形状。
tasks.withType<Test>().configureEach {
    // 测试 JVM 并行 fork：默认全挤 1 个 JVM 串行，16 核全在闲。8 路×1GB 堆 + 守护进程
    // ≈12GB < 16GB 物理内存；各 fork 独立 JVM，静态状态互不串扰。OOM 就降回 4。
    maxParallelForks = 2
    maxHeapSize = "1g"
    if (name == "testDebugUnitTest" && project.hasProperty("ci-lite")) {
        exclude("**/com/lovebrain/app/ui/visual/**")
    }
}
