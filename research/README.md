# LoveBrain 研究目录（research/）

本目录是 LoveBrain 长程对话记忆与上下文管理研究的全部公开资产：合成题库与 Gold 标注、候选机制、实验记录与判决书、评测与复现工具。研究方法一句话：把机制候选放入独立研究考场（`arena/`），在固定模型服务下做连续对话对照与消融，以判决制收口（KEEP / MERGE / 淘汰 / 未判定如实登记），只有获得判决支持的机制才移植进 Android 产品。

**数据规模（权威口径，详见 [benchmark/STATISTICS.md](benchmark/STATISTICS.md)）：** 317 个唯一故事 / 299 个 Gold 覆盖 episode / 4,082 轮对话 / 9,646 条可核验原子断言。

## 核心文档

| 文档 | 回答什么问题 |
| --- | --- |
| [RESEARCH_OVERVIEW.md](RESEARCH_OVERVIEW.md) | 研究了什么、为什么、主要贡献（一页入口） |
| [CORE_MECHANISMS.md](CORE_MECHANISMS.md) | 机制怎么工作、为何有效、判决映射 |
| [DATASET_DOCUMENTATION.md](DATASET_DOCUMENTATION.md) | 数据怎么构造、数量、例子、标注与局限 |
| [BENCHMARK_EVALUATION.md](BENCHMARK_EVALUATION.md) | 如何评估、Baseline、可比性规则 |
| [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) | 系统模块与真实调用关系 |
| [REPRODUCIBILITY.md](REPRODUCIBILITY.md) | 环境怎么搭、入口在哪、哪些能复现哪些不能 |
| [PAPER.md](PAPER.md) | 完整研究总结（论文稿，文末附历史口径附注） |
| [FAILURE_ANALYSIS.md](FAILURE_ANALYSIS.md) | 已知失败族与未入选候选 |

## 目录导览（十二区）

| 区域 | 说明 |
| --- | --- |
| [benchmark/](benchmark/) | 题库与评测基准：合成故事库 stories_v2、Gold 侧车双轨、公开开发集 episode、rubric 两版与统计口径页。 |
| [dataset/](dataset/) | 母数据集公开面：结构摘要与合成样本（原件为真实产品数据，永不出站）。 |
| [candidates/](candidates/) | 全部候选目录与版本谱系（分域冠军、历史版本、阴性对照、消融、模板、参考件全保留，含不晋升者）。 |
| [evaluation/](evaluation/) | 评审判决版本链（FINAL / SUPERSEDED / INTERIM / BUGGY 等全保留，错误版不删）。 |
| [experiments/](experiments/) | 实验账本：runs 批次目录、逐批次登记表（runs-registry.csv）、隔离测试与原始日志索引（原始日志原件走 Release/LFS）。 |
| [arena/](arena/) | 研究考场运行时框架：纯标准库 Python，含网关、计分器、Gold 加载、Judge 器械族与隔离包。 |
| [refs/](refs/) | 判决书、实验协议、独立评审与失败分析（[VERDICT-INDEX.md](refs/VERDICT-INDEX.md) 为 53 份判决的入口索引）。 |
| [archive/](archive/) | 历史归档：移植原型、交付快照与历史工作指引副本（均附索引与 historical 标注）。 |
| [provenance/](provenance/) | 迁移证据：来源映射、逐文件迁移清单、隐私与许可审查。 |
| [tools/](tools/) | 评测/审计/重评分工具与测试（唯一第三方依赖 Pillow）。 |
| [architecture/](architecture/) | 架构文档：真实生产链路、研究架构与接线状态。 |
| [PAPER.md](PAPER.md) | 研究论文稿。 |

逐区关键文件索引见 [RESEARCH_INDEX.md](RESEARCH_INDEX.md)。

## 阅读路径

- **第一次了解（约 20 分钟）：** [RESEARCH_OVERVIEW.md](RESEARCH_OVERVIEW.md) → [PAPER.md](PAPER.md) §0 TL;DR → [STATISTICS.md](benchmark/STATISTICS.md) §一 → 在 [refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md) 挑感兴趣的判决看结论。
- **核验或复用：** [REPRODUCIBILITY.md](REPRODUCIBILITY.md)（环境、入口、复现范围与 smoke test 实测记录）→ [STATISTICS.md](benchmark/STATISTICS.md)（全部口径、恒等式与使用纪律）→ [experiments/runs-registry.csv](experiments/runs-registry.csv)（选批次）→ `refs/` 判决原件。

## 快速入口

- **运行环境与依赖：** [REPRODUCIBILITY.md](REPRODUCIBILITY.md) §1–§2（Python 3.12；`arena/` 零第三方依赖，`tools/` 仅需 Pillow；API Key 走环境变量占位符，见同文 §5）。
- **数据统计口径：** [benchmark/STATISTICS.md](benchmark/STATISTICS.md)（唯一口径页；历史口径并列登记，不混用统计单位）。
- **数据隐私边界：** KB 快照原件（真实聊天导出）本地保留、永不出站；公开面为结构摘要（零正文）+ 合成样本，见 [DATASET_DOCUMENTATION.md](DATASET_DOCUMENTATION.md) §8。
- **B 线 24 篇状态：** 已公开的普通对照资产，不再是未曝光盲测；见 [benchmark/b_line/B_LINE_README.md](benchmark/b_line/B_LINE_README.md) §0。
- **机制接线状态：** LIVE_AUTO 分域接线为 `closeout-20261106` 产品分支状态；本研究分支 `app/` 代码不含该接线（默认 OFF），见 [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) 卷首分支状态声明。
- **迁移来源与隐私审查：** [provenance/](provenance/)（SOURCE_MAP、MIGRATION_MANIFEST、PRIVACY_REVIEW、LICENSE_AND_RIGHTS）。
