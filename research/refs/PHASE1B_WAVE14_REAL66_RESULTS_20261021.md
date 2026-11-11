# Wave14：66 次真实调用实跑——调用/格式事实（收口版）

日期：2026-10-21。批次 `l3b_66_real66_v1`。用户已明确授权（66 次硬上限，方案 v3）。
执行器=补丁后版本 `2d3e5724…c68849`（10/10 配对验收全过），adapter=openai_compat，
model=dsf，端点 `http://127.0.0.1:17770/v1`，出站温度 0.7，fail-fast 无重试。
**全部数据为本地批次账 + 原始事件日志实测，非估算。**

> **本版为收口版（2026-10-21 v13 复核 §7 整改）**：质量初审、匿名对照、三臂结论等
> 已被 v12 独立复核推翻或降级的旧正文，全部移入
> `refs/PHASE1B_WAVE14_REAL66_RESULTS_ARCHIVED_20261021.md`（仅供追溯，**不得引用**）。
> 本文件只保留一套经独立复核签收的有效表；失效结论以
> `refs/REAL66_V12_INDEPENDENT_REVIEW_20261021/` 为准。

## 1. 有效表：调用 / 格式 / 合法动作（v12 复核签收）

| 指标 | A1 父版本 | A2 检索 | A3 检索+表达 |
|---|---|---|---|
| 槽位（NCA 6+NCB 5 × 2 重复） | 22 | 22 | 22 |
| 真实网关 started / completed / failed | 22 / 22 / 0 | 22 / 22 / 0 | 22 / 22 / 0 |
| 逐槽 (run_id, case_id, attempt) 严格配对 | 22/22 | 22/22 | 22/22 |
| 空白 / 非 JSON / schema 破损 / 未知字段 | 0 / 0 / 0 / 0 | 同左 | 同左 |
| 越权不回 / 动作违规 / scorer 崩溃 | 0 / 0 / 0 | 同左 | 同左 |
| 首选可发送（recommended 非 null） | 22/22 | 22/22 | 22/22 |
| 任一备选可用 | 22/22 | 22/22 | 22/22 |

- 批次总计 **66 started / 66 completed / 0 failed**，无隐藏补跑，无重试消耗。
- 唯一非满分槽：A2|NCA|r2|t06 = 8.33，原因 `style_diversity`（格式风格检查项，
  非内容缺陷）；该槽无 veto、首选正常。

## 2. 已失效内容（详见 v12 复核与归档）

以下旧结论**不再有效**，明细见归档文件与 v12 复核报告：

- "62/66 原样愿发" → 撤回（独立初审 57/5/4，仍不代替用户语气裁决）；
- "只有一处真 F2 / 没有主体与时地错误" → 撤回（备选 54 条字段级登记；
  记忆污染链实测成立）；
- "12/12 检索采纳"口径 → 错误（分母口径错误；t04 污染链成立）；
- 8 组匿名材料 v1 → 撤换（现行为 `REAL66_ANON_GROUPS_V2_20261021.md`，
  生成器 v3 含 id/speaker/intent/回复双侧绑定断言）；
- 三臂方向性结论 → A2/A3 均不晋级，A1 保留开发父版本。

## 3. 证据清单（原始证据不受复核影响）

- 批账：`logs/l3b_66_real66_v1/`（slots.json / summary.json / batch_config.json /
  runs/<arm>/<ep>/<rep>/ 每槽 calls.json+manifest.json+scoring.json+reply_raw.txt+
  gateway_events.jsonl；A2/A3 另有 _sandbox/retrieval_trace.jsonl 逐轮追踪）。
- 抽取产物：`logs/real66_v1_all_recommended.json`（66 首选）、
  `logs/real66_v1_all_alternatives.json`（431 备选）、`logs/real66_v1_expr_audit.json`
  （四族审计）、`tools/gen_anon_groups.py`（匿名组生成器 v3，可复现）。
- 执行账：执行器散列 2d3e5724…（补丁后）、方案 v3 散列冻结校验通过、
  66 started=66 completed=66 配对 verified_complete。
