# E088 群里说的不算 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 skeleton 的 story_id/themes 字段 + wave1 E059 格式参照）。
- 全部人物、报名官网、加场通知、同学群均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：学习成长；冲突型：信息不确定类。HER 小蕊已报12月28日普通话考试（认定材料就差这一项），同学群传出"11月下旬加场"且先到先得；此后全程无实锤——官网空白、截图模糊、热线落空、版本漂移、预备页报错，直到官宣落地：加场属实但仅限师范类应届生，传闻半真半假。她在无实锤期间完成"稳守不赌"的决策，最终专注度反升，以冲刺表和请客收尾。
- 生活动机真实可信：考试群传闻、截图式"证据"、版本漂移，是社群信息传播的典型形态；认定材料压力让传闻有真实诱惑力。

## 2. 连续性与回扣
- T01 传闻入场 → T02 官网空白 → T03 模糊截图 → T04 热线落空 → T05 两条路成型 → T06 版本漂移 → T07 预备页报错 → T08 按兵不动 → T09 官宣落定 → T10 群内闭环 → T11 副产品回血 → T12 冲刺表与请客收尾。
- 跨轮引用原子 6 条（A12/A20/A24/A28/A34/A38）≥3。
- 冲突型落实（关键事实全程无实锤、参考卡双向禁定责）：T08 A23（retracted+negation，赌局念头撤回）、A24（confirmed，预备页不足为凭）；T09 A25（confirmed，官宣）、A26（negation，资格不符）；每轮参考卡均禁断言传闻真伪或指认传者责任，证据等级被逐轮显式登记。

## 3. ME 历史与边界
- ME 阿屿：表姐高校教务背景、先分"官宣"与"群传"、不替人拍板只摆选项（initial_kb.me/style）。
- T05 参考卡明示摆选项不拍板；全程不判定传闻真伪（直至 HER 转述官宣）、不指认群里任何人传播谣言有责、不附和"被带节奏"的自我指控（T04 明确"双方都还没实锤"）。
- ME 不虚构通知全文与退报规则，信息核实建议均限于官网单一信源。

## 4. no_reply / 媒体局限
- HER 全部为单条文本消息；无 no_reply 轮；may_decline 全部 false。
- 局限：官网页面、截图、通知原文均不直接呈现，仅经 HER 转述；通知细节以 HER 引用为唯一来源。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- required_this_turn 原子 6 条，全部引用早前轮次，未引当前轮。
- unknowns 明示未定项（截图真伪、报错原因、退报规则等）；双向禁定责落在 forbidden_factual_claims 与 private_guidance。

## 6. 已知风险
- 普通话考试报名流程、面向对象限制为剧情化设定，不构成报考指导。
- "谣言过滤器""集体安静"为口语表达，语义审查时勿当事实断言。
- 传闻从出场到官宣历时16天，时间压缩属戏剧化处理。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 逐轮单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(学习成长, 普通话考试加场+信息不确定全程无实锤+双向禁定责+传闻闭环, 信息不确定类) 与既有篇三元组不同
