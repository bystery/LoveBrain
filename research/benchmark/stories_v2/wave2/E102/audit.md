# E102 门一直开着 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审判决文档，仅使用任务书白名单（模板 + W03-06_SKELETON_DEDUPE 的 story_id/themes 字段 + wave1/E059 格式参照）。
- 全部人物、仓鼠、线索与垃圾清运细节均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false。

## 1. 生活动机
- 域：宠物；主线：HER 栗子的仓鼠糯米清晨越狱，关键事实（在屋内/已逃出/是否存活）全程无实锤：瓜子壳、面粉无脚印、瓜子被搬、凌晨声响、垃圾袋沉了一点、窗纱破洞轮番出现，每条都两读并存；她从疯狂排查、不冤枉阿姨，到守株待鼠、封堵窗纱，最后再领养一只但保留糯米笼位粮照换，在'放下还是没放下'的自问中收束。
- 生活动机真实可信：小型宠物越狱排查与'无结论'的悬置感是真实养宠日常；她的底线（没证据不冤枉人）贯穿 T06-T08。

## 2. 连续性与回扣
- T01 越狱 → T02 搜寻与瓜子壳 → T03 陷阱+快递假说 → T04 面粉/瓜子两读 → T05 凌晨声响 → T06 垃圾袋假说 → T07 红包分寸 → T08 道谢+守株待鼠 → T09 粮未少再两读 → T10 窗纱假说 → T11 封堵收尾+双轨决定 → T12 '门一直开着'回扣收尾。
- 跨轮引用原子 8 条（A06/A11/A12/A14/A17/A22/A25/A28/A31 中 message 跨轮 8 条）≥3。
- 冲突型=信息不确定类落实：关键事实全程无实锤；冲突轮原子大量 negation:true（A06/A08/A10/A15/A17/A21/A25/A32），参考卡对'在屋内/已离开''幻听/真在''阿姨有无责任'双向禁定责。

## 3. ME 历史与边界
- ME 麦冬：同住室友，习惯把'确定的'和'猜的'分开说（initial_kb.me），这一定位在 T02/T03/T06/T09/T10 的证据分级建议中反复兑现。
- T05/T07/T12 may_decline=true：T05 凌晨求助接人先于接事；T07 直接劝住红包并给替代；T12 不替她对'放下与否'盖章。
- ME 全程不断言糯米下落、不指认阿姨、不虚构排查结论。

## 4. no_reply / 媒体局限
- HER 每轮单条长消息，全部文本；凌晨三点求助（T05）呈现真实节奏，但仍是文字消息。
- 未设 no_reply 轮；局限：阿姨原话经 HER 转述引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条互异、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T04（A08 negation）、T05（A10 negation）、T06（A14/A15 双假说竞争+negation）、T07（A17 negation 保护阿姨）、T09（A21 negation）、T10（A25 negation）。
- 跨轮引用原子 8 条 ≥3；required_this_turn 全篇 8 条（A06/A12/A14/A17/A22/A25/A28/A31），全部来自早前轮次，6-9 红线达成。

## 6. 已知风险
- 关键事实刻意不解决，语义审查时须防止把'收束'误读为'确认死亡或回归'；参考卡与 forbidden 均双向禁定。
- 阿姨为第三方但本篇冲突型为信息不确定而非第三方插入：阿姨未实质改变义务，仅提供模糊线索，归属清晰（线索无实证、无过失认定）。
- 仓鼠行为学细节（越狱能力、耐饿天数）为剧情化处理，不构成饲养指导。

## 7. 出口检查
- [x] episode.json 顶层四键、12 轮、turn_type=reply、gold={}、ts≤clock 逐轮单调（含凌晨 03:24 轮）、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表、dimensions 在九维内
- [x] timeline.json 与 episode 机械一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=宠物）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(宠物, 仓鼠越狱+排查无实锤+不指认边界+双轨收尾, 信息不确定类) 与既有篇三元组不同
