# Phase 1 · 来源与范围声明（SOURCE_AND_SCOPE）

- 起始参考提交：`byster/arena@75ab97e`（工作起点；用户外部指导书 2026-11-07）
- 执行目录映射：本地 `D:/lovebrain-arena/*` ↔ 仓库 `harness-lovebrain/*`
- 执行时间：2026-11-07 21:00–23:00（+08:00），执行者=AI Worker

## 读取过的既有材料（复用前提）

- 治理：`AGENTS.md`、`OBJECTIVE.md`、`WORKER_CURRENT_RUNBOOK.json`、`VERSION_LINEAGE_V2.json`、`PAPER.md`、`README.md`、`benchmark/rubric.md`
- 审查材料：`refs/dataset_review_20261107/`（A–F 全部 8 文件）
- 运行器代码：`arena/lb_runner.py`（run_episode/lifecycle v2）、`arena/lb_gateway.py`（ModelGateway/OpenAICompatAdapter/MockAdapter/ADAPTERS）、`arena/api_candidate.py`（出站白名单）、`arena/lb_scorer.py`（契约/协议键）、`tools/api_trial.py`（capture 先例）、`tools/rescore_batch.py`、`tools/test_three_arm_executor.py`
- 数据：`benchmark/stories_v2/`（manifests/episodes/reference）、`benchmark/stories_v2/GOLD_SIDECAR/`、`benchmark/b_line/B_LINE_MANIFEST.json`（仅元数据）、custody 账本

## 新建（全部在 tools/benchmark_v1/ 与 refs/benchmark_v1_20261107/phase1/）

- `tools/benchmark_v1/`：four_arm_runner.py、normalize_output.py、check_provider.py、capture_and_validate.py、aggregate_pilot.py、make_blind_review.py、gold_diag.py、inventory.py、tests/test_benchmark_v1_offline.py、baseline_candidates/{direct_baseline,cot_baseline}/（各含 assets×4+mechanism+params+IDENTITY）
- 运行产物（本地，不入 GitHub）：`logs/benchmark_v1/phase1_pilot/`（plan.json、plan_sha256、16 个 arm×episode 产物、gateway_events.jsonl、calls.json、CAPTURE_VALIDATION_REPORT、run_records、run_summary）、`logs/benchmark_v1/phase1_live_attempt/`（BLOCKED 登记）
- 交付：本目录 24 个文件（索引见 PHASE1_README.md）

## 明确未修改（完整性基线 `UNTOUCHABLE_HASHES.json`：72 个文件）

- 正式题库（4 个 pilot 剧集的 episode/reference + GOLD_SIDECAR 全部 + rubric）
- 三个 harness 候选目录全部文件（action_policy_r9_slim / window_expand_b_norecap_guard / window_expand_b_norecap_uc）
- `arena/*.py` 全部运行器代码、`benchmark/b_line/B_LINE_MANIFEST.json`
- 测试 `test_untouched_integrity` 逐文件核验（pytest 全绿）

## 关键纪律执行记录

- 零模型调用（API Key 未配置 → live pilot 按规程 BLOCKED，见 API_COMPATIBILITY_REPORT.md / LIVE_BLOCKED.json）
- B 线：全程未读任何 episode 正文；仅 manifest 元数据用于排除检查（inventory.py）
- 无任何对既有题目/Gold/评分器/harness 资产的写入（pytest 断言背书）
- 密钥：仅从环境变量读取；provider_check 的 redact 遮盖有单测；全 run 目录扫描无 key 落盘（capture 校验 PASS）
