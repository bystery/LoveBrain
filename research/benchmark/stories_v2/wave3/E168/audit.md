# E168 审计（B 篇 / 跨故事对 E167+E168）

## 0. 性质与暴露
- author=AI，real_user_material=false：全部人物、台词、价格、场馆均为合成材料，无任何真实用户素材。
- 本篇为跨故事连续对之 B 篇（kind=cross_story_pair, pair=E167+E168, role=B），域=运动健身，冲突型=费用类。
- exposure.readers=["author"]，frozen_at=null，experiments_used=[]，dev_exposed=false。
- 隔离声明：本会话未读 logs/、candidates/、任何评审/判决文档；仅读白名单（生成清单、模板、各 manifest 的 story_id/title/themes 字段、校验器）。

## 1. 生活动机
- 室内攀岩体验课是 A 篇音乐节晒怕之后的自然转向（晒→室内），四人局由苗苗组织符合朋友圈成局常态。
- 费用冲突生活化：128 散客 vs 108 团购、锁位垫付、改期手续费二十元责任归属、3960 正式课 vs 2380 次卡的降级决策，全部是真实健身消费摩擦。
- 无虚构商家促销话术，费用数字前后严格对齐（108、40、20、3960、2380）。

## 2. 连续性与回扣
- B 篇含 4 组跨篇引用原子（cross_case_ref 标注 A 篇轮 id）：T01 攀岩局承接 E167_T11 苗苗提议；T03 玩笑引用 E167_T02 老纪换车；T04 兑现 E167_T12 酸汤牛肉承诺；T09 归还 E167_T05 的 40 元定金垫款（原话「你先帮我垫一下四十，回头发你」）。
- 机制说明：机械校验器要求 source.case_id 在本篇 case 集内，故跨篇原子的 source 指向 E168 内 HER 复述轮或 E168 initial_kb.lessons（lessons 载明 A 篇既成事实），cross_case_ref 字段记录 A 篇轮 id；未在 B 篇泄露 A 篇之外的未来剧情。
- 篇内跨轮引用 ≥3：T03 垫付 → T08 核对 → T12 结清；T06 手续费 → T07 认领 → T08 解除；T04 牛肉 → T11 定时 → T12 兑现。

## 3. ME 历史与边界
- ME 沿用 A 篇设定：留书面记录、爽快垫付、不催账（T08 核金额、T12 只对齐不念叨）。
- ME 边界：不替 HER 拍板 3960 正式课（T10 只参谋）；不占苗苗便宜（T07 AA 提议交 HER 决定）。

## 4. no_reply / 媒体局限
- 12 轮均为 reply 型；E168_T01（攀岩邀约）设 may_decline=true，合法婉拒不扣分。
- 无图片/语音/位置媒体输入；支付记录仅口头核对，不假装可见转账截图。

## 5. 参考卡质量
- 每轮 current_action+acceptable_actions≥2+dimensions≥3；原子 quote 逐字、message_id 全部 m0（单消息轮）；required_this_turn 原子均取自更早轮或 initial_kb，无一取自当前轮。
- protected_expressions 逐字取自该轮 HER 台词；unknowns 明示（改期规则、购买状态等）。

## 6. 已知风险
- 金额链（108→T08 核对→T12 结清；40→T09 归还→T12 确认；20→T07 认领→T08 解除）数字必须一致，评审防漂移。
- 跨篇原子依赖 initial_kb.lessons 与 HER 复述，若评审只认 source.case_id 会低估跨篇引用数量，audit §2 已作机制说明。

## 7. 出口检查
- 语义复审补记（2026-11-02，W03-06 Wave3 T2 S2）：T08"你昨天垫的108"与 T03 垫付日 12-01（周二）不符，按 ts 级时间指称改"你周二垫的108"，timeline 同步；与 gold A18"周二垫付"对齐。跨篇引用（A02/A03→E167_T11、A07/A08→E167_T02、A09/A10→E167_T12、A19/A20→E167_T05）逐条对 A 篇原文核验通过，无未来剧情泄露。
- episode/reference/timeline/exposure/audit 五件套齐；initial_kb 仅用允许键（me/her/warmth/style/stage/lessons）；无评审术语词汇。
- (运动健身, 室内攀岩主题集, 费用类) 与全库既有篇三元组至少一元不同；与 A 篇主题集差异明显。
