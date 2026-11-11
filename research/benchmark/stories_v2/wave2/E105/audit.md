# E105 改了五回的北山局 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 dedupe JSON 的 story_id/title/themes 字段）。
- 全部人物、公司、山名（北山为泛称）、路线均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：运动健身；冲突型：取消改期类。主线：HER 小满组织11月15日北山经典环线徒步局，因公司盘点值班首次取消，投票改期至21号；原日又因盘点取消而复活，她在双日纠结后拍板维持21号并独自踩点；踩点实证栈道封路，路线改推西线；随后两人临时退出，包车改公交。局前后改了五回，最终成行并以补环线之约收尾。
- 生活动机真实可信：组局者的'局不能黄'执念、改期必给交代、自己先兜底，都是常见社交生态。

## 2. 连续性与回扣
- T01 发起原局 → T02 成行+登山杖 → T03 取消预警 → T04 改期投票 → T05 改期落定 → T06 人数费用更新 → T07 原日复活对撞 → T08 拍板维持+踩点计划 → T09 封路改线 → T10 人数回退+公交变通 → T11 最终报备道谢 → T12 成行收尾+补环线回扣。
- 跨轮引用原子 8 条（A07/A12/A17/A20/A24/A28/A30/A34）≥3。
- 冲突型=取消改期类落实：A07（suspended+negation，原局悬置）、A12（retracted+negation，取消预警解除）、A17（confirmed，改期锁定）、A22（negation，拒绝加场）、A24（suspended+negation，环线搁置）、A28（retracted+negation，包车口径作废）、A30（confirmed，西线定案）。

## 3. ME 历史与边界
- ME 阿澈：周末起床难但答应的局从不咕（initial_kb.me），回话短。
- T01 与 T12 may_decline=true：T01 是邀约，ME 可婉拒；T12 是收尾闲聊，保留不接路径。
- ME 全程不替她拍板日期与路线（T07/T09 参考卡明确），只提供选项与接应；不虚构封路公告与路况。

## 4. no_reply / 媒体局限
- HER 每轮单条消息，全部文本；无语音/图片（攻略、投票均以转述呈现）。
- 未设 no_reply 轮。局限：同事态度、投票结果均经 HER 单方转述，无独立信源。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互异、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- required_this_turn 原子全篇 8 条（T03/T05/T07/T08/T09/T10/T11/T12 各 1），全部来自早前轮次，在 6-9 区间。
- unknowns/protected_expressions/forbidden_factual_claims 均为列表，protected 逐条为当轮 HER 台词子串。

## 6. 已知风险
- 日期与星期为内部自洽设定（11-02 周一起），未做历法外部校验，语义审查时注意'周日/周六'称谓与日期对应。
- 栈道封路为剧情化设定，不构成任何景区实况。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词、无评审术语
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=运动健身）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] (域, 主题集, 冲突型)=(运动健身, 徒步爬山+连环改期+踩点变通, 取消改期类) 与既有 64 篇三元组不同
