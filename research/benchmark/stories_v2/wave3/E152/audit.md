# E152 周末不外借 — audit

## 0. 性质与暴露
- author=AI (CF1 isolation worker)；real_user_material=false
- 本篇为反事实孪生篇（W13-02 / 批 CF1-20261102）：kind=counterfactual_twin，source_story=E072，perturbation=negation。
- 扰动点：反事实孪生扰动点（negation）：T10 的电钻赠送承诺翻转为拒绝赠送、改为外借并要求用完归还；触及 T10(m0 ME台词、m1 HER引用)/T11(人情账条目调整) 台词与 A25(negation=false→true，value/status/quote 跟随)/A26 原子；其余台词与原子逐字保留源篇。
- 隔离声明：生成会话未读 logs/、candidates/、任何 VERDICT/REVIEW/评审 JSON、KNOWN_FAILURES、B3 队列；仅读 W13-02 设计（§2 扰动协议）、源篇五件套、模板与 validate_story_v2.py。
- 全部人物与机构均为合成；exposure readers=["author"]，frozen_at=null。
- 最小编辑协议落实：除触及被换属性的轮次台词与对应原子外，其余轮台词、非扰动原子与 persona/initial_kb 逐字保留源篇（脚本以精确计数替换保证）。

## 1. 生活动机
- 域：租房改造；主线：HER 栗子两次请ME老许帮忙装家具后开出顺手清单，被'急活找房东'边界口径收住；闺蜜小雨借人请求与周末游说被'周末不外借'挡回并转平台解决，房东履约补完边界课；她转入自学（洞洞板、挂画），电钻以借用方式留在她家，最终以记账平账与三人火锅约定收尾。

## 2. 连续性与回扣
- T01 首次求助 → T02 完工战报 → T03 置物架与陪球抵工费 → T04 顺手清单与闺蜜登场 → T05 边界首次生效 → T06 小雨借人请求 → T07 微信已推与周末游说 → T08 拒绝落地、小雨转平台 → T09 房东履约 → T10 自学战报与电钻出借(非赠送) → T11 记账平账 → T12 散会宣言与火锅约定。
- 冲突型落实：T05（A11 retracted，清单撤下）、T08（A20 retracted，借人请求撤回；A18 边界生效）、T10（A25 negation=true，赠送承诺被拒绝改为出借）。
- 改动后自检：时间线单调、前后引用不矛盾、全部原子 quote 仍为来源台词逐字子串。

## 3. ME 历史与边界
- ME 老许：动手能力强、急活搭把手大活找师傅、不当随叫随到的装修队；T07 may_decline=true。
- ME 不代她做决定、不虚构第三方与机构细节；对风险只提示不保证。

## 4. no_reply / 媒体局限
- 消息形态与源篇一致，全部为文本消息；未设 no_reply 轮。
- 局限：第三方的话均经 HER 转述或引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions ≥2 条、applicable_dimensions ≥3 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 扰动原子与源篇对应原子只差被换字段（negation 维）及其直接跟随表述；非扰动原子双篇逐字相同。
- 跨轮引用原子与 required_this_turn 原子均引用早前轮次。

## 6. 已知风险
- 孪生篇作者可见源篇 reference，属因果敏感性素材，不作独立泛化证据（独立泛化归 W13-03 新领域批）。
- 合成数字与剧情化简化同源篇，不构成实务指导。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER/ME、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由孪生 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind=counterfactual_twin/source_story/perturbation、hashes、runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] 扰动点已在 reference.json 顶部 note 与本文件登记，供机械核验
