# E101 汤圆还没到家 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审判决文档，仅使用任务书白名单（模板 + W03-06_SKELETON_DEDUPE 的 story_id/themes 字段 + wave1/E059 格式参照）。
- 全部人物、猫舍、价格与诊疗细节均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false。

## 1. 生活动机
- 域：宠物；主线：HER 阿澄在软云猫舍订布偶猫付2000定金，11月底接猫；猫舍主理人琳姐（第三方）两次实质卷入改变义务——先以整窝猫瘟阳性隔离复查把接猫延期并抛出"定金不退"，再在复查阴性后以行情上涨要求到店补300；她从冲动退定金退到书面锁定承诺，再以一口价聊天记录让补价撤回；接猫后发现耳螨与驱虫承诺不符，以轻沟通换药费补偿收口。
- 生活动机真实可信：活体交易定金、书面承诺、临时加价是宠物消费最高频的纠纷形态；"吃软不吃硬、认书面承诺"的人物轴一致。

## 2. 连续性与回扣
- T01 定金+接猫窗口 → T02 接前承诺与取名 → T03 隔离延期（第三方插入一） → T04 止损征询 → T05 书面承诺锁定 → T06 复查阴性+补价（第三方插入二） → T07 价格证据 → T08 补价撤回 → T09 接猫+耳螨缺口 → T10 轻沟通补偿 → T11 痊愈+原则沉淀 → T12 见猫之约与"不打不成交"回扣收尾。
- 跨轮引用原子 7 条（A07/A13/A18/A21/A23/A33 及 initial_kb 的 A12）≥3。
- 冲突型=第三方插入类落实：T03 琳姐改变接猫义务（A07 suspended+negation）；T05 止损冲动 retracted（A13）；T06 琳姐临时补价与书面承诺冲突（A17 negation、A18 confirmed）；T08 补价要求 retracted（A21）；T09 承诺与现状不符（A23 negation）。

## 3. ME 历史与边界
- ME 小鹿：养过两轮猫，懂定金/疫苗/接猫流程；一贯"先保证据再谈情面，不替她拍板"（initial_kb.me，并在 T04 作为 required 原子进入参考卡）。
- T04/T07/T12 may_decline=true：T04 只摆推演不代拍板；T07 给开口方式不代写羞辱性话术；T12 见猫之约保留答应与改期双路径。
- ME 全程不虚构行规条文、不做医学诊断，耳螨表达限于"常见、可治、先取证"的口径。

## 4. no_reply / 媒体局限
- HER 每轮单条长消息，全部为文本；琳姐的原话、转账与截图均经 HER 转述呈现，属刻意的信息二传设计。
- 未设 no_reply 轮；无语音/图片通道。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互异、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词；每轮 unknowns/protected/forbidden 为列表。
- 冲突轮落实：T03（A07 suspended+negation）、T05（A13 retracted+negation）、T06（A17 negation、A18 confirmed）、T08（A21 retracted+negation）、T09（A23 negation）。
- 跨轮引用原子 7 条 ≥3；required_this_turn 原子全篇 7 条（A07/A12/A13/A18/A21/A23/A33），全部来自早前轮次或 initial_kb，6-9 红线达成。

## 6. 已知风险
- 猫瘟检测、耳螨为剧情化简化处理，不构成医学或活体交易指导；语义审查时注意引用层级（琳姐原话经 HER 转述引号）不构成虚构第三方直接发言。
- "渐暧昧"关系轴仅在 initial_kb 与 T12 见猫之约轻触，参考卡未强制升级关系，避免越界推进。

## 7. 出口检查
- [x] episode.json 顶层四键、12 轮、turn_type=reply、gold={}、ts≤clock 逐轮单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表、dimensions 在九维内
- [x] timeline.json 与 episode 机械一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=宠物）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(宠物, 活体购猫+定金书面承诺+改价更正+接前承诺缺口, 第三方插入类) 与既有篇三元组不同
