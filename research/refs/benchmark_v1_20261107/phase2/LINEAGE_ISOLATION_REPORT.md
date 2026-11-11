# LINEAGE_ISOLATION_REPORT — Benchmark V1 Phase 2 谱系组隔离与保管×分区核对

> 生成：2026-11-08。全部数字来自 `python -I` 只读实跑（不手填）：`tools/benchmark_v1/lineage_check.py`
> 与 `tools/benchmark_v1/custody_split_guard.py`（纯标准库只读工具），配套结构化清单
> `phase2/CUSTODY_SPLIT_MANIFEST.json`（两文件互引，数字一致）。本报告与两工具均
> 不读 logs/、candidates/、B 线 episode 正文、GOLD_SIDECAR 内容；账本 append-only 只读。

## §1 lineage_check 机械核查结果

- 命令：`python -I tools/benchmark_v1/lineage_check.py --stories-root benchmark/stories_v2 --split-csv refs/benchmark_v1_20261107/phase2/LINEAGE_SPLIT_PROVISIONAL.csv --json`（运行时间 2026-11-08 15:28:33）。
- 摘要：**谱系组 26 组；分区映射 38 篇（E301–E338 全 dev）；B 线成员 0（主运行未传 manifest，临时清单不含 B 线成员）；违规 0；警告 0**。退出码 0。
- 26 组构成：34 条谱系边（manifest source 字段 > 标题正则 > LRE 命名约定三档派生，逐边登记 derivation）union 成组，`group_id = LIN-<母本>`；覆盖 16+2 长剧原位续写组与 8 CF 孪生组的母本/变体边。
- 补充运行（同命令加 `--b-line-manifest benchmark/b_line/B_LINE_MANIFEST.json`）：B 线成员识别 24 名，违规仍 0、警告仍 0——C2（B 线关联组整体退出 dev/selection）通过。
- §1–§6 引用的机械核查均为只读检查：manifest 与 exposure.json 元数据两字段（experiments_used / dev_exposed），零模型调用，不写任何被查对象。

## §2 custody_split_guard 三路核对（分派表 × 账本 × exposure）

- 命令：`python -I tools/benchmark_v1/custody_split_guard.py --allocation refs/benchmark_v1_20261107/phase2/DATASET_V1_FINAL_ALLOCATION.csv --stories-root benchmark/stories_v2 --expected-pending 157 --json`；运行时间 **2026-11-08 15:38:34**（与 CUSTODY_SPLIT_MANIFEST.json 内嵌现跑同源）。
- 摘要：分派表 240 行（定稿 83 = 旧题 45 + wave4 新题 38；PENDING 157）；合并谱系组 68（声明组 ∪ 派生组）；孪生组 8；**违规 0**；警告 38（全部为 wave4 新题 E301–E338 未登记账本的"新题待 seed"提示，非违例）。`--expected-pending 157` 与分派表 v2 实数相符（#QUOTA_TOTAL pending=157）。
- 三路核对逐路：
  1. **分派表路（G1/G2/G4）**：G1 组内 split 一致 0 违例（母本/续写、CF 变体均不跨区）；G2 twin_group 不跨区 0 违例（T10-G1 全 DEV、T10-G2 全 SELECTION，G3–G8 全 PENDING）；G4 PENDING 数量 157=157 相符。
  2. **账本路**：STORY_CUSTODY_LEDGER.json（只读）登记 210 篇：EXPOSED 49、SEALED 161；无 wave4 seed 事件、wave4 38 篇未登记（→ 38 条提示；seed 待执行，见 §6）；无 HOLDOUT 本体，故"账本未登记的 HOLDOUT 本体"违例路径 0 触发。已定稿 DEV 中 19 篇账本 EXPOSED——属旧题已消费，DEV 准入明确允许，非违例。
  3. **exposure 路**：已定稿 83 篇全部有 exposure.json（逐一读取，仅两字段）；**experiments_used 非空 0 篇**；dev_exposed=true 共 19 篇且全在 DEV（SELECTION 2 篇干净）。G3 组级"未消费证明"检查因 HOLDOUT 定稿 0 篇而空转（见 §6）。
- 交叉引用：`CUSTODY_SPLIT_MANIFEST.json` → `cross_ref_custody_split_guard`（同时间戳 15:38:34、violations=0、expected_pending_match=true）。

## §3 规则统一记录（2026-11-08，Phase 2 §8.1）

- **SPLIT_AND_CUSTODY_PLAN.md §0（头部修订节，2026-11-08 新增）**：最终裁定"**使用记录逐篇记，独立性资格按谱系组算**"——R1 细化（组成员被消费→整组失去 unseen 资格；账本事件粒度不变）+ 三条分区铁律：CF 孪生不拆分区、母本/续写不跨 DEV/SELECTION/HOLDOUT（LRE 原位续写与母本同一内容单元）、反事实变体不跨分区（组内任一成员的分区决定整组分区）。§1–§4 原文保留不改。
- **NEW_STORY_SPEC_V1.md [SUPERSEDED] 就地标记**：头部引言（L29–31）、§3.5（L145）、§6.1（L274）三处标注 `[SUPERSEDED 2026-11-08（Phase 2 §8.1）]`——旧表述"组消费≠全组暴露/逐篇独立保留未见资格"仅就使用记录成立，资格判定作废改按组；该文件的账本逐篇记账条款仍然有效。
- **两工具组派生同源（测试锚定）**：`custody_split_guard.py` 经 importlib 按绝对路径加载 `lineage_check.py`，复用同一 `scan_stories_root / build_groups / read_exposure`，规则不分叉。锚定测试（2026-11-08 实跑全 green：16+3 passed）：
  - `tools/benchmark_v1/tests/test_custody_split_guard.py`（16 tests）：合成树检出跨分区/孪生跨区/HOLDOUT 消费各路径 + `test_real_data_smoke_zero_violations`（真实分派表 0 违例、E333/E334→DEV、E335/E336→SELECTION、LRE125 与 E125 同组派生兜底）+ `test_real_data_lineage_check_still_green`（两工具组派生同源回归）。注：本轮随分派表 v2 将 `twin_groups` 期望 2→8（v2 对 T10 全部 8 组声明 twin_group，含 6 个 PENDING 组；guard 按"行上声明"计数），断言更新后通过，guard 本体零改动。
  - `tools/benchmark_v1/tests/test_benchmark_v1_offline.py` §8.1（3 tests）：合成谱系树检出 cross_split / b_line_in_dev_sel / holdout_consumed、干净布局通过、真实 stories_v2 清单回归。

## §4 T10 孪生隔离

- **G1→DEV / G2→SELECTION**：`LG-T10-G1`（E333 成立线 / E334 取消线，twin_group=T10-G1，变量=状态位 T04）整组 DEV；`LG-T10-G2`（E335 表哥阿哲线 / E336 表弟阿珂线，twin_group=T10-G2，变量=主体位 T03）整组 SELECTION。另 6 组 PENDING 已预登记：G3（E339/E340）终分派 DEV、G4（E342/E343）终分派 SELECTION、G5–G8（E513–E520）终分派 HOLDOUT。
- **组内不跨区**：guard G2 机械核对 0 违例——同组两篇 split 全等；PENDING 组不参与比较（G4 管数量）。
- **跨污染零命中（引 C3 审计 `phase2/STORY_BATCH_REPORTS/AUDIT_C3_E333-E338.md`）**：同构轮卡面逐字段全等（脚本比对 0 diff）；原子 quote 集逐字对称；守卫原子 6+6 镜像（invalidated/negation=true，E335/E336"出借人错误归属（不成立）"型）；排除 forbidden/markers/守卫原子三类刻意点名处后，G1/G2 双向 + G1↔G2 跨组扫描 **0 命中**（唯一初扫命中"首日票"经人工核验属 E333 内生短语，非跨世界泄漏）。

## §5 B 线隔离

- **E048 污染登记（不冒充独立）**：B_LINE_MANIFEST v2（b_line_v2，24 篇）含 E048；其谱系组 LIN-E048 = {E048, LRE048}，兄弟篇 LRE048 于 2026-11-01 被 LR2/FLJUDGE 两批逐字消费（账本：`lifecycle=EXPOSED`，`consumed_by=[BATCH-LONGRANGE-LR2-LRE060LRE048-20261101, BATCH-FLJUDGE-LRE060LRE048-20261101]`）；E048 本体 SEALED 但按 R1 组级资格规则随组失去未见效力。登记于 SPLIT_AND_CUSTODY_PLAN §3：参战过 LR2 的臂在 E048 位答卷不具"未见"效力，处置选项 A（升 v3 换篇）/ B（终验折价标注 binds validity=discounted_lineage）待用户拍板——在此之前不宣称 E048 独立。
- **b_line_members=0**：§1 主运行摘要即 0——临时分区清单仅含 wave4 38 篇，B 线成员不在任何 DEV/SELECTION/HOLDOUT 分派中（B_LINE_MANIFEST 24 篇经 --b-line-manifest 补跑确认识别，0 违规，C2 通过：无 B 线关联组进入 dev/selection）。LIN-E048/LIN-E060/E150/E152/E153/E155 等关联组在合并分量中 split=UNASSIGNED（整体退出分配表，规则行为非违例，见 manifest lineage_groups）。
- **SEALED 正文零读取声明**：本报告与两工具对 B 线**仅读 `benchmark/b_line/B_LINE_MANIFEST.json` 的 episodes 元数据键**（工具设计如此），`benchmark/b_line/` 下任何 episode 正文零读取；账本 `b_line_sealed`/`b_line_resealed`（2026-11-03）事件仅作重封记录引用，B 线 only_once 性质不变。

## §6 已知边界

- **HOLDOUT 未生成（PENDING）**：分派表 split 列 HOLDOUT=0 篇；120 个 HOLDOUT 席全部为 PENDING 行（终分派段 E401–E520，见 manifest `per_split.PENDING.segments`）。因此本轮 G3/C3 组级消费检查为空转（核查对象 0 篇）——**"HOLDOUT 未消费"不因本轮 0 违例而成立**，须待 HOLDOUT 定稿并重跑后才可声明。
- **临时全-DEV 清单的局限**：`LINEAGE_SPLIT_PROVISIONAL.csv` 是 38 篇 wave4 新题全标 `dev` 的临时映射（E301–E338），仅锚定 wave4 内部组一致性与 manifest 派生通路，不代表最终分区。**正式分派定稿后须重跑两工具**：lineage_check 换正式 split CSV；guard 用正式表并将 `--expected-pending` 改 0（或省略）。当前 manifest 的 per_split 即该临时态快照（version 字段已注明 v2 2026-11-08）。
- **wave4 seed 未执行**：账本尚无 wave4 38 篇的 seed 事件（`wave4_stories_in_ledger=0`），当前"未消费"证明仅靠 exposure.json 元数据；**seed 待执行：`python tools/story_custody.py seed`（wave4 manifest 已在清单路径 benchmark/stories_v2/wave4_dev/W4_MANIFEST_12T/24T/32T.json）**——本会话账本只读，未代执行。
- 本报告不修改 runs/（live 在写）、不写账本、不读 GOLD_SIDECAR；guard 的 PENDING 混排提示路径（组内既有定稿又有 PENDING 成员）本轮 0 触发，冻结前如出现须先定稿整组。
