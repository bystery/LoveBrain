# E087 谁断更谁请奶茶 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 skeleton 的 story_id/themes 字段 + wave1 E059 格式参照）。
- 全部人物、公司审计、考试科目安排均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：学习成长；冲突型：目标启停类（启动→暂停→变通→重启全链）。HER 阿澄与 ME 温言结成注会刷题搭子，满配规则（每日二十道+晚十点错题+周日复盘+断更请奶茶）启动；年度审计提前进场把她压到宣布暂停一周并设下"别劝我"的边界；她自己提出降级为"每天一题蹲坑版"保住不断更认定；审计结束后重启满配，并把变通模式固化为常设备用档，最终以清账和"明年8月考场见分晓"收尾。
- 生活动机真实可信：立flag要撑、加班一来崩盘、暂停要有代价感、重启要找回性价比，是备考打工人的完整心理链。

## 2. 连续性与回扣
- T01 启动 → T02 首卡点 → T03 复盘正循环 → T04 压力预警 → T05 暂停宣布 → T06 低温维持 → T07 变通提案 → T08 一题模式跑通 → T09 重启预告 → T10 重启落地 → T11 机制固化 → T12 清账收尾。
- 跨轮引用原子 7 条（A13/A18/A20/A24/A27/A31/A33）≥3。
- 冲突型落实（启停全链）：T05 A11/A13（suspended+negation，规则悬置）；T07 A18（confirmed，降级归因于审计压力）；T09 A24（retracted+negation，暂停解除）；T10 A27（confirmed，规则恢复）；T11 A28（confirmed，备用档固化）；T12 A31/A33（confirmed，条款重新解释后闭环）。

## 3. ME 历史与边界
- ME 温言：去年过注会经济法、错题本齐全、搭子只对题不对人（initial_kb.me/warmth）。
- 关键边界：T05 HER 明令"别劝我"，参考卡要求照单全收、不停不劝不说教，仅留一句"想回来随时说"；T06 暂停期不催不推进度；T12 错题本借出为 ME 分内的搭子互助。
- ME 不代她认定"通过"，不虚构知识点原文，学习建议限于节奏与方法。

## 4. no_reply / 媒体局限
- HER 全部为单条文本消息；无 no_reply 轮；may_decline 全部 false。
- 局限：审计过程、每日一题的具体题目均不直接呈现，仅以 HER 转述结果的方式存在。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- required_this_turn 原子 6 条，全部引用早前轮次，未引当前轮。
- unknowns 明示未定项（暂停持续多久、长投错因、触发条件细节等）。

## 6. 已知风险
- 价外费用、长投等知识点表述为剧情化简化，不构成考试指导。
- "暂停税""模式的模式"为两人私造黑话，语义审查时勿当事实断言。
- 暂停期的情绪刻画依赖 HER 单侧文本，ME 的回应分寸是本篇主要检验面之一。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 逐轮单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(学习成长, CPA刷题搭子+目标启停全链+规则降级变通+机制固化, 目标启停类) 与既有篇三元组不同
