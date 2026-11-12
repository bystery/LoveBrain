# research/RESEARCH_INDEX.md —— 研究资产全索引

> 定位：`research/` 十二区的件级索引——回答"每区有什么关键文件、从哪读起"。精简入口与阅读路径见 [README.md](README.md)，本文是区级/件级导航的权威件。文中路径均为本仓相对路径；区级文件数一律以 `ls`/`find` 实测为准，不在本文登记快照值。

---

## 1. 十二区逐区导览

> 分区框架：11 个目录区 + `PAPER.md`；`README.md`（研究入口）与本文（全索引）为根面导览件，不计入十二区。

| # | 区域 | 一句话说明 | 关键文件 |
| --- | --- | --- | --- |
| 1 | `benchmark/` | 题库与评测基准：全量剧情库、Gold 侧车双轨、公开开发集、rubric 两版、统计口径页与图表落点 | `STATISTICS.md`（公开统计唯一口径页）；`rubric.md` + `rubric_v0_6_draft.md`（现行版与历史草案并存）；`core_set.json` / `full_set.json` / `test_suite.json` / `dims_map.json`；`stories_v2/`（剧情库分组目录 + `GOLD_SCHEMA.md` / `GOLD_SCHEMA_V2.md` + `GOLD_SIDECAR/` 权威侧车 + 远端快照对照轨）；`b_line/`（B 线 24 篇公开对照集，状态见 `B_LINE_README.md` §0）；`episodes/`（149 个公开开发集剧集）；`anchors/`、`annotations/`、`cases/`、`tasks/`、`releases/`、`charts/` |
| 2 | `dataset/` | 母数据集公开面：结构摘要 + 合成样本（原件为真实运行数据，永不出站） | `manifests/STRUCTURAL-SUMMARY.md`（结构与字段骨架摘要，零正文）；`synthetic_samples/`（AI 学习真实结构后生成的全合成样本，三层合成标记 + `SYNTHETIC_MANIFEST.json` + `README-synthetic.md`） |
| 3 | `candidates/` | 参赛候选全保留（不止冠军）+ 版本谱系：champion / historical / negative_control / ablation / template / reference 六类 | `VERSION_LINEAGE_V2.json`（谱系登记）；`window_expand_b_norecap_uc/` 与 `window_expand_b_norecap_guard/`（双域冠军，含 `IDENTITY.md` / `MECHANISM.md` / `params.json`）；`reference_product_v1/`（参考基线）；`neg_a_amnesia/` `neg_b_dumper/` `neg_c_service/`（阴性对照）；`ablate_*`（消融对照）；`_template_mechanism/`（模板，不列为已验证候选） |
| 4 | `evaluation/` | 评测与判决版本链：各状态全保留（含错误版成对留档，不删改历史） | `QUALITY_FIRST_MAIN_REPORT_V1.md`（主报告）；`P06_RESULTS.md`、`P07_PILOT_RESULTS.md`；`JUDGE_MANUAL_V1` → `V1_1` → `V2_0_A3`（评委手册版本链）；`A3_CALIBRATION_ANALYSIS_V1` 版本链（含 buggy/interim 留档）；`C4_FOUR_ARM_REVIEW_PACK_V1.md`；`audits/hc2/` |
| 5 | `experiments/` | 实验落盘：runs 批次 + 隔离测试 + 原始日志索引 | `runs-registry.csv`（逐批次登记表：run 名 / 文件数 / 布局形态 / 评分文件数等 14 列）；`runs/`（逐批次实验目录；run 内典型内容：回合级 `manifest.json` / `scoring.json` / `reply_raw.txt` / `calls.json`（含端点痕迹）/ 机制说明件，run 级 `run_identity.json` 与授权账本 `ledger.json`；layout 三形态 `out_layout` / `variant_episode` / `rh_batch`，读前先查 registry 行）；`isolated_runs/`（隔离/授权测试材料）；`raw_logs/INDEX.json`（原始日志索引；原件按 run 边界分卷走 Release/LFS） |
| 6 | `arena/` | 考场运行时框架：纯标准库 Python，含隔离子包 | `lb_runner.py`（运行时 v2 入口）、`lb_scorer.py`（现行评分器 v0.7）、`lb_gateway.py`（统一网关与 API 配置）、`lb_assembly.py`（上下文装配）、`gold_sidecar.py`（Gold 侧车加载）、`protocol_public.py`、`scorer.py`（前置代际评分器，并存保留）；`isolation/`（可信控制器/授权预算/凭据加密等）；`KNOWN_FAILURES.md`（指针件，正文唯一权威在 `refs/`）；`pyproject.toml`（环境声明） |
| 7 | `refs/` | 判决书、实验协议、独立评审、失败分析与基线材料（保留源仓历史相对层级） | `KNOWN_FAILURES.md`（已知故障回归门禁，唯一权威副本）；`VERDICT-INDEX.md`（53 份判决文件全量目录索引）；`REFERENCES.md`；`experiment_protocol_20261023/`（实验协议与判决主体，占本区体量约 95%）；`arena_v1/BASELINE_PARITY.md`（保真边界）；独立评审目录四组；`BASELINE_CAPABILITY.md`、`HARNESS_IO_SPEC.md`、`BUDGET_PLAN.md` |
| 8 | `archive/` | 历史归档：历史资产、历史工作指南、移植原型，每束附索引说明 | `historical_assets/README.md`（归档索引，含 OBJECTIVE.md、RUN_STATE.json 与历史交付快照包）；`historical_worker_guides/INDEX.md`；`integration_preview_20261025/`（历史移植原型，标注"不代表当前生产接线"） |
| 9 | `provenance/` | 迁移证据：来源映射总账、逐件机读台账、隐私与许可审查、对外证据索引 | 详见 §4 |
| 10 | `tools/` | 评测/审计/重评分/统计工具与测试脚本（含在研活跃子区） | `requirements.txt`（环境声明）；`benchmark_v1/`（在研活跃区）；`transcript_miner.py`；`test_*`（67 个测试脚本）；约 150+ 一次性探针按历史归档口径保留原位 |
| 11 | `architecture/` | 架构文档落点（以 `arena/` 实际代码与 refs 材料撰写） | `ARCHITECTURE.md`（正式架构总览，见 §3） |
| 12 | `PAPER.md` | 研究论文稿（正文按现行权威口径，文末附历史口径附注） | 见 §3 |

---

## 2. 研究文档集索引

| 件 | 路径 | 一句话 | 覆盖范围 |
| --- | --- | --- | --- |
| RESEARCH_INDEX | [本文件](RESEARCH_INDEX.md) | 全索引：十二区导览 + 文档件索引 + 阅读路径 | 全部研究资产 |
| RESEARCH_OVERVIEW | [RESEARCH_OVERVIEW.md](RESEARCH_OVERVIEW.md) | 研究总览一页入口 | 研究问题、动机、整体方法与主要贡献；三仓库体系、机制三主线叙事、数据集与评测要点、实验结果概述（限定语整句）、关键边界 |
| PAPER | [PAPER.md](PAPER.md) | 研究论文稿 | 同一模型服务下完整聊天 harness 的可验证迭代：被测对象边界、数据集、30+ 次真实同场对照、分域冠军链与全部判决账本（含未胜出尝试）；文末附历史口径附注 |
| BENCHMARK_EVALUATION | [BENCHMARK_EVALUATION.md](BENCHMARK_EVALUATION.md) | 评测体系与基准评估 | 指标、计算口径、数据切片与评分方式；汇总登记件，判决定位判决书原文件、不复写不重算 |
| CORE_MECHANISMS | [CORE_MECHANISMS.md](CORE_MECHANISMS.md) | 核心机制详解与判决映射 | 各机制解决什么问题、怎么工作、有哪些判决支撑、处于产品实现路径哪一层；与 ARCHITECTURE.md 机制研究叙事节共用同一套证据 |
| DATASET_DOCUMENTATION | [DATASET_DOCUMENTATION.md](DATASET_DOCUMENTATION.md) | 数据集文档 | 数据规模、构造方式、七个专项、Gold 双轨与局限、B 线口径与合成样本；与 benchmark/STATISTICS.md 口径页逐项一致 |
| FAILURE_ANALYSIS | [FAILURE_ANALYSIS.md](FAILURE_ANALYSIS.md) | 失败机制保留与未入选说明 | 已知失败族与判决状态、未入选候选逐项、负结果保留原则、runs 域断跑登记；全部条目引自判决原文件，不翻案 |
| REPRODUCIBILITY | [REPRODUCIBILITY.md](REPRODUCIBILITY.md) | 复现说明 | 环境怎么搭、入口在哪、数据怎么读、哪些能复现哪些不能；离线 smoke test 实测记录以该文为最新 |
| ARCHITECTURE | [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) | 架构总览 | 真实生产链路、模块职责与状态归属（详见 §3） |

---

## 3. 独立件索引

| 件 | 路径 | 一句话 |
| --- | --- | --- |
| ARCHITECTURE.md | [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) | 正式架构总览：项目三层结构（产品/研究/治理）、产品生产调用链、Arena 研究架构、LIVE_AUTO 接线架构与分支状态声明（不把计划画成已启用） |
| PAPER.md | [PAPER.md](PAPER.md) | 研究论文稿：研究叙事与主结果全文；正文用现行权威口径，历史口径与勘误登记于文末附注 |
| STATISTICS.md | [benchmark/STATISTICS.md](benchmark/STATISTICS.md) | `benchmark/` 公开统计口径定稿：八个公开口径数字逐数字版本化（值/口径/统计时间/来源/边界），恒等式闭合与使用纪律；是 benchmark 域统计数字的唯一口径页 |
| KNOWN_FAILURES.md | [refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md) | 已知故障回归门禁 v0.2：9 个真实事故失败族，任何候选不得重新引入；`research/arena/KNOWN_FAILURES.md` 为代码域指针件（不复制正文，防双源漂移） |
| VERDICT-INDEX.md | [refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md) | 判决原文件目录索引：53 份名称含 VERDICT 的 .md/.json 逐一登记（一句话结论/主线归入） |
| README.md | [README.md](README.md) | 研究入口与精简导航：核心文档表、十二区一句话导览、阅读路径与快速入口 |

---

## 4. provenance/ 索引

> 各件职责、关键章节指针与互相引用关系以导航件 [provenance/PROVENANCE-INDEX.md](provenance/PROVENANCE-INDEX.md) 为准，此处只给入口。

| 件 | 路径 | 一句话 |
| --- | --- | --- |
| SOURCE_MAP.md | [provenance/SOURCE_MAP.md](provenance/SOURCE_MAP.md) | 迁移登记总账：资产从哪个仓库/分支来、落到哪里、什么不迁、数字对没对上 |
| MIGRATION_MANIFEST.csv | [provenance/MIGRATION_MANIFEST.csv](provenance/MIGRATION_MANIFEST.csv) | 逐件权威台账：每文件一行，含 privacy/license/处置/核验状态列 |
| PRIVACY_REVIEW.md | [provenance/PRIVACY_REVIEW.md](provenance/PRIVACY_REVIEW.md) | 隐私与密钥审查终稿：扫描范围与规则、各源命中、脱敏决策、敏感资产处置、遗留风险与总体判定 |
| LICENSE_AND_RIGHTS.md | [provenance/LICENSE_AND_RIGHTS.md](provenance/LICENSE_AND_RIGHTS.md) | 许可与权利登记：双许可适用范围、权利归属、逐域权利落位与待决清单 |
| EVIDENCE-INDEX.md | [provenance/EVIDENCE-INDEX.md](provenance/EVIDENCE-INDEX.md) | 对外证据索引：旧索引逐条改写为公开路径，逐条实测存在性 |

---

## 5. charts/ 图表索引

> 图表落点 `benchmark/charts/`。纪律：数据来自真实 CSV/JSON，保留绘图脚本与源数据引用，不截断坐标轴、不手工修饰数值、不同口径不混合同轴。

| 件 | 路径 | 一句话 |
| --- | --- | --- |
| chart-A-d1d7.svg | [benchmark/charts/chart-A-d1d7.svg](benchmark/charts/chart-A-d1d7.svg) | 图表 A：七个评测方向（D1–D7）的方向覆盖条图——逐柱标注口径出入与缺口，Y 轴自 0 起、柱高不相加（各方向样本可重叠） |
| chart-context-retention.svg 等 | `benchmark/charts/` | 根 README 选用的机制效果图（长程保持 / 上下文成本 / 契约质量）与生成脚本 `generate_chart_a.py`、`generate_selection_charts.py`；源数据与口径决定见 `charts-data.md` |
| charts-data.md | [benchmark/charts/charts-data.md](benchmark/charts/charts-data.md) | 图表数据底稿：源数据与口径决定（逐方向回填值/审计原值/出入登记）；Gold 三代口径对比与保管账本对照以表格呈现——三代口径分母不同不画同轴图 |

---

## 6. 外部读者阅读路径建议

### 6.1 面试官（第一次打开仓库，20 分钟级）

1. [README.md](README.md)——研究入口与核心文档表；
2. [RESEARCH_OVERVIEW.md](RESEARCH_OVERVIEW.md)——研究问题、动机、方法与主要贡献的一页入口；
3. [PAPER.md](PAPER.md) §0 TL;DR——研究一句话、主结果与口径说明；
4. [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) §1–§2——三层结构与产品真实调用链；
5. [benchmark/STATISTICS.md](benchmark/STATISTICS.md) §一——八个公开口径数字与边界；
6. [refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md)——挑感兴趣的判决点看一句话结论；
7. [refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md)——研究围绕哪些真实故障族构建；
8. 需要看证据时再进 `benchmark/`（题库）与 `experiments/`（runs 批次登记表）。

### 6.2 研究者（要核验或复用，半天级）

1. [REPRODUCIBILITY.md](REPRODUCIBILITY.md)——环境怎么搭、入口在哪、数据怎么读、哪些能复现哪些不能（smoke test 实测记录以其 §7 为最新）；
2. [benchmark/STATISTICS.md](benchmark/STATISTICS.md)——全部口径、恒等式与使用纪律（数字不相加、不互改）；
3. [DATASET_DOCUMENTATION.md](DATASET_DOCUMENTATION.md)（数据集文档，与 STATISTICS.md 口径页逐项一致）+ `dataset/` 结构摘要与合成样本说明；
4. [evaluation/](evaluation/)——评委手册版本链与主报告；`evaluation/QUALITY_FIRST_MAIN_REPORT_V1.md` 起读；
5. [experiments/runs-registry.csv](experiments/runs-registry.csv)——选批次；run 目录典型内容与 layout 三形态见本文件 §1 第 5 行；
6. [refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md) → 判决原件（`refs/experiment_protocol_20261023/`）；
7. [tools/](tools/)——重评分/统计/审计脚本；环境依赖见 `tools/requirements.txt`；
8. [arena/](arena/)——框架代码与隔离包；平铺导入约束见 REPRODUCIBILITY.md §2。

### 6.3 维护者（要更新或审计资产，按需）

1. 本文件——先定位区域；
2. [provenance/PROVENANCE-INDEX.md](provenance/PROVENANCE-INDEX.md) → `provenance/SOURCE_MAP.md` 与 `provenance/MIGRATION_MANIFEST.csv`——逐件出处与处置的权威台账；
3. [refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md)——登记新失败族**只改 refs 侧**（`arena/` 侧是指针件）；
4. [candidates/VERSION_LINEAGE_V2.json](candidates/VERSION_LINEAGE_V2.json)——候选谱系与身份（候选 Prompt 不覆盖产品 assets）；
5. [archive/](archive/)——历史归档各束索引，归档新增须附索引说明；
6. [benchmark/charts/charts-data.md](benchmark/charts/charts-data.md)——图表数据底稿与复跑指引（改图先改源数据，不手工修饰 SVG）；
7. [REPRODUCIBILITY.md](REPRODUCIBILITY.md) §10 待实测清单——发布前逐项收口后更新状态；
8. 数字变动时同步 `benchmark/STATISTICS.md`（唯一口径页）与本文相应行。

---

> 维护说明：本文件为区级/件级导航权威件；任何件增删或数字变动，按实树实测更新相应行。
