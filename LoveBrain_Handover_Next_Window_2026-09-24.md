# LoveBrain 交接：下一窗口开工单（2026-09-24，CI 首跑之后）

> 你要做的事只有一件：**严格按照 `LoveBrain_Three_Phase_Reaudit_and_Six_Principles_UI_Architecture_Guide_c0ff0415_2026-09-24.md` 继续**。
> 这份单子告诉你：现在真正卡在哪、上一窗口做到哪、怎么起手、哪些坑不必再踩。
> 账本：`LoveBrain_Guide_Item_by_Item_Verification_2026-09-24.md`（逐条对照，三态标注）·
> `LoveBrain_Three_Phase_Execution_Log_c0ff041_guide_2026-09-24.md`（§2i–§2n 是最近六轮实测）。

## 0. 一句话现状

**远端已经是 `3d92488`**（本窗口推过了，没有未推送提交）。CI run `36019334520`（10m10s）结果：
`verify` **红** · `ui-test` **红** · `upgrade-test` **skipped**。产物第一次齐了五件：
`unit-test-report` `lint-report` `instrumentation-test-report` `test-xml-report` `logcat`。
缺 R8/APK 元数据、SBOM、费用 dry-run、egress 证据 —— 全部因为 verify 在中间一步断掉，没走到。

## 1. 起手必查（照抄，别凭记忆）

```bash
git fetch origin && git rev-parse --short HEAD origin/main
gh run list --limit 3                       # 看 36019334520 之后有没有新 run
gh run view 36019334520 --json jobs \
  --jq '.jobs[] | .name + " | " + (.conclusion // "?") + " | " + (.steps | map(select(.conclusion=="failure") | .name) | join(" ; "))'
gh run view --job=107699925887 --log-failed 2>&1 | tr -d '\r' | grep -E "lint-budget|FAIL" | head -8
gh run view --job=107699926424 --log-failed 2>&1 | tr -d '\r' | grep -Ei "gate\] ui-test: tests=|FAIL ui-test" | tail -4
```

本地基线（`3d92488`，本窗口实测）：**1082 单测 / 134 套件 / 0 失败 / 0 跳过**；
本机 lint **71 条 / 15 规则**（⚠️ 与 CI 不一致，见 §2.1）；跨层 6 条；取消审计 NEEDS_REVIEW=0；
工单编号 PASS；prompt 零 diff + lock `6dcde732…`；`>500` 行 18 个 / `>800` 行 10 个。

## 2. 现在真正卡着的两件事（按顺序做，做完才谈别的）

### 2.1 verify 红：lint 预算是**按平台**量的

CI 实测 `81 条 / 16 条规则`，预算文件登记 `15 条规则`，本机是 `71 / 15`。
差出来的那条规则只在 Linux checkout 上出现。

- **禁止**把预算抬到 81 了事 —— 那是用调尺子冒充修问题，本仓这条闸两次拦下我新增的债，
  一次都没抬过预算，别开这个先例。
- 该做的：把 CI 的 `lint-report` 产物与本机 XML 的 issue **id 集合做差**，找出那条只在 CI 出现的规则
  （`curl -L` 下 artifact 或 `gh run download 36019334520 -n lint-report`）。
- 然后二选一，并在提交信息里写清选了哪个、为什么：
  ① 那条规则报的是**真问题** → 改代码消掉它；
  ② 那条规则是平台/环境产物（例如只在 Linux 触发的配置类提示）→ 给预算文件加**按平台各自登记**，
  两边各自锁死，不许取 max。
- 顺手改一条纪律：`check_lint_budget.sh` 的输出里要点名"本次是在哪台尺上量的"。

### 2.2 ui-test 红：43 条全跑了，23 条真失败——这是收获不是回归

`[gate] ui-test: tests=43 failures=23 errors=0 skipped=2 suites=1`，两次尝试都红，
但 XML、logcat、`home.png`/`knowledge-base.png` 都产出了。对照指导书当年那条
"`ui-test` 在 Gradle 之前就退出、40 个测试 0 个运行"，现在的问题从"没证据"变成"证据说你不达标"。

要做的：**逐条分类那 23 条**，分完再决定动谁。分类只有三种：

| 类别 | 判据 | 处置 |
|---|---|---|
| 真不达标 | 语义树断言量出的尺寸/标签确实不够（如 48dp、role、重复播报） | 改生产，按 §3 那台仪器在本机先复现 |
| 夹具/装配问题 | 节点找不到、窗口没测到、依赖没注进来 | 改测试夹具，**不许把断言改软** |
| 只有设备才有的差异 | 本机 JVM 绿、真机红（density、字体回退） | 留档 + 标"只能等 CI"，不许写"已修复" |

指导书 §9 第 3/4 条在这格上是真的：**不许为了绿调软断言，不许用源码 grep 顶替行为测试。**
已跳过的 2 条是 `Assume`（Service destroy），算 skipped 不算通过。

做完 2.1 + 2.2，`upgrade-test` 与那四类缺失产物才第一次有机会亮出来。

## 3. 已经有的仪器（**别重新发明，直接复用**）

`app/src/test/java/com/lovebrain/app/core/testing/`：`UiMatrix`（4 宽 × 3 字 = 12 格）、
`SemanticsProbe`（读语义树的尺）、`UiProbeApplication`（空壳 App）。走 `:app:testDebugUnitTest`
→ CI 的 verify 每次都跑，本机也跑，**不必再等 emulator**。

可复用的断言：`assertAllActionableMeetTouchFloor` / `assertAllActionableLabeled` /
`assertNoDuplicatedAnnouncement` / `assertSelectableAnnounceState`；读宽度用
`UiMatrix.FULL`，节点尺寸取 `Target.widthDp/heightDp`，图标的话看 `contentDescriptions`（不是 `label`）。

现成范例（照抄形状最快）：`PanelHeaderTouchTargetsTest`、`MessageListEmptyStateTest`、
`ReplyPrimaryActionsContractTest`、`LbAsyncStateTest`、`ProviderSectionSemanticsTest`、
`SuggestTipCardSemanticsTest`。**"这页要 ViewModel 所以测不了"不成立**：
`ProviderSectionSemanticsTest` 用 mockk 桩住三条 StateFlow 就整页可测。

## 4. 上一窗口做完的事（别重复劳动）

| 提交 | 内容 |
|---|---|
| `cb44ceb` `6177cd0` | 装这台 JVM 语义树仪器 + 清掉它自己带进来的两条 lint 债 |
| `c927b2e` | 三段模式切换点击区 **84x18dp → 116x48dp**，带 `Role.Tab`，selected 随模式走 |
| `9f4747a` | 空态蓝字入口 **224x23dp → ≥48dp** + `Role.Button`，两种内联中文进资源 |
| `ead80c1` | 收起按钮读屏念两遍（`Collapse panel+Collapse panel`）修掉，加可复用闸 |
| `2ef47ac` | §2.1 那张"交互合同应永久钉死"表 → 6 格 JVM 用例（含 N=0 是"灰着不能点"不是消失） |
| `df1e802` | §5.3 第二格：`KnowledgeCatalogStore`，`listAll` 与 `listAllUnlocked` 从此一个所有者 |
| `cd7e1df` | 文案尺补第一个盲区（`text = if …` 看不见）：实测 **209 → 254** |
| `e359930` | `core/designsystem` 建了：`ScreenState` + `LbEmptyState` + `LbAsyncState`；反馈案例页换完四态（534→500 行） |
| `ca76bac` | 锦囊折叠入口 **344x17dp → ≥48dp**；`stateDescription` 内联中文进资源；文案尺第二盲区（STATE 判据，起点 0） |
| `d902514` `eb8666c` `80bc78e` | 供应商页：先被量到（「＋ 添加供应商」**312x34dp → 48dp**、折叠图标本地化），再并到统一空态版式，最后接上同一个 `ScreenState` 四态出口 |
| `1167c34` `3d92488` | 组件矩阵扩到 12 格全跑 + 给循环本身加哨兵断言（⚠️ `1167c34` 是个编译不过的中间提交，`3d92488` 修的；改写历史与否由用户决定） |

## 5. 还没做的（分两类，别混）

**A. 只有推送/CI 才能闭**：§2.1、§2.2 两格；`upgrade-test`；R8/APK 元数据、SBOM、费用 dry-run 四类产物；
egress 剩 4 格（CI 上 `apt-get install tshark`）；Service destroy 2 格（设备侧安排）。

**B. 本机就能做、上一窗口没做**：
1. §5.3 剩 **document / profile / memory / round** 四格 + **catalog 写侧**
   （`create/delete/setActive/updateDisplayName/ensureInitialKnowledgeBase`）。
   形状照 `5a21ec2` 与 `df1e802`：给每格定义"它真正需要的最小能力接口"，由 `RepoStorage` 一个内部类实现；
   **不 new Mutex、不搬 CoroutineScope**（`SingleOwnerContractTest` 会拦，别绕闸）。
   `applyProfileUpdateAtomically`（160+ 行、跨画像/温度/阶段/向量）别拿它开第一刀。
2. §6.1 剩 **9 个** `Lb*` 组件；token 从 `ui.theme` 迁进 `core/designsystem`
   （这条欠账登记在 `PackageDependencyTest` 的 core 规则旁边）。
3. §6.3 剩 **知识库、捕获范围** 两个目的地；§6.2 首页四段 0%；§6.4 ResultArea 浮层拆分 0%（仍 1305 行）。
4. §6.5：截图工具（Roborazzi 或 Paparazzi 二选一）+ baseline **人工 review** 流程 ——
   上一窗口核过 `roborazzi{,-compose,-junit-rule}:1.24.0` 坐标真实可取但**故意没接**：
   §6.1–§6.4 铺开之前拍的 baseline 会整批作废。做之前先想清楚这条顺序。
   未覆盖的还有：中文环境那一格、超长 Provider 名/`￥9999.999`/`100000 次生成`、颜色对比度、
   把"产品只有浅色"写死（实测 grep `darkColorScheme`/`DayNight` 零命中）、`LoveBrainPanelScreen`
   其余可点控件、谈心面板那颗"继续追问"胶囊（它整页要 VM，属 §5.2 第 6 步的下游）。
5. **§5.2 第 6 步删 facade：0%**，`LoveBrainViewModel` 仍 2746 行。它是 A 类里两处"本机测不到"的共同上游。
6. P1-05 剩 **250** 处字面量（尺子已修准，搬了会掉数字）；P1-01 大文件一个没减（**不许机械切文件凑数**）。
7. §3.3 两条文档改口：prompt"冻结"只冻 hash 不冻文本；`LoveBrain_Rework_Acceptance_2026-09-24.md` 里
   "全部写路径统一拒绝""仍用冻结 prompt"与实现不符。
8. 仓库根目录那两份输入报告（本指导书 + `LoveBrain_Comprehensive_Reaudit_286c9406_2026-09-23.md`）
   仍是 **untracked**，入库与否等用户点头。

## 6. 上一窗口踩过的坑，一次列全（照着避，能省好几轮）

**验证方式**
1. **`./gradlew … | tail -3 && …` 会骗你**：管道把 Gradle 退出码换成 `tail` 的 0，
   于是编译失败也往下走，读到上一次留下的**陈旧 XML** 就当绿了 —— 真出过一次，
   交了个编译不过的提交 `1167c34`。规矩：**gradle 不接管道**、单独取退出码、
   再按 mtime 逐文件比新鲜度（`test-results/` 失败时不清理）。
2. **Windows 上 `rm -rf app/build/test-results/...` 会 Device or resource busy**，
   别指望删目录来保证新鲜，用 mtime 比。
3. **控制台是 GBK**：python 打印中文一律 `PYTHONIOENCODING=utf-8`，否则你会以为文档乱码了。
4. **bash heredoc 里写 python 字符串，别在双引号串里嵌 ASCII 双引号**（这错误连踩三次）；
   改文档优先用 Edit 工具。
5. 仓库文件**混着 CRLF 和 LF**：脚本化改文件要先探测 EOL，字节读字节写，改完比 `wc -c`。

**那台 JVM 仪器**
6. `@GraphicsMode(NATIVE)` 是前提：legacy 模式下**文字度量是假的**（同一节点 32x39dp vs 224x23dp）。
7. 纯控件要用 `UiProbeApplication`，不然第二个用例撞 `KoinAppAlreadyStartedException`。
8. **一个用例只能 `setContent` 一次**：跑矩阵得改 hoisted 状态，别在循环里重新挂载。
9. 别按 index 点节点：点过返回箭头当卡片，会让三条断言在"根本没展开"上跑（假绿）。
10. 合并语义里 `label` 优先取文字，图标声明的话要读 `contentDescriptions`。
11. `Modifier.semantics {}` **不是 composable 上下文**，里面不能调 `stringResource`——在外面解析好再传。

**闸的用法**
12. **还债之后计数没动 = 尺子在漏**（这条在文案尺上连中三次：`text = if …`、赋值锚点被内层括号截断、
    `stateDescription` 根本不在判据里）。搬了字面量而预算不掉，先怀疑尺子。
13. lint 预算的教训新增一条：**本机绿 ≠ CI 绿**，它是按平台量的（见 §2.1）。
14. 新断言必须被坏实现打破过才算有牙；"注了反例还全绿"有两种解释（测试瞎 / 变异等价），先分清再动手。
15. 生产注释里不要写工单编号（`strip_ticket_ids.py --check` 会红，我踩过）。

## 7. 硬约束（违者就是没读指导书）

不许改 prompt 内容（`git diff --exit-code 286c9406..HEAD -- app/src/main/assets/engine` 必须零差异）；
一个提交一个验收目标、message 写用户行为；先写会红的回归测试；
不许用源码 grep 顶替 UI/触摸/无障碍测试；不许用"脚本存在"代替产物；
不许新增 `|| true` / `continue-on-error`；**不许为了让 CI 绿而调软断言或抬预算**；
文档里的数一律来自当次命令输出；不为行数机械拆文件；
**发布要独立复核签字，worker 不自签**；推送需要用户明确说「推送」。

## 8. 建议的起手顺序

1. §2.1 那条 lint 平台差（一次提交就能让 verify 有机会绿，从而解锁 §2.2 的产物阅读）。
2. §2.2 那 23 条：先分类再动，每类各一提交。
3. 然后回到 B 类：§5.3 的 document 一格，或 §6.3 的知识库页接四态（测试形状已有两份范例可抄）。
4. 任意时刻用户说「推送」→ 先推，再读同一 SHA 的三项结果。

## 9. 当前发布判定

**NO-GO。** `verify` 与 `ui-test` 在 `3d92488` 上都是红的，`upgrade-test` 没跑，四类产物缺失。
本窗口不签 PASS，下一个窗口也不许签 —— 判据是指导书 §10：三项 required checks 全绿且 artifacts 齐全。
