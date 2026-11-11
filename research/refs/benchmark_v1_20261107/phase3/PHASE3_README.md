# PHASE3_README · Benchmark V1 Phase 3（2026-11-09 开工）

> 指导书：用户 2026-11-09 下发《Phase 3 最终实施指导书》（外部复核 AI 起草，Worker 照单执行）。
> 审查基线 commit：`387c55ace6a020b8e73670623824d480f30adea8`（GitHub bystery/arena，harness-lovebrain/）。
> 本目录 = Phase 3 交付目录（指导书 §P3-11 结构逐步补齐）。

## 0. 最高执行原则（照录）

240 篇主数据集真实存在；10 专题数量准确；80/40/120；Gold 与评分可靠性经验证；四组同模型同输入条件；
HOLDOUT 只消费一次；用户主观评分与 AI Judge 明确区分；结论与记录严格对应。不为赢改评估标准，
不为数量降故事质量，不覆盖旧文件，不拿 Mock 冒充真实，不拿程序分冒充回复质量。

## 1. 十步执行顺序（§P3-12）与状态

| 步 | 内容 | 状态 | 交付件 |
|---|---|---|---|
| 1 | 修正 Phase2 分区与暴露账本 | **DONE 2026-11-09** | PHASE2_CLOSEOUT_CORRECTIONS.md、CUSTODY_EXPOSURE_RECONCILIATION.md、DATASET_V1_ALLOCATION_V3.csv、LINEAGE_SPLIT_PROVISIONAL_V2.csv、reconcile_step1.py、账本备份 |
| 2 | 40 篇 SELECTION（E339–E378） | 进行中 | wave5_selection/ + 批次报告 |
| 2b | 120 篇 HOLDOUT（E401–E520，独立会话生成，SEALED） | 待 2 收口 | wave6_holdout/ + HOLDOUT_SEAL_REPORT.md |
| 3 | Benchmark V1 冻结（240 清单+哈希+HOLDOUT 强隔离） | 待 2b | DATASET_V1_FROZEN_MANIFEST.json 等 |
| 4 | 四组入口修复（LEGACY_COMPAT_R9 接线、phase3_runner、全局预算门、评分公平层） | 待 | SCORER_PARITY_REPORT.md 等 |
| 5 | 评分校准（40 真实锚点、Judge 资格复核、C14/C15 语义判据+100 控制） | 待 | JUDGE_CALIBRATION_V2.md |
| 6 | 冻结 EXPERIMENT_PROTOCOL_FROZEN_V1 | 待 | EXPERIMENT_PROTOCOL_FROZEN_V1.md |
| 7 | SELECTION 四组正式实验（cap 2250） | 待 | SELECTION_RUN_REPORT.md |
| 8 | HOLDOUT 一次性四组实验（cap 7000，once-only） | 待 | HOLDOUT_RUN_REPORT.md |
| 9 | 人工盲评（60 位×3 对=180 组）+ 统计 | 待 | HUMAN_PREFERENCE_*、STATISTICAL_ANALYSIS.md |
| 10 | 论文 + GitHub 交付 | 待 | PAPER_V1.md、SHA 回填 |

## 2. 关键纪律（本目录所有工作共同遵守）

- **三角色隔离**：作者/标注/审计 = 独立会话（H 段编号用于 HOLDOUT 批，物理隔离 DEV 系材料）。
- **统一模型**：USTC `deepseek-flash-2`，temp 0.7，stream false；LB_API_KEY 只进环境变量零落盘。
- **预算**：全局共享 attempt 账本（第 4 步建成）；SELECTION cap=2250、HOLDOUT cap=7000，到限停批。
- **once-only**：HOLDOUT 开跑前 10 项门禁全过（§P3-6.1）；跑后 EXPOSED 不可逆。
- **AI 不代投**：用户盲评 NOT_COLLECTED 就如实写；未校准 judge 不判死。

## 3. 文件索引（随进度更新）

- `reconcile_step1.py` + `_pilot_consumption_evidence.json` — 第 1 步对账执行件与证据
- `DATASET_V1_ALLOCATION_V3.csv` — 正式分派表 v3（240 主集 + 3 诊断池；160 PENDING 预编 ID）
- `LINEAGE_SPLIT_PROVISIONAL_V2.csv` — wave4 分区映射 v2（E335/E336→dev）
- `PHASE2_CLOSEOUT_CORRECTIONS.md` — P0 修正登记（A 已执行 / B 移交后续步骤）
- `CUSTODY_EXPOSURE_RECONCILIATION.md` — 暴露账本逐篇对账（1,163 attempts 精确闭合）
- `phase3_runner_notes.md`（第 4 步）、`SELECTION_AUTHOR_BRIEF.md`（第 2 步）等按步骤追加
