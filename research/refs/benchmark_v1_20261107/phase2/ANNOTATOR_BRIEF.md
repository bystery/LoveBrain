# Benchmark V1 · Gold 标注简报（ANNOTATOR_BRIEF v1，2026-11-08）

> 适用：wave4_dev 已冻结故事的 Gold Annotator（角色 B）。角色隔离：你不得读取任何候选
> 在这些故事上的运行结果/失败案例/评审点评（logs/、candidates/、判词）；你知道的只有
> 故事本身与标注规范。开工登记 GENERATION_ISOLATION_LEDGER.json（annotator 项）。

## 输入（允许读）

- 你负责的篇目：`benchmark/stories_v2/wave4_dev/<EID>/episode.json` + `timeline.json`
  （已冻结正文，不得改动 message 文本/clock/轮序）；
- `refs/benchmark_v1_20261107/phase1/TOPIC_RUBRIC_DRAFT_V1.md` 对应专题条目；
- `benchmark/stories_v2/GOLD_SCHEMA.md`（sidecar 对齐契约——affirm_markers/item_markers/
  unknowns 对象化的确定性消费方式）；
- 格式样例：`benchmark/stories_v2/wave3/E117/reference.json`（只学结构）。

## 输出（每篇）

1. `benchmark/stories_v2/wave4_dev/<EID>/reference.json`：
   `{"story_id":"E3xx","turns":{ "<E3xx_T01>": <卡>, ... }}`
   卡字段（全 9 项必填，缺一不可）：
   - `case_id`
   - `current_action`：本轮实际在发生什么（一句话）
   - `acceptable_actions`：**≥2 条**允许的不同正确行为（自然短回/调侃/反问/合法不回都可；
     约束事实、意图、场景、分寸，不规定唯一措辞——rubric §10 自由度）
   - `applicable_dimensions`：**≥3 个真实适用**的九维（goal/factuality/attribution/
     time_state/boundary/memory_use/interaction/initiative/emotion_relation）；
     不为凑数硬塞
   - `required_memory_atoms`：原子数组（结构见下）；本轮必须正确处理的事实才标
     `required_this_turn=true`；无则空数组（不要造原子）
   - `private_guidance`：给评审的私下提示（不进候选输入面）
   - `unknowns`：**对象数组**（每项含 topic/note/markers——按 GOLD_SCHEMA §8，
     禁止裸字符串）
   - `protected_expressions`：保护表达（她明确说过的边界话、不想被提起的事）
   - `forbidden_factual_claims`：禁止坐实的说法（未定时间、单方猜测、取消事项）
   - `may_decline`：与 episode input.may_decline 一致的布尔

2. 原子断言（写入 required_memory_atoms / 必要时 forbidden 引用）：
   ```json
   {"atom_id":"E3xx_T03_A01","subject":"HER|ME|SHARED|THIRD|PUBLIC","relation":"...",
    "value":"...","valid_time":"2026-11-12","status":"asserted|tentative|confirmed|
    cancelled|superseded|...","negation":false,
    "source":{"kind":"message","case_id":"E3xx_T03","message_id":"m0",
              "quote":"<逐字来自该轮 input.messages>"},"required_this_turn":false}
   ```
   十条纪律（指导书 §3.5）：一原子一可核验事实；必有真实来源；quote **逐字**匹配
   对应消息；不引用未来消息；subject 四分（HER/ME/THIRD/SHARED，公共常识 PUBLIC）；
   区分 确定/暂定/取消/无效/未知；不把推测升级为事实；不要求回复逐字复述；
   不为凑数重复拆分；无据不建。
   **取消/否定原子与"取消事项"必须带可靠 `affirm_markers`/`item_markers`**
   （v2 enrichment 字段，直接写在原子上：`"affirm_markers":["...","..."]`，
   每个都是文本中可逐字出现的短语）；所有原子 `valid_time` 用故事内真实日期，禁止裸"明天"。

3. `benchmark/stories_v2/wave4_dev/<EID>/audit.md`（标注侧审计草稿）：
   事实一致性自查记录（每个关键事实→轮位→quote 核对结果）、回指位清单、专题判别力
   自评、遗留风险。

4. 更新 `exposure.json`：`status` → `"annotated_pending_audit"`；补 `domain`、
   `primary_topic`、`lineage_group_id`（T10 孪生用 brief 分配的组号）、`frozen_at`（标注完成时刻）。

## 质量红线

- 卡与正文矛盾=最大事故：每个原子 quote 必须能在 episode.json 对应轮 messages 里逐字找到
  （自检脚本：遍历原子→quote in 该轮消息文本拼接）。
- unknowns/取消原子缺 markers = 层 1 无法实例化 → 该轮事实判分将诚实 N/A，别让这种
  可避免的 N/A 发生。
- 不看任何模型输出；Gold 不针对任何 harness 的已知弱点定向加严（防偏向）。

## 交付报告

篇目清单、每篇卡数/原子数/取消原子数/unknowns 数、quote 逐字核验结果、遗留问题。
