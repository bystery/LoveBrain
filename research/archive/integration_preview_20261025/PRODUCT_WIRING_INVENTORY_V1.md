# PRODUCT_WIRING_INVENTORY_V1 — v5 arena harness → D:/LoveBrain 接入盘点与接线方案

日期 2026-10-25。作者：只读盘点（Qoder）。**本轮不改生产、不编译 Kotlin、不跑 Android、不触碰 `127.0.0.1:17770`。**
产品仓库核对（只读，`git --no-pager`）：branch `main`、HEAD `176e521 chore: raise the version to 1.4.2`(v1.4.2)、
`nothing to commit, working tree clean`。承接 `KOTLIN_INTEGRATION_MAP.md` 的 6 项未验证——本文件把「能在本机磁盘上证实的」
与「本机根本证不了的」分开写死。**原型跑通 ≠ Android 通过**：所有结论按证据分级，绝不冒称已验收。

证据分级标记：
- **[V-opened]** = 本轮我实际打开该文件并按行读到（下文引 `文件:行`）。
- **[V-disk]** = 本机文件系统上直接证实存在/不存在。
- **[U]** = 未验证 / 本机证不了，须人工做。

---

## 1. 构建前置条件（evidence-based）

### 1.1 工具链版本（全部来自文件，不猜）

| 项 | 值 | 来源（[V-opened]） |
|---|---|---|
| Gradle wrapper | **8.7**（`gradle-8.7-bin.zip`） | `gradle/wrapper/gradle-wrapper.properties:3` |
| Android Gradle Plugin (AGP) | **8.6.0** (`com.android.application`) | `build.gradle.kts:3` |
| Kotlin | **1.9.24**（`org.jetbrains.kotlin.android` + `plugin.serialization`） | `build.gradle.kts:4`、`build.gradle.kts:5` |
| Roborazzi 插件 | 1.30.0（钉版，因 1.7x 需 Kotlin 2.0.21） | `build.gradle.kts:9`、`app/build.gradle.kts:181-184` |
| Compose Compiler ext | 1.5.14 | `app/build.gradle.kts:113` |
| Compose BOM | 2024.06.00 | `app/build.gradle.kts:130` |
| compileSdk | **35** | `app/build.gradle.kts:23` |
| minSdk | **26** | `app/build.gradle.kts:27` |
| targetSdk | **35** | `app/build.gradle.kts:28` |
| versionCode / versionName | 11 / 1.4.2 | `app/build.gradle.kts:32-33` |
| Java source/target | **VERSION_17** | `app/build.gradle.kts:92-93` |
| Kotlin jvmTarget | **"17"** | `app/build.gradle.kts:96` |

### 1.2 Android SDK 路径解析（env/property）

- `gradle.properties:2` 注释明确：**「本机 JDK 路径由环境变量 `JAVA_HOME` 提供，不写入仓库」**。
- 仓库里 **没有 `local.properties`**（[V-disk]：`NO local.properties`）→ 因此**没有 `sdk.dir` 属性**。
- AGP 找 SDK 的优先级：`local.properties` 的 `sdk.dir` → 环境变量 `ANDROID_HOME` → `ANDROID_SDK_ROOT`。
  本机实测（[V-disk]）：`ANDROID_HOME` **未设**；`ANDROID_SDK_ROOT=D:\Android\Sdk` **已设**。
  ⇒ 当前 SDK 通过 `ANDROID_SDK_ROOT` 解析（不是靠仓库里的 `sdk.dir`）。

### 1.3 JDK 要求 vs 本机实况

- 项目**要求 JDK 17**（`compileOptions`/`jvmTarget` 锁 17；`README.md:73`「需要 Android SDK 与 JDK 17」；AGP 8.6 最低 17）。
- 本机实况（[V-disk]）：
  - `java -version`（PATH）= **11.0.26**（Oracle）。**过低**：用它跑 Gradle 8.7/AGP 8.6 会因 <17 直接失败。
  - `JAVA_HOME` = **Eclipse Adoptium jdk-21.0.3.9-hotspot**（cmd `%JAVA_HOME%` 证实）。Gradle 走 `JAVA_HOME`，JDK 21 能跑 AGP 8.6 且能产 17 字节码。
  - **没有独立的 JDK 17**（`C:/Program Files/Java` 不存在）。
  ⇒ **MISSING/风险**：与 README 精确一致的 **JDK 17 未安装**；现用 JDK 21 代跑属「超出钉版」，能否逐字节复现官方产物 **[U]**。

### 1.4 依赖是否需要联网（仓库声明，[V-opened]）

需要网络拉依赖，声明集中在 `settings.gradle.kts`：
- `pluginManagement.repositories`（`:1-13`）：`google()`（限 `com.android.*`/`com.google.*`/`androidx.*`）、`mavenCentral()`、`gradlePluginPortal()`。
- `dependencyResolutionManagement.repositories`（`:14-20`）：`google()`、`mavenCentral()`；且 `RepositoriesMode.FAIL_ON_PROJECT_REPOS`（`:15`）→ 模块不得自带仓库。
- 主要外部坐标（`app/build.gradle.kts:128-198`）：Compose BOM、okhttp 4.12.0、koin 3.5.6、kotlinx-serialization 1.6.3、kotlinx-coroutines 1.8.1、security-crypto 1.1.0、robolectric 4.14.1、roborazzi 1.30.0、androidx.test.* 等。

### 1.5 离线构建在本机此刻是否可行

- **有利证据 [V-disk]**：Gradle 8.7 wrapper 发行包已缓存 `~/.gradle/wrapper/dists/gradle-8.7-bin`；依赖模块缓存 `~/.gradle/caches/modules-2` 存在（约 124 个坐标目录）；`app/build/` 留有历史 `intermediates/outputs/test-results/snapshot`（说明本机此前成功构建过）。⇒ 对 `assembleDebug`，**离线（`--offline`）看起来可行**。
- **但仍未证实 [U]**：我没运行任何 gradle 任务，无法保证缓存对当前 HEAD 完整闭合、也无法验证 JDK 21 vs 钉版 17 的差异。**离线可行性只是「像可行」，非「已跑通」。**

### 1.6 Android SDK 组件盘点 [V-disk]（`D:/Android/Sdk`）

- `platforms/`：**android-34、android-35**（compileSdk 35 所需平台在）。
- `build-tools/`：**34.0.0、35.0.1、37.0.0**。
- `platform-tools/`（含 `adb.exe`）、`cmdline-tools/latest`、`emulator/`、`sources/`、`licenses/`（`android-sdk-license` 已接受）。
- **缺失**：`system-images/` **不存在**、`~/.android/avd` **不存在**、`adb devices` **无设备连接**。⇒ **无法起模拟器/真机、无法跑 `androidTest`/UI 验收。**

### 1.7 本机缺失清单（plainly）

1. **JDK 17** 未安装（PATH 是 11，JAVA_HOME 是 21，都不等于钉版 17）。
2. **无 `local.properties`/`sdk.dir`**（靠 `ANDROID_SDK_ROOT` 兜底，CI/干净机可能定位不到）。
3. **无 emulator system-image、无 AVD、无连接设备**（instrumented/UI/体验验收全不可做）。
4. 未安装/未确认的构建产物一致性：**离线构建从未在本轮实际跑过**（仅凭缓存推断）。

---

## 2. 真实代码接入点（文件路径 + 符号，逐个定位）

> 说明：以下是 v5 arena harness 五接口对应的**产品真实 Kotlin 落点**。定位=只读找到同名文件与符号；
> 「v5 需要换什么」= 接线设想，非本轮改动。除标注外均为 **[V-opened]**（我打开并按行读到符号签名）。

### 2.1 回复轮提示词装配
- **文件**：`app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt`
- **符号**：`suspend fun buildReplyUserPromptWithRefs(...)`（`PromptBuilder.kt:225`，[V-opened]）；装配子块在 `domain/prompt/`（`IntentIdeaBlock.kt`/`ChatTranscriptBlock.kt`/`CurrentSceneInjection.kt` 等 [V-disk]）。
- **v5 要换**：把 `normalize_events`/`IntentInput.effective_status` 产出的 (a) 已发送历史 `<chat>` 白名单、(b) 仅 ACTIVE 的意图块、(c) note-kind 私密约束通道接进这里。
- **既有开关？** 无引擎开关；`PromptBuilder` 由 DI 单例固定装配（见 2.5）。**须新增**路由。

### 2.2 意图状态策略
- **文件**：`app/src/main/java/com/lovebrain/app/domain/IntentPolicy.kt`
- **符号**：`fun effectiveStatus(expiry: IntentExpiry, status: IntentStatus): IntentStatus`（`IntentPolicy.kt:68`，[V-opened]）。
- **v5 要换**：**直接复用**产品既有终态归一（COMPLETED/EXPIRED/PAUSED→非 ACTIVE），`IntentInput.effective_status` 语义映射到此；关闭意图 ≠ 删真实约定。
- **既有开关？** 不需要开关，是复用。**[U]**：`effectiveStatus` 的逐状态等价性未与 arena 对拍（MAP §5）。

### 2.3 模型仓库调用点（出站生成）
- **文件**：`app/src/main/java/com/lovebrain/app/data/DeepSeekRepository.kt`
- **符号**：`generateRaw`（`:352`、重载 `:373`）、`generateRawWithMetadata`（`:362`、`:391`），实现端口 `domain/port/AiGateway.kt:45/48`（[V-opened]）。
- **v5 要换**：`OutputConsumer.parse` 的消费边界（空 analysis 合法、形状变体不崩、原文逐字节保留、键名可审计归一）应落在此文件既有 JSON 解析处（`DeepSeekRepository.kt:304/313` `LoveBrainResponse` 解码）。
- **既有开关？** 该实现被 Koin 固定绑定为唯一 `AiGateway`（见 2.5）。切换引擎 = **须新增** `AiGateway` 的另一实现 + 路由。

### 2.4 知识/记忆持久化 + corrections
- **文件**：`app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt`、`KnowledgeMemoryStore.kt`、`KnowledgeRepoStorage.kt`
- **符号**：`KnowledgeMemoryStore.kt:31` `writeTransaction(kbName, block)`（接口）、`KnowledgeRepoStorage.kt:45` 覆写实现；带校验写：`KnowledgeRevisionCheckedWriteService.kt`（[V-disk]）；corrections：`MemoryCorrectionPolicy.kt`、`KnowledgeRepoStorage.kt:113` `List<MemoryCorrection>` 解码（[V-opened]）。谈心/分析写回：`KnowledgeRepository.kt:699-704 appendCounselingEntries`。
- **v5 要换**：arena `EventStore` 的 (engine×contactId×story) 隔离与 snapshot/restore，映射到产品既有 revision-check 写；**新引擎事件存储必须与原版 kb/corrections 分目录/分 kb，互不覆盖**。
- **既有开关？** 无引擎级隔离开关（产品按 kbName 隔离，不按引擎）→ **须新增**「引擎」维度目录键。**[U]**：arena 事件存储语义 vs 产品 kb/corrections 逐字段等价未对拍（MAP §5）。

### 2.5 新旧引擎路由 / feature flag —— **产品现无引擎实验开关，须新增**
- **既有开关机制核查 [V-opened]**：全仓 grep `feature.?flag|experiment|remoteConfig|use_v5|BuildConfig.…` 无引擎开关。命中的「开关」都是**面向用户的功能开关**（`toggleCapture`/`toggleProactiveMode`/`toggleOnlyThisRound`，存于 `SecurePrefs`），不是新旧引擎路由。`BuildConfig` 仅 `DEBUG`/`BUILD_TYPE`/`GIT_SHA`（`app/build.gradle.kts:46-47/75/79`）。
- **引擎固定绑定点**（Koin `di/AppModule.kt`，[V-opened]）：
  - `single { DeepSeekRepository(get()) }`（`:63`）
  - `single<AiGateway> { get<DeepSeekRepository>() }`（`:65`）
  - `single { PromptBuilder(get(), get(), get(), get()) }`（`:91`）
  - `single { GenerationEngine(get(), get()) }`（`:98`）
- **结论**：`EngineRouter(use_v5)` **无对应既有产品机制**，须新增。落点建议：在 `AppModule.kt` 把 `AiGateway`/`PromptBuilder` 选择做成受 flag 驱动，flag 持久化复用 `SettingsStorePort`(`AppModule.kt:32`，实装 `SecurePrefs`)，非生产门禁用 `BuildConfig.BUILD_TYPE`。**[U]**：真实读取路径尚未接（MAP §3）。

---

## 3. 回滚设计（对齐原型 `EngineRouter`）

**默认态不变**：arena `EngineRouter.handle(..., use_v5=True)`（`engine_adapter.py:279-281`）里 `chosen = engine if use_v5 else ENGINE_OLD`。
产品侧接线应取**反向默认**——`use_v5` **默认 false**，只有显式开才走新引擎；关闭即回退原版 `PromptBuilder` + 原记忆管线。instant revert = 把该 flag 写回 false，不迁移/不回滚任何真实数据。

**按 contact / story 隔离**（源自 `EventStore._dir`，`engine_adapter.py:151-152` = `root/<engine>/<contactId>__<story>`）：
- flag 粒度到 `(contactId, story)`，仅被实验选中的联系人×剧情走 v5；其余完全用原版。
- `EventStore.exists_other_engine`（`:172-175`）保证新旧两份状态**不共用、不互相覆盖**。产品对应：新引擎事件目录键加「engine」维度，与 `KnowledgeRepository` 原 kb 物理分目录。

**flag OFF 时禁止交叉写**（回滚硬边界）：
- 新引擎事件存储 **绝不** 写入产品的 knowledge 归档 / `counseling`（`appendCounselingEntries` `KnowledgeRepository.kt:703`）/ corrections（`List<MemoryCorrection>`）/ profile 存储；反之原管线也不得读 arena 快照当作已发送历史。
- 空 analysis 在新引擎合法（`OutputConsumer.parse` `:181-208`）：不得被旧消费端当损坏去清库或触发旧摘要写回（MAP §4）。**须先在产品回放夹具验证（MAP 未验证项 4）再上开关。**

**note-kind 结构边界（私密军师备注）**（`Event.__init__` `engine_adapter.py:83-105`；`to_msg` 对 note 直接 `raise` `:100-101`）：
- `IDEA/ADVISOR_NOTE → kind="note"`；`note` **永不进 `sent`/`<chat>`**（`send_turn :293-295`，`to_msg` 拒）、**永不落真实事件存储**、恢复后不残留（`BOUNDARIES_RESULT.json` check 3/2 PASS）。
- 这是**结构判定（typed `kind`），不靠 speaker 字符串匹配**。产品接线时，note 通道只能进本轮临时约束、不得回流到 `ChatTranscriptBlock` 已发送历史或 `KnowledgeRepository` 归档。

---

## 4. 本机证不了的事 + 人工后续动作（逐条可交给人）

**证不了（blunt）**：① 无任何 Kotlin 编译；② 无 Android 模拟器/真机（`adb devices` 空、无 system-image/AVD）；③ 无 UI/体验验收；④ 未对用户真实数据做任何读写；⑤ 未触碰 `127.0.0.1:17770`。原型全 PASS 只证 **arena Python 替身 I/O**，不证 Android。

**人工下一步（每条给出命令/看什么）**：
1. **装 JDK 17**（与 README/`jvmTarget` 对齐）：装 Temurin 17 并把 `JAVA_HOME` 指过去（AGP 8.6 最低 17；现用 21 属超出钉版）。
2. **补 `local.properties`**（干净机/CI 用）：写 `sdk.dir=D\:\\Android\\Sdk`；否则确认构建环境导出 `ANDROID_SDK_ROOT`。
3. **先跑一次编译门禁用它证明「能编」**：`cd /d D:\LoveBrain && gradlew.bat --offline compileDebugKotlin`（离线不行就去 `--offline` 联网拉依赖）；看是否 BUILD SUCCESSFUL、有无 JDK 版本 / AGP 报错。
4. **跑纯 JVM 单测（不需设备）**：`gradlew.bat :app:testDebugUnitTest`（注意 `app/build.gradle.kts:235-245` 的 `-Pci-lite` 只跳矩阵/扫描族，核心生成解析/意图/装配回归仍跑）。看新接线是否破坏既有断言。
5. **装 emulator 组件才能做 UI/instrumented**：用 `D:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager` 装一个匹配 platforms 的 system-image + 建 AVD，再 `gradlew.bat :app:connectedDebugAndroidTest`；本机现在**跑不了**这一步。
6. **接实验开关前**：按 `AGENTS.md` 中央根解析（`TEAM_WORKSPACE_ROOT` 或 `<repo>/../TeamWorkspace` → `<中央根>/team/projects/lovebrain/START_HERE.md`）加载正式规则，核对当前仓库/分支后再动产品；在回放/状态恢复夹具先验「空 analysis 不被旧消费端当损坏」（MAP §4）。
7. **App 侧人眼验收**：开悬浮球→生成回复，确认原版行为不变（flag OFF 默认）、意图块只在 ACTIVE 出现、私密备注不出现在已发送历史、切 flag 能即时回退且不清库。

---

## 5. 成本记账（lab 规则）

产品集成工程量须记**独立桶**，**不得因「本轮零模型调用」而隐藏**（`BOUNDARIES_RESULT.json`/`REPLAY_RESULT.json` 均 `real_model_calls: 0`）。
本文件所费工时归入 **`集成测量`**（工程/接线/盘点），与 `考生运行`（真实 48×/32× 出分）、`评估研发`（评分器/协议）三桶分列，互不折算。本轮为只读盘点 + 单文档产出，计入 `集成测量`；无 `考生运行` 消耗。

---

## 6. 未改动声明

- **`git status`（D:/LoveBrain，[V-disk]）**：`On branch main / Your branch is up to date with 'origin/main'. / nothing to commit, working tree clean`；HEAD `176e521 chore: raise the version to 1.4.2`。
  本轮对 D:/LoveBrain **纯只读**（Read/Grep/git 只读命令/find/adb devices），未新增、未修改、未删除任何文件；工作树仍 clean 即证明未落盘。
- 新建的唯一文件：`D:/lovebrain-arena/integration_preview/PRODUCT_WIRING_INVENTORY_V1.md`（本文件），位于 arena 侧，非产品仓库。
