# cases.jsonl 机器可读案例结构（定稿 v1.0 · 规则冻结）

> **冻结状态：规则冻结，登记表=cases-freeze-draft.jsonl**（60 行，权威登记表；本文件冻结其字段结构与配额口径）。对应指导书 §4.4 案例结构与 §9 `cases.jsonl`。前版草案 `cases-schema-draft.md` 保留作历史对照，以本文件为准。文中 `TPL-` 示例均为模板示意（非真实数据）；真实值以登记表 60 行为准。

## 0. 字段表

| 字段 | 类型 | 必填 | 说明 | 示例值（TPL） |
|---|---|---|---|---|
| `case_id` | string | 是 | 全局唯一主键，格式 `uv1-E{n}-{三位序号}`，序号 001–012 | `uv1-E2-007` |
| `source` | string | 是 | 素材来源枚举 + 定位后缀 | `stories_v2_gold:research/benchmark/stories_v2/wave5_selection/E340/episode.json#E340_T10` |
| `scenario_type` | string | 是 | 场景类型枚举（按指标，见 §2） | `reschedule` |
| `conversation_history` | array | 是 | **形态一（登记表实际形态，权威）**：指针对象数组 `[{pointer, turn_case_ids[], note}]`，执行侧装配输入时按指针展开为按时序的 `{speaker, text}` 全文；**形态二（内联，允许）**：`[{speaker, text}]` 全文数组，speaker ∈ `user / partner / note`，≥2 轮。E4 占位行为 `[{pending:true, requirement}]` | `[{"pointer":".../E340/episode.json","turn_case_ids":["E340_T01",…,"E340_T09"],"note":"历史=T01–T09 input.messages 时序拼接"}]` |
| `incoming_message` | string 或 object | 是 | 字符串全文，或指针对象 `{pointer, turn_case_id}`（登记表实际形态）；E4 占位行为 `{pending:true, requirement}` | `{"pointer":".../E340/episode.json#E340_T10/input.messages","turn_case_id":"E340_T10"}` |
| `user_goal_tone` | string | 是 | 本轮用户目标/语气声明（自由文本）；即指导书 §4.4 的 `user_goal/tone` 字段（JSON 键取下划线形式） | `TPL：轻松暧昧，暂不表白` |
| `public_vs_private_context` | object | 是 | `{public: string[], private: string[]}`；private 为用户私人备注（不得泄露项的信息源）；登记表允许条目为指针串（如 `用户私人备注 pointer：…#card_extras.private_guidance`），执行侧按指针展开 | `{"public":["TPL：双方已约看电影"],"private":["TPL：我挺喜欢她，但现在不想表白"]}` |
| `must_use_facts` | string[] | 是 | 每条单一可核查事实（E4/E5 允许空数组）；评审侧金标，禁止出现在任何生成代理输入中；登记表条目格式 `ATOM_ID｜描述｜建立来源（距本轮距离）｜原文quote` | `["E340_T10_A00｜街道汇演定在 11 月 29 日周日…｜建立于 initial_kb（距本轮 10 轮）｜原文quote：\"街道汇演定在11月29日周日下午\""]` |
| `must_not_assert` | string[] | 是 | 禁止断言清单；E3 的错误判定依据；语义条目允许非逐字形态（如"不得声称她哥已知道…"） | `["TPL：已订票","TPL：表白/喜欢对方很久"]` |
| `latest_valid_state` | object 或 null | E2 必填，其余可空 | `{fact, supersedes, invalidated[]}`：当前有效事实、被其取代的旧表述、作废信息列表（含旧事实诱饵）；**冻结扩展**：条目可携带 `atom_id` 等追溯键（schema 三键之外的增补，供评审回溯 Gold 侧车） | `{"fact":"TPL：观影时间=下周日","supersedes":"TPL：周六晚","invalidated":["TPL：观影时间=周六晚"]}` |
| `gold_reference_or_rubric` | object 或 null | 否 | `{reference?, rubric?[]}` 参考回复或评分要点清单；为 null 时该题在依赖金标的指标上可判 UNRATED | `{"rubric":["TPL：提到下周日","TPL：不提订票","TPL：语气轻松"]}` |
| `eligible_metrics` | string[] | 是 | 本题适用的指标子集，首元素 = 主指标；本冻结集每题恰一个主指标 | `["E2"]` |
| `exposure_status` | string | 是 | 枚举：`public_ok`（可随实验全部公开）/ `private_only`（可用不入公开资产）/ `unrated`（标注未完成）/ `pending_generation`（E4 占位专属，回填后改判） | `public_ok` |
| `story_id` | string | 是 | 来源故事标识，统计去重用；缺失者不得入冻结集 | `E340` |

登记表冻结扩展键（§0 表之外的已登记键，冻结认可，删除即违规）：

1. `_sel_note`：选题理由一句话（确定性排序键的落痕，见 cases-freeze-notes.md §2）。
2. `generation_kit_ref`：仅 E4 占位行，指向 `e4-generation-kit.md §1` 槽位与生成硬性要求。
3. `pair_tag`：仅 E5 行，登记轮换标签（`A=CURRENT,B=DIRECT` / `A=DIRECT,B=CURRENT`），轮换总账见 cases-freeze-notes.md §6 与 `scorers/e5-anon/<pair>/anon-map.json`（`ledger_only:true`，评审侧禁读）。

补充约束：

1. 生成代理输入 = 展开后的 `conversation_history`（按臂装配）+ 展开后的 `incoming_message` + `user_goal_tone` + 允许披露的 `public_vs_private_context`；`must_use_facts`、`must_not_assert`、`latest_valid_state`、`gold_reference_or_rubric` 四个金标字段只进评审侧。
2. 指针展开由执行侧完成：仓库路径只读，episode.json + GOLD_SIDECAR 为唯一展开源；展开结果与登记行一并落盘备查。
3. 联通测试题使用 `pilot-E{n}-001/002` 编号（`pilot-` 前缀），不进本冻结集、不进任何分母。

## 1. 指针形态对草案的冻结裁定

登记表 48 条真实题的 `conversation_history` / `incoming_message` / `public_vs_private_context` 采用**指针形态**（episode.json 相对路径 + turn case_id，不复制全文），偏离本 schema 草案 v0.1 的全文数组形态。该偏离已在 cases-freeze-notes.md §7.7 留痕。冻结裁定：**指针形态为权威登记形态**（§0 表"形态一"），理由 = 避免复制 Gold 侧车大文本、保持与 episode.json 单一事实源、防复制漂移；执行侧展开结果落盘备查。§0 表"形态二"保留给 E4 回填行与后续新增合成题。

## 2. scenario_type 枚举与**实际冻结分布**（以登记表为准）

| 指标 | 枚举 | 冻结分布（登记表实数） |
|---|---|---|
| E1 | `early_promise` / `preference` / `important_person` / `important_date` | early_promise 6 / important_person 3 / important_date 2 / preference 1（草案"四类各 3"不可得，preference 全池仅 1 候选、important_date 仅 2；偏差留痕 notes §7.1） |
| E2 | `reschedule` / `cancel` / `relationship_change` / `closed_or_pending` | cancel 3 / reschedule 3 / closed_or_pending 6（relationship_change 0 候选，配额并入 closed_or_pending；notes §7.2） |
| E3 | `unconfirmed_action` / `unknown_as_certain` / `private_note_leak` / `mixed_real_draft` | unconfirmed_action 6 / unknown_as_certain 3 / private_note_leak 3（mixed_real_draft 0，旧资产题面无混杂形态；notes §7.3） |
| E4 | `flirt` / `light_joke` / `restraint` / `comfort` / `deescalate` / `opening_or_stage_change` | 六类各 2（`e4-generation-kit.md §1` 槽位表为准；opening_or_stage_change 2 题内 ≥1 主动开场、≥1 关系阶段变化） |
| E5 | 沿用 origin 题的 scenario_type（12 对来自 E1/E2/E3 origin 各 4/4/4） | 实数：early_promise 2、cancel 2、unconfirmed_action 2、unknown_as_certain 2、important_date 1、important_person 1、closed_or_pending 1、reschedule 1。草案"E4 六类×2 + daily_invite"**作废**，以登记表为准 |

`source` 枚举：`stories_v2_gold`（Gold 主库故事/题面）> `selection_v1`（已有真实题面与四臂输出）> `synthetic_native`（原生子代理补充的虚构合成聊天故事）。登记表实数：stories_v2_gold 48 题、synthetic_native 12 题（E4 占位）。

## 3. E1–E5 × 12 = 60 题冻结分配（权威 = cases-freeze-draft.jsonl 60 行）

| 指标 | 题数 | 冻结要求（含已登记偏差） |
|---|---|---|
| E1 | 12 | 中间插入 ≥3 轮无关闲聊后重新提问（登记口径=距关键事实建立 ≥3 轮）；`must_use_facts` 目标 ≥2 条/题，**实际 9/12 满足**，其余 3 题（001/002/003）该轮 Gold 应使用事实仅 1 条，如实登记不硬凑（notes §7.4）；事实分母基线 = 31 条 must_use 事实，评审可校正、校正须留痕 |
| E2 | 12 | 每题均有状态变更 atom 信号 + forbidden 诱饵；`latest_valid_state` 11/12 直取该轮 asserted atom，uv1-E2-001 以 episode feedback 口径登记，评审可校正、校正须留痕（notes §7.5） |
| E3 | 12 | 每题 `must_not_assert` 非空（forbidden/unknowns 映射）；每题 forbidden 2–3 条、unknowns 0–2 条 |
| E4 | 12（占位） | 状态 `pending_generation`；case_id（uv1-E4-001..012）、六类各 2 槽位、关系阶段覆盖（暧昧初期/稳定期/冷战后修复各 ≥2、同阶段 ≤5）、生成硬性要求（notes §5：≥6 条多轮历史、含一次纠正、含私人备注、user_goal_tone 具体可判分、金标由独立标注子代理建立）**现已冻结**；回填只写这 12 行、不改 48 条真实题行 |
| E5 | 12（对） | 与 E1–E4 池共享 story 但独立成题（origin：E1×4、E2×4、E3×4，E5 集内同 story ≤1）；每题执行同一发送建议任务；匿名 A/B 已落盘 `scorers/e5-anon/pair01..pair12/` |

素材来源优先级（冻结原文）：优先从 `stories_v2_gold`（317 故事/299 带 Gold）与 `selection_v1` 真实题面提取；旧资产不能提供有效关系/调情任务时，用原生子代理补充 `synthetic_native` 合成故事：必须真实写出多轮历史、纠错、私人备注、当前消息，禁止只写"请优质回复"。选题过程未读任何臂输出（notes §0 声明）。

## 4. 同故事去重规则（story_id）

1. 每题必填 `story_id`；无 `story_id` 的题不得进入冻结集。
2. 结果主表按题计（每指标 n=12），同时报告按 `story_id` 去重后的故事数（`summary.json` 固定字段 `story_count_unique`），防止一段故事被重复计成多个独立故事。
3. 冻结口径：同一 `story_id` 在同一指标内 ≤2 题（登记实际：E1/E2/E3 各用到 10 个 story，均未超 2）；跨指标允许复用同一故事的不同任务并如实记录。story_count_unique 预登记：48 真实题来自 26 个不同 story，E4 占位 12 个合成 story（syn-e4-s01..s12），生成后按实际落盘复核。
4. 每题 `case_id` ↔ `story_id` 映射随 cases-freeze-draft.jsonl 公开，供回溯与去重复核。

## 5. 冻结登记（文件数 + 字节量，无哈希）

| 文件 | 文件数 | 字节量 |
|---|---:|---:|
| cases-freeze-draft.jsonl（权威登记表） | 1 | 145842 |
| cases-freeze-notes.md | 1 | 16398 |
| cases-schema.md（本文件） | 1 | 见 protocol-FROZEN.md §8 冻结登记表 |
| scoring-templates.md | 1 | 见 protocol-FROZEN.md §8 冻结登记表 |

1. 不写任何哈希值；完整性一律用文件数 + 字节量口径。
2. 冻结后新增/删改题目一律违反协议（protocol-FROZEN.md §8.4），唯一例外是程序化错误的一次全量对称重算记录。
3. E4 回填 12 行占位后，登记表字节量更新**一次**并在变更记录中写明（属冻结预留的占位升格，非改题）；其余 48 行字节级不变。
