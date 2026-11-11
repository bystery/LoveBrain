# E072 周末不外借 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单材料（模板 + 既有篇的 story_id/title/themes 防撞字段 + E059 格式参照）。
- 全部人物、公寓、宜家家具、平台服务均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家居装修；主线：HER 栗子新租老公寓做低成本改造，从请 ME 装衣柜到加码"顺手清单"，ME 以"急活搭把手、大活找师傅"立边界；中段闺蜜小雨借人请求叠加人情抵押（第三方插入），并出现"周末闲着"的游说受压时刻；ME 拒绝后小雨转平台解决、房东按合同修完清单三项，栗子转向自己动手（洞洞板、挂画），最终以人情账平账、海底捞感谢宴与"动锤子先请火锅"的三人约定收尾。
- 生活动机真实可信：能干朋友的帮忙边界、租房维修归房东、闺蜜间的人情与分寸，都是日常高频张力。

## 2. 连续性与回扣
- T01 首次求助 → T02 完工与请饭兑现 → T03 第二次合理求助（奶茶抵工费）→ T04 清单加码+闺蜜登场 → T05 边界首次生效 → T06 小雨借人插入 → T07 游说受压爆发 → T08 拒绝生效、平台解决 → T09 房东履约、边界课毕业 → T10 自学成长与电钻礼物 → T11 人情账平账 → T12 三人约定散会收尾。
- 跨轮引用原子多条（A10/A17/A20/A23/A28/A32），≥3。
- 冲突型=边界类落实：T04（A08 清单 asserted，加码苗头）、T05（A11 retracted+negation，清单撤下）、T07（A17 工作边界规则 suspended 施压）、T08（A20 retracted+negation，借人请求撤回；A18 confirmed 拒绝落地）、T12（A29 边界默契成文）。

## 3. ME 历史与边界
- ME 老许：动手强、"急活搭把手，大活找师傅"、"不当随叫随到的装修队"（initial_kb.me），T05 划界与 T07 拒绝均由此人设承载。
- T05 与 T10 各含一条 ME 历史消息（划界口径、电钻相送），属已发送事实陈述，拒绝不越人设；T07 may_decline=true，参考卡同时保留"按急活口径评估一面墙的活"的余地，拒绝不是唯一路径。
- ME 不替房东修、不替小雨接单、不替栗子决定改造范围。

## 4. no_reply / 媒体局限
- 全篇为文本消息；战果照片、小雨的清单、样板间照片均以 HER 转述呈现。
- 未设 no_reply 轮。局限：小雨的原话均经 HER 转述引用，属信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互不重复、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id（T05/T10 的 ME 台词参与 message_index），value 均 ≥4 实义词。
- 冲突轮落实：T05（A11 retracted+negation）、T07（A17 suspended）、T08（A20 retracted+negation）。
- required_this_turn 原子共 7 条（T04、T05、T07、T08、T09、T11、T12 各 1），全部引用早前轮次；跨轮引用原子 ≥3 条红线达成。

## 6. 已知风险
- ME 拒绝闺蜜单的轮次（T07/T08）为该篇核心，语义审查时注意 acceptable_actions 中"按急活口径评估"与"明确拒绝"两条路径的分寸差异。
- 平台收费（四十元）与房东免费维修为剧情化设定，不构成消费参考。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER/ME、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息逐条一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家居装修, 租房改造+帮忙边界+闺蜜推单+人情记账, 边界类) 与既有 48 篇三元组至少一元不同
