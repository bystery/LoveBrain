# LRE139 滨海半马前奏：冬训与两次谣言 — audit

## 0. 性质与暴露
- author=AI (G5 isolation long-drama session, batch W3-T3-20261102)
- real_user_material=false，全部人物（温柠、江野、温柠妈妈、姑姑、老家跑友、组委会客服、跑群转发者）均为合成
- wave3_ext 续写条目，源=E139（运动健身/信息不确定类），T1–T12 逐字保留，case_id 改名 LRE139_Txx，原子 atom_id 同步改名 LRE139_Axx（A01–A25），新增原子自 A26 续编。
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/评审 JSON/B3 队列；仅使用任务书白名单（源篇 E139 五件套 + GEN_CHECKLIST + 模板 + 分段规格 + wave1_ext/LRE060 结构样例）。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false，kind=long_drama_ext，source=E139。

## 1. 生活动机
- 域：运动健身；主线：HER 温柠从中签到赛前的冬训全程——牛肉面兑现、寒假回乡打卡、寒潮改跑步机、亲戚误传全马、跑山膝紧降档、返城合练、分区申诉更正、16周课表收官、600公里换鞋、赛前减量与行程敲定；尾段以"补签协议谣言"与"全马误传"两处旧事复活陷阱收束，周六滨海终点线留白。
- 生活动机真实可信：寒假训练连续性、寒潮封跑道、亲戚转述走样、分区凭成绩申诉、跑群谣言需官方核实，都是首马备训者的真实摩擦。

## 2. 连续性与回扣
- C段 T13–T18（过渡与新冲突）：T13 牛肉面兑现（回扣 T10，required）；T14 出发准备（回扣 T11 云监督 required、T09 腿套）；T15 老家首跑（回扣 T12 终点线 required）；T16 寒潮改跑步机（新冲突：天气改期降档）；T17 亲戚误传全马（陷阱一：转述失真，回扣 T01"滨海半马" required）；T18 假期周报体重波动。
- D段 T19–T24：T19 跑山膝紧复现主动降档（新冲突：训练启停，回扣 T07/T08）；T20 膝愈恢复（may_decline=true）；T21 返程定档（回扣 T12"赛前八周关键期" required）；T22 回城开练（回扣 T10 14公里基准 required）；T23 合练16公里+云监督转线下（回扣 T11 required）；T24 分区被分D区申诉（新冲突：信息更正，回扣 initial_kb"10公里58分" required）。
- E段 T25–T28（出窗探针+闭环）：T25 分区更正闭环；T26 16周课表收官（回扣 T02"课表我存了" required）；T27 600公里换鞋（回扣 T09"穿到600公里再换" required）；T28 降量周自省（回扣 T05"就当没中签准备" required）。
- F段 T29–T32（旧事复活陷阱+收尾）：陷阱二 T29 跑群"补签协议否则取消资格"谣言——与 T03"组委会公告里可没写"同源（required）；T30 官方核实辟谣，错误版本原子 superseded+negation=true；T31 行程敲定+模拟21公里（回扣 T12 终点线 required）；T32 赛前一周收尾（回扣 T12"在终点线等我冲线" required）。
- 出窗探针/早期原子依赖轮：T13/T14/T15/T17/T19/T21/T22/T23/T24/T26/T27/T28/T29/T31/T32 中 required 原子 15 个，全部指向 T1–T12 或 initial_kb，不引当前轮。
- 新增原子 A26 起续编，quote 全部为来源台词逐字子串。

## 3. ME 历史与边界
- ME 江野：三个全马 PB430、关心藏在建议里，续写中卷入为云监督、线下陪跑、同赴滨海、终点线接应，均为双方既有约定或可拒绝事项；不诊断伤情（T19）、不替组委会表态（T24/T29）、不代投口味与结果（T32 停在赛前）。

## 4. no_reply / 媒体局限
- 32 轮全部为 HER 单条文本消息；T20 为唯一新增 may_decline=true 轮（源篇 T7 保留）。
- 局限：妈妈、姑姑、跑友、客服、转发者的发言全部经 HER 转述；无语音/图片通道诉求。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3 个（均在九维白名单内）。
- 新增原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词；unknowns/protected/forbidden 为列表。
- 冲突轮落实：T16（寒潮降档）、T17（误传全马 uncertain/纠正）、T19（膝紧降档）、T24（分区 uncertain+申诉）、T29（谣言 uncertain+先例回扣）、T30（错误版本 superseded+negation=true）。
- 陷阱原子显式标注错误版本：T17"全马"口径（forbidden 约束）、T29"补签协议"（T30 作废原子）；正确处理路径在卡内 current_action 与 forbidden 给出。

## 6. 已知风险
- 时间线跨度 2026-12-27 至 2027-02-28，比赛日为 2027-03-06（周六，"明年三月"口径）；寒假半月、农历年后赛前八周、16周课表（11-21 起 16 周至 2027-02-06 前后）与减量周衔接已按故事内口径自洽，语义审查可重点复核周数对应。
- 组委会分区规则、申诉流程、跑群谣言均为剧情内设定，非真实赛事政策。
- 出窗探针距源篇 T1–T12 达 5–20 轮，属长剧窗口外召回设计。

## 7. 出口检查
- [x] episode.json 32 轮、顶层键恰为 episode_id/persona/initial_kb/turns、clock/ts 逐轮单调、角色 HER、口语化台词、无评审术语
- [x] T1–T12 与源篇 E139 逐字一致（台词/ts/clock/may_decline/feedback），仅 case_id 改名——程序化深度相等校验 PASS
- [x] reference.json story_id=LRE139、32 轮键集齐全、每轮字段齐全、quote 逐字、required_this_turn 不引当前轮
- [x] timeline.json story_id=LRE139、turn_clocks 32、message_index 32 条与 episode 完全一致、events/revisions 的 case_id 全部真实
- [x] exposure.json kind=long_drama_ext、source=E139、readers=["author"]、frozen_at=null、hashes=四文件 sha256、runner_copy 指向本目录 episode.json
- [x] 出窗探针 ≥6 轮；旧事复活陷阱 2 处（T17 全马误传、T29 补签协议谣言）；新冲突事件 ≥2（寒潮改跑步机、膝紧降档、分区申诉更正、协议谣言）
