# LoveBrain 返工验收包（对齐 2026-09-23 全面独立复核报告）

> 被审提交：`286c9406b40b79e4443f1db465bfa5b489f540fa`（报告结论 FAIL）
> 本轮返工提交链：`8745a3d` → `ce89b86` → `5424e21` → `6d67b37` → `0abe572` → `73ed6a8` → `d390dc6` →（本文件所在提交）
> 复核依据：`LoveBrain_Comprehensive_Reaudit_286c9406_2026-09-23.md`，逐节对照
> 编写日期：2026-09-24

## 0. 先说没做到的部分

按报告 §9 末尾那句"任何用『后续完善』『理论上』『注释已说明』『测试文件已新增』替代，都不算完成"，
先把**未完成项**放在最前面，并说清它为什么没完成，不粉饰：

| # | 报告条目 | 状态 | 原因 |
|---|---|---|---|
| 1 | §6 S2-05 后半：`KnowledgeBaseActivity` / `KbEditActivity` 数据逻辑迁 ViewModel | **未做** | 本轮动手改到一半（已写出两个 ViewModel 并完成 Activity 接线），连续撞 Koin `inject` 重载歧义、`readFileWithVersion` 契约差异、`lastKbEditFile` 可空性三处，继续推进有把树改坏的风险。**已整体回退**，Activity 里那两条"此处违反 SRP/DIP、以后应改"的注释原样保留。这是报告点名"记录技术债不等于修复技术债"的那一条，它现在仍然是技术债。 |
| 2 | §6 S2-07：错误类型仍可从字符串前缀反推 | **未做** | 同上：typed `StreamEvent.Error(kind)` 改到一半（Models/仓库/引擎三处联动），未收敛即回退。当前 `PARAM_UNSUPPORTED:` / `isConfigError(message)` 的 `startsWith` 判定仍有 6 处。 |
| 3 | §6 S2-07：历史工单编号留在生产代码 | **未做** | 实测仍有约 566 处 `F09-7 / RA-04 / P0-① / S2-04` 一类标记。数量大、纯机械、且和当前行为正确性无关，本轮选择把预算花在可执行门禁上。这一条我明确认账，不当成已完成。 |
| 4 | §7 P3-02/P3-03：TalkBack、2.0x 字体、360dp 截图矩阵 | **未执行** | 本机没有 Android system image（`D:/Android/Sdk/system-images` 为空），`adb devices` 为空，跑不了任何 instrumentation。触摸区问题我用**可离线执行的静态合同测试**（`ProductionUiContractTest`）把 §7.1 点名的每个反例钉住，但那不等于设备上的真实验收。 |
| 5 | §8 第一步门禁 6 项中的 `connectedDebugAndroidTest` 真跑 | **未执行** | 同上。CI 里这一项会真跑（`scripts/run_ui_tests.sh` + 空证据即失败），但我在这台机器上没有执行过，所以不声称它通过。 |
| 6 | §7 P3-04：Macrobenchmark / Perfetto / 帧时间 | **未产出数据，且模块接线本轮撤回** | 需要设备。另外并行工程加过一个 `:benchmark` 模块，但它声明的 `androidx.baselineprofile` 插件在 1.3.0–1.3.4 各版本于 Google Maven、Gradle Plugin Portal、mavenCentral **全部 404**（实测），而插件写在根 `build.gradle.kts`，导致**所有** Gradle 任务在配置期就失败（连 `:app:tasks` 都跑不起来）——正是报告 §1 的头号问题。已整体回退该接线，只保留可离线验证的部分：真增量解析器 + `IncrementalJsonStreamParserTest`。帧数据仍然没有，不给它披上"已接入"的外衣。 |
| 7 | §5.2 锦囊"真实费用基准" | **未产出数据** | 需要用户 API Key 与真实请求。已提供可重放脚本与输出契约（`scripts/suggest_cost_baseline.py`），未执行 = 无数字。 |
| 8 | §7.2/§8.3.6 抓包证明零遥测 | **未执行** | 需要设备。已提供 `scripts/verify_network_egress.sh`；README 已改成"依据源码与依赖清单核对，抓包未执行"，不再拿未做的事当证据。 |

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
| S2-02 | invokeOnCompletion 在 Mutex 外读改写 StateFlow；stop/stopByType/shutdownAll 不持锁；`startSync` 锁忙返回 null 但调用方已先启动 Job 且忽略 null；SUGGEST/PROFILE_REFRESH 无同类去重；REWRITE/PROFILE_REFRESH 无注册点；ViewModel 仍手写 guard；`stopGeneration` 一次停三类；`dispose` 自己 cancel 不调 shutdownAll；**仓库中没有 ForegroundOperationCoordinatorTest** | 协调器重写：`start(type, requestId) { body }` 在锁内用 `CoroutineStart.LAZY` **先注册后启动**，被拒就 cancel 一个从未跑过的 Job → 不可能有"跑了但不受管"的孤儿；start/stop/stopCurrent/shutdownAll/invokeOnCompletion 全部走同一把 `ReentrantLock`（非挂起路径也能上同一把锁）；六类互斥矩阵含同类去重；REWRITE 与 PROFILE_REFRESH 真正注册；`stop` 按租约、`stopCurrent` 按类，`stopGeneration()` 只停 REPLY；ViewModel 删掉 `generateJob/counselingJob/suggestJob/proactiveJob/rewriteJob/profileRegenerationJob` 与手写 guard，`isProactive/isSuggesting/isCounseling/profileRegenerating` 全成派生；`dispose()` 调 `shutdownAll()`。**新增 `ForegroundOperationCoordinatorTest`**（10 个用例，含"被拒的 start 一次都不跑 body""停一个不牵连另一个"） |
| S2-03 | Engine 仍暴露巨型 Callbacks；回调无 requestId；旧请求迟到可被贴新 requestId；Idle 接受任意迟到 Completed；`onReplyResult` 不拒绝还直写 `_result`；Chunk/Schemes/Usage 未接 Flow；reducer 只是局部包装 | `GenerationEngine.Callbacks` 整个接口删除；Engine 改四条冷流 `replyStream/counselingStream/suggestStream/proactiveStream`，不收 scope、不 launch、不返回 Job；每个事件自带冻结 requestId；回复状态收进单一 `ReplyUiState`，唯一写入口 `dispatchReply → ReplyReducer.reduce`，被拒时返回**同一个对象**（调用方据此知道被拒）；`_result/_replyRequestState/_streamingCoreText/_streamingSchemes` 不再是独立可写流，全部 `map` 派生；chunk 在 dispatch 层合并后再归约，节流不再靠绕过状态源实现 |
| S2-04 | 见 §1；另外：roundId 随机、恢复丢 sourceIds/itemId/state、WRITING 从不写、target 不在单一锁内、recent marker 当跨文件提交标记、恢复顺序导致漏加 count、rotate 边界重复 rotate、手写 JSON 丢反斜杠、`turnCountIncrement` 不按值用、无 `RoundCommitJournalTest` | `RoundCommitJournal` 重写：kotlinx.serialization 取代手写 parser（转义保真，实测 `\n`/`\r`/引号/字面 `\\n`/尾反斜杠往返不变）；roundId 由 kb+消息 ID 集合派生（重试同身份）；六个投影各有水位；rotate 与 setCurrentTopic 拆成两个投影，"已 rotate 未 set"不再重复归档；PREPARED→**WRITING**→COMMITTED 三段真写；`incrementTurnCountBy(delta)` 按事件里的值；跨文件幂等看 committed 清单，recent marker 降级为该文件自身去重（用例：抹掉 marker 后仍不重复写）；在途事务存在时开新轮抛错拒绝覆盖；取消也保留 journal；损坏 journal 先落 `.round_commit_corrupt.log` 再删；`RoundCommitJournalTest` 17 例，对六个边界逐个故障注入并断言恢复后与一次跑通逐文件等值 |
| S2-05 | 七个上帝类；两个 Activity 只加注释承认违规 | **未达标**。Activity 迁移**未做**并已回退（§0 第 1 条）。上帝类行数逐只量过，见第 4.1 节：GenerationEngine 828→678 与 TopicRecorder 1006→991 确实降了，但 KnowledgeRepository **2118→2271 反而涨**、PromptBuilder 1177→1199 也涨，ViewModel 2954→2920 基本没动。这一格是净负进展，不能写成"已拆分"。 |
| S2-06 | 新库无 oldGlobal 直接 return 可能永不写版本；`KbRelativePath` 只有定义无使用；公共方法仍收裸 String；未来 schema 不拒绝；无 v1.3.1 夹具升级/中断/降级测试 | 三处全改并有 `KnowledgeSchemaVersionTest` 13 例：新库落 CURRENT；只有 legacy marker 时也归一化写版本并清 marker；`isBeyondSupported` + `schemaTooNewKbs` 让过新的库转只读（所有写路径统一拒，读仍可用）；`safeKbFile` 让**所有 String 入口**都过 `KbName`/`KbRelativePath`，并补掉盘符绝对路径与反斜杠两个真漏洞（旧校验只挡 `/`），canonical 失败按拒绝处理而非抛穿。v1.3.1 真机升级/降级测试仍待设备 |
| S2-07 | 错误类型可从字符串前缀反推；生产大量硬编码中文；历史工单编号留生产；四套机制并存；无全仓 CancellationException 审计与 lint 防回归 | 硬编码中文：面板主操作/页头/加载态/错误文案/首页行迁入 `strings.xml` 并被组件真调用，`values-en/` 补齐且用 `ProductionUiContractTest` 双向核对（zh 每条都有 en，en 不多出）；死 API `draftText`、`ResultArea.onSaveToKb` 链删除并有防回流用例。四套机制：Job 字段与 boolean 真源已删（见 S2-02）。**字符串前缀推错误类型未做**、**工单编号未清理**（§0 第 2、3 条）；`grep` 口径实测数据见第 4 节，供下一轮定量 |

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
| `scripts/check_apk_metadata.sh` | OK：`com.lovebrain.app` / versionCode 9 / 1.4.0-rc1 / SHA-256 记录在 `dist/apk-metadata.txt` |
| `scripts/verify_signing_continuity.sh` | OK：证书 SHA-256 `c2986640bce6…2f7d`，与已公开 v1.3.1 一致（同时用 `gh api` 对过 release 正文与 asset digest） |
| `scripts/license_scan.sh` | OK：124/124，SBOM 产出且可 JSON 解析 |
| `scripts/assert_artifacts.sh` | OK：真实 724 例；空目录 → 1；篡改 XML 声明数 → 1 |
| `scripts/asset_hashes.sh --check` | OK：prompt 资产未漂移 |
| `bash -n`（16 个脚本 + 2 个 lib） | 全部通过 |
| `:app:lintDebug` / `:app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| `yaml.safe_load` 两份 workflow | 通过；`|| true`/`continue-on-error`/`|| echo` 计数 0 |

推送后 CI 的 required checks 应为：`verify`、`ui-test`、`upgrade-test`，Release 三者都等。
其中 `ui-test` 与 `upgrade-test` **本机无法执行**（无 system image、无设备）。

## 4. 测试数量、失败数与工件（§9 第 4 项）

权威口径：`python` 解析 `app/build/test-results/testDebugUnitTest/*.xml` 汇总，
并用 `scripts/assert_artifacts.sh --min-tests auto` 复核"声明数 == 实际 `<testcase>` 数"，
结果 747 / 91 套件 / 0 failures / 0 skipped。

| 阶段 | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| 报告被审提交（8745a3d 之前）基线 | 85 | 675 | 0 | 0 | 0 |
| WAL 重写后 | 86 | 692 | 0 | 0 | 0 |
| 架构统一 + 测试适配后 | 89 | 724 | 0 | 0 | 0 |
| +协调器合同用例与 requestId 绑定 | 91 | 747 | 0 | 0 | 0 |

工件：`app/build/test-results/testDebugUnitTest/*.xml`（90 份）、
`app/build/reports/tests/testDebugUnitTest/index.html`。
计数用解析 XML 得到，不是复制网页数字；`assert_artifacts.sh` 会额外核对
"声明的 tests 数 == 真实 `<testcase>` 数"，防止报告注水。
没有 `@Ignore`、没有 `assumeTrue(false)`、没有在 workflow 里用 `--tests` 过滤缩小范围。

instrumentation：44 个用例编译通过，**执行数为 0（未跑）**，见 §0 第 4/5 条。

本轮最后一次全量门禁（同一棵树，含 lint 与签名 release 构建）：
`:app:testDebugUnitTest` + `:app:compileDebugAndroidTestKotlin` + `:app:lintDebug` → BUILD SUCCESSFUL。

## 5. 发布判定

按报告 §1 的独立放行意见执行：**保持 `1.4.0-rc1` 开发状态，不打 tag、不建 Release、不宣称阶段完成。**

理由（不对外甩锅，对内也不自签 PASS）：
1. 第一步门禁的 `connectedDebugAndroidTest` 没真跑过——报告正是因此判 FAIL 的，我不能用同一缺口自证通过。
2. §0 列的 8 项未完成里，S2-05 与 S2-07 是报告明确点名的生产架构/正确性问题，不是可选项。
3. worker 不得自签 PASS（报告 §8 第三步门禁）。这份文件是**交付给复核者的证据包**，不是通过证明。

worker 不能自签的另外一面也照做：上面每个"OK"都是命令输出，不是叙述；每个"没做"都写了没做的原因和现状数字。

### 4.1 上帝类行数（报告 §6 S2-05 的表，本轮实测对照）

报告给的被审行数与本轮 `wc -l` 实测：

| 文件 | 报告 | 本轮 | 变化 |
|---|---:|---:|---:|
| `LoveBrainViewModel.kt` | 2954 | 2920 | −34（删掉 Job 字段与手写 guard，但新增事件归约代码抵消了大部分） |
| `KnowledgeRepository.kt` | 2118 | **2271** | **+153（变差）** |
| `KnowledgeBaseActivity.kt` | 1214 | 1213 | −1（等于没动） |
| `PromptBuilder.kt` | 1177 | **1199** | **+22（变差）** |
| `TopicRecorder.kt` | 1006 | 991 | −15 |
| `GenerationEngine.kt` | 828 | 678 | −150（Callbacks 与 launch 逻辑移除） |
| `KbEditActivity.kt` | 585 | 584 | −1（等于没动） |

结论：S2-05 的"职责拆分"只在 GenerationEngine / TopicRecorder 上真实发生；
Repository 与 PromptBuilder 因为承接了 S2-01/S2-06 新加的内容而变大，
两个 Activity 基本原封不动。报告说"记录技术债不等于修复技术债"，
本轮不能一边批评它一边犯同样的错，所以这里按实测写"未达标"，不写成已完成。
