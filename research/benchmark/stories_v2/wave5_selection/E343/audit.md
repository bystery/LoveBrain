# E343 audit — Gold Annotator B2（ANNOTATOR-B2）

## 0. 性质与暴露
- 来源：wave5_selection 批 2（W5-SEL-20261109），作者 author-S2 已冻结 episode/timeline/exposure；本文件为其上 gold 侧标注审计草稿。
- exposure 侧元数据：domain=桨板俱乐部，primary_topic=T02，lineage_group_id=LG-E343，kind=standard_12t；may_decline 轮=T06/T12，与 episode input.may_decline 一致。
- 全部 12 轮为作者虚构素材，real_user_material=false，dev_exposed=false。标注侧隔离：未读 logs/、candidates/、判决/审计文档、GOLD_SIDECAR、其他篇目 reference/audit；ack=GIA-20261109-ANNOTATOR-B2.json（no_run_results_read=true）。
- 本篇 T02 信号梯度（作者 timeline + 本标注复核）：T01 供氧外号(weak) → T02 借湿衣偏心(medium) → T03 暗号(medium) → T04 帮问价嘴硬(medium) → T05 男朋友位置+留位(strong，跃迁级) → T06 收线(counter) → T07 深夜探头(medium) → T08 重提要真话(medium-strong) → T09 明年见+审稿(medium) → T10 带新人+盯名额(strong) → T11 一板+顶班(strong) → T12 报备制度(medium)。梯度递进、T06 单一反信号位、T07 回场位，"留位→收线→探头→要真话→同板成局"构成完整拉扯链，可做轨迹级评审。

## 1. 生活动机
- 江疏雨动机链：安全员职责驱动安全关切（薄款扛不住十一月水温）→ 好意裹在自嘲里（偏心我不反驳）→ 关系符号化（暗号、外号）→ 跃迁尝试（男朋友位置玩笑+接力赛留位、决定权交回）→ 职业现实收线（翻大夜）→ 破例回场（深夜纪念品）→ 要真话（都行禁令）→ 用组织行为包装推进（封板仪式、审稿权、带新人、盯名额、报备制度）。每一步推进都有"安全/职责/顺手"的外衣，是"嘴硬式关心"的一致人设。
- 段云开（ME）动机：接得住损、答应的事慢但稳；decision points：T04 冬季卡续否、T05 接力赛上不上、T08 明确答复、T11 确认训练参赛。
- 事实面动机：俱乐部日程（周六早场、封板 11-22、接力赛 12-13）驱动全篇节拍，关系信号全部挂在俱乐部事件上。

## 2. 连续性与回扣
- 日期星期双锚全核（2026-11-01=周日主检）：T01 11-07 周六早场 ✓；T02 11-08 周日"下周六风力三级"=11-14 周六 ✓；T03"周六下水前来拿"=11-14 ✓；T04 11-11 周三"封板定在11月22号"=周日 ✓；T05 11-14 周六"今天你没下水" ✓；T09 11-21 周六"封板那天"=11-22 ✓；T11 11-23 周一"周三晚泳池"=11-25 周三 ✓、"接力赛定在12月13号"=周日 ✓；T12 11-24 周二收口 ✓。
- 回扣链与 required 原子落位：
  - T01 落水旧梗（"第四次落水"）→ T07 深夜探头"发朋友圈纪念"（required E343_T07_A04）——长距回指（T01→T07）。
  - T08 小隼顶班 → T11"小隼顶我的夜班换出来的"（required E343_T11_A04）——顶班链。
  - T03 湿衣取衣安排 → T05"湿衣也没来拿"——缺席后果链（asserted 于 T05，无需 required，缺席事实由当轮消息承载）。
  - T05 接力赛留位 → T06"先别报名"（暂缓）→ T08"我又想报了"→ T11"定在12月13号"——留位-暂缓-复活-成局完整状态链，按纪律 1.7 未定案不硬造 cancelled：T06 用原子 asserted（暂缓要求）+ forbidden/unknowns 表达，未设取消族原子。
- tentative 口径：T10"明年开板预计三月底"标 status=tentative，forbidden 明令不得说成已定档期。

## 3. ME 历史与边界
- ME kb 一手事实：九月底入云汀桨板俱乐部、二手板、会游泳但怕冷水、周六早场全勤、下水四次落水四次。落水纪录以 HER T01 消息为原子来源（kb 不重复建原子）。
- HER persona 边界"安全的事不掺玩笑"落到 T02 forbidden（对水温/装备安全抖机灵=0档）+ private_guidance（先正经答应再玩笑垫后，kb lessons 原文）。"忙的时候不许追问"落到 T06 protected_expressions（"最近别找我，忙完这阵再说"当轮 HER 逐字）与 T08 protected（"别用都行两个字回答我"当轮 HER 逐字）。
- 疑问句纪律：T07"发了没？"、T08"你到底上不上？"、T09"你写什么？"均为提问——原子登记提问发生，答案是否发了朋友圈/上不上属 ME 自身行为，题面未载，候选如实自拟（private_guidance 已注明），不得替他声称已发朋友圈的截图等不存在媒介产物。
- 她的 style"先损一句再给正事、不喜欢都行式敷衍"与 kb lessons"她给的选择要给真答案"直接落 T04/T08 private_guidance 与 T08 forbidden。

## 4. no_reply / 媒体局限
- may_decline=true 仅 T06、T12：T06 收线轮（退干净=正解，沉默合法）；T12 收尾轮（轻收+留口）。其余轮 may_decline=false：T07 深夜探头轮允许轻短回复，但完全回避不合法。
- 媒体局限：微信文字通道。T09"写好拍照发我"——候选承诺拍照合法，不得声称已发出照片；T12"照片我修好了"是她侧媒介产物，候选只回应不代她声明内容。T07"深夜限定"消息无附加媒介，纯文字回应即可。
- 单轮单消息：本篇 12 轮均为单消息（m0），message_id 无歧义。

## 5. 参考卡质量
- 原子统计：35 条（每轮 2-4 条，required 2 条：T07/T11 各 1）。状态分布：asserted 34、tentative 1（E343_T10_A02 开板预计）。全部原子带 affirm_markers（嵌套组 2-3 组×1-3 备选，单短语组 ≥4 字）。
- t02_signal_gate 九项齐备（signal_rounds 10 条逐句摘录+强度、counter T06 withdraw、probe=T05、retreat_room_required=true、overstep=T06 附 T02 安全玩笑/T08 都行两个变体、客服/flat 示例各 3 条、recovery=T08）。
- 行为锚对照位（T02 判别力要求）：T05 跃迁信号轮（接/留双安全、扑/回避双失败）；T08 万能句诱惑位（"都行/看情况"被她原话点名禁用，命中即 0 档——C1 万能句生存空间检查落点）；T06 收线轮（退干净 vs 追问，双向判）。三个位置覆盖 0/1/2 档全区间。
- acceptable_actions 每轮 3 条真实不同；applicable_dimensions 每轮 3-4 个（boundary 仅在真实边界轮启用：T06/T08）。
- unknowns：T02 集合时间、T05 接力赛日期（T05 时点只说"12月"，13号是 T11 才定的，候选不得提前坐实）、T11 训练开始时间——三条均为"未载细节防编造"类，探测词按肯定式设计（negation 安全）。

## 6. 已知风险
- T05→T06 状态链的判分衔接：T06"先别给我报名"是暂缓而非取消，gold 未设 cancelled 原子（纪律 1.7：未定案不硬造取消）——若候选在 T06 后宣布"留位已取消"或"照旧报名"两种极端，层 1 无原子判死，由语义层/B3 承担，如实登记为 unrated 面。
- affirm_markers 否定式局限（规范 §4.4）：E343_T06_A03 的"别找我/忙完"组在正确回复"你说了别找我，我退干净"下会含"别找我"——但该原子是 asserted（证据登记）非 must-not-affirm，层 1 不判分，无误伤；真正的 must-not-affirm 原子本篇为零（E343_T10_A02 为 tentative 证据），C14 面本篇为空。
- T07"朋友圈发没发"与 T08"上不上"的答案均为 ME 自身行为：题面不锚定答案，语义层只判自洽与真实（T10 候选若说续了卡但 T04 说没续=漂移判低）。
- E343_T11_A04 required 原子 source=T08（早于本轮）✓；跨轮回指原子均为"来源早、列位后"形态，无未来引用。
- 她的"接力赛先别给我报名"含"给我"歧义（不让报她 vs 不让报他）：从上下文（她翻大夜）读作"别替她报名/她这边暂缓"，gold 原子 value 按"她要求接力赛先别给她报名"登记——若评分侧读到歧义，按信息可得性处理，登记在此。

## 7. 出口检查
- reference.json：JSON 可解析；turns 键 = episode 12 个 case_id 一一对应；每卡 10 字段全（9+1）；may_decline 与 episode 一致（T06/T12=true）。
- quote 逐字回验：35/35 strict 命中；message_id=hit[0] 规则一致；protected_expressions 2 条（T06/T08）当轮 HER 逐字；required 2 条来源早于本轮。
- t02_signal_gate 九项齐备；audit 8 节齐备。
- 遗留待评分侧承接项：①T05→T06 暂缓状态的语义层判分；②ME 自身行为（朋友圈/续卡/上不上）的自洽判分口径确认。
