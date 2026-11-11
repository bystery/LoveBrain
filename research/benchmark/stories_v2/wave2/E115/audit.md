# E115 报错了的那座山 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审 JSON，仅使用任务书白名单（模板 + 既有 dedupe 的 story_id/title/themes 字段 + wave1 E059 格式参照）。
- 全部人物、徒步团、线路数据均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：运动健身；主线：HER 大乔头脑一热报名周日徒步并邀 ME 陪走，她自述的线路是'6公里平路小环线'，并按此立flag（同事面前'周一满血'）、按此买鞋（网面透气）；补保险翻订单时实证更正——报的是青云线12公里爬升420米三星线，退改已关；flag、秋游、鞋全建在'平路'俩字上；四天内完成补救（换防滑鞋、改口预期、借杖、确认下撤点），当天完走12公里，flag以'轻松局是吹的，但我走完了'兑现，进阶邀约以'先练8公里'稳步收束。
- 生活动机真实可信：手滑报错团、牛皮先吹再圆、装备将错就错，是户外新手的经典事故组合。

## 2. 连续性与回扣
- T01 错误线路认知邀约 → T02 flag+错鞋加固 → T03 装备清单裂缝被误归因 → T04 订单实证引爆 → T05 弃走代价与后路策略 → T06 双重更正完成 → T07 出发日备装 → T08 过下撤点 → T09 完走 → T10 flag兑现+新规矩 → T11 进阶邀约求实话 → T12 稳步决策+秋游回扣收尾。
- 跨轮引用原子 6 条（A09/A18/A21/A26/A28/A30）≥3。
- 冲突型=更正类落实：T04（A08 confirmed+negation、A09 retracted+negation，线路认知被订单实证撤回）；T05（A13 confirmed，团费不退）；T06（A14 negation，鞋的错误被更正）；T08（A21 resolved，下撤后路随行程关闭）；T10（A26 confirmed，flag以更正口径闭环）。

## 3. ME 历史与边界
- ME 老康：十几年徒步老手、装备一抽屉借人大方（initial_kb.me），知道新手怕被吓退不怕被累退。
- ME 全程不代办：借杖可以，陪走策略给抓手（节奏、热身、清单），不替她决定进阶与否；T11 '别捧我'之下给了分层实话（不是不行，是不是现在）。
- 医学/安全边界：酸痛淤青只问诊不定性，不虚构路况与天气，不保证完走。

## 4. no_reply / 媒体局限
- HER 以单条长消息为主，T04/T05 为同日两连发，呈现'引爆→盘算'的真实节奏。
- 未设 no_reply 轮。局限：领队、导购、同事、群友的原话均经 HER 转述引用，属刻意的信息二传设计；轨迹截图、订单以转述呈现，无图片媒体。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T04（A08 negation、A09 retracted+negation）、T06（A14 negation 鞋更正）、T08（A21 resolved 后路关闭）、T10（A26 confirmed flag闭环）。
- 跨轮引用原子 6 条，≥3 条红线达成；required_this_turn 原子 6 条（T04/T07/T08/T10/T11/T12 各 1），全部来自早前轮次。

## 6. 已知风险
- 领队、导购、同事的原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 爬升/里程数据为剧情化设定，不构成户外安全指导；日期（2026-10-31 周六起、11-08 周日徒步）与真实日历一致。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=运动健身）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(运动健身, 徒步报错团+订单实证更正+装备补救+进阶决策, 更正类) 与既有 64 篇三元组至少一元不同
