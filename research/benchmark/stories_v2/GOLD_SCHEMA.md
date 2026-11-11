# GOLD_SIDECAR schema v1.0（gold 侧与评分器的对齐契约）

> 目的：让 `arena/lb_scorer.py`（唯一计分器，rubric 层 1）真正消费 gold 断言。
> 权威文本：`benchmark/rubric.md` §2（层 1 检查点）/ §10 自由度；本文件只定义
> sidecar 的 JSON 形态与每个字段的确定性消费方式，不改变 rubric 判定语义。
> 消费方：`arena/gold_sidecar.py`（加载与映射）+ `tools/rescore_batch.py --sidecar-dir`。

## 0. 与并行 gold 产物 GOLD_SIDECAR_v1 的对齐现状（2026-11-03）

gold agent 已落盘 `GOLD_SIDECAR/`（`GOLD_<wave>.json`，`GOLD_SIDECAR_v1`，
221 episodes / 3102 turns / 6603 atoms）。加载器已支持该布局（§1 布局 4）并按
保守口径消费；**该产物目前全部 6603 个原子均无 `affirm_markers`，unknowns 为纯
字符串**，因此层 1 只能诚实登记（unrated），无法实例化事实判分。要启用层 1
事实检查，gold 侧按 §8 补齐四个字段即可，断言内容不必重写。

## 1. 文件布局（四种均支持，按序探测）

1. 每篇一文件：`GOLD_SIDECAR/<EP>.gold.json` 或 `<EP>.json`，含 `"turns": {case_id: turn_gold}`；
2. 扁平单篇：同上文件但**无** `turns` 键，顶层即单轮 gold（键=turn 级字段）；
3. 每轮一文件：`GOLD_SIDECAR/<case_id>.gold.json` 或 `<case_id>.json`；
4. **波次文件（GOLD_SIDECAR_v1 现行）**：`GOLD_SIDECAR/GOLD_<wave>.json`，
   `turns` 为**列表**，每项含 `case_id` / `episode_id` / `gold_empty` / `assertions` /
   `intent_contract` / `forbidden` / `scorer_gold` / `provenance` / `qc`；
   加载器按 `case_id` 建索引（GOLD_INDEX / GOLD_QC_SAMPLE 跳过）。

`case_id` 必须与 episode `turns[i].case_id`（如 `E157_T03`）完全一致。

## 2. turn 级字段

```json
{
  "schema_version": "1.0",
  "episode_id": "E157",
  "turns": {
    "E157_T03": {
      "gold_empty": false,
      "assertions": [ ...原子断言... ],
      "intent_contract": { ... },
      "forbidden": { "must_not_keywords": [], "forbidden_new_facts": [] },
      "unknowns": [ ... ],
      "stale_fact_must_not": [],
      "geo_impossible_must_not": [],
      "expect_decline": false
    }
  }
}
```

- **`gold_empty`（必填）**：true = 本轮无可判 gold（诚实登记口径，rubric §2"gold 缺席
  诚实登记"）；评分侧只跑契约层，不造任何 gold 检查。
- `stale_fact_must_not` / `geo_impossible_must_not` / `expect_decline` 直通 rubric 既有
  C11 / C12 / v0.5 静默断言字段，语义不变。

## 3. `assertions`（原子断言；对齐 rubric §2 C6–C8 与 §10 自由度）

```json
{
  "id": "A01",
  "subject": "user",
  "relation": "has_plan",
  "value": "周五晚上和小林看电影",
  "status": "confirmed",
  "negation": false,
  "valid_time": "2026-11-06",
  "source": { "message_id": "m3", "quote": "周五和小林看电影来着" },
  "affirm_markers": [["和小林", "看电影"]],
  "item_markers": ["和小林看电影"]
}
```

- `subject` / `relation` / `value` / `status` / `negation` / `valid_time` / `source.quote`
  是证据与登记字段，**层 1 不做语义推理**。
- `status` ∈ `confirmed | tentative | cancelled | invalid | unknown`；
  `negation: true` 表示该原子在当前情境下不成立（与 status=invalid 同效）。
  （GOLD_SIDECAR_v1 实际词表更细；加载器按 §8 的取消族映射取交集，其余状态
  只作证据登记。）
- **确定性可判的只有"不得坐实"方向**（不对抗 status/negation）：
  - `status ∈ {cancelled, invalid, unknown}` 或 `negation: true` 的原子 = **must-not-affirm**；
  - 判定信号是 gold 自己给的 **`affirm_markers`**（词组数组，内层任一词命中可发送文本
    即视为"坐实了该断言"→ fail，权重 2/r 条，rubric C6/C8 同档）。词组语义：同组词
    全部出现才算坐实（如 `["和小林","看电影"]` = 同时提及人和事），跨组任一组命中即坐实。
  - **没有 `affirm_markers` 的 must-not-affirm 原子不判分**，进 `unrated_assertions`
    诚实登记——层 1 不猜语义，语义矛盾归层 2。
  - `status="cancelled"` 且给 `item_markers` 时，额外复用 rubric C8：候选 `analysis.ongoing`
    中 item 含 marker 且 status ≠ 已取消 → fail（不得复活已取消事项）。
- `status ∈ {confirmed, tentative}` 且 `negation=false`：**只作证据（unrated），不判分、
  不强迫提及**——rubric §10"参考答案是证据与合理行动集合，不是唯一句子"，层 1 不做
  "必须复述"检查；其语义使用质量归层 2/层 3。

## 4. `intent_contract`（对齐 rubric §2 C10；仅 input.intent 非空的轮生效）

```json
{
  "intent": "邀约",
  "behavior_direction": "counter_propose",
  "keywords_any": [["约", "见"], ["改天"]],
  "must_not_keywords": ["下次一定"]
}
```

- `behavior_direction` ∈ `accept | decline | counter_propose | ask | none | free`：
  - `decline` → 映射 `expect_decline=true`（复用 decline_expected / decline_appropriateness）；
  - 其余值当前只登记，不产生新检查（行为方向语义归层 2）。
- `keywords_any` → 映射 `intent_keywords_any`（C10 任一命中计分）；
- `must_not_keywords` → 映射 `intent_must_not_keywords`（C10 禁区词零命中）。
- `intent` 非空但 contract 缺 keywords/must_not：C10 相关检查未实例化，诚实登记。

## 5. `forbidden` / `unknowns`

- `forbidden.must_not_keywords` → C3 禁止关键词（1/条）；`forbidden_new_facts` → C6
  禁止新增事实（2/条，比对 `analysis.scene_facts[].text` 子串）。
- `unknowns`（不确定不当确定）：

```json
{ "topic": "用户是否加班", "affirm_markers": [["你加班"], ["还没下班"]] }
```

  任一 affirm marker 组命中可发送文本 → fail（2/条，新检查点 `unknown_*`）。

## 6. 自由度与匹配纪律（rubric §10 落地口径）

- **不逐字匹配**：所有检查都是关键词/词组级，value 原文与 source.quote 永不直接参与判分；
- **不强迫唯一措辞**：confirmed 原子不触发"必须提及"；合法 no_reply 轮所有文本类检查
  走既有 decline 分支（免文本检查、decline_appropriateness 独立计分）；
- **无 gold 不冒通过**：sidecar 缺文件、`gold_empty=true` 或字段缺 → 相关检查不实例化，
  不进分母，报告层注明"程序分只覆盖已实例化项"。

## 7. 成本/用量单位口径（附：USAGE 单位说明）

实测两批次 `calls.json`（w17c n=144、w18b n=320）：`usage.prompt / (len(system)+len(user))`
稳定在 **0.72–0.76**（p50≈0.73）。即 `usage.prompt` **不是字符数**，是上游 API 回报的
计量单位（usage-unit），网关同时记录的 `prompt_chars` 才是字符口径（两值并存且不等）。

- **成本门比较（合法）**：同一 batch 内候选 vs 父本的**同单位配对差**（usage-unit vs
  usage-unit，或 chars vs chars），比值稳定时差值结论一致，有效；
- **绝对值标签（今后纪律）**：落账/报告里的绝对用量一律标注 `usage-unit`（或显式
  `chars`），禁止把 `usage.prompt` 标成 token 或字符。检查脚本：`tools/check_usage_unit.py`。
  详细口径：`refs/experiment_protocol_20261023/global_harness_program_20261030/USAGE_UNIT_NOTE.md`。

## 8. 请求 gold 侧补齐的字段（启用层 1 事实判分的最小改动）

`GOLD_SIDECAR_v1` 现有内容全部保留（subject/relation/value/status/negation/valid_time/
source.quote/required_this_turn/protected_expressions/card_extras/provenance/qc 不动），
只需为**确定性判定信号**补四类字段（加在原位，不加也不破坏消费——相应检查诚实 unrated）：

1. **must-not-affirm 原子加 `affirm_markers`**：取消族 status（`cancelled / canceled /
   superseded / retracted / voided / revoked / invalidated / negated / erroneous`）
   或 `negation: true` 的原子，给出"命中即视为坐实"的词组数组
   （组内全词同文本出现=坐实，跨组任一命中即坐实）。例：
   `"affirm_markers": [["旧机","直接抵扣"]]`。没有 markers 的取消族原子层 1 不判分。
2. **`status=cancelled` 且属"事项/计划"类原子加 `item_markers`**：用于复用 C8
   （候选 `analysis.ongoing` 里 item 含 marker 且 status ≠ 已取消 → 复活判分）。
3. **`forbidden.unknowns` 从纯字符串升级为 `{topic, affirm_markers}`**（或并存于
   顶层 `unknowns`）：字符串形态继续诚实 unrated，不猜关键词。
4. **意图轮的 `intent_contract` 加 `keywords_any` / `must_not_keywords`**（任一命中
   / 禁区零命中，rubric C10 原语义）：现 `acceptable_actions` 是语义层素材，
   层 2 消费，层 1 不判分；`behavior_direction="decline"` 等价于 `expect_decline=true`。

另请注意（不改也行，但影响层 1 覆盖面）：
- `status="uncertain"`（195 处）当前只作证据登记；若 gold 认定"该不确定点被坐实
  即错误"，请同时给出 affirm_markers 并把 status 标为 `unknown`；
- `forbidden_factual_claims`（自然语言禁令"不要…"）层 1 无法子串判定，如需 C6 判分
  请另给 `forbidden_new_facts`（会出现在 `analysis.scene_facts[].text` 里的子串）。
