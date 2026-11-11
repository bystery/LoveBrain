# E150 改了五回的北山局 — audit

## 0. 性质与暴露
- author=AI (CF1 isolation worker)；real_user_material=false
- 本篇为反事实孪生篇（W13-02 / 批 CF1-20261102）：kind=counterfactual_twin，source_story=E105，perturbation=time。
- 扰动点：反事实孪生扰动点（time）：改期档期11月21号(周六)→11月22号(周日)；触及 T04(投票结果与确认)/T05(定档)/T07(已改22号)/T08(维持22号)/T11(周日早八点) 台词与 A08/A09/A10/A11/A17/A19/A20/A29/A32/A33/A34/A35 原子，T12 时间戳顺延一日；原局日期(11月15号周日)、盘点取消、踩点与西线改线台词逐字保留源篇。
- 隔离声明：生成会话未读 logs/、candidates/、任何 VERDICT/REVIEW/评审 JSON、KNOWN_FAILURES、B3 队列；仅读 W13-02 设计（§2 扰动协议）、源篇五件套、模板与 validate_story_v2.py。
- 全部人物与机构均为合成；exposure readers=["author"]，frozen_at=null。
- 最小编辑协议落实：除触及被换属性的轮次台词与对应原子外，其余轮台词、非扰动原子与 persona/initial_kb 逐字保留源篇（脚本以精确计数替换保证）。

## 1. 生活动机
- 域：徒步爬山；主线：HER 小满组织的北山徒步局因盘点值班取消预警，投票改期到11月22日周日并重启准备，原日因盘点取消复活引发双日纠结，她拍板维持22号并以周日踩点发现栈道封路改推西线，人数进出两轮后改公交，最终周日成行并以补环线之约与登山杖玩笑收尾。

## 2. 连续性与回扣
- T01 新局发起(15号) → T02 阵容成形 → T03 盘点冲突取消预警 → T04 投票改期22号并向ME确认 → T05 改期落定重启准备 → T06 表弟加入费用重算 → T07 原日复活双日纠结 → T08 拍板维持22号、次日踩点 → T09 封路改西线 → T10 两人退出改公交 → T11 最终方案报备(周日早八点)与道谢 → T12 22号成行收尾。
- 冲突型落实：T03（A07 suspended，局悬置）、T05（A12 retracted，取消预警解除）、T07（A17 confirmed，改期决议锁定22号）、T09（A24 suspended、A25 改线）、T10（A28 retracted，包车口径作废）。
- 改动后自检：时间线单调、前后引用不矛盾、全部原子 quote 仍为来源台词逐字子串。

## 3. ME 历史与边界
- ME 阿澈：前同事，周末起床困难但答应的局从不咕；T01 may_decline=true，T12 may_decline=true。
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
