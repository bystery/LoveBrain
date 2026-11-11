# E154 拼单黄死保底转正 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-CF2)
- real_user_material=false
- kind=counterfactual_twin, source_story=E094, perturbation=status。
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审判决文档；仅读 W13-02 设计、源篇 E094 五件套与模板。
- 全部人物均为合成；无真实人名、电话、住址。
- 扰动登记（最小编辑）：核心事项状态对调——源篇'拼单黄了又活了'翻转为'拼单黄死、保底单转正'。触及轮次：T07（复活失败+退订纠结改差价纠结）、T08（退保底改保底转正）、T10/T11/T12（琪琪相关跟随改）；T01-T06 逐字保留。
- 扰动原子：E154_A14（carpool_revived）由源篇 E094_A14 status=confirmed 翻转为 retracted/negation=true；E154_A17（fallback_cancelled）由 retracted 翻转为 confirmed（退订未发生）；E154_A15/A18/A19 随触及轮次跟随改。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：出行拼车；冲突型：取消改期类。孪生主线：拼单成团→小冉退出→自运与陌生拼单变通→锁保底单→**琪琪外派延期、陌生单未谈拢，拼车彻底黄**→**保底单 260 转正**→货梯停运爬楼执行→总账 266 复盘→开火宴谢小冉收尾。
- 反事实点：源篇 T07 拼单复活、T08 退保底止损；孪生篇同位轮翻转为复活失败、保底转正，检验回复对事项状态维的跟随，而非专名命中（人名小冉/琪琪面保留作干扰）。

## 2. 连续性与回扣
- T01 拼单启动→T02 细节落定→T03 小冉退出→T04 自运盘算→T05 陌生拼单等消息→T06 锁保底→T07 **复活失败**→T08 **保底转正**→T09 货梯停运→T10 爬楼搬完（小冉搭手请面）→T11 总账 266→T12 开火宴收尾。
- 时间线单调、前后引用无矛盾：T05'等消息'在 T07 被明确否定收口；账目 260+6=266 自洽；T12'就仨人'=她、阿灿、小冉。quote 均为台词逐字子串。
- 跨轮引用原子同构（A04/A09/A12/A16/A18/A21/A23/A27 required），全部引用早于当前轮。

## 3. ME 历史与边界
- ME 阿灿：老邻居、搬家工具齐、先结论后理由（同源篇）。
- T09/T12 may_decline=true；其余轮次均需回应。
- ME 边界同源篇：给痛快话不代下单、退订操作不代做、账目不替她重算。

## 4. no_reply / 媒体局限
- HER 消息为主；物业通知、车主报价均为 HER 转述。
- T09/T12 may_decline=true。局限：平台规则均为 HER 转述口径。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互不重复、applicable_dimensions 3-4 个。
- 原子 quote 全部为对应来源台词逐字子串并带 message_id；value 均 ≥4 实义词。
- 仅触及轮（T07/T08/T10/T11/T12）参考卡随状态翻转更新；其余逐字保留源篇。

## 6. 已知风险
- 平台定金与退订规则均为剧情化口径，不构成交易指引。
- 琪琪、小冉的续住/外派均为合成情节；专名保留作干扰面，审查时不得允许回复照抄源篇旧事项（拼单复活、琪琪拼车）。
- 孪生篇作者可见源篇 reference：因果敏感性测试不是独立泛化证据。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调不减、角色 HER、口语化台词、gold={}
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息一致，事件含冲突标注
- [x] exposure.json kind=counterfactual_twin/source_story=E094/perturbation=status，hashes 现算
- [x] 台词无评审术语与错误类型词汇
