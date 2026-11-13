# 评测体系与基准评估（BENCHMARK_EVALUATION）

> 状态：2026-11-12 撰写（迁移 worktree 快照）。本文回答评测文档必须回答的问题：**指标、计算口径、数据切片和评分方式**。
> 纪律声明（全文有效）：① 全部数字带版本标签（哪个文件、哪个版本、哪个日期口径）；② 完整性口径一律为**文件数 + 字节量**；③ 零真实聊天内容、零密钥、零真实端点（模型与网关字段落盘于各 run 的 manifest，正文不转写）；④ B 线（`benchmark/b_line/`，24 篇）按**普通研究资产**登记；该批次已随公开分支公开，自公开时起为**公开 Benchmark / 历史 B 线，不再是未曝光盲测集**（状态标注见 `benchmark/b_line/B_LINE_README.md` §0）；⑤ 不同条件下的结果不拼接比较（§3 红线）。本文为汇总登记件：判决定位判决书原文件，本文不复写、不重算。
> 数据来源：迁移落盘 worktree（`research/` 各域实存树）+ 迁移核对底稿（2026-11-12 只读核对件：五层协议逐层核对、evaluation 域 98 文件分类、runs 逐 run 登记、benchmark 域对账）。全部输入只读。

## 0. 一页总览

| 节 | 回答什么 | 一句话结论 |
| --- | --- | --- |
| §1 | 评分方式与指标 | rubric v0.5 五层协议（5/5 层已核对）；层 1 计分器 lb_scorer v0.7 现行权威，前置代际 scorer.py v3 并存留档 |
| §2 | Judge 校准状态 | 手册版本链齐；校准本体 P1–P4 全部 PENDING；运行目录冲突定性为版本滞后 |
| §3 | 可比性规则 | 不同条件不拼分（红线）；六条实操规则 |
| §4 | 产物状态分类 | 七状态覆盖 98 文件（UNKNOWN=0）；特殊后缀非垃圾，错误版不删 |
| §5 | 四臂实验 | selection_v1 四臂齐全，1968=1968 计划=实际，已被 guards v2 重评分 |
| §6 | holdout 进度 | holdout_v1 部分执行 889/6,240（≈14.2%，2026-11-12 实测口径），判决不存在 |
| §7 | 公开索引 | evaluation 域逐文件状态明细（分类底稿 CSV 未随公开树落盘，见 §7.1）+ 各域入口与限定语 |

---

## 1. 评分五层协议（rubric v0.5 与计分器代际）

### 1.1 协议版本链

评分协议规范文档为 `research/benchmark/rubric.md`（**v0.5**，13,862 B，2026-10-23 落版）。历史沿革（rubric.md v0.5 头注原样保留，可回退不覆盖）：

| 版本 | 要点 |
| --- | --- |
| v0.2 | 新增 C10 意图契约；记忆四层降为诊断附录；晋级流程新增 HITL 步骤 |
| v0.3（2026-10-17） | C0 契约按候选声明校验、新增 C11 记忆污染、成本退出晋级门禁（旧选择） |
| v0.4（2026-10-17） | 新增 C12 物理可行性禁区、C13 风格趋同（无 gold 依赖、全题生效） |
| **v0.5（2026-10-23，现行）** | ① 取消"程序分 = 晋级唯一依据"，改多目标并列门禁（质量/污染/成本/时延/可维护性）；② 恢复成本/时延为并列门禁；③ note_lcs / style_diversity 阈值按证据分层（note_lcs 分母改 max(len)，命中阈值 0.6 才计"复读"）；④ gold={} 覆盖诚实登记；⑤ 首选/备选分审落到 LAYERED_SCORE；⑥ 未校准 judge 只辅助 |
| v0.6 草案 | `research/benchmark/rubric_v0_6_draft.md`（2026-11-11，9 KB，未跟踪历史草案）原样留档 |

协议变更纪律（rubric v0.5 头注）：协议可迭代，但必须单独版本化并用基线候选重评——**协议变更 ≠ 候选进步**。

### 1.2 五层表

直接源为 rubric.md v0.5 §1 层表（表题沿用历史名"三层评估架构"，实列五层；2026-11-12 逐层核对 **5/5 层与源表一致**）：

| 层 | 评什么 | 谁来评 | 在分数中的角色 |
| --- | --- | --- | --- |
| 层 1 程序规则 | 输出契约、source_ids、状态断言、LCS、关键词、AI 味词表 | lb_scorer.py（确定性） | 契约与红线检查，可触发 veto；v0.5 起不再独占晋级门禁 |
| 层 2 语义与互动 | 事实/人物/意图/事项状态、自然度、话题污染语义档、可发送性 | 人工监督（现阶段）+ 校准后 judge 辅助 | 并列门禁：语义失败率、污染率、严重问题备选比例 |
| 层 3 用户偏好 | 是否愿意真的发送、语气与个人口味 | 用户锚点 + 小范围实测 | 最终裁决；worker/judge 不代投 |
| 层 4 资源与等待 | 全链真实用量、调用、时延、失败 | 网关 usage + 考场计时 | 并列门禁：token / 延迟 / attempts；失败不从分母删除 |
| 层 5 可维护性 | 状态与规则是否重复、用户纠正次数、故障恢复难度 | 报告式；无数值阈值 | 报告；不擅自设硬门禁 |

### 1.3 层 1 计分器：v0.7 现行权威 + 前置代际并存

- **现行权威：`research/arena/lb_scorer.py`，v0.7**（演化 v0.4→v0.7；C0–C15 检查点、veto 族、no_reply 评分绕过修复；经 `protocol_public` re-export 同一函数对象）。
- **前置代际并存保留：`research/arena/scorer.py`（v3**，读管家账本 + 回合回复文件的上一代评分器）——两代都迁移、标注代际关系，不互相冒充。
- tools 侧评分面：`exam_layered_score`（离线分层）、`tools/benchmark_v1/universal_scorer`（通用评分层 + 语义守卫，见 §5.4）、`rescore_batch` / `rescore_offline`。
- 评分文件形态：run 内回合级 `scoring.json`（约 2.8 万份）与修正版 608 件（修正版登记见 `refs/KNOWN_FAILURES.md` FK-SCORE-01：W19-01 离线重评分，原 scoring.json 未动）。**重算必须与产出该文件的评分器版本对齐；"批次↔评分器版本"对照表待登记（T7），未登记前不声称任意历史 scoring 文件都能用 v0.7 重算出一致结果。**
- 现状佐证（smoke-test-run1，2026-11-12）：lb_scorer 模块级导入成功（源仓库口径）；231 个 run 的 scoring.json 抽样可读，score10/veto/checks 结构与 lb_scorer `_finish` 返回结构一致（runs 逐 turn `manifest.json` 含 model/case_id/score10/veto/tokens/gateway_calls 字段）。

### 1.4 评估纪律六条

1. **Judge 未校准的维度不能单独判死**（rubric v0.5 头注⑥；AGENTS.md §3：judge 评审团永不单独触发 veto）。
2. **severe 结论需要原文证据、引文硬校验和人工复核**（B3 亲读实践：判决书明示亲读口径覆盖 judge 原始口径；`research/evaluation/B3_PRIMARY_READ_E006/E007/E008/E012_V1.md` 四分片为亲读留痕）。
3. **首选回复和备选回复分开统计**（LAYERED_SCORE 主字段 primary_semantic 与 backups_semantic 分别记录；`QUALITY_PANEL_SCORECARD_V2.json` 自述"denominators and primary/backup split corrected"，V1 为 SUPERSEDED 前版）。
4. **Gold 为空或断言不存在时记为未测，不能算通过**（gold={} 覆盖诚实登记；缺测表 `FIRST_CLASS_METRIC_NR_TABLE_V1.md`）。
5. **合法不回复不应被强制扣分**（decline_appropriateness 单独计分；合法不回只免风格/正文类检查）。
6. **用户愿不愿意发送不能由 AI 代填**（层 3；用户校准表 `USER_CALIBRATION_SHEET_V1` 16 组；`USER_W_SAMPLE_PACK_V1` 自述"未回收时 W 维度保持 unknown"）。

### 1.5 多目标判决语义（rubric v0.5 §1）

- 质量更好 + 严重错误/污染不增 + 成本/等待不恶化 → 明确收益；
- 质量在预登记可接受差异内相当 + 成本或等待实质下降 + 另一项不恶化 → 效率收益；
- 均分微升但严重问题/污染/成本明显增加 → **不是整体更好**；
- 某项收益换某项代价 → 报告交易，由用户裁定，不自动冠军。

---

## 2. Judge 校准状态（校准本体全部 PENDING，如实登记）

### 2.1 Judge 手册版本链

| 版本 | 状态 | 说明 |
| --- | --- | --- |
| `JUDGE_MANUAL_V1.md` | SUPERSEDED | 首版，被 V1.1 取代 |
| `JUDGE_MANUAL_V1_1.md` | FINAL | 204 批判词按 V1.1 留档，未改章节继续生效（V2.0-A3 头注声明） |
| `JUDGE_MANUAL_V2_0_A3.md` | FINAL | **A3 专用增量**（MANUAL-V2.0-A3，2026-10-26，适用 A3 小校准 128 次请求，`CALIBRATION_PACKET_A3_20261026/`）：行为分与事实风险分拆开、拆分粒度=主张×输出位置、受保护表达边界、位置独立硬约束。**与 V1.1 是并存口径而非取代关系**（evaluation 域分类核对，2026-11-12） |

配套机制链（evaluation 域分类，2026-11-12）：`IRR_AGREEMENT_V2_validation_frozen`=FINAL、`JUDGE_QUALITY_THRESHOLDS_DECLARED_V1`=FINAL、三个校准包（V1：8 文件；V1_1：13 文件；A3_20261026：5 文件）=FINAL。

### 2.2 A3 小校准与分析版本链（修复前版本逐字节相同已登记）

A3 分析五件全部原样保留、无一被覆盖（evaluation 域分类核对，2026-11-12；明细同 §4.1 状态分类表）：

| 文件 | 状态 | 要点 |
| --- | --- | --- |
| `A3_CALIBRATION_ANALYSIS_INTERIM.json` | INTERIM | interim:true，42 records 批在途；被 V1_2 取代 |
| `A3_CALIBRATION_ANALYSIS_V1.json` | BUGGY | 与 `.buggy-swap-not-normalized` 副本**逐字节相同**（cmp IDENTICAL）——V1 本身即带"换序未归一化"bug 的版本，两份共存是有意的成对留档；换序未归一化（detection 51/56、false_flag 27） |
| `A3_CALIBRATION_ANALYSIS_V1.buggy-swap-not-normalized.json` | BUGGY | 修复前显式留档（后缀即性质），不得覆盖 |
| `A3_CALIBRATION_ANALYSIS_V1_1.partial-norm.json` | BUGGY | 半修正中间态（仅 pref 字段异于 V1，不完整） |
| `A3_CALIBRATION_ANALYSIS_V1_2.json` | **FINAL** | 换序归一化修正定稿（detection 56/56、false_flag 15、action_range 归一）；现行口径 |

### 2.3 Judge 维度资格现状（W20-03 校准报告，2026-11-03）

`refs/experiment_protocol_20261023/long_probe_20261030/W20_03_JUDGE_CAL_REPORT_20261103.md` §2.1 资格表（2026-11-12 逐维核对一致）：

- goal / time / interaction / emotion：**有可辅助资格**（full 通过档）；
- factuality / initiative：**低证据**（样本单元不足）；
- attribution：**不可测试**（not-testable）；
- boundary / memory_use：**机械判 disqualified**——禁止单独判死。

配合纪律 1（§1.4 条 1）：未校准维度只能辅助、不能单方面判死；judge 校准上岗门槛为 ≥80% 人评一致率（20 道人评锚点 + trap 负样本，`lb_judge_calib.py` 设计口径）——该门槛在本批校准上**尚未实测达成**（见 §2.4）。

### 2.4 Phase 2 人工审核队列与 P1–P4 PENDING

`refs/benchmark_v1_20261107/phase2/HUMAN_REVIEW_QUEUE.json/.md` 建立 40 个人工审核锚点（quota=distribution_actual，2026-11-12 程序化计数）：T02×12、T03×8、T04×6、T01×4、T05×4、T07×2、T08×2、T09×1、T10×1；来源构成 wave4_dev 35 + old_exposed_T02 4 + twin_T10-G1 1。

`JUDGE_CALIBRATION_REPORT.md` §5（2026-11-08 交付时口径）PENDING 清单——**四项全部 PENDING，无任何已完成校准结论**：

| 项 | 内容 | 状态 |
| --- | --- | --- |
| P1 | 用户填锚（40 锚 fill_status 全=PENDING_USER，已填 0） | **PENDING** |
| P2 | judge 对锚点位跑分（须待队列刷新真实回复后进行） | **PENDING** |
| P3 | 人机一致率计算（kappa/简单一致率，依赖 P1+P2） | **PENDING** |
| P4 | 各维度"解除未校准/维持未校准"判定（依赖 P3＋用户确认阈值） | **PENDING** |

底线：**在 P1–P4 完成前不能宣称 Judge 已完成新一轮校准**；用户填锚是唯一解锁路径，AI 不代投（纪律 6）。A01–A04 四个旧 T02 锚（E032/E039/E042 系）的填充路径另行登记待办。

### 2.5 运行目录冲突 = 版本滞后（pilot2_all vs pilot2_wave4_mock）

现象：`JUDGE_CALIBRATION_REPORT.md` §2 与 `PHASE2_ACCEPTANCE.md` #12 的快照文本仍写 run_dir=`runs/pilot2_wave4_mock`（引用队列 2026-11-08 11:22:29 的 mock 版），而现盘 `HUMAN_REVIEW_QUEUE.json/.md` 已于 **2026-11-08 18:11:52** 以 `--run-dir runs/pilot2_all` 重生成（generated_at=18:11:52）。

时间线（mtime 实读口径，2026-11-12 核对）：mock 预演 11-08 11:01 定稿 → mock 版队列 11:22:29 → 验收矩阵起草 12:02、校准报告落稿 15:25 → live 三 run 18:08 齐 → pilot2_all 聚合定稿 18:11:51 → 队列 18:11:52 重生成 → 验收最后改 18:12:34（#12 未同步）。

定性：**版本滞后产物，非数据矛盾**。三段子定性：

1. 正式输入目录**可裁决** = `runs/pilot2_all`（CURRENT+DIRECT 两臂 live 聚合，1,024 位全部 complete；pilot2_wave4_mock 为同计划 bm1_mock 预演、976 位固定 mock 串，报告自身已载明 mock 不得作为任何校准或评分输入）；
2. 报告/验收快照未对齐（未解决，维持登记）；
3. 人工裁决未收集：40/40 PENDING_USER（未解决，维持登记）。

目录记账（引用必须带目录名，不得相互换算）：全 pilot 位集 = **1,104 位**（40 篇 × 2 臂，`PHASE2_ACCEPTANCE` #26 口径 = live_b 456 + live_c 520 + oldT09 128 三目录合计）；`pilot2_all` 为 **1,024 位聚合目录**（78 个 episode_manifest，无 run_summary/plan），差异 = LRE121 两臂 80 位未入聚合目录。两数均为实测、差异可解释，不影响 40 锚点（锚位无 LRE121 位）。

---

## 3. 可比性规则（不同条件不拼分——红线）

### 3.1 可比性红线

- **不把不同模型、不同题目、不同评分器或不同 Token 预算的结果硬放进同一张对比图。**
- **不能把不同条件下的最高分拼在一起，宣称某个机制全面胜出。**
- 不能把不可比结果画在同一张对比图里；每张图必须能找到源 CSV/JSON 和绘图脚本。

### 3.2 可比性核对七项

放进同一张正式对比图的结果，必须核对：模型版本；数据集和版本；Gold 和评分口径；Prompt 与生成参数；样本集合；运行条件；统计单位。**不满足可比条件的结果可以保留，但必须分开呈现。**

### 3.3 本仓可比性实操规则

1. **批内才可比**：同一批次内四臂共用同一模型与采样配置（benchmark 系 temperature 0.7，授权单规定；模型与网关字段落盘于各 run 的 `episode_manifest.json` / `provider_check.json`，正文不转写端点）。跨批次结果分开呈现，不做跨条件择优拼接。
2. **目录口径不换算**：pilot 位集 1,104 位（权威样本三目录合计）与 pilot2_all 聚合目录 1,024 位并列登记（§2.5）；引用必须带目录名。
3. **口径代际并列、公开用权威口径**：Gold 三代口径并列（文档口径 221/3,102/6,603；远端快照 2026-11-08 口径 259/3,590/7,953；**本地 v2 重建 2026-11-10 权威口径 299 集 / 4,082 轮 / 9,646 原子**，落位 `benchmark/stories_v2/GOLD_SIDECAR/`）。公开叙事用权威口径，历史口径不改写。防混淆登记：Gold 冲突 resolution 的 93 行与 benchmark 对账的 93 个实质差异文件是**两个无关的 93**，严禁混写。
4. **判决数字带版本标签**：W22 数字口径 = `W22FULL_VERDICT_REV2_20261104.md`（取代同日 REV1）；FS1-B severe 行为勘误后口径（勘误前 0/3 为错臂值，勘误后候选 3 / 父本 0，ERRATUM-SEVERE-SWAP-20261030）。
5. **协议变更 ≠ 候选进步**：rubric/评分器版本变更须用基线候选重评后才能比较（§1.1）。
6. **分母纪律**：失败不从分母删除（层 4）；gold={} 记未测不算通过；首选/备选分审（纪律 2/3/4/5，§1.4）。PAPER.md 成绩表述须与 runs/判决书互查，禁写成"全面优于原版"（表达红线，迁移译注已随 PAPER.md 落位）。

---

## 4. 产物状态分类（七状态 + 后缀非垃圾口径）

### 4.1 七状态定义与 evaluation 域分布

分类对象：`D:\lovebrain-arena\evaluation\` 全部 98 文件（2026-11-12 分类，文件名规律 + 全部 98 文件内容抽样 + 定向深查；UNKNOWN = 0）。明细底稿 = `evaluation-classification.csv`（98 行；列：relative_path, size_bytes, mtime, status_label, evidence_note；**该底稿未随公开树落盘**，公开树内逐件 provenance 登记见 `research/provenance/MIGRATION_MANIFEST.csv` evaluation 域行）。

| 状态 | 语义 | 数量 | 占比 | 代表 |
| --- | --- | ---: | ---: | --- |
| FINAL | 收口/判决/成绩单/审计/manifest 类，现行口径 | 79 | 80.6% | C4 收口、P06/P07 结果、知识审计 |
| SUPERSEDED | 已被新版本取代，原文保留不改写 | 6 | 6.1% | JUDGE_MANUAL_V1、DELIVERY_AND_COST_COLUMNS_V1、QUALITY_PANEL_SCORECARD_V1、REFERENCE_LABELS_V1_1 |
| INTERIM | 中间态/初步裁决，自declared 不冒充终版 | 5 | 5.1% | A3 分析 INTERIM、P06_QUALITY_RESULTS.interim、P06_RESULTS.md（自declared 初步裁决） |
| BUGGY | 已知缺陷版本，显式留档不得覆盖 | 3 | 3.1% | A3 三份修复前分析（§2.2） |
| PREVIEW | 草案/提案页，待审定 | 3 | 3.1% | P06_QUALITY_RESULTS.preview、DRAFT_SUMMARY.json、GOALS_AND_SCOPE_V1.md |
| MOCK | mock 产物，不得当实测结果 | 1 | 1.0% | IRR_AGREEMENT_V2_SELFTEST_mock.json |
| DEBUG | 调试口径产物，不得当实测结果 | 1 | 1.0% | IRR_AGREEMENT_V2_dev_debug.json |
| UNKNOWN | 无法定性 | **0** | 0% | — |

### 4.2 特殊后缀非垃圾口径

`.buggy-swap-not-normalized`、`.partial-norm`、`.interim`、`.preview` 后缀文件**全部原样登记，未做改名/合并/删除**；每份在分类明细中写明性质与替代关系。失败/调试/mock 证据与成功产物同权保留——它们是评测体系"能拒绝看似进步实则违约束候选"的证据面。

### 4.3 版本链与更正波次证据（不覆盖修复前结果、不把 interim 改名 final）

- **A3 分析五件**：见 §2.2（四连版本链完整 + INTERIM 在途件）。
- **REFERENCE_LABELS V1_1 → V1_2**：14/60 节点的 reference_provenance 由 human_reviewed 修正为 evidence_reviewed_by_ai；`JUDGE_QUALITY_V2_1_SCOPE_CORRECTION.json` 明示"旧 V1_1 保留；V1_2 为生效口径"（V1_1=SUPERSEDED，仍被 FROZEN_MANIFEST/ARCHIVE_INDEX 引用；V1_2=FINAL）。
- **P06_QUALITY_RESULTS 三连**：preview(records=180) → interim(219) → final(230)，递进关系与 mtime 一致，三份全保留。
- **10-26 更正波次**：JUDGE_QUALITY_V2_1_SCOPE_CORRECTION、SWAP_REINTERPRETATION_V1_2 均以"不改写旧文件、新文件为生效口径"方式落地，被更正对象（JUDGE_QUALITY_V2_AND_V5_DISPOSITION 等）原文保留 → SUPERSEDED 而非删除。

### 4.4 定性权衡登记（5 处额外权衡，均已写入分类明细）

P06_RESULTS.md=INTERIM（自declared 初步裁决，公开索引不得冒充 FINAL）；GOALS_AND_SCOPE_V1.md=PREVIEW（待审定提案页）；CALIBRATION_PACKET_V1_1/DRAFT_SUMMARY.json=PREVIEW（草案摘要工件）；CALIBRATION_PACKET_A3_20261026/CLAIM_SIDELANE_20261026.txt=FINAL（废弃条件未触发，包实际被启用）；BACKLOG_M1_TO_M2_20261026.md=FINAL（挂账登记文件性质，不冒充结果）。另：JUDGE_MANUAL_V1_1 与 V2_0_A3 均为 FINAL——并存口径（§2.1）。

### 4.5 公开树落位与使用纪律

- 公开树 `research/evaluation/` 落盘 **97 件**（`research/provenance/SOURCE_MAP.md` §13 D 行，2026-11-12 口径：源域 98 件 − 密件 3 件按迁移清单排除（SEALED_KEY×2 + 禁分发件 1）+ hc2 审计 2 件；原 3 件审查保留件已收口迁入）。排除件在迁移清单登记 exclusion，不进公开树。
- **错误版不删**：SUPERSEDED/BUGGY/INTERIM/PREVIEW/MOCK/DEBUG 全部随链保留。
- 使用纪律：MOCK/DEBUG 产物不得当实测结果；INTERIM 不得冒充 FINAL；含"机器判定 fuzzy 命中未经人工复核不得进对外结论"限定语的文件（如知识原子审计）引用时必须保留该限定语（§7.3）。

---

## 5. 四臂实验概述（selection_v1）

### 5.1 评测数据切片与统计单位

- **题库层级**：`research/benchmark/stories_v2/` 19 个一级子目录 + 顶层 GOLD_SCHEMA.md = 2,023 文件（benchmark 域对账，2026-11-12）；**341 个 episode 目录**各自配套五件套（episode.json / reference.json / timeline.json / exposure.json / audit.md，相对路径模式 100% 命中）；各层 manifest 原样保留、不合并不同 wave 的同名 manifest、不静默去重（十条层级规则逐条核验通过）。
- **统计单位纪律**（`benchmark/STATISTICS.md` §三；`DATASET_DOCUMENTATION.md` §1.5）：Story（版本目录不重复计数）/ Episode（副本不重复计数）/ Turn（一次题目轮）/ Atom（可核验原子断言）/ Gold coverage（未覆盖 episode 不得当 Gold 样本）。stories_v2 的文件数**不是 episode 数**；唯一 ID 去重等权威计数见 `benchmark/STATISTICS.md`（2026-11-11 机器重算口径链）。
- **切片使用纪律**：`wave4_dev` 不是正式测试集（pilot 用）；`wave5_selection` 不是已验证正式集（四臂选择实验用）；holdout 题库为封存 vault（runs 侧数据集版本字段登记），消耗受 holdout 纪律约束。
- **B 线一行**：`benchmark/b_line/` 24 篇 episode 按普通研究资产随 benchmark 域迁移，清单件与说明件的版本差异按原样保留。**该批次题目与 Gold 已随公开分支公开——自公开时起为公开 Benchmark / 历史 B 线，不再是未曝光测试集**，任何文档不得将其表述为"当前独立盲测"（状态标注见 `benchmark/b_line/B_LINE_README.md` §0）。

### 5.2 四臂定义（ARM_REGISTRY.json，arm_registry_v1，2026-11-08）

| 臂 | 名称 | 来源 |
| --- | --- | --- |
| DIRECT | Direct | Phase 1 新建轻量适配器（直接基线） |
| COT | CoT-style | Phase 1 新建轻量适配器 |
| LEGACY | Historical Harness | `candidates/action_policy_r9_slim` |
| CURRENT | LoveBrain Harness | 双域冠军按轮数分流：≤24 轮 window_expand_b_norecap_guard、>24 轮 window_expand_b_norecap_uc |
| （可选第五臂）REFERENCE | 原版产品参考组 | reference_product_v1；不入主对照，先核保真边界 |

（臂身份以注册表与各 episode_manifest 的身份链为准。）

### 5.3 执行规模与完整性（selection_v1，Phase 3 正式四臂选择实验）

- runs 实测（runs-registry / 42 重点 run 核对表，2026-11-12）：`selection_v1` 共 10,090 文件 / 70.3 MB；**160 集（4 臂 × wave5_selection 40 篇），episode_manifest 计划 1968 位 = 实际 reply 1968 = scoring 1968，COT/CURRENT/DIRECT/LEGACY 四臂全 ok**。
- 计划口径以 160 个 episode_manifest 为准：run 内 plan.json 只覆盖最后一轮 LEGACY 补跑 24 位（gen 2026-11-10 18:02），不构成完整计划口径（登记差异，以 EM 为完整口径）。
- 授权：`AUTH-BM1-PHASE3-SELECTION-20261110-01`；数据集：wave5_selection 40 篇；同批四臂同模型同采样（§3.3 条 1）。

### 5.4 评分栈与 guards v2 重评分

- 第一层：逐 turn `scoring.json`（九项 checks 计分器）；
- 复评层：`universal_scorer_v2 + semantic_guards_v2`——selection_v1 内含 `universal_scores_guardv2.csv`（**已被 guards v2 重评分**）；RESUME_STATE.scorer 记载 v2 修复 cancel_ack 类别，v1 九类无退化。
- 判决定位：runs/ 内无判决文档；正式判决定位 `refs/research_harness_20261110/`（overnight/RESUME_STATE.json latest_verdicts 等）与 `refs/benchmark_v1_20261107/`；判决原文件目录索引见 `refs/VERDICT-INDEX.md`（53 个 VERDICT 文件，2026-11-12 扫描）。

### 5.5 前身尝试与失败证据（全部保留）

`selection_v1_current`（断跑多次重启，59 位）、`selection_v1_direct`（中止，234 位）、`selection_v1_fast`（放弃，46 位）、`selection_v1_slow`（放弃，13 位）——均为失败/中断证据，已被 selection_v1 吸收替代，只登记不删改。

### 5.6 结果呈现边界

四臂分数只在**同批同条件**下进同一张表（§3）；跨臂结论表述与判决书互查，不得写成"全面胜出"。具体分数与判决不在本文展开（EXPERIMENT_RESULTS 轨），本文只登记执行完整性与评分栈口径。

---

## 6. holdout_v1 部分执行（889/6,240，如实登记）

- **计划**：6,240 位（runs plan 口径：4 臂 × 120 集，位数为 plan/EM 位级合计）；授权 `AUTH-BM1-PHASE3-HOLDOUT-20261110-01`（另有 `phase3/HOLDOUT_EXPOSURE_MARK_20261111.json` holdout 消耗纪律件）。
- **实际**（runs 实测，2026-11-12）：`holdout_v1` 共 4,281 文件 / 21.3 MB，mtime 2026-11-10 19:09:39——**当晚中止、未续**。实际 889 reply / 888 scoring ≈ **14.2% 部分执行**。
- 明细：80 个 episode 目录，69 个有 episode_manifest（全 complete），EM 内计划合计 828 位；`run_records.json` 70 条（DIRECT 20 / COT 19 / LEGACY 15 / CURRENT 16），69 ok + 1 error；**无 run_summary.json**（完成态只能由 EM + records 拼合——已登记）。
- 缺口：COT/E405 缺 1 份 scoring.json（`COT/E405/turn_10_E405_T10`，答卷在、评分缺——异常清单第 3 项）。
- **判决不存在纪律**：holdout_v1 判决**不存在**，任何场合不得写成"holdout 判决已完成"，也不得声称 Benchmark V1 的 240 篇目标、120 篇 Holdout 已全部完成。其低执行度与 holdout 消耗纪律相关，后续处置另行决定，本文不预判。
- **独立泛化终验状态（2026-11-13 口径）**：本仓**不存在对真正未曝光数据的已完成独立泛化终验**——唯一封存 holdout 批（holdout_v1）中止于 14.2%，判决不存在；已公开的 B 线 24 篇自公开时起不再构成未曝光盲测集（§5.1 B 线一行）。如实登记为"独立泛化终验未完成"，不为展示补实验。

---

## 7. 公开索引与读取路径

### 7.1 评测域逐文件状态索引

- **逐文件状态权威明细**：`evaluation-classification.csv`（98 行 × 5 列：relative_path, size_bytes, mtime, status_label, evidence_note；2026-11-12 分类）——**该底稿 CSV 未随公开树落盘**（2026-11-13 全树核查零命中）；公开树内逐件登记以 `research/provenance/MIGRATION_MANIFEST.csv`（evaluation 域行）承载，本文 §2/§4 的版本链结论以 §4.1 分类表为准。
- 公开树落位：`research/evaluation/`（97 件，§4.5）。

### 7.2 相关域入口表

| 材料 | 落位 | 口径 |
| --- | --- | --- |
| 评分协议 | `research/benchmark/rubric.md`（v0.5 现行）+ `rubric_v0_6_draft.md`（历史草案） | §1.1 |
| 数据统计权威口径 | `research/benchmark/STATISTICS.md`（2026-11-11 机器重算口径链） | §5.1 |
| Gold 三代 | `benchmark/stories_v2/GOLD_SIDECAR/`（v2 权威，14 文件）+ 远端快照目录（13 文件）+ `GOLD_SCHEMA.md`（文档 v1.0，滞后原样保留） | §3.3 条 3 |
| 层 1 计分器 | `research/arena/lb_scorer.py`（v0.7）+ `scorer.py`（v3 前置代际） | §1.3 |
| 批次索引 | `research/experiments/`（runs-registry.csv，288 run × 14 列） | §5/§6 |
| Judge 材料 | `research/evaluation/`（JUDGE_MANUAL 三版、A3 分析五件、QUALITY_* 链）+ `research/refs/benchmark_v1_20261107/phase2/`（HUMAN_REVIEW_QUEUE、JUDGE_CALIBRATION_REPORT、PILOT2_AUTHORIZATION 等） | §2 |
| 判决原文件 | `research/refs/VERDICT-INDEX.md`（53 个 VERDICT 文件索引）+ `refs/experiment_protocol_20261023/`（long_probe_20261030、global_harness_program_20261030 判决目录）+ `refs/KNOWN_FAILURES.md` | §5.4 |
| 论文稿 | `research/PAPER.md`（成绩表述须与 runs/判决书互查；迁移译注带版本标签） | §3.3 条 6 |
| 机制与生产链路 | `research/architecture/ARCHITECTURE.md` | 机制轨 |

### 7.3 引用限定语（公开引用必须随行）

1. **Judge 校准**：P1–P4 全部 PENDING 期间，不得出现"Judge 已校准/已扩权"表述（§2.4）。
2. **机器判定**：机器判定 fuzzy 命中未经人工复核不得进对外结论（知识原子审计文件自带口径，引用必须保留）。
3. **产物状态**：MOCK/DEBUG/INTERIM/PREVIEW 产物不得当实测结果或终版结论（§4）；旧口径数字引用必须带口径标签（§3.3 条 3/4）。

---

## 8. 自检登记（2026-11-12）

| 检查项 | 结果 |
| --- | --- |
| 完整性口径（文件数 + 字节量） | 通过——全文统计口径统一 |
| 零隐私（零真实聊天内容、零密钥、零真实端点） | 通过——mock 串仅描述未引用；模型/网关只述字段落盘位置 |
| 数字带版本标签 | 通过——全部数字标注来源文件与日期口径 |
| B 线按普通研究资产登记 | 通过——§5.1 一行中性登记 |
| 红线（不同条件不拼分） | 通过——§3 全节落实 |

---

## 9. 用户价值五项实验（user_value_v1，2026-11-13）

完整方法、结果主表与逐对证据见 [report.md](evaluation/user_value_v1/report.md)（含 §3 结果主表、§9 盲评逐对表）。

2026-11-13 新增一批面向用户价值的对照实验，落位 `research/evaluation/user_value_v1/`（14 文件：protocol.md、protocol-v1.1-addendum.md、cases.jsonl、cases-schema.md、outputs.jsonl、judgments.jsonl、scores.csv、scoring-templates.md、anon-map-revealed.json、summary.json、report.md、plot_results_v11.py、user-value-results.svg、showcase-case.md；protocol.md §7 承诺的最小交付集 8 件全部在场）。实验评五项指标 M1–M5，对应根 [README.md](../README.md) 用户语言版的 E1–E5 通俗名（关键记忆 / 纠错与关系状态 / 不编造、不越界 / 当下意图与分寸 / 自然、好发）。结果按两条独立线统计、禁止合并：H 线=历史固定模型复评（`selection_v1` 四臂原始回复重评，不重新生成），N 线=原生子代理新生成（B1 基线 vs L 机制臂真实生成+盲评）。本文不复写该实验数值——通俗五问与结果主表见根 README 用户语言版（README 更新版落位后生效），逐题判定与计分明细见 `research/evaluation/user_value_v1/` 内文件。

### 9.1 v1.1 重评口径

- **对称重评**：评分规则以 v1.1 冻结件为准（`protocol-v1.1-addendum.md` 与 scoring-templates v1.1 对称重评版，先冻结、后批量）；规则冻结 v1.1 后所有比较臂对称重评，不在看见输赢后改分；v1.0 判定与数据全部留档、原样保留，与 v1.1 并列呈现。
- **E5 更名**：E5 定名"盲评回复偏好/自然度与可发送性代理评分"——原生子代理代理评分（回复级五选一偏好裁决 + 可发送性 0–3 量尺），全处结果按代理评分口径引用，不称用户发送率。
- **M2 双门**：M2 采用 M-PAIR 双门口径，pair_score = min(recall, suppress)，无半分，分门落盘，负对照自检作为批量前门禁。
- **两类实验分开**：产品整体对比（完整 Harness vs 基线，保留双方各自真实候选集合与正常生成流程）与单机制消融（只切换单机制、结论只讲该机制的因果贡献）分开设计与报告，各自独立成立。
- **E5-N v2 口径**：N 线机制臂使用分域冠军 Harness `window_expand_b_norecap_guard`（KB 注入修复版）整组多候选盲评；v1.0 N 线因 KB 未注入、候选不对等而作废，按 v1.1 全候选口径重建重跑，纠偏史如实留档于 v1.0 判定件。重跑结果：冠军 Harness 整组盲评 L2 11/B1 1（n=12，复评 3/3 胜负一致）（npair2-11 自然度单维分歧经第三评委仲裁终裁 5），逐对证据见 report.md §9。
