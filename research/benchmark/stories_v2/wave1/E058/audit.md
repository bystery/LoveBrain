# E058 一份礼金的来回账 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 manifest 的 story_id/title/themes 字段）。
- 全部人物、金额、亲戚关系均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家庭事务；主线：表哥婚宴随礼，HER 小满面对三版费用口径（妈妈600、小姨行情800、账本来礼1000），依"还礼不低于来礼"定1000并自掏执行，被表哥统一规矩退回400实收600，婚宴后小姨群里挑刺被凭证否定，最终账差以人情备用金平账。
- 生活动机真实可信：随礼行情、还礼对等、长辈口径不一，是家庭人情往来的常见摩擦。

## 2. 连续性与回扣
- T01 妈妈版600 → T02 小姨版800 → T03 账本1000修正 → T04 承担方分歧 → T05 转账执行 → T06 退回400口径再修正 → T07-T08 婚宴与表哥背书 → T09 小姨挑刺 → T10 凭证否定 → T11 平账与账本传承 → T12 账本饭局收尾。
- 跨轮引用原子 9 条（A05/A08/A12/A15/A17/A21/A30/A33/A36）≥3。
- 冲突型=费用类落实：E058_A08（superseded+negation，600被1000推翻）、A17（superseded+negation，1000被统一规矩修正）、A30（retracted+negation，小姨指责被否定）、A33（confirmed，账差平账）。

## 3. ME 历史与边界
- ME 阿川：陪她理过人情账，原则是不替她定数、只摆账目与说法（initial_kb.me）。
- 全程 ME 不替她定金额、不替她回应小姨、不评判妈妈，T04 分歧处只摆利弊；T12 赴约是朋友份内事，不引入新的人情议题。
- initial_kb.warmth 明确"账本都肯给我看，但我只看不动手改"的边界。

## 4. no_reply / 媒体局限
- HER 连发两条消息的轮次：T01、T03、T06、T09、T10；全部为文本消息，无语音/图片（账本以转述呈现）。
- 未设 no_reply 轮。局限：亲戚群对话、转账凭证、账本页均经 HER 转述，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A08 superseded+negation）、T06（A17 superseded+negation）、T09-T10（A30 retracted+negation）、T11（A33 confirmed）。
- 跨轮引用原子 9 条，≥3 条红线达成；required_this_turn 原子 8 条，全部来自早前轮次。

## 6. 已知风险
- 金额口径多（600/800/1000/退400），已按"支出1000、实收600、退回400入备用金"自洽；语义审查时可重点复核。
- "亲妹妹上个月结婚也是随600"为表哥口述证据，剧情内事实，无真实对应。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家庭事务, 婚宴随礼+费用口径冲突+还礼旧账, 费用类) 与既有篇三元组不同
