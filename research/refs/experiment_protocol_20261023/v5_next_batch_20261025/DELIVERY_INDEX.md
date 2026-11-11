# 连续执行交付索引 — v5 真实 48 次收口判决 + 接入原型 + 独立验证交接包（2026-10-25）

依 `v5_independent_review_20261025/VERDICT_AND_NEXT_EXECUTION.md`。**本轮真实模型调用 = 48**
（BATCH-V5-CTX-20261025 跑满 48/48、0 传输失败，额度已追加 `authorization_closed` 关闭）。
下面第 4/5 节描述的是开跑前的准备态，结论已被第 6 节实跑结果取代——原文保留供追溯，
每节顶部标注"已取代"，不改写历史数字。

## 阶段

本项目已做完**两个可核对的真实开发对照**：先验证删 system（总 token −60.79%），再比较简单事件
记忆+目标上下文的完整新架构 v4_intent_context_r2（本轮 r2）。尚未完成未见故事验收与原项目替换。
本轮把 r2 真实结果收口成判决、把下一代"用途对齐"架构做成可实测的 v5、并冻结下一份唯一 48 次清单。

## 1. r2 真实 48 次收口（不重跑，读当时付费证据）

- **额度关闭**：`logs/auth_budget/AUTH-R2-CTX-20261024-01.jsonl` 追加 `authorization_closed`
  （48 started/48 completed/0 failed 之后）；签名单 `.../r2_next_batch_20261024/AUTHORIZATION.json`
  改 status=CLOSED/exhausted=true。用现成 `AuthorizationBudget.close()`，未改原始答卷、未增补调用。
- **独立复算**：`tools/verify_real_batch_r2.py`（修正来源 ID 抽取——匹配成对 `<chat>…</chat>`，
  不再把正常 m0/m1 误标非法；本批两臂 `illegal_source_slots=0/0`）。结果
  `.../r2_next_batch_20261024/R2_INDEPENDENT_RESULTS.json`：48 全项检查通过、6 独立 run、顺序=冻结交错。
- **判决与公开对照**：`.../r2_next_batch_20261024/R2_VERDICT.md`（资源表 + 四层配对 + ≤6 组重点公开对照）。
  完整逐字段初审见复核会话 `r2_real_independent_review_20261025/NODE_REVIEW.md`。
- **关键结论**：总 token −56.56%（183,352→79,646，prompt −59.49%）**成立**；**提速未成立**
  （p50 25.4s≈持平、p95 44.8→65.4s +45.91%、累计等待 +20.11%）。completion +16.75%、首选字数 +77.11%
  只描述、不扣分。程序均分原版 9.58/1veto、r2 9.86/0veto 不冒充质量。**保留精简+真实事件架构作研发方向；
  r2 冻结作参照，不接入默认生产**（依据：资源收益明确+部分目标链可用，但仍有无据现场/路线/装备/亲历与
  备选视角问题，且等待未改善——**并非因回复变长**）。原版同批也有可见错误（E007_T03 补现场、
  E012_T08 已发生仍写"周六见"），不能仅按程序均分判胜负。

## 2. v5_purpose_aligned 新架构候选（新身份，原版/r2/v3 全冻结）

目录 `candidates/v5_purpose_aligned/`。整套"用途对齐"：继承 r2 三护栏+目标作用域+简单事件记忆，另加
两项设计变量：

- **B1 删未消费的内部摘要**：r2 只取 `res['text']`、从不读回 topic/scene_facts/ongoing，却仍每轮生成
  完整 analysis（约占原始 JSON 字符 41.87%——字符占比，非 token/非预计节省）。v5 把 `analysis` 固定为
  `{}`，**保留** response 四风格/directions 四方向/合法 null，不改可选文本数、不设短答目标。已由复核会话
  `EMPTY_ANALYSIS_PROTOCOL_PROBE.json` 离线复算 24 份候选原答卷 analysis→{} 合同仍合法、程序分不变
  （只证协议兼容，不证更快/质量不变/已省 token，须实跑）。诚实边界：删摘要后 analysis 来源错误不再是
  同等测量项，不据此单独宣称记忆/真实性提升。
- **B2 事实与行动的区分（通用装配规则 `_fact_action_rule()`，非按素材特判）**：信息不足仍可查/商量/建议/
  表达兴趣/推进（含玩笑调情）；只禁"未查明→已查明、希望→已发生、常识冒充本次现场/路线/设施/天气、
  新提议冒充已有装备/既有亲历"；私下目标转自然商量不冒称她已同意、关闭后不让其它备选再推同一邀约；
  首选与方向槽都须是可发正文（非"你可以这样发"类操作指令）；已发生后据回顾收尾不复活旧约。

身份：candidate identity_sha256 = `ef855510…`（25 文件闭包）。`format.md` 改 analysis→{} 使 v5 system 与
r2/v3 **有意不同**（设计变量，非漂移；r2 的 system==v3 断言不适用 v5）。离线夹具
`tools/test_v5_purpose_aligned.py` **6/6 PASS、real_model_calls=0**（analysis 空协议、事实行动规则进入请求、
继承三护栏、目标启停/已确认事项持续/草稿不写回、snapshot/restore 事件+时间续用、notes 前缀 v5）。

## 3. 下一批三故事输入公平（一次修齐，离线双校）

改在生成源 `tools/gen_dev_expansion_e7_12.py`，重生成 E007/E008/E009（`import_pilot` 0 硬失败）：

| 故事 | 原不对等 dotted key | 修齐（双方均消费的支持字段） | 约束 |
|---|---|---|---|
| E007 | me.目标/her.偏好 | her.偏好→`her`；私密邀约目标移出永久种子（由当轮 intent 承载） | 关闭轮不泄漏旧目标 |
| E008 | her.妹妹 / 共同朋友 | 妹妹→`her`（前缀"她妹妹"，仍明确是妹妹，不改写成 HER 本人）；共同朋友→`warmth` | 保留归属 |
| E009 | 事项.修电脑 | →`warmth`：既有承诺"此前 ME 答应重装系统"，**不补确定日期** | 日期由后续消息推进 |

reference vs v5 完整值 capture 双校（三故事逐轮）：`seed_missing=0 / priv_leak_closed=0 / 归属保留` → `V5_FAIRNESS_OK`，
real_model_calls=0。

## 4. 唯一下一份 48 次清单（已批准并跑满 → 见第 6 节；准备态原文保留）

- 冻结计划：`.../v5_next_batch_20261025/INTERLEAVE_PLAN.json`（parent=reference_product_v1 identity `6e65b6bc…`、
  candidate=v5_purpose_aligned `ef855510…`；E007/E008/E009 各 8 位、起始臂交替、逐位交错、48 硬上限）。
- 零网络 capture 已验证可运行：48/48、顺序 verified、real_calls=0（token/时延为替身罐头值，不作资源结论）。
- 待签清单：`.../v5_next_batch_20261025/AUTHORIZATION_REQUEST.json`（authorized=false）——固定
  `127.0.0.1:17770/v1`、dsf、0.7；无第三臂、无自动 judge、fail-fast、无重试/补配对。批准前
  `tools/run_next_batch_v5.py` 在派发前即拒绝（已验证：无账本、无 live 目录、零网络）。

## 5. 实跑与整批判决 ——（当时登记"未做"，现已完成 → 见第 6 节）

v5 的真实质量/资源配对**尚未产生**，本轮不宣称候选有效或优于原版。唯一待办 = 上面那份 48 次授权。
批准后一条命令连续跑满并交 D 包全量初审（48 首选+全部非空备选分审、目标/反馈动作、备注不过度保守、
真实资源配对）与预登记口径的保留/淘汰判决。E008/E009 是**新增公开实跑覆盖、非未见泛化**；比较 v5 对
原版的完整效果，不把不同故事上的 r2 与 v5 数字直接相减冒充"已分离删 analysis 的收益"。保留后的下一
里程碑才做小批独立新故事验证与 `D:/LoveBrain` 新旧引擎实验切换接入。

## 6. 【本轮实际交付】v5 真实 48 次 + 三项主线成果

### 6.1 实跑与额度关闭

用户 2026-10-25 09:37 原文批准 → `AUTHORIZATION.json`（status ACTIVE，绑定
parent `6e65b6bc…` / candidate `ef855510…` / mechanism `422ec227…` / format `fe5221b5…`）→
`tools/run_next_batch_v5.py` 派发前断言"授权单身份==当前冻结身份、额度==48"后
`execute_plan(mode=live)` 逐位交错跑满。

| 收口项 | 证据 |
|---|---|
| 48 started / 48 completed / 0 failed | `logs/v5_ctx_live_20261025.DONE.json`（`boundary_problems=[]`、`execution_order_verified=true`） |
| 真实账本 48 占用 + `authorization_closed` | `logs/auth_budget/AUTH-V5-CTX-20261025-01.jsonl`（`tools/close_v5_authorization.py`：先校验账本↔DONE 一致且 48/48/0 才允许关） |
| 关闭后不可复活 | 现在重建 `AuthorizationBudget(ledger, aid, 48)` 在构造期 `_check_scope` 即抛 `BudgetRefused`，不是等派发才拦 |
| 独立复算 | `tools/verify_real_batch_v5.py` → `V5_INDEPENDENT_RESULTS.json`，15 项检查 `failed_checks=[]`（逐位 raw/calls/case_input sha 绑定、6 独立 run、顺序=冻结交错、48 唯一 response_id、temp/model/adapter 逐次、seed 双达、关闭轮无私目标泄漏、成对 `<chat>` 来源合法性） |

### 6.2 资源与等待（实测，非 capture）

| 指标 | 原版 | v5 | 变化 |
|---|---|---|---|
| prompt tokens | 176,355 | 72,146 | −59.09% |
| completion tokens | 6,966 | 5,491 | −21.17% |
| 总 tokens | 183,321 | 77,637 | **−57.65%** |
| 等待 p50 | 29,606 ms | 24,381 ms | −17.65% |
| 等待 p95 | 61,401 ms | 77,949 ms | **+26.95%（变差）** |
| 累计等待 | 823,836 ms | 826,005 ms | +0.26%（持平） |
| 首选平均字数 | 23.38 | 35.17 | +50.45%（不设长度目标，不计优劣） |

交易口径：**省资源与中位等待成立，提速不成立**（尾部由 E007_T02 91.2s、E009_T03 79.2s、
E007_T05 71.0s、E009_T08 65.9s 拉出；原版最慢 64.5s）。无分段计时，不解释尾延迟成因。
`analysis={}` 遵守率 **24/24 空、0 非空、0 解析坏**（实现遵从，非聊天胜负）；
completion 实测只降 21.17%，低于早先按字符占比估的 41.87%——那从来不是 token 承诺。

### 6.3 质量四层（逐位判定 `V5_PAIRING_LAYERS.json`，48 首选 + 316 非空备选全部已读）

层1 首选内容：**原版 2 胜 / v5 3 胜 / 19 平**。v5 胜在 E008_T04（转述与本人表态分离并回问她）、
E008_T06（接住她连说两遍的主体强调）、E009_T01（原版把机器日期 `2026-11-14` 写进要发的话）；
原版胜在 E007_T03/T04（v5 把没查的现场声学说成已知、多替她承接一步）。
层2 交付契约：**各 1 次整位 0 分，故障族不同**——原版 E007_T05 `illegal_source_id:t4_1`
（恰好出在被 v5 删掉的那层），v5 E007_T08 `json_schema_broken`（必需键名混入零宽空格 U+200B）。
层3 备选风险：原版 4 条（含 **1 条高危**：E007_T08 备选凭空造"那天阵容里有两支偏躁的"）、
v5 7 条（**0 高危**，5 中危：把 note 禁拉的小林写进玩笑、"那我先记着这句"近赌气、
"让她妹直接跟我讲"主体错位等）。读法：v5 更常踩 note 的语气边界，原版更常踩事实边界。
共享观察（不算任一臂功劳）：两臂军师指令泄漏 0 命中、合法 null 两臂 48 位均未使用、
程序均分 9.51/9.58 与逐位程序胜负 parent1/candidate2/tie21 只作契约参考。

### 6.4 判决

**保留 v5 方向（效率型开发候选），不替换原版。** 依据预登记口径：能力线未见实质退化、
效率线总资源与中位等待实质改善 → 支持开发保留；尾部等待更高、两臂各一次整位交付失败、
v5 备选踩 note 更频繁 → 不足以替换原版。是否接受这笔交易由用户裁，见
`V5_KEY_PUBLIC_GROUPS.md`（6 组，含同故事前情、全文不截断）与 `V5_VERDICT.md`。

### 6.5 三项主线成果状态

1. **真实对照结果** → 本节 + `V5_VERDICT.md` + `V5_PAIRING_LAYERS.json`（已交，不停在"跑完"）。
2. **接入原型** → `integration_preview/`：`engine_adapter.py` 五接口（事件归一/意图输入/
   事件存储与 snapshot/输出消费含空 analysis/新旧引擎路由与回退）+ `replay_fixture.py`
   替身回放 **4/4 PASS**（`REPLAY_RESULT.json`）+ `KOTLIN_INTEGRATION_MAP.md`（映射到
   `PromptBuilder`/`IntentPolicy`/`DeepSeekRepository`/`KnowledgeRepository` 真实入口，
   并登记 5 项未验证：未编译 Kotlin、未做 UI 验收、feature flag 未接、产品侧空 analysis
   消费者未证、kb/corrections 等价未 diff）。**未改生产默认、未迁用户真实库。**
3. **独立验证交接包** → `refs/experiment_protocol_20261023/independent_validation_handoff_20261025/`
   （README + `episode_schema.md` + `card_schema.md` + 中性样例，样例经 `import_pilot` 0 硬失败）。
   建议最小验收批 = 2 条新生活故事 ×8 位 ×2 臂 = **32 次**，属未来条件清单，**不在本授权内**。

### 6.6 留给用户拍板（我不擅自动手）

1. 零宽字符这族：(a) format.md 显式禁不可见字符并重冻重测 / (b) 改评分器做可见字符规范化
   （属**尺子改动**，须版本化+离线重打分，不得计入 harness 收益）/ (c) 先不动看下批是否复现。
   我倾向 (c)+(a)，一次样本不足以定族。
2. 下一步：独立验证批 32 次（需新额度）、接入原型真机验证（需 `D:/LoveBrain` 编译与 Android 条件）、
   或先做最小机制修复再对照——三件事可并行但各自需要授权。

## 7. 【2026-10-25 追加】主线改挂评估基准工作包，本批结论已被修订

用户方向校正后，192 次扩量对照不再排第一优先；先把"怎样可靠判断聊天效果"做实。本节只登记变化，
细节见 `D:/lovebrain-arena/evaluation/`。

- **本批质量结论已降级**：`V5_VERDICT_REVISION_01.md` 合并复核 8 项修正；我自己的 null 计数报错
  （directions 合法缺省实为 原版 7 / v5 12，整轮 no_reply 双方 0）已用修正后的抽取器离线重算
  `V5_INDEPENDENT_RESULTS_REV01.json`，与独立复核 6 项逐一相符。"其余 46 份可原样发送"改为
  **原始协议通过 46/48**；2 胜/3 胜/19 平降级为"待重评的发现清单"。付费原文与旧评分未动。
- **评审手册 v1**：`evaluation/JUDGE_MANUAL_V1.md`（F1–F4 事实分级、目标 0–3 锚点、互动分列不打总分、
  交付/等待/费用独立成表、"卡片缺陷不得当资格证明"、尺子改动须版本化离线重评）。
- **裁判可靠性第一次实测**：13 匿名节点 ×3 名盲评（含整体换序一组），阈值事先写死
  `JUDGE_QUALITY_THRESHOLDS_DECLARED_V1.md`，统计 `tools/analyze_irr.py` → `IRR_AGREEMENT_V1.json`。
  结论：**目标完成度 0–3 可批量辅助**（相邻一致 100%、对构造参考 18 项零超差）；
  **事实分级 83.3%、信息不足 62.5–87.5%、归属一致 33–50% 三项未过线**；协议污染维度因标定包
  抽取层抹平原文而**无法检验**；口味维度 AI 弃权率 30.8%→69.2% 跨会话不稳，**不宣布任何合格**。
- **题库审计**：`evaluation/CARD_CONFLICTS_V1.json` —— 72 张卡全为 dev_exposed（无独立集）、
  全部 pending_review（未冻结）；命中 6 张口径缺陷卡（E007_T03 即"据偏好确认不吵"那条）；
  覆盖矩阵显示"调侃反撩 5 位 / 深聊 3 位 / 转场钩子 4 位"远少于防守面 50+ 位——
  这就是此前"没输≠更会聊"的分布根因。
- **标定包与你的锚点**：`evaluation/CALIBRATION_PACKET_V1/`（54 节点/108 答案，臂身份与类别只存密件）；
  请你只裁 `evaluation/USER_CALIBRATION_SHEET_V1.md`（16 组匿名成对首选，甲/乙/都行/都不想/说不清）。
- **并行修的常用 bug 已完成**（不占主线、不宣称质量收益）：接入原型私密备注不再进已发送历史与事件库、
  消费器对 `[]`/列表型 response 不再崩溃、零宽键名改为消费侧可审计适配（保留原文+审计记录），
  恢复后真正再发一轮已覆盖：`tools/test_integration_preview_boundaries.py` 9/9 PASS。
- **额度**：本阶段生成额度 236/256 **未动用**（账本仍是你 4 次计时探针的 4/4）；
  今天全部工作真实模型调用 0。

## 8. 【同日追加】交付可靠性四列、分段计时能力件、原型修复验收

- **五列分开**（`evaluation/DELIVERY_AND_COST_COLUMNS_V1.md` + `tools/delivery_columns_offline.py`，零调用）：
  T 传输 / P 原始协议 / C 消费后真正可发 / S 程序级语义 / W 你愿不愿原样发。
  实测：v5 批 原版 T24 P24 C24 S23；候选 T24 **P23 C24** S23。r2 批 原版 T24 **P23 C24** S23；候选 24/24/24/24。
  **新发现**：`json_schema_broken` 不是候选独有——r2 批是**原版臂**栽过一次；两批合看属共同的输出协议脆弱性，
  我上次"v5 自己要面对的一族"的归因作废。键名污染经生产侧消费适配恢复，原文逐字节保留、审计 1 次，
  所以"整位清零"是评分口径结果，不是正文不可用（口径对照已登记但**未生效**：改判据属尺子改动）。
  每可用结果 token：v5 3,375.5 vs 原版 7,970.5；cache 字段 24/臂为 unknown；无单价 ⇒ **金额 null，不折算**。
- **分段计时能力件已接入**（`arena/lb_stream_timing.py` + 网关 `timing` 字段一处最小改动）：
  pre_request/headers/first_content/first_preview/first_usable/last_content/completion/all_ready/persisted，
  含单调自检与"role-only、心跳不算首内容"测试。`tools/test_stream_timing.py` 15/15 PASS，
  `tools/exam_interleave_plan.py --all` 13 项全 PASS，real_model_calls=0。
  **待你定 A/B/C**：流式取 usage 需要 `stream_options`，它不在出站白名单内（已验证会被当场拒），
  扩白名单＝实际 API 配置变更；建议 A（扩并声明新配置身份，正式对照两臂同配置重跑，不与旧 144 次拼接）。
  历史已付费答卷**无法**回溯分段——等待结论至今仍是完成耗时口径。
- **三项原型缺陷我已亲自验收**（不盲信子代理回报）：`tools/test_integration_preview_boundaries.py` 9/9 PASS、
  `integration_preview/replay_fixture.py` 4/4 PASS、v5/r2 候选测试 6/6 与 8/8 PASS；
  关键代码点逐条读过（`kind` 结构化边界、note-kind 拒绝成聊天、Cf 仅用于键名匹配、值零改动）；
  离线重算确认 `frozen_files_match_current=true`，`candidates/`、`benchmark/`、`logs/` 与评分器均未被改动。


