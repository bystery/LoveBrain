# E085 订单写的是上午十点 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 skeleton 的 story_id/themes 字段 + wave1 E059 格式参照）。
- 全部人物、攀岩馆、订单、教练均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：运动健身；冲突型：时间线倒错更正类。HER 小鹿抢到攀岩馆双人首刷体验课，把攻略帖示例图里的"晚7点"当成真实开课时间；错误时间连续承载承诺——向同事宣告、ME 周五带装备、推掉莉莉火锅局、约定周五量尺寸；周五她点开确认短信才发现订单实为周六上午10点，正面撞上 ME 周六上午陪家长采购的铁律，引爆连锁窟窿；靠馆方"免费改期一次"改到下周六晚，逐项补救后完成首刷登顶。
- 生活动机真实可信：抢到促销就冲、不看确认短信、对外宣告后难以改口，是典型的急性子消费行为。

## 2. 连续性与回扣
- T01 错误时间认定 → T02 对外扩散 → T03 推火锅局加码 → T04 装备与量尺寸 → T05 订单实证引爆 → T06 窟窿盘点 → T07 改期窗口 → T08 改期落定 → T09 补救反馈与顺延 → T10 临场紧张 → T11 到场立约 → T12 登顶与火锅局回扣收尾。
- 跨轮引用原子 7 条（A12/A16/A19/A22/A28/A36/A37）≥3。
- 冲突型落实：T05 A12（retracted+negation，晚7点证伪）；T06 A16（confirmed，相撞事实确认）；T07 A19（retracted+negation，上午场放弃）；T08 A22（confirmed，政策兑现）；T09 A28（retracted+negation，量尺寸作废顺延）；T12 A36/A37（confirmed，条件与时段闭环）。

## 3. ME 历史与边界
- ME 阿铁：攀过两年岩、先看订单再动身、答应的事留档（initial_kb.me/warmth/style）。
- ME 周六上午陪家长采购是铁律（initial_kb.me 引用于 T05 原子 A13 的对话回扣，HER 台词"你周六上午不是要陪阿姨去市场吗"），构成引爆的义务面。
- ME 不代操作订单页（T07 明示不得替她操作或承诺代点），仅视频协助核对；装备借出承诺（周五带安全带）全程守约。

## 4. no_reply / 媒体局限
- HER 全部为单条文本消息；无 no_reply 轮；may_decline 全部 false（每轮均为求助或汇报，宜回）。
- 局限：订单内容仅经 HER 转述引用，属刻意的信息二传；攻略帖内容未直接出现。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词或 initial_kb 的逐字子串并带 message_id；value 均 ≥4 实义词。
- required_this_turn 原子 7 条，全部引用早前轮次，未引当前轮。
- unknowns 明示未定项（改期是否收费、能否退货、登顶与否等），forbidden_factual_claims 禁虚构订单细节与预判成绩。

## 6. 已知风险
- 订单时间串（"周六 10:00-12:00 上午场"）含空格与冒号，语义审查时注意逐字匹配层级。
- 改期政策、装备细节为剧情化简化，不构成消费或运动指导。
- HER 台词中"三点固定"为安全带穿戴口诀的口语化说法，非专业术语校验点。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 逐轮单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(运动健身, 攀岩首刷+时间线倒错更正+改期补救+装备租赁, 时间线倒错更正类) 与既有篇三元组不同
