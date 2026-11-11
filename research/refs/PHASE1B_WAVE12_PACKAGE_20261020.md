# Wave12 收口报告：执行器 fail-fast 重写（v10 独立复核执行令）

日期：2026-10-20 深夜。范围：**只动执行器、其测试与方案失败/统计/配置口径**；
检索、表达、剧本及全部已签收项未触碰。本批零真实调用。

## 1. 签收与边界

- 表达注入/审计、剧本/软关键词两组整改按复核签收关闭，本轮零改动。
- 已登记限制（单字召回等）与正式晋级冻结维持原状。
- v1 执行器两份演练账（dryrun_v2 / drill_v2）**保留原始记录不改写**，
  各加 `_OVERTURNED_BY_V10_REVIEW.md` 标记：其"调用前注入失败 + 作废后继续"
  口径被独立反例（67 次调用尝试）推翻，不再作为通过证据引用。

## 2. P0 修复：66 槽 ≠ 66 次调用 → 真实事件为额度权威

**根因（复核 §3.3 采纳）**：`stop_after=turn1, resume=(turn1>1)` 不核对
checkpoint 前驱，checkpoint 落后时 resume 顺带重跑历史轮；`_consumed` 只数
槽账行数；`done_keys` 连 occupied 也跳过；expr 断言空列表 `any()` 恒假。

**v2 执行器（tools/l3b_three_arm_executor.py）修复**：

1. **fail-fast（复核 §5-1）**：传输失败/网关内部失败/配对不符/注入断言不符
   → 槽 failed 留证 + 整批立即终止。`--resolve-failed` 移除（传入即
   `RECOVERY_DISABLED`）；failed 或 occupied 未决的批次重入一律
   `BATCH_ABORTED_TERMINAL`；不自动新建批次。
2. **checkpoint 前驱断言（生成前，不占槽不生成）**：turn1>1 时 checkpoint
   必须存在且 `turns_done == turn1-1`；缺失/过旧/超前 → `CHECKPOINT_MISMATCH`
   拒绝；turn1==1 不得有残留 checkpoint。
3. **额度权威 = 真实网关 attempt_started**：每槽占用前核对真实 started 计数，
   第 67 次真实请求发出前拒绝（拒绝路径零槽账、零调用）。
4. **每槽配对校验（复核 §5-3）**：以 (run_id, case_id, attempt_id) 对真实
   started/终态配对——恰 1 次 started、case=目标 case（case 不符=隐藏重跑）、
   有且仅有一个终态、无网关失败事件；另核 manifest 的 run_id 归属/case 一致/
   gateway_calls==1/audit_errors 空。任一不符即槽 failed 停批。
5. expr 断言修空列表漏洞（`not counts or any(...)`）。

## 3. P1 修复一：汇总分类（复核 §4.1）

解析成功与否**独立判断**（`lb_scorer.extract_json_block`），九类互斥分列：
`blank` / `parse_fail_not_json` / `schema_broken` / `unknown_field` /
`unauthorized_no_reply` / `action_violation` / `scorer_crash` / `other_veto` /
`legal_no_reply` / `reply`。**前七类一律不算合法收尾**——复核三夹具的旧账
（坏 JSON 与越权不回被计成 no_reply、parse_err=0）不再可能重现。
summary 增列 `real_started / real_completed / real_failed / pairing_verified /
verified_complete`，槽状态汇总与真实调用计数并列（66=66=66 且逐槽配对全验证
才算 verified_complete）。

## 4. P1 修复二：实际配置锁定（复核 §4.2）

- batch_config 从适配器**真实配置源**建立：`endpoint_env_name=LB_API_BASE`
 （不再记错 LB_ENDPOINT）、`endpoint_base`、`key_set`（只记是否设置，不落
  密钥）、`model_effective`、`temperature_effective`（openai_compat 实际出站
  0.7 归一口径）。
- 修复 `slots.setdefault("config", cfg)` 不生效的 bug（账面空配置根因）。
- 再次进入批次**逐项比对**：adapter/端点/模型/温度漂移 → `CONFIG_MISMATCH`
  拒绝（mock/真实混跑被锁死）；真实适配器缺 LB_API_BASE 在首调用前
  `CONFIG_MISSING` 拒绝。
- 版本清单补齐（复核 §4.2 末条）：params×3、expr_audit 工具、三臂方案 v3
  散列入 FROZEN_SHA；执行器自身 sha256 动态记录入 batch_config。

## 5. 演练注入改网关内部（复核 §5 反例矩阵）

`--chaos-attempt N`：第 N 次 `generate` 在适配器**内部**抛 TimeoutError
（真实 attempt_started + attempt_failed 事件），不再调用前注入。

**准入矩阵实测（test_three_arm_executor.py，全过）**：

| 情形 | 结果 |
|---|---|
| 干净 66 槽 | 恰 66 真实 started / 66 completed / 0 failed，逐槽一对一配对，verified_complete=true |
| 网关内部超时（第 13 次） | 真实 attempt_failed 事件、槽 failed 留证、立即停批；同配置重启 `BATCH_ABORTED_TERMINAL`；`--resolve-failed` → `RECOVERY_DISABLED`；重启后真实 started 仍 13（无隐藏补跑） |
| 中断 occupied | `BATCH_ABORTED_TERMINAL`，不跳过、不猜成功、不自动重发，summary occupied_slots 如实列示 |
| checkpoint 缺失/落后/超前 | 三反例均生成前 `CHECKPOINT_MISMATCH` 拒绝；不占槽、零真实调用 |
| 第 67 个真实请求 | 66 条真实 started 后新槽在发出前 `CAP_EXCEEDED`，零槽账零调用 |
| 坏 JSON/空白/越权不回/合法不回 | 四类互斥分列，前三类+越权均不计合法收尾 |
| adapter/端点/模型/温度漂移 | 批次重入 `CONFIG_MISMATCH` 拒绝 |

**正式演练账**：`logs/l3b_66_dryrun_v3`（66/66/66，配对 66/66，verified）与
`logs/l3b_66_drill_v3`（13 started / 12 completed / 1 failed，重启阻断留档）。
原始网关事件独立复数与执行器汇总一致（66=66、13/12/1）。

## 6. 回归与包

- 13 套离线回归全 PASS（含重写的 test_three_arm_executor 反例矩阵）。
- 方案升级 v3（refs/L3B_THREE_ARM_PLAN_20261020.md）：§0 fail-fast 策略、
  §3 checkpoint/配对/配置锁定行、§4 分类口径——**执行前不再改写**。
- 成果包：lovebrain_review_v11_20261020.zip（见 sidecar manifest sha256）。

## 7. 66 次真实调用授权清单（最终版，一次提交）

- 方案：refs/L3B_THREE_ARM_PLAN_20261020.md（v3，散列已冻结入执行器）。
- **66 次硬上限、无重试额度、fail-fast 无恢复**：额度权威=真实网关
  attempt_started；第 67 次真实请求发出前拒绝；失败批次整批终止、不可作废
  续跑；mock/真实混用与端点/模型/温度漂移被配置锁拒绝。
- 三臂 A1（父）/A2（检索 v2.2）/A3（检索+表达）× NCA 6 位 + NCB 5 位 × r1/r2；
  组序轮换 A1A2A3→A2A3A1→A3A1A2；逐槽 (run_id, case_id, attempt) 配对入账。
- 获得授权后执行固定对照；下一次交付真实回复质量结果（主表按方案 §4 多列口径）。
