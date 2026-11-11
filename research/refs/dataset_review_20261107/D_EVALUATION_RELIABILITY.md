# D — 评估可靠性审查（2026-11-07）

> 审查范围：LoveBrain Arena 评估体系六要素——评分器（层1）、judge（层2）、人工审核（B3）、
> 盲评与曝光管理、版本绑定与复现、以及用户点名风险逐项核对。
> 审查方式：只读核证，每个结论附证据文件路径；优点与缺口并重。
> 本文件只写本目录；`benchmark/b_line/` 仅读取 `B_LINE_MANIFEST.json` 与 `B_LINE_README.md` 元数据（封存纪律，未读题目内容）。
> 审查基准日：2026-11-07。全局状态出处：`D:/lovebrain-arena/refs/experiment_protocol_20261023/global_harness_program_20261030/RESEARCH_STATE.json`。

---

## 1. 评分器（层 1 程序）：版本演进、已知失效史与现状

### 1.1 版本演进（`arena/lb_scorer.py` 文件头版本志）

现行版本 **v0.9.3（2026-11-03）**。演进主线：v0.2 意图契约 → v0.3 旧事重提 C11 → v0.4 蜂刺加固（C12 物理禁区/C13 风格趋同）→ v0.5 may_decline/关键词分级 → v0.7 no_reply 绕过修复（P0）→ v0.8 类型安全/矛盾动作 → v0.9 嵌套结构校验 → v0.9.2 null 契约统一 → **v0.9.3 GOLD_SIDECAR 接入（C14 assertion_* / C15 unknown_*，仅当 gold 键存在时实例化；gold={} 的老批次行为与 v0.9.2 逐字节一致）**。
出处：`arena/lb_scorer.py`（文件头版本注释，L1–L48）；gold 映射适配 `arena/gold_sidecar.py`；回归测试 `tools/test_gold_sidecar_scoring.py`；接入收口记录 `RESEARCH_STATE.json` updates[55]（2026-11-03T21:30："lb_scorer v0.9.3 接 gold（C14/C15，回归逐字一致）"、fact_gold sidecar 2919/3102 位断言实例化）。

**优点**：每次改动有版本号、动机与影响面注记；v0.9.3 明确"老批次 gold={} 行为逐字节一致"，不重写历史读数；评分公式与 veto 边界写在文件头，权重对候选不可见（候选运行包只携带 `arena/protocol_public.py`，见 `arena/lb_scorer.py` L52–L66 迁移注记与 `tools/test_protocol_public.py` 等价证明）。

### 1.2 已知失效史（全部已修复并留痕）

| 失效 | 内容 | 修复与重评 | 证据 |
|---|---|---|---|
| **v0.9.1 null 判零被推翻** | directions[i]=null 曾被判零（A1-t5 得 8.75→0 的改判），独立复核发现生成侧 format.md/HARNESS_IO_SPEC 明确允许 null 不凑满 | 撤销 v0.9.1，契约统一 string\|null，历史输出按当时契约重算，v0.9.1 重评结论作废 | `arena/lb_scorer.py` v0.9.2 注记 |
| **FK-SCORE-01（m_src 合法集）** | 评分器把模型引用 prompt 内真实存在的历史标签 `m_src[i] (i<prior_real_n)` 误判为 illegal_source_id | 2026-11-02 修复；离线重评 3 例历史误 veto 全清（E061_T02/T04、LRE060_T03：0.0→10.0）；FLREG/FLREG2/FLJUDGE 三份判决附记勘误（结论均不变，淘汰依据为语义门） | `refs/experiment_protocol_20261023/long_probe_20261030/SCORER_MSRC_REEVAL_20261102.json`；`FLREG_VERDICT_20261101.md`/`FLREG2_VERDICT_20261101.md`/`FLJUDGE_VERDICT_20261102.md` 勘误段；`RESEARCH_STATE.json` updates[40] |
| **W19-01（隔离路径同族复现）** | FK-SCORE-01 在隔离计分路径 `trusted_controller.step_one_turn` 复现（未继承 m_src 合法集），影响 W17b/W17c/W18B 三批 | 2026-11-03 修复 + 单测 4/4；`tools/rescore_batch.py` 离线重评 608 位：W17c 候选 9.306→9.583（2 误 veto 清除、零分 5→3）、W17b 8.958→9.236（清 2）、W18B 320/320 逐位不变；红队 13 个 illegal_source_id 位逐一独立重算（4 清 10 留，留者均真实非法）全对 | `W17C_MERGE_VERDICT_20261103.md` Rev3 附记；`REDACT_TEAM_W19_01_CHECK_20261103.md`（五项全 PASS）；`arena/isolation/trusted_controller.py` L62-77、`tools/test_fk_score01_isolation.py` |
| **W04-02 severe 臂映射反转** | 三个评审脚本把 judge severe 的 L/R 归一到 cand/par 臂时映射写反；dims/winner 归一正确、仅 severe 写反 | 2026-10-30 修复 3 个脚本；4 份评审 JSON 原位修正（buggy 值保留在 `severe_reported_buggy` 字段可审计）；第一轮判决由预注册执行器重跑（E3 KEEP→ELIMINATE、Bmin REWORK→ELIMINATE）；后续第四轮以全量跨批核算改判 E3 复职（见 §6.2 与 E 文件） | `global_harness_program_20261030/W04-02_ERRATA_SEVERE_SWAP_20261030.md`；`W04-03_FIRST_LOOP_VERDICT.json`（重跑版）；`W04-04_FS1_FULL_SCORECARD.md` 修正补记；`RESEARCH_STATE.json` updates[10] |

**残余缺口**：① E164_T04 `m2` 类候选真实 source-id 格式错误未在守卫层清洗（W17C 判决登记待 W19-02 处理，出处同 Rev3 附记）；② 早期批次 gold={} 时代程序分只覆盖已实例化检查点，覆盖度弱（AGENTS v1.4 ⑤ 已明文禁止据此反向宣布"程序分与语义无关"）；③ 评分器版本在多数判决书正文未逐批记录版本号，只能按文件头版本志+时间线归位（本审查已按时间线归位，见 E 文件各批"评分器与判据版本"列）。

---

## 2. Judge（层 2 语义评审）：资格、severe 用法、引文硬校验、swap 门

### 2.1 九维逐维资格表（W20-03 锚点校准，跑前冻结判据）

校准批：102 次 judge 判定 + 36 次负对照生成（授权 AUTH-W20-JUDGE-CAL-20261103-01 cap160 实用 114 / AUTH-W20-NEGGEN-20261103-01 cap40 实用 36，与 calls.json 对账）。
出处：`refs/experiment_protocol_20261023/long_probe_20261030/W20_03_JUDGE_CAL_REPORT_20261103.md`（含设计/原始输出/指标三个伴生文件）。

| 维 | 资格结论 | 关键读数 | 操作地位 |
|---|---|---|---|
| goal | full-qualified | 误杀 0/58，双序一致 0.829 | 可进九维辅助门 |
| time | full-qualified | 误杀 2/46=0.043，双序一致 0.714 | 辅助门 |
| interact | full-qualified | 误杀 0/64，坏臂检出 24/24（客服腔） | 辅助门 |
| emotion | full-qualified | 误杀 0/42 | 辅助门 |
| fact | full-qualified（低证据，仅 6 单元） | 漏检不可测 | 辅助；涉事实判死仍 B3 |
| init | full-qualified（低证据） | — | 辅助门 |
| attrib | not-testable | 卡面无此维、负对照不定向 | 维持 assistive-only |
| boundary | 机械判 disqualified | 漏检 19/22=0.864 | **不得单独判死**；负对照器械效度不足（缺陷注入在提示侧不落回复表层） |
| memory | 机械判 disqualified | 漏检 28/46=0.609 | **不得单独判死**；素材补建进 backlog |

**优点**：资格判定有冻结阈值（误杀≤10% 且漏检≤20% 且双序一致≥80%）、有 GT 标签与原始输出可复核；"未证明合格"与"证明不合格"分开表述（boundary/memory 是测试面不足非 judge 一定错）。
**缺口**：severe GT 阳性仅 2/48；锚点为单臂素材（构造校准对，未做单臂直评）；memory/boundary 判别素材补建仍在 backlog（报告 §4.5）。

### 2.2 severe 旗只作索引，永不单独判死

- 校准实测 **severe precision 0.062（1 TP/15 FP）、recall 0.5**；15 个 FP 全部是坏臂侧把"模板化/未承接"升格为 severe；好臂误杀=0 → 用法只能是"severe=true → 强制 B3 亲读复核"。出处：`W20_03_JUDGE_CAL_REPORT_20261103.md` §2.2/§3。
- **W19CROSS 亲读 26 位对账**（账本文件逐位记录 33 条，含 B3 补烧位）：judge 缺陷四类——**误烧 4 位**（E164_T10、LRE121_T33、LRE146_T26、LRE146_T27，其中 3 位为"未查前文/谎称未出现"型，原文逐字有据：`那单我不下了`/`11月14日`+`封网演练`/`全出`+`钙片`/`撇嘴…等着瞧`）、**漏检 7 位**（含 cand 5 个 0 字节空回复未烧）、**无注 severe 8 位**（PARSE_OK 位不可复现）、**双重标准 3 处**（同类内容只烧 cand 臂）。出处：`LP_W19CROSS_B3_READRESULT.json`（judge_defects_registered 字段）；判决书 `W19CROSS_VERDICT_20261103.md` §关键事实 1。
- **W21FULL judge 初判 2/5 漏 3**：快车道批 par 5 例 severe 中 judge 只抓到 2，B3 补烧 3（全部题面逐字反证）。出处：`W21_FASTLANE_G0_VERDICT_20261104.md` 诚实注记 2；`RESEARCH_STATE.json` updates[68]。
- 同类证据链：FLJUDGE B3 亲读 3/3 全部 rejected（judge 误报+引文幻觉，实际双臂 severe 0/0，出处 `FLJUDGE_VERDICT_20261102.md` §6/§7 与 `LP_FLJUDGE_B3_READRESULT.json`）；LD1 severe 初判 cand 6/par 2 经亲读修正为 1/1（出处 `LD1_VERDICT_20261102.md` §4）；W22FULL 11 例 par severe 中 6 个 judge note 为空（出处 `W22FULL_VERDICT_20261104.md` G1 节）。

**优点**：severe 降级为复核触发器的纪律在 rubric §3 与 W20-03 数据两侧都有依据，且在 FLJUDGE→W19CROSS→W21/W22 四批复现的缺陷形态上持续得到验证；B3 亲读多次实质推翻 judge（见 §3.2）。
**缺口**：judge 不可靠在四个批次重复出现说明评审基建本身有系统性弱点（单裁判、单序、无自检），当前靠"亲读兜底"而非"judge 变好"。

### 2.3 引文逐字硬校验（确定性工具，已接线为管线必选项）

- 工具：`tools/quote_hard_check.py`（strict=整句逐字子串命中 / loose=空白归一；severe=true 且 strict 未命中 → `severe_unverified` 强制 B3，不改写原 verdict）。接线：`run_lp_review_generic.py` 落盘后自动跑（`RESEARCH_STATE.json` updates[58]）。
- 校准批实测：101 条有效 key_quote 逐字命中 88（87.1%），**硬幻觉 12 条（11.9%）**，幻觉形态="真片段拼装改写"（`W20_03_JUDGE_CAL_REPORT_20261103.md` §2.4）——这就是接线动机。
- 各批复检读数（`LP_<TAG>_QUOTE_CHECK.json`，同目录）：

| 批 | 引文 n | strict 命中 | strict 率 | note_like | fragments_all_hit | fragments_miss | no_hit | severe_unverified |
|---|---|---|---|---|---|---|---|---|
| W19CROSS | 221 | 166 | 75.1% | 9 | 24 | 22 | **0** | 14（强制 B3） |
| W21FULL | 223 | 170 | 76.2% | 6 | 27 | 20 | **0** | 9 |
| W22FULL | 225 | 178 | 79.1% | 8 | 15 | 24 | **0** | 7 |
| W21 快车道 | 50 | 42 | 84% | 1 | 3 | 4 | **0** | 1 |
| W22 快车道 | 52 | 44 | 84.6% | 0 | 6 | 2 | **0** | 0 |

**优点**：no_hit=0 全批成立（无完全脱空编造引文）；severe_unverified→B3 的强制链真实生效（W19CROSS 14 例与亲读账一致，出处 `RESEARCH_STATE.json` updates[58]；快车道首战 T23 cand 空注 severe 被双通道拦截，出处 `W21_FASTLANE_G0_VERDICT_20261104.md` 诚实注记 3）；工具零模型调用、失败不阻断评审。
**缺口**：strict 率仅 75–85%，fragments_miss（片段拼装改写）长期存在 15–24 条/批——引文作为"证据"只能当线索，不能当判定；该残余与 judge 单裁判弱点同根。

### 2.4 swap 翻转门（评审顺序敏感性作为单冠军阻断条件）

双序（main/swap）评审在近平局批的翻转被用作"不得宣布单冠军"的冻结门：
- **W19CROSS**：故事级方向翻转 4/10 + 位级真翻转 4 → 不得宣布单冠军（`W19CROSS_VERDICT_20261103.md` G5 行）。
- **W21FULL**：G1–G4 全过，G5 翻转 **3/10** → 保留双域冠军、uc_retry 归档为候选（`W21FULL_VERDICT_20261104.md`）。
- **W22FULL**：G5 翻转 **7/22**（5/10 故事有翻转，2 个全翻转恰是 cand 的 2 个 severe 位）→ DUAL_CHAMPIONS_RETAINED（`W22FULL_VERDICT_20261104.md` G5 节）。
- 历史登记：D1 swap 5/8（3 分歧全在近平位）、F3 4/8、FLJUDGE 5/8、W15N 3/8、W17C 3/6——近平局不稳定是跨批重复现象（各判决书 swap 行）。
- W20-03 复读实验补充：逐维分数 ±1 一致率 0.972（读数稳），winner 单值复现 6/12=50%（单值噪声大）→ 门禁读数用逐维分、winner 只作展示（`W20_03_JUDGE_CAL_REPORT_20261103.md` §2.5/§4.3）。

**优点**：swap 门多次真实阻断了"以单序 judge 读数宣布总冠军"（W19CROSS/W21/W22 三次），是防"评审噪声变成晋级结论"的有效机制，且全部按跑前冻结原文执行、未因样本量异议改判。
**缺口**：swap 样本量小（短域 1 位/篇、长域 4 位/篇），翻转统计本身是低功效检验——它只能阻断，不能证明"候选真的更差"（W22FULL 注记 swap 22 位为探索性读数）。

---

## 3. 人工审核（B3 亲读）：制度、账本与对 judge 的实质纠偏

### 3.1 制度与账本

- **b3_queue 自动产出**：W16-05 v3 关单交付 `tools/b3_queue.py` 并验证接线（P1 审计 7/7 全为松散引文、零幻觉），评审完成后自动生成亲读队列（`RESEARCH_STATE.json` updates[13][14]）。
- **亲读账清单**（`refs/experiment_protocol_20261023/long_probe_20261030/`）：`LP_FLJUDGE_B3_READRESULT.json`、`LP_CF1_B3_READRESULT.json`、`LP_LD1_B3_READRESULT.json`、`LP_W15N_B3_READRESULT.json`（W15N 判决引用）、`LP_W17_B3_READRESULT.json`、`LP_W17C_B3_READRESULT.json`、`LP_W18B_B3_READRESULT.json`、`LP_W19CROSS_B3_READRESULT.json`、`LP_W21FULL_B3_QUEUE.json`（W21FULL 判决书载明全量对账附卷"待补"，亲读对账暂以判决书 §逐门/机制读数为准）、`LP_W22FULL_B3_QUEUE.json`（亲读对账见判决书 G1 节）、`LP_W18_B3_READRESULT_PARTIAL.json`（W18 事故批部分数据，标注 partial 不进主判）等。
- **亲读方法**（`LP_W19CROSS_B3_READRESULT.json` method 字段）：severe 位逐位读 HER 原文（episode turns）+ 双臂 reply_raw.txt + 评审引文逐字回验；机械位（乱码/空）以 reply_raw + mechanism_notes + scoring.json 直接裁定。

### 3.2 亲读驳回 judge 的实质案例（抽样）

- FLJUDGE：3/3 severe 全部 rejected（parent"编造"两例均逐字有据且处反事实推演内；cand 四条细节逐条有源属接梗复述）→ 实际双臂 severe 0/0，KEEP 判决方向不变且更强（`FLJUDGE_VERDICT_20261102.md` §7.1）。
- W19CROSS：误烧 4 位全部驳回（如 LRE121_T33：par"那个九点半是11月14号封网演练取消的"两句均有原文根据）；另发现 judge 漏检 7 位（含 5 个 0 字节空回复），severe 账以亲读为准重写（`LP_W19CROSS_B3_READRESULT.json`）。
- W18B：judge 初判 cand 2 位亲读全部降级（引文幻觉/双重标准），主分析集确认 severe cand 0 vs par 2（`W18B_VERDICT_20261103.md` G1 行）。
- W22 快车道：cand T34 由 judge 漏标、B3 补烧确认（`W22_FASTLANE_G0_VERDICT_20261104.md` B3 亲读要点）——亲读既驳回误报也补真实漏报，方向双向。

**优点**：亲读是全场唯一能同时抓"误烧"与"漏检"的通道，多次改变 severe 账方向且改变过程留痕（erratum 附记回填判决书与 KNOWN_FAILURES）。
**缺口（结构性，须如实声明）**：B3 执行人是**主会话（AI worker）本人**，不是人类用户、也不是独立第三方会话；"亲读"防的是 judge 单点幻觉与引文造假，防不了"同一会话系统性偏见"。用户愿发偏好至今 UNKNOWN（`W04-04_FS1_FULL_SCORECARD.md` W 愿发列 = UNKNOWN，12 对人工样本待用户、AI 不代投）。另有抽样盲区先例：FS1 B3 亲读档未抽到 E026_T05（prescreen 未旗标，事后由 errata 复核捕获，出处 `W04-02_ERRATA_SEVERE_SWAP_20261030.md` §7）。

---

## 4. 盲评与曝光管理：custody 账本与 B 线封存

### 4.1 故事保管账本（SEALED→EXPOSED 不可逆）

- 账本：`refs/experiment_protocol_20261023/global_harness_program_20261030/STORY_CUSTODY_LEDGER.json`——登记 210 篇故事（含 episode sha256 双哈希）+ 173 条事件流；消费即 EXPOSED，事件只追加；协议文档 `W03-05_CUSTODY_PROTOCOL_20261031.md`；工具 `tools/story_custody.py`。
- 各判决批均在判决书登记消费（如 FLJUDGE："LRE060/LRE048 追加 BATCH-FLJUDGE 消费记录（EXPOSED 不变，72 events）"，`FLJUDGE_VERDICT_20261102.md`；LD1 消费四篇首用 SEALED→EXPOSED，`LD1_VERDICT_20261102.md` 批头）。
- 生成隔离有显式确认机制：隔离生成会话记账本事件 `generation_isolation_ack`（updates[36]），W03-06 扩库全程以隔离代理分片生成（updates[41][45][48]）。

### 4.2 B 线（封存盲测集）与 b_line_v1→v2 重建事件（真实未遂泄漏，如实写）

- 现行 **b_line_v2**：`benchmark/b_line/B_LINE_MANIFEST.json`——24 篇（16 标准 + 4 长剧 LRE119/123/139/142 + 4 反事实 E150/152/153/155），每篇 source_sha256=copy_sha256 三重对账，`integrity.verified_at = 2026-11-03T21:51:25`；`only_once` 条款明写"仅允许封存冠军验收运行一次，运行后转 CONSUMED，禁止按逐题错误修补"。
- **真实未遂泄漏事件（本审查确认为已发生并已处置）**：b_line_v1（2026-11-02 离线封存）的选篇含**账本漏记的实际已暴露篇**（E117/E164/E191/LRE121/LRE146/LRE127 曾被 W13-03/W17c/W18B/W19B/W14J 消费）；2026-11-03 复核发现后补录 54 条 exposure 事件、从干净池重选 24 篇、v2 原位替换 v1（v1 未被消耗过，无数据损失）。出处：`B_LINE_MANIFEST.json` version/supersedes 字段；账本事件流 `b_line_sealed`（B-LINE-SEAL-20261102）与 `b_line_resealed`（B-LINE-SEAL-V2-20261103，note 全文记载污染成因与处置）；`RESEARCH_STATE.json` updates[55]（"B线盲测集 v2（24 篇全净，账本补录 54 条 exposure）"）。
- 封存纪律文本：`benchmark/b_line/B_LINE_README.md`（ONLY_ONCE 最高条款、开箱前置、开发迭代禁止读取条款）。

**优点**：这是"曝光管理机制真实工作"的正面证据——账本漏记被检查流程抓住，处置方式是补账+重建封存集+原样登记（不掩盖），且 manifest 的 supersedes 字段保留了完整的污染审计链。gold/reference sidecar 不进 b_line 目录（README §6），防 gold 泄露面。
**缺口**：① 账本漏记本身说明"消费记账靠人工/工具自觉"，当时无强制对账门（现由 W20-04 基线修复补录，但缺自动化闭环证明）；② b_line_v2 至今未消费——全场尚无一次真正盲测验收；③ 隔离会话"未读开发侧材料"靠事件流声明自证，无独立见证。

---

## 5. 版本绑定与复现：plan_sha256、capture 回放、prompt 等价、同批同条件、预算账本

### 5.1 plan_sha256 现场重算与 frozen_bindings

- 每批三件套（BATCH_CONFIG / INTERLEAVE_PLAN / AUTHORIZATION）落盘后 precheck G1–G7 含"plan_sha256 现场重算一致"（如 W22FULL：plan sha bd8e3d27…，`RESEARCH_STATE.json` updates[79]；W21FULL：d41f23990a99a7ac，updates[69]；FLJUDGE：7d1226e9…现场重算一致，`FLJUDGE_VERDICT_20261102.md` 批头）。
- frozen_bindings 冻结双臂身份 sha256；红队独立复核 W19CROSS 批：parent 43 文件/candidate 55 文件闭包与磁盘 sha256 0 mismatch、identity_sha256 用 `api_trial.harness_identity` 重算逐字吻合、232 卡 case_id 与既有卡集并集精确相等（`REDACT_TEAM_W19_01_CHECK_20261103.md` §4）。
- 判据跑前冻结：各批设计文件（如 `W22_01_ATTR_DESIGN_20261104.md`、`W17C_PREREGISTRATION_20261103.md`、`W04-03_VERDICT_THRESHOLDS_PREREG.json`）先于 live 落盘；W22 快车道授权单构造四轮 schema 修正如实入档（updates[64]）。

### 5.2 capture 回放与 prompt 字节等价

- 全量批 capture 回放 **464/464**（W21FULL updates[69]；W22FULL updates[79]）；快车道批 mock 104/104 order=True 零真实调用（updates[63]）。
- prompt 字节等价实证：W21FULL **821,643=821,643（+0.00%）**（`W21FULL_VERDICT_20261104.md` G4 行）；快车道 185,527=185,527（`W21_FASTLANE_G0_VERDICT_20261104.md`）；W22 候选无触发轮 prompt 与父本逐字节相同（L0-1 硬门，updates[77]；`W22FULL_VERDICT_20261104.md` G4）。重试另册 dispatch 亦有"重建 prompt 与基批逐字节一致（prompt_match=True）"走查（updates[62][71]）。

### 5.3 同批同条件纪律与 W18 并发事故

- 固定条件：端点 `http://127.0.0.1:17770/v1`、model 别名 dsf、双臂同批同采样（授权 scope 逐批登记，如 `logs/auth_budget/AUTH-W22-FULL-20261104-01.jsonl` 首行 scope.endpoint/model_alias）；episode_order 双臂 parent_first/candidate_first 严格交替（`W22_FULL.BATCH_CONFIG.json`；红队 §4 核对 E164 candidate 在前）。
- **W18 执行事故（如实入账，未粉饰）**：`BATCH-W18-UCSUPP-20261103` 因双 live 批 + 三篇评审并行（约 9 路，dsf 饱和约 6 路）→ p95 ~100s → 12:24:48 网关 120s 超时 → fail_fast 中止于 90/320；处置=不续跑不重试（授权 no_retry/no_makeup/fail_fast）、原样保留证据、部分数据只作实现核对不进主判、重跑 W18B 独占 6 workers 且评审后置；流程修正"live 批独占模型容量"固化为后续批纪律（W17c p95 120s 注记、W19CROSS "未占 6 流"、W21/W22 全量 6 流独占）。出处：`W18_EXECUTION_INCIDENT_20261103.md`（时间线/根因/处置/证据路径四节齐全）。
- 采样噪声的诚实处理：崩坏率跨批波状（W19CROSS 同素材 17.4% → W21 快车道 0/104 → W21FULL 2.6% → W22FULL par 1.7%/cand 3.0%），各判决书均注记"不外推族消失/不外推趋势"（`W21_FASTLANE_G0_VERDICT_20261104.md` 注记 1、`W21FULL_VERDICT_20261104.md` 机制读数 3、`W22FULL_VERDICT_20261104.md` 注记 4）。

### 5.4 预算账本（scope + cap + calls.json）

- 授权账本逐 attempt 记账：`logs/auth_budget/*.jsonl`（现存 281 份非 lock 账本文件）；每本首行 = authorization_scope（purpose/scope_id/max_attempts/endpoint/model），逐行 attempt_started/completed。全量批示例：AUTH-W22-FULL-20261104-01 cap 464、929 行事件。
- 汇总口径：`RESEARCH_STATE.json` budget_state——total_started_all_ledgers=2726、ledgers=79、closed=36、open=43、nominal_unused_reservation_in_open=587（note："开放=缺关单事件非缺终态；管理帽 16384 不变"）。
- 失败如实记账实例：W21 重试首轮 12 次失败 attempt（流式旗×假响应不兼容根因）入另册账本不删不改，重试 v2 12/12 修复，另册实耗 24/92（updates[70][71]；`W21FULL_VERDICT_20261104.md` G4 行）。

### 5.5 复现面残余缺口

① RESEARCH_STATE 自述两窗口并行更新存在"后写覆盖前写"风险，靠 TASK_QUEUE+磁盘工件互校（updates[57] 注）；② W22FULL 评审进程曾在 172/464 中断后续跑完成，"续跑前后数据一致性未独立校验"（`W22FULL_VERDICT_20261104.md` 注记 5）；③ judge 调用失败率跨批不同（W18B 评审 16.3% 已降权、LD1 32 处 CALL_FAIL、W17C 5.6%）——已有"CALL_FAIL>10% severe 自动降权待亲读"规则（LD1 §6.3）但执行依人；④ 红队观察：`benchmark/episodes` 8 文件同字节重存 mtime 异常未查明工具来源（内容已证清白，`REDACT_TEAM_W19_01_CHECK_20261103.md` §5）。

---

## 6. 用户点名风险逐项核对（证据 + 残余风险）

### 6.1 题目泄漏（候选在开发期见过题）

- **证据（真实性成立）**：`W03_CENSUS_20261030.md`（机器读件 `W03_CENSUS_20261030.json`）——全库 43 篇（现库 208 条目）**author_type=AI、dev_exposed=true、independent_review=false → 当前无一具备未见验证资格**；`RESEARCH_STATE.json` w03_01_census.independence 同文。正在使用的全部判决批（FS1→W22）均为已暴露开发素材，FLJUDGE 判决明写"EXPOSED（LR2 已消费，跨代对比口径，不称'未见'）"。
- **缓解在位**：custody 账本 SEALED→EXPOSED 不可逆（§4.1）；隔离生成会话 + `generation_isolation_ack`（§4.1）；B 线是唯一 blind 且封存（§4.2）；"已见域机制回归批"在判决中显式标注（W17b："六篇均已暴露，本批是机制回归；泛化证据由 W17c 未暴露篇补"，`W17B_GUARD_VERDICT_20261103.md` 诚实注记）。
- **残余风险（结构性）**：当前**没有任何一条结论拥有"未见验证资格"**——包括现役双冠军的全部成绩；"未暴露篇"（W17c/LD1/W16 的 LRE148）只等于"该批首次消费"，不等于作者侧未见。B 线未消费前，对外只能声明开发集证据。

### 6.2 评分偏向某 harness（评估器偏向候选/冠军）

- **反向证据（errata 咬过候选也咬过冠军）**：W04-02 勘误把 E3 从 KEEP 改判 ELIMINATE（severe 0→2），把 Bmin 从 REWORK 改判 ELIMINATE——勘误方向不利于候选；第四轮又以全量核算复权 E3（`FOURTH_ROUND_VERDICT_20261031.md` §四）——复权方向利于候选但附登记缺陷与"字面口径并列保留不删除"。两方向都有实例，说明勘误不是单向护主。
- **对称性机制**：程序层唯一计分器对双臂同规则（含 W17b 守卫对照：同批父本违规 7/72 vs 候选 2/72，`W17B_GUARD_VERDICT_20261103.md`）；judge 不单独判死（rubric §3 + W20-03 severe P=0.062 实证）；双序 swap 门（§2.4）；双臂对称校验（W19B detect 模式实测 par 43/cand 37 崩坏双侧对称计数，updates[61]；W21 重试双臂对称 cand 5/5、par 7/7，`W21FULL_VERDICT_20261104.md` G3'）。
- **残余风险**：judge/卡面/gold 由同侧 worker 会话生产（见 6.3）；B3 与判决同会话（§3.2 缺口）；"程序分=10 分"批（FLJUDGE 双臂 10.0）说明程序层对语义无判别力，语义面只剩 judge+亲读两条 AI 通道。

### 6.3 AI 自己出题、自己判分

- **事实**：题目 author_type=AI（census）；gold/reference 为 AI 侧断言（gold sidecar fact_gold 2919/3102 位断言，updates[55]）；judge 为同基座模型 dsf；B3 为主会话 AI。
- **缓解在位**：① 程序断言独立于 judge（层1/层2 分离，judge 永不 veto，`arena/lb_scorer.py` 宪法行）；② 引文逐字硬校验是确定性代码，能实证 judge 引文造假（§2.3）；③ 机械窗口保持器"同器同 GT"跨臂测量（LR2/FLJUDGE/LD1 的 window-keep 表），不经过 judge；④ 判据跑前冻结+预注册（§5.1），削减跑后自判空间；⑤ 判决书强制"诚实登记/边界声明"节（每份判决均有）。
- **残余风险（如实写）**：语义维度的"参考答案是否合理"没有人类锚点——用户愿发 UNKNOWN（`W04-04_FS1_FULL_SCORECARD.md`）；gold 自身的错误（E184 课时账错数被 gold 背书后经审查改造，`STORY_CUSTODY_LEDGER.json` W3-T4-SEMANTIC-S5 事件；LRE128_T35/E164_T05 同类改造，updates[46][47]）说明 AI gold 会错、且错误只能在审查中偶获，无系统性 gold 质量度量。**结论：本体系所有"语义质量"结论的证据上限 = AI 互评 + 确定性一致性检查，未达人类评价。**

### 6.4 题目模板化（同质题刷分）

- **证据**：`tools/story_skeleton_dedupe.py` 全库普查 **184 篇标准 0 近重簇**（累计 32→48→128→184 复跑均 0，updates[36][41][48][49]）；**12 域**覆盖且全域 ≥8 篇（域分布：职19/数19/运18/学18/家18/医18/餐17/宠17/拼16/庭15/兴8/其他1，updates[48]）；分域配额生成（`W03-06_WAVE3_SKELETON_PLAN_20261102.md` §1，T1 32 篇 4 代理按配额）；184 篇各自独立 skeleton（查重底表 `W03_CENSUS_20261030.json` per_story.themes）；另有 16 长剧 + 8 反事实孪生（`B_LINE_MANIFEST.json` composition 与 LD1/CF1 判决批头）。
- **残余风险**：全部题目出自同一生成管线（QoderWork worker-AI 合成，census），"无近重"不等于"分布等于真实用户输入"；素材瑕疵有先例（CF1 E149 孪生 T05 明天/后天措辞与 +1 天时间戳不一致，`CF1_VERDICT_20261102.md` §5.3；E-MAT 系列见 6.7）；域是自报标签，无外部域效度验证。

### 6.5 只检查负面（缺正向互动证据）

- **证据（正向面有专项工作）**：`W08-01_POSITIVE_ANNOTATION_20261031.json`（4 故事 48 轮正向互动三轴标注）；`W08-02_HOOK_CANDIDATES_20261031.md`（五类钩子分类法 + 派发规则）；`W08-03_FLIRT_ANCHORS_20261031.md`（8 钩点位 × 3 语域）；`W08-04_DEEPCHAT_TEST_20261031.md` + `W08-05_DEEPCHAT_VERDICT_20261031.md`（深聊三档实测：0-语域轨迹损伤 2+ 轮确认、冠军从未落入 0 语域）；正向能力表 B0 vs r7（updates[27]，W08-06）；`primary_metrics` 含 positive_interaction_slices / interaction / initiative / emotion_relation（`RESEARCH_STATE.json`）；rubric §10 参考答案自由度（不规定唯一措辞、不逐字匹配）。
- **残余缺口**：正向面证据形态是"标注+锚点+行为验证"，**没有晋级级量化门**——没有任何一次 KEEP/淘汰以正向互动指标为主要判据；正向标注仅 48 轮/4 故事；调情/钩子锚点的跨域覆盖未测。

### 6.6 不同候选不同条件（比较不公平）

- **证据**：同批双臂同条件是硬纪律——固定端点/model/采样（授权 scope 逐批登记，`logs/auth_budget/AUTH-W22-FULL-20261104-01.jsonl`）；episode_order 双臂严格交替 + 顺序核对（红队 §4）；prompt 字节等价门（§5.2）；capture 回放 464/464（§5.2）；候选与父本"无触发轮逐字节相同"的单变量设计（uc_retry/uc_attr 均 mechanism 层纯函数注入，`W22FULL_VERDICT_20261104.md` G4；红队 §3 单变量 diff 核验）；跨批数字不可比的明文纪律（AGENTS §5"请求位嵌套于故事"、判决书"跨批横比探索性不进判决"，`W04-03_FIRST_LOOP_VERDICT.json` w_note）。
- **事故处置佐证**：W18 中止后部分数据**不进主判**、重跑新批（§5.3）——宁可重花 320 attempts 也不让不同条件数据混入。
- **残余风险**：评审侧条件跨批不同（judge callfail 率、swap 位数量、评审版本），已用降权规则缓解但评审驱动脚本本身在演进（3 个评审脚本修 severe 映射、run_lp_review_generic 接 quote check），评审"版本"未像评分器那样逐批版本号化。

### 6.7 测试集反复调参（到赢为止）

- **制度**：封板后改题=舞弊条款（AGENTS §4"封板前 pilot 窗口是唯一题目迭代期"）；rubric 改动必须版本化+旧答卷离线重评（AGENTS v1.4 ④，如 v0.9.1 重评结论作废重算先例）；"不无限重试到赢"（AGENTS §8：连续 3 批无实际优势暂停换向；FLREG2 判决按 H_FL_null 淘汰不调参，`FLREG2_VERDICT_20261101.md`；W14M02 b2 淘汰不调参，`W14M02_JUDGMENT_VERDICT_20261102.md`）。
- **勘误制度实例（E-MAT）**：E-MAT-E062-F06（快门数品类错误→探针作废不计失败，`LONGPROBE_VERDICT_20261030.md` §5）；E-MAT-E021-T07（时钟×卡面矛盾，updates[2]）；E-MAT-E065-T21（15200 vs 14600 算术矛盾，updates[6]）；E164_T05/LRE128_T35 账目陷阱改造（gold 不再背书错误算术，updates[46][47]）——全部发生在**消费前的素材审查窗**（W03-06 语义审查），改后有哈希级联与复验（updates[42][46]）。
- **残余风险**：素材修复集中在入库审查期，入库后若再发现 gold 错误只能"登记不回改"还是允许勘误，边界依赖 W03-05 流程文本；b_line 是唯一封板集且未消费，"封板"制度尚无完整执行案例（v1 重建恰说明封存前检查必要）。

---

## 7. 可靠性总评

### 7.1 可信的机制（有实证、多次生效）

1. **程序层计分边界**：唯一计分器、权重对候选不可见、协议函数单点权威（`arena/protocol_public.py`），v0.9.3 向后兼容设计使其历史读数不漂移（`arena/lb_scorer.py`；`tools/test_gold_sidecar_scoring.py`）。
2. **双臂同批同条件 + prompt 字节等价 + capture 回放**：跨批公平性的硬件级保障（§5.2/§5.3）。
3. **custody 曝光账本**：SEALED→EXPOSED 不可逆 + 追加式事件流；b_line_v1 污染被抓住并留全审计链（§4）。
4. **引文逐字硬校验**：确定性、零成本、已接线，no_hit=0 稳定成立，severe_unverified→B3 强制链生效（§2.3）。
5. **severe 亲读兜底 + swap 阻断门**：四次（W19CROSS/W21/W22/W18B）真实改变或阻断判决，且改动方向双向（§2.2/§2.4/§3.2）。
6. **预算账本与如实记账**：失败 attempt 不删不改（W21 重试 24/92），管理帽不超标（§5.4）。
7. **勘误留痕制度**：errata 双向咬、buggy 值保留、判决重跑由预注册执行器执行（§1.2/§6.2）。

### 7.2 有已知失效史的机制（依赖时须查勘误链）

1. **judge severe 旗**：P=0.062、四批复现误烧/漏检/空注/双标——只能当索引（§2.2）。
2. **judge winner 单值**：复现 50%、近平局翻转频繁——不进判决（§2.4）。
3. **severe 臂映射**（W04-02）：历史四份报告受影响，已修正留痕（§1.2）。
4. **评分器 m_src 两处**（FK-SCORE-01 + 隔离路径）：608 位离线重评已清，W17c/W17b 数字以修正口径为准（§1.2）。
5. **评审引文幻觉**：11.9% 校准实测 + FLJUDGE/W18B 实例，fragments_miss 长期存在（§2.3）。
6. **B 线 v1 选篇**：账本漏记污染，v2 重建（§4.2）。
7. **执行基建**：W18 9 路并发 120s 超时事故（§5.3）；W21 重试首轮流式旗 bug（12 失败入账）；W22FULL 评审中断续跑未独立校验（§5.5）。

### 7.3 结构性缺口（当前无解、必须如实申报）

1. **零未见验证资格素材**：全库 dev_exposed，B 线未消费——所有结论止步于开发集（§6.1）。
2. **语义评价无人类锚点**：gold=AI、judge=AI、B3=主会话 AI；用户愿发 UNKNOWN（§6.3/§3.2）。
3. **judge 单裁判单序**：boundary/memory 无测试面、severe GT 阳性 2/48、评审脚本未版本号化（§2.1/§2.3/§6.6）。
4. **正向互动无晋级级量化门**（§6.5）。
5. **B 线 only_once 之外没有第二套冻结题**，封板制度无完整执行先例（§6.7）。
6. **暴露记账的自动化闭环未证明**（§4.2 缺口①）。

**一句话总评**：程序层与执行公平性机制可信（有确定性代码与账本实证）；语义层是"AI 互评 + 确定性一致性检查 + 亲读兜底"的三层补偿结构，其单点失效有频繁历史且已被制度化对冲，但其证据上限受"无人类锚点、无未见素材"两个结构性缺口封顶——在 B 线消费与用户愿发采集之前，本评估体系的语义结论只能以开发集证据的名义、按 E 文件的分级使用。
