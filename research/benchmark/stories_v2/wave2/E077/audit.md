# E077 原来车是这周六 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 48 篇的 story_id/title/themes 防撞字段）。
- 全部人物（苗苗、老康、雨桐、贾姐、小蒋、组长）、拼车群、订单均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：出行拼车；主线：HER 苗苗凭印象记错顺风车出发日与婚礼日（11月7/8日，实为10月31日/11月1日），请假、礼服取件、火锅之约全部压在错误日期上；行前一夜点开订单详情发现倒错，引爆已生效安排——明早早班无人顶、组长失联、取件预约作废、火锅撞婚期。她连夜换班、挪假、加急寄礼服、改约火锅，婚礼圆满后以备忘录自嘲与三闹钟收尾。
- 生活动机真实可信：语音/口头信息不点开订单核对，是常见的凭印象办事习惯；时间线倒错的代价由多条已生效承诺共同承担。

## 2. 连续性与回扣
- T01 错误日期宣布 → T02 请假取件闭环（错误基座）→ T03 拼车AA与火锅邀约 → T04 行程倒推 → T05 订单实证引爆 → T06 班次死结 → T07 顶班与假条挪期 → T08 登车报平安 → T09 礼服险情 → T10 礼服到手与火锅改约 → T11 婚礼圆满 → T12 备忘录与三闹钟收尾。
- 跨轮引用原子 7 条（A15/A19/A22/A26/A30/A34/A40）≥3。
- 冲突型=时间线倒错更正类落实：T05（A13/A14 superseded+negation，A15 推翻T01车次记忆）、T07（A21/A22 superseded，请假作废挪期）、T09（A30 superseded，取件预约作废）、T10（A32 cancelled、A34 cancelled，火锅取消改约）。错误时间承载的承诺（请假、取件、火锅、班表）逐一被实证引爆并重排。

## 3. ME 历史与边界
- ME 老康：老同学，常跑两市，抢票拼车门道熟；initial_kb 中"先看订单详情再下判断"是他的习惯锚点。
- T03 与 T10 与 T12 may_decline=true：火锅之约与补约属可商量的私人安排，参考卡保留留活口路径；其余轮次为行程与班次的紧急处理，ME 以参谋身份在场。
- ME 全程不代她做决定、不指责她记错，涉及公司班表与请假制度时不虚构流程细节；对顺风车准点与快递时效只给风险提示不给保证。

## 4. no_reply / 媒体局限
- HER 多轮单条长消息为主，T01/T03/T04/T05/T06/T07/T08/T09/T10/T12 为补发两条；全部为文本消息，无语音/图片（订单与请柬以转述呈现）。
- 未设 no_reply 轮。局限：组长、小蒋、贾姐、雨桐的话均经 HER 转述或引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T05（A13/A14/A15 superseded+negation）、T07（A21/A22 superseded）、T09（A30 superseded）、T10（A32/A34 cancelled）。
- 跨轮引用原子 7 条，≥3 条红线达成；required_this_turn 原子 7 条，全部引用早前轮次。

## 6. 已知风险
- 组长、小蒋、贾姐的原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 顺风车准点、快递时效、公司请假流程均为剧情化简化，不构成任何实务指导；"高速费一人68"为合成数字。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(出行拼车, 顺风车日期倒错+订单实证更正+换班连锁+火锅补约, 时间线倒错更正类) 与既有篇三元组不同
