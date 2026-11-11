# E192 审计（《笔记本寄修两改期》）

## 0. 性质与暴露
author=AI，real_user_material=false；readers=["author"]，frozen_at=null，experiments_used 空。全合成人物，无真实人名电话住址。kind=standard_12t，conflict_type=取消改期类，domain=数码消费。

## 1. 生活动机
插画师的吃饭家伙笔记本屏幕故障，官方上门检测被取消一次、再改期一次后转寄修；全程以数据备份、留证（截图、封箱视频、工单号）和保内零费用收尾——真实的数码服务冲突与止损闭环。

## 2. 连续性与回扣
取消改期链条完整：T01 预约周六上午上门 → T05 上门取消（status=cancelled + negation）改约周一上午（status=rescheduled）→ T07 二次改期转寄修（status=rescheduled，跨轮引用 T05 的周一改约）→ T09 寄出留证 → T11 保内免费换屏发回。时间自洽：改约周一上午为十一月十日，二次改期发生在十一月九日早；预计七至十个工作日返回，实际十一月十二日发回、十三日到货（快于预期）。跨轮引用原子 ≥3：T05→T01 原预约（required）、T07→T05 改约（required）、T10→T09/T11→T09 寄修状态（required）、T12→T03 备份进度（required）。

## 3. ME 历史与边界
老魏的主意全部先给最坏情况：备完前不送修、封箱拍视频、故障描述宁短勿长、报价先核保内保外；尊重她"不用第三方维修、恢复出厂要书面确认"的边界，不催决定。

## 4. no_reply / 媒体局限
无 may_decline 轮次：T01 报障求助、T05/T07 改期坏消息、T11 好消息汇报、T12 致谢邀约均需回应；本篇未使用图片/语音媒体。

## 5. 参考卡质量
每轮 current_action + ≥2 acceptable_actions，维度 ≥3；冲突型为取消改期类，落实轮（T05/T07）原子带 status=cancelled/rescheduled 与 negation 标记；跨轮原子 required_this_turn 均指向更早轮；protected_expressions 仅取自当轮 HER 台词；unknowns 明示故障原因、往返时长、报价单等未定项。

## 6. 已知风险
候选若声称周六或周一上门照常进行、运费由她承担、维修产生了费用，参考卡已用 forbidden_factual_claims 与 status/negation 原子拦截；维修中心地址与屏幕型号刻意留空，不得虚构。

## 7. 出口检查
12 轮、时间单调（2026-11-06 至 2026-11-13）、gold={}、feedback 每轮有 her_state/trigger 变化标注；台词无评审术语、无真实人名；机械校验由 tools/validate_story_v2.py（stage=wave3, turns=12, count=4）执行。
