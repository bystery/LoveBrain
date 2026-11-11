# 判决原文件目录索引（verdict-file-index）

- 产出日期：2026-11-12
- 依据：guide2-verify-mechanisms.md 勘误 **E3（P3）**——02 §12.4 Arena 证据索引未列判决原文件目录，本文件补齐该缺口（供 §12.4 增补行引用）
- 扫描口径：`refs\` 下全部文件名含 VERDICT 的 .md / .json，共 **53 个**（实测 `os.walk` 全量枚举，非抽样）
- 双落点：原始在 `D:\lovebrain-arena\refs\`；已随 F6 迁移至 worktree `D:\LoveBrain-research-staging\research\refs\`（两处逐一 stat 比对：**53/53 存在，大小与 mtime 全部一致，0 差异**）
- 纪律：零 sha256；只读扫描；结论行直接摘自判决文件，不读全篇；未向用户提问
- 三主线定义（`第5份指导书\05_CORE_MECHANISM_SELECTION.md` §0 冻结名单）：
  - **① 上下文装配与相关性门控**（RH-CONVERGE、意图/场景/锚注入、检索投影）
  - **② 证据优先的历史记忆**（稳定消息身份、真实消息保留、3600 CU 历史窗口、recap 删除及其消融对照）
  - **③ 事实与输出约束**（uc 未确认事项约束 + guard 契约守卫两个子机制；含账本式事实压制 b2 / fact_ledger 等同域淘汰候选）
  - **历史归档** = harness 建设/准入复核/瘦身工程线/被取代版本，不归属三主线但证据保留

## 1. 长剧探针判决书目录：refs\experiment_protocol_20261023\long_probe_20261030\（27 个）

| # | 文件 | 大小B | mtime | 一句话结论（摘自判决） | 主线归入建议 |
|---|---|---:|---|---|---|
| 1 | LONGPROBE_VERDICT_20261030.md | 3569 | 2026-10-30 06:20 | 长剧首批 2 篇 E3 vs B0：长上下文出窗回忆显著强于 B0，KEEP 判定在长剧域得到独立证据（§6） | ② |
| 2 | SECOND_ROUND_VERDICT_20261030.md | 6179 | 2026-10-30 23:47 | E3 优势全面复现但携系统性编造过去事件族，触发第一轮 severe 阻断（修正后 ELIMINATE）；继位线 B0→E3→r5_tier，终裁留 D1 批 | ② |
| 3 | THIRD_ROUND_VERDICT_20261031.md | 5851 | 2026-10-31 01:33 | r5_tier 对 E3 挑战失败不予继位（severe 反增 2/0）；E3 修正后 ELIMINATE 维持；冠军位空缺走 tiering v2 | ② |
| 4 | FOURTH_ROUND_VERDICT_20261031.md | 5828 | 2026-10-31 03:08 | 大样本（5 批 624 位）否证第一轮 ELIMINATE：E3 复职冠军（附登记缺陷），tiering 线关闭，harness 冻结 E3 架构 | ② |
| 5 | W05-03_VERDICT_20261031.md | 4687 | 2026-10-31 05:21 | r7_supersede（fixed-gate）F2 首战：行为与冠军持平+结构性修复+零回归，作为 E3 继任补丁归档 | 历史归档（F2 修复线） |
| 6 | LR2_VERDICT_20261101.md | 5560 | 2026-11-01 18:33 | v0_minimal_events 淘汰出 >24 轮域（窗口保持率 FAIL 距门槛极远），使用域维持 ≤24 轮 | ②（消融对照） |
| 7 | FLREG_VERDICT_20261101.md | 2777 | 2026-11-02 02:00 | fact_ledger_v1 回归门 FAIL（九维最差维 −0.333、severe 2>1），不进 >24 轮判决批 | ③（账本线，淘汰） |
| 8 | FLREG2_VERDICT_20261101.md | 3067 | 2026-11-02 02:00 | fact_ledger_v2 回归门 FAIL，按 H_FL_null 淘汰；账本注入线暂停（连续 2 批无语义优势） | ③（账本线，淘汰） |
| 9 | FLJUDGE_VERDICT_20261102.md | 6429 | 2026-11-02 02:00 | window_expand_b 于 >24 轮域 KEEP，prompt −41.9%，承接 FK-LR2-01 修复归属 | ② |
| 10 | CF1_VERDICT_20261102.md | 4520 | 2026-11-02 05:28 | W13-02 反事实判别批：保留不确定，无沿用故障（192/192 零失败，−51.3%） | ② |
| 11 | LD1_VERDICT_20261102.md | 5304 | 2026-11-02 11:05 | KEEP——window_expand_b 域外迁移成立（304/304 零失败，−44.0%） | ② |
| 12 | W13-03_VERDICT_20261102.md | 1983 | 2026-11-02 17:43 | H_GEN 泛化成立：双冠军在新域兴趣社交均无塌陷 | ② |
| 13 | W13-04_VERDICT_20261102.md | 1990 | 2026-11-02 20:34 | H_STAGE 成立：无回避型塌陷；渐暧昧桶 b 九维全正全面占优 | ② |
| 14 | W14M01_REGRESSION_VERDICT_20261102.md | 2149 | 2026-11-02 14:47 | b2 回归批三项门全过（回归 PASS），获进入判决批资格 | ③（b2 压制线） |
| 15 | W14M02_JUDGMENT_VERDICT_20261102.md | 2454 | 2026-11-02 18:29 | b2 判决批 H_SUP_null 触发（LRE146_T20 坐实单方说法+无据归属）→ 淘汰 | ③（b2 压制线，淘汰） |
| 16 | W15N01_ABLATION_VERDICT_20261102.md | 2652 | 2026-11-02 21:30 | norecap 消融三门全过（九维 9/9 全正、severe 1≤3、T25-32 保持 1.000） | ② |
| 17 | W16_UNC_VERDICT_20261102.md | 3287 | 2026-11-03 11:14 | KEEP → >24 轮域冠军升级为 window_expand_b_norecap_uc（severe 亲读 1 vs 6） | ③（uc） |
| 18 | W17_MERGE_VERDICT_20261102.md | 3428 | 2026-11-03 09:53 | 不合并（维持双冠军）：冻结判据 G3 破 + 契约违规率实质退化 | ③ |
| 19 | W17B_GUARD_VERDICT_20261103.md | 2627 | 2026-11-03 11:59 | KEEP：window_expand_b_norecap_guard 成为 ≤24 轮域候选，进入 W17c 合并复评 | ③（guard） |
| 20 | W17C_MERGE_VERDICT_20261103.md | 8789 | 2026-11-03 15:12 | MERGE：≤24 轮域冠军改为 b_norecap_guard，r9_slim 转历史；Rev2（红队复核）维持判决、修正 G1 口径 | ③（guard） |
| 21 | W18B_VERDICT_20261103.md | 4932 | 2026-11-03 16:10 | uc_supp vs uc：severe cand 0 vs par 2（S/U 族未实例化），判决只能写"无退化"不得写"改善" | ③（uc） |
| 22 | W19CROSS_VERDICT_20261103.md | 4854 | 2026-11-03 23:43 | 不宣布开发集总冠军，双域冠军保留（≤24=guard / >24=uc）；uc_guard 跨域候选未晋升 | ③ |
| 23 | W21_FASTLANE_G0_VERDICT_20261104.md | 2544 | 2026-11-04 03:18 | FASTLANE_PASS：uc_retry 获全量批资格（快车道只淘汰不晋级） | ③（uc_retry） |
| 24 | W21FULL_VERDICT_20261104.md | 3554 | 2026-11-04 05:53 | 保留双域冠军；uc_retry = 跨域候选未晋升（G5 故事级方向翻转 3/10） | ③（uc_retry，未晋升） |
| 25 | W22_FASTLANE_G0_VERDICT_20261104.md | 1876 | 2026-11-04 07:57 | FASTLANE_PASS：uc_attr 获全量批资格（只淘汰不晋级，severe 贴线 1≤1） | ③（uc_attr） |
| 26 | W22FULL_VERDICT_20261104.md | 7666 | 2026-11-04 10:27 | REV1（**已被同日 REV2 取代**，保留存档）：DUAL_CHAMPIONS_RETAINED——G3 读数与 G1 亲读账两处实质错误后被红队审计推翻 | ③（存档，勿引用） |
| 27 | W22FULL_VERDICT_REV2_20261104.md | 7718 | 2026-11-04 10:40 | **生效版**：KEEP_NO_PROMOTE 取代 REV1——归因锚定有效性成立（A 族 0 vs 2）但契约门 FAIL + swap 翻转，uc 不让位 | ③（uc_attr，生效版） |

## 2. 全局持续迭代判决：refs\experiment_protocol_20261023\global_harness_program_20261030\（10 个）

| # | 文件 | 大小B | mtime | 一句话结论（摘自判决） | 主线归入建议 |
|---|---|---:|---|---|---|
| 28 | W04-03_VERDICT_THRESHOLDS_PREREG.json | 1610 | 2026-10-30 03:30 | FS1 判决阈值预登记（看到评审结果前冻结）：severe 门/成本门/C1-C3 判据与 KEEP/REWORK/ELIMINATE 规则 | ②（预登记） |
| 29 | W04-03_FIRST_LOOP_VERDICT.json | 1184 | 2026-10-30 23:44 | FS1 首循环：E3 ELIMINATE（severe 2>0）、Bmin ELIMINATE（severe 3>0）——后被第 4 轮大样本否证修正 | ②（历史口径） |
| 30 | W05-04_VERDICT_20261031.md | 3274 | 2026-10-31 10:03 | 相关检索投影 vs Bmin 原文窗口：对话事实召回两臂持平 28/28，4 原子 miss 系介质定义伪差 | ① |
| 31 | W06-03_SCENARIO_VERDICT_20261031.md | 2946 | 2026-10-31 08:46 | 意图差额场景实跑：S1-S5 全部 PASS（5/5，120/120 调用零失败） | ① |
| 32 | W08-05_BRANCH_VERDICT_20261031.md | 3516 | 2026-10-31 09:17 | 受控连续分支判定：E029_T07 青岛机票钩位四分支判读（锚注入研究工具） | ① |
| 33 | W08-05_DEEPCHAT_VERDICT_20261031.md | 3033 | 2026-10-31 09:51 | 深聊位分支判定 D1-D3：D1 论点交锋 0 档损伤持续 2 轮 | ① |
| 34 | W09-02_SLIM_VERDICT_20261031.md | 2956 | 2026-10-31 10:19 | SLIM 资产候选冗余普查：system∩user 零逐字重复，冗余全为语义级同规则多陈述 | 历史归档（瘦身线） |
| 35 | W09-04_COMPOSE_VERDICT_20261031.md | 2456 | 2026-10-31 10:34 | r10_compose 补单变量门，全开态与 r7 逐字节相同（冠军本就是组合式包） | 历史归档（工程结构线） |
| 36 | G1_VERDICT_20261031.md | 2699 | 2026-10-31 11:40 | r9_slim 升格为共役冠军（瘦体优先）：九维不劣 PASS（最差 init −0.097，余量 5 倍+） | 历史归档（瘦身线） |
| 37 | LR1_VERDICT_W1005_20261031.md | 3319 | 2026-10-31 12:34 | v0_minimal_events 长程风险验证：塌陷未发生（19 条 GT 事实 T01-19 全在窗），可用域扩展至 ≤24 轮，KEEP | ②（消融对照） |

## 3. 终审与阶段质检判决（13 个）

| # | 文件 | 大小B | mtime | 一句话结论（摘自判决） | 主线归入建议 |
|---|---|---:|---|---|---|
| 38 | refs\experiment_protocol_20261023\final_mainline_review_20261029\F6_FINAL_VERDICT_20261029.md | 16842 | 2026-10-29 18:58 | 判决 REWORK（有范围条件下通过，非 ACCEPT 非 FAIL） | 历史归档（终审） |
| 39 | refs\experiment_protocol_20261023\final_mainline_review_20261029\F6_FINAL_VERDICT_20261029_REV2.md | 9347 | 2026-10-29 16:07 | 判决不变 REWORK；口径撤回改为"执行完成、核心验收未完成"，缺口精确命名 | 历史归档（终审） |
| 40 | refs\experiment_protocol_20261023\deadline_quality_20261028\FINAL_VERDICT_20261028.md | 28041 | 2026-10-28 20:54 | 不回表达修复保留；候选整体判"改进中，不签收全面超越"；泛化主张不成立 | 历史归档（截止质检） |
| 41 | refs\experiment_protocol_20261023\knowledge_quality_independent_review_20261026\REVIEW_AND_VERDICT.md | 5867 | 2026-10-27 00:04 | 签收共同位点/在场使用分离等；退修：成绩单只能称"字面覆盖与表示结构诊断"，不能称语义知识完整/保真成绩单 | 历史归档（知识质量） |
| 42 | refs\experiment_protocol_20261023\max288_v3_20261028\REVIEW_VERDICT.md | 3896 | 2026-10-28 00:12 | 已有新成果签收（P05 两 v6、P06 192 生成保留不重做），P07 尚不具备效果验收条件 | 历史归档 |
| 43 | refs\experiment_protocol_20261023\max288_v3_20261028\W02_EXIT_VERDICT.json | 4531 | 2026-10-28 07:18 | PASS_WITH_CAVEATS——七项全部有磁盘证据；三项带边界（I2 覆盖缺口、I4hes 单序未确认、n=1 无重复间变异控制） | 历史归档 |
| 44 | refs\experiment_protocol_20261023\r2_independent_review_20261024\VERDICT_AND_EXECUTION.md | 10965 | 2026-10-24 21:44 | 总判决：接近开测，保留进度，不返回考场建设；旧配对统计暂不签收 | 历史归档（准入复核） |
| 45 | refs\experiment_protocol_20261023\r2_next_batch_20261024\R2_VERDICT.md | 19649 | 2026-10-25 01:10 | r2 收口：保留方向/不替换生产；程序分与语义并列不冒充质量，三故事皆公开开发集不称泛化 | 历史归档 |
| 46 | refs\experiment_protocol_20261023\v3_trial_readiness_review_20261024\VERDICT_AND_NEXT.md | 12227 | 2026-10-24 17:03 | 签收批配置接线/v3 精简候选/输入公平性修订；仅一处 E004 数据修正后即可开跑 | 历史归档 |
| 47 | refs\experiment_protocol_20261023\v5_independent_review_20261025\VERDICT_AND_NEXT_EXECUTION.md | 10450 | 2026-10-25 09:33 | 冻结 v5 及原版对照清单可进入真实实验，无新增开测前退修项 | 历史归档 |
| 48 | refs\experiment_protocol_20261023\v5_next_batch_20261025\V5_VERDICT.md | 11748 | 2026-10-25 10:46 | 保留 v5 方向（效率型开发候选）不替换原版：省资源成立、内容层两臂接近（2:3 其余 19 平） | 历史归档 |
| 49 | refs\experiment_protocol_20261023\v5_next_batch_20261025\V5_VERDICT_REVISION_01.md | 4919 | 2026-10-25 12:00 | 评估口径修订不重跑：承认抽取器 isinstance bug 报错数字并修正（原文保留叠加层） | 历史归档 |
| 50 | refs\experiment_protocol_20261023\v5_real_independent_review_20261025\REVIEW_VERDICT.md | 4692 | 2026-10-25 11:07 | 签收省量成果（48/48，总 token −57.65%、p50 改善 p95 变差）；不能宣布整体性能更好、质量相当或可替换原版 | 历史归档 |

## 4. Harness 准入复核与研究看门狗（3 个）

| # | 文件 | 大小B | mtime | 一句话结论（摘自判决） | 主线归入建议 |
|---|---|---:|---|---|---|
| 51 | refs\arena_v1_review_20261021\VERDICT.json | 1259 | 2026-10-21 17:46 | NOT_READY（worker 自称 OFFLINE_READY 不成立）：0 真实模型调用、0 ACL 操作，重开 G01/G03/G04/G05 等门组 | 历史归档（harness 准入） |
| 52 | refs\arena_v14_review_20261022\VERDICT.json | 857 | 2026-10-22 09:26 | NOT_READY：真实候选生命周期/可信评分器已签收；信任执行边界、持久预算记账、fail-closed 集成验证仍阻塞 | 历史归档（harness 准入） |
| 53 | refs\research_harness_20261110\overnight\WATCHDOG_VERDICT.json | 7855 | 2026-11-11 16:29 | RH-CONVERGE 15 循环看门狗：2 PASS、9 FAIL、4 FAIL_WALL（max_consecutive_violation 11）；仅通过检查的循环可作证据 | ①（RH-CONVERGE 证据资格） |

## 5. 主线归入统计

| 归入 | 数量 | 说明 |
|---|---:|---|
| ① 上下文装配与相关性门控 | 5 | W05-04、W06-03、W08-05×2、WATCHDOG（RH-CONVERGE LOOP-14 资格） |
| ② 证据优先的历史记忆 | 14 | E3 四轮 + LONGPROBE + FLJUDGE/CF1/LD1/W13×2/W15N01 + LR1/LR2 + W04-03 两件（预登记/历史口径） |
| ③ 事实与输出约束 | 15 | W16/W17/W17B/W17C/W18B/W19CROSS/W21×2/W22×3 + b2 两件 + fact_ledger 两件 |
| 历史归档 | 19 | 准入复核（v1/v14/r2/v3/v5 族）、终审 F6×2、瘦身/工程线（W05-03/W09-02/W09-04/G1）、阶段质检 4 件 |
| 合计 | **53** | 与 os.walk 实测枚举数一致 |

## 6. F6 迁移校验（worktree = D:\LoveBrain-research-staging\research\refs\）

- 校验方式：对 53 个原文件逐一取 `relpath` 后在 worktree 同相对路径 stat，比对 `st_size` 与 `st_mtime`（零 sha256 纪律下不用哈希）。
- 结果：**53/53 全部存在；大小全部一致；mtime 全部一致；差异 0。**
- 附带确认：E3 建议同行列出的伴侣文件 `refs\KNOWN_FAILURES.md`（10,079B，2026-11-04 10:42）亦已双落点在位。

## 7. 版本标签提示（引用判决数字时必读）

- **W22 数字口径 = `W22FULL_VERDICT_REV2_20261104.md`**（取代同日 10:27 的 REV1；REV1 的 G3 读数与 DUAL_CHAMPIONS_RETAINED 判决已被红队审计推翻，仅存档勿引用）。
- W17C 存在 Rev2 附记（判决维持 MERGE，修正 G1 口径）；W17_MERGE 与 W17C_MERGE 是两个不同判决（前者否决升格、后者 ≤24 域易主）。
- F6_FINAL_VERDICT 与 REV2 均为 REWORK 判决，REV2 收紧口径；两文件 mtime 顺序以实测为准（REV2 16:07 早于原稿 18:58）。

---
*方法附注：本索引只读扫描 arena refs 与 worktree refs；写入仅本文件及其 worktree 副本 `research\refs\VERDICT-INDEX.md`；结论行摘自各判决文件的结论段/判决行（多数为「〇、一句话结论」或首部「## 判决」行）；零 sha256；零模型调用；未向用户提问。*
