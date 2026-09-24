# LoveBrain 三阶段再复核指导书 — 执行记录（2026-09-24）

> 输入指导书：`LoveBrain_Three_Phase_Reaudit_and_Six_Principles_UI_Architecture_Guide_c0ff0415_2026-09-24.md`
> 被审提交 `c0ff0415`；本轮起点 `4795471`（上一窗口的最后一个提交，它已停手并留了
> `LoveBrain_Handover_CI_Evidence_2026-09-24.md`）。
> 本轮 HEAD：`4a8f7f7`（谈心 store）→ `284463f`（协调器偶发红）→ `5e06cc9`（改写 store）
> → `afbe303`（enum 进 model）→ `b1df35a`（主动发的模式归位）→ `41536ad`（观测补齐）
> → `5a21ec2`（§5.3 第一格：KnowledgeBackupService）→ 本文件这条。
> **全部只在本地、未推送**：`git ls-remote origin main` 实测远端仍是起点
> `4795471`；领先多少个提交**别抄这里的数**，现算：
> `git rev-list --count 4795471..HEAD`（`5a21ec2` 落地后测得 40）。
> 阶段一的 P0-03/04/05 + 阶段二的 ports/棘轮/死 API/五个 store 与模式归属都在里面。
> 本轮没有改 prompt：`git diff --exit-code 286c9406..HEAD -- app/src/main/assets/engine` → 零差异。

> **接手请从 `LoveBrain_Handover_Unfinished_Work_2026-09-24.md` 开始读**：
> 那份是"还剩什么、为什么没做、怎么起手"的清单；本文件是已完成部分的逐项证据。

## 0. 一句话状态

阶段一的 P0-03 / P0-04 / P0-05 已落地并本机验证；P0-01 / P0-02 的**真机那一半卡在
19 条 instrumentation 真失败**上，本机没有 system image，只有 CI 能出证据，所以要推送。
阶段二：包边界棘轮 + 死 API + Koin 唯一 journal + 冻结注释改口 + ports 与双侧合同测试
+ 三道"不许变差"的闸都落地了；§5.2 的五条 feature store **全部搬完（5/5），
连§5.2 第 3 步列的"模式"也归位了**（两个 enum 先搬进 model，见 §2f）；
但 VM 反而 2651 → **2746** 行（逐提交实测，见 §2h 末尾那条链），"退成薄 facade 再删掉"（§5.2 第 6 步）还没做；
KnowledgeRepository 按能力拆分**没做**；搬 store 时被弄丢的"陈旧事件被拒"日志已全部补回（§2g）。
阶段三（设计系统 10 个组件、截图矩阵）**没开始**。

## 1. 逐项进度表

| # | 指导书条目 | 状态 | 提交 | 证据 |
|---|---|---|---|---|
| 1 | P0-01 CI 总红灯 | **部分**：verify 已全绿（上一窗口） | 前置 `2b01f21`/`f5328b7`/`8cb9819` | run 35944822268 的 verify 26 步全 ✓（含 R8/APK metadata/SBOM/费用 dry-run）；本轮新增的 egress 步骤未跑过 CI |
| 2 | P0-01「AndroidTest 缺 import」 | **不成立**（复核误判） | — | `:app:compileDebugAndroidTestKotlin` 本机 + CI 都 exit 0；`assertDoesNotExist`/`onAllNodes` 是成员函数，不需要 import |
| 3 | P0-02 生成不崩溃的 10 格真链路 | **没证成**：夹具竞态修了 + 取证加了，结果待 CI | `30d313e`, `dd5282e` | run 35943906234：40 执行 / 19 失败 / 2 跳过；本轮把 7 条同源于"只推一帧"的夹具改为等条件，并把 29 处可见性断言换成带几何量的诊断 |
| 4 | P0-03 未来 schema 写保护可绕过 | **完成** | `1658356` | 新增 `KnowledgeTx`/`transaction`/`WriteResult` + 唯一落盘出口按路径归属拒绝；`ReadOnlySchemaWriteGateTest` 5 格，24 个公开写入口跑完目录树逐字节不变 |
| 5 | P0-03 读入口不走 safeKbFile | **完成** | `1658356` | 同上文件的越界读测试（`../`、`./../`、`moment/../../`、绝对路径） |
| 6 | P0-04 抓包脚本 `|*) continue` 假阴性 | **完成（本机不可判的两格除外）** | `41d95e9` | 通配兜底删掉；5 个合成 pcap + 六格自测；本机无 tshark → 4 格判不了，脚本 exit 2 并写明 CANNOT-VERIFY |
| 7 | P0-05 WAL 锁内二次检查 + AlreadyCommitted | **完成** | `06cfc4c` | `commit()` 锁内重读、`recover()` 同源；`two coroutines committing the same round write it once` |
| 8 | P0-05 水位失败注入 | **完成** | `06cfc4c` | `an effect that landed but lost its watermark runs again on recovery` 显式要求 scene 的 effect 跑第二次 |
| 9 | P0-05 文档改口 at-least-once | **完成** | `06cfc4c` | 类 KDoc、`Tx.apply` 注释、测试类头、`docs/ARCHITECTURE.md` §2.5.1（该文档按 .gitignore 不入库） |
| 10 | P1-01 18 个 >500 行文件 | **未做**，且 KnowledgeRepository 变长 | — | 实扫：114 个生产 Kotlin 文件 / 32,673 行 / >500 行 18 个 / >800 行 10 个；KnowledgeRepository 1857 → 2000（加写边界与 Tx） |
| 11 | P1-02 用源码 grep 代替触摸边界 | **未做**（要设备） | — | 语义树 `boundsInRoot` ≥48dp 的断言还没写 |
| 12 | P1-03 GenerationActionButton 重复 modifier 链 | **完成** | `dd5282e` | 只剩一条 size→graphics→shadow→clip→background→clickable→padding |
| 13 | P1-04 Koin 双 journal | **完成** | `845330a` | `single { TopicRecorder(get(), get()) }` + 身份断言；改回旧写法 → 该格 RED（实测） |
| 14 | P1-05 文案未收口 | **未做** | — | 见 §3 |
| 15 | §3.3 fingerprint 死 API | **完成** | `845330a` | 删除，全仓 0 调用者；真正在用的只有 `GenerationFingerprints.inputOf` |
| 16 | §3.3 prompt「冻结」说法不实 | **完成（选了"改口"分支）** | `845330a` | Engine 注释改成"hash 只做诊断，本轮用的是现读文本"；`PreparedPrompt` 整体冻结没做 |
| 17 | §3.3 验收包与实现不符的两句话 | **部分** | — | "仍用冻结 prompt"已改口；"全部写路径统一拒绝"现在**变成真的**了，但验收包文档还没补这一节的实测数 |
| 18 | §7 第二步 4：包依赖测试 | **完成** | `a9bb9b1` | 棘轮 + `scripts/package_deps_report.sh`；存量 15 条 / 11 文件已登记；四格反向验证 |
| 19 | §7 第二步 1/2/3（ports、feature store、Repository 拆分） | **1 完成 / 2 完成（含『模式』）但 facade 没退 / 3 没做** | ports `2dd94c0`；store `9cbcbb1`/`d25bdb0`/`8e911b8`/`4a8f7f7`/`5e06cc9`；模式 `afbe303`+`b1df35a` | 详见 §2b–§2f；剩下的没做项全在 §3 |
| 20 | §7 第三步（设计系统 + 截图矩阵） | **未开始** | — | 见 §3 |

## 2. 本机门禁（一次跑完的实测，全部来自当次命令输出）

```
:app:testDebugUnitTest              941 tests / 113 suites / 0 失败 / 0 跳过 / 0 泄漏异常
:app:lintDebug                      72 issues，Error+Fatal 0
:app:compileDebugAndroidTestKotlin  BUILD SUCCESSFUL
scripts/audit_cancellation.py       站点 167：PROTECTED=53 WAIVED=2 SUSPEND-FREE=112 NEEDS_REVIEW=0 → PASS
scripts/strip_ticket_ids.py         PASS（生产注释/代码/字面量 0 处工单编号）
scripts/asset_hashes.sh --check     OK 6dcde732fab602813559370dd6af3b774ca24fda86ce28f2c0cfd88a8be95831
git diff 286c9406..HEAD -- assets/engine   零差异
scripts/suggest_cost_baseline.sh --self-test  all checks passed
scripts/test_verify_network_egress.sh        exit 2 = CANNOT-VERIFY（本机没有 tshark，见 §3）
```

## 2b. 阶段二第二轮推进（本机验证，提交 `2dd94c0`/`ab7457a`/`a74cc8a`）

| §7 第二步条目 | 状态 | 证据 |
|---|---|---|
| 1 建 ports 与 contract tests，先包住 concrete repositories | **知识侧 + provider 侧都完成** | `KnowledgeReadPort` / `KnowledgeWritePort` / `KnowledgePort` / `AiGateway`；合同测试一份跑两侧：知识 8 格 ×2=16 例、网关 4 格 ×2=8 例。反向验证：把 fake 的只读判定写死 false → 那一格立即红；Koin 里把端口绑定写成 `get()` → 三条图测试当场 StackOverflowError |
| 4 package dependency test | 完成 | 棘轮实测 **15 → 10 → 6** 条越界；数字来源 `scripts/package_deps_report.sh` |
| 5 清死 API / 重复 journal / 重复 modifier / 历史描述性注释 | 前三项完成，注释只清到自己踩到的那处 | `fingerprint()` 删除；`TopicRecorder(get(), get())` + 身份断言；GenerationActionButton 单链；Engine「仍用冻结 prompt」改口 |
| 2 五个 feature store | **3/5 完成**（`ReplyStore` `9cbcbb1`、`SuggestStore` `d25bdb0`、`ProactiveStore` `8e911b8`） | 回复：状态 + 增量合并 + 副作用判定；锦囊：状态 + 在途身份 + 缓存配对 + 流式 tips；主动发：options + 错误 + 收尾语义。共 18 格 store 测试不启动 VM、不需要 Android。**ProactiveStore 只搬了三样，"模式"那一样没搬完**（enum 是 VM 嵌套类型、feature 不许 import viewmodel；原因写在 store 与 VM 两边）。**体量不粉饰**：VM 2651 → 2667。剩 Counseling / Rewrite |
| — 附带修掉一个真缺陷 | 完成 | 合并循环原本是 `while (true) { delay(50); flush() }`，一旦有 chunk 就永远每 50ms 醒一次，只有外部调"丢弃"才停；在 VM 里被 `viewModelScope` 的死亡掩盖，搬进 store 用 runTest 一测当场 `UncompletedCoroutinesError`。现在循环条件与职责一致，并有一格测试锁住这个形状 |
| 3 KnowledgeRepository 按能力拆 | **没做** | 见 §3 第 4 条 |

§7 第二步「完成定义」逐条对照，不粉饰：

- 「LoveBrainViewModel 不再持有五条 feature 的内部状态」→ **部分（3/5）**：回复、锦囊、主动发
  三条的状态持有者已经出去（主动发的"模式"那一样仍在 VM），Counseling / Rewrite 两条还在里面。
  VM 2651 → 2667 行，**没到"体量下来了"的程度，别写成达成**。
- 「KnowledgeRepository 不再是所有知识能力的唯一入口」→ **部分**：domain 已全部走端口，
  写只有 `transaction`/`KnowledgeTx` 一条路；但仓库对象自身仍是所有能力的唯一实现处，拆类未做。
- 「每个 port 有 production/fake 共用 contract suite」→ `KnowledgePort`、`AiGateway` 达成；
  四个端口里 `KnowledgePort`、`AiGateway`、`Clock` 已达成（Clock 是第四轮补的，见 §2c）。
- 「新增 feature 不修改已有 reducer」→ **无法验证**：reducer 还没抽出来。
- 「新代码无 >500 行文件，现存 >800 行文件数量持续下降」→ 前半达成（本轮新增 8 个文件最大 188 行）；
  **后半未达成**：>500 行仍是 18 个、>800 行仍是 10 个，与被审提交测到的一样，
  而 KnowledgeRepository 因加写边界从 1857 涨到 2004。这一格记未达成，不写「方向正确」。

本轮收尾实扫：117 个生产 Kotlin 文件 / 32,807 行；
966 单测 / 117 套件 / 0 失败 0 跳过 / 0 泄漏异常；lint 72 issues 0 error；
androidTest 编译通过；取消审计与工单编号 PASS；prompt 目录零 diff。

一处自伤要认：`ab7457a` 里我用脚本改两个测试文件，把它们的行尾从 LF 翻成 CRLF，
一个加 2 行 import 的提交报了 796 行 diff。`a74cc8a` 已复位
（相对损伤前净差异 3 insertions / 1 deletion），损伤留在历史里没改。

## 2c. 阶段二第三轮：三道"新代码不许变差"的闸 + P1-02 语义树实测

| 指导书条目 | 状态 | 实测证据 |
|---|---|---|
| §3.3「lint 设新增 warning = 0，逐批消债」 | **完成** | `scripts/check_lint_budget.sh` + `scripts/lint-budget.txt`，按**每条规则**记 15 条预算。三道闸实测咬得住：预算抬高藏债 → FAIL；实际超出 → FAIL；超预算时 `--rewrite` 拒绝重写 |
| P1-05「禁止新增用户可见字面量」 | **闸完成，搬运未做** | `UiStringLiteralBudgetTest` 4 格（含临时目录注入正反例）。**并纠正一个数**：报告的 101 是**下界** —— 那把尺只数 `Text("中文`，换成能识别 `Text(text = "中文…")` 与跨行写法的正则后实扫 **209** 处，差 108 |
| P1-02「不允许用源码 grep 代替触摸边界测试」 | **完成（结果待 CI）** | `PanelHeaderTouchTargetsTest` 读每个可点击节点的 `boundsInRoot` 判 48dp，另两格判 selected/stateDescription 与 contentDescription；`ProductionUiContractTest` 的 KDoc 明确降级为"只证明源码里出现过这个数"。instrumentation 声明数 40 → 43 |
| §4 的 `Clock` 端口 | **完成**（第四轮 `a05b395`） | domain 里 8 个"结果进文件或进 prompt"的时间点改为注入：TopicRecorder 的轮次块头与场景时间戳、OngoingContextSelector 的冷却时间、PromptBuilder 的「当前时间」段。GenerationEngine 那 14 个 PERF 计时**故意不套**（不进状态，套了是假抽象）。`ClockWiringTest` 4 格：注入的钟要出现在落盘内容里、未注入的系统时间不许出现、拨快 45 分钟第二轮块头要跟着变、不传时默认仍是真钟；把 TopicRecorder 改回 `TimeFmt.now()` 实测前两格红 |

数字纠偏（同一个文件里前两节的数按这里为准）：

- 前面写的 "lint 72 issues" 数错了：`grep -c '<issue'` 把根元素 `<issues>` 一起数了，
  **实际 71 条**（65 Warning + 6 Information，0 Error/Fatal）。新脚本按 `<issue … id=` 计数。
- 第三轮收尾实测：969 单测 / 0 失败 0 跳过 / 0 泄漏异常；lint 71 issues / 0 error / 预算 OK；
  `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL；取消审计与工单编号 PASS。

还有一处必须留记录的自伤（本轮犯了三次）：脚本里 `open(p,'wb').write(字符串)`
会**先把文件清成 0 字节再抛异常**，而空的 `.kt` / `.md` 照样 `BUILD SUCCESSFUL` ——
那是彻底的假绿，我还把 0 字节的本文档提交过一次（`627949f`）。
规则：源码与文档的文本改动一律用 Edit 工具；真要脚本化就字节读字节写，写完 `wc -c` 看一眼。

## 2d. 阶段二第四轮：CounselingStore（五个 store 完成 4/5）+ 一次必须记录的偶发红

| §5.2 第 4 步 | 状态 | 实测证据 |
|---|---|---|
| 谈心链的状态持有者出去 | **完成** | `feature/counseling/CounselingStore.kt` 192 行：`UiState(streaming/result/error)` + `Intent(Apply/DiscardPendingChunks/Clear/Fail/Restore)` + `Effect(PersistResult/FirstTokenObserved)`。VM 侧 `counselingResult/counselingError/counselingStreaming` 全部改为从 store 派生，四个旧 `MutableStateFlow` 与 74 行 reducer/flush 代码删除；`restoreState()` 里那条"从 prefs 恢复上一轮回答"改走 `Intent.Restore`（只填 result，不再自己落一次盘）。冷启动恢复路径以前无法在 JVM 里测，现在可以 |
| 常驻合并循环 | **第二处，一并修掉** | 谈心这里原来也是 `while (true) { delay(50); flush() }`。同一缺陷写了两遍，所以两把锁都要有：只测 ReplyStore 的话，谈心的循环改回去没人发现。实测反证：把 `scheduleFlush` 改回 `while (true)` → 6/14 格红（含 `UncompletedCoroutinesError`） |
| 日志命令用冻结身份、不回读实时状态 | **从注释变成合同** | `Effect.PersistResult(kbName, userMessage, replyText, analysisText)` 四个字段全部来自事件。实测反证：`userMessage` 改成回读 `_ui.value.streaming` → 2 格红；`kbName` 改成常量 → 同两格红（`kbName = null` 那格也在里面） |
| store 不启动 VM 也能测 | **完成** | `CounselingStoreTest` 14 格，不需要 Koin / Android / 设备 |

CounselingStore 的 12 个变异全部被咬住（每条新断言都单独注入反例看它红；一次性脚本在
`_temp/mutate_counseling.py` 与 `_temp/mutate_counseling2.py`，**按 .gitignore 的 `/_temp/`
不入库**；下面协调器那三件的反证在 `_temp/mutate_coordinator*.py`。每个脚本跑完逐字节复原
并断言 `git status` 干净）。其中一格**一开始是恒真的**：`clear drops state
and unpublished chunks together` 原先只把已发布的内容抹掉，删掉 `Clear` 里的 `discard()`
仍然全绿——补上"清空时缓冲里还剩半句"才真的红。这条记下来是因为它是我自己写的假断言。

搬家顺手收紧的两处（不是等价搬运，写清楚）：

1. **发布前再核一次主人**。入口那道 `isCurrentRequest` 只拦得住"新到的事件"，拦不住
   "上一轮到一半、这轮已经易主"的存货；ReplyStore 同一位置本来就有这道核对，谈心补齐，
   两条链用同一套语法。同时按 ReplyStore 的写法在换人时先结掉上一家的缓冲，
   两轮的增量不许混进同一次发布。反证：删掉核对 → `a buffered half sentence … is never
   published` 红。
2. `CounselingResult` 到达时**不**清流式位。面板在 `isCounseling` 期间显示的就是
   `streaming`，而 `CounselingEnded` 晚半拍才到；提前清会让最后一段正文闪成"还在等待"占位。
   这一条老代码本来是对的，我第一版写成了清空，被自己的测试挡下来（`a settled result
   leaves the streamed text standing until the round ends`）。

一处偶发红必须记录，不能压掉：整仓 1005 格一起跑时
`ForegroundOperationCoordinatorTest > a finished operation disappears from both the snapshot
and the state flow` 报了一次 `awaitTrue` 3s 超时（该类单独跑 3 次全绿；再加 10 个 busy loop
压 CPU 跑 8 次仍全绿，抓不到第二次）。读代码定位：登记在 `start()` 返回前就完成，所以那一
等必然立刻为真，超时的是**等清理**那一步 —— 1000 多格共用 `Dispatchers.Default`，
LAZY 启动的任务体排不上队，3s 被调度饥饿吃掉，不是清理丢了。处理方式不是把断言调软：

- 那个中间态改成同步断言（`start()` 返回时 `snapshot().size == 1`），不再轮询转瞬状态；
  反证：让 `start()` 不登记表 → 该类 9 格红。
- 等清理的墙钟预算 3s → 15s，并让超时消息带上最后一次看到的快照。反证：把
  `invokeOnCompletion` 整段删掉 → 仍然红（15s 后），消息里带着卡住的那条租约。
  **预算只改变"多久之后才认定丢了"，不改变判定标准。**
- 归约搬进 store 时"陈旧事件被拒"的 `L.w` 差点一起丢掉，在唯一的注入点（VM 的
  `isCurrentRequest` 闭包）补回来了；store 仍然不知道有日志这回事。
  Reply/Suggest/Proactive 三处在前三轮搬家时同样带走了这条日志（共 4 处），**那三处本轮没补**，
  记在 §3。

本轮收尾实测（全部来自当次命令输出）：

```
:app:testDebugUnitTest              1005 tests / 123 suites / 0 失败 / 0 跳过 / 0 泄漏异常
:app:lintDebug                      71 issues（65 Warning + 6 Information），Error 0；check_lint_budget.sh OK
跨层 import 棘轮                     6 条（与上一轮持平，本轮没新增也没减）
:app:compileDebugAndroidTestKotlin  BUILD SUCCESSFUL（本轮没动 instrumentation）
生产 Kotlin                          122 文件 / 33,544 行 / >500 行 18 个 / >800 行 10 个
VM                                   2661 行（搬 store 之前 2651，三轮下来 +10 —— 体量没降，别写成达成）
KnowledgeRepository                  2001 行，仍是全仓第二长
```

§7 第二步「完成定义」第一条对照更新：「LoveBrainViewModel 不再持有五条 feature 的内部状态」
→ **4/5**：回复、锦囊、主动发、谈心四条的状态持有者已经出去（主动发的"模式"那一样仍在 VM），
只剩 Rewrite 一条。

## 2e. 阶段二第五轮：RewriteStore（五个 store 全部搬完 5/5）

| §5.2 第 5 步 | 状态 | 实测证据 |
|---|---|---|
| 接管 `rewriteRequestId` | **完成** | 两个裸字符串（`rewriteRequestId` / `rewriteContextId`）合成 `RewriteStore.Identity(requestId, contextId, identityKey, option)`，发起时冻结、终态时带回来核对。VM 里不再有任何人能做"第二次改写没登记"这种事 |
| 接管 ledger | **完成，且台账只管一件事** | `RewriteLedger` 搬进 `feature/rewrite`（留在 viewmodel 包会让 feature 反向 import viewmodel），并拆掉它的第二职：卡片状态（Loading/Done/Error）归 `RewriteStore.UiState`，台账只剩版本栈。`RewriteLedgerTest` 7 格留历史，状态断言 4 格搬进 `RewriteStoreTest` |
| 接管取消 | **完成** | `Intent.RequestCancel` → 只有取消的确实是那张在途卡时才发 `Effect.StopRunningRewrite(requestId)`，VM 再核对租约身份才 `stopCurrent` |

搬出来后**当场看得见**修掉的三个顺序缺陷（每个都能复述成用户手上一件事）：

1. 被协调器拒绝的改写让卡片永久转圈 —— 旧写法先 `begin` 再 `start`，而 `start` 会返回 null
   （前台槽位被占、或连点两张卡）。现在 `Begin` 挪进任务体第一行：任务没跑起来就什么都不留。
2. 点 A 卡的"取消"会停掉 B 卡正在跑的改写 —— 旧 `cancelRewrite` 无条件
   `stopCurrent(REWRITE)`。反证：把"是不是在途那张"的判定改成"只要有在途就停" →
   `cancelling a card that is not the in-flight one stops nothing` 红。
3. **撤销会吃掉历史** —— 旧写法先 `pop()` 再检查"结果还在不在、key 解不解得开"，任何一步
   失败就 `return`，那一版正文永久丢失、卡片还挂着 Done。改成两步式
   （`UndoRequested` 只 peek 交效果 → VM 真贴回去才 `UndoCommitted` → store 这时才 pop）。
   可达性不高（轮次切换会连带清历史），但顺序是错的，所以修。

新增接缝测试 `RewriteEffectWiringTest` 8 格：钉的是"store 交出来的效果真的落到结果上"——
只换目标卡、其他三张一个字不动、新正文从 NONE 开始、撤销连正文带赞踩一起回来、计数只加一次。
这条接缝是搬家时新长出来的，不写它等于把"贴错卡片 / 撤销不还原反馈"留给真机发现。
踩到的一次假失败值得记：`vm.result` 是 `stateIn` 派生的，`StandardTestDispatcher` 下
不推进调度器读到的永远是上一帧的 `null` —— 第一版 5 格 NPE 全红在这里，不是生产问题。

13 个变异逐个注入反例，每个只咬住它该咬的那格（两条打在 VM 接缝上：贴固定的 STYLE:A、
撤销不还原反馈），脚本 `_temp/mutate_rewrite.py`（不入库），结果留档
`_temp/rewrite_mutation_results.txt`；跑完逐字节复原并断言 `git status` 与跑前一致。

体量这条本轮最难看，按实数写：**VM 2661 → 2734（+73）**。规则搬出去 223 行，
但 `onRewriteEffect` 与它的注释留在了里面。§7 第二步"完成定义"第一条
（VM 不再持有五条 feature 的内部状态）**状态持有者这一半达成（5/5）**，
"薄 facade"这一半**反而更远**；把 VM 减下来是删 facade（§5.2 第 6 步）那一刀的事。

本轮收尾实测：

```
:app:testDebugUnitTest              1028 tests / 125 suites / 0 失败 / 0 跳过 / 0 泄漏异常
:app:lintDebug                      71 issues（65 Warning + 6 Information），Error 0；check_lint_budget.sh OK
跨层 import 棘轮                     6 条（feature/rewrite 没有新增任何越界方向）
生产 Kotlin                          123 文件 / 33,810 行 / >500 行 18 个 / >800 行 10 个
取消审计 / 工单编号 / prompt diff      NEEDS_REVIEW=0 / PASS / 零 diff，lock 6dcde732… OK
```

## 2f. 阶段二第六轮：enum 搬进 model + 主动发的"模式"终于归位

> 本节取代 §2b / §2d 里"主动发的模式那一样没搬完"的说法——那两句在各自那一轮是真的，
> 现在不再成立。

| 指导书条目 | 状态 | 实测证据 |
|---|---|---|
| §5.2 第 3 步剩下那一项「模式」 | **完成** | `ComposerMode` / `ResultMode` 先搬进 `model`（`afbe303`，纯机械改动，6 个文件逐处判过语境），然后 `ProactiveStore.UiState` 接管模式（`b1df35a`）。`ProactiveStoreTest` 5 → 10 格，新增 5 格全在模式上；6 个变异逐个注入反例，各咬各的格 |
| 规则不再跨两个所有者 | **完成** | "结束且真拿到可展示开场才退回普通回复"以前是 store 报效果 + VM 改自己的 `_composerMode`/`_resultMode` 两步，中间没有原子性；现在一次 `copy()` 做完。`resultMode` **故意留在 VM**（回复链也写它），store 只能通过 `Effect.ExitedProactiveMode` 通知 |
| 跨层棘轮没有被绕过 | 实测 6 条，与上一轮持平 | feature 包不 import viewmodel 这条现在是**真成立**，不再靠"模式没搬所以没违反"绕过去 |

VM 2729 → 2732（+3，本轮）；本轮前那一步（enum 搬家）是 −5。**§5.2 五条链的状态持有者
连同"模式"这一项全部搬完，但 VM 比开工前（2651）还长 81 行**——
"退成薄 facade 再删掉"（§5.2 第 6 步）没做，体量要还得等那一刀。

### 一条我自己写错的断言，按实修在明处

`b1df35a` 的提交信息里我写了：

> 新一轮开始 → 清结果但保留模式（旧写法是整份 UiState() 重建，用户正在主动发里也会被弹回普通回复
> ——这一格是本轮新钉的行为差异）

**"旧写法会把用户弹回普通回复"是错的。** 旧代码的模式在 VM 的 `_composerMode` 字段里，
`ProactiveStarted -> _ui.value = UiState()` 根本碰不到它，所以旧行为同样是"模式保持"。
新代码里那条 `copy(options = emptyList(), error = null)` 只是把一个**原本靠字段住处分隔开的
隐含行为**变成 store 里的显式规则并加了测试，行为本身没有差异。
这条测试仍然要留（它现在钉的是显式规则，将来谁把 `ProactiveStarted` 改回整份重建就会红），
但"新钉的行为差异"这个说法不成立，在这里更正，不留到 review 被抓。

本轮**真实**的行为差异只有一处：旧 `exitProactiveMode()` 在模式本来就是普通回复时也会把
`resultMode` 置回 REPLY；现在这种情况只在"迟到的开场落地"那条分支里归位一次，
对"本来就没进主动发"的点击不再发通知。`an opener that lands while the user has already left
still resets the result area` 钉的就是前者。

另记一次测试自己错（第一次跑当场红，改断言不改生产）：`toggling out` 那格我先写了
"切出去之后再断言没有任何效果"，忘了切出去本身就发一次 `ExitedProactiveMode` ——
是把计数写对，不是把规则放宽。

本机收尾实测：1033 单测 / 125 套件 / 0 失败 0 跳过；lint 71 issues 0 error 预算 OK；
`:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL；取消审计 167 站点 NEEDS_REVIEW=0；
工单编号 PASS；跨层 6 条。

## 2g. 阶段二第七轮：把搬 store 时弄丢的观测补回来（§3 第 7 条结账）

`41536ad`。现象是线上事故形状：面板没出字、logcat 里一句被拒记录都没有——
闸还在，但拒绝不吭声。五次搬家一路弄丢 `L.w("… rejected (stale requestId)")`，
本轮按"谁都能核、store 不知道有日志"补回四处：

| 链 | 补法 |
|---|---|
| Suggest / Proactive / Counseling / Rewrite | 归属核对本来就是 VM 注入给 store 的闭包，统一走新的 `ownsAndLog(type, requestId, what)`——四处内联写法、四种措辞收成一处 |
| Reply | 判据在 reducer 里（原样返回同一个对象才算被拒），闭包那套不适用 → `ReplyStore` 多一个 `onStaleEvent` 出口 |

**为什么 reply 不用"accept 返回布尔"**（我先写成返回值，看到这条才换）：增量先收进缓冲、
下一个节拍才归约，`accept` 那一刻还没判定，返回值会把真正的拒绝点整个漏掉。
新格测试 `a rejected event is reported once, at the moment it is actually judged`
锁的就是这个时机：投迟到 chunk 时断言"还没判"，推进一节后断言只报一次，
再投合法 Completed 断言它不进这个出口。两个反证各自让它红（去掉 `onStaleEvent` 调用；
把被拒分支短路成永不拒）。

本机：1034 单测 / 125 套件 / 0 失败 0 跳过；lint 71 issues 0 error 预算 OK；
取消审计 NEEDS_REVIEW=0；工单编号 PASS；跨层 6 条；androidTest 编译通过。
`§3` 第 7 条因此结账，阶段二剩下的仍是那两件：facade 删除、KnowledgeRepository 按能力拆分。

## 2h. 阶段二第八轮：§5.3 动手——KnowledgeBackupService 是拆出的第一格

`KnowledgeRepository` 2001 → **1942 行**（−59），新增 `data/KnowledgeBackupService.kt` 132 行
+ `KnowledgeBackupServiceTest` 14 格 + 原 4 格分组键回归（文件随归属改名）。

| 拆出去的 | 留在仓库的 |
|---|---|
| 复制哪些目录、留几份、按库删备份、`.last_backup` 的间隔判定 | 那把唯一的 `fileMutex`、"什么时候要备份"的 5 秒节流调度、`atomicWriteText` 这道写门 |

**这一格最有价值的部分是它先被自家门禁拦下来了。** 我第一版把 `scope.launch` 的节流备份一起
搬进新类，`SingleOwnerContractTest > only the coordinator may own a scope and launch jobs`
当场红（"领域/数据/VM 不许接收外部 CoroutineScope，唯一例外是协调器"）。修法不是给闸开例外，
而是把设计改对：策略类不拿 scope、不 launch —— 现在 `KnowledgeBackupService` 是纯文件系统策略类，
不需要协程、不需要 Android。这条正好演示了那种闸的意义：它拦住的是"拆类拆出第二个启动者"。

写边界继续只有一条：备份唯一那次落盘（`.last_backup`）经 `BackupStorage.guardedWrite`
回到仓库唯一的 `atomicWriteText`，`the marker write goes through the storage gate and a refusal
is not worked around` 那格把门关着再跑一次，要求"一个字都不许写进去、也不许冒出第二条路径"；
`ReadOnlySchemaWriteGateTest`（24 个公开写入口跑完目录树逐字节不变）本轮仍全绿。
`BackupStorage` 刻意不复用 `KbStorageAccess`：备份只要"看见根目录 + 过一次写门"，
共用接口等于给它用不到的写权限（ISP）。

变异反证 8 个，7 个各咬各的格；**一个必须留档的等价变异**：把 `backups.size > maxCount`
写成 `>=`，13 格全绿 —— 不是测试弱，是这两种写法行为完全相同
（进块之后 `drop(7)` 对 7 份是空操作）。同一格换成真实的错就红：`drop(maxCount - 1)`（多剪一份）、
`sortedBy`（留最旧剪最新）。记这条是因为"注入反例还全绿"这种局面有两种解释，
分清是**测试瞎**还是**变异等价**才不致于去改一个本来没坏的断言。

本轮收尾实测：1045 单测 / 126 套件 / 0 失败 0 跳过；lint 71 issues 0 error 预算 OK；
跨层 6 条；取消审计 167 站点 NEEDS_REVIEW=0；工单编号 PASS；androidTest 编译通过；
prompt 目录零 diff。

### 更正：VM 行数以前抄过一个过期的数，这里换成逐提交实测的链

`git show <提交>:…/LoveBrainViewModel.kt | wc -l`：

```
4795471 2651   ← 本轮开工前
9cbcbb1 2624   ← ReplyStore      （−27，这一步是真降的）
d25bdb0 2647 … 8e911b8 2667       ← Suggest / Proactive 之后
4a8f7f7 2661   ← CounselingStore
5e06cc9 2734   ← RewriteStore     （+73：规则出去 223 行，效果落地留在 VM）
b1df35a 2732   ← enum 进 model + 模式归位
41536ad 2746   ← 被拒日志补齐     （+14：ownsAndLog 与五处闭包）
HEAD  2746
```

两个错要认：①§2f/§2g 与 §0/§3 里那句"VM 2732 行、比开工前长 81 行"是 `41536ad`
**之前**量的，量完没重测就写进下一节 —— 真值 2746 / +95，已在上面全部改掉；
②"搬 store 不降体量"这句在第一步并不成立：`ReplyStore` 那一刀实打实 −27 行，
是后面几步把编排与转发留在 VM 才涨回去的。下一格别照抄"反正不会降"这个结论。

## 2i. 阶段二第九轮（另一窗口接续）：先造仪器，再用它量三处无障碍缺陷

> 本轮起点是交接文档 `c21b200`。逐项逐条对照结果另成一份：
> `LoveBrain_Guide_Item_by_Item_Verification_2026-09-24.md`（含"本机已验 / 沿用上轮未复验 / 只能等 CI"三态标注）。
> 提交：`cb44ceb` `6177cd0` `c927b2e` `9f4747a` `ead80c1` `2ef47ac`。

### 为什么这一轮从 §5.3 改道去装仪器

交接建议先拆 catalog。实际先做的是 §6.5 的**语义树那一半**，理由一条：
指导书 §10 说下一次复核"不重复接受'代码看起来已经修了'"，而 UI 类断言此前唯一的真通道是
instrumentation——本机跑不了、CI 上还压着 19 条真失败。没有这台仪器，后面每处 UI 改动都只能继续交"我看过了"。
拆 store 不产生这类证据。

### 仪器（`app/src/test/…/core/testing/`）

`UiMatrix`（320/360/412/600dp × 字体 1.0/1.3/2.0，12 格）、`SemanticsProbe`（读语义树的尺）、
`UiProbeApplication`（空壳 App）。Robolectric 4.14.1 + `ui-test-junit4`，走 `testDebugUnitTest`
→ CI 的 verify job 每次都跑。两个坑（都是本机踩出来的，写进类注释）：

1. **`@GraphicsMode(NATIVE)` 是前提**。legacy 模式下文字度量是假的：同一个节点
   32x39dp（legacy）→ **224x23dp**（NATIVE）。少了这行，"实测"两个字对任何由文字撑开的尺寸都不成立。
2. 真 Application 的 `onCreate` 会 `startKoin`，同一 JVM 沙箱第二个用例就
   `KoinAppAlreadyStartedException`——四格红在装配阶段，与被测控件无关。空壳 App 顺便也成了
   "这个控件没偷偷依赖全局单例"的证据。

### 量到并修掉的三处

| 处 | 改前实测 | 改后 | 提交 |
|---|---|---|---|
| 三段模式切换的可点击节点 | **84x18dp / 85x18dp / 85x18dp**，role=无（外层 48dp 的 Box 根本不可点） | 116x48dp 一整列，`Role.Tab`，selected 随模式走 | `c927b2e` |
| 空态蓝字（主动发唯一入口） | **224x23dp**，role=无，两种文案还是 `Text(text = if (…) "中文" else "中文")` | ≥48dp 的 Box + `Role.Button`，文案进 strings 两份 | `9f4747a` |
| 收起按钮的标签 | **`Collapse panel+Collapse panel`**（热区 Box 与内层 Icon 各声明一次，合并后念两遍） | 标签只在可点击那一处，图标显式装饰 | `ead80c1` |

### §2.1 那张合同表挪到了必跑的地方

`ReplyPrimaryActionsContractTest` 6 格：REPLY 无结果=全宽「生成回复 · N条消息」、
N=0 **灰着不能点而不是消失**（所以要给"可交互"判据加上 `Disabled`：只认 `hasClickAction`
的话这两种实现会一起判绿）、有结果=重试/记入知识库且主动发不得占位、PROACTIVE 空闲=全宽生成开场、
任一生成中=唯一停止入口（LOADING 有无限脉冲动画，那一格把测试时钟改成手动推进）。
守它的还是那批 instrumentation 的另一半，两边不互相替换。

### 本轮的两笔自报

1. **`UiStringLiteralBudgetTest` 的尺有盲区**：TEXT 正则只认 `Text("…` / `Text(text = "…`，
   看不见 `text = if (…) "中文" else "…"`. 本轮搬进资源的那 2 处**本来就不在 209 里**，
   所以预算数字没动——动了就是把没还的账记成还掉了。**209 这个数字因此是下界，不是全量。**
2. **仪器自己带进来两条 lint 债**（`ComposableNaming`、`TestManifestGradleConfiguration`），
   被 `check_lint_budget.sh` 当场拦下（73 > 71），没抬预算、改代码过关（`6177cd0`）。
   顺带记一次：`P1-02` 这个编号被我写进生产注释，`strip_ticket_ids.py --check` 也红了——这两条闸有效。

### 本轮能红的能力（变异反证）

| 注入的反例 | 结果 |
|---|---|
| 未改动前的生产（真实缺陷） | 三段 48dp 那格红、空态两格红、"恰好一段选中"红 |
| `selected = true` 写死 + 收起标签清空 | "念两遍/标签"与"恰好一段选中"红（本来已绿的断言证明不恒真） |
| `replyEnabled = true`（N=0 也放开） | "N=0 必须带 disabled"红（实测 97x48dp） |
| 生成按钮去掉 `fillMaxWidth()` | "全宽"那格红（实测 193x48dp） |

四组都是字节级改写 + 跑完逐字节复原（前两组用 sha256 比对，后两组用 `git checkout --` 后 `git status` 干净）。

### 本轮收尾实测

**1062 单测 / 130 套件 / 0 失败 / 0 错误 / 0 跳过**（1045 → 1062 全部是本轮新增 17 格；
XML 逐个比过 mtime，无陈旧报告冒充）；lint **71 条 / 15 规则**与预算一致、0 error；
`compileDebugAndroidTestKotlin` 通过；跨层 **6** 条；取消审计 167 站点 NEEDS_REVIEW=0；
工单编号 PASS；prompt 零 diff + lock 匹配 `6dcde732…`。
行数没降：VM **2746**、仓库 **1942**、`ResultArea` **1305**；>500 行 **18** 个、>800 行 **10** 个——
与指导书 P1-01 报的持平，本轮没靠切文件凑数。
Robolectric 首次跑要在 CI 下载 android-all 与 native 运行时，**CI 侧时长本轮没实测**。

## 2j. 第十轮：§5.3 第二格——目录枚举只留一个回答者（`df1e802`）

`KnowledgeRepository` 1942 → **1941 行**，新文件 `KnowledgeCatalogStore.kt` **83 行**：
**总量涨 82 行**。这一格买到的不是体量，是"同一件事不再有两份判据"——
先按指导书 §6 那句"文件变小不是目标"把话说在前面，别把这次记成降行数。

拆它的直接理由是一处**真实的行为分叉**：`listAll()`（公开）与 `listAllUnlocked()`（无锁）
各写了同一套"扫目录 → 读 kb.json → 校验 name 与目录名等值 → 按 updatedAt 倒序"，
两份唯一的差别是**只有公开那份在被挡下时记日志**。也就是说"这个坏掉的库会不会被说出来"
取决于调用方走的哪条路——上轮 §3 第 7 条那类"搬家搬掉观测"的坑，同一个形状。

新类的形状照 `5a21ec2`（backup 那一格）：不持锁、不写盘、不接 `CoroutineScope`；
拿到的能力面是只有 `catalogRoot` + `decodeMeta` + `onMetaRejected` 三件事的 `CatalogStorage`，
**不复用** `KbStorageAccess`、**不复用** `BackupStorage` 那道写门（列目录不该拿到改库的权限）。
解析仍由仓库那份 `Json { ignoreUnknownKeys… }` 注入，避免两处各配一把尺。

### 5 格新用例 × 5 个变异，每刀各咬红一格

| 注入的反例 | 红掉的那格 |
|---|---|
| 去掉"以 `.` 开头的目录不是库"过滤 | `hidden directories are not libraries` |
| 放行 name 与目录名不符的条目 | `a library whose metadata disagrees…` |
| 解不开 kb.json 时不上报（静默丢） | `one broken library drops itself…` |
| `sortedByDescending` 改成 `sortedBy` | `libraries come out newest first` |
| 根目录不存在时 `listFiles()!!` 抛出去 | `an empty or missing root is an empty list…` |

第一格值得单记：夹具原本只放了一个"没有 kb.json 的 `.backup/`"，那样的话把过滤那行删了
它也照样绿——**尺子量不到东西**。补一个"带合法 kb.json 的隐藏目录"之后才有牙。
跑完逐字节复原，sha256 `f6dce644…` 与跑前一致，`M[1-5]` 标记 0 处残留。

### 收尾实测（`df1e802`）

**1067 单测 / 131 套件 / 0 失败 / 0 错误 / 0 跳过**（XML 逐个比过 mtime）；
lint **71 条 / 15 规则**与预算一致、0 error；`compileDebugAndroidTestKotlin` 通过；
跨层 **6** 条；取消审计 167 站点 NEEDS_REVIEW=0；工单编号 PASS。
`KnowledgeRepository` 里剩下没出去的仍是 §5.3 列的那几格：
`create/delete/setActive/updateDisplayName/ensureInitialKnowledgeBase`（catalog 的写侧）、
document / profile / memory / round。

## 3. 明确没做到 / 没法在本机做到的（不混进上面）

1. **19 条真机 instrumentation 失败还在**。本轮只做到：把 7 条同源的夹具竞态改掉、
   给 29 处可见性断言加几何诊断、把重复 modifier 链清掉。是不是转绿要等同一 SHA 的
   `ui-test` 产物；没看到 XML + logcat + 真实 requestCount 之前，
   "点击生成回复不崩溃"仍然不许写成已修复。
   §2i 之后要补一句区分：**"合同成不成立"与"真链路上崩不崩"现在是两把尺**——
   前者（四行主操作区 + 空态入口 + 三段热区/角色/标签）已有 JVM 用例，verify job 每次跑、本机已绿；
   后者（请求数、重试次数、连接取消、崩溃）仍旧只能等 CI，两者不互相顶替。
2. **egress 六格里有 4 格本机判不了**（没有 tshark）。CI 里 `apt-get install tshark` 之后
   才算真判过；脚本没有"没装就跳过"的分支，装不上就是红。
3. **Service destroy 那两格是 Assume 主动跳过的**（instrumentation 起不了悬浮窗/FGS）。
   报告里算 skipped，不算通过。
4. **阶段二剩下两件**：①KnowledgeRepository 按
   catalog/document/profile/memory/migration/archive/round 拆（§5.3，且必须共用一个
   `KnowledgeTransactionManager`，不许每个新类各自 new Mutex）；
   ②§5.2 第 6 步"VM 退成薄 facade 然后删掉"——五条链的状态持有者连同"模式"已经全部出去（§2f），
   但 VM 反而从 2651 涨到 2746 行，**这 95 行就是 facade 那一步要还的债**；
   『两个 enum 仍在 VM 里、主动发的模式没搬完』那一条本轮做完（§2f：`afbe303` 搬 enum、
   `b1df35a` 把模式归 ProactiveStore）。
   端口层已铺好（domain 不再 import data，越界 15→6），所以这些都是"往上搬"，
   不必边拆边补依赖。§5.1 的 `core/designsystem` / `core/testing` 目录也都还没建
   （`feature/` 下五个 store 算开了个头）。
   仍然要认的一条：KnowledgeRepository 拆类**才开始**——§2h 只搬出了 backup 一格（2001 → 1942 行），
   catalog/document/profile/memory/archive(导入导出部分)/round 还都在里面；
   它同时是"最大的一次性改动"和"最长文件之一"这件事没有变。
5. **阶段三：组件体系那一半仍然一行没动**（`grep "fun Lb[A-Z]"` 生产源码 0 命中，实测）。
   本轮改动的部分是"验收它的那台仪器"：§6.5 的语义树/尺寸/角色/标签已能在 JVM 上跑
   （见 §2i），截图工具（Roborazzi 或 Paparazzi 二选一）**仍没接**——1.24.0 的
   `roborazzi`/`roborazzi-compose`/`roborazzi-junit-rule` 坐标本轮核过真实可取，
   但没接进来、没 baseline、没有人工 review 流程。Home/Usage/Provider/Feedback 重写、
   Panel/ResultArea 的 modal host、`ScreenState` 四态统一都还没开始。
6. P1-05 文案收口基本没动（本轮除 §2i 搬掉那 2 处之外没搬过别的）。宽尺测到的数是 **209 处**
   用户可见中文字面量，见 §2c 对 "101 处" 的纠偏——101 只数了 `Text("中文`，是下界。
   `UiStringLiteralBudgetTest` 的闸已装上，剩下的是还债速度。
   **§2i 又发现这把尺还有一层盲区**：`Text(text = if (…) "中文" else "中文")` 这种写法它看不见，
   所以 **209 本身也是下界**；要先补判据再谈"还清"，否则搬掉的记不进账、留下的也数不全。
7. "陈旧事件被拒"的日志——**已补齐**（`41536ad`，见 §2g）。五次搬 store 一共带走 4 条
   `L.w("… rejected (stale requestId)")`；现在 reply / suggest / proactive / counseling /
   rewrite 五处在拒绝时都有一条，判据仍在 store 与 reducer 里，日志由 VM 写。

## 4. 脚本索引（本轮新增/改动的可执行件）

| 脚本 | 用途 | 怎么跑 |
|---|---|---|
| `scripts/test_verify_network_egress.sh` | 抓包判据的六格正反自测 | `bash scripts/test_verify_network_egress.sh`（要 tshark+python3；`WORK_DIR=…` 可留产物） |
| `scripts/make_egress_fixtures.py` | 生成 5 个合成 pcap；`--inspect` 自带解析器复述内容 | `python scripts/make_egress_fixtures.py [--out DIR]` |
| `scripts/package_deps_report.sh` | 实测跨层 import，输出可直接贴进棘轮基线 | `PYTHON=python bash scripts/package_deps_report.sh [--count]` |

## 5. 下一步（按依赖顺序，不跳步）

1. 说一声「推送」→ 把本地领先 `4795471` 的那一串提交推上去
   （多少个别抄数，现算 `git rev-list --count 4795471..HEAD`），等同一 SHA 的
   `verify` / `ui-test` / `upgrade-test`；
   `ui-test` 若还红，用新加的诊断输出定位那 8 条"not displayed"是没测量、被裁还是出窗口；
   新加的 3 格语义树断言（48dp / selected / contentDescription）**预期可能红**，
   那是把假绿换成真信号，不是回归。
2. 阶段二收尾（五条链与"模式"已归位、被拒日志已补齐，只剩一件大的）：
   §5.2 第 6 步——VM 退成只组合 StateFlow 的 facade，然后**删 facade**（调用点直连 store）。
   这一刀才是把 VM 从 2746 行往下压的那一刀：前面七步搬完它反而涨了 95 行。
   做的时候顺手确认一件事——`RewriteStore` 把"这轮改写算不算数"收进在途身份之后，
   coordinator 与它各持一半身份（requestId 同源、两处判）；若把租约直接注进 store 的
   `isCurrentRequest`，别留下第二本账。
3. KnowledgeRepository 按 §5.3 拆 catalog/document/profile/memory/migration/archive/round，
   共用一个 `KnowledgeTransactionManager`（不许每个新类各自 new Mutex）。
   **已有的缝可以照抄，不要另起一套**：`KnowledgeMigrator` + `KbStorageAccess` 就是这条模式的
   第一次落地——协作类不持锁、不碰路径拼接，所有文件动作经 `RepoStorage`（一个只暴露无锁原语的
   受限内部视图）回到本类的唯一一把 `fileMutex`。migration 因此**已经算拆出去了**，
   剩下六项按同一个形状往外搬即可。
4. P1-05 的真正收口：把宽尺测到的 209 处中文字面量搬进 strings.xml / values-en，
   每搬一批就把 `UiStringLiteralBudgetTest` 的预算改小（闸已装上，剩下是还债速度）。
5. 阶段三：**语义树那半已经有一台能跑的仪器**（§2i），所以顺序改成
   ①把这台仪器推到其余页面（`LoveBrainPanelScreen` 其它可点控件、Home/Provider/Feedback/知识库/捕获范围，
   并补 `stateDescription` 与高度维度）→ ②`core/designsystem` token + 11 个 `Lb*` 组件 + `ScreenState` 四态
   （每页一格提交，改完当场用语义树测尺寸/标签，不靠"看着对齐了"）→ ③最后才接截图工具做像素与 baseline
   （现在接，baseline 会在组件收敛后整批作废）。
   ④把文案那把尺的盲区补掉（`text = if …` 这种写法）再谈 P1-05 还了多少。
