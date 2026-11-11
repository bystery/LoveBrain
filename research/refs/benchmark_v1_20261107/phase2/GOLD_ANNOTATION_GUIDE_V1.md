# GOLD_ANNOTATION_GUIDE_V1 — Benchmark V1 Gold 标注规范定稿

> 版本：**V1 ｜ 日期：2026-11-08 ｜ 首批 38 篇实战后定稿**（B1–B6 六标注员 488 卡、
> 1350 原子、quote 全量核验通过；三审计 C1–C3 与两修复 R1/R2 的裁决已吸收）。
> 取代 `phase2/ANNOTATOR_BRIEF.md` 的规范部分（该简报保留为流程/隔离简报；口径冲突以
> 本文件为准）。消费契约：`benchmark/stories_v2/GOLD_SCHEMA.md`（sidecar 对齐契约，
> 不改判定语义）；结构校验器：`tools/validate_story_v2.py`（本文件引用其 L204/L230 规则
> 为硬门）。卡片原子结构细则见 `ATOMIC_ASSERTION_GUIDE_V1.md`；专题判别力要求见
> `TOPIC_RUBRIC_V1.md` §2。

---

## 0. 适用范围与角色纪律

0.1 本规范适用于 wave4_dev 及后续批次 Gold Annotator（角色 B）。角色隔离：不得读取任何
候选在这些故事上的运行结果/失败案例/评审点评（logs/、candidates/、判词）；开工登记
`GENERATION_ISOLATION_LEDGER.json`（annotator 项）。Gold 不针对任何 harness 的已知弱点
定向加严（防偏向）。

0.2 每篇产出三件：`benchmark/stories_v2/<wave>/<EID>/reference.json`（turn 级卡）、
`audit.md`（标注侧审计草稿）、`exposure.json` 更新（status → `annotated_pending_audit`；
补 domain / primary_topic / lineage_group_id / frozen_at）。

0.3 **卡与正文矛盾=最大事故**：每个原子 quote 必须能在 episode.json 对应轮 messages 里
逐字找到（自检：遍历原子 → quote in 该轮消息文本拼接）；第一批工具沉淀
`tools/check_gold_b3.py`（python -I 可复用）。

---

## 1. 卡片 9+1 字段逐字段规范

`reference.json` 形态：`{"story_id":"E3xx","turns":{"<E3xx_T01>": <卡>, ...}}`，每卡全
9 项必填（`tools/validate_story_v2.py` 硬门：缺字段/mismatch 即 ERR），另加一致性字段
may_decline（+1）。

1.1 **case_id**：与 episode `turns[i].case_id` 完全一致（如 `E301_T03`）；reference case
集合必须等于 episode case 集合（校验器：`reference case set != episode case set`）。

1.2 **current_action**：本轮实际在发生什么，一句话，非空。写给评审与 B3 亲读用，
不进候选输入面。

1.3 **acceptable_actions**：**≥2 条**且必须**真实不同**——两条动作约束的必须是不同的
事实/意图/场景/分寸组合（自然短回/调侃/反问/合法不回都可），不是同一句话的同义改写。
校验器硬门：`fewer than 2 acceptable actions` / `duplicate acceptable actions`。
"真实不同"的自检问法：把两条分别给两个合格回应者，他们是否可能出于不同风格各自选择？
若两条只在措辞上不同、在事实上互斥或语义上同一，重写。约束事实、意图、场景、分寸，
不规定唯一措辞（rubric §10 自由度）。

1.4 **applicable_dimensions**：**≥3 个真实适用**的九维（goal/factuality/attribution/
time_state/boundary/memory_use/interaction/initiative/emotion_relation）。校验器硬门：
`fewer than 3 applicable dimensions` / `unknown dimensions`（词表外的维名直接报错）。
"真实适用"的自检：该维在本轮**有判别内容**（有可判好坏的具体依据），不是"沾边即选"。
不为凑数硬塞；判据见 `TOPIC_RUBRIC_V1.md` §2 各专题"必测能力点"。

1.5 **required_memory_atoms**：原子数组（结构与十纪律见 `ATOMIC_ASSERTION_GUIDE_V1.md`
§1–§2）；无则空数组，**不要造原子**。required_this_turn 口径见 §2（本文件定稿核心）。

1.6 **private_guidance**：给评审的私下提示，非空（校验器：`empty private_guidance`），
不进候选输入面。内容承担：本轮判分的语义裁量线（如 KB 取用边界、ME 消息轮特殊形态、
note 型事实的证据链指引）。

1.7 **unknowns**：**对象数组**（每项含 topic/note/markers，见 §3）；禁止裸字符串。

1.8 **protected_expressions**：她明确说过的边界话、不想被提起的事；逐字纪律见 §5。

1.9 **forbidden_factual_claims**：禁止坐实的说法（未定时间、单方猜测、取消事项）。
注意：自然语言禁令层 1 无法子串判定——需要 C6 判分的禁令另给可出现在
`analysis.scene_facts[].text` 里的具体子串形态（GOLD_SCHEMA §8 附注）。

1.10 **（+1）may_decline**：与 episode input.may_decline 一致的布尔；合法不回轮所有
文本类检查走 decline 分支（免文本检查、decline_appropriateness 独立计分）。

1.11 **gold_empty 语义（诚实登记）**：本轮确无可判 gold（无原子/无 unknowns/无意图契约）
时如实留空——评分侧 `gold_empty=true` 只跑契约层，不造任何检查、不进分母
（rubric §2"gold 缺席诚实登记"）。**不要为填满分母而造弱原子/弱 unknowns**。

---

## 2. required_this_turn 口径定稿（首批实证分歧在此，V1 唯一口径）

2.1 **规则**：required_this_turn=true 当且仅当该原子**来源轮位早于本轮**。校验器硬门
（`tools/validate_story_v2.py` L230）：`atom required but sourced from current turn` →
ERR。来源轮位以 source.case_id 与当前卡 case_id 比较为准。

2.2 **理由（回声原子无记忆价值）**：required 的语义是"本轮必须**从记忆取回**的事实"
（`tools/build_f3_from_specs.py` 归一口径：required_this_turn 统一按"来源是否为当轮"
机械推导）。来源是当轮消息的事实是**回声原子**——候选输入面里本来就有，要求候选复述
既不测记忆也不测质量，只会诱导复读。因此"本轮必须处理的事实"**不用 required 承载**，
改由以下字段承载：
- **acceptable_actions**：把"必须答到什么"写进动作约束（如 E301_T02 必须答后端材料
  状态、E310_T06 必须直接答+理由——C1 万能句生存空间检查的落点全在这里）；
- **forbidden_factual_claims**：本轮不得坐实/不得编造的边界（如取消事项、未定时间）；
- **private_guidance**：语义裁量线与证据链指引（如"顺她撇清的安全回复不算坐实"）。

2.3 **已知覆盖面后果（如实登记，Phase 3 解读用）**：按 2.1 口径翻转后，wave4_dev 存活
required=true 共 **51 处**（分布：E301–E316=0——B1/B2 的"本轮必须处理"全部落在当前轮
事实；E317–E324=4、E325–E332=12、E333–E338=35——B5/B6 按记忆/更正链语义标注）。层 1
required-affirmation 检查对这部分新篇贡献为零；事实判分由 cancel 族违例（affirm_markers，
不依赖 required 标志）＋语义层承担；旧题 45 篇 required 覆盖不变。这是**口径选择的结果，
不是标注缺陷**；后续批次按同一口径执行，不做"补 required"式回改。

2.4 判分语义：required=true 的原子也不触发"必须逐字复述"——层 1 对 confirmed/asserted
原子只作证据（GOLD_SCHEMA §3）；required 的判分价值在语义层（B3 亲读"漏处理 required
原子"）与 cancel 族程序判分。标注员不要因为标了 required 就以为程序会强制提及。

---

## 3. unknowns 规范（不确定不当确定）

3.1 **对象形态**：每项 `{"topic": "...", "note": "...", "markers": [[...]]}`。禁止裸
字符串（旧库 2649 条纯字符串仅 189 条升级的教训：出题时就给信号，别留给 enrichment 猜）。
markers 为嵌套词组数组（组内 AND、跨组任一命中即坐实→fail，检查点 `unknown_*`，
2/条；GOLD_SCHEMA §5）。

3.2 **markers 语义＝候选侧断言探测词（与原子 markers 最易混，V1 定稿裁决）**：
- 原子的 affirm_markers/item_markers：**题面逐字纪律**（必须能在 episode 语料或来源
  消息里逐字出现；机制与反例见 `ATOMIC_ASSERTION_GUIDE_V1.md` §4）。
- unknowns 的 markers（探测词）：写"**若候选错误地把该不确定点坐实，它大概率会说什么**"
  ——是候选侧断言探测词，**不要求题面逐字**。消费语义为"探测器无需在正文"（C1 对
  B1/B2 的 27 个 marker 组逐组人工判读口径：10 组在正文属 HER 原话锚定、17 组为候选侧
  探测器，全部可消费）。两者不可混标：给原子套语料外词会被 Marker QC 拦截（首批 43 处
  违例即此根因），给 unknowns 强套题面锚点反而会把"提及"误判为"坐实"（机械替换方案
  已在 REPAIR_MARKER_FIX_1 §六否决）。
- 探测词设计目标：**精度优先**——假设短语（如"组长其实特别满意""批注里骂的是"）命中
  即近乎必然是坐实。可接受的形态：题面锚定的同现组、候选惯用话术、HER 原话反义钩。
- **双键同步纪律**：对象化 unknowns 同时给 `markers` 与 `affirm_markers` 双键（同值）。
  首批曾因 E317–E332 只写 `markers` 键而 sidecar 只读 `affirm_markers` 导致漏消费
  （R2 管线发现，已并入 fix_wave4_schema_v1 A8 补 159 处双键）；新标注直接双键，
  校验脚本抽查两键一致。

3.3 **置空诚实**：提不出可靠探测词 → 该 unknown 的 markers 置空（或整个对象不建），
层 1 对其诚实 unrated，**不造词凑检查**。可避免的 N/A 要避免（ANNOTATOR_BRIEF 红线），
但精度不可牺牲——宁 unrated 不误伤。

3.4 **登记场景（首批实证）**：相对日期无后续证实（E320_T12"下周三球局"、E323_T12
"下周五重映"）；多源口径冲突未裁决（E327_T03 同事说法 vs 公众号口径、E327_T06 群推荐
vs 同事报价）；推算类读法分歧（E318_T11 下周六 12-12 双读法、E334"下周三"双读法归层 2）。
有后续证实的相对日期不走 unknowns，直接建原子（推算依据入 audit.md）。

3.5 **已知误伤面（登记，护栏归评分侧 Phase 3）**：朴素子串匹配对否定/疑问/假设类
**正确回复**有误伤面（C1 MAJOR-8⑧ 共 7 处实例，如 E305_T07 探测词 `["是特意"]` 会误伤
安全回复"不是特意"、反问"你爸妈知道吗"被判坐实）。gold 侧对策=选词避开正确回复会
包含的词形；系统性护栏（否定/疑问窗）属评分器变更，按 §9 版本化纪律放 Phase 3 预注册
处理，本阶段不改计分器。

---

## 4. 状态原子的 markers 纪律（取消 / 否定 / superseded / conflict）

原子状态词表与判分语义的权威文本在 `ATOMIC_ASSERTION_GUIDE_V1.md` §2；本节只定标注侧
markers 纪律。

4.1 **取消/否定族原子必带 affirm_markers**（题面逐字）：status ∈ 取消族（cancelled/
superseded/…）或 negation=true 的原子，给出"命中即视为坐实"的词组数组。**affirm_markers
逐字落盘、item_markers 事项名逐字**；格式嵌套 `[[...]]`（组内全词同现才算坐实，跨组
任一组命中即坐实）；单短语成组须 ≥4 字（`MIN_SINGLE_PHRASE_LEN`，防"提及"误判"坐实"）。
首批 Marker QC 在 13 文件拦下 43 处违例（压缩转述"匀两节课""周四球局"、语料外线索词
"还是/没错/是我说的"）全部按"同原子来源消息内逐字、功能等价"替换，置空 0 处——
替换范例如 E325_T09_A01 item「匀两节课」→「匀两节」（E325_T08/m0"让我匀两节给你"）。

4.2 **item_markers 仅 status ∈ {cancelled, canceled} 的事项/计划类原子必给**（进 C8
复活判定：候选 `analysis.ongoing` 中 item 含 marker 且 status ≠ 已取消 → fail）；
superseded 原子的 item_markers 为 QC 可见、判分不消费，按同规则书写（防日后改判分时
是死数据）。T10 孪生组的守卫原子（status=invalidated，详见
`ATOMIC_ASSERTION_GUIDE_V1.md` §2）按取消族同路径消费 affirm_markers，同样适用本条与
4.1 的逐字纪律——守卫 quote 必须逐字命中本篇真实正文。

4.3 **conflict 用双原子＋unknowns 表达**：同一事实两源口径冲突时，两侧各建一原子
（status=conflict，relation 写明冲突性质），未裁决真相用 unknowns 登记——候选不得擅自
裁决冲突当事实。实证：E327_T03_A01（同事称索道检修停开）与 E327_T03_A03（公众号口径
正常运行）互指冲突＋unknowns"是否正常运营未裁决"；E327_T06 群推荐"人均四五十"vs 同事
"70 起步"同构。conflict 不在 sidecar 取消族映射内 → 层 1 不判分（B4 遗留，诚实登记），
判死依赖 forbidden 子串＋required 纠错原子；conflict 原子的 affirm_markers 按 QC 合规＋
标注意图登记（E326_T07_A03"这话是你跟我说的吧"归属冲突）。

4.4 **反转位反例（状态原子不是终身追逃）**：E332_T09 反转位刻意**不带**旧取消原子的
must-not-affirm——医生口径放宽、剧情自行演进已推翻旧禁令，机械带旧禁令会惩罚正确跟随
剧情的候选。规则：每个取消族原子挂 marker 前问一句"该禁令在当前轮位是否仍被剧情承认"；
被剧情推翻的旧禁令改为在 private_guidance/acceptable_actions 里描述当前正确方向。

4.5 **防误伤选词（首批三范式）**：
- 不用裸共享词：E326_T11 item「改去28号汇演」而不用裸「28号汇演」（防 C8 误伤真实
  28 号汇演 ongoing 项）；E332_T03_A01 item「11月21号」而不用「《雾中灯塔》」（防误伤
  新档期 ongoing 项）；
- 不并入未变特征：E316_T01_A02 item「北门」不并入「七点五十」（时间未变，合并会误伤
  新集合项）；
- 利用跨形态不连续：E329_T11_A03 item「一小把」（新口径"一次小半把"中不连续出现，
  无误伤）；E337_T08_A02 affirm「到我楼下」（更正话术"到我家楼下"不含该连续串）。

---

## 5. protected_expressions（当轮 HER 逐字硬门）

5.1 **规则**：protected_expressions 每条必须**当轮 HER 消息逐字**出现。校验器硬门
（`tools/validate_story_v2.py` L204–209）：`protected expression not in HER message` →
ERR（取该轮 role=HER 的消息拼接后做子串校验）。

5.2 **定义与用途**：她明确说过的边界话、不想被提起的事——judge/boundary 判定的题面
依据。无题面禁区依据不判"泄漏"（TOPIC_RUBRIC_V1 §2 T10）；因此标注员漏登记
protected_expressions = 该轮 boundary 判定失去程序/题面依据，不是评审保守。

5.3 历史注记：旧卡曾普遍漏登记（`build_gold_sidecar.py` 头注——修复了旧卡漏登记
protected_expressions 与 may_decline）；新标注一律当轮落盘，不留 enrichment 补。

---

## 6. 40 锚点与用户愿发的接口（AI 不代投）

6.1 **40 个人工审核锚点**：由 pilot（样本=首批 38 篇新题+2 篇 ACCEPT 级 T09 旧题
E063/LRE121，DIRECT vs CURRENT，cap1500，见 `PILOT2_SUBSET.json` /
`PILOT2_AUTHORIZATION.json`）产出后落 `HUMAN_REVIEW_QUEUE.md`；锚点是**人工审核入口**
不是 AI 评审替身——标注员与 judge 都不得代投锚点位的人工结论。

6.2 **用户愿发（层 3 最终裁决）**：W 表五选一用户盲评，**AI/worker 不代投**；未采集时
记 `NOT_COLLECTED`，该栏留空不冒填。标注产物（acceptable_actions 等）是愿发评审的
语义素材，不是愿发结论本身；首选与备选分审，愿发票只投 primary_text。

6.3 标注侧与锚点的接口纪律：每卡 acceptable_actions/applicable_dimensions 的设计要能
回答锚点位将问的问题（"这轮哪些行为算合格""这轮在测什么"）；锚点位若暴露卡面判据
无法回答的问题，按勘误流程回写本规范与对应专题条目，不就地改口径。

---

## 7. 交付、自查与变更纪律

7.1 **audit.md 必含**（首批 B1–B6 实践定稿）：每个关键事实→轮位→quote 核对结果；
回指位清单；相对日期/星期推算依据（B3 口径：推算依据入 audit，如 2026-12-02=周三类
日历主检）；专题判别力自评；遗留风险（含"评分侧已知误伤面"点名）。

7.2 **自查脚本最低集**（python -I，标准库 only）：①quote 逐字（原子 quote in 来源轮
消息文本；message_id 与 hit[0] 一致）；②protected_expressions 当轮 HER 逐字；③
required 原子来源早于本轮；④unknowns 双键一致且为对象形态；⑤markers 嵌套格式
（affirm [[...]] 非空、item 字符串数组非空）。官方门禁：`tools/build_gold_sidecar.py
--dry-run`（Marker QC verbatim_violations=0；首批终态 n=1370、quotes=1350
strict_hit=1350）。

7.3 **正文重序列化警觉**：全角引号/书名号/空格数字形态的 quote 逐字依赖正文原样
（E308_T02《夜奔》全角、E313 空格数字、E334 短信"11 月 14 日 19:40 场"带空格形态）；
若日后重排正文文件（空格/标点变化），须重跑全量 quote 核验（B2 先例：脚本留存）。

7.4 **变更纪律**：本文件实质变更须版本化＋用已有答卷离线重评并列报；与
ANNOTATOR_BRIEF / GOLD_SCHEMA / 外部指导书冲突时，以本文件（更新版本）与指导书为准，
并在沿革节回写差异点。首批遗留待承接项：unknowns 探测词的逐 topic 专门打磨轮
（REPAIR_MARKER_FIX_1 §六建议的 marker-fix-2，含双字段同步纪律）；C15 否定/疑问护栏
（评分侧 Phase 3 预注册）。
