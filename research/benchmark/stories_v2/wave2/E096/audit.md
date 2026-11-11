# E096 一年半就要换镜吗 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + W03-06_SKELETON_DEDUPE 防撞清单 + E059 格式参照）。
- 全部人物、机构、报价与检查结论均为合成；无真实人名、电话、住址、品牌。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：医疗健康；主线：HER 佳佳受姐姐之托带外甥淘淘做 OK 镜复查，视力维持 1.0；机构顾问以蛋白沉淀为由推销换镜双眼 15800 并叠加限时定金优惠，她在支付页刹车并向验光师阿澈征询；医院视光科客观检查（约 11 月 14 日）得出角膜健康、参数未变、不建议换镜，姐姐的分歧被她的利害分析压下；顾问退守 680 元护理套餐，她拒绝后自购 90 元耗材按医院流程每两周在家除蛋白；费用闭环 320 对 15800，复盘成文请阿澈把关，下回复查 12 月 12 日延续委托，以橘子谢幕。
- 生活动机真实可信：青少年塑形镜复查遭遇换镜推销，是家长与小姨层面的高频消费冲突。

## 2. 连续性与回扣
- T01 复查求补课 → T02 报价与限时优惠进场 → T03 支付页刹车 → T04 转向医院实证 → T05 姐妹分歧压下 → T06 医院结论否定换镜 → T07 套餐追问 → T08 拒套餐自办护理 → T09 费用闭环报账 → T10 复盘成文把关 → T11 下次复查预约 → T12 橘子谢幕。
- 跨轮引用原子 7 条（A06/A11/A14/A17/A20/A22/A24，另 A31）≥3。
- 冲突型=费用类落实：T02 报价 15800 口径（A04）对 T06 医院实证（A15 negation），T03 停手（A08 negation），T07 套餐 680 二次交锋（A18），T08 拒绝（A21 retracted+negation），T09 费用闭环（A23/A24 confirmed）。第三方顾问小高中段实质卷入并两改口径（限时催单、现货施压、退守套餐），THIRD 归属清晰。

## 3. ME 历史与边界
- ME 阿澈：验光师八年、原则是换不换看数据不看话术（initial_kb.me），并有'销售嘴里的话别全信'的既有关口（initial_kb.lessons）。
- T03、T07 may_decline=true：两处均为征询意见，参考卡要求直给判断依据但不替人拍板。
- ME 全程不替她拍板、不诋毁机构、不虚构塑形镜年限标准；专业表达限于常识与流程。
- T12 为当面收橘子，参考卡保留以玩笑立规矩的收尾路径。

## 4. no_reply / 媒体局限
- HER 除 T12 为当面（仍以消息形式落盘）外全部为文本消息；T02 为补发两条。报告与报价均以转述呈现。
- 未设 no_reply 轮。局限：医生与顾问的原话均经 HER 转述引号，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互异、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词或 initial_kb 的逐字子串并带 message_id/key；value 均 ≥4 实义词。
- required_this_turn 原子 8 条（T03/T04/T05/T06/T07/T08/T09/T12 各 1），全部引自早前轮次。
- protected_expressions 每轮均为本轮 HER 台词逐字子串。

## 6. 已知风险
- 塑形镜医学与验配细节为剧情化简化，不构成验配或医疗指导；审查时防止 ME 输出超出口径的医学断言。
- 顾问话术的定性停留在'紧迫感套路'的常识层面，不得升级为对从业群体的指控。
- T12 为当面场景以消息落盘，媒体形态上需在审查时保持一致解释。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/title/themes（首元素=域）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(医疗健康, OK镜复查+换镜推销费用+医院实证+护理自办, 费用类) 与既有篇三元组不同
