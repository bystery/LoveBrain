# RESEARCH-P1 Phase 1 交付目录

**阶段代号**：RESEARCH-P1（第一大阶段：研究体系启动与首轮机制探索）
**宪章**：[RESEARCH_PRINCIPLES.md](RESEARCH_PRINCIPLES.md)（2026-11-11 用户下发，执行宪章，优先级最高）
**参考基线**：`phase3-sync-20261110` @ `947770edad719877401b6c5883cbb064b0a22ec9`（已核验）

## 目录

| 路径 | 内容 | 状态 |
| --- | --- | --- |
| `RESEARCH_PRINCIPLES.md` | 宪章全文（P1-01~P1-10、A/B/C 三线、G0~G3 门槛） | ✅ 已落盘 |
| `WORKSTREAM_STATUS.md` | 三线工作流状态板 | 动态更新 |
| `benchmark/` | A 线：DEV 快速评估集 + 覆盖/判别力审计 | 🔄 进行中 |
| `literature/` | B 线：外部文献检索日志、精读笔记、机制假设 | 🔄 进行中 |
| `harness/` | C 线：统一接口、机制注册表、实验日志 | 🔄 进行中 |
| `PHASE1_ACCEPTANCE.md` | G0/G1/G2 门槛核对表 | 动态更新 |

## 三线与角色（P1-07）

| 角色 | 承担者 | 交付位置 |
| --- | --- | --- |
| Benchmark Worker（A 线） | 独立子代理 + 主会话 | `benchmark/` |
| Research Worker（B 线） | 独立子代理（真实联网检索） | `literature/` |
| Harness Worker（C 线） | 主会话 | `harness/` |
| Audit/Integration（复核） | 主会话独立复核批次 | `PHASE1_ACCEPTANCE.md` |

## 数据与预算纪律

- **本批次（G0 启动批）零模型 API 调用**：文献检索用通用联网搜索工具，DEV 评估集用本地已有数据构造；真实模型冒烟测试在 G1 达标后另行登记 AUTH。
- 禁区（对所有 Worker）：`benchmark/b_line/`（物理封存）、`D:/lovebrain-arena_holdout_vault/`（HOLDOUT 金库）、任何未消费测试集的 Gold、HOLDOUT E401–E520 题面。
- API Key 只从环境变量 `LB_API_KEY` 读；仓库/日志零落盘。
- 旧 dsf@17770 成绩不与 USTC `deepseek-flash-2` 结果混合比较。

## 历史成果的继承（G0.2）

三阶段 Benchmark V1 的全部历史成果原样保留：

- 冻结 manifest：`refs/benchmark_v1_20261107/phase3/DATASET_V1_FROZEN_MANIFEST.json`（hash `ed63b6b9…`，240 篇 = DEV 80 / SELECTION 40 / HOLDOUT 120）
- SELECTION 四臂结果：`refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_SUMMARY.json`（2026-11-10 18:16）
- HOLDOUT 实跑停写状态与曝光标记：`refs/benchmark_v1_20261107/phase3/HOLDOUT_EXPOSURE_MARK_20261111.json`
- 曝光账本：`benchmark/stories_v2/*/exposure.json` + `STORY_CUSTODY_LEDGER.json`
- Judge 校准 V2：`refs/benchmark_v1_20261107/phase3/JUDGE_CALIBRATION_V2.md`
- GitHub 快照：`phase3-sync-20261110` @ `947770ed`（14518 文件）

**本阶段不进行新的 HOLDOUT 实验**（宪章 C6 第 3 级）；HOLDOUT 120 篇继续保持封存，once-only 消费权移交后续正式比较阶段。
