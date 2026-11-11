# E081 楼道里那一爪 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审判决文档，仅使用任务书白名单（模板 + dedupe JSON 的 title/themes 字段 + E059 格式参照）。
- 全部人物（阿澄、老唐、陈姐及其老公、刘管家）、小区与业主群均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：宠物；冲突型：第三方插入类。主线：遛狗电梯口爪子刮伤邻居陈姐→本人当场和解→陈姐老公深夜在业主群改写事件性质（第三方实质性改变义务口径）→物业升级为三方调解→调解达成不索赔加戴嘴罩条款→老公散场追加两千元营养费→陈姐两次切割并公开在群里收口→排骨汤与冻干回扣→义诊遛狗之约收尾。
- 生活动机真实可信：养狗人最怕的不是狗出事，而是'当事人说没事、旁人不认账'的责任口径分叉。

## 2. 连续性与回扣
- T01 刮伤→T02 和解假象→T03 第三方群内改写→T04 私聊还原→T05 物业调解启动→T06 证据备齐→T07 调解条款+老公追加要求→T08 陈姐切割→T09 条款首日执行→T10 陈姐群里公开收口→T11 排骨汤冻干回扣→T12 义诊邀约收尾。
- 跨轮引用原子 8 条（A08/A16/A17/A24/A28/A34/A37/A40）≥3。
- 第三方插入落实：陈姐老公（T03/T07 两次改变信息与义务）、物业刘管家（T05/T06 升级程序）、另位邻居（T10 噪声源）、陈姐本人（T04/T08/T10/T11 归位主体）；ME 需把'THIRD 内部'的声音分开归属。

## 3. ME 历史与边界
- ME 老唐：同小区老友、懂物业流程、习惯分事实和气话（initial_kb.me/warmth）。
- T12 may_decline=true：邀约可按日程自定；其余轮次均需回应。
- ME 全程不代言陈姐家、不建议私下转账了事、不替物业预判调解结果；对两千元只做'不当场答应也不当场翻脸'的护栏。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，T03/T07 各两条补发；全部为文本，无语音图片（群消息与照片均为转述）。
- 未设 no_reply 轮。局限：陈姐老公的群内原文与陈姐原话均经 HER 转述引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互不重复、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A08 negation）、T05（A16 negation）、T07（A22 negation）、T08（A25 negation + A28 suspended）、T10（A34 superseded+negation）。
- required_this_turn 原子全篇 7 条，全部引用早于当前轮的来源。

## 6. 已知风险
- 陈姐老公的群内原话与'营养费'要求均为 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 爪刮伤与'咬伤'的措辞差是故事内核心事实分歧，参考卡已禁止把刮伤升格为咬伤。
- 两千元营养费最终未决（suspended），属留白而非悬案漏洞。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调不减、角色 HER、口语化台词、gold={}
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=宠物）/author=ZCode isolation worker-AI/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(宠物, 遛狗刮伤责任协商+业主群风波+物业调解, 第三方插入类) 与既有 48 篇三元组至少一元不同
