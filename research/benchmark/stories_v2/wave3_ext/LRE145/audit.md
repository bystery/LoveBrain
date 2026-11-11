# LRE145 元旦返校拼车：口径、改期与旧通知 — audit

## 0. 性质与暴露
- author=AI (isolation-continuation-G7)
- real_user_material=false
- wave3_ext 长剧续写条目，源=wave3/E145，T1–T12 逐字保留。
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审/判决/设计文档，未发起任何网络/模型调用；仅使用任务书白名单（源篇 E145 五件套 + 模板清单 + wave1_ext/LRE060 结构样例 + 校验器）。
- episode_id=story_id=LRE145；T1–T12 台词文本、ts、clock、may_decline、feedback、intent 字段自 E145 逐字继承，仅 case_id 由 E145_Txx 改名 LRE145_Txx；reference 原子 atom_id 同步改名 E145_Txx_Ayy → LRE145_Txx_Ayy，新增原子按同风格逐轮续编（LRE145_T13_A00 起）。
- persona / initial_kb 结构与内容自源篇原样复制。
- 全部人物（导师、小柯、李师傅、张师傅、苗苗爸妈、同门师妹）均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false。

## 1. 生活动机
- 域：出行拼车；主线：HER 苗苗在月末归家后的元旦返校续章——开题面谈定档、拼车群双口径（高速一百二/省道九十）、评价翻查与订车、司机婚车改期、张师傅旧口径揽客、改期与面谈联动、东广场旧通知陷阱、口碑转介。
- 生活动机真实可信：节假日拼车上浮、司机改期、群消息过期、检票截止倒推出门，都是学生返乡返校高频摩擦。

## 2. 连续性与回扣
- C段 T13–T19（跨日过渡）：面谈定档（T13）→ 拼车群一百二（T14）→ 省道九十双口径（T15）→ 求结论（T16）→ 评价翻查（T17）→ 定李师傅（T18）→ 行程确认（T19）。
- D段 T20–T24（冲突事件一：改期+旧口径揽客）：T20 李师傅改期撞上张师傅"走省道两个小时就到"（陷阱一，A02 source=T17 "张师傅有两条差评"，superseded+negation）；T21 三预案；T22 改1月2日+面谈改约（A02 source=T18，superseded+negation）；T23 新单确认；T24 小柯同行。
- E段 T25–T30（出窗探针 ≥6）：T25 回扣T10"人齐就便宜"；T26 回扣T04"停止检票九点二十五"；T27 回扣T06"谁先上车谁往群里丢定位"；T29 回扣T09"七点五十就到南站了"；T23 回扣T08"七点零二司机到了"；T32 回扣T11"评价四点九"。共 6 轮出窗探针，全部 required_this_turn=true 且 source 指向 T01–T12。
- F段尾（陷阱二+收尾）：T28 小柯转来"南站进站口改到东广场"旧通知（陷阱二触发，A01/A02 suspended），T29 到站证伪（superseded+negation）翻篇；T31 面谈收官+复购意向；T32 口碑转介收尾（may_decline=true）。
- 新增跨轮引用原子（source 为 T01–T12 既定锚点）分布于 T23/T25/T26/T27/T29/T32；每轮 ≥1 原子，quote 全部为来源台词逐字子串。

## 3. ME 历史与边界
- ME 阿灿：本城工作、常坐高铁、留双保险（initial_kb 继承源篇）；本篇承担"结论先行"的裁决者角色。
- 新增 ME 卷入均为轻量参谋：口径拆解、评价判读、预案裁决、旧通知存疑标记；不代订车、不代与司机交涉、不虚构大巴班次。
- ME 不替她拍板换车与否（T20/T21 forbidden 显式约束），不对张师傅作超出评价记录的指控。

## 4. no_reply / 媒体局限
- 32 轮全部为 HER 单条文本消息；T32 may_decline=true 为唯一可拒收尾轮。
- 局限：导师、小柯、李师傅、张师傅、爸妈的发言全部经 HER 转述，延续源篇"信息二传"设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2–3 条、applicable_dimensions ≥3（九维白名单内）。
- 新增原子逐轮 value 均 ≥4 实义词；message 来源 quote 逐字核验；required_this_turn 不引当前轮。
- 冲突轮落实：T20（A02 superseded+negation，旧口径揽客陷阱）、T22（A02 superseded+negation，原方案作废）、T29（A00 superseded+negation，旧通知证伪）。
- 两处旧事复活陷阱（T20 张师傅省道旧口径、T28 东广场旧通知）均以 negation/superseded 原子显式标注错误版本，unknowns 明示，正确路径在 current_action 与 forbidden 给出。

## 6. 已知风险
- 时间线 2026-12-02 至 2027-01-05 单调；2027-01-03 为周日、1月2日为周六经日历核验一致。
- 司机婚车改期、东广场旧通知、检票截止十五分钟为剧情内设定，非真实平台/车站政策承诺。
- 金额链（一百二/九十/差三十/人均八十）内部自洽，未与源篇数字冲突（源篇未报拼车单价）。

## 7. 出口检查
- [x] episode.json 32 轮、顶层键恰为 episode_id/persona/initial_kb/turns、clock/ts 逐轮单调、角色 HER、口语化、无评审术语
- [x] T1–T12 与源篇 E145 逐字一致（台词/ts/clock/may_decline/feedback/intent），仅 case_id 改名；12 轮深度相等校验 PASS
- [x] reference.json story_id=LRE145、32 轮键集齐全、每轮字段齐全、quote 逐字、required_this_turn 不引当前轮
- [x] timeline.json story_id=LRE145、turn_clocks 32、message_index 32 条与 episode 完全一致、events/revisions case_id 全部真实
- [x] exposure.json readers=["author"]、frozen_at=null、hashes=现算 sha256、runner_copy 指向本目录
- [x] 出窗探针 ≥6 轮（T23/T25/T26/T27/T29/T32）；旧事复活陷阱 ≥2 处（T20、T28）
