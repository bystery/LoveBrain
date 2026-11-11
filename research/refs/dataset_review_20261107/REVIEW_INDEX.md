# 数据集与评估体系独立审查包——入口索引（2026-11-07）

> 用途：供外部审查者独立核验 LoveBrain Arena 数据集与评估体系。**本次只做同步、证据提取、审查准备与方案设计；未修改任何正式题库、评分口径、封存集；零模型调用。**
> 独立审查入口建议顺序：本文件 → A（是什么）→ B（为什么）→ C（gold 与样本）→ D（可信吗）→ E（结论强度）→ F（改造草案）。

## 1. 包内文件

| 文件 | 内容 | 大小 |
|---|---|---|
| `REVIEW_INDEX.md` | 本索引 | — |
| `A_COMPOSITION_AND_PROVENANCE.md` | 数据集构成与溯源：规模关系式、生成方式、审核、结构、版本、去重、覆盖标签、划分与曝光史、分母表、12 条"不能证明之事" | ~38KB |
| `B_DESIGN_RATIONALE.md` | 设计逻辑：场景/九维/故障族/长度的选择依据与覆盖审查（若与 A 有重叠处以交叉引用为准） | 见文件 |
| `C_GOLD_AND_SCORING.md` | gold 字段定义、生成方法、审核机制、勘误台账、评分五层分工、judge 校准现状 | ~31KB |
| `C_SAMPLES.md` | **29 个代表性完整样本**（无据确定化/更正链/账目陷阱/人物归属/长程保持/反事实/正向互动/边界不回应/时间状态与已知瑕疵/记忆使用），每个含 persona+前情+输入原文+gold 逐字段+双臂输出摘录+评审结论+能证明/不能证明 | ~60KB |
| `D_EVALUATION_RELIABILITY.md` | 评估可靠性：评分器失效史、judge 资格表、引文硬校验实测、B3 亲读账、盲评与曝光管理、版本绑定与复现、七项点名风险逐条核对、总评三分法 | ~34KB |
| `E_RESULTS_EVIDENCE_STRENGTH.md` | 逐批结论与证据强度（~30 行总表：分母/评分器版本/通道标注/结论类型/路径/强度评级）；跨批不可比声明；负结果 11 条；未做 8 条 | ~38KB |
| `F_REDESIGN_DRAFT.md` | 数据集改造草案：风险排序、保留/修订/新增、覆盖矩阵设计逻辑、制度配套、小样本先行流程 | ~8KB |
| `recompute_stats.py` | 只读统计复算脚本（纯标准库、零模型调用；跳过 b_line） | ~20KB |

## 2. 一键复算主数字

```bash
cd <仓库根>   # 即 harness-lovebrain/
python -I refs/dataset_review_20261107/recompute_stats.py          # 文本模式
python -I refs/dataset_review_20261107/recompute_stats.py --json   # JSON 模式
```
预期输出对账：库 208 集/2944 轮；GOLD 221 集/3102 轮/2919 带断言/79 空 gold/104 有卡无断言/6603 原子；must-not-affirm 711（取消族 412 ∪ 否定 632，重叠 333）；九维覆盖 9 数；域分布 12 数；custody 210/161/49/173。A 文档 §11 有脚本实跑结果。

## 3. 本地专用证据（不入 GitHub，路径+校验）

| 内容 | 本地路径 | 说明 |
|---|---|---|
| 原始逐调用答卷 | `D:/lovebrain-arena/logs/`（约 2.1GB，191+ 批次子目录） | 每批含 raw 输出、calls.json（usage/时延）、评分卡；独立复算时的原始层 |
| 授权账本 | `D:/lovebrain-arena/logs/auth_budget/*.jsonl`（281 份） | scope+cap+attempt 全记账 |
| ACL 锁定历史快照 | `D:/lovebrain-arena/archive/20261107_root_cleanup/acl_locked_snapshots_20261021/`（130 目录） | 2026-10-21 事件快照，仅存档 |
| 隔离出题账本 | `refs/experiment_protocol_20261023/global_harness_program_20261030/STORY_CUSTODY_LEDGER.json` | 已入 GitHub（210 篇/173 事件） |
| 包内文件校验 | `refs/dataset_review_20261107/SHA256SUMS.txt` | 本包全部文件的 sha256 |

## 4. 审查边界声明

1. `benchmark/b_line/` 封存盲测集：**本次审查材料未读取其任何 episode 内容**（仅 manifest/README 元数据）；未消费、未运行、未改一字。
2. 未修改：任何 episode/grading card/gold 文件、`arena/lb_scorer.py`、`benchmark/rubric.md`；零模型调用。
3. 已知如实登记的不一致（不粉饰）：B_LINE_README 停留 v1 构成；status 词表超集；subject 401 条自由文本；`LP_W21FULL_B3_READRESULT.json` 待补（判决书口径为准）；W19CROSS 亲读 26 位 vs 33 条记录两种口径并存。

## 5. 与本包配套的仓库级同步说明

- 同步范围：本包 + `PAPER.md` + `README.md` + W24 更新的状态文件 + `benchmark/` 数据集（**不含** `benchmark/b_line/` 增量改动、`logs/`、ACL 快照内容、交付 zip）。
- W24 根目录整理（130 ACL 快照等归档）的远端镜像与归档说明见 `archive/20261107_root_cleanup/MANIFEST.md`。
