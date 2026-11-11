# 存档索引 — 2026-10-25 本轮收尾（真实模型调用 0）

评价方法已在任何待测裁判之前冻结；裁判批次与8次流式集成**均未运行**；v5 处置**未定**。
交接读 `refs/experiment_protocol_20261023/evaluation_baseline_review_20261025/HANDOFF_TO_NEXT_SESSION.md`。

| 项目 | 路径 | 存在 | sha256(前16) | 字节 |
|---|---|---|---|---|
| 评审量尺(修订) | `evaluation/JUDGE_MANUAL_V1_1.md` | 是 | dbbbf9cde656875e | 9962 |
| 评审量尺(旧,历史) | `evaluation/JUDGE_MANUAL_V1.md` | 是 | 66e6286259bfbaae | 7659 |
| 缺陷卡修订(6张) | `benchmark/dev_expansion_v01/05_GRADING_CARDS_R2REV_20261025.json` | 是 | 530a3cd66c6c67fa | 24789 |
| 卡片冲突审计 | `evaluation/CARD_CONFLICTS_V1.json` | 是 | 391fcac175115514 | 4581 |
| 六列交付/费用 v2 | `evaluation/DELIVERY_AND_COST_COLUMNS_V2.json` | 是 | 61150f23ad4c2285 | 21230 |
| 证据更正 | `evaluation/EVIDENCE_CORRECTIONS_V1.md` | 是 | 258230bd8557fe29 | 6461 |
| 标定包 60对(公开评审输入) | `evaluation/CALIBRATION_PACKET_V1_1/nodes_public.jsonl` | 是 | 63cfe6801ae2d9cb | 259223 |
| 标定包 60对(内部含分组) | `evaluation/CALIBRATION_PACKET_V1_1/nodes.jsonl` | 是 | 5d96dd0711736331 | 283087 |
| 密件(臂身份/散列) | `evaluation/CALIBRATION_PACKET_V1_1/SEALED_KEY_V1_1.json` | 是 | d758b75f5c268b7d | 43909 |
| 参考标签(裁判前冻结) | `evaluation/CALIBRATION_PACKET_V1_1/REFERENCE_LABELS_V1_1.json` | 是 | e8d06608eaa1ecf2 | 888073 |
| 冻结清单(哈希) | `evaluation/CALIBRATION_PACKET_V1_1/FROZEN_MANIFEST.json` | 是 | c41b3f968e18e386 | 1302 |
| 人工核对覆盖表 | `evaluation/CALIBRATION_PACKET_V1_1/reference_overrides.json` | 是 | d673fdf33b083d77 | 8196 |
| 控制项构造参考 | `evaluation/CALIBRATION_PACKET_V1_1/CONTROLS_REF.json` | 是 | 708bff7ffbea0abb | 9908 |
| 主张机械初稿 | `evaluation/CALIBRATION_PACKET_V1_1/CLAIMS_MECHANICAL.json` | 是 | 15e31827504445f2 | 555494 |
| 新6故事 题面 | `benchmark/positive_capability_20261025/01_EPISODES_P1.json` | 是 | 0aae58594e0e4634 | 34939 |
| 新6故事 卡片(48位) | `benchmark/positive_capability_20261025/02_GRADING_CARDS_P1.json` | 是 | d0aeda20beb1f3bd | 72294 |
| 产品接入盘点 | `integration_preview/PRODUCT_WIRING_INVENTORY_V1.md` | 是 | fafdcdcaaaf482f6 | 15494 |
| 阈值预声明 | `evaluation/JUDGE_QUALITY_THRESHOLDS_DECLARED_V1.md` | 是 | 188a20fbc64278d7 | 4640 |
| v5 独立重算(null更正) | `refs/experiment_protocol_20261023/v5_next_batch_20261025/V5_INDEPENDENT_RESULTS_REV01.json` | 是 | dabc54ee41a33b40 | 4903 |
| 流式计时接通测试 v2 | `tools/test_stream_timing_v2.py` | 是 | 2dfe0535516d4266 | 9861 |
| 流式计时接通测试 v1 | `tools/test_stream_timing.py` | 是 | c88c6d65cec255ac | 10467 |
| 新故事自检 | `tools/test_positive_capability_stories_p1.py` | 是 | ed525c03054a86c2 | 21093 |
| 裁判批次脚本(未跑通) | `tools/run_judge_batch_v1_1.py` | 是 | 63b839aacf4b8527 | 11290 |
| 标定包生成器 | `tools/build_calibration_packet_v1_1.py` | 是 | 69efdd3cbf077ab3 | 39704 |
| 参考冻结器 | `tools/build_reference_labels_v1_1.py` | 是 | b411cb5d45d41665 | 13027 |
| 交接文档 | `refs/experiment_protocol_20261023/evaluation_baseline_review_20261025/HANDOFF_TO_NEXT_SESSION.md` | 是 | 65cc8f9f5a35581e | 15492 |
| 执行书 | `refs/experiment_protocol_20261023/evaluation_baseline_review_20261025/WORKER_EXECUTION.md` | 是 | 307314eabf3fb0c3 | 11119 |
| 独立复核 | `refs/experiment_protocol_20261023/evaluation_baseline_review_20261025/REVIEW.md` | 是 | d4d32067406970fa | 10750 |

```json
{
 "archived_at": "2026-10-25",
 "real_model_calls_this_session": 0,
 "budget": {
  "AUTH-HARNESS-PERF-PHASE1-20261025-01": {
   "cap": 256,
   "used": 4,
   "remaining": 252
  }
 },
 "evaluation_method_frozen_before_test_judges": true,
 "frozen_reference_sha256": "e8d06608eaa1ecf286f7ca40d281a821e61d3e06b47d34456b1b410cffd06b46",
 "judge_batch_run": false,
 "stream_integration_8calls_run": false,
 "v5_rescore_with_v1_1": "not_started",
 "v5_verdict": "undetermined",
 "missing": []
}
```
