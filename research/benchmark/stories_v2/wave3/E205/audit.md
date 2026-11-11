# E205 审计报告 —《牵绳这条线》

## 0. 性质与暴露
- author=AI;author_type=ai_generated;real_user_material=false。
- 全部人物(苏蔓、陈屿、豆豆、汤圆、小白)均为合成,无真实人名/电话/住址。
- exposure.json:readers=["author"],frozen_at=null,kind=standard_12t,conflict_type=边界类,experiments_used=[]。
- 本篇为 wave3 标准 12 轮故事,域=宠物,冲突型=边界类。

## 1. 生活动机
- 苏蔓:想让豆豆玩得尽兴、撮合两只狗认识,同时对规则心存侥幸。
- 陈屿:保护应激中的汤圆,守住牵绳边界,同时不想因边界破坏暧昧关系。
- 冲突动机真实:都市小区遛狗牵绳之争是常见生活情境,双方动机均有正当性。

## 2. 连续性与回扣
- 跨轮回扣链:T01 汤圆躲人 → T02 上月被扑旧事 → T06 萨摩小白事件 → T08 物业群投诉 → T11 小白再现时提前收绳 → T12 红榜收尾。
- 时间线:2026-11-03 至 2026-11-15,单调递增,无回拨;2026-11-08 为周日,错峰安排为早七点。
- T06 边界落实轮:HER 牵绳承诺被违反(negation=true,status=violated),ME 当轮声明边界。

## 3. ME 历史与边界
- ME 历史:上月在花园被未牵绳大狗扑过(汤圆视角),十月新规;均来自 initial_kb,首现可查。
- ME 边界:牵绳是硬边界,但表达方式给台阶(错峰方案);不见于台词的私人目标只写进 private_guidance。

## 4. no_reply / 媒体局限
- 全部 12 轮 turn_type=reply,无 no_reply 轮;T03/T10 为可婉拒邀约(may_decline=true)。
- 无图片/语音/位置媒体轮;台词均为纯文本微信式口语。
- 未使用 intent 字段;input.note 未使用。

## 5. 参考卡质量
- 每轮 acceptable_actions≥2 且不重复;applicable_dimensions≥3 且均在九维枚举内。
- required_memory_atoms 含逐字 quote(逐字子串校验通过),message_id 与首现位置一致。
- 跨轮引用原子(A05/A08/A09/A10/A12/A13/A15/A17/A21/A23/A25 等)≥3,覆盖回扣与记忆依赖。
- T06 冲突原子 E205_A11 negation=true、status=violated,满足冲突型落实要求。
- unknowns 明示:扑狗主人身份、抽查形式等未定项未当事实写入。

## 6. 已知风险
- '豆豆是否就是扑汤圆的狗'全篇保持未确证,参考卡已禁止过早定论。
- T06 情绪强度较高,若裁判按'必须立刻原谅'打分会低估边界轮价值,已在 private_guidance 说明。
- 台词零评审术语;未出现计分/观测类词汇。

## 7. 出口检查
- 五件套齐全:episode/reference/timeline/audit/exposure + runner_episode.json(与 episode.json 内容一致)。
- 三哈希(episode/reference/timeline)写入 STAGE1_MANIFEST_T4G7.json。
- (域,主题集,冲突型)三元组与既有宠物篇(E055/E056/E057/E080/E081/E101/E102/E113/E123/E124/E148/E159/E172)至少一元不同;主题集与同域既有篇 Jaccard<0.6。
- 数字自洽:本篇无算术账目;豆豆'超重两斤'为单一事实无连锁计算;11-08/11-15 均为周日,11-14 为周六,与台词一致。
