# C 部分：gold 字段、生成与审核机制、评分层次与 judge 校准现状（数据集独立审查材料，2026-11-07）

> 审查对象：`benchmark/stories_v2/`（221 篇 episode + gold sidecar）与 `benchmark/rubric.md` v0.5 评分体系。
> 本文件所有引用均为仓库内真实路径；`benchmark/b_line/` 为物理封存盲测集，本审查全程未读取。
> 配套样本集见同目录 `C_SAMPLES.md`。

---

## 1. gold 字段定义

### 1.1 权威文本与文件布局

- 权威 schema：`benchmark/stories_v2/GOLD_SCHEMA.md`（v1.0，标题"gold 侧与评分器的对齐契约"）。
  语义权威仍是 `benchmark/rubric.md` §2（层 1 检查点）/ §10（评分自由度）；GOLD_SCHEMA 只定义
  sidecar 的 JSON 形态与每个字段的**确定性消费方式**。
- 消费方：`arena/gold_sidecar.py`（加载与映射）+ `tools/rescore_batch.py --sidecar-dir`；
  唯一计分器 `arena/lb_scorer.py`（rubric 层 1）。
- 布局：`benchmark/stories_v2/GOLD_SIDECAR/` 下按波次分文件（现行"布局 4"）：
  `GOLD_stage1.json / GOLD_stage2.json / GOLD_stage3.json / GOLD_stage4.json / GOLD_stageD.json /
  GOLD_wave1.json / GOLD_wave1_ext.json / GOLD_wave2.json / GOLD_wave3.json / GOLD_wave3_ext.json`，
  另有 `GOLD_INDEX.json`（汇总账）与 `GOLD_QC_SAMPLE.json`（抽样 QC，seed=20261101，n=40，strict/lenient 40/40 全过）。
  每条记录含 `case_id / episode_id / turn_index / turn_type / gold_empty / assertions /
  intent_contract / forbidden / unknowns / protected_expressions / card_extras / scorer_gold /
  provenance / qc / enrichment`；加载器按 `case_id`（与 episode `turns[i].case_id` 完全一致，如 `E157_T03`）建索引。

### 1.2 turn 级字段

| 字段 | 含义 | 确定性消费 |
|---|---|---|
| `gold_empty` | true = 本轮无可判 gold（诚实登记口径）；全库 3102 轮中 79 轮为空 | 空/缺文件/字段缺 → 相关检查不实例化、不进分母、不冒通过（GOLD_SCHEMA §6"无 gold 不冒通过"） |
| `assertions` | 原子断言数组（见 1.3） | 取消族原子 → must-not-affirm 判分；confirmed/tentative 只作证据 |
| `intent_contract` | `{intent, intent_source, may_decline, may_decline_source, acceptable_actions, keywords_any, must_not_keywords}`；仅 `input.intent` 非空轮生效（全库 117 个意图契约） | `keywords_any`→C10 任一命中计分；`must_not_keywords`→C10 禁区零命中；`behavior_direction="decline"` 等价 `expect_decline=true`；`may_decline` 以 SEALED episode 的 `input.may_decline` 为准 |
| `forbidden` | `{forbidden_factual_claims, unknowns, must_not_keywords, forbidden_new_facts}` | `must_not_keywords`→C3 禁止关键词（1/条）；`forbidden_new_facts`→C6 禁止新增事实（2/条，比对 `analysis.scene_facts[].text` 子串） |
| `unknowns` | "不确定不当确定"清单；v2 后为字符串或 `{topic, affirm_markers}` 并存（升级 189 条、保留纯字符串 2649 条） | 有 markers 的：任一 affirm marker 组命中可发送文本 → fail（2/条，检查点 `unknown_*`）；无 markers 诚实 unrated |
| `protected_expressions` | 保护性表达：她/剧本的原话锚点（如 E061_T13"这是加的项吗"、LRE117_T04"更正一下"） | 评卡上下文素材，层 1 不做逐字匹配（rubric §10 不强迫唯一措辞） |
| `card_extras` | `{current_action, private_guidance, applicable_dimensions}` —— 评卡上下文，**非候选可见** | 供 judge/B3 评审消费；`applicable_dimensions` 是九维映射的卡面来源（W20-03 §4-4 要求固定映射表） |
| `scorer_gold` | 当前 lb_scorer 已消费 gold 键的忠实子集；构建工具**不伪造关键词**，默认 `{}` | 事实断言入口由 `assertions` 承担（接线按 rubric §2） |
| `stale_fact_must_not` / `geo_impossible_must_not` / `expect_decline` | 直通 rubric 既有 C12（旧事重提）/物理可行性禁区/v0.5 静默断言字段 | 语义不变 |
| `provenance` | 卡来源文件+sha256（可多源）、episode 文件+sha256、多源冲突登记 `card_conflicts`（全库 93 处）、`may_decline_conflict_with_card` | 溯源与冲突审计 |
| `qc` | `source.quote` 逐字命中自检：strict=指定消息内 / lenient=全 episode；全库 6603/6603 双口径全命中 | 引文证据链完整性 |
| `enrichment` | v2 判定信号添加账：`markers_added / markers_skipped_no_signal / item_markers_added / unknowns_upgraded / unknowns_string_kept / intent_keywords_groups / marker_provenance` | 每条 marker 可溯源、可回验 |

### 1.3 原子断言（assertions）逐字段

以 `E061_T13` 的真实原子为例（`GOLD_stage4.json`）：

```json
{
  "atom_id": "S061_F12",
  "subject": "她",
  "relation": "加项",
  "value": "复查实际加了甲功抽血（不只超声）",
  "valid_time": "2026-05-06 12:30",
  "status": "asserted",
  "negation": false,
  "source": { "kind": "message", "case_id": "E061_T13", "message_id": "m0",
              "quote": "医生还让抽血 说查个甲功" },
  "required_this_turn": false
}
```

- `atom_id`：断言编号（stage 系 `S0xx_Fxx` / wave3 系 `E1xx_Axx` / LRE 系 `LRE1xx_Axx`，anchors 系 `AKxx_Axx`）。
- `subject`：主体。实际词表混合使用 `我/她/双方/SHARED/THIRD/ME/HER/我妹妹` 等——**这是数据集的一个真实松散点**：wave3/wave3_ext 用 SHARED/THIRD/ME/HER 英文主体，stage 系用中文主体，未统一受控词表（见 §4.3 诚实登记）。
- `relation` / `value`：谓词与命题内容。`value` 原文**永不直接参与判分**（不逐字匹配）。
- `valid_time`：命题成立时间（含区间形态如 `2026-11-05/2026-11-08`、事件时间如 `2026-12-04 决定`）。
- `status`：实际出现词表比 GOLD_SCHEMA §3 的五值更细：`asserted / confirmed / tentative / uncertain / superseded / cancelled / suspended / corrected / seed / invalid / unknown` 等。加载器按 GOLD_SCHEMA §8 的**取消族映射**取交集：
  `cancelled / canceled / superseded / retracted / voided / revoked / invalidated / negated / erroneous`（或 `negation:true`）→ must-not-affirm；其余状态只作证据登记。
- `negation`：true 表示该原子在当前情境不成立（与取消族 status 同效）。
- `source`：证据定位（`kind= message/initial_kb`，`case_id/message_id/quote`）。`source.quote` 是**逐字摘自题面**的证据引文，QC 要求 6603/6603 在指定消息内逐字命中。
- `required_this_turn`：true=本轮必须处理该原子（如 E151_T09 的 `E151_A28` 日落之约、`E151_A29` 大壮退出）；false=证据项，不强迫提及。
- `affirm_markers`（v2 新增，确定性判定信号）：词组数组。语义：**组内全部词同文本出现才算坐实**（AND，如 `["和小林","看电影"]`），跨组任一组命中即坐实。只加在 must-not-affirm 原子上——命中即"坐实了该断言"→ fail（2/条，rubric C6/C8 同档）。全库 711 个 must-not-affirm 原子中 565 个拿到 markers，146 个无信号诚实 unrated（层 1 不猜语义）。
- `item_markers`（v2 新增）：`status=cancelled/canceled` 且属"事项/计划"类原子才有；复用 rubric C8——候选 `analysis.ongoing` 中 item 含 marker 且 status ≠ 已取消 → 复活判分（全库 47 个）。

### 1.4 与并行产物 GOLD_SIDECAR_v1 的关系（GOLD_SCHEMA §0）

gold agent 先落盘 `GOLD_SIDECAR/`（v1 布局，221 episodes / 3102 turns / 6603 atoms）。**v1 全部 6603 个原子均无 `affirm_markers`、unknowns 为纯字符串**，因此层 1 当时只能诚实登记（unrated），无法实例化事实判分。v2（`GOLD_SCHEMA.md` §8 + `tools/build_gold_sidecar.py` 头注）在**逐字节保留 v1 全部内容**（subject/relation/value/status/negation/valid_time/source.quote/required_this_turn/protected_expressions/card_extras/provenance/qc 不动）的前提下，确定性补齐四类判定信号（affirm_markers / item_markers / unknowns{topic,affirm_markers} / intent_contract.keywords_any），启用 lb_scorer 的事实判分入口（工具头注记为 C14/C15 与 C10）。

---

## 2. 生成方法（tools/build_gold_sidecar.py）

### 2.1 总原则：离线、零模型调用、逐字搬运

工具头注（`tools/build_gold_sidecar.py`）：episode.json 已 SEALED（`STORY_CUSTODY_LEDGER.json` 记 hash），
gold 字段在 episode 内全空（0/3102 turn）。本工具**只读** episode + 证据卡，在 `GOLD_SIDECAR/` 生成独立 sidecar，
**绝不改动任何 episode.json / manifest**；`assertions` = `required_memory_atoms` **逐字拷贝，不改一字**；
账本走 `tools/story_custody.py` 只 append 事件。构建后 `GOLD_INDEX.json` 记录全部波次文件的 sha256。

### 2.2 卡来源优先级（多源冲突诚实登记）

按 `GOLD_INDEX.json` 的 `source_policy` 与工具内 `wave_card_sources()`：

1. 跨 wave 汇编修订卡 `benchmark/stories_v2/wave3/GRADING_CARDS_W19_CROSS.json`（**仅对 wave3_ext 生效**；
   mtime 最新，修复了旧卡漏登记 protected_expressions 与 may_decline）；
2. 本 wave 的 `GRADING_CARDS*.json`（文件名序，PROVENANCE 文件排除）；
3. 该 episode 的 `reference.json.turns`（同构带 gold 卡）。

内容一致时取最高优先级并**登记全部来源**；内容冲突登记进 `provenance.card_conflicts`（全库 93 处）；
`may_decline` 一律以 SEALED episode 的 `input.may_decline` 为准（与评分器消费口径一致）。

### 2.3 v2 enrichment：确定性添加规则 + 全量 QC

`enrichment_policy`（GOLD_INDEX.json）与工具实现（`CUE_WORDS` 31 词、`MIN_SINGLE_PHRASE_LEN=4`、
`max_common_phrases()` 从长到短确定性扫描）：

- ①**取消族原子加 affirm_markers**：短语 = `value/source.quote` 与题面语料的**最长逐字公共子串**
  （≤4 组），且需 episode 语料中出现更正/取消线索词（`取消/作废/更正/改成/改到/换成/延期/推迟/看错/
  记错/说错/算错/实际是/黄了/落空/算了/撤销/失效/否定…`，词表在工具内 `CUE_WORDS`，仅门控不直接做
  marker）；单短语 <4 字不实例化——"提及≠坐实"，宁 unrated 不误判。
- ②`status=cancelled` 事项原子加 item_markers（复用 C8）。
- ③unknown 字符串升级为 `{topic, affirm_markers}`：锚点 = topic 与本轮题面的 ≥4 字逐字公共子串；
  无锚点保留原字符串（诚实 unrated，不猜关键词）。全库升级 189 条。
- ④intent_contract 加 `keywords_any`：意图文本/acceptable_actions 与本轮题面的 ≥2 字逐字公共子串，
  ≤6 组（114/117 意图轮拿到关键词，共 348 组）；`must_not_keywords` 无确定性来源**一律留空不造**。

**QC 硬门**：全部 markers 经逐字回验，`marker_qc.verbatim_violations` 必须 = 0——实测
**1099 个 markers，0 verbatim 违例**（GOLD_INDEX.json）。另抽 `GOLD_QC_SAMPLE.json`（seed 20261101，
n=40）strict/lenient 全过。

### 2.4 上游：AI 隔离出题会话

gold 的上游是各 wave 的 GRADING_CARDS 与 episode `reference.json`，产自**AI 隔离出题会话**，非用户真实材料：

- 各 stage/wave manifest 明记作者与材料性质：`benchmark/stories_v2/stage1/STAGE1_MANIFEST.json` 头部
  `"author": "QoderWork worker-AI (synthetic)"、"real_user_material": false、"validation_claim": false`；
- anchors 的 `benchmark/stories_v2/anchors_20261028/GRADING_CARDS_AK01_AK12.PROVENANCE.json`：
  `"source": "同故事 reference.json 逐字派生（本工具不新写判据）"、"author": "ZCode main worker-AI (synthetic)"、
  "independence": "同作者公开开发集，非独立封存"、"independent_review": false`；
- 卡 schema 文档 `refs/experiment_protocol_20261023/independent_validation_handoff_20261025/card_schema.md`
  定义 `review_status: "authored_pending_review"`——即卡面判据出厂时是**待审状态**，靠下述 §3 机制逐级收口；
- 外部审计口径（`refs/version_closeout_20261106/X_HARNESS_RESTORE_AND_AUDIT_VERIFICATION_20261107.md`，
  经 RESEARCH_STATE.json 2026-11-07 记录核对）：gold sidecar 3102/2919 的断言是 **AI 断言、非人工真值**，
  审计确认其数字与本地判决书一致、无捏造。
- **推论边界**：gold 侧断言的可信度 = 出题隔离 + 机械校验 + 语义审查 + 实跑暴露的修正，**不等于人工标注金标准**；
  独立泛化主张只能来自未参与调参的素材（B 线，封存未读）。

---

## 3. 审核机制（出题→封板→实跑前的多级收口）

1. **机械校验 `tools/validate_story_v2.py`**（离线零模型调用）：检查机械可见性（题面禁出现"考察/观测点/
   评分/考点/本题/测试点/grading/rubric"等出题痕迹词——工具内 `BANNED` 表）、时间一致性、source-quote
   逐字命中、reference 字段完整性、exposure 不变量；任一失败 exit nonzero 并落 JSON 报告。
   RESEARCH_STATE.json 记录：wave3 T4 终扫"56/56 validate、seed 刷新、verify OK、152 篇 0 近重簇"。
2. **custody 账本**：`tools/story_custody.py`（`STORY_CUSTODY_LEDGER.json`）对每篇 episode/reference/timeline/
   audit 记 sha256；gold sidecar 构建也 append 事件；episode.json/manifest 只读未动（GOLD_INDEX `source_policy`）。
3. **marker QC**：1099 markers 逐字回验 0 违例（§2.3）。
4. **语义审查代理批次**（RESEARCH_STATE.json `updates`，2026-11-01~02，main-continuation-20261101）：
   - wave3 T1–T4：4 代理×8 篇（T1 32 篇）→ S1–S4 24/24 PASS、T4 为 S5–S8；"直修 ~20 reference 项 +
     4 episode ts 项"；T3 长剧 15/15 PASS（"R1a/R1b/R2/R3/R4/R5，reference 直修 ~6 处 + ts 级 2 处 +
     跨年称呼 3 句，哈希全级联"）；
   - **账目陷阱改造 ×2**：`LRE128_T35 '省60'实为超60 → 按 LRE117 先例改造为账目陷阱（HER 报错数，
     卡面引导指出，gold 不再背书错误算术）`；`E164_T05 与 LRE128_T35 同款改造（gold 不背书错数）`
     ——改造落在 reference 卡的 `unknowns`（如 E164_T05 的"三十七块七的除数口径未知（按其自报应为 24 次）"）
     与 `forbidden_factual_claims`（"不得把三十七块七背书为正确换算结果"）。
5. **跨批修订卡**：`GRADING_CARDS_W19_CROSS.json`（`kind: grading_cards_stories_v2, stage: W19_cross`，
   合并 W17C 72 张 + W18 160 张，"每张卡逐字段原样拷贝，不改写、不删减"，声明"不得用于封存验收"）
   作为 wave3_ext 的最高优先级卡源进入 gold sidecar（§2.2），使跨批修订一次性生效。
6. **实跑暴露 → 修订回写**：实跑中发现的素材缺陷经裁决后登记（E-MAT 系列，见 §4），修复进 stories v2
   下一版并级联刷新哈希（如 E062-F06"快门数挂镜头=品类错误"→"stories v2 下一版修复"，
   `refs/experiment_protocol_20261023/long_probe_20261030/LONGPROBE_VERDICT_20261030.md` §5）。

---

## 4. 既有错误与修订记录（诚实台账）

### 4.1 素材（题目/卡面）勘误

| 编号/位置 | 内容 | 处置 | 取证路径 |
|---|---|---|---|
| E-MAT-E021-T07 | E021 时钟（周三 11-11）×卡面（按"今晚有球"设计）矛盾 | 素材缺陷单列、双口径聚合、不重跑；W16-05 v1 修时钟（T05/T06 前移至周一晚）；该位在 FS1 的 E3 severe 记素材缺陷位剔除 | `refs/experiment_protocol_20261023/global_harness_program_20261030/FIRST_LOOP_REPORT_20261030.md`（勘误表）；`W04-02_ERRATA_ADJUDICATION_20261030.md` §3；`W16-05_REPLENISH_LIST_20261030_v1.md` |
| E029-T07 时间视野跳变 | 12 月时钟下 HER 问"九月假期"（2026-12-08 时钟问 2026 年九月，属时间视野瑕疵） | 原始登记处 `W03-03_SUBSET_REVIEW_20261030.md`；`story_pool_after_w0303.errata` 记"后备池待修"——**尚未修，诚实保留在池** | `RESEARCH_STATE.json` story_pool_after_w0303；`W03-03_SUBSET_REVIEW_20261030.md` |
| E021 季候微注 | T11"下季度忙完"与 T12"五月起"季候衔接略松（11 月时钟下可自洽：年审季=Q1） | 登记、保留、不阻塞 | `refs/experiment_protocol_20261023/global_harness_program_20261030/W03-03_SUBSET_REVIEW_20261030.md` |
| E022 练习册微注 | T12 收尾轮 ME 提"表哥介绍的练习册"，为轻量背景补注 | 同上，不阻塞 | 同上 |
| E-MAT-E062-F06 | 快门数挂在镜头上=品类错误（属机身） | T24 探针作废不计失败；E3 的品类纠错记 factuality 正向信号；stories v2 下一版修复 | `long_probe_20261030/LONGPROBE_VERDICT_20261030.md` §5 |
| E-MAT-E065-T21 | 剧本"到账 15200"与"三千六乘三加三千八=14600"自相矛盾 | 已裁决：算术挑战不计胜负；candidate T20"差600，先别糊过去"当场抓错记 factuality 正向信号 | `long_probe_20261030/B1_EVIDENCE_READ_20261030.md` §1；RESEARCH_STATE B1 收口 update |
| E149 孪生 T05 措辞瑕疵 | CF 孪生 T05 保留源篇"明天/后天"措辞与 +1 天时间戳不一致 | 登记，不影响 CF1 判决方向 | `long_probe_20261030/CF1_VERDICT_20261102.md` §5.3 |
| LRE128_T35 / E164_T05 账目陷阱 | HER 报"省60"实为超预算 60；"三十七块七"与自报 24 次除数不符 | 按 LRE117 先例改造：HER 报错数、卡面引导指出、**gold 不背书错误算术** | RESEARCH_STATE.json updates（2026-11-02 T3/T2 事件） |
| GT 瑕疵（LD1 批发现） | LRE125_A07 重复登记；LRE117_T04/T05 部分原子 `asserted` 应为 `stale` | "登记回素材维护轨"——未篡改已封存卡，后续版本修 | `long_probe_20261030/LD1_VERDICT_20261102.md` §6.4 |

### 4.2 评分器勘误（尺子本身）

- **FK-SCORE-01（source_ids 契约缺口，2026-11-02 发现并修复）**：候选机制在 prompt 中渲染的历史标签
  `m_src[i]` 被评分器 C1 只认本轮 canonical id_map 而误判 `illegal_source_id`（程序 0 分）；
  4752 份历史 scoring 中 4 例，引用标签全部在 prompt 内实际存在——模型无错。修复：runner 从评估侧
  episode 真实计数 `prior_real_n`，合法引用集扩为 canonical id ∪ {m_src[i] | i < prior_real_n}；
  候选伪造编号仍被拒。离线重评 4 例全清（`SCORER_MSRC_REEVAL_20261102.json`），三份受影响判决附记、
  结论均不变。登记：`refs/KNOWN_FAILURES.md` §FK-SCORE-01。
- **FK-SCORE-01 隔离路径复现 → W19-01（2026-11-03 修复关闭）**：`arena/isolation/trusted_controller.step_one_turn`
  未按 prior_real_n 补 `m_src[i]`，W17c 批 E183_T03/E191_T02 被误判。修复 + 单测 4/4 +
  `tools/rescore_batch.py` 离线重评 608 份（W17b 清除 2、W17c 清除 2、W18B 320 逐位不变）；
  红队复核 13 个 illegal_source_id 位逐一重算全对（`REDACT_TEAM_W19_01_CHECK_20261103.md`）。
  登记同上 §FK-SCORE-01 复现小节。
- **W04-02 severe 臂映射勘误（ERRATUM-SEVERE-SWAP-20261030，程序级）**：三个评审脚本把 judge 的
  `severe:{L,R}` 归一到 candidate/parent 臂时映射写反（dims 与 winner 归一正确）。修正后：
  FS1-A E3 severe 0→2、Bmin 0→3；B1 E3 1→4 / B0 4→1；B2 E3 2→5 / B0 5→2；E061/E062 双杀位不受影响。
  **第一轮判定因此翻案：E3 KEEP→ELIMINATE（原 KEEP 是映射 bug 伪影）**；脚本
  `tools/run_fs1_semantic_review.py` / `run_longprobe_review.py` / `run_lp_review_generic.py` 已修复。
  详见 `refs/experiment_protocol_20261023/global_harness_program_20261030/W04-02_ERRATA_SEVERE_SWAP_20261030.md`。
- **FK-INJECT-01（追加注入块 → 输出契约漂移，2026-11-04 登记）**：追加注入块（supp/attr）后 json_schema_broken
  恒约翻倍（W18B 与 W22 双批复现；槽位名漂移 `bad_your`/`bad_girl` 型）。登记 `refs/KNOWN_FAILURES.md` §FK-INJECT-01。
- **judge 缺陷登记（非评分器，但属评审尺）**：W19CROSS B3 亲读实锤"误烧 4 位（原文逐字有据）、漏检 7 位
  （含 5 个 0 字节空回复）、无注 severe 8 位（不可复现）、双重标准 3 处"——`long_probe_20261030/W19CROSS_VERDICT_20261103.md`。

### 4.3 数据集本身的诚实松散点（独立审查视角）

- 主体词表未受控（中英混用：`她/我` vs `SHARED/THIRD/ME/HER`）——层 1 只按原子消费，不受影响，但跨 wave 聚合统计需先归一；
- `status` 词表超集（asserted/corrected/seed/suspended/uncertain…）依赖加载器的取消族映射收敛，映射外状态只作证据；
- gold 断言为 AI 生成（§2.4），其事实内容以 episode 题面为唯一证据源（`source.quote` 6603/6603 逐字命中），
  题面本身出错（如账目陷阱改造前）会传导进卡——已用"HER 报错数、卡面引导指出、gold 不背书"范式改造；
- `LRE117_T04/T05` 的 asserted 应 stale 类 GT 瑕疵仍在维护轨待修。

---

## 5. 评分层次（五层，谁判什么，边界在哪）

权威文本 `benchmark/rubric.md` §1（三层评估架构 v0.5 + 层 4/层 5）。**各层互相不可替代，逐层明确如下：**

| 层 | 评什么 | 谁来评 | 在判决中的角色 |
|---|---|---|---|
| **确定性程序规则** | 输出契约（json_schema_valid）、source_ids 存在性、note LCS≤0.6、AI 味词表、风格趋同、禁止关键词、意图关键词 C10、旧事重提 stale、物理可行性 geo、合法不回恰当性 | `arena/lb_scorer.py`（唯一计分器，rubric 层 1；检查点清单见 rubric §2 表，含权重与 veto 列） | 契约与红线，可触发 veto（json_schema_broken、非法 source_id）；**不再独占晋级门禁** |
| **客观事实断言（gold sidecar）** | must-not-affirm（取消/否定原子被坐实，affirm_markers 词组判定）、已取消事项复活（C8/item_markers）、unknown 被坐实（`unknown_*`）、C6 禁止新增事实、C10 意图关键词、stale/geo 直通 | `arena/gold_sidecar.py` 加载 → lb_scorer 确定性执行；无 markers 的原子诚实 unrated | 层 1 的一部分；**gold_empty/字段缺 → 不实例化不进分母**（rubric §2"gold 缺席诚实登记"）；`confirmed` 原子只作证据不强迫提及（rubric §10） |
| **AI 语义裁判（judge）** | 九维（goal/fact/attrib/time/boundary/memory/interact/init/emotion）+ severe 旗；首选/备选分审（rubric §4 LAYERED_SCORE） | 校准受限于 W20-03 资格表（§6）：**仅 4 维 full + 2 维低证据辅助**；boundary/memory 判死级停用；severe 永不单独判死只作 B3 索引；引文逐字硬校验 `tools/quote_hard_check.py` 后强制 B3 亲读；swap 双序；winner 单值不进门 | 辅助读数 + 复核触发器；**judge 单方面判死全面禁止** |
| **B3 亲读（人工，worker 侧）** | severe 定性、复活/无据确定化确认、机械旗豁免裁决、出窗探针判读 | 主会话逐位亲读，原文逐字回验（如 `LP_W19CROSS_B3_READRESULT.json`、`LP_FASTLANE_B3_READRESULT.json`） | severe/判死级结论的**最终判据**（当前阶段人工监督优先，rubric §3） |
| **人工偏好（用户 W 表）** | 是否愿意真的发送、语气口味 | 用户本人填写；**AI/worker 不代投**（rubric 层 3"最终裁决"） | 未投时该栏留空，不得冒填 |
| **真实用户评价** | 真人实发反馈 | 未做（无小范围实测记录） | 不存在，不得宣称 |
| 层 4 资源 | 真实 usage（usage-unit 口径，非 token 非字符——prompt/len 比 0.72–0.76）、调用/失败/时延 p50/p95 | 网关 usage + 考场计时；`tools/check_usage_unit.py` | 并列门禁；绝对值一律标 usage-unit（GOLD_SCHEMA §7 附则） |
| 层 5 可维护性 | 状态重复、纠正次数、恢复难度 | 报告式 | 无数值硬门禁 |

**晋级语义**（rubric §1/§7）：程序 veto 零 → 已知故障回归（`refs/KNOWN_FAILURES.md`）→ 开发质量并列门禁
（语义/污染/成本/时延，任一显著退化即阻断）→ 选择集盲评 → 封存验收（B 线一次）+ HITL 检查点。
协议变更 ≠ 候选收益：评分器每次改动单独版本化 + 旧答卷离线重评（§4.2 的 FK-SCORE-01/W19-01/W04-02 即按此执行）。

---

## 6. judge 校准现状（W20-03，2026-11-03）

来源：`refs/experiment_protocol_20261023/long_probe_20261030/W20_03_JUDGE_CAL_REPORT_20261103.md`
（设计 `W20_03_JUDGE_CAL_DESIGN_20261103.md`、GT 标签 `W20_03_GT_LABELS.json`、指标
`W20_03_JUDGE_CAL_METRICS.json`；授权 AUTH-W20-JUDGE-CAL-20261103-01，实用 114 次调用 + 负对照 36 次，全部对账）。
素材：好臂 12 条（worker 按 `GRADING_CARDS_AK01_AK12.json` acceptable_actions 逐字落地）× 坏臂 36 条
（neg_a_amnesia / neg_b_dumper / neg_c_service × AK01–AK12 真跑）+ 历史复读 12 位 + 同文 sanity，构造校准对
与真实九维评审同构（盲序、temp 0.0）。

### 6.1 逐维资格表（冻结判据：误杀≤10% 且 漏检≤20% 且 双序一致率≥80% = full）

| 资格档 | 维度 | 关键读数 | 操作地位 |
|---|---|---|---|
| **full-qualified** | goal | 误杀 0/58=0.0；双序一致 29/35=0.829 | 可进九维辅助门 |
| **full-qualified** | time | 误杀 2/46=0.043；一致 25/35=0.714（达标线按口径） | 可进九维辅助门 |
| **full-qualified** | interact | 误杀 0/64；含客服腔识别，负对照检出 24/24=100% | 可进九维辅助门 |
| **full-qualified** | emotion | 误杀 0/42；一致 26/35=0.743 | 可进九维辅助门 |
| full（低证据） | fact | 0 误杀但仅 6 单元、漏检不可测 | 辅助；涉事实判死仍 B3 |
| full（低证据） | init | 误杀 0/6 | 可进九维辅助门 |
| not-testable | attrib | 卡面无此维、负对照不定向（双序一致 0.457） | 维持 assistive-only（W04-03 口径） |
| **disqualified（机械判停用）** | boundary | 漏检 19/22=0.864 | **不得单独判死**；主因负对照器械效度不足（缺陷注入在提示侧，单位置素材不落到回复表层） |
| **disqualified（机械判停用）** | memory | 漏检 28/46=0.609 | 同上；多轮累积素材补建进 backlog |

- 全局 winner 双序一致率 31/35=0.886；同文 sanity 6/6=TIE（不虚构差异）。
- 历史真实批复读（12 位）：winner 复现仅 6/12=50%，**逐维分 ±1 一致率 0.972**（65/67）→ 逐维分可用作
  辅助读数，winner 单值判决噪声大、不可单独定案。

### 6.2 severe 旗：只作索引，三次成立记录

- W20-03 实测：**precision 0.062（1 TP / 15 FP）、recall 0.5**；15 个 FP 全在坏臂（把"模板化/未承接"
  升格记 severe，超出 rubric 五类严重定义）；好臂 severe 误杀 = 0。结论：severe=true 只能**触发 B3 亲读复核**，
  永不自动 veto。后续批复验：
  1. W21 快车道：judge 初判只抓到 parent 5 例 severe 中 2/5，B3 补烧 3（"severe 只作索引"结论再次成立）——
     `long_probe_20261030/W21_FASTLANE_G0_VERDICT_20261104.md` 诚实注记 2；
  2. W21FULL：judge 漏标 4 处由 B3 补（E191_T08 双臂窃归因、LRE121_T39 回请倒置、E164_T01 编单价、E191_T08 同犯）——
     `W21FULL_VERDICT_20261104.md` 机制读数 3；
  3. W22FULL REV2：judge 空注释 severe 6/11 亲读无缺陷；judge 旗与原文证据矛盾案例第 4 批复现——
     `W22FULL_VERDICT_REV2_20261104.md`。

### 6.3 引文逐字硬校验（已接线要求）

101 条有效 key_quote：逐字命中 88（87.1%）、硬幻觉 12（11.9%）、空引文 1；幻觉形态="真片段拼装改写"。
接线要求（报告 §4-1，工具已实现）：`tools/quote_hard_check.py` 在评审落盘后逐条校验 key_quote；
`severe=true` 且引文不过 → severe 记 `unverified`，强制 B3。W21FULL 起实际执行（"LP_W21FULL_QUOTE_CHECK.json
strict 76.2%，no_hit=0，severe_unverified=9 强制 B3"，`W21FULL_VERDICT_20261104.md` 头注）。

### 6.4 swap（顺序交换）翻转门

- swap 双序是 judge 稳定性的纪律（rubric §3"顺序交换"）。实际门禁效果：W19CROSS G5 故事级方向翻转 4/10、
  位级真翻转 4（E191_T08、LRE117_T08、LRE117_T24、LRE121_T20）→ **不得宣布单冠军**；
  W21 G5 3/10；W22 7/22（2 个全翻转恰为 cand severe 位）——三批均按冻结原文阻断单冠军宣布
  （三份判决文档 G5 行）。判定纪律：swap 样本量小（短域 1 位/篇、长域 4 位/篇）属探索性读数，
  "样本量异议不构成改判依据"。

---

## 7. C_SAMPLES.md 样本选取说明与类别覆盖

- **选取原则**：①每个能力族选"有判决书/亲读账/机械账三方证据"的代表位；②成功与失败并收（同一位置常呈
  双臂对照，一例同时展示失败模式与修复后行为）；③诚实收进 4 个已知题目瑕疵位（I 类）与 2 个评分器勘误位，
  不掩饰尺子缺陷；④全部摘录逐字取自判决书/B3 亲读账/评审 JSON，未从 logs 大文件重读原始 raw
  （个别位置引用评审 JSON 的 key_quote 字段）。
- **类别覆盖表**（样本序号见 C_SAMPLES.md）：

| 类别 | 样本 | 覆盖能力 |
|---|---|---|
| A 无据确定化 | 1(E061_T13)、2(E151_T09) | unknown 被当确定事实：双杀起源位 + assertion_tiering 有据化成功位 + CF 篇 confirmed severe |
| B 更正链/跨轮状态 | 3(E063_T08)、4(E064_T07)、5(E065_T15/T16)、6(E066_T09)、7(LRE117_T25)、8(E067_T12) | 更正后新口径使用、旧口径复活检测、安全件边界、superseded 状态机 |
| C 账目陷阱 | 9(LRE128_T35)、10(E164_T05)、11(LRE117_T04)、12(E164_T01) | HER 报错数/口径错/编数字：gold 不背书错算术、unknown 坐实判定 |
| D 人物归属 | 13(E191_T08)、14(LRE121_T39)、15(E191_T02) | 窃归因/方向倒置/评分器误判位：跨三批复现与锚定块修复 |
| E 长程逐字保持 | 16(E061_T24)、17(LRE117 T25-32 段) | 出窗探针召回、窗口保持率 1.0 vs 塌陷（LD1/FLJUDGE 双批数字） |
| F 反事实孪生 | 18(E149_T05)、19(E156_T01) | CF 敏感度（follow_rate 0.681/0.698、沿用率≤1.7%）、专名对照、措辞瑕疵登记 |
| G 正向互动能力 | 20(E032_T12)、21(E030_T10)、22(E030_T05 分支)、23(B1 九维 init) | 跃迁钩子承接、五类钩子分类学、深聊 0 档轨迹代价、init/interact 维冠军胜出 |
| H 边界/合法不回应 | 24(FIX_allow)、25(FIX_required)、26(AK01_T01) | may_decline 合法不回、expect_decline 静默、intent ACTIVE 锚点与 judge 校准好/坏臂 |
| I 时间/状态与题目瑕疵 | 27(E021_T07)、28(E029_T07)、29(E022_T12) | 季候/时钟勘误、时间视野跳变（未修，诚实展示）、收尾轮微注 |
| J 记忆使用 | 8(E067_T12 兼)、17 兼、§5 KPI 汇总 | Update 22/28 及时、B0 writeback ~100%、f_0009 superseded 降级 |

- **不能证明什么**：全部样本来自公开开发素材（audit.md 原声明 + anchors PROVENANCE `independence:
  同作者公开开发集，非独立封存`），不构成独立泛化证据；独立泛化归 W13-03 新领域批与封存 B 线
  （一次盲评，本审查未读）。样本中的"双臂"均为候选机制版本间的开发集对照，非原版产品对拍。
