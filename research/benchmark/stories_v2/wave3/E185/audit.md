# E185 腰伤按下暂停键 审计

## 0. 性质与暴露
- author: AI（隔离生成会话 W3-T4-2）
- author_type: 全合成人物、全合成情节
- real_user_material: false（无任何真实用户素材、真实人名、电话、住址）
- 域: 运动健身；冲突型: 目标启停类；主题集: 三个月增肌计划、双人训练打卡、腰伤停训、康复降档、十一月重启
- 暴露: readers=["author"]，frozen_at=null，experiments_used=[]

## 1. 生活动机
HER 的动机链：以'年底深蹲50公斤'的立约驱动打卡，受伤后经历停训-上头-降档-复查提前-重启的完整启停弧。每轮 HER 的输入都由该动机推动，冲突落实轮（E185_T04/E185_T09）的原子含状态变化或否定，直接改变后续可行动作空间。

## 2. 连续性与回扣
12 轮按时间单调推进，跨轮回扣点：T02乳清/硬拉伏笔→T04停训→T06康复方案→T07降档课表→T08复查提前→T09新期限→T10乳清拼单→T11二四制正课→T12面约。timeline 与 message_index 由 episode 逐字生成，events/revisions 记录关键转折。
- 审查修订（2026-11-02，S5）：T11 明确第一次正课=下周二（11-17），T12 原文"今晚练完"（11-16 周一）与之矛盾→ts 级修正为"明晚练完"（E163 先例）；reference T12_A23 valid_time 2026-11-16晚→2026-11-17晚、value 补"正课当晚"，acceptable"确认今晚见"→"确认明晚正课见"；episode/runner_copy/timeline 三处级联。

## 3. ME 历史与边界
ME 侧设定：五年训练习惯的固定搭子，负责保护动作，边界是不碰药物增补、不替她决定弃练。ME 不替 HER 拍板涉及她身体的决定、不代投家人意见；私人目标只影响口吻，不改写事实。

## 4. no_reply / 媒体局限
may_decline 轮：E185_T05, E185_T10。无图片/语音/位置等媒体输入，全部为文本消息；未声明任何不可见信息被候选看到。

## 5. 参考卡质量
每轮 current_action 唯一，acceptable_actions ≥2 且互斥可选；applicable_dimensions ≥3；required_memory_atoms 带 source.quote 逐字子串且 message_id 取首现；跨轮引用原子 ≥3；冲突型落实轮原子含 status 变化或 negation=true。protected_expressions 均为该轮 HER 台词逐字子串。

## 6. 已知风险
腰伤康复节奏为虚构医学设定，参考卡已把安全口径（不跳空杆、不停复查）写进禁止项。

## 7. 出口检查
- 机械校验：tools/validate_story_v2.py（--stage wave3 --turns 12 --count 4 --manifest-name STAGE1_MANIFEST_T4G2.json）通过后入库。
- (域, 主题集, 冲突型) 三元组与既有篇至少一元不同；主题集与同域既有篇 Jaccard < 0.6。
- 数字口径自洽（深蹲40→45→50公斤、期限12月20日改12月27日，无金额账目）；gold 全空；台词零评审术语。
