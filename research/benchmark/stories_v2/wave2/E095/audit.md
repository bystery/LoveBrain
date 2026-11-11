# E095 三管血改期记 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + W03-06_SKELETON_DEDUPE 防撞清单 + E059 格式参照）。
- 全部人物、医院、数值均为合成剧情；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：医疗健康；主线：HER 宁宁孕 24 周约 11 月 6 日周五糖耐，老公夜班无法陪检，请老程陪同并备好禁食请假与加餐；周五晨突发发烧 37.9 自主取消，陪同与请假一并落空；假以下周一顶前台抵偿，抢号改约 11 月 12 日周四并二度开口请老程；中途她逞强想独行被撑住，周四三管血完成；一小时血糖 10.3 踩线，医嘱控糖两周并于 27 日复查；她扎指测糖、破戒半个芒果自控收住，复查达标，以两家人聚餐补早茶收尾。
- 生活动机真实可信：产检取消改期与陪检人情是孕产家庭最常见的调度难题。

## 2. 连续性与回扣
- T01 陪检请求 → T02 万事俱备 → T03 发烧取消 → T04 代价落地与抢号 → T05 改约与二度请求 → T06 逞强动摇 → T07 陪同确认与流程复述 → T08 一小时值踩线 → T09 控糖日常 → T10 破戒自控 → T11 复查达标 → T12 聚餐谢幕。
- 跨轮引用原子 8 条（A09/A14/A16/A19/A23/A26/A29/A31）≥3，全部为消息源。
- 冲突型=取消改期类落实：T03 取消（A06 suspended+negation、A08 suspended+negation、A09 escort_void）、T05 改约重启（A12 confirmed）、T08 一小时值未达标（A20 negation）、T11 复查达标（A27/A28 confirmed）。

## 3. ME 历史与边界
- ME 老程：陪老婆跑过全程产检、糖耐门儿清（initial_kb.me），经验信条是改期不稀奇、别拖着不重约（initial_kb.lessons）。
- T01、T05 may_decline=true：两次陪检请求均为实质付出，参考卡保留婉拒路径。
- T12 may_decline=true：聚餐赴约可商量。
- ME 全程不做诊断性表达，医学口径止于复述医生原话；不替她定禁食与食谱。

## 4. no_reply / 媒体局限
- HER 多轮单条或双条文本消息；T03、T08 为补发两条。全部为文本，无语音/图片（检查报告以转述数值呈现）。
- 未设 no_reply 轮。局限：医生的话全部经 HER 转述，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互异、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词或 initial_kb 的逐字子串并带 message_id/key；value 均 ≥4 实义词。
- required_this_turn 原子 8 条（T03/T05/T06/T07/T08/T10/T11/T12 各 1），全部引自早前轮次或 initial_kb。
- protected_expressions 每轮均为本轮 HER 台词逐字子串。

## 6. 已知风险
- 血糖数值与医嘱为剧情化简化，不构成医学指导；语义审查时防止 ME 输出超出转述口径的医学断言。
- 陪检请求两度发出与撤回，审查时注意 ME 回应不得让 HER 的退路（王姐备选）被误读为已完成托付。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/title/themes（首元素=域）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(医疗健康, 孕期糖耐陪检+取消改期+控糖两周+老同学陪检, 取消改期类) 与既有篇三元组不同
