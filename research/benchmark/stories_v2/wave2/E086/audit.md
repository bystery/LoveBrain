# E086 表哥非要跟 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 skeleton 的 story_id/themes 字段 + wave1 E059 格式参照）。
- 全部人物、绿道、游船、烤肠摊均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：运动健身；冲突型：第三方插入类。HER 乔乔与 ME 约好周日两人轻装骑行芦苇荡看日落；表哥大壮刷到朋友圈强行求入，HER 出于亲戚情面接纳，义务随之重排——ME 转领骑、她转押队、备用头盔借出、刹车检修责任落到 ME；半程大壮体力崩盘，无退路预警应验，以"推车至码头乘游船带车返回"体面退场，两人保住日落之约；大壮以押队费红包与换车flag完成义务闭环。
- 生活动机真实可信：健身卡新人强行跟骑、亲戚情面难拒、退场要体面，是熟人户外活动的典型难题。

## 2. 连续性与回扣
- T01 计划发起 → T02 分工时限 → T03 第三方求入 → T04 拍板与义务重排 → T05 车况警报 → T06 检修完成 → T07 出发 → T08 半程爆雷 → T09 水路变通 → T10 日落兑现 → T11 红包了结 → T12 复盘新规与烤肠欠账收尾。
- 跨轮引用原子 8 条（A18/A25/A28/A29/A31/A36/A38/A39）≥3。
- 冲突型落实（第三方插入归属清晰：大壮由 HER 主动引入，引入与退出的义务变动均归 HER 侧消化）：T04 A13（retracted+negation，两人轻装方案推翻）；T08 A24（negation，大壮自称不是骑车的料）、A25（confirmed，无退路预警应验）；T09 A29（suspended+negation，加入意愿中止）；T11 A36（confirmed，情面双向落地）。

## 3. ME 历史与边界
- ME 老代：骑行五年、借出装备必先检查、分工认位不认人（initial_kb.me/style/warmth）。
- T03 may_decline=true：带不带表哥的决定权在 HER，ME 只摆代价不拍板。
- ME 全程不越权：不替她拒绝亲戚、不虚构游船班次与价格、不预判大壮体能；安全预警（不通车、刹车）均为事实性提醒而非吓阻。

## 4. no_reply / 媒体局限
- HER 全部为单条文本消息；无 no_reply 轮；仅 T03 may_decline=true。
- 局限：大壮的原话与游船信息均经 HER 转述，属刻意的信息二传；检修操作仅以结果呈现。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- required_this_turn 原子 8 条，全部引用早前轮次，未引当前轮。
- unknowns 明示未定项（游船班次、刹车是否需换新、大壮体能底线等）。

## 6. 已知风险
- 骑行速度、里程、日落时间（17:48）为剧情设定，不构成骑行指导。
- "表哥条款""水路骑行第一人"为玩笑式社群语言，语义审查时勿当事实断言。
- 红包金额未写明，为刻意留白。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 逐轮单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(运动健身, 骑行团+第三方插入+装备借用+无退路预警+复盘新规, 第三方插入类) 与既有篇三元组不同
