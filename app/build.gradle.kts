import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    //  的截图基线：JVM 渲染（Robolectric + Compose），不依赖设备截屏——
    // 三个 Activity 都设了 FLAG_SECURE，设备侧 screencap 结构上拍不到那一屏
    // （CI run 36236822959 实测：装回去、前台确认是 app 之后，screencap 交回 0 字节）。
    id("io.github.takahirom.roborazzi")
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
// 2026-10-10：1.4.1 / versionCode 10。这一批装的是本轮那几处修复与补齐
// （首次建库不再造出第二座库、悬浮球透明度与大小、悬浮助手开关、回复卡纵向排列、
// 供应商展开列表叠放、顶部拖动带命中区、意图保存回执、首页累计使用小卡）。
// 只推代码：没打 tag、没建 GitHub Release；发版是另一步，要产品负责人点头。
versionCode = 10
versionName = "1.4.1"
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
    implementation("androidx.security:security-crypto:1.1.0")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

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
    //  截图基线（JVM 渲染）：见上面的 roborazzi 插件。
    // 钉版本的理由：1.7x 用 Kotlin 2.0.21 编，本仓库还是 Kotlin 1.9.24。
    testImplementation("io.github.takahirom.roborazzi:roborazzi-core:1.30.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.30.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.30.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.30.0")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
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

// §13 CI 精简（§13.2 原文口径）：`-Pci-lite` 只由默认 push/PR 门禁传（ci.yml「Unit tests」步骤）。
// 指导书点名"移到已有手动工作流"的两族在这里排除：
//   ① 视觉快照族 `ui.visual.*`——专用比对入口是 manual job 的 scripts/verify_visual_baseline.sh
//      （:app:verifyRoborazziDebug），不为 push 陪跑；
//   ② 源码文本/字符串预算/形状所有权扫描族 `architecture.*`——§13.2 原句点名，随 manual 的
//      发布前全量步骤跑；
//   ③【本轮补】昂贵的全量 JVM UI 渲染 / 多尺寸多字号**矩阵循环族**——§13.2 L340 同半句
//      "昂贵的全量 JVM UI 渲染/多尺寸多字号……移到已有手动工作流"。此前只落了前半句
//      （快照+源码扫描），"多尺寸多字号"这一半没落实：两类逐档扫的矩阵渲染仍在 push 陪跑。
//      只移**整类皆为多档 for/forEach 循环**的类（每颗 @Test 都跑满 UiMatrix.FULL / WIDTHS_DP /
//      FONT_SCALES，无单档语义测试可被误伤）：
//        · com.lovebrain.app.ui.matrix.UiMatrixFullSweepTest —— 6 格全为 4 宽×3 字逐格扫；
//          其单档语义判据本就钉在 LbChipTest / LbStatusBadgeTest / LbSettingRowStateTest /
//          LbPrimaryButtonStateTest（文件头自述"本文件只**扩矩阵**，不改那些用例的判据口径"），
//          移出后 push 仍保留那四颗单档语义；
//        · com.lovebrain.app.ui.home.LongProviderNameSemanticsTest —— 2 格皆为 FULL 逐格扫 +
//          FONT_SCALES 字号族，判据本身依赖宽度/字号上涨，无单档形态可留。
//      ⚠ 保留清单（不动）：ui.visual.* / architecture.* 之外的**大量 UiMatrix 用点属"单档挂载
//      夹具"**——只 `UiMatrix(360, …).RenderIn` 钉一次密度/字号，是 §13.2 L338 核心语义回归的载体，
//      一律保留。带"1 颗矩阵循环 + N 颗单档语义"混合的类也不整类移出（移一颗会连带杀其语义覆盖，
//      违 §13.2 L338）：HomeScaffoldFrameSemanticsTest / LbAsyncStateTest / KbOnboardingWizardSemanticsTest /
//      EmptyStateOwnershipSemanticsTest / PanelHeaderTouchTargetsTest / UsageStatBarSemanticsTest /
//      MessageListEmptyStateTest / HomeScreenStructureTest。本轮 §7–§11 落地的行为测试（含带 FULL 循环的
//      CaptureAppRowSemanticsTest / CaptureAppsScreenStatesTest）按保护清单一律保留，矩阵循环不作移出面。
// 这两类经类名档 excludeTestsMatching 移出 push 后，在 manual.yml 的 visual-baseline
// "发布前扫描族专用入口"同一步骤照跑（--tests 并列），移出 ≠ 删除，§13.2 "不能全删/不能假绿"由此兑现。
// ⚠ 黑名单制：excludeTestsMatching 只**追加排除**，新写的测试默认仍在 push 跑；不用包名一把梭
//   （一把梭会把同包里大量"单档挂载夹具"语义测试一并静默漏跑＝假绿）。本地不带开关的全量跑一档不跳。
// §13.2 要求保留的核心回归族（知识库生命周期与隔离、生成解析与取消、配置/连接状态、范围和意图
// 装配、本轮直接修复的输入/列表行为）全部留在 push——套件"几千格"的本体是这些快断言族，
// 不是被移走的渲染/扫描族。本地不带开关的全量跑一档不跳，读数不变。
// 懒配置（configureEach）+ 名字判断：`tasks.named("testDebugUnitTest")` 在脚本位置急切执行，
// 那时 AGP 还没注册变体任务，整条配置链直接炸（本机实测）；`withType<Test>` 不带名字判断
// 又会把 roborazzi 的 verify/record 同型任务一起排掉——那是"静默漏跑＝假绿"的形状。
tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest" && project.hasProperty("ci-lite")) {
        exclude("**/com/lovebrain/app/ui/visual/**")
        exclude("**/com/lovebrain/app/architecture/**")
        // §13.2 L340 "多尺寸多字号"半句：整类皆为逐档矩阵扫描的两颗，类名档精确移出（黑名单追加）。
        // 用文件模式而非 `excludeTestsMatching`：后者在 Gradle 8.0 的 Test 任务上不存在
        // （本机实测脚本编译红），且 `*` 尾一并挡掉 `$1`/lambda 内部类文件。
        exclude("**/com/lovebrain/app/ui/matrix/UiMatrixFullSweepTest*")
        exclude("**/com/lovebrain/app/ui/home/LongProviderNameSemanticsTest*")
    }
}
