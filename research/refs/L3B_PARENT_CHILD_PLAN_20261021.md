# L3B 父子对照方案 v6（52 槽）——v12 §6-D 配置 + v13/v16 复核整改版

日期：2026-10-21。前置：v12 复核 `refs/REAL66_V12_INDEPENDENT_REVIEW_20261021/`、
v13 复核 `refs/WAVE15_V13_INDEPENDENT_REVIEW_20261021/`、
v16 复核 `refs/WAVE16_INDEPENDENT_REVIEW_20261021/`。
**本文件是配置登记，不是调用授权。** 52 次真实生成需用户另行明确授权；
不得用此前 66 次授权追加本批。

## 0a. v16 复核整改增补（§7，本版 v5→v6 变更）

- **类型门分工**：完整 HH:MM 验证通过后从 token 检查中剥离（不再散字重审）——
  149 条旧真实摘要回放 valid_hm_conflict=0，复核点名的 D3145 新车次事实在库
  （logs/replay149_gate_v17_20261021.json，132 接受/17 拒绝三分类）；
- **纠正限定主体**：硬作废仅作用于与更正说话人同主体的旧事实；跨主体共享值
  只留痕 uncertain + 注入警示（HER 改签不再作废 ME 的同车次事实）；
- **状态证据化**：过去/完成标记必须来自所引原文（摘要自证不作数）；共同活动
  event 须原文同时含共同词+共同完成词；ME 接受先过拒绝/推脱标记；证据不支持
  → unconfirmed（不当事实背书）；
- **ongoing 支持门**：实体支持检查（时刻逐字 + token 原文池），不可核验 →
  隔离为"未核验摘要（待确认，不得当事实）"，禁止裸注入当事实；
- **may_decline 许可绑定**：匿名生成器补许可对照断言（true→false / false→true
  双向守卫，tools/test_anon_binding_guards.py 8 项）；
- **中文数词**：明确缩窄为已支持的单字归一（两/二→2…），不声称全部数量等价。

## 0. v13 复核整改增补（§7/§8，本版 v4→v5 变更）

- **冻结补齐**：父子两臂实际加载的 `candidates/phase1_retr_idf/mechanism/mechanism.py`
  纳入 FROZEN_SHA / FREEZE_FILES / 槽级依赖注记（A3/F 两臂均记 dep_mechanism_sha256）；
- **run_id 唯一化**：run_id 使用完整 episode 名（原 ep[:3] 使 4 个 DEV 槽 run_id 重复），
  52 槽 52 个不同 run_id；
- **CAP 文案随 CAP 生成**（拒绝文案不再出现硬编码"67"）；
- **开发组序修正**：代码轮换为 [("A3","F"),("F","A3")] 取模——实际顺序
  DEV_MEETING=A3/F、DEV_TWO_BOXES=F/A3、DEV_NEW_STATION=A3/F、DEV_ROOMMATE=F/A3
  （v4 文档"同题先 A3 后 F"与代码不符，保留代码、修正文档）；
- **口径修正**：执行器参数化后**不称"66 槽行为逐字节等价"**——准确口径：核心请求/
  配对/停批逻辑同源复用，52 槽矩阵与独立演练通过，旧 66 执行环境已封存；
- **匿名材料生成器 v3**：绑定断言升级 id+speaker+clock+有效意图+may_decline 许可
  +原回复双侧绑定（tools/test_anon_binding_guards.py 8 项守卫自测）；
- **WAVE14 报告收口**：旧正文移历史归档，当前报告单表。

## 1. 三臂准入判决（复核 §6，全单照收）

A1 保留开发父版本；A2 检索能力保留（本批未证明事实质量更好）；A3 表达能力保留
（人称/习惯/数量/时地问题仍在）。**三者均不晋级。正式晋级与自主进化维持冻结。**

## 2. 批次设计（一次父子对照，预算 52 封顶）

| 项 | 值 |
|---|---|
| 父臂 | `A3` = candidates/phase2_expr（开发父版本） |
| 子臂 | `F` = candidates/phase3_facts（事实出处与纠正状态，唯一主变量） |
| 共同工程修复 | `arena/lb_assembly.py` infer_scene v12 §6B 整改——**两臂同时施加，不计候选收益** |
| 主对照 | NCA_CORR_INTENT_v1（6 轮）+ NCB_ENTITY_DENSE_v1（5 轮）× r1/r2 × 两臂 = **44 槽** |
| 开发性正反例 | 4 题 × 两臂各一次 = **8 槽**（DEV_MEETING_FACTS / DEV_TWO_BOXES / DEV_NEW_STATION / DEV_ROOMMATE_ADVICE，各 1 轮，无重复组） |
| 合计 | **52 槽硬上限**；最多 52 started；无生成重试；坏 JSON 如实记账；传输/审计失败停批不换批号 |
| 轮换 | 主对照 22 组按 [("A3","F"),("F","A3")] 交错；开发题无重复不轮换，4 题按 [("A3","F"),("F","A3")] 取模交错（DEV_MEETING=A3/F、DEV_TWO_BOXES=F/A3、DEV_NEW_STATION=A3/F、DEV_ROOMMATE=F/A3） |
| 额度权威 | 真实网关 attempt_started 事件计数；第 53 次请求发出前拒绝 |
| 恢复路径 | 禁用（fail-fast：failed/occupied 即整批终止，不重跑不补齐） |
| 执行器 | `tools/l3b_parent_child_executor.py`——导入 66 槽执行器（已验证守卫同源复用），仅覆盖配置常量；`tools/l3b_three_arm_executor.py` 参数化改造为行为保持（v3 默认值不变） |

## 3. 冻结散列登记（v6；执行前逐项核对，失配即停）

| 文件 | sha256 |
|---|---|
| candidates/phase1_retr_idf/mechanism/mechanism.py（v13 §7：实际检索依赖纳入冻结） | `50c095eb7ee021b24831f741d56ea7cad5e30466a11f25217b476ecee49f8727` |
| candidates/phase2_expr/mechanism/mechanism.py | `31a1dd72e355257b75b12bb7f5d74cf104abea6084ba904972d2b1908dfd482a` |
| candidates/phase3_facts/mechanism/mechanism.py（v16 三项退修第 3 稿） | `c9f05bddef71c96664bc3aa5d5bb69ea98168aa35e58e3af4c9360a61fef03ed` |
| candidates/phase2_expr/assets/system_prompt/expression.md | `707128023da1def0177210d120986ef67a3094ee74b40646a7c2399c4214c367` |
| candidates/phase3_facts/assets/system_prompt/facts_evidence.md（v13 异题材示例版） | `b9516d44f1a04b20bc36add6d841f3fb4c450ee245a013ba22ec080d9f65d1f7` |
| candidates/phase2_expr/params.json | `e215b71811aee85d7051514c2ff13fdfc12323fbaf8aee75cdc987ebfe0406dc` |
| candidates/phase3_facts/params.json | `e215b71811aee85d7051514c2ff13fdfc12323fbaf8aee75cdc987ebfe0406dc` |
| arena/lb_scorer.py | `678c4e191b20394da444809814ff9f7665d23cb83d03c997543fb89272f7e959` |
| arena/lb_assembly.py（v12 §6B + v13 §6 同族闭合版） | `e6c4b58900b3197e8583cfcabf2c5f8a6a147f10da3b471a6f3f6f705724425c` |
| arena/lb_runner.py | `89568033f74124a032e388d7bee0df2635b2ca97c2031a79b5ae02377cf137de` |
| arena/lb_gateway.py | `b0587e6ad137898e6facbed469bf42687f58dc6c1b0bdb9a4c5a6897669f8a35` |
| tools/expr_audit.py | `61486cd6a38c763705651139429349c2a24772c5b9ff2d8328537a4fd7a98dcd` |
| benchmark/episodes/NCA_CORR_INTENT_v1.json | `018826ba3f7025edd8139cb7b02267d38e096589ecf5f6c9dbe4e43778470e57` |
| benchmark/episodes/NCB_ENTITY_DENSE_v1.json | `8fbb29b5fec8f6ab61a0e89d8fe9e121024d482aeb4cda9f125f7b5c6afb62f7` |
| benchmark/episodes/dev/DEV_MEETING_FACTS_v1.json | `cbe5cb6eda859b1f5a6bb0696eefcf57ee82480cf20a4f80a5a19eb6b42819f4` |
| benchmark/episodes/dev/DEV_TWO_BOXES_v1.json | `6b68bafdf53a171d843d4c257eb470353c4d330d306ac144ba3dcad5e0852c3f` |
| benchmark/episodes/dev/DEV_NEW_STATION_v1.json | `743eeb75a8456b1d4e813f5c80e35fd0c5900ba9dfd9d2fa59d258c42940181d` |
| benchmark/episodes/dev/DEV_ROOMMATE_ADVICE_v1.json | `d498b46baedc750ae9b8faa42f60c74b244c090dc33295df23f2d72e8f1730d7` |
| 本方案（登记后冻结） | 见 refs/REVIEW_PACKAGE_MANIFEST.json 旁路核对 |

## 4. 执行器参数化改造声明（不覆盖方案 v3）

`tools/l3b_three_arm_executor.py` 的字面量内嵌配置提为模块级常量（BATCH_PREFIX /
EXPR_WANT / FREEZE_FILES / EP_REPEATS / ROTATIONS 取模），**默认值 = 方案 v3 冻结
配置；核心请求/配对/停批逻辑同源复用**（v13 复核 §7 口径：参数化改变了 run_id
格式等，不再称"逐字节等价"；52 槽矩阵与独立演练通过，旧 66 执行环境已封存）。
v3 批次（l3b_66_real66_v1）账内记录的执行器 sha
`2d3e5724…c68849` 为改造前版本；改造后 66 配置因 `arena/lb_assembly.py` 已按
§6B 升级而**必然 FROZEN_MISMATCH 拒绝运行**（封存机制生效，test 0 项断言）。
等价守卫矩阵全部转移至 52 槽执行器回归（同源守卫代码）。

## 5. 离线验收（执行前必须全过，已落盘）

- `tools/test_phase3_facts.py`（14 项）：改签到站写回链阻断 / supersession /
  邀请≠约定 / 意向 / 锚点 / 越界拒绝 / 合并引用 / memory-off 写回关闭 /
  检索零改动 / 双资产在场——验证实际存储与下一轮注入，不只验函数返回；
- `tools/test_scene_assembly.py`（19 项 + 冻结 episode 回归）：收尾跨消息组合 /
  外部坏消息≠争执 / 追问反例 / 真争执不误伤 / 当前发言动作重判（v13 §6 同族四修）；
- phase1/phase2/memory-off/NCA-NCB 全套既有回归无回归；
- 52 槽执行器等价矩阵 `tools/test_parent_child_executor.py`：干净 52 / chaos 停批 /
  occupied 阻断 / checkpoint 三反例 / 第 53 次拒绝 / 配置漂移拒绝 / 六类汇总分列。

## 6. 交付口径（复核 §6-D，一次交齐）

首选与备选分层质量（57/5/4 式分层，不混总）、至少到下一轮的写回/再注入证据、
各故障族分母与负例（钟点/否定经历/人称/湖州杭州/婚礼跨日/一盒归属/改签写回/
无确认约定）、候选数量/null/格式退化、6–8 组真实题面绑定的匿名材料（生成器
从冻结 episode 提取 + calls.json 绑定断言）。即使未改善也报实测，不临时换样本。
