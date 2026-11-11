# E075 烧烤账算明白 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审/判决文档，仅使用任务书白名单（模板 + dedupe JSON 的 title/themes 字段 + wave1 E059 格式参照）。
- 全部人物（大伟、阿岳、王哥）、绿堤营地、金额均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：餐饮聚会；主线：HER 米粒张罗 10 月 24 日绿堤营地烧烤，垫 840 立起 8 人 AA 口径；人数 8 变 9、超时加收、两人缺席三重变动叠加后当晚算懵求援；睡醒后启用"爽约照摊"群规矩收敛为每人 80 整；阿岳私聊求免单，她守规矩但递"下回啤酒他包"的台阶；押金 180 冲抵垫付废止旧退款方案，账目表格公示获群里认可，红包兑现台阶，下一局升级为"不垫先收"。
- 费用类的关键设定：费用口径三次立起三次变（82.5→73→80；退款方案→冲抵），每一步都被引用与推翻，账目全程可复算（垫900=660+180+60；实付720=660+60；720/9=80）。

## 2. 连续性与回扣
- T01 垫付与AA立口径 → T02 人数变动重算 → T03 超时预告 → T04 乱账求援 → T05 口径收敛80整 → T06 免单请求 → T07 不免+台阶 → T08 收款与退款 → T09 押金冲抵两清 → T10 账目公示 → T11 台阶兑现 → T12 不垫新规收尾。
- 跨轮引用原子 6 条（A15/A18/A21/A26/A29/A32/A35，其中 6 条 required）≥3。
- 冲突型落实：E075_A15（superseded+negation，73 元口径被取代）、A18/A21（爽约规矩被撬动后确认守住）、A26（superseded+negation，旧退款方案废止）、A29（80 整方案执行落地）。

## 3. ME 历史与边界
- ME 老潘：做过财务、核账从不出错、口头禅"账平了，话才好说"（initial_kb.me）。
- T06 与 T12 may_decline=true：T06 只给两种选择的代价、决定权还她；T12 被邀作证，参考卡保留应下与调侃两条路径。
- ME 全程不替她拍板阿岳那份怎么算（T04 关键）、不虚构营地收费项与转账渠道细节；所有金额以 HER 流水转述为准。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，全部文本消息；账目表、红包均以转述呈现，无图片。
- 未设 no_reply 轮。局限：群规矩原始出处、营地通知均经 HER 转述，属信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥12 字。
- 冲突轮落实：T02（口径一变）、T04（费用叠加爆发）、T05（A15 superseded+negation）、T06/T07（规矩之争）、T09（A26 superseded+negation）。
- required_this_turn 原子 6 条（A15/A18/A21/A26/A29/A32/A35 中 6 个），全部来自早前轮次，6-9 区间内。

## 6. 已知风险
- 2026-10-24 为周六，与"周六去烧烤"一致；2026-11-02 为周一。
- 金额体系为剧情化设计但内部自洽（660+180+60=900；720/9=80；98-80=18），语义审查时可用作事实核对锚点。
- 群规矩"放了鸽子也摊场地钱"的原始出处未在故事内出现（设定为早于本篇的群公告），已列入参考卡 unknowns 提示防虚构。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注与费用类事件类型
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(餐饮聚会, 户外烧烤+场地押金+垫付理账+爽约摊费+账目闭环, 费用类) 与既有篇三元组不同
