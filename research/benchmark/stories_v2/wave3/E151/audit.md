# E151 发小非要跟 — audit

## 0. 性质与暴露
- author=AI (CF1 isolation worker)；real_user_material=false
- 本篇为反事实孪生篇（W13-02 / 批 CF1-20261102）：kind=counterfactual_twin，source_story=E086，perturbation=subject。
- 扰动点：反事实孪生扰动点（subject）：第三方插入者表哥大壮→发小大壮；触及 T03(求入与情面)/T08(路墩子称呼'妹'→'乔大小姐')/T11(家族群→老哥们群、我妈渠道调整)/T12(表哥条款→发小条款) 台词与 A07/A09/A29/A34/A36/A37 原子及 persona/initial_kb 对应表述；姓名、体能、车况、剧情结构与其余台词逐字保留源篇。
- 隔离声明：生成会话未读 logs/、candidates/、任何 VERDICT/REVIEW/评审 JSON、KNOWN_FAILURES、B3 队列；仅读 W13-02 设计（§2 扰动协议）、源篇五件套、模板与 validate_story_v2.py。
- 全部人物与机构均为合成；exposure readers=["author"]，frozen_at=null。
- 最小编辑协议落实：除触及被换属性的轮次台词与对应原子外，其余轮台词、非扰动原子与 persona/initial_kb 逐字保留源篇（脚本以精确计数替换保证）。

## 1. 生活动机
- 域：骑行团；主线：HER 乔乔与ME老代约了周日骑行芦苇荡看日落，发小大壮刷到朋友圈非要跟，情面与队形冲突；带上后义务重排（ME领骑、她押队、头盔借出、无退路预警），大壮车况堪忧、半程体力崩盘，水路体面退场，两人完成日落之约，红包谢押队并以复盘新规与烤肠欠账收尾。

## 2. 连续性与回扣
- T01 两人轻装骑约 → T02 分工与时限定死 → T03 发小求入情面冲突 → T04 拍板带上、义务重排 → T05 车况警报 → T06 刹车检修提前完成 → T07 出发就位 → T08 半程爆雷 → T09 水路退场变通 → T10 日落兑现 → T11 押队费红包与换车宣言 → T12 复盘新规与欠账收尾。
- 冲突型落实：T04（A13 retracted，两人方案推翻；A10-A12 义务重排）、T08（A25 confirmed，无退路预警应验）、T09（A29 suspended，大壮退出；A30 分工对调）。
- 改动后自检：时间线单调、前后引用不矛盾、全部原子 quote 仍为来源台词逐字子串。

## 3. ME 历史与边界
- ME 老代：骑行五年懂车懂维修，分工认位不认人；T03 may_decline=true。
- ME 不代她做决定、不虚构第三方与机构细节；对风险只提示不保证。

## 4. no_reply / 媒体局限
- 消息形态与源篇一致，全部为文本消息；未设 no_reply 轮。
- 局限：第三方的话均经 HER 转述或引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions ≥2 条、applicable_dimensions ≥3 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 扰动原子与源篇对应原子只差被换字段（subject 维）及其直接跟随表述；非扰动原子双篇逐字相同。
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
