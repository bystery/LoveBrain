# E060 搬家的三次改期 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 manifest 的 story_id/title/themes 字段）。
- 全部人物、小区、平台、司机均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家庭事务；主线：HER 小鹿周六搬家遇货车连环改期——9点改11点半撞电梯预约、车辆追尾整单取消、重下单又遇司机调岗，三单三变后在原师傅私人援手（同行小车两趟）下赶在周日上午十一点交房死线前搬空，押金全退，平台退还80元加价。
- 生活动机真实可信：搬家平台调度、物业货运电梯时段、房东收房死线，是城市租房搬家的高频摩擦。

## 2. 连续性与回扣
- T01 计划锁定 → T02 改期一（撞电梯）→ T03 电梯改约失败 → T04 改期二（整单取消撞死线）→ T05 重下单加价80 → T06 电梯改约+ME升总指挥 → T07 改期三（司机调岗）→ T08 师傅援手两趟方案 → T09 易碎品先行+夜宿新家 → T10 收官验收押金全退 → T11 80元退回+好评 → T12 纸箱晚餐收尾。
- 跨轮引用原子 8 条（A05/A09/A11/A15/A20/A24/A27/A32）≥3。
- 冲突型=取消改期类落实：E060_A05（superseded+negation，9点安排被打破）、A11（superseded+negation，二次方案落空）、A19/A20（suspended，第三单悬置）、A24（superseded，改派悬念被私人方案接手）、A28/A29（confirmed，收官）、A32（superseded+negation，加价被退回）。

## 3. ME 历史与边界
- ME 阿远：前同事、擅长与物业司机这类第三方打交道，出主意不出钱、跑腿可以（initial_kb.me）。
- T06 may_decline=true（总指挥请求），参考卡给出答应+分工草案；ME 全程不替她付钱、不替她跟平台交涉，只给可行动作并到场跑腿。
- ME 不虚构自己无法到场或额外垫付等情节，角色限于信息支持与现场验收。

## 4. no_reply / 媒体局限
- 12 轮全部为 HER 单条文本消息，无语音/图片（订单页、合同条款均以转述呈现）。
- 未设 no_reply 轮。局限：司机、平台客服、物业、房东的发言全部经 HER 转述，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T02（A04 superseded / A05 superseded+negation）、T04（A10 cancelled / A11 superseded+negation）、T07（A19/A20 suspended+negation）、T08（A24 superseded）、T11（A32 superseded+negation）。
- 跨轮引用原子 8 条，≥3 条红线达成；required_this_turn 原子 8 条，全部来自早前轮次。

## 6. 已知风险
- 时间线密度高（9:00→11:30→取消→周日8点→6:30两趟），已按"周六电梯9-11、下午13-17被装修占用、周日7-10电梯、周日11点交房"自洽，语义审查时可重点复核。
- 平台调度失误补偿（退80+赠券）为剧情内设定，非真实平台政策。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家庭事务, 搬家死线+货车改期+平台调度, 取消改期类) 与既有篇三元组不同
