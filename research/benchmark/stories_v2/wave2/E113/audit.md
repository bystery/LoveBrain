# E113 市集在周日 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审 JSON，仅使用任务书白名单（模板 + 既有 dedupe 的 story_id/title/themes 字段 + wave1 E059 格式参照）。
- 全部人物、市集、日期事件均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：宠物；主线：HER 苗苗抢到宠物友好市集门票并邀 ME 同行看雪貂，但她把市集日期记错成 10 月 24 日周六，并在此错误日期上层层加固：请 ME 借同事推车、请外婆改期、出好双票；舅舅临时值班导致她越界请 ME 替她圆外婆的场（被引向改约）；主办方须知实证市集实为 10 月 25 日周日——与她刚跟外婆拍胸脯的周日饺子局正面相撞；最终以"压缩市集、12 点前撤场赶饺子局"的方案二执行成功，两头兑现，雪貂遗憾转成春季再约的钩子。
- 生活动机真实可信：兔主人想给宠物开眼界、对长辈承诺必到，是最常见的日程乐观偏差场景。

## 2. 连续性与回扣
- T01 错误日期邀约 → T02 借推车 → T03 代班+出票（错误日期加固）→ T04 舅舅退出+越界求助 → T05 改约外婆周日（引爆载荷装载）→ T06 实证更正引爆 → T07 三案权衡 → T08 方案二敲定+推车落空 → T09 现场执行设闹钟 → T10 提前撤场 → T11 饺子局圆满+雪貂遗憾 → T12 春季市集讲解员分工收尾。
- 跨轮引用原子 7 条（A12/A17/A18/A27/A29/A33/A35）≥3。
- 冲突型=时间线倒错更正类落实：早期错误时间（T01 的"10月24号周六"）已承载承诺与行动（推车借用 A23、外婆改约 A13/A14、双票 A08），T06 实证纠正时引爆（A16 confirmed+negation、A17 retracted+negation、A18 双承诺相撞）。

## 3. ME 历史与边界
- ME 阿岛：隔壁组同事、养宠圈门儿清（initial_kb.me），先接情绪再给办法。
- T04 may_decline=true：ME 婉拒"替她圆外婆的场"（外婆认生、家事不代劳），并把路径引向她本人跟外婆改约——T05 她照办。
- ME 全程不替她拍板最终决定（T07 三条路把决定权还给她），不虚构市集规则，不对兔子状态做兽医判断。

## 4. no_reply / 媒体局限
- HER 以单条长消息为主，T04 为连发两条（求助+找补），呈现真实求助时的自我找补节奏。
- 未设 no_reply 轮。局限：外婆、舅舅、同事的原话均经 HER 转述引用，属刻意的信息二传设计；市集须知以 HER 转述呈现，无截图媒体。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T04（A12 suspended+negation）、T06（A16 negation、A17 retracted+negation、A18 相撞）、T08（A23 retracted+negation，推车落空）、T10（A29 confirmed，承诺兑现）。
- 跨轮引用原子 7 条，≥3 条红线达成；required_this_turn 原子 8 条（T04/T06×2/T07/T09/T10/T11/T12 各 1），全部来自早前轮次或 initial_kb。

## 6. 已知风险
- 长辈与同事的原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 兔子应激为剧情化生活常识表达，不构成兽医指导；市集日期口径（2026-10-24 周六 / 10-25 周日）与真实日历一致。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=宠物）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(宠物, 兔子+宠物友好市集+日期倒错+双承诺赶场, 时间线倒错更正类) 与既有 64 篇三元组至少一元不同
