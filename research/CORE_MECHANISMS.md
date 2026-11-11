# LoveBrain 核心机制详解与判决映射

本文回答三个问题：LoveBrain 的核心机制分别解决什么问题、怎么工作；每个机制有哪些实验判决支撑；这些机制现在处于产品实现路径的哪一层。三条主线与总体叙事的公开口径定稿见 `research/architecture/ARCHITECTURE.md`（"机制研究叙事"节），本文是其机制级展开与判决映射，两者引用同一套证据，不另立口径。

**证据与路径约定（全文有效）：**

- 路径均相对本仓库根目录。产品代码 = `app/src/main/java/com/lovebrain/app/`（下文简写为 `app/…`），行号实测于 2026-11-12，产品分支 `closeout-20261106` @ 提交 `a0104be0`，工作树干净。
- 研究资产 = `research/…`（Arena 运行时、候选机制目录、判决文件均已随迁移落盘于本仓）。
- 实验数字一律来自 `research/refs/` 下的判决原文件（文件名 + 日期即出处），逐字或按原口径转写；限定语与数字同源，整句携带、不截断。
- 本文不计算、不引用、不比对任何文件完整性哈希值；完整性口径 = 文件数 + 字节量 + 时间戳。文中出现的 `afa9559f` / `61afbc92` / `a0104be0` 为本仓 git 提交标识，用于锚定接线代码，非完整性校验。
- **三实现状态口径（全文通用）：**
  | 状态 | 含义 |
  | --- | --- |
  | Arena 研究原型 | `research/candidates/`（历史机制线）或 `research/tools/benchmark_v1/prototype_candidates/`（新研究线）中的 Python 机制 |
  | Kotlin 移植件（SHADOW 影子链路） | `app/…/domain/champion/` 已移植并在 Android 侧注册，经显式 SHADOW 档在影子请求中运行，不影响主回复 |
  | 生产接线（LIVE_AUTO 主链路） | 正常回复主请求按轮次自动装配/守卫（本地提交 `afa9559f`→`61afbc92`→`a0104be0` 落码；真机行为级验收清单待执行，推送待授权——本文所有"已接线"表述均为代码与本地确定性测试级，不代表真机已验证） |

---

## 1. 总览：三条主线

> LoveBrain 的 Harness 围绕上下文装配、证据记忆和生成约束三条主线设计。系统首先根据当前意图、场景、话题和持续事项筛选相关信息，再通过完整历史事件和来源身份保留关键证据，并使用未确认事项约束与确定性输出守卫减少事实升级和结构错误。我们将这些机制放入独立 Arena，在固定模型服务下进行连续对话对照和消融，再将有价值的机制移植回 Android 产品。

（上段为公开叙事定稿原文，与 `research/architecture/ARCHITECTURE.md` 同一逐字文本。）

| 主线 | 核心问题 | 主要机制 | 判决映射（详见 §2–§4） |
| --- | --- | --- | --- |
| 上下文装配与相关性门控 | 记忆很多但本轮不一定相关；上下文过长噪声多 | 持续事项选择、场景/意图装配、Prompt 预算、段检索；新研究线整合 Harness `rh_converge` | `research/refs/research_harness_20261110/overnight/` 台账三件（rh_converge 判决 KEEP，节锚点见 §2.3） |
| 证据优先的历史记忆 | 早期金额、日期、约定、原话滑出滑动窗口；摘要链丢细节 | 稳定 UID 真实消息事件流、3600 CU 完整消息窗口、删除无贡献 recap | `FLJUDGE_VERDICT_20261102.md`、`LD1_VERDICT_20261102.md`、`W15N01_ABLATION_VERDICT_20261102.md` |
| 事实与输出约束 | 未确认事项被说成已发生事实；输出 JSON 结构漂移 | 未确认事项约束 uc（输入侧）、确定性输出契约守卫 guard（输出侧） | `W16_UNC_VERDICT_20261102.md`、`W17B_GUARD_VERDICT_20261103.md`、`W17C_MERGE_VERDICT_20261103.md`；负结果 `W19CROSS_VERDICT_20261103.md` 等 |

判决文件位于 `research/refs/experiment_protocol_20261023/long_probe_20261030/`（W 系列与 LD1/FLJUDGE/CF1）与 `research/refs/research_harness_20261110/overnight/`（研究线循环台账）。

**成绩表隔离限定（引用任何本文数字前必读）：** 2026-11-11 的 `research-p1-sync-20261111` 分支延续了 Arena 工作，但它是新的研究阶段，不能与旧版 W16/W17 直接混成同一张成绩表。本文分节呈现两代证据，不做跨代合表。

---

## 2. 主线一：上下文装配与相关性门控

### 2.1 机制说明

聊天辅助产品不是记忆越多越好：全量注入会让已结束话题重现、旧目标在用户改变想法后继续生效、旧事项挤占预算。LoveBrain 的上下文机制不是"检索更多记忆"，而是**先判断哪些状态仍然有效、哪些信息与当前轮相关，再按优先级组织上下文并控制预算**。核心原则：存储里存在，不代表当前轮应该使用。

产品侧机制族：持续事项选择（四态生命周期 + 相关性信号 + 冷却）、话题切换门（话题状态为 new 不等于话题已切换）、纠正事实过滤（被纠正的旧事实不再进入上下文）、场景注入、意图装配、Prompt 预算。

新研究线的 `rh_converge`（`research/tools/benchmark_v1/prototype_candidates/rh_converge/`）把这套确定性装配重组为完整 Harness：三类相关性信号 + 3 轮冷却 + DORMANT 5 轮、7 类场景固定优先级、hedge/question/quote 三信号未确认块、9000 字符三步裁剪、知识段装配、said/invalidated 账本、Top-3 段检索、b20 示例锚定、每轮一次模型调用。

### 2.2 代码位置

产品原生件（`app/…/domain/`，全部实存）：

| 机制 | 文件与锚点（行号 2026-11-12 实测） |
| --- | --- |
| 持续事项选择 | `domain/OngoingContextSelector.kt`：类 :40；事项状态枚举 ACTIVE/DORMANT/FINISHED/CANCELLED :50-54；DORMANT 推导 :129-130；冷却 `PendingCooldownUpdate` :407 / `OngoingCooldownStore` :425 |
| 话题切换门 | `domain/TopicSwitchGate.kt`：`internal object` :50、`Decision` :71、`evaluate` :98 |
| 话题记录/用户确认提交 | `domain/TopicRecorder.kt`：类 :53 |
| 纠正事实过滤 | `domain/prompt/CorrectionExtractionFilter.kt`：object :32 |
| 知识上下文读取 | `domain/KnowledgeContextReader.kt`：类 :35 |
| 场景注入 | `domain/prompt/CurrentSceneInjection.kt` :22；链式 `domain/SceneChainInjection.kt` :20（伴生 `domain/SceneChainStore.kt`） |
| 意图装配 | `domain/prompt/IntentIdeaBlock.kt` :27 |
| 角色与来源隔离 | `domain/prompt/ChatTranscriptBlock.kt` :26；`model/GenerationInput.kt` :24 |
| Prompt 预算 | `domain/PromptBudget.kt` object :14；`domain/PromptBuilder.kt` 类 :41 |
| 知识段装配 | `domain/prompt/PromptKnowledgeSection.kt` :29 |

`rh_converge` 原型（`research/tools/benchmark_v1/prototype_candidates/rh_converge/mechanism/mechanism.py`，621 行）：`_ongoing_select` :175（`COOLDOWN_TURNS = 3` :54、`DORMANT_TURNS = 5` :55）；`_scene_infer` :235 / `_scene_block` :273；未确认块段装配 :547；`_budget_trim` :290（`TOTAL_BUDGET = 9000` :62）；`_knowledge_assemble` :304；`_build_said_ledger` :366 / `_build_invalidated` :391（`MAX_LEDGER_TAIL = 5` :59、`MAX_INVALIDATED = 4` :60）；`_build_segments` :339 / `_select_segments` :415（`TOP_SEGS = 3` :58）；`_build_examples_block` :441；`run_turn` :595 单次 `gateway.generate(..., tag="main")`。

**计数口径登记：** 候选自述件 `MECHANISM.md` 写"14 段装配（头部稳定→中部动态→尾部贴近生成点）"；代码 `sections.append` 槽位计数为 15（"硬约束"段与"已作废/挂起"段同条件块成对）。段结构、三段式组织一致，属计数口径差，不影响机制结论；公开面沿用自述口径"14 段"，被追问时以本登记口径答复。

### 2.3 实验证据（rh_converge，只认 overnight 台账三件）

数字来源：`research/refs/research_harness_20261110/overnight/` 的 `LIVE_LOOP_LOG.md`、`RESUME_STATE.json`、`WATCHDOG_VERDICT.json`。晨报口径限定：`MORNING_REPORT.md` 的早期版本只覆盖到 LOOP-7；涉及 LOOP-14 时，以 `LIVE_LOOP_LOG.md`、`RESUME_STATE.json` 和 `WATCHDOG_VERDICT.json` 的对应记录为准。

| 读数 | 值 | 限定语（整句携带，不截断） |
| --- | --- | --- |
| 五维质量均值（8 关键位） | 4.67 vs DIRECT 4.69（−0.3%） | 对照值 4.69 来自更早一轮 60 位池审计的总均（`FAILURE_ANALYSIS.md` 对应节；12 位固定抽样上 DIRECT 为 4.65），完成节呈现口径与源文件一致——公开引用 4.69 必须携带此口径，不得当作 8 关键位同口径值 |
| Token/轮 | 1,090 vs 1,420（约 −23%） | "约 23%"为源文件原口径；`overnight/CANDIDATE_SCOREBOARD.csv` rh_converge 行第三方印证 |
| 端到端 P50 | 42 秒 vs 约 20 秒 | 如实并列：更多上下文处理不自动等于更快，等待时间更长是该机制如实登记的代价 |
| Q2 逻辑连贯 | 首次突破 4.0：E355_T03、E376_T04 两处 Q2=5（2/8 关键位）；b20、b21 对照均为 Q2=4.0 | DIRECT 无同口径 8 关键位 Q2 读数（池审计中的 DIRECT Q2 4.98 属 60 位审计、另一口径） |
| 判决 | KEEP（`LIVE_LOOP_LOG.md` 对应完成节 + `RESUME_STATE.json` core_law + `WATCHDOG_VERDICT.json` 对应条目 `overall: PASS`，15 个循环中仅 2 个获得 PASS、本条目为其中之一） | "它是当前新研究线中最值得保留的效率型整合候选，不应写成已经全面击败 DIRECT 的质量冠军。" |

机制组成与实现边界（逐项对应 `MECHANISM.md` 与源码）：三类相关性信号、冷却 3 轮、DORMANT 5 轮、7 类场景、三步裁剪 9000 字符、Top-3 段检索、示例锚定、said/invalidated 账本、每轮 1 次调用；实现边界 5 条 = 状态在单故事进程内、corrections 只留接口、冷却简化为进程内状态、SceneChainInjection 未移植、无 `ongoing_plan` 时整体 no-op。MemoryRefPolicy 为简化版（源码头注"只留接口"）。

### 2.4 实现状态

| 状态 | 内容 |
| --- | --- |
| Arena 研究原型 | `rh_converge` 在位（研究树落盘副本与源侧逐件同内容）；判决 KEEP（§2.3） |
| Kotlin 移植件（SHADOW 影子链路） | 无 rh_converge 专属移植件（不在移植清单）；其重组的各确定性机制以**产品原生件**形式存在（见 2.2 表，12 件实存） |
| 生产接线（LIVE_AUTO 主链路） | 产品原生装配机制一直在正常回复链路内生效；**rh_converge 作为整合 Harness 未移植、未接线**——这是研究线结论，不得写成生产机制 |

---

## 3. 主线二：证据优先的历史记忆

### 3.1 机制说明

长对话中丢失的不是大概主题而是具体事实：早期金额、日期、约定、决定主体、方案是否取消、较早的重要原话。滑动窗口把早期消息移出上下文，摘要链可能丢事件动作、主体、原话和细节；问题的实质是**关键原始证据已不在当前可见上下文里**。机制三件套：

1. **有序真实消息事件流**：稳定 UID、保留角色/原文/时间、以完整消息组织、当前轮独立保留；**模型生成的回复只是草稿，不进入真实事件流**；只有用户确认的真实消息进入后续历史——"模型说过一句话，不代表用户真的发送过这句话"。
2. **3600 CU 完整历史窗口**：`window_expand_b` 将历史预算从 1400 提到 3600 CU（按 UTF-16 code unit 计量、从最新向前保留、以完整消息为单位、不切半句、每轮记录 `history_cu`）。
3. **删除无贡献 recap**：`window_expand_b_norecap` 在消融判决支撑下移除摘要段。判决自我限定：**"简化主张是『无损可删』，不是『recap 有害』"**——不得扩张成"所有摘要都没有价值"。

### 3.2 代码位置

Arena（`research/candidates/…/mechanism/mechanism.py`）：

| 机制 | 锚点 |
| --- | --- |
| 3600 CU 预算 | `window_expand_b_norecap_uc_guard` :76 `MAX_EVENT_HISTORY_CU = 3600`、:134 `_line`、:140 `_budget_history`；`window_expand_b_norecap` :46 同常量；`window_expand_b` :67 头注"delta ①：历史真实消息段的 UTF-16 码元预算 1400 → 3600" |
| 逐字父本原则 | uc_guard 头注 :6 明确"守卫只在 gw.generate 返回后做确定性修复+审计；prompt 构造、3600 CU …逐字=父本"；各候选 `MECHANISM.md`「草稿永不写回事件流」 |

Kotlin（`app/…/`，行号 2026-11-12 实测）：

| 机制 | 锚点 |
| --- | --- |
| 历史块装配 | `domain/champion/EventHistoryAssembler.kt`：`MAX_EVENT_HISTORY_CU = 3600` :25、`line` :32、`budgetHistory` :45（从最新往旧整条累加、越界即停、无 recap、无相关性排序） |
| 事件流与存储 | `domain/champion/ChampionEvent.kt`：`ChampionEventStore` :141（`append` :163、`events` :200、`nextTurn` :205）、`requireRealRole` :146（白名单，回复草稿在入口被拒）、文件名键哈希防碰撞 :89-97 |
| 确认后提交 | `domain/champion/ChampionEngine.kt`：`commitConfirmedTurn` :310 为事件入库唯一路径（提交点挂在确认轮次，见 §5 调用链）；`shadowTurn` :347 不触库；稳定消息身份去重 = `ChampionMessage.msgUid`（= 用户消息 id） |
| 依赖注册 | `di/AppModule.kt` :102-123（事件存储目录与知识库投影物理分离） |

### 3.3 实验证据

| 实验（判决文件，均在 `research/refs/experiment_protocol_20261023/long_probe_20261030/`） | 读数 | 限定语 |
| --- | --- | --- |
| FLJUDGE（`FLJUDGE_VERDICT_20261102.md`） | T25-32 保持率 1.0 vs 滑窗父本 0.493/0.509；T13-18 0.832/0.865 vs 1.0/1.0；T19-24 0.650/0.596 vs 1.0/1.0；Prompt 482,846 → 280,628（−41.9%） | 0.493（LRE060）与 0.509（LRE048）是**同一父本臂 r9_slim 在两篇故事上的读数**，不存在独立"对照"臂——表列名必须写"滑窗父本（r9_slim）两篇读数"，不得写"父本/对照" |
| LD1（`LD1_VERDICT_20261102.md`） | 四篇新领域（出行拼车/工作职场/餐饮聚会/医疗健康，40/40/40/32 轮）T25-32 保持率全部 1.0（0 miss）；父本臂四篇读数 .323/.512/.624/.568；Prompt 991,960 → 555,786（−44.0%）；终态真实保持 425/430 = 0.988 | 父本四值同为 r9_slim 臂读数，不得写"对照"；长程保持效果跨四域复现 |
| W15N01（`W15N01_ABLATION_VERDICT_20261102.md`） | 删除 recap 后两臂保持率 1.000/1.000（LRE117+LRE146）；九维 9/9 全正（memory +0.253、fact +0.241、goal/boundary +0.203、最差 init/emotion +0.038）；severe **亲读账 1 ≤ 3**（judge 原始 1 vs 12）；Prompt 303,664 vs 304,550（−0.3%） | severe 口径 = 亲读账（与判决同口径引用）；判决自我限定"简化主张是『无损可删』，不是『recap 有害』" |
| recap 出线依据（同判决依据 1 原文口径） | "LD1+本批共 6 篇长剧实测 recap 仅在 T34+ 出线且 ≤199 CU（T25-32 判决段全程为空）" | 引用必须用判决原口径（6 篇证据池、"仅 T34+"、"≤199 CU"），不得弱化为"主要在 T34 之后/通常不超过" |

### 3.4 实现状态

| 状态 | 内容 |
| --- | --- |
| Arena 研究原型 | `window_expand_b` → `_norecap` → 分域冠军 `_norecap_uc`（>24 轮）/ `_norecap_guard`（≤24 轮）候选链在位（`research/candidates/`） |
| Kotlin 移植件（SHADOW 影子链路） | `ChampionEvent.kt` / `EventHistoryAssembler.kt` / `ChampionPromptAssembler.kt` / `ChampionEngine.kt` 实存并注册；草稿不入流、确认后提交、msgUid 去重均有测试钉死 |
| 生产接线（LIVE_AUTO 主链路） | 接线后历史块进入**正常回复主请求**（此前只在 SHADOW 影子请求中出现）：两条分域策略都注入 3600 CU 历史块，注入位置在当前轮聊天记录之前；"仅看本轮"请求仍完全绕过；当前轮消息按 msgUid 排除且不写库，草稿不入流原则在接线后依然成立 |

---

## 4. 主线三：事实与输出约束

### 4.1 机制说明

两条互补约束，分别作用在输入侧与输出侧：

1. **未确认事项约束（uc，输入侧）。** 未确认的信息必须继续保持未确认，不能被模型补全成已发生事实。纯函数检测三类信号（转述/推测、未确认问句、引号转述），检测后检查后续是否已有明确确认、锚点重合与当前轮相关性；命中才插入短约束块：最多 3 条、整段 ≤120 CU、原始逐字引用不改写不概括、无未确认事项时整段完全不出现（此时 Prompt 与父本逐字节相同）、不调用额外模型。**实现口径限定（Arena 侧）：**"只保留仍未确认且与当前轮有关的事项"在该机制中的实现为**预算截断保最新**（≤3 条 / ≤120 CU / 超限只留最新），无显式相关性评分；Kotlin 移植侧另有 `closed`/`relevant` 判定函数。
2. **确定性输出契约守卫（guard，输出侧）。** 模型返回文本后做确定性处理：去围栏、弯引号规范化、已知键别名映射、未知风格键按字符相似度映射（仅目标槽为空时）、response 缺槽补 null 不虚构、directions 截 4 补 null、空回复只记审计不改文、无法安全修复保留原始输出。工程原则：**确定性代码只修复结构，不替模型编造回复内容**；原始模型文本在守卫运行前已写入调用记录；修复不增加模型调用；变化记审计。模糊风格键映射在 Android 移植中默认关闭（`FUZZY_MAP_ENABLED = false`，未与 Python 夹具对拍前不得启用）。

### 4.2 代码位置

Arena（`research/candidates/window_expand_b_norecap_uc_guard/mechanism/`）：

| 机制 | 锚点 |
| --- | --- |
| uc 检测 | `uncertain_rules.py`：`UNCERTAIN_BUDGET_CU = 120` :17、`MAX_ITEMS = 3` :18、`utf16_len` :38、`split_clauses` :70、`find_uncertain` :157、`build_block` :188 |
| guard 修复 | `contract_guard.py`：`_best_style_slot` :58、`_action_shaped` :73、`_map_style_keys` :87、`repair_and_audit` :131；vendor 件 `_vendor/output_repair.py`（`repair` :219）、`_vendor/normalize_reply_contract.py`（`normalize_raw` :144） |
| 分域冠军目录构成 | `_norecap_uc`（>24 轮冠军）目录**无** contract_guard.py；`_norecap_guard`（≤24 轮冠军）目录**无** uncertain_rules.py——与分域判决逐件吻合 |

Kotlin（`app/…/domain/champion/`，行号 2026-11-12 实测）：

| 机制 | 锚点 |
| --- | --- |
| `UncertainBlockDetector.kt` | object :17、`UNCERTAIN_BUDGET_CU = 120` :23、`MAX_ITEMS = 3` :24、`isCandidate` :108、`closed` :123、`relevant` :134、`itemsOfMessage` :147、`findUncertain` :183、`buildBlock` :224 |
| `ContractGuard.kt` | 类 :45（`auditSink` 注入）、`FUZZY_MAP_ENABLED = false` :65（:677 未启用返回 null）、`loadsDict` :242、`sequenceMatcherRatio` :254、`repairJson` :307、`GuardOutcome` :767、`repairAndAudit` :784 |
| 接线 | `GenerationEngine.kt`：守卫判据 `liveContext?.strategy == ChampionTurnStrategy.GUARD_SHORT` :328，执行体 `repairAndAudit` :337，插在 `parseReplyResponse` :394 之前 |

### 4.3 实验证据

| 实验（判决文件均在 `research/refs/experiment_protocol_20261023/long_probe_20261030/`） | 读数 | 限定语 |
| --- | --- | --- |
| W16（`W16_UNC_VERDICT_20261102.md`）· KEEP，>24 轮域冠军机制 | severe **亲读 1 vs 6**（judge 原始 1 vs 10，以亲读账为准）；九维 9/9 全正：memory +0.301、fact +0.287、goal +0.278、emotion +0.226、attribution +0.217、interaction +0.191、time +0.175、boundary +0.148、initiative +0.087；Prompt 444,475 → 447,936（**+0.78%** ≤5%）；额外模型调用 0；触发 35/120 = 29.2%；块 CU 峰值 119 ≤ 120（均值 107.3） | severe 用亲读账口径；触发面说明机制只在有需要时增加上下文，不每轮堆提示词 |
| W17B（`W17B_GUARD_VERDICT_20261103.md`）· KEEP | 契约违规 **2/72 vs 7/72**；severe 亲读候选 0 vs 父本 2（judge 原始 0 vs 7）；空回复 0 vs 0；九维 8/9 为正（initiative −0.058 为唯一负维，如实并列）；Prompt 206,245 = 206,245（+0%）；额外调用 0；离线回放（72 条原始回复复评）违规 16 → 4 | "8/9 为正"不得写成"9/9"；离线回放 16→4 与正式 live 2/72 是两个口径，分列呈现 |
| W17C（`W17C_MERGE_VERDICT_20261103.md`）· 短对话域复评 | 6 篇未暴露 12 轮故事全过冻结门；severe 亲读 candidate 0 vs parent 3 确认（judge 旗 4，其中 1 例降级）；契约违规双方 2/72；九维 main-only（n=61/72）配对差 9/9 为正（goal +0.377 … emotion +0.033）；Prompt 440,613 → 210,578（−52.2%）；calls 72/72，额外 0 | 该判决后续修订仅涉口径登记与机械分均值，未改动上列任何读数；−52.2% 为该批父本对照读数（父本与候选消费结构不同），不将其归因于 guard 本身 |

### 4.4 实现状态

| 状态 | 内容 |
| --- | --- |
| Arena 研究原型 | uc / guard / 合并候选齐备；分域冠军保留：uc = >24 轮、guard = ≤24 轮（W19/W21 判决维持不让位） |
| Kotlin 移植件（SHADOW 影子链路） | `UncertainBlockDetector.kt` / `ContractGuard.kt`（+ `FileGuardAuditSink`，`AppModule.kt` :116-121）实存；模糊映射默认关 |
| 生产接线（LIVE_AUTO 主链路） | 守卫仅在 ≤24 轮域（GUARD_SHORT）于 parse 前执行，异常或未修改一律保留原文；uc 仅在 ≥25 轮域（UC_LONG）按规则注入；两域互斥、不叠加，未晋升的合并候选不以任何形式变相启用（身份登记 `legacy_shadow_candidate = window_expand_b_norecap_uc_guard — CROSS_DOMAIN_NOT_PROMOTED`，`ChampionEngine.kt` :518 起身份段）；`FUZZY_MAP_ENABLED` 保持 false |

---

## 5. 接线架构概述（LIVE_AUTO 分域）

### 5.1 分域规则与实现契约

正常回复主请求按轮次自动选择分域策略，规则唯一且纯确定性：

- 请求轮次 = 已提交轮数 + 1；**≤24 → GUARD_SHORT**（历史块注入、uc 恒空、guard 执行）；**≥25 → UC_LONG**（历史块 + uc 注入、guard 不执行）。边界 24 单点定义于 `ChampionTurnStrategy.SHORT_DOMAIN_MAX_TURN`（`ChampionEngine.kt` :69），任何地方不得散写数值比较。
- 依据：短域判决 `W17C_MERGE_VERDICT_20261103.md`（guard）+ 长域判决 `W16_UNC_VERDICT_20261102.md`（uc）+ `W19CROSS_VERDICT_20261103.md`（两域不叠加、合并候选不晋升）。

实现契约（签名要点，已按此落码）：

| 契约件 | 锚点（行号 2026-11-12 实测） | 语义 |
| --- | --- | --- |
| `ChampionMode.LIVE_AUTO` | `ChampionEngine.kt` :41 | 新增生产档；`isMainPathGuarded` :49 保持只认旧两档，守卫判据走 strategy，不复用旧档位语义 |
| `ChampionTurnStrategy` | :61-73（`forTurn` :72） | 纯函数分域；`GUARD_SHORT` / `UC_LONG` |
| `ChampionLiveContext` | :81 | 本轮冻结装配结果：currentTurn/strategy/historyBlock/uncertainBlock/historyCu/uncertainCu |
| `prepareLiveContext` | :245 | 读唯一事件库 → `budgetHistory` 3600 CU 整条装配 → 当前轮仅作临时输入（不 append/不落盘/不入 shadow 日志）→ forTurn → 仅 UC_LONG 调 `UncertainBlockDetector`；零模型调用；不启动协程/影子 |
| `commitConfirmedTurn` 双轨门禁 | :310 | 生产链路传冻结快照（不回读设置）；null 走原语义；OFF → 空返回零 IO |
| 冻结输入 | `model/GenerationInput.kt`：`championMode` 字段、`KbContext.committedTurnCount` | 引擎全部判据来自冻结字段，不回读设置；字段名即口径——`committedTurnCount` 永远是 `getTurnCount` 原值（已提交轮数），任何"当前请求轮次"现场 +1 |
| Prompt 三参 | `domain/PromptBuilder.kt` :272-274（`historicalBlock`/`uncertainBlock`/`currentTurn`）；`domain/PromptBudget.kt` :38-39 | 空默认串拼零字符；拼装序 = 知识+场景+意图+军师备注+历史+未定+聊天+时间戳；裁剪序 = 知识段尾→recent→整块移除历史块→整块移除未定块→对话头部，两块只整块保留或整块移除 |
| 默认档 | `data/SecurePrefs.kt` :371（null 哨兵 `?: return "LIVE_AUTO"`) | 键不存在 → LIVE_AUTO；显式 OFF/SHADOW 原样尊重；旧档位与脏值 fail-closed 读为 OFF；键名不变、启动期不写盘覆盖 |
| ViewModel 冻结 | `viewmodel/LoveBrainViewModel.kt` :1237/:1239（一次性冻结）→ :1264-1265（传入）；提交门禁 :1608、`modeSnapshot` :1641 | 模式与轮次在生成开始各读一次；生成中改设置不影响本轮；topicRecorder 成功后才提交，事件写失败只记日志不回滚产品落盘 |

### 5.2 生产调用链（接线后）

```text
runReplyRequest（生成开始，一次性冻结 championMode + committedTurnCount）
  ▼ GenerationInput（冻结快照，引擎零回读设置）
replyStream 分支：
  分支 1 显式 OFF     → 原生装配路径（不装配/不读事件库/不修文/不影子，提交零 IO）
  分支 2 SHADOW       → 原生主回复 + 收尾块按冻结快照跑影子请求（旧影子行为，判据来源改冻结输入）
  分支 3 LIVE_AUTO    → prepareLiveContext（零模型调用）
        · "仅看本轮"为第一判据：全跳过，仍只发一次模型请求
        · 轮次缺失不猜轮次：整链回退原生路径
        · ≤24（GUARD_SHORT）：历史块进原生 Prompt、uc 空、guard 于 parse 前执行
        · ≥25（UC_LONG）：历史块 + uc 进原生 Prompt、无 guard、绝不跑影子
  ▼ 原生 PromptBuilder（画像/阶段/场景/recent/持续事项等能力全部保留）→ 恰好一次流式请求
  ▼ ≤24：guard 修改原文时先发 UI 重置事件再重放修复全文（UI 最终显示 = 最终解析结果）→ parser → 完成
nextRound 提交点：冻结快照门禁 → 话题记录成功 → commitConfirmedTurn（HER/ME 过滤、msgUid=消息 id）→ 产品落盘
```

### 5.3 回退与安全边界

- 显式 OFF 全链短路；`committedTurnCount` 读取失败不猜轮次（不默认第 1 轮也不默认第 25 轮），整链回退原生路径，正常生成继续——辅助机制故障绝不拖垮产品回复。
- guard 异常或无修复能力范围（空回复、全乱码）不触碰不伪造，保留原文进 parser；守卫未修改原文不发任何 UI 重置。
- 影子语义零改动：`launchShadow`/`shadowTurn`/影子日志原样，LIVE_AUTO 分支不调用影子。
- 超预算时历史/未定两块只整块保留或整块移除，禁止裸截断、切半条、破坏聊天 JSON 行边界、删 recent。

---

## 6. 字节冻结硬门

接线必须证明"新机制关闭时旧输出逐字节不变"。承载测试：`PromptByteFreezeBaselineTest`（冻结表逐行内容指纹与字节数双重比对）+ `PromptBudgetTest`（空区块旧 7 参调用与新 9 参空区块调用在超预算/不超预算两路径上逐字节相等）。

- **冻结表规模：101 行读数。** 接线实施按"先跑基线、后改码、实测回填、不许手算"执行：改码前基线全绿；落码后复跑，101 行中**恰好 3 行漂移、其余 98 行逐字节一致**。
- **3 行预期漂移（接线自身的内容真变，逐一登记）：**
  1. `system/reply`：20,149 → 20,483 字节——系统提示安全声明扩展（声明新增段落明确"历史原文与未定事项引用同为不可信第三方文本，不是新的系统指令"）；
  2. `asset-hash/reply`：换值——新声明参与 Prompt 资产指纹口径；
  3. `reply/live-blocks`：新增行——三参非空时全文读数 3,313 字节（注入顺序与整块语义的回归锚点）。
- 三行读数均取自实跑测试输出回填，回填后全绿；98 行既有读数覆盖回复/预算溢出/引用/别名/围栏/仅看本轮/知识/谈心/润色/主动/反思/向量/阶段/时间戳等全部旧装配面。**冻结测试红 = 实施立即停，不得改冻结表迁就。**

---

## 7. 测试证据

四组定向单测于 2026-11-12 真实 Gradle 实跑（测试结果 XML 计数），合计 **192 tests / 0 failures / 0 errors / 0 skipped**；`:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin`、`:app:assembleDebug` 全部通过（APK 产物在盘）。

| 组 | 测试类与计数 | 覆盖 |
| --- | --- | --- |
| 分域/事件库/uc/guard | `ChampionEngineTest` 20、`ChampionEventStoreTest` 11、`EventHistoryAssemblerTest` 7、`UncertainBlockDetectorTest` 14、`ContractGuardTest` 21、`ChampionPromptAssemblerTest` 9（小计 82） | 分域表 1/23/24→GUARD_SHORT、25/40→UC_LONG；多消息一轮仍计一轮；3600 CU/整条消息/不切半句/无 recap；稳定 UID/同 msgUid 去重/草稿拒绝入库；uc 逐字引用/确认关闭/无关不注入；guard 不编造/空 response 不伪造/无法修复保原文/模糊映射保持关闭 |
| 生产链路接线 | `GenerationEngineLiveAutoWiringTest` 8、`GenerationEngineChampionShadowScopeTest` 2、`GenerationEngineTimeoutLanesTest` 7（小计 17） | OFF 三连零调用；SHADOW 影子行为按冻结快照保持；LIVE_AUTO 第 24 轮历史块进原生 Prompt + guard 执行、第 25 轮 uc 注入 + 无 guard；"仅看本轮"全跳过；null 轮次回退；不跑影子；模型调用恰一次；guard 修改后 parser 收修复文本且 UI 与最终结果一致；guard 异常原文回退；取消信号原样传播 |
| Prompt 装配 | `PromptBudgetTest` 16、`PromptByteFreezeBaselineTest` 11、`PromptAssemblyOrderContractTest` 11、`PromptBuilderAdvisorNoteTest` 3、`PromptBuilderOngoingDirectiveIntentTest` 5、`CorrectionExtractionFilterTest` 25、`IntentIdeaBlockNoteTest` 6、`ReflectCorrectionsFilterTest` 7（小计 84） | 空参逐字节不变（§6 硬门）；历史块在军师备注后、聊天记录前的单调顺序；整块保留或移除；recent.md 不被挤出；KB 有/无两分支；引用与来源别名原行为；新安全声明在场且入资产指纹 |
| 提交边界 | `LoveBrainViewModelChampionCommitBoundaryTest` 5、`SingleOwnerContractTest` 4（小计 9） | 生成开始冻结模式/轮次且各只读一次；生成中改设置不影响本轮；话题记录成功才提交、失败不提交；msgUid=消息 id；显式 OFF 事件库零痕迹；scope 未命中零 IO |

**如实登记（不越过证据级别）：**

- 上述全部判定为"本地确定性测试 + 源码证据 + 构建产物"级；**真机行为级验收清单（60 项）待执行**——不得把单测通过写成真机已通过。
- 接线三笔提交（`afa9559f` / `61afbc92` / `a0104be0`）在本地 `closeout-20261106` 分支，**未推送**（待授权）。
- 提交边界三项断言（只提交 HER/ME、事件写失败不回滚产品保存、"仅看本轮"确认消息下一轮仍入库）由生产码结构（角色过滤、异常只记日志、提交点与生成期开关无耦合）+ 关联测试（入口拒稿、影子正向提交链）覆盖，暂无独立专测方法，登记为低优先补充项。

---

## 8. 未入选机制保留

所有未成为重点机制的候选与失败实验都保留在公开研究归档中（`research/candidates/`、`research/experiments/`、`research/refs/`），按"已验证、初步有效、失败、未判定、被替代"五状态整理。池上限与交付口径（来源 `research/refs/research_harness_20261110/overnight/FAILURE_ANALYSIS.md`）：96 条总均 4.60；**被选中（first-passing）**4.80；池最优（每位置取 max）4.95；b04 单发 4.63（按位均；按维均 4.68）；DIRECT 4.69。**"候选池最优不是用户最终实际收到的结果。选择器选中结果才是交付结果。"** 引用该表必须带原文件口径声明：跨行绝对分含审阅者间校准差 ±0.1–0.2，只有"同一评审计 + 同一样本"内行间差可直读；池内两行与单发臂不可直比。"被选中（first-passing）"行是固定序交付结果，多候选选择器本身的判定是另一次 12 位审计（5 更好 / 7 持平 / 0 更差），两者不得混同。

### 8.1 负结果逐项登记（判决原文件均在 `research/refs/experiment_protocol_20261023/long_probe_20261030/`）

| 候选/实验 | 判决与读数 | 归类与限定语 |
| --- | --- | --- |
| b2（`window_expand_b2`，账本式事实压制；`W14M02_JUDGMENT_VERDICT_20261102.md`） | H_SUP_null 触发 → 淘汰（不调参重跑）；触发位 LRE146_T20 亲读成立（"电池本身有毛病" + 无据归属"老板亲口说的"） | **失败（淘汰）**；历史实现原样保留。机制归因限定：判决归因为**"检测覆盖缺口，非压制失效"**（该篇压制段为空、b2 与父本输入逐字节相同、触发位两臂同暴露）——不得写成"b2 机制主动坐实" |
| W19 合并候选（`window_expand_b_norecap_uc_guard`；`W19CROSS_VERDICT_20261103.md`） | 不宣布开发集总冠军，双域冠军保留；长域 5 个零字节空回复（破门）、短域全过；故事级方向翻转 4/10 | **跨域候选未晋升**；目录保留为"完整候选装配"参考件。教训：两个机制分别有价值不等于组合后更好 |
| uc_retry（`window_expand_b_norecap_uc_retry`；`W21FULL_VERDICT_20261104.md`） | 同 Prompt 确定性重试修复率 12/12 = 100%（双臂对称）；Prompt +0.00%；重试调用另册记账；swap 翻转 3/10 | **跨域候选未晋升**；重试机制作为评测基建组件保留，不包装成新的质量冠军 |
| uc_attr（`window_expand_b_norecap_uc_attr`；`W22FULL_VERDICT_REV2_20261104.md`） | JSON 契约失败父本 11 → 候选 22（n=232/臂）；veto 19/29；swap 翻转 7/22；判决 KEEP_NO_PROMOTE | **保留实现、不晋升**。版本限定：读数全部来自 **REV2（取代同日 REV1——REV1 判决与 G3 读数已被红队审计推翻，引用必须带 REV2 标签）**。结论：不能为了降低归因错误，接受更严重的输出契约回归 |
| CF1（反事实跟随对照；`CF1_VERDICT_20261102.md`） | 跟随率 0.681 vs 0.698，双臂均落 0.60–0.75 不确定带；不判 PASS、不淘汰任何臂、不补跑 | **未判定。**"保留原始结果，不把它包装成胜出机制。" |

### 8.2 新研究线未入选候选（判决轨迹来源 = overnight 台账三件）

| 候选 | 判决轨迹 | 归类 |
| --- | --- | --- |
| rh_b04_anchored_layout | 早期 KEEP（60 位稳住）；看门狗追溯将早期循环判决降级 | 初步有效 → 降级待复验 |
| rh_b09_extractive_slim | 端到端延迟最低；亲读补充实锤"丢了单杠梗回调"（氛围损失首例）；同组降级 | 初步有效带已知损失 → 降级待复验 |
| rh_b06_tiny_cascade | 全部回复"盲答"（A 类实现 Bug）；升级率 45.8% 与 Token 全场最高；路线撞墙 | 失败（淘汰） |
| rh_b11 | 约 1k Token 用户段无可测排序差异，UNCERTAIN（偏淘汰） | 未判定（降级为装配卫生规范） |
| rh_b15_multicast_validator | 池天花板 +0.32、零负位、选择器 +0.12/位；成本约单路 2.13 倍；同组降级 | 初步有效（高成本）→ 降级待复验 |
| rh_b15b_selector | 12 位审计 5 更好 / 7 持平 / 0 更差；同组降级 | 初步有效 → 降级待复验 |
| rh_b12_invalidation_track | 机制按预期工作，n=24 上无质量分离，UNCERTAIN | 未判定 |

早期循环限定（引用上表任何"KEEP"前必读）：**相关早期循环在看门狗复核后降级为 PROVISIONAL，不作为本项目当前核心机制的主结论。**

### 8.3 五状态归类总表

| 状态 | 条目 |
| --- | --- |
| 已验证（保留为最终重点机制） | 双域冠军 uc（>24 轮）/ guard（≤24 轮）、3600 CU 事件流 + norecap、rh_converge（判决 KEEP，效率型定位，§2.3） |
| 初步有效 | rh_b04 / rh_b09 / rh_b15 / rh_b15b（均已降级 PROVISIONAL，须合规循环复验后才可升级表述） |
| 失败 | b2（H_SUP_null 淘汰）、rh_b06（撞墙淘汰）、W19 合并候选与 uc_retry（跨域未晋升）、uc_attr（KEEP_NO_PROMOTE） |
| 未判定 | CF1（不确定带）、rh_b11、rh_b12 |
| 被替代 | `window_expand_b`（首版冠军）→ `_norecap`（W15N01 更替）→ 分域冠军更替链；W22 REV1 被 REV2 取代 |

---

## 9. 机制—判决—代码—产品状态映射总表

| 机制 | 判决文件（`research/refs/…`） | Arena 代码（`research/…`） | Kotlin 代码（`app/…/`） | 产品状态 |
| --- | --- | --- | --- | --- |
| 3600 CU 完整消息历史窗口 | FLJUDGE / LD1 / W15N01 | `candidates/window_expand_b_norecap{,_uc,_guard}/mechanism/mechanism.py`（`MAX_EVENT_HISTORY_CU = 3600`） | `domain/champion/EventHistoryAssembler.kt` :25/:45 | LIVE_AUTO 两域均注入正常回复主请求 |
| 真实消息事件流（草稿不入流） | 同上（配套） | 各候选 `MECHANISM.md`"草稿永不写回事件流" | `domain/champion/ChampionEvent.kt` :141/:146/:163、`ChampionEngine.kt` :310 | 确认后提交点唯一入库；接线后口径不变 |
| 删除无贡献 recap | W15N01 | `candidates/window_expand_b_norecap*/` | （无独立移植件，随历史块设计不含 recap） | 历史块装配无 recap（`EventHistoryAssembler` 无摘要段） |
| 未确认事项约束 uc | W16 / W19（分域维持） | `window_expand_b_norecap_uc/mechanism/uncertain_rules.py` | `domain/champion/UncertainBlockDetector.kt` :23/:24/:183/:224 | LIVE_AUTO 仅 ≥25 轮（UC_LONG）注入 |
| 确定性输出契约守卫 guard | W17B / W17C / W19（分域维持） | `window_expand_b_norecap_guard/mechanism/contract_guard.py` + `_vendor/` | `domain/champion/ContractGuard.kt` :45/:65/:784 | LIVE_AUTO 仅 ≤24 轮（GUARD_SHORT）于 parse 前执行 |
| 持续事项选择/场景/意图/预算装配 | 产品原生（架构核对，见 `research/architecture/ARCHITECTURE.md`） | rh_converge 重组验证 | `domain/OngoingContextSelector.kt` 等 12 件 | 一直在正常回复链路生效 |
| 整合 Harness rh_converge | `overnight/` 台账三件（`LIVE_LOOP_LOG.md` 对应完成节；rh_converge 判决行 + `RESUME_STATE.json` + `WATCHDOG_VERDICT.json` 对应条目） | `tools/benchmark_v1/prototype_candidates/rh_converge/` | 无移植件 | **未接线（研究线结论）** |
| LIVE_AUTO 分域生产档 | W16 + W17C + W19 组合判决的程序化落地 | （无 Arena 侧对应物——分域策略是产品接线设计） | `ChampionEngine.kt` :41/:61-73/:245/:310、`GenerationEngine.kt` :163/:328、`LoveBrainViewModel.kt` :1237/:1608、`SecurePrefs.kt` :371 | 已落码默认启用（真机待验、推送待授权） |

---

## 10. 文档纪律自检

| 检查项 | 结论 |
| --- | --- |
| 零哈希 | 本文不计算、不引用、不比对任何文件完整性哈希值；字节冻结门以"内容指纹与字节数"描述测试机制、只登记字节量读数；代码内部行为描述"事件存储文件名使用键哈希防碰撞（`ChampionEvent.kt` :89-97）"属源码行为描述，未展开算法名与任何哈希值；`afa9559f`/`61afbc92`/`a0104be0` 为 git 提交标识，非完整性校验 |
| 限定语整句保留 | ① rh_converge"效率型整合候选/不应写成全面击败 DIRECT 的质量冠军"整句落位（§2.3）；② recap 依据用判决原口径"6 篇/仅在 T34+/≤199 CU/全程为空"（§3.3）；③ FLJUDGE/LD1 父本读数一律标"滑窗父本（r9_slim）"，不写"对照"（§3.3）；④ W22 标 REV2 取代 REV1（§8.1）；⑤ b2 带"检测覆盖缺口，非压制失效"归因（§8.1）；⑥ 四数表带"被选中（first-passing）"行名 + ±0.1–0.2 校准差与不可直比声明 + 交付口径整句（§8）；⑦ CF1"保留原始结果，不把它包装成胜出机制"整句（§8.1）；⑧ 早期循环降级整句（§8.2）；⑨ 晨报口径整句（§2.3）；⑩ DIRECT 4.69 口径注记（§2.3/§8）；⑪ 成绩表隔离整句（§1）；⑫ W15N01"无损可删/不是 recap 有害"整句（§3.1）；⑬ W17B"8/9 正"不写成"9/9"、离线回放与 live 分口径（§4.3）；⑭ uc 实现口径"预算截断保最新，无显式相关性评分"（§4.1） |
| 不利读数如实并列 | rh_converge e2e 42s vs 20s、Prompt +0.78%/+0%、九维 8/9（initiative −0.058）、b15 成本 2.13 倍、W19 长域空回复、W22 契约失败 11→22 均如实登记 |
| 三实现状态不混写 | 每条主线按"Arena 研究原型 / Kotlin 移植件（SHADOW 影子链路）/ 生产接线（LIVE_AUTO 主链路）"分栏；rh_converge 明示未接线；接线级表述全部注明"代码与本地确定性测试级，真机待验" |
| 零内部任务编号 | 全文出处只写本仓相对路径、判决文件名、测试类名与 git 提交标识；不出现内部任务书编号、阶段/批次编号与内部审计件编号。"LOOP-7/LOOP-14"仅出现在晨报口径限定整句内——该句为必须逐字携带的限定语，其中标识符是 `overnight/` 台账公开文件的节名，同 W16/W17C 判决文件名的引用口径；除该句外全文零 LOOP 编号 |
| 数字溯源 | 全部实验数字可按文件名 + 路径直查判决原文件；机制行号实测于 2026-11-12（产品分支 `closeout-20261106` @ `a0104be0`，工作树干净；研究树落盘副本与源侧逐件同内容） |

*本文为核心机制详解与判决映射，与 `research/architecture/ARCHITECTURE.md`（架构与接线）、`research/PAPER.md`（研究论文）互为引用，不重复承载环境与复现说明（见 `research/README.md`）。*
