# E191 审计（《人均四十五到六十》）

## 0. 性质与暴露
author=AI，real_user_material=false；readers=["author"]，frozen_at=null，experiments_used 空。全合成人物，无真实人名电话住址。kind=standard_12t，conflict_type=费用类，domain=兴趣社交（桌游搭子线）。

## 1. 生活动机
桌游吧包间低消三百六十元的八人局被临时放鸽两人，人均从四十五涨到六十；组织者垫付、带头认差价、当周对账、下局凑回八人——真实的搭子局费用冲突与信用修复。

## 2. 连续性与回扣
费用链条完整且算术自洽：T05 八人变六人（negation 否定原八人局，status=cancelled），低消三百六十除六得人均六十、较四十五多十五；T07 垫付三百六十、每人还六十；T09 收回五份共三百加自担六十恰为三百六十（5×60=300，360-300=60）；T11 新人补位八人齐、人均回四十五。跨轮引用原子 ≥3：T05→T01 原费用口径（required）、T06→T05 新人均（required）、T09→T07 垫付口径（required）、T11→T09 凑局目标（required）、T12→T11 新人入局（required）、T07→T03 速查表（required）。

## 3. ME 历史与边界
阿树的信用习惯：转账带备注从不过夜（T08），带头认差价给组织者搭台阶（T06），提议速查表群文件化让她省力（T10）；不凑生面孔拉人头，尊重她"费用说清不留糊涂账"的边界。

## 4. no_reply / 媒体局限
无 may_decline 轮次：T01 报喜、T05 放鸽坏消息、T09 对账汇报、T12 周六之约均需回应；本篇未使用图片/语音媒体。

## 5. 参考卡质量
每轮 current_action + ≥2 acceptable_actions，维度 ≥3；冲突型为费用类，落实轮（T05）原子带 status=cancelled 与 negation 标记，费用原子金额口径逐轮可核；跨轮原子 required_this_turn 均指向更早轮；protected_expressions 仅取自当轮 HER 台词；unknowns 明示缺席补偿、新人变卦等未定项。

## 6. 已知风险
候选若把人均算成六十以外的数、把对账总额说成三百六十以外，或声称缺席两人已补付，参考卡已用 forbidden_factual_claims 与原子口径拦截；桌游吧名称与地址刻意留空，不得虚构。

## 7. 出口检查
12 轮、时间单调（2026-11-04 至 2026-11-26）、gold={}、feedback 每轮有 her_state/trigger 变化标注；台词无评审术语、无真实人名；机械校验由 tools/validate_story_v2.py（stage=wave3, turns=12, count=4）执行。
