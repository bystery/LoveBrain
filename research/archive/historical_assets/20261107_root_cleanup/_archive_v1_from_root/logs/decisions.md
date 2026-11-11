# 架构演化决策记录 (decisions.md)

## [2026-10-02 23:05] Round 0 (基线与环境就绪)
- **初始动作**：完成 `D:\guanjia-harness` 脚手架全套初始化，涵盖基准测试集（20题）、自动化评估器 `eval_runner.mjs`、参考库索引及自进化宪法 `AGENTS.md`。
- **基线现状**：加载 `baseline/v0_architecture.md`。Dry-run 综合得分 77.51 分（准确率 69 分，Token 效率 87.49 分，延迟得分 94.95 分）。
- **主要失分点分析**：
  1. `D_BOUNDARY` (60.0%): 缺少明确的证据真实性防伪协议，小模型容易顺从用户虚假完成。
  2. `E_ERROR` (48.0%): 缺少严格的前台唤醒防死睡机制与失联超时主动报警机制。
- **下一阶段执行目标 (Round 1)**：
  - 启动外脑调研，检索 7-10 篇关于 Agent Harness 评测、防假完成凭证链与前台阶梯监控的研究成果。
  - 针对 `D_BOUNDARY` 与 `E_ERROR` 提炼具体的架构约束补丁，生成 `round_1` 候选配置并执行全面评测。

## [2026-10-02 23:08:23] Round 1 突破新高 (94.56)
配置 {'round': 1, 'v1_identity_lines': 15, 'v2_task_decomposition': 'sentence_level', 'v3_ask_delivery_target': 'strict', 'v4_routing': 'hybrid', 'v5_ipc': 'dual_channel'} 成功超越此前基准。全量用例通过率提高，已刷新 best_architecture。

## [2026-10-02 23:08:28] Round 2 未达标 (94.56)
得分低于历史最佳 94.56，已按规约回退变异，记录为消极变异样本。

## [2026-10-02 23:11:42] Round 1 突破新高 (99.94)
配置 {'round': 1, 'v1_identity_lines': 15, 'v2_task_decomposition': 'sentence_level', 'v3_ask_delivery_target': 'strict', 'v4_routing': 'hybrid', 'v5_ipc': 'dual_channel'} 成功超越此前基准。全量用例通过率提高，已刷新 best_architecture。

## [2026-10-02 23:18:08] Round 1 突破新高 (99.94)
配置 {'round': 1, 'v1_identity_lines': 15, 'v2_task_decomposition': 'sentence_level', 'v3_ask_delivery_target': 'strict', 'v4_routing': 'hybrid', 'v5_ipc': 'dual_channel'} 成功超越此前基准。全量用例通过率提高，已刷新 best_architecture。

## [2026-10-02 23:23:23] Round 1 突破新高 (97.60)
配置 {'round': 1, 'v1_identity_lines': 15, 'v2_task_decomposition': 'sentence_level', 'v3_ask_delivery_target': 'strict', 'v4_routing': 'hybrid', 'v5_ipc': 'dual_channel'} 成功超越此前基准。全量用例通过率提高，已刷新 best_architecture。
