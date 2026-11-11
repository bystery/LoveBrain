# E074 亲家母把菜单改了 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审/判决文档，仅使用任务书白名单（模板 + dedupe JSON 的 title/themes 字段 + wave1 E059 格式参照）。
- 全部人物（表妹、亲家母、酒店销售）、酒店名、价格均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：餐饮聚会；主线：HER 小鹿操办表妹 11 月 28 日订婚宴，试菜刚定稿，表妹未来婆婆（THIRD）下午进群一句话改两事——鳜鱼必须换东星斑、娘家加两桌。她夹在预算、酒店与不敢驳婆婆的表妹之间，用"把决定权踢回决策方+钱谁出谁拿主意"的策略理顺主体归属，随后经历验货、桌数二次变动、核单拦错、宴前改开席时间三次书面兜底，宴席圆满收束。
- 第三方插入类的关键设定：亲家母是实质性卷入者——她改变信息（菜单）、义务（差价归属）与计划（桌数、开席时间），ME 需全程正确把变动主体归属到 THIRD 而非表妹或酒店。

## 2. 连续性与回扣
- T01 试菜计划 → T02 试菜过关 → T03 婆婆插入引爆 → T04 两头为难求接法 → T05 传话策略 → T06 婆婆松口收敛 → T07 酒店落实+验货 → T08 验货过关+锁定期限 → T09 桌数二次收敛 → T10 锁定+核单拦错 → T11 改时间走书面 → T12 宴席圆满收尾。
- 跨轮引用原子 7 条（A10/A14/A17/A27/A31/A34/A37）≥3。
- 冲突型落实：E074_A10（superseded+negation，原菜单计划被推翻）、A14（换鱼诉求归属亲家母）、A19（差价归属婆婆方）、A27（锁定期限悬置）、A31（期限兑现）、A34（多次插入促成书面流程）。

## 3. ME 历史与边界
- ME 老唐：开过两年餐馆、懂宴席门道、劝人先拆事把人和钱分开（initial_kb.me）。
- T04 may_decline=true：ME 给的是方向（人和钱分开、决定权归决策方），不替她拍板；其余轮次以接执行报备、钉期限、给验货看点为主。
- ME 全程不替婆婆补台词、不分析长辈动机、不虚构酒店价格与厅期；加价金额 180 与差价归属均以 HER 转述为准。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，全部文本消息；确认单、变更单均以转述呈现，无图片。
- 未设 no_reply 轮。局限：婆婆的两次表态均经 HER 或表妹转述，属信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥12 字。
- 冲突轮落实：T03（A10 superseded+negation，第三方插入推翻原计划）、T06（A19 cost_attribution 确认归属 THIRD）、T09（A25/A26 桌数与补差二次收敛）、T11（A32/A34 第三次变动+书面兜底）。
- required_this_turn 原子 7 条（A10/A14/A17/A27/A31/A34/A37），全部来自早前轮次，6-9 区间内。

## 6. 已知风险
- 2026-11-16 为周一、11-19 为周四、11-26 为周四、11-28 为周六，台词中"这周四/周四上午/宴前一天"与真实星期一致，语义审查时以此为准。
- 东星斑加价 180/桌、13 桌定金流程均为剧情化数字，不构成消费参考；引用层级上婆婆原话均为 HER 转述，不构成虚构第三方直接发言。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注与 third_party_insert 事件类型
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(餐饮聚会, 订婚宴+试菜+亲家母改单+书面变更单, 第三方插入类) 与既有篇三元组不同
