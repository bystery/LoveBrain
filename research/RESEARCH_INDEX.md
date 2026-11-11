# research/RESEARCH_INDEX.md —— 研究资产全索引

> 定位：`research/` 十二区全量导航件——回答"有什么、在哪、从哪读起"。区级导览与件级索引均以迁移 worktree 实测落盘为准。
> 快照口径：文件数与件数为 **2026-11-12 05:24 实测**；研究文档九件套已全部落盘（见 §2）。全树落盘 74,131 件（不含本文件），本文件写入后为 74,132 件。
> 完整性口径 = 文件数 + 字节量（与 [research/README.md](README.md) 头部、[provenance/SOURCE_MAP.md](provenance/SOURCE_MAP.md) §13 一致）；本文件零哈希值、零真实密钥、零真实聊天内容、零内部任务编号；文中路径均为本仓相对路径。

---

## 1. 十二区逐区导览

> 分区框架与 [research/README.md](README.md) §1 一致：11 个目录区 + `PAPER.md` 共十二区；`README.md`（总览与环境说明）与 `RESEARCH_INDEX.md`（本文件）为根面导览件，不计入十二区。

| # | 区域 | 一句话说明 | 文件数 | 关键文件（实测） |
| --- | --- | --- | ---: | --- |
| 1 | `benchmark/` | 题库与评测基准：全量剧情库、Gold 侧车双轨、公开开发集、rubric 两版、统计口径页与图表落点 | 2,479 | `STATISTICS.md`（公开统计口径定稿）；`rubric.md` + `rubric_v0_6_draft.md`（现行版与历史草案并存）；`core_set.json` / `full_set.json` / `test_suite.json` / `dims_map.json` / `dev_pool_registry_20261026.json`；`stories_v2/`（剧情库分组目录 + `GOLD_SCHEMA.md`/`GOLD_SCHEMA_V2.md` + `GOLD_SIDECAR/` 权威侧车 + 远端快照对照轨）；`b_line/`（B 线 24 篇封存剧集包与清单件）；`episodes/`（149 个公开开发集剧集）；`anchors/`、`annotations/`、`cases/`、`tasks/`、`releases/`、`media/`、`dev_pilot_v02/`、`dev_expansion_v01/`；`charts/`（见 §5） |
| 2 | `dataset/` | 母数据集公开面：结构摘要 + 合成样本（原件为真实运行数据，不出站，口径见 README §3） | 94 | `manifests/README.md`、`manifests/STRUCTURAL-SUMMARY.md`（结构与字段骨架摘要，不含正文）；`synthetic_samples/README-synthetic.md`、`synthetic_samples/SYNTHETIC_MANIFEST.json`、`synthetic_samples/synthetic_kb_01`～`synthetic_kb_05/`（AI 学习真实结构后生成的全合成样本，三层合成标记） |
| 3 | `candidates/` | 参赛候选全保留（不止冠军）+ 版本谱系：champion / historical / negative_control / ablation / template / reference 六类 | 527 | `VERSION_LINEAGE_V2.json`（谱系登记）；`window_expand_b_norecap_uc/` 与 `window_expand_b_norecap_guard/`（双域冠军，含 `IDENTITY.md` / `MECHANISM.md` / `params.json`）；`reference_product_v1/`（参考基线）；`neg_a_amnesia/` `neg_b_dumper/` `neg_c_service/`（阴性对照）；`ablate_*` 与 `_replay_tmp_off_knowledge_block/`（消融对照）；`_template_mechanism/`（模板，不列为已验证候选） |
| 4 | `evaluation/` | 评测与判决版本链：各状态全保留（含错误版成对留档，不删改历史） | 97 | `QUALITY_FIRST_MAIN_REPORT_V1.md`（主报告）；`P06_RESULTS.md`、`P07_PILOT_RESULTS.md`；`JUDGE_MANUAL_V1` → `V1_1` → `V2_0_A3`（评委手册版本链）；`A3_CALIBRATION_ANALYSIS_V1` 版本链（含 buggy/interim 留档）；`C4_FOUR_ARM_REVIEW_PACK_V1.md`；`TASK_MAP_AND_DATASET_V1.md`、`GOALS_AND_SCOPE_V1.md`、`BACKLOG_M1_TO_M2_20261026.md`；`audits/hc2/` |
| 5 | `experiments/` | 实验落盘：runs 批次 + 隔离测试 + 原始日志索引（逐批次登记，批次级口径见登记表） | 50,621 | `runs-registry.csv`（逐批次 14 列登记表）；`runs/`（逐批次实验目录，代表性批次体量见 README §2.11）；`isolated_runs/`（隔离/授权测试材料，附隔离说明）；`raw_logs/INDEX.json`（原始日志索引；原件按 run 边界分卷走 Release/LFS，不进 git） |
| 6 | `arena/` | 考场运行时框架：纯标准库 Python，含隔离子包；已知故障门禁在本域挂引用件 | 31 | `lb_runner.py`（运行时 v2 入口）、`lb_scorer.py`（现行评分器 v0.7）、`lb_gateway.py`（统一网关与 API 配置）、`lb_assembly.py`（上下文装配）、`lb_mechanism.py`、`gold_sidecar.py`（Gold 侧车加载）、`protocol_public.py`、`scorer.py`（前置代际评分器，并存保留）；`isolation/`（10 个 .py：可信控制器/授权预算/凭据加密等）；`KNOWN_FAILURES.md`（指针件，正文唯一权威在 `refs/`）；`harness_loader.md`；`pyproject.toml`（环境声明） |
| 7 | `refs/` | 判决书、实验协议、独立评审、失败分析与基线材料（保留源仓历史相对层级） | 19,680 | `KNOWN_FAILURES.md`（已知故障回归门禁，唯一权威副本）；`VERDICT-INDEX.md`（53 份判决文件全量目录索引，见 §3）；`REFERENCES.md`；`experiment_protocol_20261023/`（实验协议与判决主体，占本区体量约 95%，大 JSONL 单列逐件登记）；`arena_v1/`（含 `BASELINE_PARITY.md` 保真边界）；独立评审目录四组；`BASELINE_CAPABILITY.md`、`BASELINE_FIDELITY.md`、`HARNESS_IO_SPEC.md`、`BUDGET_PLAN.md` |
| 8 | `archive/` | 历史归档：历史资产、历史工作指南、移植原型，每束附索引说明（不是无说明垃圾堆） | 79 | `historical_assets/README.md`（归档索引）、`historical_assets/OBJECTIVE.md`、`historical_assets/RUN_STATE.json`、`historical_assets/20261107_root_cleanup/MANIFEST.md`（含 11 个历史交付快照包）；`historical_worker_guides/INDEX.md`（历史指南索引）；`integration_preview_20261025/`（历史移植原型：`KOTLIN_INTEGRATION_MAP.md` 等 6 件，标注"不代表当前生产接线"） |
| 9 | `provenance/` | 迁移证据：来源映射总账、逐件机读台账、隐私与许可审查、对外证据索引 | 6 | 五件文档 + 导航件，详见 §4 |
| 10 | `tools/` | 评测/审计/重评分/统计工具与测试脚本（含在研活跃子区） | 508 | `requirements.txt`（环境声明）；`benchmark_v1/`（在研活跃区，33 个 .py）；`exam_batch_configs/`（批量配置）；`transcript_miner.py`；`test_*`（67 个测试脚本）；约 150+ 一次性探针按历史归档口径保留原位 |
| 11 | `architecture/` | 架构文档落点（以 `arena/` 实际代码与 refs 材料撰写） | 1 | `ARCHITECTURE.md`（正式架构总览，见 §3） |
| 12 | `PAPER.md` | 研究论文稿：源件逐字原样迁移 + 卷首迁移译注十条（口径对照） | 1 | 见 §3 |

合计：十一目录区 + `PAPER.md` = **74,124 件**（2026-11-12 05:24 实测；根面另有 `README.md` 总览件与研究文档九件套其余 6 件（见 §2），不属十二区，全树落盘 74,131 件不含本文件；另有少量源锁定件仅登记未复制，以 `provenance/MIGRATION_MANIFEST.csv` 为准）。

---

## 2. 研究文档九件套索引

> 九件套 = 研究文档集九件（本文件为其一）。**落盘状态（2026-11-12 05:24 实测）：9/9 全部落盘。**

| # | 件 | 路径 | 状态 | 一句话 | 覆盖范围 |
| --- | --- | --- | --- | --- | --- |
| 1 | RESEARCH_INDEX | `research/RESEARCH_INDEX.md` | **已落盘**（本文件） | 全索引：十二区导览 + 文档件索引 + 阅读路径 | 全部研究资产 |
| 2 | RESEARCH_OVERVIEW | `research/RESEARCH_OVERVIEW.md` | **已落盘** | 研究总览一页入口 | 研究问题、动机、整体方法与主要贡献；三仓库体系、机制三主线叙事、数据集与评测口径链、实验结果概述（限定语整句）、公开资产导览、诚实边界 |
| 3 | PAPER | `research/PAPER.md` | **已落盘** | 研究论文稿 | 同一模型服务下完整聊天 harness 的可验证迭代：被测对象边界、数据集、30+ 次真实同场对照、分域冠军链与全部判决账本（含未胜出尝试）；卷首译注登记口径版本 |
| 4 | BENCHMARK_EVALUATION | `research/BENCHMARK_EVALUATION.md` | **已落盘** | 评测体系与基准评估 | 指标、计算口径、数据切片与评分方式；汇总登记件，判决定位判决书原文件、不复写不重算 |
| 5 | CORE_MECHANISMS | `research/CORE_MECHANISMS.md` | **已落盘** | 核心机制详解与判决映射 | 各机制解决什么问题、怎么工作、有哪些判决支撑、处于产品实现路径哪一层；与 ARCHITECTURE.md 机制研究叙事节共用同一套证据 |
| 6 | DATASET_DOCUMENTATION | `research/DATASET_DOCUMENTATION.md` | **已落盘** | 数据集文档 | 数据规模、构造方式、七个专项、Gold 双轨与局限、B 线口径与合成样本；与 benchmark/STATISTICS.md 口径页逐项一致 |
| 7 | FAILURE_ANALYSIS | `research/FAILURE_ANALYSIS.md` | **已落盘** | 失败机制保留与未入选说明 | 已知失败族与判决状态、未入选候选逐项、负结果保留原则、runs 域断跑登记；全部条目引自判决原文件，不翻案 |
| 8 | REPRODUCIBILITY | `research/REPRODUCIBILITY.md` | **已落盘** | 复现说明 | 环境怎么搭、入口在哪、数据怎么读、哪些能复现哪些不能；离线 smoke test 实测进展以该文为最新记录 |
| 9 | ARCHITECTURE | `research/architecture/ARCHITECTURE.md` | **已落盘** | 架构总览 | 真实生产链路、模块职责与状态归属（详见 §3） |

---

## 3. 六件独立件索引

> 六件各自独立成篇、可直接对外阅读；其中 ARCHITECTURE 与 PAPER 同时属于九件套（§2 已列，此处给独立件视角）。

| 件 | 路径 | 一句话 | 覆盖范围 |
| --- | --- | --- | --- |
| ARCHITECTURE.md | `research/architecture/ARCHITECTURE.md` | 正式架构总览：项目三层结构（产品/研究/治理）、产品生产调用链、Arena 研究架构、接线三态红线（不把计划画成已启用） | 产品树以实树实测为证据基准、研究树以落盘快照为准；全部引用限本仓可核验；含机制研究叙事 |
| PAPER.md | `research/PAPER.md` | 研究论文稿（源件逐字迁移，零改动）+ 卷首迁移译注十条 | 研究叙事与主结果全文；三条版本标签（数据集口径已被 v2 重建取代、B 线叙述为时点记录、判决数字以生效版判决书为口径）+ 两条实质差异注记 + 五条简要注记；**引用正文数字前先读译注** |
| STATISTICS.md | `research/benchmark/STATISTICS.md` | `benchmark/` 公开统计口径定稿：八个公开口径数字逐数字版本化（值/口径/统计时间/来源/边界），恒等式闭合与使用纪律 | 数字与 `research/README.md` §2.4 权威口径链、§2.5 Gold 三代口径交叉核对一致；是 benchmark 域统计数字的唯一口径页 |
| KNOWN_FAILURES.md | `research/refs/KNOWN_FAILURES.md`（唯一权威副本） | 已知故障回归门禁 v0.2：9 个真实事故失败族，任何候选不得重新引入 | 权威正文在 `refs/`；`research/arena/KNOWN_FAILURES.md` 为 harness 代码域挂的指针件（不复制正文，防双源漂移），两者双落位登记见 `provenance/PROVENANCE-INDEX.md` |
| VERDICT-INDEX.md | `research/refs/VERDICT-INDEX.md` | 判决原文件目录索引：53 份名称含 VERDICT 的 .md/.json 逐一登记（大小/mtime/一句话结论/主线归入） | 覆盖长剧探针判决书目录与全局持续迭代判决目录等全部判决落点；机制主线三分法定义与历史归档口径在卷首 |
| README.md | `research/README.md` | 研究资产总览与环境说明十四项 | 十二区导览（§1）、运行环境与入口（§2）、dataset 三口径（§3）、待实测清单（§4）；是外部读者的第一入口 |

---

## 4. provenance/ 五件索引

> `provenance/` 共 6 个文件 = 五件文档 + 一页导航件 [provenance/PROVENANCE-INDEX.md](provenance/PROVENANCE-INDEX.md)（各件职责、关键章节指针与互相引用关系**以该导航件为准**，此处只给入口）。版本与状态以各件卷首版本行为准。

| 件 | 路径 | 一句话（引 PROVENANCE-INDEX.md） |
| --- | --- | --- |
| SOURCE_MAP.md | `provenance/SOURCE_MAP.md` | 迁移登记总账：资产从哪个仓库/分支来、落到哪里、什么不迁、数字对没对上（字段口径、排除清单、数字对账、未决事项均在此） |
| MIGRATION_MANIFEST.csv | `provenance/MIGRATION_MANIFEST.csv` | 逐件权威台账：每文件一行（22+1 字段），含 privacy/license/处置/核验状态列 |
| PRIVACY_REVIEW.md | `provenance/PRIVACY_REVIEW.md` | 隐私与密钥审查终稿：扫描范围与规则、各源命中、脱敏决策、敏感资产处置、遗留风险与总体判定 |
| LICENSE_AND_RIGHTS.md | `provenance/LICENSE_AND_RIGHTS.md` | 许可与权利登记（草稿，待终审定稿）：双许可适用范围、权利归属、逐域权利落位与待决清单 |
| EVIDENCE-INDEX.md | `provenance/EVIDENCE-INDEX.md` | 对外证据索引：旧索引逐条改写为公开路径，逐条实测存在性 |

---

## 5. charts/ 图表索引

> 图表落点 `benchmark/charts/`（3 件，2026-11-12 落盘）。纪律：数据来自真实 CSV/JSON，保留绘图脚本与源数据引用，不截断坐标轴、不手工修饰数值、不同口径不混合同轴；全程零哈希登记。

| 件 | 路径 | 一句话 |
| --- | --- | --- |
| chart-A-d1d7.svg | `benchmark/charts/chart-A-d1d7.svg` | 图表 A：七个评测方向（D1–D7）的方向覆盖条图——按最终回填口径绘制，逐柱标注口径出入与缺口，Y 轴自 0 起、柱高不相加（各方向样本可重叠） |
| generate_chart_a.py | `benchmark/charts/generate_chart_a.py` | 图表 A 生成脚本：纯 Python 标准库、程序化解析源 CSV，解析失败即退出；源数据未变则重跑输出字节不变 |
| charts-data.md | `benchmark/charts/charts-data.md` | 图表数据底稿：图表 A 的源数据与口径决定（逐方向回填值/审计原值/出入登记）；图表 B（Gold 三代口径对比表）与图表 C（保管账本补登前后对照表）以 Markdown 表格呈现——三代口径分母不同不画同轴图，表格是最诚实形式；文末逐源文件清单与数据截至时间 |

---

## 6. 外部读者阅读路径建议

### 6.1 面试官（第一次打开仓库，20 分钟级）

1. [research/README.md](README.md) §1——十二区导览，先知道有什么；
2. [research/RESEARCH_OVERVIEW.md](RESEARCH_OVERVIEW.md)——研究问题、动机、方法与主要贡献的一页入口；
3. [research/PAPER.md](PAPER.md) §0 TL;DR + 卷首译注——研究一句话、主结果与口径版本；
4. [research/architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) §1–§2——三层结构与产品真实调用链；
5. [research/benchmark/STATISTICS.md](benchmark/STATISTICS.md) §1——八个公开口径数字与边界；
6. [research/refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md)——挑感兴趣的判决点看一句话结论；
7. [research/refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md)——研究围绕哪些真实故障族构建；
8. 需要看证据时再进 `benchmark/`（题库）与 `experiments/`（runs 批次登记表）。

### 6.2 研究者（要核验或复用，半天级）

1. [research/REPRODUCIBILITY.md](REPRODUCIBILITY.md) 与 [research/README.md](README.md) §2——环境怎么搭、入口在哪、数据怎么读、哪些能复现哪些不能（smoke test 实测进展以 REPRODUCIBILITY.md 为最新记录）；
2. [research/benchmark/STATISTICS.md](benchmark/STATISTICS.md)——全部口径、恒等式与使用纪律（数字不相加、不互改）；
3. [research/DATASET_DOCUMENTATION.md](DATASET_DOCUMENTATION.md)（数据集文档，与 STATISTICS.md 口径页逐项一致）+ `dataset/` 结构摘要与合成样本说明；
4. [research/evaluation/](evaluation/)——评委手册版本链与主报告；`evaluation/QUALITY_FIRST_MAIN_REPORT_V1.md` 起读；
5. [research/experiments/runs-registry.csv](experiments/runs-registry.csv)——选批次；run 目录典型内容与 layout 三形态见 `README.md` §2.11；
6. [research/refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md) → 判决原件（`refs/experiment_protocol_20261023/`）；
7. [research/tools/](tools/)——重评分/统计/审计脚本；环境依赖见 `tools/requirements.txt`；
8. [research/arena/](arena/)——框架代码与隔离包；跨目录依赖与平铺导入约束见 `README.md` §2.2。

### 6.3 维护者（要更新或审计资产，按需）

1. 本文件——先定位区域；
2. [provenance/PROVENANCE-INDEX.md](provenance/PROVENANCE-INDEX.md) → `provenance/SOURCE_MAP.md`（§12 四步复核法）与 `provenance/MIGRATION_MANIFEST.csv`——逐件出处与处置的权威台账；
3. [research/refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md)——登记新失败族**只改 refs 侧**（`arena/` 侧是指针件，见 §3）；
4. [research/candidates/VERSION_LINEAGE_V2.json](candidates/VERSION_LINEAGE_V2.json)——候选谱系与身份（候选 Prompt 不覆盖产品 assets）；
5. [research/archive/](archive/)——历史归档各束索引（`historical_assets/README.md`、`historical_worker_guides/INDEX.md`），归档新增须附索引说明；
6. [research/benchmark/charts/charts-data.md](benchmark/charts/charts-data.md)——图表数据底稿与复跑指引（改图先改源数据，不手工修饰 SVG）；
7. [research/README.md](README.md) §4 待实测清单——发布前逐项收口后更新状态；
8. 数字变动时同步三处：`benchmark/STATISTICS.md`（口径页）、`provenance/MIGRATION_MANIFEST.csv`（台账）、本文件 §1 计数（以实测重跑为准）。

---

> 维护说明：本文件为快照式索引——九件套已全部落盘（2026-11-12）；后续任何件增删或数字变动，按实树重测更新 §1 计数与 §2/§3 状态列；文件数一律以 `ls`/`find` 实测为准，不抄记忆值。
