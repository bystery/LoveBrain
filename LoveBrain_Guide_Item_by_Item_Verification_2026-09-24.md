# LoveBrain × 三阶段指导书 逐条对照（2026-09-24，本窗口）

> 被对照的输入：`LoveBrain_Three_Phase_Reaudit_and_Six_Principles_UI_Architecture_Guide_c0ff0415_2026-09-24.md`
> 本窗口的提交（11 个，接在交接文档 `c21b200` 之后）：
> `cb44ceb` `6177cd0` `c927b2e` `9f4747a` `ead80c1` `2ef47ac` `f5aed70` `df1e802` `1e1600c`
> `cd7e1df` `e359930` `ca76bac` `d902514`。领先远端 `4795471` 的条数别抄这里，
> 现算：`git rev-list --count 4795471..HEAD`。
> 上一轮的过程证据：`LoveBrain_Three_Phase_Execution_Log_c0ff041_guide_2026-09-24.md`
> 上一轮的交接：`LoveBrain_Handover_Unfinished_Work_2026-09-24.md`

## 0. 这份表怎么读（判据先说清楚）

三种状态标法，不许混用：

- **本机已验**：数字/结论来自本窗口某次命令输出，命令就写在旁边。
- **沿用上轮（未复验）**：上一轮执行记录里有，本窗口没重跑。不当作本窗口的战果。
- **只能等 CI**：本机没有 system image / 没有 tshark，写"完成"就是撒谎。

### 0.1 本窗口实测基线

| 量 | 命令 | 本窗口实测 |
|---|---|---|
| 单测 | `./gradlew :app:testDebugUnitTest` + 逐 XML 解析 | **1082 tests / 134 套件 / 0 失败 / 0 错误 / 0 跳过**（无陈旧 XML：逐个比过 mtime；`ca76bac` 之后。1045 → 1077 的 32 格全部来自本窗口） |
| lint | `:app:lintDebug` + `check_lint_budget.sh` | **71 条 / 15 条规则，预算一致，0 新增债**；0 error |
| androidTest 编译 | `:app:compileDebugAndroidTestKotlin` | 通过 |
| 跨层越界 | `package_deps_report.sh --count` | **6**（与基线一致，没长） |
| 取消审计 | `audit_cancellation.py` | 站点 167，NEEDS_REVIEW=0 |
| 工单编号 | `strip_ticket_ids.py --check` | PASS（我自己写注释踩到一次，被这条闸拦下后改掉） |
| prompt | `git diff --exit-code 286c9406..HEAD -- assets/engine` + `asset_hashes.sh --check` | **零 diff**；lock 匹配 `6dcde732…` |
| 大文件 | 逐文件行数统计 | 生产 Kotlin 125 个 / >500 行 **18 个** / >800 行 **10 个**（与指导书 P1-01 报的数量持平，见 §3.2 行） |
| VM / 仓库 | `wc -l` | `LoveBrainViewModel.kt` **2746**；`KnowledgeRepository.kt` **1941**（`df1e802` 拆出 `KnowledgeCatalogStore.kt` **83** 行，总量涨 82——那一格买的是所有者唯一，不是行数） |
| 深色主题 | grep `darkColorScheme\|DayNight` | **零命中** → 产品只有浅色，见 §6.5 第 5 栏 |
| 抓包判据 | `scripts/test_verify_network_egress.sh` | `CANNOT-VERIFY tshark not installed`（本机判不了，不是通过） |

### 0.2 本窗口新装的仪器（下面很多"本机已验"靠它）

`app/src/test/…/core/testing/`：`UiMatrix`（320/360/412/600dp × 1.0/1.3/2.0 的坐标）、
`SemanticsProbe`（读语义树的尺）、`UiProbeApplication`（空壳 App）。
Robolectric 4.14.1 + Compose `ui-test-junit4`，走 `testDebugUnitTest` → **CI 的 verify job 每次都跑，本机也跑**。

两条前提，踩出来的：
1. 必须 `@GraphicsMode(NATIVE)`。legacy 图形模式下**文字度量是假的**：同一个节点
   先量到 32x39dp，换 NATIVE 才是 224x23dp。少了这行，"实测"对任何由文字撑开的尺寸都不成立。
2. 纯控件用空壳 Application。真的 `LoveBrainApp.onCreate` 会 `startKoin`，
   同一 JVM 沙箱第二个用例就 `KoinAppAlreadyStartedException`（四格全红在装配阶段）。

---

## 1. §3.1 P0 五条（发布阻断）——逐子条

| 指导书子条 | 状态 | 证据 / 还差什么 |
|---|---|---|
| 修 `run_ui_tests.sh` 空 grep（grep 1 → 0） | 沿用上轮（未复验） | 上轮 §2b 记了改动；本窗口没跑该脚本（本机无设备） |
| `verify` 里加 `:app:compileDebugAndroidTestKotlin` | 沿用上轮（未复验）+ 本机复核 workflow 第 69 行确有此步 | 同一 SHA 的 CI 产物仍缺 |
| 补 AndroidTest import | 沿用上轮（未复验） | 本窗口 `compileDebugAndroidTestKotlin` **通过**，说明编译层面成立；运行结果仍要 CI |
| UI job 真打印 Gradle task、非空 XML/HTML、报告数=源码 `@Test−@Ignore` | 只能等 CI | 本机无 system image |
| prompt hash 用 canonical LF，禁改 prompt 迎合 lock | **本机已验** | `asset_hashes.sh --check` OK + prompt 目录零 diff（§0.1） |
| `upgrade-test` 改成 `needs: [verify, ui-test]` | **本机已验** | `ci.yml:280` 实测就是 `needs: [verify, ui-test]` |
| **P0-01 总红→全绿** | **只能等 CI** | 远端仍在 `4795471`，本地领先 **48** 个提交；没推送就没有同一 SHA 的三项绿 |
| **P0-02 十格真链路** | 部分（本窗口推进了 4 格的可运行化） | 主操作区五行合同的**布局/禁用/唯一停止入口**现在有 JVM 用例（`ReplyPrimaryActionsContractTest` 6 格、`MessageListEmptyStateTest` 4 格），进 verify job；但"1 请求 / 不重试 / 连接取消 / requestCount 恒 0"这些**必须**真机 + logcat + 真实计数，本机给不出 |
| **P0-03 未来 schema 只读保护** | 沿用上轮（未复验其格数）+ 本窗口跑过那套套件 | `ReadOnlySchemaWriteGateTest` **5 格全绿**（本轮实测计数）；上轮记"其中一格遍历 24 个公开写入口"，这个 24 本窗口未复验 |
| **P0-04 零遥测抓包** | 本机只能判 2/6 格 | `test_verify_network_egress.sh` 实跑 → `CANNOT-VERIFY`（无 tshark）。README 那句"未通过抓包证明"必须继续留着 |
| **P0-05 WAL 承诺** | 沿用上轮（未复验）+ 本窗口跑过那套套件 | `RoundCommitJournalTest` **19 格全绿**（本轮实测计数）；`TopicRecorder` 仍 1009 行（>800 那一档没动） |

## 2. §3.2 P1 五条（高风险工程债）

| 条目 | 状态 | 证据 / 还差什么 |
|---|---|---|
| **P1-01** 18 个 >500 行、10 个 >800 行 | **数量没动**（本机已验） | 本窗口只改了 3 个文件的实现，没有一个靠"切文件"降行数（指导书 §6 硬约束：不许为行数机械拆文件）。`ResultArea` 仍 1305 行 → §6.4 那一格没做 |
| **P1-02** 语义树边界断言，不许源码搜索 | **本窗口做了四处，仍不全** | 已覆盖：PanelHeader（12 格矩阵）、空态入口、主操作区、锦囊折叠入口、**供应商页整页**（5 格，`d902514`）。实测值：改前 **84x18dp**（三段）与 **224x23dp**（空态蓝字），改后 ≥48dp。**还差**：`LoveBrainPanelScreen` 其余可点控件、Home/Provider/Feedback/知识库各页；锦囊卡片的折叠入口已量已修（`ca76bac`：实测 **344x17dp**，320dp+2.0 倍字时 304x33dp → `heightIn(min=48)` + `Role.Button`，并断言公告随状态变、展开后示例配文真出现）；**仍欠**谈心面板那颗『继续追问』胶囊——整页要 `LoveBrainViewModel` 才能组合，本机测不到（§5.2 第 6 步的下游之一） |
| **P1-03** `GenerationActionButton` modifier 链重复 | 沿用上轮（未复验） | 本窗口读过该文件，NORMAL/DISABLED 只剩一条链（`size→graphics→shadow→clip→background→clickable→padding`），LOADING/STOP 分支各自一条——但这是**我读到的现状**，不是本轮改动 |
| **P1-04** Koin 双 journal 注册 | 沿用上轮（未复验）+ 本窗口 grep 过 | `AppModule.kt:55` 现为 `single { TopicRecorder(get(), get(), get()) }`，`:51` 只有容器那一份 journal |
| **P1-05** 209 处中文字面量 | **先修尺，再还 4 处** | 搬进 `strings.xml`/`values-en`：`proactive_empty_send_one`、`proactive_empty_turn_off`。**盲区**：`UiStringLiteralBudgetTest` 的 TEXT 正则只认 `Text("…` / `Text(text = "…`，看不见 `Text(text = if (…) "中文" else "中文")`——我搬掉的这两处**本来就不在 209 的计数里**，所以预算数字当时没改（改了就是假账）。本轮把尺换成按括号配对截整段实参，**209 → 254**：
这 45 处不是新塞的中文，是原来漏量的。之后搬进资源 4 处（空态两种文案 + 反馈页的"重试"/"暂无反馈案例"），
预算 **254 → 252 → 250**（棘轮只许往下）。DESC 在第三次修尺后实测 **12**（此前那个 10 同样是下界：赋值锚点的切片会被 if 的内层括号截断）。剩下 250 处没动。
**又发现第二个盲区并补上**：`stateDescription` 是念给用户听的话，TEXT/DESC 两把尺都看不见它——已加 STATE 判据，起点 0（`ca76bac`） |

## 3. §3.3 P2 五条

| 条目 | 状态 | 说明 |
|---|---|---|
| `GenerationInput.fingerprint()` 死 API | 沿用上轮（本窗口 grep 复验：`fun fingerprint` 在 `app/src/main` **零命中**） | 已删 |
| prompt"冻结"只冻 hash | 未做（本窗口没碰） | 要么文档改口，要么进 Engine 前冻结完整 `PreparedPrompt` |
| 验收包 `LoveBrain_Rework_Acceptance_2026-09-24.md` 的过期表述 | 未做（本窗口没改它） | 指导书点名它"全部写路径统一拒绝""仍用冻结 prompt"与实现不符 |
| BENCHMARK 数字只叫历史参考 | 沿用上轮 | 本窗口没动 BENCHMARK.md |
| lint baseline + 新增 warning=0 | **本机已验（现状即达标）** | `check_lint_budget.sh` 逐规则登记，实测 71/15 条规则一致；本窗口两次新增（`ComposableNaming`、`TestManifestGradleConfiguration`）都被这条闸拦下并当场修掉，**没有靠抬预算过关** |

## 4. §4 六大设计原则（逐行）

| 原则 | 指导书的可验收标准 | 状态 | 证据 / 还差什么 |
|---|---|---|---|
| SRP | 改锦囊不碰 Reply Store；改 KB 迁移不碰编辑 UI | 部分 | 五个 feature store 在 `feature/`（上轮）；本窗口没动。仓库侧 §5.3 只拆出 2 格（backup/migration），**catalog/document/profile/memory/round 还在全在里面** |
| OCP | 新增一种模式只新增实现与注册，不改已有 reducer | 未验 | 本窗口没做过"新增一种生成模式"的演练 |
| LSP | 同一 contract suite 对 production adapter 与 fake 都通过 | **本机已验（存在且全绿）** | `FakeAiGatewayContractTest` 4 格 + `ProductionAiGatewayContractTest` 4 格；`InMemoryKnowledgePortContractTest` 8 格 + `FileBackedKnowledgePortContractTest` 8 格；`KnowledgeEngineContractTest` 12 格 |
| ISP | Home 调不到知识库内部写；Reply UI 看不到 Provider CRUD | 部分 | 端口层已有（越界 6 条），但 VM 仍是 2746 行的"什么都暴露"门面（§5.2 第 6 步没做） |
| DIP | domain 不 import Android/Koin/File/具体 Repository | 部分 | `PackageDependencyTest` 6 格棘轮生效，跨层实测 6 条；`viewmodel` 仍有 `java.io.File` 两处（基线登记内） |
| 迪米特 | composable 参数不出现 Repository/Prefs/Context | 部分 | `UiLayerDependencyContractTest` 6 格在跑（本轮计数）；本窗口没新增违规 |

## 5. §5 目标架构

### 5.1 目录
`core/model`、**`core/designsystem`（本窗口建了：`ScreenState.kt` + `LbAsyncState.kt`）**、
`core/testing`（**本窗口建了，但在 test 源下**：`app/src/test/…/core/testing/` 三件）、
`domain/port`（上轮）、`data/*`、`platform/*`（未做）、`feature/*`（上轮五个 store）。
**还差**：`core/designsystem` 的 token 与组件（`grep "fun Lb[A-Z]"` 在生产源码 **0 命中**，实测）。
另记一笔坐标差异：本窗口的 `core/testing` 只装了"读 UI 的仪器"，
已有的假端口（`FakeAiGateway` 等）住在 `app/src/test/…/domain/port/` 下，
**没有**和 `core/testing` 并到一起——§5.1 那张目录图要的是后者，这一格还没归位。

### 5.2 六步（五个 store + facade）
1–5 步（Reply/Suggest/Proactive/Counseling/Rewrite）沿用上轮，**全部完成**。
**第 6 步：VM 退成只组合只读 StateFlow 的 facade，然后删掉——一行没做。**
VM 本窗口实测 **2746 行**，与上轮交接同值（既没涨也没降，本窗口没动它）。

### 5.3 按能力拆仓库（七格）
| 指导书列的类 | 状态 |
|---|---|
| `KnowledgeMigrationService` | 已有（`KnowledgeMigrator`，上轮之前） |
| `KnowledgeArchiveService`（backup 部分） | 已有（`KnowledgeBackupService`，上轮 `5a21ec2`） |
| `KnowledgeCatalogStore` | **拆出一半**（`df1e802`）：目录枚举 `listAll/listAllUnlocked` 已有唯一所有者 + 5 格用例；`create/delete/setActive/updateDisplayName/ensureInitialKnowledgeBase` 这五个**写侧**动作还在仓库里 |
| `KnowledgeDocumentStore` | **未做** |
| `KnowledgeProfileStore` | **未做** |
| `KnowledgeMemoryStore` | **未做** |
| `RoundCommitStore` | **未做** |
| 共用一个 `KnowledgeTransactionManager`、新类不许各自 new Mutex | 现状满足（仓库唯一 `fileMutex` + `RepoStorage` 受限视图），本窗口没动这块，也没新增锁 |

## 6. §6 UI 架构与交互

### 6.1 十一个 `Lb*` 基础组件
**2 / 11**（`e359930`）：`LbEmptyState`、`LbAsyncState`——都带语义树用例，动作热区 ≥48dp 与 `Role.Button` 是量出来的。剩下九行没建： `LbScreenScaffold` `LbTopBar` `LbSection` `LbPrimaryButton` `LbActionCard`
`LbSettingRow` `LbMetricCard/Grid` `LbEmptyState` `LbAsyncState` `LbModalSheet/Dialog` `LbStatusBadge`
一个都没建（`grep "fun Lb"` 在生产源码零命中）。**这是阶段三"一行没动"的主体。**

### 6.2 首页固定四段 — 未做（本窗口没碰 Home）。

### 6.3 `ScreenState` 四态统一 — **一个目的地已换，其余没动**（`e359930`）

供应商页（`d902514`）先被量到并修热区/本地化；它的空态现已走统一版式 `LbEmptyState`，并就地给出「添加供应商」，页面下方那个重复的第二个入口一起收掉（`eb8666c`，由一格断言盯着「空态里只能有一个添加入口」）。**供应商页现在也走这套**：空/有内容由同一个 `ScreenState` 判定、由 `LbAsyncState` 画，页面里不再各画一遍（`d902514`→`eb8666c`→本提交）。剩下三个目的地：知识库、捕获范围，以及反馈页之外的 Loading/Error 维度（供应商页现在只有 Empty/Content 两格，这一页本来就没有 loading/error 流——不假造状态）。

`ScreenState`（Loading/Content/Empty/Error）与 `LbAsyncState` 已建；**反馈案例页**换完：
原来三个分支各画一套居中文版式，现在一处判定 + 一套版式，页面 **534 → 500 行**，两处中文进了资源。
判定顺序与替换前逐字相同（Loading > Error > Empty > Content）。
**还差**：Provider、知识库、捕获范围三个目的地。

### 6.4 ResultArea 拆独立 state holder + modal host — 未做，实测仍 **1305 行**。

### 6.5 无障碍与视觉门禁九栏（逐栏）

| 栏 | 状态 | 本窗口实测 |
|---|---|---|
| ①所有 clickable/toggleable 边界 ≥48×48dp，不是在源码里搜常量 | 部分（三处已改用语义树） | PanelHeader 12 格、空态、主操作区。**还差**：面板其余控件、Home/Provider/Feedback/知识库/捕获范围 |
| ②TalkBack role / selected / disabled / stateDescription / contentDescription | 部分 | role：三段现在 `Tab`、空态入口现在 `Button`（实测）；selected：随模式走（实测）；disabled：N=0 那格（实测）；contentDescription：断言在跑，另修掉一处"念两遍"；stateDescription：锦囊折叠入口**已被断言**（收起→展开公告跟着变，`ca76bac`）；两处公告文案原来都是内联中文，现已进 strings（英文环境念 Expanded/Collapsed）。谈心那颗仍无断言（要 VM 才能组合） |
| ③字体倍率 1.0 / 1.3 / 2.0 | 部分 | PanelHeader 跑满 12 格（含 1.3）；另外三处只在 1.0 与 2.0 的部分格 |
| ④宽度 320/360/412/600dp，高度覆盖小屏与长屏 | 部分 | 四个宽度都量到（PanelHeader）；**高度维度没做**；且窗口固定在 600dp，窄格是给被测子树加约束得来的（`UiMatrix` 注释里写明了这个边界，不假装换了台设备） |
| ⑤浅色/深色；不支持深色就明确锁浅色 | 未做（决定已可下） | 实测 grep `darkColorScheme`/`DayNight` **零命中** → 产品只有浅色。需要写死"锁浅色"，而不是做半套主题 |
| ⑥中文、英文 | 部分 | JVM 侧解析到 `values-en`（英文环境下念中文会红——空态那格就是这么被抓出来的）；**中文环境没测**，需要按 locale 分格 |
| ⑦长 Provider 名、超长模型名、`￥9999.999`、`100000 次生成` | 未做 | 这些在 Provider/Usage 页面，需要先有那些页的语义树用例 |
| ⑧颜色对比度；STOP 不能浅灰底+白字 | 未做（但把前提查了） | 实测 `Neutral200 = hsl(250, 6%, 40%)`，不是"浅灰"；**没算过对比度，也没门禁** |
| ⑨baseline 变更必须人工 review，禁止自动覆盖后判绿 | 未做 | 截图工具没接（下条） |

### 关于截图工具（指导书 §6.5 / §7 第三步第 4 条 / §8 二选一）
**没接。** 本窗口做过的事：确认 `roborazzi` / `roborazzi-compose` / `roborazzi-junit-rule`
在 1.24.0（Kotlin 1.9.24 时代）坐标真实可取，也确认本机 Maven 可达。**没做且没假装**：
接进来、生成 baseline、按人工 review 流程走。
判断依据：指导书把"截图矩阵"排在**改完 UI 之后**验收（§7 第三步第 4 条），而 §6.1–§6.4 一行没动；
现在拍的每一张都会在组件收敛后作废，"人工 review 过的 baseline"也就成了空转。
**先决条件已经补上的是语义那一半**（尺寸、标签、角色、状态），像素那一半仍缺。

## 7. §7 三步执行计划——每步"完成定义"逐条

### 第一步（恢复可信发布门禁）：**没完成**
- unit ≥920、0 failures/errors → **本机已验**：1073 / 0 / 0。
- AndroidTest compile success → **本机已验**。
- 40 个 instrumentation 全运行且报告数与源码一致 → **只能等 CI**。
- UI XML/HTML、截图、logcat 非空 → **只能等 CI**（截图仓库侧根本没有）。
- upgrade evidence 非空且 v1.3.1 数据可读 → **只能等 CI**。
- release R8 candidate、metadata、SBOM → **只能等 CI**。
- Actions 总结全绿 → **没有**：远端仍 `4795471`，本地领先 48。

### 第二步（按六原则拆架构）：**没完成**
- VM 不再持有五条 feature 的内部状态 → 沿用上轮（状态持有者已全搬走）。
- 仓库不再是所有知识能力的唯一入口 → **没达到**：七格里出去 3 格（migration / backup / catalog 的枚举侧），仍 1941 行；catalog 写侧与 document/profile/memory/round 未动。
- 每个 port 有 production/fake 共用 contract suite → 见 §4 LSP 行，**这一条成立**。
- 新增 feature 不修改已有 reducer → **没做过演练**。
- 新代码无 >500 行、现存 >800 行持续下降 → **没下降**（18 / 10，与指导书报的持平）。本窗口新增的都是测试与 3 个既有文件的改动。

### 第三步（统一 UI 与交互）：**基本没开始，但有了一条能量尺寸的仪器**
- 首页四段稳定 → 没做（Home 一行未动）。
- Provider/捕获/反馈同一套 row/card/state 语法 → 没做。
- 反馈案例不内联堆叠 → 沿用上轮（已独立路由）；本窗口另把它换成 §6.3 的四态语法（`e359930`）。
- 主动发合同未变化 → **本窗口验证过并钉住了**（6 格 JVM 用例）。
- 360dp + 2.0x 无裁切/无遮挡/无 <48dp 热区 → **热区那一半：PanelHeader 在 320dp+2.0x 下实测达标**；"无裁切/无遮挡"要像素，没做。
- 关键屏幕 baseline 已 review → 没做。

## 8. §9 给 worker 的九条执行规则 + 交付清单

| 规则 | 本窗口 |
|---|---|
| 1 不改 prompt | 遵守：prompt 目录零 diff（实测） |
| 2 一个提交一个验收目标，写用户行为 | 遵守：6 个提交各一件事（仪器 / 仪器自身的 lint 债 / 三段热区+role / 空态入口 / 念两遍 / 合同钉死），message 写"用户点得到/念得出/灰着不能点" |
| 3 先写会失败的回归测试，且测试要真进 CI | 遵守且可查：三段 `84x18dp`、空态 `224x23dp`、念两遍、"恰好一段选中"都是**先红后改**；新用例全在 `testDebugUnitTest`，verify job 每次跑 |
| 4 不许用源码 grep 顶替 UI/触摸/无障碍测试 | 遵守：本轮断言一律读语义树；`ProductionUiContractTest` 那类静态闸只当防回退 |
| 5 不许用"脚本存在"代替产物 | 遵守：§6 明写哪些只能等 CI；egress 本机实跑结果是 CANNOT-VERIFY |
| 6 不许新增 `\|\| true` / continue-on-error / 空产物放行 | 遵守：本窗口没动 CI 与脚本的判定分支 |
| 7 行数/测试数/hash/SHA 来自当次命令输出 | 遵守：§0.1 全是本窗口输出。**但要认一笔**：交接文档里"1045 单测"是上轮数，本窗口是 1062，差异全部来自我新增的 17 格 |
| 8 不为行数机械拆文件 | 遵守：没降任何行数，也没切文件 |
| 9 发布前独立复核，worker 不自签 PASS | 遵守：**本窗口不签 PASS**。§0 的结论是"第一步没闭、第三步没开始" |

交付清单九格：精确 SHA（本窗口 HEAD=`2ef47ac`，远端 `4795471`）· base...head（48 个提交）·
三条 check 的 URL 与结论（**没有，未推送**）· unit/instrumentation 数量（1062 / 本机跑不了）·
AndroidTest compile（通过）· screenshot/logcat/upgrade/APK/SBOM（**无**）·
prompt 零 diff + lock（**通过**）· P0/P1/P2 对应 commit（见本文各表）· 未完成项（本文 §9 下一节，单列）。

## 9. 还差什么（不混进上面）

**A. 指导书要求、本窗口一行没做**
1. §5.2 第 6 步：删 VM facade（2746 行）。
2. §5.3 剩余：catalog 的写侧（create/delete/setActive/updateDisplayName/ensureInitial）+ document / profile / memory / round 四格。
3. §6.1 剩下九个 `Lb*` 组件（`LbScreenScaffold` `LbTopBar` `LbSection` `LbPrimaryButton`
   `LbActionCard` `LbSettingRow` `LbMetricCard/Grid` `LbModalSheet/Dialog` `LbStatusBadge`）
   + 把 token 从 `ui.theme` 迁进 `core/designsystem`（这条欠账登记在 `PackageDependencyTest` 的 core 规则旁边）。
4. §6.2 首页四段；§6.3 余下三个目的地（Provider / 知识库 / 捕获范围）；
   §6.4 ResultArea 浮层拆分（1305 行）。
5. §6.5 截图工具与 baseline 人工 review 流程；⑦超长文案/极端数字那一格；⑧对比度；②里 stateDescription 那两处的断言。
6. P1-05 剩下 **250** 处中文字面量。（尺子的三个盲区已补：`cd7e1df` 与 `d902514`；STATE 判据起点 0）
7. §3.3 里"prompt 冻结"与验收包过期表述这两条文档改口。

**B. 本窗口新留下没做的**
1. 语义树断言目前只覆盖 3 个组件（PanelHeader / 空态 / 主操作区），其余页面仍无守卫。
2. `UiMatrix` 的宽度格靠"约束被测子树"实现，窗口仍固定 600dp；真窗口宽度矩阵要靠截图那一格补。
3. Robolectric 首次跑要在 CI 上下载 `android-all-instrumented` 与 native 运行时（本机已缓存）——
   CI 的 verify 时长会变，这一条**没有实测过**。

**C. 只有推送之后才能闭的（指导书的真正卡点）**
P0-01 三项 check 全绿、P0-02 的 10 格真链路 + requestCount + logcat、
P0-04 egress 剩下 4 格（要 CI 的 tshark）、Service destroy 那 2 格（现在是 Assume 跳过，算 skipped 不算通过）、
以及 §3.1 里那 3 格新加的语义树 instrumentation 断言（现在它们在 JVM 与仪器两边都有，本机这边已绿）。

**一句话结论**：第一步仍然只差 CI 证据（本机这边能验的都验了）；
第二步 store 五条 + 端口 contract 成立，仓库拆分成 3/7 格（catalog 只出了枚举侧）；
第三步从"一行没动"变成：一台能量尺寸、能读语义树的仪器 + 用它抓到并修好三处无障碍缺陷 +
交互合同钉进每次必跑的 job + `ScreenState` 与两颗 `Lb*` 组件落地、一个目的地换完。
仍未开始的是：首页四段、其余三个目的地、ResultArea 浮层拆分、截图门禁、facade 删除。
**发布判定不变：NO-GO，且 worker 不自签。**

---

# 追加：接手自 `3d92488` 的那一轮（跨到 2026-09-25 凌晨）

上面整篇是上一窗口写的。本节只记这一轮动过的两格（交接单 §2.1、§2.2）与它们改到的数，
判据仍照 §0 的三态标法：**本机已验**（旁边就是命令）／**沿用上轮（未复验）**／**只能等 CI**。

提交（5 笔，全部未推送）：`eb8ac9a` lint 尺与账本口径 · `f76f64a` 判据自测不再写死数字 ·
`47bbc13` ResultArea 夹具 payload · `555ca48` 文案改资源驱动 + 中英文两侧各一条断言 ·
`6eea6eb` 真链路夹具等入口能点再点 + 修 R2 那句断言。

## 10.0 本轮实测基线（与 §0.1 冲突的两行以本节为准）

| 量 | 命令 | 本轮实测 |
|---|---|---|
| 单测 | `./gradlew :app:testDebugUnitTest` + 逐 XML 解析（GRADLE_RC=0） | **1091 tests / 137 套件 / 0 失败 / 0 错误 / 0 跳过 / 陈旧 XML 0** |
| lint | `:app:lintDebug` + `check_lint_budget.sh` | 报告 **70 条 / 15 规则**；其中**进预算 69 条 / 14 规则**、advisory 1 条 / 1 规则；门禁 rc=0 |
| androidTest 编译 | `:app:compileDebugAndroidTestKotlin` | rc=0 |

§0.1 那句"1082 单测"没算错，是量的时刻不同：它在 22:56 量，早于 `1167c34`（那笔加了 1 格
哨兵）。本轮 1091 = 1083（`1167c34` 之后的实际数）+ 本轮新增 8 格
（`ComposerAddButtonGatingTest` 3 + `ReplyPayloadShapeForUiFixtureTest` 2 + `PanelUiTextLocaleParityJvmTest` 3）。
跨层 6 条 / 取消审计 / 工单编号 / prompt 零 diff / 大文件计数 本轮**未重跑 → 沿用上轮（未复验）**。

## 10.1 §2.1：verify 差的不是一条规则，是两条；两条都不属于这个仓库（本机已验）

用门禁自己的正则对两份 `lint-results-debug.xml` 做集合差（两侧同尺）：

```
CI    : 81 issues / 16 rules        （gh run download 36019334520 -n lint-report）
本机  : 71 issues / 15 rules        （./gradlew :app:lintDebug，Gradle 判 lintReportDebug UP-TO-DATE，
                                      即报告输入就是当前代码；分析中间产物时间戳另验过一次）
只在 CI 出现的规则：OldTargetApi 1 条
条数不同：GradleDependency  CI=10  本机=1
```

两条都是"发现来自仓库之外"：

- `GradleDependency` 量的是这台机器解析到的 Maven 版本清单——**同一句** `androidx.test.ext:junit:1.1.5`
  声明，CI 报"可升到 1.3.0"，本机报"可升到 1.2.1"。
- `OldTargetApi` 量的是本机 SDK 里装了多新的平台（两边 `targetSdk` 都是 35）。

所以交接单给的"二选一"两个选项都不成立：①改代码消不掉（上游还会发新版，且本机根本复现不出
CI 那 10 条）；②按平台各锁一份也锁不住（这个数既不随平台稳定也不随时间稳定，锁住 CI=10
等于给门禁装定时引信）。**选了第三条**，且没有抬预算：

- 这两类进 `advisory` 段：不比预算条数，但每次照样打印条数与登记理由；
- 有资格被降级的规则名单写死在脚本的 `EXTERNAL_RULES` 里（想加一条必须改脚本、上评审）；
  把仓库自己的债降级 → 当场 `FAIL ADVISORY-FORBIDDEN`；advisory 少写理由 / 与预算重复登记
  → `CANNOT-VERIFY`（退出 2）；
- 输出第一行自报这把尺：`ruler: tool=lint version=8.6.0 os=… machine=… report=… budget=…`；
- 挑出去之后**进预算的部分两边逐条相等：70 条 / 14 规则**，其余 14 条登记数字一个没动。
  （`eb8ac9a` 当时是 70；本轮后面那 12 处 finder 改走资源之后还掉了一格 `UnusedResources`，
  于是 §10.0 的现在值是 69——见 §10.3 第 2 条，这两个数都对，只是量的时刻不同。）

判据自身交给 `scripts/test_check_lint_budget.sh`（27 格，已接进 verify），夹具是这两份真报告。
改之前它对旧脚本 20 格红 8 格绿（绿的 8 格是旧行为本来就对的，证明不是一律红）。
`scripts/fixtures/lint/README.md` 记了两份产物的来路。

**结果状态：本机已验（两侧同尺这条）；verify 会不会因此转绿 = 只能等 CI。**

## 10.2 §2.2：23 条失败分完类了，四条来源三种是夹具（分类=本机已验，修复=只能等 CI）

产物：`test-xml-report`（43 tests / 23 failures / 0 errors / 2 skipped）+ 每条用例独立 logcat +
instrumentation HTML。**本轮把 CI 的 5 个产物全下载下来读过**，其中并无任何 PNG
——上一轮交接说"截图 home.png/knowledge-base.png 都产出了"，这句**不成立**，记在此处。

| 类 | 条 | 用例 | 判它的尺 | 处置 |
|---|---:|---|---|---|
| 夹具：把中文文案写死在测试里，而生产早已 `stringResource` | **12** | `MessageListEmptyStateTest` ×4、`ReplyPrimaryActionsTest` ×7、`OverlayGenerateSmokeTest.emptyState_blueProactiveEntry…` ×1 | ① 都报"节点不存在（fetchSemanticsNode 失败）"；② 同文件里**只用 `hasClickAction()` 不认文字**的那格是绿的 → 面板确实组合出来了；③ 生产六个标签全走资源且 `values-en` 各有词条；④ JVM 同名用例断的是英文原文且绿 | `555ca48`：新增 `UiText`（当前配置取文案 / `inTag` 取指定语言 / 由模板现场拼停止棒正则），12 处 finder 改资源驱动，停止棒改用生产 `GENERATE_STOP_TEST_TAG` 定位 |
| 夹具：入口还没带上点击语义就点，`performClick` 静默打空 | **7** | `OverlayGenerateSmokeTest` 的 noProvider_realGenerateButtonTap / stop / 401 / parseFailure / successStream / rapidDoubleTap / timeout | 本机 `ComposerAddButtonGatingTest` 复现：关了 `autoAdvance` 后没有帧就没有重组，草稿没变成 `canAdd=true`，生产 `.then(if (canAdd) clickable else …)` 不给 clickable，而 `performClick` 只注入触摸不查语义。**变异检查**：只加一句 `advanceTimeBy(64)` 这格就红 | `6eea6eb`：`awaitAddEntryActionable` 推帧推到"带点击语义的 ➕"真出现，推不到当场红 |
| 夹具：payload 构造用错 helper | **3** | `ResultAreaInteractionTest` 的 displaysSchemeCards / styleToDirection / directionMode | 生产解析器实测：`parsedSuccess(整段JSON)` 只往 recommended 一格塞正文 → schemes = `[整段JSON,"","",""]`，"推荐回复内容"从来不是任何一格的完整文本；同目录用 `parseProviderText` 的那 6 格是绿的 | `47bbc13`：改用 `parseProviderText`，并把那颗 helper 改名 `successWithOnlyRecommendedReply` + 写明"另外三格是空的" |
| 未决 → 已换成能自证的断言 | **1** | `lateCallbacksFromSupersededRequest` | 红在 `requestCount >= 1`，但那个计数是 R1/R2 **共用同一个 fake 服务累计**的：R1 出过一次门这句就成立 → 这句从来没断过 R2 | `6eea6eb`：先断 R1 `>= 1`、再把 R2 改成 `>= 2` 并打印 isGenerating / providerReady / result / baseUrl。下一次 CI 能自己答出是"请求没出门"还是"迟到事件污染" |

合计 **23**；另 2 条 skipped 是 Service destroy 的 `Assume`，算 skipped 不算通过。
三类里没有一条是"把断言改软"：12 处换了取词来源（并加了中英两侧守卫）、
7 处加了等待条件、3 处修了数据构造、1 处把一句失效的断言修成有效的并多加了一格。

**结果状态：分类与机理=本机已验；这 23 格改完是否真绿 = 只能等 CI（本机没有 system image）。**

## 10.3 本轮新增的欠账与两条尺的性质（别丢）

1. ** composer 输入行是第 5 个可点节点，带 `EditableText` 但没有文案也没有 contentDescription**
   ——读屏念不出它是干什么的输入框。属 §6.5 第②栏，改的是生产码，尚未动。
   （`ComposerAddButtonGatingTest` 逐点数入口时量到的，写进了断言，将来悄悄多成第 6 个会红。）
2. **`UnusedResources` 把 androidTest 的引用也算"已使用"**：本轮 12 处 finder 改走资源之后
   `R.string.panel_mode_proactive` 从"未使用"变成"已使用"，预算 34→33。这条尺证明不了
   "该文案在生产路径上真的可达"，别拿它当用户可见性证据。
3. 两个测试源集之间没有共享目录，夹具文本只能两边各存一份（JVM 那份 `ReplyPayloadShapeForUiFixtureTest`
   与 androidTest 那份要一起改）——这条欠账本质是 §5.1 的 `core/testing` 还没归位。
4. 判据自测**不得把预算数字写死**：上一版写死了 34，本轮合法还一条债就 5 格假红（见 `f76f64a`）。
   历史产物夹具只断"两边进预算的部分逐条相等 + CI 独有规则全在 advisory 里"，不断退出码。

## 10.4 过程坑（本轮新增，都是实测）

- `python -` 从 heredoc 读脚本时按 ANSI 码页解码，**正则里的中文自己先坏**（报
  "unterminated character set"）。要么写成文件再执行，要么用 `\u` 转义。
- `sed '0,/<issue\n/s//…/'` 那种带 `\n` 的模式永远不命中而**退出码还是 0**——
  我差点把一份没动过手脚的夹具当成"变异检查通过"。以后做变异先断言"手脚确实动了"
  （本轮补的写法：`assert head in t`）。
- `git commit` 前那个 `values-en/strings.xml` 一直显示 ` M`，实测
  `git rev-parse HEAD:…` 与 `git hash-object …` **同一个 hash**：内容零差异，只是 stat 缓存脏。
  别为这个"清理"工作树。
- Windows 控制台 GBK 下脚本的中文输出是乱码，**只影响肉眼读日志**，不影响字节文件；
  测试断言一律匹配 ASCII 片段（`STATS` / `ruler` / `ADVISORY <规则>=<n>` / `OVER <规则>`）。

## 10.5 本轮发布判定

**NO-GO，worker 不自签。** 依据指导书 §10：要新 SHA 的三项 required checks 全绿且
artifacts 齐全才算。本轮只把"本机能够自证"的部分做到能证；`verify` 与 `ui-test` 会不会
因此转绿，只有推上去跑一次同一 SHA 才知道——**本轮没有推送**（推送需要用户明确说「推送」）。

### 10.5.1 收口前的机器体检（六道闸，本机重跑，全部带退出码）

| 闸 | 命令 | 结果 |
|---|---|---|
| 单测 | `:app:testDebugUnitTest` | rc=0，1091 / 137 套件 / 0 失败 / 0 跳过 / 陈旧 XML 0 |
| lint 预算 | `PYTHON=python bash scripts/check_lint_budget.sh` | rc=0，进预算 69 条 / 14 规则与账本一致 |
| 判据自测 | `PYTHON=python bash scripts/test_check_lint_budget.sh` | rc=0，27 格全对 |
| androidTest 编译 | `:app:compileDebugAndroidTestKotlin` | rc=0 |
| prompt 未被碰 + lock | `git diff --exit-code 286c9406..HEAD -- assets/engine`；`asset_hashes.sh --check` | 零差异；lock 匹配 `6dcde732…` |
| 工单编号 / 跨层 | `strip_ticket_ids.py --check`；`PYTHON=python bash scripts/package_deps_report.sh --count` | rc=0 PASS；**6**（与基线相同，没长） |

**没验到的部分写清**：`ui-test` 那 23 格（改完 22 格 + 1 格仍待自证）与 `verify` 是否转绿，
本机给不出——没有 system image，`connectedDebugAndroidTest` 跑不了；`upgrade-test` 与那四类
缺失产物同理。一律标"只能等 CI"，不写"已修复"。

顺带一条坑（接 §10.4）：**`package_deps_report.sh` 没有 `check_lint_budget.sh` 那种 python
可用性探针**，Windows 上不带 `PYTHON=python` 直接 exit 49——看着像"检查判失败了"，其实是
根本没跑。同一族脚本里这是个缺口：下次动 scripts/ 时补上探针，缺解释器要给
`CANNOT-VERIFY`（退出 2），不要崩一个谁都看不懂的码。

---

# 追加：第二阶段第一格（§5.3 文档格，提交 `adbf5f3`）

§5.3 七格从 **3/7 → 4/7**。

## 11.1 这一格为什么拆（指导书要的是"一处所有者"，不是"文件变小"）

量到一条真实的宽严不一：公开读 `readFile` 过 `safeKbFile`（拒 `..`、绝对路径、盘符、UNC），而
**无锁快速读 `readFileUnlockedFast` 直接 `File(File(root, kbName), relativePath)`，完全不过守门**。
指导书 P0-03 最后一段写的就是这件事："读取接口也必须走 safeKbFile，canonical boundary
并未覆盖所有 String 入口"。现在两个入口是同一个函数。

顺带抓到更紧的一条：`KbRelativePath` 里"不许含 Windows 反斜杠分隔符"那条 `require`
**整条被吞在同一行的注释里**（而且 `'\'` 在 Kotlin 里本就是非法字面量）——这道校验从写下那天起
就不存在。它不是读代码读出来的，是 JVM 用例报红报出来的：
`路径「understand\me.md」竟然被接受`，Windows 上实测解析出了库目录外的 File。

## 11.2 新加的所有权棘轮（以及这把尺自己踩的坑）

`StorageBoundaryOwnershipTest`：data/ 里三条"能力"各登记允许出现在谁家里，断言**相等**——
多了=第二个所有者；少了=欠账还得比登记的干净，必须回来改小。
需要它的理由：`ReadOnlySchemaWriteGateTest` 的源码绊线**只扫 `KnowledgeRepository.kt` 一个文件**，
新类自带第二份落盘实现时没有任何测试会拦，而 §5.3 明写"所有实现共用一个事务入口"。

变异检查两次都红：① data/ 里塞一个真开流的新类 → 点名 `新出现的所有者 [ZzSecondOwnerProbe.kt]`；
② 文档格里塞一行 `target.writeText(...)` → 报 `直接写文件 1 处`。验完撤掉，探针按"不删"约定挪进
`_temp/mutation-probes/`。

⚠ 这把尺自己先错过一版：用带 lookbehind 的正则时同一行代码报 **0 命中**（字面量查得到）。
一把自己都会读错的尺报的是"没人碰了"，会诱导人把登记**删小**——那是反向的假绿。判据改成逐行字面判断。

## 11.3 实测与代价

| 量 | 结果 |
|---|---|
| 全量单测 | rc=0：**1104 tests / 139 套件 / 0 失败 / 0 错误 / 0 跳过**，最旧 XML 01:50:16（比 1091 多的 13 格＝本格两组新用例） |
| 既有边界合同 | `KnowledgeSchemaVersionTest` 11 / `AtomicWriteRegressionTest` 6 / `ReadOnlySchemaWriteGateTest` 5 全绿——改严没碰坏任何既有合同 |
| androidTest 编译 / lint | rc=0；进预算 **69 条 / 14 规则**不变，本格新增债 0 |
| 行数 | `KnowledgeRepository.kt` 1941→**1910**，新类 155 行：**总量涨 124 行**。买的是所有者与一条修好的边界，不是降体量，别记成行数战果 |
| P1-01 | 审的 SHA 上 >500 行 **18 个** → 现在 **17 个**：跨下来的是 `FeedbackCasesScreen.kt`（`e359930` 那次 534→500），无一个新跨上去；>800 仍 10 个 |

## 11.4 第二阶段还差什么（别把这一格当成 §5.3 完成）

- §5.3 仍差 **catalog 写侧 + profile + memory + round** 四格（round 实为已在 `RoundCommitJournal`，
  是否按指导书口径算拆出，下次要再判一次，不许含糊记成已做）。
- §5.2 第 6 步删 facade：0%，`LoveBrainViewModel` 仍 **2746 行**。
- §4 的 DIP/ISP 可验收标准未整体达成（跨层 6 条是基线，不是零）。
- 本轮 §5.3/§5.2 的改动**没有一条有 CI 判决**：JVM 用例进 verify 每次跑、本机已验；真链路仍等 CI。

---

# 追加二：读路径的第二个漏口，与 §5.3 第五格

提交 `297d1db`（隐私修）与 `a0fef45`（记忆格）。§5.3 从 4/7 → **5/7**。

## 12.1 先纠我自己上一轮的误判

上一份账把 §5.3 的 `RoundCommitStore` 记成"未做"。**错了**：`RoundCommitJournal.kt` 是
`domain/` 下独立的 457 行类、在 Koin 里注册（`AppModule.kt:51`），而 `KnowledgeRepository` 里
**一条 journal 逻辑都没有**（只剩一句拿 journal 打比方的注释）。所以那一格早有所有者——
只是它从来不在仓库里，"从仓库拆出来"这个动作并没有发生。两种说法不能混着记。

## 12.2 同一条读路径的第二个漏口（`297d1db`）

指导书 P0-03 末段那句"读取接口也必须走 safeKbFile"不是一处而是两处。上一格修了无锁快速读，
这轮量到**公开 API** 也漏：

    suspend fun readCorrections(kbName) = File(File(knowledgeRoot, kbName), "memory/corrections.json")

`memory/corrections.json` 存的是"哪条记忆被判记错了人、补正成什么、什么时候别提"——
这份产品里最敏感的数据。带 `..` 的库名就能把它从知识库根外面读进来。
先红后修，红话是：`带 .. 的库名把库外的纠正读进来了（实到 1 条）`。

**要写清的一件事**：我同时加的"撤销不许写库外"那格**在修之前就是绿的**——写那一侧本来就
经 `KnowledgeTx` 守门。所以它不是回归证据，只是防回退；漏的只有读。这种"同一份数据、
两种入口宽严不一"正是 P0-03 要消灭的形状。

顺手按 §7 第二步第 5 项清掉 `writeMemoryRevisionUnlocked`（全仓零调用点，实测）。

## 12.3 §5.3 第五格：`KnowledgeMemoryStore`（`a0fef45`）

拆的动机不是行数，是一条**单调性**承诺被写了两遍：`saveCorrection` 与 `undoCorrection`
各自实现"revision 递增 + 只在纠正文件写成后才写标记"。R07 修的就是"用剩余记录取 max 推算"
这种写法——撤销最大那条之后 revision 会倒退，后台防护因此漏判。

接口 `MemoryStorage` 只给五样（守门读 / 一次写事务 / 编解码 / kbExists / timestamp），
不给锁、不给 `File` 构造、不给落盘实现——这三件事分别只有仓库的 `fileMutex`、文档格的守门、
`atomicWriteText` 一处，新类有没有偷偷越界，是 `StorageBoundaryOwnershipTest` 替我证的（它绿=没越界）。

变异检查：把"取持久化标记"改回 R07 之前的"看剩余记录取 max" →
`revisionKeepsIncreasingAcrossSaveAndUndo` 当场红
（`删过之后再新增不许复用旧号 expected:<10> but was:<9>`）；撤回后 7 格全绿。

一处既存怪癖**没有顺手改**：原实现"纠正写成、revision 写失败"时仍返回 true。
收紧它是另一个验收目标，本次改为把这个怪癖钉成断言（标记必须保持原值），不悄悄改也不假装没看见。

## 12.4 本轮实测（两道格合起来）

| 量 | 结果 |
|---|---|
| 全量单测 | rc=0：**1113 tests / 140 套件 / 0 失败 / 0 错误 / 0 跳过**（最旧 XML 02:32:22） |
| 上一轮的格子有没有被碰坏 | `MemoryCorrectionTest` 8、`ReadOnlySchemaWriteGateTest` 5、`KnowledgeDocumentStoreTest` 10、所有权棘轮 3 —— 全绿 |
| androidTest 编译 / lint | rc=0；进预算 **69 条 / 14 规则**不变，新增债 0 |
| 行数 | `KnowledgeRepository` 1941 → **1878**；新增两格共 296 行。**总量仍在涨**（+233），买的是所有者与两处边界，不是降体量 |
| §5.3 进度 | **5/7**：migration、archive(backup)、catalog 枚举、document、memory；round 见 §12.1 的重判 |
