# E094 拼单黄了又活了 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + W03-06_SKELETON_DEDUPE 防撞清单 + E059 格式参照）。
- 全部人物、车主、平台报价均为合成；无真实人名、电话、住址、平台品牌。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：出行拼车；主线：HER 苗苗与楼上小冉拼一辆小面包车搬家（一人 190），细节落定后小冉因房东续租退出，拼单暂停；她先盘自运变通，再遇陌生单'等消息'的悬置，遂锁定平台保底单双轨并行；小冉介绍闺蜜琪琪使拼单复活，退保底单撞上 24 小时窗口认扣 30 元定金止损；执行日货梯停运改为人力爬楼，全链闭环；总账 226 元、结交琪琪，以推车还物加开火宴收尾。
- 生活动机真实可信：合租到期拼车搬家、伙伴变卦、保底与拼单双轨，是城市租房族的常见组合。

## 2. 连续性与回扣
- T01 拼单启动与借物 → T02 细节落定 → T03 拼单暂停 → T04 自运变通 → T05 陌生单悬置 → T06 保底单锁定 → T07 拼单复活与退订窗口冲突 → T08 止损退出 → T09 货梯停运求援 → T10 搬迁落成 → T11 总账复盘 → T12 开火宴收尾。
- 跨轮引用原子 6 条（A04/A09/A16/A18/A21/A27）≥3，另含 initial_kb 源原子 1 条（A12）。
- 冲突型=目标启停类落实：启动（T01）→暂停（T03，A05 suspended+negation）→变通（T04/T05）→保底（T06）→重启（T07，A14 confirmed）→执行再变通（T09，A20 negation）→完成（T10 confirmed）；T08 止损（A17 retracted+negation）。

## 3. ME 历史与边界
- ME 阿灿：搬过三次家、有推车捆扎带，信奉搬家留半天余量（initial_kb.me/lessons）。
- T09 may_decline=true：帮忙抬床垫是体力请求，参考卡保留婉拒路径并要求立即给替代方案。
- T12 may_decline=true：开火宴赴约可商量。
- ME 全程不替她操作退订、不虚构平台规则、不代她与车主谈判。

## 4. no_reply / 媒体局限
- HER 多轮单条或双条文本消息；T04、T06、T07 为补发两条。全部为文本，无语音/图片（物业通知、平台规则均以转述呈现）。
- 未设 no_reply 轮。局限：车主报价与平台退订规则全部经 HER 转述，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互异、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词或 initial_kb 的逐字子串并带 message_id/key；value 均 ≥4 实义词。
- required_this_turn 原子 7 条（T03/T04/T05/T07/T08/T09/T12 各 1），全部引自早前轮次或 initial_kb。
- protected_expressions 每轮均为本轮 HER 台词逐字子串。

## 6. 已知风险
- 与既有'搬家死线/货车改期'主题有场景相邻性，本篇冲突核为拼单目标的启停全链与保底止损，三元组（域, 主题集, 冲突型）与既有篇不同。
- 平台退订窗口与报价为剧情化设定，不构成对任何真实平台的描述。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/title/themes（首元素=域）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(出行拼车, 搬家面包车拼单+目标启停+保底单止损+新邻里结盟, 目标启停类) 与既有篇三元组不同
