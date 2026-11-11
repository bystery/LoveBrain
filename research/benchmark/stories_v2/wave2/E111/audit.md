# E111 定金付错了屏 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 dedupe JSON 的 story_id/title/themes 字段 + E059 五文件作格式参照）。
- 全部人物、平台活动、型号价格均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：数码消费；主线：HER 小周预售付定金时误点热销推荐，把八寸大屏付成六寸青春版，早期错误口径已承载两笔对外承诺（借小柯大屏看文献、读书群开箱长文）；实证更正引爆承诺压力，她以提前坦白改口径兑现、把糗事写成开箱标题；实测文献表格全裂后换机冲动复燃，又以重排文献的变通工程化解，最终八寸弃购、经验沉淀为对比单和'人工防呆'之约。
- 生活动机真实可信：付错配置、向朋友兑现不了当初的口径，是电商时代的常见更正题。

## 2. 连续性与回扣
- T01 预售立诺 → T02 实证更正 → T03 两条路摇摆 → T04 尾款定档+承诺挂账 → T05 坦白改口径 → T06 实测落差冲动复燃 → T07 忍住转重排 → T08 重排见效定位重置 → T09 经验成单 → T10 打卡小组 → T11 无门槛券回补 → T12 防呆之约收尾。
- 跨轮引用原子 9 条（message 跨轮 7 条：A09/A11/A21/A24/A27/A30/A33；initial_kb 跨轮 2 条：A18/A36）≥3。
- 冲突型=更正类落实：E111_A04（negation，八寸宣称被推翻）、A09（confirmed 更正前提）、A11（suspended+negation，借阅承诺无法照原样兑现）、A14/A15（confirmed，承诺改口径）、A19/A21（忍住+冲动撤下）、A24（retracted，表格全裂问题被化解）、A34（negation，八寸最终弃购）。

## 3. ME 历史与边界
- ME 苏航：用阅读器三年，习惯是下单前让人把链接发他过一眼（initial_kb.me），劝人先劝等一等、爱列一二三。
- T12 may_decline=true：防呆之约与咖啡客串属可轻答可玩笑的收尾场合。
- ME 全程不替她拍板付不付、不虚构平台规则与型号参数，只摆账、给核对动作。

## 4. no_reply / 媒体局限
- HER 每轮单条消息，全部为文本；订单页、对比单、开箱长文均以转述呈现，无截图类媒体。
- 未设 no_reply 轮。局限：订单详情与群内反应均经 HER 转述，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T02（A04 negation）、T04（A11 suspended+negation）、T06（A18 跨轮归因+A17 冲动复燃）、T07（A19 negation+A21 retracted）、T12（A34 negation）。
- 跨轮引用原子 9 条，≥3 条红线达成；required_this_turn 原子 8 条，全部来自早前轮次或 initial_kb。

## 6. 已知风险
- 平台活动规则、型号参数与价格均为剧情化设定，不构成任何购物指导。
- 小柯是 HER 的同事兼书友（THIRD），其原话均经 HER 转述，语义审查时注意引用层级。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(数码消费, 预售定金型号记错+承诺更正+经验清单+人工防呆, 更正类) 与既有 64 篇三元组至少一元不同
