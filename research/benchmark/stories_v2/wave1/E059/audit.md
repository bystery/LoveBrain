# E059 爸说我自己能去 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 manifest 的 story_id/title/themes 字段）。
- 全部人物、体检机构、报告结果均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家庭事务；主线：HER 苏苏为65岁父亲订体检套餐并坚持全程陪同，父亲两轮拒绝并明确划界"我一个人查完就回家"；她从'偶遇潜入'退到'送到门口'，又口头应允却对面奶茶店留守两小时，被父亲以豆浆温和接住；复查节点上父女达成"独立去检查+报告先发"协议，血脂复查正常后父亲主动让步形成共识。
- 生活动机真实可信：老人抗拒被当病人、子女想在场，是最典型的代际边界课题。

## 2. 连续性与回扣
- T01 陪同计划 → T02 首次碰壁 → T03 划界爆发 → T04 偶遇企图 → T05 折中收敛 → T06 送门口+留守 → T07 豆浆缓和 → T08 报告与借车请求 → T09 协议达成 → T10 复查虚惊收束 → T11 共识形成 → T12 奶茶店两小时回扣收尾。
- 跨轮引用原子 8 条（A08/A11/A13/A18/A26/A30/A33/A36）≥3。
- 冲突型=边界类落实：E059_A08（suspended+negation，陪同悬置）、A13（retracted+negation，偶遇撤销）、A18（父亲指令与留守相悖）、A26（retracted+negation，陪同诉求撤下换知情权）、A33（confirmed，边界方案闭环）、A31/A32（confirmed，共识）。

## 3. ME 历史与边界
- ME 老纪：发小、懂与老人相处的分寸，劝人不劝死理（initial_kb.me/warmth）。
- T04 与 T12 may_decline=true：T04 明确不配合潜入并给替代出口；T12 赴约属发小份内事，参考卡保留答应路径。
- ME 全程不替父女拍板、不越权分析老人心理，医学表达限于"常见、可复查"的降风险口径。

## 4. no_reply / 媒体局限
- HER 多轮单条长消息为主，T03 为补发两条；全部为文本消息，无语音/图片（报告以转述呈现）。
- 未设 no_reply 轮。局限：父亲的原话均经 HER 转述并引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A07 negation+A08 suspended）、T04（A10 vs A11 抵触）、T05（A13 retracted）、T09（A26 retracted）、T11（A31/A32 confirmed）。
- 跨轮引用原子 8 条，≥3 条红线达成；required_this_turn 原子 8 条，全部来自早前轮次。

## 6. 已知风险
- 父亲语气词与原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 血脂偏高/复查正常为剧情化简化处理，不构成医学指导。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家庭事务, 老人体检+陪同边界+报告透明化, 边界类) 与既有篇三元组不同
