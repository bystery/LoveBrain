# LoveBrain 交接 · 第 12 窗口开工包（2026-09-26）

这份文档是**自包含的**：读完它 + 照第 2 节的命令跑一遍，就能直接开工，不需要先读完
前面十一个窗口的账本。所有数字都是 2026-09-26 这一轮实测出来的，**注明了是哪条命令的输出**；
凡本机量不到的，一律标"只能等 CI/设备"，不当已解决。

- 本地 HEAD：**第一笔已落 `aa29950`**（A1 那把新尺，纯测试码）；开工时是 `9fc81dc`（这份文档本身），
  开工前远端 `main` = `85d2d42`，本窗口至今**没推过**。
- 那份"还差多少活"的判断在第 3 节，**先看 3.A 和 3.B 就够了**。

---

## 0. 现在站在哪（一句话 + 硬读数）

**发布判定仍是 NO-GO**，但卡点换了：以前是"verify 自己红、后面 12 步被 skip，什么都没跑到"；
现在是"门禁全绿、该跑的都在跑，**剩下的是设备侧真失败 + 架构最大那块没拆 + 视觉证据没做**"。

同一 SHA（`85d2d42`，run 36214822274）的 CI 实况：

| job | 结论 | 读数 |
|---|---|---|
| `verify` | **success** | 33 步全跑、**0 skipped**（含 R8 release、APK metadata、SBOM、取消审计、工单号、费用 dry-run、出口七格自测、三个 Upload） |
| `ui-test` | **failure** | `tests=45 failures=8 errors=0 skipped=2 suites=1`（45 声明 − 0 @Ignore = 45 执行 ⇒ 报告数与源码数一致） |
| `upgrade-test` | skipped | `needs: [verify, ui-test]`，ui-test 红 ⇒ 不跑 ⇒ **v1.3.1 覆盖安装零证据** |
| 产物 | 12 件 | `unsigned-r8-check-apk 2518724B / apk-metadata 725B / sbom-and-licenses 4808B / cancellation-audit 2745B / egress-selftest-evidence 6031B / lint-report 57041B / unit-test-report 254450B / suggest-baseline-dry-run 2181B / logcat 149380B / instrumentation-test-report 16619B / test-xml-report 2967B / ui-test-evidence 610077B` |

本机基线（`./gradlew :app:testDebugUnitTest --rerun` + `scripts/assert_artifacts.sh`）：
**189 套件 / 1409 单测 / 0 失败 / 0 错误 / 0 跳过**，189 份 XML 同一批（跨度 0.04 秒）。
CI 的产物门自报同一个数 ⇒ 这一轮两侧没漂。

---

## 1. 原始指导文档在哪（三份，两份没入库）

| 文件 | 作用 | git 状态 |
|---|---|---|
| `LoveBrain_Three_Phase_Reaudit_and_Six_Principles_UI_Architecture_Guide_c0ff0415_2026-09-24.md` | **就是这份要照着干的指导书**（664 行） | **未跟踪 `??`** |
| `LoveBrain_Comprehensive_Reaudit_286c9406_2026-09-23.md` | 上一轮综合复核报告 | 未跟踪 `??` |
| `LoveBrain_Guide_Item_by_Item_Verification_2026-09-24.md` | 逐条对照表（账本，5219 行，最新一节「追加五十九」在 5129 行起） | 已入库 |
| `LoveBrain_Handover_Next_Window_2026-09-25.md` | 十一轮的开工单（§0.44 最新、§1 起手命令、§6 坑表 1–123） | 已入库 |

⚠ **前两份至今没入 git**：谁 clone 这个仓库都拿不到它们，只剩工作目录里这一份。要不要入库是**用户的决定**（见第 8 节），
所以本节把指导书的结构抄成行号地图，让你不用那份文件也知道每条要求在哪：

```text
§2.1 第一阶段复核（主动发回退交互合同表）………… 123
§2.2 第二阶段复核（并发/状态/知识库/工程结构）… 150
§2.3 第三阶段复核（发布/隐私/无障碍/证据）……… 166
§3.1 P0-01 CI 总体红灯 …………………………… 186
     P0-02 "点击生成回复不崩溃"仍未被证明 …… 200   ← 十个场景那张表
     P0-03 未来 schema 的只读保护可被绕过 …… 217
     P0-04 零遥测验证脚本有假阴性 ……………… 246
     P0-05 WAL 的承诺高于实现 …………………… 268
§3.2 P1-01 大文件（18 个 >500 行）……………… 285
     P1-02 语义树断言（不许源码 grep 顶替）… 304
     P1-03 GenerationActionButton modifier 链  310
     P1-04 Koin 双实例 / 死注册 ………………… 321
     P1-05 文案未收口（Text 101 / cd 10）…… 332
§3.3 P2 一致性与文档债（五条）………………… 341
§5.1 先做包边界，不急着多模块化 ……………… 385
§5.2 迁到 feature store（1–5 各 store，6 删 facade）428
§5.3 拆 KnowledgeRepository（七个名字）……… 454
§6.1 UI 语法：11 颗组件表 + 禁止单页异形 …… 472
§6.2 首页四段信息架构 …………………………… 492
§6.3 页面状态统一（Loading/Content/Empty/Error）503
§6.4 悬浮面板交互（ResultArea 不承载全部浮层）518
§6.5 无障碍与视觉门禁（8 条必须自动化）…… 527
§7 三步执行计划（第一步 544 / 第二步 566 / 第三步 582，各带"完成定义"）
§9 给 worker 的执行规则（9 条）……………… 625
§10 独立放行意见（NO-GO 的六条理由）……… 653
```

---

## 2. 起手必查（照抄，别凭记忆）

```bash
cd /d/LoveBrain
git log --oneline -3 && git status --short          # 工作区应只剩那两份未跟踪 md
gh run list --branch main --limit 3
gh run view <run-id> --json conclusion,jobs --jq '.jobs[]|"\(.name)=\(.conclusion)"'
# ⚠ 这台机器上本副本【没配 remote.origin.fetch】：git fetch 安静成功但不写 refs/remotes/origin/*，
#   `git log origin/main..HEAD` 会报 unknown revision、`git status` 不显示 ahead/behind。
#   要比领先几条只能：git ls-remote origin main  → 再 git rev-list --count <那个SHA>..HEAD
#   修它是一行 git config —— 属于改用户 git 配置，没点头别动。

./gradlew :app:testDebugUnitTest --rerun            # 绝不接管道；数格子必须 --rerun
bash scripts/assert_artifacts.sh --label unit \
  --xml-dir app/build/test-results/testDebugUnitTest \
  --html-dir app/build/reports/tests/testDebugUnitTest
bash _temp/run_gates109.sh                          # 全套 12 步门禁，每步记 RC + 字节数（换格子要换死导入清单）
```

Windows 上必踩的六条（原来四条，详见交接单 §6 坑表 107 / 116–120；本窗口又补了 121–123 与下面两条）：
`PYTHON=` 要写成 `bash -c 'PYTHON=/d/anaconda/python bash …'`，**别用 `env VAR=… cmd`**（PATH 上有个空壳会吞参数还返回 0）；
`python3` 是商店占位符（返回 49），一律用 `/d/anaconda/python`；
Python 脚本走 stdout 要 `PYTHONIOENCODING=utf-8`（默认 GBK 会因中文崩）；
写盘脚本先归一成 LF、按原口径写回、**写完回读比对**。
本窗口再补两条同一族的：**`python - <<'PY'` 连脚本源码都按 ANSI 码页解码**——
脚本里写中文注释/中文替换文本会当场 `SyntaxError: invalid character '…'`（`PYTHONIOENCODING` 管不到它），
正解是把中文内容用写文件工具落成 UTF-8 件，再让一个**纯 ASCII** 的脚本按路径读它；
以及**后台命令的重定向前要先 `mkdir -p` 目标目录**，否则整条命令连脚本都没启动（RC=1、只有一句 No such file）。

---

## 3. 还差什么工作（按"能不能马上动"分四组）

### 3.A 现在就能动、本机可验（建议按顺序吃）

**A1 ｜ 给 P0-03 的第二半句装一把会红的尺**（指导书 217 行）
- 要求原文：**"所有 mutation 只能从 `KnowledgeTx` 取得安全路径与原子写能力；Repository 内禁止出现第二条 `atomicWriteText` 公共链。"**
- 今天实测：**后半句做到了**——只有一条落盘出口（`ReadOnlySchemaWriteGateTest` 钉 `rawAtomicWriteText` 定义 1 / 调用 1 / `FileOutputStream` 1），只读库在 `KnowledgeRepository.kt:325-337` 统一拒。
  **前半句没做到**：`KnowledgeRepository.kt` 里 34 处 `atomicWriteText` 调用，只有 4 处（:473 :483 :595 :606）在 `KnowledgeTx` 之内，**其余 30 处是裸写**——
  `ensureInitialKnowledgeBase` 13 处（:684-703）、`create` 13 处（:825-842）、`ensureKbFilesCompleteUnlocked` 2 处（:750 :757）、`RepoStorage.atomicWrite/guardedWrite` 2 处（:166 :173）。
  **而且没有任何一格在数这 30 处**（`data/StorageBoundaryOwnershipTest.kt` 数的是 `File(File(knowledgeRoot,` 拼接，owners 确为空集）。
- 第一步：**先数、先红，别先改**。新写一格静态尺：扫 `codeOf(KnowledgeRepository.kt)` 里 `atomicWriteText(` 的调用点，按"是否在 `KnowledgeTx` 作用域内"分两堆，登记 `tx 内=4 / 裸=30` 两个数，判 `==`（还债就要回来改小），再用注入反例证明它有牙。
- 验收：新格 + 变异两发（把一处裸写改成 tx 内 → 表必须红；把 tx 内那 4 处撤掉 → 必须红）。
- ⚠ 我这轮差点把这条当成"已清零"——原因见第 5 节。别信"读/写边界已清零"这句话，它说的是拼接那把尺。
- ✅ **本窗口已收（`aa29950`，只造尺没改生产码）**：新格 `data/KnowledgeTxMutationEntryTest.kt`（8 格）
  登记 **总数 34 = 唯一写链 4 + 裸写 30**，并另钉逐作用域明细表、定义必须留 `private`、
  不许出现 `::atomicWriteText` 引用、`data/` 别家不许调用它。
  变异反证**跑了八发**（开工单要求的两发是 M1/M2），全符合预期；
  最值钱的一发是 M1：**把一处裸写挪进写链，总数一点不动**，只钉总数的棘轮对"搬家"是瞎的。
  ⚠ 口径修正：那 4 处的"在 `KnowledgeTx` 之内"在这把尺里定义为"落在四个锁内写核里"
  （`KnowledgeTx` 类体自己不直接调 `atomicWriteText`，它调那四个核）——细节与读数在**账本 §59**。
  **剩下的活是还这 30 处**（建议从 `ensureKbFilesCompleteUnlocked` 那 2 处开手），每还一批回来把三个数改小。

**A2 ｜ §6.5 的极端值矩阵**（指导书 527 行第 6 条："长 Provider 名、超长模型名、￥9999.999、100000 次生成"）
- 实测：只有 `ProviderSectionSemanticsTest.kt` 用了长供应商名；**全仓没有任何一格**含 `9999` 或 `100000`（`grep -rl "9999\|100000" app/src/test app/src/androidTest` 空）。
- 第一步：在 `LbMetricGrid` / `UsageDetail` / 首页统计那三处挂 JVM 格子，喂 ¥9999.999 与 100000，用 `SemanticsProbe` 判"没被裁（省略号可用『字宽==盒宽−2×内边距』判出来）+ 热区没被挤小"。
- 验收：每格先红后绿；矩阵跑 320/360/412/600 × 1.0/1.3/2.0 时**一台仪器一个用例只能 `setContent` 一次**（坑表 3）。

**A3 ｜ 深色模式的口径要么写死要么做出来**（指导书 527 行第 5 条）
- 实测：`values/themes.xml:3` 是 `Theme.Material.Light.NoActionBar`；`app/src/main/res/` 下**没有任何 night 目录**；没有门禁或文档明写"产品暂不支持深色"。
- 第一步：二选一并写进 `docs/` + 加一格静态守卫（例如"不许出现 `values-night/`，出现即红"或反之）。**别做半套主题**。

**A4 ｜ P1-01 缺的那道"新增代码 >800 行硬门禁"**（指导书 285 行）
- 实测：`grep -lE "800|500" scripts/*.sh` **空** ⇒ 指导书建议的"500 设 review warning、800 对新增代码硬门禁"**一行都没写**；现存 >500 行 17 个、>800 行 10 个（见第 6 节）。
- 第一步：新脚本 + 门禁（只挡"新增/净增长"，对存量走逐步清零），照 lint 预算那套"等号证人 + 表不许虚高"的写法。

**A5 ｜ §57.3 排定的第 2 格：三处整卡与两颗行内按钮的 role**（任务 #40，指导书 472/527）
- 实测读数（账本 §57）：知识库整卡（激活）`312x136dp @(24,89) role=无`、供应商整卡（折叠）`312x53dp role=无`、知识库行内「编辑」「导出」各 `50x48dp role=无`。
- 判据要点：**认整卡要按"这一屏最宽的可点节点"认**（卡片合并后的第一个文本不是标题——知识库那颗念「阶段： ｜ 已对话 0 轮」）；
  激活/选中是互斥可重复 ⇒ 候选 `Role.Tab` + `selected`；展开/折叠不是选中 ⇒ 候选 `Role.Button` + `stateDescription`（资源 `state_expanded`/`state_collapsed` 已在）。**先量再定，别照抄。**

**A6 ｜ 反馈案例页的另外三档**（指导书 503 行 §6.3；账本 §58.9）
- 这一页刚搬进 `LbScreenScaffold`/`LbTopBar` 并补齐六颗芯片热区与九颗 role（`6f0bc6e`），但**只量了 Content 档**；`Loading/Error/Empty` 三档一颗控件都没读过。
- 第一步：把 `FeedbackCasesSemanticsTest` 加三档夹具（`feedbackLoading=true` / `feedbackError=MutableStateFlow("…")` / 案例为空），照 `PanelErrorStatesSemanticsTest` 的形状判"这一档里还剩几颗动作、那颗能不能按"。
- ⚠ 挂档时注意：**"挂的是哪一档"自己要有证人**（账本 §51.1：`when` 里 `Success` 排在 `!providerReady` 前面，挂错档照样全绿）。

**A7 ｜ 两笔纯文档假账（十分钟的活，但都是"注释比实现新"）**
- `PackageDependencyTest.kt:14-15` 仍写"真实存量是 **15 条越界 import，分布在 11 个文件**"，而它自己的基线是 **6 条 / 6 文件**（`assertEquals(6, …)`）。
- `LoveBrain_Rework_Acceptance_2026-09-24.md:76` 仍写"漂移只记诊断、**仍用冻结 prompt**"、`:81` 仍写"**所有写路径统一拒**"——指导书 §3.3（341 行）点名的就是这两句与实现不符；A1 修完之后第二句才真的成立，**别先改字**。
  ⚠ 第一句**本窗口已对着实现核过**：`GenerationEngine.kt:256-264` 那里注释自己写着
  "以前这行注释写的是'仍用冻结时的 prompt 文本'，那是没有实现的说法"——资产 hash 漂移只 `L.w` 一句，
  随后 `val user = buildResult.prompt` 用的就是**现读资产拼出来的**文本。所以"只记诊断"对、"仍用冻结 prompt"错。

**A8 ｜ 本窗口新登记的行为空白（账本 §59.6）：只读库走"补缺文件"那条路径没量过**
- `ReadOnlySchemaWriteGateTest` 那份 24 条公开 mutation 的目录树比对**不含建库两条路径**
  （`create` / `ensureInitialKnowledgeBase`）。而 `ensureInitialKnowledgeBase` 在"已有库"分支里
  会调 `ensureKbFilesCompleteUnlocked(kb.name)`（`KnowledgeRepository.kt:652-655`），那个 kb 完全可能是
  schema 过新的只读库，`ensureKbFilesCompleteUnlocked` 里两处是**裸写**（A1 那把尺登记的 2 处）。
- 按代码读它会被出口判定（`atomicWriteText` 里按 `kbOwning` 统一拒）挡下，但**这句只是读出来的形状**：
  第一步是给那格补一档夹具——"未来库缺 `memory/lessons.md`"，跑 `ensureInitialKnowledgeBase`
  后判"文件没被创建 + 目录树逐字节不变"。先红（现在多半会创建出来）再决定要不要改代码。

### 3.B 大块，要拆成好几格（别一口气做）

**B1 ｜ §5.2 第 6 步：删掉 `LoveBrainViewModel` 这个 facade**（指导书 428 行；第 1–5 步**已完成**）
- 实测：五个 store 都在（`feature/reply/ReplyStore.kt:34`、`suggest/SuggestStore.kt:30`、`proactive/ProactiveStore.kt:31`、`counseling/CounselingStore.kt:32`、`rewrite/RewriteStore.kt:34`）；
  但 `viewmodel/LoveBrainViewModel.kt` **2723 行**（`wc -l`）、113 条 `fun`、对外 51 条 `val …: StateFlow`、私有 33 条 `MutableStateFlow`，被 **48 个 .kt 引用**（main 18 / test 27 / androidTest 3），UI 侧仍在直接用（`ui/panel/LoveBrainPanelScreen.kt`、`service/FloatingService.kt`）。
  ⇒ "facade 只组合几个只读 StateFlow"这一条**远未达成**，第 6 步 0%。
- 建议切法：①先加一格"调用点清单证人"（数出 main 侧 18 个引用文件与它们读的是哪几条流）；②按 feature 一批批把调用点改成直接用 store（一批=一个 commit=一个验收目标）；③每批之后清单必须变小（判 `==`）；④最后 facade 归零才谈删除。
- 这是指导书 §10"不宣称全面完成"里最大的一块，也是"新增 feature 不修改已有 reducer"那条完成定义的前提。

**B2 ｜ ResultArea 的体量**（指导书 518 行 §6.4 那半句的下游）
- 浮层那五刀已走完（裸 `Dialog(`/`AlertDialog(` 在 `ui/panel/` 实扫为 0，账本 §29–§33），但**文件本身仍 1286 行**，仍是结果区 + 状态 + 工具入口挤在一起。
- 先量再拆：`ResultAreaTouchTargetsTest`/`PanelErrorStatesSemanticsTest` 已把这块接进 JVM 仪器，拆之前那些格子是唯一的现形证据。

### 3.C 本机判不了 —— 只能等 CI 或真机（**别用"脚本存在"顶替**）

1. **P0-02 那 8 格红**（指导书 200 行）：`OverlayGenerateSmokeTest` 7 格 + `ReplyPrimaryActionsTest.generating_showsProductionLoadingStopAffordanceAndCalls…` 1 格。
   指导书原话："没有同一 SHA 的 XML、logcat 和真实 requestCount，不得写『生成崩溃已修复』。"XML/logcat 产物现在有（`logcat 149380B`、`instrumentation-test-report 16619B`），**但那 8 格仍红 ⇒ 这条不成立**。
2. **v1.3.1 覆盖安装证据**（§2.3 表 + 第一步完成定义）：`upgrade-test` 被 `needs` 挡着不跑。要让 `ui-test` 先绿。
3. **真机 pcap 抓包**（P0-04 尾巴）：判据侧的七格自测已全过，缺"真抓一份包"。README 的诚实措辞**保持到那一刻**（现在是对的，见 `README.md:166`）。
4. **`--capture-on-device` 那段脚本**：从没在任何设备跑到过；读代码看到 `verify_network_egress.sh` 现场抓包时先设 tcpdump 清理 trap、后面解析段又设 `rm -rf $WORK` 的 EXIT trap ⇒ **前一条被顶掉**（死在 pull 之前收不掉设备上的 tcpdump）。**这是读出来的形状，不是量到的行为**，接上设备时顺手验，别当已修（账本 §58.9）。
5. **insets / `systemBarsPadding` 取值**：全仓 12 个内容根只有 2 个加过，且没有 Activity 做 edge-to-edge，Robolectric 给的 insets 恒 0 ⇒ 只能真机判。决定已收在 `LbScreenScaffold(handlesSystemBarInsets=…)` 一个参数上。
6. **Macrobenchmark / 性能**（§2.3 表 FAIL 那行）：无设备数据、无 baseline profile、无帧耗时证据。

### 3.D 要人给口径，worker 不许自签

- **"⋯ 菜单只放低频次操作"里"低频"没有定义**（指导书 518 行）⇒ 要产品口径。
- 首页英文标签「New knowledge base」被内边距挤成省略号（自然宽 174 vs 槽位 152）：**改英文措辞还是把那一行拆成两排**，都是产品判断（账本 §52/§57）。
- **要不要引入 screenshot baseline**、用 `roborazzi` 还是 `paparazzi`（指导书 §8 明写二选一，别同时引入两套）。**这是"还差多少视觉证据"的开关**，不定就一直欠着。
- `LbButtonTone` 缺"进行中转灰"那一档；§6.1 那张表缺"次级文字动作 / 破坏性文字动作"两行 ⇒ `LbTextActionTone` 只有 Accent/Muted（账本 §50.7）。

---

## 4. 这一轮我自己翻出来的两笔假账（别重复踩）

1. **P0-03 我原以为"读/写边界已清零"** —— 那是另一把尺（`File(File(knowledgeRoot,` 拼接，owners 确实空集）。指导书要的第二半句"mutation 只能从 `KnowledgeTx` 取"完全没被任何尺盯着，实测 30 处裸写。⇒ **凡是"某条已清零"，回去把那句要求原文找出来逐字对，别对工具名。**
2. **`PackageDependencyTest` 的注释比基线旧**（15/11 vs 6/6）。⇒ 还了债要顺手把"注释里的存量叙述"一起扫，这是本仓库第 N 次撞同一族。

---

## 5. 硬约束（一条没变，指导书 §9 + 用户定的）

不许改 prompt 内容（`git diff --exit-code 286c9406..HEAD -- app/src/main/assets/engine` 必须零差异）；
一个提交一个验收目标、message 写用户行为；**先写会红的回归测试，新断言必须被坏实现打破过**（变异注入 + 还原后逐字节核对）；
**不许用源码 grep 顶替 UI/触摸/无障碍测试**（UI 证据只能来自 `core/testing/` 那台语义树仪器）；
不许用"脚本已提供"代替产物；不许新增 `|| true` / `continue-on-error`；
**不许为了绿调软断言或抬预算**（lint 预算、字面量预算、三把 :490 尺的表都是"只许往下 + 等号证人"）；
文档里的行数、测试数、hash、SHA 全部来自当次命令输出；不为行数机械拆文件；
**绝不删除文件**，退役只 rename 进 `_temp/` 或 `_archive/`（`_temp/` 现在收着 **2528 个**探针、收档件与读数，
`find _temp -type f | wc -l` 实数）；
派生物不许回扫当输入、批量工具要幂等；改严一处要回扫同族（含注释/KDoc）；
**worker 不自签发布**；**推送需要用户明确说「推送」**，推完要逐 job 读同一 SHA 的根因。

---

## 6. 最新一轮读数快照（数只在这里给，别抄进生产代码）

- 单测：**1409 / 189 套件 / 0 失败 / 0 错误 / 0 跳过**（`--rerun`，XML 同一批 0.04s）。CI 产物门自报同一个数。
  ⚠ **本窗口第一笔之后已涨到 1417 / 190**（+8 格 +1 套件 = 新尺 `KnowledgeTxMutationEntryTest`，`aa29950`）；
  上面那两个数是"开工时"的基线，别再拿它当现在值。
- 门禁 12 步全 rc=0（除 `prompt` 合法 0 字节）：lint `measured_issues=67 measured_rules=15 gated_issues=66 gated_rules=14 advisory_issues=1`；
  预算自测 27 格全对；跨层依赖 6；工单号 PASS；取消审计 165 站（PROTECTED 54 / WAIVED 2 / SUSPEND-FREE 109 / NEEDS_REVIEW 0）；
  prompt 资产锁 OK（`6dcde732…`）；`assembleAndroidTest` rc=0；被改文件死导入 0 条。
- 字面量四栏（剥注释口径）：**TEXT 174 / DESC 10 / STATE 0 / COMPONENT 76**。
- §6.1 :490 那三把尺：**表面色 44 处/17 文件；自造品牌底可点控件 23 处/10 文件；经 containerColor 2 处/2 文件**（旧数 25/19/5 是换口径前的下界，**不许拿来比涨跌**）。
- 大文件实测：**>500 行 17 个；>800 行 10 个** —— 2723 `viewmodel/LoveBrainViewModel.kt`、1793 `data/KnowledgeRepository.kt`、1286 `ui/panel/reply/ResultArea.kt`、1155 `service/FloatingService.kt`、1114 `ui/panel/LoveBrainPanelScreen.kt`、1114 `data/DeepSeekRepository.kt`、1008 `domain/TopicRecorder.kt`、974 `domain/PromptBuilder.kt`、944 `ui/panel/SuggestPanel.kt`、937 `ui/KnowledgeBaseActivity.kt`。
- 已完成、不用再做的（免得重跑）：P0-04 出口判据（七格自测 + verify 绿）、P0-05 WAL（`RoundCommitJournal.kt:317` 起在 `txMutex.withLock` 内重读、已提交时返回 `CommitOutcome.AlreadyCommitted`（类型 :229），并发格 `RoundCommitJournalTest.kt:327`，水位故障注入格 :270，口径已改 at-least-once :29）、P1-02 语义树仪器、P1-03（`GenerationActionButton` 已退役、四态各一条链）、P1-04（`AppModule.kt:51/:55` 容器注入 + `AppModuleGraphTest:148` 的 `assertSame`）、P2 fingerprint（已删，真 SHA-256 接在 `GenerationEngine`/`LoveBrainViewModel:640`）。

---

## 7. 交付清单模板（指导书 §9 那份，收口时逐条填）

```text
[ ] 精确 SHA：
[ ] compare base...head：
[ ] verify / ui-test / upgrade-test 三个 URL 与结论：
[ ] unit / instrumentation 数量（本机与 CI 各一份，注明是否相等）：
[ ] AndroidTest 编译证据：
[ ] screenshot / logcat / upgrade / APK / SBOM 产物与字节数：
[ ] prompt 目录零 diff + canonical lock 通过：
[ ] P0/P1/P2 每项对应 commit、测试、产物：
[ ] 未完成项单独列出，不混入"已完成"：
```

## 8. 等用户表态的三件事（别自己拍板）

1. 仓库根那两份未跟踪的输入文档（指导书 + 综合复核报告）要不要入 git。
2. 历史里编译不过的中间提交 `1167c34` 要不要重写。
3. 这台机器的 `git config remote.origin.fetch` 缺失要不要补（一行命令，属于改 git 配置）。

---

**上一轮的详细过程在这里，不必先读**：账本「追加五十七」「追加五十八」（`LoveBrain_Guide_Item_by_Item_Verification_2026-09-24.md` 4915 行起、4977 行起），
交接单 §0.42 / §0.43 与坑表 114–120（`LoveBrain_Handover_Next_Window_2026-09-25.md`）。
本轮新造的探针与差分桩都留在 `_temp/`：`probe42.py`（七发变异驱动）、`stub_tshark_from_fixtures.py` + `stub_tshark.sh`（出口判据差分桩）、
`probe_egress_no_guard.sh`、`dupcase_probe.sh`、`run_gates108.sh`（12 步门禁）、收档的 `gates107/ZzRoleGapProbeTest.kt.retired-2026-09-26` 与读数 `gates107/role_gap_probe.txt`。

**本窗口（第 12 格）已经做掉的在这里，别重复劳动**：账本「追加五十九」（同一份账本 5129 行起）——
P0-03 前半句那把新尺的口径、34/4/30 三张读数表、八发变异反证的逐发实读、
以及两处"绿着的错表"是怎么被仪器自己造出来的。
交接单 §0.44 与坑表 121–123（同一份开工单）。
本窗口新增的留档在 `_temp/`：`a1_mutate_probe.py`（八发变异驱动，跑前先把原件与 sha256 清单落盘、
每发跑完立刻还原并逐字节核对）、`a1_mutate/readings.txt`（八发实读 + 收尾核对）、
`run_gates109.sh`（12 步门禁，死导入清单换成本格那个新测试文件）、`a1_measure1.log`/`a1_measure2.log`/`a1_green1.log`
（造尺过程中"先跑红拿实测数"那三次读数）。
