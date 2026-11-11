# P06_DELIVERY_RELIABILITY_V1 — P06 三批真实对照的交付/协议可靠性（不依赖裁判）

日期：2026-10-27。数据源：`logs/p06_{A,B,C}_live_20261027.DONE.json`、
`.../_reports/*`、`refs/experiment_protocol_20261023/p06_judge_qwen2_20261027/P06_ANSWER_INVENTORY.json`
与 `P06_PROTOCOL_ANOMALIES.json`。考生=**dsf**（已失效）；本表只统计“能不能被真实消费”，
不含九维语义判决（语义见 P06 质量成绩单，另行）。

V2 §0 裁定：**严重交付失败必须计入任务失败，不得被平均质量分掩盖。** 本文就是这条的落地。

## 1. 传输层（T）：全部送达、非空

三批 192 次 live 调用 `started=completed=64`、`failed=0`、`stopped=null`、`real_model_calls=64`、
order+bijection、8 residents、`state_isolated_real=true`。传输层没有丢答案，没有串状态。

## 2. 协议/消费层（P）：可提取首选 + 契约合规

| 批 | 两臂 | 程序均分 program_mean10 | 程序 veto 数 | 整条不可评(JSON非法) | 严格配对程序胜(父/候选/平) |
|---|---|---|---|---|---|
| A | reference vs **v5** | 父 9.9 / 候选 10.0 | 父 0 / 候选 0 | 0 | 0 / 2 / 30 |
| B | reference vs **v6_goal_context** | 父 10.0 / 候选 9.01 | 父 0 / 候选 **3** | 候选 1（B/E007_T04 花引号致 JSON 非法） | 4 / 0 / 28 |
| C | reference vs **v6_integrated** | 父 **9.01** / 候选 9.38 | 父 **3** / 候选 **2** | 0 | 2 / 3 / 27 |

程序 veto 明细（`veto` 字段）：

- B/v6g：`E007_T04 json_schema_broken`（中文花引号→整条不可解析，唯一整条丢失）、
  `E007_T05 json_schema_broken`（`response.bad_boys` 未声明键）、`E012_T02 json_schema_broken`（directions 只 3 项）。
- C/reference：`E006_T07 illegal_source_id:t6_1`（analysis 跨轮裸编号）、`E008_T06 json_schema_broken`、
  `E012_T08 json_schema_broken`。
- C/v6i：`E007_T05 json_schema_broken`（`bad_style`）、`E008_T01 json_schema_broken`（`bad_bo`）。

## 3. 输出契约故障族（P06_PROTOCOL_ANOMALIES 抽取到的“未识别风格键/数组异常”）

`unrecognized_response_keys` 事件（模型把风格键写错）：

- 候选侧：v6g `bad_boys`；v6i `bad_style`、`bad_bo`。
- 父本侧：reference `bad_man`（C/E012_T08，也被程序 veto）。

结论：**“风格键写错 → json_schema_broken”不是 v6 独有，而是横跨父本与候选的共享输出契约脆弱性**
（符合 V2 §P11“零宽键等共享协议故障不独占归因给某候选”）。v6 分支并未把它稳定住；父本 reference
在 C 批也出现了同类与 `illegal_source_id`。方向：这是**下一轮第一优先修复**（约束输出键名集合 +
消费侧对可识别槽的稳健回退 + 分析 source_ids 只允许本轮别名）。

其它：`directions_len≠4`（B/v6g E012_T02=3、C/父 E008_T06=5）、`slot_empty_string "null"`
（C/v6i E007_T03 把 null 写成字符串）、`slot_equals_primary`（A/父 E007_T07、B/父 E012_T06、C/父 E007_T04）。

## 4. 等待/用量（W/C，二级）

来自 DONE（非流式 stream=on(+usage) 考生批；单位 token / ms）：

| 批·臂 | prompt | completion | p50 | p95 | max |
|---|---|---|---|---|---|
| A 父(reference) | 234,998 | 9,140 | 22,668 | 42,966 | 63,494 |
| A 候选(v5) | 96,198 | 7,487 | 30,151 | 80,336 | 84,680 |
| B 父(reference) | 235,239 | 9,005 | 26,814 | 83,076 | 90,114 |
| B 候选(v6g) | 126,099 | 8,739 | 31,428 | 66,314 | 74,856 |
| C 父(reference) | 234,981 | 8,896 | 25,547 | 60,798 | 83,907 |
| C 候选(v6i) | 152,989 | 12,571 | 26,904 | 70,179 | 89,669 |

- reference 基线 prompt 恒 ~235k（长 system 资产），候选 prompt 更短（v6 精简/投影），但 **completion 更大**
  （v6i 12,571 > 基线 8,896，v6 目标/证据分析块写入更多 token）。金额 `unknown`（无单价），cache `unknown`。
- p95 在 A 批 80s 级、B/C 66–90s 级，小样本只作分布描述，不据此解释长尾成因。

## 5. 可靠性判读（先于语义质量，但不足以定冠军）

- **v5（A 候选）协议最干净**：10.0、0 veto、0 未识别键，配对程序 2 胜 30 平。
- **v6g（B 候选）协议最差**：3 个 json_schema_broken（含 1 条整条不可评），程序配对 0 胜 4 负。
  新增目标/行动块后，输出键名与数组完整性控制变弱，是实测到的**真实退化**，不是噪声。
- **v6i（C 候选）居中**：2 veto，程序均分 9.38，配对程序 3 胜 2 负 27 平；completion 明显变大。
- **父本 reference 不是“稳”的参照**：C 批自己就 3 veto（含 illegal_source_id 与 bad_man 键），
  说明本轮故障族是**共享的模型/契约层脆弱**，不能只算在候选头上。
- 语义好坏要等 qwen3.8-flash 裁判成绩；但**仅凭本节，v6g 已因交付退化不能直接进 M2**，
  v6i 需要语义成绩与修复验证共同决定。严重交付失败按 V2 计入任务失败，不被九维平均抵消。

## 6. 下一步（据本轮真实失败）

1. 输出契约硬约束：候选生成端固定 style 键集合与 directions=4；消费/评分端对可识别槽稳健回退，
   非法整条计“交付失败”而非“语义 0 分”。
2. `analysis.scene_facts.source_ids` 只允许本轮 `m*` 别名，跨轮引用走稳定 uid（对应父本 C/E006_T07）。
3. v6i 的 completion 膨胀纳入 P11：目标/证据分析块裁剪、避免把审计字段写进可发送正文。
4. 下一轮候选在**同一基座模型**上重设基线再比（dsf 已失效，后续改 qwen3.8-flash，跨模型不冒充收益）。
