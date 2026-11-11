# E089 寿宴不是八号 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 64 篇 story_id/title/themes 防撞字段 + E059 格式参照）。
- 全部人物、蛋糕店、酒店订单均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家庭事务；冲突型：时间线倒错更正类。HER 小满把外婆九十寿宴记成11月8日，车票、蛋糕、接站承诺全部按错误日期承载；10月28日酒店订单实证纠正为11月1日，错误时间上的全部承诺同时引爆：改签费、蛋糕改单差价、撤调休、群里收话四线并起。
- 生活动机真实可信：把"上旬、8号前后"听成"8号"是口语转述最典型的日期吞噬；为外婆庆寿的用心与被打乱的手忙脚乱都出自同一份心意。

## 2. 连续性与回扣
- T01 日期承载 → T02 承诺加码 → T03 实证引爆 → T04 损失盘点 → T05 改期落地 → T06 群内甩锅 → T07 自嘲灭火 → T08 蛋糕确认与外婆来电 → T09 三百块两难 → T10 寿宴落成 → T11 红包转围巾 → T12 寿桃蛋糕致谢收尾。
- 跨轮引用原子 8 条（A10/A11/A12 源T01、A22 源T06、A25 源T05、A28 源T05、A30 源T01、A35 源T10、A38 源T10）≥3。
- 冲突型落实：E089_A07（revised+negation，日期实证更正）、A09（suspended+negation，错误计划悬空）、A10/A11/A12（suspended+negation，票/蛋糕/接站三项待改）、A13（retracted+negation，调休撤回）、A30（retracted+negation，接站承诺作废）。

## 3. ME 历史与边界
- ME 老柯：大学同学、张罗过大酒席，先列清单再动手（initial_kb.me/warmth）。
- T09 与 T12 may_decline=true：T09 收不收钱可暂不表态；T12 赴约属顺路小事，参考卡保留答应路径。
- ME 全程不替她拍板收不收钱、不替二姨定性，改签金额只复述 HER 给出的数字。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，全部为文本；酒店订单、蛋糕、围巾均以转述呈现，无图片消息。
- 未设 no_reply 轮。局限：家庭群对话全部经 HER 转述并引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A07 revised+A09 suspended）、T04（A10/A11/A12 suspended+negation、A13 retracted）、T07（A22 confirmed 化解）、T10（A30 retracted）。
- 跨轮引用原子 8 条，≥3 条红线达成；required_this_turn 原子 9 条（T04×3、T07、T08、T09、T10、T11、T12），全部源自早前轮次。

## 6. 已知风险
- 二姨、表妹、外婆的原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 车票改签、蛋糕改单流程为剧情化简化处理，不构成票务或商户规则指导。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家庭事务, 外婆寿宴+日期倒错更正+票务蛋糕改期+礼物闭环, 时间线倒错更正类) 与既有篇三元组不同
