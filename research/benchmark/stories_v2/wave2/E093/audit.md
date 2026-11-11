# E093 我全按周五办的 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + W03-06_SKELETON_DEDUPE 防撞清单 + E059 格式参照）。
- 全部人物、拼车群、快递物流均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：出行拼车；主线：HER 阿棠加入机场夜班拼车群，约到王师傅周五晚送机并把生鲜快递托付给大周，调班、请假全部压在周五上；师傅群发接车名单实证航班实为周四 23:55，她从头到尾记成周五，周五系承诺（周五半天假、周五快递、周五装行李）与周四缺口（周四下午的班无人顶）同时引爆；她用年假补周四、远程报表赔罪小蒋，并把快递托付升级为代开箱真空冷链寄老家；出发夜第三方临时换车引发短暂信任危机，核实后放行；伴手礼赶上婚宴、顶班兑现人情两讫，最终沉淀出截图存档与日历双记的防错流程。
- 生活动机真实可信：记错出发日期导致既有承诺连锁塌方，是群接龙时代最常见的个人乌龙。

## 2. 连续性与回扣
- T01 拼车与托付双线启动 → T02 调班落定 → T03 日期实证更正引爆 → T04 二次换班被拒 → T05 年假补位与托付升级 → T06 出发夜换车惊魂 → T07 代寄执行 → T08 伴手礼闭环 → T09 顶班前犯愁 → T10 顶班兑现人情两讫 → T11 防错流程与复核员角色 → T12 火锅谢宴收尾。
- 跨轮引用原子 7 条（A08/A11/A14/A17/A19/A23/A29）≥3，另含 initial_kb 源原子 1 条（A09）。
- 冲突型=时间线倒错更正类落实：T03 实证更正引爆（A06 corrected+negation、A07 suspended+negation、A08 suspended+negation），T04 拒绝（A10 negation），T05 托付义务升级（A13 对 A14 的状态跃迁），T06 第三方换车。

## 3. ME 历史与边界
- ME 大周：先看日历再答应、答应的事落在具体动作上（initial_kb.me/style）；对 HER 的日期有"多问一句"的前科警觉（initial_kb.lessons）。
- T05 may_decline=true：代寄冷链是有实质成本的新托付，参考卡保留拒绝路径并要求给替代方案。
- T12 may_decline=true：谢宴赴约属可商量范围。
- ME 全程不替她向小蒋传话、不承诺物流时效、不代操作购票。

## 4. no_reply / 媒体局限
- HER 多轮单条或双条文本消息；T03、T05、T06、T07 为补发两条。全部为文本，无语音/图片（接车名单、机票短信均以转述呈现）。
- 未设 no_reply 轮。局限：王师傅与小蒋的行为全部经 HER 转述，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互异、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词或 initial_kb 的逐字子串并带 message_id/key；value 均 ≥4 实义词。
- required_this_turn 原子 7 条（T03/T04/T05/T06/T07/T09/T12 各 1），全部引自早前轮次或 initial_kb。
- protected_expressions 每轮均为本轮 HER 台词逐字子串。

## 6. 已知风险
- 王师傅换车一节与第三方信任话题相邻，语义审查时注意 ME 不做安全说教升级。
- 冷链寄递、生鲜保存为剧情化简化处理，不构成寄递实务指导。
- 日期倒错的全链依赖 HER 记忆与群接龙文本，信息二传层级需在审查时保持一致。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/title/themes（首元素=域）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(出行拼车, 机场夜班送机+日期倒错更正+冷链代寄+调班人情, 时间线倒错更正类) 与既有篇三元组不同
