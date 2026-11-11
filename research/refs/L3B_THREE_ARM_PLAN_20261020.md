# L3B 三臂生成对照固定方案 v3（待授权清单 · 本批零真实调用）

日期：2026-10-20（v3）。v2→v3 依据：v10 独立复核 §3/§4/§5——执行器隐藏重跑
反例（账面 66 槽、实际 67 次调用尝试）推翻 v2 的"作废失败槽后继续"恢复路径；
失败策略改 fail-fast、额度权威改真实网关事件、汇总分类与配置锁定修正。
**执行前不再改写；若需改动（含失败策略），作废重编并重新提交授权。**

## 0. v3 失败与恢复策略（推翻 v2 §3 的"显式恢复后继续"）

- **fail-fast**：传输失败 / 网关内部失败 / 配对校验不符 / 注入断言不符 /
  checkpoint 前驱不符 → 槽 failed 留证 + **整批立即终止**。
- **禁用恢复路径**：`--resolve-failed` 已移除（传入即 RECOVERY_DISABLED）；
  failed 或 occupied 未决的批次重入一律 `BATCH_ABORTED_TERMINAL` 阻断；
  不自动新建批次、不隐藏补跑。若操作者决定放弃该批，显式新建批次号重编。
- v2 演练（logs/l3b_66_dryrun_v2、drill_v2）的"作废失败槽后继续"口径被
  v10 独立反例推翻，留档不改写、不再作为通过证据引用。

## 1. 三臂与主变量（相邻比较各一个）

| 臂 | 候选 | 相邻主变量 | 机制 sha256（v2 冻结） |
|---|---|---|---|
| A1 | phase0_beta_single（父版本） | —（基线） | `fc8fdd68abffbc28f70ab85d3069b174e5fd705f2b83620c590383089ee849c3` |
| A2 | phase1_retr_idf（检索 v2.2） | 检索策略（A1→A2） | `50c095eb7ee021b24831f741d56ea7cad5e30466a11f25217b476ecee49f8727` |
| A3 | phase2_expr（A2 + 表达策略） | 表达/选择策略（A2→A3） | `31a1dd72e355257b75b12bb7f5d74cf104abea6084ba904972d2b1908dfd482a` + expression.md `707128023da1def0177210d120986ef67a3094ee74b40646a7c2399c4214c367` |

- **A3 依赖散列**（v9 复核 §3.1）：A3 机制经模块加载直接复用 A2 源码——
  A3 每个 slot 账目记录 `dep_A2_mechanism_sha256 = A2 机制完整 sha256`；
  整批执行期间冻结该依赖文件（执行前复制快照入批次目录归档）。
- 共享冻结件：scorer `678c4e19…f7e959`（v0.9.2）、assembly `a671c23b…096e1b`、
  runner `89568033…f137de`、gateway `b0587e6a…9f8a35`、expr_audit 工具
  `61486cd6…a98dcd`。三臂共享 assets 15 文件逐字节一致（A3 仅新增
  expression.md）；params.json 逐字节一致（string|null 契约）。
- 注入幂等已修复并回归（test_expr_candidate.py A5/F 组）：A3 每请求恰 1 份
  表达块，A1/A2 零表达块，执行器逐槽断言 `expr_block_count` 入账。

## 2. 题材（v2 修订：剧本/评分口径）

- **NCA_CORR_INTENT_v1**（6 请求位，sha `018826ba3f7025edd8139cb7b02267d38e096589ecf5f6c9dbe4e43778470e57`）：
  意图变化+纠正+纠正后明确查询+收尾许可。v2 修订：t05 可见 intent 去测评注释
  （观测说明移隐藏 meta.obs）；开放闲聊硬关键词降级 soft_keywords_any
  （t01/t02/t03/t04）；t05 保留组会时间客观契约（hard）+ 建议承接（soft）。
- **NCB_ENTITY_DENSE_v1**（5 请求位，sha `8fbb29b5fec8f6ab61a0e89d8fe9e121024d482aeb4cda9f125f7b5c6afb62f7`）：
  英数车次+英数纠正+特产新事实+既往回提+收尾许可。v2 修订：旅程唯一时间线
  （周四购票/改签→周五到合肥+送糕两盒→周六收糕/婚礼→周日返程）；"你们吃过没"
  改为"你那盒别放着 早点吃"（写清谁和哪盒）；车次号 verbatim 保留为唯一 hard
  契约（G7812/D3145），其余 soft。
- 检索正向收益观测位：NCA t05（换项目组事实仅存库存、不在窗口、
  intent/initial_kb 不泄露）——11 请求位内的显式检索观测节点，不扩题库。
- 成族检索反例保持**离线回归**，不消耗真实调用。
- memory-off 消融：本批不跑。

## 3. 采样表（固定）

| 项 | 值 |
|---|---|
| 每臂请求数 | 11（NCA 6 + NCB 5） |
| 重复数 | 2（r1/r2，独立 sandbox，同题同窗口） |
| 总请求数 | 3 臂 × 11 × 2 = **66** |
| **总调用上限** | **66（硬上限，无重试额度）**；额度权威=真实网关 attempt_started 计数（非槽账行数），第 67 次真实请求**发出前**拒绝；槽先占后耗，终态成功/失败都消耗 |
| 槽位轮换 | 每个请求位组（同 episode/turn/repeat 的三臂）按组序轮换 A1A2A3 → A2A3A1 → A3A1A2（平衡顺序，实际顺序逐槽入账）；**缩短三臂时间间隔，不宣称消除时间漂移**；跨轮依赖（arm 内 turn 次序）各自有序 |
| 沙箱起点 | 每臂×每重复独立空 sandbox；episode 内事实由各臂 analysis 写回自然累积——库存演化差异属主变量效应，如实报告 |
| 不可用输出政策 | **不重试**：每请求位一次生成定槽；坏 JSON 记格式失败（0 分）保留原始输出后**继续**；**传输失败/网关内部失败/配对不符 → fail-fast 整批终止**（§0），无恢复路径 |
| checkpoint 前驱断言 | 生成前核对 checkpoint.turns_done 恰为本分支前一轮；缺失/过旧/超前拒绝（不占槽不生成）——杜绝 checkpoint 落后时 resume 顺带重跑历史轮（v10 复核 §3.3 根因） |
| 每槽配对校验 | 每槽以 (run_id, case_id, attempt_id) 对真实 started/终态配对：恰 1 次 started、case=目标 case、有且仅有一个终态、无网关失败事件、manifest.audit_errors 空——任一不符即槽 failed 停批 |
| 失败留证 | 网关事件日志/calls/error.json 全落盘（test_failure_persistence 口径）；执行器槽账逐槽 occupied→terminal 配对 + 真实事件计数并列 |
| adapter/模型 | adapter=执行时配置；batch_config 从适配器真实配置源建立并逐项锁定（LB_API_BASE / LB_API_KEY 是否设置 / LB_MODEL 实际生效值 / temperature 实际出站 0.7 归一口径）；再次进入批次逐项比对，**adapter/端点/模型/温度漂移拒绝**；真实适配器缺端点在首调用前拒绝；不落 API 密钥；dry-run 用 adapter=mock |
| 后测 | 沿用事件配对真校验（test_posttest_event_pairs 口径），逐臂逐重复出具 |

## 4. 判读表（v3：主表多列口径，v9 复核 §4.3 + v10 复核 §4.1 分类修正）

三臂结果主表**至少分列**（执行器自动汇总 + 人工判读列）：

| 列 | 口径 |
|---|---|
| 槽位成功率 | terminal success / 66 分臂列出 |
| **真实调用计数** | real_started / real_completed / real_failed（网关事件权威，与槽状态并列；verified_complete 要求 66=66=66 且逐槽配对全验证） |
| 首选可发送性 | response.recommended 非空、解析合格且无 veto 的槽位占比 |
| 任一可用选项 | 四槽+directions 非 null 合计 ≥1 条可发送正文 |
| **输出分类（互斥分列）** | blank / parse_fail_not_json / schema_broken / unknown_field / unauthorized_no_reply / action_violation / scorer_crash / legal_no_reply / reply——**前七类一律不算合法收尾**（v10 复核 §4.1） |
| 事实/视角错误 | 人工判读列（软证据线索：expr findings F1–F4 + 车次/更正口径核对），不计检测器计数为结论 |
| 各槽位实际非空交付数 | 四槽+directions 非 null 计数逐槽列出 |
| 合法收尾 | legal_no_reply 单列（解析合格+无 veto+显式/隐式不回且无相冲突正文） |

- 防表面优势：A3 若把更多 directions 置 null，"缺陷少"不成立——以"任一可用
  选项 + 首选可发送性 + 事实/视角错误"并列呈现。
- 判读优先级：每个节点"有没有可用回复、哪些事实/角色错、是否更愿意发"；
  检索以 trace 证据 + 词面报告（retr_report v2）为证，语义使用人工判读。
- 词面命中=初筛标记非定罪：HER 问怎么应对第三方时"你就跟她说…"类自然建议
  不记军师腔（v9 复核 §3.3 口径已入 expression.md 与 expr_audit 标签）。
- 检测器计数不能充当真人感结论；真人感由原始回复逐槽判读。
- 用户语气裁断：仅 4–6 组真正的语气分歧进用户表。
- 允许负结果：A3 表达策略若无效或伤害自然度，如实报告。

## 5. 执行前置检查（授权后、首调用前）

1. 散列复核：本文件 §0–§2 散列与工作区现状一致（不一致即停）；版本清单含
   三臂机制/expression/params×3/scorer/assembly/runner/gateway/expr_audit/
   两题面/本方案（v10 复核 §4.2：不能只锁十个文件而遗漏实际输入依赖）。
2. 离线全绿：test_retr_idf / test_expr_candidate / test_nca_ncb_episodes /
   test_three_arm_executor / 既有九套测试。
3. **同路径 dry-run 已完成（v2 执行器口径）**：66 槽 mock 全量走 runner/gateway
   真实路径，**恰 66 次真实 started**、逐槽一对一配对、额度/隔离/轮换/
   expr_block 断言全过；反例矩阵（网关内部超时停批不可恢复 / occupied 阻断 /
   checkpoint 缺失落后超前拒绝 / 第 67 请求发出前拒绝 / 配置漂移拒绝）全过。
4. 事件账初始化：66 槽位登记（arm/episode/turn/repeat），逐槽 occupied→terminal
   配对 + 真实网关事件计数并列。

## 6. 授权状态

**本批未执行任何真实调用。** 本清单为完整调用方案（66 次、硬上限、无重试、
fail-fast），待用户一次性授权后依 §3 槽位轮换执行；授权范围外不自行扩样、
不自行重试、不自行恢复失败批次。
