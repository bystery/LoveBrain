# F3 新材料报告（2026-10-29）

## 1. 材料构建与冻结

四篇新故事由 `tools/build_f3_from_specs.py` 从 `benchmark/stories_v2/final_20261029/{N01,N02,K01,K02}/spec.json` 编译为 episode/reference/timeline/audit/exposure 五件套，并通过 FINAL_N/K_VALIDATION（0 错误）与 72 张评分卡（GRADING_CARDS_N01_N02_K01_K02.json，0 错误）校验。N01/N02 各 12 轮，K01/K02 各 24 轮；合计 72 个可评位置、144 次生成调用。材料 sha256 见 `logs/f3_newmat_live_restart_20261029/RESULT.json` 的 `materials_sha256`。

诚实记录：exposure 元数据明确标注作者（我）在编写评分卡时看过候选答案，材料不是双盲写作，评分卡只保证判分标准先于本次盲评冻结。

## 2. 生成执行与授权账本

正式批 `AUTH-F3-RESTART-20261029-01`（dsf / 0.7 / 17770/v1，候选 `action_policy_r4_guard_final`，对照 `reference_product_v1`）共 144 次授权：账本 144 `attempt_started` / 144 `attempt_completed`、1 次 `batch_rejected`、已 `authorization_closed`。被拒的那次是编排器对 K02 候选续跑按整批 24 预检、而额度仅剩 12；我按规则未重跑已用回合，改用 `--expect-attempts 12` 手工续跑 T13–T24，最终 `candidate_K02_resume/checkpoint.json` 为 24 轮。8 条链的终态 checkpoint 全部到位（N 链 12、K 链 24），`logs/f3_newmat_live_restart_20261029/DONE.json` 中 `failed_resume:1` 是编排器预检失败的历史记录，不代表数据缺失。

K01/K02 的 T12 checkpoint 真实复制后再续跑，续跑轮次在同一 run 树内产生 turn_13…turn_24 完整证据（`_iso_run_*_resume/` 与各 turn 目录）。首轮（被内存事故中断的批次）证据保存在中断记录 `HOST_MEMORY_STOP_20261029.md`，未计入正式批。

回炉统计：144 个回合中，候选 143 条正常 reply、1 条协议失败（见第 5 节）；对照 144 条全部 reply。

## 3. 盲评设计与执行

`tools/build_f3_blind_materials.py` 构造 72 对盲材：完整前史（不含答案泄漏）、随机 A/B（36 对对照在前、36 对候选在前）、`F3_MATERIALS.jsonl` sha256 `686a2843...`，绑定表 `F3_PRIVATE_BINDINGS.json`。评分沿用 F1 模板加长篇附注，模型 hy3 / temperature 0 / 非流式，额度 `AUTH-F3-JUDGE-20261029-01`（上限 72）账本 72 started / 72 completed，已关闭；结果 72/72 schema 通过、72/72 传输通过（`logs/f3_judge_20261029_115709/F3_JUDGE_SUMMARY.json`），聚合在 `F3_ANALYSIS.json`。

## 4. 结果

总体胜负（72 对）：候选 38、对照 10、平 24、uncertain 0。

| 切片 | n | 候选 | 对照 | 平 |
|---|---|---|---|---|
| N 正面信号 | 12 | 5 | 2 | 5 |
| K 知识链 | 24 | 15 | 3 | 6 |
| 其余 N/K 位置 | 36 | 18 | 5 | 13 |

分故事：N01 6/2/4、N02 6/2/4、K01 11/4/9、K02 15/2/7（候选/对照/平）。

九维均分（全体 72 对）：

| 维度 | 对照 | 候选 |
|---|---|---|
| goal | 1.72 | 2.25 |
| factuality | 2.07 | 2.16 |
| attribution | 2.10 | 2.21 |
| time_state | 2.04 | 2.16 |
| boundary | 2.14 | 2.23 |
| memory_use | 1.92 | 2.38 |
| interaction | 2.03 | 2.22 |
| initiative | 1.73 | 2.40 |
| emotion_relation | 1.92 | 2.31 |

动作贴合度：候选 appropriate 57 / acceptable 9 / bad 6；对照 appropriate 30 / acceptable 37 / bad 5。major_errors：候选 8 条（N01_T10 1、N02_T03 2、K01_T19 2、K01_T20 1、K02_T16 2）、对照 5 条（K01_T06、K02_T14、K02_T16、K02_T18、K02_T21 各 1）。card_conflicts 0。

候选的优势集中在主动性、目标推进、记忆使用与情绪承接；对照的主要败因是漏召回近期更正（11 月 8 日、母亲两万到账）与替她做决定。候选的 8 条 major errors 多为把暂定/未知说成确定（10 点半“来得及”、13–16 三晚、八万三等），K02_T16 双方同题均错预算口径。

## 5. 协议缺陷：N01_T05

候选在 N01_T05 的原回复 `"]\n,{"analysis"` 多一层花括号，`output_repair` E2 修复失败，确定性 guard 保持原样，最终动作层记为 `format_undeliverable`（veto `json_schema_broken`，1/72），对照为正常 reply。这是可交付性缺陷而非语义缺陷，盲评里该对判给对照。列为 F5 唯一确认修复目标。

## 6. 知识链路审计（重要保留意见）

`tools/f3_knowledge_chain.py` 用“来源句是否还在原始历史窗口内”作标准，18 行审计：候选 required 原子 9 个，仅 2 个在原文历史之外（K02 两条），3 个出现在 facts 投影段，0 个属于“投影里有、原文里没有”。原因是 v5/v6 历史预算把 `history_cu` 压到 1400 以内，但被裁剪的窗口仍是较近回合，早期事实大多还留在原文里。因此本轮不能宣称长期记忆召回增益：候选在 K 链的优势更多来自近期上下文的使用与打分维度，而不是“跨窗口取回被裁掉的事实”。要证明长期通道，需要构造“事实在 T≤8、且在 T24 时已滑出原文窗口”的探针。

## 7. 成本

| 项目 | 调用 | prompt tokens | completion tokens |
|---|---|---|---|
| 生成（去重后 144） | 144 | 1,014,589 | 56,580 |
| 盲评 hy3 | 72 | 251,377 | 50,970 |
| 合计 | 216 | 1,265,966 | 107,550 |

合计 1,373,516 tokens；网关事件未回报 cache 命中。若含续跑复制的 48 次重复回合目录记录，token 统计会虚高到 1,425,755，本报告已按去重口径。

## 8. 结论

72 对里候选以 38:10:24 领先，正面信号切片 5:2:5，知识切片 15:3:6，且 0 评分卡冲突；同时存在候选协议失败 1 例（N01_T05）、8 处 major errors 集中在过度保证与时间状态；知识投影的长期召回未被验证（第 6 节）。F3 判定：候选在新材料上可见净优势，但不足以宣布最终 ACCEPT，须处理 N01_T05 并保留“长期记忆未验证”的结论边界。
