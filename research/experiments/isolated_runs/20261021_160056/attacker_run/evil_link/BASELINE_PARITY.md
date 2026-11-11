# BASELINE_PARITY — 冻结原产品主链路参考基线（工作包 B / G04）

日期：2026-10-21。原则：基线保真 = **与原产品相符**，不是基线通过全部能力题；
不为对拍添加原产品没有的能力；原产品本来不检索长期档案，如实保留。

## 1. 冻结来源版本（sha256，2026-10-21 实算）

参照：`D:/LoveBrain`（Kotlin/Android 工作树）。被测主链路组件：

| 原文件 | sha256 |
|---|---|
| domain/prompt/ChatTranscriptBlock.kt | `f8397740…d14e` |
| domain/prompt/IntentIdeaBlock.kt | `ed27e023…92e` |
| domain/prompt/CurrentSceneInjection.kt | `2f342778…2ab2` |
| domain/MemoryCorrectionPolicy.kt | `6cfc2711…1adf` |
| data/KnowledgeMemoryStore.kt | `07cfa07e…4423` |
| domain/OngoingContextSelector.kt | `ac642635…69d6` |
| domain/PromptBuilder.kt | `b08b2a08…e19` |
| model/Models.kt | `3b3f48a9…319` |
| AppConfig.kt | `b865c790…43e5` |
| test/.../MemoryCorrectionPolicyTest.kt（预期来源） | `a4bb5dc2…d146` |
| test/.../OngoingContextSelectorTest.kt（预期来源） | `e0be2c45…7871e` |

参考移植与夹具：

| 文件 | sha256 |
|---|---|
| candidates/reference_product_v1/reference_chain.py | `5ee21efd…1737` |
| tools/test_baseline_parity.py | `a5460b1a…1422` |

## 2. 实现方式（§4-B2）

- 原实现为 Kotlin/Android，本环境无构建运行条件 → 采用 **Python 参考移植**，
  逐函数引用原文件+行号；预期值来自**原仓库源码逐字常量与自带单元测试**
  （MemoryCorrectionPolicyTest、OngoingContextSelectorTest、PromptByteFreezeBaselineTest）。
- 模型输出环节用固定原文替身，无采样噪声混入机制对拍。
- 未顺手替原产品修 bug：发现的差异只登记（见 §4），不改原语义。

## 3. 对拍结果（tools/test_baseline_parity.py，26 夹具 / 9 多轮）

| 能力（§4-B3） | 夹具 | 结果 |
|---|---|---|
| 静态资产与动态装配 | B-T01/T02/T03（T03 多轮长会话掐尾） | PASS |
| 意图/备注/阶段开关 | B-T04/T05（有效性四态、军师备注逐字） | PASS |
| HER/ME/IDEA 与来源 | B-T01（IDEA 不占号不冒充来源） | PASS |
| 场景判定（优先级/主体） | B-T06/T07/T08/T09 | PASS |
| 四种纠正 + revision 单调 | B-T10~T14（T12/T13/T14 多步/恢复） | PASS |
| ongoing（冷却/休眠/完成取消/词表） | B-T15~T25（T17/T19/T21/T24/T25 多轮） | PASS |
| 时效与常量边界 | B-T26（2h/2 条/60 条/3 轮/5 轮） | PASS |
| 重复提交/异常与恢复 | B-T14（写失败不写 revision+坏 JSON） | PASS |
| 图片和输出解析 | B-T02（JSON 转义防注入；图片出站属 E11 真实集成验证范围） | PASS |

执行：`python tools/test_baseline_parity.py` → `BASELINE PARITY PASS: 26 fixtures (9 multi-turn)`。
证据清单：`refs/arena_v1/baseline_parity_fixtures.json`。

## 4. 题库接口适配登记（§4-B2）

| 原形（原产品） | 适配后（考场题面） | 理由 | 双方可见条件 |
|---|---|---|---|
| intent = `moment/intent.json`（IntentConfig，enabled/status） | 题面 `input.intent = {text, enabled, status}` 直接映射 | 同一颗 isIntentEffective 判据 | 双方同源同判 |
| may_decline 不存在于原产品（无"允许不回"概念） | 题面 `input.may_decline` 轮级许可 | 考场协议能力；基线不消费此字段 | 仅候选契约消费；基线对比不计该维度 |
| IDEA = 本轮用户想法（replyDirective） | 题面 IDEA 消息行 | 同义映射 | 双方均不进围栏/不占编号 |
| 时钟 = 系统时钟（TimeFmt） | 题面固定 clock 字段 | 确定性对拍需注入固定时钟（B2 允许并记录） | 双方一致 |

适配新增能力（may_decline 等）**不**归为候选或产品的已证明优势（§4-B2）。

## 5. 差异与排除声明

1. **图片实际出站**：原产品经 Android 层发送；本环境无法真发 → 图片出站归 G11
   真实集成验证，本轮以媒体登记/散列单向流验证（test_input_boundary 已覆盖）。
2. **后台引擎**（谈心/主动开场）不在 B1 范围，未删除任何影响比较的逻辑。
3. Kotlin 单元测试未在本机执行（无 Gradle/SDK）——预期值转写自测试文件与源码逐字
   常量；若需构建级对拍，属外部缺件（见 SCOPE_AND_STATUS §6）。
4. 考场侧 infer_scene（arena/lb_assembly.py）的 Wave15/16 修复属**候选侧共同工程**，
   与参考基线（原产品语义）的差异是设计使然，不构成基线失真。

## 6. 2026-10-22 跨轮基线补齐（复核 v4 §3 唯一核心收口）

**为什么补**：此前 `mechanism.py` 明确排除 TopicRecorder，run_turn 只组装+调用+
turn_count+1，不解析本轮 analysis 写回。于是"拿候选赢一个没有跨轮能力的基线"
不是公平比较——原产品在 `nextRound` 里本来就会把本轮消息与 analysis 提交入库，
下一轮装配再从 `moment/*` 读回来。

**补了什么**（`candidates/reference_product_v1/product_memory.py`，逐函数对照
TopicRecorder/RecentRoundStore/SceneChainStore/OngoingPlanStore/
KnowledgeTopicTextService/SceneChainInjection）：

| 投影 | 原文件（行号） | 考场等价 |
|---|---|---|
| 话题归档 + 话题标签 | TopicRecorder.kt L188-212 | status=new 且有旧话题 → 旧话题进 `memory/raw_topic.md`、清 recent/scene；same/drift/new 的标签落 `moment/topic.md`（`- [t] 正在聊：X`） |
| recent.md | RecentRoundStore.kt L39-117 | 只记 HER/ME 实际消息 + marker 去重；留最近 2 轮，溢出进 `memory/raw_chat.md`；候选回复不冒充已发送（scheme=null） |
| scene.md | SceneChainStore.kt L85-330 | 来源校验（m<i> 别名→冻结快照 HER/ME）、同来源新文本原地替换、幂等、TTL 2h、最多 2 条、溢出进 `memory/raw_scene.md` |
| plan.md | OngoingPlanStore.kt L47-230 | 稳定 itemId、状态链合并、幂等、终态不复活（除非带新证据）、链截断 10 条、双分区 |
| 轮次计数 | TopicRecorder 投影 6 | `memory/kb/.meta.json` 的 turnCount（产品在 KB 元数据里） |

读侧同步改：`_knowledge_block` 不再读 `initial_kb.recent`，改为产品的六段结构
（【懂得】画像 / 阶段策略 / 【记忆】经验教训 / 【此刻】话题龄+当前话题+场景状态链 /
# 最近对话 / 【进行中事项】），其中此刻、最近对话、进行中事项**全部来自写回文件**；
`read_plan_active` 改读 `moment/plan.md` 的「## 进行中」（此前读的是自造的
`plan/plan_active.md`，属移植偏移，一并纠正）。

**没搬什么（如实登记，见 product_memory.py 文件头）**：RoundCommitJournal 的
WAL/崩溃恢复重放、FactSubjectResolver 三层主体解析（不写 `subj=`）、
Kotlin `hashCode` 型 itemId（改用 sha1 前 8 位，只要求跨轮稳定）、
archive 四步事务与知识库重估触发、系统时钟（考场用题面 clock，冻结时序）。

**证据**：`tools/test_baseline_carryover.py` 7/7（`logs/baseline_carryover_result.json`）：
①第二轮 prompt 真的读到上一轮 recent/scene/topic/ongoing；②换故事不串；
③snapshot→restore 后仍读得到；④非法来源事实被拒；⑤无 analysis 时零写入；
⑥终态不复活、填充词不落盘；⑦话题轮转归档且清空此刻层。
另有隔离链路实跑：`iso_run --adapter mock --stop-after 2` 的 mechanism_notes
显示 turn_02 `prompt_has=...,recent,...`（写了也读了，不是只落盘）。

**版本身份**：补基线是**基线代码变更**，旧散列不得再用。
product_memory.py `fee3b418beebc104…`、mechanism.py `6c5f4b366df3c676…`、
test_baseline_carryover.py `cbb74718bf09661f…`（首次 16 位，全量见 release.json）。

## 7. 2026-10-23 首场实测后的三处基线纠错（不改能力，只把基线拉回原版应有水平）

首场 24 位跑完（`logs/exam1_20261023`，身份 `399c82b7…`）后逐位复盘，发现三处
"基线比原版更弱"的地方。它们不是产品弱点，是移植没搬完，会让"赢过基线"变得廉价。

| # | 症状（实测证据） | 原版对照 | 修法 |
|---|---|---|---|
| 1 | 话题轮转只往 `raw_topic.md` 落 `## [t] 标题`，被清空的对话正文永久消失 | `KnowledgeArchiveService.archiveEntry` L131-150：`# [t] 话题` ＋ `## [t] 状态变化`（mergeSceneEntries）＋ `### [t] 对话记录`（rawChat+recent） | 新增 `archive_entry()`，轮转按原版格式落完整条目 |
| 2 | `SandboxStorage.write_file` 不剥 `memory/` 前缀，`path()` 剥 → 写 `kb/memory/x`、读 `kb/x`；`_append` 永远读到空串，第二次归档覆盖第一次 | 产品只有一套 `KnowledgeDocumentStore.resolve`，读写同一路径 | `write_file` 统一走 `self.path()` |
| 3 | 题面每轮从 `m0` 编号，用序号当 id → 两轮消息数相同则 `recent.md` 标记撞车，整轮不落 recent（首场 5/24 位） | 产品消息 id 是真实 UUID，天然不撞 | id 改 `t<seq>_<i>`；幂等判据改"标记＋正文都相同才跳过" |

**仍与 Kotlin 不完全逐字节**：`mergeSceneEntries` 的合并策略只做去重拼接（不按时间戳
归并、不把无时间戳的 legacy 行留尾）；archive 四步事务状态机（WAL/中断恢复）与
`kb.json` 的 `topicCount` 回填仍不搬。

**证据**：`tools/test_baseline_carryover.py` **9/9**（原 7 项 ＋ `t8` 同消息数两轮、
`t9` 轮转归档必须含对话正文）。G04 门禁 observed 现为 `carryover rc=0（9 项…）`。

**身份**：再次变更，`399c82b7…` 与本节之前的所有散列作废；重测需新授权（首场额度已用尽）。
修复后 `reference_product_v1` identity = `9301d89902611020…`；由其派生的第一代改进候选 `v2_recall` = `e4707534cd606156…`。
离线门禁：`logs/arena_v1_acceptance/20261023_exam1_report` → OVERALL: OFFLINE_READY。

## 8. 2026-10-23 合并工作包（rubric v0.5 / AGENTS v1.4）：三项移植补齐 + 范围限定

依据 `refs/fair_baseline_20261023/FAIR_BASELINE_AND_REFACTOR_GUIDE.md` §2.2-2.3，本批把
影响公平比较的三项"基线比原版弱"补齐；不复刻安卓 UI、不重建考场、不新增真实调用。
本节所有改动均为**基线代码变更**，旧 §7 之后的散列**再次作废**。

| # | 移植偏移（fair_baseline §2 明示） | 原版对照 | 本批修法 | 回归证据 |
|---|---|---|---|---|
| 1 | `run_turn` 直连拼块，未过 `PromptBudget.byBlocks`；长上下文不裁 | PromptBuilder.kt L242-258 + PromptBudget.kt L21-84，TOTAL_BUDGET=9000（UTF-16 长度目标） | 新增 `rc.by_blocks(...)` 三阶段语义移植（知识段尾 → 旧 recent → 对话头部整行）；`run_turn` 装配走 by_blocks；边界浮动与 Kotlin 一致（+17~40 属原版裁尾提示本身长度） | `tools/test_baseline_parity_extra.py` e1/e2/e3/e4，e10 |
| 2 | `_knowledge_block` 读 `corrections` 却未用它做逐项过滤 | PromptKnowledgeSection.kt L62-149 逐段调 `MemoryRefPolicy.isCorrected` | 画像四段/经验/场景链/进行中事项各自 `make_ref` → `is_corrected`；WRONG 补 replacement 提示、MUTED 标"暂停主动提及可被动回应"、WRONG_PERSON/FINISHED 静默跳过 | e5/e6/e7/e8 |
| 3 | `_timestamp_block` 只出 `# 当前时间\n<ts>`，缺"基于当前时间/禁止臆测/自然贴合时段/系统时钟"三条行为约束 | PromptBuilder.buildTimestampPrompt L338-341 逐字 | 新增 `rc.build_timestamp_prompt(clock)` 逐字对齐；run_turn 装配用它 | e9、e10 |

**范围限定（本批仍未覆盖的能力，如实登记）**（fair_baseline §2.4）：

- **FactSubjectResolver 三层主体解析**：本批仍不搬；说话人（HER/ME）与事实主体（第三方）
  在 `product_memory.py` 里合并处理，涉及"同一 HER 谈自己 / 谈 ME / 谈第三人"的替换、
  纠正与污染相关的比较**不在本批判决范围**。跨主体场景的保护样本留扩题（协议 §3.2 已列）。
- **Kotlin RoundCommitJournal WAL / 崩溃恢复重放**：本批仍以单进程 `snapshot→restore`
  验证跨轮读写一致性；进程崩溃后的**磁盘半写恢复**不测。父本 `reference_product_v1`
  与候选 `v2_recall` 共用同一恢复语义；候选侧只验证 `RecallSelector` 恢复后仍在，
  不声称与 Android 完整事务等价。
- **话题轮换后的关系维度 / 经验画像后台重估触发（KnowledgeTriggerCoordinator）**：
  **本批主动限定为前台回复、排除后台**——不是因为"短故事未达 3/5 话题阈值"
  （reviewer 独立复核：E001 实测 8 轮里 7 轮 topic_status=new，阈值不构成不触发理由），
  而是本批 scope 明确只覆盖 `run_turn` 主链、不动 `KnowledgeTriggerCoordinator`。
  任何"候选比原版省 token / 快"的判决**不包含后台重估的调用**，
  需要长程连续故事批次单独测。
- **谈心 / 主动开场 / 润色三种工作模式**：本批沿用 §2 只测常规回复。`may_decline`、
  `no_reply` 属候选契约扩展能力，父本不消费；两者比较时须单列，不得据"候选支持、
  父本不支持"造出对照优势（fair_baseline §4 明写）。

**新版身份**（本批冻结，替代 §7 之后的 9301d899 / e4707534）：

| 对象 | identity_sha256 前 16 | 说明 |
|---|---|---|
| `reference_product_v1`（合并工作包后） | `b91d9ef44a4cbaf0` | 装配走 by_blocks；纠正过滤到位；时间戳逐字；父本 bundle = mechanism + reference_chain + product_memory + assets/system_prompt/*.md |
| `v2_recall`（四组保护收口后） | `07c6f4f33c717dd7` | P1 last-msg-only recall 判定；P2 RecallSelector bigram 相关性 + 排除 FINISHED/CANCELLED；P3 MAX_NEW_CONTEXT_CHARS=1800；P4 restore 重装 wrapper。bundle = v2 mech + 父本 bundle + v2 assets |
| `arena/lb_scorer.py` + `arena/protocol_public.py` | `39b6faeba013eebe` | v0.5 note_lcs 拆 `lcs_ratio` / `lcs_ratio_symmetric`，仅后者换 max(len) 分母 + 0.6 阈值 + 短文本豁免；style_diversity 保持旧语义 |

**离线门禁**（本批全部通过）：

- `python tools/test_baseline_parity.py` → **BASELINE PARITY PASS: 26 fixtures (9 multi-turn)**
- `python tools/test_baseline_parity_extra.py` → **BASELINE PARITY EXTRA: PASS (10 checks)**
- `python tools/test_baseline_carryover.py` → **CARRYOVER RESULT: PASS (9 checks)**
- `python tools/test_v2_recall.py` → **V2 RECALL RESULT: PASS (11 checks)**（c1-c5 沿用 +
  c6 P1 反例 / c7 P2 30 项灌入 / c8 P3 预算 / c9 P4 restore / c10-c11 摄影与出差两领域）
- `python tools/rescore_offline.py --exam logs/exam1_20261023` → 24 位旧答卷新尺离线重评，
  仅 E002_T02 与 E002_T05 从 7.5 → 10.0（v0.5 修的正是这两处 note_lcs 误伤，
  其他 22 位分不变）；总程序均分 9.31 → **9.51**（**协议变更 ≠ 候选收益**，
  见 `logs/exam1_20261023/RESCORE_SUMMARY.json`）。

**判决边界**：本节所有事实来自代码 diff 与离线测试输出，不来自模型；候选 vs 基线的
"整体优于原版"**仍未证明**——那需要 §5 冻结清单在有效新授权下的两臂交错实跑对照，
本报告只提交清单不发真实调用。
