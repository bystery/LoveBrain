# E340 audit — Gold Annotator B1（ANNOTATOR-B1，2026-11-09）

## 0. 性质与暴露
- 性质：AI 作者（author-S1）生成的虚构 T02 调情拉扯故事，`real_user_material=false`，`dev_exposed=false`，domain=社区合唱团，primary_topic=T02，lineage LG-E340，kind=standard_12t。作者冻结，本标注只读 episode/timeline/exposure。
- may_decline 轮：仅 T12（夜宵确认+查岗退路），与 exposure.may_decline_turns 一致。
- 隔离：未读 logs/、candidates/、判决审计文档、GOLD_SIDECAR、他篇 reference/audit；no_run_results_read=true，ack=GIA-20261109-ANNOTATOR-B1。

## 1. 生活动机
- 她的动机链：互怼一年的合唱团同事，借声部互换递信号（T02/T03）→ 嗓子禁唱期退半步但收下暖（T04）→ 团建日隔空谢含片回暖（T05）→ 拿到独唱段后把舞台依赖说出口（T06）→ 深夜递夜宵跃迁钩（T07）→ 清晨怕团里眼杂整体撤回、留帽子暗语（T08）→ 借散伙饭选址探头（T09）→ 向哥哥递"低音搭子"（T10）→ 演出成功夜把撤回的话亲手捡回（T11）→ 深夜定约+奶茶债回扣（T12）。
- 本篇是标准的"她退-探头-回-进"拉扯链（对应 TOPIC_RUBRIC T02 实证教训③的轨迹级样本）：T07 钩 → T08 撤 → T09 探 → T10 走明 → T11 重启 → T12 落定，档位匹配可在轨迹级评审。
- ME 位置：男低音搭子+琴房钥匙管理员，全程有"金嗓子"这个单向暖动作（note：ME 放的，未点破）。

## 2. 连续性与回扣
- 日历锚（python 核验 2026-11-10=周二）：T01 周五排练=11-13 ✓；奶茶周二=11-17 ✓；北山团建周六=11-21 ✓（T05 团建日吻合）；T06 排练夜周二=11-24 ✓（"练到刚才"）；T08"周五排练戴帽子"=11-27，T09 12:30 恰为该周五中午 ✓；汇演 11-29 周日下午（KB）与 T11 演出夜 21:31 吻合 ✓；T12 同夜 23:05 ✓。
- 回指位清单（required=true 的跨轮原子）：
  - T03←T02：举手报信（猜人钩自答的对象）。
  - T05←T04：金嗓子（她认领的对象）。
  - T07←T06：台上手势（深夜信号语境）。
  - T08←T07：夜宵跃迁钩（撤回对象）。
  - T09←T08：撤回（探头语境，recovery_check 依据）。
  - T10←KB：汇演 11-29 周日（initial_kb 原子，"明天演出"依据）。
  - T11←T06/T07：手势承诺+被撤回的钩（拉扯链收口双回指）。
  - T12←T01/T11：奶茶债（首尾线）+馄饨摊问句（落定依据）。
- 同轮伏笔：T01 奶茶三分糖去冰 → T12 "你欠我的那杯奶茶连夜宵一起结"（跨 11 轮首尾扣）。

## 3. ME 历史与边界
- initial_kb 使用：T10_A00（街道汇演 11-29 周日下午，key=her）——支撑"明天演出"的档期判断。kind=initial_kb+key，quote 逐字取自 KB 文本。
- note（ME 侧知识，不进原子 quote）：T04 note（金嗓子是 ME 中午放的，她还没问）→ private_guidance 处理点破裁量；T10 note（她哥在杭州工作、三年没回来看演出）→ 回复可带重量，已入 guidance。
- protected_expressions：T04"别再发我微信了"（禁声边界，逐字命中）；T08"你别多问"（帽子暗语的边界，逐字命中）。T12"你就算了"是给 ME 的退路而非她自己的边界，未登记为 protected。
- 关系边界纪律：全篇她不点破、ME 不点破（KB：谁也没把话说破）——gold 的 unknowns/forbidden 与点破类判负口径一致（T09 点破探头、T10 坐实关系）。

## 4. no_reply / 媒体局限
- 12 轮全为 reply，无 no_reply 设计；may_decline 仅 T12。
- 媒体局限：T04 她禁声禁多话（医嘱）——回复宜短、不轰炸，已在 guidance/acceptable_actions 体现；T05 隔两条街听见为玩笑式夸张，不得当物理事实回应；T11 演出当晚 21:31 问"周日晚上"即当晚，时间理解错误=事实错。

## 5. 参考卡质量
- 原子 46 条（每轮 3-5 条；其中 required（来源早于本轮）10 条）；unknowns 2 条（T08 帽子暗语——探测词诚实置空；T09 散伙饭探头——双侧禁断言）；forbidden 6 条（T02/T09/T10/T12 各 1、T08 2）。每原子带 affirm_markers 嵌套组。
- t02_signal_gate：九字段全填（signal_rounds 8 条含 turn/原句摘录/strength；counter_signal_rounds 2 条 T04/T08 均为 withdraw 型；initiative_probe_suitable=T05（次选 T09）；retreat_room_required=true；overstep_turn=T08（T04 同判）；customer_service_examples 2 条；safe_but_flat_examples 2 条；recovery_check_turn=T09）。摘录均为 episode 消息逐字子串（自查脚本同步核验 gate 内摘录）。
- 信号强度梯度自评：T01 weak（人情）→ T02/T03 medium（递话+自答）→ T05 medium（回暖探头）→ T06 strong（舞台依赖）→ T07 strong（跃迁钩）→ T08 withdraw → T09 medium 探头 → T10 medium（借家人走明）→ T11 strong（重启）→ T12 落定。梯度递增无跳变，符合 T02 信号分布纪律。
- 万能句诱惑位：T07 缩钩诱惑（"你自己决定"判 0 档已入卡）；T08 撤回轮的"好"式机械回复为 1 档下限（留温度更稳）；T09 客服式推荐无温度判平。行为锚判据全部写进 private_guidance/acceptable_actions，未用关键词判定替代行为分档。
- 判别力自评：interact/emotion_relation 主判；每篇 ≥4 个裸信号轮（T02/T03/T05/T06/T07/T10/T11 共 7 个）+两个反诱惑对照位（T05 无过度解读空间、T08 撤回后不得推进）——T02 专项复核口径满足。

## 6. 已知风险
- T08 帽子暗语 unknowns 探测词置空（诚实 unrated）：任何"帽子是X"式探测词都会误伤疑问式正确回复（"帽子是暗号？"是高分的接法之一）；系统性否定/疑问护栏属评分器 Phase 3 预注册范围。该 unknown 的判死当前完全依赖语义层——点名提请 B3/judge 注意。
- T09 散伙饭探头的探测词（"你就是想约我"）设计为"命中即真错"（点破越线本就是 0 档行为），但若评分侧 Phase 3 加疑问护栏，需同步豁免疑问形态。
- T05/T06 的推半步与扑钩边界靠行为锚描述，程序层无检查点（T02 程序通道只有契约+红线）——语义评审需对照 gate 的 customer_service/safe_but_flat 示例判档，防止"没越界=高分"的错觉。
- 奶茶债跨 11 轮未结是作者设计（T12 原文"你欠我的那杯"）——若某候选在中间轮的回复里"已买"（回复不被记录进 episode），T12 原文仍说欠着，gold 以 HER 语料为准，不视为矛盾。
- T02 声部互换是提议非定案（forbidden 已覆盖）；T06 独唱段"正式给我"用 confirmed 与 KB"刚分给她"衔接。

## 7. 出口检查
- reference.json 可解析；turn 键 12 个 = episode case 集合一致；顶层含 t02_signal_gate（九字段）。
- quote 逐字回验：46/46 strict 命中（message 原子 45 条 hit[0]/message_id 一致；initial_kb 1 条命中 KB 文本）；gate 摘录 10 条亦逐一验过为消息子串——0 失败（自查脚本输出）。
- 结构自查：acceptable_actions ≥2 且互异；dimensions ≥3 且在词表；protected 2/2 当轮 HER 逐字；required 原子来源均早于本轮；unknowns 对象形态+双键一致（T08 置空为有意设计）；may_decline 12/12 一致。
- audit.md 8 节齐全（恰 8 个 '^## '）。
- 隔离声明：见 §0；gold 未参考任何候选侧信息。
