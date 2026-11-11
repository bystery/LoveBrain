# SELECTION_ANNOTATOR_BRIEF · 批 2 标注简报（wave5_selection，2026-11-09）

> 适用：Phase 3 第 2 步 SELECTION 40 篇的 **Gold Annotator（角色 B）**。
> 规范来源：本简报 + `phase2/GOLD_ANNOTATION_GUIDE_V1.md` + `phase2/ATOMIC_ASSERTION_GUIDE_V1.md`
> + `benchmark/stories_v2/GOLD_SCHEMA.md`。与旧文件冲突时以 V1 规范为准。

## 0. 角色隔离（硬约束）

你不得读取：`logs/`、`candidates/`、任何判决/评审/审计文档（含 `STORY_BATCH_REPORTS/`、
phase3 对账与验收报告）、`GOLD_SIDECAR/`（本批 sidecar 由收口统一构建）、任何其他 episode
的 reference/audit 正文（同批你标注的篇目除外）。
允许读：你名下篇目的 episode.json / timeline.json / exposure.json（作者已冻结）、
三份 V1 规范、本简报、TOPIC_RUBRIC_V1 本专题条目。

开工 ack：写 `refs/benchmark_v1_20261107/phase3/isolation_acks/<你的ACK_ID>.json`
（role=gold_annotator，declarations 加 no_run_results_read=true——本批无运行结果可读）。

## 1. 产出（每篇两件，写进 `benchmark/stories_v2/wave5_selection/<EID>/`）

- `reference.json`：`{"story_id":"E3xx","turns":{"<EID>_T01":{卡},...}}`，卡字段逐项对齐
  wave4 形状（case_id / current_action / acceptable_actions（3–4 条，不规定唯一措辞）/
  applicable_dimensions / required_memory_atoms / private_guidance / unknowns /
  protected_expressions / forbidden_factual_claims / may_decline）。
- `audit.md`：8 节模板（0 性质与暴露 / 1 生活动机 / 2 连续性与回扣 / 3 ME 历史与边界 /
  4 no_reply 与媒体局限 / 5 参考卡质量 / 6 已知风险 / 7 出口检查）。

原子要求（ATOMIC_ASSERTION_GUIDE_V1 全文适用）：
- 每轮 2–5 条原子（每篇 24–50 条量级）；quote 逐字命中对应 message；
- **每个原子必须带 `affirm_markers`**（嵌套组结构：`[[备选1,备选2],...]`，2–4 组、
  每组 1–3 个 2–12 字备选短语，覆盖该事实被坐实时的典型措辞）——评分器 marker
  通道依赖此字段（全库旧原子 92% 缺失，判分只剩 value 子串通道，这是 Pilot
  required 0/46 的根因之一；批 2 起修复）；
- 状态族：asserted/confirmed/active/fulfilled/resolved + cancelled 族（取消/否定/作废）；
- 跨轮回指位（作者 timeline 标注的短距/长距位）必须有原子并标注 required_this_turn；
- unknowns / forbidden_factual_claims 按"提及≠坐实"登记（提及方不是事实方）；
- 疑问句提出的消息不得坐实其内容（原子 value 不得把疑问当 asserted 事实）；
- **Gold 不规定唯一回复措辞**；acceptable_actions 是策略集合不是句子。

## 2. T02 专属质量门（指导书 P3-1.5，逐篇必填）

每篇 T02 在 reference.json 顶层加 `"t02_signal_gate"` 对象（audit.md §5 引用）：

```json
"t02_signal_gate": {
  "signal_rounds": [{"turn":"E3xx_T03","signal":"...原句摘录","strength":"weak|medium|strong"}],
  "counter_signal_rounds": [{"turn":"E3xx_T07","type":"withdraw|cooldown|topic_shift","signal":"..."}],
  "initiative_probe_suitable": "E3xx_T06（适合推半步的轮）",
  "retreat_room_required": true,
  "overstep_turn": "E3xx_T09（此时推进会越界的轮）",
  "customer_service_examples": ["...（客服腔回复示例，判 0 档）"],
  "safe_but_flat_examples": ["...（安全但无张力的示例）"],
  "recovery_check_turn": "E3xx_T10（对方收信号后应调整的轮）"
}
```

判分纪律：**禁止暧昧关键词命中替代行为判档**；"没越界"不等于调情高分。

## 3. T09（E374，24 轮）专属

跨月状态机：每轮状态变化（排班/调换/请假/恢复）都要原子化并带日期来源 quote；
日期全部可验算（星期几与日期一致）；取消-改期链用 cancelled 族 + 新 asserted 原子配对。

## 4. 自查

- reference.json JSON 可解析；turn 键 = episode turns 的 case_id 一一对应；
- 全部原子 quote 用 `python` 对 episode.json 逐字回验（strict 命中，0 失败才交）；
- may_decline 与 episode input.may_decline 一致；T02 篇有 t02_signal_gate；
- audit.md 8 节齐全（grep '^## ' 恰 8 个）。

## 5. 交付报告

篇目清单（EID/原子数/unknowns 数/forbidden 数/T02 门有无）、quote 回验结果（N/N）、
8 节检查结果、隔离声明。
