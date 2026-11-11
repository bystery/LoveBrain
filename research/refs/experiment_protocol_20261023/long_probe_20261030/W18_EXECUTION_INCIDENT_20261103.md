# W18 执行事故记录（2026-11-03 12:25）

## 结论

`BATCH-W18-UCSUPP-20261103` 在 **90/320 attempts** 处执行中止（infrastructure failure），
**不是候选质量结果**；按授权 `no_retry/no_makeup/fail_fast` **不续跑、不重试该位**。
W18-03 记 `PARTIAL (EXECUTION_FAILED_TIMEOUT)`，另立 W18B 全量重跑。

## 时间线（2026-11-03）

- 12:01:57 live 启动（双批并行：W18 6 workers + W17c 6 workers）。
- 12:20:20 逐篇评审 watcher 为 W17c E164 起评审；12:25 前后 E157/E170 也起评审
  → 并发模型流约 9 路（dsf 饱和点约 6 路），p95 等待已接近 100s。
- 12:22:48 W18 parent LRE121 T15 attempt 开始。
- 12:24:48 **TimeoutError: timed out（120.018s）** → `HOST_EXCEPTION:
  gateway generate failed: upstream failed: timed out`；`error.json` 与
  `attempt_ledger.jsonl` 均落盘。
- 12:25:55 fail_fast 停止该臂，批次返回不完整结果；收尾 `reports_from_batch`
  在缺失的 `turn_15_LRE121_T15/manifest.json` 上抛 FileNotFoundError（次生错误）。
- 批次实际落盘：parent/candidate 共 90 manifests，91 个 turn 目录（LRE121 parent T15
  只有 error.json，无 manifest）。LRE138 未开始。

## 根因

1. **直接原因**：网关 120s 超时触发宿主异常，fail_fast 终止批次。
2. **促成条件**：两 live 批 + 三篇评审同时占用 dsf；调用 p95 已 ~100s，任一位抖动即越线。
3. **次生**：报告器假定所有 turn manifest 齐全，未处理 fail-fast 留下的半成品目录。

## 处置

- **不续跑**：AUTH-W18-20261103-01 明写 `fail_fast/no_retry/no_makeup`；
  重试 T15 或续跑都会违反跑前冻结。
- **保留证据**：`logs/w18_ucsupp_live_20261103/` 原样保留（R7 日志只增不改）。
- **部分数据身份**：仅作实现核对与探索；**不进 G1/G2 主判**。已完成的预设位亲读
  （LRE117_T04/T12、LRE121_T06）保留在 `LP_W18_B3_READRESULT_PARTIAL.json`，
  标注 partial，W18B 判决时以全量批重新亲读为准。
- **重跑**：新批 `BATCH-W18B-UCSUPP-20261103`（同臂/同篇/同卡，新 plan 与新授权），
  **独占 6 workers、不与任何评审并行**；评审等 live 全部结束后再起。
- **流程修正**：live 批独占模型容量，评审只在其后运行；逐篇 watcher 不再与 live 并行。

## 证据路径

- `logs/w18_ucsupp_live_20261103/parent_LRE121/out/turn_15_LRE121_T15/error.json`
- `logs/w18_ucsupp_live_20261103/parent_LRE121/out/_control/attempt_ledger.jsonl`（末条 attempt_failed）
- `logs/auth_budget/AUTH-W18-20261103-01.jsonl`（scope 含 fail_fast/no_retry/no_makeup）
- 运行器 stdout（后台任务输出，含 traceback）
- `logs/w18_ucsupp_live_20261103/`（90 manifests）
