# E073 交房日子记岔了 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审/判决文档，仅使用任务书白名单（模板 + dedupe JSON 的 title/themes 字段 + wave1 E059 格式参照）。
- 全部人物、装修合同、保洁公司、金额均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家居装修；主线：HER 阿棠凭记忆认定 10 月 31 日交房，据此立起两个已生效安排（约老储周六搬第一趟、订 11 月 1 日开荒保洁）；师傅一句"15 号验房"引爆倒错——合同白纸黑字是 11 月 15 日。她当场作废周六之约、改约 11 月 21 日、免费窗口内改保洁、红包赔不是；随后在"窗帘盒 1200"支线上砍掉预算，验房顺延一天时改走书面凭证，搬家日以验收单定日子落成。
- 时间线倒错更正类的关键设定：错误日期不是无害口误，它已经承载了承诺与花钱的安排；更正如同引爆一颗已生效的安排，这正是 ME 需要识别的核心。

## 2. 连续性与回扣
- T01 错误日期+搬家之约 → T02 保洁跟进 → T03 师傅质疑 → T04 合同引爆 → T05 作废改期 → T06 保洁改约 → T07 窗帘盒考题 → T08 参谋 → T09 决断 → T10 验房顺延+书面凭证 → T11 验房落定 → T12 搬家日按凭证收尾。
- 跨轮引用原子 8 条（A10/A12/A14/A19/A22/A28/A36/A37，另含 initial_kb 引用 A30）≥3。
- 冲突型落实：E073_A12（superseded+negation，31 号被合同推翻）、A14（cancelled+negation，搬家之约作废）、A19（cancelled+negation，1 号保洁取消）、A28（superseded，窗帘盒预算项取消）、A30（superseded+negation，凭脑记习惯被书面凭证取代）。

## 3. ME 历史与边界
- ME 老储：做过家装监理、帮她逐项对过报价、口头禅"翻凭证，别翻记忆"（initial_kb.me）。
- T08 may_decline=true：参谋只给利弊不下判决，决定权还给她；其余轮次以接报备、钉日期、陪补救为主。
- ME 全程不虚构合同条款、不替师傅转述、不提前断言哪个日期为真（T03 是关键防虚构点）。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，全部文本消息；合同原文、验收单均以转述呈现，无图片。
- 未设 no_reply 轮。局限：师傅与客服原话均经 HER 转述引用，属信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词或 initial_kb 值的逐字子串并带 message_id/key；value 均 ≥4 实义词。
- 冲突轮落实：T04（A12 superseded+negation）、T05（A14 cancelled+negation）、T06（A19 cancelled+negation）、T09（A28 superseded）、T10（A30 superseded+negation）。
- required_this_turn 原子 8 条（A10/A12/A14/A19/A22/A30/A36/A37），全部来自早前轮次或 initial_kb，6-9 区间内。

## 6. 已知风险
- 2026 年 10 月 31 日、11 月 21 日为周六，11 月 15 日为周日——剧情按此排布，语义审查时注意她"周六"的说法与真实星期一致。
- 交房日落在周日属剧情化简化；金额 1200 元与保洁改期规则均为合成，不构成装修或消费建议。
- HER 转述中夹带师傅/客服引号原话，引用层级不构成虚构第三方直接发言。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家居装修, 二手房交房+日期倒错引爆已生效安排+书面凭证习惯, 时间线倒错更正类) 与既有篇三元组不同
