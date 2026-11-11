# 连续执行单交付索引 — 收口删除实验判决 + 完成新 v4 架构并准备唯一对照（2026-10-24）

依 `WORKER_NEXT_BATCH_REVISED.md`（§0 目标纠正）与 `REVIEW_AND_NEXT_EXECUTION.md` 合并执行。
本轮**真实模型调用 = 0**：上一批 48 次额度已用尽并关闭；本"复核+推进"指导书未追加新额度，
worker 未自行补造许可，故只做离线收口/加固/冻结/清单，实跑待唯一授权批准。

## 阶段与本轮实际推进（不倒回考场建设）

第一轮真实删除实验已完成并签收：总 token −60.79%（182,785→71,667，实际 API usage）。本轮
把它收口为可用判决、一次修齐下一批输入公平、把下一代架构做成可实测的 `v4_intent_context_r2`、
冻结唯一 48 次对照清单，并把实跑判决路径预登记清楚。四个里程碑均已实体落地（见下文件路径）。

## 1. 旧 48 次收口（零真实生成，直接取自当时付费证据）

- **账本关闭**：`logs/auth_budget/AUTH-V3-DELETION-20261024-01.jsonl` 追加 `authorization_closed`
  （48 started / 48 completed / 0 failed 之后；额度耗尽，不得复活/覆盖/补跑）。
  用现成 `authorization_budget.AuthorizationBudget.close()`，未改原始答卷、未增补调用。
- **配对初审结论**：`refs/experiment_protocol_20261023/real_v3_independent_review_20261024/PAIRED_CONCLUSION.json`
  —— 24 位首选逐位 supports：`{candidate:1, original:2, both:16, borderline:4, undecided:1}`，
  每行绑当时 `raw_sha256 / case_input_sha256 / 程序分 / veto / 首选字数`；`review_status=not_user_rated_not_blind_review`，
  程序分不冒充质量。
- **≤6 组匿名对照**：`.../V3_DELETION_ANON_GROUPS.md`（甲=原版 / 乙=v3，题面取冻结 episode、原文取
  EXTRACTED_OUTPUTS 并绑散列；覆盖 E004_T08 媒体、E005_T02/T04 主动反撩、E005_T06 日期、E006_T05 深聊、E006_T08 收尾）。
- **整合结论**（写进 PAIRED_CONCLUSION.verdict）：删除大量 system 复杂度有真实资源收益、方向保留；
  当前 v3 因真实质量/交付问题（日期迁移、无图装看图、来源 ID、职业编造亲历、军师视角混入备选，
  以及一处来源契约失败 E006_T02）**尚未达替换门槛 → 不晋级为生产冠军**；`reference_product_v1` 与
  `v3_system_slim` 身份保持冻结作对照。输出 +19.15%、字数 +64.74% 只描述、不单独扣分。

## 2. 下一批输入公平（一次修齐，离线完整值验证）

改在生成源 `tools/gen_dev_expansion_e7_12.py`（非只改 JSON），重生成 E007/E010/E012：

| 故事 | 原不对等（仅 v4 收到） | 修齐后（双方均消费） | 私密目标处理 |
|---|---|---|---|
| E007 | `me.目标`、`her.偏好` 两 dotted key 原版收不到 | 公开偏好 `her.偏好` → 支持字段 `her` | 私密邀约目标移出永久种子，只由当轮 ACTIVE intent 承载；关闭轮不再被旧目标绑架 |
| E010 | `公开事实` 原版收不到 | → 关系画像字段 `warmth`（## 我们） | "想二人"仍留 T04 当轮 intent，不塞画像 |
| E012 | `背景` 原版收不到 | → `warmth` | — |

验证（`real_model_calls=0`）：
- 独立 probe `probe_next_inputs.py` 重跑：双方 seed 完整值均 present；E007 关闭轮 `persistent_private_goal_text_present` 全 false；ACTIVE 轮 `active_goal_block_present` 双方一致 true。
- **参考臂 vs r2 候选臂专门双校**（本批真正要跑的两臂）：三故事逐轮 seed_missing=0、关闭轮私密泄漏=0 → `FAIRNESS_OK`。
- 生成源经 `import_pilot_questions` 机械自检：6 故事/48 位/48 卡，0 硬失败（6 条 warn 仅为中性编号建议，非阻断）。

## 3. 新架构候选 `v4_intent_context_r2`（整套重构，新身份，冻结臂不动）

目录 `candidates/v4_intent_context_r2/`（父本 reference_product_v1 与 v3_system_slim 未改）。
依 REVIEW §B1 登记为**整套架构**：v0 简单真实事件记忆 + 当前目标作用域/上下文优先级 + r2 三项输入边界加固。
相对 v4 初稿的 r2 增量**纯上下文组织、不加第二次模型调用、不改 system 资产**：

- `_source_rule`：analysis.source_ids 只可引用本轮 `<chat>` id；历史 `m_src[uid]` 仅供回扣 → 治 E006_T02。
- `_media_line`：按本轮真实附带图片数表达；0 图禁止评价构图 → 治 E004_T08。
- `_time_scope_rule`：时间性事实属其出现日期、跨日不继承旧空闲、职业不证明亲历 → 治 E005_T06 + 职业编造。

身份闭包：candidate identity_sha256 = `eaf6d267…`（17 文件，父本 superset）；`build_system_prompt` 与 v3 逐字节相等（2598 码元）。
离线夹具 `tools/test_v4_intent_context_r2.py`：**7 项全 PASS，real_model_calls=0**（媒体无/有图、来源、时间作用域、
目标启停/替换、已确认事项持续、草稿不写回）。结果 `logs/v4_intent_context_r2_result.json`。
草稿 `v4_intent_context` 回归仍 8/8 绿。

**能力缺口如实声明**：r2 不自动从模型输出维护新确认约定（plans 只来自 ongoing_plan 种子），无多层摘要/反思；
是否"更会聊"**未证**，fixtures 只证输入装配与边界。

## 4. 唯一 48 次对照清单（原版 vs v4 r2，待批准）

- 冻结计划：`refs/experiment_protocol_20261023/r2_next_batch_20261024/INTERLEAVE_PLAN.json`
  （parent=reference_product_v1 identity `6e65b6bc…`、candidate=v4_intent_context_r2；E007/E010/E012 各 8 位、起始臂交替、逐位交错、48 硬上限）。
- **零网络 capture 已验证可运行**：`.../BATCH_SUMMARY.json` — 48/48、逐位顺序 verified、派发双射、
  6 常驻宿主状态隔离、`real_model_calls=0`。capture 里的 token/时延是**替身罐头值**，不作资源结论。
  跨臂 user prompt 长差 max=682（整套 harness 差异，属预期，非仅 system）。
- 待签清单：`.../AUTHORIZATION_REQUEST.json`（`authorized=false` / `PENDING_USER_APPROVAL`）——
  固定 `http://127.0.0.1:17770/v1`、model=dsf、temp=0.7、max_attempts=48；无第三臂、无自动 judge、
  fail-fast、无重试/补配对/额外冒烟。批准前 `execute_plan(mode=live)` 会被 `validate_authorization_doc` 拒绝。

## 5. 实跑与整批判决 —— 未做（无新额度，诚实登记）

`v4_intent_context_r2` 的真实质量与资源配对**尚未产生**，本轮不宣称候选有效或优于原版。唯一
待办 = 上面那份 48 次授权。批准后一条命令连续跑满并出两臂分层+配对（真实 token/时延）+ 预登记
口径的保留/淘汰/证据不足判决：

```
# 批准：把 AUTHORIZATION_REQUEST.json 改成 authorized=true / status=ACTIVE / 记录用户同意原文与时间，
# budget_ledger 指向新建空账本；随后：
python tools/run_next_batch_r2.py   # execute_plan(mode=live) 逐 slot 交错，ensure_batch(48) 首发预检
```

预登记选择口径（写进请求单 selection_criteria_pre_registered）：质量收益路线或效率收益路线任一成立
均可开发保留；质量相当须有内容/互动/交付审查证据、不等于"没看到 veto"；篇幅变长不构成损失；严重
事实/状态错误或首选交付退化不得被省 token 抵消；三故事皆公开开发集，不据此称泛化或生产冠军；
保留后下一里程碑为少量独立新故事验收与原项目接入。
