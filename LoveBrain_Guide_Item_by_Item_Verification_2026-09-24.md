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

供应商页（`d902514`）只做到「被量到 + 修热区/本地化」，**它自己那套空态还没换成 `ScreenState`**。

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
