# E090 床垫拉锯战 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 64 篇 story_id/title/themes 防撞字段 + E059 格式参照）。
- 全部人物、床垫来源、价格轨迹均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家庭事务；冲突型：目标启停类。HER 苗苗替三叔在家族群转卖闲置乳胶床垫：启动（900挂牌、表姑父拍板）→暂停（丈母娘需睡硬床顺延后退出）→变通（降价750如实吆喝）→重启（200定金锁期规则）→变数（全款700新买家撞上定金约定）→转成交（表弟妹主动退出、全额退款赔情）→收尾（跑腿费转红包、腌菜缸就位）。
- 生活动机真实可信：家族群卖闲置是熟人社会最典型的目标拉锯，价格、体面、人情三条线全在一单里。

## 2. 连续性与回扣
- T01 启动 → T02 成交在望 → T03 顺延挂起 → T04 正式暂停 → T05 降价变通 → T06 新意向 → T07 定金重启 → T08 双买家相撞 → T09 摊牌转成交 → T10 跑腿费两难 → T11 人情两头落定 → T12 腌菜缸与口碑回扣收尾。
- 跨轮引用原子 8 条（A08 源T02、A11 源T03、A14 源T04、A19 源T06、A22 源T07、A26 源T08、A28 源T09、A31 源T10、A34 源T01）≥3。
- 冲突型落实：E090_A06（suspended+negation，购买暂缓）、A09/A11（retracted+negation，退出与顺延作废）、A17（confirmed，定金规则）、A22（confirmed，规则约束生效）、A23（retracted+negation，表弟妹退出）、A24（confirmed，转售达成）。

## 3. ME 历史与边界
- ME 老申：老同学、处理过二手家具、懂定金尾款规矩（initial_kb.me/warmth）。
- T05、T10、T12 may_decline=true：T05 文案可慢慢斟酌；T10 收钱两难可暂不站队；T12 收尾玩笑可轻接。
- ME 全程不替她决定退不退定金、收不收跑腿费，只提供规矩骨架与优先级；不把亲戚行为定性为失信。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，全部为文本；床垫照片、腌菜缸照片、群内对话均以转述呈现。
- 未设 no_reply 轮。局限：家族群对话与电话内容全部经 HER 转述，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A06 suspended）、T04（A09/A11 retracted）、T07（A17/A18 confirmed）、T08（A20/A21 相撞）、T09（A23 retracted、A24 confirmed）。
- 跨轮引用原子 ≥3 条红线达成；required_this_turn 原子 9 条（T03、T04、T05、T07、T08、T09、T10、T11、T12），全部源自早前轮次。

## 6. 已知风险
- 表姑妈、表弟妹、三叔的原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 定金规则的合法性表述停留在民间约定层面，参考卡已明确不得断言法律效力。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家庭事务, 家族群二手转让+床垫价格轨迹+定金规则+人情闭环, 目标启停类) 与既有篇三元组不同
