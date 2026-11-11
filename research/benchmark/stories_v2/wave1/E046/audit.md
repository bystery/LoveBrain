# E046 白熊动画的两次改口 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + stage2/stage3 manifest 的 story_id/title/themes 字段 + 同批 E053 样例形制）。
- 全部人物、公司、供应商、合同条款、事件均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：工作职场；冲突型：第三方类；主线：乙方 AE 苏苏被外包供应商白熊动画两次改口延期，需在"报喜不报忧"与如实对上转述之间校准，最终以补充协议、上线改期与客户口径完成三方对表。
- 生活动机真实可信：供应商延期、对上级转述、风险说明与客户口径都是广告乙方日常。

## 2. 连续性与回扣
- T01 延期进场未上报 → T02 模糊提及被'尽量别延'顶回 → T03 口头'压回30号' → T04 书面确认推翻口头（superseded）→ T05 摊牌接下三问 → T06 风险说明初稿 → T07 二次改口撕掉口径（superseded）→ T08 补充协议与上线改7号（原上线日 superseded）→ T09 压线交片 → T10 复盘转述方法论（半透明转述 superseded）→ T11 口径成立 → T12 上线闭环并回扣改口次数。
- 跨轮引用：T01 延期与上线日在 T02/T08 被引用；T03 口头承诺在 T04 被推翻；T04 书面确认在 T05/T09 被引用；T06 口径在 T07 被作废、T11 被启用；T07 二次改口在 T12 被归档。
- 跨轮引用原子共 9 条（≥3）。

## 3. ME 历史与边界
- ME 记过项目节点（initial_kb.me），并有"不帮她向上级编造进度，也不替她直接联系她的上级或供应商"的长期立场（negation 型原子，T07 落实）。
- 冲突型=第三方类：冲突源在供应商，ME 不出面、不代谈、不编进度，只做事实链梳理与转述校对；对上级的摊牌由 HER 亲自完成（T05）。
- ME 不夸大隐瞒后果，不虚构总监态度。

## 4. no_reply / 媒体局限
- 全部为文本消息，无语音/图片媒体；邮件、补充协议仅以台词转述形式出现，不落地为附件。
- 未设 no_reply 轮：12 轮均为 HER 主动发起且要求回应的场景。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions ≥2、applicable_dimensions ≥3（全部在九维白名单内）。
- 原子共 37 条（required 11 条），quote 全部为对应来源台词/initial_kb 的逐字子串，message-kind 原子均标注 message_id。
- 冲突轮落实：T04 原子 status=superseded + negation（口头承诺被推翻）；T05 原子 status=confirmed（摊牌完成）；T07 原子 status=superseded + negation（口径作废）与 ME negation 立场；T08 原子 status=superseded + negation（原上线日作废）；T10 原子 status=superseded + negation（半透明转述被抛弃）。
- 跨轮引用原子共 9 条 ≥3。

## 6. 已知风险
- 白熊二次改口的动机未交代（产能还是议价），已列入 unknowns；不影响 HER 转述主线。
- '内部精修档期'口径带轻微话术成分，篇内仅表述为客户未起疑，未做道德背书。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns 非空或明确为空列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(工作职场, 项目延期+对上转述, 第三方类) 与既有篇三元组不同
