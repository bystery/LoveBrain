# E057 寻猫启事贴了又撕 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 manifest 的 story_id/title/themes 字段）。
- 全部人物、猫、小区、事件均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：宠物；主线：独居的阿棠以为橘猫汤圆从窗台走失，两日内目标三启三停（贴启事→目击线索暂停→虚惊后重启→监控顺延暂停→爪印线索守株待兔→猫自床板夹缝钻出），最终确认走失虚惊，随后撤启事、装门磁把漏洞补上。
- 生活动机真实可信：猫躲床板夹缝"假性走失"是养宠家庭常见虚惊；启停来自线索反复，属生活自然节奏。

## 2. 连续性与回扣
- T01 惊慌启动 → T02 计划成形 → T03 目击线索令计划暂停 → T04 虚惊后重启 → T05 20张贴完收工 → T06 深夜低谷 → T07 爪印线索再启 → T08 守候+远程陪伴 → T09 猫自夹缝钻出（走失结论推翻）→ T10 归因致谢 → T11 撤启事+门磁新目标 → T12 门磁装好+家宴收尾。
- 跨轮引用原子 8 条（A08/A09/A11/A20/A23/A25/A31/A35）≥3。
- 冲突型=目标启停类落实：T03 暂停（suspended）、T04 目击否定（retracted）、T09 走失结论推翻（retracted+negation）、T11 启事目标终结（cancelled+negation），每次启停均落在原子 status 变化上。

## 3. ME 历史与边界
- ME 大树：住隔壁栋步行五分钟（initial_kb.me），帮人列清单、不越界、帮完不居功。
- T02 与 T08 两处请求 may_decline=true，参考卡均给出答应路径且不强加额外承诺；ME 全程不替她决定搜索与守候方式，T06 深夜以情绪接应为主、不加码行动。
- ME 不虚构"我到场守候"类外部动作，远程陪伴以信息支持呈现。

## 4. no_reply / 媒体局限
- HER 两轮发连续两条消息（T04、T09），为补发消息形态；全部为文本消息，无语音/图片。
- 未设 no_reply 轮：12 轮均为 HER 主动发起并要求回应的场景。局限：邻居群、物业张主任等第三方信息均经 HER 转述。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词/initial_kb 的逐字子串并带 message_id（message-kind）。
- 冲突轮落实：E057_A08（suspended）、A09（retracted）、A13（suspended）、A25（retracted+negation）、A31（cancelled+negation）。
- 跨轮引用原子 8 条（A08/A09/A11/A20/A23/A25/A31/A35），≥3 条红线达成；required_this_turn 原子 8 条，全部来自早前轮或 initial_kb。

## 6. 已知风险
- 猫"两天躲床板夹缝不吃不喝"的合理性依赖"夜间出来吃粮"的爪印证据线，已在 T07 埋设；语义审查时可复核。
- T04 散养橘猫（三栋老陈家）为剧情内设定，无真实对应。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(宠物, 走失虚惊+目标启停+邻里互助, 目标启停类) 与既有篇三元组不同
