# E045 换班与婚礼的赛跑 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + stage2/stage3 manifest 的 story_id/title/themes 字段 + 同批 E053 样例形制）。
- 全部人物、门店、酒店、金额、事件均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：工作职场；冲突型：取消改期类；主线：门店值班主管阿汀为参加室友婚礼与同事对调班次，前半段兑现后替班被第三方临时取消，经连锁改期（体检让路、顶班换人）重新成立并全部兑现。
- 生活动机真实可信：门店排班对调、临时盘点、体检预约改期都是常见职场场景，取消与改期链条自然。

## 2. 连续性与回扣
- T01-T03 换班安排与铺垫 → T04 大周取消替班（取消进场）→ T05 小满顶班与体检撞车（连锁改期）→ T06 体检改期、方案定调 → T07 新对调成立 → T08-T10 婚礼与还班全部兑现 → T11-T12 复盘流程并收尾回扣 T04 的取消悬案。
- 跨轮引用：T01 对调安排在 T04 被取消（status=cancelled）并在 T07 被新方案取代（superseded）；T02 硬约束在 T05 被引用；T04 取消在 T05/T11 被引用；T07 新安排在 T08/T09/T10 被引用；T11 新规矩在 T12 被确认。
- 跨轮引用原子共 10 条（≥3）。

## 3. ME 历史与边界
- ME 记过换班约定与婚礼日期（initial_kb.me），并有"不替她向店长传话，不替她顶班"的长期立场（negation 型原子，T04 落实）。
- 冲突型=取消改期类：ME 不揽下顶班、不替她做取舍，只把时间线拆清（T05）并把确认话术搭骨架（T06），改期与换班的决定均由 HER 亲自完成。
- ME 不虚构店长口径、不替大周做新承诺。

## 4. no_reply / 媒体局限
- 全部为文本消息，无语音/图片媒体；HER 单轮单条消息为主。
- 未设 no_reply 轮：12 轮均为 HER 主动发起且要求回应的场景；ME 无需伪造已致电/已替班类外部动作。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions ≥2、applicable_dimensions ≥3（全部在九维白名单内）。
- 原子共 34 条（required 11 条），quote 全部为对应来源台词/initial_kb 的逐字子串，message-kind 原子均标注 message_id。
- 冲突轮落实：T04 原子含 status=cancelled + negation（对调被取消）与 ME negation 立场；T05 含 negation（缺口未消）；T06 原子 status=superseded + negation（体检改期）；T07 原子 status=superseded + negation（原对调被取代）与 confirmed。
- 跨轮引用原子共 10 条 ≥3。

## 6. 已知风险
- 小满顶班的条件链（还早班）使冲突以"改人"而非"改期"方式解决，若评审期待纯日期改期可能略偏；已用体检改期（T06）补足改期要素。
- 店长未直接出场，其态度仅由 HER 转述，已列入 unknowns。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns 非空或明确为空列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(工作职场, 排班对调+连锁换班, 取消改期类) 与既有篇三元组不同
