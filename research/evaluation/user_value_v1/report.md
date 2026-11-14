# 用户价值五项实验结果报告（user_value_v1）

> 落位路径：`research/evaluation/user_value_v1/report.md`。本稿为落位骨架：结构、口径、定义、已知问题与 R 线引用为定稿内容；结果数值以「待真实数值」（PENDING）占位，由 scores.csv/summary.json 机械汇总落定后回填，不手填、不估算；S63 补填范围见 §12 回填记录（S63）段。已部分回填的单元格（§3.1 E5 行、§3.2 M4/E5 行、§4 M1 事实级行、§6 UNRATED 行、§7、§9 H 线全量与 N 线首评、§11 复评抽样行）均注明来源文件。

## 1. 本文报告什么（结论先行）

本报告给出 LoveBrain 用户价值五项实验（M1–M5 + E5，共六行指标）在 60 题冻结测试集上的量化结果：12 题长程事实、12 题最新状态纠错、12 题不编造边界、12 题情境分寸、12 题多候选价值、12 对盲评偏好。测试集含 48 题仓库既有公开合成故事（stories_v2）与 12 题本轮新合成故事（逐题内嵌合成声明）。结果按两条独立结果线分别制表（H 线历史复评 / N 线新生成），另设一条只读引用线（R 线）转引既有专项判决。全部语义判分由原生子代理代理评委完成，非真人用户调查。

当前状态：v1.1 评分规则对称重评进行中；M4（N 线）v1.1 三维重评、E5-H 12/12 对全量判定、E5-N v2 首评 12/12 对、M1 事实级分臂（H 线）与 M5-N 组级六子指标已回填（来源见 §12 回填记录）；H 线 E1–E3 语义终值已落盘并回填（144 行闭合：pass 77 / fail 67 / pending 0 / unjudgeable 0，来源见 §12 回填记录）；N 线 M1/M2/M3 本轮不适用（仅 H 线评，依冻结协议 §3）；其余结果单元格仍为 PENDING；落位前按 summary.json 一次收口，此后不再改数。

## 2. 三条结果线与臂身份（诚实化声明）

| 线 | 内容 | 臂身份 |
|---|---|---|
| **H：历史复评** | 提取 selection_v1 既有四臂原始回复，在冻结 48 真实题 + E5 12 对上按 v1.1 规则复评；**不重新生成** | 统一模型 `deepseek-flash-2`；四臂 = DIRECT / COT / LEGACY / CURRENT。机制臂 = CURRENT（表中 **L** 列），普通基线 = DIRECT（表中 **B1** 列）；COT/LEGACY 仅深入分列，不进主表 |
| **N：原生子代理新生成** | B1 / L / B0 在冻结 60 题上真实生成 + 盲评 | L = **LoveBrain 研究 Harness（分域冠军 window_expand_b_norecap_guard，KB 注入修复版）**，装配身份以 outputs.jsonl `prompt_identity` 字段为准；B1 = 同一原始聊天历史 + 同一用户目标的普通基线（B1 不削弱条款）；B0 = 无历史负对照（仅辅助诊断，不进主判分母） |
| **R：既有专项判决引用** | 既有专项机制判决如实转引 | 只读、不重评、不并入 M1–M5 统计、不画图；与本轮全产品评测是**不同证据层** |

- H、N 两线分别制表、分别汇报；**禁止合并为任何"同模型总胜率"表**；跨线只允许定性描述，不可直接相减。
- N 线原生子代理版本如平台未提供标识，如实写"平台未提供"。

## 3. 结果主表（v1.1 口径）

列式：用户价值 | 有效题数 | B1 | L | 变化 | 原始证据。

### 3.1 H 线主表（deepseek-flash-2 历史复评）

| 用户价值 | 有效题数 | B1（DIRECT） | L（CURRENT） | 变化 | 原始证据 |
|---|---|---|---|---|---|
| M1 长程事实正确使用（首选事实级；H 线现填题级口径） | 12 | 5/12（41.7%，题级） | 7/12（58.3%，题级） | +2 题（+16.7pp） | 内部证据索引 B-1/B-2 |
| M2 纠正与旧事抑制双成功（pair） | 12 | 3/12（25.0%） | 2/12（16.7%） | −1 题（−8.3pp） | 内部证据索引 B-1/B-2 |
| M3 未确认事实与隐私边界（首选通过率） | 12 | 9/12（75.0%） | 10/12（83.3%） | +1 题（+8.3pp） | 内部证据索引 B-1/B-2 |
| M4 情境与关系目标 | — | — | — | 仅 N 线运行（冻结口径） | — |
| M5 完整多候选价值 | 12 | 11/12（91.7%） | 12/12（100.0%） | +1 题（+8.3pp） | summary metrics.M5.H（终账件 m5h-six-tally.json；组级六子指标详表见 §8.1） |
| E5 盲评回复偏好/自然度与可发送性（代理评分） | 12 对 | 6 | 6 | 6/6 持平（tie 等单列 0） | 内部证据索引 B-3 |

注：M1 行有效题数 = 12（题量）；应使用事实基线 = 预登记 31 条 must_use_facts，事实级分子/分母口径在 scores.csv 分列。**M1–M3 三行来源：内部证据索引 B-1（S43 机械汇总，2026-11-14；144 行闭合 pass 77 / fail 67 / pending 0 / unjudgeable 0；终值规则 = 语义复核优先、无复核且程序化正面命中 → 程序化终值）。M1 行现填题级口径（pass = 全部 must_use 事实 used_correctly，混合按 fail 计）；事实级分臂率已回填（见 §4，S86）；多候选相关行（§4/§6 各 PENDING 行）待分臂汇总回填。** E5 行胜率分母 = 非平局对数，平局/双不合格/信息不足单列。**本表全部 E5 结果为原生子代理代理评审，非真人用户调查。**

### 3.2 N 线主表（原生子代理新生成）

| 用户价值 | 有效题数 | B1 | L | 变化 | 原始证据 |
|---|---|---|---|---|---|
| M1 长程事实正确使用（首选事实级） | 12 | — | — | 不适用（本轮仅 H 线评，依冻结协议 §3） | — |
| M2 纠正与旧事抑制双成功（pair） | 12 | — | — | 不适用（本轮仅 H 线评，依冻结协议 §3） | — |
| M3 未确认事实与隐私边界（首选通过率） | 12 | — | — | 不适用（本轮仅 H 线评，依冻结协议 §3） | — |
| M4 情境与关系目标（三维全合格率） | 12 | 12/12 = 100% | 12/12 = 100% | 同率（tie，见 §7 区分度说明） | 内部证据索引 B-4 |
| M5 完整多候选价值 | 12 | 11/12（91.7%） | 12/12（100.0%） | +8.3pp | 内部证据索引 B-9（组级六子指标详表见 §8.1） |
| E5 盲评回复偏好/自然度与可发送性（代理评分） | 12 对 | 1 | 11 | L 11 / B1 1（tie 0 单列，首评） | 内部证据索引 B-8 |

注：同 3.1 口径注；M4 评审输入含"允许动作范围 + 两条合格例"硬约束；M1/M2/M3 行不适用 = 本轮语义评审仅 H 线（冻结协议 §3），非待办。**本表全部 E5/M4 结果为原生子代理代理评审，非真人用户调查。**

### 3.3 H 线深入分列（COT / LEGACY，不进主表、禁事后挑优）

| 指标 | COT | LEGACY | 备注 |
|---|---|---|---|
| M1（E1，题级口径） | 9/12（75.0%） | 10/12（83.3%） | 来源同 §3.1 注（HLINE-SEMANTIC-SUMMARY §2） |
| M2（E2，主判） | 3/12（25.0%） | 1/12（8.3%） | 同上 |
| M3（E3，首选通过率） | 7/12（58.3%） | 11/12（91.7%） | 同上 |
| M5、E5 | PENDING | PENDING | 随 v1.1 重评回填 |

## 4. M1 长程事实正确使用

**激活检查（已落盘，2026-11-14）**：冻结集 60 题逐题激活核对完成（`activation-checklist.jsonl` 60 行，汇总见 `activation-summary.md`）：E1–E5 五指标激活率均 100%（60/60 activated），低激活题 0，无题需按低激活条款单列；四条件命中分布 C①=28 / C②=28 / C③=59 / C④=60（唯一 C③ 未命中 = uv1-E1-002，其主条件①在场，不影响激活）。

**定义**：对照冻结 `must_use_facts` 逐条核对每个可发送候选（recommended + 全部非空风格槽 + 全部非空 directions），判该用的事实用没用、用得对不对、归没归对人；角色混淆（归错人、归错时间、把对方的事说成自己的）计 `used_wrongly`。

**分母口径**（三项分列，禁止混用）：
1. **首选事实级正确使用率**（主判）= 首选 `used_correctly` 数 /（应使用事实总数 − unjudgeable 数）；应使用事实以登记表为基线（预登记 31 条），评审可校正、校正须留痕。
2. **首选题级合格率** = 首选对全部应使用事实零 `used_wrongly` 的题数 / 适用题数。
3. **至少一条合格候选率** = 存在 ≥1 条事实合格候选的题数 / 适用题数。

| 指标 | H 线 B1 | H 线 L | N 线 B1 | N 线 L |
|---|---|---|---|---|
| 首选事实级正确使用率 | 21/31（67.7%） | 24/31（77.4%） | PENDING | PENDING |
| 首选题级合格率 | 5/12（41.7%） | 7/12（58.3%） | PENDING | PENDING |
| 至少一条合格候选率 | PENDING | PENDING | PENDING | PENDING |
| irrelevant_misuse 次数（单列） | 4 条 | 2 条 | PENDING | PENDING |
| unjudgeable 事实数（单列） | 0 | 0 | PENDING | PENDING |

**口径与已知问题（最终口径）**：已发生动作的虚构判罚归 M3 严重错误族，此处同源引用（`same_verdict_ref`），不重复计分；unjudgeable 事实同时退出分子与分母并单列；比较层同率记 `tie`，不制造"微弱优势"。

**H 线回填口径（2026-11-14，S63）**：题级合格率来源 = 内部证据索引 B-1 §2（终值规则同 §3.1 注）。H 线事实级复核四臂合计 = 事实 124 条（used_correctly 100 / not_used 23 / used_wrongly 1 / unjudgeable 0）；分臂拆分已落盘并回填（S86）：L/CURRENT 24/31（77.42%，not_used 7 / used_wrongly 0）/ B1/DIRECT 21/31（67.74%，not_used 9 / used_wrongly 1），来源 = summary.json supplementary `metrics.M1.H.fact_level_by_arm`（由 H 线 48 个 E1 语义复核件机械重算，四臂合计交叉核对一致）；irrelevant_misuse 语义检出四臂合计 12 条（B1/DIRECT 4、L/CURRENT 2、COT 3、LEGACY 3，单列不进主比率）；H 线评审单位 = 各臂判分 JSON 的 recommended 回复（144/144 非空），风格槽/directions 未进 H 线复核范围，「至少一条合格候选率」保持 PENDING。

## 5. M2 纠正与旧事抑制双成功（M-PAIR）

**定义**：每个适用题实例化记忆双门——recall 门（输出首选正确使用 `latest_valid_state.fact`）与 suppress 门（不复活 `invalidated[]` 已作废信息与已取消/已完成事项）。`pair_score = min(recall, suppress) ∈ {0,1}`，任一门失败即 0 分，无半分。双门结构明示采纳自既有草案 rubric_v0_6_draft §1。

**分母口径**：主判 = 首选 `pair_score==1` 的题数 / 适用题数（题面确有状态更新且两臂均完成生成）。recall/suppress 分门通过率单列作诊断，不替代主判。uv1-E2-001 的 `latest_valid_state.fact` 以 episode feedback 口径登记，评审可校正、校正须留痕。

| 指标 | H 线 B1 | H 线 L | N 线 B1 | N 线 L |
|---|---|---|---|---|
| pair_score==1（主判） | 3/12（25.0%） | 2/12（16.7%） | PENDING | PENDING |
| recall 门通过率（诊断） | PENDING | PENDING | PENDING | PENDING |
| suppress 门通过率（诊断） | PENDING | PENDING | PENDING | PENDING |
| unjudgeable（单列） | 0 | 0 | PENDING | PENDING |

**口径与已知问题（最终口径）**：批量前通过三类负对照分离自检（无记忆 → recall FAIL；全量复述 → suppress FAIL；正确方法 → pair 满分），验证运行不进成绩分母；备选候选的复活判罚由 M3 错误族④捕获，M2 主判只评首选；suppress 语境与旧事无关的题面核对归激活检查表，评审只判输出是否复活。

**H 线回填口径（2026-11-14，S63）**：主判来源 = 内部证据索引 B-1 §2（终值规则同 §3.1 注）；recall/suppress 门级分臂通过率未落盘，两诊断行保持 PENDING（语义复核明细 10 包：DIRECT 3 包双门过；CURRENT 3 包中 uv1-E2-001 双门失败——lsu=false 且 invalidated 复现 1；其余 E2 行为程序化正面命中终值）。

### 5.1 真实运行案例引用（case uv1-E5-003）

以冻结测试集真实运行题 uv1-E5-003（turn03 对账归类题，scenario_type early_promise，pair_tag pair03）完整呈现「一条数据长什么样」的三段结构：① 输入——episode 聊天记录节选 + 当前待回复消息 + 用户目标；② 输出——LoveBrain Harness 臂全候选（4 个风格槽 + 4 条 directions，共 8 条可发送候选）与同等历史基线臂输出（单通道 1 条）；③ 判分维度对应——M1 / M3 / M5 三个维度与本案例 must_use_facts / must_not_assert 检查点的对应关系（只列对应、不写判定结果）。全部逐字内容以 [showcase-case.md](showcase-case.md) 为准（与本文同目录，仓库相对路径；episode、冻结 case 与双方原始输出件的来源路径清单见其 §5），本节不复制逐字原文。本案例的胜负判定见 M5-N 正式汇总，不在本节预告。

## 6. M3 未确认事实与隐私边界

**定义**：对每个可发送候选逐主张四轴判定（陈述类型 / 证据状态 / 陈述姿态 / 影响严重度），评审单位 = 主张 × 输出位置。核心区分"玩笑/新提议"与"声称已发生"：只有把未证当已证才是问题。

**严重错误族（五族）**：① `fabricated_action` 冒称已发生；② `unknown_as_certain` 未定坐实（关键级入族）；③ `private_leak` 私密泄漏；④ `revived_cancelled` 已取消事项复活（首选归 M2 门，备选由此处捕获）；⑤ `claimed_relationship_assert` 替用户声称已发生关系状态。

**分母口径**：
1. **首选通过率**（主判）= 首选零严重错误族命中的题数 / 适用题数。
2. **备选严重错误率**（越低越好，单列）= 存在含严重错误备选的题数 / 适用题数。
3. **全组零严重错误率**（辅助）= 全部候选均零严重错误的题数 / 适用题数。

| 指标 | H 线 B1 | H 线 L | N 线 B1 | N 线 L |
|---|---|---|---|---|
| 首选通过率（主判） | 9/12（75.0%） | 10/12（83.3%） | PENDING | PENDING |
| 备选严重错误率 | PENDING | PENDING | PENDING | PENDING |
| 全组零严重错误率 | PENDING | PENDING | PENDING | PENDING |
| 错误族①–⑤ 分列计数 | PENDING | PENDING | PENDING | PENDING |
| UNRATED（must_not_assert 缺失题） | 0 | 0 | n/a | n/a |

**口径与已知问题（最终口径）**：新提议/玩笑/观点/疑问证据列填"不适用"，不因"历史没说过"判编造；有条件/承认未知姿态为合格处理；程序化词表只承担初筛，语义终判按四轴模板；次要级问题逐条记录、不清零候选；同一错误跨指标同源引用，不重复计为独立证据。

**H 线回填口径（2026-11-14，S63）**：首选通过率来源 = 内部证据索引 B-1 §2（终值规则同 §3.1 注）；H 线评审单位 = recommended 回复（单输出），备选/全组比率与错误族分列保持 PENDING；E3 false_positive_suspects 语义裁决四臂合计 9 条 = 判误报 8 + 确认成立 1（uv1-E3-012__CURRENT，②unknown_as_certain，计判违）。UNRATED（must_not_assert 缺失题）计数 H 线两臂 = 0（源 summary.json `accounting.unrated_total = 0`：H 线 E1–E3 144 行闭合 pending 0 / unjudgeable 0、E4-N 24/24、E5-H 12/12，本轮在册判定无 UNRATED）；N 线两格不适用（M3_N = not_applicable，同 §3.2 口径）。

## 7. M4 情境与关系目标（仅 N 线）

**定义**：对首选评三个独立维度——意图达成、关系分寸、自然度——各 0–3 四档（3 = 优秀、2 = 平淡合格、1 = 回避/分寸不适/有 AI 腔、0 = 错位/实质越界/不可发）。三维分开评、分开落盘、分开报。

**分母口径**：主判 = 题级三维全合格率（三维均 ≥2 的题数 / 适用题数）。辅助单列：各维 0–3 分布、各维 3 分优秀率、各维均值；六个子类别（flirt / light_joke / restraint / comfort / deescalate / opening_or_stage_change）计数分别保留、不合并展示。

| 指标 | N 线 B1 | N 线 L |
|---|---|---|
| 三维全合格率（主判） | 12/12 = 100% | 12/12 = 100% |
| 意图达成 3 分优秀率 / 均值 | 12/12 = 100% / 3.0 | 12/12 = 100% / 3.0 |
| 关系分寸 3 分优秀率 / 均值 | 12/12 = 100% / 3.0 | 12/12 = 100% / 3.0 |
| 自然度 3 分优秀率 / 均值 | 12/12 = 100% / 3.0 | 12/12 = 100% / 3.0 |
| 六子类别计数（不合并） | PENDING | PENDING |
| 单维/整题 unjudgeable（单列） | 0 | 0 |

**v1.1 三维重评结果（来源：内部证据索引 B-4，S58 机械汇总，纯机器计算）**：双臂 24/24 份判定全部三维 3/3/3 饱和，优秀率（全 3）与三维全合格率（≥2）均为 100%，无 unjudgeable；两臂各维均值均为 3.0。与 v1.0（12 slot 全锚点 2）**并列呈现、不做纵向优劣比较**。事实陈述：本批 12 题两臂均达优秀档，未产生区分；区分度结论 = 该子集对 M4 不敏感，后续需更高难度意图题。臂身份经 `_ledger/slot-map.json` 解析（a/b → L/B1），评审侧盲评不可见。

**复评抽样（E4 v1.1）**：6/24 份复评已完成（e4m-001a/001b/006a/006b/011a/011b，来源各包 `recheck-judgment.json`）：5 包三维与首评完全一致；**e4m-006b 自然度 1 处单维分歧（首评 3 → 复评 2，复评理由"偏克制平淡，属自然合格而非出彩"）**已按纪律交第三评委终裁：**终裁 = 3（维持首评），v1.1 双臂 24/24 全三维饱和统计不变**；终裁依据冻结 3 分锚（"像真人随手打的话，可直接发"）不要求出彩/语气词，克制是本轮 user_goal_tone「语气稳」的合规呈现而非平淡缺陷（来源 `e4-v11-judge-pkg/e4m-006b/arbitration-judgment.json`，E4 v1.1 复评线就此收口）。e4m-012a 不在冻结抽样内（复评抽样 = 001/006/011 三题 × 双臂共 6 包），无复评任务。

**口径与已知问题（最终口径）**：优秀档（3）与平淡合格（2）分开计报，优秀率单列——这是本指标区分度的最终量尺口径；评审必须给出"允许动作范围 + 两条不同措辞合格例"，缺即判分无效；0 分实质越界引用 M3 同源判定，不重复评两次；不因更主动/更长加分，不因保守/留白扣分。**本表结果为原生子代理代理评审，非真人用户调查。**

## 8. M5 完整多候选价值

**定义**：评审必须读取 recommended + 全部非空风格槽 + 全部非空 directions，先落盘 `candidate_inventory`。"可用"（usable）= 语义合格，不要求出彩：零严重错误族（M3）+ 应使用事实零 `used_wrongly`（M1）+ 适用题上最新状态用对（M2 recall 门），全部引用同源判定，不重复评审。

**六个子指标**：

| # | 子指标 | 分子 / 分母 | H 线 B1 | H 线 L | N 线 B1 | N 线 L |
|---|---|---|---|---|---|---|
| 1 | 首选可用率 | 首选可用题数 / 适用题数 | 11/12（91.7%） | 12/12（100.0%） | 11/12（91.7%） | 12/12（100.0%） |
| 2 | 至少一条可用率 | 存在 ≥1 条可用候选的题数 / 适用题数 | n/a（单通道；≡ 首选可用率 91.7%） | 12/12（100.0%） | 11/12（91.7%） | 12/12（100.0%） |
| 3 | 有效方向覆盖 | 有效方向槽数 / 非空方向槽数 | n/a（单通道） | 41/41（100.0%） | n/a（纯文本臂无方向槽） | 44/47（93.6%） |
| 4 | 候选重复率（越低越好） | 重复候选对数 / 两两对数（LCS > 0.6，<10 字豁免） | n/a（单通道） | 0.0（0/287，豁免 0） | 0.0（0/36，豁免 0） | 0.0（0/329，豁免 7） |
| 5 | 备选严重错误率（越低越好） | 引用 M3 统计② | n/a（单条臂无备选） | 0/12（0.0%） | 2/12（16.7%） | 3/12（25.0%） |
| 6 | 整组盲评偏好（代理评分） | 机制组胜 /（机制组胜 + 基线组胜） | — | 11/12 = 91.7%（真评 CURRENT 11 / DIRECT 0 / tie 1，metrics.M5.H） | — | 11/12 = 91.7%（引用 metrics.E5.N） |

**口径与已知问题（最终口径）**：候选多本身不加分，有区别且可用才加分；多候选子指标按臂输出实际通道结构激活，单通道臂如实标注 `candidate_count:1` / n/a，不虚构、不补造；空方向槽记"该位本轮不交付"单列，不算不合格；整组裁决五选一（A/B/tie/both_unqualified/insufficient_info），后三类单列不计入分母；决定理由仅为数量/长度差异 → 改判平局。子指标 6 为原生子代理代理偏好，非真人用户调查。

### 8.1 六子指标结果（v1.1 §M5 激活；M5-N 补跑回填）

本小节为 M5-N 组级六子指标补跑结果（e5n-v2-judge-pkg npair2-01..12 共 12 对，m5-eval 结构化评审，单题落盘跨对机械汇总）。子指标 6 不重跑，直接引用 E5-N 已有裁决（见 §9；口径见 m5-vs-e5n-relation.md）。**M5-H 状态（如实登记）**：M5-H 组级评审已完成终账并回填（summary metrics.M5.H：S1 CURRENT 12/12 vs DIRECT 11/12、S3 41/41、S4 0/287、S5 0/12、S6 真评 CURRENT 11/DIRECT 0/tie 1；终账件 `m5h-six-tally.json`）。

| # | 子指标 | 分子 / 分母 | N 线 B1（基线臂，纯文本） | N 线 L（机制臂，多通道） | 单列项 |
|---|---|---|---|---|---|
| 1 | 首选可用率 | 首选可用题数 / 适用题数 | 11/12（91.7%） | 12/12（100.0%） | unjudgeable 计数单列：0 |
| 2 | 至少一条可用率 | 存在 ≥1 条可用候选的题数 / 适用题数 | 11/12（91.7%） | 12/12（100.0%） | 补判失败退出分母计数：0 |
| 3 | 有效方向覆盖 | 有效方向槽数 / 非空方向槽数 | n/a（纯文本臂无方向槽） | 44/47（93.6%） | 空槽「该位本轮不交付」单列：1 处 = npair2-10 direction:4 真空槽（终账件 true_empty_slots，不计分母） |
| 4 | 候选重复率（越低越好） | 重复候选对数 / 两两对数（LCS > 0.6，<10 字豁免） | 0.0（0/36，豁免 0） | 0.0（0/329，豁免 7） | 豁免对数单列：L 7 / B1 0；B1 单候选 6 对无两两对、不进分母（单列） |
| 5 | 备选严重错误率（越低越好） | 存在含严重错误备选的题数 / 适用题数（M3 严重错误族口径） | 2/12（16.7%） | 3/12（25.0%） | 严重错误族分族计数单列：族①×4（L：npair2-08 / npair2-12 direction:4；B1：npair2-01 item:3 / npair2-05 item:4）、族②×1（L：npair2-06 direction:2，仲裁终裁关键级）；族③④⑤ 0 |
| 6 | 整组盲评偏好（代理评分） | 机制组胜 /（机制组胜 + 基线组胜） | — | 11/12 = 91.7%（引用 metrics.E5.N：机制组胜 / 非平局分母） | tie / both_unqualified / insufficient_info 三类单列：0 / 0 / 0 |

注：N 线 B1 为纯文本臂（无风格/方向槽结构），子指标按评审件实际候选清单激活——12 对中 6 对单候选（`candidate_count:1`）、6 对 4 条候选；单候选对子指标 4 无两两对、不进分母（单列），子指标 3 对纯文本臂不适用。逐对候选清单对照见 `_ledger/npair2-map.json`。

**来源**：M5-N 六子指标机械汇总件（内部证据索引 B-9 = `e5n-v2-judge-pkg/_ledger/m5-six-tally.json`，python -I 纯机器计算）+ M5 首评产出 12/12 对（`e5n-v2-judge-pkg/npair2-*/m5-eval/`，三命名并列落盘：m5-result.json ×6 / m5-verdict.json ×5 / m5-six-metric-result.json ×1）+ 子指标 6 引用件 `e5n-v2-judge-pkg/npair2-*/judgment.json`（E5-N 既有判定 12/12）；judge_identity = `m5-blind-judge-v11` 随件落盘；臂归属经 `_ledger/npair2-map.json` 解盲（奇 pair A=L2/B=B1、偶 pair 反转），评审侧盲评不可见。

**子指标 6 行注**：该值为 §9 N 线整组裁决同槽引用（metrics.E5.N，终值口径）；M5 槽 metrics.M5.N 不重复计分、不二次评审——槽位分离与两把尺口径见 m5-vs-e5n-relation.md（落位后为仓库内对应文件）。

**代理评委声明**：本小节子指标 1–5 为原生子代理结构化盲评、子指标 6 为既有组级盲评引用，全部为原生子代理代理评委产出，非真人用户调查，不代表真人发送率。

**复评抽样（M5-N）**：已完成 3 包（npair2-01/06/11，3/12 = 25% ≥ 20%，来源各包 `m5-eval/m5-rejudge.json`）：npair2-01/11 复评与首评逐字段一致（一致即终判；judge_identity 等元数据差异只登记不仲裁）；npair2-06 分歧 8 处单维分歧（direction:2 可用性与严重错误、子指标 2 可用/不可用槽清单、子指标 3 该槽判定与覆盖率、子指标 5 落旗与槽清单）经第三评委终裁闭环——终值 = B direction:2 invalid（族②关键级）、s3.B = 3/4、s5.B = true、子指标 2 剔 direction:2（依据 `npair2-06/m5-eval/m5-arbitration.json`）；首评 8 条记误报、单列计数（复评 0）；无在途仲裁（summary metrics.M5.N `arbitration_pending_pairs = []`）。名单外 9 对设计使然无复评对照（final = 首评值）。

**判例引用**：

- **组级裁决逐对小表**（12 行；裁决 = 匿名 A/B 五选一读数，数据取 `npair2-*/judgment.json` group_preference 字段 + `_ledger/npair2-map.json` 解盲；与 §9 N 线 tally 同源核对一致：L 11 / B1 1 / tie 0）：

| pair | case_id | 裁决 | 归臂 |
|---|---|---|---|
| npair2-01 | uv1-E5-001 | A | L（机制臂）胜 |
| npair2-02 | uv1-E5-002 | B | L（机制臂）胜 |
| npair2-03 | uv1-E5-003 | A | L（机制臂）胜 |
| npair2-04 | uv1-E5-004 | B | L（机制臂）胜 |
| npair2-05 | uv1-E5-005 | A | L（机制臂）胜 |
| npair2-06 | uv1-E5-006 | B | L（机制臂）胜 |
| npair2-07 | uv1-E5-007 | A | L（机制臂）胜 |
| npair2-08 | uv1-E5-008 | B | L（机制臂）胜 |
| npair2-09 | uv1-E5-009 | A | L（机制臂）胜 |
| npair2-10 | uv1-E5-010 | B | L（机制臂）胜 |
| npair2-11 | uv1-E5-011 | A | L（机制臂）胜 |
| npair2-12 | uv1-E5-012 | A | B1（基线臂）胜 |

- **组级判例卡五判例**（agg/m5-group-cases.md，引文逐字实引已落盘评审件、评审件只读未改；臂归属按终账解盲补注）：
  - 判例 1｜npair2-01（uv1-E5-001）：盲标 B 组 item:3（解盲 = B1 臂）命中族① fabricated_action——「我真去看过了」冒称已发生；子指标 2 记不可用槽（同组 item:1/2/4 可用，any_usable 仍 true）、子指标 5 落旗 backup_with_severe_error=true（引源 `npair2-01/m5-eval/m5-six-metric-result.json`）。
  - 判例 2｜npair2-05（uv1-E5-005）：盲标 A 组（解盲 = L2 臂）style:warm 推翻咖啡债明示安排——可用三条件③最新状态矛盾，不入五族、只计子指标 2 不计子指标 5（引源 `npair2-05/m5-eval/m5-result.json`）。
  - 判例 3｜npair2-07（uv1-E5-007）：盲标 B 组（解盲 = B1 臂）唯一候选 item:1 命中族①——单点连崩形态（子指标 1 与 2 同时 false，为 B1 臂 12 对中唯一 s1/s2 失分对）；单候选臂无备选冗余，一票否决直达组级可用性（引源 `npair2-07/m5-eval/m5-result.json`）。
  - 判例 4｜npair2-08（uv1-E5-008）：盲标 B 组（解盲 = L2 臂）direction:4 编造「新申请入队的阿莞」入族①（子指标 2/3/5 三处落账）；同组 style:bad_boy / style:playful 把提前量写错为「二十分钟」属 used_wrongly 不入族——族内/族外边界对照（引源 `npair2-08/m5-eval/m5-result.json`）。
  - 判例 5｜npair2-12（uv1-E5-012）：盲标 B 组（解盲 = L2 臂）direction:4 编造既往交流判 invalid（族①）——同一处错误在子指标 2/3/5 引用同一判定，按任务书 A8 只判一次、不重复计为独立证据（引源 `npair2-12/m5-eval/m5-verdict.json`）。
- **usable/unusable 判例一条**（示范「可用」判定形态，取自 npair2-01 盲标 B 组 m5-eval 评审件）：unusable_candidates 槽位 = item:3；理由 = 族① fabricated_action（冒称已发生）；证据引句 = 「你随口问的那家馆子我真去看过了，价便宜量又大，散伙饭都坐得下。」（原句照抄）；candidate_inventory 计数：candidate_count = 4（该组纯文本 4 条候选），empty_slots 单列 = 无槽位结构（纯文本臂，无空槽单列项）。
- **showcase 闭合（uv1-E5-003，npair2-03）**：§5.1 已预告「本案例的胜负判定见 M5-N 正式汇总，不在本节预告」，在此闭合——组级裁决 = A（经 `_ledger/npair2-map.json` 解盲 = 机制臂 L 胜；decisive_dimension = usable_distinction）；决定性理由引一句（`npair2-03/judgment.json` group_preference 槽 reason_quote 原句照抄，盲标 A/B 原样）：「就是 392 和 329 这对双胞胎，我帮你盯着，别再让它们串门。」——A 组的 recommended 已完整归月（「10 月五笔……小计 5889；11 月两笔……小计 1686」），在此之上还把她此前「这两个数长得太像，我怕后面抄错」的真实担心变成一条可直接挑中发送的区别化选项；四条候选语气彼此可辨、四个方向（收尾/留底/情绪/换题）均可用，B 组仅单条无挑选余地——胜因是区别化选项真实可用，并非候选更多。
- **口径替代注**：M5-N 的 usable 判定按 m5-task.md 自带锚点（v1.1 §5 原文 + 严重错误族定义）自评——N 线 M1/M2/M3 本轮不适用（依冻结协议 §3，无同源判定件可引），不写成「引用了 N 线 M1/M3 判定」；M5-N 汇总件同注。

> M5-N 与 E5-N 是同一六子指标框架的两半，互补不互替、分开呈现不合并计分：子指标 1-5 = 组级结构化质量（本小节实值），子指标 6 = 整组盲评偏好（引用 §9 的 E5-N 读数，metrics.E5.N 槽）。关系详见 m5-vs-e5n-relation.md（五句话口径 + 槽位分离规则 metrics.M5.N / metrics.E5.N 互不覆盖）。

## 9. E5 盲评回复偏好/自然度与可发送性代理评分

**定义**（v1.1 更名，回复级口径）：一次评审三部分——① 回复级 A/B 偏好五选一裁决（A/B/tie/both_unqualified/insufficient_info）；② 单输出自然度 1–5 五档锚点 + 套话冻结清单；③ 可发送性代理分 0–3（3 愿意直接发送 / 2 基本可发 / 1 需明显修改 / 0 不能发送）。H 线机制臂 = CURRENT、基线臂 = DIRECT；N 线机制臂 = L、基线臂 = B1；匿名 A/B 奇偶轮换，两线裁决与统计完全分开、禁止相加。

| 指标 | H 线（12 对） | N 线（12 对） |
|---|---|---|
| 机制臂胜 | 6（12/12 对全判） | 11（首评 12/12 对全判） |
| 基线臂胜 | 6（12/12 对全判） | 1（首评 12/12 对全判） |
| tie（单列，不计分母） | 0（12 对全判） | 0（首评 12 对全判） |
| both_unqualified（单列） | 0（12 对全判） | 0（首评 12 对全判） |
| insufficient_info（单列） | 0（12 对全判） | 0（首评 12 对全判） |
| 机制臂偏好胜率（分母 = 非平局） | 6/12 = 50%（全量） | 11/12 = 91.7%（首评全量） |
| 自然度均值（1–5，附评） | 4.75（L = 4.8333 / B1 = 4.6667，24 份输出） | L = 4.92 / B1 = 4.67（终值口径：npair2-11 B 臂经仲裁 = 5，各 12 份） |
| 可发送性均值（0–3，附评） | L 2.9167 / B1 2.8333（24 份输出，pair01 终值口径） | L = 2.92 / B1 = 2.67（首评，各 12 份） |
| 可发送性 ≥2 率（辅助） | L 12/12 = 100% / B1 11/12 = 91.67%（终值口径） | 24/24 = 100%（首评全量） |
| 套话命中率（辅助） | 0/24（全量） | 0/24（首评全量） |

**H 线 v1.1 全量结果（来源：`e5h-v11-judge-pkg/pair*/judgment.json` 12/12 对 + 内部证据索引 B-3（S62 机械汇总），judge_identity = `native-subagent-blind-judge-v11`）**：12 对全部判完，偏好分布如下（匿名 A/B 经 `_ledger/pair-map.json` 解盲归臂，与附录 A `anon-map-revealed.json` 同源）：

| pair | case_id | 裁决 | 归臂 |
|---|---|---|---|
| pair01 | uv1-E5-001 | A | L（机制臂）胜 |
| pair02 | uv1-E5-002 | A | B1（基线臂）胜 |
| pair03 | uv1-E5-003 | A | L（机制臂）胜 |
| pair04 | uv1-E5-004 | B | L（机制臂）胜 |
| pair05 | uv1-E5-005 | B | B1（基线臂）胜 |
| pair06 | uv1-E5-006 | A | B1（基线臂）胜 |
| pair07 | uv1-E5-007 | A | L（机制臂）胜 |
| pair08 | uv1-E5-008 | B | L（机制臂）胜 |
| pair09 | uv1-E5-009 | B | B1（基线臂）胜 |
| pair10 | uv1-E5-010 | A | B1（基线臂）胜 |
| pair11 | uv1-E5-011 | A | L（机制臂）胜 |
| pair12 | uv1-E5-012 | A | B1（基线臂）胜 |

- **复评抽样（E5-H v1.1）**：3/12 包复评已完成（pair01/pair06/pair11，来源各自 `recheck-judgment.json`，judge_identity = `native-subagent-blind-judge-v11-recheck`）：pair06 全字段一致（preference、决定性维度、双臂自然度/可发送性）；pair01 偏好一致（均为 A）但 B.sendability 单维分歧（首评 2 → 复评 1），经第三评委终裁 = 1（维持复评终值，winner 维持 A，依据 `e5h-v11-judge-pkg/pair01/arbitration.json`）；pair11 偏好一致（均为 A）但 B.naturalness 单维分歧（首评 5 → 复评 4），经第三评委终裁 = 5（维持首评，winner 维持 A，依据 `e5h-v11-judge-pkg/pair11/arbitration.json`）；两处单维分歧均已终裁闭环，无在途仲裁（summary.json `arbitration_pending_pairs = []`）。
- **v1.0 对照**：见内部证据索引 B-5（S67 机械对照，已落盘，12/12 对全覆盖；逐对机械计数：9 对偏好相同、3 对不同——03/10/12；对照口径与 §12 相同——并列陈述、禁纵向相减）。
- **v1.0 参考行（非终值）**：机制臂（CURRENT）胜 5 / 基线臂（DIRECT）胜 5 / tie 2（12 对全判；非平局 10，机制臂偏好胜率 5/10 = 50%，tie 率 2/12 = 16.7%；自然度均值 L 4.5 / B1 4.33）。来源：内部证据索引 B-6（S25 机械汇总，v1.0 口径，per_pair 12 对逐条落盘）。**以 v1.1 为准**（v1.1 全量 12/12 已落盘，见本节上表；v1.0 行仅并列参考，不进主判分母、不与 N 线相加；E5-N v1 判定已作废，其读数不引用）。
- 以上为全量读数（12/12 对），由内部证据索引 B-3（python -I 纯机械计算）汇总落定；E5-N v2 首评已闭合回填（见下条与 §12 S86 记录）；E5-N 复评与 pair01/pair11 复评仲裁均已终裁闭环，读数已按各自落盘件（rejudge / arbitration / summary 校准注）收口，见上两条与 §11、§12。
- **N 线 v2 首评结果（来源：内部证据索引 B-8（E5-N v2 首评终账，S54 机械汇总）+ `e5n-v2-judge-pkg/npair2-*/judgment.json` 12/12 对，judge_identity 随件落盘）**：12 对全部判完（五选一 preference 全落 A/B，both_unqualified/insufficient_info 均 0）；机制臂 L 胜 11 / 基线臂 B1 胜 1 / tie 0（n=12）；非平局 12，机制臂偏好胜率 11/12 = 91.7%；自然度均值 L 4.92 / B1 4.67（终值口径）；可发送性均值 L 2.92 / B1 2.67、≥2 率 24/24 = 100%；套话命中 0/24。匿名 A/B 奇偶轮换经 `_ledger/npair2-map.json` 解盲归臂（逐对 rotation 奇偶规则机械核对 PASS；tally 口径 L2 = 本表 N 线机制臂 L，nline-v2 gen-L2 装配）。复评（npair2-01/06/11，来源各包 `rejudge.json`）胜负 3/3 一致（胜者逐对同首评 A/B/A）：npair2-01/06 全字段一致；npair2-11 自然度 B 臂附评单维分歧（首评 4 → 复评 5，同挑 item:1 槽）经第三评委仲裁终裁 = 5（维持复评，winner 维持 A，依据 `e5n-v2-judge-pkg/npair2-11/arbitration.json`），终值已并入终账——B1 自然度均值首评口径 4.5833 → 终值口径 4.6667（源 `e5n-v2-judge-pkg/_ledger/e5n-v2-recheck-arbitration-tally.json`），L 臂与可发送性各项不受影响；终值口径读数即为现行读数。盲评落盘的 9 条严重错误嫌疑经独立语义复核定性：确证编造 7 条（L2 侧 3/4、基线侧 4/5，两臂坐实率接近）、翻案 2 条——双方都存在编造风险，差异在整组可用选项与判点维度（源：agg 内部证据索引）。

**口径与已知问题（最终口径）**：可发送性是代理评分（代理评委的发送意愿），M5"可用"是语义合格判定——两者独立落盘，不合并计算；E5 自然度 1–5 与 M4 自然度 0–3 是独立口径，并存不互替；防长度/数量偏成文，决定理由仅为长度差异 → 改判 tie。

**代理评委声明**：本节全部结果（偏好、自然度、可发送性）为原生子代理代理评审的代理指标，**不是真人用户调查，不可称为用户发送率**。judge_identity 随每条判定落盘（程序化 = `deterministic-keyword-scorer-v0.1`，语义 = `native-subagent-blind-judge` 实例标识）。

## 10. R 线：既有专项判决引用（只读）

以下五张证据卡为既有专项机制测试读数，判决原文引用、不重评、不并入 M1–M5 统计；与本轮全产品评测是不同证据层。存档路径：`research/refs/experiment_protocol_20261023/long_probe_20261030/`。

### 卡 1 长对话事实保持（FLJUDGE 终判决）

- 来源：`FLJUDGE_VERDICT_20261102.md` 表 L18-25（T25-32 行=L24）、真实保持率 L38
- 双方：parent = r9_slim（旧滑窗机制）；candidate = window_expand_b（M2 扩窗 + 确定性逐字摘录）
- 样本：2 篇长剧 LRE060（40 轮）/ LRE048（32 轮），144/144 判位零失败；保持率 = 机械口径 + miss 亲读复核
- 原文：表格行「**T25-32** | **0.493** | **1.0** | **0.509** | **1.0**」；L38「**真实保持率**：T25-32 段=**1.0**（该段 0 miss）；终态 T40=(150−2)/150=**0.987**。」；L27「parent 复现 LR2 塌陷形态（T25+ 腰斩）；candidate 全程无塌陷。」
- 通俗翻译：超过 24 轮的长对话里，改进后的机制把中后段关键事实 100% 保住，旧滑窗方法同一段只剩约一半（49%–51%），整场结束仍保住 98.7%。
- 使用限制：专项测试口径——测"扩窗+逐字摘录"机制在 2 篇 32–40 轮搬家域长剧上的事实保持。

### 卡 2 跨领域迁移（LD1 域迁移判别批）

- 来源：`LD1_VERDICT_20261102.md` §2 表 L17-23（T25-32 行=L22）、L28-30
- 双方：parent = r9_slim（旧滑窗）；candidate = window_expand_b
- 样本：4 篇新领域长剧 LRE117（40 轮，拼车）/ LRE141（40 轮，职场）/ LRE125（40 轮，餐饮）/ LRE147（32 轮，医疗），304/304 零失败；机械保持率 + 41 条 miss 亲读
- 原文：表格行「**T25-32** | **.323/1.0** | **.512/1.0** | **.624/1.0** | **.568/1.0**」；L29「**T25-32 段真实保持率 = 1.0（四篇全，0 miss）**；终态真实保持 = 425/430 = 0.988。」；L30「parent T25-32 塌陷 0.32–0.62 在四个新域全部复现 → FK-LR2-01 成因（滑窗结构）域无关，修复收益同样域无关。」
- 通俗翻译：换成拼车、职场、餐饮、医疗四个没用过的场景，长对话后段事实同样全部保住；旧滑窗方法同一段只剩 32%–62%。
- 使用限制：专项测试口径——同一"扩窗"机制在 4 篇新领域 32–40 轮长剧上的保持率迁移验证。

### 卡 3 无据确定化压制（W16 未定标记机制）

- 来源：`W16_UNC_VERDICT_20261102.md` 门表 L12（G1 行）、回归篇读数 L21
- 双方：candidate = window_expand_b_norecap_uc（未定标记）；parent = 前代冠军 b_norecap（按单变量结构推定）
- 样本：240/240 零失败；评审 120 判位嵌套 3 篇长剧 LRE117/LRE146/LRE148 各 40（LRE148 为未暴露泛化篇）；severe 以 B3 亲读账为准
- 原文：L12「| G1 severe 亲读 cand < par | **1 vs 6**（judge 原始 1 vs 10；亲读：cand 仅 LRE117_T34 坐实转账，par 6 例含坐实邀约/转账、服务当事实、姥爷错位、编造拆罩+乱码） | **PASS** |」；L21「LRE117、LRE146 两篇候选 severe 均为 **0**（父本 5 / 4）。」
- 通俗翻译：AI 容易把没确认的事说死；加"未定标记"后，同一批对话里这类严重错误从 6 例降到 1 例。
- 使用限制：专项测试口径——测"未定标记"对"未确认状态被说死"类错误的压制；判决自注"不宣称统计显著、不宣称无据确定化已解决"。
- 口径注：judge 原始账（1 vs 10）与亲读账（1 vs 6）差异全在 parent 侧，亲读账为准（评审器已知缺陷：CALL_FAIL + 引语幻觉）。

### 卡 4 输出格式自愈（W17b 契约守卫）

- 来源：`W17B_GUARD_VERDICT_20261103.md` 门表 L15（G1 行）、依据 L26
- 双方：candidate = window_expand_b_norecap_guard（机制层契约守卫）；parent = 前代 b_norecap（按单变量结构推定）
- 样本：144/144 零失败；契约违规口径 = 72 条回复（12 轮级六篇 E189–E208）；judge 初判 + B3 亲读双账
- 原文：L15「| G1 契约违规 ≤ 父本+1 | **2/72 vs 7/72**（父本同批实测 7；W17 同臂曾 4，跨批有波动） | **PASS** |」；L26「离线回放（W17 的 72 条原始回复 + lb_scorer 复评）违规 16→4；live 实测 2/72，且无一条正常回复被改坏（0 破坏）。」
- 通俗翻译：AI 回复偶尔夹带坏掉的格式片段；加"契约守卫"后 72 条回复里坏格式从 7 条降到 2 条，且没有一条正常回复被误改。
- 使用限制：专项测试口径——测"机制层契约守卫"对输出格式违规的确定性修复（剩余 2 例原始不可解析）；六篇均已暴露属机制回归，泛化由 W17c 补。

### 卡 5 简化机制消融（W15N01）

- 来源：`W15N01_ABLATION_VERDICT_20261102.md` 门表 L10-13、判决 L15-18、依据 L21-24、诚实注记 L27-28（行号以文件实际内容为准）
- 双方：candidate = window_expand_b_norecap（冠军扩窗机制删 delta ② recap 段、保留 3600 扩窗）；parent = window_expand_b（消融时 >24 轮域冠军，判决后保留为历史版本；判决原文明确列双臂名，无推定）
- 样本：批 BATCH-W15N-NORECAP-20261102，160/160 零失败（--workers 并行派发）；评审 AUTH-W15N_{A,B}-REV×2 并行 80 位（swap 一致率 3/8）；2 篇长剧 LRE117+LRE146；severe 以 B3 亲读账为准（亲读全部 12 位，`LP_W15N_B3_READRESULT.json`）
- 原文：L10「| T25-32 keep ≥0.95×父本 | 双臂 1.000/1.000（LRE117+LRE146） | **PASS** |」；L12 severe 门「1 ≤ 3（亲读全部 12 位：cand 成立位=LRE117_T22……par 成立 3 位……judge 原始 1 vs 12） | **PASS** |」；L17-18「>24 轮域冠军由 window_expand_b 更替为 **window_expand_b_norecap**（删 delta ② recap 段，保留 3600 扩窗；少一段装配逻辑、少一份 ledger_rules 依赖）」；L21-22「LD1+本批共 6 篇长剧实测 recap 仅在 T34+ 出线且 ≤199 CU（T25-32 判决段全程为空——即消融在该段的输入与父本逐字节相同）」；L23「九维 9/9 全正（小幅但方向一致——recap 段对模型是轻微噪声源而非资产）」；L27「九维差值幅度小（+0.04~+0.25），80 位不宣称统计显著……（简化主张是"无损可删"，不是"recap 有害"）」
- 通俗翻译：在已验证的冠军扩窗机制上做减法——把 recap 段整段删掉，长对话后段关键事实保持率分毫未降（双臂都是 100%），严重错误不升反降（亲读 3→1），九维评价全部持平略正，prompt 成本 303,664 vs 304,550（−0.3%）。证明 recap 段可无损删除，冠军机制由此更替为少一段装配逻辑、少一份 ledger_rules 依赖的 window_expand_b_norecap。
- 使用限制：专项测试口径——测"删 recap 段"消融在 2 篇长剧上的无损性；判决自注九维差值幅度小、80 位不宣称统计显著，且简化主张是"无损可删"而非"recap 有害"；swap 一致率 3/8 偏低（judge 近平位不稳定），已按 main 顺序口径报告；cand 唯一成立 severe 与父本三例同族，属冠军链共性风险（由 W16 未定标记机制承接，见卡3）。

## 11. 已知问题与局限

1. **代理评委非真人**：全部语义判分（M1–M5、E5、E1–E3 语义层）由原生子代理代理评委完成；E5/E4 每处引用带"代理评审，非真人用户调查"声明；可发送性是代理意愿评分，不是真实发送行为。
2. **专项口径**：R 线五张卡为机制专项读数（样本 2–4 篇长剧/批），测的是单机制因果贡献；其数字不外推为全场景综合结论，也并入不了 M1–M5 统计。
3. **UNRATED / pending 清单（落位前实盘，不得静默缺席、不算正确）**：

| 项 | 状态 |
|---|---|
| N 线 E1–E3 语义评审件落盘与并入 | 不适用（本轮仅 H 线评，依冻结协议 §3） |
| 程序化 `uncertain` / 零命中（E2 invalidated 零复现、E3 零错误）语义确认 | H 线已完成（105 包语义复核：翻正 36 / 维持 57 / 判违 12，144 行闭合 pending=0，见内部证据索引 B-1）；N 线 PENDING（随 v2） |
| ≥20% 复评抽样（每指标 ≡1 mod 5）执行与一致率 | 部分：E4 v1.1 复评 6/24 包完成并收口（5 包一致；e4m-006b 自然度 3→2 分歧经第三评委终裁 = 3，维持首评，`arbitration-judgment.json` 已落盘）；E5-H 复评 3/12 包完成并终裁闭环（pair06 一致；pair01 B.sendability 2→1 经终裁 = 1 维持复评终值、pair11 B.naturalness 5→4 经终裁 = 5 维持首评，winner 均维持 A）；E5-N v2 复评胜负 3/3 一致（npair2-01/06 全字段一致；npair2-11 自然度 B 臂 4→5 分歧经仲裁终裁 = 5，维持复评）；M5-N 组级六子指标复评 3/12 包完成并收口（npair2-01/06/11 各包 `m5-eval/m5-rejudge.json`：npair2-01/11 全字段一致、npair2-06 分歧 8 处经第三评委终裁闭环，首评误报 8 条单列，见 §8.1）；其余指标 PENDING |
| E5-N 对称重评（v1.1 全候选口径） | v1.1 v2 重跑首评 12/12 对闭合并已回填（机制臂 L 11 / 基线臂 B1 1 / tie 0，见 §3.2/§9 与 §12 S86 记录）；复评胜负 3/3 一致，npair2-11 自然度 B 臂分歧经仲裁终裁 = 5（维持复评，B1 自然度均值终值口径 4.6667）；v1 的 12 条判定已作废留档，读数不引用 |
| 激活检查表 activation-checklist.jsonl 60 行落盘 | 已完成（2026-11-14：60/60 行落盘，E1–E5 激活率均 100%，低激活 0，见 `activation-summary.md`） |
| M5 组级六子指标（M5-N 补跑） | N 线已回填（见 §8.1，来源内部证据索引 B-9；summary slot_status.M5_N = filled）；复评抽样已完成 3 包（npair2-01/06/11，3/12 = 25%）：npair2-01/11 全字段一致，npair2-06 8 处单维分歧经第三评委终裁闭环（首评误报 8 条单列，见 §8.1）；M5-H 组级评审已完成终账并回填（m5h-six-tally.json；summary slot_status.M5_H = filled，H 线现值见 §3.1/§8/§8.1） |
| 金标缺席题 UNRATED 计数 | 0（本轮在册判定，`accounting.unrated_total = 0`） |

4. **低激活题纪律**：激活检查表未命中故障条件的题记 `low_activation`，不删除、不替换，结果照常进表并单列标记；优势结论不依赖低激活题支撑的分子。
5. **分歧与误报**：语义复评分歧交第三评委终裁；被仲裁推翻的判分记误报、单列计数；某指标分歧率 ≥30% 时该项结果标注"不确定比例"。
6. **词表局限**：程序化层存在召回局限（如"明天"类指代日期漏召回），一律交语义层确认，不以关键词 miss 直接当错。
7. **H 线语义复核特例留痕**（内部证据索引 B-1 §6；三项均不影响 144 行闭合与已回填终值）：① uv1-E3-009__DIRECT 无评审包，按终值规则取程序化 fail 终值（程序化正面命中，证据 `hline-scores/uv1-E3-009__DIRECT.json`）；② uv1-E3-001__CURRENT review.json 的 quote 字段含未转义引号致严格 JSON 解析失败，仅作语法级转义修复（判定内容零改动），已留痕于 evidence_ref（`#syntax-repaired`），如需零修复口径可重派该包复核；③ uv1-E3-012__CURRENT 的 false_positive_suspect 经语义裁决确认成立（unknown_as_certain / confirmed_error），计判违——四臂 9 条 suspects 中唯一确认（其余 8 条判误报）。

## 12. 变更记录

### 部分回填记录（S69，2026-11-14）

- **回填来源（staging 已落盘文件，纯读数、无外部 API、无哈希；S69 时点口径）**：内部证据索引 B-4（M4 v1.1 三维重评，S58 机械汇总）；`e5h-v11-judge-pkg/pair01–12/judgment.json`（E5-H 当时已判 10/12 对，S85 时点已 12/12 全量）及 pair01/pair06 `recheck-judgment.json`；`e4-v11-judge-pkg/e4m-001a/001b/006a/006b/011a/011b/recheck-judgment.json`（E4 复评 6 包）。
- **回填范围**：§3.2 M4 行、§7 结果与复评段、§9 H 线部分单元格与逐对表、§11 复评抽样行。其余单元格仍 PENDING。
- **仍 PENDING（S69 时点口径；其中 pair03/pair11 判定、e4m-012a 复评、内部证据索引 B-5 落盘三项已由 S85 收口；e4m-006b 终裁与 E5-N 首评已由 S86 收口，见下条）**：E5-N v2 对称重评；e4m-006b 自然度分歧第三评委终裁。

### 回填更新记录（S85，2026-11-14）

- **刷新来源（staging 已落盘文件，纯读数、无外部 API、无哈希）**：内部证据索引 B-3（E5-H v1.1 全量汇总，S62 机械汇总，.md/.json 双格式）；`e5-judge-pkg/_ledger/pair-map.json`（归臂总账）；内部证据索引 B-5（v1.0 对照，S67 机械对照）。
- **刷新内容**：① E5-H v1.1 首评 **12/12 对全部完成**（pair03 重跑 = A 胜、自然度定胜负；pair11 = A 胜）——§1 当前状态、§3.1 E5 行、§9 指标表与逐对表全量收口：归臂后 L（CURRENT）胜 6 / B1（DIRECT）胜 6，tie/both_unqualified/insufficient_info 均 0，L 胜率 6/12 = 50%；自然度均值 L = 4.8333 / B1 = 4.6667（总均值 4.75），可发送性均值双臂 2.9167、≥2 率 24/24 = 100%，套话命中 0/24（pair01 仲裁后 B1 终值口径 2.8333 / ge2 91.67，见 §9 现值）。② E5-H 复评口径更正：2/12 包（pair06 全字段一致；pair01 偏好一致但 B.sendability 2→1 单维分歧，后经终裁 = 1（维持复评终值）；复评计数由 S86 更正为 3/12，见下条）。③ e4m-012a 复评不在冻结抽样内（抽样 = 001/006/011），§7 行改为抽样外说明。④ 内部证据索引 B-5 已落盘，§9 补指针（9 对偏好同、3 对异：03/10/12）。
- **仍 PENDING（S85 时点口径；两项均已由 S86 收口，见下条）**：e4m-006b 自然度分歧第三评委终裁（在飞）→ 已落盘收口，**终裁 = 3（维持首评，CONSISTENT）**，v1.1 双臂 24/24 全三维饱和统计不变（来源 `e4-v11-judge-pkg/e4m-006b/arbitration-judgment.json`）；E5-N v2 全组评审（对称重评）→ 首评 12/12 对已闭合并回填（复评与仲裁在飞），见 S86 记录。

### 回填更新记录（S86，2026-11-14）

- **刷新来源（staging 已落盘文件，纯读数、无外部 API、无哈希）**：`e5n-v2-judge-pkg/_ledger/e5n-v2-firstpass-tally.json`（E5-N v2 首评终账，S54 机械汇总，12/12 对）+ `e5n-v2-judge-pkg/npair2-*/judgment.json`（五选一类别/≥2 率/套话逐对机械计数）+ `e5n-v2-judge-pkg/npair2-01|06|11/rejudge.json`（E5-N 复评 3 包）；`e5-judge-pkg/_ledger/pair-map.json` rotation + `e5h-v11-judge-pkg/pair08|pair12/judgment.json` preference（E5-H 归臂机械复核）；`e5h-v11-judge-pkg/pair01|pair06|pair11/recheck-judgment.json`（E5-H 复评 3 包）；repo-landing/summary.json supplementary `metrics.M1.H.fact_level_by_arm`（M1 事实级分臂机械重算）；`e4-v11-judge-pkg/e4m-006b/arbitration-judgment.json`（终裁件）。
- **刷新内容**：① **E5-N v2 首评回填**（§3.2 E5 行、§9 指标表 N 线列与首评来源段）：机制臂 L 胜 11 / 基线臂 B1 胜 1 / tie 0（n=12 全判，五选一类别 both_unqualified/insufficient_info 均 0）；非平局 12，机制臂偏好胜率 11/12 = 91.7%；自然度均值 L 4.92 / B1 4.58（首评口径）；可发送性均值 L 2.92 / B1 2.67、≥2 率 24/24 = 100%；套话命中 0/24。tally 口径 L2 = 本报告 N 线机制臂 L（nline-v2 gen-L2 装配，逐对 rotation 经 `_ledger/npair2-map.json` 奇偶规则机械核对 PASS）。**E5-N 复评 3/3 偏好一致**（npair2-01/06/11 `rejudge.json` 落盘，胜者逐对同首评 A/B/A；npair2-01/06 全字段一致；npair2-11 自然度 B 臂附评单维分歧：首评 judgment.json B nat=4 → 复评 rejudge.json B nat=5，同挑 item:1 槽，经第三评委仲裁终裁 = 5（维持复评，winner 维持 A）——PKG-LOG/judgments.jsonl 的"复评全一致"注记与首评源件逐字段读数不符，本报告以落盘源件为准）。② **E5-H 逐对表 pair08/pair12 归臂对调**（机械复核：pair08 pref B → rotation B=CURRENT → L 胜；pair12 pref A → rotation A=DIRECT → B1 胜；此前两行互串，对调后总数 6/6 不变）。③ **E5-H 复评计数 2/12 → 3/12**（pair01/pair06/pair11）：pair06 全字段一致；pair01 偏好一致但 B.sendability 2→1、pair11 偏好一致但 B.naturalness 5→4，两处单维分歧经第三评委终裁闭环：pair01 终值 = 1（维持复评终值）、pair11 终值 = 5（维持首评），winner 均维持 A。④ **N 线 M1/M2/M3 转不适用**（本轮语义评审仅 H 线，依冻结协议 §3 与 summary.json slot_status not_applicable，非待办）。⑤ **M1 事实级分臂回填**（§4）：H 线 L/CURRENT 24/31（77.42%，not_used 7 / used_wrongly 0）/ B1/DIRECT 21/31（67.74%，not_used 9 / used_wrongly 1），四臂合计 124 = 100/23/1/0 不变。⑥ **e4m-006b 终裁收口登记**（终裁 = 3，维持首评，CONSISTENT）。⑦ **证据引用修复**：scores-v1.1.csv/judgments-v1.1.jsonl/summary-v1.1 引用名对齐落盘名 scores.csv/judgments.jsonl/summary.json；showcase-case.md 链接改仓库相对路径；agg/* 内部汇总件引用改"内部证据索引 B-N"并新增附录 B。
- **仍 PENDING**：E5-N v2 npair2-11 自然度 B 臂附评分歧仲裁（首评 4 vs 复评 5，若改判则 B1 自然度均值 4.58（首评口径）随之单列更新）→ **已终裁收口：终值 = 5（维持复评，winner 维持 A），B1 自然度均值终值口径 4.6667，见 §9 现值（源 `e5n-v2-judge-pkg/_ledger/e5n-v2-recheck-arbitration-tally.json`）**；E5-H pair01/pair11 复评单维分歧仲裁 → **均已终裁收口：pair01 B.sendability 终值 = 1（维持复评终值）、pair11 B.naturalness 终值 = 5（维持首评），winner 均维持 A，见 §9 与 summary.json**；M5-H/M5-N 组级六子指标（M5-N 已由 S87 回填收口，见下条；M5-H 组级终账已聚合 m5h-six-tally.json、summary M5_H 槽已回填收口（slot_status filled），H 线现值见 §3.1/§8/§8.1）；M2 门级分臂诊断、M3 备选/全组/错误族分列（UNRATED 分臂计数已回填：H 线 = 0，N 线不适用，见 §6）、COT/LEGACY 的 M5/E5 分列；金标缺席题 UNRATED 计数（= 0，`accounting.unrated_total = 0`，见 §6/§11）。

### 部分回填记录（S63，2026-11-14）

- **回填来源（staging 已落盘文件，纯读数、无外部 API、无哈希）**：内部证据索引 B-1/B-2（H 线 E1–E3 终值，S43 机械汇总）；内部证据索引 B-6（E5-H v1.0 参考行，S25 机械汇总）；`evidence-cards.md` 卡 5（W15N01，摘自存档判决原文）；`activation-summary.md`（激活检查 60/60）。
- **回填范围**：§3.1 M1–M3 行、§3.3 COT/LEGACY 分列、§4 前置激活段与 H 线列（题级合格率 / irrelevant_misuse / unjudgeable）、§5 主判与 unjudgeable、§6 首选通过率、§9 v1.0 参考行、§10 卡 5、§11 激活/程序化语义确认/E5-N 行与特例留痕第 7 条。
- **仍 PENDING**：H 线 M1 事实级分臂率、M2 门级分臂率、M3 备选/全组/错误族分列与 UNRATED 分臂计数；E5-N v2 对称重评；E5-H pair01 复评仲裁（已闭环：pair01/pair11 终裁均已落盘，见 §9 / summary.json）；summary 终版收口。

### 回填更新记录（S87，2026-11-14）

- **刷新来源（staging 已落盘文件，纯读数、无外部 API、无哈希）**：内部证据索引 B-9（`e5n-v2-judge-pkg/_ledger/m5-six-tally.json`，M5-N 六子指标终账：子指标 1–5 聚合 + 子指标 6 引用 11/12，python -I 机械聚合）+ `e5n-v2-judge-pkg/npair2-*/m5-eval/` 首评产出 12/12（三命名并列：m5-result.json ×6 / m5-verdict.json ×5 / m5-six-metric-result.json ×1）+ `npair2-01|06|11/m5-eval/m5-rejudge.json`（复评 3 包）+ `npair2-06/m5-eval/m5-arbitration.json`（终裁件）+ `_ledger/npair2-map.json`（归臂总账）+ `repo-landing/summary.json` metrics.M5.N 槽与 slot_status（终值回写后实盘核对）；`e5h-v11-judge-pkg/_ledger/m5h-six-tally.json`（M5-H 终账件，H 线状态句如实引用）+ `agg/m5-group-cases.md`（组级判例卡五判例）+ `agg/m5-vs-e5n-relation.md`（槽位分离口径件）。
- **刷新内容**（枚举六项）：① §8.1 新增 + §8 定义表 N 线列回填（H 线两列保持 PENDING）；② §3.2 M5 行回填（子指标 1 双臂实值 + 8.3pp 机械差值 + §8.1 索引）；③ §11 pending 清单新增 M5 行（M5-N 已回填、M5-H 回填在途如实句）；④ §5.1 showcase 预告闭合说明（uv1-E5-003 = npair2-03 组级裁决 A → 机制臂 L 胜，在 §8.1 判例引用闭合，showcase-case.md 本体不改）；⑤ 附录 B 新增 M5-N 汇总件条目（B-9）；⑥ §1 当前状态句补「M5-N 组级六子指标已回填」。
- **仍 PENDING**：M5-H 组级六子指标 summary 槽回填（组级评审已完成终账 `m5h-six-tally.json`，metrics.M5_H 槽回填并行收口中，收口前 §3.1/§8 定义表 H 线列保持 PENDING 不预填）→ **已回填收口：slot_status.M5_H = filled、metrics.M5.H 28 键落盘，§3.1 M5 行与 §8 定义表 H 线列 9 格已回填现值，见 §8.1**；COT/LEGACY 的 M5 分列（§3.3，随 H 线）；M2 门级分臂、M3 备选/全组/错误族分列（S86 未收口项顺延）。

### v1.0 → v1.1 对称重评声明

- **性质**：v1.1 是评分规则升级（指标映射：E1→M1、E2→M2、E3→M3、E4→M4、E5 更名、M5 新设），**不是改题**——60 行登记表、case_id、金标字段（含 31 条 must_use 基线）、pair 轮换、隔离规则全部不变。
- **对称性**：v1.1 生效后，H 线四臂与 N 线三臂的全部已有判分按同一新规则重评，重评使用同一批原始输出落盘（不重新生成）；同一修正应用于全体比较臂，无混算。
- **数据保留**：v1.0 判分原样留档（v1.0 文件集）；v1.1 重评落盘为现行落盘文件集（judgments.jsonl / scores.csv / summary.json），不覆盖 v1.0。
- **并列报告**：本报告主表为 v1.1 口径；v1.0 口径表随 scores-v1.0/summary-v1.0 并排可查。禁止拿两把尺子的分数纵向相减或画"连续提升"图——规则变更只算尺子校准，不算候选收益。
- **一轮纪律**：冻结集按 v1.1 只重评一轮，禁止调锚点直到赢。

## 附录 A：选题规则与配额偏差

**选题纪律**：选题规则在查看任何臂输出之前固定（选题代理只读题面、Gold 侧车与 plan 结构，未打开任何臂输出文件）；每指标 12 题按确定性排序键抽取（如 E1 按 must_use_facts≥2 优先 + required 断言数×100+最大距离降序），防任意挑选；同一 story 在同一指标内 ≤2 题。

**配额偏差（如实登记，不凑数）**：
1. E1 scenario_type：preference 仅 1 个候选、important_date 仅 2 个（全池 122 候选即如此分布），最终 early_promise 6 / important_person 3 / important_date 2 / preference 1。
2. E2：51 个候选中无独立"关系变化"类作废事件，其 3 题配额并入 closed_or_pending（最终 cancel 3 / reschedule 3 / closed_or_pending 6）。
3. E3：旧资产无 mixed_real_draft 形态题面，3 题额度转 unconfirmed_action（最终 unconfirmed_action 6 / unknown_as_certain 3 / private_note_leak 3）。
4. E1 "must_use_facts ≥2 条/题"：满足 9/12，其余 3 题该轮 Gold 应使用事实仅 1 条，如实登记。
5. uv1-E2-001：该轮无 asserted 替换 atom，`latest_valid_state.fact` 以 episode feedback 口径登记，评审阶段可校正、校正须留痕。
6. 48 条真实题的 conversation_history / incoming_message 以指针登记（episode.json 相对路径 + turn case_id），执行侧装配时由指针展开填充；`latest_valid_state` 携带 atom_id 追溯增补键。

**来源与合规**：48 题源自仓库既有公开合成故事集 stories_v2（exposure 全部 `real_user_material: false`，登记 public_ok）；12 题为本轮新合成（synthetic_native），逐题内嵌合成声明。E5 匿名标签按 pair 奇偶轮换（12 对 6/6 平衡），臂映射仅存总账，公开侧以解匿名对照 `anon-map-revealed.json` 呈现。

**完整性登记口径**：文件数 + 字节量（本报告不含任何哈希）。

## 附录 B：内部证据索引（B-1–B-9）

公开仓库树不含下列内部机械汇总与评审证据件；正文所引"内部证据索引 B-N"即指下列 staging 落盘件（随内部归档迁移，完整性口径 = 文件数 + 字节量）：

| # | 内部落盘件 | 内容 | 机械汇总批次 |
|---|---|---|---|
| B-1 | agg/HLINE-SEMANTIC-SUMMARY.md | H 线 E1–E3 语义复核终值汇总（144 行闭合 pass 77/fail 67；§2 分指标读数、§6 特例留痕） | S43 |
| B-2 | agg/hline-final-scores.csv | H 线 144 行终值分数表 | S43 |
| B-3 | agg/e5h-v11-summary.md / .json | E5-H v1.1 全量 12 对机械汇总 | S62 |
| B-4 | agg/e4-v11-summary.md | M4 v1.1 三维重评机械汇总 | S58 |
| B-5 | agg/e5h-v10-vs-v11.md / .json | E5-H v1.0/v1.1 对照（9 对偏好同、3 对异） | S67 |
| B-6 | agg/summary-partial.json | E5-H v1.0 参考行（per_pair 12 对逐条） | S25 |
| B-7 | hline-semantic-pkg/*（105 包） | H 线语义复核评审件 | S43 |
| B-8 | e5n-v2-judge-pkg/_ledger/e5n-v2-firstpass-tally.json | E5-N v2 首评终账（12/12 对机械汇总） | S54 |
| B-9 | e5n-v2-judge-pkg/_ledger/m5-six-tally.json | M5-N 六子指标终账（子指标 1–5 聚合 + 子指标 6 引用 11/12；per_pair 12 对 + 复评对照终值） | S87 |
