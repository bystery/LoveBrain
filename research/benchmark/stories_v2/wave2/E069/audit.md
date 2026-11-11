# E069 日期以截图为准 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单材料（模板 + 既有篇的 story_id/title/themes 防撞字段 + E059 格式参照）。
- 全部人物、口腔诊所、公司安排均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：医疗健康；主线：HER 小满凭记忆把口腔正畸复诊记成"11月4号周三下午"，该错误时间已承载承诺（组长批假、ME 挪会排号），她拿预约短信实证纠正时如同引爆已生效安排：假白请、会白挪、号白排；随后撤假、面对新日期撞大促不可请假周、以周六顶班换 11 号上午半天保住复诊，火锅改期兑现，并以截图互存立规收尾。
- 生活动机真实可信：凭印象约事、日期记错、请假尴尬、大促期请假难，是职场与就医日常的高频组合。

## 2. 连续性与回扣
- T01 口述错误日期并排约 → T02 错误日期承载请假承诺 → T03 钢丝磨腮插曲 → T04 诊所指示维持周三口径 → T05 双侧按错误日期就位 → T06 预约短信实证更正（爆发）→ T07 撤假社死落地 → T08 新日期撞大促请假窗口 → T09 换班保复诊 → T10 新时间线落位+截图互存 → T11 复诊与火锅兑现 → T12 人形日历与充卡回扣收尾。
- 跨轮引用原子多条（A14/A15/A16/A18/A21/A25/A28/A31/A34），≥3。
- 冲突型=时间线倒错更正类落实：A14（superseded+negation，原日期被推翻）、A15/A16（cancelled+negation，火锅与请假安排落空）、A18（cancelled+negation，假条正式撤销）、A28（superseded+negation，火锅改期而非取消）、A13/A26（新时间线 asserted/confirmed）。

## 3. ME 历史与边界
- ME 老唐：高中同学、行政出身、日程表管得死（initial_kb.me），"日期以截图为准"是他人设的口头禅，T06 的钉事实与 T10 的立规矩都由此人设承载。
- T05 含一条 ME 历史消息（挪会+排号），属已发送事实陈述，不替 HER 决策；T12 may_decline=true，参考卡保留"陪诊之约可商定时间"的余地。
- ME 全程不替她找组长、不替诊所改期，医学表达限于复述诊所与医生原话。

## 4. no_reply / 媒体局限
- 全篇为文本消息；预约短信以 HER 转述呈现，未直发图片（T10 提到"截图我这就发你"，仅作为即将发生的动作，不构成媒体内容）。
- 未设 no_reply 轮。局限：组长与诊所的原话均经 HER 转述引用，属信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互不重复、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T06（A14 superseded+negation，A15/A16 cancelled）、T07（A18 cancelled）、T08（A21 冲突锚定）、T10（A28 superseded）。
- required_this_turn 原子共 7 条（T06/T07/T08/T09/T10/T11/T12 各 1），全部引用早前轮次；跨轮引用原子 ≥3 条红线达成。

## 6. 已知风险
- "11月4号"与"11月11号"的日期双写需在语义审查中确认 HER 转述引文与 atom quote 的逐字一致（全角括号）。
- 大促值班、换班规则为剧情化简化，不构成公司制度模板；正畸表述限于转述，不构成医学指导。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER/ME、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息逐条一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(医疗健康, 口腔正畸复诊+日期倒错更正+换班保复诊+截图互存, 时间线倒错更正类) 与既有 48 篇三元组至少一元不同
