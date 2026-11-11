# OLD_STORY_FINAL_DISPOSITION — Phase 2 第二工作包：45 篇旧题候选逐篇审核收口

> 日期：2026-11-08 ｜ 审核：AI auditor (Phase2 batch audit, session 2026-11-08)（AI 审核，非人工，如实署名）
> 输入：`phase1/DATASET_INVENTORY.csv` eligible_for_reuse=true 的 45 篇（EXPOSED 旧题，允许读取）。
> 方法：每篇通读 episode.json 全对话 + reference.json 卡 + timeline.json，对照 GOLD sidecar（GOLD_<wave>.json）与 gold_overlays（审核前为空）；机械层全量核验（episode SHA 对账、sidecar 1,260 条原子中全部 1,204 条 message 引文逐字回验、timeline/message_index 一致性、sidecar↔reference 字段 diff）；人工层按指导书 §2.1 十项检查逐篇判定。**全程零模型调用。**
> 产出：`PHASE2_OLD_STORY_AUDIT.csv`（45 行）、`GOLD_CONFLICT_RESOLUTION.csv`（93 行）、`gold_overlays/E065_gold_overlay_v1.json`、`gold_overlays/LRE121_gold_overlay_v1.json`、本文件。
> 硬约束遵守：未修改任何既有文件；未读取 benchmark/b_line/ 正文；overlay 全部落在 phase2/gold_overlays/；发现问题如实记 REPAIR/N-A，未凑数放水。

---

## 1. 四类决策统计（45/45 审毕）

| 决策 | 篇数 | 明细 |
|---|---|---|
| ACCEPT | 42 | stage2 全部 8 篇、stage3 全部 4 篇、stage4 除 E065 外 6 篇、wave3 全部 18 篇、wave3_ext 除 LRE121 外 7 篇 |
| ACCEPT_WITH_NA | 1 | E021（N/A 位=E021_T07，见 §3） |
| REPAIR_OVERLAY | 2 | E065（T20 账目陷阱卡）、LRE121（T23 契约残留），overlay 已落盘 |
| REJECT | 0 | 无 — 无一篇达到淘汰线（无编造、无跨人物泄底、无致命时间断裂） |

**机械层全量结果**：45 篇 episode.json SHA256 与盘点账全对；1,204 条 message 引文逐字命中对应轮 input.messages（0 失败、0 越界）；KB 引文全命中；timeline/message_index/speaker/ts 与 episode 零不一致；时钟逐轮单调；required_this_turn 原子 0 例自引当前轮；sidecar↔reference 原子字段 diff 全库仅 1 处（LRE121_T23 may_decline，裁决见 §4）。

## 2. 来源冲突裁决统计（93 处 = LRE117 13 + LRE121 40 + LRE146 40）

| 裁决结果 | 处数 | 说明 |
|---|---|---|
| 裁决完毕（有明确 final_value） | 93/93 | 无一例 N/A 降级 |
| 其中：老批次 schema 缺键、非语义分歧 | 85 | LD1 缺 may_decline/intent 键（LRE117 13 轮）；W14 缺 private_guidance/protected_expressions 键（LRE121/LRE146 各 40 轮中的非 intent 轮）。当前链五方逐字一致（W16/W18="reference.json 逐字派生"，W19_CROSS="W18 逐字段拷贝不改写"，built_at 2026-11-03），final=修订链值 |
| 其中：reference 卡 intent 字段过期（episode input 有显式 intent） | 7 | LRE117_T04/T11/T15/T24/T28 + LRE121_T04/T07。final=W19_CROSS 显式 intent（与 episode.json input.intent 逐字一致，sidecar 已取该值 intent_source=card） |
| 其中：reference 卡 may_decline 过期 | 1 | LRE121_T23：卡面 True vs episode input False。final=False（候选可见输入为准，sidecar 已对齐）；由此暴露的 acceptable_actions"婉拒"残留由 LRE121 overlay 修复 |

要点：**93 处冲突没有任何一处需要改动运行时 gold 的值**——sidecar 在全部实质分歧处已经跟随修订卡/episode input；冲突实质是老批次（W14/LD1）与现行链的年代差 + reference.json 8 个字段的溯源陈旧。reference.json 不可改（硬约束），其陈旧字段仅作溯源注记。LRE117 本身不在 45 篇复用清单内（eligible_for_reuse=false），其 13 处冲突按任务要求一并裁决入库。

## 3. 两个 overlay（REPAIR_OVERLAY 决策的修复内容）

1. **E065_T20（账目陷阱卡反向缺陷）**：现卡 current_action 写"验算确认：3600×3+3800=14600+3800=15200 ✓ 确认无误"（算式自身二次加错），原子 S065_F15 把"到账15200"坐实为 asserted——与该题已登记裁决（勘误 E065_T21：算术挑战不计胜负、当场抓错记 factuality 正向）及 E164_T05/LRE128_T35"gold 不背书错误算术"先例直接冲突，会惩罚应得正向的抓错候选。overlay 按 E164_T05 范式改写为陷阱口径：申报与算式不符登记为 unknown（status=unknown、relation=到账申报（账目陷阱）），forbidden 双向禁背书 15200/14600，unknowns 补租金口径与到账实际值。base_sha256=6387266e…e6b8（实测=盘点账）。
2. **LRE121_T23（契约残留）**：episode input may_decline=False（不可拒），sidecar may_decline 已正确取 False，但 acceptable_actions 第三项仍是 may_decline=True 时代的"婉拒并建议她改约周中"——误导语义评审把违约拒绝当合法选项。overlay 将三路动作改为"应下并确认会合 / 答应并细化流程（依 T24 剧情九点五十会合-十点二十接种-留观-早茶）/ 答应并打趣早茶报账"。base_sha256=4c8e3ed7…0274（实测=盘点账）。

两个 overlay 均为独立 V1 JSON（version=gold_overlay_v1），不触碰任何既有文件；未加载 overlay 的旧口径仍按原样复现。

## 4. 每专题实际获得篇数（对照指导书 §1.2 旧题配额）

盘点口径（primary_topic 计数）与配额完全一致：T01=18 / T02=3 / T04=4 / T05=2 / T07=1 / T08=3 / T09=14，合计 45，零 REJECT/淘汰，**配额名义上全额保持**。审核后按"是否真测该专题"的真实测度注记如下：

| 专题 | 配额 | 审核后实得 | 注记 |
|---|---|---|---|
| T01 日常接话 | 18 | 18（名义）／约 11-13（强代表） | E033/E037 之外的 stage2 篇＋wave3 全部 T01 篇多为关键词兜底（annotation_confidence=low），其中 E157/E164/E183/E208 实为 T07 口径核实链、E189/E190/E195/E199 实为 T09 目标启停链；作 T01 判尺可用（接话自然度），作 T07/T09 判尺时按 secondary_topics 取 |
| T02 调情/拉扯 | 3 | 0 强代表（E032/E039/E042 三篇均弱） | 三篇的"渐暧昧"仅存于 ME 私有 kb 或尾轮轻拉扯，对话面是事务/组队/改约——**T02 旧题实际空缺，建议新题补齐 ≥2 篇强 T02（推进-退让节奏在对话面可见的）** |
| T04 主动推进/邀约 | 4 | 4（E024/E027/E031/E140 全部高保真，E140 为组织链范本） | 达标 |
| T05 情绪理解/安慰 | 2 | 2（E037 关心具体化、E041 裁员陪伴＋合法暂停；E068 陪护可作 T05 辅助） | 达标且质量高 |
| T07 事实/归属/不确定性 | 1 | 1（E061 模范篇）＋4 篇 T01 挂名实为 T07（E157/E164/E183/E208） | 实际弹药充足；E061 有 9/24 轮程序层无原子（语义卡面在场），选用时按 partial 口径登记 |
| T08 记忆/KB | 3 | 3（E034 更正链、E166 多线程、E191 对账） | 达标 |
| T09 长程/状态变化 | 14 | 14（6 篇 stage4＋8 篇 LRE 长剧；E065 经 overlay 后可用） | 全部为 strong；见 §5 pilot 清单 |

## 5. T09 旧题清单（EXPOSED 且 ACCEPT*，Phase 2 pilot 用）与推荐

14 篇全部合格：E063、E064、E065(overlay 后)、E066、E067、E068、LRE121、LRE125、LRE127、LRE138、LRE141、LRE145、LRE146、LRE147。

**Pilot 2 篇推荐**：
1. **E063**（stage4，24 轮，学习成长/驾考）：13/24 轮有原子断言，全状态机（挂科→改期→换教练→科二过→科三改期→过→科四），日期全部验算一致（12-05/12-26/1-09 均为周六）；无血缘同池问题，自包含，是 24 轮域最干净的代表。
2. **LRE121**（wave3_ext，40 轮，医疗健康）：40/40 全覆盖原子，双旧事复活陷阱（T33/T36）显式标注，取消-改期-代陪-纠错链最完整；overlay 修复 T23 契约残留后即为最强 40 轮代表（注意 pilot 需加载 overlay）。

备选：LRE145（32 轮，三口径时间+过期消息陷阱，冲突史干净）。**不建议** pilot 首选 LRE125：其与 E125 同属 LIN-E125 且两者都在 45 篇池内（见 §6-3）。

## 6. 配额影响与新题补齐建议

1. **T02 实际空缺**是本次审核唯一的配额实质影响：3 篇挂名篇（E032/E039/E042）建议降级为 T04/T09 辅助使用，新题批次按 NEW_STORY_SPEC_V1 补 ≥2 篇强 T02（拉扯节奏须在对话面可见，不能只写在私有 kb 里）。
2. **T01 名义足额但稀释**：18 篇中约 5-7 篇实为 T07/T09 链（Q1 分类债的延续）。不阻塞 Phase 2（同一回复可落多专题，rubric §0.4），但 Phase 3 扩量前建议完成 Q1 式逐篇归位复核。
3. **同池血缘对**：E125 与 LRE125 双双入选（LIN-E125，前者 12 轮为后者 T1-T12 的逐字前缀）。 allocation 时必须分拆到不同 split/阶段，或在同批使用时只计其一的 T1-T12 判位，避免同题双计。其余 LRE 与其 E 源（E117/E121/E127/E138/E141/E145/E146/E147）均只有一个在池内，无此问题。
4. **人名复用（背题/混淆风险，新题批次需错开）**：ME"阿灿"×4（LRE141/LRE145/LRE146/LRE147）；ME"阿树"×2（E140/E191）；HER"阮清"×2（E183/E189）；HER"唐棠"×2（E208/LRE146）；"小满"跨三篇（E117 配角、LRE127 覃小满、LRE141 小满）；ME"路遥"（E212）与 HER"路遥"（LRE138）跨性别复用。建议新题启用唯一人名池并在 validate 工具里加跨篇重名检查。
5. **未登记勘误的两个 watch item（判分口径注记，非缺陷）**：E125/LRE125_T07"加两瓶大饮料凑低消"与 11×128=1408>1280 低消冗余（判分不应惩罚指出冗余的候选）；E068_T09→T10"听你的"预设结构（卡面已含护工建议动作，预设成立）。

## 7. 逐篇一览（决策/一句话理由，全量明细见 PHASE2_OLD_STORY_AUDIT.csv）

| 篇 | 决策 | 一句话理由（关键轮引用） |
|---|---|---|
| E021 | ACCEPT_WITH_NA | T07 为 W04-02 已裁决 material_defect 位（不记臂错、双口径），对话"周二球"与周三时钟残余张力按登记口径列 N/A |
| E024/E026/E027/E031 | ACCEPT | 邀约/改期/落成链完整；E026_T07、E031_T10 意图轮有卡有原子 |
| E032/E039/E042 | ACCEPT | 故事可用但 T02 信号弱（渐暧昧仅在私有 kb/尾轮）——专题债 |
| E033 | ACCEPT | 量房-材料单-验收事务链完整；T01 为兜底分类 |
| E034 | ACCEPT | 3月14号更正链+T12"错的是我"自嘲与 T06 更正自洽（非矛盾） |
| E037/E041 | ACCEPT | T05 高保真（挪课/热身具体关心；裁员陪伴+T06 合法暂停） |
| E061 | ACCEPT | T07 模范篇；24 轮中 9 轮无原子断言（语义卡面/unknowns 在场），按 partial 口径登记 |
| E063/E064/E066/E067/E068 | ACCEPT | 长程状态机+更正链全部闭环（E067 base"2万45"看岔、E064 拆线"回医院拆别自己动手"） |
| E065 | REPAIR_OVERLAY | T20 卡面背书错误算术"确认无误"，与已登记裁决及 E164_T05 先例冲突（§3-1） |
| E117/E122/E125/E131/E140/E157/E164/E166/E170/E183/E189/E190/E191/E195/E199/E207/E208/E212 | ACCEPT | wave3 全部 12 轮全覆盖原子；E164_T05 陷阱卡验证为正确范式；E125_T7 低消冗余记 watch item |
| LRE121 | REPAIR_OVERLAY | 40 轮状态机+双陷阱最完整；T23 acceptable_actions"婉拒"与 may_decline=False 残留冲突（§3-2） |
| LRE125 | ACCEPT | T14 双重记账自纠→T21 旧价陷阱→T26/T27 旧账回扣（132/鳜鱼）闭环；A07 勘误=T30 有意记忆回扣的台账观感瑕疵 |
| LRE127/LRE138/LRE141/LRE145/LRE146/LRE147 | ACCEPT | 边界/口径/谣言/预案四类长链全部自洽；LRE146 T20 未坐实"网上不保险"（W14M02 先例位处理正确） |

## 8. 诚实声明与局限

- 本审核为 AI 全量通读+程序核验，非人工独立复核；所有判决引用了可查验的具体轮位/原子/账目数字，供 Phase 2 验收会或人工抽检复核。
- gold_available=partial 的篇目（stage2/3/4 多数）存在程序层无原子断言的轮位——这些轮语义卡面（acceptable_actions/forbidden/unknowns/card_extras）完整在场，语义评审可判，程序层按 rubric"gold 缺席诚实登记（unrated）"处理，不是"不可判"。
- E021_T07 之外的 44 篇未发现新的时间/人物/状态断裂；LRE 八篇的 2026-11→2027-03 跨年时钟全部单调自洽。
- 未读 benchmark/b_line/ 正文；未调用任何模型；除 4 个产出文件外未写入任何路径。

---

## 9. §EXPOSED_RECLASSIFICATION — 16 篇 EXPOSED 兜底题重分类核对（追加节，2026-11-08）

> 背景：Phase 1 盘点出 **136 篇 T01 低置信兜底题**（annotation_basis=fallback、annotation_confidence=low），
> 其中 **120 篇 SEALED 不动、16 篇 EXPOSED 可查看重分类**（指导书 §2.3）。本节核对 Phase 2 是否落实。
> 方法：以 `phase1/DATASET_INVENTORY.csv` 实读拆分（T01 兜底 136 = SEALED 120 + EXPOSED 16），16 篇逐一与
> `PHASE2_OLD_STORY_AUDIT.csv`（45 篇审核）对账。**核对会话（AI，非人工）未重复审任何一篇**：16/16 均已在
> 45 篇审核内，按任务规则直接引用 audit 的 reviewed_topic / topic_confidence / decision_reason，与 45 篇审核同尺度。
> EXPOSED 清单：E033、E117、E122、E125、E131、E157、E164、E170、E183、E189、E190、E195、E199、E207、E208、E212。

| episode_id | 原标 | 复审专题（audit reviewed_topic 原文） | 置信度 | 依据一句话（缩自 audit decision_reason） | 决策 |
|---|---|---|---|---|---|
| E033 | T01（低置信兜底） | T01(兜底)——实为事务决策剧 | low | 量房改期/材料单改版/验收链完整，T06 意图轮（材料方案+37万预算）有卡有原子 | ACCEPT |
| E117 | T01（低置信兜底） | T01(数字事务接话) | low | 拼车人数/费用/时间三连更正+小满退出，全库更正链范本 | ACCEPT |
| E122 | T01（低置信兜底） | T01(互怼日常) | low | 打卡监督语态自洽，T05 暂停/T08 降档恢复意图轮齐全，跨零点时钟单调 | ACCEPT |
| E125 | T01（低置信兜底） | T01(会务接话) | low | 88→128 记岔更正+低消/定金链清晰；T07"1408 已超低消再加饮料"冗余记 watch item | ACCEPT |
| E131 | T01（低置信兜底） | T01(兜底)——实为T07口径核实链 | low | 三源信息打架→两路核实→口径一致进山，测口径/不确定性 | ACCEPT |
| E157 | T01（低置信兜底） | T01(兜底)——实为T07口径链+T04钩 | low | 补贴政策五连转全落纸，T10"抵630=600+30"自洽，T09/T12 火锅邀约追账 | ACCEPT |
| E164 | T01（低置信兜底） | T01(兜底)——实为T07账目剧 | low | 80→72.5→754÷24 陷阱（卡面明标且 forbidden 禁背书，卡面正确），算术全验算成立 | ACCEPT |
| E170 | T01（低置信兜底） | T01(兜底)——T07书面证据链显著 | low | 合同条款引用→复尺漏项认责→变更单/改约费/责任单落纸，工期链自洽 | ACCEPT |
| E183 | T01（低置信兜底） | T01(兜底)——T07口径核实 | low | 临界值口径冲突（妈 vs 同事）→医生裁决，报告数字一致（HER"阮清"与 E189 重名） | ACCEPT |
| E189 | T01（低置信兜底） | T01(兜底)——T09目标启停 | low | 内转备考暂停→A/B 方案→十二月批次重启，T07 劝阻动摇轮真实 | ACCEPT |
| E190 | T01（低置信兜底） | T01(兜底)——T09/T07更正链 | low | 科目填错→截证→撤回→改回→审核过，截止时间"还有一天半"自洽 | ACCEPT |
| E195 | T01（低置信兜底） | T01(日常接话+合规) | low | 叫停→备案→过渡点→寒潮轮值→跨年，T12 账目 45×4=180 平摊成立 | ACCEPT |
| E199 | T01（低置信兜底） | T01(日常+陪伴) | low | 买房线暂停→复查良性→顺序反转，T06"你也别顺着我瞎想"边界好 | ACCEPT |
| E207 | T01（低置信兜底） | T01(日常+改期) | low | 暴雨取消→蛋糕/帐篷免费改期→重办成功，账目 158+120+85=363 自洽 | ACCEPT |
| E208 | T01（低置信兜底） | T01(兜底)——T07口径核实 | low | 5:40/6:00/7:20 三口径→师傅电话定案→车牌 739→兜底预案，时间轴全自洽 | ACCEPT |
| E212 | T01（低置信兜底） | T01(兜底)——T07书面证据 | low | 督学缩水→差异清单三条→联署→盖章书面确认（ME"路遥"与 LRE138 HER 重名） | ACCEPT |

**核对结论**：
1. **16/16 已被 45 篇审核覆盖，无遗留**——不存在需要新开审核的 EXPOSED 兜底题；全部 16 篇 decision=ACCEPT，无一篇因兜底分类本身被淘汰。
2. 重分类以"复审专题注记"落地：登记主专题全部维持 T01（`DATASET_V1_FINAL_ALLOCATION.csv` primary_topic 未改，账本不动）；真实测度注记 = 6 篇 T01 日常接话成立（E117/E122/E125/E195/E199/E207）、7 篇实为 T07 口径/账目链（E131/E157/E164/E170/E183/E208/E212）、2 篇实为 T09 目标/更正链（E189/E190）、E033 为事务决策剧（T06 意图轮为锚）。作 T01 判尺可用；作 T07/T09 判尺按 secondary_topics 与注记取用（与 §4/§6-2 口径一致）。
3. **120 篇 SEALED 兜底题零接触**：本核对仅用盘点 CSV 元数据计数，未读取任何 SEALED 正文；未改任何 gold/正文/分区/账本。逐篇归位复核按 §6-2 留 Phase 3 扩量前。
