# E091 团建账不能糊 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 64 篇 story_id/title/themes 防撞字段 + E059 格式参照）。
- 全部人物、公司、农家院、金额轨迹均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：餐饮聚会；冲突型：边界类。HER 阿真承办部门团建AA记账，规矩"家属按人头"被组里老同事以"全家捧场交一份"挑战，私聊被截入小群带节奏、新人效仿苗头出现；她以预算公示、截止规则守住边界，老贺补交四份并附对账条件；团建落成后多退少补闭环，第二波边界（领导提议结余留她卡里）也以"卡里不存公款"守住，老贺约饭致歉收尾。
- 生活动机真实可信：AA记账是最常见的职场边界考场，钱清则人清。

## 2. 连续性与回扣
- T01 规则启动 → T02 一份钱挑战 → T03 小群带节奏 → T04 效仿苗头 → T05 公示立规 → T06 老贺补交 → T07 账本自查 → T08 团建落成冰释 → T09 多退少补闭环 → T10 结余提议 → T11 边界再守 → T12 约饭致歉收尾。
- 跨轮引用原子 8 条（A11 源T03、A18 源T03、A21 源T06、A24 源T06、A27 源T06、A30 源T09、A33 源T10、A36 源T06）≥3。
- 冲突轮落实：E091_A06（retracted+negation，按人头报价被拒）、A07（asserted，小群施压）、A09（效仿出现）、A12/A13（confirmed，公示规则）、A15（confirmed，补交）、A28（领导提议）、A31/A32（confirmed，边界守住与信任）。

## 3. ME 历史与边界
- ME 老罗：同部门老搭档、管过行政、懂公示与多退少补流程（initial_kb.me/warmth）。
- T10 与 T12 may_decline=true：T10 回话方式可由她自行定夺；T12 赴约可自选。
- ME 全程不替她顶撞领导、不替老贺定性占便宜、不评判结余留存的合规性，只给流程化的台阶话术。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，全部为文本；群公告、账本、公示截图均以转述呈现，无图片消息。
- 未设 no_reply 轮。局限：小群与全群对话、领导私聊均经 HER 转述，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A06 retracted+negation、A07 施压）、T05（A12/A13 confirmed 公示落地）、T06（A15 confirmed 规则胜出）、T10（A28 提议 vs A29 边界本能）、T11（A31/A32 confirmed）。
- 跨轮引用原子 8 条，≥3 条红线达成；required_this_turn 原子 8 条（T04、T06、T07、T08、T09、T10、T11、T12），全部源自早前轮次。

## 6. 已知风险
- 老贺、领导、新人的原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 金额轨迹（预交300/人均268/退32）为剧情化简化，不构成财务建议；结余留存不做过 compliance 定性。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(餐饮聚会, 公司团建+AA记账+名单与家属口径+账目公示与公私边界, 边界类) 与既有篇三元组不同
