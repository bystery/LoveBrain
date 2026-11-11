# E149 原来车是这周日 — audit

## 0. 性质与暴露
- author=AI (CF1 isolation worker)；real_user_material=false
- 本篇为反事实孪生篇（W13-02 / 批 CF1-20261102）：kind=counterfactual_twin，source_story=E077，perturbation=time。
- 扰动点：反事实孪生扰动点（time）：真实出发日10月31日(周六)07:20→11月1日(周日)07:20、真实婚礼11月1日→11月2日；触及 T04(彩排周六→周日)/T05(订单与请柬实证)/T06(周日早班冲突)/T07(周日顶班与下周日对冲)/T10(火锅取消理由与8号周日改约)/T12(周日晚上火锅) 台词与 A10/A13/A14/A16/A20/A21/A23/A24/A26/A27/A28/A31/A32/A34/A35/A36/A37/A38/A39/A40 原子；T08-T12 时间戳顺延一日；她自述的错误日期(11月7/8日、5号取件)与其余台词逐字保留源篇。
- 隔离声明：生成会话未读 logs/、candidates/、任何 VERDICT/REVIEW/评审 JSON、KNOWN_FAILURES、B3 队列；仅读 W13-02 设计（§2 扰动协议）、源篇五件套、模板与 validate_story_v2.py。
- 全部人物与机构均为合成；exposure readers=["author"]，frozen_at=null。
- 最小编辑协议落实：除触及被换属性的轮次台词与对应原子外，其余轮台词、非扰动原子与 persona/initial_kb 逐字保留源篇（脚本以精确计数替换保证）。

## 1. 生活动机
- 域：出行拼车；主线：HER 苗苗凭印象记错顺风车出发日与婚礼日（11月7/8日，实为11月1日/11月2日），请假、礼服取件、火锅之约全部压在错误日期上；行前一夜点开订单详情发现倒错，引爆已生效安排——周日早班无人顶、组长失联、取件预约作废、火锅撞出发日。她连夜换班、挪假、加急寄礼服、改约火锅，婚礼圆满后以备忘录自嘲与三闹钟收尾。

## 2. 连续性与回扣
- T01 错误日期宣布 → T02 请假取件闭环（错误基座）→ T03 拼车AA与火锅邀约（与出发日暗中相撞）→ T04 行程倒推 → T05 订单实证引爆 → T06 班次死结 → T07 顶班与假条挪期 → T08 登车报平安（11月1日）→ T09 礼服险情 → T10 礼服到手与火锅改约（8号周日）→ T11 婚礼圆满（11月2日）→ T12 备忘录与三闹钟收尾（11月3日）。
- 冲突型落实：T05（A13/A14 superseded+negation，A15 推翻T01车次记忆）、T07（A21/A22 superseded，请假作废挪期）、T09（A30 superseded）、T10（A32 cancelled、A34 cancelled，火锅取消改约）。
- 改动后自检：时间线单调、前后引用不矛盾、全部原子 quote 仍为来源台词逐字子串。

## 3. ME 历史与边界
- ME 老康：老同学，常跑两市，抢票拼车门道熟；initial_kb 中'先看订单详情再下判断'是他的习惯锚点；T03/T10/T12 may_decline=true。
- ME 不代她做决定、不虚构第三方与机构细节；对风险只提示不保证。

## 4. no_reply / 媒体局限
- 消息形态与源篇一致，全部为文本消息；未设 no_reply 轮。
- 局限：第三方的话均经 HER 转述或引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions ≥2 条、applicable_dimensions ≥3 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 扰动原子与源篇对应原子只差被换字段（time 维）及其直接跟随表述；非扰动原子双篇逐字相同。
- 跨轮引用原子与 required_this_turn 原子均引用早前轮次。

## 6. 已知风险
- 孪生篇作者可见源篇 reference，属因果敏感性素材，不作独立泛化证据（独立泛化归 W13-03 新领域批）。
- 合成数字与剧情化简化同源篇，不构成实务指导。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER/ME、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由孪生 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind=counterfactual_twin/source_story/perturbation、hashes、runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] 扰动点已在 reference.json 顶部 note 与本文件登记，供机械核验
