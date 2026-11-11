# E107 一笔一笔写清楚 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 dedupe JSON 的 story_id/title/themes 字段）。
- 全部人物、家庭、价格均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家庭事务；冲突型：费用类。主线：HER 念念为爸妈更换用了十年的燃气热水器，分摊口径遭弟弟'降档省钱'主张冲击；她垫付全款先斩后奏推进，辅材追加令总价从2680升至2940，弟弟咬死只出1000致僵局；她把费用单拍照入群逐笔摊开，爸妈出500定调，弟弟转账1500附带旧账说明，账目闭环为1500+500+940，最终以'先吵架再摊单子最后请客和好'的流程共识和理账饭之约收尾。
- 生活动机真实可信：给爸妈换大件的姐弟分摊、垫付理账、嘴硬式和解，都是常见家庭账目生态。

## 2. 连续性与回扣
- T01 计划与分摊初版 → T02 降档主张冲突 → T03 死线之争破裂 → T04 垫付先斩后奏 → T05 辅材追加破预算 → T06 咬死1000僵局 → T07 摊单子入群 → T08 爸妈入局 → T09 转账1500 → T10 账目闭环 → T11 爸妈认证+嘴硬和解 → T12 流程共识+理账饭回扣摊单子。
- 跨轮引用原子 5 条（A11/A14/A17/A28/A33）+ initial_kb 原子 A08 ≥3。
- 费用类落实：A08（confirmed，安全口径与ME原则相合，来源 initial_kb）、A14（revised+negation，预算被突破）、A17（confirmed，垫付事实）、A28（confirmed，口径冲突收口）、A26（confirmed，最终账目）、A24（旧账清偿说明）。

## 3. ME 历史与边界
- ME 老隋：装过两回修过三回热水器的发小，原则'安全件不省'（initial_kb.me），管她爸妈叫叔婶。
- T04 与 T12 may_decline=true：T04 她求验棋，ME 可以只听不判；T12 赴约属发小份内事，参考卡保留答应路径。
- ME 全程不当家庭裁判、不替她骂弟弟，只把账摆明白；对按摩椅旧账保持未核实态度。

## 4. no_reply / 媒体局限
- HER 每轮单条消息，全部文本；无语音/图片（单据、视频均以转述呈现）。
- 未设 no_reply 轮。局限：弟弟与爸妈的发言均为 HER 转述引语，无独立信源。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互异、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；A08 为 initial_kb 源（key='me'）；value 均 ≥4 实义词。
- required_this_turn 原子全篇 6 条（T03/T04/T05/T06/T10/T12），全部来自早前轮次或 initial_kb，在 6-9 区间。
- unknowns/protected_expressions/forbidden_factual_claims 均为列表，protected 逐条为当轮 HER 台词子串。

## 6. 已知风险
- 价格（2680/1400/260/2940）与三笔分摊数字需全篇一致，语义审查时注意 T10 汇总与前文累计的对应。
- 燃气热水器安装与排烟管更换为剧情化简化描述，不构成安装指导。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词、无评审术语
- [x] reference.json 每轮字段齐全、quote 逐字（含 initial_kb 源）、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=家庭事务）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] (域, 主题集, 冲突型)=(家庭事务, 热水器更换+姐弟分摊+垫付理账闭环, 费用类) 与既有 64 篇三元组不同
