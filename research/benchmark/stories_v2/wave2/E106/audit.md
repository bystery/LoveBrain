# E106 笔记不是教程 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 dedupe JSON 的 story_id/title/themes 字段）。
- 全部人物、公司、考试（中级经济师为公开泛称）均为合成场景；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：学习成长；冲突型：第三方插入类。主线：HER 阿岚备考整理错题笔记，新同事小蕊从求分享到每天工位问讲，再到甩未做的模拟卷求代查，HER 的讲题义务被逐步改写；她立规矩（每周三午休一小时、题须先做）、被绕开（空手听讲+拉人）、退群立硬边界，小蕊自报网课道歉后退回自学，HER 拿回时间后模拟卷涨分，以扉页题词收尾。
- 生活动机真实可信：职场里'帮忙'滑坡成'代劳'是常见困境，THIRD 归属清晰——备考是小蕊自己的义务。

## 2. 连续性与回扣
- T01 笔记汇报 → T02 分享请求 → T03 分享落空 → T04 时间被切碎 → T05 代查卷讨说法 → T06 立规矩 → T07 规矩被绕开 → T08 退群硬边界 → T09 冷处理余波 → T10 反转归位 → T11 涨分兑现 → T12 扉页题词回扣开篇。
- 跨轮引用原子 7 条（A13/A16/A19/A21/A26/A30/A32）≥3。
- 第三方插入类落实：A04/A09/A11（THIRD 实质卷入改变 HER 义务）、A16（revised+negation，义务改写）、A19（confirmed，底线仍在）、A21（retracted+negation，外溢模式被终结）、A26（retracted+negation，讲题停摆归位）、A25（THIRD 退出代劳）。

## 3. ME 历史与边界
- ME 老晏：考过同证的学长，习惯先问'你自己先做了没'（initial_kb.me），点人不骂人。
- T05 与 T12 may_decline=true：T05 她讨说法，ME 可以只听不判；T12 拟题词是轻请求，保留不接路径。
- ME 全程不替她决定发不发笔记、不代骂小蕊、不虚构小蕊动机，只在 T05 给出明确判断（代劳非帮忙）。

## 4. no_reply / 媒体局限
- HER 每轮单条消息，全部文本；无语音/图片（笔记、模拟卷均以转述呈现）。
- 未设 no_reply 轮。局限：小蕊言行均经 HER 转述（含引语），信息为二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互异、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- required_this_turn 原子全篇 7 条（T05/T06/T07/T08/T10/T11/T12），全部来自早前轮次，在 6-9 区间。
- unknowns/protected_expressions/forbidden_factual_claims 均为列表，protected 逐条为当轮 HER 台词子串。

## 6. 已知风险
- 小蕊的引语（'岚姐你救大命了'等）出自 HER 转述，语义审查时注意引用层级。
- 涨十一分为剧情化设定，不构成备考方法建议；'报网课'为情节道具，无机构指向。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词、无评审术语
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=学习成长）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] (域, 主题集, 冲突型)=(学习成长, 错题笔记+第三方求带+讲题边界, 第三方插入类) 与既有 64 篇三元组不同
