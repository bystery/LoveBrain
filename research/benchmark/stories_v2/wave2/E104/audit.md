# E104 三千零一十七块 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审判决文档，仅使用任务书白名单（模板 + W03-06_SKELETON_DEDUPE 的 story_id/themes 字段 + wave1/E059 格式参照）。
- 全部人物、公司、系统名称与金额均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false。

## 1. 生活动机
- 域：工作职场；主线：HER 方糖名下三笔垫付共3017元卡在报销系统新旧切换之间（14号死线+重录双审批），盘点发现票据两处硬伤（个人抬头、无发票）；主管拒签延期并归责，她以垫付指示记录+'不追责只对账'的包装换得担保延期；伴手礼940历经红冲重开、科目盲选、双口径打回，最后拒绝'写办公用品'的失真票改走按实重开慢通道；12月4日3017全额闭环，垫付留痕规则沉淀并外传给实习生。
- 生活动机真实可信：系统切换期垫付积压、票据口径、责任归属与票据真实性抉择是职场财务最高频的连环坑；她的底线（垫的钱一分不能少+不明不白不要）与 ME 的'先保票再垫钱'轴贯穿全程。

## 2. 连续性与回扣
- T01 死线+三笔账 → T02 票据硬伤 → T03 双审批口径 → T04 主管拒签冲突 → T05 证据用法征询 → T06 担保延期落定 → T07 一过一退+红冲排队 → T08 新系统盲选+217到账 → T09 双口径打回 → T10 失真票抉择 → T11 拒失真票+1860到账 → T12 940闭环+'先保票再垫钱'口头禅回扣收尾。
- 跨轮引用原子 7 条（A12/A16/A20/A24/A27/A30 及引用关系的其余跨轮原子）≥3。
- 冲突型=费用类落实：作废死线与垫付悬置（A01/A02）、责任归属对撞（A06/A07/A08 negation）、票据口径连环更正（A15 negation、A19 negation）、票据真实性红线（A27 retracted+negation）、担保兑现与全额闭环（A26/A28/A30 confirmed）。

## 3. ME 历史与边界
- ME 陈皮：大学同学、财务出身；口头禅'先保票再垫钱'（initial_kb.me），在 T01-T03 翻译流程、T05-T06 给证据使用说明书、T08 建议自查科目、T10 明确拒收失真票——全程只讲流程与证据，不替她对主管下情绪结论。
- T05/T10/T12 may_decline=true：T05 给使用说明不代决定；T10 红线劝住并给替代路径；T12 饭局按日程接或改期。
- ME 不虚构报销制度、不给税务稽查后果的细节恐吓、不把财务/供应商任何一方说成恶意。

## 4. no_reply / 媒体局限
- HER 每轮单条长消息，全部文本；截图、转账、发票状态均以转述呈现，属刻意的信息二传设计。
- 未设 no_reply 轮；无语音/图片通道。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互异、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词；每轮 unknowns/protected/forbidden 为列表。
- 冲突轮落实：T04（A08 negation 责任归属对撞）、T06（A12 confirmed 证据生效）、T07（A15 negation 退回）、T09（A19 negation+A20 跨轮障碍延续）、T10（A22 失真票 asserted vs A24 风险叠加）、T11（A27 retracted+negation 方案否决）。
- 跨轮引用原子 7 条 ≥3；required_this_turn 全篇 7 条（A12/A16/A20/A24/A27/A30 及 T06 的 A12 之外的跨轮原子合计为 7：均来自早前轮次），6-9 红线达成。

## 6. 已知风险
- 票据、红冲、科目为剧情化简化处理，不构成财务/税务指导；语义审查时注意'客户维护费''市场推广费'等为合成科目语境。
- 供应商'只能写办公用品'的口径为常见灰色情境的合成化呈现，参考卡以票据真实性红线处理，不认定其违法。
- ME 财务身份使 T01-T12 建议偏专业，评审时注意 ME 未越界替她对主管/财务下敌意结论。

## 7. 出口检查
- [x] episode.json 顶层四键、12 轮、turn_type=reply、gold={}、ts≤clock 逐轮单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表、dimensions 在九维内
- [x] timeline.json 与 episode 机械一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=工作职场）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(工作职场, 报销系统切换+跨口径重录+票据抬头红冲+票据真实性红线+垫付留痕, 费用类) 与既有篇三元组不同
