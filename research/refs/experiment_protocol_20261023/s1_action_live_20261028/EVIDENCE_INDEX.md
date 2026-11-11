# 截止日主轨证据索引（自动现算，非手填）

本文件由 `tools/build_evidence_index.py` 生成；所有数字来自已落盘的授权账本、冻结计划、原始答卷与成绩表。零模型调用。

## 1. 真实计费与传输

| 阶段 | 授权单 | 不同 attempt_id 数 | 传输失败 | 关键事件 |
|---|---|---|---|---|
| S1 | AUTH-P08-S1-20261028-01 | 96 | 0 | attempt_completed=96, attempt_started=96 |
| S2 | AUTH-P08-S2-20261028-01 | 24 | 0 | attempt_completed=24, attempt_started=24 |
| S2R | AUTH-P08-S2R-20261028-01 | 24 | 0 | attempt_completed=24, attempt_started=24 |
| S3 | AUTH-P08-S3-20261028-01 | 24 | 0 | attempt_completed=24, attempt_started=24 |
| D1 | AUTH-P08-D1-20261028-01 | 32 | 0 | attempt_completed=32, attempt_started=32 |
| R3A | AUTH-P08-R3A-20261028-01 | 48 | 0 | attempt_completed=48, attempt_started=48 |
| R3B | AUTH-P08-R3B-20261028-01 | 12 | 0 | attempt_completed=12, attempt_started=12 |

已登记物理请求合计 **260** 次（本周期管理帽 16,384，不是支出目标）。

## 2. 批次身份与答卷数

### S1 — BATCH-S1-ACTION-20261028

- 计划 `refs/experiment_protocol_20261023/s1_action_live_20261028/S1_ACTION/INTERLEAVE_PLAN.json`（sha 19053ee7a20e3a69），计划请求 96，实际写出答卷 {'parent': 48, 'candidate': 48}
- 两臂身份：{"parent": "501bc426977b2e9bcee3a6d83b917bb670642f3d9d4c2e79154634f64cd65b67", "candidate": "6e450c8f607b9c255171ec9c40c1c4f731461d0ae8b16b045ac7549665b2a46b"}
- 卡文件：`benchmark/stories_v2/stage2/GRADING_CARDS_E024_E027_E031_E034.json`
- 输入公平性审计：`S1_INPUT_FAIRNESS_CAPTURE.json`, `S1_INPUT_FAIRNESS_LIVE.json`
- 配对胜负：{"wins_candidate": 32, "wins_parent": 4, "ties": 12, "n": 48}
  - reference_product_v1：P 失败 2/48，C 可发首选 48，合法静默 0，严重错 0
  - action_policy_r1：P 失败 3/48，C 可发首选 48，合法静默 0，严重错 0
  - parent 九维均值：{"boundary": 2.812, "emotion_relation": 2.2, "factuality": 2.778, "goal": 2.81, "initiative": 2.333, "interaction": 2.605, "memory_use": 2.419, "time_state": 2.875}
  - candidate 九维均值：{"boundary": 2.75, "emotion_relation": 2.567, "factuality": 3.0, "goal": 2.905, "initiative": 2.778, "interaction": 2.93, "memory_use": 2.871, "time_state": 2.812}
- 资源差：{"total_tokens_change": -28.49, "prompt_change_pct": -30.84, "completion_change_pct": 31.69, "p50_change_pct": -1.77, "p95_change_pct": -22.09}

### S2 — BATCH-S2-ANCHORS-20261028

- 计划 `refs/experiment_protocol_20261023/s1_action_live_20261028/S2_ANCHORS/INTERLEAVE_PLAN.json`（sha 969878a2582b48bc），计划请求 24，实际写出答卷 {'parent': 12, 'candidate': 12}
- 两臂身份：{"parent": "501bc426977b2e9bcee3a6d83b917bb670642f3d9d4c2e79154634f64cd65b67", "candidate": "6e450c8f607b9c255171ec9c40c1c4f731461d0ae8b16b045ac7549665b2a46b"}
- 卡文件：`benchmark/stories_v2/anchors_20261028/GRADING_CARDS_AK01_AK12.json`
- 输入公平性审计：`S2_INPUT_FAIRNESS_CAPTURE.json`
- 配对胜负：{"wins_candidate": 9, "wins_parent": 0, "ties": 1, "n": 10}
  - reference_product_v1：P 失败 0/12，C 可发首选 12，合法静默 0，严重错 1
  - action_policy_r1：P 失败 3/12，C 可发首选 10，合法静默 0，严重错 1
  - parent 九维均值：{"boundary": 2.333, "emotion_relation": 2.0, "factuality": 3.0, "goal": 2.6, "initiative": 2.0, "interaction": 2.111, "memory_use": 2.0, "time_state": 2.167}
  - candidate 九维均值：{"boundary": 2.833, "emotion_relation": 3.0, "factuality": 3.0, "goal": 2.7, "initiative": 3.0, "interaction": 2.889, "memory_use": 3.0, "time_state": 3.0}
- 资源差：{"total_tokens_change": -28.32, "prompt_change_pct": -30.77, "completion_change_pct": 25.3, "p50_change_pct": -2.93, "p95_change_pct": 78.82}

### S2R — BATCH-S2R-FIX-20261028

- 计划 `refs/experiment_protocol_20261023/s1_action_live_20261028/S2R_RETEST/INTERLEAVE_PLAN.json`（sha 92369c27b44f0976），计划请求 24，实际写出答卷 {'parent': 12, 'candidate': 12}
- 两臂身份：{"parent": "501bc426977b2e9bcee3a6d83b917bb670642f3d9d4c2e79154634f64cd65b67", "candidate": "352c0c063e96a6dc728ca54b95c12aeaf8321aa4798d7a4e3c9f000679cb7333"}
- 卡文件：`benchmark/stories_v2/anchors_20261028/GRADING_CARDS_AK01_AK12.json`
- 输入公平性审计：`S2R_INPUT_FAIRNESS_CAPTURE.json`, `S2R_INPUT_FAIRNESS_LIVE.json`
- 配对胜负：{"wins_candidate": 7, "wins_parent": 0, "ties": 3, "n": 10}
  - reference_product_v1：P 失败 0/12，C 可发首选 12，合法静默 0，严重错 0
  - action_policy_r2：P 失败 1/12，C 可发首选 10，合法静默 2，严重错 0
  - parent 九维均值：{"boundary": 2.667, "emotion_relation": 2.4, "factuality": 3.0, "goal": 2.8, "initiative": 2.0, "interaction": 2.222, "memory_use": 2.5, "time_state": 2.5}
  - candidate 九维均值：{"boundary": 2.667, "emotion_relation": 3.0, "factuality": 3.0, "goal": 2.8, "initiative": 3.0, "interaction": 3.0, "memory_use": 3.0, "time_state": 2.833}
- 资源差：{"total_tokens_change": -16.05, "prompt_change_pct": -18.09, "completion_change_pct": 28.23, "p50_change_pct": 4.03, "p95_change_pct": 8.62}

### S3 — BATCH-S3-ATTRIB-20261028

- 计划 `refs/experiment_protocol_20261023/s1_action_live_20261028/S3_ATTRIB/INTERLEAVE_PLAN.json`（sha a877944558078493），计划请求 24，实际写出答卷 {'parent': 12, 'candidate': 12}
- 两臂身份：{"parent": "ac2672ffac3ba82d799a49b753840573cb1933eb71d8110792e5e0f3448c127b", "candidate": "6e450c8f607b9c255171ec9c40c1c4f731461d0ae8b16b045ac7549665b2a46b"}
- 卡文件：`benchmark/stories_v2/anchors_20261028/GRADING_CARDS_AK01_AK12.json`
- 输入公平性审计：`S3_INPUT_FAIRNESS_CAPTURE.json`, `S3_INPUT_FAIRNESS_LIVE.json`
- 配对胜负：{"wins_candidate": 2, "wins_parent": 3, "ties": 5, "n": 10}
  - v6_integrated：P 失败 2/12，C 可发首选 12，合法静默 0，严重错 3
  - action_policy_r1：P 失败 3/12，C 可发首选 10，合法静默 0，严重错 3
  - parent 九维均值：{"boundary": 2.833, "emotion_relation": 3.0, "factuality": 3.0, "goal": 2.9, "initiative": 3.0, "interaction": 2.889, "memory_use": 2.5, "time_state": 3.0}
  - candidate 九维均值：{"boundary": 3.0, "emotion_relation": 3.0, "factuality": 3.0, "goal": 2.7, "initiative": 3.0, "interaction": 2.778, "memory_use": 3.0, "time_state": 2.833}
- 资源差：{"total_tokens_change": 4.58, "prompt_change_pct": 5.7, "completion_change_pct": -7.27, "p50_change_pct": -5.84, "p95_change_pct": -39.62}

### D1 — BATCH-D1-GENERAL-20261028

- 计划 `refs/experiment_protocol_20261023/s1_action_live_20261028/D1_GENERAL/INTERLEAVE_PLAN.json`（sha aa1da608e948065e），计划请求 32，实际写出答卷 {'parent': 16, 'candidate': 16}
- 两臂身份：{"parent": "501bc426977b2e9bcee3a6d83b917bb670642f3d9d4c2e79154634f64cd65b67", "candidate": "352c0c063e96a6dc728ca54b95c12aeaf8321aa4798d7a4e3c9f000679cb7333"}
- 卡文件：`benchmark/stories_v2/stageD/GRADING_CARDS_D01_D02.json`
- 输入公平性审计：`D1_INPUT_FAIRNESS_CAPTURE.json`, `D1_INPUT_FAIRNESS_LIVE.json`
- 配对胜负：{"wins_candidate": 5, "wins_parent": 3, "ties": 5, "n": 13}
  - reference_product_v1：P 失败 0/16，C 可发首选 16，合法静默 0，严重错 3
  - action_policy_r2：P 失败 0/16，C 可发首选 13，合法静默 3，严重错 3
  - parent 九维均值：{"attribution": 3.0, "boundary": 3.0, "emotion_relation": 2.5, "factuality": 3.0, "goal": 2.875, "initiative": 2.9, "interaction": 2.0, "memory_use": 2.5, "time_state": 2.875}
  - candidate 九维均值：{"attribution": 3.0, "boundary": 3.0, "emotion_relation": 3.0, "factuality": 3.0, "goal": 3.0, "initiative": 2.8, "interaction": 3.0, "memory_use": 2.875, "time_state": 2.875}
- 资源差：{"total_tokens_change": -10.46, "prompt_change_pct": -12.93, "completion_change_pct": 41.78, "p50_change_pct": 9.74, "p95_change_pct": -17.42}

### R3A — BATCH-R3A-DECLINE-20261028

- 计划 `refs/experiment_protocol_20261023/s1_action_live_20261028/R3A_RETEST/INTERLEAVE_PLAN.json`（sha d378b2643449fdad），计划请求 48，实际写出答卷 {'parent': 24, 'candidate': 24}
- 两臂身份：{"parent": "352c0c063e96a6dc728ca54b95c12aeaf8321aa4798d7a4e3c9f000679cb7333", "candidate": "e400275f7e272695b577f4f8536ca0aa2e999420297e2c0840f08d3d79db704c"}
- 卡文件：`benchmark/stories_v2/stageD/GRADING_CARDS_R3_DECLINE.json`
- 输入公平性审计：`R3A_INPUT_FAIRNESS_CAPTURE.json`, `R3A_INPUT_FAIRNESS_LIVE.json`
- 配对胜负：{"wins_candidate": 0, "wins_parent": 1, "ties": 20, "n": 21}
  - action_policy_r2：P 失败 0/24，C 可发首选 20，合法静默 4，严重错 0
  - action_policy_r3：P 失败 1/24，C 可发首选 24，合法静默 0，严重错 0
  - parent 九维均值：{"attribution": 3.0, "boundary": 3.0, "emotion_relation": 3.0, "factuality": 3.0, "goal": 3.0, "initiative": 3.0, "interaction": 3.0, "memory_use": 3.0, "time_state": 3.0}
  - candidate 九维均值：{"attribution": 3.0, "boundary": 3.0, "emotion_relation": 3.0, "factuality": 3.0, "goal": 3.0, "initiative": 3.0, "interaction": 3.0, "memory_use": 3.0, "time_state": 2.923}
- 资源差：{"total_tokens_change": 4.45, "prompt_change_pct": 4.59, "completion_change_pct": 2.52, "p50_change_pct": -18.96, "p95_change_pct": -18.07}

### R3B — BATCH-R3B-D03-CONTROL-20261028

- 计划 `refs/experiment_protocol_20261023/s1_action_live_20261028/R3B_CONTROL/INTERLEAVE_PLAN.json`（sha 47e086649db40481），计划请求 12，实际写出答卷 {'parent': 6, 'candidate': 6}
- 两臂身份：{"parent": "501bc426977b2e9bcee3a6d83b917bb670642f3d9d4c2e79154634f64cd65b67", "candidate": "e400275f7e272695b577f4f8536ca0aa2e999420297e2c0840f08d3d79db704c"}
- 卡文件：`benchmark/stories_v2/stageD/GRADING_CARDS_R3_DECLINE.json`
- 输入公平性审计：`R3B_INPUT_FAIRNESS_CAPTURE.json`, `R3B_INPUT_FAIRNESS_LIVE.json`
- 配对胜负：{"wins_candidate": 1, "wins_parent": 0, "ties": 5, "n": 6}
  - reference_product_v1：P 失败 0/6，C 可发首选 6，合法静默 0，严重错 0
  - action_policy_r3：P 失败 0/6，C 可发首选 6，合法静默 0，严重错 0
  - parent 九维均值：{"boundary": 2.8, "emotion_relation": 3.0, "factuality": 2.5, "goal": 3.0, "initiative": 3.0, "memory_use": 3.0, "time_state": 3.0}
  - candidate 九维均值：{"boundary": 3.0, "emotion_relation": 3.0, "factuality": 3.0, "goal": 3.0, "initiative": 3.0, "memory_use": 3.0, "time_state": 3.0}
- 资源差：{"total_tokens_change": -9.4, "prompt_change_pct": -11.35, "completion_change_pct": 33.32, "p50_change_pct": -5.33, "p95_change_pct": -29.53}

## 3. 尺子与执行器哈希漂移披露

| 文件 | 现算 sha256 | 各计划登记 | 是否一致 |
|---|---|---|---|
| `arena/lb_scorer.py` | b3dbea758be36b25 | D1:b3dbea758be3 / R3A:b3dbea758be3 / R3B:b3dbea758be3 / S1:b3dbea758be3 / S2:b3dbea758be3 / S2R:b3dbea758be3 / S3:b3dbea758be3 | 全批一致 |
| `benchmark/rubric.md` | 0b96fc5b0d8bf325 | D1:0b96fc5b0d8b / R3A:0b96fc5b0d8b / R3B:0b96fc5b0d8b / S1:0b96fc5b0d8b / S2:0b96fc5b0d8b / S2R:0b96fc5b0d8b / S3:0b96fc5b0d8b | 全批一致 |
| `arena/isolation/trusted_controller.py` | 7d5798a9c958c68d | D1:7d5798a9c958 / R3A:7d5798a9c958 / R3B:7d5798a9c958 / S1:7dc3c93a57c4 / S2:7dc3c93a57c4 / S2R:7d5798a9c958 / S3:7d5798a9c958 | 存在漂移 |
| `tools/exam_layered_score.py` | 9db7335386bf15c4 | D1:9db7335386bf / R3A:9db7335386bf / R3B:9db7335386bf / S1:93dc7a86dd29 / S2:93dc7a86dd29 / S2R:9db7335386bf / S3:9db7335386bf | 存在漂移 |
| `tools/exam_interleave_plan.py` | c27d9600c68f4081 | D1:39b49a4c9d74 / R3A:c27d9600c68f / R3B:c27d9600c68f / S1:39b49a4c9d74 / S2:39b49a4c9d74 / S2R:39b49a4c9d74 / S3:39b49a4c9d74 | 存在漂移 |
| `arena/lb_runner.py` | 4bf704c0d3e7fab5 | D1:4bf704c0d3e7 / R3A:4bf704c0d3e7 / R3B:4bf704c0d3e7 / S1:4bf704c0d3e7 / S2:4bf704c0d3e7 / S2R:4bf704c0d3e7 / S3:4bf704c0d3e7 | 全批一致 |
| `arena/protocol_public.py` | 24e47616655fce8e | D1:24e47616655f / R3A:24e47616655f / R3B:24e47616655f / S1:24e47616655f / S2:24e47616655f / S2R:24e47616655f / S3:24e47616655f | 全批一致 |

## 4. 阅读纪律

- 传输失败（T 列）≠ 协议失败（P 列）≠ 无可发正文（C 列）≠ 语义差（S 列）；用户愿不愿发（W 列）本轨一律 unknown，AI 不代投。
- 合法静默（action=no_reply）单列，不记故障、也不给满分。
- 任何跨批比较先看本表：只有卡与尺子同哈希的批次才可配对。
