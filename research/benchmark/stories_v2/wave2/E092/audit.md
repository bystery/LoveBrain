# E092 来几个做几个 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 64 篇 story_id/title/themes 防撞字段 + E059 格式参照）。
- 全部人物、室友群、离职传闻均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：餐饮聚会；冲突型：信息不确定类。HER 念念周日设室友重聚家宴，到场人数全程无实锤：小雅"可能带对象"、桃子"尽量"、阿禾只回表情、老周仅一个"哈"；老周的缺席传闻是二手消息，她选择不问破；人数在 6→5→6 之间反复，她按最大口径备菜并随动微调；老周当天"放下东西就走"却坐下剥蒜一下午；事后解码：没人报数是众人不忍戳他的心事。
- 生活动机真实可信：熟人饭局的人数悬置几乎人人遇过，信息不确定的根源是体谅而非怠慢。

## 2. 连续性与回扣
- T01 家宴启动 → T02 人数含糊按6备 → T03 不问破决定 → T04 无人报数 → T05 大口径采购 → T06 小雅落定微调 → T07 桃子母加入 → T08 老周"就走"加备 → T09 满座战报 → T10 沉默解码 → T11 下月掌勺与开口时机悬置 → T12 萝卜干与剥蒜故事回扣收尾。
- 跨轮引用原子 8 条（A10 源T03、A13 源T02、A17 源T05、A20 源T06、A23 源T03、A27 源T08、A30 源T04、A33 源T10、A36 源T09）≥3。
- 冲突型落实（双向禁定责）：E092_A04/A06（uncertain，人数与传闻全程无实锤）、A09（uncertain，无人报数）、A12（uncertain，杀鱼量待定）、A21（uncertain，老周"就走"口径）、A32（uncertain，开口时机悬置）；A14/A24/A25/A26（confirmed，逐步落定）。参考卡 forbidden 同时禁止断言老周必然缺席与必然到场的原因、禁止指责 HER 轻信与室友沉默。

## 3. ME 历史与边界
- ME 老原：室友群编外常客、饭桌不逼人谈近况（initial_kb.me/warmth）。
- T11 与 T12 may_decline=true：T11 装不知道到何时可由她自行定夺；T12 送萝卜干可自选。
- ME 全程不确认或否认离职传闻、不给沉默者归因、不评判多买与加备的对错，只给备菜活口与体面话术。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，全部为文本；室友群对话与私信均以转述呈现。
- 未设 no_reply 轮。局限：老周的近况全程只有二手转述与原话引用两层，属刻意的信息不确定设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词；信息不确定轮使用 uncertain 状态。
- 冲突轮落实：T03（A06 uncertain 传闻、A07 不问破）、T04（A09/A10 uncertain）、T07（A18 asserted 人数回升）、T08（A21 uncertain vs A22 加备决策）、T09（A24-A27 confirmed 落定）、T10（A28/A29/A30 confirmed 解码）。
- 跨轮引用原子 8 条，≥3 条红线达成；required_this_turn 原子 8 条（T04、T05、T06、T07、T08、T09、T11、T12），全部源自早前轮次。

## 6. 已知风险
- 离职话题为剧情化的敏感信息线索，语义审查时注意参考卡的双向禁定责边界（不替 HER 定性轻信、不替室友定性冷漠、不替老周宣布决定）。
- 人数轨迹 6→5→6 为剧情化简化，备菜开销不构成采购建议。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(餐饮聚会, 室友重聚家宴+帮厨备菜+到场人数不确定+沉默解码, 信息不确定类) 与既有篇三元组不同
