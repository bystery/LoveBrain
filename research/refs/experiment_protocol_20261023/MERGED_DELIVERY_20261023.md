# LoveBrain · 训练执行会话 · 合并交付 2026-10-23

**交付对象**：独立复核会话（本窗口之前的角色）。
**执行范围**：训练启动 prompt 步骤 1-3 全量落地，步骤 4 冻结清单已就位；
步骤 5（独立出题/扩题）本批未做，登记在"尚缺证据"。
**授权状态**：`AUTH-EXAM1-20261023-01` 已 24/24 用完关闭；本批**未发出任何真实模型调用**；
`logs/auth_budget/*.jsonl` 尾行 `authorization_closed` 保持。
**修改边界**：只改斗兽场 `D:\lovebrain-arena` 内 harness / 测试 / 评分 / 直接冲突规范；
`D:\LoveBrain` 原版只读；不重开账户/防火墙/ACL。

## 0. 三句话

1. **父本 `reference_product_v1` 补三项移植偏移**（byBlocks 预算、纠正过滤、时间戳逐字），
   与 `refs/fair_baseline_20261023/FAIR_BASELINE_AND_REFACTOR_GUIDE.md` §2.2-2.3 对齐；
   未覆盖能力（FactSubjectResolver/WAL/后台重估/谈心-开场-润色）在 §8 明列范围限定。
2. **候选 `v2_recall` 四组保护收口**（P1 末条真实消息才激活回忆、P2 bigram 相关性
   排除无关 DORMANT/终态、P3 `MAX_NEW_CONTEXT_CHARS=1800` 新增段预算、P4 `restore()`
   重装 `RecallSelector`），11 项离线回归 c1-c11 全绿；E001 杯子之外的**摄影/出差**两
   领域正反例（c10/c11）证明规则**未按题目专名特判**。
3. **rubric v0.5 + AGENTS v1.4 + OBJECTIVE v1.4 同步**：程序分不再是唯一门禁，成本/时延/
   污染/可维护性恢复为并列门禁；note_lcs 分母 max(len) + 0.6 阈值 + 短文本豁免；
   `exam_layered_score.py` 落逐槽 primary/backup + 三汇总指标；**旧 24 位离线重评**
   E002_T02/T05 从 7.5 → 10.0（v0.5 修的正是这两处误伤），程序总均分 9.31 → **9.51**——
   **协议变更 ≠ 候选收益**，两口径并列报告。

## 1. 修改文件清单（一手证据）

### 1.1 父本 `reference_product_v1`

| 文件 | 变动 | 关键片段 |
|---|---|---|
| `candidates/reference_product_v1/reference_chain.py` | 新增 156 行：`TOTAL_BUDGET=9000` / `utf16_len` / `by_blocks`（PromptBudget.kt L21-84 三阶段）/ `build_timestamp_prompt`（PromptBuilder.kt L338-341 逐字）/ `make_ref` + `make_ongoing_entry_ref` + `is_corrected` + `_is_mute_expired`（MemoryRefPolicy.kt L33-119 逐语义） | L446- |
| `candidates/reference_product_v1/mechanism/mechanism.py` | `_knowledge_block` 重写：画像四段/经验/场景链/进行中事项分别过 `is_corrected`；WRONG 补 replacement / MUTED 加被动回应标记 / WRONG_PERSON 静默隔离 / FINISHED 不注入。`_timestamp_block` 改用 `rc.build_timestamp_prompt`。`run_turn` 装配从直连 `+` 改为 `rc.by_blocks(...)` | L189- |

### 1.2 候选 `v2_recall`

| 文件 | 变动 |
|---|---|
| `candidates/v2_recall/mechanism/mechanism.py` | 全量重写。①`is_recall_turn` 只看末条真实消息（P1）；②`RecallSelector.select_for_injection` 用 bigram 交集判相关（`_MIN_BIGRAM_OVERLAP=2`），排除 FINISHED/CANCELLED（P2）；③`MAX_RECALL_BLOCKS=2 / MAX_RECALL_CHARS=1200 / MAX_NEW_CONTEXT_CHARS=1800` 三层预算，`_cap_new_render` 装配前裁（P3）；④`restore()` 显式重装 `RecallSelector`（P4）；⑤`run_turn` 不再 monkey-patch 父本 `_knowledge_block`，改为显式 `_wrap_recall_into_knowledge`，装配走 `rc.by_blocks` 与父本一致 |

### 1.3 评分层

| 文件 | 变动 |
|---|---|
| `arena/lb_scorer.py` | 拆 `lcs_ratio`（旧语义，style_diversity 沿用） / `lcs_ratio_symmetric`（v0.5 note_lcs 用）；C2 note_lcs 用新公式 + `NOTE_LCS_MAX=0.6` + `NOTE_LCS_MIN_LEN=10`；desc 明写"v0.5 复读机测试"；其余 12 检查点字节不动 |
| `tools/exam_layered_score.py` | 加 `_extract_slots` / `_slot_semantic` / `BACKUP_SEVERE` 覆盖表；每 row 落 `primary_slot / primary_text / primary_semantic / primary_semantic_note / backup_slots / backup_semantic_per_slot / at_least_one_pass / backup_with_severe_error / slot_parse`；episode 与 total 落 `primary_semantic_pass_rate / at_least_one_pass_rate / severe_error_backup_rate`；`EXAM_SCORING_FILE` 环境变量允许指向 scoring_v05.json |
| `tools/rescore_offline.py` | 新工具。逐 turn 从 `calls.json` 取 raw_text，用当前 `lb_scorer` 重跑，落 `scoring_v05.json` 到每 turn 目录；写 `RESCORE_SUMMARY.json` 记录 old/new 分数与失败项 diff；不覆盖原 `scoring.json` |

### 1.4 测试

| 文件 | 变动 |
|---|---|
| `tools/test_baseline_parity_extra.py` | 新增。10 checks：e1-e4 by_blocks 语义 + e5-e8 四种纠正动作 + e9 时间戳逐字 + e10 run_turn 集成 |
| `tools/test_v2_recall.py` | 追加 c6-c11（6 新）。c6 P1 反例（同 send 旧句"之前"+新句"午饭"不激活）；c7 P2 30 项灌入；c8 P3 预算；c9 P4 restore；c10 摄影领域正向；c11 出差领域正向。c1-c5 保留原断言 |

### 1.5 规范

| 文件 | 变动 |
|---|---|
| `benchmark/rubric.md` | 升 v0.5；顶部沿革保留 v0.2/v0.3/v0.4；§1 表新增层 4 资源与层 5 可维护性；§2 note_lcs 阈值改 0.6 + max(len)；§4 首选/备选分审落到 LAYERED_SCORE；§5 成本恢复为并列门禁；§6 报告扩到九数；§7 晋级流程按多目标判决；§8 版本纪律新增 |
| `AGENTS.md` | 升 v1.4；顶部保留 v1.1/v1.2/v1.3；新增 v1.4 五条变更（成本/时延/污染/可维护性并列门禁、程序分非唯一门禁、首选备选分审、协议变更≠候选收益、gold 覆盖度诚实登记） |
| `OBJECTIVE.md` | 升 v1.4；顶部加变更说明；成长阶梯后加"v1.4 补充"三条并列门禁；其余保留 |
| `refs/arena_v1/BASELINE_PARITY.md` | 追加 §8：本批三项移植 + 四项范围限定 + 新版身份 + 离线门禁 + 判决边界 |

**未改**：`benchmark/dev_pilot_v02/*` 24 题与 24 卡（内容层未动，扩题留后续）；
`D:\LoveBrain/*` 原版；`arena/lb_assembly.py` 与 `arena/lb_gateway.py`（考场侧主链）；
`arena/protocol_public.py`（协议契约不变）；`refs/experiment_protocol_20261023/*`
（本交付只 append 两份新文档，未改协议/prompt 原文）。

## 2. 基线差异核销表（对齐 fair_baseline §2.2-2.4）

| fair_baseline 编号 | 移植偏移 | 本批状态 | 一手证据 |
|---|---|---|---|
| §2.2 PromptBudget.byBlocks 未接主请求 | ✅ 收口 | `rc.by_blocks` 装配 + `run_turn` 走过；`test_baseline_parity_extra.py` e1/e2/e3/e4/e10 全 PASS；边界浮动 +17~40 与 Kotlin 原版一致（fair_baseline 明写"它自身的边界问题也应保留并登记"） |
| §2.3 知识段读 corrections 却不用 | ✅ 收口 | 画像 4 段 / LESSON / SCENE / ONGOING 逐条 `make_ref`+`is_corrected`；e5 WRONG / e6 MUTED / e7 WRONG_PERSON / e8 FINISHED 全 PASS |
| §2.3 时间戳块缺三条行为约束 | ✅ 收口 | `rc.build_timestamp_prompt` 逐字（PromptBuilder.kt L338-341）；e9 精确字符串断言；e10 装配里出现"【当前时间】"而非旧"# 当前时间" |
| §2.4 FactSubjectResolver 主体解析 | ⚠️ 未搬，本批范围限定 | BASELINE_PARITY §8 明列；跨主体场景判决限定为"本批未测" |
| §2.4 Kotlin WAL / 崩溃半写恢复 | ⚠️ 未搬，本批范围限定 | 同上；跨 snapshot→restore 内存语义一致（c9 PASS），磁盘半写不测 |
| §2.4 后台重估触发（KnowledgeTriggerCoordinator） | ⚠️ 不触发，本批范围限定 | 短故事未达 3/5 话题阈值；§8 明列成本比较不含后台 |
| §2.4 谈心 / 主动开场 / 润色三模式 | ⚠️ 不测，本批范围限定 | §2 范围沿用常规回复；`may_decline / no_reply` 属候选扩展能力，父本不消费（fair_baseline §4 明写不得据此造对照优势） |

## 3. v2 四组保护 + 三领域回归（一手证据）

| # | 保护 | 反例测试 | 结果 |
|---|---|---|---|
| P1 | 旧句"之前"+ 新句"午饭" 不误触发当轮回忆 | `test_v2_recall.py::c6_no_cross_message_recall_activation` 直接调 `is_recall_turn` | PASS |
| P2 | 30 条无关 DORMANT/ACTIVE 项 + 1 条相关 ACTIVE → extras ≤ 2 且相关事项必在 | `c7_no_dormant_whole_dump` 种 plan.md 30+1，跑 RecallSelector | PASS，extras=1 |
| P3 | 归档 body 远超预算时装配段 ≤ `MAX_NEW_CONTEXT_CHARS + 400` | `c8_total_new_injection_budget` 种 60×长归档，跑 run_turn 量段长 | PASS，seg_len=1521 cap=2200 |
| P4 | snapshot→restore 后 RecallSelector 仍生效，回忆轮仍能注入 | `c9_restore_keeps_recall_wrapper` 跑 T01-T05 snapshot，restore 后跑 T06 断言【回忆】在 | PASS |
| 领域 1 · 陶艺（E001 沿用） | c1-c5 | 断言 T06 prompt 出现"杯底/刻字/不刻"、其它轮不注入、E003 prompt 与父本逐字节相同 | PASS |
| 领域 2 · 摄影（新） | c10_positive_photo_exhibit_domain | 种 raw_topic.md 一条看展归档 + plan.md "河边照片后期" → 回忆轮召回原话 + ongoing 旁路接上 | PASS |
| 领域 3 · 出差（新） | c11_positive_business_trip_domain | 种 武汉出差 光谷酒店 归档 → 回忆轮召回 + 旁路接上 | PASS |

**未按题号/车次/杯子专名特判**（协议 §4.2 + 启动 prompt 步骤 2）：c10/c11 用两个未参与
E001 调参的生活领域验证；规则是 `_bigrams(name+chain) ∩ _bigrams(recall_clue) ≥ 2`，
不含"陶瓷杯""14:55"等答案词。

## 4. 评分与量尺收口

### 4.1 三项修订

- **rubric v0.5 门禁语义**：程序 veto 级严重错误（json_schema_broken /
  illegal_source_id / unauthorized_no_reply）仍由层 1 触发；语义严重错误（错人 /
  编造已发生 / 明确禁止的私有信息泄漏 / 已取消事项复活）需带原文证据复核，
  **未校准 judge 不单独判死**；多目标并列判决（质量 / 污染 / 成本 / 时延 / 可维护性）。
- **note_lcs 阈值改动**：分母 `max(len(a), len(b))`，阈值 0.6，<10 字候选豁免。
  `style_diversity` 保留旧 `len(a)` 分母（协议 §8 变更纪律：一次只动一处，
  改动单独版本化）。
- **首选/备选分审落地**：`exam_layered_score.py` 加逐槽字段 + 三汇总指标；
  `primary_semantic` 继承 case-level 人工初审（现有 24 位仅有主槽初审，如实登记）；
  `backup_semantic_per_slot` 默认 unrated；`BACKUP_SEVERE` 覆盖表**显式登记**
  E003_T04 方向 3"有点可惜"泄漏私有情绪这一条已知严重问题。

### 4.2 旧 24 位离线重评（协议 §8：协议变更 ≠ 候选收益）

- 工具：`python tools/rescore_offline.py --exam logs/exam1_20261023`
  → 每 turn 落 `scoring_v05.json`，`RESCORE_SUMMARY.json` 汇总。
- 结果：**只有 E002_T02（7.50 → 10.00）与 E002_T05（7.50 → 10.00）** 分数变化，
  `note_lcs` 从旧分母 + 0.3 阈值的误伤里被解放；其余 22 位分不变。
  E002_T04（style_diversity 8.33）与 E003_T02（json_schema_broken 0.0）保留原判决。
- 分层汇总（新尺 + 新逐槽字段）：

```
episodes:
  E001  program 10.00  primary_pass_rate=0.75  at_least_one=0.75  severe_error_backup=0.000
  E002  program  9.79  primary_pass_rate=1.00  at_least_one=1.00  severe_error_backup=0.000
  E003  program  8.75  primary_pass_rate=0.857 at_least_one=0.75  severe_error_backup=0.125
total:
  program_mean 9.31 → 9.51  （尺子变化，非候选收益）
  semantic 20/2/1/1 不变
  primary_semantic_pass_rate 0.87  at_least_one_pass_rate 0.833  severe_error_backup_rate 0.042
```

- 与旧 LAYERED_SCORE 并列保留（旧 `LAYERED_SCORE.json` 不动，新写
  `LAYERED_SCORE_v05.json`）；两份都作为父本基线，供两臂对照用。

### 4.3 现有 24 位判决的诚实边界

- 24 位跑在**已作废**身份 `399c82b7…`（含 5/24 位跨轮写回缺失 + 1 位格式坏 veto）；
- 语义层是 worker **逐位初审**（非盲评校准），24 卡 `review_status = not_user_rated_not_model_run`；
- 新逐槽字段中 backup_semantic 大部分记 unrated（旧答卷未分槽复核），不冒填；
- 因此**不能**据"primary_pass_rate=0.87"就宣布父本已达 87% 主槽合格率；那需要新身份
  下的两臂交错实跑才能定基线；
- **旧 24 位是父本的故障样本，不是父本的成绩**（协议 §8、fair_baseline §5）。

## 5. 冻结清单（进入真实两臂对照前的最后一道门）

清单落 `refs/experiment_protocol_20261023/EXPERIMENT_PLAN_20261023.md`（本次交付同目录）。
核心要素：

- 唯一主假设 H1（回忆检索） + 8 条退化保护（程序 / 严重问题备选 / 主槽合格率 /
  至少一 pass 率 / token / p95 时延 / P1-P4 反例 / 装配段预算）；
- 交错运行顺序：同故事按序、每故事独立状态、E001/E002/E003 两臂先后手轮换；
- 停止条件 5 条明确列出（授权账本 remaining=0 / 连续 2 veto 同臂 / 主槽合格率<0.5 /
  网关 5xx / P1-P4 现场复现）；
- 本批预算：无新授权 → 只提交清单不发真实调用；有授权则严格按上限执行；
- 未预登记事项（如提升幅度阈值）明写"不凭空编"。

## 6. 尚缺证据（本批诚实登记）

- **候选在有效两臂下的真实输出改善**：需新授权 48 attempts；
- **独立泛化证据**：dev_pilot 全 24 位 `dev_exposed`，未参与调参的独立选择集/B 集 0 位；
  错人子类 / MEDIA_PENDING / 长程跨话题 / 重启恢复 4 类保护样本尚未扩；
- **judge 校准**：`benchmark/anchors/` 20 锚点未准备；rubric v0.5 §3 明写未校准 judge
  只辅助；
- **用户愿发偏好**：worker 不代投，`LAYERED_SCORE_v05.json.total.willing_to_send =
  "未投票"`；
- **首字时间 / 流式**：本地网关非流式，本批不报 TTFT；
- **法币账**：网关未给单价，`money.billing_source=unknown`，本地 URL ≠ 免费；
- **跨主体场景（FactSubjectResolver）**：§8 范围限定；扩题批次处理；
- **后台重估（KnowledgeTriggerCoordinator）**：短故事未触发；长程连续故事批次处理；
- **Kotlin 完整构建对拍**：本机无 Gradle/SDK；预期值转写自源码与自带单元测试
  （见 `refs/arena_v1/BASELINE_PARITY.md` §5）。

## 7. 下一步（本会话之外）

- **交独立复核会话**：本份 + `EXPERIMENT_PLAN_20261023.md` + `BASELINE_PARITY.md §8` +
  `LAYERED_SCORE_v05.json` + `RESCORE_SUMMARY.json`；复核按前一轮约定的 6 条：
  基线差异核销 / v2 四保护反例证据 / 规范同步 / 分层评分字段落地 / 冻结身份 + 清单 /
  是否越界宣布；
- **通过后**由用户裁定：a) 是否发新授权 `AUTH-EXAM2-*` 48 attempts；
  b) 交错顺序是否调整；c) 可接受差异是否需要再收紧；
- **实跑之后**：候选保留/退回判决（协议 §5.4 + §6），仍不覆盖泛化；
- **扩题**（协议 §7 步骤 5-6）：先补 4 类保护样本的独立故事；开发回归可用同族派生
  （采纳记录 §不采纳第 3 条），但独立泛化验证必须用**未参与调参**的故事；
- **judge 校准与用户锚点**（独立会话出题与保管）。

## 8. 常规实现决策（自行决定并记理由，采纳记录"不采纳逐夹具审批"）

- `_bigrams` 用 Chinese 2-gram 而非 jieba 分词：项目本地零依赖约束，且父本
  `extract_keywords` 已用整块 name；bigram 交集对短中文召回足够，避免引入 tokenizer；
- `MAX_NEW_CONTEXT_CHARS=1800` 而非直接扩父本 `TOTAL_BUDGET=9000`：新增段应与 recall
  body + ongoing extras 关联，与总预算解耦；实测 E001_T06 recall body 约 850 字，
  1800 上限允许两条归档 + 若干 ongoing extras；
- 未拆 `is_recall_turn` 走 NLP 分类器：项目零依赖 + 单次假设改动，词表 24 项
  + 末条真实消息即可覆盖；扩题批次视 H1 结果决定是否值得升级；
- `_wrap_recall_into_knowledge` 而非 monkey-patch 父本 `_knowledge_block`：
  避免 `finally` 恢复的时序耦合，也顺带解开 restore 后 wrapper 丢失问题（P4）；
- `lcs_ratio` 与 `lcs_ratio_symmetric` 并存而非直接替换：保留 style_diversity 旧语义，
  符合协议 §8"一次只动一处"；
- BACKUP_SEVERE 用覆盖表而不是让 worker 现场逐槽重审：现有 24 位只有 case-level
  初审，硬造"分槽初审"会污染未评原则；覆盖表只登记 EXAM_REPORT 已明确点名的
  E003_T04 一处，其余保持 unrated；
- rescore_offline 输出到 `scoring_v05.json` 而非覆盖 `scoring.json`：并列证据便于
  两口径 diff；协议 §8 版本纪律要求"与旧口径同时输出一次以对比"。

## 9. 一手证据索引（复核入口）

| 主题 | 一手文件 |
|---|---|
| 父本 diff | `candidates/reference_product_v1/{mechanism/mechanism.py, reference_chain.py}` |
| 候选 diff | `candidates/v2_recall/mechanism/mechanism.py` |
| 评分器 v0.5 | `arena/lb_scorer.py` C2 note_lcs 段 + 顶部 `lcs_ratio` 拆分 |
| 分层重评工具 | `tools/exam_layered_score.py`（逐槽 + 三指标）+ `tools/rescore_offline.py` |
| 基线 parity extra | `tools/test_baseline_parity_extra.py`（10 checks） |
| v2 保护反例 | `tools/test_v2_recall.py`（11 checks，c1-c5 沿用 + c6-c11 新） |
| 离线门禁输出 | `logs/baseline_parity_extra_result.json`（本批首次跑，未落默认路径可由复核方重跑）；`logs/v2_recall_result_20261023.json` |
| 24 位旧答卷重评 | `logs/exam1_20261023/RESCORE_SUMMARY.json` + 每 turn 目录 `scoring_v05.json` |
| 新分层成绩单 | `logs/exam1_20261023/LAYERED_SCORE_v05.json`（primary_pass=0.87 at_least_one=0.833 severe_backup=0.042） |
| 规范同步 | `AGENTS.md v1.4` + `benchmark/rubric.md v0.5` + `OBJECTIVE.md v1.4` + `refs/arena_v1/BASELINE_PARITY.md §8` |
| 冻结实验清单 | `refs/experiment_protocol_20261023/EXPERIMENT_PLAN_20261023.md` |
| 授权未复活 | `logs/auth_budget/AUTH-EXAM1-20261023-01.jsonl` 尾行 `kind=authorization_closed` 未变 |

## 10. 判决边界（本份**不宣称**）

- 不宣称"v2 已优于父本"——只有输入侧证据 + 四组反例保护，没有新的两臂真实答卷；
- 不宣称"整体均分从 9.31 涨到 9.51 是候选收益"——那是 rubric v0.5 修 note_lcs 的尺子变化，
  协议变更 ≠ 候选收益（AGENTS v1.4 ④）；
- 不宣称"24 位覆盖了跨主体/长链/重启/MEDIA"——`dev_pilot_v02/04_COVERAGE_AND_AUDIT.md`
  明写这几类待扩；
- 不宣称"judge 已校准"、"用户口味已锚定"、"泛化已验证"；
- 不宣称"成本已降低"——本批只补 by_blocks 与新增段预算，父本与候选在同一预算规则下
  待两臂实跑后才能报。

一句"go"或"进入复核"即交独立复核会话；需要我调整任何一项、补任何证据或回退任何改动，
点名即可，我不再自行动在跑批。
