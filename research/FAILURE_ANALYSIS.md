# FAILURE_ANALYSIS.md —— 失败机制保留与未入选说明

> 本文回答四个问题：① 项目登记了哪些已知失败族、当前判决状态如何；② 哪些候选未入选、判决与淘汰/保留决定是什么；③ 负结果的保留原则是什么；④ runs 域登记在案的断跑与部分执行证据有哪些。本文是失败与负结果的保留登记文件，不是新失败分析：全部条目引自判决原文件，原文保留、不改写、不翻案。
>
> 纪律声明（全文有效）：① **零 sha256**——按 2026-11-12 用户令停用一切哈希校验，本文不计算、不引用、不比对任何哈希值；② **零隐私**——不含真实聊天内容、密钥与个人信息，涉及用户真实事故只描述机制、不引用对话原文；③ 全部数字带来源标签（判决原文件名 + 日期，或审计/登记文件名）；④ **限定语一律整句呈现**——凡判决或复核登记过的限定语，本文按原文件口径以完整句子写出，不压缩、不弱化；⑤ **零防御性标签**（2026-11-12 决策一）——对任何资产不加防御性状态标记，一律按普通资产口径如实登记；⑥ 撰写全程只读，未向用户提问。

---

## 1. 已知失败九族（KNOWN_FAILURES 门禁）

已知故障登记的权威正文是 [`refs/KNOWN_FAILURES.md`](refs/KNOWN_FAILURES.md)（v0.2 门禁，共 9 个失败族条目，最后更新 2026-11-04）；[`arena/KNOWN_FAILURES.md`](arena/KNOWN_FAILURES.md) 是 harness 代码域挂的引用指针，正文只在 refs 侧维护以防止双源漂移（指针文件自述 diff 零差异）。已知故障回归是晋级流程第 2 步的硬门：任何候选不得重新引入以下真实事故，回归门不通过不得晋级。

产品侧逐项三值判决（STILL_PRESENT / FIXED / NEEDS_RUNTIME_TEST）出自 [`refs/version_closeout_20261106/T5_KNOWN_FAILURES_AUDIT.md`](refs/version_closeout_20261106/T5_KNOWN_FAILURES_AUDIT.md)（2026-11-06，对 closeout-20261106 产品源码只读审计）。九族逐族登记如下（每族一句现象描述 + 判决引用）：

### F1 角色错位（三元组混淆）

想法输入、备注内容与历史残留行被错当成对话角色信息使用，导致私密感受泄入可发送话术、用户猜测被升级为事实入库。判决引用：T5 审计判 F1.1、F1.2 为 FIXED，F1.3 为 STILL_PRESENT（备注逐字注入且无反泄露规则，需产品侧另修），F1.4 为 STILL_PRESENT（部分修复：非法来源 fact 已拒收，无来源 fact 仍入库）。

### F2 话题污染

话题切换后旧话题仍句句纠缠、已回应事项无新信息仍反复追问。判决引用：T5 审计判 F2.1 为 STILL_PRESENT（切换判定仍是单判据、完全信任模型单轮输出的 topic_status；scene 条目 2 小时 TTL 使污染有界但不清零），F2.2 为 NEEDS_RUNTIME_TEST（plan 侧已部分缓解，scene 事实去重跨轮换源重述仍会重复）。

### F3 记忆污染

模型生成的候选话术被当成已发生事实入库、计划改期后旧事项复活、纠错后旧值在摘要中回流覆盖新值、同一事实被多次摘要而重复增信。判决引用：T5 审计判 F3.1 为 FIXED（入库 sourceIds 必须命中本轮冻结快照），F3.2 为 FIXED（主因已修，残留"换名旁路"：同一已取消事项换个名字即获得新身份），F3.3 为 STILL_PRESENT（纠正只在注入侧生效，提取/重写链完全不消费纠正表），F3.4 为 PARTIAL / NEEDS_RUNTIME_TEST。

### F4 表达缺陷

回复平淡、AI 味（成语/书面语/客服腔）、四风格趋同、对方无理取闹时无条件道歉讨好。判决引用：T5 审计判 F4.1–F4.4 均为 PARTIAL / NEEDS_RUNTIME_TEST（资产层有风格与自然度检查条款，属提示词缓解而非机制保障，实际效果只能运行判定）。

### F5 装配缺陷

近窗截断丢失完整交流单元（问句与应答被分开截断），以及关键约束置于 prompt 中段时被忽略（Lost-in-the-Middle）。判决引用：T5 审计判 F5.1、F5.2 均为 STILL_PRESENT（产品回复 prompt 上下文上限为近 2 轮 + 场景 2 条 + 经验末 3 块，raw 层不进回复 prompt；预算超支时中段直接丢弃）；arena 侧 window_expand_b 扩窗机制对该族有效但当时未移植进产品。

### F6 现实一致性盲区（五子族 F6.1–F6.5）

物理不可能动作、时间/空间错位、无视分离状态、远距离问题用近距离方案解决，以及为避免前四者而句句强调距离的反向过纠。判决引用：T5 审计判 F6.1–F6.4 为 STILL_PRESENT（全库仅有问卷期静态画像预设，场景判定纯关键词、无位置/时间/分离状态感知），F6.5 为 STILL_PRESENT / NEEDS_RUNTIME_TEST（无任何反向配重规则）。

### FK-LR2-01 长剧域逐字事实保持塌陷（2026-11-01 登记）

>24 轮故事中早期轮次的逐字事实（数字/日期/约定原文）在两臂共用的"persona 种子 + 最近 12 条滑窗"上下文结构中大量滑出：T25-32 段机械窗口保持率 47.6–50.4%，亲读扣除等义投影/作废合法项后真实保持 38.6–55.7%，且随轮数继续下滑（登记文件 refs/KNOWN_FAILURES.md FK-LR2-01 节，证据批 BATCH-LONGRANGE-LR2-LRE060LRE048-20261101，144/144 零失败）。判决引用：登记判决 LR2_VERDICT_20261101.md；修复归属判决 FLJUDGE_VERDICT_20261102.md（2026-11-02，RESOLVED(cand)：修复判给 window_expand_b 的 3600 CU 扩窗方案，>24 轮判决批 T25-32 真实保持 1.0，成本 −41.9%，KEEP）；域迁移终审 LD1_VERDICT_20261102.md（非搬家域四长剧 T25-32 全 1.0、成本 −44.0%，KEEP，迁移成立）。限定语整句：FLJUDGE 判决中的 0.493（LRE060）与 0.509（LRE048）是同一父本臂 r9_slim 在两篇故事上的读数，不存在独立"对照"臂，引用时列名必须写"滑窗父本（r9_slim）两篇读数"。同族处置：竞争臂 fact_ledger_v1/v2 被淘汰（FLREG/FLREG2 判决，注入认识论缺陷），v0_minimal_events 淘汰出 >24 轮域；塌陷事实记录保留不改写；r9_slim 在 ≤24 轮域地位不变。产品侧判决 STILL_PRESENT（结构性，T5 审计，修复机制当时未移植进产品）。

### FK-SCORE-01 评分器 source_ids 契约缺口（2026-11-02 发现并修复）

评分器 C1 校验只认本轮 canonical id_map，模型引用 prompt 中真实渲染的历史标签 `m_src[i]` 时被误判 `illegal_source_id` 并程序 0 分（4752 份历史 scoring 中 4 例，引用的标签全部在 prompt 内实际存在，模型无错）。判决引用：修复登记见 refs/KNOWN_FAILURES.md FK-SCORE-01 节（2026-11-02，W12-02 实测副产物发现）：runner 按评估侧 episode 真实计数 prior_real_n 扩合法引用集，候选伪造编号仍被拒；离线重评 4 例全部 10.0/veto 清除（SCORER_MSRC_REEVAL_20261102.json），三份受影响判决文档（FLREG/FLREG2/FLJUDGE）均附记且结论不变（FLREG/FLREG2 本就因语义门淘汰，FLJUDGE KEEP 主证据是机械保持率）。隔离路径复现：2026-11-03 由 W19-01 修复关闭（trusted_controller 与 runner 同口径，单测 4/4；离线重评 608 个 scoring 文件，原件未动），红队复核 13 个 illegal_source_id 位逐一独立重算全对（REDACT_TEAM_W19_01_CHECK_20261103.md）；W18B 的 9 个 json_schema 失败属 schema 族，与本修复正交。

### FK-INJECT-01 追加注入块 → 输出契约漂移（2026-11-04 登记，W22FULL 判决 REV2）

在冠军链候选上追加任一注入块（supp 或 attr，约 80–120CU）后，候选臂 json_schema_broken 恒约翻倍，且在 W18B（320 位）与 W22 批（232×2）双批复现（登记文件 refs/KNOWN_FAILURES.md FK-INJECT-01 节）。判决引用：W22FULL_VERDICT_REV2_20261104.md（2026-11-04）登记本族证据，W22 批契约失败率 4.7%→9.5%，按研究区 AGENTS §5 阻断组件直接继承，是 uc_attr 判 KEEP_NO_PROMOTE 的直接原因之一。限定语整句：本族的因果尚未消融（注入锚定块本身 vs 采样噪声），目前为跨批一致性佐证而非因果结论；该族正是输出契约守卫（guard，W17c ≤24 轮域组件）的设计靶点，后继候选 uc_attr_guard（锚定+守卫合并）为预登记验证路径。产品侧判决 NEEDS_RUNTIME_TEST（T5 审计：产品侧注入块与严格 JSON 契约的结构条件存在但无守卫，实际失败率需运行探针）。

---

## 2. 未入选候选逐项登记（b2 / W19 / uc_retry / W22 / CF1）

总章程阶段 5 原文要求："所有其他机制仍然保留在公开研究归档中，按已验证、初步有效、失败、未判定、被替代等状态整理"（00_LOVEBRAIN_FINAL_EXECUTION_MASTER.md §6 阶段 5）。本节对核心机制主线（03 指导书 §5）的五项负结果逐项登记判决与淘汰/保留决定；五项全部经 guide3-batchB-evidence-recheck.md §2 于 2026-11-12 对判决原文件逐项复核，结论为：五项负结果无一处翻案、无一处数字错抄（03 指导书引用的约 60 项读数中一致 54 项、数字级错配 0 项、无法核对 0 项）。

分类口径先行说明（限定语整句）：candidates-classification.csv 的 champion 分类（9/50，window_expand_b 冠军系）依据是目录名规律加 lineage status 字段，因此**分类标签不等于最终判决状态**；本节各候选在分类表中的行均标 champion 系，而其淘汰/未晋升/保留决定以 refs/ 判决原文件为准，两套口径并列、不互相改写。custody 批次登记范围说明（限定语整句）：custody-ledger-analysis.md 的批次表逐批登记到 BATCH-W19B-CROSS-20261103 为止，W21/W22/CF1/W14M02 的独立批名行不在该分析件登记范围内，这不是账本缺失而是登记范围口径——W 波判决批的执行证据不在 runs/（288 目录零登记）而在 logs/auth_budget/ 的 AUTH-* 授权件（CF1-W13/W21-FULL/W22-FULL 等实测在位）加 refs 判决书（guide2-verification-report §4.2 O-3 定位口径），02/03 均未声称这些批在 runs/ 内。

### 2.1 b2 —— window_expand_b2（账本式事实压制），W14M02

判决：W14M02_JUDGMENT_VERDICT_20261102.md（2026-11-02）判 **H_SUP_null 触发 → window_expand_b2 淘汰（不调参重跑）**；触发位为 LRE146_T20 亲读成立 1 例——故事角色的单方说法被升级为确定事实，并附加了无来源依据的归属转述。淘汰/保留决定：**淘汰**，历史实现与结果原样保留。限定语整句（R-07/G-B5，不弱化）：该判决的机制归因是"检测覆盖缺口，非压制失效"——该篇压制段为空、b2 与父本输入逐字节相同、T20 坐实两臂同暴露，因此不得把该失败写成"b2 机制主动坐实"。教训（03 §5.2）：记忆结构化不等于事实可靠；未确认状态需要单独保留，不能只靠事实账本压制。

### 2.2 W19 合并候选 —— window_expand_b_norecap_uc_guard

判决：W19CROSS_VERDICT_20261103.md（2026-11-03）判 **不宣布开发集总冠军，双域冠军保留**；uc_guard 记为跨域候选未晋升（两域全过才算总冠军，long40 破门）。读数：G3 long40 空回复 5 ≠ 0（LRE138 T32/T36/T37/T38/T39 全 0 字节），short12 空回复 0 且 G1/G2/G4 全 PASS（短域通过）；G5 故事级方向翻转 4/10。淘汰/保留决定：**合并候选不晋升，双域分域冠军保留**（uc = >24 轮域、guard = ≤24 轮域）；uc_guard 目录保留为 03 §8.1"完整候选装配"参考件（批次 A 全件核对一致）。限定语整句（03 §5.1 原文教训）：两个机制分别有价值不等于组合后更好，不为合成一个名字追加实验。

### 2.3 uc_retry —— window_expand_b_norecap_uc_retry，W21

判决：W21FULL_VERDICT_20261104.md（2026-11-04）判 **uc_retry = 跨域候选未晋升**；同批 G3' 实测重试修复率 12/12 = 100%（同 Prompt 确定性重试，双臂对称 cand 5/5、par 7/7），Prompt 增量 +0.00%（821,643 = 821,643），额外调用按重试另册 24/92 如实记账，G5 swap 翻转 3/10 → 不得宣布单冠军。淘汰/保留决定：**重试机制作为评测基建组件保留**（可靠性组件，判决状态节原文口径），不包装成新的质量冠军；uc_retry 候选不晋升。

### 2.4 W22 —— window_expand_b_norecap_uc_attr（uc_attr）

版本标签限定语整句（R-08/G-B4，必带）：本节全部 W22 数字出自 **W22FULL_VERDICT_REV2_20261104.md（2026-11-04，取代同日 REV1）**——REV1 曾判 DUAL_CHAMPIONS_RETAINED 且其 G3 veto 0/0 读数被红队审计推翻，因此引用 W22 数字必须携带 REV2 版本标签，不带标签会得出"判决与读数不符"的误判。判决：REV2 判 **KEEP_NO_PROMOTE**；读数为 json_schema_broken par 11 → cand 22（n=232/臂）、veto par 19 / cand 29（G3 = FAIL）、G5 swap 翻转 7/22。淘汰/保留决定：**保留实现、不晋升**。结论整句（03 §5.4）：不能为了降低归因错误，接受更严重的输出契约回归。

### 2.5 CF1 —— 反事实跟随对照（parent r9_slim vs candidate window_expand_b）

判决：CF1_VERDICT_20261102.md（2026-11-02）：跟随率 0.681（parent r9_slim）vs 0.698（candidate window_expand_b），双臂均落 **0.60–0.75 不确定带**；判决原文为不判 PASS、不淘汰任何臂、不补跑。淘汰/保留决定：**保留原始结果，不判胜**（未判定态，03 §5.5）。CF1 不是独立候选目录（对照双方为既有候选），其执行证据为 AUTH-CF1-W13 授权件加判决书（guide2 §2/O-3 定位口径）。

### 2.6 新研究线（rp1）未入选候选与五状态归类

新研究线（research_p1，2026-11-11）的未入选候选登记如下（来源：guide3-batchC-convergence.md §2.2/§2.3，读数经批次 B 对 overnight 三件套直核）：rh_b06_tiny_cascade（条件级联调用）为失败淘汰——LOOP-1 全部回复"盲答"（A 类实现 Bug），v2 升级率 45.8%、tokens/位 1,631 全场最高，路线撞墙；rh_b11（缓存布局）为未判定——约 1k Token 用户段无可测排序差异，判决 UNCERTAIN（偏淘汰），降级为装配卫生规范；rh_b12_invalidation_track 为未判定——机制按预期工作但 n=24 无质量分离；rh_b04_anchored_layout、rh_b09_extractive_slim、rh_b15_multicast_validator、rh_b15b_selector 曾获早期 KEEP，但看门狗追溯已将早期循环判决降级 PROVISIONAL（b04/b09/b12/b15/b15b/b17 全组须在合规 LOOP 复验后才可升级表述，RESUME_STATE next_steps 原文口径），引用时不得按早期 KEEP 引用。五状态归类总表（00 章程阶段 5 五状态，引 guide3-batchC §2.3）：已验证 = 双域冠军 uc/guard、3600 CU 事件流 + norecap、rh_converge（LOOP-14 KEEP，效率型定位）；初步有效 = rh_b04/rh_b09/rh_b15/rh_b15b（均降级 PROVISIONAL 待复验）；失败 = b2（H_SUP_null 淘汰）、rh_b06（撞墙淘汰）、W19 合并候选与 uc_retry（跨域未晋升）、uc_attr（KEEP_NO_PROMOTE）；未判定 = CF1、rh_b11、rh_b12；被替代 = window_expand_b（首版冠军）→ norecap（W15N01 更替）→ 分域冠军更替链，W22 REV1 被 REV2 取代。新研究线期间的失败与池天花板分析原件保留于 [`refs/research_harness_20261110/overnight/FAILURE_ANALYSIS.md`](refs/research_harness_20261110/overnight/FAILURE_ANALYSIS.md)（含 §6.2 四数表及其口径声明、b06 崩塌参照系、b12 处理端与登记端分离结论），本文不重复其正文。

---

## 3. 负结果保留声明

总章程阶段 4 原文（00_LOVEBRAIN_FINAL_EXECUTION_MASTER.md §6 阶段 4 第 10 条）：

> **10. 所有正面、负面、不确定结果都保留。**

同章程的配套条款原文：阶段 5 要求"所有其他机制仍然保留在公开研究归档中，按已验证、初步有效、失败、未判定、被替代等状态整理"；§8.1 把"失败机制""不确定结果"列入必须保留并公开的研究资产，并明确"不要只公开成功结果。失败实验也是工作量和研究证据，必须保留"；阶段 8 第 5 条要求"其余机制和失败实验通过研究归档索引公开"、第 9 条要求"所有合规研究数据、Gold、原始实验结果、失败记录和复现材料都有公开入口"；验收清单"研究"项要求"失败和不确定结果全部保留"。

本文件据此声明并执行以下保留口径：

1. **判决原文不改写。** 九族失败与五项负结果的登记数字、机制归因和判决措辞均引自判决原文件；判决后被修复的失败（FK-LR2-01 已有 KEEP 修复归属、FK-SCORE-01 已修复关闭）其塌陷/误判事实记录本身保留不改写，修复结论另立登记。
2. **被取代的版本如实登记取代关系。** W22 的 REV1 被 REV2 取代，本文只登记 REV2 读数并整句登记取代事实；早期循环判决被看门狗降级 PROVISIONAL 的候选按降级后口径登记，不按早期 KEEP 引用。
3. **不确定结果不判胜不判死。** CF1 落不确定带即登记为未判定态；rh_b11/rh_b12 的 UNCERTAIN 判决原样保留；任何"偏淘汰"都保留原词而不升级为"已淘汰"。
4. **未启动的方法不适用本声明。** 总章程阶段 4 第 4 条规定"某方法从未开始，就不启动；不必在最终公开展示中专门写一段'我们没做某方法'"——本文件只登记真实跑过且产生了失败、未晋升、未判定或部分执行结果的条目，不为从未启动的基线体系补造失败记录。
5. **失败证据目录原样保留。** runs 域全部失败/中止 run 目录只登记、不删改（见第 4 节）；迁移时失败证据与替代关系一并登记，不删失败批次。

---

## 4. runs 域断跑登记

runs 域逐 run 登记的核对基准是 runs-top-runs-check.md（2026-11-12，第 1 份指导书 §3.6）与 experiments/runs-registry.csv（288 个 run 目录全量脚本化登记）；本节登记其中的断跑与部分执行专项。处置原则（runs-top-runs-check §4 原口径）：以下目录全部为失败/中断证据，只登记、不删改，迁移时原样保留。

### 4.1 `_lc_lc_broken` 隔离断跑系列（23 个）

runs-registry.csv 登记 `_lc_lc_broken*` 共 **23 个**目录（`_lc_lc_broken` 本体 + 22 个带时间戳副本），worktree `research/experiments/runs/` 下实测在位 23 个。23 个目录结构一致：`out/_control/run_identity.json` + `run/staged/...`（候选环境快照，reference_product_v1），全部 **0 reply / 0 scoring / 0 turn**（每目录 13–17 文件、88–232KB）——run 在隔离沙箱内于产出任何答卷之前中断（run_identity 记录 candidate=reference_product_v1、adapter=echo、quota=32）。时间分布：2026-10-22 当日 13 个、2026-10-23 深夜 7 个、2026-10-26 三连断 3 个；同一 reference_product_v1 候选在 isolated 环境反复中断，属于机制调试期失败证据（runs-top-runs-check.md §4.1 含 23 行完整 run_identity 清单）。逐行分类：23/23 均为断跑残留（layout=out_layout，无 episode_manifest、无 run_summary、无账本）。

### 4.2 holdout_v1 部分执行（889/6,240，中止，无判决）

holdout_v1 为 Benchmark V1 Phase 3 holdout 正式批次（授权 AUTH-BM1-PHASE3-HOLDOUT-20261110-01），**部分执行**：计划 4 臂 × 120 集 = 6,240 位，实际 889 reply / 888 scoring（≈14.2%），2026-11-10 当晚中止（run 目录 mtime 19:09:39，未续），**无 run_summary.json、判决不存在**。执行残留如实登记（runs-top-runs-check.md §2.3/§3）：80 个 episode 目录中 69 个有 episode_manifest.json（全 complete）、11 个无；EM 内计划合计 828 位；run_records.json 70 条（DIRECT 20 / COT 19 / LEGACY 15 / CURRENT 16），69 ok + 1 error；缺 1 份 scoring 文件（COT/E405/turn_10_E405_T10，答卷在、评分缺）；`HOLDOUT_EXPOSURE_MARK_20261111.json` 已存在。处置口径整句（guide4-errata.md T-09，P1）：holdout_v1 登记为部分执行、当晚中止、判决不存在——**不得写成已完成，也不得写成 0/120 未启动**。其低执行度与 HOLDOUT_EXPOSURE_MARK 的 holdout 消耗纪律相关，由后续指导书处置（runs-top-runs-check.md §2.3 原注口径）。

### 4.3 其他失败/中止 run（保留指针）

runs-top-runs-check.md §2.2/§3/§4 登记的其余失败与中止证据同样原样保留：selection_v1_current 断跑链（E342–E348 各集中途 error.json，仅 E349 完成，已被 selection_v1 吸收替代）；selection_v1_fast / selection_v1_slow 放弃的并发方案片段（46 与 13 个 reply）；rp1_b19_mock 与 rp1_b19_mock_2 两次全 24 位 error、零产出（12 文件），第三次 rp1_b19_mock_3 修复后成功——三个目录全部保留，迁移时按"失败证据 + 替代关系"登记，不得删前两次；rp1_expand60 的 1 个 error 位（rh_b04 D60-012 读超时，119/120 诚实缺失）。以上全部目录在迁移中按失败证据原样带走，本文不另立新分析。

---

## 5. 纪律自检

| 检查项 | 结论 |
| --- | --- |
| 零 sha256 | 本文不计算、不引用、不比对任何哈希值：无任何 32/64 位十六进制串、无文件指纹、无账本哈希字段；run 与批次身份以目录名、日期与登记文件行为准（2026-11-12 用户令停用一切哈希校验） |
| 零隐私 | 本文不含真实聊天内容、联系人、账号、密钥与个人信息；F1/F6 涉及的用户真实事故只写机制级描述，不引用对话原文；b2 触发位只写"单方说法被升级为确定事实并附加无据归属"的机制描述，逐字原文以判决原文件 W14M02_JUDGMENT_VERDICT_20261102.md 为准 |
| 限定语整句 | 全文限定语以完整句子呈现且不弱化：① FLJUDGE 父本读数列名整句限定"同一父本臂 r9_slim 两篇读数、无独立对照臂"（R-04/G-B6）；② b2 机制归因整句"检测覆盖缺口，非压制失效"（R-07/G-B5）；③ W22 版本标签整句"REV2 取代同日 REV1，标签必带"（R-08/G-B4）；④ 分类标签与判决状态分离整句、custody 批次登记范围整句（§2 开头两段）；⑤ FK-INJECT-01 因果未消融整句；⑥ holdout_v1"不得写成已完成，也不得写成 0/120 未启动"整句（T-09）；⑦ rp1 早期 KEEP 降级 PROVISIONAL 整句 |
| 零防御性标签 | 按 2026-11-12 决策一，本文对任何资产（含 holdout/曝光相关登记）不加防御性状态标记，一律普通资产口径如实登记状态事实本身 |
| 数字来源 | 全部数字溯源三层之一：判决原文件名 + 日期（九族/五候选读数）、审计与登记文件名（T5_KNOWN_FAILURES_AUDIT.md、runs-top-runs-check.md、runs-registry.csv）、复核报告（guide3-batchB-evidence-recheck.md / guide3-batchC-convergence.md / guide4-errata.md），零数字由转述单独支撑 |
| 只读 | 撰写全程只读输入（判决原文件、审计件、登记件、章程），未改动任何仓库/判决/账本/登记文件；唯一写入为本文与 asset-audit 记录 |

---

*收口说明：本文为第 8 份指导书 §11 研究文档九件套的失败机制保留件；引用其读数时须连同各节限定语整句一并携带。产品侧 STILL_PRESENT 项的修复进展以产品仓库后续提交与 T5 审计的更新为准，本文不预写修复完成态。*
