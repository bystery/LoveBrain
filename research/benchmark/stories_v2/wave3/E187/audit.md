# E187 后备箱与第三单 审计

## 0. 性质与暴露
- author: AI（隔离生成会话 W3-T4-2）
- author_type: 全合成人物、全合成情节
- real_user_material: false（无任何真实用户素材、真实人名、电话、住址）
- 域: 出行拼车；冲突型: 边界类；主题集: 周五跨城顺风车、三单拼车边界、行李箱权属、拒绝顺路捎货、平台规则退款
- 暴露: readers=["author"]，frozen_at=null，experiments_used=[]

## 1. 生活动机
HER 的动机链：以'每周五准时到家'驱动订车，面对捎货与半退的边界施压，走平台规则拿到全额退款并沿用口径。每轮 HER 的输入都由该动机推动，冲突落实轮（E187_T04）的原子含状态变化或否定，直接改变后续可行动作空间。

## 2. 连续性与回扣
12 轮按时间单调推进，跨轮回扣点：T01订票158→T02第二单→T03捎货减20→T04拒绝与规则→T05半退→T06留证→T07换皮→T08改约188全额退→T09判决→T10到账→T11只拼一单沿用→T12返程同行。timeline 与 message_index 由 episode 逐字生成，events/revisions 记录关键转折。

## 3. ME 历史与边界
ME 侧设定：同小区常拼同一线路的老熟人，熟悉平台规则，边界是不私下改交易条件。ME 不替 HER 拍板涉及她身体的决定、不代投家人意见；私人目标只影响口吻，不改写事实。

## 4. no_reply / 媒体局限
may_decline 轮：无。无图片/语音/位置等媒体输入，全部为文本消息；未声明任何不可见信息被候选看到。

## 5. 参考卡质量
每轮 current_action 唯一，acceptable_actions ≥2 且互斥可选；applicable_dimensions ≥3；required_memory_atoms 带 source.quote 逐字子串且 message_id 取首现；跨轮引用原子 ≥3；冲突型落实轮原子含 status 变化或 negation=true。protected_expressions 均为该轮 HER 台词逐字子串。

## 6. 已知风险
平台规则口径为该故事内部设定，参考卡要求引用时与HER台词逐字一致，不外推具体平台名与条款号。

## 7. 出口检查
- 机械校验：tools/validate_story_v2.py（--stage wave3 --turns 12 --count 4 --manifest-name STAGE1_MANIFEST_T4G2.json）通过后入库。
- (域, 主题集, 冲突型) 三元组与既有篇至少一元不同；主题集与同域既有篇 Jaccard < 0.6。
- 数字口径自洽（原单158元、新单188元、全额退158元，净增支出30元且未宣称其他口径）；gold 全空；台词零评审术语。
