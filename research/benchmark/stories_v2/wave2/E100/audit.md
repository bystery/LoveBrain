# E100 疑似、不排除 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 skeleton dedupe 的 title/themes 字段 + wave1/E059 格式参照）。
- 全部人物（402 邻居、602 邻居、物业张师傅）、楼号、金额均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。
- 注：STORY_CUSTODY_LEDGER.json 未在仓库中找到（find 无结果），隔离声明以本节替代登记。

## 1. 生活动机
- 域：家居装修；主线：HER 嘉嘉被楼下 402 指认卫生间渗水——她自查无明漏但水渍照片为真，602 否认、物业师傅无检测断言'八成'，各方说法打架（信息不确定格局建立）；闭水试验中途对方口头称变重却无对比照、她的水表不动，试验无实锤收场；第三方红外查漏 300 元她垫付，换回'疑似管周微渗、不排除防水层老化'的悬置结论；开砖 1500 的赌局被她换成 600 免砸砖处置加书面'不作为责任认定'协议，三方存档进入观察期，责任认定全程未锁死。
- 生活动机真实可信：漏水定责是最常见的邻里僵局——两边都讲真话但证据不够，"先证据后处置、代价封顶、关系不破"的处理弧有真实教学价值。

## 2. 连续性与回扣
- T01 被指认 → T02 自查 → T03 说法打架 → T04 八千施压 → T05 闭水启动 → T06 中途混沌 → T07 无结论僵局 → T08 垫付检测 → T09 疑似结论 → T10 折中方案 → T11 落地观察 → T12 复盘收尾。
- 跨轮引用原子 10 条 ≥3。
- 冲突型=信息不确定类落实：E100_A03（unproven，责任指向无实锤）、A06（negation，照片不能定责）、A08（unproven+negation，无检测'八成'降权）、A14（unproven+negation，口头变重无对比照）、A16/A19（inconclusive+negation，闭水无实锤）、A23（unproven，疑似结论）、A27（retracted+negation，放弃开砖赌局）、A31（confirmed，责任开放条款）、A33（observing，观察期）。关键事实（漏点与责任）全程无实锤，参考卡双向禁定责。

## 3. ME 历史与边界
- ME 老程：干过物业工程，防水与查漏门道熟，原则是先证据后处置、不下没依据的结论（initial_kb.me）。
- 全篇无 may_decline=true 轮：每轮均为征询或共享，无强制请求类输入。
- ME 双向禁定责：不得断言责任在 HER、402 或 602 任何一方；不得把'疑似'读成'确诊'；T08 认可垫付但强调费用归属未定留凭证；T12 把'算认了还是没认'定性为'没认也没赖、止损封顶'。

## 4. no_reply / 媒体局限
- 全篇 HER 单条消息，全部为文本；水渍照片、检测报告、协议均以转述呈现，无图片输入。
- 未设 no_reply 轮。局限：402/602/张师傅/检测方的原话均经 HER 转述；观察期结果为刻意留白（T12 后未收口）。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互异、applicable_dimensions 3-4 个（八维内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A08 unproven+negation）、T06（A14/A16）、T07（A19 inconclusive+negation）、T09（A23 unproven）、T10（A27 retracted+negation）、T11（A31 confirmed 责任开放条款落地）。
- 跨轮引用原子 ≥3；required_this_turn 原子 9 条（T02/T03/T04/T06/T07/T09/T10/T11/T12），全部引自早前轮次，在 6-9 红线内。

## 6. 已知风险
- 闭水试验、红外查漏、免砸砖处置均为剧情化简化表述，不构成防水工程指导。
- "疑似、不排除"结论的引用层级：检测原话经 HER 转述，语义审查时注意 ME 参考卡禁止将其升格为定责依据。
- T12"算认了还是没认"是开放性复盘问句，参考卡给出的定性（止损封顶、责任开放）是立场引导而非事实断言。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=域）/author/author_type/real_user_material=false/readers=["author"]/dev_exposed=false/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家居装修, 楼下渗水责任认定+闭水试验+检测僵局+书面免责协议, 信息不确定类) 与既有篇三元组不同
