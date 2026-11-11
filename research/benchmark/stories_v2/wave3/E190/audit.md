# E190 审计（《熄灯前填错的科目》）

## 0. 性质与暴露
author=AI，real_user_material=false；readers=["author"]，frozen_at=null，experiments_used 空。全合成人物，无真实人名电话住址。kind=standard_12t，conflict_type=更正类，domain=学习成长。

## 1. 生活动机
教资面试报名在熄灯前十分钟抢填，科目下拉框点错；在"仅可改一次"的硬约束下完成截图留证、撤回更正、重报与盯审核，随后回到试讲陪练主线并修剪超时——真实的报错-更正-成长弧线。

## 2. 连续性与回扣
更正链条完整：T03 抢填不安伏笔 → T04 逐格截图自查法 → T05 发现误选（negation：科目误选为高中信息技术）→ T06 三步改错清单 → T07 更正完成（status=corrected）→ T09 审核通过与填表新规。数字自洽：试讲十分钟规定，T11 十四分钟超四分钟（14-10=4），T12 砍例后十一分钟还差一分钟（14→11 砍三分钟）。跨轮引用原子 ≥3：T05→T01 报名科目（required）、T06→T05 仅可改一次（required）、T07→T05 误选事实（required）、T09→T07 更正结果（required）、T11→T08 卡表规则（required）、T12→T11 超时基线（required）。

## 3. ME 历史与边界
老宋是流程控：给的是清单和卡表规则，不是情绪安慰；更正仅一次的约束下强调先留证再动手；修剪建议给选择不给命令，例子取舍由她定。

## 4. no_reply / 媒体局限
无 may_decline 轮次：T01 报喜邀约、T05 慌乱求助、T11 求修剪意见、T12 致谢邀约均需回应；本篇未使用图片/语音媒体。

## 5. 参考卡质量
每轮 current_action + ≥2 acceptable_actions，维度 ≥3；冲突型为更正类，落实轮原子带 status=erroneous/corrected 与 negation 标记；跨轮原子 required_this_turn 均指向更早轮；protected_expressions 仅取自当轮 HER 台词；unknowns 明示审核排队、彩排时间等未定项。

## 6. 已知风险
"仅可改一次"是本篇硬约束，候选若声称可多次修改或更正失败，参考卡已用 forbidden_factual_claims 拦截；试讲时长数字若被算错（如称超时五分钟）属事实错误。

## 7. 出口检查
12 轮、时间单调（2026-11-04 至 2026-11-12）、gold={}、feedback 每轮有 her_state/trigger 变化标注；台词无评审术语、无真实人名；机械校验由 tools/validate_story_v2.py（stage=wave3, turns=12, count=4）执行。
