# LRE146 姥姥轮椅之后：电池、生日与旧话回声 — audit

## 0. 性质与暴露
- author=AI (isolation-continuation-G7)
- real_user_material=false
- wave3_ext 长剧续写条目，源=wave3/E146，T1–T12 逐字保留。
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审/判决/设计文档，未发起任何网络/模型调用；仅使用任务书白名单（源篇 E146 五件套 + 模板清单 + wave1_ext/LRE060 结构样例 + 校验器）。
- episode_id=story_id=LRE146；T1–T12 台词文本、ts、clock、may_decline、feedback、intent 字段自 E146 逐字继承，仅 case_id 由 E146_Txx 改名 LRE146_Txx；reference 原子 atom_id 同步改名 E146_Txx_Ayy → LRE146_Txx_Ayy，新增原子按同风格逐轮续编（LRE146_T13_A00 起）。
- persona / initial_kb 结构与内容自源篇原样复制。
- 全部人物（姥姥、舅舅、门店老板、邻居王阿姨、售后刘师傅、旗舰店客服、姨姥姥）均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false。

## 1. 生活动机
- 域：家庭事务；主线：HER 唐棠在姥姥电动轮椅购入后的续章——邻居闲话、挡风被配件差价、姥姥生日宴订定、轮椅续航故障与免费包换售后、旧渠道旧报价的两次复活、四轮代步车提议的探查与撤销。
- 生活动机真实可信：低温续航衰减、配件保修匹配、饭店包间费与最低消费、长辈人情账的当面结账，都是家庭大件售后与聚会高频摩擦。

## 2. 连续性与回扣
- C段 T13–T18（跨日过渡）：邻居闲话（T13）→ 挡风被差价（T14）→ 原厂下单（T15）→ 生日宴差事（T16）→ 三家饭店比较（T17）→ 福满楼订定（T18）。
- D段 T19–T24（冲突事件一：电池故障+旧渠道复活）：T19 续航骤降；T20 舅舅重提"去门店换块原装电池"（陷阱一，A01 source=T03 "网上坏了没保障"，superseded+negation）；T21 保修卡核查（A00 source=T04 "电池第一年包换"）；T22 门店老板反证；T23 售后上门免费换新；T24 舅舅改口。
- E段 T25–T32（出窗探针 ≥6）：T25 回扣T08"发票抬头开的姥姥名字"；T26 回扣T10"非要转我三百"；T27 回扣T01"报价四千六百八"；T28 回扣T04"凭发票全国联保两年"；T29 回扣T04"电池第一年包换"；T31 回扣T12"天天自己溜达到菜市场"；T32 回扣T01"报价四千六百八"。共 7 轮出窗探针，全部 required_this_turn=true 且 source 指向 T01–T12。
- F段 T33–T40（陷阱二+收尾）：T33 "库存翻新货"流言+旧报价"四千六百八的才是原装"（陷阱二，A02 source=T03 "网上坏了没保障"旧论复活，superseded+negation）；T34 证伪拍板不退；T35 挡风被告一段落；T36–T38 四轮代步车提议→查证→姥姥否决撤销；T39 账单成页；T40 幸福收尾（may_decline=true）。
- 新增跨轮引用原子（source 距当前轮 ≥2 或为既定事实锚点）遍布 T20/T21/T23/T25/T26/T27/T28/T29/T31/T32/T33/T40；每轮 ≥1 原子，quote 全部为来源台词逐字子串。

## 3. ME 历史与边界
- ME 阿灿：市里上班、先查参数再比价（initial_kb 继承源篇）；本篇继续承担"条款派"参谋角色。
- 新增 ME 卷入均为轻量参谋：挡风被选型、饭店比价、故障处理路径、代步车查证；不代付款、不代拍板、不与舅舅争对错。
- ME 不虚构自己到场宴席（T30–T31 为 HER 现场转述）、不虚构换新电池的保修条款（T23 forbidden 显式约束）。

## 4. no_reply / 媒体局限
- 40 轮全部为 HER 单条文本消息；T40 may_decline=true 为唯一可拒收尾轮。
- 局限：姥姥、舅舅、门店老板、王阿姨、刘师傅的发言全部经 HER 转述，延续源篇"信息二传"设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2–3 条、applicable_dimensions ≥3（九维白名单内）。
- 新增原子逐轮 value 均 ≥4 实义词；message 来源 quote 逐字核验；required_this_turn 不引当前轮。
- 冲突轮落实：T15（方案否决 superseded+negation）、T20（A01 superseded+negation，旧渠道复活陷阱）、T23（A01 superseded+negation）、T33（A02 superseded+negation，流言陷阱）、T34（A01 superseded+negation）、T38（A00 cancelled+negation）。
- 两处旧事复活陷阱（T20 门店渠道旧话、T33 库存翻新流言+旧报价）均以 negation/superseded 原子显式标注错误版本，unknowns 明示，正确路径在 current_action 与 forbidden 给出。

## 6. 已知风险
- 时间线 2026-11-16 至 12-20 单调；12-06 为周日（姥姥生日、周日宴）经日历核验一致。
- 售后免费上门换新、订金抵菜金为剧情内设定，非真实平台/商家政策承诺。
- 金额链（4680/3980/差价700/挡风被180/订金100/转800）与源篇兼容，未引入冲突数字。

## 7. 出口检查
- [x] episode.json 40 轮、顶层键恰为 episode_id/persona/initial_kb/turns、clock/ts 逐轮单调、角色 HER、口语化、无评审术语
- [x] T1–T12 与源篇 E146 逐字一致（台词/ts/clock/may_decline/feedback/intent），仅 case_id 改名；12 轮深度相等校验 PASS
- [x] reference.json story_id=LRE146、40 轮键集齐全、每轮字段齐全、quote 逐字、required_this_turn 不引当前轮
- [x] timeline.json story_id=LRE146、turn_clocks 40、message_index 40 条与 episode 完全一致、events/revisions case_id 全部真实
- [x] exposure.json readers=["author"]、frozen_at=null、hashes=现算 sha256、runner_copy 指向本目录
- [x] 出窗探针 ≥6 轮（T25–T29/T31/T32 共 7 轮）；旧事复活陷阱 ≥2 处（T20、T33）
