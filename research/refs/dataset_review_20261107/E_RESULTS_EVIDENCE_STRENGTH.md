# E — 实验结论与证据强度清单（2026-11-07）

> 逐批核对判决书与状态文件，把每个批次的结论归入四类并给出证据强度评级。
> 出处：`refs/experiment_protocol_20261023/long_probe_20261030/` 各判决书、`refs/experiment_protocol_20261023/global_harness_program_20261030/`（FS1 系）、`RESEARCH_STATE.json`。评分器版本按各批判决书记载与 `arena/lb_scorer.py` 版本志归位。
> 本文件只登记证据；评估体系本身的可靠性审查见同目录 `D_EVALUATION_RELIABILITY.md`。

## 0. 阅读须知（先读这四条）

1. **结论类型四类**：`已证实（含跨域复现）`＝判决批级结论有跑前冻结判据＋程序层＋B3 亲读（或机械量级差）通道，且在第二批复现（跨批/跨域）；`开发集局部结果`＝单批成立、未复现或复现面受限；`无证据推测（未做）`＝有说法无实证；`负结果与历史失效`＝淘汰/挑战失败/阻断/不确定/勘误。
2. **证据强度评级**：`强`＝预注册判据＋程序层＋亲读三通道且跨批复现；`中`＝单批多通道（预注册＋程序＋亲读）；`弱`＝单通道（仅 judge、仅机械、仅亲读）或 judge 未亲读/评审降权批。
3. **程序契约通过率 ≠ 回复质量**：所有批次"程序分/程序 mean10/契约违规率"只证明输出格式与红线契约；语义结论只能来自"语义九维（judge，未校准，辅助）"或"B3 亲读"或"机械保持率（窗口保持器，同器同 GT）"。下表每行按 `程序`/`九维`/`亲读`/`机械保持` 标注证据通道。
4. **跨批数字不可直接比**：请求位嵌套于故事、素材逐批消耗、judge 与评分器在批次间发生过勘误（W04-02、FK-SCORE-01、W19-01）、v0.9.3 才接入 gold。现役冠军（>24 轮域 `window_expand_b_norecap_uc` / ≤24 轮域 `window_expand_b_norecap_guard`，出处 `RESEARCH_STATE.json` main_incumbent）的得分**分别来自 W16 与 W17b+W17c 各自判决批**，任何"从 FS1 到 W22 的得分曲线"都是拼接错觉（详见 §3）。

---

## 1. 证据强度总表

### 1.1 第一环：公平原版与最小对照（FS1，2026-10-30）

| 批次 | 比较双方 | 测试集与分母 | 评分器与判据版本 | 关键数字（按通道标注） | 结论类型 | 证据路径 | 强度 |
|---|---|---|---|---|---|---|---|
| FS1-A | E3 (action_policy_r4_guard_e3) vs B0 (reference_product_v1) | 4 篇 stage2 短剧 ×24 轮 ×双臂 = 96 attempts / 48 有效对 | 评分器 v0.9.2 系（gold sidecar 前，gold={} 未实例化 C14/C15）；判据=W04-03 预注册阈值（跑前冻结） | `程序`：10.0 vs 9.38；`九维`：全 9 维非负（memory +0.532/interact +0.562/init +0.75 最强，fact −0.043 微负）；`亲读`：severe 修正后 E3 2 / B0 0（E026_T05 实锤）；成本 prompt −18.5%、p50 −16.2%、completion +34%（交易已报告）；W 愿发=UNKNOWN | 开发集局部结果（首环判决经 W04-02 勘误重跑为 ELIMINATE，第四轮复权 KEEP——erratum 链见 §4；九维优势并入跨批链升"已证实"） | `global_harness_program_20261030/W04-04_FS1_FULL_SCORECARD.md`（含修正补记）、`W04-03_FIRST_LOOP_VERDICT.json`、`W04-02_ERRATA_SEVERE_SWAP_20261030.md`、`fs1_generation_result`（RESEARCH_STATE） | 中 |
| FS1-B | Bmin (v0_minimal_events) vs B0 | 同上 96 attempts / 48 对 | 同上 | `程序`：9.79 vs 9.79；`九维`：Bmin 多维正但 C1 日常承接 False（interact 0.68/emotion 0.60）、C2 弱证实 False；`亲读`：severe 修正后 Bmin 3 / B0 0；成本 prompt −63.6% | **负结果与历史失效**：Bmin ELIMINATE（severe 3>0 + C1/C2 假设未过；首环原判 REWORK-per-registered-letter 系 C1 阈值符号勘误，双口径上报后重跑改判） | `W04-03_FIRST_LOOP_VERDICT.json` verdicts.B、`W04-02_ERRATA_SEVERE_SWAP_20261030.md` §3/§5、`FOURTH_ROUND_VERDICT_20261031.md` §四（维持） | 中 |

### 1.2 长剧探针环（E3 vs B0 主线，2026-10-30~31）

| 批次 | 比较双方 | 测试集与分母 | 评分器与判据版本 | 关键数字（按通道标注） | 结论类型 | 证据路径 | 强度 |
|---|---|---|---|---|---|---|---|
| 长剧探针（E061 甲状腺/E062 镜头） | E3 vs B0 | 2 篇 ×24 轮 ×双臂 = 96 attempts / 40 主判对 + 8 真双序 | 评分器 v0.9.2 系；判据=LONGPROBE_PREREGISTRATION.json（跑前冻结） | `程序`：10.0 vs 9.55；`九维`：E3 九维全胜（interact +0.90/init +0.95）；`亲读`：15 处疑似复活全为更正语境合法提及（双臂 0 真实复活）；severe 1/1 双杀位 E061_T13（无据确定化首个真实双杀，judge 判对、B3 确认）；出窗探针 E3 回忆更深 4 位；成本 prompt −15.3% | 开发集局部结果（并入 E3-vs-B0 跨批链）；E061_T13 登记为基座族锚点位 | `long_probe_20261030/LONGPROBE_VERDICT_20261030.md`、`LONGPROBE_NINEDIM_REVIEW.json`、`LONGPROBE_SCORE_MECHANICAL.json` | 中 |
| B1（E063/E064/E065） | E3 vs B0 | 3 篇 ×24 轮 ×双臂 = 144 attempts / 72 判位 | v0.9.2 系 + W04-02 severe 映射勘误修正后口径；判据=LONGPROBE_B1_PREREGISTRATION.json | `九维`：E3 全 9 维正（memory +0.857/interact +0.903/init +1.028）；`亲读`：修正后 severe E3 4 / B0 1（E3 四位全为编造过去事件族；B0 另有 E065_T15/T16 机械已证复活被 judge 漏旗标）；`程序`：parent 10.0 / cand 9.98；swap 11/12 | 开发集局部结果（E3 语义优势跨批复现；severe 族证据） | `SECOND_ROUND_VERDICT_20261030.md` §一、`LP_B1RERUN3_NINEDIM_REVIEW.json`（含 severe_reported_buggy） | 中 |
| B2（E066/E067/E068） | E3 vs B0 | 144 attempts / 72 判位 | 同上 | `九维`：7 维正 2 微负（fact −0.084/attrib −0.044 噪声级）；`亲读`：severe E3 5 / B0 2（E067_T10 引语+时间戳双编造实锤；B0 另有 E066_T09 安全错误 judge 漏旗标）；swap 12/12 | 开发集局部结果（同上） | `SECOND_ROUND_VERDICT_20261030.md` §二、`LP_B2RERUN_NINEDIM_REVIEW.json` | 中 |
| C1 挑战（E063/E064） | r5_tier vs B0（臂标签勘误后） | 2 篇 = 96 attempts / 48 判位 | 同上；判据=C1 预登记立功条件 | `九维`：r5_tier 全 9 维正（interact +1.105/memory +0.916）；`亲读`：severe 0/0（E3 曾在同两篇犯 2 位的同位零发生，跨批探索性对照）；`程序`：B0 9.79 / r5_tier 9.93；prompt 318.8K vs 353.0K；swap 7/8 | 开发集局部结果（r5_tier 对基线立功条件满足；与 E3 的头对头留 D1） | `SECOND_ROUND_VERDICT_20261030.md` §三、`RESEARCH_STATE.json` long_probe | 中 |
| D1 头对头（E061/E062 双杀原位） | r5_tier vs E3 | 96 attempts / 48 判位 + 8 双序 | 同上；判据=D1 预登记 protections | `九维`：全平（|diff|≤0.15）；`亲读`：severe r5_tier 2 / E3 0（judge 口径；B3 口径 1 实锤 + 1 同担 vs 0 + 1 同担）；双杀位 E061_T13 未消除只变异（来源包装式）；swap 5/8（近平局不稳定登记） | **负结果与历史失效**：r5_tier 挑战失败不继位（不淘汰，vs-B0 成绩保留）；冠军位一度空缺 | `THIRD_ROUND_VERDICT_20261031.md`、`LP_D1_NINEDIM_REVIEW.json`、`D1_EVIDENCE_READ_20261031.md` | 中 |
| E1 头对头（E065/E066） | r6_tier2 (tiering v2) vs E3 | 96 attempts / 48 判位 + 8 双序 | 同上；判据=E1 预登记 | `九维`：r6_tier2 微幅领先（goal +0.417/init +0.404）；`亲读`：severe 2/0（v2 修好"来源包装"没拦住"细节具名化"）；swap 8/8；`程序`：双臂打平；成本 prompt +6.2%；**全量跨批核算（5 批 624 位）：E3 编造族率 3.8% vs B0 3.1%（含漏检加项）无显著差 → 第一轮 2>0 小样本信号被否证** | **负结果与历史失效**：r6_tier2 挑战失败，tiering 线按"不无限重试到赢"关闭；**同时构成 E3 复权 KEEP 的核心依据（erratum 链，见 §4）** | `FOURTH_ROUND_VERDICT_20261031.md`（§一/二/三全表）、`LP_E1_NINEDIM_REVIEW.json` | 中（其中"E3 vs B0 九维优势跨 5 篇复现"整体评级=强） |

### 1.3 记忆取代线（r7_supersede，2026-10-31）

| 批次 | 比较双方 | 测试集与分母 | 评分器与判据版本 | 关键数字（按通道标注） | 结论类型 | 证据路径 | 强度 |
|---|---|---|---|---|---|---|---|
| F2（E065/E066） | r7_supersede (fixed-gate) vs E3 | 96 attempts / 48 判位 + 8 双序 | 评分器 v0.9.2 系；判据=W05-03 设计（归档待升规则） | `九维`：7 正 2 微负（全 ±0.17 噪声级）；`亲读`：severe r7 0 / E3 2（E3 两次凭空租金数字）；`程序`：r7 唯一 veto 属随机契约族（E065_T03 json_schema）；swap 5/8；离线重放双向验证（F1 真阳性保持、F2 假阳性消除） | 开发集局部结果（继任补丁归档待升） | `W05-03_VERDICT_20261031.md`、`W05-03_REPLAY_FIXED_GATE.json`、`LP_F2_NINEDIM_REVIEW.json` | 中 |
| F3（E062/E064） | r7_supersede vs E3 | 96 attempts / 48 判位 | 同上；升格规则=W05-03 判决预登记（复现且无回归→升格） | `亲读`：severe r7 0 / E3 1；**两批合并：r7 0/96 vs E3 3/96，2/2 批复现，作用面=更正链数字与机制一致**；`九维`：F3 多维微负但保护维全过（最差 interact −0.208 未破线）；veto 观察解除（prompt 同分布）；成本零额外调用 | **已证实（跨批复现）**：severe 边际 2/2 批 → **r7_supersede 升格冠军**（E3 转前任）；auto-revert 触发器预登记 | `F3_PROMOTION_RULING_20261031.md`、`LP_F3_NINEDIM_REVIEW.json`、`W05-03_F3_KPI.json` | 强 |

### 1.4 事实保持线（>24 轮域：LR2 → FL 系 → FLJUDGE/LD1，2026-11-01~02）

| 批次 | 比较双方 | 测试集与分母 | 评分器与判据版本 | 关键数字（按通道标注） | 结论类型 | 证据路径 | 强度 |
|---|---|---|---|---|---|---|---|
| LR2（LRE060 40轮/LRE048 32轮） | r9_slim vs v0（>24 轮域） | 144 attempts | 评分器 v0.9.2 系；判据=LONGPROBE_LR2_PREREGISTRATION.json（跑前冻结） | `机械保持`（同器同 GT）：T25-32 窗口保持 v0 47.6%/50.4% < 90% 门 → FAIL；`九维`：四项 PASS（fact +0.236/init +0.208）；`亲读`：miss 逐原子分类（真丢失 42–61%，artifact=0）；成本 prompt −50.3%；**结构性发现：塌陷=双臂共用 12 条滑窗结构问题（FK-LR2-01 入 KNOWN_FAILURES）** | **负结果与历史失效**（v0 淘汰出 >24 轮域）＋**已证实结构性发现**（滑窗塌陷跨臂同型） | `LR2_VERDICT_20261101.md` | 中（结构发现评级=强） |
| LR2R7 唤回（LRE060） | r9_slim vs r7_supersede（lineage-full） | 80 attempts | 同上 | `机械保持`：r7 T25-32 49.8% vs r9_slim 48.4%（+1.4pp，n=40 噪声带）→ **r7 同塌**：簿记层概括级记忆无论薄厚承载不了逐字事实召回 | 负结果（修复方向排除"恢复更厚旧簿记"；归属确认给机制改造） | `LR2_VERDICT_20261101.md` 附录（2026-11-01 回填） | 中 |
| FLREG（E061） | fact_ledger_v1 vs r9_slim | 48 attempts，零重试零补跑 | v0.9.2 系 + FK-SCORE-01 勘误（T02 误 veto 修复后离线重评 10.0，结论不变——淘汰依据为语义门）；判据=W12 设计 §4.1 跑前冻结 | `九维`：最差 fact/interact −0.333 < −0.3 门 FAIL；`亲读`：severe 2 vs 1（T13=已知基座族；T09=cand 独有"账本诱导错账"）；`机械保持`：窗口保持 98.8% PASS；成本 prompt −47.4% PASS | **负结果与历史失效**：回归门 FAIL，不进 >24 轮判决批（已确认收益留案：保持/成本/init） | `FLREG_VERDICT_20261101.md`（含 2026-11-02 勘误）、`SCORER_MSRC_REEVAL_20261102.json` | 中 |
| FLREG2（E061） | fact_ledger_v2 vs r9_slim | 48 attempts | 同上；判据同冻结口径 | `九维`：interact −0.500 触发 H_FL_null；severe 2（T13+新 T15）；`机械保持`：98.8%；成本 −47.5% | **负结果与历史失效**：v2 淘汰、事实账本注入线暂停；机制结论"注入行为本身升格信息认识论"（两次修法无效的解释） | `FLREG2_VERDICT_20261101.md`（含勘误） | 中 |
| FLJUDGE（LRE060/LRE048） | window_expand_b vs r9_slim（>24 轮域） | 144 attempts / 72 判位 + swap8 + 真双序补判12 | 评分器 v0.9.2 系 + FK-SCORE-01 勘误（LRE060_T03 修复后重评 10.0，KEEP 不变）；判据=W12 设计 §4.2 跑前冻结；plan_sha256=7d1226e9… 现场重算一致 | `机械保持`：T25-32 真实保持 **1.0 vs parent 0.493/0.509**（量级差）；终态 0.987；miss 亲读分类 equivalent18/true_loss2/stale2；`九维`：保护维全过（goal −0.194/boundary +0.044/init +0.167）；severe 亲读后 0/0（judge 3/3 误报驳回）；成本 prompt **−41.9%**；swap 5/8（如实登记，判据主证据=机械量级差） | **已证实（跨批复现）**：window_expand_b KEEP，**FK-LR2-01 修复归属判给 window_expand_b**；与 LR2 塌陷读数跨批衔接 | `FLJUDGE_VERDICT_20261102.md`、`LP_FLJUDGE_B3_READRESULT.json`、`FLJUDGE_MISS_CLASSIFICATION.json`（logs 下） | 强 |
| CF1（8 对反事实孪生 E149–E156） | window_expand_b vs r9_slim | 192 attempts / 96 判位 + swap8 | 评分器 v0.9.2 系（FK-SCORE-01 修复同期）；判据=W13-02 设计 §3 跑前冻结；plan_sha256=a95da5ca… | `机械+亲读`：CF 跟随率 parent 0.681 / cand 0.698 均落 0.60–0.75 不确定带；沿用率 0–1.7% ≪40% 红线（H_CF_null 不成立）；`九维`：interact −0.385/emotion −0.406（短剧面差距观察）；`亲读`：severe cand 0 / parent 1（E151_T09 confirmed）；成本 −51.3%；swap 7/8 | **负结果与历史失效（不确定类）**：W13-02 判据结果=保留不确定（不判 PASS、不淘汰、不补跑）；CF 孪生明文"非独立泛化证据" | `CF1_VERDICT_20261102.md`、`LP_CF1_B3_READRESULT.json` | 中 |
| LD1 域迁移（LRE117/141/125/147 四新域长剧） | window_expand_b vs r9_slim | 304 attempts / 128 判位 + swap16 | 同上；判据=W13_LD1_MIGRATION_DESIGN 跑前冻结；plan_sha256=123bd9f1… | `机械保持`：T25-32 真实保持 **1.0（四篇全）**，parent 塌陷 0.32–0.62 在四个新域全部复现；终态 425/430=0.988；`九维`：保护维全过（最差 −0.177）；`亲读`：severe 初判 6/2 经亲读修正 **1/1**（评审器 CALL_FAIL 32 处致初判不可靠）；成本 −44.0% | **已证实（跨域复现）**：FK-LR2-01 修复域无关迁移成立 → 双冠军分工终审确认（>24 轮 window_expand_b / ≤24 轮 r9_slim） | `LD1_VERDICT_20261102.md`、`LP_LD1_B3_READRESULT.json` | 强 |

### 1.5 冠军精化与挑战环（2026-11-02~04）

| 批次 | 比较双方 | 测试集与分母 | 评分器与判据版本 | 关键数字（按通道标注） | 结论类型 | 证据路径 | 强度 |
|---|---|---|---|---|---|---|---|
| W15N-01 消融 | window_expand_b_norecap vs window_expand_b | 2 篇长剧 ×2 臂 = 160 attempts / 80 判位 | 评分器 v0.9.2 系；判据=W15_NORECAP_ABLATION_DESIGN 跑前冻结 | `机械保持`：T25-32 双臂 1.000/1.000（判决段 recap 为空=输入逐字节相同，LD1 离线证据共 6 篇）；`九维`：9/9 全正（小幅 +0.04~+0.25）；`亲读`：severe 1 ≤ 3（12 位全读，judge 原始 1 vs 12 修正）；成本 −0.3% | 开发集局部结果（KEEP→冠军简化为 b_norecap；主张="无损可删"非"recap 有害"） | `W15N01_ABLATION_VERDICT_20261102.md`、`LP_W15N_B3_READRESULT.json`、`WINDOW_KEEP_W15N.json` | 中 |
| W16-10 未定标记 | window_expand_b_norecap_uc vs b_norecap | 3 篇长剧（2 回归+1 泛化）= 240 attempts / 120 判位 | 评分器 v0.9.2 系；判据=W16_UNCERTAIN_DESIGN §4 冻结（含 R1–R5 实现期修订，已声明） | `亲读`：G1 severe cand 1 vs par 6（judge 原始 1 vs 10；回归两篇候选 severe=0）；`九维`：9/9 全正（memory +0.301）；`程序`：契约违规 10→4、机械分 8.65→9.62；成本 +0.78% ≤5%；触发面 29.2% | **已证实（开发集多批）**：KEEP → **>24 轮域冠军升级为 window_expand_b_norecap_uc**（其后 W18B/W19CROSS/W21/W22 四批作为父本参战无翻案）；明文禁止宣称"无据确定化已解决"（T34 两臂都坐实） | `W16_UNC_VERDICT_20261102.md` | 强 |
| W14M-02 判决 | window_expand_b2（压制段） vs b | 4 篇长剧 = 288 attempts / 144 判位 | 评分器 v0.9.2 系；判据=W14_SUPPRESSION_DESIGN §3.2 冻结 | `亲读`：同类 severe b2=1 ≠ 0（LRE146_T20 坐实"电池有毛病"+无据归属）→ 主门 FAIL；`机械保持`：双臂 1.000；`九维`：最差 −0.007 无退化；`程序`：LRE146 压制段全程为空=检测覆盖缺口非压制失效 | **负结果与历史失效**：H_SUP_null 触发 → b2 淘汰（不调参重跑）；正向信号（LRE121 压制段活跃篇 b2 零 severe）留下一假设 | `W14M02_JUDGMENT_VERDICT_20261102.md` | 中 |
| W13-03 新域 | 双冠军 × 新域兴趣社交 E131 等 | 144 attempts / 72 判位 + 6 双序 | 评分器 v0.9.2 系；判据=W13-03_NEWDOMAIN_DESIGN 跑前冻结 | `九维`：双冠军新域最差维 vs 已见最差维 −0.26/+0.06 ≥ −0.5 → H_GEN 成立；附带头对头（b vs r9_slim，探索性）：9/9 全正、`亲读` severe b 0 / r9_slim 1；成本 −53%；`程序`：b 新域 8.01 失分族=json_schema 采样噪声 | 开发集局部结果（新域=第 12 域首次消费，非人类独立泛化；b 的 9/9 读数留作冠军再评估证据，未即时改判） | `W13-03_VERDICT_20261102.md` | 中 |
| W17-01 合并评估 | b_norecap vs r9_slim（≤24 轮已见域） | 6 篇 ×12 轮 = 144 attempts / 72 判位 | 评分器 v0.9.2 系；判据=W17_SINGLE_CHAMPION_DESIGN 冻结 | `亲读`：G3 severe 2 vs 1 FAIL；`程序`：**契约违规 16/72 (22.2%) vs 4/72 (5.6%)**（判据外实质发现，登记 D-W17-01）；`九维`：G1/G2 过；成本 −52.6% | **负结果与历史失效**：不合并（维持双冠军）；缺口定位="短剧域缺输出纪律非记忆"；下一批 W17b 设计由此导出 | `W17_MERGE_VERDICT_20261102.md` | 中 |
| W17b 守卫单变量 | b_norecap_guard vs b_norecap | 同六篇（已暴露，机制回归批）= 144 attempts / 72 判位 | 评分器 v0.9.2 系（后经 W19-01 离线重评：cand 8.958→9.236，清 2 误 veto）；判据=W17B_GUARD_DESIGN 冻结 | `程序`：契约违规 2/72 vs 7/72、空回复 0 vs 0、prompt +0%；`九维`：8/9 正（最差 init −0.058）；`亲读`：severe cand 0 vs par 2（judge 原始 0 vs 7 修正） | 开发集局部结果（KEEP；泛化证据归 W17c） | `W17B_GUARD_VERDICT_20261103.md` | 中 |
| W17c 合并复评 | b_norecap_guard vs r9_slim | 6 篇**未暴露** 12 轮篇 = 144 attempts / 72 判位（main n=61） | 评分器 v0.9.2 系 + **W19-01 隔离路径修复后离线重评**（候选机械 9.306→9.583；Rev2/Rev3 勘误链）；判据=W17C_PREREGISTRATION_20261103.md 跑前冻结 | `九维`（main-only）：9/9 全正（最差 emotion +0.033）；`亲读`：severe cand 0 vs parent 3 确认（judge 旗 4、1 降级，零抽样全读）；`程序`：契约 2/72 vs 2/72、空回复 0；成本 −52.2%；swap 3/6（候选优势顺序敏感，如实注记） | **已证实（两批链 W17b+W17c，含未暴露篇）**：MERGE → **≤24 轮域冠军=window_expand_b_norecap_guard**（r9_slim 转历史；"真正单构建 guard+uc"明文不宣称） | `W17C_MERGE_VERDICT_20261103.md`（含 Rev2/Rev3）、`LP_W17C_B3_READRESULT.json`、`LP_W17C_G1_MAINONLY.json` | 中（两批合并判"强"，附勘误与 swap 敏感性注记） |
| W18（中止）/W18B 重跑 | uc_supp vs uc | W18 90/320 中止（执行事故）；W18B 320/320 独占 6 路 / 160 判位（主分析集 120） | 评分器 v0.9.2 系 + W19-01 离线重评（本批 0 变化）；判据=W18 设计 G1–G4 冻结 | `亲读`：主分析集 severe cand 0 vs par 2（judge 初判 cand 2 位亲读全降级）；**S/U 两族 parent 0 例=未实例化 → 判决只能写"无退化"**；`九维`（main-only）：全正（judge 未校准只辅助）；成本 +1.02%；评审 callfail 16.3% 已降权；swap 14/16 | 负结果/无晋级证据：KEEP-no-regression（uc_supp="已验证无退化的可选组合"）；**晋级主张缺实例证据，不替换冠军**（判定张力如实登记） | `W18B_VERDICT_20261103.md`、`W18_EXECUTION_INCIDENT_20261103.md`、`LP_W18B_B3_READRESULT.json` | 中 |
| W19-03 跨域（w19b 重跑批） | uc_guard vs 双冠军合判 | 6 短剧 + 4 长剧 = 464 attempts / 232 判位 + swap22 | 评分器已修复（W19-01 闭）；判据=W19-03 设计 G1–G5 跑前冻结 | `亲读`（26 位对账+引文逐字回验）：short12 severe 2≤2、long40 11≤13；judge 缺陷四类全录（误烧4/漏检7/无注8/双标3）；`九维`：两域全 9 维正（辅助门）；`程序`：long40 **空回复 5≠0 破 G3 零容忍门**；G5 故事级翻转 4/10 | **负结果与历史失效**：不宣布单冠军，双域冠军保留（uc_guard 记跨域候选未晋升）；同时是 judge 不可靠性的最全证据批 | `W19CROSS_VERDICT_20261103.md`、`LP_W19CROSS_B3_READRESULT.json`、`LP_W19CROSS_QUOTE_CHECK.json` | 中（harness 结论）/ 强（评估基建证据） |
| W21 快车道 G0 | uc_retry vs uc（门禁批，只淘汰不晋级） | E157+LRE117 = 104 attempts | 评分器 v0.9.3（2026-11-03 接 gold sidecar）；判据=FAST_LANE §5 四门（重试后口径修订在案） | `亲读`（52/52 全覆盖）：severe cand 1 ≤ par 5（judge 初判 2/5 漏 3）；`程序`：prompt 185,527=185,527（+0.00%）、崩坏 0/104（不外推）；`九维`：main-only 全正；quote 硬校验首战（strict 84%、severe_unverified=1 强制 B3） | 开发集局部结果（FASTLANE_PASS=全量批资格门，不构成 KEEP 证据） | `W21_FASTLANE_G0_VERDICT_20261104.md`、`LP_W21FASTLANE_QUOTE_CHECK.json` | 中 |
| W21FULL 全量 | uc_retry vs uc | 10 篇 232 位 = 464 attempts + 重试另册 24/92 如实记账 | 评分器 v0.9.3 + 引文硬校验（strict 76.2%、no_hit=0、severe_unverified=9 强制 B3）；判据=W21-02 G1–G5+G3' 冻结；plan sha d41f23990a99a7ac | `程序`：**重试后有效回复率双臂 232/232=100%（重试 12/12 修复、prompt 821,643=821,643）**；`亲读`：G1 severe cand 4 ≤ par 15（B3 补烧 4）；`九维`：双域全正；G5 翻转 **3/10 → 不得宣布单冠军** | **负结果与历史失效（保留不晋升）**：双冠军保留，uc_retry 归档为候选；**组件级已证实：崩坏轮确定性重试=12/12 修复+字节等价+双臂对称（转评测基建工具）** | `W21FULL_VERDICT_20261104.md`（载明 B3 全量对账附卷待补）、`LP_W21FULL_QUOTE_CHECK.json`、`W21_FULL_RETRY_RESOLUTION.json` | 中（候选结论）/ 中-强（组件结论） |
| W22 快车道 G0 | uc_attr vs uc（门禁批） | E157+LRE117 = 104 attempts | 评分器 v0.9.3 + quote 硬校验（strict 84.6%）；判据=W22_01_ATTR_DESIGN + FAST_LANE §5 | `亲读`（52 位）：severe cand 1 ≤ par 1（贴线过）；`程序`：prompt +1.42%、触发面 34.6%、双臂 0 空回复；归因敏感位不在素材内（主假设留全量批） | 开发集局部结果（FASTLANE_PASS，不晋级） | `W22_FASTLANE_G0_VERDICT_20261104.md` | 中 |
| W22FULL 全量 | uc_attr（归属锚定） vs uc | 10 篇 232 位 = 464 attempts / 232 判位 + swap22 | 评分器 v0.9.3 + quote 硬校验（strict 79.1%、no_hit=0、severe_unverified=0）；判据=W22_01_ATTR_DESIGN G1 主假设冻结；plan sha bd8e3d27… | `亲读`：**主假设正面：归因错位族 severe cand 0 < par 1**（LRE121_T39 方向修复直接命中；W21FULL 同位倒置）；总数 cand 4 ≤ par 11；`九维`：9/9 全正（memory +0.202 最大正差）；`程序`：双臂 0 空回复、prompt +1.77%、触发面 42.7%；G5 翻转 **7/22（5/10 故事）→ 阻断晋升** | **负结果与历史失效（保留不晋升）+ 组件级开发集局部结果**：DUAL_CHAMPIONS_RETAINED；uc_attr 保留为冠军链组件（单变量有效证据：族清零+九维全正+零额外调用） | `W22FULL_VERDICT_20261104.md`、`W22_01_ATTR_DESIGN_20261104.md`、`LP_W22FULL_QUOTE_CHECK.json` | 中 |
| W14M01/W13-04 等 2026-11-02 副线 | — | — | — | W14M01 回归验证与 W13-04 阶段分布验证收口（updates[52][54]） | 开发集局部结果（登记性） | `W14M01_REGRESSION_VERDICT_20261102.md`、`RESEARCH_STATE.json` updates | 弱（本审查未逐项复核，仅登记存在） |

### 1.6 测量与评审基建（非比较批，无生死判决）

| 批次/任务 | 内容 | 关键数字 | 结论类型 | 证据路径 | 强度 |
|---|---|---|---|---|---|
| W05-02 记忆 KPI/保真 | 零调用测量 + F1 instrumented 96/96 | Update 及时效 22/28、guard 计数全零、B0 写回 ~100%；内容捕获 30/31=96.8%（1 真实 miss F10）；supersession 双模式发现 | 测量（开发集局部结果）；内容级保真缺口如实登记 | `global_harness_program_20261030/W05-02_MEMORY_KPI_REPORT_20261031.md`、`W05-02_FIDELITY_CLOSURE_20261031.md`、updates[15][16] | 中 |
| W20-03 judge 校准 | 102 judge 判定 + 36 负对照（授权 cap160/cap40，对账） | 九维资格表（full: goal/time/interact/emotion；低证据辅助: fact/init；not-testable: attrib；disqualified: boundary/memory）；severe P=0.062/R=0.5；引文硬幻觉 11.9%；winner 复现 50% | 评估基建（负结果性质：judge 多维不合格） | `long_probe_20261030/W20_03_JUDGE_CAL_REPORT_20261103.md` | 中 |
| W08 正向互动包 | 48 轮三轴标注/五类钩子/3×3 深聊/能力表 | 深聊 0-语域轨迹损伤 2+ 轮、1-语域可恢复、冠军未落 0-语域 | 测量与锚点建设（无晋级级量化门） | `W08-01..W08-05_*.md/json`（global_harness_program_20261030）、updates[23]–[28] | 弱-中 |

---

## 2. 按四类归组

### 2.1 已证实（含跨域/跨批复现）

1. **E3 对 B0 的九维对话能力优势**：FS1-A（48 对）→ 长剧探针（40 对，九维全胜）→ B1（全 9 维正）→ B2（7/9 正），跨 5 篇长剧 120+ 位复现；但 E3 携带与基线无显著差的编造族（3.8% vs 3.1%，624 位核算），E3 本人不任冠军。证据：`W04-04_FS1_FULL_SCORECARD.md`、`LONGPROBE_VERDICT_20261030.md`、`SECOND_ROUND_VERDICT_20261030.md`、`FOURTH_ROUND_VERDICT_20261031.md` §三。评级：强（judge 未校准是读数上限）。
2. **r7_supersede 的 severe 边际**：F2 0/2 → F3 0/1，2/2 批复现、作用面与机制一致、保护维全过 → 升格冠军（后被 r9_slim 世代更替，非被推翻）。证据：`W05-03_VERDICT_20261031.md`、`F3_PROMOTION_RULING_20261031.md`。评级：强。
3. **FK-LR2-01（长剧逐字事实塌陷）及其修复归属**：LR2 双臂同塌（结构性发现）→ LR2R7 排除"更厚簿记"→ FLJUDGE 修复（T25-32 保持 1.0 vs 0.49，−41.9% 成本）→ LD1 四个新域复现（parent 0.32–0.62 vs cand 1.0）。证据：`LR2_VERDICT_20261101.md`、`FLJUDGE_VERDICT_20261102.md`、`LD1_VERDICT_20261102.md`。评级：强。
4. **>24 轮域冠军 window_expand_b_norecap_uc（W16 判决批）**：预注册门全过 + 亲读 severe 1 vs 6 + 回归两篇候选 zero severe + 泛化篇不塌 + 触发面/成本门；此后四批作为父本参战未翻案。证据：`W16_UNC_VERDICT_20261102.md`；`W18B_VERDICT_20261103.md`/`W19CROSS_VERDICT_20261103.md`/`W21FULL_VERDICT_20261104.md`/`W22FULL_VERDICT_20261104.md`（均以 uc 为 parent，G1 severe/九维无翻案）。评级：强（判定域=开发集）。
5. **≤24 轮域冠军 window_expand_b_norecap_guard（W17b+W17c 两批链）**：机制回归批（已见 6 篇）+ 未暴露 6 篇复评，契约违规 16/72→2/72 的单变量修复链、severe 亲读 0 vs 3、未暴露篇九维 main-only 全正。证据：`W17_MERGE_VERDICT_20261102.md`、`W17B_GUARD_VERDICT_20261103.md`、`W17C_MERGE_VERDICT_20261103.md`（Rev2/Rev3）。评级：两批合并=强；单批各=中。
6. **崩坏轮确定性重试组件**：12/12 修复率、prompt 逐字节等价、双臂对称、另册 24/92 如实记账；快车道与全量两批验证。证据：`W21_FASTLANE_G0_VERDICT_20261104.md`、`W21FULL_VERDICT_20261104.md`、`W21_FULL_RETRY_RESOLUTION.json`。评级：中-强（组件级、机制确定性支撑；"崩坏族消失"不在此列）。
7. **归因锚定组件（uc_attr）单变量有效性**：主假设归因错位族 cand 0 < par 1（LRE121_T39 方向修复直接命中，W21FULL 同位倒置对照）；九维全正、零额外调用。证据：`W22FULL_VERDICT_20261104.md`。评级：中（单批、单位直接命中、swap 阻断晋升）。

### 2.2 开发集局部结果（单批成立、未复现或复现面受限）

FS1-A 的批内读数（含 E3 completion +34% 成本交易）；长剧探针出窗回忆优势；C1 的 r5_tier-vs-B0 全 9 维（头对头失败后归档保留）；F2 首战（归档待升）；W15N-01 消融简化（"无损可删"主张）；W13-03 新域无塌陷（H_GEN，第 12 域首次消费）；W17b 机制回归（泛化归 W17c）；W18B "无退化"（S/U 未实例化，明文不构成改善证据）；W21/W22 快车道门禁通过（只淘汰不晋级）；W05-02 测量（Update 及时效 22/28、保真捕获 96.8%）；W08 正向互动包。全部路径见 §1 表。

### 2.3 无证据推测（未做——现存说法不得当结论使用）

1. **未见验证/独立泛化**：全库 author_type=AI、dev_exposed=true、independent_review=false，无一篇具备未见验证资格；B 线（唯一 blind）从未运行。证据：`W03_CENSUS_20261030.md`、`benchmark/b_line/B_LINE_MANIFEST.json`（status 未 CONSUMED）。→ 任何"对真实用户泛化"的表述无证据。
2. **用户愿发偏好**：W 愿发=UNKNOWN（12 对人工样本待用户、AI 不代投）。证据：`W04-04_FS1_FULL_SCORECARD.md`、`W04-03_FIRST_LOOP_VERDICT.json` w_note。
3. **guard+uc 单构建冠军**：W17c 明文"本判决不宣称"。证据：`W17C_MERGE_VERDICT_20261103.md`。
4. **uc_supp 压制段有效**：S/U 两族 parent 0 例未实例化，晋级主张缺实例证据（W18B）。证据：`W18B_VERDICT_20261103.md` §三。
5. **"无据确定化已解决"**：W16 明文禁止（T34 两臂同坐实）；W22 的 cand 4 severe 仍全属该族。证据：`W16_UNC_VERDICT_20261102.md` 注记、`W22FULL_VERDICT_20261104.md` G1。
6. **"输出契约崩坏族消失"**：快车道 0/104 明文"不外推"；跨批波状 17.4%→0→2.6%→1.7%/3.0%。证据：`W21_FASTLANE_G0_VERDICT_20261104.md` 注记 1、`W22FULL_VERDICT_20261104.md` 注记 4。
7. **产品接入收益 / S3 全量 / 192 扩量性能**：W15-06 仅交付设计与 S1-S3 阶段化路径；192 扩量任务未开始（用户 2026-10-25 方向校正）。证据：`W15-06_INTEGRATION_DESIGN_20261031.md`、`W15-05_SCOPED_RULING_20261031.md`、AGENTS §0。
8. **统计显著性**：所有批为嵌套位，无一批声明显著；"N=请求位"不得当独立样本。证据：AGENTS §5、`W16_UNC_VERDICT_20261102.md` 诚实注记 3。

### 2.4 负结果与历史失效（单列，不得从_summary中抹去）

1. **Bmin**：FS1-B ELIMINATE（首环原判 REWORK 系 C1 阈值符号勘误→重跑改判→第四轮维持；severe 6.2% + C1/C2 假设未过）。证据：`W04-03_FIRST_LOOP_VERDICT.json`、`FOURTH_ROUND_VERDICT_20261031.md`。
2. **E3 曾被误 ELIMINATE 后复权（erratum 链）**：首环 KEEP（原判）→ W04-02 severe 映射勘误重跑 ELIMINATE → D1 维持 ELIMINATE → 第四轮全量核算（624 位，3.8% vs 3.1% 无显著差）复权 KEEP；随后 F3 升格 r7 转 E3 为前任。证据链：`W04-02_ERRATA_SEVERE_SWAP_20261030.md` → `W04-03_FIRST_LOOP_VERDICT.json`（重跑版）→ `THIRD_ROUND_VERDICT_20261031.md` → `FOURTH_ROUND_VERDICT_20261031.md`。
3. **tiering 线关闭**：r5_tier D1 挑战失败（severe 反增 2/0、双杀位变异）；r6_tier2 E1 挑战失败（细节具名化未拦住，两代族率同为 4.2%）。证据：`THIRD_ROUND_VERDICT_20261031.md`、`FOURTH_ROUND_VERDICT_20261031.md`。
4. **b2 淘汰**：W14M02 H_SUP_null（检测覆盖缺口，非压制失效）。证据：`W14M02_JUDGMENT_VERDICT_20261102.md`。
5. **事实账本线暂停**：FLREG FAIL → FLREG2 H_FL_null 淘汰（"账本=事实"认识论问题，两次修法无效）。证据：`FLREG_VERDICT_20261101.md`、`FLREG2_VERDICT_20261101.md`。
6. **v0 淘汰出 >24 轮域**（LR2；保持塌陷距门槛极远）；r7 唤回同塌（LR2R7）。证据：`LR2_VERDICT_20261101.md`。
7. **W17 合并被拒**（G3 severe FAIL + 判据外契约违规 22.2%）；**W17c 单冠军被 W19CROSS 阻断**（long40 空回复 5≠0 破 G3 + G5 翻转 4/10）。证据：`W17_MERGE_VERDICT_20261102.md`、`W19CROSS_VERDICT_20261103.md`。
8. **W21FULL/W22FULL 双双 KEEP_NO_PROMOTE**：G5 翻转 3/10 与 7/22 阻断单冠军；uc_retry/uc_attr 归档为候选/组件。证据：`W21FULL_VERDICT_20261104.md`、`W22FULL_VERDICT_20261104.md`。
9. **CF1 UNCERTAIN**：跟随率双臂落不确定带，不判 PASS 不淘汰不补跑。证据：`CF1_VERDICT_20261102.md`。
10. **评估器历史失效**：W04-02 severe 臂映射反转（4 份报告修正）；FK-SCORE-01 m_src 误 veto（3 例重评 + W19-01 隔离路径 608 位重评）；judge severe P=0.062 与四批误烧/漏检/空注/双标账；W17c Rev2/Rev3 勘误（机械分口径）；B 线 v1 选篇污染重建为 v2。证据：§1 各行路径 + `D_EVALUATION_RELIABILITY.md` §1.2/§2/§4.2。
11. **执行事故**：W18 9 路并发 120s 超时中止（90/320，不进主判）；W21 重试首轮流式旗 bug 12 次失败（另册如实入账）。证据：`W18_EXECUTION_INCIDENT_20261103.md`、`W21FULL_VERDICT_20261104.md`、`RESEARCH_STATE.json` updates[70]。

---

## 3. 现役冠军的证据边界（跨批不可比声明）

- **现役双冠军**（`RESEARCH_STATE.json` main_incumbent，2026-11-03 W17c 后；2026-11-04 W21/W22 判决后维持）：
  - **>24 轮域 window_expand_b_norecap_uc**：得分来自 **W16 判决批**（240 attempts/120 判位：亲读 severe 1 vs 6、九维全正、成本 +0.78%、触发面 29.2%）。
  - **≤24 轮域 window_expand_b_norecap_guard**：得分来自 **W17b（机制回归）+ W17c（未暴露篇 MERGE）两判决批**（契约 2/72 vs 7/72 与 2/72 vs 2/72；severe 亲读 0 vs 2 与 0 vs 3）。
- **不可拼接示例**：W16 的"九维全正（120 位）"与 W17c 的"main-only 九维全正（n=61）"来自不同故事、不同素材暴露状态、不同评审轮次与不同评分器状态（W17c 数字经 W19-01 修正），两者相加或相除无意义；W18B 的 0 vs 2 与 W19CROSS 的 2≤2 亦然。
- **唯一允许的跨批读法**：同一冻结机制作为父本在后续批次被挑战而不翻案（uc 在 W18B/W19CROSS/W21FULL/W22FULL 四批作为 parent 臂的 severe 账与九维辅助门均未出现翻案级证据）——这是"存活"证据，不是"分数增长"证据。
- **冠军链沿革（判决事实，非升级曲线）**：B0 → E3（第四轮复权）→ r7_supersede（F3 升格）→ r9_slim（G1 升格，共役）→ 双冠军（FLJUDGE/LD1 终审：>24 轮 window_expand_b，≤24 轮 r9_slim）→ 简化与机制升级（W15N01 norecap、W16 uc、W17b/c guard）→ W19CROSS/W21/W22 三次单冠军尝试均被冻结门阻断。证据：`G1_VERDICT_20261031.md`（r9_slim 升格，64/64 零失败、机械 32/32 全平、severe 0/0、prompt −3.8%/p50 −28%）、`LR1_VERDICT_W1005_20261031.md`（v0 ≤24 域 KEEP）、各判决书。

---

## 4. 判决链与 erratum 制度（结论改判必须走勘误，不走静默改写）

| 日期 | 事件 | 性质 |
|---|---|---|
| 2026-10-30 | FS1 首环：E3 KEEP / Bmin REWORK（原始版） | 原判 |
| 2026-10-30 | W04-02 severe 臂映射勘误：4 份报告原位修正（buggy 值保留）、首环重跑 E3/Bmin 双 ELIMINATE | 程序级 erratum |
| 2026-10-31 | D1：r5_tier 挑战失败，冠军空缺 | 负结果 |
| 2026-10-31 | 第四轮：跨批核算否证小样本 ELIMINATE → E3 复权 KEEP（字面口径并列保留） | 复权（附登记缺陷） |
| 2026-10-31 | F3：r7_supersede 升格冠军 | 晋级 |
| 2026-11-01~02 | LR2→FLREG/FLREG2 FAIL→FLJUDGE KEEP：FK-LR2-01 修复归属 window_expand_b | 负结果+晋级 |
| 2026-11-02 | FK-SCORE-01 勘误：FLREG/FLREG2/FLJUDGE 三份判决附记（结论不变）；SCORER_MSRC_REEVAL 3 例重评 | 评分器 erratum |
| 2026-11-02 | W14M02 b2 淘汰；CF1 UNCERTAIN；W15N01/W16 冠军精化 | 负结果+晋级 |
| 2026-11-03 | W19-01 隔离路径修复：608 位离线重评（W17c 9.306→9.583、W17b 8.958→9.236、W18B 0 变化）；W17c Rev2/Rev3；红队复核五项 PASS | 评分器 erratum |
| 2026-11-03 | W19CROSS：不宣布单冠军；judge 缺陷四类账；引文硬校验接线 | 负结果+基建 |
| 2026-11-04 | W21/W22：双冠军保留；重试组件与归因锚定组件登记 | 保留+组件证据 |

原则：每次改判均以新判决文书/勘误附记落盘、原值保留（severe_reported_buggy 字段、Rev 修订段、勘误段），从未出现静默改数——这是"负结果与历史失效"得以单列成表的前提。

---

## 5. 总评

- **证据最硬的三条线**：①FK-LR2-01 塌陷与修复归属（机械保持器同器同 GT + 跨批跨域复现）；②r7 severe 边际（2/2 批 + 机制因果 + auto-revert 条款）；③双冠军的各自判决批（W16 与 W17b+c，预注册门+亲读零抽样或全覆盖）。
- **所有语义结论的共同上限**：judge 未校准（九维只辅助）＋gold=AI＋无未见素材＋用户愿发未采集——因此本清单中没有任何结论可升级为"对真实用户的已证实优势"；B 线消费之前，全场最外层结论是"开发集上、AI 互评口径下的保留/淘汰记录"。
- **负结果密度是健康信号**：11 条负结果/历史失效（§2.4）覆盖候选（Bmin/b2/账本线/tiering 线/uc_retry/uc_attr）、机制（滑窗、压制、事实注入）与评估器（映射、m_src、severe 旗）三类，全部留痕可审计；配合 §3 的跨批不可比纪律，当前证据结构不支持任何"一路变好"的叙事，只支持"每批有判据、判据可翻案、翻案留痕"的过程可靠性。
