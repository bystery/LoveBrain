# E188 六天改成四天 审计

## 0. 性质与暴露
- author: AI（隔离生成会话 W3-T4-2）
- author_type: 全合成人物、全合成情节
- real_user_material: false（无任何真实用户素材、真实人名、电话、住址）
- 域: 家庭事务；冲突型: 更正类；主题集: 元旦回家计划、停留天数更正、高铁票改签、家宴提前、四天清单收口
- 暴露: readers=["author"]，frozen_at=null，experiments_used=[]

## 1. 生活动机
HER 的动机链：以'答应爸妈的事必须给交代'驱动假期安排，公司通知引发六天改四天的逐项更正与清单收口。每轮 HER 的输入都由该动机推动，冲突落实轮（E188_T04）的原子含状态变化或否定，直接改变后续可行动作空间。

## 2. 连续性与回扣
12 轮按时间单调推进，跨轮回扣点：T01六天框架→T02三件承诺→T03家宴1月2日→T04更正四天→T06家宴提前1日→T07钓鱼挪31日→T08改签3号上午→T09姥姥正月补约→T11清单核对→T12出发。timeline 与 message_index 由 episode 逐字生成，events/revisions 记录关键转折。
- 审查修订（2026-11-02，S5）：T09 原文"1月3号就得 return"混入英文词→错别字级修正为"1月3号就得回来了"；episode/runner_copy/timeline 三处级联，reference 无 quote 覆盖该片段无需改。

## 3. ME 历史与边界
ME 侧设定：同乡老同学兼清单参谋，边界是替她理清单但不替她面对家人。ME 不替 HER 拍板涉及她身体的决定、不代投家人意见；私人目标只影响口吻，不改写事实。

## 4. no_reply / 媒体局限
may_decline 轮：E188_T10。无图片/语音/位置等媒体输入，全部为文本消息；未声明任何不可见信息被候选看到。

## 5. 参考卡质量
每轮 current_action 唯一，acceptable_actions ≥2 且互斥可选；applicable_dimensions ≥3；required_memory_atoms 带 source.quote 逐字子串且 message_id 取首现；跨轮引用原子 ≥3；冲突型落实轮原子含 status 变化或 negation=true。protected_expressions 均为该轮 HER 台词逐字子串。

## 6. 已知风险
改签差价口径按'同席别免费改签'设定，参考卡禁止编造手续费；公司盘点为合成情节不指向真实企业。

## 7. 出口检查
- 机械校验：tools/validate_story_v2.py（--stage wave3 --turns 12 --count 4 --manifest-name STAGE1_MANIFEST_T4G2.json）通过后入库。
- (域, 主题集, 冲突型) 三元组与既有篇至少一元不同；主题集与同域既有篇 Jaccard < 0.6。
- 数字口径自洽（12月31日去/1月3日返改签同席别无差价，停留四天与清单四天一致）；gold 全空；台词零评审术语。
