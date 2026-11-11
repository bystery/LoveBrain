# E167 审计（A 篇 / 跨故事对 E167+E168）

## 0. 性质与暴露
- author=AI，real_user_material=false：全部人物、台词、金额、车次均为合成材料，无任何真实用户素材。
- 本篇为跨故事连续对之 A 篇（kind=cross_story_pair, pair=E167+E168, role=A），域=出行拼车，冲突型=第三方类。
- exposure.readers=["author"]，frozen_at=null，experiments_used=[]，dev_exposed=false。
- 隔离声明：本会话未读 logs/、candidates/、任何评审/判决文档；仅读白名单（生成清单、模板、各 manifest 的 story_id/title/themes 字段、校验器）。

## 1. 生活动机
- 周末跨城音乐节是 HER（策展助理、爱凑音乐节）的自然周末；拼车省钱顺路符合年轻同事友群常态。
- 冲突来源生活化：拼车群主临时换车换司机、出发时间反复、定金不退的拉扯，都是拼车场景的真实摩擦。
- 第三方（老纪、苗苗、表弟）只作背景与传话，不喧宾夺主。

## 2. 连续性与回扣
- 篇内跨轮引用 ≥3：T05 定金垫付 → T08 赔偿对比 → T12 账目回收；T06 C5671 → T07 改签；T11 攀岩局 → T12 酸汤牛肉钩子。
- 跨篇铺垫：T11 苗苗提议室内攀岩局（E168 主线）、T12 定金 40 元缓结 + 酸汤牛肉承诺（E168 归还与兑现）。B 篇不泄露 A 篇未来剧情（A 篇在 B 篇已是既成过去）。
- 机制说明：机械校验器要求 source.case_id 必须在本篇 case 集内，故 E168 的跨篇引用原子以 source 指向 E168 内 HER 复述轮或 E168 initial_kb.lessons（lessons 记载 A 篇既成事实），并在原子附加 cross_case_ref 字段标注 A 篇轮 id（如 E167_T05）。

## 3. ME 历史与边界
- ME 设定为结构工程师、爱留书面记录、愿意垫付：T05 垫付、T08 书面留痕式参谋均由此出发。
- ME 边界：不替 HER 在群里开骂（T08/T09）、不催她还 40 元（T12）；安全问题上不逞强（T04）。

## 4. no_reply / 媒体局限
- 12 轮均为 reply 型；E167_T11（攀岩邀约）设 may_decline=true，合法婉拒或犹豫不扣分。
- 无图片/语音/位置媒体输入；改签截图仅口头提及，不假装可见其内容。

## 5. 参考卡质量
- 每轮 current_action+acceptable_actions≥2+dimensions≥3；原子含逐字 quote 与 message_id，required_this_turn 原子均取自更早轮或 initial_kb。
- protected_expressions 逐字取自该轮 HER 台词；unknowns 明示未定项（驾龄未经实证、票价未定等）。

## 6. 已知风险
- 金额与车次（C5671、198、40、10 元券）需在 B 篇严格对齐，防数字漂移。
- 第三方（老纪/苗苗/表弟）线索多，评审需防止把转述当实证（atoms 已用 status=uncertain 标注苗苗转述）。

## 7. 出口检查
- episode/reference/timeline/exposure/audit 五件套齐；initial_kb 仅用允许键；无评审术语词汇；quote 均为台词逐字子串。
- (出行拼车, 音乐节拼车主题集, 第三方类) 与全库既有篇三元组至少一元不同。
