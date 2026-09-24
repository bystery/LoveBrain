# LoveBrain 交接：未完成清单（2026-09-24）

> 写给**下一个窗口**。这份文档只讲"还没做什么、为什么没做、怎么起手"。
> **本文件写完后又被下一个窗口改过状态**：`cb44ceb…2ef47ac`（6 个提交）装了一台
> JVM 侧读语义树的仪器并修掉三处无障碍缺陷。**逐条最新对照请看**
> `LoveBrain_Guide_Item_by_Item_Verification_2026-09-24.md`；本文件里标 ⚠️ 的行以那份为准。
> 已完成部分的逐项证据在 `LoveBrain_Three_Phase_Execution_Log_c0ff041_guide_2026-09-24.md`
> （§2b–§2i 是一轮一轮的实测记录）。输入指导书：
> `LoveBrain_Three_Phase_Reaudit_and_Six_Principles_UI_Architecture_Guide_c0ff0415_2026-09-24.md`。

## 0. 现状三句话 + 起手必查命令

- 阶段一：P0-03 / P0-04 / P0-05 本机完成；**P0-01 / P0-02 的真机证据还没拿到**（要推送才能闭）。
- 阶段二：§5.2 五条 feature store + "模式"全部归位；**§5.2 第 6 步（删 facade）没做**；
  **§5.3 出去 3 格**（migration / backup / catalog 的枚举侧 `df1e802`）；
  catalog 的写侧与 document / profile / memory / round 未动。
- 阶段三：⚠️ 组件体系（`Lb*` / 首页四段 / `ScreenState` / ResultArea 拆分 / 截图工具）**仍一行没动**；
  但 §6.5 的"语义树那一半"已有仪器，并据此修掉三处缺陷（`cb44ceb…2ef47ac`，详见执行记录 §2i）。

```bash
git rev-parse --short HEAD && git rev-list --count 4795471..HEAD && git ls-remote origin main
PYTHON=/d/anaconda/python bash scripts/check_lint_budget.sh
PYTHON=/d/anaconda/python bash scripts/package_deps_report.sh --count
bash scripts/asset_hashes.sh --check docs/prompt-assets.lock
rm -rf app/build/test-results/testDebugUnitTest && \
  ./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin --no-daemon
```

本机实测（最后一次全量跑，`df1e802`）：**1067 单测 / 131 套件 / 0 失败 / 0 错误 / 0 跳过**（比上一轮的
1045 多的 22 格：§2i 的语义树/合同 17 格 + §2j 的目录枚举 5 格）；lint 71 issues、0 error；
跨层越界 6 条；取消审计 167 站点 NEEDS_REVIEW=0；工单编号扫描 PASS；prompt 目录零 diff；
生产 Kotlin 125 个 / >500 行 18 个 / >800 行 10 个。

**别信文档里任何写死的行数或计数**——本次交接就是因为一个数没重量产出了错误（见 §4 第 1 条）。

## 1. 指导书要求、但**一行没做**的（不是"做了一半"）

| # | 条目 | 指导书出处 | 现状 | 起手式 |
|---|---|---|---|---|
| 1.1 | §5.2 第 6 步：VM 退成只组合只读 StateFlow 的 facade，然后**删 facade** | §5.2 步骤 6 / §7 第二步完成定义 | VM `LoveBrainViewModel.kt` **2746 行**，比本轮开工前（2651）**长 95 行**。状态持有者都搬走了，编排与转发全留着 | 先量调用面：`grep -rn "viewModel\." app/src/main/java/com/lovebrain/app/ui app/src/androidTest` 统计每个 public 流的引用数；只被一个面板用的流 → 把该面板改成收 `StateFlow` 参数而不是收 VM；命令类方法（generate/cancel/undo）保留在 VM |
| 1.2 | §5.3 剩下几格：catalog 写侧 / document / profile / memory / round | §5.3 | 仓库 1941 行。已出三格：backup（`5a21ec2`）、migration（早就是 `KnowledgeMigrator`）、**catalog 的枚举侧**（`KnowledgeCatalogStore`，`df1e802`：`listAll` 与 `listAllUnlocked` 从此一个所有者）。`create/delete/setActive/updateDisplayName/ensureInitialKnowledgeBase` 这五个写侧动作还在仓库里 | 照 §5.3 分组：**catalog**=`listAll/getActive/setActive/create/delete/updateDisplayName/ensureInitialKnowledgeBase`；**document**=`readFile/appendFile/deleteFile/writeFile/writeFileWithVersion/readFileWithVersion/toKbName/toKbPath/safeKbFile`；**profile**=`readProfile/applyProfileUpdateAtomically/getCurrentStage/updateStage/updateWarmthStageLabel/readVector/writeVector/getTurnCount/incrementTurnCount*`；**memory**=`readIntent/saveIntent/readCorrections/saveCorrection/undoCorrection/*RevisionCheck/appendActualSentRecord/replaceActualSentRecord/appendCounselingEntries/readCounselingAnalysisBlocks`；**round**=`transaction/KnowledgeTx/RoundCommitJournal` 那条链 |
| 1.3 | 拆类必须共用**一个** `KnowledgeTransactionManager`，不许每类各自 new Mutex | §5.3 末句 | 现状是仓库唯一一把 `fileMutex` + `RepoStorage` 受限视图，形状已经对了——**下一格必须沿用**，不要新开锁 | 沿用 `BackupStorage` 那个做法：给每格定义"它真正需要的最小能力接口"，由 `RepoStorage` 一个内部类去实现，别把 `KbStorageAccess` 当万能接口传 |
| 1.4 | §6.1 设计 token + 11 个 `Lb*` 基础组件 | §6.1 表 | 一个都没有。现有页面各自造标题样式、按钮、卡片 | 组件清单（照指导书表）：`LbScreenScaffold` `LbTopBar` `LbSection` `LbPrimaryButton` `LbActionCard` `LbSettingRow` `LbMetricCard/Grid` `LbEmptyState` `LbAsyncState` `LbModalSheet/Dialog` `LbStatusBadge`。先建 §5.1 说的 `core/designsystem` 目录（还没建） |
| 1.5 | §6.3 每个目的地统一 `ScreenState`（Loading/Content/Empty/Error 四态） | §6.3 | 未做。Provider、反馈案例、知识库、捕获范围各自发明空态/错误态 | 定义一次 `sealed interface ScreenState<out T>`，然后**逐页**替换，每页一格提交 |
| 1.6 | §6.2 首页固定四段 + 不把编辑器/列表展开在首页 | §6.2 | Home 未按四段重排 | 四段：顶部 About / 军师状态主卡 / 快捷功能 `LbActionCard` / 设置概览 `LbSettingRow` + 三等分 `LbMetricGrid` |
| 1.7 | §6.4 ResultArea 不再承载全部浮层，拆成独立 state holder + modal host | §6.4 | 未做。`ResultArea.kt` 仍 1305 行，菜单/纠正中心/发送记录/改写/版本历史/反馈原因都在里面 | 先把每个浮层的 state holder 从 `ResultArea` 的参数里剥出来，再谈 modal host；这一步做完 §6.2/§6.3 才有落点 |
| 1.8 | §6.5 截图/视觉门禁：Roborazzi **或** Paparazzi 二选一接入 | §6.5 | 截图工具根本没接 | 先选型再写用例。矩阵维度：宽 320/360/412/600dp × 字体 1.0/1.3/2.0 × 中英文 × 深浅色（**若暂不支持深色就明确锁定浅色，不要做半套主题**）；用例要含长 Provider 名、超长模型名、`￥9999.999`、`100000 次生成`；**baseline 变更必须人工 review，禁止自动覆盖 baseline 后判绿** |
| 1.9 | §6.5 无障碍自动化：TalkBack role / selected / disabled / stateDescription / contentDescription；bounds ≥48dp **不许用源码搜索代替** | §6.5 / P1-02 | ⚠️ **本窗口推进了**：JVM 语义树仪器已建（`app/src/test/…/core/testing/`，走 `testDebugUnitTest`，CI verify 每次跑），PanelHeader 12 格矩阵 + 空态入口 + 主操作区共 **17 格**在跑，三处缺陷据此修掉。**仍缺**：`LoveBrainPanelScreen` 其它可点控件、Home/Provider/Feedback/知识库/捕获范围；`stateDescription` 那两处（谈心/锦囊折叠）无断言；高度维度与"超长文案/极端数字"没测 | 直接照 `PanelHeaderTouchTargetsTest` 的形状往其它页扩：`SemanticsProbe.assertAllActionableMeetTouchFloor / assertAllActionableLabeled / assertNoDuplicatedAnnouncement`。**两个前提别丢**：`@GraphicsMode(NATIVE)`（legacy 的文字度量是假的，32x39 与 224x23 之差）、纯控件用 `UiProbeApplication`（不然第二个用例撞 `KoinAppAlreadyStartedException`）。`ProductionUiContractTest` 那种源码 grep 门禁只能当"防回退"，不能当证据 |
| 1.10 | P1-05 文案收口：209 处用户可见中文字面量搬进 `strings.xml` / `values-en` | §6.1 / P1-05 | 一行没搬。宽尺测到 **209 处**（报告里的 101 是只数 `Text("中文` 的下界） | 闸已经装上：`UiStringLiteralBudgetTest`（TEXT 209 / DESC 10 的预算）。每搬一批就把预算**改小**，不允许调大；一次提交搬一页，别混进组件改造 |
| 1.11 | P1-01 18 个 >500 行文件、10 个 >800 行文件 | P1-01 | 数量没动（本轮只让 KnowledgeRepository 从 2001 降到 1942） | 每完成 1.2 的一格就少一个候选；`ResultArea` 1305、`FloatingService` 1153、`DeepSeekRepository` 1114 是下一批目标 |

## 2. 我**主动没开**的（理由：单次会话的上下文余量不够，怕留半成品）—— 不是"指导书没要求"

明确写清楚，免得被当成"已经评估过、不值得做"：

1. **§5.3 剩下五格**。我在完成 backup 这一格后停手，原因是那一轮已经用了大量上下文在
   `SingleOwnerContractTest` 拦回第一版设计（`scope.launch` 不能进策略类）+ 14 格新测试 + 8 个变异反证。
   五格里 **catalog 和 document 最独立、最容易先做**；`profile` 的
   `applyProfileUpdateAtomically`（160 多行、跨画像/温度/阶段/向量）最硬，别拿它开第一刀。
2. **删 facade**。它天然是"改所有调用点"的大面积改动（UI + androidTest 签名一起动），
   和 §6 的面板重写撞在一起——先做 §6.4 的 ResultArea 拆分，删 facade 会便宜很多。
   我选择先去开 §5.3 的头（能留一个可复制的形状），而不是把这两件都开成半成品。
3. **阶段三全部**。指导书把它排在第三步，我按顺序做，没跳。
4. 三件小的、本来顺手能做但我没排进那一轮：
   - `KnowledgeRepository` 里 `readFile`/`appendFile` 这类 document 能力的**公开重载去重**；
   - `scripts/check_lint_budget.sh` 在 Windows 上必须 `PYTHON=...`，否则走 `python3` 占位符
     报 CANNOT-VERIFY（已写进脚本输出，但 README/CI 文档没提）；
   - 本轮所有变异脚本都留在了 `_temp/`（按 `.gitignore` 不入库）——**下一个窗口没法重跑它们**，见 §5。

## 3. 只能等推送 / CI 的（本机做不到，不是没做）

| 事项 | 为什么本机闭不掉 | 推送后要拿的证据 |
|---|---|---|
| P0-01 CI 总红 → 全绿 | 本机没有 system image，`ui-test` / `upgrade-test` 只能在 CI 跑 | 同一 SHA 的 `verify`(26 步) + `ui-test` + `upgrade-test` 全 ✓，产物含 XML / HTML / 截图 / logcat / upgrade 报告 / APK / SBOM |
| P0-02 "点击生成回复不崩溃" 10 格真链路 | 同上：现在有 **19 条 instrumentation 真失败**（7 条同一个夹具竞态已修、8 条只报 "component is not displayed"、4 条各一因） | 未见 XML + logcat + 真实 requestCount 之前，这一格**不许写成已修复** |
| §2i 新增 17 格 JVM 语义树/合同断言（48dp / role / selected / disabled / 标签 / 合同四行） | 本机已跑完并全绿 | **不需要等 CI 才算证据**，但 CI 的 verify 也要过（这些用例就在 `testDebugUnitTest` 里）。首次跑要在 CI 下载 android-all 与 native 运行时，**时长未实测** |
| androidTest 里那 3 格语义树断言（48dp / selected / contentDescription） | 本机无 system image | PanelHeader 那处的缺陷已在 `c927b2e` 修掉，所以这 3 格**预期转绿**；若真机上仍红，说明是真机差异（density/字体）而不是"没修"，别为此把断言调软 |
| P0-04 egress 六格里 4 格判不了 | 本机没有 tshark；脚本没有"没装就跳过"分支，装不上就是红 | CI `apt-get install tshark` 之后跑 `scripts/test_verify_network_egress.sh`，六格全判过 |
| Service destroy 那两格 | instrumentation 起不了悬浮窗/FGS，是 `Assume` 主动跳过 | 报告里算 skipped，**不算通过**；要真闭需要设备侧安排 |

## 4. 已知缺陷与我自报的错误（都按实写，别当成"生产问题"或"已解决"）

1. **我刚写错的数**：执行记录 §2f/§2g/§0/§3 里写 "VM 2732 行 / 长 81 行"。那是
   `41536ad`（观测补齐）**之前**量的，之后没重测；真值是 **2746 行 / 长 95 行**。
   本轮已在本文件与执行记录里更正。教训：写完一轮再抄数，或者干脆写命令。
   顺带一个被我叙述错的点：`ReplyStore` 那一刀其实是 **2651 → 2624（−27）**，
   是后面几步把编排留在 VM 才涨回去的——"搬 store 不降行数"这句在第一步并不成立，别照抄。
2. **`SingleOwnerContractTest` 拦住的第一版设计**：把 `scope.launch` 搬进策略类是错的，
   现在 backup 的调度留在仓库。下一格拆 category/document 时**同一条闸会同样拦你**，
   不要绕，照 §2h 的做法改设计。
3. **一个等价变异**：`backups.size > maxCount` 改成 `>=` 时 13 格全绿，
   因为进块后 `drop(7)` 对 7 份是空操作——两种写法行为相同。
   同一格换成真实错误（`drop(maxCount-1)`、排序方向反）立刻红两格。
   意思是：**"注了反例还全绿"有两种解释**，先判断这把尺能不能分辨，再决定补不补测试。
4. **`RewriteStore` 与协调器各持一半身份**：requestId 在 `ForegroundOperationCoordinator`
   和在途身份里都判一次。下一步若把租约直接注进 store 的 `isCurrentRequest`，
   要确认不会变成第二本账。
5. **搬家丢过的观测**（已补回）：五条链的 `… rejected (stale requestId)` 日志齐了，
   但只有 Reply 用 `onStaleEvent` 出口、其余用注入闭包——形状不统一是历史原因
   （Reply 的判据在 reducer 里，增量先缓冲后归约），别再"顺手统一"成一个返回值。
6. **本机验证环境的四个坑**（每次都咬过我）：`python3` 是 Store 占位符（exit 49 零输出）；
   GBK stdout 会把中文打乱码；Gradle 失败时不清理 `test-results/`（陈旧 XML 会冒充刚跑过）；
   脚本 `open(p,'w')`/`newline` 处理不当会整份翻 LF/CRLF 甚至把文件清成 0 字节还 `BUILD SUCCESSFUL`。
   规则：源码与文档改动一律用 Edit；真要脚本化就**字节读字节写 + 断言命中数 + 写完看 `wc -c`**。

## 5. 脚本索引（可执行件）

**入库、可直接跑**：

| 脚本 | 用途 | 怎么跑 |
|---|---|---|
| `scripts/check_lint_budget.sh` | 15 条规则的 lint 预算闸（抬高债/超预算都 FAIL，`--rewrite` 拒绝增大） | `PYTHON=/d/anaconda/python bash scripts/check_lint_budget.sh` |
| `scripts/package_deps_report.sh` | 实测跨层 import，输出可贴进棘轮基线 | `PYTHON=python bash scripts/package_deps_report.sh [--count]` |
| `scripts/test_verify_network_egress.sh` | 抓包判据 6 格正反自测（要 tshark；无 tshark 时 exit 2 = CANNOT-VERIFY，**不是通过**） | `bash scripts/test_verify_network_egress.sh` |
| `scripts/make_egress_fixtures.py` | 生成 5 个合成 pcap；`--inspect` 自带解析器复述内容 | `python scripts/make_egress_fixtures.py [--out DIR]` |
| `scripts/audit_cancellation.py` | 挂起函数取消保护审计（站点 167，NEEDS_REVIEW 必须 0） | `python scripts/audit_cancellation.py` |
| `scripts/strip_ticket_ids.py` | 生产代码/注释/字面量不许出现工单编号 | `python scripts/strip_ticket_ids.py --check` |
| `scripts/asset_hashes.sh` | prompt 资产 hash（LF 规范化，跨平台一致） | `bash scripts/asset_hashes.sh --check docs/prompt-assets.lock` |
| `scripts/run_ui_tests.sh` + `scripts/assert_artifacts.sh` | CI 的 instrumentation 与产物断言（min-tests 由源码 `@Test` 数推） | CI 里跑；本机没有 system image |

**没入库（`.gitignore` 的 `/_temp/`）——重跑不了，只留下"这些断言曾经被这样验证过"的记录**：
`_temp/mutate_counseling.py`、`_temp/mutate_counseling2.py`、`_temp/mutate_coordinator*.py`、
`_temp/mutate_rewrite.py`、`_temp/mutate_proactive_mode.py`、`_temp/mutate_backup.py`、
`_temp/coord_load_repro.sh`。
如果下一格还要用变异反证，**照抄其中任意一个脚本改**（它们的形状一致：
字节级改写 + 断言命中数 + 跑完逐字节复原并 `assert git status` 与跑前一致）。
要把这套验证变成仓库资产，就把通用版本提进 `scripts/` 并接进 CI——这属于没做的事。

## 6. 硬约束（改之前必须知道，违者就是没读指导书）

1. **不许改 prompt 内容**：`git diff --exit-code 286c9406..HEAD -- app/src/main/assets/engine` 必须零差异；
   只有 hash 工具能变。`core.md` 那类宪法文件更不许动。
2. **一个提交解决一个验收目标**，message 写用户行为不写实现细节；先写会红的回归测试。
3. **不许用源码 grep 代替 UI / 触摸 / 无障碍行为测试**；静态门禁只能标成"防回退"。
4. **不许用"脚本存在"代替产物**；不许新增 `|| true` / `continue-on-error` / 空产物告警。
5. 文档里的计数、hash、SHA 一律来自**当次命令输出**。
6. 不许为了行数做机械拆文件。
7. **发布要独立复核签字**，worker 不能自签 PASS。
8. 推送需要用户明确说「推送」。当前状态：本地领先 `4795471`，**远端仍是 `4795471`**。

## 7. 建议的下一个顺序（不跳步）

1. §5.3 再拆 1–2 格：catalog 剩下的**写侧**（create/delete/setActive/updateDisplayName/ensureInitial，
   枚举侧已在 `df1e802` 出去，直接往里加即可），再 **document**。形状照 `5a21ec2` 与 `df1e802`：
   给每格定义『它真正需要的最小能力接口』，由 `RepoStorage` 一个内部类去实现。
2. §6.4 拆 ResultArea 的浮层 state holder + modal host。**做之前先花 10 分钟**：
   照 `PanelHeaderTouchTargetsTest` 给 ResultArea 现有的可点控件补一组语义树用例（本机就能跑），
   这样"拆完没把热区/标签拆坏"是量出来的，不是看出来的。截图工具放在 §6.1–§6.4 之后接。
3. §5.2 第 6 步删 facade（这时调用面已经跟着 §6 的重排收敛过一轮）。
4. P1-05 按页搬字面量，每页把 `UiStringLiteralBudgetTest` 预算改小。
   **先补那把尺的盲区**（`Text(text = if (…) "中文" …)` 现在数不到，见 §2i 自报），
   否则搬掉的记不进账、留下的也数不全。
5. 期间任意时点用户说「推送」→ 立刻去闭 §3 那五行 CI 证据（那才是真正的 NO-GO 卡点）。
