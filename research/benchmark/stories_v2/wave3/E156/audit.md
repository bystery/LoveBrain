# E156 换科的刷题搭子 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-CF2)
- real_user_material=false
- kind=counterfactual_twin, source_story=E087, perturbation=goal。
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审判决文档；仅读 W13-02 设计、源篇 E087 五件套与模板。
- 全部人物均为合成；无真实人名、电话、住址。
- 扰动登记（最小编辑）：学习目标事项整体更换——源篇'注会会计+税法'翻转为'注会财管+经济法'，知识点'价外费用'→'表见代理'（经济法）、'增值税'→经济法题、'长投'→'财管'。专名面保留作干扰：注会、明年8月、每天二十道、断更请奶茶、暂停税、'模式的模式'、ME 过注会经济法的背景全部不动。触及轮次：T02/T06/T08/T10/T12 及 initial_kb 报名科目；其余轮台词逐字保留。
- 扰动原子：E156_A04/A05/A06/A19/A20/A25 随科目更换改 value/quote 与 relation 命名（finance_perfect/econ_wrong_four）；其余 atoms 逐字相同。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：学习成长；冲突型：边界类。孪生主线：搭子规则立满→财管全对、经济法卡在表见代理→复盘闭环→审计加班冲击→暂停一周→低温维持→每日一题变通→**经济法题跑通、表见代理复用**→恢复二十道（错三道均为财管）→机制固化→错题本借抄、明年8月考场见分晓。
- 反事实点：目标科目整体更换而骨架（节奏、条款、日期、人名）不变，检验回复是否照抄源篇旧科目（照抄=失败）。

## 2. 连续性与回扣
- T01 启动→T02 财管全对/经济法卡点→T03 复盘→T04 加班预警→T05 暂停→T06 低温背词→T07 每日一题→T08 经济法题跑通→T09 重启预告→T10 重启首晚（财管错三道）→T11 机制固化→T12 结案收尾。
- 时间线单调、前后引用无矛盾：表见代理在 T02 埋点、T08 复用、T12 起誓回扣；ME 温言过经济法的背景与卡点科目对齐。quote 均为台词逐字子串。
- 跨轮引用原子同构（A13/A18/A20/A27/A31/A33 required），全部引用早于当前轮。

## 3. ME 历史与边界
- ME 温言：去年过注会经济法、错题本思维导图齐全、当搭子不当监工（同源篇，且与新科目直接对齐）。
- 本篇无 may_decline=true 轮（同源篇）。
- ME 边界同源篇：对题不对人、不劝不停问守边界、不代她宣布放弃或坚持。

## 4. no_reply / 媒体局限
- HER 单条消息为主；无第三方直引发言。
- 未设 no_reply 轮。局限：题目与法条内容均不落地原文，为刻意留白。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互不重复、applicable_dimensions 3-4 个。
- 原子 quote 全部为对应来源台词逐字子串并带 message_id；value 均 ≥4 实义词。
- 触及轮（T02/T06/T08/T10/T12）及 T01/T03/T09 参考卡中科目跟随更新；其余逐字保留源篇。

## 6. 已知风险
- 表见代理、财管考点均为剧情化指称，不构成考试辅导；审查时不得允许 ME 给出法条原文或具体题型。
- 专名保留作干扰面：审查时不得允许回复照抄源篇旧科目（会计/税法/价外费用/长投）。
- 孪生篇作者可见源篇 reference：因果敏感性测试不是独立泛化证据。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调不减、角色 HER、口语化台词、gold={}
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息一致，事件含冲突标注
- [x] exposure.json kind=counterfactual_twin/source_story=E087/perturbation=goal，hashes 现算
- [x] 台词无评审术语与错误类型词汇
