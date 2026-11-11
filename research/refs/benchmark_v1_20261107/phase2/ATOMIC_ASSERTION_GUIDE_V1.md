# ATOMIC_ASSERTION_GUIDE_V1 — Benchmark V1 原子断言规范定稿

> 版本：**V1 ｜ 日期：2026-11-08 ｜ 首批 38 篇实战后定稿**（wave4_dev 1350 原子全量
> quote 复验 0 失配；三审计 C1–C3、两修复 R1/R2、marker 修复 marker-fix-1 的机制发现
> 已吸收）。十纪律源头：外部指导书 Phase 2 §3.5；消费契约：`benchmark/stories_v2/
> GOLD_SCHEMA.md` §3/§8；结构校验器：`tools/validate_story_v2.py`；构建与门禁：
> `tools/build_gold_sidecar.py`（Marker QC + v2 enrichment）。
> 卡片字段口径见 `GOLD_ANNOTATION_GUIDE_V1.md`；专题适用见 `TOPIC_RUBRIC_V1.md` §2。

---

## 0. 原子断言是什么 / 不是什么

0.1 **是什么**：一个可核验、有来源、有状态的最小事实单元（一原子一可核验事实），
写在卡的 `required_memory_atoms` 数组里；构建期逐字拷贝进 sidecar `assertions`。

0.2 **不是什么**：不是候选必须复述的句子；不是语义推理材料。层 1（唯一计分器）
不做语义推理——subject/relation/value/status/negation/valid_time/source.quote 是证据
与登记字段；层 1 只对 must-not-affirm 方向做确定性词组判定（GOLD_SCHEMA §3）。
不要求回复逐字复述：confirmed/asserted 原子只作证据（rubric §10），语义使用质量归
层 2/层 3。

---

## 1. 十纪律（外部指导书 §3.5）逐条 + 首批实证反例

1.1 **一原子一可核验事实**：不把复合事件塞一个原子；也**不为凑数重复拆分**（同一事实
拆成多个同义原子不增加判分面，只稀释审计注意力）。

1.2 **必有真实来源；无据不建**：source 指不到真实消息/KB 的事实不建原子。
- 反例（正确处理示范）：E305_T10"换脚不换手"无 ME 原话出典——按无据不建**未设原子**，
  acceptable_actions 留出典空间（B1 登记）；
- 反例（正确处理示范）：E316_T09 note 金额（120/200）只在 note 不在 HER 消息——原子
  value **不写金额**避免无源断言，候选引用 note 数字合法、编造其他金额归语义层（B2）。

1.3 **quote 逐字匹配对应消息**：quote 必须能在 source.case_id 对应轮 messages 里**逐字**
（含全角/半角、空格、书名号形态）找到。
- 实证：E308_T02《夜奔》书名号依赖全角字符逐字核验（若管线做符号归一化需重验，B1）；
  E313 全角引号与空格数字 quote 已验证，正文重序列化须重跑核验（B2）；E334 短信
  "11 月 14 日 19:40 场"是本篇唯一带空格日期形态，逐字取用（marker-fix-1 #13）。

1.4 **不引用未来消息**：source.case_id 不得晚于当前轮（校验器：`source is future` →
ERR）；跨篇引用时来源 story 必须先于当前 story 入场（校验器 STORY_ORDER 规则）。

1.5 **subject 五分**：HER / ME / THIRD / SHARED / PUBLIC（公共常识）。归属写错会误导
B3/judge 读取：
- 反例：**E311_T01_A02 value 写错 ME 人名**（把 E309 的 ME"周衍"写进 E311，E311 的 ME
  是"韩江"）——MAJOR-7，quote 本身正确、行为契约不受损，但 gold 元数据错误仍判 FAIL
  级修复（R1 M7 定点改一词）。原子落笔前逐一对表该篇 persona 人名。

1.6 **区分状态**（词表与判分语义见 §2）：确定/暂定/取消/被取代/无效/冲突各归各，
不混标。

1.7 **不把推测升级为事实**：单方猜测、未定案变动不建 asserted 原子。
- 实证：E331_T05→T06 取货变动**未定案不硬造 superseded 对**——以新约束原子+tentative+
  unknowns 表达（B4）；E328_T10_A03 前台"压根没收到"证伪的代收猜测按已证伪口径登记
  （marker 选"代收"覆盖改写形态，marker-fix-1 #8）。

1.8 **不要求回复逐字复述**：原子不携带"必须提到我"的语义；confirmed 原子层 1 不判分、
不强迫提及（GOLD_SCHEMA §3；rubric §10）。

1.9 **valid_time 用故事内真实日期，禁止裸"明天"**；相对日期必须可推算且推算依据登记。
- 月粒度登记：月份级计划原文无具体日 → valid_time 用当月 1 日代表（"2026-10"型）——
  E329_T06 兽医超重（B4）、B3 批温泉/实习/月底回来同口径；
- 相对日期推算入 audit.md：B3 批 5 处（下周五/下周四等）；E330_T12"明天"河堤（C2 登记）；
  E318_T11 下周六=12-12 双读法登记（R2 修）；
- 日期星期双锚全核：E326 日期+星期逐一对照（A6 设计纪律，防单锚错）。

1.10 **无据不建**（与 1.2 同源的独立检查位）：建每个原子前自问"quote 指得到、value
不超过 quote+语料、subject 对得上"三问；任何一问不过即不建，改用 acceptable_actions/
forbidden/private_guidance 承载。

---

## 2. 原子 schema 全字段表与 status 判分语义

2.1 字段表（wave4_dev 实测形态；required_memory_atoms 内逐字拷贝进 sidecar assertions）：

| 字段 | 类型 | 说明 | 校验/消费 |
|---|---|---|---|
| atom_id | str | `<EID>_T<nn>_A<nn>` | 全库唯一 |
| subject | enum | HER/ME/THIRD/SHARED/PUBLIC | 校验器必填；归属元数据（纪律 1.5） |
| relation | str | 关系/性质一句话 | 校验器必填；证据登记 |
| value | str | 事实内容（不超过 quote+语料） | 校验器必填；enrich 取交集的材料 |
| valid_time | str | 故事内真实日期；月份级"YYYY-MM" | 校验器必填；禁裸"明天"（纪律 1.9） |
| status | enum | 见 §2.2 词表 | 决定 must-not-affirm 与否 |
| negation | bool | true=该原子在当前情境不成立（与 invalid 同效） | 取消族判定输入 |
| source.kind | enum | message / initial_kb | note 不进 source（纪律 1.2） |
| source.case_id | str | 来源轮 | 不得晚于当前轮；跨篇须先入场 |
| source.message_id | str | `m<idx>`，idx=该轮内首个含 quote 的消息序号（校验器 hit[0] 规则：`message_id != "m%d" % hit[0]` → ERR） | **轮内序号**，非全局计数（B6 口径：装载层如按全局计数需适配；R2 曾修 4 处指位） |
| source.quote | str | 逐字来自该消息 | 校验器 `quote not in source` → ERR |
| required_this_turn | bool | true 当且仅当来源轮位早于本轮（required 原子＝来源在更早轮位的原子） | 口径定稿见 GOLD_GUIDE §2（L230 硬门） |
| affirm_markers | [[str]] | 取消族/否定原子的"坐实"判定词组；组内 AND、跨组 OR | 题**面逐字**（§4）；无 markers 的 must-not-affirm 诚实 unrated |
| item_markers | [str] | cancelled/canceled 事项类原子的事项名（进 C8）；superseded 的为 QC 可见、判分不消费 | 题**面逐字**（§4） |

2.2 **status 词表与判分语义**（V1 定稿；wave4_dev 实测分布：1350 原子中 asserted 1164 /
confirmed 43 / tentative 65 / cancelled 21 / superseded 37 / conflict 6 / invalidated 12 /
unknown 2）：

| status | 语义 | 层 1 判分语义 |
|---|---|---|
| asserted | 当前情境下成立的事实 | 只作证据；不判分、不强迫提及 |
| confirmed | 已经确认成立（比 asserted 更强的确认位） | 同上 |
| tentative | 暂定/未定案 | 证据；配 unknowns 表达未定（E331_T05→T06 范式） |
| unknown | 该不确定点被坐实即错误 | **must-not-affirm**（C15，2/条）——须给 affirm_markers；实测仅 2 处，一般不确定性走卡面 unknowns（其 markers 为候选侧**探测词**，与原子 markers 的题面逐字纪律不同，见 GOLD_GUIDE §3.2） |
| cancelled | 已取消 | **must-not-affirm**（C14 affirm_markers，2/条）；事项类必给 item_markers（C8 复活判定） |
| superseded | 已被新口径取代（更正链旧口径） | **must-not-affirm**（C14）；与 asserted 新口径原子成对（更正链 sup 原子对，TOPIC_RUBRIC T09）；item_markers 判分不消费 |
| invalidated | **守卫原子——T10 孪生特设装置**：把孪生兄弟篇的变体事实在本篇标记为不成立 | affirm_markers 按取消族同路径消费——命中即跨污染坐实。实证形态：E335_T03_A04 `relation:"出借人错误归属（不成立）"`、`value:"'出借人为表弟阿珂'的说法在本篇不成立——本篇出借人为表哥阿哲"`、`negation:true`、`affirm_markers:[["阿珂"]]`（E335/E336 各 6 条镜像，T03/T04/T06/T07/T11/T12 各 1 条）。C3 登记"可整体摘除"：invalidated 状态若评审不收，其余 markers 独立有效——装置不阻塞判分管线。守卫 quote 必须逐字命中本篇真实正文（不得凭空原子） |
| conflict | 双方口径冲突未裁决 | 层 1 **不判分**（不在 sidecar 取消族映射 CANCEL_FAMILY 内）；按 GOLD_GUIDE §4.3 双原子+unknowns 表达；判死依赖 forbidden 子串+required 纠错原子；affirm_markers 按 QC 合规+标注意图登记（E326_T07_A03） |

2.3 取消族全集（与 `arena/gold_sidecar.py` MUST_NOT_AFFIRM_STATUS / 构建期
CANCEL_FAMILY 逐字一致）：cancelled / canceled / superseded / retracted / voided /
revoked / invalidated / negated / erroneous；`negation:true` 等效 invalid。

---

## 3. 来源引用规则

3.1 **quote 逐字**：见纪律 1.3；QC 规则=quote in 来源轮消息文本（strict），全 episode
语料兜底为 lenient——strict 是标写目标，lenient 命中要在 audit 登记。

3.2 **message_id = hit[0] 规则**：`m<idx>` 的 idx 是该轮 messages 中**第一个**包含 quote
的消息下标（`tools/validate_story_v2.py`：`hit = [k ... if quote in text]`，取 hit[0]）。
同轮多消息按序 m0/m1…；引用跨篇消息时以来源篇的轮内序号为准。

3.3 **不引未来轮/未来篇**：见纪律 1.4。

3.4 **note 与 initial_kb**：note（军师备注）内容**不进** message 原子的 quote（note 不是
消息）——note 型事实的证据链在 private_guidance，层 1 对其无直接检查点（B4 遗留，诚实
登记）；initial_kb 内容可建 `source.kind="initial_kb"` 原子（quote 逐字取自 KB 文本）。

3.5 **subject 五分**（纪律 1.5）；SHARED=双方共同事实，PUBLIC=公共常识（店名/节假日）。

---

## 4. markers 规范（原子侧）

4.1 **题面逐字铁律**：原子的 affirm_markers/item_markers 每个词必须是**来源消息或
episode 语料中可逐字出现的短语**——不得写事实摘要/压缩转述/假设候选措辞/语料外线索词。
首批 Marker QC dry-run 报 43 处违例（13 文件），根因即"把'匀两节课''周四球局'（压缩
转述）与'还是/没错/是我说的'（语料外复活线索词）当 marker"；修复=逐处替换为**同原子
来源消息内逐字、检测功能等价**的特征词组，置空 0 处（REPAIR_MARKER_FIX_1，官方门禁
复检 verbatim_violations=0）。

4.2 **组语义**：嵌套 `[[...]]`——**组内全词同现才算坐实（AND），跨组任一组命中即坐实
（OR）**（如 `[["和小林","看电影"]]`=同时提及人和事）。单短语成组须 ≥4 字
（`MIN_SINGLE_PHRASE_LEN`，防"提及"误判"坐实"）；多短语 AND 组自带约束，可保留 ≥2 字
短语。

4.3 **防误伤选词三范式**（详见 GOLD_GUIDE §4.5）：不用裸共享词（E326_T11「改去28号
汇演」非裸「28号汇演」）；不并入未变特征（E316_T01 item「北门」不并入「七点五十」）；
利用跨形态不连续（E329_T11「一小把」、E337_T08_A02「到我楼下」）。

4.4 **未知反例与否定式局限**：affirm_markers 无法区分否定式措辞——E333 因此采用反向
禁令设计（每卡在位"不得声称场次已取消或退款"）而不设取消说法守卫原子（C3 认可理由
成立）；同理 E326_T07_A03 的 affirm 组在 conflict 状态下仅作标注意图登记。系统性否定/
疑问护栏归评分侧 Phase 3（GOLD_GUIDE §3.5）。

---

## 5. 与 sidecar / enrich 的对接（构建期机制，标注员须知）

5.1 **构建期覆盖机制（marker-fix-1 发现，重要）**：`tools/build_gold_sidecar.py` 的
`enrich_cancel_atom` 会对**取消族原子**（CANCEL_FAMILY 或 negation=true）**重算并覆盖**
affirm_markers——四步取交集：①value∩源消息 → ②value∩全 episode 语料 → ③quote∩源消息
→ ④quote∩全语料（`max_common_phrases`，组内取前 2 短语）；**仅当四步全提不出 ≥4 字逐字
信号**（`no_verbatim_signal` / `single_phrase_too_short`）时，reference.json 里的原值才
实际落盘参与判分。后果：enrich 成功覆盖的原子，其 reference 内旧 markers 是**死数据**
（QC 不可见）——标注员仍应按 §4 写好 markers（那是 enrich 失败时的唯一落盘值），但不
要指望手写值总是生效值；enrich 只作用于取消族，asserted/confirmed 原子的 markers 不被
消费也不被覆盖。

5.2 **"无 affirm_markers 的 must-not-affirm 不判分"（诚实口径）**：取消族/否定原子缺
markers → 层 1 不判分，进 `unrated_assertions` 诚实登记——层 1 不猜语义，语义矛盾归
层 2。标注员不要因此留空了事：可避免的 unrated 要避免（GOLD_GUIDE §3.3），enrich 兜底
不等于可以不写。

5.3 **item_markers 消费窗口**：仅 status ∈ {cancelled, canceled} 进 C8（
`gold.cancelled_items`，子串匹配 ongoing item）；superseded 原子的 item_markers 为 QC
可见、判分不消费（marker-fix-1 §二机制登记）。

5.4 **sidecar 流转与门禁**：`build_gold_sidecar.py` 把 required_memory_atoms 逐字拷贝为
assertions，并做 Marker QC（verbatim 校验）+ quote strict 校验；首批终态：QC n=1370、
verbatim_violations=0、quotes=1350 / strict_hit=1350（38 篇全过）。unknowns 字符串→对象
化 enrich 与 intent_contract 补齐按 GOLD_SCHEMA §8 口径，提不出确定性信号一律保持原
形态诚实 unrated。

5.5 **修复纪律（marker-fix-1 范式，后续修复轮沿用）**：改 markers 必须功能等价替换
（同一原子来源消息内逐字）；同原子未违例的 markers 一律不动；置空须逐处给理由；修复
后以结构化 diff 证明非 marker 字段零意外改动；官方门禁 dry-run 复检归零；修复报告登记
逐项表（旧词/新词/出处轮位/检测功能）。

---

## 6. 变更纪律

- 本文件实质变更须版本化＋用已有答卷离线重评并列报；status 词表增删 = 协议变更
  （影响取消族映射），不得静默改。
- 与 GOLD_SCHEMA / GOLD_ANNOTATION_GUIDE_V1 / 外部指导书 §3.5 冲突时以指导书与本文件
  最新版为准，并在沿革节回写差异点。
- 首批遗留待承接项：E326_T07_A03 等 conflict 原子的层 1 消费口径（当前不判分）如需
  变更，走评分器版本化，不改原子数据；guard/守卫原子的"invalidated 可摘除"结论
  （C3）已登记，T10 扩题继续按孪生组挂守卫原子。
