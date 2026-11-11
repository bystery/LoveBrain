# 数据集独立审查材料 A：构成与溯源审查

- 审查对象：`D:/lovebrain-arena` 仓库内 LoveBrain Arena 数据集（当代故事库 `benchmark/stories_v2/` 及其前代演进链）。
- 审查日期：2026-11-07。
- 审查方式：只读文件取证 + 确定性脚本复算（`refs/dataset_review_20261107/recompute_stats.py`，纯标准库、零模型调用、不修改任何仓库文件）。本文所有数字均标注出处；脚本可复算的数字在 §11 列出实测对账结果。
- 审查边界：**`benchmark/b_line/` 为封存盲测集，本材料未读取其任何 episode 内容**；仅引用其 manifest 元数据（`benchmark/b_line/B_LINE_MANIFEST.json`）与 README 纪律文本（`benchmark/b_line/B_LINE_README.md`）。
- 文体声明：本文是审查材料，不是宣传稿。凡当前证据不能证明的事项，在 §10 逐条明说。

---

## 1. 真实规模（含四个主数字的关系式）

### 1.1 当代故事库（benchmark/stories_v2/"库 208 集"）

出处：目录实测（每篇以存在 `episode.json` 计），见 `refs/dataset_review_20261107/recompute_stats.py` §[1] 输出。

| 波次 | 篇数 | 每篇轮数 | 轮数合计 | 出处 |
|---|---|---|---|---|
| stage2 | 16 | 12 | 192 | `benchmark/stories_v2/stage2/`（16 目录）+ `STAGE2_MANIFEST.json` |
| stage3 | 8 | 8 | 64 | `benchmark/stories_v2/stage3/` + `STAGE3_MANIFEST.json` |
| stage4 | 8 | 24 | 192 | `benchmark/stories_v2/stage4/` + `STAGE4_MANIFEST.json`（`total_turns=192`） |
| wave1 | 16 | 12 | 192 | `benchmark/stories_v2/wave1/` + `STAGE1_MANIFEST.json`（`count=16`） |
| wave2 | 48 | 12 | 576 | `benchmark/stories_v2/wave2/` + `STAGE1_MANIFEST.json`（`count=48`） |
| wave3 | 96（88 标准 + 8 反事实 CF） | 12 | 1152 | `benchmark/stories_v2/wave3/` + `STAGE1_MANIFEST.json`（`count=88`）+ `STAGE1_MANIFEST_CF.json`（`count=8`） |
| wave3_ext | 16 长剧（8×40 + 8×32） | 40/32 | 576 | `benchmark/stories_v2/wave3_ext/`（16 个 LRE 目录） |
| **库合计** | **208** | — | **2944** | 脚本实测 episodes=208, turns=2944 |

- 208 = 16+8+8+16+48+96+16；2944 = 192+64+192+192+576+1152+576。分母即此表。
- wave3 的 96 篇中，E149–E156 为 8 篇反事实孪生（CF），由 `benchmark/stories_v2/wave3/STAGE1_MANIFEST_CF.json` 登记基线篇与扰动类型（E149←E077/time、E150←E105/time、E151←E086/subject、E152←E072/negation、E153←E084/negation、E154←E094/status、E155←E090/status、E156←E087/goal）；CF 不进 dedupe 域普查（`RESEARCH_STATE.json` updates 2026-11-02："CF 孪生 8 篇入库…不进 dedupe 域普查"）。
- wave3_ext 16 篇长剧由 16 篇既有 12 轮篇"原位续写"扩为 32/40 轮（工艺声明见 `refs/experiment_protocol_20261023/global_harness_program_20261030/W03-06_WAVE3_SKELETON_PLAN_20261102.md` §2："T1-T12 逐字保留、case_id 延名"）。抽样验证：LRE117 前 12 轮 `input` 与 E117 前 12 轮逐字一致（12/12），case_id 前缀改为 `LRE117_T*`。
- 每篇目录五件套齐全（episode.json / reference.json / timeline.json / audit.md / exposure.json 共 221/221 齐全，脚本 §[7] n=221）；`runner_episode.json` 仅部分篇导出（wave3 64 篇 + wave3_ext 7 篇），另有 `benchmark/episodes/` 平铺导出供运行器消费。

### 1.2 Gold sidecar（比库多三族）

出处：`benchmark/stories_v2/GOLD_SIDECAR/GOLD_INDEX.json`（schema `GOLD_SIDECAR_v2`，generated `2026-11-03T22:25:52`，tool `tools/build_gold_sidecar.py`）。

**四个主数字的关系式（脚本恒等式 I1/I2，PASS）：**

```
sidecar episodes 221 = 库 208 + stage1 8 + stageD 3 + wave1_ext 2
sidecar turns   3102 = 库 2944 + stage1 64 + stageD 22 + wave1_ext 72
```

即：库是 208 集 2944 轮；sidecar 在库之外还收录 stage1（8×8）、stageD（3 篇 22 轮）、wave1_ext（2 篇 72 轮）三族早期材料。sidecar 不含 `final_20261029`（K01/K02/N01/N02）与 `anchors_20261028`（AK01–12）。

sidecar 汇总（`GOLD_INDEX.json` totals，脚本 §[2] 原样复述）：

| 字段 | 值 | 占比分母与说明 |
|---|---|---|
| episodes | 221 | 分母=sidecar 全部篇 |
| turns | 3102 | 分母=sidecar 全部轮 |
| with_assertions | 2919 | 2919/3102 = 94.1%（该轮断言表非空） |
| empty_gold | 79 | 79/3102 = 2.5%（episode.json 的 gold={}，无卡无断言） |
| 无断言但有卡轮 | **104**（脚本新复算） | 104/3102 = 3.4%：有评审卡（forbidden/unknowns/九维在卡）但 `required_memory_atoms` 为空。三类相加 = 3102（恒等式 I3，PASS）。主要在 stage4（80 轮）与 stage1/2/3（24 轮），均为 `turn_type=reply` 的互动轮 |
| no_card | 0 | 每轮都解析到卡或 reference |
| atoms | 6603 | 原子总数，与逐轮重算一致（恒等式 I5，PASS） |
| unknowns | 2838 | "未确定信息"字符串/结构条数 |
| intent_contracts | 117 | 意图契约轮数（intent 字段非空口径）；另有 may_decline=true 轮 279 |
| must_not_affirm_atoms | 711 | 711/6603 = 10.8%（口径见 §7.3） |
| atoms_with_affirm_markers | 565 | 有 affirm_markers 的取消族原子 |
| source_conflicts | 93 | 全部在 wave3_ext（跨 wave 汇编修订卡与卡/参考卡来源冲突登记） |
| marker_qc | n_markers=1099, verbatim_violations=0 | marker 逐字回验（脚本级子串校验） |
| qc_sample | seed=20261101, n=40, strict/lenient 全 40 过 | 抽样 QC |

### 1.3 前代与外围材料（不计入库 208）

出处均为对应文件实测：

- 怪物题/门禁题（助手能力赛时代）：`benchmark/core_set.json`（12 题）、`benchmark/full_set.json`（20 题 = 怪物 12 + 基础回归 T 系 8）、`benchmark/dims_map.json`（六维能力映射 + 5 gates；注意六维是旧赛制量尺，与当代九维不是同一套）。
- 口味锚：`benchmark/anchors/user_taste_anchors_61.json`（`items` 实测 59 条；**文件名 61 与实际条数 59 不一致**，来源标注为 4 个 kb zip 导出，exported_at 2026-10-16）；`benchmark/anchors/anchors_key_v2.json`（A01–A20 共 20 键 + `_说明`）。
- 单轮锚：`benchmark/stories_v2/anchors_20261028/`（AK01–AK12，`ANCHORS_MANIFEST.json`：12 篇×1 轮，may_decline 3 轮，`independence="同作者公开开发集，非独立封存"`）。
- 开发试点：`benchmark/dev_pilot_v02/`（3 篇 E001–E003 × 8 轮 = 24 请求位；`DEV_EXPOSED_MANIFEST.json`：`content_version=dev_pilot_v0.2_20261022`、`import_verdict=IMPORT_OK`、`registered_as="DEV_EXPOSED（公开开发集；独立选择集/B/扩题不在此列）"`）。
- 扩展开发集：`benchmark/dev_expansion_v01/`（9 篇 E004–E012，评审卡分册 `03_GRADING_CARDS_E004_E006.json` / `04_GRADING_CARDS_E007_E012.json` / `05_GRADING_CARDS_R2REV_20261025.json`）；导出平铺在 `benchmark/episodes/E001.json–E012.json`。
- final_20261029：`benchmark/stories_v2/final_20261029/`（K01/K02 各 24 轮、N01/N02 各 12 轮 = 72 轮，另有 2 个非故事探测目录 f4_probes / f4_probes_t13_old）。
- W03 普查快照：`refs/experiment_protocol_20261023/global_harness_program_20261030/W03_CENSUS_20261030.json`（2026-10-30 时点：manifest 口径 31 篇 350 请求位 = stage1 64 + stage2 192 + stageD 22 + final 72；普查扫描 43 个故事目录 362 位 = 31 篇 + AK 12 篇×1 轮；`note_31_claim` 明文）。

---

## 2. 来源与生成方式

### 2.1 全部 AI 合成，无真实用户材料

- exposure.json 普查（脚本 §[7]，n=221=库 208+stage1 8+stageD 3+wave1_ext 2）：`real_user_material=false` 221/221；`independent_review=false` 221/221；`author_type` 全部为 AI 族变体：`AI` 178、`ai_generated` 16、`synthetic_ai_generated` 10、`isolated_generation_session` 8、`synthetic_generator` 4、`ai_generator` 2、`AI_subagent_candidate_blind` 2（stageD D01/D02）、`AI_worker_targeted_retest` 1（stageD D03）。
- 篇级 `audit.md` 首节明文声明合成性质，例（`benchmark/stories_v2/wave3/E117/audit.md`）："本篇为 AI 合成标准 12 轮故事，无任何真实用户材料（real_user_material=false）…人物及全部机构、地点、电话均为虚构合成"。
- 前代口味锚 `user_taste_anchors_61.json` 例外地来自真实来源标注（4 个 kb zip），但它是"锚点参考"不是考题；本材料按 manifest/audit 口径统计考题类故事。

### 2.2 生成隔离（防污染铁律）的执行证据

- 协议：`refs/experiment_protocol_20261023/global_harness_program_20261030/W03-05_CUSTODY_PROTOCOL_20261031.md` §1：生成会话只允许读 stage1/规格/既有主题清单，禁止读 logs/、candidates/、评审 JSON 失败原句；开工前登记 `generation_isolation_ack`。
- 账本事件：`STORY_CUSTODY_LEDGER.json` events 含 `generation_isolation_ack` ×1（脚本 §[6] 具名事件表）。**注意：账本只有 1 条 ack 事件，而生成会话远多于 1 个（wave3 至少 T1 四分片 + T2 四代理 + T3 四代理 + T4 八代理 + CF + 长剧续写）。**隔离声明更多记载于各篇 `audit.md`（如 E117 audit："隔离声明：本会话未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/AUDIT/评审 JSON"）与 RESEARCH_STATE updates（"4 隔离代理"），而非逐会话入账。不能仅凭账本证明每个生成会话都执行了隔离。
- 篇级批次/会话字段：exposure.json 带 `batch` 与 `session`（例 E117：`batch=W3-T1-20261102`、`session=W3-T1-A`）。

### 2.3 标签粒度：篇级词，无逐条故障族标签（如实声明）

- 篇级主题词（themes）与冲突型（conflict_type）由 manifest/audit 登记，例 E117：`conflict_type=更正类`、`domain=出行拼车`、themes=["出行拼车","返乡顺风车","同乘人数更正","油费重算","后备箱取舍"]。
- **主题词是篇级自由词，不是受控枚举**：`W03-06_SKELETON_DEDUPE_20261031.json` 的 theme_frequency 列出 300+ 种自由主题词（高频："宠物"17、"数码消费"17、"医疗健康"15、"自然收尾"14…长尾多为 1 次）。
- **数据集没有逐轮/逐原子的"故障族"标签。**故障族治理记录在 `refs/KNOWN_FAILURES.md` 与 RESEARCH_STATE `failure_families`（面向 harness 评测），与题目文件无外键关联。凡声称"某题针对某故障族"的说法，本材料不能从题目文件本身证实。

---

## 3. 人工审核情况（语义审查代理批次与直修记录）

### 3.1 机械校验

- `tools/` 的 `validate_story_v2` 每批入库前机械校验（轮数/唯一 case 数/manifest 哈希），证据见各波 `STAGE*_VALIDATION*.json` 与审查文档附言。例（`W03-06_WAVE3_T1_SEMANTIC_REVIEW_A_20261102.md` §校验与哈希）："validate_story_v2 → 8/8 PASS 0 error；32/32 PASS；story_custody.py verify → OK"。
- 入库链（`W03-06_WAVE3_SKELETON_PLAN_20261102.md` §3）：validate_story_v2 → story_custody seed（SEALED）→ 语义审查 → 修复重校验 → dedupe 普查 → 状态三件套同步。

### 3.2 语义审查代理批次（审查轨逐篇亲读）

审查文档清单（均位于 `refs/experiment_protocol_20261023/global_harness_program_20261030/`）：

| 波次/批 | 审查文档 | 覆盖 |
|---|---|---|
| wave1 | `W03-06_WAVE1_SEMANTIC_REVIEW_20261101.md` | 16 篇 |
| wave2（含 2B） | `W03-06_WAVE2_SEMANTIC_REVIEW_20261101.md`、`W03-06_WAVE2B_SEMANTIC_REVIEW_20261101.md` | 48 篇 |
| wave3 T1（32 篇） | `W03-06_WAVE3_T1_SEMANTIC_REVIEW_A/B/C/D_20261102.md`（4 分片×8 篇） | E117–E148 等 |
| wave3 T2（24 篇） | `W03-06_WAVE3_T2_SEMANTIC_REVIEW_S1–S4_20261102.md`（4 批×6 篇） | E157–E180 |
| wave3 T3（16 长剧） | `W03-06_WAVE3_T3_SEMANTIC_REVIEW_R1a/R1b/R2/R3/R4/R5/R6_20261102.md`（7 份） | LRE117–LRE148 |
| wave3 T4（32 篇） | `W03-06_WAVE3_T4_SEMANTIC_REVIEW_S5/S6/S7/S8_20261102.md`（4 批×8 篇） | E181–E212 |

- 口径说明：任务基线所称"R1-R8"与实际文件不符——T3 长剧批实际是 R1a/R1b/R2–R6 共 7 份；S 系跨 T2（S1–S4）与 T4（S5–S8）共 8 份。本材料以实际文件清单为准。
- 审查性质：**AI 审查代理的语义审查**（每批一份 markdown 逐篇判决 + 缺陷清单），不是独立人类评审。全部 221 篇 exposure.json `independent_review=false`，与"无独立人类评审资格"一致（见 §8.3）。
- 判决分布示例（T1 分片 A，8 篇）：干净 2、微注登记 2、修复 2、正文级缺陷登记 2（E119 挪日、E124 改期周数塌缩 FAIL 级）、阻塞级 0；"卡面均未教错话，两处 FAIL 均在正文不在卡"。
- 收口记录（`RESEARCH_STATE.json` updates）：
  - 2026-11-02T05:00 "语义审查 4 代理完成 32/32 可开测；episode 修复 5 篇(E119/E122/E124/E131/E132)+timeline/quote 级联+重验全过"；
  - 2026-11-02T09:00 "T3 语义审查收口：15 篇全 PASS（R1a/R1b/R2/R3/R4/R5，reference 直修~6 处+ts 级 2 处+跨年称呼 3 句，哈希全级联）"（第 16 篇 LRE127 漏派后补 G4b，对应 R6）；
  - 2026-11-02T10:00 "S1-S4：24/24 PASS；直修~20 reference 项+4 episode ts 项"；
  - 2026-11-02T12:00 起至 2026-11-02 收口 "T4 32/32 审查 PASS（S5-S8）；全库终扫 88/88 validate"。

### 3.3 reference 直修与"账目陷阱"改造（可抽查的具体案例）

- **T1 修复 5 篇（E119/E122/E124/E131/E132）**：审查文档 A 给出逐条缺陷表，例如 E122 reference T01 `acceptable_actions[0]` 乱入英文 "starting"→"问她哪条最难"（已修）；E124 正文改期周数塌缩（两处锚点指 12-19 而执行日 12-12）。修复落地验证：`benchmark/stories_v2/wave3/E124/timeline.json` 当前 turn_clocks T10–T12 = 2026-12-19（与"最小修复路径 T10–T12 ts 12-12→12-19"一致），E119 timeline 首轮 2026-11-03。
- **账目陷阱改造两处**（RESEARCH_STATE updates 2026-11-02T09:00 与 T10:00）：
  - `LRE128_T35`："省60"实为超60 → 按 LRE117 先例改造为账目陷阱（HER 报错数，卡面引导指出，gold 不再背书错误算术）；
  - `E164_T05`：与 LRE128_T35 同款改造（E164 为 wave2 篇，属 T2 批 S1–S4 收口）。
  - 即：题目侧的处置是**改造成"HER 报错、卡面引导指出、gold 不背书错数"的陷阱题**，不是删除。
- **哈希级联**：每次直修后 manifest/exposure/账本哈希现算刷新（T1 分片 A 文档："已修文件共 6 个…STAGE1_MANIFEST.json、STAGE1_MANIFEST_A.json、三篇 exposure.json、STORY_CUSTODY_LEDGER.json 哈希全部现算刷新，新增账本事件 wave3_t1_semantic_review_passed"）。账本 `verify` 子命令可对账（`W03-05` §3）。

### 3.4 审查未覆盖之处（如实声明）

- 审查是生成后入库前的批次审查（封板前迭代期，各审查文档明文"审查与修复均发生在首次暴露前，修复合法"）；**入库后再无逐篇独立复审**。
- "全 PASS"是审查代理的判决；本材料未逐篇重放其亲读过程，只能核对：(a) 审查文档存在且含逐篇结论；(b) 指名修复在当前文件里可验证（§3.3 抽查两处通过）；(c) 哈希账本 verify 机制存在。**不能证明全部直修都正确落地**，只能证明机制与抽样成立。

---

## 4. 故事级与轮级结构

以 `benchmark/stories_v2/wave3/E117/` 为样例（各波结构一致）：

- **episode.json（4 个顶层字段）**：`episode_id`、`persona`（her/me/relationship_stage）、`initial_kb`（me/her/warmth/style/lessons 五键）、`turns`（12 项）。轮字段：`case_id`、`turn_type`（reply 等）、`gold`（episode 侧原始 gold，多为空，卡在评审卡/侧车）、`input`（messages[clock/ts/text/role] + clock + may_decline）、`feedback`（ HER 对 ME 回复的反馈机制，2 键）。
- **reference.json**：`story_id` + `turns`（按 case_id 索引）。每轮卡字段：`case_id`、`current_action`（单值）、`acceptable_actions`（≥2 多选）、`applicable_dimensions`（九维标签，见 §7.2）、`required_memory_atoms`（原子表）、`private_guidance`、`unknowns`、`protected_expressions`、`forbidden_factual_claims`、`may_decline`。
- **timeline.json**：`story_id`、`turn_clocks`（每轮 HER 时钟）、`message_index`（逐消息 quote/ts/speaker）、`events`、`revisions`（事件与修订链）。
- **exposure.json**：`author/author_type/readers/frozen_at/experiments_used/dev_exposed/real_user_material/hashes{episode,reference,timeline}/runner_copy/kind/conflict_type/domain/batch/session`。
- **audit.md**：篇级审计单（性质与暴露/生活动机/连续性与回扣/ME 历史与边界/no_reply 与媒体局限/参考卡质量）。
- 断言原子 schema（`required_memory_atoms[]`，9 必填 + 6 可选；出处 GOLD 逐字段计数，脚本 §[3] `atom_fields_seen`）：
  - 必填 9：`atom_id/subject/relation/value/valid_time/status/negation/source{kind,case_id,quote,message_id}/required_this_turn`（6603/6603 全存在）；
  - 可选出现次数：`affirm_markers` 565、`item_markers` 47、`cross_origin` 9、`cross_story` 8、`cross_case_ref` 8、`quote` 1。
  - **无类型枚举**：`relation` 为自由文本，去重 4468 种（分母 6603）；因此不能按 fact/date/etc. 分型统计。`subject` 在 wave1 及以后规范为五类（§7.4），stage1–4+stageD 存在自由文本。

---

## 5. 版本、封存与哈希

- **content_version**：当代故事库本身没有全局 content_version 字段；版本绑定靠三层哈希：① 各波 manifest 的 per-file sha256（如 `stage2/STAGE2_MANIFEST.json` story[0] 记 episode/reference/timeline/audit 四件 sha256）；② exposure.json `hashes`（episode/reference/timeline 三件）；③ 保管账注册项哈希（`W03-05` §3"篡改可检出"）。有显式 `content_version` 字段的是前代 `benchmark/dev_pilot_v02/DEV_EXPOSED_MANIFEST.json`（`dev_pilot_v0.2_20261022`）与 B 线 manifest（`b_line_v2`）。
- **frozen_at**：221 篇中仅 stageD 3 篇非空（D01/D02 2026-10-28T17:41:16、D03 2026-10-28T19:29:16），其余全部 null（脚本 §[7] frozen_at 非空=3）。即"冻结"在 exposure 字段层面只发生在 stageD 盲候选材料上；库主体以"账本 lifecycle + 哈希"而非 frozen_at 表达封存。
- **sha256 对账机制**：`tools/story_custody.py verify` 对账（审查文档与 `W03-05` 多处引用其输出 OK）；B 线 manifest `integrity{algorithm:sha256, verified_at:2026-11-03T21:51:25}`。
- **b_line_v1→v2 重建**（出处 `benchmark/b_line/B_LINE_MANIFEST.json` 元数据 + 账本 `b_line_resealed` 事件）：
  - v1 选篇含 6 篇"账本漏记实际已暴露"的篇（E117/E164/E191/LRE121/LRE146/LRE127）；
  - 2026-11-03 补录 **54 条 backfill exposure 事件**（涉及 28 篇，批次覆盖 W13-03/W17/W17B/W17C/W18/W18B/W19B/W14J/W14R/W15N/W16）后，从干净池重选 24 篇重建为 v2（16 标准 + 4 长剧 LRE119/123/139/142 + 4 CF E150/152/153/155）；
  - v1 目录被 v2 原位替换（未消耗过，账本注"无数据损失"）。
  - **已知不一致（如实登记）**：`benchmark/b_line/B_LINE_README.md` 仍停在 v1 口径——其 §6 写"4 篇长剧（LRE121/LRE146 40 轮、LRE139/LRE127 32 轮）+ 4 篇反事实（E150/E152/E153/E156）"，与 manifest v2 的实际构成（LRE119/123/139/142 + E150/152/153/155）在长剧名单与 E156/E155 上不一致；README 头部仍写"元数据与 hash 账本见 B_LINE_MANIFEST.json（b_line_v1）"。以 manifest v2 为准，README 未同步。

---

## 6. 去重方法与结果

- 工具：`tools/story_skeleton_dedupe.py`；普查文件：`refs/experiment_protocol_20261023/global_harness_program_20261030/W03-06_SKELETON_DEDUPE_20261031.json`。
- 口径：total=184 **标准篇**（stage2 16 + stage3 8 + stage4 8 + wave1 16 + wave2 48 + wave3 标准 88；不含 8 CF、不含 16 长剧、不含 stage1/stageD/wave1_ext/final/AK）。
- 方法（`W03-06_WAVE3_SKELETON_PLAN_20261102.md`）：每篇 (域, 主题集, 冲突型) 三元组与全部既有篇至少一元不同；模板实例间 bigram 重叠 <0.4 由 dedupe 工具扩展版把关；域用 manifest 显式字段 + 归一映射（`W03-06_DOMAIN_NORMALIZATION_20261102.json`，13 篇二次归一 + 新域"兴趣社交"启用，"题目与 manifest 哈希不动，工具旁路引用"）。
- 结果：`near_dup_clusters=0`（dedupe 文件 `near_dup_clusters` 为空表；脚本 §[5] 复述）。分阶段复跑记录见 RESEARCH_STATE updates（"dedupe 复跑 total=128 near_dup=0"→"152 篇 0 近重簇"→"total=184 标准 near_dup_clusters=0"）。
- **诚实限定**：0 近重簇是"三元组至少一元不同 + bigram<0.4"口径下的结论，不等于语义上无相似故事；长剧 16 篇与 CF 8 篇按设计不进该普查（长剧与母本 12 轮篇输入逐字相同是工艺要求，见 §1.1）。

---

## 7. 覆盖标签与多标签重叠口径

### 7.1 域分布（184 标准篇）

出处：`W03-06_SKELETON_DEDUPE_20261031.json` domain_coverage（脚本 §[5]）：

工作职场 19 / 数码消费 19 / 运动健身 18 / 学习成长 18 / 家居装修 18 / 医疗健康 18 / 餐饮聚会 17 / 宠物 17 / 出行拼车 16 / 家庭事务 15 / 兴趣社交 8 / 其他 1（E044），合计=184（恒等式 I6，PASS）。

- 显式/派生口径：152 篇（wave1 16 + wave2 48 + wave3 主 manifest 88）的 STAGE1_MANIFEST 有显式 `domain` 字段（恒等式 I7，脚本实测 152）；stage2/3/4 共 32 篇无显式字段，由 `tools/story_skeleton_dedupe.py` 按 `W03-06_DOMAIN_NORMALIZATION_20261102.json` 映射+关键词规则派生进 184 口径。
- 其余篇族无 domain 字段或不在 184 口径：wave3 8 CF（CF manifest 各自带 domain，但不进 184）、wave3_ext 16 长剧（per-篇 manifest 无 domain）、stage1 8/stageD 3（无 domain）；wave1_ext 两篇 manifest 均带 domain（LRE048=学习成长、LRE060=家庭事务），也不在 184 口径内。

### 7.2 九维卡面覆盖

- 分母 = 208 库篇的 2944 个 reference.json 卡轮（脚本 §[4] 实测扫描）。
- 出处字段：reference.json `applicable_dimensions`；九维↔判分维一一映射见 `benchmark/stories_v2/wave3/DIM_LABEL_MAP_20261104.json`（"9↔9 一一对应，无多义/无缺漏（卡面 232 张全用该 9 标签）"）。

| 维度 | 轮数 | 占 2944 轮 |
|---|---|---|
| interaction | 1924 | 65.4% |
| memory_use | 1625 | 55.2% |
| goal | 1623 | 55.1% |
| emotion_relation | 1460 | 49.6% |
| factuality | 1234 | 41.9% |
| time_state | 908 | 30.8% |
| boundary | 763 | 25.9% |
| initiative | 580 | 19.7% |
| attribution | 345 | 11.7% |

- **多标签重叠口径**：一卡可含多维，标签实例合计 10462（平均 3.55/轮）；各维百分比分母均为轮数 2944，因此各维之和 >100% 是口径使然，不是重复计数错误。覆盖率之外，本材料**不声称**九维分布"均衡"——attribution 11.7% 与 interaction 65.4% 的差异是事实，是否达标无预设门槛可判。

### 7.3 断言 status 与 must-not-affirm 口径

出处：GOLD_*.json 逐轮重算（脚本 §[3]，分母 6603 原子）。

- status 为自由字符串集合（44 种），前部：asserted 4994 / confirmed 616 / superseded 224 / uncertain 195 / suspended 108 / cancelled 90 / corrected 77 / retracted 63 / resolved 39 / active 29 / negated 11 / invalidated 11 / revoked 7 / voided 3 / erroneous 2 / canceled 1 / …（长尾各≤13）。
- **取消族（cancel family）**= status ∈ {cancelled, canceled, superseded, retracted, voided, revoked, invalidated, negated, erroneous}（口径即 `tools/build_gold_sidecar.py` enrichment_policy 明文），复算 412 原子。
- **negation=true**：632 原子（632/6603=9.6%）。
- **must_not_affirm = 取消族 ∪ negation=true**：412 + 632 − 交集 333 = **711**（恒等式 I4，与 GOLD_INDEX must_not_affirm_atoms=711 一致）。711/6603=10.8%。交集 333 即"status 在取消族且 negation=true"的原子（如 negated status 本身带 negation 标记），合并口径下只计一次。
- 其余关键字段：`valid_time` 非空 6603/6603=100%；`required_this_turn=true` 1874（1874/6603=28.4%）。
- subject 规范五类（wave1+ 时期全部规范；脚本复算）：SHARED 2503 / HER 2047 / THIRD 1244 / PUBLIC 271 / ME 137，合计 6202；其余 401 条为自由文本，分布在 stage1/2/3/4/stageD（如"她""我""me&her""HR"等）。基线称"stage1-4 自由文本"，实测 stageD 亦有 5 种自由 subject（如 `her`/`me`/`me&her`），此为对基线的小幅修正。

### 7.4 意图契约与 may_decline

- intent_contract（intent 非空口径）117 轮（分母 3102）；其中 114 轮有 keywords_any（关键词组 348 个，为题面与意图文本的确定性子串派生，见 GOLD_INDEX enrichment_policy）；may_decline=true 279 轮（279/3102=9.0%）。
- may_decline 以 SEALED episode 的 `input.may_decline` 为准（GOLD_INDEX source_policy，与评分器消费口径一致）。

---

## 8. 开发/选择/独立测试划分与曝光历史

### 8.1 三个面的划分（按项目自述）

| 面 | 材料 | 依据 |
|---|---|---|
| 公开开发面（dev_exposed） | dev_pilot_v02 3 篇 + dev_expansion_v01 9 篇 + stage1 8 + stage2 16 + stage3 8 + stage4 8（exposure.json dev_exposed=true 共 40 篇 stories_v2 材料） | `DEV_EXPOSED_MANIFEST.json`（registered_as="DEV_EXPOSED（公开开发集；独立选择集/B/扩题不在此列）"）；exposure 普查 dev_exposed true=40/false=181 |
| 选择/判别面 | wave1/wave2/wave3/wave3_ext（dev_exposed=false 于 exposure.json，但部分已被批次消费→账本 EXPOSED） | 账本 lifecycle |
| 独立盲测面 | **B 线 24 篇（唯一 blind）** + 未消费 SEALED 池 | `B_LINE_MANIFEST.json` only_once 条款；账本 SEALED=161 |

- ready_8 / first4（早期开发选择记录，`RESEARCH_STATE.json` story_pool_after_w0303）：ready_8=[E021,E022,E023,E025,E026,E030,E032,E033]，first4=[E021,E026,E032,E033]，errata 三条（E029-T07 时间视野跳变等），backup_clean=[E028,E035,E036]。

### 8.2 曝光历史（custody consumed_by）

出处：`STORY_CUSTODY_LEDGER.json`（脚本 §[6]）。

- registered=210；SEALED=161 / EXPOSED=49（恒等式 I8，PASS）。分波：stage2 8S+8E、stage3 4S+4E、stage4 0S+8E、wave1 16S、wave1_ext 2E、wave2 48S、wave3 78S+18E、wave3_ext 7S+9E。**账本注册表不含 stage1（8 篇）与 stageD（3 篇）**（账本 stages 实测只有 stage2/stage3/stage4/wave1/wave1_ext/wave2/wave3/wave3_ext 八族，合计 210），这 11 篇早期材料的消费不在本账本追溯范围内；`W03-05` §4 同样明文旧代故事（E004–E014，stories_v2 之前）"不在本注册表；其暴露视为全量已消费"。
- events=173（含具名事件 generation_isolation_ack、各波 semantic_review_passed、b_line_sealed、b_line_resealed 等 12 种，其余为逐批 expose 事件）。
- 有 `consumed_by` 的篇=49（与 EXPOSED 数一致）。消费批次族谱（按批次去重计数）：longprobe 系（stage4 8 篇）、fs1_a/b capture/live 系、p08_s1、SLIM-G1、LR1/FLREG 系（E061）、LR2/FLJUDGE（LRE048/LRE060）、W13-03 新域、W14 系、W15N、W16-UNC、W17/W17B/W17C、W18/W18B、W19B-CROSS、LD1 等。历史事件由 logs/ 扫描播种（`W03-05` §3："历史事件由 logs/ 扫描播种（52 件，2026-10-24..31）"）。
- **账本曾经漏记的实证**：54 条 backfill exposure（2026-11-03 补录，28 篇）。此事是 B 线 v1 污染事故的根因（§5），说明"账本没记=未暴露"在 2026-11-03 之前不完全可靠；补录后的账本是当前唯一权威（`W03-05` §2"账本 lifecycle 字段是唯一权威"）。

### 8.3 独立验证资格的现状（如实结论）

- exposure.json `independent_review=false` 221/221：**当前不存在任何一篇有"独立评审资格"标注的故事**；"独立故事检验"面在数据集标注层面为空，只能靠未消费 SEALED 池（161−24 B 线−其他预留）承担。
- B 线 24 篇是唯一 blind 集（manifest only_once："仅允许在封存冠军验收时运行一次；运行后整目录转 CONSUMED"）。
- W03 普查（2026-10-30）记录 43 篇（31 manifest 篇 + 12 AK）`dev_exposed=true` 36 篇 / false 7 篇（stageD 3 + final 4）、`independent_review=false` 43/43——与当前 exposure 普查在 stage1–4 一致，final K/N 的 dev_exposed=false 是"新材质未在开发迭代用过"，但其作者仍是 worker 系 AI（author_type=AI_worker_new_material_final），不是独立第三方。

---

## 9. 分母清单（每类统计的显式分母）

| 统计 | 分母 | 出处 |
|---|---|---|
| 库篇数 208 / 库轮 2944 | 库=7 波目录 | 脚本 §[1] |
| sidecar 221 篇 / 3102 轮 / 2919 with_assertions / 79 empty_gold / 104 有卡无断言 | sidecar=库+stage1+stageD+wave1_ext | GOLD_INDEX + 脚本 §[2] |
| 原子 6603 及其 status/negation/required_this_turn/valid_time/subject/relation | 6603 原子 | GOLD_*.json 重算（脚本 §[3]） |
| must_not_affirm 711 | 6603 原子（取消族 412 ∪ negation 632，交集 333） | 同上 |
| intent_contracts 117 / keywords 114 轮 / 348 组 / may_decline 279 轮 | 3102 轮 | 同上 |
| 九维覆盖各百分比 | 2944 库卡轮 | reference.json 扫描（脚本 §[4]） |
| 域分布 184 | 184 标准篇（不含 CF/长剧/stage1/stageD/wave1_ext） | dedupe 文件（脚本 §[5]） |
| 显式 domain 152 | wave1+wave2+wave3 主 manifest 条目 | 各 STAGE1_MANIFEST.json（脚本 §[5]） |
| custody 210/161/49/173 事件/54 补录 | 账本注册表 | STORY_CUSTODY_LEDGER.json（脚本 §[6]） |
| exposure 221 / dev_exposed 40+181 / independent_review false 221 / real_user_material false 221 / frozen_at 3 | 221 篇五件套齐备的篇 | exposure.json 普查（脚本 §[7]） |
| B 线 24 = 16+4+4 | manifest count | B_LINE_MANIFEST.json（脚本 §[8]，episode 零读取） |

---

## 10. 不能证明之事（审查声明）

1. **无独立人类评审**：221 篇 `independent_review=false`；全部"审查"是 AI 审查代理批次 + worker 直修。任何"数据集经过人工审核"的说法不能由仓库证据支持。
2. **生成隔离不能逐会话证明**：账本仅 1 条 generation_isolation_ack；逐会话隔离声明在 audit.md/RESEARCH_STATE 叙述中，未逐一会话入账，本材料未逐会话核验。
3. **relation 无类型体系**：4468 种自由文本 relation，无法产出 fact/date 等分型统计；基线中"无类型枚举"的判断属实。
4. **无逐条故障族标签**：themes/conflict_type 是篇级自由词；"某题覆盖某故障族"的说法无题目侧字段支撑。
5. **账本完整性在 2026-11-03 前不可追溯地证明**：54 条补录证明漏记发生过；补录是否穷尽（是否还有未发现的漏记消费）无法从账本自身证明，只能由 logs/ 侧独立对账（本材料未做全量 logs 扫描对账）。
6. **marker/quote 的 0 违例是子串级结论**：1099 个 marker 逐字回验 0 违例（脚本级子串匹配），证明"引语在题面中出现"，不证明语义正确或 gold 判分正确。
7. **gold 语义质量不可程序证明**：104 轮"有卡无断言"与 79 轮 empty_gold 的轮次，程序分覆盖有限；原子语义是否正确仍依赖审查代理判决（§3.4）。
8. **B 线内部构成只有 manifest 声明**：本材料遵守封存未读取其 episode；其"24 篇 sha256 对账"只有 manifest `verified_at` 字段佐证，未独立重算（重算需读取该目录，超出本材料授权）。
9. **README v1 口径未同步**（§5）：以 manifest v2 为准，但这意味着依赖 README 的读者会得到错误的长剧/CF 名单。
10. **前代材料与当代库的兼容性未审计**：E001–E012、怪物题、AK 锚为不同时代的量尺与形态，本材料只登记存在与数量，不声明其与 208 库可比。
11. **域"其他"仅 1 篇（E044）**与兴趣社交 8 篇是实测缺口/新域事实；域配额是否"达标"取决于目标口径（`W03-06_WAVE3_SKELETON_PLAN_20261102.md` 的目标表曾写兴趣社交达成 7，实际 184 口径为 8——差异来自后续 E024/E025/E027 收编口径变化），审查者应按 dedupe 文件为准。
12. **长剧与母本的"逐字保留"只抽样验证**：LRE117↔E117 前 12 轮 input 12/12 一致；其余 15 篇长剧未逐一验证。

---

## 11. 可复现性（脚本实测对账）

- 脚本：`refs/dataset_review_20261107/recompute_stats.py`（纯标准库；只读；不读 `benchmark/b_line/` 任何 episode，仅读其 manifest 元数据文件）。
- 运行方式：仓库根 `D:\lovebrain-arena` 下执行
  ```
  python -I refs/dataset_review_20261107/recompute_stats.py          # 文本摘要
  python -I refs/dataset_review_20261107/recompute_stats.py --json   # 机器可读全量
  ```
- 2026-11-07 实测运行（文本模式全文要点，与本文各节数字一致）：

```
=== recompute_stats 只读复算（root=D:\lovebrain-arena）===

[1] 库规模（目录实测 episode.json）
  stage2     episodes=16  turns=192   轮数直方图={12: 16}
  stage3     episodes=8   turns=64    轮数直方图={8: 8}
  stage4     episodes=8   turns=192   轮数直方图={24: 8}
  wave1      episodes=16  turns=192   轮数直方图={12: 16}
  wave2      episodes=48  turns=576   轮数直方图={12: 48}
  wave3      episodes=96  turns=1152  轮数直方图={12: 96}
  wave3_ext  episodes=16  turns=576   轮数直方图={32: 8, 40: 8}
  库合计 episodes=208 turns=2944
  [sidecar-only] stage1     episodes=8 turns=64
  [sidecar-only] stageD     episodes=3 turns=22
  [sidecar-only] wave1_ext  episodes=2 turns=72

[2] GOLD_INDEX.json（GOLD_SIDECAR_v2 generated=2026-11-03T22:25:52）
  episodes=221 / turns=3102 / with_assertions=2919 / empty_gold=79 / no_card=0
  atoms=6603 / unknowns=2838 / intent_contracts=117 / must_not_affirm_atoms=711
  atoms_with_affirm_markers=565 / source_conflicts=93
  marker_qc={'n_markers': 1099, 'verbatim_violations': 0}
  qc_sample={'seed': 20261101, 'n': 40, 'strict_all_pass': 40, 'lenient_all_pass': 40}
  per_wave: stage1 8/64/64、stage2 16/192/115、stage3 8/64/53、stage4 8/192/123、
            stageD 3/22/47、wave1 16/192/535、wave2 48/576/1657、wave3 96/1152/2552、
            wave3_ext 16/576/1232、wave1_ext 2/72/225（格式：ep/turns/atoms）

[3] 原子级复算（分母=3102 轮 / 6603 原子）
  无断言但有卡轮=104（with_assertions+empty_gold+该类=总轮数）
  status 分布: asserted 4994、confirmed 616、superseded 224、uncertain 195、
              suspended 108、cancelled 90、corrected 77、retracted 63、resolved 39、
              active 29、paused 14、fulfilled 13、open 13、revised 12、seed 11、
              negated 11、invalidated 11、resumed 7、revoked 7、其余长尾各≤6（44 种）
  取消族=412  negation=true=632  交集=333  并集(must_not_affirm)=711
  required_this_turn=true=1874  valid_time 非空=6603/6603  relation 去重=4468 种
  subject 规范五类={'HER': 2047, 'SHARED': 2503, 'THIRD': 1244, 'ME': 137, 'PUBLIC': 271}
                   合计=6202 自由文本其余=401
  intent_contract(intent 非空)=117  有 keywords 轮=114  关键词组=348  may_decline=true 轮=279

[4] 九维卡面覆盖（分母=2944 库轮 / 208 篇 reference.json）
  interaction 1924 (65.4%) / memory_use 1625 (55.2%) / goal 1623 (55.1%) /
  emotion_relation 1460 (49.6%) / factuality 1234 (41.9%) / time_state 908 (30.8%) /
  boundary 763 (25.9%) / initiative 580 (19.7%) / attribution 345 (11.7%)
  标签实例合计=10462（一卡多维，故各维之和>100%）

[5] 域分布（184 标准篇，出处 W03-06_SKELETON_DEDUPE_20261031.json）
  工作职场19 / 数码消费19 / 运动健身18 / 学习成长18 / 家居装修18 / 医疗健康18 /
  餐饮聚会17 / 宠物17 / 出行拼车16 / 家庭事务15 / 兴趣社交8 / 其他1；合计=184
  near_dup_clusters=[]（空表）
  wave manifest 显式 domain 篇数=152（wave1/2/3 主 manifest）

[6] 保管账（STORY_CUSTODY_LEDGER.json）
  registered=210 {'EXPOSED': 49, 'SEALED': 161}
  分波: stage2 8E+8S / stage3 4E+4S / stage4 8E / wave1 16S / wave1_ext 2E /
        wave2 48S / wave3 18E+78S / wave3_ext 9E+7S
  events=173  backfill exposure 事件=54（涉及 28 篇）  有 consumed_by 的篇=49
  具名事件: generation_isolation_ack、wave1/wave2/wave3_t1 semantic_review_passed、
            wave2_batch2_review_passed_96_reached、wave3_ext_g2_seeded、
            lr2_material_*、b_line_sealed、b_line_resealed 等 12 种

[7] exposure.json 普查（n=221）
  author_type: AI 178、ai_generated 16、synthetic_ai_generated 10、
              isolated_generation_session 8、synthetic_generator 4、ai_generator 2、
              AI_subagent_candidate_blind 2、AI_worker_targeted_retest 1
  dev_exposed true/false = 40/181  independent_review false=221
  real_user_material false=221  frozen_at 非空=3

[8] B 线（仅 manifest 元数据，episode 零读取）
  version=b_line_v2 date=2026-11-03 count=24（标准16+长剧4+CF4）
  integrity={'algorithm': 'sha256', 'verified_at': '2026-11-03T21:51:25'}
  supersedes=b_line_v1（v1 选篇含账本漏记的实际已暴露篇
  E117/E164/E191/LRE121/LRE146/LRE127；账本已补录 54 条 exposure 事件后重选）

[9] 恒等式核对（8/8 PASS）
  I1_sidecar_episodes=208+8+3+2=221 : PASS
  I2_sidecar_turns=2944+64+22+72=3102 : PASS
  I3_with_assertions+empty_gold+card_without_assertions=turns : PASS
  I4_must_not_affirm=cancel_family∪negation : PASS
  I5_atoms_index_vs_recount : PASS
  I6_domain_184_sum : PASS
  I7_wave_manifest_domain_152 : PASS
  I8_custody_210=S161+E49 : PASS
```

- 与任务基线的对账结论：
  - **全部主数字一致**：208/2944/3102/2919/79/6603/2838/117/711/565/412/632/1874/4468/subject 五类与 401/九维九数/域 12 数/分波 per_wave/custody 210=161+49/events 173/补录 54——脚本实测与基线完全吻合（8/8 恒等式 PASS）。
  - **两处口径修正/补充**：①"subject 自由文本 401 条"实测分布在 stage1–4 **加 stageD**（基线写 stage1-4）；②author_type 实测含 8 种变体（基线笼统称 AI），且审查批次名"R1-R8"实际为 R1a/R1b/R2–R6 + S1–S8（§3.2）。
  - **一处基线未列的新数字**：104 轮"有卡无断言"（I3 恒等式的第三类，使 2919+79 与 3102 的差得到解释）。
  - 运行环境：Windows 10 / Python 3.12.4；脚本对控制台 GBK 代码页已做 UTF-8 输出处理，`--json` 输出可直接被 `json.load` 消费。

---

*本材料由只读审查产生；除本目录两个文件（本文档与 recompute_stats.py）外未写入仓库任何位置。*
