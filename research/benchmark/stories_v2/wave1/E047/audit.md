# E047 兼职报价单保卫战 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + stage2/stage3 manifest 的 story_id/title/themes 字段 + 同批 E053 样例形制）。
- 全部人物、公司、价格、事件均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：工作职场；冲突型：边界类；主线：设计师阿棠的周末私单被同事以'顺手''交情''练手'三连话术压价与白嫖，她以报价单、赠送项与转介绍台阶守住价格边界，再以'只送家人'口径挡下免费头像请求，最终交易闭环、关系无损。
- 生活动机真实可信：副业接单被熟人砍价、人情白嫖是高频职场兼职场景。

## 2. 连续性与回扣
- T01 早餐抵稿费请求 → T02 '要求不高'施压 → T03 报价800 → T04 还价500（冲突峰值）→ T05 底线与替代方案 → T06 接受报价后免费头像二次越界 → T07 划界拒绝 → T08 定金与头像成交 → T09 合同条款起效回扣 T02 的改稿恐惧 → T10 沉淀心法 → T11 全款闭环回扣 T08 → T12 把规矩延伸到 ME 并收尾。
- 跨轮引用：T04 砍价在 T05 被否决（negation）；T05 底线在 T06/T12 被确认；T07 口径在 T08 被接受（confirmed）；T02 改稿恐惧在 T09 被解除（superseded）；T08 定金在 T11 构成全款闭环。
- 跨轮引用原子共 6 条（≥3）。

## 3. ME 历史与边界
- ME 记过她的兼职规矩（initial_kb.me），并有"不替她接单砍价，也不替她在同事面前圆场"的长期立场（negation 型原子，T04/T07 两处落实）。
- 冲突型=边界类：ME 全程不代谈价、不圆场，帮 HER 把口径说清楚，砍价与拒绝都由 HER 亲自完成。
- ME 不建议降价、不虚构市场行情。

## 4. no_reply / 媒体局限
- 全部为文本消息，无语音/图片媒体；表情仅以文字转述（'啧'加一个兔子表情）。
- 未设 no_reply 轮：12 轮均为 HER 主动发起且要求回应的场景。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions ≥2、applicable_dimensions ≥3（全部在九维白名单内）。
- 原子共 35 条（required 9 条），quote 全部为对应来源台词/initial_kb 的逐字子串，message-kind 原子均标注 message_id。
- 冲突轮落实：T04 ME negation 立场原子；T05 原子 negation（还价未成交）；T06 原子 status=confirmed（底线成立）；T07 ME negation 立场原子；T08 原子 status=confirmed（口径被接受）；T09 原子 status=superseded + negation（改稿恐惧解除）。
- 跨轮引用原子共 6 条 ≥3。

## 6. 已知风险
- 渐暧昧关系下 T12 的奶茶钩子刻意未挑破，若评审期待更明确的推进可能偏含蓄。
- 转介绍方案（450元档同学工作室）为备用路径，篇内未启用，属明示未定项。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns 非空或明确为空列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(工作职场, 兼职私单+报价边界, 边界类) 与既有篇三元组不同
