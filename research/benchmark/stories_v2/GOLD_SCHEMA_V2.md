# GOLD_SIDECAR schema v2（research 域数据字典与 v1 差异对照）

> 目的：`GOLD_SCHEMA.md`（v1.0）描述的是 2026-11-03 时点 `GOLD_SIDECAR_v1` 产物
> （221 episodes / 3,102 turns / 6,603 atoms）的字段状态；现行 Gold 数据已重建为
> `GOLD_SIDECAR_v2`，字段结构与可判性状态与 v1 文档描述存在实质差异。本文件按
> 当前 `GOLD_SIDECAR/` 14 个文件的实际键结构程序化提取（2026-11-12 提取），
> 作为 v2 数据字典；**v1 原文按迁移纪律原样保留、一字未改**，两者并存互为版本历史。
> 消费方：`arena/gold_sidecar.py`（加载与映射）+ `tools/rescore_batch.py --sidecar-dir`；
> 判定语义仍以 `benchmark/rubric.md` §2 / §10 与 v1 文档 §3/§6 为准，本文件不改变语义。

## 0. 版本口径（三代口径表；底稿 = gold-dual-track-plan §0）

| 口径 | 实体位置 | 生成时间（GOLD_INDEX generated） | 字面 schema_version | totals（episodes/turns/atoms） | 文件数 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| ① v1.0 文档口径 | 仅存在于 `GOLD_SCHEMA.md` 文本 | 2026-11-03 前后 | "GOLD_SIDECAR_v1"（文档称谓） | 221 / 3,102 / 6,603 | —（无文件实体留存） | gold agent 首版产物；被 ② 取代，全仓与远端 HEAD 均无其文件 |
| ② 远端已提交快照 | 远端 git 历史 + 迁移树 `GOLD_SIDECAR_v1_remote_snapshot/`（13 文件） | 2026-11-08T06:00:43 | GOLD_SIDECAR_v2 | 259 / 3,590 / 7,953 | 13 | 远端最后一次提交的 Gold 构建波次（11-08 Benchmark V1 Phase 2 提交） |
| ③ 本地 v2 重建（权威） | `GOLD_SIDECAR/`（本目录） | 2026-11-10T04:25:15 | GOLD_SIDECAR_v2 | **299 / 4,082 / 9,646** | **14**（新增 GOLD_wave5_selection.json） | 11-10 账本补录 + Gold 重建批次产物，本地工作树现状 |

阅读纪律：

- **不要把 v1 文档的字段状态套用到 v2 数据上。** v1 文档 §0 写明"该产物目前全部 6,603 个
  原子均无 `affirm_markers`，unknowns 为纯字符串"——这只适用于口径①。v2 的 7,953 atoms
  （口径②）与 9,646 atoms（口径③）都不符合该描述，实际状态见 §4。
- 口径②与口径③的字面 `schema_version` 相同（GOLD_SIDECAR_v2），但 totals 与文件数不同，
  属两代 v2 实体；正式结果以口径③ `GOLD_SIDECAR/` 为准，口径②仅为版本历史存档。
- 迁移树中 `GOLD_SIDECAR_v1_remote_snapshot/` 目录名里的 "v1" 是迁移侧"快照轨 A"的轨位
  称呼，其内容 schema_version 同为 GOLD_SIDECAR_v2（口径②），不要把目录名误读为 schema v1。
  该快照目录与 `_remote_v1_snapshots/` 为迁移侧产物，随迁移树（research 工作区）存放；
  arena 工作树无这两个目录，arena 侧以本地权威轨 `GOLD_SIDECAR/` 为准。
- 加载器按传入的 sidecar 目录 `listdir` 消费 `GOLD_*.json`；快照目录不应作为 `sidecar_dir` 传入。

## 1. 文件布局（v2 实际）

`GOLD_SIDECAR/` 共 14 个 JSON 文件（12 个波次文件 + 2 个登记文件），全部 `schema_version=GOLD_SIDECAR_v2`：

| 文件 | 字节 | episodes | turns | atoms |
| --- | ---: | ---: | ---: | ---: |
| GOLD_stage1.json | 166,374 | 8 | 64 | 64 |
| GOLD_stage2.json | 420,589 | 16 | 192 | 115 |
| GOLD_stage3.json | 145,320 | 8 | 64 | 53 |
| GOLD_stage4.json | 442,582 | 8 | 192 | 123 |
| GOLD_stageD.json | 90,419 | 3 | 22 | 47 |
| GOLD_wave1.json | 683,386 | 16 | 192 | 535 |
| GOLD_wave1_ext.json | 258,409 | 2 | 72 | 225 |
| GOLD_wave2.json | 2,152,480 | 48 | 576 | 1,657 |
| GOLD_wave3.json | 3,748,112 | 96 | 1,152 | 2,552 |
| GOLD_wave3_ext.json | 1,877,755 | 16 | 576 | 1,232 |
| GOLD_wave4_dev.json | 1,999,141 | 38 | 488 | 1,350 |
| GOLD_wave5_selection.json | 2,592,982 | 40 | 492 | 1,693 |
| GOLD_INDEX.json | 10,836 | — | — | — |
| GOLD_QC_SAMPLE.json | 5,799 | — | — | — |
| 合计 | | **299** | **4,082** | **9,646** |

- **波次文件顶层键**：`schema_version` / `wave` / `generated` / `stats` / `per_episode` / `turns`。
  `stats` 与 `GOLD_INDEX.per_wave` 同构（逐波次 totals）；`per_episode` 为
  `{episode_id: {turns, with_assertions, empty_gold}}` 摘要。
- **`turns` 为轮级扁平列表**（每项 = 一轮，12 波合计 4,082 项）。注意：v1 文档 §1 布局 4
  把波次文件描述为"turns 列表每项含 case_id/episode_id/…/qc"的 episode 级条目再嵌
  turns——v2 实际是**每项即一轮**，没有中间嵌套层；加载器按轮项 `case_id` 建索引的规则不变。
- **GOLD_INDEX.json 顶层键**：`schema_version` / `generated` / `tool`
  （=tools/build_gold_sidecar.py） / `source_policy` / `enrichment_policy` / `marker_qc` /
  `totals` / `per_wave` / `qc_sample` / `files_sha256`（12 个波次文件的登记字段）。
- **GOLD_QC_SAMPLE.json**：`{seed: 20261101, rows: [{case_id, wave, strict_all_pass,
  lenient_all_pass, failed_atoms}]}` 抽样回验记录；`GOLD_INDEX.qc_sample` 摘要为
  `{seed: 20261101, n: 40, strict_all_pass: 40, lenient_all_pass: 40}`。
- `case_id` 必须与 episode `turns[i].case_id`（如 `E157_T03`）完全一致（v1 §1 规则沿用）。

## 2. turn 级字段（v2 实际键集；4,082 轮逐键统计）

```json
{
  "schema_version": "GOLD_SIDECAR_v2",
  "case_id": "E013_T01",
  "wave": "stage1",
  "episode_id": "E013",
  "turn_index": 0,
  "turn_type": "reply",
  "gold_empty": false,
  "assertions": [ ...原子断言... ],
  "intent_contract": { ... },
  "forbidden": { "forbidden_factual_claims": [...], "unknowns": [...] },
  "protected_expressions": [...],
  "card_extras": { "current_action": "...", "private_guidance": "...", "applicable_dimensions": [...] },
  "scorer_gold": {},
  "provenance": { ... },
  "qc": { ... },
  "unknowns": [ ... ],
  "enrichment": { ... }
}
```

逐键事实（4,082 轮全覆盖字段除注明外）：

| 键 | 覆盖 | 说明 |
| --- | --- | --- |
| `schema_version` | 4,082 | 恒 `GOLD_SIDECAR_v2` |
| `case_id` / `episode_id` / `wave` / `turn_index` | 4,082 | 定位四元组 |
| `turn_type` | 4,082 | 观测值恒 `reply` |
| `gold_empty` | 4,082 | true 79 轮（诚实登记口径，评分侧只跑契约层）；false 4,003 轮 |
| `assertions` | 4,082 | 原子断言数组（空数组表示本轮无原子），见 §3 |
| `intent_contract` | 4,082 | 见 §4 |
| `forbidden` | 4,082 | 形态见 §4 |
| `protected_expressions` | 4,082 | 数组 2,945 轮（857 轮空数组）+ null 280 轮 |
| `card_extras` | 4,082 | 恒为对象 `{current_action, private_guidance, applicable_dimensions}` |
| `scorer_gold` | 4,082 | 观测值恒为空对象 `{}` |
| `provenance` / `qc` | 4,082 | 见 §5 |
| `unknowns` | 2,581 轮携带 | 顶层不确定信息数组，共 3,116 项，见 §4 |
| `enrichment` | 4,082 | v2 重建登记字段，见 §5 |

**与 v1 文档 §2 的差异**：v1 文档列出的 `stale_fact_must_not` /
`geo_impossible_must_not` / `expect_decline` 三个顶层键在 v2 文件中**未出现**；
decline 相关口径由 `intent_contract.may_decline` 登记（`provenance.may_decline_conflict_with_card`
记录与评分卡的冲突）；`unknowns` 从 v1 的纯字符串数组变为混合数组（见 §4）；
v1 文档列出的 `unknowns`/`stale_fact_must_not` 等键位与 v2 实际键位不同，消费方以本节键集为准。

## 3. `assertions`（原子断言；9,646 个）

字段键集（9,646 原子逐键统计）：

| 键 | 覆盖 | 说明 |
| --- | --- | --- |
| `atom_id` | 9,646 | 形如 `N001_F01`；v1 文档写作 `id`，v2 实际键名为 `atom_id` |
| `subject` / `relation` / `value` / `valid_time` | 9,646 | 证据与登记字段 |
| `status` | 9,646 | 词表见下 |
| `negation` | 9,646 | true = 该原子当前不成立 |
| `source` | 9,646 | 对象 `{kind, case_id, message_id, quote, key}`：`quote` 9,646 全覆盖；`message_id` 字符串 9,350 / null 296；`key` 307 |
| `required_this_turn` | 9,646 | 布尔登记 |
| `affirm_markers` | 2,359 非空 | 词组数组（组内 AND、跨组 OR），见 §4 |
| `item_markers` | 128 非空 | status 分布：cancelled 78 / superseded 47 / erroneous 2 / canceled 1 |
| `cross_origin` / `cross_story` / `cross_case_ref` | 9 / 8 / 8 | 跨卡/跨篇引用登记 |
| `quote` | 1 | 个别原子的直引登记 |

**`status` 实际词表：46 个不同取值**（高频：asserted 7,706 / confirmed 683 /
superseded 294 / uncertain 195 / tentative 136 / cancelled 123 / suspended 108 /
corrected 77 / retracted 63 / resolved 39 / invalidated 23 / negated 11 / revoked 7 /
erroneous 7；其余为低频状态，完整分布可由文件统计复算）。v1 文档 §3 只列 5 个规范值并注明
"实际词表更细、加载器按取消族映射取交集"——该消费规则在 v2 下不变：取消族
（`cancelled / canceled / superseded / retracted / voided / revoked / invalidated /
negated / erroneous`）或 `negation: true` = must-not-affirm；其余状态只作证据登记。

9,646 原子全部通过 quote 逐字回验（`GOLD_INDEX.totals`：quotes_total / quote_strict_hits /
quote_lenient_hits = 9,646/9,646/9,646；`marker_qc.verbatim_violations = 0`）。

## 4. 判定信号与可判性状态（v2 实测；对照 v1 文档 §0/§8 的请求项）

v1 文档 §8 曾"请求 gold 侧补齐四类字段"。v2 重建按 `enrichment_policy` 落实了其中三类，
实际可判性状态如下：

| 项 | v1 文档口径（①） | v2 实际（③） | 诚实登记口径 |
| --- | --- | --- | --- |
| must-not-affirm 原子 | 全部 6,603 原子无 markers、不判分 | 832 个（`must_not_affirm_atoms`），其中 686 个带 `affirm_markers` 可判分 | 146 个无确定性信号，继续 unrated 不猜语义（`atoms_affirm_no_signal=146`） |
| `affirm_markers` 非空总量 | 0 | 2,359 原子 | 除 must-not-affirm 687 个外，其余 1,672 个为证据侧原子（asserted/confirmed/tentative 等，源自评分卡合并）；消费方仍按 v1 §3 规则只在 must-not-affirm 方向消费 markers，证据侧不触发判分、不强迫提及 |
| `item_markers` 非空 | 0 | 128 原子（v2 重建新增 68，其余来自评分卡） | 复用 rubric C8 复活判分 |
| `unknowns` | 纯字符串 | 3,116 项 = 字符串 2,649 + 对象 467（`{topic, affirm_markers}` 189 = v2 升级；`{topic, affirm_markers, note, markers}` 278 = 评分卡源） | 2,649 个字符串继续诚实 unrated；升级项的原字符串并存于 `forbidden.unknowns`（原样保留），顶层 `unknowns` 存升级后形态 |
| `intent_contract` | 仅 input.intent 非空轮生效；keywords 缺失诚实登记 | 4,082 轮全部携带五基键（`intent/intent_source/may_decline/may_decline_source/acceptable_actions`）；`keywords_any`/`must_not_keywords` 出现在 358 轮，355 轮 keywords 非空（共 1,616 组） | 3 个意图轮 keywords 仍空；`must_not_keywords` 全部为空（无确定性来源一律留空不造禁区词，C10 禁区检查未实例化） |
| `forbidden` | `{must_not_keywords, forbidden_new_facts}` → C3/C6 | `{forbidden_factual_claims ×6,200, unknowns ×3,116}` | v1 §5 的两个判分键在 v2 文件中**不存在**；`forbidden_factual_claims` 为自然语言禁令（层 1 不做子串判定，语义归层 2），若需 C3/C6 判分须另建映射，未建立前维持 unrated |

`GOLD_INDEX.enrichment_policy`（原文摘录）记录了 v2 新增信号的生成纪律：取消族原子加
`affirm_markers`（单组 AND，短语=最长逐字公共子串，需语料出现更正/取消线索词，≥4 字才实例化，
提及≠坐实、宁 unrated 不误判）；`status=cancelled/canceled` 事项原子加 `item_markers`；
unknown 字符串升级为 `{topic, affirm_markers}` 并存；`intent_contract` 加 `keywords_any`
（≥2 字逐字公共子串，≤6 组）；全部 markers 经逐字回验 QC（`verbatim_violations` 必须为 0）。

## 5. v2 新增登记字段（`provenance` / `qc` / `enrichment`）

- **`provenance`**（4,082 轮全覆盖）：`card_found` / `card_sources`（`[{file, sha256}]`
  登记字段） / `card_conflicts`（全仓 93 条，全部在 wave3_ext，`source_conflicts=93`） /
  `may_decline_conflict_with_card` / `episode_file` / `episode_sha256`（登记字段）。
  `source_policy` 原文记录卡优先级：跨 wave 汇编修订卡（仅 wave3_ext）→ 本 wave
  GRADING_CARDS*.json → episode reference.json.turns；多源一致取最高优先级并登记全部来源。
- **`qc`**：`{n_quotes, strict_hits, lenient_hits, failures}` 逐轮 quote 回验记录。
- **`enrichment`**（逐轮登记 v2 重建新增量）：`markers_added`（合计 675）/
  `markers_skipped_no_signal`（157）/ `item_markers_added`（68）/ `unknowns_upgraded`（189）/
  `unknowns_string_kept`（2,649）/ `intent_keywords_groups`（1,616）/
  `marker_provenance`（`{atom_id: {cue_words_found, marker_source, phrases}}`）。
  观测计数与登记口径的差异（如非空 `item_markers` 128 vs 新增 68）= 评分卡源数据携带的部分，
  两套数字各自如实登记，不做人为对齐。

## 6. 版本历史与 v1 链接

- **v1**：`GOLD_SCHEMA.md`（本目录，schema v1.0，2026-11-03，口径① 221/3,102/6,603）。
  按迁移纪律原样保留、一字未改；其字段状态描述（全部原子无 `affirm_markers`、unknowns
  纯字符串、`forbidden` 含 `must_not_keywords`/`forbidden_new_facts`）**仅适用于口径①**，
  不适用于 v2 数据。消费语义（§3 判定方向、§6 自由度与匹配纪律、§7 用量单位口径）仍以
  v1 文档为权威文本。
- **v2 数据两代实体**：口径②（远端已提交，2026-11-08，259/3,590/7,953，13 文件）存于迁移树
  `GOLD_SIDECAR_v1_remote_snapshot/`；口径③（本地权威重建，2026-11-10，299/4,082/9,646，
  14 文件）即本目录 `GOLD_SIDECAR/`。来源与恢复过程登记见迁移 provenance
  （MIGRATION_MANIFEST / SNAPSHOT_README）。
- **本文件**：GOLD_SCHEMA_V2.md v2.0，2026-11-12 编写（第 4 份指导书 S4）。全部键结构与
  计数由 `GOLD_SIDECAR/` 14 文件程序化提取实测；后续 Gold 修订应新增版本文件，不在本文件
  与 v1 文件上就地改写。
