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

---

# 追加三：画像格与"读要过守门"这件事的最后一批

提交 `a07b285`（读孔）→ `e7b8fdc`（工单编号还债）→ `b6873cf`（画像格）。§5.3 从 5/7 → **6/7**。

## 13.1 先纠上一轮记错的一条：**工单编号门禁当时是红的**

交接单 §1 与本文 §10 都写着"工单编号 PASS"。本轮实跑 `python scripts/strip_ticket_ids.py --check`
→ **rc=1，命中 4 处**，其中 3 处是上一轮往 `KnowledgeDocumentStore.kt`（两处）与
`KnowledgeRepository.kt`（一处）注释里写的 `P0-03`，第 4 处是本轮新写的注释里同一族编号。
那句 PASS 是沿用上上轮的数，没重跑——属于"自述当取证"。

处理：四处改成不带编号的指法（"复核报告里 P0 那条读路径"），`--check` 回到 **rc=0**，
并用脚本自带的 `TOKEN_RE` 独立复扫 `app/src/main` 全部注释 → **0 命中**。
测试代码里的编号指法不动（那条门禁只扫生产代码，保留出处更有用）。
`e7b8fdc` 单独一笔，因为它还的是上一轮的债，与画像格不是同一个验收目标。

## 13.2 读路径的最后一批裸路径（`a07b285`）

同一把尺（`File(File(knowledgeRoot,` 逐行字面判）量到仓库里还剩 **10 处**，本轮封 6 处：

| 入口 | 修之前实测漏了什么（值来自失败输出） |
|---|---|
| `getCurrentStage` | 读到库外那份 kb.json 的 stage：`实到「分手冷却期」` |
| `getTurnCount` | 读到库外 turnCount：`实到 99`（这个数会进 PromptBuilder 与 TopicRecorder 的 inputRevision） |
| `readIntent` | 读到库外意图：`实到「库外的私密意图」/ revision=6` |
| `saveIntent` | 更硬：把库外读到的 revision **+1 当本次保存结果返回**：`实到 revision=7`（应为 1） |
| `readArchiveOpUnlocked` | rotateTopic 的幂等状态；读错库＝对真库少干活。拼路径的 `archiveOpFile` 一并删掉，读写删三处改用同一个文件名常量 |
| `writeVectorUnlocked` | 见 §13.3，这一处**不外露** |

四格红先、每格配库内正对照（防止"本来就解析不出来"造成假绿）。

## 13.3 `writeVectorUnlocked`：一处只当防回归，一处真红

"库名带 `..`"那一面黑盒量不到——读到的内容不外露，落盘那一侧本来就拒。
对应格子（`vectorWriteStillRefusesToTouchAnOutsideLibrary`）在修**之前**跑过，是绿的，
所以它是防回归，不当本轮证据。本轮真正的证据是形状那一侧：
`StorageBoundaryOwnershipTest` 新增一条按**条数**登记的规则（文件级所有者相等挡不住同一文件里多一处），
当前登记 **4 处**（全在 `applyProfileUpdateAtomically` 的备份快照里，那格单独处理），只许降不许升；
注入第 5 处时实测报 `登记的是 4 处，实际命中 5 处`。

但这一处改动带出一个**测得出的真差别**，而且是修之前才测得出：旧布局（只有 `global/status.md`）的库，
`readVector` 透过回退一直看得见内容，`writeVectorUnlocked` 走裸路径看不见于是**静默不写**，
而阶段标签那条用的又是公开读、会写——同一份内容三种宽严。归到守门读之后同一把尺。
`writingVectorForALegacyLibraryActuallyLands` 用坏实现验过它会红（9 跑 1 红）。

## 13.4 第六格 `KnowledgeProfileStore`（`b6873cf`）

指导书 §5.3 那行的原文是"**画像、温度/阶段、向量**"。这三件现在都在这一格里；
仓库侧只剩锁 + suspend 外壳 + 转发（1878 → **1844** 行）。搬进去的动机是三条"以前有两份"：

1. 维度表被**三处**各自循环引用（`readVector`、`writeVectorUnlocked`、`readVectorUnlockedFast`），
   其中"按中文维度名解析数值"那段正则有**两份逐字相同**（`readVector` 与 `readVectorUnlockedFast`），
   后者还少一条零匹配日志（实测：`git show a07b285:` 里 1192 与 1539 两行一模一样）→
   现在只剩 `vectorOf` 一处，第三处回到它。（提交信息里我写成"五维解析有三份"，
   严格说是"表三处、解析两份 + 替换一份"，以这里为准。）
2. warmth 里阶段标签行的改写规则（变体认法 + 「当前状态」节插入 + "过去曾经是…"）
   被非事务版与 strict 版各抄 30 行 → 只剩 `rewriteStageLine` 一处，strict 版回来共用。
3. 九阶段白名单"拒绝时说不说、怎么说"散在四处 → 只剩 `normalizeStage` 一处。

能力接口 `ProfileStorage` 六样（`note / timestamp / read / writeUnlocked / metaOf / writeMetaTransaction`），
不给 Mutex、不给 `File`、不给第二份落盘。两处实现细节值得记：
`writeMetaTransaction` 与记忆格的 `writeTransaction` 不能同名——两个 `fun interface` 都擦除成
`Function1`，JVM 上是 platform declaration clash，我原本注释里写的"参数类型不同所以互不干扰"是错的，
编译器当场纠正了我。另一处：`profileText` 的 KDoc 里写 `understand/*.md` 会**开一个嵌套块注释**
（Kotlin 注释可嵌套），整格从那里往下被吞掉，报 `Unclosed comment`。这是两轮之内第二次
"注释吞掉代码"（上一次是 `KbRelativePath` 的 require 被行注释吞了），下一格考虑给它配一把尺。

## 13.5 两处"探针没打破"的断言——改掉而不是留着当已验

新写的 20 格逐条注入反例。其中两格（"两段内容对调"、"同内容放不同路径"）是想证明
`contentRevision` 把路径也喂进摘要有用：把路径盐整段删掉，两格**照样绿**——
条目数固定、顺序固定、每条后面跟一个分隔符，内容里再塞分隔符只会多出一个字节，构造不出撞号。
所以：断言换成"修订号是内容指纹、不许混进库名"（注入 `M6` 把库名混进去 → 当场红），
路径盐留在代码里当纵深防御，注释与本文都写明它**不算已验收益**。别把这条改回去，除非先造出真反例。

其余变异：`M2` 丢掉"过去曾经是…" → 3 红；`M3` 去掉空文件守卫 → 1 红；`M4` 白名单外放行 → 2 红；
`M5` 内容没变也照写 → 1 红；`M8` 让画像格碰 `File(` → 新增的"后拆出去的格子零存储能力"一格红。

## 13.6 本轮实测（三笔合起来，全部同一轮跑完）

| 量 | 结果 |
|---|---|
| 全量单测 | rc=0：**1143 tests / 142 套件 / 0 失败 / 0 错误 / 0 跳过**（最旧 XML 03:27:32，日志起点 03:24:40） |
| 既有边界合同 | `KnowledgeDocumentStoreTest` 10 / `KnowledgeMemoryStoreTest` 7 / `MemoryCorrectionTest` 8 / `ReadOnlySchemaWriteGateTest` 5 / `StorageBoundaryOwnershipTest` 4 全绿 |
| lint | **报告先重新生成**（`lintReportDebug` 报 UP-TO-DATE 且不重写文件，先把旧报告移进 `_temp/` 逼它写一遍，新报告 03:32）：70 条 / 15 规则，进预算 **69 / 14**，advisory 1，rc=0；判据自测 27 格 rc=0 |
| 其它门禁 | androidTest 编译 rc=0；工单编号 rc=0；prompt 零 diff + lock `6dcde732fab60281…`；跨层 **6** 条（与基线同） |
| 行数 | `KnowledgeRepository` 1878 → **1844**；新类 227 行 → 总量 **+193**。买的是所有者与一批读边界，不是降体量 |
| §5.3 进度 | **6/7**：migration、archive(backup)、catalog 枚举、document、memory、**profile**；剩 archive 的 rotateTopic 那一格 |

## 13.7 本轮量到的"已知未做"，别当已做

- `applyProfileUpdateAtomically`（160+ 行跨文件事务）里的 **4 处**裸路径读仍在，
  由条数棘轮钉着；那一格与 `KnowledgeArchiveService`（rotateTopic/import/export）一起处理。
- catalog 写侧仍未收窄（诚实接口要 ~10 个成员，违背 §5.3 自己的 ISP），只做了枚举。
- 指导书点名的 `KnowledgeTransactionManager` 这个名字**全仓不存在**：共用一个事务入口这件事
  是靠仓库唯一的 `fileMutex` + `transaction`/`transactionUnlocked` + `KnowledgeTx` 做到的，
  语义达成、命名未达成。答"§5.3 是否照指导书做完"时这条要分开说。
- 本轮 §5.3 的改动**没有一条有 CI 判决**：JVM 用例进 verify 每次跑、本机已验；真链路仍等 CI。
- 仓库里 `values-en/strings.xml` 在 `git status` 里长期显示 `M` 而 `git diff` 为空——
  是变异探针跑过之后 mtime 变了（内容逐字节相同），别去 `git add` 它。
- `scripts/asset_hashes.sh --check` 必须带锁文件路径（`--check docs/prompt-assets.lock`），
  起手命令里少写这个参数会撞 `$2: unbound variable`；`test_check_lint_budget.sh` 在 Windows 上
  同样要 `PYTHON=python`，否则 CANNOT-VERIFY(2)。

---

# 追加四：回滚那条第二写链，与"没抛异常"被当成成功

提交 `007e4fd`（回滚写边界）与 `3b86b03`（只读事务如实报错）。§5.3 的读路径欠账**清零**。

## 14.1 两把尺混用会造成什么（`007e4fd`）

`applyProfileUpdateAtomically` 的三段——写前快照、回滚落盘、回滚后校验——各自拼
`File(File(knowledgeRoot, kbName), path)`，回滚还直接 `atomicWriteText` / `file.delete()`。
真正造成损失的是**同一份快照里两个字段出自两道门**：

```kotlin
val file = File(File(knowledgeRoot, kbName), path)      // 裸：库名带 .. 也说"在"
backups[path] = (file.exists() to readFileUnlockedFast(kbName, path))  // 守门：给空串
```

上一轮我把 `readFileUnlockedFast` 收紧过，于是这一行变成"存在=true、旧内容=空串"，
回滚照着空串把**知识库根外面的文件清空**。实测红：
`expected:<外面那份画像不该被动> but was:<>`。

教训单独记一条：**把某个读入口改严之后，必须回头搜"有没有别的地方还在判它的存在性/大小/时间"**。
一宽一严不只是"两种答案"，还会拼出第三种谁都没打算写的行为。

改法与配套：存在性与旧内容同出一门（`snapshotBeforeWriteUnlocked`，读不出内容就让异常穿出去——
这段在所有写之前，抛出=一个字节没动；吞成空串反而会照着空串把真文件写没）；
恢复用 `KnowledgeTx.write`、删除用 `KnowledgeTx.deleteAt`、校验用 `safeKbFile`；
回滚循环里那句 `file.parentFile?.mkdirs()` 一起去掉（它会在库外建目录）。
格内另有一格"越界不许报 Success"修前就是绿的，只算防回归。

棘轮那条按数登记的规则从 10 → 4 → **0**，owners 改空集。"0 命中"通常是尺瞎了，这次不是：
注入一行裸路径读，实测同时报「登记的是 []，实际命中 {KnowledgeRepository.kt=1}」和
「登记的是 0 处，实际命中 1 处」。**还债把计数降到 0 之后，这条的语义就反过来了**——
以后任何一处都是新增，读法要跟着改，别当"这族问题永远不会有了"。

## 14.2 "全程没抛"不等于"写成了"（`3b86b03`）

同一段事务的前置检查只查"库在不在"和"revision 对不对"，没查"还写得动吗"。
schema 过新的只读库上：两次写被 `writeFileUnlocked` 静默跳过、没有任何一步抛，
函数一路返回 `Success` —— 磁盘没变，UI 却提示"画像已更新"并把建议卡清掉。
`ReadOnlySchemaWriteGateTest` 那 24 个入口的磁盘逐字节比对**本来就绿**，因为它钉的是磁盘，
不是返回值；这一族里唯一带 typed result 的入口就这样漏了过去。

- 只读升格为第三种前置条件 `PreconditionReason.LIBRARY_READ_ONLY`，判定放在入口，
  与公开 `transaction()` 的 `RefusedNewerSchema` 同一把尺；
- UI 分清两件事：库没了 / 资料变了 = 建议作废（清卡）；库只读 = 这个 App 写不动它（卡留着，
  升级后同一份建议仍可确认）；
- `ProfileTransactionResultTypeTest` 的"enum 恰好 2 个值"改成 3 —— 这条尺的存在就是逼这次判断。

两格新断言各用一次变异验红：`U2a` 撤掉入口只读判定 → gate 那格红；`U2b` 让只读分支跟着清卡 →
VM 那格红；两次都只有目标格红。仍留着一个缺口没补：**其余 24 个入口的返回值没有被同样断言**
（返回 Boolean 的那几个本身诚实，见 §12.3 的"报成功却没写"两格；返回 Unit 的无法这样断言）。

## 14.3 本轮实测（两笔合起来）

| 量 | 结果 |
|---|---|
| 全量单测 | rc=0：**1148 tests / 143 套件 / 0 失败 / 0 错误 / 0 跳过**（最旧 XML 04:09:01，日志起点 04:05:41） |
| 回滚那一族既有合同 | `RepositoryRollbackFaultInjectionTest` 全绿（含"rollback 删除新建文件"“verification 异常 → RollbackFailed"） |
| 只读那一族既有合同 | `ReadOnlySchemaWriteGateTest` 6 格全绿（入口从 24 增至 25）；`ProfileTransactionResultTest` 新增一格后全绿 |
| lint | 报告重生成（03:56 那份先移进 `_temp/`）→ 04:11：70 条 /15 规则、进预算 **69 /14**、advisory 1，rc=0；判据自测 27 格 rc=0 |
| 其它 | androidTest 编译 rc=0；工单编号 rc=0；prompt 零 diff + lock `6dcde732…`；跨层 **6** 条（与基线同） |
| §5.3 进度 | 仍 **6/7**（本轮是把 archive 那格欠的读/写边界先还清），裸路径计数 **0** |

## 14.4 §5.3 现在到底还差什么（别再抄旧数）

- 只剩 `KnowledgeArchiveService`：`rotateTopic` 的四步状态机仍在仓库（`moment/.archive_op.json` 的
  读已归守门，事务与删除也已归 `KnowledgeTx`，剩的是"这一格该不该独立存在"的判断）。
- `kbExistsUnlocked` 仍在用裸 `File(knowledgeRoot, kbName)` 判目录+kb.json：只泄露一个布尔
  （库外是否存在一个像库的目录），不读内容。改严会让 ~15 个入口的日志措辞从"path refused"
  变成"kb no longer exists"，是一次显式决定，不是顺手活。
- 指导书点名的 `KnowledgeTransactionManager` 这个**名字**仍不存在（语义由 `fileMutex` +
  `transaction`/`transactionUnlocked` + `KnowledgeTx` 承担）。
- 本轮两笔的改动**没有一条有 CI 判决**；`ReadOnlySchemaWriteGateTest`/新格都进 `verify` 的
  JVM 步骤，真链路仍等 CI。

---

# 追加五：话题行与 §5.3 第七格（归档）——格子齐了，但有一格是半个

提交 `99c209d`（话题行格式）与 `0c4d6d6`（`KnowledgeArchiveService`）。§5.3 七个名字**全部到位**。

## 15.1 一个标记五个主人（`99c209d`）

`正在聊：` 这个标记在生产代码里出现五次：`KnowledgeMigrator` 初始化、仓库建默认库、
仓库 `ensureInitial`、`setCurrentTopic` 各写一次，`getCurrentTopic` 再单独解析一次。
而 `substringAfter` 的默认行为是"找不到就把整行还给你"——任何一处改字，读侧不会报错，
而是**把整行当话题名**，`rotateTopic` 就把带时间戳的一整行写进归档标题。

收进 `KbTextOps.topicLine / topicLabel` 与三个常量，五处转调，产出字节逐字相同
（第一格把 `- [时间] 正在聊：标签` 这个线格式钉死，改格式要显式过一次）。
另加一条源码形状尺：这个字面量在生产代码里只许出现在 `KbTextOps.kt`，且扫描自己断言
"至少扫到 10 个文件"（否则路径写错时它会永远通过——`SingleOwnerContractTest` 同族写法）。
既存怪癖只钉不改：首行没有标记时返回整行（要改得先决定"读不到给什么"）。
变异：T1 塞第二处字面量→形状尺红；T2 丢 key 后缀切分→第 2 格红；T3 改线格式→第 1 格红。

## 15.2 第七格 `KnowledgeArchiveService`（`0c4d6d6`）

`rotateTopic` 原先是仓库里一段 80 行的方法体，四条规则混在一起且**没有名字**：
步骤判定、归档条目格式、旧话题名读法、计数回填口径。现在各有所有者，
`archiveEntry` 从 `buildString` 里抽出来变成能单独测的东西——这是拆的主要收益，不是行数。

能力接口 `ArchiveStorage` 七样（`note` / 两把时钟 / 守门读 / 一次事务 / 状态编解码），
`ArchiveTx` 四类动作由 `ArchiveTxView` 从 `KnowledgeTx` 收窄而来（不直接交 `KnowledgeTx`
是为了不把 `pathOf`——它能拿 `File`——一起交出去）。零能力棘轮把它纳进清单。

两条**不是搬家**的变化，写在代码与这里：
 ① `getLessonCount` 原先读 kb.json 与回填各加一次锁，两次之间别人可以写完一份，回填会拿旧口径
    盖一次计数；现在整段一次锁内完成（收紧）。
 ② 状态落盘原先静默；现在"事务里的写被挡下"与"整个事务被外层跳过"两种没写成都留痕。

两把时钟是既存事实不是设计癖好：条目行/`operationId` 用 `TimeFmt.now()`，
kb.json 的 `updatedAt` 用 ISO-8601——合并任何一把都会悄悄换掉落盘格式，所以接口里显式两个名字。
`runTransaction` 不叫 `writeTransaction` 是编译逼的：`MemoryTx.() -> Unit` 与 `ArchiveTx.() -> Unit`
都擦除成 `Function1`。**同一条坑这一轮踩到第二次**（第一次是画像格），说明它该进坑表而不是靠记性。

证据：新格 10 格全被坏实现打破过，七次注入各自只红目标格——`A1` 不清源文件→2 红、
`A2` 计数正则放宽→1 红、`A3` 去掉追加的幂等守卫→2 红、`A5` 少落一次状态→1 红、
`A6` 两把时钟合一→1 红、`A7` 去掉没写成的留痕→1 红、`A8` 恢复时改取当下→2 红。
既有 net（真文件系统上 `ArchiveOperationStateTest` 五格 + Reentrancy + 25 入口只读闸）全绿。

## 15.3 本轮实测（两笔合起来）

| 量 | 结果 |
|---|---|
| 全量单测 | rc=0：**1163 tests / 145 套件 / 0 失败 / 0 错误 / 0 跳过**（最旧 XML 04:45:22；日志止点 04:45:24、本次跑 183s → cut 由日志自身算出，不凭记忆） |
| 既有 net | `ArchiveOperationStateTest` / `KnowledgeRepositoryReentrancyTest` / `ReadOnlySchemaWriteGateTest`(25 入口) / `StorageBoundaryOwnershipTest`(4) / `TopicLineFormatTest`(5) / `KnowledgeArchiveServiceTest`(10) 全绿 |
| lint | 报告重生成（04:47；04:11 那份先移进 `_temp/`）：70 条 / 15 规则、进预算 **69 / 14**、advisory 1，rc=0；判据自测 27 格 rc=0 |
| 其它 | 工单编号 rc=0；prompt 零 diff + lock `6dcde732…`；androidTest 编译 rc=0；跨层 **6** 条（与基线同） |
| 行数 | `KnowledgeRepository` 1876 → **1793**（本轮第一次真正变短 −83）；新格 230 行、话题格式那笔净 +24 |

## 15.4 §5.3 现在准确的说法：七个名字都在，但 catalog 那格是半个

> ⚠ 提交 `0c4d6d6` 的标题写的是"§5.3 7/7"，那是不该写的省话——按下面这张表，准确说法是
> "七个格子都有、其中 catalog 只搬了一半、archive 那个名字由三个协作者凑成"。
> 已发布的历史不改写（宁可多一笔更正），以这里为准。

| 指导书点名的格 | 现状 |
|---|---|
| `KnowledgeCatalogStore` create/list/activate/delete/rename | **只搬了枚举侧**；`create`/`delete`/`setActive`/`updateDisplayName`/`ensureInitial` 仍在仓库。写侧接口要 ~10 个成员（违 §5.3 自己的 ISP），显式退回过一次 |
| `KnowledgeDocumentStore` 安全路径、版本化读写 | 在（`adbf5f3`） |
| `KnowledgeProfileStore` 画像、温度/阶段、向量 | 在（`b6873cf`） |
| `KnowledgeMemoryStore` 纠正、revision、actual sent | 在（`a0fef45`）；**actual sent 没进这格**，它仍走仓库的 `appendFile` 链 |
| `KnowledgeMigrationService` schema detect/migrate/read-only | 在，名字叫 `KnowledgeMigrator`（命名与指导书不同，语义齐） |
| `KnowledgeArchiveService` topic rotate/import/export/backup | rotate 本轮搬入（`0c4d6d6`）；import/export 在 `KbArchiveTransfer`、backup 在 `KnowledgeBackupService`——**是三个协作者凑成一个指导书里的名字**，这点要如实说 |
| `RoundCommitStore` journal/transaction/idempotency | 在，是 `domain/RoundCommitJournal`（§12.1 重判过） |
| 「所有实现共用一个 `KnowledgeTransactionManager`」 | **语义达成、命名未达成**：全仓没有这个类型，共用靠仓库唯一 `fileMutex` + `transaction`/`transactionUnlocked` + `KnowledgeTx`，并由棘轮与 `KnowledgeMigratorLegacyTest` 的锁计数盯着 |

还没还的账，别当已做：`kbExistsUnlocked` 仍用裸路径判"目录+kb.json 在不在"（只泄露一个布尔）；
本轮所有改动**没有一条有 CI 判决**。

---

# 追加六：面板九个统计并成一份快照（§2.2 那行的第一处真正落地）

提交 `cf80b91`。指导书 §2.2「谈心/锦囊/主动发统一状态模型 = PARTIAL」的原话是
"仍各自直接写多个 MutableStateFlow，没有统一 Reducer/UiState"——本轮挑的是这一族里最独立的一处。

## 16.1 量到的形状

| 项 | 拆之前 |
|---|---|
| `MutableStateFlow` 个数 | 九个（今日花费/本次/首字耗时/累计首字/生成/累计花费/复制/采用/改写） |
| 直接改它们的语句 | 十二处 |
| 顺手抄回 `SecurePrefs` 的位置 | 五处（各写各的，字段与写入条件没有汇总点） |
| 状态之外的状态 | `private var todayCostDate`（跨天清零判据，**不在任何 flow 里**）+ `rollTodayCost` 独立函数 |
| UI 侧 | 面板 5 次 `collectAsStateWithLifecycle()`；`UsageStatsRow` 五个参数 |

"这九个数的所有者是谁"没有答案；要测"复制一次会不会误伤采用数"只能起整个 ViewModel。

## 16.2 现在

`UsageStats`（不可变快照）+ 六种事件的**纯** `reduce` + 一个写入漏斗 `applyUsage`。
落盘只在漏斗一处，日期判据并回快照本身，`rollTodayCost` 与那个 var 一起消失。
渲染一格没改（五格、首字那格的 `>0` 条件、`"—"` 占位原样），UI 侧 5 次 collect 并成 1 次、5 个参数并成 1 个。

既有断言**搬家不删**：`CostDisplayTest` 四条跨天判据搬到 `loaded`；
`MechanismClosureTest`（IO 失败时 adopt 不许涨）与 `RewriteEffectWiringTest`（改写计数恰好写 prefs 一次）
只改读法、断言原样——落盘那半边仍由它们看着（新纯函数测试故意不覆盖 prefs）。

新格 9 条，五处注入各自只红目标格：`U1` 前后台反了→2 红、`U2` 丢掉跨天分支→1 红、
`U3` 生成串到复制→1 红、`U4` 计时事件丢旧值→1 红、`U6` 默认值从 null 变 0.0→1 红。
一条**注入不了**：`reduce 不改旧快照`——data class 的 `copy` 结构上就改不动，
它防的是将来有人手写实现，不算已验战果。

## 16.3 两条本轮踩到的新坑（都写进交接单坑表）

1. **JUnit4 的 `assertEquals(double, double)` 运行期直接拒判**
   （`Use assertEquals(expected, actual, delta) to compare floating-point numbers`）：
   我这格一次红了七次，全是这个而不是断言内容。浮点比较必须带 delta，或改 `kotlin.test`。
2. **用脚本往代码里注入变异时，注释不能跟在"带行尾逗号的参数"后面**：
   `val x: Double? = 0.0 // PROBE` 会把那个逗号吃进注释 → 语法错误，
   而报错位置在**下一行**（"Expecting comma or ')'"、"Unresolved reference"），看着像被测代码坏了。
   注入的注释一律单独占一行。另：`python - <<EOF` 里带中文会按 ANSI 解码坏掉（坑表第 16 条第三次命中），
   变异脚本先落成文件再执行。

## 16.4 实测

| 量 | 结果 |
|---|---|
| 全量单测 | rc=0：**1172 tests / 146 套件 / 0 失败 / 0 错误 / 0 跳过**（最旧 XML 05:16:10，cut 05:13:10 由日志自身时长反推） |
| 搬家没丢断言 | `CostDisplayTest` 2 / `MechanismClosureTest` 全 / `RewriteEffectWiringTest` 全 绿 |
| lint | 报告重生成（05:18）70 条 /15 规则、进预算 **69 /14**、advisory 1，rc=0；判据自测 27 格 rc=0 |
| 其它 | 工单编号 rc=0；prompt 零 diff + lock `6dcde732…`；androidTest 编译 rc=0；跨层 **6** 条 |
| 行数 | `LoveBrainViewModel` 2756 → **2732**（−24），新文件 `UsageStats.kt` 94 行 |

## 16.5 §2.2 那行现在的准确状态

只并掉了**统计这一族**。VM 里还剩 **30 个** `MutableStateFlow`（39 → 30，实测同一把尺），
其中画像是 `profileSuggestion` + `isProfileConfirming` + `stageSuggestion` + `vectorUpdate` 四个各写各的、
意图是 `intentConfig` + `showIntentEditor`、消息编辑是 `messages`/`editingIndex`/`currentRole` 三个。
下一处该并哪个要有判据（谁互相必须同帧变化），不是按字母顺序清库存。
`LoveBrainViewModel` 也仍是 facade：§5.2 第 6 步"调用点迁完后删除"远没到——
VM 还有一百多个公开成员、十几个命令式方法（`generate`/`nextRound`/`copyScheme`/`recordActualSentMessage`…）
没有对应的 store intent，硬搬会把 `SecurePrefs`、coordinator、engine 全拖进 store 的端口里
（与 catalog 写侧同一种"宽端口"陷阱）。

---

# 追加七：画像卡片那两件事并成一份快照（§2.2 第二处），并更正上一笔的一格计数

提交 `6910098`。

## 17.1 先更正我自己的数

上一笔（§16.5）写的"VM 里 `MutableStateFlow` 39 → 30"差一格。用**同一把尺**（正则含带数字的变量名）
在三个提交上各量一遍：

| 位置 | 私有 `MutableStateFlow` 个数 |
|---|---|
| `3b86b03`（统计合并之前） | 39 |
| `cf80b91`（九个统计并成一个之后） | **31**（不是我写的 30） |
| `6910098`（画像卡片两件事并成一个之后） | **30** |

上一笔那次正则漏的是带数字的名字，所以 39 那次没漏、31 那次漏了一个——**同一族计数换正则就等于换尺**，
这条按"两侧同尺"的规矩记下来。已提交的提交信息不改写。

## 17.2 这一格做了什么

卡片"显示与否"看建议、"按钮与转圈"看 `isConfirming`，原先是两个 flow、VM 里 14 处各改各的。
分两处就会拼出没人设计过的中间态（确认成功那一刻"建议已清空、confirming 还是 true"），
以及两次请求同时通过 `if (isConfirming) return` 这道门。
现在是一份不可变 `ProfileReview` + 五种事件的纯 reduce + 一个漏斗 `applyReview`，
判据住在状态本身：`canAttemptConfirm`、`canConfirm`、重复点确认自己忽略、
**清卡只清"我确认的那一份"**（`ClearedIfCurrent`——原先这条是调用点手写的"读 flow、比 id、再写 null"，
最容易被下一个图省事的人写成无条件清，把确认期间到达的新卡片抹掉）。

同一族的 `vectorUpdate` 与 `stageSuggestion` **没有**并进来：它们与这张卡片不需要同帧
（一个顶部横幅、一个独立卡片），并进去只是把两件无关的事变成一次大快照复制。这是判断，不是没做完。

调用点迁完：面板 2 次 collect 并成 1 次；三处测试只改读法、断言一字未动
（`ProfileConfirmTest` 无效建议清卡、`ProfileTransactionResultTest` 五种 typed result 各自的卡片去向、
`KbgBatch8Test`）。新纯函数 9 格，六处注入各自只红目标格：
`P1` 去掉重复确认守卫、`P2` 清卡不比 id、`P3` 新建议顺手抹平 confirming、`P4` 可点性只看有没有卡片、
`P5` 关卡片顺手停 spinner、`P6` 结束确认时连卡片一起清。

## 17.3 实测

| 量 | 结果 |
|---|---|
| 全量单测 | rc=0：**1181 tests / 147 套件 / 0 失败 / 0 错误 / 0 跳过**（最旧 XML 05:36:20 ≥ shell 记的起点 05:33:20） |
| lint | 报告重生成（05:39；05:18 那份移进 `_temp/`）70 条 / 15 规则、进预算 **69 / 14**、advisory 1，rc=0；判据自测 27 格 rc=0 |
| 其它 | androidTest 编译 rc=0；工单编号 rc=0；prompt 零 diff + lock `6dcde732…`；跨层 **6** 条 |
| 行数 | `LoveBrainViewModel` 2732 → **2739**（+7，多了漏斗与判据注释），新文件 61 行；flow 数 31 → 30 |

## 17.4 §2.2 那行还剩什么（本轮之后）

已并两处（统计九个 → 1，画像两件 → 1），VM 里还剩 **30** 个私有 `MutableStateFlow`。
按"必须同帧变化"这把判据看，下一处该并的是意图族（`intentConfig` + `showIntentEditor`，
编辑面板打开时两者一起决定渲染）；消息编辑族（`messages`/`editingIndex`/`currentRole`）同理但要小心
`messages` 已被多处命令读写。其余（`panelState`/`panelMode`/`outputMode`/`resultMode`/`draftText` 等）
是各自独立的单选值，并成一份大快照只会让"改一个要复制全部"——那不是统一状态模型，那是结构体崇拜。
`LoveBrainViewModel` 仍是 facade，§5.2 第 6 步的距离没有实质缩短。

---

# 追加八：编辑位的三处推理并成一条判据（并撤回我上一笔的一个判断）

提交 `26ecf95`。

## 18.1 先撤回一条我自己写错的判断

上一笔（§17.4）我提名"下一处该并意图族 `intentConfig` + `showIntentEditor`"。这一笔开工前先把判据
量了一遍：**那两处之间没有必须同帧的不变式**——保存后关编辑器，中间那一帧只是"刚存的值 +
对话框还开着"，而编辑器显示的正是刚存的值。把它们并成一个快照不会消灭任何真实竞态，
只会得到"改一个字段要复制整份结构体"。所以没动它。教训：**"下一个该并谁"必须由不变式检索得出，
不能由我上一段话的顺嘴提名得出**（这正是"按字母清库存"的另一种形式）。

## 18.2 真正有不变式的那一族

`editingIndex` 是 `messages` 的下标 ⇒ 任何改列表的操作必须同帧修正它，否则就是
"我在改第 2 条、实际改到第 3 条"这种**不报错的用户事故**。规则原先写在三处：
`removeMessageById` 与 `reorderMessages` 是算术位移（后者带三段边界推理 + 两个反例注释才说得清
等号含不含），`commitReplyRound` 按身份重算。

## 18.3 顺序：先证明一致，再合并

新增 `MessageEditingIndexInvariantTest`：在**真实 VM 方法**上穷举 130 个
(长度, 编辑位, from→to / 被删条) 组合，只断言不变式
"操作后 `messages[editingIndex]` 仍是操作前那条消息（被删则 `-1` 且草稿清空）"。
**结果全绿：没找到 bug**，但一致性从此有网（每个穷举用例带"至少跑够 N 组"的哨兵，
防循环条件写错后零次执行假绿）。然后才把三处收到 `MessageListEditing.reindex`（按身份重算），
并顺带拧紧一条：悬空编辑位判成"没有编辑位"——旧写法会留着它，而 `ReplyInput` 只看
`editingIndex >= 0` 就进编辑态，那是在编辑一条不存在的消息。

纯函数 7 格 + 矩阵 5 格。注入：`V1` reindex 原样返回旧下标 → 12 格里 7 红（矩阵与纯格一起咬）；
`V2` `editing < 0` 返回 0 → 4 红；`V3` 悬空索引兜到列表首元素 → 2 红。

## 18.4 实测与性质界定

| 量 | 结果 |
|---|---|
| 全量单测 | rc=0：**1193 tests / 149 套件 / 0 失败 / 0 错误 / 0 跳过**（最旧 XML 05:55:02 ≥ 起点 05:51:50） |
| lint | 报告重生成（05:56）70 / 15，进预算 **69 / 14**，advisory 1，rc=0；判据自测 27 格 rc=0 |
| 其它 | androidTest 编译 rc=0；工单编号 rc=0；prompt 零 diff + lock `6dcde732…`；跨层 **6** 条 |
| 行数 | `LoveBrainViewModel` 2739 → **2719**（−20，边界推理连注释一起搬走）；新文件 31 行；私有 flow **仍 30 个** |

⚠ 这一笔是"**一条规则一个所有者**"，不是"一份状态一个所有者"（flow 数没变）。
§2.2 那行"没有统一 Reducer/UiState"的进度仍停在 §17 的两处；下一步该由"哪些字段必须同帧"
检索得出，目前已知的候选是**回滚族**（`generationHistory` / `currentVersionId` / `inputChanged`：
`currentVersionId` 必须是 history 里存在的一项，而 `rollbackToPreviousGeneration` 一次改三处）。

---

# 追加九：回滚族量完之后不做，与输入框的读屏名字

提交 `0e83c95`。第一半是"量完决定不动"。

## 19.1 回滚族：不变式今天成立，唯一的理论缺口不可达

量到的形状：`SuccessCommitted` 里 `_currentVersionId` 是**无条件**赋值，而 history 那条快照
只在 `replyGenerationContext?.let { … }` 里追加。若一次"成功"落在 context 为 null 的时刻，
就会出现"当前版本不在版本栈里"，而 `rollbackToPreviousGeneration` 删的是 `kbHistory.last()`，
两者错位——这本来是该并的不变式。

但它**不可达**：`stopGeneration` 先 `stopCurrent(REPLY)` 再清 context，而 store 的归约只在
`isCurrentRequest` 通过时才发 `SuccessCommitted`（单 owner 那本账），停止后迟到的成功不会提交；
`nextRound`/`commitReplyRound` 也不开这个窗口。既有 `GenerationRollbackTest` 已在真实生成链上
钉住"回退删的是当前那条、被放弃的版本不再出现"（v1/v2/v3/v4 四段序列）。

所以这一格**不加防御性 else**，把"不可达"记在这里。强度要说清：这条判断来自
**读码 + 既有生成链用例覆盖**，不是穷举证明——真要钉死它，需要一个能在 context 为 null 时
直接喂 `SuccessCommitted` 的入口，生产里没有那个口。

## 19.2 §6.5 无障碍第②栏：输入框在语义树上没有名字

placeholder 是**兄弟节点**的一行 `Text`，只在草稿为空时画；TalkBack 念不到兄弟，
于是这一颗只有 `EditableText` 语义、报"编辑框"，而用户敲进第一个字之后连那行 Text 都不画了。
逐点数入口时量到（`ComposerAddButtonGatingTest` 里 `describe()` 那条兜底分支就是它）。

改法：给可编辑节点本身挂 `contentDescription = placeholder`（与那句提示同源，不另起一份文案）。
同形状两处一起改：`PanelTextInput`（回复/主动发/谈心共用）与 `CompactInput`（问卷页、供应商弹窗共用）。
视觉一格没改。新增 `ComposerInputLabelTest` 五格（JVM 语义树 + `UiMatrix` + NATIVE）：
首帧有名字、切角色名字跟着换、敲字之后仍在、`placeholderOverride` 传进来就是什么、第二个实例同样有。
**刻意不断言中文原文**——那三句提示目前仍是硬编码在 `ReplyInput` 里的字面量，
资源驱动/中英 parity 是另一笔账，本轮不改用户可见文案，只钉语言无关的三件事实。
红先：注入 `A1`（撤掉两处 semantics 行）→ 五格全红，同文件既有 3 格不受牵连；恢复后全绿。

## 19.3 实测

| 量 | 结果 |
|---|---|
| 全量单测 | rc=0：**1198 tests / 150 套件 / 0 失败 / 0 错误 / 0 跳过**（最旧 XML 06:10:17 ≥ 起点 06:07:16） |
| lint | 报告重生成（06:12）70 / 15、进预算 **69 / 14**、advisory 1；`check_lint_budget.sh` **不带管道**单独取退出码 rc=0 —— 我在管道里取过一次 `$?`，量到的是 `grep` 的码（同族坑第二次犯） |
| 其它 | 判据自测 27 格 rc=0；androidTest 编译 rc=0；工单编号 rc=0；prompt 零 diff + lock `6dcde732…`；跨层 **6** 条 |

## 19.4 连着看才知道 §2.2 那行走了多远

本轮之前我提名过两处"下一格"：意图族（§18.1 量完撤回：没有必须同帧的不变式）、
回滚族（§19.1 量完撤回：不变式成立、缺口不可达）。加上做了的两处（统计 §16、画像卡片 §17），
连着看才是真实进度：**§2.2 那行只推进了两处，另两处是"查过、不动"**。
下一处候选只剩消息编辑族（`messages`/`editingIndex`/`currentRole`——前两个已有穷举矩阵兜底，
并成一份快照的收益要重新估），以及 §6.1–§6.4 那批 UI 结构活（本阶段没动）。

---

# 追加十：知识库页接上四态（§6.3 第三份），顺手量到页头那颗 32dp 的返回钮

## 20.1 指导书这一格的原话

> 每个目的地只允许这四类顶层状态：`Loading` / `Content<T>` / `Empty(message, action)` / `Error(message, retry)`
> Provider、反馈案例、知识库、捕获范围都用同一套空态/错误态，**而不是每页自己发明卡片、内联文字、弹窗或展开区**。

替换前的状态：反馈案例页已接（`e359930`）、供应商区已接（`d902514`→`80bc78e`），
知识库页是第三家——它当时写的正是指导书点名的那两种形状：`ui/KnowledgeBaseActivity.kt:281`
的 `if (kbs.isEmpty())` 就地画一张约 40 行的 `Card`（72dp 圆形图标 + `titleLarge` 标题 +
一行指路文字"点下方「新建知识库」…"），外加页面自己的一套判定。
§6.1 对 `LbEmptyState` 明写的是"**动作是一处操作，不是一行文字**"——那行指路文字正是被点名的那个形状。

## 20.2 Error 那一格：先问有没有真信号，没有就不画

四格里唯一不能"顺手编一个"的是 Error。我把读取链一路读到底：

- `KnowledgeCatalogStore.list()` 把"根目录读不动"与"一个库都没有"**合并成同一个 `emptyList()`**
  （`listFiles()` 返回 null → `?: emptyList()`），单库 `kb.json` 坏了也只是丢弃那一条并记日志。
  → 从 `repo.listAll()` 的返回值里**读不出**失败，这条不能拿来当 Error 的来源。
- 但 `loadState()` 里还有第二处读取：`repo.getActive()` → `securePrefs.activeKbName` →
  `EncryptedSharedPreferences.getString`。`SecurePrefs` 只在**构造**时兜了降级路径
  （`runCatching { EncryptedSharedPreferences.create(...) }` → 回落明文 prefs），**逐次读没兜**；
  keystore 里那份值解不开时 `getString` 抛。这是真实故障类，不是我造的假想敌。
- 抛出后今天的行为：`viewModelScope.launch` 里没人接，`_state` 停在初始值 →
  页面永远落在"第一次数据还没到"那一格，转圈转到用户退出。**所以这一格改的是一条已有的死路**，
  不是给四态凑数。

没有为了这一格把 `listAll()` 改成可空/可抛：`grep -rn "listAll(" app/src | wc -l` 实扫
**59 处引用**（测试里 50 处、生产 9 行——生产那 9 行还含 1 处定义与 2 处注释，真调用点 6 个），
为省一个 `loadFailed` 布尔去改返回形状不划算，而且会把刚并完的 catalog 判据重新劈成两份。
"根读不动 vs 真的空"这件事记在这里，归 §5.3 剩下的 catalog 写侧那一格一起处理。

## 20.3 判据只有一处

`internal fun kbScreenState(state, emptyMessage, errorMessage, newKb, retry)`：
`Loading > Error > Empty > Content`，与另两家同一副顺序；纯函数，`KbScreenStateMappingTest` 5 格穷举
（`loaded` × `loadFailed` × 列表长度 三个维度，含"失败 + 有残留列表"那格）。

- `KbListState` 多一个 `loadFailed`，**只由 `loadState()` 写**；失败时同时置 `loaded = true`
  （否则判据还要猜谁优先）。
- 失败不清空列表：`knowledgeBases` 留着上一次读到的那份，但整份快照被标记为失败，
  判据据此把 Error 排在 Content 之前——所以"留着"不会被画成"刚读到的"。
- `Content` 交回**整份快照**而不是只有 `List`：卡片要不要标"当前激活"取决于 `activeName`，
  只交 List 就得在 UI 里另拿一份状态，判据立刻变成两处。
- 取消走 `throw e`（与建库事务同一判据），不算读失败。

底部那颗"新建知识库 / 导入知识库"大按钮是页面常驻工具条，四态都留着：
空态那颗动作开的是**问卷向导**，这条右边那颗开的是**文件选择器**，两件事，不算重复入口。

## 20.4 顺手量到的一条：页头返回钮 32×32dp

这轮第一次把整屏可交互节点交给 `SemanticsProbe` 量（此前只有 `PanelHeader` 有过用例，
`ScreenHeader` 一颗都没有）。量到：`「返回」 role=无 … 尺寸 32x32dp @(24,32)`，
低于 §6.5 的 48dp 下限，而四个二级页（知识库管理 / 编辑知识库 / 设置页 / 新建知识库问卷）共用这个页头。

改法：`ScreenHeaderDimens.BACK_HOTZONE_DP` 32 → 48。行高本来就是 48dp，热区垫满行高不改版式，
字形仍 22dp；唯一可见变化是标题右移 16dp。反向证明见 §20.5 的 M4。

## 20.5 变异探针：四条，各自红在该红的那格

| 变异 | 红在哪 | 实测 |
|---|---|---|
| M1 判据里把 Error 挪到 Empty 之后 | mapping 2 格 + 目的地 2 格 | `a failed read is Error even when a stale list is still in hand`、`the four slots are distinguishable…`、`the four cells … one renderer`（`Text contains 'KB_ERROR_SENTINEL'` 找不到节点）、`the error cell offers a retry…`（`expected:<1> but was:<0>`） |
| M2 失败时不宣布 `loaded = true` | VM 2 格 | `loadFailed 必须与 loaded 同真`、`a throwing list read is recorded the same way` |
| M3 空态那颗动作退回"一行文字" | mapping 1 格 + 目的地 2 格 | `Empty 必须带一个动作`、`空态没有动作入口`、`只量到 3 个可交互节点` |
| M4 返回钮热区退回 32dp | 整屏热区那格 | `有 1/4 个可交互节点小于 48dp … 「返回」 32x32dp @(24,32)` |

M1+M4 一次跑（5 红 / 9 完成）、M2+M3 一次跑（5 红 / 15 完成），互不掩盖：每条红的测试都点得出
是哪一个变异造成的。回滚用 `_temp/mut63.py revert`，脚本要求"变异片段恰好命中 1 次"才回写，
**没改上的变异不允许冒充已证伪**。全部回滚后复跑全量。

## 20.6 实测（数字全部来自当次命令输出）

| 量 | 结果 |
|---|---|
| 全量单测 | `GRADLE_RC=0`：**153 套件 / 1213 tests / 0 失败 / 0 错误 / 0 跳过**；起点 09:06:23，全部 XML mtime 09:09:15，`stale=0` |
| 与上一格对账 | 1198 → 1213（+15 = 本轮 mapping 5 + 目的地 4 + VM 6），150 → 153 套（+3 = 我新写的三个类）。**两处增量互相对得上** |
| 受影响既有类 | `KnowledgeBaseViewModelTest` 15 格、`LbAsyncStateTest` 6 格、`PackageDependencyTest` 6 格、`UiLayerDependencyContractTest` 6 格、`UiStringLiteralBudgetTest` 4 格：本轮改动没把它们挤红 |
| lint | 报告 09:13:14 重生成；实测 70 条 / 15 规则，进预算 69 / 14，advisory 1；`check_lint_budget.sh` rc=0（**不带管道**单独取退出码） |
| 其它闸 | 工单编号 `strip_ticket_ids.py --check` rc=0；prompt 资产 lock rc=0；跨层计数 **6** 条（与上一格同数）；lint 判据自测 27 格 rc=0 |
| 文案 | 用户可见中文字面量 TEXT **250 → 247**（那张卡搬走 3 处），棘轮同步改小；DESC 12 / STATE 0 本轮没动。新 key 中英各 5 条 |

## 20.7 §6.3 逐字对照（指导书点名的四个目的地）

| 指导书原文 | 现在 |
|---|---|
| "Provider … 用同一套空态/错误态" | 已有（`d902514`→`80bc78e`）。本轮复跑其组件用例 6 格仍绿 |
| "反馈案例 … 用同一套" | 已有（`e359930`） |
| "知识库 … 用同一套" | **本轮**：四格全接，判据一处，空态带真动作；那张自造 Card 与那行指路文字一起删 |
| "捕获范围 … 用同一套" | **未做**。`ui/home/CaptureAppsScreen.kt:75` 仍是 `if (allowed.isEmpty())` + 内联 `Text(capture_apps_empty_hint)`，:115 还有第二处（搜索后为空）。这是下一格 |
| "而不是每页自己发明卡片、内联文字、弹窗或展开区" | 知识库页这一次同时少了"卡片"和"内联文字"两种形状；捕获范围那格还欠着 |
| 四类状态的签名 | 仓库里是 `Empty(message: String, action: ScreenAction?)`，指导书写 `UiText` / `Action`。差异原因（不另造 UiText：调用方 `stringResource` 解析后交出 String，用户可见字面量由 §20.6 那把预算尺管着）此前已记，本轮沿用 |
| "每个目的地**只允许**这四类顶层状态" | 判据函数 `kbScreenState` 是这一页唯一的四态出口；`Loading` 之外没有第五格 |

## 20.8 这一格没做的

- 页头那颗「返回」的 `contentDescription` 仍是硬编码中文（`ScreenHeader.kt:78`），英文环境下会念中文——
  属于"资源驱动"那笔账，本轮只按 §6.5 改了热区，没顺手改文案。
- 空态版式不再有 72dp 图标与两行标题（共用组件只有一段说明 + 一颗动作）。这是 §6.3 要的结果，
  不是回归；但它**是一处可见的视觉变化**，截图基线那一格（§6.5，仍故意推后）回来时要认这笔。
- `UiLayerDependencyContractTest` 里 `production sources carry no debt-note comments without a fix`
  这条闸**看不见自己的目标**：判据先用 `codeOf()` 剥掉注释，再拿剥完的代码去匹配"审计技术债 / 修复方向"，
  而这两个词只可能出现在注释里 → 恒不命中。本轮只静态读出这个形状，**没注反例验证**；
  下一格顺手注一份反例证明它恒绿（按"新断言必须先被坏实现打破"的规矩，届时要么给它牙，要么删掉它）。
- 捕获范围那一格、§6.4 `ResultArea` 拆分、§6.1 那九个 `Lb*` 组件与令牌迁移。

---

# 追加十一：捕获范围页接上四态（§6.3 四家齐了），以及那颗 288×15dp 的输入框

## 21.1 这一页替换前长什么样

指导书 §6.3 点名的第四家，也是此前**一格用例都没有**的一家（全仓 grep `CaptureApp` 在
`test/`、`androidTest/` 里零命中）。量到的形状是三处各说一遍同一件事：

- `CaptureAppsScreen.kt:75` 自造一张 inset 卡片：「尚未选择任何 App，消息捕获实际处于关闭状态」；
- `:101` 一行「已选 0 个」；
- `:116` 列表过滤为空时一行内联 `Text`「没有匹配的 App」。

前两处是同一个事实说两遍，而其中一遍正是 §6.3 点名的"每页自己发明卡片、内联文字"。
现在：inset 卡片删掉；状态行改用**首页那两行同款文案**（`capture_apps_row_subtitle` / `_none`），
0 的时候连后果一起说（"未选择 App · 不会捕获任何内容"），比"已选 0 个"多一句、比那张卡少一层；
列表区走 `LbAsyncState`，判据并成一处纯函数 `captureScreenState`。

## 21.2 只有三格：Loading 在这一格是装饰分支，就不画

这一页两个数据源都是同步的——`captureAllowedPackages` 在 ViewModel 构造时就把 prefs 里那份读出来，
`selectableCaptureTargets` 是组合期一次同步枚举 ⇒ 首帧就有答案。
指导书说"每个目的地只允许这四类"，是**词表上限**，不是"四格必须都出现"；
画一个永远不出现的转圈分支，等于给下一轮留一条没人走过的假路。
真要让 Loading 有意义，得先把那次同步 IPC 挪到 IO 上——那笔属于主线程/性能账，写在 §21.7。

## 21.3 Error 那一格：信号是真的，只是在上一层被吞掉了

```kotlin
// 替换前
return runCatching { pm.queryIntentActivities(launchIntent, 0).orEmpty() }
    .getOrDefault(emptyList())
```

抛异常、平台回 null、确实一台可启动的都没有——**三种答案压成一个空表**。
于是页面对用户说"这台设备没有可授权的 App"，而真相可能是那次同步 IPC 失败了
（装机量大时 `TransactionTooLargeException`、远端进程死亡都从这里过）。
对一个默认 fail-closed 的采集功能，这是把用户往"我的 App 怎么都不见了"上误导，
而且空态没有重试，因为系统以为没什么可重试。

现在 `selectableCaptureTargets` 返回 `List<CaptureApp>?`：抛 → null（错误态 + 真会重扫的重试）；
空表 → 仍然是空表（**不许升格成失败**，否则错误页+重试永远出不去——这条有反向用例钉着）。

**为什么这页敢改签名、知识库那页不敢**：`selectableCaptureTargets` 全仓只有一个生产调用点
（就是这页），改签名代价是一个文件；`listAll()` 实扫 59 处引用（生产真调用点 6 个），
为省一个布尔去改返回形状不划算。两处共同守的规矩是同一条：*没有真信号就不画 Error 格*。

一条**没验到**的要写清：平台真回 null 那条分支在本机构造不出反例——当前 compileSdk 把这个方法
标成 `@NonNull`，mockk 连 `returns null` 都在编译期拒掉。所以那条 `?:` 是**防注解撒谎的兜底**，
不当已验收益记账（留着的理由只有一个：注解拦不住 ROM 真回 null，漏出去就是组合期 NPE 崩整页）。

## 21.4 又量到一条真缺陷：那颗输入框的可点节点是 288×15dp（`cbcdebe`）

上一格给知识库页空态做整屏热区检查时暴露了页头 32dp；这一格给捕获页做**同一把尺**，
暴露的是搜索框：外层 `Box` 有 `AppDimens.INPUT_ROW_HEIGHT_DP = 36`，
而真正带点击/编辑语义的 `BasicTextField` 节点量出来 **288x15dp @(36,172)**。
这恰是 `SemanticsProbe` 注释里写的那种形状——"只放大外层容器而点击仍挂在子节点上，等于没改"；
指导书 :531 要的是"所有 clickable/toggleable bounds ≥48×48dp"，:596 验收线写"无小于 48dp 热区"。

修法两步一起做才成立：

1. 共享档 `INPUT_ROW_HEIGHT_DP` 36 → **48**（复用方：`CompactInput`、`PanelTextInput` 默认高、
   `ProviderSection` 两行、`KbEditActivity` 一行）；
2. `heightIn(min = TOUCH_TARGET_MIN_DP)` 挂到 `CompactInput` 与 `PanelTextInput`
   **各自的可编辑节点**上——只抬外层那一档不改里面这颗，量出来还是 15dp。

`UiBaselineRegressionTest` 那条锁 36 的断言按它自己写的规矩同步改 48，并注明**这是故意漂移**。
上一格只给 `PanelTextInput` 加了读屏名字、没量尺寸；同一颗节点两笔账，这次补齐。
两颗各自有牙：N1 只撤 `CompactInput` 的 heightIn ⇒ 它那格与捕获页整屏格同时红；
N5 只撤 `PanelTextInput` 的 ⇒ 只有它那格红。**不是一张网蹭另一张网的绿。**

## 21.5 lint：一条是我新增的（修代码），一条是还掉的债（落账）

- `AutoboxingStateCreation` 实测 7 > 预算 6：是我那句 `mutableStateOf(0)`（`rescanTick`）。
  改 `mutableIntStateOf(0)`，**没抬预算**——这条正是"禁止抬预算了事"那把尺当场拦下来的。
- `PluralsCandidate` 实测 2 < 预算 3：删掉「已选 %1$d 个」这条重复文案顺带还掉的债。
  闸要求"还了一条也要重跑，否则下一轮拿旧数当现状"，用 `--rewrite` 落账；
  改完 `git diff scripts/lint-budget.txt` 看过——**只动了那一行**才敢写进账本。
- 报告重生成后（09:53:20）实测 69 条 / 15 规则，进预算 68 / 14，advisory 1。

## 21.6 用例与变异

| 新用例 | 格数 | 钉什么 |
|---|---|---|
| `CaptureScreenStateMappingTest` | 7 | 读不出来≠真的没有；两个 Empty 各说各的话；过滤判据（标签/包名、忽略大小写、纯空格等于没搜）；四格可区分表 |
| `CaptureAppsScreenStatesTest` | 5 | **整页**挂载走共用渲染器；重试那颗**确实又枚举了一次**（`verify` 数调用次数）；勾选仍落到 `setCaptureAllowed(包名, true)`；整屏每个可交互节点过 §6.5 尺 |
| `CaptureTargetEnumerationFailureTest` | 4 | 抛 → null；空表 → 空表；排除自身/去重/排序不动；二次拒绝的类别仍列出但标灰 |
| `ComposerInputLabelTest`（补 2 格） | +2 | 两颗输入框的可编辑节点自己 ≥48dp |

变异五条，各自红在该红的那格（每条都要求"目标片段恰好命中一次"才算注入）：
N1 撤 CompactInput 热区 → 2 格红；N2 把 Error 并进 Empty → 4 格红（含 3 格判据）；
N3 重试不再触发重扫 → `Verification failed: call 1 of 1 … needs at least 2`（与 N2 的红不同形状，
所以敢分开跑、不互相掩盖）；N4 抛异常退回空表 → 1 格红；N5 撤 PanelTextInput 热区 → 1 格红。

## 21.7 §6.3 逐字对照：指导书点名的四家全部到位

| 指导书原文 | 现在 |
|---|---|
| "Provider … 用同一套空态/错误态" | 已（`d902514`→`80bc78e`） |
| "反馈案例 … 用同一套" | 已（`e359930`） |
| "知识库 … 用同一套" | 已（`3dc2180`，账本 §20） |
| "捕获范围 … 用同一套" | **本轮**（`054b789`）：inset 卡片与内联文字删掉，判据一处、版式一处 |
| "而不是每页自己发明卡片、内联文字、弹窗或展开区" | 四家都不再有自造形状；本轮消掉的是"卡片 + 内联文字"各一处 |
| "每个目的地只允许这四类顶层状态" | 词表上限已满足；**知识库/捕获两家的 Loading 与捕获家的 Empty/Error 是"有信号才画"**，各自理由写在 §20.2 / §21.2 |
| 签名差异（`UiText`/`Action` vs `String`/`ScreenAction`） | 沿用既有判定：不另造 UiText，调用方解析资源后交出 String，由字面量预算管着 |

## 21.8 这一格没做的（别当我顺手清了）

- `capture_apps_back` 全仓零引用，**早就**是死资源（在 `UnusedResources` 既有计数里）。
  本轮只删被自己的改动弄死的两条，不动这条——那是另一笔账。
- 勾选行"现在有没有被勾上"读屏念不念得出来：本轮**只挂不断**（属 §6.5 第②栏）。
  要判得先看语义树里到底有没有 `Selected`/`ToggleableState`，别顺手扩范围。
- 候选枚举仍在主线程（同步 IPC，`remember` 只挡了每帧重算）。挪到 IO 之后 Loading 那格才有意义。
- JVM 上 `ResolveInfo.loadLabel` 取不到标签 ⇒ 断言里 displayName 就是包名；
  真机上排序键会随标签变，**这条差异本轮没有任何用例覆盖**（androidTest 里也没有捕获页的用例）。
- §6.4 `ResultArea` 拆分、§6.1 那九颗 `Lb*` 组件与令牌迁移仍未动。

---

# 追加十二：design token 整体搬进 core/designsystem（第三步-1 的前半），两把尺一起收紧

## 22.1 指导书把这一步和下一步都写好了

- 第三步 1 号步骤原文：**「建 `core/designsystem` tokens + 10 个基础组件。」**
- §5.1 目录图：`designsystem/  token、统一组件、语义规范`。
- 而 `PackageDependencyTest` 里那条登记过的欠账，注释连**还法**都写着：
  「等 theme 整体迁进 core/designsystem，这里要再加一条前缀 `"com.lovebrain.app.ui."`」。

搬家之前只有后两样的一半：`core/designsystem/` 里躺着 `ScreenState.kt` 与 `LbAsyncState.kt`
（含 `LbEmptyState`），而 token 全住在 `ui.theme` 下面——所以"设计系统"反过来依赖 UI 层。

一处指导书自身对不上的地方，逐字对照时说明：§7 写「10 个基础组件」，§6.1 那张表实际是
**11 行**（`LbMetricCard/Grid`、`LbModalSheet/Dialog` 各占一行；按名字展开是 13 个）。
本轮按表为准，不按"10"这个数。

## 22.2 搬了什么、为什么这么切

| 原位置 | 现位置 | 说明 |
|---|---|---|
| `ui/theme/Color.kt` | `core/designsystem/Color.kt` | 整档（`git mv`，历史保留为 R） |
| `ui/theme/Dimens.kt` | `core/designsystem/Dimens.kt` | 整档（`AppDimens`） |
| `ui/theme/Type.kt` | `core/designsystem/Type.kt` | 整档（`AppTypography` + Markdown 两个字号） |
| `ui/theme/Theme.kt` 里的 `object Spacing` | `core/designsystem/Spacing.kt` | 从 Material 包装里切出来 |
| `ui/theme/Theme.kt` 里的 `object LoveBrainShape` | `core/designsystem/Shapes.kt` | **文件名跟着内容走**：装着形状的文件不许叫 Spacing |
| `ui/theme/Theme.kt`（`LoveBrainTheme` + ColorScheme + 无水波 Indication） | 原地不动 | 它确实是 Compose 主题包装，不是 token |
| `test/…/ui/theme/UiBaselineRegressionTest.kt`、`ContrastRegressionTest.kt` | `test/…/core/designsystem/` | 测试包跟着被测包走 |

## 22.3 波及面是量出来的，不是估的

清单 **48 个文件、实际改写 38 个、补 16 行通配 import**。另有两类是脚本第一轮没看见的：

- **2 处不走 import 的全限定引用**——`SolidColor(com.lovebrain.app.ui.theme.Primary)`
  （`CompactInput`）与 `com.lovebrain.app.ui.theme.PrimarySubtle`（`HomeComponents`）。
  编译器当场报 `Unresolved reference: Primary`，所以"改完就漏"没发生。
- **2 个与被测包同包的测试**一行 import 都没有（同包直接可见），搬包之后必须跟着搬。

第一次跑脚本还 ABORT 在 `Theme.kt` 的切片正则上：本仓库 `Theme.kt`/`HomeComponents.kt` 是 **CRLF**
而 `LbAsyncState.kt` 是 **LF**，按 `\n` 匹配直接扑空，而扑空之前已经把三个文件 `git mv` 走了。
脚本因此改成全程按 LF 处理、写回时还原原行尾，并且**可重复跑**（源文件不在就只补 package 行）。
这条记进坑表第 53 条。

## 22.4 搬家撞红的那两格，都是承重的

全量跑第一遍红两格，两格都是**按路径读源码**的断言：

1. `ContrastRegressionTest` 直接读 `ui/theme/Color.kt` 正则解析 `Color.hsl(h, s, l)` 三元组
   ——路径改了读不到文件。改成读 `core/designsystem/Color.kt`。
2. `ResultAreaStructureTest > theme token files exist and are stable` 判的是
   「`ui/theme` 下有 Color/Dimens/Theme/Type 四个文件」，搬家之后三个不在了 ⇒ 红。
   **撞红说明它有牙**，但判据本身太弱：只判"文件名在不在"。顺着搬家升级成方向判据——
   token 必须在 `core/designsystem`、`Theme.kt` 留在 `ui/theme`、并且**反向断言 token 不许再回
   `ui/theme`**（搬家被 revert 一半是最难发现的那种坏法）。

同时纠正一句**写在注释里的假话**：那格的注释是「SHA 校验由 CI 层完成」。
`grep .github/workflows` 里 theme/Color.kt 的哈希计算**零命中**——CI 从来不算 token 的 SHA。
注释承诺一道不存在的闸，比没有闸更坏（下一个人会以为已经有人看着）。注释改成判它真正判的东西。

## 22.5 两把尺一起收紧（这才是这格的重点）

- JVM 侧：`PackageDependencyTest.forbidden["core"]` 加上 `"com.lovebrain.app.ui."`。
- 报告侧：`scripts/package_deps_report.py` 的 `FORBIDDEN` **原来根本没有 `core` 这一条**，
  而它文件开头自己写着"规则与 PackageDependencyTest 里那份一一对应"。
  不补的后果很具体：谁把 `core → ui` 的 import 引回来，JVM 闸会红，
  而 `package_deps_report.sh --count` 仍旧报同一个数——"跨层条数没长"这句话在 CI 侧就是空的。

变异 T1（往 `LbAsyncState.kt` 注入一行真实存在的 `import com.lovebrain.app.ui.theme.LoveBrainTheme`）：

| 尺 | 注入后 | 撤掉后 |
|---|---|---|
| `package_deps_report.sh --count` | **7**（6 → 7） | 6 |
| `PackageDependencyTest` | 6 格里 **3 格红**（无新增越界 / 基线仍对得上 / 条数对得上） | 全绿 |

**两把尺同时看得见，才配叫"同一套规则"。**（坑表 47 条"本机与 CI 也会漂"的同族。）

## 22.6 实测

| 量 | 结果 |
|---|---|
| 全量单测 | `GRADLE_RC=0`：**156 套件 / 1231 tests / 0 失败 / 0 错误 / 0 跳过**，无陈旧 XML（起点 11:03:23） |
| 与上一格对账 | 1231 / 156 **一字没动**——搬家不改行为，"测试数不变"本身就是这条声明的证据（数字变了才要怀疑） |
| lint | 报告重生成（11:09:23）实测 69 / 15、进预算 **68 / 14**、advisory 1，`check_lint_budget.sh` rc=0（与搬家前同一组数：没新增也没误还） |
| androidTest | `:app:assembleAndroidTest` rc=0 |
| 其它闸 | 工单编号 rc=0；prompt 资产 lock rc=0 且 `git diff --exit-code 286c9406..HEAD -- assets/engine` rc=0；跨层 **6** 条；判据自测 27 格 rc=0 |
| 历史 | 5 个文件记为 rename（R），不是删除+新增 |

## 22.7 §6.1 与第三步-1 现在走到哪

| 指导书 | 现在 |
|---|---|
| 第三步-1「建 `core/designsystem` **tokens**」 | **本轮完成**：Color / AppDimens / AppTypography / Spacing / LoveBrainShape 全部到位，且 core 不再允许 import ui |
| 第三步-1「+ 10 个基础组件」 | 未完成。表里 11 行现在只有 `LbAsyncState`、`LbEmptyState` 两颗在位。其余 9 行的**形状其实早有主人**，只是名字与所在包不按表：`HomeTopBar`≈`LbTopBar`、`HomeSectionHeader`≈`LbSection`、`HomeActionCard`≈`LbActionCard`、`HomeSettingRow`≈`LbSettingRow`、`UsageSummary`/`UsageMetric`≈`LbMetricCard/Grid` ⇒ 下一格是"按表改名 + 搬进 core/designsystem + 接 §6.2 首页四段"，**不是从零造** |
| §6.1 末句「禁止创建只在一个页面看起来不一样的按钮/卡片」 | 部分成立：同一形状在各页只有一个主人（上表可查），但**没有闸**在拦"新页面又画一张卡"。等它们按表改名搬齐，才好按 `Lb*` 名字扫调用方 |
| §6.5「浅色/深色（若暂不支持深色，明确锁定浅色）」 | `Color.kt` 头部仍写着"暗色模式完全删除，全站固定亮色"——锁定的是**注释**，不是资源/主题。真要"明确锁定"得看 `values-night` 与 `LoveBrainTheme` 是否拒绝跟随系统，**本轮没查** |

## 22.8 这格没做的

- `Spacing.kt` / `Shapes.kt` 是**新增文件**（内容来自 `Theme.kt`），git 不记为 rename。
  账在这里：两把标尺的原文在 `3605edd^:app/src/main/java/com/lovebrain/app/ui/theme/Theme.kt`。
- `ui/theme/Theme.kt` 现在只剩一档 Material 包装 + 一个无水波 `Indication`；
  是否并进 `core/designsystem` 或改名 `LoveBrainTheme.kt`，等 §6.1 那格一起判，别单独动。
- §6.2 首页四段、§6.4 `ResultArea` 拆分、九颗组件改名搬包；以及那句
  "还有多少屏没被整屏热区量过"（`KbEditActivity`、设置页两颗）仍未做。
- `ContrastRegressionTest` 读的是 `Color.kt` 的**源码文本**再自己算 WCAG 对比度
  （正则解析 `Color.hsl(h, s, l)` 三元组）。这格只把它读的路径挪了，没重划它的覆盖面：
  它断言的是 TextHint vs 三个底、Success 与白字、Warning 两组 ≥4.5:1，
  **不是全部颜色两两配对**——做 §6.5 颜色那一栏时要按这个边界说，别说成"全站对比度已锁"。
- 顺带记一条界线，免得以后一刀切：这格读源码是正当的（token 定义本身就是被测对象），
  而"别拿源码 grep 当 UI 证据"那条针对的是**尺寸/热区/层级**这类必须读语义树的事实。
  同一种"读文件"的断言，一个该读、一个不该读，区别在被测事实是不是编译期常量。

---

# 追加十三：§6.1 的 `LbStatusBadge` 落地，首页那五处平行 `when` 并成一份判据

## 23.1 表上那一行与搬家前的现实

指导书 §6.1 表里这一行写的是：

> `LbStatusBadge` | Running/Hidden/Off/Error 的**颜色和文案体系**

搬家前没有这张表。首页 `HomeScreen.kt` 是**五个平行 `when`**，每个都把同样三个条件重判一遍：

```kotlin
val statusText  = when { !overlayGranted -> "未授权"; !isServiceRunning -> "未启动"; … }
val statusColor = when { !overlayGranted || !isServiceRunning -> Neutral300; … }
val description = when { !overlayGranted -> "需要悬浮窗权限才能显示军师浮窗"; … }
val buttonText  = when { !overlayGranted -> "授权悬浮窗"; TEMP_HIDDEN -> "恢复军师"; … }
val buttonAction = when { !overlayGranted -> onStartService; TEMP_HIDDEN -> onRestore; … }
```

而胶囊的**配方**（状态色 15% 底 + 状态色字）内联在 `AssistantStatusCard` 里，
由调用方传一个 `Color` 进来决定。于是"这一档是什么颜色"和"这一档说什么话"分在 5 处，
加一个状态时最容易漏的恰恰是颜色那一处——**而编译器一声不响**。

现在：`LbStatus`（`labelRes` + `color` 同源的一张枚举表）+ `LbStatusBadge`（唯一画它的地方）+
`advisorStatus(授权, 服务在不在, windowState)` 一次算出 `(badge, 说明, 按钮, 意图)` 一份不可变快照。
`AssistantStatusCard` 的签名从四个参数收成**一个** `AdvisorStatus`——旧签名允许调用方把
"运行中"配成灰色，新签名根本递不进去这种组合。

## 23.2 两处不是搬运、是修（都有可达性证据，不是想象）

穷举 16 组输入（2 授权 × 2 服务在不在 × 4 窗口状态）逐组比旧判据，差两格：

1. **`serviceRunning && window == STOPPED`**：旧代码落 `else` ⇒ 说"运行中 · 军师正在运行，
   长按消息即可捕获"。这一组真能读到：`showBubble()` 里 `wm.addView` 失败会 `stopSelf()`，
   而 **`stopSelf()` 是异步的**——从抛出到 `onDestroy` 把 `instance` 置空之间，
   首页看到的就是"实例在、窗口从没出现过"。现在这一档是 `WindowMissing` + Error 色 +
   一句说明为什么按钮仍写"打开军师"：`EventBus.requestPanel` 那条路不依赖悬浮球，
   服务活着就能 `showPanel()`（`openPanelFromHome` → `requestPanel` → 收集器里
   `if (!isPanelShowing) showPanel()` 逐读过），**所以那颗按钮不是死的**。
2. **`!serviceRunning && window == TEMP_HIDDEN`**：`onDestroy` 里 `instance = null` 先跑、
   `setWindowState(STOPPED)` 后跑，所以这一刻"实例已空、状态仍写着已隐藏"能读到。
   旧 `buttonText` / `buttonAction` 把 `TEMP_HIDDEN` 排在 `isServiceRunning` **之前**
   ⇒ 卡片上是一颗「恢复军师」，点了发出 `ACTION_RESTORE`，新实例里
   `restoreFromTempHidden()` 第一行 `if (windowState != TEMP_HIDDEN) return` —— **点了没反应**。
   现在这一档判"未启动 · 启动军师悬浮窗"，走的真的能把球拉起来。

## 23.3 两处对表名的偏离，写清楚而不是偷偷降信息量

- **多了第五档 `NoPermission`**。表里只有 Running/Hidden/Off/Error；但旧代码把"未授权"与
  "未启动"分开显示，而用户要做的事完全不同（去系统授权 vs 点一下启动）。
  为了对上四个名字并成 `Off`，等于**少说一件事**——所以不并，并在这里记下偏离。
- **`Error` 那一档 entry 叫 `WindowMissing`**。同包（`core.designsystem`）里已经有一个颜色叫
  `Error`（`Color.kt`），枚举项再叫 `Error` 会在构造参数位置上撞名。含义不变。

## 23.4 读屏与中英（§6.5 第②栏 + 第⑥栏各一条）

- 胶囊带 `contentDescription`（父容器合并语义后孤立 `Text` 可能不被单独播报），
  并且 **`liveRegion = Polite`**：状态从"运行中"变成"已隐藏"时 TalkBack **自己补播一句**，
  用户不必去找那一格。`stringResource` 在 `semantics {}` 外面解析（语义 lambda 延后执行，
  在里面现调资源是这仓库踩过并按进坑表的形状）。
- 「军师已暂时隐藏，点击恢复」旧代码写了**两遍且用词不同**：首页是"点击恢复"、
  前台通知是"点此恢复"（`FloatingService.updateNotification`）。收成一条 `status_hidden_desc`，
  两处读同一份。通知其余三句（悬浮球/面板/已停止）**没有并进来**：它们说的是"点此返回设置"
  这类通知专属动作，不是状态词，硬并会说出错话。
- 五档状态词逐档比中英：`values-en` 少一条就静默回落到中文，这条断言当场红（变异 T5 验过）。

## 23.5 用例与变异——**其中一条暴露了性质格自己的强度边界**

12 格新用例：`AdvisorStatusTest` 8（16 组矩阵 + 逐分支标签 + 两处修复各一格 + 颜色两格）、
`LbStatusBadgeTest` 4（语义树挂载：名字、liveRegion、换档换词、中英逐档互不相同）。

变异五发，按"红格互不重叠"分三批跑：

| 变异 | 红了谁 |
|---|---|
| T1 `Hidden` 档按钮换成"打开军师" | 逐分支比标签那格 |
| T2 `WindowMissing` 退回 `Running` | 窗口那格 + 性质格（5 档变 4 档） |
| T3 撤 `liveRegion` | 语义那格 |
| T4 撤 `contentDescription` | 语义那格 + 换档换词那格 |
| T5 删 `values-en` 一条状态词 | 中英那格 |

**T1 把性质格自己的弱点照出来了**：`badge 决定其余三项` 这一格**照样绿**。原因是
`Hidden` 全矩阵只有一组输入走到它，没有第二组来跟它比——同 badge 跨多组输入的不变式，
只在 `NoPermission`（8 组）与 `Off`（4 组）这两档真的有牙，而那恰恰是旧五个 `when`
最容易各说各话的两档。这条限制已经写进用例 KDoc（"别把'badge 决定其余三项'读成全覆盖"），
逐分支那几格才是管单例档的。

## 23.6 实测

| 量 | 结果 |
|---|---|
| 全量单测 | `GRADLE_RC=0`：**1243 tests / 158 套件 / 0 失败 / 0 错误 / 0 跳过**，无陈旧 XML |
| 与上一格对账 | 1231 → 1243 = **+12**（新增 8 + 4），156 → 158 套 = **+2**（新增两个类）⇒ 两处增量互相咬得上 |
| lint | 报告重生成（11:38:09）实测 **69 / 15**、进预算 **68 / 14**、advisory 1 —— 与上一格同一组数（新组件没带新增债） |
| 其它闸 | 跨层 **6** 条；工单编号 rc=0；prompt 资产 lock rc=0；`:app:assembleAndroidTest` rc=0；变异全撤后目标类复跑 rc=0 |

## 23.7 §6.1 那张表现在走到哪（11 行逐行）

| 表里的行 | 现在 |
|---|---|
| `LbAsyncState` / `LbEmptyState` | ✅ 已在 `core/designsystem`（§20/§21 四家全走它） |
| `LbStatusBadge` | ✅ **本轮** |
| `LbSection` | ❌ 形状有主人 `HomeSectionHeader`，名字/位置不按表 |
| `LbTopBar` | ❌ 同上：`HomeTopBar` |
| `LbScreenScaffold` | ❌ 同上：`ui/common/ScreenPage` + `ScreenHeader` |
| `LbActionCard` | ❌ 同上：`HomeActionCard`（首页两处已在用同一颗，形状不重复） |
| `LbSettingRow` | ❌ 同上：`HomeSettingRow` |
| `LbMetricCard/Grid` | ❌ 同上：`UsageSummary` / `UsageMetric` |
| `LbPrimaryButton`（Idle/Loading/Disabled/Stop 四态） | ❌ **真的没有同名物**：面板主按钮那四态散在 `ReplyPrimaryActions` 一带，是下一格里唯一"不是改名而是要抽出来"的行 |
| `LbModalSheet/Dialog` | ❌ 同名物无；各页现在各自用 `AlertDialog`（禁 Toast 那条已锁，浮层语法未收口） |
| §6.1 末句"禁止创建只在一个页面看起来不一样的按钮/卡片" | 部分成立：形状各有唯一主人，但**没有闸**拦"新页面又画一张卡"（主人名字不按表，装闸也扫不出来）⇒ 等改名搬齐 |

## 23.8 这格没做的（含一条量出来的尺子盲区）

- **字面量那把尺看不见"自定义组件参数位"**。粗测 `ui/` 下约 **70 条**含汉字的字面量落在
  `Text(` / `contentDescription =` / `stateDescription =` 三个锚点之外（这个数含构造函数位的
  假阳，如 `ProviderTicket(...)`），确认的形状是：
  `HomeSectionHeader("快捷功能")`、`HomeSettingRow(title = "模型供应商", trailingText = "管理")`、
  `UsageMetric("累计生成")`、`FilterChip("全部")`、`RowActionButton("编辑")`、`IconAction("确认")`。
  最直白的一对：`R.string.home_manage`（"管理"）**定义了但全仓零引用**，
  而 `HomeScreen.kt:161` 那儿写的是字面量 `"管理"`——字面量不进计数、资源躺在 `UnusedResources 33`
  里当死账，两头都看不见这笔。这就是"资源驱动"没真落地的形状。
  它们全是**用户听得见的话**，但预算的 TEXT 计数一个字都不涨——
  这与指导书 P1-05"禁止 production composable 新增直接用户可见字面量"的本意不符。
  下一格（或穿插格）给那把尺补第四个锚点：**任意大写开头的 composable 调用实参**。
  换尺会让数字变大（209 → 254 那次一样），届时要说清是量到了以前漏的，不是债涨了。
- 通知其余三句状态文案没并表（理由见 §23.4）。
- **设置行还在用同一个旧模式**：`HomeSettingRow(statusText = …, statusColor = if (…) Primary
  else Neutral300)`——"颜色由调用方交进来"这件事我刚从状态卡上拿掉，两行设置行还是它。
  而且供应商行传的是 `statusText = ""`（画一颗**没有字**的点）。接到 `LbStatus` 那张表之前，
  得先决定那颗空 statusText 是要文案还是不画点——归 §6.1 的 `LbSettingRow` 那一行。
- 首页四段（§6.2）结构本身没重排：本轮只换了第 2 段内部的判据；四段顺序今天已经是对的
  （顶部 / 军师状态主卡 / 快捷功能 / 服务设置 + 使用概览），但没有闸在看住它。
- 深色/浅色那条（§6.5）仍**没查**：`values-night` 与 `LoveBrainTheme` 是否真的拒绝跟随系统。


---

# 追加十四：设置行那个"从来不画状态词"的状态槽（§6.1 `LbSettingRow`），以及一条看不见它的旧闸

## 24.1 指导书那一行与组件实际做的事

§6.1 表里：

> `LbSettingRow` | 设置项；标题、说明、**状态**、尾部动作统一

组件的签名确实收了 `statusText: String? = null`，函数体里却只写了这一句：

```kotlin
// 状态点
if (statusText != null) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))   // ← 只画点
}
```

**`statusText` 的值从来没有被画出来过。**它唯一的用途是当"画不画点"的开关。
两个调用方各自的后果：

- 消息捕获那行：`if (accessibilityGranted) stringResource(if (captureEnabled) home_on else home_off) else null`
  ——认真判了无障碍授权、解析了"开/关"，然后**丢掉**。而 `R.string.home_on` / `home_off`
  在 lint 的 `UnusedResources` 里也不会被报，因为它们**确实被引用了**。
  引用了 ≠ 用上了：这笔账在两把尺（资源未使用 / 参数未使用）上都是隐形的。
- 模型供应商那行：`statusText = ""`，本意是"我只要一颗点"——用空串去顶一个不存在的开关，
  顺带让"状态槽有没有词"这件事只能靠读调用方代码猜。

## 24.2 为什么旧的那两条闸没抓住它

| 旧闸 | 为什么是绿的 |
|---|---|
| `ProductionUiContractTest > home trailing text action meets the touch floor` | 它 grep 的是 `ui/common/RowAction.kt` 里的 `MIN_HEIGHT_DP ≥ 48`——**另一个组件**。`HomeSettingRow` 尾部那颗"管理"是另写的一处 `heightIn(min = 32.dp)`，不在它视野里。这正是指导书 :306 点名的形状（"只检查源码里出现常量名 ⇒ 错误判绿"）换了个文件 |
| `dead parameters removed by the audit do not come back` | 那是**两个名字的黑名单**（`draftText`、`onSaveToKb`）。新死参数不在名单里就不会红；何况 `statusText` 连"未使用"都不算——它在 `if (…!= null)` 里被读了，只是**值被丢弃** |

所以这格把两件事都换到语义树上做：整行的可交互节点交给 `SemanticsProbe` 量，
状态词用 `onNodeWithText` 查它**在不在屏上**。旧写法在这组用例下必红（变异 T6 实测过）。

## 24.3 拆成两个旋钮，颜色收进一张表

- `dot: LbRowState?` —— 画不画点、什么颜色。新增 `core/designsystem/LbRowState`
  只有两档：`Ready(Primary)` / `NotReady(Neutral300)`。
  调用方从此不能各写一遍 `if (…) Primary else Neutral300`——上一格我刚从军师状态卡上
  拿掉同一个模式，这两行设置行还留着，这格补齐。
- `statusText: String?` —— 要不要在点旁边写那两个词；空白串不画（不留空文本节点）。

**为什么不复用上一格的 `LbStatus`**：那是"军师"的词汇表（运行中/已隐藏/未启动/未授权/窗口未出现）。
供应商行如果借用它，读屏就会对着一行配置念"运行中"——把一枚徽标说成另一件事实。
所以这里是两张小表，各自守自己那一域的词与色，而不是硬凑成一张大表。

## 24.4 可见变化与那条 32dp

两处**故意**的视觉变化，写清楚：
1. 捕获行现在真的会在点旁边显示「开 / 关」（以前那两个词是死数据）；
2. 尾部那颗「管理」的行高 32 → 48dp（§6.5 :531 所有 clickable ≥48×48；:596 验收线"无小于 48dp 热区"）。

那颗点是**装饰**：6dp、没有读屏名字，而且整行是 `clickable` ⇒ 语义合并会把子节点藏进父节点，
合并树里查不到它。用例因此走 `useUnmergedTree = true` 并同时核它 `6x6dp` 的实际尺寸。
**没有**为了"能被合并树查到"而给它加 `contentDescription`——那是让 TalkBack 多念一句废话。

## 24.5 用例与变异（只写实际跑过的）

`HomeSettingRowStateTest` 4 格（Robolectric + 语义树）：词画得出来 / 空串不留幽灵节点 /
点与词是两个独立旋钮 / 整屏可交互节点过热区与读屏命名下限。

| 变异 | 红了谁（实测） |
|---|---|
| T6 把组件体退回"一个参数两个用途、词不画" | 3 格红（词画不出来；两旋钮合并；空串那格失去依据） |
| T7 把尾部热区改回 32dp | `every interactive node in the row meets the touch and labeling floor` 当场红 |

本轮没有再跑别的变异——上一格我差点把没执行的探针结果写进账本，这里按实际输出记。

## 24.6 实测

| 量 | 结果 |
|---|---|
| 全量单测 | `GRADLE_RC=0`：**1247 tests / 159 套件 / 0 失败 / 0 错误 / 0 跳过**（起点 11:56:13，无陈旧 XML） |
| 与上一格对账 | 1243 → 1247 = **+4**，158 → 159 套 = **+1** ⇒ 两处增量互相咬得上 |
| 死 import 清理后复跑 | `:app:testDebugUnitTest --tests "…ui.home.*" --tests "…ProductionUiContractTest"` rc=0（删掉 `HomeScreen` 里两个不再被用的颜色 import） |
| lint | 报告重生成（12:02:45）实测 **69 / 15**、进预算 **68 / 14**、advisory 1，rc=0（与上一格同数：没新增也没误还） |
| 其它闸 | 跨层 **6** 条；工单编号 rc=0；prompt 资产 lock rc=0；判据自测 27 格 rc=0；`:app:assembleAndroidTest` rc=0 |

## 24.7 §6.1 表 11 行现在走到哪

| 行 | 状态 |
|---|---|
| `LbAsyncState`、`LbEmptyState`、`LbStatusBadge` | ✅ 已在 `core/designsystem`，四家目的地 / 首页状态卡各自接上 |
| `LbSettingRow` | **本轮补齐语义**（状态槽真的能显示状态、颜色有唯一所有者），但**名字与所在包还不按表**（仍叫 `HomeSettingRow`，在 `ui/home`）⇒ 改名搬包仍欠着 |
| `LbSection`、`LbActionCard`、`LbMetricCard/Grid`、`LbTopBar`、`LbScreenScaffold` | ❌ 形状各有主人，名字/包位置不按表 |
| `LbPrimaryButton`、`LbModalSheet/Dialog` | ❌ 真没有同名物 |
| 末句"禁止创建只在一个页面看起来不一样的按钮/卡片" | 仍**没有闸**。且现在能确定一件事：装在 `Lb*` 名字上会恒绿（今天按这些名字扫调用方是 0 个），装在"每屏可交互节点都过语义树"上才有牙——本轮就是这条路 |

## 24.8 这格没做的

- `HomeSettingRow` / `HomeActionCard` / `HomeSectionHeader` / `HomeTopBar` / `UsageSummary`
  的**改名 + 搬进 `core/designsystem`**（表里名字那半）仍没做，本轮只把它们其中一颗的语义修对了。
- 首页四段（§6.2）**当时仍然没有任何自动守卫**：顺序全靠人眼看。这格没顺手加，
  因为它需要挂整页 `HomeScreen`（要造 4 个 StateFlow + 动 `FloatingService.instance` 这个静态），
  是一件独立事，别混在本格里做半套。**→ 下一格（§25，`11e121f`）做的正是它：守卫装在了改名之前。**
- `LbRowState` 只有两档，是照今天真实存在的两种说法建的；
  出现"第三种就绪状态"时该新增还是换表，届时要判，不要顺手塞一个 `Pending`。
- 上一格提的"字面量那把尺看不见自定义组件参数位"仍没动（`HomeSettingRow(title = "模型供应商",
  trailingText = "管理")` 这类，约 70 条粗测）。

---

# 追加十五：§6.2 首页四段的第一条自动守卫（改名字之前先有网）

## 25.1 指导书那段话，与"今天由谁在看着"

§6.2 原文是把首页**固定为四段**：

1. 顶部：LoveBrain + 一句价值说明；右侧 About。
2. 军师状态主卡：状态 badge、简短说明、**唯一主按钮**；隐藏图标**只在可隐藏时**出现于**右上角**。
3. 快捷功能：知识库、反馈案例使用相同 `LbActionCard`；未来新增功能仍走同组件。
4. 设置与使用概览：模型供应商、消息捕获放 `LbSettingRow`；统计放**三等分** `LbMetricGrid`。

外加一句负向的：「不要把 Provider 编辑器、反馈案例列表、捕获 App 清单展开在首页；点击统一进入独立 screen」。

这五件事**此前一句都没有守卫**：`HomeNavigationTest` 只管目的地枚举与保存/恢复，
`ProductionUiContractTest` 只管几颗按钮的尺寸。也就是说四段顺序被人挪了、
主卡里多塞一颗"次主按钮"、统计从三等分变成两等分——**没有一条用例会红**。
这一格补的就是这条。

## 25.2 判据的取法：tag 定位 + 坐标判位置

- **锚点用 tag，不用中文**：`LbHomeTags`（SECTION / ABOUT / STATUS_CARD / PRIMARY_BUTTON /
  HIDE_BUTTON / ACTION_CARD / SETTING_ROW / METRIC_CELL）。理由是本仓库写过的"文字会变，tag 不会"——
  拿中文当锚点，改一句文案就把**结构**守卫弄红，而结构其实没动。
  这些锚点同时是将来截图基线（§6.5）要用的定位点，不是只为测试临时造的。
- **"右上角"与"三等分"只能用坐标判**：隐藏那颗的 `left` 要落在卡片右边界 80dp 之内、
  `top` 要在卡片顶部 60dp 之内；三格 metric 的宽度极差要 < 1.5dp。
  这两句在指导书里是形容词，在这里必须是数——不然"三等分"三个字可以靠 `SpaceEvenly` + 随便什么权重糊过去。
- **负向那句用角色判**：首页上 `Role.Checkbox` 节点数必须为 0（那是捕获清单的标志），
  带 `SetTextAction` 的节点数必须为 0（那是 Provider 编辑器/反馈列表展开时的标志）。
  配一条"整棵树节点数 > 20"的反空跑断言——**没这条，两个 0 可能只是挂载失败**。

## 25.3 两个"看不见的前提"变成声明的参数

四段的判据里藏着两个输入：`Settings.canDrawOverlays(context)` 和进程内单例
`FloatingService.instance != null`。要证明第 2 段那句"**只在**可隐藏时"，就必须能把
权限 × 服务 × 窗口状态摆出来——挂在私有实现上就摆不出来。

于是 `HomeScreen` 多两个**默认值就是原行为**的参数：

```kotlin
overlayGrantedOverride: Boolean? = null,   // 默认仍读 Settings.canDrawOverlays(context)
serviceRunningOverride: Boolean? = null    // 默认仍读 FloatingService.instance != null
```

生产调用方一字不改。这不算"为测试改结构"：这两个值本来就是四段结构的**前提**，
只是过去藏在函数体里，谁都不知道少了它们也能画出错页面。

## 25.4 这格踩到的两把语义树细节

- **同一个 LayoutNode 上会有两个带同一 tag 的节点**：`Modifier.clickable(…)`（合并语义）
  与 `Modifier.testTag(…)`（不合并）各自成为一个语义节点，未合并树里两个都带那个 tag。
  第一版 `tagCount` 因此数出 2 颗 About。改成**按 layoutNode 去重**，
  并把 `top()` / `topLevel()` 从"取第一个"改成"取最外那个"——顺序依赖父链，是个隐藏地雷。
- **分区个数按不同 top 坐标数**，不按节点个数：同上原因；"有几个分区标题"本来就是位置事实。

## 25.5 五格用例逐条对到 §6.2

| 用例 | 钉的是哪一句 |
|---|---|
| `the four segments sit in the order the guide fixes` | ①顶部在最上、②主卡在其下、③④两个分区标题与统计的上下次序；3 个分区 |
| `the status card holds exactly one primary button and a conditional hide entry` | ②「唯一主按钮」+「隐藏图标只在可隐藏时出现于右上角」——四个组合（权限×服务×窗口）逐一摆，含"已隐藏时不该再有隐藏入口" |
| `quick actions are two of the same card and both are actionable` | ③「知识库、反馈案例使用相同组件」：同 tag 2 张、都整卡可点 |
| `settings hold two rows and the metrics split the row into three equal cells` | ④两行设置 + 统计**三等分**（宽度极差 <1.5dp） |
| `nothing that belongs to a sub-screen is expanded on home` | 负向那句：无 Checkbox、无编辑框，并有反空跑断言 |

## 25.6 变异：五发改生产代码，每发只红该红的那格

| 变异（改的是生产代码） | 红了谁 |
|---|---|
| H1 三格 metric 中 one 的 `weight(1f)` → `2f` | `settings hold two rows and the metrics split…` |
| H2 状态卡里再塞一颗同 tag 的主按钮 | `the status card holds exactly one primary button…` |
| H3 首页放一个 `Checkbox` | `nothing that belongs to a sub-screen is expanded on home` |
| H4 `canHide` 放宽成 `isServiceRunning`（已隐藏时也画隐藏入口） | `the status card holds exactly one primary button…` |
| H5 隐藏图标 `TopEnd` → `TopStart` | `the status card holds exactly one primary button…` |

**H2 / H4 / H5 都落在第 ② 条用例上，所以三发必须分开跑**——一起跑就只能知道"②红了"，
不知道是哪一种坏法。分开发跑后各自 `tests completed, 1 failed`，可归因。
每发变异都由脚本断言"目标片段恰好命中一次"才落刀，跑完立刻回滚并复跑全量。

## 25.7 实测

| 量 | 结果 |
|---|---|
| 全量单测 | `GRADLE_RC=0`：**1252 tests / 160 套件 / 0 失败 / 0 错误 / 0 跳过**（起点 12:30:55，跑在**变异全撤之后**的最终树上） |
| 与上一格对账 | 1247 → 1252 = **+5**，159 → 160 套 = **+1** ⇒ 两处增量互相咬得上 |
| lint | 报告重生成（12:37:17）实测 **69 / 15**、进预算 **68 / 14**、advisory 1，rc=0（连续四格同一组数） |
| 其它闸 | 跨层 **6** 条；工单编号 rc=0；prompt 资产 lock rc=0；判据自测 27 格 rc=0；`:app:assembleAndroidTest` rc=0 |
| 结构 | 首页四段第一次有自动守卫 ⇒ 下一格做 §6.1 的改名/搬包时才有回归网（这正是把它排在守卫之后的原因） |

## 25.8 这格没做的

- **§6.1 表里的改名与搬包仍没动**：`HomeTopBar` / `HomeSectionHeader` / `HomeActionCard` /
  `HomeSettingRow` / `UsageSummary`+`UsageMetric` 还在 `ui/home`，名字不按表。
  守卫已经就位，这一格是故意排在守卫之后。
  **→ 已还：见 §26（提交 `054c6e8`）。** 名字与包都按表了，
  §26.3 还顺手量出这次搬家把字面量预算照出了一个瞎点。
- §6.2 那句「**未来**新增功能仍走同组件」还是没有闸：现在能证明"今天正好两张卡"，
  不能证明"第三张必须用同一颗"。要闸得住"新增"，得等名字按表齐了再扫 `Lb*` 调用数——
  或者扫"首页里 tag=ACTION_CARD 之外的可点卡片"。这条我没做，别以为守卫管住了它。
- `HomeTopBar` 里 `contentDescription = "关于"` 与 `"暂时隐藏浮窗"` **仍是硬编码中文**
  （在 `DESC = 12` 那笔预算里），英文环境下读屏会念中文；本轮只加锚点没动文案。
- 截图基线（§6.5）仍未接；这格把定位点备齐了而已。
- `Settings.canDrawOverlays` 与 `FloatingService.instance` 这两个前提在生产上仍是从环境/单例读，
  我只加了 override，没有把它们提到 VM 里——"UI 不读进程单例"这件事归第二/三步的账。


---

# 追加十六：§6.1 五颗组件归 core 并改名，搬家照出两把尺的瞎点（提交 `054c6e8`）

提交 `054c6e8`。这一格是 §25.8 第一条欠的那笔：名字按表、包也按表。

## 26.1 指导书那两栏逐字对到

指导书 :474 起（§6.1）：

> 只保留以下基础组件：… | `LbTopBar` | 标题、副标题、返回/关于等单一尾部动作 | …
> | `LbSection` | 标题 + 可选说明 + 内容，不允许每页另造标题样式 | …
> | `LbActionCard` | 快捷功能入口；图标、标题、副标题、箭头统一 |
> | `LbSettingRow` | 设置项；标题、说明、状态、尾部动作统一 |
> | `LbMetricCard/Grid` | 使用统计；数值、单位、标签统一 |
> 禁止创建"只在一个页面看起来不一样"的按钮/卡片…

指导书 :494-499（§6.2）：

> 3. **快捷功能**：知识库、反馈案例使用相同 `LbActionCard`；未来新增功能仍走同组件。
> 4. **设置与使用概览**：模型供应商、消息捕获放 `LbSettingRow`；统计放三等分 `LbMetricGrid`。

两段用的都是**组件名**，而仓库里当时叫 `HomeActionCard` / `HomeSettingRow` / `UsageSummary`，
且住在 `ui/home/HomeComponents.kt`——名字和归属两样都不对。这一格把两样一起改过来。

## 26.2 搬了什么，留在什么

| 表里的名字 | 搬之前（ui/home/HomeComponents.kt） | 现在 |
|---|---|---|
| `LbTopBar` | `HomeTopBar`（把"LoveBrain"+副标题+关于图标烤进组件里） | `core/designsystem/LbTopBar.kt`（54 行，签名 `title/subtitle/trailing`） |
| `LbSection` | `HomeSectionHeader` | `core/designsystem/LbSection.kt`（22 行） |
| `LbActionCard` | `HomeActionCard` | `core/designsystem/LbActionCard.kt`（96 行） |
| `LbSettingRow` | `HomeSettingRow` | `core/designsystem/LbSettingRow.kt`（158 行） |
| `LbMetricCard/Grid` | `UsageSummary` + `UsageMetric` | `core/designsystem/LbMetricGrid.kt`（84 行） |

`HomeComponents.kt` 545 → **249 行**，剩下的四样都是"首页自己的东西"，搬走就是撒谎：
`HomeDestination`（导航密封类）、`LbHomeTags`（页面锚点）、`HomeAboutEntry`（关于入口，
带 `contentDescription = "关于"` 这句首页文案）、`AssistantStatusCard`（军师状态主卡）。

`LbTopBar` 第一次搬的时候我把"LoveBrain""帮你更自然地表达"和那颗关于图标一起挪进了
`core/designsystem` —— 那是**设计系统认识了一个具体页面**。所以尾部改成 `trailing` 槽，
具体内容和那句文案回到调用点（`HomeScreen` 里 `trailing = { HomeAboutEntry(onNavigateAbout) }`）。

`rememberPressScale` 原先在 `ui/panel/DragHandle.kt` 里，五颗组件要用它就得让 core 去
import ui ⇒ 单独抽成 `core/designsystem/PressScale.kt`（27 行），22 个文件的 import 改写。
抽取时第一版把 `DragHandle` 和 `TriangleArrow` 一起删掉了——`git checkout --` 复原后按行精确切。

tag 归属跟着搬：组件自持的 4 颗（SECTION / ACTION_CARD / SETTING_ROW / METRIC_CELL）
进 `core/designsystem/LbTags.kt`，值从 `lb_home_*` 改成 `lb_*`（它们不再属于首页）；
页面锚点（ABOUT / STATUS_CARD / PRIMARY_BUTTON / HIDE_BUTTON）留在 `ui/home` 的 `LbHomeTags`。
`LbSettingRowStateTest` 也跟着组件搬进 `core/designsystem` 测试包（git 认出 rename，96% 相似）。

## 26.3 这格真正的收获：搬家把一把尺照出了瞎点

搬完之后 `UiStringLiteralBudgetTest` 报：

```
Text 可见文案: 实测 246 < 预算 247（还掉了就来把数字改小）
```

按棘轮的语义，改小数字了事是**合规**的。但"还掉了"这三个字要能作证：我没有搬走任何一条
硬编码中文，一条都没有。于是用 `_temp/measure_text_delta.py` 把"搬家前后各看见哪些字面量"
做成了**逐条差集**（不是比总数），结果是：

```
搬家前 TEXT 可见 = 2，搬家后 = 1
看不见的（前 > 后）:  -1  "帮你更自然地表达"
```

同一条字符串还在原地，只是从 `Text("帮你更自然地表达")` 变成了
`LbTopBar(subtitle = "帮你更自然地表达")`，而那把尺的锚点是 `Text(`。
**计数掉了不等于债还了**——这是 §15 那条"还债后计数没动 = 尺在漏"的反向版本。

修法不是改窄回去，是补一栏：`Kind.COMPONENT`，锚点 `\bLb\w+\s*\(`，
口径写成"整段实参里的中文字面量 **减去已被前三栏区间覆盖的那些**"（按字符区间判，
所以 `LbCard { Text("中文") }` 只记一次，不会两栏重复入账）。实扫 = **16**，起点就登记 16。
账对上：246（TEXT）+ 那 1 条（现在在 COMPONENT 里）= 旧的 247，总数一笔没少。

盲区还剩多少，也量了：`ui/` + `core/designsystem/` 里前三栏都看不见的内联中文 = **364 条**，
但这个数不能当预算用——它里面大头是注释与日志（`LbRowState.kt` 7、`ScreenState.kt` 4 这类），
把它登记成"用户可见文案"是假账。所以那 364 里的真文案（页面自造子组件的具名实参，
如 `StatCell(label = "…")`）**仍然欠着**，写进 §4 待办，别当已覆盖。

`the counters count what they claim and nothing else` 那格里补了三条夹具反例：
`LbTopBar(title = "页面标题", trailing = { Text("里面" + "那颗字") })` ⇒
COMPONENT 必须 1（具名实参那条要看见）、TEXT 必须 6（套在里面的两处不许重复记）；
再加 `OtherCell(label = "别人的组件不算")` ⇒ COMPONENT 仍是 1，
这一格明写"非 `Lb` 前缀不数"，免得下一个人以为这栏覆盖全部组件。

## 26.4 新尺 `design system components live in core and … stay retired`

放在 `UiLayerDependencyContractTest`（它就是管"东西该在哪一层"的）。三条判据都全仓扫：

1. 六个旧名字（含 `typealias`）声明数 = 0；
2. 六个新名字全仓声明数恰 = 1；
3. 那唯一一处落在 `core/designsystem/` 子树里。

②③ 必须分开写。只写"core 里恰好一颗"的话，把整颗组件搬回 `ui/home` 两个方向都不报
（core 数到 0 颗不满足"恰一颗"？——第一版按**文件**去重数，搬走之后 core 里 0 颗、
`assertTrue(size == 1)` 才红，但同一文件里复制一颗永远数不到）；只写"全仓一颗"的话，
搬出 core 又抓不到。所以一条测数量、一条测位置。

## 26.5 变异：七发，每发只咬该咬的那格

P1-P4 打 §26.4 的新尺，P5 打 COMPONENT 栏，H1-H5（§25.6 那五发）**搬家后重跑一遍**，
确认 §6.2 的守卫没被搬家弄成摆设。

| 探针 | 改了什么 | 结果（消息点名） |
|---|---|---|
| P1 | `ui/home` 里重新声明 `@Composable fun HomeActionCard(…)` | 红：`不许重新声明：[ui/home/HomeComponents.kt]` |
| P2 | `ui/home` 里 `typealias HomeTopBar = String` | 红（同一句，另一条正则分支） |
| P3 | `core/designsystem/LbSettingRow.kt` 里再加一颗 `fun LbSettingRow(probeOnly: Int)` | 红：`应当全仓只声明一次，实到 2` |
| P4 | 把 `LbMetricGrid.kt` 整颗搬回 `ui/home`（包名不动，编译照过） | 红：`唯一声明应当在 core/designsystem 子树里，实到：[ui/home/LbMetricGrid.kt]` |
| P5 | `LbTopBar(title = "LoveBrain" + "新增中文", …)` | 红：`组件实参里的可见文案: 实测 17 > 预算 16` |
| H1 | 三等分其中一格 `weight(2f)` | 红：`三等分不是修辞：实测宽度 [70.0, 140.0, 70.0]，差 70.0dp` |
| H2 | 主卡里再放一颗 `PRIMARY_BUTTON` | 红：`状态卡里的主按钮必须唯一 expected:<1> but was:<2>` |
| H3 | 首页插入一颗 `Checkbox` | 红：`首页不该出现捕获清单的勾选框 expected:<0> but was:<1>` |
| H4 | `canHide` 放宽成只看服务在跑 | 红：`服务在跑但窗口是 STOPPED：不该给隐藏入口` |
| H5 | 隐藏图标 `TopEnd` → `TopStart` | 红：`隐藏图标必须在卡片右上角：hide=(32.0,96.0) card=(24.0,88.0,336.0)` |

**P1-P4 第一版全绿的其中两发是假的**：Gradle 不知道这条门禁在读磁盘源码，
`:app:testDebugUnitTest UP-TO-DATE` 直接跳过测试任务、退出码 0。
从此变异一律 `--rerun`，跑前 `touch marker`、跑完只认比 marker 新的 XML，
日志里出现 `testDebugUnitTest UP-TO-DATE` 这一格作废重跑。
另外 P2 那发还顺手证明了我自己写的正则分支是死的：`typealias` 后面跟 `=` 不跟 `(`，
原来的字符类 `[<(]` 永远数不到 typealias。P3 那发证明"按文件去重"是错的口径。
也就是说：**这三发探针的价值不在"新尺有牙"，在"把还没牙的三处照出来"**——牙是补完之后重新长的。

复验：所有探针 revert 后与 `_temp/mut70-backup/` 逐字节比对 IDENTICAL，
`grep 新增中文|probeOnly|SECONDARY|TopStart` 在生产源码里 0 命中。

## 26.6 实测（数字全部来自当次命令输出，退出码单独取）

| 项 | 结果 |
|---|---|
| `:app:compileDebugKotlin` / `:app:compileDebugUnitTestKotlin` | RC=0 / RC=0 |
| `:app:testDebugUnitTest` 全量 | 160 套件 / **1253 例** / 0 红 / 0 跳过（上一格 1252，这格 +1 = 新尺那一格） |
| `HomeScreenStructureTest` + `core/designsystem.*` + `ui.home.*` | 11 套件 / 57 例 / 0 红 |
| `:app:lintDebug` + `check_lint_budget.sh` | RC=0 / RC=0：`measured_issues=69 measured_rules=15 gated_issues=68 gated_rules=14 advisory_issues=1 advisory_rules=1 budget_rules=14`（与搬家前同一组数，条数没动） |

⚠ 提交信息 `054c6e8` 里写的"537 → 249"**是错的**：`git show HEAD~1:…HomeComponents.kt | wc -l`
实到 **545**。537 是我上一格给设置行拆 `dot`/`statusText` **之前**记的数，我凭记忆抄进了提交信息。
行数口径以这里为准（545 → 249）；提交信息不改写（`--amend` 会动已存在的历史），在此点名留档。
| `test_check_lint_budget.sh` | RC=0 |
| `strip_ticket_ids.py --check` | RC=0 |
| `asset_hashes.sh --check docs/prompt-assets.lock` | RC=0 |
| `package_deps_report.sh --count` | RC=0，**6 笔**，与搬家前同一批 6 个文件（不增不减，所以"计数没动"这次不是漏） |
| `:app:compileDebugAndroidTestKotlin` | RC=0 |

## 26.7 这格没做的

- §6.1 表 11 行仍欠三行有主的组件：`LbScreenScaffold`（背景/安全区/统一水平边距）、
  `LbPrimaryButton`（Idle/Loading/Disabled/Stop 四态）、`LbModalSheet/Dialog`。
  现在表里 8 行有主，别报成"§6.1 做完了"。
  **→ 其中 `LbPrimaryButton` 已还：见 §27（提交 `d8f36d2`），表里还剩两行。**
- §6.2 那句「**未来**新增功能仍走同组件」仍然没闸（§25.8 第二条照旧成立）。
  名字按表之后可以扫 `Lb*` 调用数了，但这一格没做。
- COMPONENT 那栏只认 `Lb` 前缀；页面自造子组件的具名实参仍在盲区（§26.3 末尾量到 364 条
  里含真文案，但没法和注释/日志分桶，所以没登记）。
- `HomeAboutEntry` 里 `contentDescription = "关于"` 与 `AssistantStatusCard` 里
  `"暂时隐藏浮窗"` 仍是硬编码中文（在 DESC = 12 那笔里），英文环境读屏念中文，这格没动。
- 五颗组件的**样式**没检查是否仍与 §6.5 基线一致（字号、圆角、间距那三张表）；
  这格只搬不改，行为等价性由 §25 那套结构守卫 + `UiBaselineRegressionTest` 撑着，
  截图基线仍未接。

---

# 追加十七：§6.1 表里 B 类第一行——`LbPrimaryButton` 四态（提交 `d8f36d2`）

## 27.1 指导书那一行，与仓库里当时的形状

指导书 :481：

> | `LbPrimaryButton` | 页面唯一主动作，Idle/Loading/Disabled/Stop 四态 |

仓库里对应的是 `ui/panel/reply/GenerationActionButton.kt`（196 行，1 个调用方 `ReplyPrimaryActions`）。
它**已经有四态**，但那四态是**两个平行旋钮拼出来的**：

```kotlin
enum class ButtonMode { NORMAL, LOADING, STOP }            // 少一档"禁用"
fun GenerationActionButton(
    text: String, onClick: () -> Unit, ...,
    enabled: Boolean = true,          // ← "禁用"在这里
    mode: ButtonMode = ButtonMode.NORMAL,   // ← 其余三态在这里
    containerColor: Color = Primary, textColor: Color = Color.White,
    heightDp: Int = MIN_TOUCH_TARGET_DP
)
```

两个旋钮拼四态的代价是三样东西没人定义过：`STOP + enabled=false` 在类型上完全合法；
`Disabled` 其实是 `NORMAL` 里的一个 `if`，所以"禁用"和"主动作"这层语义只存在于实现细节里；
而 `text` 在 LOADING 分支被传成 `""`（那一支根本不读它），是个假参数。

## 27.2 收口后的形状

| 表里要求的 | 现在 |
|---|---|
| 名字与归属 | `core/designsystem/LbPrimaryButton.kt`（旧名进棘轮黑名单） |
| 四态一旋钮 | `enum class LbButtonState { Idle, Loading, Disabled, Stop }`，`mode`+`enabled` 双双退役 |
| 页面唯一主动作 | 组件自己钉 48dp（`LB_PRIMARY_MIN_HEIGHT_DP`），调用方拿不到高度旋钮 |
| 着色调性 | `LbButtonTone { Primary, Deep }` 两档小表，不再由调用方交 `containerColor` |
| 停止锚点 | `LbTags.PRIMARY_STOP`，值仍是 `generation_stop_action`（换归属不换值） |

**刻意没搬进设计系统的两件事**：

1. "分析对话 · 7s 点击停止"这串字与 5s/15s 阶段规则 ⇒ 留在 reply 层新建的 `GeneratingLabel.kt`。
   上一格 `LbTopBar` 犯的错（设计系统认识了一个具体页面）不能犯第二次。
   计时从按钮里搬到调用方时语义保持：那个 composable 只在"生成中"分支被调用，
   退出组合即失去 `remember`，下一次生成从 0 秒重数——与原先挂在按钮 `LaunchedEffect(Unit)` 上等价。
2. 阶段规则顺手抽成纯函数 `generatingPhaseResFor(seconds)`。它原先焊在 composable 里 ⇒
   **本机一格都量不到**，5s/15s 两个边界只能等设备上有人盯秒数。

**删掉的三样死东西**：`heightDp` 参数（实现里写 `maxOf(heightDp, 48)`，只能改高不能改矮 =
不存在的自由度）、LOADING 那个被传成 `""` 的 `text`、`textColor` 参数（0 个调用方传过）。
退役件不在 git 里，落 `_temp/GenerationActionButton.kt.retired-2026-09-25`；
git 侧 `git show HEAD~1:app/src/main/java/com/lovebrain/app/ui/panel/reply/GenerationActionButton.kt` 取得回。

## 27.3 新守卫：五格语义树 + 两格纯函数

`LbPrimaryButtonStateTest`（一次 `setContent` 挂四态、换态靠 hoisted 状态——本仓库仪器要求每测只挂一次）：

| 格 | 判据 | 实到 |
|---|---|---|
| 四态同盒 | 四态 `boundsInRoot` 逐位相同 | `0/0/360/48` ×4 |
| 四态过下限 | 每态 `assertAllActionableMeetTouchFloor` + 高度恰为下限 | 48dp |
| 禁用不消失 | Disabled 仍画得出来、带 `SemanticsProperties.Disabled`；Idle 不带 | 通过 |
| 点得动/点不动 | Idle·Stop·Loading 各恰好回调一次；Disabled 点下去回调次数不变 | 3 次 |
| 锚点归属 | `LbTags.PRIMARY_STOP` 只在 Loading 出现（其余三态 0 个） | 通过 |

`GeneratingPhaseTest` 穷举 `0/1/4/5/9/14/15/60/3600` 九个点 + 一条"三档都真能走到"的反空跑。

**这一格最重要的证据不是新用例，是老用例一字未改仍然绿**：
`ReplyPrimaryActionsContractTest` 那 7 格（§2.1 四行合同：无结果全宽带计数、N=0 灰着不消失、
有结果是"重试|记入知识库"两颗、PROACTIVE 全宽、任一生成中唯一停止入口）
读的是语义树标签、宽度与 disabled 位。换掉整颗按钮实现后它 7 格全绿 ⇒ 用户可见行为没变。

## 27.4 写测试时自己踩的两个假前提（都是先红才知道的）

1. **"四态占同一个盒子"一开始没红在实现上，红在我的测试上**：第一版没给 `fillMaxWidth()`，
   量到 `Idle=63 / Loading=120 / Disabled=102 / Stop=71` dp。这颗按钮**本来就不铺满**，它按内容宽。
   ⇒ §6.4 那句"模式切换不移动主操作按钮"是**调用方 + 组件**的联合性质：槽位宽度得由调用方钉死。
   生产五个分支都带 `fillMaxWidth()` 或 `weight(1f)`，测试因此改成按同一种形状挂，
   并把上面这四个实测数写进 KDoc 当证据——不然下一个人又会以为宽度是组件负责的。
2. **`assertEquals(4, Regex("\\.paddingVerticalInside\\(").count())` 数到 5**：
   定义那一行 `private fun Modifier.paddingVerticalInside()` 里 `Modifier.` 后面那个点也算命中。
   改成锚在"换行 + 缩进 + 点"上。这就是本仓库第 4 号坑（正则漏写法）在同一格里的第二次现身。

## 27.5 变异：九发，各咬各的

| 探针 | 改了什么 | 红了谁 |
|---|---|---|
| M1 | Loading 分支绕开共用的 `base`、自己写 `.height(56.dp)` | 同盒格红（`[360x48, 360x56, 360x48, 360x48]`）；**下限格照绿**（56≥48）⇒ 两把尺判的不是同一件事 |
| M2 | `LB_PRIMARY_MIN_HEIGHT_DP` 48→40 | 新守卫红 + §2.1 合同三格红 + 源码闸红（三处独立，同一根因） |
| M3 | Disabled 分支去掉 `enabled = false` | disabled 语义格、回调次数格、合同"N=0 必须带 disabled"三处红 |
| M5 | Loading 的锚点换成别的 tag | "只有 Loading 有停止锚点"红（`expected:<1> but was:<0>`）+ 资源闸红 |
| M5B | 把停止锚点加到共用标签函数上 | "Idle 的停止锚点数 `expected:<0> but was:<1>`" 红 |
| M6 | `seconds < 5` 改成 `<= 5` | 边界格红（第 5s 报了上一档）；可达性别报假红 |
| M7 | `ui/home` 里复活 `enum class ButtonMode {…}` | 归属棘轮红 ⇒ 为此把尺的字符类补上 `class/interface/{`（只认 `fun`+`(` 时状态表整个逃出尺外） |
| M8 | 删掉 Stop 那一行的 `.paddingVerticalInside()` | 链数 `expected:<4> but was:<3>` 红 |
| M9 | `heightDp: Int` 参数回来 | "heightDp 这个死参数不许回来" 红 |

**事故（当场报，不攒到最后）**：M5 第一版的"变异"是**把那行删掉**（替换文本 = 空串）。
revert 时驱动去数 `t.count("")`，得到 7379（= 长度 + 1），"命中数必须为 1"的哨兵当场报错，
**文件被留在变异态**。还原靠的是 apply 之前先落盘的 `_temp/mut72-backup/`——
"记账与回滚件必须在副作用之前"这条这次是真的救了这一格。
修法是驱动里禁止空串替换（启动期断言），M5 改成"换成别的 tag"：效果等价、且可逆。

## 27.6 实测（数字全来自当次命令输出，退出码单独取）

| 项 | 结果 |
|---|---|
| `:app:compileDebugKotlin` / `UnitTestKotlin` / `compileDebugAndroidTestKotlin` | RC=0 / RC=0 / RC=0 |
| `:app:testDebugUnitTest` 全量 | **162 套件 / 1260 例 / 0 红 / 0 跳过**（上一格 160/1253；+2 套 +7 例 = 本格两个新文件） |
| `:app:lintDebug` 重生成后 | RC=0，`measured_issues=68 measured_rules=15 gated_issues=67 gated_rules=14 advisory 1` |
| `check_lint_budget.sh` | 先 RC=1：`STALE AutoboxingStateCreation: 实测 5 < 预算 6` → 逐条核过当次报告剩余 5 条分别在 `KnowledgeBaseActivity:525`、`OnboardingFlow:52`、`ProviderSection:340`、`ResultArea:646/765`，**没有一条在退役文件里**；退役文件里那行确实是 `mutableStateOf(0)`，新代码写的是 `mutableIntStateOf` ⇒ 确实是还掉一条，`--rewrite` 后 RC=0，diff 只动 `lint-budget.txt` 一行 |
| `test_check_lint_budget.sh` / `strip_ticket_ids --check` / `asset_hashes --check` | RC=0 / RC=0 / RC=0 |
| `package_deps_report.sh --count` | RC=0，仍是 6 笔同一批文件 |
| 探针撤回后 | 三个被改文件与 `_temp/mut72-backup/` 逐字节 IDENTICAL |

## 27.7 这格没做的

- §6.1 表里 B 类还剩两行（**→ `LbDialog` 半边已还：见 §28，提交 `38520b0`**）：**`LbModalSheet`/`LbDialog`**（各页各用 `AlertDialog`，浮层语法没收口）与
  **`LbScreenScaffold`** 的安全区/统一水平边距部分。别把这一格报成"B 类做完了"。
- **主操作以外的重复按钮实现没动**：`ui/` 下"Primary 底色 + clickable"这种自造实现实扫 **17 处 / 11 个文件**
  （`KnowledgeBaseActivity:451`、`LoveBrainPanelScreen:213/923`、`OnboardingFlow:304`、`SuggestPanel:213/257`、
  `CounselingPanel:205/367`、`CorrectionCenter:188`、`DislikeReasonPanel:111/125/253`、`RecordSentDialog:136`、
  `ResultArea:316/397/1271`、`SchemeCard:515`）。这 17 处里有多少是"页面主动作"、多少是 chip/切换/次级动作，
  **要一处一处判语义**才能定，不能按数量收口——本格只收了 `ReplyPrimaryActions` 那一处真主动作。
- §6.4 那句「模式切换只改变内容区，不移动主要**输入**和主操作按钮」里，输入那一半仍没守卫
  （本格只钉了按钮这半边）。
- 设备侧那三条依赖 `PRIMARY_STOP` 与那句阶段文案的 androidTest（`OverlayGenerateSmokeTest`、
  `ReplyPrimaryActionsTest`）**本机跑不了**：我只改了它们的 import 与常量指向，实扫证明
  tag 的值一字未改、渲染节点仍是那颗标签，但"设备上仍然点得到"这句话要等 CI。
- 截图基线（§6.5）照旧故意没接；这格改了按钮的着色来源与文本样式收敛，接基线时会看到
  Stop 态标签多了 `maxLines=1/ellipsis`（四态共用一个标签函数），那是刻意的收敛。

---

# 追加十八：§6.1 浮层收口成 `LbDialog`，并量出 11 颗对话框按钮的 40dp 缺陷（提交 `38520b0`）

## 28.1 指导书那一行与仓库当时的形状

指导书 :487：

> | `LbModalSheet/Dialog` | 需要用户决策的浮层；不把展开内容直接插在原页面下方 |

实扫（`grep -c`，剥注释后）生产里直接 call Material 浮层的点：**`AlertDialog(` 11 处 / 6 个文件**
+ 裸 `Dialog(` 1 处（`ProviderSection` 的供应商编辑器）。11 处的写法互不相同：

| 位置 | 标题样式 | 正文 | 动作着色 |
|---|---|---|---|
| `KbEditActivity:496` 清空 | `titleLarge` | `bodyMedium` | 清空 Error / 取消 TextSecondary |
| `KnowledgeBaseActivity:229/255/354/377` | `titleLarge` | `bodyMedium` | Primary / Error / TextSecondary 混用 |
| `KnowledgeBaseActivity:486` 改名 | `titleLarge` | **`OutlinedTextField`** | 保存带 `enabled` |
| `FeedbackCasesScreen:360` 导出预览 | **`titleMedium` + SemiBold + 手写 color** | **`labelSmall` + 滚动 Column** | **4 颗**（保存 / 分享 / 复制 / 关闭），复制那颗还按状态换字重 |
| `FeedbackCasesScreen:433/449` 失败 | `titleMedium` | **Error 色正文** | 关闭 Primary |
| `AccessibilityDisclosureDialog` | `titleLarge` | Column 五行 + 分隔线 | 同意并继续 / 取消 |
| `ProviderSection:303` 删除工单 | `titleLarge` | `bodyMedium` | 删除 Error / 取消 |

## 28.2 先量后写：那一颗 40dp 的"确定"

新建 `DialogProbeTest`（它不是守卫，是**探尺**：绿只证明仪器看得见对话框里的节点）量到：

```
PROBE 对话框按钮实测：「PROBE_CONFIRM_LABEL」 role=Button selected=null state=null 尺寸 188x40dp @(68,120)
```

⇒ §6.5 :531 要求"所有可点击节点 ≥48×48dp"，而 11 个浮层里的每一颗"确定/取消/删除"都是 **40dp 高**。
之前那把 48dp 的尺（`SemanticsProbe.assertAllActionableMeetTouchFloor`）从没抓到过这件事，
原因是它只被用在页面挂载上——**对话框是另一扇窗，没人把尺伸进去过**。
`LbDialog` 的动作统一垫 `heightIn(min = 48dp)`，`LbDialogTest` 逐颗读 `boundsInRoot` 钉住；
N1 探针（改成 `min = 8.dp`）当场报出 `3/3 个可交互节点小于 48dp`、
禁用那颗 `expected:<48.0> but was:<34.0>`，说明这把尺这次是有牙的。

## 28.3 形状上的四个决定（每个都有理由，别当成风格偏好）

1. **颜色来自词表**：`LbDialogActionTone{Accent, Destructive, Muted}` + `LbDialogMessageTone{Plain, Error}`。
   调用方不再交 `containerColor`/`color`，与 `LbSettingRow`、`LbPrimaryButton` 同一口径。
2. **`body` 槽**：正文不是一句话的三处（隐私披露的五行长文、改名那颗的输入框、导出预览的滚动区）
   走 `body`，其余走 `message`。`body` 有值时不再画 `message`——夹具那格要求空白正文节点数为 0。
3. **`secondary` 上限 3 并抛**：`require(secondary.size <= 3)`。一个主动作 + 最多三个次级；
   再多说明这不是对话框该装的东西（该走 Sheet 或独立 screen）。N3 探针把上限放宽到 9，
   "拒绝第四颗"那格立刻红 ⇒ 这条不是注释而是行为。
4. **`enabled` 留着，但与上一格删掉的 `mode`+`enabled` 不是一回事**：
   主动作那两个旋钮编码的是**同一根轴**（我处在哪个状态）⇒ 并成一棵 `LbButtonState`；
   对话框动作的 `enabled` 是**另一根轴**（表单此刻填没填满，比如"显示名不许为空"）⇒ 保留。
   这个区别写在 KDoc 里，免得下一个人顺手把它也"收"掉。

ProviderSection 那颗自造 `Dialog(`（供应商编辑器）**没**并进来：它是十几字段的完整表单，
属于表里 Sheet 那一类，而 Sheet 半边这格没做。它在闸里被**点名豁免**并写明原因——是欠账，不是漏网。

## 28.4 新闸：`floating decision surfaces have exactly one owner`

`AlertDialog(` 全仓只许出现在 `core/designsystem/LbDialog.kt` 且恰 1 处；
裸 `Dialog(` 只许出现在点名豁免表里、且**每处的计数要逐一对上**。
第二条的后半是故意的：豁免登记 1 处，如果哪天那处被删了，`got != want` 也会红——
**不成立的豁免比没有豁免更危险**（它会让人以为这件事已被管住）。

顺带确认了 `\b` 在这把尺上的作用：`ProviderEditDialog(`、`LbDialog(` 都以 `Dialog(` 结尾，
但 `\bDialog` 要的是词边界，所以它们不被算成"裸 `Dialog(`"。第一版没写 `\b` 的话豁免计数会是 3。

## 28.5 字面量账本：两栏一起动，逐条核过没逃

| 栏 | 上一格 | 这一格 | 差 |
|---|---|---|---|
| TEXT | 246 | 209 | −37 |
| COMPONENT | 16 | 59 | +43 |
| 合计 | 262 | 268 | **+6** |

- **37 处换了形状**：`Text("取消")` → `LbDialogAction(label = "取消")`，从 TEXT 进 COMPONENT，一条没还。
- **+6 是以前两栏都看不见的既有债**：写在 `TextButton(onClick = { … })` 里的提示语
  （"清空失败，请重试"、"文件已被后台修改，请重新打开"、"没有可用的分享应用"、"分享到"…），
  搬进 `LbDialogAction(onClick = { … })` 之后才落进实参切片 ⇒ 涨的是量具新看见的，不是这次新塞的
  （与 209 → 254 那次换尺同一回事，写在预算旁边）。
- 另外核到两条"看起来消失"的串是**转义写法变了**：
  `"\u201c消息捕获\u201d"` 与 `“消息捕获”` 逐码点相同（60/60、56/56 字符），不是删了文案。
- **这把尺自己也修了一处重复计数**：嵌套 `LbDialog(…, confirm = LbDialogAction(…))` 时
  外层实参切片含内层那条串一次、内层锚点又数一次。按锚点求和 ⇒ 同一次实扫 **85**；
  按字符区间去重 ⇒ **59**。不去重的话"把一颗按钮拆成两颗 Lb 组件"都会让数字涨，那涨的是量具。
  夹具补了 H.kt 当牙（N5 退回求和版本，那格立刻 `expected:<3> but was:<4>`）。

## 28.6 变异：七发，其中两发第一版是无效探针

| 探针 | 改了什么 | 结果 |
|---|---|---|
| N1 | 动作 `heightIn(min = 8.dp)` | `对话框 有 3/3 个可交互节点小于 48dp`、禁用那颗 `expected:<48.0> but was:<34.0` |
| N2 | `TextButton(enabled = true)` 不再透传 | `禁用那颗必须带 Disabled 语义` 红 |
| N3 | 上限 3 放宽成 9 | `超过 3 颗次级出口应当抛，实到异常：null` 红 |
| N4 | `text = null`（正文槽不接） | 标题/正文格 + body 格两格红 |
| N5 | COMPONENT 退回按锚点求和 | 夹具 H 那格 `expected:<3> but was:<4` 红（顺带增长闸） |
| N6 | 生产里再直接 call 一颗 `AlertDialog` | 所有者闸点名 `[ui/home/HomeComponents.kt]`（同时涨字面量闸） |
| N7 | 豁免文件再加一颗裸 `Dialog(` | `实到 2，豁免登记的是 1` 红 |

⚠ **N6/N7 第一版根本没跑到测试**：注入的 Kotlin 编译不过（只给两个实参时 `AlertDialog` 解析到了
"自定义 content"那个 overload，`title` 就成了未知参数；`Dialog(properties = {})` 类型也不对）。
读结果的脚本只看"有没有新鲜的失败 XML"，于是把这种情况报成 **"探针没咬（恒绿）"**——
那是把"我的探针无效"误报成"我的闸没牙"，比假绿更坏，因为它会让人去改闸。
现在 runner 先分诊：日志里有 `e:`/`Compilation error` 就报"这一发作废"，并打印前三条编译错误。

## 28.7 实测

| 项 | 结果 |
|---|---|
| `:app:compileDebugKotlin` / `UnitTestKotlin` / `compileDebugAndroidTestKotlin` | RC=0 / RC=0 / RC=0 |
| `:app:testDebugUnitTest` 全量 | **164 套件 / 1268 例 / 0 红 / 0 跳过**（上一格 162/1260；+2 套 = `LbDialogTest`、`DialogProbeTest`） |
| `:app:lintDebug` + `check_lint_budget.sh` | RC=0 / RC=0：`measured 68 / rules 15`、入预算 `67 / 14`、advisory 1（条数与上一格一字不动） |
| `test_check_lint_budget.sh` / `strip_ticket_ids --check` / `asset_hashes --check` | RC=0 / RC=0 / RC=0 |
| `package_deps_report.sh --count` | RC=0，仍是 **6** 笔同一批文件 |
| 探针撤回后核账 | 四个被改文件与 `_temp/mut73-backup/` 逐字节 IDENTICAL |

## 28.8 这格没做的

- §6.1 这一行**只做了一半**：Dialog 半边收口，**`LbModalSheet` 半边没有**。
  **→ Sheet 半边已还：见 §29（提交 `cccabb0`）**。但那句「不把展开内容直接插在原页面下方」
  仍然没闸——那一格只换了形状的所有者，没换状态的所有者。
  表里那句还有后半段"不把展开内容直接插在原页面下方"——那说的是面板里
  `DislikeReasonPanel`、`CorrectionCenter`、`RecordSentDialog` 这类"插在原页面下方"的展开内容，
  归 §6.4（`ResultArea` 拆分 + state holder + modal host），这格一处都没动。
- 供应商编辑器那颗裸 `Dialog(` 仍在（已点名豁免）。它要并进去，得先有 Sheet 那一半。
- 对话框的**可见性本身**仍无守卫：11 处浮层里"什么时候弹、弹了挡住什么操作、返回键算不算取消"
  这些语义一条 JVM 断言都没有（这格只钉了"弹出来之后长什么样、点不点得到"）。
- `LbDialogTest` 钉的是组件自己：11 个**调用点**没有逐屏用例（改名那颗的输入框、导出预览那四颗
  按钮的实际渲染），只有组件层 + "只有一个所有者"那把闸在守。设备侧同样没跑（本机无 system image）。
- 截图基线（§6.5）照旧故意没接。这格之后接基线会看到的可见变化：对话框标题统一成 `titleLarge`
  （导出预览、导出失败、操作失败那三屏的标题变大）、按钮统一 SemiBold + 48dp 热区、
  复制那颗不再按状态换字重（文案仍换）。

---

# 追加十九：§6.1 Sheet 半边归 core，浮层里三条量出来的无障碍缺陷（提交 `cccabb0`）

## 29.1 那一行的后半句与仓库当时的形状

指导书 :487：

> | `LbModalSheet/Dialog` | 需要用户决策的浮层；不把展开内容直接插在原页面下方 |

上一格（§28）收了 `Dialog` 半边。这一格收 `Sheet` 半边：面板与气泡里的浮层原先是
`ui/panel/PanelModalHost.kt`（141 行，三颗公开组件 `PanelModalHost` / `PanelModalTitle` /
`PanelModalActions`），三处调用点（`ResultArea` 的时长子菜单与"标记为错误"、`SuggestPanel` 的目标编辑）。

**为什么这里必须是第二种形状，而不是复用 `LbDialog`**：面板跑在
`TYPE_ACCESSIBILITY_OVERLAY` 窗口里，没有合适的 activity token，Material 的 `AlertDialog`
（内部起一棵 Dialog 窗口）会抛 `WindowManager.BadTokenException`。所以这一套是**同一棵
ComposeView 里自画**的遮罩 + 居中卡片。这是平台约束，不是"又有人想自造一套"——
这句话写进 KDoc，免得下一个窗口把它当成违规收掉。两种形状**共用同一份动作词表**
（`LbDialogAction` / `LbDialogActionTone`），所以"同一颗取消"在对话框和浮层里至少同一个说法、同一种着色。

## 29.2 先量后写：旧形状的四个可交互节点，两个没名字、两个不够大

`SheetProbeTest` 在改之前量到的（原文照抄）：

```
PROBE 面板弹层可交互节点：
   「」 role=无 selected=null state=null 尺寸 360x1000dp @(0,0)
   「PROBE_SHEET_TITLE」 role=无 selected=null state=null 尺寸 331x84dp @(15,458)
   「取消」 role=无 selected=null state=null disabled 尺寸 48x26dp @(262,504)
   「」 role=无 selected=null state=null disabled 尺寸 24x22dp @(310,506)
PROBE 没有可读名字的节点数 = 2；高度小于 48dp 的 = 2 / 4
```

三条缺陷，各自对应 §6.5 的一句话：

| 缺陷 | 违反 | 旧形状为什么长这样 | 现在 |
|---|---|---|---|
| ① 动作 26dp / 22dp 高 | :531 所有可点击节点 ≥48×48dp | 按钮是裸 `Text` + `padding(vertical = Spacing.sm)` | `heightIn(min = LB_SHEET_ACTION_MIN_DP.dp)`，且内边距排在 `clickable` **之后** |
| ② `confirmLabel = ""` 画出一颗 24x22 的**无名**可点节点 | :531 "可交互节点要有可读名字" | 时长子菜单没有主动作，调用方就用空串表达"这里没有" | 标签为空的动**不入树**；"没有主动作"就是不画那颗节点 |
| ③ 遮罩（360x1000）与卡片（331x84，还把标题合并进去当成自己的标签）各是一颗 clickable 节点 | 同上 + 读屏多念两口没名字的按钮 | "点空白关闭"和"点内容不关闭"都用了 `clickable`，后者还写了一个空的 onClick | 两处改用 `pointerInput { detectTapGestures }`：手势照拦，不再对外声明"我是按钮" |

顺带并掉一处**同一个规则的两重表达**：`SuggestPanel` 那颗"保存"原先同时写
`confirmEnabled = !overLimit` 和 `onConfirm = { if (!overLimit) … }`——两处判同一个条件，
改一处忘另一处就会"灰着却能点"。现在只有 `LbDialogAction.enabled` 一处。

## 29.3 守卫与探针

`LbModalSheetTest` 5 格（全部读 `boundsInRoot` 与语义属性，不看源码里的数字）：
每颗出口 48dp 且**这个形状里就只有那两颗出口**、遮罩与卡片不许冒充按钮（宽度 >320dp 的可交互节点必须为 0）、
空标签的动不入树、禁用那颗"灰着还在 + 带 Disabled 语义 + 仍有 48dp"、标题与正文各恰好一个节点。

| 探针 | 改了什么 | 红了谁 |
|---|---|---|
| S1 | 摘掉动作的 `heightIn` | 下限格 + 禁用格（`expected:<48.0> but was:<22.0>`） |
| S2 | 不再过滤空标签 | 报出多出来的 `24x48`、`33x48` 两颗无名节点 |
| S3 | 遮罩换回 `clickable` | "不许冒充按钮"红：`「SHEET_TITLE_SENTINEL」…360x1000dp` |
| S4 | 卡片拦截换回 `clickable` | 同一格、红在另一颗节点：`331x106dp`——正是旧形状把标题念成按钮的真实形状 |
| S5 | 摘掉 `enabled` 透传 | 禁用语义格红 |
| S6 | `ui/home` 里复活 `PanelModalTitle` | 归属棘轮点名（登记 11 对之后仍然咬） |

S3 与 S4 落进同一格用例但**是两颗不同节点**，所以分开跑——这是坑表 60 那条口径的又一次应用
（一起跑只知道"那格红了"，说不出是哪种坏法）。

## 29.4 字面量：+7 条全是从"默认实参"和"非 Lb 锚点"里露出来的既有文案

TEXT 不动（209），COMPONENT 59 → **66**。这 7 条逐个对得上：
「暂停时长」「标记为错误」「确认」「保存」「取消」×3——它们原先写在
`PanelModalTitle("…")` 与 `PanelModalActions(confirmLabel = "…")` 这类**非 `Lb` 锚点**的实参里，
三把尺一条都看不见；其中「取消」更藏在前身组件的**默认实参** `dismissLabel: String = "取消"` 里
（新坑 66：默认实参里的用户可见文案，比调用点写的更难被锚点看见）。
⇒ 涨的是量具新看见的既有债，不是这一格新塞的字。

## 29.5 实测

| 项 | 结果 |
|---|---|
| `:app:compileDebugKotlin` / `UnitTestKotlin` / `compileDebugAndroidTestKotlin` | RC=0 / RC=0 / RC=0 |
| `:app:testDebugUnitTest` 全量 | **166 套件 / 1274 例 / 0 红 / 0 跳过**（上一格 164/1268，+2 套 = `LbModalSheetTest`、`SheetProbeTest`） |
| 受影响范围（`core.designsystem.*` + `ui.panel.*` + 归属棘轮） | 125 例 / 0 红——其中面板那批测试一字未改仍然绿，是"换所有者没换行为"的主要证据 |
| lint | RC=0；`measured 68 / rules 15`、入预算 `67 / 14`、advisory 1（收了三处浮层 + 退役一个 141 行文件，条数一字未动） |
| 预算四栏 | TEXT 209 / DESC 12 / STATE 0 / COMPONENT 66，与常量零差 |
| 其它闸 | 预算自测、工单编号、资源锁、跨层依赖 **6** 笔（同一批文件）、androidTest 编译全 RC=0 |
| 探针核账 | S1-S6 撤回后 `LbModalSheet.kt`、`HomeComponents.kt` 与 `_temp/mut74-backup/` 逐字节 IDENTICAL |

## 29.6 这格没做的

- §6.1 表 11 行现在**都有主人了**，但 `LbModalSheet/Dialog` 那行的**后半句仍然没闸**：
  「不把展开内容直接插在原页面下方」。`ResultArea` 里的 `MemoryRefItem` 仍然**自己持有**浮层状态
  （`menuOpen` / `showMuteSubmenu` / `showWrongDialog` / `wrongText` 全是它内部 `remember` 的），
  这属于 §6.4 那条"ResultArea 只负责结果内容…拆成独立 state holder + modal host"——
  这一格只换了**形状的所有者**，没动**状态的所有者**。别把两件事混着报。
- 面板里剩下的浮层（`DislikeReasonPanel`、`CorrectionCenter`、`RecordSentDialog`）
  仍不是 `LbModalSheet`：`CorrectionCenter` 与 `RecordSentDialog` 各挂自己的遮罩与卡片
  （§29.7 未列全，因为这一格没去数它们的语义树）。要并进来是 §6.4 那一格的事。
- 遮罩的**可达性**这格只做了"不冒充按钮"，没做"读屏知道点空白可以关闭"。
  真要补，应该是给遮罩加 `contentDescription` 走资源（中英两份），而不是把它改回 clickable。
- `SHEET_MAX_HEIGHT_DP = 560` 这条尺寸约束仍是**写死在组件里**的旧值，没量过它在不遮挡输入时
  是否成立（面板高度会变），要动它得配合 §6.5 的截图基线一起做。
- 设备侧仍未跑（本机无 system image）。`PanelModal*` 那三颗的名字在 androidTest 里没有引用，
  所以改名不伤设备用例；但"overlay 窗口里点得到点不到"这句话还是要 CI 才算数。
