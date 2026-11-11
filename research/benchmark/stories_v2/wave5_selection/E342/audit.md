# E342 audit — Gold Annotator B2（ANNOTATOR-B2）

## 0. 性质与暴露
- 来源：wave5_selection 批 2（W5-SEL-20261109），作者 author-S2 已冻结 episode/timeline/exposure；本文件为其上 gold 侧标注审计草稿。
- exposure 侧元数据：domain=烘焙工作室，primary_topic=T02，lineage_group_id=LG-E342，kind=standard_12t；may_decline 轮=T03/T12，与 episode input.may_decline 一致。
- 全部 12 轮为作者虚构素材，real_user_material=false，未暴露给任何开发/实验（dev_exposed=false）。标注侧隔离：未读 logs/、candidates/、判决/审计文档、GOLD_SIDECAR、其他篇目 reference/audit；ack=GIA-20261109-ANNOTATOR-B2.json（no_run_results_read=true）。
- 本篇 T02 信号梯度（作者 timeline + 本标注复核）：T01 掩护(weak) → T02 操心(medium) → T05 深夜糖(strong) → T06 占位自点破(strong) → T07 报名进安排(strong) → T08 撤回(counter) → T09 自提相看(medium) → T10 验收点钩(弱-中) → T11 第一块+有人一起(strong，全篇最强) → T12 你猜+期限(strong)。梯度递增无跳变，T08 是唯一反信号位、T09 为回场位，符合 T02 批内信号分布要求（≥4 裸信号轮 + 1 反诱惑对照位 + 完整"进-撤-回-进"拉扯链，可做轨迹级档位匹配评审）。

## 1. 生活动机
- 陆聆月的动机链：同班互助（掩护翻车）→ 课下传艺（记他烤箱容量）→ 群体活动拽他入场（交换会）→ 深夜泄漏（挤花时笑）→ 课上加码（占位、报名志愿者）→ 家事撤回（母亲复查优先）→ 主动回场（自提相看）→ 交付与期限（第一块塔、续课"有人一起"、周五截止）。动机是关系推进与分寸自控的双线：每进一步都自带退路（挑战式邀约留弃权、志愿者留小莼顶上、撤回留"别提我"、续课留"你猜"）。
- 聂远舟（ME）动机：接住玩笑+还台阶（kb lessons），回应的分量随她的投入度走；decision points：T03 去不去、T07 志愿者答复、T11/T12 续课答复。
- 事实面动机：烘焙课的时间结构（周二晚班）驱动"下周二教塔皮→11月10日塔皮课→塔皮第三炉→验收→第一块"整条实物链，所有关系信号都挂在这条链上。

## 2. 连续性与回扣
- 日期星期双锚全核（2026-11-01=周日主检）：11-03 周二（T01"晚上第二节课"，kb"周二晚班"一致）；T02 11-04 周三"下周二教塔皮"=11-10 周二 ✓；T06 11-10 周二塔皮课 ✓；T07 11-11 周三"这周六开放日"=11-14 周六 ✓；T12 11-16 周一"这周五截止"=11-20 周五 ✓。
- 回扣链与 required 原子落位：
  - T02"下周二教塔皮"→ T05"下周塔皮的方子"（required E342_T05_A03）→ T10"第三炉/验收"（required E342_T10_A03）——塔皮长链。
  - T04 陨石摆收银台 → T06"收银台挂着照片呢"（required E342_T06_A03）——短距回指。
  - T08 撤回 → T09 回场（required E342_T09_A03）——撤回-回场链。
  - kb"她记得聂远舟说过吃不了太甜"→ T11"减糖版"（required E342_T11_A03，initial_kb 原子）——记忆取用位。
  - T03 陨石带去镇场子 → T04 陨石替他出席——邀约与结果的连续。
- 取消链：T07 志愿者安排 → T08"志愿者的事取消吧"（cancelled 原子 E342_T08_A03：原定两人一起摆台试吃，affirm_markers 全部含"一起"，因她退出后"两人一起"在任何读法下都不可能，不误伤他本人志愿者档是否保留的自由度）。

## 3. ME 历史与边界
- ME kb 一手事实：上月报拾麦烘焙工作室11月周二晚班六节课四百八、第一节挤糊一炉曲奇、班群外号陨石哥、测绘院画图、19升小台式烤箱。仅 T02（烤箱容量）与 T11（口味）作为判分依据进入原子；其余 kb 事实不硬造原子。
- ME persona 边界：接得住玩笑、回消息慢半拍——不要求每轮长回复；HER persona 边界"调笑可以，点破她的小心思不行"落到 protected_expressions：T02"别问我为什么操心这个"、T06"别问我为什么知道你怕黄油化"、T08"别提我"、T11"别问为什么是第一块"（全部当轮 HER 逐字，校验通过）。
- HER 边界"不占人便宜，AA 算得清"落到 T03 卡（场地费 AA 三十，候选抢着请客=没接住她的规则，private_guidance 承载）。
- 疑问句纪律：T01"你那台小烤箱几升的？"、T10"开放日人多吗？老桂给你发工资没有"均为 HER 的提问——原子只登记"提问发生/她追问的口径"，不把疑问内容当 asserted 事实（T10_A01 登记的是"隔空追问"这一事实）。

## 4. no_reply / 媒体局限
- may_decline=true 仅 T03、T12：T03 是决定轮（决定去/不去都合法，含玩笑式认领弃权）；T12 是收尾轮（轻收+留口为正解）。其余 10 轮 may_decline=false，其中 T05/T11 为深夜/信号强轮——完全沉默不合法，但允许短回复（不要求长文）。
- 媒体局限：全篇为微信式文字，无照片/语音通道；T10"拍照验货"要求的是他拍照发她，候选以文字应答+承诺发照合法，不得替他声称已发出不存在的照片。
- 单轮多消息轮位：T05（m0 失眠备方 / m1 糖信号）、T11（m0 塔 / m1 续课）——两段消息都必须被回应（漏答任一段=漏处理），已在 acceptable_actions 与 private_guidance 标明。

## 5. 参考卡质量
- 原子统计：35 条（每轮 2-3 条，required 5 条：T05/T06/T09/T10 各 1 + T11 kb 1）。状态分布：asserted 34、cancelled 1（E342_T08_A03）。全部原子带 affirm_markers（嵌套组 2-3 组×1-3 备选，单短语组 ≥4 字）。
- t02_signal_gate 已按简报九项落在 reference.json 顶层：signal_rounds 8 条（含逐句摘录与强度）、counter_signal_rounds 1 条（T08 withdraw）、initiative_probe_suitable=T06、retreat_room_required=true、overstep_turn=T08（附 T05 点破/T11 直球两个变体）、customer_service_examples 3 条、safe_but_flat_examples 3 条、recovery_check_turn=T10。判分纪律：禁止暧昧关键词命中替代行为判档——"没越界"不等于调情高分（T08 客服式"祝你妈妈早日康复"在 customer_service_examples 中明确 0 档）。
- acceptable_actions 每轮 3 条且真实不同（决定方向/分寸方向/回敬方向三种事实-意图组合）；applicable_dimensions 每轮 3-5 个且有判别内容（不为凑数塞 emotion_relation 于纯事务轮）。
- 万能句生存空间检查：T02（点破动机=0档，纯事务回复=客服腔）、T05（回避暧昧义=1档下）、T11（缩回"你自己决定"=缩钩0档）三个位置确保"我理解你/都听你的"式万能句无法全身而退。
- unknowns：本篇仅 T03 一条（交换会开始时间未载）——T02 类故事不确定面天然少，其余不确定（他是否到场开放日、他朋友圈发没发等）属 ME 自身选择/行为，题面未载且候选自拟合法，不以 unknowns 硬造（诚实登记原则）。

## 6. 已知风险
- 素材时间线瑕疵（作者侧，非标注侧引入）：T08（11-12 周四）说"我妈膝盖复查提前到这周六上午"（=11-14 周六），但 T09（11-13 周五 21:40）已报"复查结果没事"，且 T09 note 描述"周六上午排队"的当日事件——复查结果先于复查日期一天出现。处理：gold 不设日期陷阱原子（E342_T08_A01 value 保留她的原话"这周六"、T09 原子 value 不含日期），评分按信息可得性理解（E303 教训②同构：gold 两侧动作均允许），在此如实登记供审计与验收复核。
- T08/T10 读法含糊：T08"志愿者的事取消吧"可读作"她退出"或"全部取消"，T10"老桂给你发工资没有/开放日人多吗"又预设他在场。处理：gold 取最小共同事实（她退出、"两人一起"不可能），他本人志愿者档去留不判，T10 acceptable_actions 允许"去了报现场/没去自嘲"两种自洽续接，private_guidance 注明按信息可得性评分。
- cancelled 原子 E342_T08_A03 未给 item_markers（事项类原子 4.2 条的刻意豁免）：他本人志愿者档是否保留题面未定，C8 子串无法区分"两人同行安排"与"他自己的档"，给 item_markers 会误伤合法 ongoing 项——取消方向改由 affirm_markers（含"一起"）+语义层承担，unrated 风险如实登记。
- affirm_markers 无法区分否定式措辞（规范 §4.4 已知局限）：E342_T08_A03 的"一起摆台"组在"咱俩别一起摆台了"式否定句下会误伤——选词时已把误伤面压到最低（否定式回复极少复述"一起+摆台/试吃/志愿者"三元组），系统性否定/疑问护栏按规范归评分侧 Phase 3 预注册。
- initial_kb 原子（E342_T11_A03）的 affirm_markers 只能用题面语料词（"减糖版/第一块/留着"均在 T11 m0）：kb 原文"吃不了太甜"不在消息语料内，故不作为 marker——Marker QC 语料=消息+note，不含 kb。

## 7. 出口检查
- reference.json：JSON 可解析；turns 键 = episode 12 个 case_id 一一对应（E342_T01..T12）；每卡 10 字段全（9+1，含 may_decline）；may_decline 与 episode 一致（T03/T12=true）。
- quote 逐字回验：35/35 strict 命中（脚本见交付报告），message_id=hit[0] 规则全部一致；protected_expressions 4 条全部当轮 HER 逐字；required 原子 5 条全部来源早于本轮（含 1 条 initial_kb）。
- t02_signal_gate 九项齐备；audit 8 节齐备（本节即第 8 节）。
- 遗留待评分侧承接项：①E342_T08_A03 无 item_markers 的 C8 缺口；②否定式措辞护栏（Phase 3 预注册）；③T08/T10 读法含糊的"信息可得性"评分口径确认。
