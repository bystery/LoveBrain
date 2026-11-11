# PILOT_METRIC_DEFINITIONS · 指标定义（pilot_metrics_v0 + gold_diag_v0）

> 只验证指标能否正确计算，不做正式冠军判决。每个数字都有分母与出处；无可靠 gold 的轮记 N/A。

## 一、运行可靠性（全部四组，同一口径）

| 指标 | 定义 | 出处 |
|---|---|---|
| 完成率 | turns_done / 计划轮位（失败故事缺失轮次如实展示） | run_records.json |
| API 错误率 | attempt_failed 事件 / attempt_started 事件 | gateway_events.jsonl |
| JSON 可解析率 | parse_ok=true 的轮 / 该组全部轮 | normalize_v1 |
| 空回复率 | error_type=empty 的轮 / 全部轮 | normalize_v1 |
| 首选可提取率 | primary_text 非空或合法 no_reply 的轮 / 全部轮 | normalize_v1 |

## 二、客观质量（benchmark_v1_gold_diag_v0，跨四组统一）

- 数据源：gold sidecar v2（`benchmark/stories_v2/GOLD_SIDECAR/GOLD_*.json`，AI 出题断言
  ——非人工真值，此局限如实声明）。
- **取消族坐实违例**：primary_text 命中"取消/否定族原子"（status ∈ {cancelled, superseded,
  retracted, voided, revoked, invalidated, negated, erroneous} 或 negation=true）的
  affirm_markers 或原子 value 逐字（≥4 字）→ 计 1 违例。
- **required 命中率**：required_this_turn 且 active 族（asserted/confirmed/active/fulfilled/
  resolved）原子中，primary_text 命中 markers/value 的比例；未命中=miss。
- **N/A 纪律**：gold 空轮、无断言轮、无 applicable 断言轮 → N/A，不自动算正确。
- 定位：诊断指标，不替代权威评分器（LEGACY/CURRENT 的契约计分仍以 lb_scorer 为准），
  不做冠军判决。

## 三、开放式回复质量

- 盲评包（`PILOT_BLIND_REVIEW.md`）：12 个固定位置（等间距公式 floor(j(n+1)/(k+1)+0.5)，
  固定种子 20261107+zlib.crc32 打乱）；用户填"最愿发送/多条不错/全部不愿发/理由"；
  未填 = NOT_COLLECTED，**Worker 不代投**。身份映射只在运行目录 `blind_key.json`。
- AI judge 诊断（Phase 3 才引入）：须注明 judge 身份与 W20-03 资格限制
  （boundary/memory 停用；severe 只作索引）；不得冒充人工评分。

## 四、资源与稳定性

- API 总请求数（attempt_started）、input/output tokens（usage.source=api_usage 时为
  服务端真值；mock 下为字符估算，标注 source）、每成功回复总 token、请求时延
  p50/p95（calls.json latency_ms）、各组失败数、内部附加调用开销（gateway_calls）。

## 已知读数局限（Phase 1 mock 产物）

- mock/capture 模式下：harness 组 `illegal_source_id` veto = MockAdapter 台本引用
  不存在消息源的伪影；DIRECT/COT 的 harness 评分器 veto = 轻量契约不在其协议键内的
  预期伪影；tokens 为字符估算（source=kotlin_len_chars）。**真实行为读数仅来自 --live**。
