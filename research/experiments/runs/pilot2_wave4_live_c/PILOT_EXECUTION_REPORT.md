# Pilot 执行报告（2026-11-08 18:08:41）

> 指标版本 `pilot_metrics_v0`；本报告只验证指标能否正确计算，**不做冠军判决**。
> 程序分/契约读数 ≠ 回复质量；开放式质量见盲评包（NOT_COLLECTED 项如实标注）。
> 客观诊断层 `benchmark_v1_gold_diag_v0`：跨四组统一的 gold 断言确定性检查（诊断用，非判决）。

**mock/capture 模式注记**：harness 组（LEGACY/CURRENT）的 `illegal_source_id` veto 来自 MockAdapter 台本引用了不存在的消息源（m0/m2），属离线伪影；DIRECT/COT 轻量契约 `{action, recommended}` 不在 harness 评分器协议键内（其 veto 为预期伪影，判据以归一化 parse/contract 为准）。真实行为读数仅来自 --live。

| 组 | 轮位 | 可用回复 | 合法不回 | parse_ok | contract_ok | 主要错误 | p50/p95 时延(ms) | prompt tok | completion tok | 程序分均值 |
|---|---|---|---|---|---|---|---|---|---|---|
| DIRECT | 260 | 254 | 5 | 0.996 | 0.996 | parse×1 | 11679/29896 | 281838 | 80741 | 0.192 |
| COT | 0 | - | - | - | - | - | - | - | - | - |
| LEGACY | 0 | - | - | - | - | - | - | - | - | - |
| CURRENT | 260 | 260 | 0 | 1.000 | 0.996 | veto:illegal_source_id:1,1×1 | 68922/131430 | 663679 | 371072 | 9.955 |

## 客观诊断（gold_diag_v0，跨四组统一）

| 组 | 可判轮 | N/A 轮 | 取消族坐实违例 | required 命中/总数 | 命中率 |
|---|---|---|---|---|---|
| DIRECT | 18 | 242 | 0 | 0/25 | 0.0 |
| CURRENT | 18 | 242 | 0 | 0/25 | 0.0 |

## 失败/异常轮位明细（如实保留，不伪造完整结果）

| 组 | case | 错误 |
|---|---|---|
| DIRECT | E325_T01 | parse |
| CURRENT | E322_T02 | veto:illegal_source_id:1,1 |

## 运行记录

- CURRENT/E322 [live] ok 
- CURRENT/E321 [live] ok 

**LIVE BLOCKED**: live precheck 未通过 ['provider 检查未通过: BLOCKED（最小请求 HTTP 503（服务错误））']
