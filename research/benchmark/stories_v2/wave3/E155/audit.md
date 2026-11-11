# E155 补齐尾款的那单床垫 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-CF2)
- real_user_material=false
- kind=counterfactual_twin, source_story=E090, perturbation=status。
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审判决文档；仅读 W13-02 设计、源篇 E090 五件套与模板。
- 全部人物均为合成；无真实人名、电话、住址。
- 扰动登记（最小编辑）：定金/转让承诺状态对调——源篇 T09 表弟妹退出、床垫转售老同事；孪生篇翻转为**表弟妹补齐尾款按 750 成交、老同事出价落空**。触及轮次：T09（摊牌结果）、T11（赔情改道谢）、T12（价格轨迹 900→750 成交）；T01-T08、T10 逐字保留。
- 扰动原子：E155_A23（cousin_voluntary_exit）retracted→confirmed（退出未发生）、E155_A24 成交主体换为表弟妹、E155_A25 退款赔情→定金抵扣、E155_A26（cash_buyer_context）asserted→cancelled（出价落空）；E155_A28/A29/A34 随触及轮次跟随改。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家庭事务；冲突型：取消改期类。孪生主线：900 挂牌→表姑父暂缓→降价 750→表弟妹 200 定金锁期→老同事全款 700 相撞→**表弟妹一夜掂量补齐 550 尾款成交**→三叔塞 150 跑腿费→转红包、表弟妹道谢相邀元旦→腌菜缸与好酒玩笑收尾。
- 反事实点：源篇定金承诺黄、全款买家活；孪生篇对调，检验回复对'谁的承诺活着'的跟随，而非专名命中（亲戚称谓、金额面大量保留作干扰）。

## 2. 连续性与回扣
- T01 挂牌→T02 表姑父拍板→T03 暂缓→T04 退出→T05 降价→T06 表弟妹意向→T07 定金锁期→T08 全款相撞→T09 **补款成交**→T10 跑腿费两难→T11 道谢与红包→T12 腌菜缸收尾。
- 时间线单调、前后引用无矛盾：账目 750=200 定金+550 尾款自洽；T10'床垫拉走了，车库也腾了'对孪生同样成立（表弟妹拉走）；T11 道谢与 T09 成交因果一致。quote 均为台词逐字子串。
- 跨轮引用原子同构（A08/A11/A14/A19/A22/A26/A28/A31/A34 required），全部引用早于当前轮。

## 3. ME 历史与边界
- ME 老申：老同学、懂定金尾款规矩、先结论后理由（同源篇）。
- T05/T10/T12 may_decline=true；其余轮次均需回应。
- ME 边界同源篇：规矩给骨架不代拍板、收钱的事不替她决定、不断言定金条款法律效力。

## 4. no_reply / 媒体局限
- HER 单条长消息为主；亲戚发言均为 HER 转述引用。
- T05/T10/T12 may_decline=true。局限：群聊与转账均为口头转述。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互不重复、applicable_dimensions 3-4 个。
- 原子 quote 全部为对应来源台词逐字子串并带 message_id；value 均 ≥4 实义词。
- 仅触及轮（T09/T11/T12）及 T10 的 A28 source quote 随翻转更新；其余逐字保留源篇。

## 6. 已知风险
- 定金规则为剧情化口径，不构成交易指引；审查时不得允许 ME 断言定金条款法律效力。
- 表姑父退出、老同事落空均为合成情节；审查时不得允许回复照抄源篇旧事项（转售老同事、退定金赔橘子）。
- 孪生篇作者可见源篇 reference：因果敏感性测试不是独立泛化证据。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调不减、角色 HER、口语化台词、gold={}
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息一致，事件含冲突标注
- [x] exposure.json kind=counterfactual_twin/source_story=E090/perturbation=status，hashes 现算
- [x] 台词无评审术语与错误类型词汇
