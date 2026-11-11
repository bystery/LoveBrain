# E114 别再传话了 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审 JSON，仅使用任务书白名单（模板 + 既有 dedupe 的 story_id/title/themes 字段 + wave1 E059 格式参照）。
- 全部人物、公司、编制事件均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：工作职场；主线：HER 阿满答应大学同学周锐的内推请求并承诺'盯到底'，流程顺利之际 HR（第一位第三方）实质介入：编制冻结、材料转二组、要求她退出跟进不再传话——保人义务被第三方中止；她按 ME 建议如实摊牌，遭遇周锐冷淡与二组主管（第二位第三方）加微信划界'别管了'；一面通过后她拒红包立边界、周锐主动和解，终面通过后群里公开致谢带来短暂烫手，组长咖啡表态解铃，入职还奶茶债收尾。
- 生活动机真实可信：内推人被流程要求退场、承诺与合规打架、功劳公开时的敏感，是职场最常见的第三方变局课题。

## 2. 连续性与回扣
- T01 取经 → T02 承诺升级'盯到底' → T03 正面评价与客服消耗 → T04 HR 介入（第三方插入引爆）→ T05 两头堵 → T06 摊牌与降温 → T07 主管划界 → T08 对组长含糊 → T09 一面通过+和解 → T10 终面通过+公开致谢烫手 → T11 组长解铃 → T12 奶茶债收尾。
- 跨轮引用原子 5 条（A09/A11/A15/A18/A24/A27/A30/A33 中 required 8 条）≥3。
- 冲突型=第三方插入类落实：T04 HR 实质卷入改变义务（A07 confirmed、A08 退出令、A09 承诺 suspended+negation）；T07 二组主管二次划界（A16）；T10 公开致谢触发合规敏感（A27）；T11 组长表态（A28 confirmed）收束；THIRD 归属清晰（HR、二组主管、组长、周锐各自独立原子）。

## 3. ME 历史与边界
- ME 老程：前同事、被内推进现公司的过来人（initial_kb.me），把丑话说在前头。
- ME 全程不出头代办：不替她跟 HR 或主管交涉，不给话术之外的越界建议；T06 之后建议她'不主动补话'守住退出边界。
- T09 拒收红包、T11 '没瞒'复盘均由 HER 自己完成，ME 只做肯定与方法论认领。

## 4. no_reply / 媒体局限
- HER 每轮单条长消息，情绪浓度递进（郑重→两难→没劲→舒展→落地）。
- 未设 no_reply 轮。局限：HR 谈话、主管微信、组长咖啡的原话均经 HER 转述引用，属刻意的信息二传设计；无群聊截图媒体。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T04（A09 suspended+negation，承诺与退出令相撞）、T06（A15 retracted+negation，承诺收束）、T07（A18 confirmed，HR 令持续有效）、T11（A30 resolved，忐忑解除）。
- 跨轮引用原子 8 条，≥3 条红线达成；required_this_turn 原子 8 条（T04/T05/T06/T07/T09/T10/T11/T12 各 1），全部来自早前轮次。

## 6. 已知风险
- HR、二组主管、组长、周锐的原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 编制冻结、内推流程为剧情化简化处理，不构成 HR 指导；日期（2026-11-02 周一起）与'下周二面试'表述一致。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=工作职场）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(工作职场, 内推保人+编制改道+第三方退出令+拒礼边界, 第三方插入类) 与既有 64 篇三元组至少一元不同
