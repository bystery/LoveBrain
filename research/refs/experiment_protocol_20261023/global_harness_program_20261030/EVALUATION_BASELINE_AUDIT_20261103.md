# 评测基准复核（EVALUATION_BASELINE_AUDIT）2026-11-03

> 状态：audit_done（只读复核；未改题、未改评分器、未新增模型调用）
> 证据截止：2026-11-03 13:50；W18B live 320/320 完成、0 error，评审尚未开始。
> 目的：回答"数据集/评测基准是什么、有没有问题、现在能不能收尾选冠军"。

## 0. 一句话结论

尺子不是坏的，但当前证据只能支持**开发集内的相对改进**，不能支持
"已选出经封存验收的最终冠军"。具体缺口是：语义 judge 未按 rubric v0.5 完成
20 锚校准；近期故事 gold 全为空，程序分只测契约；隔离路径有 FK-SCORE-01
计分 bug；选择集与 B 线从未创建，`final_20261029` 已被 F3 消耗；用户偏好层没有
对当前冠军投过票。因此现在可以定"分域最佳"，不能定"通用最终冠军"。

## 1. 评测基准到底是什么

### 1.1 五层结构（rubric v0.5）

| 层 | 评什么 | 实现 | 当前可信度 |
|---|---|---|---|
| 层1 程序 | JSON 契约、source_ids、LCS、AI 味、风格多样、gold 断言 | `arena/lb_scorer.py` | 契约可信；gold 断言近期未实例化 |
| 层2 语义/互动 | 九维 0–3、severe、污染、可发送性 | `tools/run_lp_review_generic.py` + judge | 未完成 judge 校准；只能辅助 |
| 层3 用户偏好 | 是否真愿意发送 | 用户锚点/HITL | 当前冠军未测 |
| 层4 资源/等待 | usage、调用、p50/p95、失败 | `calls.json` / manifest | 可信（真实 usage） |
| 层5 可维护性 | 规则重复、纠正次数、故障恢复 | 报告式 | 未量化 |

### 1.2 题目与卡

- 题目单位 = story episode；每个 turn 是一个请求位（聊天记录 + clock + 可选
  intent/may_decline）。最近批次还用 batch 专用 `GRADING_CARDS_W*.json` 给 judge
  提供 `current_action`、`acceptable_actions`、`required_memory_atoms`、
  `unknowns`、`forbidden_factual_claims`、`intent`、`may_decline`。
- 旧开发集：`benchmark/episodes/*.json` 144 篇 / 1969 turns；
  gold 非空仅 59 turns（3.0%）；intent 265 turns（13.5%）。
- 新故事集：`benchmark/stories_v2/**/episode.json` 239 篇 / 3202 turns
  （含 12 篇单轮锚点、4 篇 final、16 篇 LRE、212 篇 E、3 篇 D）；
  gold 非空 **0 / 3202**；intent 217 turns（6.8%）。
- 最近判决批的样本：
  - W17c（≤24 域）：6 篇 ×12 turns = 72 位/臂；卡 72 张，intent 仅 5 张，
    may_decline=true 仅 7 张。
  - W18B（>24 域）：4 篇 ×40 turns = 160 位/臂；卡 160 张，intent 10 张，
    may_decline=true 14 张。
  - W16（>24 域）：3 篇 ×40 turns = 120 位/臂；卡 120 张。
- 卡内 case_id 当前批次无重复（W17C 72/72、W18 160/160）；旧卡包跨版本文件
  有 326/1752 条重复，属版本混用风险，不是单批判分重复。

### 1.3 基准/冠军谱系

- 原版参照：`reference_product_v1`（B0）。直接对 B0 的证据在早期批次：
  W04 fs1（E3/Bmin vs B0）、W09（v0 vs B0）、早期 longprobe（如 C1 vs B0）。
- 当前决赛身份：`window_expand_b_norecap_guard`（≤24）与
  `window_expand_b_norecap_uc`（>24）。它们分别对 r9_slim / uc 父本比较，
  **没有在同一批里对 reference_product_v1 做头对头**。因此"比原版好"目前是
  沿谱系的间接推断，不是同题同批证据。

## 2. 层1：程序分只证契约，不证语义

1. **近期故事 gold 全空**（0/3202），所以 W17c 的 9.31 vs 9.68、
   W18B 的 8.5–9.5 等 `score10` 只覆盖
   `json_schema_valid`、`source_ids_exist`、`note_lcs`、`ai_flavor`、
   `style_diversity`、`decline_appropriateness` 等可实例化检查；
   `must_kw_*`、`forbidden_fact_*`、`ongoing_*`、`cancelled_*`、`intent_kw_*`、
   `stale_kw_*`、`geo_must_not_*` 没有 gold 可实例化。
   **不能把 9.x 说成"事实/质量 9.x"。**
2. **FK-SCORE-01（OPEN）**：`arena/isolation/trusted_controller.py` 的
   `step_one_turn` 没有继承 `arena/lb_runner._exec_turn_v2` 的
   `m_src[i] -> HIST` 映射；隔离路径把合法的 `m_src` 引用判成非法
   source_id，触发假 veto。W17b/W17c/W18B 都受影响。
   - W17c 原始候选零分 5 个，其中 E183_T03/E191_T02 是本 bug 误杀；
     修正后候选 3 零 vs 父本 2 零；原始候选均值 9.3056，修正约 9.58，
     父本 9.6758（最终口径待 W19-01 离线重评）。
   - 这不是候选退步，是尺子 bug；W19-01 修复前，程序分的零分/均值只能
     作临时参考。
3. 旧集 gold 只有 59/1969，且旧卡只覆盖 81/144 篇；旧批的程序分也不是
   完整事实检查。历史结论同样应按"契约分为主"解读。

## 3. 层2：语义 judge 未校准，且顺序敏感

### 3.1 校准要求未满足

- rubric §3：judge 上岗需 20 锚点盲评一致率达标，落
  `logs/judge_calibration.json`。该文件不存在。
- 评审工具实际使用 `anchors="W04-01_RUBRIC_ANCHORS_V1"`、dsf temp0.0；
  `benchmark/anchors/` 下虽有 ANCHORS_20/ANCHORS_20_v2，但没有把当前九维
  judge 的 20 锚一致率写成上岗记录。
- 唯一的资格证据 `evaluation/JUDGE_QUALITY_REPORT_V1.md`（IRR-V1.0，
  13 节点×3 会话）显示：
  - 目标完成度：精确 76.9–88.5%、相邻 100%（合格）；
  - **明确事实错漏判：83.3%，低于 ≥90% 阈值（不合格）**；
  - **信息不足处理：62.5%，低于 ≥80% 阈值（不合格）**；
  - **归属判定一致：33–50%，低于 ≥75% 阈值（明显不合格）**；
  - 顺序敏感：可比样本只剩 3 例，0 翻转（不足以下结论）。
- `evaluation/A3_CALIBRATION_ANALYSIS_V1_2.json` 是另一套 A3 judge/电池的
  稳定性证据（fact_unfounded 22/22、protected_true 20/20、retest 15/15、
  swap 16/16、跨会话 30/30 等），不能替代当前九维 judge 的 20 锚校准；
  其 `expected_pref_note` 还注明锚点偏好全是"甲"，pref 不能当判决依据。

### 3.2 顺序敏感性（W17c 实证）

`LP_W17C_G1_MAINONLY.json`：

| 顺序 | 位数 | 九维配对差 |
|---|---|---|
| main | 61 | 全正：goal +0.377、fact +0.213、attrib +0.164、time +0.262、boundary +0.148、memory +0.393、interact +0.180、init +0.230、emotion +0.033 |
| swap | 6 | 除 time=0 外全负，多数维 −0.333，emotion −0.5 |

swap 一致仅 3/6。**同一候选的九维优势会随展示顺序翻转**；这不是小噪声，
而是位置偏置的直接证据。W17c 判决 Rev2 已披露，但结论必须降级为
"main 顺序下为正、swap 下为负"，不能当作稳定的语义优势。

### 3.3 严重错误（severe）是当前最可用的语义证据

W17c 的 severe 由 B3 主会话亲读 4 个 judge 标记：
E157_T06 降级为不成立；E164_T04、E170_T02、E170_T05 坐实。
候选 severe 0 vs 父本 3。这个证据带原文/当时卡，强于九维均分；
但样本只有 6 篇，且 4 个标记是 judge 初筛后的人工复核，仍不能外推成
"所有故事 severe 都更低"。

### 3.4 评审调用失败

W17c 合并评审 callfail 4/72（5.6%），swap 只覆盖 3/6 篇；
W17 合并评审 2/72。失败位未计入均值，属于"未评"而非"通过"；
样本小，需在报告中保留。

## 4. 层3：用户偏好缺席

- `benchmark/anchors/user_taste_anchors_61.json` 有 61 条真实聊天/已发送回复，
  可用于风格锚点，但**不是**对候选 A/B 的"愿不愿意发"投票。
- 当前冠军没有经过 HITL 回复展示包，也没有用户盲评；rubric 的层3
  "最终裁决"没有数据。worker/judge 不能代投。

## 5. 层4：成本/等待——这部分是可信的

- W17c：父本 prompt 440,613 vs 候选 210,578（**−52.2%**）；
  T07–12 保持率 1.034；p50 约 59–100s，p95 约 97–120s。
- W16：prompt 444,475 → 447,936（**+0.78%**，≤5% 门）；0 额外调用。
- W18B（刚完成，机械读数）：prompt 总体约 +1%；
  p50 35–46s，p95 63–85s；候选 LRE121 出现 9 个 schema 失败，
  机械分 7.75 vs 父本 8.50，是本批最差的一篇。
- 金额：本地网关没有可核对单价，按 rubric 记"金额未知"，不按免费填 0。
- 失败与超时不从分母删除；W18 首轮 90/320 的 120s 超时事件已单独记账，
  W18B 是新批不是续跑。

## 6. 层5：可维护性未量化

没有数值门禁，也没有对 guard/uc 的规则重复、用户纠正次数、故障恢复难度
做统一测量。当前只能报告机制差异与代码规模，不能声称"更容易维护"。

## 7. 数据集独立性与最终验收缺口（最严重）

1. `benchmark/releases/arena_v1/split_manifest.json` 明写：
   - `selection_set`: role=selection，n=0，状态 `EXPOSED / NOT_READY`；
   - `b_line_sealed`: role=final_sealed，n=0，状态 `NOT_CREATED`。
   即 rubric §7 第4步选择集盲评、第5步 B 线封存验收**从未具备可执行资产**。
2. `benchmark/stories_v2/final_20261029/` 的 K01/K02/N01/N02 虽在
   exposure 中写 `experiments_used: []`、`frozen_at` 2026-10-29，
   但实际已被 `logs/f3_newmat_live_live_20261029/` 使用
   （candidate_N01 有 manifest，candidate_K01 有 error），
   `logs/c_contrast_live_20261029_*` 也用到 K/N。**它不是未动用的最终集。**
3. LRE 长剧是 E117/E121/... 的扩展（`exposure.source=E117`），
   与 12 轮域共享故事种子，不是独立材料；且 LRE117/LRE146/E157 的
   `experiments_used` 仍为空，已被 W16/W17c/W18B 使用却未登记，
   exposure 账本不可信，`dev_exposed=false` 不能当作"未暴露"。
4. W16/W18 卡元数据 `independent_review: false`、author 为 AI；
   W17C 卡文件没有 independent_review 字段。参考/卡均非独立人审。
5. 结果：没有"未参与调参的故事"上的独立迁移证据；当前所谓冠军
   只能是**开发集冠军**，不能叫封存验收冠军。

## 8. 与基线/原版的比较现状

- 直接对 B0 的同批证据是早期 W04/W09/早期 longprobe；当前 guard/uc
  没有与 B0 同批同题对拍。
- guard vs r9_slim（W17c）：契约、severe、成本三项可辩护；
  九维受顺序敏感和未校准影响，只能辅助。
- uc vs b_norecap（W16）：九维 9/9 正、severe 亲读 1 vs 6、
  prompt +0.78%；是当前 >24 域最强的开发集证据。
- uc_supp vs uc（W18B）：live 刚完成，机械读数不支持"更好"
  （LRE121 明显退化），语义评审未开始；不能先宣布 KEEP。

## 9. 现在能定什么、不能定什么

| 问题 | 现在能否回答 | 依据/缺口 |
|---|---|---|
| ≤24 域最佳 | 能：`window_expand_b_norecap_guard` | W17c Rev2；契约/severe/成本；九维仅辅助 |
| >24 域最佳 | 能：`window_expand_b_norecap_uc` | W16 KEEP；uc_supp 待 W18B 评审 |
| 单一通用冠军 | 不能 | 没有 guard+uc 合并体的同批跨域测试 |
| 比原版 LoveBrain 更好 | 不能直接下结论 | 当前决赛未与 B0 同批同题 |
| 封存最终验收 | 不能 | selection/B 线不存在，final_20261029 已消耗 |
| 用户愿不愿意发 | 不能 | 无当前冠军的用户盲评/HITL |
| 成本/等待改善 | 能（分域） | 真实 usage；金额未知 |
| 可维护性改善 | 不能量化 | 无测量口径 |

## 10. 有界收尾路径（按依赖顺序）

1. **W19-01（离线，0 调用）**：修 `trusted_controller.step_one_turn` 的
   `m_src` 映射，离线重评 W17b/W17c/W18B；在此之前不把程序零分当候选退步。
2. **W18B 评审**（live 已完，评审可在 live 后单独跑）：4 篇 ×160 位，
   main + swap 子集，B3 亲读 severe；按 W18 设计门判 uc_supp 去留。
3. **单冠军跨域批**（若要做"一个最佳"）：把 guard 与 uc（或存活后的
   uc_supp）放到 W17c 的 6 篇 + W18B 的 4 篇上，同批 464 调用、6 流、
   约 75 分钟；这是唯一能把"双域冠军"收成一个开发集冠军的实验。
4. **最终验收**（若要"封存"）：新写 4–6 篇未暴露故事（不能复用 K/N/LRE），
   对 reference_product_v1 + 当前冠军做同批盲评，并让用户做 A/B 愿发投票；
   否则只能发布"开发集冠军"并显式标注未封存。
5. **尺子修复（可与上面并行）**：judge 20 锚校准落
   `logs/judge_calibration.json`；exposure 账本补 `experiments_used`；
   W17C/W18 卡补 independent_review；旧卡跨版本重复 case_id 建清单隔离。

## 11. 证据路径

- rubric：`benchmark/rubric.md`（v0.5）
- split：`benchmark/releases/arena_v1/split_manifest.json`
- 旧集：`benchmark/episodes/*.json`
- 新集：`benchmark/stories_v2/**/episode.json`
- 卡：`benchmark/stories_v2/wave3/GRADING_CARDS_W17C.json`、
  `benchmark/stories_v2/wave3_ext/GRADING_CARDS_W16.json`、
  `benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json`
- 程序分：`logs/w17c_merge_live_20261103/_reports/*`、
  `logs/w18b_ucsupp_live_20261103/_reports/ARM_STATS_W18B.json`
- 九维：`refs/experiment_protocol_20261023/long_probe_20261030/LP_W17C_NINEDIM_REVIEW.json`、
  `.../LP_W17C_G1_MAINONLY.json`、`.../LP_W16_NINEDIM_REVIEW.json`
- judge 资格：`evaluation/JUDGE_QUALITY_REPORT_V1.md`、
  `evaluation/JUDGE_QUALITY_THRESHOLDS_DECLARED_V1.md`、
  `evaluation/A3_CALIBRATION_ANALYSIS_V1_2.json`
- 最终集被使用：`logs/f3_newmat_live_live_20261029/`、
  `logs/c_contrast_live_20261029_*`
- 隔离计分 bug：`refs/KNOWN_FAILURES.md`（FK-SCORE-01）、
  `arena/isolation/trusted_controller.py`、`arena/lb_runner.py@334`
