# LoveBrain 返工验收包（对齐 2026-09-23 全面独立复核报告）

> 被审提交：`286c9406b40b79e4443f1db465bfa5b489f540fa`（报告结论 FAIL）
> 本轮返工提交链：`8745a3d` → `ce89b86` → `5424e21` → `6d67b37` → `0abe572` → `73ed6a8` → `d390dc6` → `c51e443` → `a9ae136` → `42da306` → `aea24c5` → `4e1f2e4` → `eaba6af`(S2-05) → `73b3d08`(S2-07 错误类型) → `6597bd9`(S2-07 取消审计) → `9539dd2`(S2-07 工单编号) → `0da80d5`(修自己的伪门禁) → `dc1cb31`(SetupActivity 收口) → `316913f`(TriggerCoordinator 改冷流) → `4c1ec8b`(记账) → `50e4fb6`(Koin 图 JVM 解析) → `bac28b8`(仓库文本算法外提) → `db5cdf9`(schema/迁移→KnowledgeMigrator) → `690db96`(赞踩/点踩案例→FeedbackCaseController) → `c1293f1`(纠正判定与文案→MemoryCorrectionPolicy) → `752d34c`(改写台账与改写 prompt 外提) → `3871a25`(方案卡位置映射→ReplyPatch) → `63bcb48`(两处生成指纹→GenerationFingerprints) → `74a263d`(预算裁剪与场景链→PromptBudget/SceneChainInjection) →（本文件所在提交）
> 复核依据：`LoveBrain_Comprehensive_Reaudit_286c9406_2026-09-23.md`，逐节对照
> 编写日期：2026-09-24（第二轮补：S2-05 与 S2-07 三项在初版里被记为"未做"，现已做完并重新实测）

## 0. 先说没做到的部分

按报告 §9 末尾那句"任何用『后续完善』『理论上』『注释已说明』『测试文件已新增』替代，都不算完成"，
先把**未完成项**放在最前面，并说清它为什么没完成，不粉饰：

| # | 报告条目 | 状态 | 原因 |
|---|---|---|---|
| 1 | §7 P3-02/P3-03：TalkBack、2.0x 字体、360dp 截图矩阵 | **未执行** | 本机没有 Android system image（`D:/Android/Sdk/system-images` 为空），`adb devices` 为空，跑不了任何 instrumentation。触摸区问题我用**可离线执行的静态合同测试**（`ProductionUiContractTest`）把 §7.1 点名的每个反例钉住，但那不等于设备上的真实验收。 |
| 2 | §8 第一步门禁 6 项中的 `connectedDebugAndroidTest` 真跑 | **未执行** | 同上。CI 里这一项会真跑（`scripts/run_ui_tests.sh` + 空证据即失败），但我在这台机器上没有执行过，所以不声称它通过。 |
| 3 | §7 P3-04：Macrobenchmark / Perfetto / 帧时间 | **未产出数据，且模块接线本轮撤回** | 需要设备。另外并行工程加过一个 `:benchmark` 模块，但它声明的 `androidx.baselineprofile` 插件在 1.3.0–1.3.4 各版本于 Google Maven、Gradle Plugin Portal、mavenCentral **全部 404**（实测），而插件写在根 `build.gradle.kts`，导致**所有** Gradle 任务在配置期就失败（连 `:app:tasks` 都跑不起来）——正是报告 §1 的头号问题。已整体回退该接线，只保留可离线验证的部分：真增量解析器 + `IncrementalJsonStreamParserTest`。帧数据仍然没有，不给它披上"已接入"的外衣。 |
| 4 | §5.2 锦囊"真实费用基准" | **未产出数据** | 需要用户 API Key 与真实请求。已提供可重放脚本与输出契约（`scripts/suggest_cost_baseline.py`），未执行 = 无数字。 |
| 5 | §7.2/§8.3.6 抓包证明零遥测 | **未执行** | 需要设备。已提供 `scripts/verify_network_egress.sh`；README 已改成"依据源码与依赖清单核对，抓包未执行"，不再拿未做的事当证据。 |
| 6 | §8.3.7 v1.3.1 → 候选 APK 真机升级断言 | **脚本与门禁做，设备未跑** | `scripts/run_upgrade_test.sh` 全链存在且被 CI 调用，但覆盖安装、数据可读、主流程可用这三段都需要设备。 |
| 7 | §6 S2-05 的"上帝类"这一项**只部分达成** | 部分 | `KnowledgeRepository` 已从 2283 降到 **1857**（低于报告基线 2118）：文本算法进 `KbTextOps`、schema 探测与旧库迁移进 `KnowledgeMigrator`。`LoveBrainViewModel` 2941 → **2678**（报告基线 2954，−9%）——赞踩/点踩案例进 `FeedbackCaseController`、纠正判定与文案进 `MemoryCorrectionPolicy`、改写台账与改写 prompt 进 `RewriteLedger`/`RewritePrompt`、方案卡位置映射进 `ReplyPatch`、两处生成指纹进 `GenerationFingerprints`，但**降幅仍在两位数以下**，剩下的单条改写链路 228 行、锦囊 155 行、Trigger 事件落点 188 行、持续意图 134 行还在同一个类里。`PromptBuilder` 已从 1199 降到 **968**（低于基线 1177）。拆文件不等于拆职责，也不等于行数下降，这一格不给"完成"。 |

初版这里还列着五条"未做"：S2-05 的 Activity 迁移、S2-07 的字符串前缀推错误、S2-07 的工单编号，
外加逐字复核时我自己抓出来的两处同病（SetupActivity 伸手进 VM 拿仓库、KnowledgeTriggerCoordinator
仍是"收外部 scope + 自启 Job + 巨型 Callbacks 反向写 VM 状态"）。这五条本轮全部做完，
移入第 1 节变更表，每条都配了可执行证据与防回流静态规则——不再是"注释式修复"。

## 1. 逐条变更表（报告条目 → 改动 → 证据）

### §1 / §3：编译与 CI

| 报告事实 | 改动 | 可核对证据 |
|---|---|---|
| `TopicRecorder.kt:925:83 Cannot find a parameter with this name: chain` | 不是删参数，而是把 WAL ongoing/scene payload 改成完整类型化结构（见 §6 S2-04 行） | 本机 `./gradlew --offline :app:compileDebugKotlin` 与 `:app:compileDebugAndroidTestKotlin` 均 BUILD SUCCESSFUL；后者正是报告里 `ui-test` 挂掉的同一道门 |
| `ui-test` retry shell 报 `expecting "fi"` | 整段 retry 搬进仓库脚本 `scripts/run_ui_tests.sh`（`set -euo pipefail`、保留退出码、workflow 只调用脚本） | `bash -n` 全部 16 个脚本通过；脚本可本地执行 |
| CI run #39 总结论 failure | 见"第 3 节 同一 SHA required checks" | 本地全门禁绿（第 2 节） |
| 最新公开 Release 仍是 v1.4 无候选 | 不打 tag、不发 Release（遵从报告 §1"独立放行意见"） | 见第 5 节发布判定 |

### §3：CI 与发布门禁的 8 个伪门禁，逐个真做

| 报告点名的伪门禁 | 现在 |
|---|---|
| 旧 APK 下载 `|| echo` + `continue-on-error: true` | `scripts/download_release_apk.sh`：下载失败硬失败；再校验非空、是合法 ZIP、SHA-256 等于 `scripts/signing-baseline.txt` 钉住的值、APK 内 versionName 与请求 tag 一致、签名证书与钉住指纹一致 |
| "写入升级夹具"只有注释没有命令 | `scripts/write_upgrade_fixture.sh` 真写夹具并回读确认；`scripts/assert_upgrade_state.sh` 用清单核对 |
| 旧版安装/首次启动 `\|\| true` | 去掉；任一步失败即红 |
| 候选用 `assembleDebug` 而非签名/R8 Release | `upgrade-test` 先 `:app:assembleRelease`，候选是 release 产物 |
| `adb logcat \| grep "FATAL EXCEPTION" \|\| true` 发现崩溃也返回成功 | `scripts/check_logcat_fatal.sh` 极性改正：命中即失败；空日志按"没采到证据"失败而不是通过（实测：含 FATAL → exit 1，干净非空 → exit 0） |
| 没有断言旧数据仍可读、schema 已升级、主页面可用 | `assert_upgrade_state.sh` 逐项断言：包版本等于候选版本、每个夹具文件存在且非空且仍含哨兵、库 schema ≥ 候选 CURRENT、kb.json 存在可解析、主 Activity 起来且 resumed、进程存活、无 fatal |
| Release 只等 verify + ui-test | `wait-ci` 现在等 verify + ui-test + **upgrade-test** 三项 |
| 不比较已公开 v1.3.1 证书指纹，只自验签名 | `verify_signing_continuity.sh` 与 `signing-baseline.txt` 的 `cert_sha256` 比对 |
| "Dependency license scan" 实为 `gradle dependencies` + `|| true` | `license_scan.sh`：解析 releaseRuntimeClasspath 解析后坐标 → 定位 POM（跟随 parent、再退化到 license URL）→ 按 `license-policy.txt` 分类 → 产出 SPDX-2.3 SBOM + CSV + 报告；无许可证/被禁/图为空/SBOM 缺失或不可解析一律失败。实测本机 **124/124 模块可证、0 拒绝、0 缺元数据** |
| XML/HTML/截图为空可"warn 后放行" | `assert_artifacts.sh`：无 XML、0 字节、tests=0、failures>0、低于 `--min-tests`、HTML 缺失/过小、截图目录空 → 全部失败；上传步骤 `if-no-files-found: error` |

`assert_artifacts.sh` 额外加了**反伪造**检查（报告的精神是"不信自报"）：`--min-tests auto` 要求
XML 声明的 `tests="N"` 与真实 `<testcase>` 元素个数完全相等。实测：把一份 XML 写成
`tests="5"` 但只放 2 个 `<testcase>` → 该门禁 exit 1。

两份 workflow 里 `|| true` / `continue-on-error` / `|| echo` 计数：**ci.yml 0、release.yml 0**。
两份 YAML 用 `yaml.safe_load` 解析通过（ci: verify/ui-test/upgrade-test；release: wait-ci/build）。

### §2 + §5：第一阶段遗留

| Issue | 报告结论 | 本轮 |
|---|---|---|
| S1-01 | PARTIAL PASS：入口方向对，验证/无障碍/死 API/停止范围不达标 | 方向保持不动（遵从"不能再改回双半按钮"，且新增反向护栏用例：空态是纯文字入口，出现"主动发"/"生成回复 · 0条消息"即红）。报告列的 7 个"仍不给 PASS"的点逐个处理，见 §8.1.3-6 行 |
| S1-02 | FAIL：编译不过、真实链路没跑通、旧回调可污染新请求 | 编译恢复；reducer 身份门禁让旧请求事件整条丢弃（`ReplyChunk`/`ReplyCompleted` 带 requestId，`ownerRequestId` 在 Idle 后仍保留，堵掉"Idle 时 requestId==null 所以谁都合法"）；`ProductionUiContractTest` + 新 instrumentation 用例覆盖真实 Panel + fake Provider |
| S1-03 | FAIL：Overlay 测试没挂载生产按钮、没有成功 Provider 流 | 新增 `androidTest/.../testing/MainChainHarness.kt` + `FakeProviderServer.kt`：挂生产 Panel、经 MessageList 真输入、点真"生成回复"、用可控 Provider 流；44 个 instrumentation 用例编译通过。**执行仍未做**（无设备） |
| S1-04 | PARTIAL/FAIL：指纹虚报、日期未冻结、真实费用基准缺失 | 指纹覆盖真正注入的事项（`moment/plan.md` 内容指纹）与表达偏好（`understand/style.md`），prompt 版本改成资产内容 hash 而非 `BuildConfig.VERSION_NAME`；日期在发起时冻结进 `SuggestRequestContext`，跨午夜完成仍写发起那天。费用基准仍缺真实数据（§0 第 7 条） |
| S1-05 | PARTIAL PASS | 未新增改动（报告未列必改项） |
| S1-06 | PARTIAL PASS：无 APK/截图/字体证据 | 本轮产出**可安装签名 APK**；截图矩阵仍未做（§0 第 4 条） |

### §6：第二阶段遗留

| Issue | 报告点名的具体缺陷 | 本轮处置 |
|---|---|---|
| S2-01 | Engine 立刻 `toChatMessages()/toKnowledgeBase()` 退回旧模型；`profile` 固定空串；revision 只是 turnCount 近似；准备后回读完整 config，不一致只写日志；prompt 资产版本没冻结 | 全部改掉：`KbContext.profile` 装真实画像正文（`readProfile`）；`revision` 是回复链路实际读取文件的 SHA-256（`contentRevision`），不再用 turnCount 近似；Provider 身份冻结 `ticketId/hostHash/model/thinkingMode` 四项，Engine 只按冻结 ticketId 取配置（`configForTicket`），**对不上就失败**（新增 `ReplyFailureKind.ProviderChanged`），不再"记日志然后用新配置发"；prompt 资产 hash 冻结进 input，漂移只记诊断、仍用冻结 prompt；资产 hash 由 `asset_hashes.sh` + `docs/prompt-assets.lock` 落地并可 `--check`。`GenerationInput` 适配器仍保留但降级为 PromptBuilder 内部使用（公开入口收 GenerationInput） |
| S2-02 | invokeOnCompletion 在 Mutex 外读改写 StateFlow；stop/stopByType/shutdownAll 不持锁；`startSync` 锁忙返回 null 但调用方已先启动 Job 且忽略 null；SUGGEST/PROFILE_REFRESH 无同类去重；REWRITE/PROFILE_REFRESH 无注册点；ViewModel 仍手写 guard；`stopGeneration` 一次停三类；`dispose` 自己 cancel 不调 shutdownAll；**仓库中没有 ForegroundOperationCoordinatorTest** | 协调器重写：`start(type, requestId) { body }` 在锁内用 `CoroutineStart.LAZY` **先注册后启动**，被拒就 cancel 一个从未跑过的 Job → 不可能有"跑了但不受管"的孤儿；start/stop/stopCurrent/shutdownAll/invokeOnCompletion 全部走同一把 `ReentrantLock`（非挂起路径也能上同一把锁）；六类互斥矩阵含同类去重；REWRITE 与 PROFILE_REFRESH 真正注册；`stop` 按租约、`stopCurrent` 按类，`stopGeneration()` 只停 REPLY；ViewModel 删掉 `generateJob/counselingJob/suggestJob/proactiveJob/rewriteJob/profileRegenerationJob` 与手写 guard，`isProactive/isSuggesting/isCounseling/profileRegenerating` 全成派生；`dispose()` 调 `shutdownAll()`。**新增 `ForegroundOperationCoordinatorTest`**（11 个用例，含"被拒的 start 一次都不跑 body""停一个不牵连另一个"） |
| S2-03 | Engine 仍暴露巨型 Callbacks；回调无 requestId；旧请求迟到可被贴新 requestId；Idle 接受任意迟到 Completed；`onReplyResult` 不拒绝还直写 `_result`；Chunk/Schemes/Usage 未接 Flow；reducer 只是局部包装 | `GenerationEngine.Callbacks` 整个接口删除；Engine 改四条冷流 `replyStream/counselingStream/suggestStream/proactiveStream`，不收 scope、不 launch、不返回 Job；每个事件自带冻结 requestId；回复状态收进单一 `ReplyUiState`，唯一写入口 `dispatchReply → ReplyReducer.reduce`，被拒时返回**同一个对象**（调用方据此知道被拒）；`_result/_replyRequestState/_streamingCoreText/_streamingSchemes` 不再是独立可写流，全部 `map` 派生；chunk 在 dispatch 层合并后再归约，节流不再靠绕过状态源实现 |
| S2-04 | 见 §1；另外：roundId 随机、恢复丢 sourceIds/itemId/state、WRITING 从不写、target 不在单一锁内、recent marker 当跨文件提交标记、恢复顺序导致漏加 count、rotate 边界重复 rotate、手写 JSON 丢反斜杠、`turnCountIncrement` 不按值用、无 `RoundCommitJournalTest` | `RoundCommitJournal` 重写：kotlinx.serialization 取代手写 parser（转义保真，实测 `\n`/`\r`/引号/字面 `\\n`/尾反斜杠往返不变）；roundId 由 kb+消息 ID 集合派生（重试同身份）；六个投影各有水位；rotate 与 setCurrentTopic 拆成两个投影，"已 rotate 未 set"不再重复归档；PREPARED→**WRITING**→COMMITTED 三段真写；`incrementTurnCountBy(delta)` 按事件里的值；跨文件幂等看 committed 清单，recent marker 降级为该文件自身去重（用例：抹掉 marker 后仍不重复写）；在途事务存在时开新轮抛错拒绝覆盖；取消也保留 journal；损坏 journal 先落 `.round_commit_corrupt.log` 再删；`RoundCommitJournalTest` 17 例，对六个边界逐个故障注入并断言恢复后与一次跑通逐文件等值 |
| S2-05 | 七个上帝类；两个 Activity 只加注释承认违规 | **Activity 侧已做完**（初版这里写的"未做并已回退"已不成立）：新增 `KnowledgeBaseViewModel` + `KbEditViewModel`，两个 Activity 里 `KnowledgeRepository`/`DeepSeekRepository`/`SecurePrefs` 四个类型出现次数实测 **0**，改由 `by viewModel()` 取得；两条"此处违反 SRP/DIP、以后应改"的注释删除。建库事务原本靠 `kbCreationInProgress` boolean + `onboardingJob` 两套台账，现在只剩 `creationJob?.isActive` 一个真源，五种结果（画像完整/降级/建库失败/取消/未配置供应商）改成 typed `KbEvent` 回传，UI 只按事件选文案。另把两块**只能在真机上碰**的私有逻辑下沉成纯件：`KbArchiveTransfer`（zip 导出 + 暂存→校验→原子搬入）与 `OnboardingResultParser`（marker 分段 + 降级判定），并新增 13 + 8 条 JVM 用例真跑到 0 失败（路径穿越、zip bomb、条目数超限、元数据不一致、同名碰撞、坏包不留半截库、暂存清理）。`KnowledgeBaseActivity` 1213→924、`KbEditActivity` 584→572 行。<br>仓库与 ViewModel 侧继续拆：`KbTextOps`（不碰文件的文本/格式算法）、`KnowledgeMigrator`（schema 探测 + v1→v3 旧库迁移 + plan 归档压缩，经 `KbStorageAccess` 回到仓库同一把锁）、`FeedbackCaseController`（赞/踩与点踩案例）、`MemoryCorrectionPolicy` + `RoundCorrectionStore`（纠正判定与文案）、`RewriteLedger`（改写状态与被改写版本历史）、`RewritePrompt`（改写请求拼装）。`ReplyPatch`（方案卡位置映射：A/B/C/D↔四个字段、方向按 index 且 null=本轮不适合）。实测 `KnowledgeRepository` 2283 → **1857**（已低于报告基线 2118），`LoveBrainViewModel` 2941 → **2678**。<br>但"上帝类"这一格仍只算**部分达成**：VM 降 9%，剩下单条改写链路 228 行、Trigger 事件落点 188 行、锦囊 163 行、事件归约 138 行等还在同一个类里，`PromptBuilder` 1199 行一行未动（见 4.1）。我不用文件变小来冒充职责变清。 |
| S2-06 | 新库无 oldGlobal 直接 return 可能永不写版本；`KbRelativePath` 只有定义无使用；公共方法仍收裸 String；未来 schema 不拒绝；无 v1.3.1 夹具升级/中断/降级测试 | 三处全改并有 `KnowledgeSchemaVersionTest` 11 例：新库落 CURRENT；只有 legacy marker 时也归一化写版本并清 marker；`isBeyondSupported` + 只读集合让过新的库转只读（所有写路径统一拒，读仍可用）；`safeKbFile` 让**所有 String 入口**都过 `KbName`/`KbRelativePath`，并补掉盘符绝对路径与反斜杠两个真漏洞（旧校验只挡 `/`），canonical 失败按拒绝处理而非抛穿。本轮又把整套版本探测与旧库迁移搬出仓库成 `KnowledgeMigrator`，并补 10 条迁移行为用例（v1 搬迁、已编辑文件不覆盖、阶段标签双写、plan 归档截断、未知格式原样保留、重跑无害）。v1.3.1 真机升级/降级测试仍待设备 |
| S2-07 | 错误类型可从字符串前缀反推；生产大量硬编码中文；历史工单编号留生产；四套机制并存；无全仓 CancellationException 审计与 lint 防回归 | **五条全做完，逐条可核**：<br>① 字符串前缀推类型：`CONFIG_ERROR_PREFIX` / `CONFIG_ERROR_MESSAGES` / `isConfigError` / `stripConfigPrefix` / `ReplyFailureKind.fromErrorMessage` 全部删除，`StreamEvent.Error` 从 `(String, String)` 改为 `(ProviderFailure, String)`；分类只发生在 `DeepSeekRepository.classifyApiError` 这一个供应商边界出口，`ReplyFailureKind` 上不留任何"从消息反推"的 API（用例用反射断言这些方法名不存在，防止回流）。原先 6 处 `startsWith("PARAM_UNSUPPORTED:")` / `isConfigError(msg)` 判定改为读 `thinkingParamRejected` / `isConfigProblem`，`grep` 实测生产码 **0 处**。补 `InsufficientBalance`/`ContentFiltered`/`ContextTooLong`/`ServerBusy`/`InvalidAddress` 五个 kind，回复链路不再把它们压成"生成失败，请重试"。<br>② 硬编码中文：面板主操作/页头/加载态/错误文案/首页行迁入 `strings.xml` 并被组件真调用，`values-en/` 双向核对。知识库两个页面仍是中文字面量（本轮只动职责，没顺手改文案），如实记为未完成。<br>③ 工单编号：`app/src/main` 注释与字面量里的编号 **913 处 → 0**（880 注释 + 33 字面量，用同一把尺在清理前后各量一次），70 个文件、988 行改写、**逐文件行数 0 变化**、只动注释列与字符串前缀。`scripts/strip_ticket_ids.py --check` 进 CI，注释/代码/字面量任一残留即红；已用注入样例证明该门禁真会失败。<br>④ 四套机制并存：Job 字段与 boolean 真源已删（见 S2-02），本轮又拔掉建库那一套双台账。<br>⑤ 取消审计：`scripts/audit_cancellation.py` 扫 `app/src/main` 全部 168 个吞异常站点，按"块内/紧邻/同一条 try 链是否显式放行 CancellationException + 段内是否真有挂起点"分四档：PROTECTED 53、WAIVED 2、SUSPEND-FREE 113、NEEDS_REVIEW **0**。审计挖出 9 处真实吞取消并逐个修（冻结输入用 `runCatching` 包挂起读、nextRound 的 `catch(Throwable)`、画像写入兜底、备份防抖、回滚兜底伪装成业务失败、导入导出把取消报成失败等）；`--check` 进 CI，同样做过注入失败验证。完整逐站点清单落在 `docs/CANCELLATION-AUDIT.md`。 |

### §7：第三阶段

| 领域 | 本轮 |
|---|---|
| P3-01 | Panel 死 API 清掉（`draftText`/`onSaveToKb`）；主链状态收进单一 `ReplyUiState` |
| P3-02 | 未新增改动（报告未点必改项；Provider 表单 `remember` 问题不在本轮清单里） |
| P3-03 | §7.1 五个反例逐个修：页头行/收起 24→48、三段切换 20dp 视觉包进 48dp 命中盒、结果工具入口 28→48（补 contentDescription）、生成按钮 40→48 且 **clickable 移到 padding 之前**（旧顺序等于自削热区）、Home 尾部 32→48；补语义标签与 testTag；`AppDimens.TOUCH_TARGET_MIN_DP` 统一常量；`ProductionUiContractTest` 把"clickable 必须排在视觉内缩之前"写成断言。TalkBack/字体/窄屏截图未执行 |
| P3-04 | reply 流式解析不再 `rawBuffer.toString()` 全量重扫：新增 `IncrementalJsonObjectScanner` / `IncrementalSingleObjectScanner`，只吃新增文本、跨 chunk 保留字符串/转义/深度/阶段状态；`PartialTipsParser` 的过程全局扫描器改成每请求一个（原实现跨请求共用会串数据）；生产 `rawBuffer.toString()` 现存 0 处（另 2 处命中只是我写的注释）。修 scanner 时发现并修掉两个真缺陷：`stepElements` 用累积输出当"有进展"导致 `while(progressed)` **死循环把 JVM 挂死**；`stepBrace` 没吃掉 `"key"` 后的冒号导致 `extractKeyObject` **恒为 null**。Macrobenchmark 数据未产出 |
| P3-05 | 脆弱 blocklist 删除，改 `CapturePolicy`：**默认 fail-closed allowlist**（没点选任何 App = 一个都不采）；命中 allowlist 仍二次拒绝系统窗口、`isPassword` 节点、支付/银行/密码管理/浏览器/医疗/企业 SSO 类别；关键词按包名**段**匹配（旧实现整串 `contains("pay")` 会把 `com.payne.chatapp` 误杀）；新增「捕获范围」选择页 + `SecurePrefs.captureAllowedPackages` + Manifest `<queries>`；`CapturePolicyTest` 11 例覆盖裁决矩阵。threat model 见 `docs/PRIVACY-THREAT-MODEL.md`；抓包未执行 |
| P3-06 | schema/WAL 正确性缺陷见上；升级 job 已真写夹具、真断言数据、真用签名 R8 候选、FATAL 真失败（脚本层）；设备上未跑 |
| P3-07 | 见 §3 表；证书连续性、SBOM、upgrade required、证据索引都在 workflow 里；`emit_release_evidence.sh` 是门禁不是排版器（引用件缺失/不一致即失败） |
| P3-08 | README"网络抓包证明"改为如实说明依据与未执行项；BENCHMARK 头部改掉"hash：见目录"（换成 `asset_hashes.sh` 的组合 SHA-256 + `docs/prompt-assets.lock`），并明确写出"2026-09-03 跑在 bf4aa8d 上"这条时间线不可信、当时提交号未被记录、缺哪些复现件；README_EN 补上同等的安全声明表与 allowlist 描述，双语文档重新对齐 |

### §8 三步返工：第一步逐条

1 修 `chain` 编译错且 WAL payload 完整化 → **做**
2 retry 抽成本地可执行脚本，workflow 只调用 → **做**
3 修 `ReplyPrimaryActionsTest`（JUnit 断言、断 disabled、按真实文案/semantics 找停止） → **做**（停止入口用 `GENERATE_STOP_TEST_TAG` 锚定，因为生产 LOADING 实际渲染"分析对话 · Ns 点击停止"）
4 新增真实 Compose/Service 主链（挂生产 Panel、点空态蓝字确认 0 次 Engine 调用、加消息、点真按钮、注入 fake Provider） → **代码与编译做，执行未做**
5 覆盖成功/无 Provider/401/超时/解析失败/停止/快速双击/旧请求迟到/Service destroy → 同上
6 测试必须真跑，空 XML 即失败，不允许 warn 后放行 → **做**（`assert_artifacts.sh`，含声明数≠实际数即失败）
7 保留正确入口与按钮，不改 proactive/polish prompt → **做**：本轮未修改 `proactive.md` / `polish.md`，资产 hash 与 `docs/prompt-assets.lock` 一致可证
8 锦囊固定真实请求基准 → **脚本做，数据未产**

第一步门禁（同 SHA compile+unit+lint+R8+connectedDebugAndroidTest 全绿 + 可安装 APK + 真实主链不崩）：
前四项与 APK 本机已绿，**connectedDebugAndroidTest 因无设备未跑**。

### §8 三步返工：第二步逐条（删架构双轨 + 修数据一致性）

| # | 报告原句 | 处置 | 可核对证据 |
|---|---|---|---|
| 1 | Engine 改为当前协程内的 suspend/Flow，不再收外部 scope 并启第二个 Job | 做（限报告所指的 GenerationEngine） | `GenerationEngine` 四个公开入口全是 `fun …Stream(…): Flow<…>`，签名里 `scope: CoroutineScope` 与 `: Job?` 各 0 处；`GenerationEngine.Callbacks` 已删<br>同类模式在 `KnowledgeTriggerCoordinator` 里也有一份，逐字复核时先记为未完成、随后一并拔掉（见 6.2），现在"全仓单 Job owner"为真 |
| 2 | 所有事件源头带 requestId，单一 reducer，删 Reply Callback 写状态路径 | 做 | `ReplyEvent` 全部携带 requestId；唯一写入口 `dispatchReply → ReplyReducer.reduce`，被拒时返回同一对象。生产码 `Callbacks` 的代码级命中 **0 处**（余 3 处全是解释性注释），`interface *Callbacks` 由 `SingleOwnerContractTest` 禁死；后台三引擎同样改成自带 kbName 的 typed 事件 |
| 3 | coordinator 提供"创建并启动"的原子 API 或注册失败立即 cancel；同一把锁；六类操作全注册 | 做 | `CoroutineStart.LAZY` + 锁内注册，被拒即 `job.cancel()`；`ForegroundOperationCoordinatorTest` 11 例，含"被拒的 start 一次都不跑 body" |
| 4 | 删 ViewModel 手写 guard、Job 真源与多 boolean；停止按 lease | 做 | `generateJob/…/profileRegenerationJob`、`_isProactive` 等 6 字段删除；`stopGeneration()` 只停 REPLY；建库侧本轮再拔一套双台账 |
| 5 | `GenerationInput` 直接进 Prompt/Provider；冻结 KB revision、资产 hash、完整非敏感 Provider 身份 | 做 | `ProviderIdentity(ticketId/hostHash/model/thinkingMode)`，Engine 只按冻结 ticketId 取配置，对不上发 `ProviderChanged` 并失败；`contentRevision` 是真实文件 SHA-256 而非 turnCount 近似 |
| 6 | 重做 round commit：稳定 roundId、完整 typed event、每投影水位或单锁 append-only log；禁 recent marker 当跨文件提交标记 | 做 | `stableRoundId` 派生自 kb+消息 ID；六投影各有水位；跨文件幂等看 committed 清单，recent marker 降级为文件内去重 |
| 7 | 对 topic/recent/scene/plan/count 每个写入边界故障注入，重启后校验 exactly-once | 做 | `RoundCommitJournalTest` 17 例逐个边界注入，断言恢复后与一次跑通逐文件等值 |
| 8 | 新库直接写 CURRENT；未来 schema 明确拒绝只读；边界全面改 `KbName/KbRelativePath` | 做 | `KnowledgeSchemaVersionTest` 11 例 + `safeKbFile` 覆盖所有 String 入口（含盘符绝对路径与反斜杠两条真实绕过）；本轮迁移实现外提成 `KnowledgeMigrator` 后再加 10 例行为用例 |
| 9 | 把 KnowledgeBase/KbEdit 的数据逻辑迁到 ViewModel/use case，删"仅写注释承认违规"的假修复 | 做（本轮） | 见 §6 S2-05 行；`UiLayerDependencyContractTest` 6 例把"ui 层不得直连 data 层"和"不得再出现只记录不修复的注释"钉成静态合同 |

第二步门禁（无双 Job owner、无无 requestId callback、无死 reducer、无未注册操作、无裸路径边界、WAL 故障矩阵全绿）：
本机逐条为真；`KnowledgeRepository` 行数仍大于基线这一事实单独记在 4.1，不用门禁通过来掩盖。

### §8 三步返工：第三步逐条（体验、无障碍、隐私、发布工程）

| # | 要求 | 处置 |
|---|---|---|
| 1 | 页面 × 状态 × 主操作 × Back 矩阵 + 截图基线 | **未做**（需设备/截图，见 §0 第 1 条） |
| 2 | 自定义 clickable 全 ≥48dp，补语义，跑 TalkBack/2.0x/360dp | 数值部分做并有静态合同；三项设备验收未跑 |
| 3 | 用户文案迁 resources 且真被调用；中英同步 | 核心组件已迁并双向核对；知识库两个页面仍是字面量，未做 |
| 4 | 真增量 parser + Macrobenchmark/Perfetto/帧时间/内存/长流 | parser 做并有 2 个真缺陷修复记录；性能数据未产出（§0 第 3 条） |
| 5 | allowlist 化、二次拒绝、默认 fail-closed | 做（`CapturePolicy` + 选择页 + 11 例） |
| 6 | threat model、出口清单、可复现抓包与脱敏结果 | 文档与脚本做，抓包未执行（§0 第 5 条） |
| 7 | 升级 job 真下载校验 v1.3.1、真写夹具、覆盖安装签名候选、断言数据与主流程、FATAL 必失败 | 脚本与门禁做，设备未跑（§0 第 6 条） |
| 8 | Release 依赖 verify+ui-test+upgrade-test、比对已公开证书、验新装/覆盖/版本/SHA-256/SBOM | 做（`wait-ci` 等三项；`verify_signing_continuity.sh` 对过线上 v1.3.1） |
| 9 | BENCHMARK 固定 commit、真实资产 SHA-256、脚本、锁文件、原始数据与重放命令 | 头部改为实测组合 hash 并写明不可信时间线；真实费用数据未产出（§0 第 4 条） |

## 2. 删除清单（报告 §9 第 2 项要求）

| 删掉的东西 | 位置 |
|---|---|
| `DomainEvent.kt`（含旧的局部 `ReplyReducer`） | 整文件删除，被 `GenerationEvents.kt` 取代 |
| `GenerationEngine.Callbacks` 接口（约 30 个方法）及其全部 ViewModel 实现 | 删；四流程改为事件冷流 |
| `generateReply(input, scope, callbacks): Job?` 等 4 个"收 scope + 返 Job"的入口 | 删；Engine 不再启动协程 |
| ViewModel 的 `generateJob` / `counselingJob` / `suggestJob` / `proactiveJob` / `rewriteJob` / `profileRegenerationJob` | 删；coordinator 是唯一 Job 真源 |
| ViewModel 手写 guard（`isActive(...)`、`_isProactive.value`、`_isCounseling`、`_isSuggesting`、`rewriteJob?.isActive`） | 删；改为 `operationCoordinator.start(...)` 返回值 + 派生 StateFlow |
| `stopGeneration()` 里 `stopByType(REPLY)+stopByType(PROACTIVE)+stopByType(REWRITE)` 三连停 | 删；只停 REPLY |
| 独立可写的 `_result` / `_replyRequestState` / `_streamingCoreText` / `_streamingSchemes` | 删；从 `_replyUi` 派生 |
| 流式 `StringBuilder + 50ms 定时器`绕过状态源的写法 | 删；改为 dispatch 层合并事件后再归约 |
| 手写 JSON 序列化/反序列化（`serializeEvent`/`extractStringField`/`unescape`） | 删；改 kotlinx.serialization |
| `beginCommit`/`markCommitted`/`rollback` 三段裸 API | 删；改 `commit/recover` 事务体 |
| `PartialJsonObjects` 的"每 50 字全量重扫"路径与 `PartialTipsParser` 全局扫描器 | 删；改增量扫描器（一次性解析入口保留供终态与单测用） |
| `sensitiveAppPrefixes` / `sensitiveAppKeywords` / `isSensitiveApp` | 删；改 `CapturePolicy` allowlist |
| `ReplyPrimaryActions.draftText`、`ResultArea`/`ResultUtilityTrigger.onSaveToKb` 死参数链 | 删；有防回流用例 |
| CI 里 `continue-on-error: true`、`|| true`、`|| echo`、`if-no-files-found: warn` | 删；两份 workflow 现为 0 处 |
| `RoundCommitJournal` 里只写注释不写数据的 `WRITING` 伪阶段 | 变成真落盘的阶段 |
| `KnowledgeBaseActivity` / `KbEditActivity` 里直接 `by inject()` 的 `repo` / `deepSeek` / `securePrefs` 三个字段 | 删；两个 Activity 现只持有 `by viewModel()` |
| `KnowledgeBaseActivity.kbCreationInProgress` boolean + `onboardingJob` 两套建库台账 | 删；只剩 `creationJob?.isActive` 一个真源 |
| `KnowledgeBaseActivity` 的 `kbReloadToken`（mutableIntStateOf）+ `version`/`kbs`/`active` 三处 `remember` 局部状态 | 删；列表状态收进 `KnowledgeBaseViewModel.state: StateFlow<KbListState>` |
| `KnowledgeBaseActivity` 私有方法 `zipKbFolder` / `unzipToKnowledge` / `parseOnboardingResult` / `parseSection` / `autoKbName` / `readEngineAsset` 与内嵌 `ParsedOnboardingResult` | 移出 UI 层：`KbArchiveTransfer` / `OnboardingResultParser` / ViewModel（可离线单测） |
| `KnowledgeBaseActivity` 里 `if (ok) { } else { }` 两个空分支（删除结果被咽下） | 删；删除失败改发 `KbEvent.DeleteFailed` 并给提示 |
| `PendingNoProviderDone: (() -> Unit)?` 载荷（把回调存字段里） | 删；二选一确认直接调 `createEmptyKb()` |
| `CONFIG_ERROR_PREFIX` / `CONFIG_ERROR_MESSAGES` / `isConfigError()` / `stripConfigPrefix()` | 删；分类只在 `classifyApiError` 一处 |
| `ReplyFailureKind.fromErrorMessage(String)` | 删；反射用例断言该方法名不得回来 |
| `StreamEvent.Error(message: String, partialText: String)` | 改为 `(failure: ProviderFailure, partialText: String)`；message 只作展示，控制流走 kind |
| `app/src/main` 注释与字面量里的 913 处历史工单编号（含写进归档文件的 `<!-- F08 plan migration backup -->` 标记） | 删；`strip_ticket_ids.py --check` 防回流 |
| 生产注释里的"审计技术债：此处违反 SRP/DIP""修复方向：…"式自述 | 删（本轮清掉 2 处，其余同类表述随编号一并清理）；`UiLayerDependencyContractTest` 防回流 |
| 吞取消的 9 处兜底（`runCatching { 挂起读 }`、`catch (Throwable)` 包住 nextRound 等） | 改为显式放行 `CancellationException` |
| `LoveBrainViewModel.loadFeedbackCases(onResult)` / `exportFeedbackMarkdown` / `exportFeedbackJson` | 删；全仓（含 androidTest）0 调用方，案例列表走 `SetupViewModel` 的状态流 |
| ViewModel 私有的 `_feedbacks` 与 `_currentFeedbackCase` 两个 StateFlow | 删字段；真源进 `FeedbackCaseController`，VM 只转发只读流（静态合同防回流） |
| 点踩案例里写死的 `promptVersion = "v1.3.2"` | 删；改为生成时冻结的回复链路四资产内容指纹（`replyPromptAssetHash`） |
| 两份各写一遍的撤销实现（`undoCorrectionFromCenter` 与 `undoMemoryCorrection`） | 合并为一份实现，只差"无生成上下文时是否回落到激活库"；顺带补上卡片那条路原本静默的失败提示 |
| `KnowledgeRepository` 里的 `schemaTooNewKbs`、`detectSchemaVersion`、`writeSchemaVersion`、`migrateIfNeededUnlocked`、`migratePlanDataIfNeededUnlocked`、`STAGE_MIGRATION`、`MigratablePlanItem` | 移出为 `KnowledgeMigrator`；仓库只留持锁壳与转调 |
| 迁移代码里从没被读过的 `newUnderstand` 局部变量，以及 `KbStorageAccess` 上没人调用的 `readUnlocked` / `codec` 两个接口成员 | 删；抽类时不留"以后可能用得上"的死 API |
| ViewModel 里 `/** 改写任务 Job——与前台生成共用任务管理 */` 这条注释 | 删；它描述的字段在 S2-02 就被删了，属于"注释比代码活得久" |

## 3. 同一 SHA 的 required checks（§9 第 3 项）

本轮返工的提交链在上面第 0/1 节。**GitHub 上的 run 需要推送才会产生**；我没有推送
（推送/打 tag/发 Release 都属于对外动作，报告 §1 的独立放行意见也要求先停止对外宣称）。
因此本节给的是**本地等价门禁**的真实结果，以及推送后应当变绿的 job 名：

本地（同一工作树，`JAVA_HOME` = Temurin 21，`ANDROID_HOME` = D:/Android/Sdk，全部 `--offline`）：

| 命令 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `:app:compileDebugUnitTestKotlin` + `:app:testDebugUnitTest` | 见第 4 节计数，0 failures / 0 errors / 0 skipped |
| `:app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL（报告里红的就是这一步） |
| `:app:lintDebug` | 0 error |
| `:app:assembleRelease` | 产出签名 + R8 APK |
| `scripts/check_apk_metadata.sh` | OK：`com.lovebrain.app` / versionCode 9 / 1.4.0-rc1 / minSdk 26 / targetSdk 35 / launcher `ui.SetupActivity` / 2 808 376 bytes；R8 mapping 350 911 行、4485 条顶格重命名条目；`apksigner verify` 出证书 `c2986640bce6…2f7d`。**SHA-256 不写死**：同一份源码本机连跑两次 `assembleRelease` 得到两个不同值（大小一致，差在 zip 内时间戳，本轮这次是 `da04c7c6…c93d`），所以发布候选的 hash 只认 CI 那一次构建产出的 `dist/apk-metadata.txt`，本机数字只作当次证据 |
| `scripts/verify_signing_continuity.sh` | OK：证书 SHA-256 `c2986640bce6…2f7d`，与已公开 v1.3.1 一致（同时用 `gh api` 对过 release 正文与 asset digest） |
| `scripts/license_scan.sh` | OK：124/124，SBOM 产出且可 JSON 解析 |
| `scripts/assert_artifacts.sh` | OK：真实 906 例 / 108 套件 / 0 failures / 0 errors / 0 skipped；空目录 → 1；篡改 XML 声明数 → 1 |
| `scripts/asset_hashes.sh --check` | OK：prompt 资产未漂移（`27abe25529deb47cb40366961998597c556c5184416fa038ff3ece81a2d6eb5c`） |
| `scripts/audit_cancellation.py --check` | OK：168 站点（PROTECTED 53 / WAIVED 2 / SUSPEND-FREE 113），NEEDS_REVIEW=0；注入吞取消样例 → exit 1（已验证会红） |
| `scripts/strip_ticket_ids.py --check` | OK：注释/代码/字面量三处工单编号均为 0；注入 `P0-9: probe` → exit 1（已验证会红） |
| `bash -n`（`scripts/*.sh` 16 个 + `scripts/lib/*.sh` 2 个，共 18 个）与 `ast.parse`（3 个 py） | 全部通过，0 语法错误（`find . -name '*.sh'` 实测 18 个，逐个 `bash -n`） |
| `:app:lintDebug` / `:app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| `yaml.safe_load` 两份 workflow | 通过；`|| true`/`continue-on-error`/`|| echo` 计数 0 |

推送后 CI 的 required checks 应为：`verify`、`ui-test`、`upgrade-test`，Release 三者都等。
其中 `ui-test` 与 `upgrade-test` **本机无法执行**（无 system image、无设备）。

## 4. 测试数量、失败数与工件（§9 第 4 项）

权威口径：`python` 解析 `app/build/test-results/testDebugUnitTest/*.xml` 汇总，
并用 `scripts/assert_artifacts.sh --min-tests auto` 复核"声明数 == 实际 `<testcase>` 数"。

| 阶段 | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| 报告被审提交（8745a3d 之前）基线 | 85 | 675 | 0 | 0 | 0 |
| WAL 重写后 | 86 | 692 | 0 | 0 | 0 |
| 架构统一 + 测试适配后 | 89 | 724 | 0 | 0 | 0 |
| +协调器合同用例与 requestId 绑定 | 91 | 747 | 0 | 0 | 0 |
| +S2-05 职责拆分（归档/解析/ViewModel/分层合同） | 95 | 788 | 0 | 0 | 0 |
| +S2-07 错误类型改造（净 +1 条：删 10 条前缀推断用例，补 12+4 条 typed 用例） | 95 | 788 | 0 | 0 | 0 |
| +SetupActivity/TriggerCoordinator 收口（9 条引导判定 + 4 条单 owner 合同 + 分层两条新规则） | 97 | 803 | 0 | 0 | 0 |
| +Koin 生产图 JVM 解析（`AppModuleGraphTest` 3 例） | 98 | 806 | 0 | 0 | 0 |
| +仓库文本算法外提 `KbTextOps`（16 例纯函数用例） | 99 | 822 | 0 | 0 | 0 |
| +`KnowledgeMigrator` 拆出（旧库迁移行为基线 10 例，含两条结构合同） | 100 | 832 | 0 | 0 | 0 |
| +`FeedbackCaseController` 拆出（14 例）+ 删 3 处死 API | 101 | 846 | 0 | 0 | 0 |
| +`MemoryCorrectionPolicy` 拆出（6 例）+ 撤销失败可见（VM 侧 +5 例） | 102 | 857 | 0 | 0 | 0 |
| +改写台账 `RewriteLedger` 与改写 prompt 外提（8 + 6 例） | 104 | 871 | 0 | 0 | 0 |
| +方案卡读/改映射收口 `ReplyPatch`（9 例） | 105 | 880 | 0 | 0 | 0 |
| +两处生成指纹外提 `GenerationFingerprints`（9 例） | 106 | 889 | 0 | 0 | 0 |
| +PromptBuilder 拆出预算与场景链（9 + 8 例） | 108 | **906** | 0 | 0 | 0 |

本轮新增/改写的用例（逐套件读 XML，全部 0 失败；总数按最后一列为 906 例 / 108 套件）：

| 套件 | 用例数 | 钉住的东西 |
|---|---:|---|
| `KbArchiveTransferTest` | 13 | zip 导入的路径穿越、条目数超限、元数据不一致/损坏/缺失、双顶层、同名碰撞、坏包不留半截库、暂存必清 |
| `OnboardingResultParserTest` | 8 | 五段 marker 切分、缺段即降级（不许把降级报成画像成功）、乱序 marker 不吞正文 |
| `KnowledgeBaseViewModelTest` | 15 | 建库事务单 owner、四种结果各发各的事件、取消不落盘、未配置供应商不碰仓库、导入后修 active、导出可回读 |
| `UiLayerDependencyContractTest` | 6 | ui 层不得直连 Repository/SecurePrefs，也不得 `viewModel.securePrefs` 穿透；Activity 必须 `by viewModel()`；不得再出现"审计技术债/修复方向"式注释 |
| `SetupViewModelOnboardingTest` | 9 | 引导可见性四条件与"补完成标记"的写动作，含缺 Context 时的降级 |
| `SingleOwnerContractTest` | 4 | 不得再有 Callbacks 回写接口、不得有函数返回 Job、scope 参数与 domain 层 launch 只许协调器、两个引擎必须是 Flow |
| `AppModuleGraphTest` | 3 | Koin 生产图在 JVM 上真解析一次：11 个 single + 4 个 ViewModel；single 复用同实例、viewModel 每次新实例 |
| `ProviderFailureClassificationTest` | 12 | typed 分类合同：Auth/参数不支持可降级、400+thinking 不可降级、运行期错误不得误判成配置错、已归类错误不外泄英文原文 |
| `DeepSeekRepositoryErrorMappingTest` | 3 | 边界产物不再夹带内部标记；未知错误保留固定话术 |
| `ReplyFailureKindTest` | 15 | 反射断言"从字符串反推类型"的 API 不存在；kind 与文案一一对应不撞句 |
| `KbTextOpsTest` | 16 | 状态链压缩/截断、场景条目按时间戳倒序且无戳条目不丢、plan 说明行包注释幂等 |
| `KnowledgeMigratorLegacyTest` | 10 | v1 旧库搬迁到三层结构、用户已编辑文件绝不覆盖、旧阶段标签同时改 kb.json 与 warmth.md、plan 归档+截断到 10 条、未知格式原样保留、重跑无害、"知识库持久层只有一把 Mutex" |
| `FeedbackCaseControllerTest` | 14 | 点踩同步出案例且返回的就是屏上那份、取消踩清屏、素材采不到不凭空造案例、四种动作各自文案、IO 失败兜底 vs 取消上抛、promptVersion 只来自冻结输入、控制器不持 scope |
| `MemoryCorrectionPolicyTest` | 6 | 只有 MUTED+THIS_ROUND 走瞬时（遍历全部组合）、快照是副本、remove 的 true/false 决定要不要落盘、失败文案不与任何成功文案撞句 |
| `RewriteLedgerTest` | 8 | 改写压入 Loading+历史版本、正文与反馈成对压入并按 LIFO 弹回、身份互不串、弹空后 key 整个消失（不留 `key -> []`）、clearAll 一次清空两本 |
| `RewritePromptTest` | 6 | 规则区固定 6 条不含用户数据、IDEA 草稿不进请求、只带最近 6 条、空段落整段省略不留占位标题、原回复里的"忽略以上规则"只落在数据行 |
| `ReplyPatchTest` | 9 | A/B/C/D 与四个字段的位置映射逐个钉（并断"只有一条被改"）、改风格不动方向、改方向不动风格、缺位只补到目标位且读回空正文、analysis 不受影响、不认识的 tag 原样返回不抛错 |
| `PromptBudgetTest` | 9 | 不超预算时逐字节不动；超预算先裁知识段尾部、最新一条真实消息与完整 IDEA 必须留下、对话按整行 JSON 裁剪且 <chat> 围栏不切半、什么都放不下时仍给省略说明并闭合围栏、锦囊高优先段保住且不出现粘连段名、兜底截断保头保尾、lastH1Blocks 带省略标记 |
| `SceneChainInjectionTest` | 8 | 龄标注三种写法（不到1小时 / 当日 N 小时前 / 跨日带日期）、时间解析不出来记「时间未知」而不是丢弃、超过窗口整条不注入、同一条事实只注入最新版本、写入端 src/spk/subj 与旧 ⟨ids⟩ 标记都不外泄、无事实的条目与不匹配的行一律不出段 |
| `GenerationFingerprintsTest` | 9 | 输入指纹对 9 类字段逐个敏感（含消息顺序/条数/身份/角色）、length-prefix 让 "ab"+"c" 与 "a"+"bc" 不撞（同时断裸拼接确实同串）、哈希显式 UTF-8、空段固定成 `-`、锦囊 11 段逐个改都必须换指纹（含冻结的发起日） |
| `MechanismClosureTest`（本轮 +5） | 19 | 卡片撤销失败必须可见、两个撤销入口失败话术一致、撤瞬时 mute 不碰仓库、持久化纠话说清改了什么、抛异常变成提示而不是崩溃 |

工件：`app/build/test-results/testDebugUnitTest/*.xml`（102 份）、
`app/build/reports/tests/testDebugUnitTest/index.html`。
计数用解析 XML 得到，不是复制网页数字；`assert_artifacts.sh` 会额外核对
"声明的 tests 数 == 真实 `<testcase>` 数"，防止报告注水。
没有 `@Ignore`；unit 侧 **skipped=0**（`assert_artifacts.sh` 读 XML 的 skipped 列，实测 0），
也没有在 workflow 里用 `--tests` 过滤缩小范围。
全仓只有 2 处 `Assume.assumeTrue`，都在 `OverlayGenerateSmokeTest`（instrumentation）里，
条件是"FloatingService 这次能不能在 instrumentation 环境起来"，带原因文案，
不是 `assumeTrue(false)` 那种无条件跳过——起不来的设备上报 skipped，起得来的设备上是真断言。

instrumentation：`app/src/androidTest` 现有 40 个 `@Test`（5 个文件，本机 `grep -c "@Test"` 实测），
`:app:compileDebugAndroidTestKotlin` 通过，**执行数为 0（无设备）**，见 §0 第 1/2 条。

补的一处覆盖缺口：`AppModule` 里的注册是位置参数，本轮又改过 `SetupViewModel` 的构造签名，
而"注册参数与构造函数不一致 / 缺绑定"这类错误在 JVM 单测里原本**完全测不到**（要等 App 启动或 instrumentation）。
`AppModuleGraphTest` 现在把整张图 resolve 一遍，并用两种破坏方式验过它确实会红：
注释掉 `single { RoundCommitJournal(get()) }` → 用例失败；给 `KbEditViewModel` 多传一个 `get()` → 编译期即拦。
写它的时候还顺带撞出一个事实：`LoveBrainViewModel` 构造即建 `viewModelScope`（`Dispatchers.Main.immediate`），
所以 JVM 侧必须先 `Dispatchers.setMain`——这条用例能跑是有前提的，不是白拿的。

静态门禁（不是测试，但同样可执行、同样进 CI verify）：
`scripts/strip_ticket_ids.py --check` 注释/代码/字面量三处工单编号 0 命中；
`scripts/audit_cancellation.py --check` 168 站点中 NEEDS_REVIEW 0（其余三档是已放行/已豁免/段内无挂起点，
不是"没扫到"）；两者都用注入样例验证过"确实会红"（前者 `NEGATIVE-TEST exit=1`，后者 `NEGATIVE exit=1`）。

本轮最后一次全量门禁（HEAD = `74a263d`，同一棵树）：
`:app:testDebugUnitTest` + `:app:lintDebug` + `:app:compileDebugAndroidTestKotlin` + `:app:assembleRelease`
→ BUILD SUCCESSFUL（906 例 / 108 套件 / 0 failures；lint 72 issue、0 error、0 fatal）；
随后 `assert_artifacts.sh`（unit + lint）、`python scripts/strip_ticket_ids.py --check`、
`python scripts/audit_cancellation.py --check`、`bash scripts/asset_hashes.sh --check docs/prompt-assets.lock`、
`check_apk_metadata.sh` 全绿。
一个只在 Windows 上出现的坑记在这里，免得下次又被当成"门禁没输出=通过"：
仓库里 `python3` 是 Microsoft Store 的占位符，调用它 **exit 49 且不打印任何东西**；
本机跑这两条 py 门禁必须用 `python`（Anaconda），CI 的 ubuntu runner 上 `python3` 才是真解释器。

## 5. 发布判定

按报告 §1 的独立放行意见执行：**保持 `1.4.0-rc1` 开发状态，不打 tag、不建 Release、不宣称阶段完成。**

理由（不对外甩锅，对内也不自签 PASS）：
1. 第一步门禁的 `connectedDebugAndroidTest` 没真跑过——报告正是因此判 FAIL 的，我不能用同一缺口自证通过。
2. §0 现在剩 **7** 项未完成，其中 5 项（截图矩阵、instrumentation、Macrobenchmark、真机升级、抓包）
   都卡在同一件事上：**没有设备**。这不是理由的替代品，只是说明它们的共同前提。
   另外两项（真实费用基准、S2-05 上帝类）是本机就能继续做的，也照样列在 §0，没有被"等设备"这句盖掉。
3. S2-05 只算部分达成：`KnowledgeRepository` 这轮真的降到了基线以下（2118 → **1857**），
   但 `LoveBrainViewModel` 只从 2954 降到 **2678**（−9%），`TopicRecorder`(1006→991) 本轮未再动，
   我不给"职责拆分完成"这个词盖章。
4. worker 不得自签 PASS（报告 §8 第三步门禁）。这份文件是**交付给复核者的证据包**，不是通过证明。

已做完的部分（§6 S2-01…S2-07、§7 P3-01/03/04/05/07/08、§8 第一步 1-3/6-7、第二步 1-9）
逐条列在第 1 节，每条给的是命令与数字，不是形容词。

worker 不能自签的另外一面也照做：上面每个"OK"都是命令输出，不是叙述；每个"没做"都写了没做的原因和现状数字。

### 4.1 上帝类行数（报告 §6 S2-05 的表，本轮实测对照）

报告给的被审行数与本轮 `wc -l` 实测（HEAD = `74a263d`）：

| 文件 | 报告 | 上一轮 | 现在 | 变化 |
|---|---:|---:|---:|---|
| `LoveBrainViewModel.kt` | 2954 | 2941 | **2678** | −276（−9%，仍是第一行，见下） |
| `KnowledgeRepository.kt` | 2118 | 2283 | **1857** | **−426，已低于报告基线 261 行** |
| `KnowledgeBaseActivity.kt` | 1214 | 1213 | **924** | **−290**（数据逻辑与 zip 归档全部下沉） |
| `PromptBuilder.kt` | 1177 | 1199 | **968** | **−231，已低于报告基线 209 行** |
| `TopicRecorder.kt` | 1006 | 991 | 991 | −15 |
| `GenerationEngine.kt` | 828 | 678 | 686 | −142 |
| `KbEditActivity.kt` | 585 | 584 | **572** | −13（保存/读版本契约下沉） |
| `KnowledgeTriggerCoordinator.kt` | 480 | 480 | **466** | −14（删 `Callbacks` 接口与 `Job` 包装；三引擎改 suspend + 事件） |
| `SetupActivity.kt` | 139 | 139 | **124** | −15（引导判定与 filesDir 读取移入 VM） |

承接方（新增，全部可 JVM 单测）：`KnowledgeMigrator.kt` 413、`FeedbackCaseController.kt` 177、
`RewriteLedger.kt` 87、`KbTextOps.kt` 121、`RewritePrompt.kt` 80、`MemoryCorrectionPolicy.kt` 81、
`ReplyPatch.kt` 68、`GenerationFingerprints.kt` 96、`SceneChainInjection.kt` 96、`PromptBudget.kt` 168；上一批还有
`KnowledgeBaseViewModel.kt` 324、`KbArchiveTransfer.kt` 138、`KbEditViewModel.kt` 53、
`OnboardingResultParser.kt` 51、`ProviderFailure.kt` 50。
即仓库减掉的 426 行没有消失，而是搬到了可单测、可离线跑的位置——
这句话只在"位置变了、能被测到"的意义上成立，不等于职责已经拆干净。

`LoveBrainViewModel` 剩下的块按节标记实测（同一把尺：节标记到下一个节标记的行距，HEAD = `63bcb48`）：
单条改写 228、Trigger 事件落点 188、今日锦囊 155、下一轮（存 KB + 清空）149、
流式生成 142、事件归约 138、生成历史与版本回退 137、持续意图 134。
本轮从这台类里搬走的是：赞踩/点踩案例、纠正判定与文案、改写台账与改写 prompt 拼装、
方案卡位置映射、两处生成指纹五块；**报告表格里这一行仍未达标**，
剩下的 228 行改写链路（coordinator 租约 + 请求构建 + 结果回写）与 155 行锦囊流程是下一刀。

结论：报告点名的两个"只写注释不整改"的 Activity 这次真改了，
且 `app/src/main` 里四个 data 层类型在 ui 包的出现次数实测为 0（`UiLayerDependencyContractTest` 锁住）。
`KnowledgeRepository` 这次真的降到了基线以下（两条结构合同钉住：持久层只有一把 Mutex、
迁移器不得自己加锁）。报告点名的七个文件里，行数**现在全部低于**它在 `286c9406` 上测到的值
（2954/2118/1214/1177/1006/828/585 → 2678/1857/924/968/991/686/572）。
但这句话只到"行数"为止：ViewModel 仍有 2678 行、还同时管着回复状态机、轮次提交、
改写链路、锦囊与意图，S2-05 只算**部分达成**，不写成已完成。
报告说"记录技术债不等于修复技术债"，本轮不能一边批评它一边犯同样的错。
---

## 6. 第二轮逐字复核：复核动作本身又抓出了什么

按要求"完成后重新根据文件一个字一个字复核"。这一轮的目标不是复述上面写过的话，
而是**拿报告当尺子重新量一遍当前树**，并把上一版本文件里写错的地方改过来。

### 6.1 上一版验收包自己写错的地方（已改）

| 上一版写的 | 实测真相 | 处置 |
|---|---|---|
| `scripts/check_apk_metadata.sh` → OK | 该步当时**从没在我本机跑通过**：R8 mapping 判定用 BRE，`'…]+ -> '` 里的 `+` 是字面加号，任何真 mapping 都匹配不上 → 门禁必红 | 已修（`-E` + 要求至少一条顶格类行左右不同，能识破 `-dontobfuscate` 的自映射），并双向实测：真 mapping exit 0 / 人造自映射 exit 1。提交 `0da80d5` |
| §4 表 "结果 747 / 91 套件" | 本轮改动后实测 **788 / 95 套件 / 0 failures** | 已按 XML 解析重填，并逐套件列出新增来源 |
| §4 "instrumentation 44 个用例" | `app/src/androidTest` 实测 **40 个 `@Test`（5 个文件）** | 已改成实测值 |
| §2 删除清单里 "`Callbacks` 全删" | 全仓 `Callbacks` 字样仍有 13 处命中，其中 **0 处在回复链路**，其余是 `KnowledgeTriggerCoordinator.Callbacks` 与两处解释注释 | 第二步 1/2 条改成"限 GenerationEngine 完成"，并把 TriggerCoordinator 那份列为 §0 第 8 条未完成 |
| §8 第二步"1-9 全做" | 第 9 条（Activity 迁 ViewModel）此前被记为未做；本轮做完 | 拆成逐条表格，每条给可核对证据；同时对"全仓单 Job owner"这类说满了的话收回 |
| APK SHA / size | 旧值 `49a0b9d9… / 2 802 248` 已失效 | 重新解析 APK：`d81e3ef1… / 2 803 568`（本轮拆类后再解析是 `da04c7c6… / 2 808 376`，见第 3 节——这类数字每轮都必须重读，不能沿用上一次的） |

### 6.2 复核中新发现的、报告没点名的两处（先记账，随后已拔掉）

| 位置 | 当时的问题 | 现在的处置 |
|---|---|---|
| `domain/KnowledgeTriggerCoordinator.kt`（旧 78/94/128/247/469 行） | 后台三引擎是"外部传 `CoroutineScope` + 自启 `Job` + 6 方法 `Callbacks` 反向写 ViewModel StateFlow"，等于第二套 Job owner | 已改冷流：对外只剩 `triggerEvents(kb)` / `profileRefreshEvents(kb)`，结果用 typed `KnowledgeTriggerEvent`（五种，均自带 originating kbName），VM 侧唯一落点 `applyTriggerEvent`。实测本类 `scope: CoroutineScope` 参数 0 处、返回 `Job` 0 处、生产码 `interface *Callbacks` 0 处；新增 `SingleOwnerContractTest` 4 条静态规则锁死（含"domain 层只有协调器可以 launch"），并用注入的 `ZzProbe` 验证其中三条确实会红 |
| `ui/SetupActivity.kt` | `by inject()` 取 VM 不经 `ViewModelStore`，配置变更后 VM 内存态丢失；另有 9 处 `viewModel.securePrefs…` 伸手进 VM 拿仓库 | 已改 `by viewModel()`；引导判定与"补完成标记"整体移入 `SetupViewModel.shouldShowOnboarding()/completeOnboarding()`，`securePrefs` 改 private；`UiLayerDependencyContractTest` 从 4 条扩到 6 条（属性穿透即红、VM 用 `by inject()` 即红），且第一条初版按文件名筛 Activity 被自己的负向用例躲过，改成按类名与属性同时判后才抓到 |

这两处都是"按类型名扫分层"抓不到的形态——**穿透字段**与**第二个 Job owner**。
把它们写进静态合同而不是只改一次，是为了让同类写法下次一进来就让 CI 红。

### 6.3 报告逐节复核对账结果（当前 HEAD）

| 报告节 | 该节的每一条可核对断言 | 现在的实测 |
|---|---|---|
| §1 六条事实 | 编译、CI run #39、retry 语法、无候选 APK、Release 停在 v1.3.1、"注释声称完成而实现未闭环" | 前五条分别由 compile/`--offline` 全门禁/APK 产物/未打 tag 处置；最后一条本轮清掉三处（S2-05 注释式技术债、S2-07 前缀推类型、工单编号），另在 6.2 补记两处同类新发现 |
| §2 表格 + 七个不给 PASS 的点 | 入口方向、只切模式 0 调用、JUnit 断言、disabled 断言、停止文案锚定、死 API、停止范围、48dp | 逐项：`OverlayGenerateSmokeTest:194,205` 断言 `requestCount==0`；`ReplyPrimaryActionsTest:130` `assertIsNotEnabled()`、:220 正则匹配真实 LOADING 文案；`ReplyPrimaryActions` 无 `draftText`、`ResultArea` 无 `onSaveToKb`；`stopGeneration()` 只 `stopCurrent(REPLY)`；48dp 见 6.4 |
| §3 九个门禁 + 八条伪门禁 | 见上一版第 3 节表 | 全部落到 `scripts/` 真脚本，两份 workflow 里 `\|\| true` / `continue-on-error` / `\|\| echo` 计数 0；引用的 15 个脚本全部存在 |
| §4 worker 自报 11 行 | 逐行 | 见 §6 S2 各行与第 1 节；本轮把"注释/提交信息声称完成、实现未闭环"的三类清零 |
| §5.1 破损用例 | 前置条件、真按钮、fake Provider、单请求、按 lease 停止、旧请求不覆盖 | `OverlayGenerateSmokeTest` 重写版先加消息再断言 `ProviderMissing` 文案与 `requestCount==0`；**执行仍需设备** |
| §5.2 锦囊六条 | 真实费用、指纹与注释一致、prompt hash、日期冻结、requestId、A/B 证据 | 指纹 KDoc 现在逐条列出实际输入并明确"不谎称覆盖温度"；`currentPromptVersion()` = `assetHashOf(SUGGEST)`；日期在发起时冻结；suggest 有 requestId 归属判定；真实费用与 A/B **未产数据**（§0 第 4 条） |
| §6 S2-01…S2-07 | 见第 1 节 §6 表 | 七项均有可执行证据；S2-05 记为部分达成 |
| §7.1 五个 48dp 反例 | 逐个 | `COLLAPSE_HOTZONE_DP = MIN_TOUCH_TARGET_DP(48)`；三段切换 20dp 视觉包进 48dp 命中盒（`PanelHeader.kt:141-145`）；结果工具入口在 `ResultArea.kt` 内 ≥48；`GenerationActionButton` `height(maxOf(heightDp, 48))` 且 `.clickable` 在 `.padding(vertical)` **之前**（三处分支）；Home 尾部动作在 `ui/common/RowAction.kt`。全部由 `ProductionUiContractTest` 静态钉住 |
| §7.2 allowlist | 默认 fail-closed、二次拒绝、无关键词 blocklist | `CapturePolicy.decide()` 七种 Deny；`CopyCaptureService` 里 `sensitiveApp*` 已删；allowlist 为空 = 一个都不采 |
| §7.3 文档与基准 | README 抓包声明、BENCHMARK hash/脚本/原始数据 | README 改为"依据源码与依赖清单核对，抓包未执行"；BENCHMARK 头部是实测组合 hash 并写明不可信时间线；原始 JSON/真实费用 **未产** |
| §8 三步 | 逐条 | 第 1 节末尾两张逐条表（9 + 9） |
| §9 十二项 | 逐项 | 1 变更表 ✅（本文件）；2 删除清单 ✅；3 required checks ⚠ 需推送才存在（未推送，给的是本地等价门禁 + job 名）；4 测试数与工件 ✅；5 录屏 ❌ 无设备；6 WAL 故障矩阵 ✅；7 v1.3.1 升级断言 ⚠ 脚本+门禁 ✅ / 真机 ❌；8 APK SHA/version/指纹 ✅；9 TalkBack/2.0x/360dp ❌；10 真实费用 ❌；11 抓包 ❌（威胁模型 ✅）；12 文档 diff ✅ |
| §10 结语四个事实 | 无法编译 / 关键测试没跑 / 架构双轨 / 伪门禁与过度声明 | 第一条已解；第二条本机可跑的全跑、需设备的如实标未跑；第三条 Reply/coordinator/建库侧已清，TriggerCoordinator 一处记为未完成；第四条清了三处伪门禁并新修了本文件自己写错的 `check_apk_metadata` 判定 |

### 6.5 第三轮（继续做 S2-05 时）复核又抓出自己五处

这一轮做的是"把上帝类里的职责搬出来"，抓出来的错全在**新写的东西自己带的**：

| 我自己写的哪一处 | 错在哪 | 处置 |
|---|---|---|
| 本文件 §1 与 §3 表里的"取消审计 167 站点 / PROTECTED 52" | 凭上一轮的记忆写的，生成件 `docs/CANCELLATION-AUDIT.md` 当时就是 **166 / 51**（表格行数与"合计"行自洽）——我在批评别人"凭自述写表"的同时自己写错了一个数 | 改成当次实测；本轮新增两处显式放行后现在是 **168 / 53**，数字来自 `audit_cancellation.py --check` 输出而不是我脑子里的数 |
| 本文件 §5 理由第 2 条"§0 现在剩 9 项…第 8、9 两条…" | §0 表格在被记的两条做完后只剩 7 行，正文没跟着改，指向两条不存在的行 | 改成 7 项，并写明另两项本机可做、没被"等设备"盖掉 |
| `KnowledgeMigratorLegacyTest` 里"持久层只有一把 Mutex"的门禁 | 正则写成 `=\s*Mutex\(`，注入 `private val probe = kotlinx.coroutines.sync.Mutex()` 后**门禁照样绿**——它看不见全限定写法的锁 | 正则改 `=\s*[\w.]*Mutex\(`；这次是靠注入探针（而不是靠读代码觉得它对）发现的，注入 → 2 例红，撤掉 → 10 例绿 |
| `FeedbackCaseControllerTest` 第一版用 `supervisor.children().any { it.isCancelled }` 断言取消信号 | 完成的子 Job 会从父的 children 里摘掉，`children()` 是空表：`none{}` 恒真、`any{}` 恒假。一条**永远通过**的断言被写进了声称"钉住取消语义"的用例 | 改成直接调 suspend 出口断异常类型（`persistCase` 抛 `CancellationException`），同时把控制器改成不 launch、不收 scope——这一步是被 `SingleOwnerContractTest` 拦下来才做的，不是我先想到的 |
| 本机用 `python3` 跑两条 py 门禁 | Windows 的 `python3` 是 Store 占位符：**exit 49、一行输出都没有**。如果我把"没输出"当"没报错"，两条门禁就是本地假绿 | 本机一律用 `python`（Anaconda）；已在 §4 末尾写下这条坑，CI 的 ubuntu runner 上 `python3` 才是真解释器 |
| §1 的 S2-06 行写 `KnowledgeSchemaVersionTest` "13 例"、S2-02 行写协调器测试 "10 个用例" | 读当轮 XML 实测是 **11** 与 **11**：表格里的套件用例数一旦是"当时抄的"而不是"每次读的"，就会静默漂掉 | 两处按 XML 重填；并把 §4 表里所有套件计数改成用脚本从 `test-results/*.xml` 取，不再手抄 |
| `PromptBudgetTest` 的"对话按整行裁剪不能切半" | 第一版注入探针（把每行 `take(20)` 截半）**没有让这条变红**——查下去发现我的 fixture 量级根本没过预算，那段裁剪从没执行，断言只是在检查没被裁过的原文 | 先在断言前加"确实裁过"（结果里必须出现省略标记、且保留行数小于原行数），再重跑探针才见红。这是本轮第三次撞上"断言跑的分支不是被测分支"，同类问题一律用注入探针当场证伪，不靠读代码觉得它对了 |
| `RewriteLedgerTest` 里"弹空要删掉 key"那条断言 | 第一版写成 `historySize(key) == 0`——**恒真**：留下 `key -> emptyList()` 时它同样是 0，看着覆盖了其实什么都没测 | 给 ledger 加 `hasHistory(key)`（`containsKey`）并改断它；注入"弹空不删 key"的探针后该例才真的红。同一个坑本轮踩到第二次（另一次是 `children()` 恒假），所以"断言能不能被坏实现打破"当成写用例后的固定一步 |

另外两处属于"搬东西时顺手发现的旧账"，一并改掉并有用例：点踩案例里写死的 `promptVersion = "v1.3.2"`（诊断会把人指向错的 prompt），
以及两个撤销入口对同一类失败一个有提示一个静默（现在失败一律可见，`MechanismClosureTest` 新增 5 例钉住）。

### 6.4 复核用的仪器本身也验过一次

被审对象之外的东西也要能失败，否则"全绿"没意义：

- `assert_artifacts.sh`：空目录 → 1；把 `tests="5"` 只放 2 个 `<testcase>` → 1。
- `check_logcat_fatal.sh`：含 FATAL → 1；干净非空 → 0。
- `audit_cancellation.py --check`：注入 `catch (Exception)` 吞 `delay` → `NEGATIVE-TEST exit=1`。
- `strip_ticket_ids.py --check`：注入 `P0-9: probe` 字面量 → `NEGATIVE exit=1`。
- `check_apk_metadata.sh`：人造全名自映射 mapping → exit=1。
- `GenerationFingerprintsTest`：去掉长度前缀（改成裸拼接）→ 撞车用例红；锦囊指纹里删掉"冻结的发起日"一段、空段不再固定成 `-` → 各 1 例红。
- 清理器幂等：`--idempotence-check` stable；落盘前后逐文件行数不变（实测 69 个 .kt，0 变化）。
- `KnowledgeMigratorLegacyTest` 两条结构合同：给迁移器塞一把 `kotlinx.coroutines.sync.Mutex()` → 该用例红；
  把"目标非空不覆盖"的守卫去掉 → 迁移用例红（先红后撤，实测 2 failed / 10 passed 与 0 failed / 10 passed）。
- `FeedbackCaseControllerTest`：删掉 `persistCase` 的取消放行 + 把 `promptVersion` 改回写死 → 2 例红。
- `MechanismClosureTest` F14：把撤销失败改回静默 + 把写盘兜底改成往外抛 → 3 例红。
- `RewriteLedgerTest` / `RewritePromptTest`：`pop` 弹空后不再删 key、`user()` 去掉空白段守卫、
  `recentChat` 的 `takeLast` 改 `drop` → 4 例红。
- `ReplyPatchTest`：把 B 的位置映射指到 `playful`、删掉方向补位循环 → 4 例红
  （这条测的就是"抄两遍会漏一遍"的那类错，改错一处必须当场红）。
- `PromptBudgetTest` / `SceneChainInjectionTest`：跳过知识段尾部裁剪、对话按**字符**切半、去掉超龄过滤、去掉同一条事实的去重 → 4 处改动打出 3 例红。
- 反向验证的口径：每次注入都跑**同一套**用例并记下命中数，不写"应该会发现"这种话。
