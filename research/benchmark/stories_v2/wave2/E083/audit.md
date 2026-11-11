# E083 一杯奶茶引发的账单 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审判决文档，仅使用任务书白名单（模板 + dedupe JSON 的 title/themes 字段 + E059 格式参照）。
- 全部人物（阿岚、老唐、网点工程师、前台、主任、组长）、延保产品与价格均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：数码消费；冲突型：费用类。主线：奶茶泼键盘致送修→初检口头预估2600加检测费300→延保客服拒赔并暗示不实申报（她拒绝）→理赔正式关闭→深度检测把报价打到680（预估被推翻）→结算冒出未经确认的120备份费→网点主任认账免两项费用实收680→取机交付双落定→条款复盘与谢饭收尾。
- 生活动机真实可信：'报价与实收不一致'加'没确认过的收费'是消费维修最典型的费用冲突；她全程不走歪路的底线是故事的骨。

## 2. 连续性与回扣
- T01 事故→T02 送修与条款疑云→T03 初检报价→T04 延保拒赔与撒谎暗示→T05 拒绝申报+理赔关闭→T06 报价反转→T07 备份费突袭→T08 交涉免除→T09 取机交付→T10 条款复盘→T11 离机梗→T12 无电子产品饭局收尾。
- 跨轮引用原子 9 条（A13/A17/A20/A24/A28/A31/A34/A37/A40）≥3。
- 费用类落实：T03（A09 negation：预估非账单）、T04（A10/A12 negation）、T05（A15 negation、A17 confirmed）、T06（A20 superseded：2600预估被680取代）、T07（A23 suspended、A22 negation）、T08（A25 negation、A28 cancelled）。

## 3. ME 历史与边界
- ME 老唐：老友、懂售后规矩、条款原文优先（initial_kb.me/warmth/style）。
- T12 may_decline=true：谢饭邀约可按日程自定；其余轮次均需回应。
- ME 边界：不替延保下'必赔/必不赔'结论、不鼓动投诉升级成闹、不代拟任何不实陈述、费用主张只抓'未经确认'这一个点。

## 4. no_reply / 媒体局限
- HER 单条长消息为主；金额与条款均以 HER 转述呈现。
- 未设 no_reply 轮。局限：延保回执与结算单均为口头转述，无照片/文件证据，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互不重复、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词逐字子串并带 message_id；value 均 ≥4 实义词。
- required_this_turn 原子全篇 8 条（A13/A17/A20/A24/A28/A31/A37/A40），全部引用早于当前轮的来源。

## 6. 已知风险
- 金额（2600/680/300/120）与条款原文均为 HER 转述，语义审查时注意引用层级不构成虚构单据。
- '客服暗示不实申报'情节为合成冲突设计，不影射任何真实厂商；审查时不得允许 ME 认可或复述该报法。
- 维修技术细节（小板、烘干）为剧情化简化，不构成维修指导。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调不减、角色 HER、口语化台词、gold={}
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=数码消费）/author=ZCode isolation worker-AI/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(数码消费, 笔记本进水维修+报价与实收差异+延保免责条款, 费用类) 与既有 48 篇三元组至少一元不同
