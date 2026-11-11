# E346 audit — 标注侧审计草稿（ANNOTATOR-B4，2026-11-09）

篇目：《天台的第二把椅子》，T02 调情/暧昧/拉扯，天文观测同好，12 轮。作者 author-S4 已冻结（exposure: authored_pending_review）。

## 0. 性质与暴露

- 虚构剧情（real_user_material=false），标准 12 轮 reply 型，dev_exposed=false，无 experiments_used。
- may_decline 轮：T12（与 episode 一致）。其余 11 轮须回复。
- 信号梯度（t02_signal_gate 已落 reference 顶层）：T02 weak（网名打趣）→ T03 medium（手电暗号）→ T05 medium（赌五毛指认）→ T06 strong（第二把椅子）→ T08 strong（名单写名，note 自承快了半拍）→ T10 medium（深夜裸信号）→ T11 strong（托付+栗子信物）；反信号：T01 withdraw（我不缠人）、T04 topic_shift（岔开起哄）、T07 withdraw（你忘了算了）、T09 cooldown（不急这一场）。梯度递增无跳变，符合 T02 量尺"隐晦→明显→混合撤回"要求。
- 万能句诱惑位：T01（拿主意必须给理由）、T06（接椅子必须给动作）、T07（到底去不去必须给准话）。

## 1. 生活动机

- HER 动机链：入坑观测（T01 购镜）→ 试探他（T02-T03）→ 用组织活动制造共处（T04 报名表、T06 机位）→ 自我节奏调节（T07 撤、T08 进、T09 松）→ 交付信任（T10 晒图、T11 托付）→ 关系预约（T12 明年英仙座）。动机自然，靠"占公家地方/壮胆/别多想"等生活话术垫心理台阶。
- ME 动机面：同好互助+心细（对焦法/机位守候），关系未点破——gold 各轮 acceptable_actions 均给"接住不点破"与"推半步可撤回"两档。

## 2. 连续性与回扣

回指位清单（原子均已登记，来源轮位早于消费轮）：
- T05→T01/T05KB：红点一闪一闪 = 红点寻星镜（T01）+ 东南阳台 102 折射镜（KB me）——指认"是我"的三重吻合。required=true 两处。
- T07→T06：椅子话头按下（required=true）。
- T11→T06：托付守"机位"（required=true）。
- T10→T02 回复：她归功"你教的对焦法"——依赖候选自身 T02 回复内容，gold 不预设，卡面给"认领/如实还功"两分支。
- T12→T11：栗子与照片承诺、去向提问——与候选 T11 承诺一致性由 private_guidance 承载（gold 不预设 ME 去没去）。
- 状态链：T06 椅子邀约→T07 按下→T08 按原计划恢复→T09 照摆——刻意**不设 cancelled 守卫原子**（按 GOLD_GUIDE §4.4 状态原子不是终身追逃；T07"你忘了算了"是节奏回调非取消，T08 剧情自行恢复）。

## 3. ME 历史与边界

- ME 人名：关山月（结构工程师、玩天文五年、住梧桐苑阳台朝东南架 102 折射镜）——原子 subject=ME 均与此对表，无跨篇人名混入。
- 名字真假（真名/网名）KB 未限定：T02 gold 不预设答案，候选自选口径合法，只禁替她编造认知。
- "你教的对焦法"（T10/T11"学费"）：ME 教学行为在候选 T02 回复侧，gold 不造原子（无源不建），B3 按一致性亲读。
- 边界：T01"我不缠人"、T04"你别理他"、T06"别多想"三条 protected 已逐字落卡（当轮 HER 消息核验）。

## 4. no_reply / 媒体局限

- 仅 T12 may_decline=true（翌晨打卡离场）——不回合法；其余轮 HER 均有明确话头，不回不合法。
- 媒体局限：T10 深夜照片——候选只能引用她转述的内容（北极星是点），不得编造星轨/参数/构图；T12 取件码未到（她说"微信发你"），不得编码。
- 视线几何（隔两条街能否望见手电）无据——T03 unknown 登记坐实探测词。

## 5. 参考卡质量

- 原子 37 条（每轮 2-5），required 5 处（T03/T05×2/T07/T11），unknowns 2 处（T03 视线、T11 机位进入方式），forbidden 19 条，protected 3 条。
- T02 门（t02_signal_gate）8 键全填：signal_rounds 7 条带逐句摘录与强度、counter_signal_rounds 4 条带类型、initiative_probe_suitable=T06、retreat_room_required=true、overstep_turn=T10（附 T07/T09 同理说明）、customer_service_examples 2 条（判 0 档示例）、safe_but_flat_examples 2 条（判 1 档以下示例）、recovery_check_turn=T09。
- 判分纪律：acceptable_actions 均为策略集合（推半步/留退路/反问回掷三分档），不规定唯一措辞；"没越界"不等于高分——flat 示例已点名。

## 6. 已知风险

- 日期推算：2026-11-08=周日（基锚），周四夜=11-12、周日晚=11-15、下周二=11-17、名单周三=11-18、明年英仙座=2027-08（月粒度）。全链与各轮 ts 互洽（脚本核验）。
- unknown markers "肯定看得见/一定看得见"对否定式正确回复（"不一定看得见"）不误伤；"你家楼顶，我熟"为候选侧探测词（不要求题面逐字）。
- T10"你教的对焦法"若候选 T02 未给方法，存在剧情缝隙——gold 已给"还功"分支兜底，B3 按一致性读。
- 深夜轮（T05 00:38、T10 23:47）回复长度分寸归语义层，层 1 无检查点（诚实登记）。

## 7. 出口检查

- reference.json 可解析；turns 12 键=episode case_id 一一对应；9+1 字段全填。
- 原子 quote 全量 strict 回验（脚本）：37/37 命中，message_id=hit[0] 一致，无未来源。
- required 5 处来源均早于消费轮；unknowns 双键一致；protected 3 条当轮 HER 逐字；may_decline 与 episode 一致。
- t02_signal_gate 在位；affirm_markers 2-4 组×1-3 短语、单短语组≥4 字、全题面逐字。
