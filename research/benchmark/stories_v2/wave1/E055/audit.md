# E055 免疫本上多出来的二十一天 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + stage2/stage3 manifest 的 story_id/title/themes 字段）。
- 全部人物、猫、医院、日期均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：宠物；主线：新手猫奴给橘猫年糕打猫三联，HER 记错间隔（两周/11月7日），医院短信与免疫本手写日期（10月24日+21天=11月14日）触发更正，二次更正年龄（三个月大→两岁半），按正确时间线完成第二针并固化疫苗卡片与存档。
- 生活动机真实可信：新手养宠的免疫本核对、间隔误记、医嘱复述都是高频场景。

## 2. 连续性与回扣
- T01 错误间隔（两周/11月7日）→ T02 免疫本更正（status=superseded + negation）→ T03 改约确认 → T05 卡片固化 → T06 按正确日期接种（卡片日期一致，confirmed）→ T10 回扣更正事件（避免白跑，negation 型原子）→ T12 存档托管回扣 T05 卡片（双备份）。
- 第二条更正线：initial_kb 中的三个月大说法在 T04 被免疫本出生日期推翻（negation），T11 精力梗与两岁半互相印证。

## 3. ME 历史与边界
- ME 管疫苗日期线（initial_kb.me），行为边界：只核对与复述，不断言医学结论、不替她联系医院。
- 冲突型=更正类：两处更正（间隔、年龄）均有原子 status 变化（superseded）与 negation；T10 的避免白跑原子同为 negation 型。
- 渐暧昧关系：T10/T12 有试探性软话，ME 回应稳而不扑。

## 4. no_reply / 媒体局限
- HER 在 T02 发两条连发消息（发现到认错的过程感）。全部为文本，无语音/图片；照片仅口头提及。
- 无 no_reply 轮；ME 无外部动作主张（不声称已看本子、已联系医院）。

## 5. 参考卡质量
- 每轮 acceptable_actions ≥2、applicable_dimensions ≥3 且均在九维白名单。
- 原子 quote 均为来源台词/initial_kb 逐字子串，message-kind 均带 message_id。
- 跨轮引用原子 9 条（A05/A07/A09/A11/A13/A18/A20/A22/A24）≥3。
- unknowns 明示：号源、驱虫药来源、照片时间等未定项。

## 6. 已知风险
- 21天间隔、12月5日第三针为剧情内医院设定，不构成医学建议口径。
- HER 两次记错可能被误读为角色不一致；已用认错快、行动力强的 trait 与卡片固化弥合。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 字段齐、real_user_material=false、readers=["author"]
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(宠物, 疫苗时间线+更正+医嘱复述, 更正类) 与既有篇三元组不同
