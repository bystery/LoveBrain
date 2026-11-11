# E186 代订的局多了两个人 审计

## 0. 性质与暴露
- author: AI（隔离生成会话 W3-T4-2）
- author_type: 全合成人物、全合成情节
- real_user_material: false（无任何真实用户素材、真实人名、电话、住址）
- 域: 餐饮聚会；冲突型: 第三方类；主题集: 大学室友十年聚、共同朋友代订、第三方擅自加人、大堂包间之争、AA预收口径
- 暴露: readers=["author"]，frozen_at=null，experiments_used=[]

## 1. 生活动机
HER 的动机链：以'十年聚必须办得规矩'驱动筹备，被第三方搅局后经对质-退出-补救局完成边界与礼数两清。每轮 HER 的输入都由该动机推动，冲突落实轮（E186_T04）的原子含状态变化或否定，直接改变后续可行动作空间。

## 2. 连续性与回扣
12 轮按时间单调推进，跨轮回扣点：T01代订安排→T02低消→T03预收1200与名单口径→T04包间被顶替加人→T06两条路→T07两桌与差价→T08退出退款150→T09补救局→T11垫付200两清→T12宵夜。timeline 与 message_index 由 episode 逐字生成，events/revisions 记录关键转折。

## 3. ME 历史与边界
ME 侧设定：社团出身的老朋友兼饭局参谋，边界是不当众撕破脸也不和稀泥。ME 不替 HER 拍板涉及她身体的决定、不代投家人意见；私人目标只影响口吻，不改写事实。

## 4. no_reply / 媒体局限
may_decline 轮：E186_T05。无图片/语音/位置等媒体输入，全部为文本消息；未声明任何不可见信息被候选看到。

## 5. 参考卡质量
每轮 current_action 唯一，acceptable_actions ≥2 且互斥可选；applicable_dimensions ≥3；required_memory_atoms 带 source.quote 逐字子串且 message_id 取首现；跨轮引用原子 ≥3；冲突型落实轮原子含 status 变化或 negation=true。protected_expressions 均为该轮 HER 台词逐字子串。

## 6. 已知风险
第三方代订与擅自加人为常见饭局摩擦，参考卡把'不当和事佬''不替第三方背书'写进禁止项。

## 7. 出口检查
- 机械校验：tools/validate_story_v2.py（--stage wave3 --turns 12 --count 4 --manifest-name STAGE1_MANIFEST_T4G2.json）通过后入库。
- (域, 主题集, 冲突型) 三元组与既有篇至少一元不同；主题集与同域既有篇 Jaccard < 0.6。
- 数字口径自洽（每人150元×8人=1200元，退款150元，垫付200元，低消688元，各处独立自洽）；gold 全空；台词零评审术语。
