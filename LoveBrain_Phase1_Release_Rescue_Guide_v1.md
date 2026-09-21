# LoveBrain 第一阶段：发布止血、首页重构与生成成本修复指导书

> 适用仓库：`bystery/LoveBrain`  
> 审查基线：`main@30a494628579ecb4c2e35d70519fb6f5a7eb7729`  
> 当前公开版本：`v1.3.3`（2026-09-21 发布）  
> 本阶段性质：**发布事故止血 + 用户主链修复**，不是再堆一批功能，也不是靠改文案伪装重构。

---

## 0. 先给结论

当前版本不能按“公开稳定版”验收。

CI 为绿色只能证明当前 HEAD 可以通过 JVM 单测、Lint、Release 构建和现有 Compose instrumentation；它不能反驳真实用户在悬浮窗里点击“生成回复”直接崩溃。仓库现有 `androidTest` 只有 `ResultAreaInteractionTest.kt` 与 `ResultAreaVisualRegressionTest.kt` 两个文件，没有覆盖“真实 Service 宿主 + Overlay + ViewModel + Provider + 生成按钮”的整条链路。

本轮反馈中的 7 项，源码核查结果如下。

| 用户反馈 | 当前源码事实 | 判定 |
|---|---|---|
| “暂时隐藏”另起一行，位置难看 | `SetupActivity.kt:409-447` 在 Hero 主按钮下面单独放 `Spacer + TextButton` | 属实，且上一版指导书已经明确要求不要这样做 |
| 首页暴露 `LoveBrain(6abf)` 一类内部版本 | `SetupActivity.kt:480-488` 直接显示 `VERSION_NAME + GIT_SHA + BUILD_TYPE` | 属实。追溯信息不应放普通用户首页 |
| 首页顶部缺少完整信息层级 | `SetupScreen` 没有 App Top Bar；`HomeTabContent` 直接从 Hero 卡开始 | 属实。现在更像调试/设置集合，不像产品首页 |
| 使用统计难看 | `StatsSection` 用一张大卡纵向堆 6 行文本和解释 | 属实。信息密度低、占屏高、层级弱 |
| 供应商、快捷功能、反馈、捕获、悬浮窗各用一种格式 | 五块分别手写不同 Card/Row/折叠/页面逻辑，没有统一首页组件语法 | 属实 |
| 反馈案例直接显示在首页下面 | `F15FeedbackCasePage` 虽写着“全屏”，却在首页可滚动 `Column` 内通过 `if` 插入 | 属实。这不是根页面导航，`fillMaxSize` 也不等于真正的新页面 |
| 点击“生成回复”仍崩溃 | 用户已在公开 APK 上复现；仓库没有这条真实主链的回归测试 | P0。必须以堆栈定位根因，不能用 CI/测试数量否认 |
| 锦囊输入很多却只出 3 条 | `suggest.md` 明确写死“最多 3 条”和“输出 1-3 条”；User Prompt 仍允许装配最高约 9000 字符的知识子集 | 属实，而且是上一版产品定义直接造成的成本/价值失衡 |

另外，所谓“全面复核与修复”提交 `6af923c` 把 `F01-F21` 写进提交信息并声称 `597/597`，但当前真实使用仍崩溃、首页仍违背原要求。因此本阶段不接受“注释写了修复”“测试数很多”“CI 绿”作为完成证据。

---

## 1. 三阶段总路线：这次只执行第一阶段

### 第一阶段：发布止血与主链恢复（本指导书）

目标是让用户能够顺畅完成：打开 App → 启动/恢复悬浮窗 → 捕获或输入消息 → 点击生成 → 得到结果；同时把首页重建成统一产品界面，把“今日锦囊”的成本与产出改到值得使用。

本阶段必须修：

1. “生成回复”崩溃及其完整请求生命周期。
2. 首页信息架构与统一视觉/交互组件。
3. 内部版本信息位置。
4. 反馈案例真正独立成页。
5. 使用统计压缩与统一展示。
6. 模型供应商、消息捕获、悬浮窗入口统一。
7. 今日锦囊的输入预算、默认产出、缓存、成本可见性。
8. 与上述改动直接相关的重复实现、根目录垃圾和误导性注释。

### 第二阶段：核心架构债务

集中处理 `LoveBrainViewModel`、`KnowledgeRepository`、`TopicRecorder`、`PromptBuilder` 的职责边界、单向状态流、事务/迁移、知识污染与可恢复性。第一阶段不得借此发起数日“大重构”，但也不能继续复制新旁路。

### 第三阶段：全产品验收与发布工程

覆盖所有页面、所有空/错/加载/升级状态、可访问性、性能、隐私、发布签名、升级安装、README/演示资料和稳定版发布门禁。只有第三阶段结束后才能再称“整个项目全面合格”。

---

## 2. 本阶段必须遵守的软件工程六原则

这是执行规则，不是写在总结里的口号。

1. **真实代码和真实状态流优先。** 用户看到什么、点下去走哪条调用链、设备实际发生什么，优先级高于提交信息、注释和 worker 自述。
2. **高价值日常 Bug 优先。** 先修直接生成崩溃、主页混乱、锦囊浪费；不先做边角动画、命名美化或无关架构实验。
3. **最小充分改动。** 不追求一次性理论完美，也不靠把大文件机械切成很多小文件制造“重构感”。每次改动必须直接服务用户任务或消除一个明确风险。
4. **小问题合并处理。** 不影响第一阶段主链的小问题进入后续清单，禁止一个小点一个提交、来回让用户验货。
5. **按改动范围分层测试。** 纯逻辑跑单测；Compose 跑组件与导航测试；Overlay/权限/升级必须上模拟器或真机；不能每个小改都只跑全量，也不能只跑单测。
6. **未经实际验证不得宣称通过。** worker 只能提交证据，不能自行宣布 PASS；最终验收由独立检查者按 HEAD、APK、实机行为和日志复核。

同时执行两条横向约束：

- **DRY / 单一事实源：** 同一业务规则、请求状态、错误映射、首页样式只保留一个实现。新增统一实现时，同提交删除或迁移旧旁路，禁止“双轨兼容以后再删”。
- **质量责任：** 技术责任要有自测、日志、指标；协作责任要有接口、文档、迁移说明；判断责任要对是否可发布签字，不能把“编译通过”偷换成“可发布”。

---

## 3. 开工前的发布止血

### 3.1 冻结错误的发布节奏

- 以 `30a494628579ecb4c2e35d70519fb6f5a7eb7729` 记录本阶段基线。
- 新建阶段分支，例如 `fix/phase1-release-rescue`；不要在 `main` 上边改边发版。
- 在第一阶段门禁通过前，不创建 `v1.3.4`，不再发布“全面修复”标题。
- `v1.3.3` 已被用户证实存在 P0；应在 Release 说明中明确“已知生成崩溃，暂停推荐下载”或标记为预发布。不能把未验证的 `v1.3.2` 自动宣传成稳定回退版。

### 3.2 固定证据身份

每份测试报告必须写：

- commit SHA；
- APK 的 SHA-256；
- Debug/Release、是否 R8；
- 签名身份；
- Android 版本、设备/模拟器型号；
- 新装还是从 v1.3.2/v1.3.3 覆盖升级；
- Provider 类型、模型名、是否启用 thinking；
- 知识库是空库、普通库还是旧版本升级库。

不要再把 Git SHA/build type 长期放在首页。追溯能力应保留在“关于与诊断”页面，Debug 构建可显示完整信息，Release 普通界面只显示 `v1.3.4`。

---

## 4. 工作包 A（P0）：彻底修复“生成回复”崩溃

### A0. 先复现，不向用户甩锅

worker 自行构建当前基线 APK，并至少跑以下矩阵：

| 维度 | 用例 |
|---|---|
| 安装状态 | 全新安装；v1.3.2 覆盖升级；保留 v1.3.3 数据覆盖升级 |
| 构建 | Debug；真实可安装 Release/R8 |
| 知识库 | 无知识库；新空库；普通库；旧版有历史数据的库 |
| Provider | 正常；Key 缺失；URL 错误；模型名错误；thinking 不支持 |
| 网络 | 正常；断网；超时；429；5xx；返回空/截断/非法 JSON |
| 操作 | 单击生成；快速连点；准备中停止；流式中停止；失败后重试；切库后立即生成 |

用 `requestId` 串起日志节点：`CLICK → PREPARE → PROMPT_READY → REQUEST_START → FIRST_CHUNK → PARSE → RESULT/ERROR → CLEANUP`。日志只记状态、时长、长度、错误类型和脱敏 Provider 身份，禁止打印 API Key、完整 Prompt 或真实聊天。

必须保存：`FATAL EXCEPTION`、首个 LoveBrain 应用栈帧、完整 cause chain。没有拿到堆栈前，不得把某个猜测写成最终根因。

### A1. 修正请求生命周期，而不是再加一层 catch

当前 `LoveBrainViewModel.generate()` 在准备协程里维护 `_isPreparing`、`generateJob`，再调用 `GenerationEngine.generate()`；Panel UI 只订阅 `isGenerating`，没有订阅准备态。这造成“已经点击但按钮仍像可点”、状态所有权分散，也让异常清理难以证明。

改造要求：

1. 建立唯一的 `ReplyRequestState`：`Idle / Preparing / Streaming / Success / RecoverableError`，每个活跃状态携带 `requestId`。现有 `isPreparing`、`isGenerating`、`isGeneratingCore` 如仍需给 UI 使用，必须从该状态派生，不能继续作为多处可写真源。
2. 整个事务边界覆盖：冻结消息与 KB → 读取意图/纠正 → 构建 Prompt → Provider 快照 → 网络 → 流解析 → 结果发布。
3. `CancellationException` 必须重抛；普通 `Exception` 转为类型化可恢复错误；禁止全局吞 `Throwable`。
4. `finally` 只能清理由相同 `requestId` 拥有的状态，旧请求不得把新请求改回 Idle。
5. 失败必须保留消息、编辑草稿、IDEA、当前 KB 和已收到的可用部分；错误区提供重试与打开供应商设置。
6. 准备态开始后，双按钮立即进入统一 Loading/Stop 状态；快速连点只能产生一个请求。
7. Provider 配置错误、Prompt 读取错误、旧数据迁移错误、解析错误分别映射为用户能行动的错误，不展示裸异常，不直接退出 App。
8. Compose 渲染异常修具体状态/布局原因；不能指望协程 catch 捕获 UI 崩溃。

不要新建第二套 `GenerationEngine2`、`SafeGenerate` 或复制 `generate()`。统一入口完成后删除被替代分支。

### A2. P0 验收

- 模拟 Provider 下连续 30 次生成/停止/重试不崩，且每次点击最多一个请求。
- 新装和覆盖升级各至少 5 次真实 Provider 生成成功。
- 上表所有错误路径都留在 App 内，显示可恢复错误，修复配置/网络后无需重启即可再次生成。
- 真实 Overlay 中完成：输入 → 生成 → 首卡 → 完成 → 复制 → 重试 → 停止。
- 提交崩溃前后录屏、堆栈、根因说明和回归测试；不能只给测试数量。

---

## 5. 工作包 B（P1）：重做首页信息架构

### B0. 首页目标

首页只回答四件事：

1. LoveBrain 是什么、当前是否可用；
2. 如何启动/打开/恢复悬浮军师；
3. 两个最高频管理入口在哪里；
4. Provider、消息捕获和使用概览当前是什么状态。

当前首页从 Hero 直接开始，之后混排快捷卡、内部版本、捕获开关、Provider 折叠卡、六行统计。必须改为一个稳定层级，不能继续在原 Column 上逐块补丁。

### B1. 目标页面结构

1. **App Top Bar**
   - 标题 `LoveBrain`；副标题用一句稳定价值表达，例如“帮你更自然地表达”。
   - 右侧只放“关于/设置”图标入口。
   - 不显示 Git SHA、build type、内部缩写。

2. **悬浮军师状态卡（唯一主卡）**
   - 左上：`悬浮军师` + 状态 Pill（未授权/未启动/运行中/已隐藏）。
   - 中部：一句与当前状态对应的说明。
   - 底部：唯一主按钮（授权/启动/打开/恢复）。
   - 运行中时，“暂时隐藏”改为卡片右上角的次级图标或更多菜单项；不能另起一行。图标触摸区至少 48dp，并有 `contentDescription`。
   - “×”只表示隐藏当前浮窗时，不得让用户误以为删除、停止服务或关闭 App。

3. **快捷功能**
   - 只保留“知识库”“反馈案例”两张同宽卡片，使用同一个 `HomeActionCard`。
   - 标题、说明、图标容器、箭头、按压、禁用状态完全同源。
   - 点击反馈案例必须发生根页面切换，不能在当前滚动 Column 下方插入内容。

4. **服务设置**
   - “模型供应商”和“消息捕获”都使用同一个 `HomeSettingRow`：图标、标题、说明、状态、尾部动作。
   - 当前名为“雷霆”的 Provider 应显示为供应商行的状态/副标题，例如 `雷霆 · <模型>`，而不是看起来像首页主按钮的独立块。
   - 点供应商行进入供应商管理页；捕获行可直接切换，缺权限时尾部动作统一为“去授权”。

5. **使用概览**
   - 默认只展示 3 个真正有用的指标：累计生成、累计花费、采用率。
   - 使用同一行 3 个 `UsageMetric`，不再纵向堆 6 行。
   - 复制/采用/改写等详细数据放“查看详情”二级页面或展开区；“采用率定义”放信息说明，不占首页主视觉。

### B2. 统一组件与设计规则

在 `ui/home/` 建立有限的组件集合：

- `HomeScreen`
- `HomeTopBar`
- `AssistantStatusCard`
- `HomeSectionHeader`
- `HomeActionCard`
- `HomeSettingRow`
- `UsageSummary`
- `HomeDestination`/页面状态

规则：

- 全首页只保留一种页面背景、一种普通卡片表面、一种主要圆角、一种边框强度和一个主色 CTA。
- Hero 不再用高饱和渐变制造“广告横幅感”；通过尺寸、标题和唯一主按钮建立主次。
- 间距只从现有 Token 取值，优先 8/12/16/24dp；禁止局部手写相近数值。
- 正文/说明/状态各只有一个明确 Typography 角色；不要每块自己选字号和粗细。
- 所有可点击项有 pressed、disabled、loading、focus 和 48dp 最小触摸区。
- 360dp 宽、普通字体和 1.3 倍字体下都不能横向溢出；系统栏、键盘和滚动位置正确。

不要为了统一造一个几十个参数的“万能卡片”。复用应建立在相同语义上：快捷卡复用快捷卡，设置行复用设置行，指标复用指标。

### B3. 内部版本信息迁移

- 首页删除 `LoveBrain vX (GIT_SHA · BUILD_TYPE)`。
- 新建或复用“关于与诊断”页：普通用户看到版本号、隐私说明、许可证和反馈入口；展开“诊断信息”后才显示 SHA、build type、APK/数据版本。
- Release 首页不得出现 `unknown`、短 SHA、`debug/release` 等工程信息。

---

## 6. 工作包 C（P1）：反馈案例真正独立成页

当前 `F15FeedbackCasePage` 的名字和注释写“全屏”，但调用位置仍在 `HomeTabContent` 的滚动 Column 内。这是典型“名字完成、结构未完成”。

改造要求：

1. 在 `SetupScreen` 根部维护页面目的地：`Home / Providers / FeedbackCases / Usage / About`。项目暂时没有 Navigation Compose 依赖时，用简单 sealed destination 即可，不为一个页面引入大型框架。
2. 当目的地为 `FeedbackCases` 时，根内容只渲染反馈页；系统返回键和顶部返回按钮回到 Home，并恢复首页滚动位置。
3. 将反馈页面移出 `SetupActivity.kt` 到 `ui/feedback/FeedbackCasesScreen.kt`。
4. Composable 禁止 `remember { FeedbackCaseRepository(context) }`。数据必须通过 ViewModel/DI 的 StateFlow 提供；这也修正 `SetupActivity` 注释声称“UI → ViewModel → data”，实际 UI 直接 new Repository 的自相矛盾。
5. 页面状态使用 `Loading / Empty / Data / Error`，加载失败不能显示成“暂无案例”。
6. 列表继续使用 LazyColumn；内部枚举必须转中文；卡片点开独立详情或稳定展开区，完整显示输入角色、候选、原因、期望表达、模型、版本和已知 token。
7. 导出使用一个统一动作；支持系统分享/保存或复制，并提供明确成功/失败反馈。不要再在伪全屏页上叠一个手写遮罩当第二层临时窗口。

---

## 7. 工作包 D（P1）：重做“今日锦囊”的价值/成本合同

### D0. 撤销旧定义

旧指导书把锦囊定义成“默认最多 3 条”，当前 `suggest.md` 又把它写死成 `1-3` 条。用户已经明确否决这一产品选择。本轮不得继续以“少而清晰”为理由只给 3 条。

### D1. 新产品合同

- 默认一次生成 **6 条**，允许 6-8 条；不是 3 条换个 UI。
- 建议按使用时机归类：`现在可用 / 今天可准备 / 有机会再做`，不强制机械配额。
- 每条折叠态只显示“做什么 + 何时”；展开后显示示例、必要素材和一句理由。
- 允许用户把某条送入“主动发”准备区；不得直接记为已发送或真实发生。
- 仍然禁止编造素材、保证对方反应、骚扰或对明确拒绝的人持续联系。

### D2. 输入预算机制

当前锦囊复用 `buildCoreKnowledgeSubset`，仍可能带画像、经验最近 3 块、进行中事项，并经过约 9000 字符总预算；对于只输出 3 条建议，性价比明显不合理。

建立独立的 `DailyBriefContext`，只包含：

1. 当前关系阶段/温度摘要；
2. 与今天相关的有效事项最多 3 条；
3. 表达偏好摘要；
4. 最近一小段真实上下文（确有必要时）；
5. 需要避开的已确认边界。

限制：

- User Prompt 目标不超过约 3000-3500 个字符；超出按“边界与当前事项 > 近期对话 >画像摘要 > 旧经验”裁剪。
- 一次请求完成，不增加自动裁判、自动二次润色或自动补全调用。
- Prompt 预算、选择规则、序列化只有一个实现；不要在 UI、Engine、PromptBuilder 各截断一次。

### D3. 缓存与“换一批”

- 按 `kbId + 日期 + DailyBriefContext 指纹 + promptVersion` 缓存成功结果。
- 打开锦囊先展示当天有效缓存，不自动重新扣费。
- “重新生成/换一批”必须由用户明确点击，并显示这是一次新的模型调用。
- 换一批携带已展示 action 的短摘要用于去重，不重新塞入完整知识库。
- 缓存放独立建议缓存，不写入关系事实、recent、scene 或 lessons。

### D4. 成本可见性

锦囊结果顶部显示：`本次输入 tokens / 输出 tokens / 估算费用 / 生成时间`。Provider 未返回 usage 时显示“未知”，禁止以 0 冒充已核实。

验收报告必须对同一组 5 个代表性知识库比较 v1.3.3 与新版本：

- prompt tokens；
- completion tokens；
- 有效建议数；
- 每条有效建议成本；
- 重复/空泛/无法执行建议数。

第一阶段目标：默认至少 6 条可见建议；中位 prompt tokens 明显下降；每条有效建议成本不高于旧版，理想目标降至旧版的 50% 以下。达不到时继续优化选择器，不靠再调用一次模型补结果。

### D5. 数据合同同步修改

同步修改 `suggest.md`、`DailySuggestion/SuggestTip`、流式解析、最终解析、缓存和 UI。推荐单条字段：

- `id`
- `timingCategory`
- `action`
- `timing`
- `materialNeeded`
- `example`
- `reason`

兼容旧缓存只能存在于一个迁移/解析入口；迁移完成后 UI 不同时维护两套字段逻辑。输出少于 6 条、重复 action、空 action、非法 JSON 和截断都要有测试；部分结果可展示，但必须标“本次结果不完整”，不能崩溃或静默算成功。

---

## 8. 工作包 E：控制代码屎山，不做表演式拆文件

当前十个核心文件合计约 13,407 行：

| 文件 | 当前行数 |
|---|---:|
| `LoveBrainViewModel.kt` | 2,611 |
| `KnowledgeRepository.kt` | 2,070 |
| `SetupActivity.kt` | 1,616 |
| `ResultArea.kt` | 1,299 |
| `LoveBrainPanelScreen.kt` | 1,164 |
| `DeepSeekRepository.kt` | 1,122 |
| `PromptBuilder.kt` | 1,063 |
| `TopicRecorder.kt` | 890 |
| `SuggestPanel.kt` | 835 |
| `SchemeCard.kt` | 737 |

行数不是唯一问题；真正的问题是 UI、导航、存储、请求状态和导出逻辑在少数文件内混杂，且生产代码充斥 `F12/P1-G/阻断D修复` 一类历史补丁注释。

本阶段只做与改动直接相关的职责迁移：

- `SetupActivity` 只保留 Activity 宿主、根路由和系统权限桥接；首页、供应商、反馈、统计、关于分别移出。
- `LoveBrainViewModel` 的回复请求状态只有一个真源；不要在本轮顺便拆完所有知识库职责。
- 锦囊使用独立的 context selector/cache，但继续复用统一 Provider/usage/error 基础设施。
- 生产注释只保留“为什么”的长期说明；删除工单号、日期、worker 对话和已过时的修补过程。

根目录还存在明显污染物：

- `_fix_nat.py` 只有 `print(1)`；
- `_p.py` 只是 placeholder；
- `_write_polish.py` 内容只是文件名；
- `cot_rvs_paper.txt` 是与 LoveBrain 无关的 ICLR 视频分割论文全文；
- 旧版修复指导书被放在产品源码根目录，而 `.gitignore` 又声明内部指导书不应入库。

worker 先用 `rg`/GitHub 搜索确认无引用，然后在独立 cleanup commit 中移除这些污染物。Git 历史已保留，不需要把无关文件继续留在公开产品仓库。

---

## 9. 测试与验收矩阵

### 9.1 必加测试

| 测试 | 覆盖内容 |
|---|---|
| `ReplyRequestStateTest` | Preparing/Streaming/Error/Cancel/Retry 的唯一状态与 requestId 所有权 |
| `ReplyGenerationFailureTest` | Prompt、Provider、网络、解析、发布回调各阶段异常都转可恢复错误 |
| `ReplyGenerationRapidTapTest` | 连点只启动一次；旧请求 finally 不清新请求 |
| `HomeScreenStateTest` | 未授权、停止、运行、隐藏四状态的主/次操作与语义 |
| `SetupDestinationTest` | Feedback/Provider/Usage/About 为根页面切换，不插在首页列表里 |
| `DailyBriefBudgetTest` | 输入选择与预算只有一个入口，超长资料按优先级裁剪 |
| `DailySuggestionContractTest` | 6-8 条、去重、空字段、截断、旧缓存迁移 |
| `DailySuggestionCacheTest` | 同日同指纹不重复请求；上下文变化后缓存失效 |
| `OverlayGenerateSmokeTest` | 真实 FloatingService 宿主点击生成到结果/错误，不发生进程崩溃 |

禁止新增“搜索源码有没有某个字符串”的伪机制测试。

### 9.2 分层命令

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon
./gradlew :app:assembleRelease --no-daemon
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

以上命令全部通过仍不等于发布通过；还必须执行真实 APK 的新装、覆盖升级和 Overlay 冒烟。

### 9.3 UI 实机矩阵

必须提交同尺寸“改前/改后”截图和短录屏：

- 360dp 小屏与常规屏；
- 字体 1.0x 与 1.3x；
- 未授权/未启动/运行/隐藏；
- Provider 未配置/配置错误/可用；
- 捕获未授权/关闭/开启；
- 反馈空/加载/失败/有长案例/详情/导出；
- 统计全 0 与大数字；
- 锦囊无缓存/有缓存/生成中/6 条结果/不完整/失败；
- 键盘开关、系统返回、进程重建。

---

## 10. 推荐提交顺序

1. `test: reproduce direct-generation crash and add request tracing`
2. `fix: make reply generation a recoverable single-owner transaction`
3. `refactor: extract home screen and shared home components`
4. `fix: move feedback cases and provider management to root destinations`
5. `feat: replace three-tip suggest flow with budgeted cached six-tip brief`
6. `test: add overlay/home/suggest acceptance coverage`
7. `chore: remove repository junk and obsolete patch comments`
8. `release: phase-1 RC evidence only`（门禁通过前不打稳定标签）

每个提交必须可构建；不能先复制一套新实现、最后一个巨型提交再删旧实现。

---

## 11. worker 最终交付物

worker 完成后一次性交付：

1. 基线 SHA、最终 SHA、逐提交说明。
2. 崩溃复现步骤、原始堆栈、根因、修复机制和回归证据。
3. 首页 4 种服务状态的改前/改后截图；反馈独立页面与导航录屏。
4. 真实 Overlay 生成成功、错误恢复、停止、重试录屏。
5. 锦囊旧版/新版同输入 token、费用、有效建议数对比表。
6. 新装与 v1.3.2/v1.3.3 覆盖升级报告，不清数据。
7. 全部测试命令与结果；CI 链接；APK SHA-256、签名和来源。
8. 删除/合并的重复实现清单与根目录清理清单。
9. 未完成项和风险必须如实列出，不得用“后续优化”隐藏 P0/P1。

worker 不得在报告中自行写“最终通过”。独立检查者会重新读取 GitHub HEAD、源码、CI、APK 与实机证据后决定是否进入第二阶段。

---

## 12. 第一阶段完成门禁

以下条件必须同时满足：

- 点击“生成回复”在真实公开构建路径不再退出 App；失败可恢复，草稿与消息不丢。
- 首页有清楚的品牌/状态/主操作层级；“暂时隐藏”不再独占一行。
- 首页不再显示 Git SHA/build type。
- 快捷卡、设置行、状态与统计形成统一组件语法；“雷霆”只作为 Provider 状态出现。
- 反馈案例是根级独立页面，不在首页下方直接展开。
- 使用概览默认不再堆 6 行。
- 今日锦囊默认至少 6 条，输入显著缩短，有缓存和真实 usage/费用展示。
- 触及的业务规则不存在新旧双轨；明显根目录垃圾已清理。
- CI 通过，真实 Overlay、新装、升级、Release/R8 均有证据。
- 用户主链实际体验达到可用；不以文件数、注释、提交标题或测试数量代替验收。

达到以上门禁后，提交独立复核；复核通过才进入第二阶段。
