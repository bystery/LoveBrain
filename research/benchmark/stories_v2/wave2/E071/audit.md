# E071 墙里全是铝线 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单材料（模板 + 既有篇的 story_id/title/themes 防撞字段 + E059 格式参照）。
- 全部人物、小区、装修队、报价均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家居装修；主线：HER 阿棠老房局部翻新水电开工，师傅老周开槽后以现场照片提出两项增项（厨房铜线四千五、渗漏修理两千）实质性改变预算与计划（第三方插入）；她爸以"铝线三十年没事"的经验之谈与之相撞形成信息冲突；经工长背书与 ME 分级判断后拍板（修渗漏+换厨房回路、其余不动、六千五封顶、白纸黑字补单）；后续走线完工、父亲改口、验收插座偏位归因工长、结算差两百以落单凭证闭环，父亲反向求介绍，辣子鸡之约收尾。
- 生活动机真实可信：老房铝线、隐蔽增项、父辈经验与现场证据之争、按单结算与红包人情，都是装修高频场景。

## 2. 连续性与回扣
- T01 开工与到场之约 → T02 预习与讲解确认 → T03 老周增项插入 → T04 父亲口径相撞 → T05 工长背书求证 → T06 拍板补单 → T07 ME 现场履约 → T08 完工与父亲改口 → T09 验收返工与责任归属 → T10 结算差两百 → T11 按单闭环与红包退回 → T12 验收签署与辣子鸡回扣收尾。
- 跨轮引用原子多条（A09/A11/A13/A19/A24/A27/A30），≥3。
- 冲突型=第三方插入类落实：T03 A05/A06/A07（师傅增项 asserted）、T04 A08（父亲口径 asserted）+A09（增项决策 suspended）、T06 A13（父亲意见 superseded+negation）、T09 A21（责任归属工长）、T10 A24（口头六千三 superseded+negation）、T11 A27（结算争议 confirmed 关闭）。

## 3. ME 历史与边界
- ME 老魏：装过房、会看走线图、"先看单子再看现场"（initial_kb.me），T05 分级判断与 T10 结算路径均由此人设承载。
- T07 含一条 ME 历史消息（到场、先看单子），属履约事实陈述；T12 may_decline=true，赴约时间可商。
- ME 只提建议不拍板、不替她跟工长老周谈判、不替父亲改口，金额判断均要求落到单据。

## 4. no_reply / 媒体局限
- 全篇为文本消息；现场照片、走线图、结算单均以 HER 转述呈现，未作为媒体内容直发。
- 未设 no_reply 轮。局限：父亲与老周原话均经 HER 转述引用，属信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互不重复、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T04（A09 suspended）、T06（A13 superseded+negation）、T10（A24 superseded+negation）。
- required_this_turn 原子共 7 条（T04、T05、T06、T08、T10、T11、T12 各 1），全部引用早前轮次；跨轮引用原子 ≥3 条红线达成。

## 6. 已知风险
- 水电工艺表述（铝线载流、卫生间渗漏、回路分区）为剧情化简化，不构成施工规范指导；被测模型若展开具体施工建议属 forbidden 范围。
- 报价数字（四千五/两千/六千五/六千三/六千五）需在语义审查中核对前后一致。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER/ME、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息逐条一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家居装修, 老房水电改造+增项决策+家人口径冲突+按单结算, 第三方插入类) 与既有 48 篇三元组至少一元不同
