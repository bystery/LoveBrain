# harness-lovebrain · LoveBrain Arena

LoveBrain（Android 聊天辅助产品）的 **harness 研究考场**：在同一固定模型服务上，比较与迭代"完整聊天 harness"——提示资产、意图处理、记忆/知识存取、上下文组织、调用与输出策略。

- **研究论文（构成、协议、结果、边界）**：[`PAPER.md`](PAPER.md)
- **治理宪法**：[`AGENTS.md`](AGENTS.md)（v1.6）、[`OBJECTIVE.md`](OBJECTIVE.md)、[`benchmark/rubric.md`](benchmark/rubric.md)（v0.5）
- **机读当前状态**：[`refs/experiment_protocol_20261023/global_harness_program_20261030/RESEARCH_STATE.json`](refs/experiment_protocol_20261023/global_harness_program_20261030/RESEARCH_STATE.json) + 同目录 `TASK_QUEUE.json`；恢复入口 `WORKER_CURRENT_RUNBOOK.json` → `WORKER_NEW_WINDOW_START.md`

## 目录速览

| 路径 | 内容 |
|---|---|
| `arena/` | 考场运行时：装配（lb_assembly）→ 运行（lb_runner）→ 网关记账（lb_gateway）→ 唯一计分器（lb_scorer） |
| `candidates/` | 参赛 harness 版本树（现行双域冠军：`window_expand_b_norecap_uc` / `window_expand_b_norecap_guard`） |
| `benchmark/` | 题库 208 条剧集 / 3,102 轮 / 6,603 原子断言 + **封存盲测集 `b_line/`（only_once）** |
| `refs/` | 协议、判决书（long_probe_20261030/）、KNOWN_FAILURES、BASELINE_PARITY |
| `tools/` | 确定性工具（gold sidecar、引文硬校验、崩坏重试、B3 队列、账本） |
| `logs/` | 原始逐调用答卷与授权账本（约 2.1GB，**仅本地保留，不入库**） |
| `archive/` | 历史归档（整理清单一律带 MANIFEST） |

## 三条纪律（细节见 AGENTS.md）

1. 被测对象 = 完整 harness；API 返回只当数据；质量问题不得归咎模型。
2. 判决必须真实同场对照：预注册判据、唯一程序计分器、judge 不单独判死、severe 必须亲读复核；成本/时延/污染与质量并列门禁。
3. 诚实优先：负结果（淘汰/阻断/不确定）与正结果同样入账；封存集只许消费一次；封板后改题 = 舞弊。

> 阶段状态（2026-11-07）：版本收口中——冠军机制以 SHADOW 形态合入产品分支 `closeout-20261106`，待真机验收；机制线冻结，评估基准与题库的下一轮迭代在收口后恢复（见 PAPER.md §6）。
