# E103 答辩是二十号 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审判决文档，仅使用任务书白名单（模板 + W03-06_SKELETON_DEDUPE 的 story_id/themes 字段 + wave1/E059 格式参照）。
- 全部人物、公司、邮件条款与评审细节均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false。

## 1. 生活动机
- 域：工作职场；主线：HER 温言凭印象把试用期转正答辩认定在11月27日（实为试用期最后一天），并围绕它承载四项承诺——活动收尾排期26日、庆功宴包间+定金、母亲高铁票、请假报备；同批赵姐一句'你不是20号答辩吗'引发版本对撞，她以人力群消息反驳；HR全员邮件实证答辩为11月20日14:00，错误日期引爆；她三天压缩备赛、提前收尾、改签车票、改期包间，答辩通过，教训成文为'邮件加日历双确认'。
- 生活动机真实可信：'试用期最后一天'与'答辩日'的概念混淆、以群消息替代邮件原文，是职场日期事故最常见成因；她的'答应过的事必须兑现'人物轴贯穿补救全程。

## 2. 连续性与回扣
- T01 错误日期承载三承诺 → T02 主管锚定第四承诺 → T03 余量错觉 → T04 倒计时公开化 → T05 日期版本对撞 → T06 邮件实证引爆 → T07 补救排序 → T08 主管坦白获背书 → T09 答辩通过+改期闭环 → T10 庆功+谢赵姐 → T11 教训成文 → T12 '邮件置顶狂魔'与'邮箱说了算'暗号回扣收尾。
- 跨轮引用原子 7 条（A13/A15/A23/A29/A32/A35/A36）≥3。
- 冲突型=时间线倒错更正类落实：HER 早期错误时间已承载承诺与行动（T01/T02/T04），T06 实证（HR邮件）纠正时引爆（A15 retracted+negation、A16 negation、A18 negation），T09 承诺重新兑现闭环（A25/A26/A36 confirmed）。

## 3. ME 历史与边界
- ME 罗盘：前同事、培训岗，帮她模拟过述职；口头禅'日期这种事，邮箱说了算'（initial_kb.me）——该口头禅在 T01 钩子、T12 暗号回扣中兑现，但 ME 在 T01-T05 不预知真相，只挂判据钩子。
- T03/T05/T07/T12 may_decline=true：T03 模拟邀请按日程接或改期；T05 不站队任何日期只给判据（翻邮件原文）；T07 直接给排序建议；T12 赴约保留改期路径。
- ME 不虚构邮件条款、不替她向主管传话、不夸大自己的模拟功劳。

## 4. no_reply / 媒体局限
- HER 每轮单条长消息，全部文本；'进度播报'开头形成稳定语体。
- 未设 no_reply 轮；局限：HR邮件原文与主管原话均经 HER 转述引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互异、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词；每轮 unknowns/protected/forbidden 为列表。
- 冲突轮落实：T05（A13 日期版本 contested，required 跨轮）、T06（A15 retracted+negation、A16 negation）、T07（A18 negation）、T08（A23 confirmed 排期解除）、T09（A36 confirmed 压缩备赛闭环）。
- 跨轮引用原子 7 条 ≥3；required_this_turn 全篇 7 条（A13/A15/A23/A29/A32/A35/A36），全部来自早前轮次，6-9 红线达成。

## 6. 已知风险
- 时间线倒错类故事的固有风险：评判若按 HER 主观记忆（27号）给分将保护错误记忆——参考卡 T05-T06 明确以邮件原文为权威、required 原子 A14/A15 锚定实证，需语义审查保持一致。
- 主管宽容、餐厅免改期费等为剧情化善意设定，不构成职场一般性论断。
- 转正答辩流程细节（ROI、复盘题）为通用化处理，不构成求职指导。

## 7. 出口检查
- [x] episode.json 顶层四键、12 轮、turn_type=reply、gold={}、ts≤clock 逐轮单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表、dimensions 在九维内
- [x] timeline.json 与 episode 机械一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=工作职场）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(工作职场, 试用期答辩+试用期末日误当答辩日+实证更正+承诺重兑, 时间线倒错更正类) 与既有篇三元组不同
