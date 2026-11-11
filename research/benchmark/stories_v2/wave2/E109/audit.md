# E109 师傅的微信给你 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 dedupe JSON 的 story_id/title/themes 字段 + E059 五文件作格式参照）。
- 全部人物、师傅、县城行程均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：出行拼车；主线：HER 蔓蔓周六要送父母和一车坛子回县城，向老同学 ME 借包车师傅资源；她先试压价，压价未成后想借 ME 人情出面抹零头，又想让定金经 ME 账户中转，两道边界（介绍不背书、钱款不经手）先后被 ME 温和守住；她对回程空放费起疑，经行情解释与总包价落纸化解；行李临时加码由师傅换车解决；行程闭环后她主动总结出"介绍归介绍，担保归担保"的分工，以一袋小米谢礼收尾。
- 生活动机真实可信：借熟人资源时的压价冲动与"钱该不该从朋友手里过"是常见分寸题。

## 2. 连续性与回扣
- T01 求介绍 → T02 自主压价 → T03 借人情压价（拒）→ T04 定金经手请求（拒）→ T05 自己转账+追问规矩 → T06 空放费起疑 → T07 总包价落定 → T08 缝纫机变数 → T09 换车出发 → T10 到家师傅表态 → T11 八字共识 → T12 小米谢礼收尾。
- 跨轮引用原子 7 条（A10/A13/A19/A26/A33/A36/A39）≥3。
- 冲突型=边界类落实：E109_A07（negation，小雷不让价）、A10（压价压力升级为借人情）、A13（negation，ME 钱款不经手旧规）、A14（retracted，经手请求撤下）、A18（negation，对空放费的不信任）、A33（retracted，压价请求事后撤回）、A34/A35/A36（confirmed，共识）。

## 3. ME 历史与边界
- ME 老唐：老同学、常年坐小雷的车，规矩为"介绍归介绍，担保归担保，价钱和钱款从不经手"（initial_kb.me）。
- T12 may_decline=true：谢礼收下与否属可轻答可玩笑的场合，参考卡保留不接长话题的路径。
- ME 全程不代砍价、不经手钱款、不替师傅作超出观察的保证，行情解释限于"回程空放常见"的常识口径。

## 4. no_reply / 媒体局限
- HER 每轮单条消息为主，全部为文本消息；师傅报价与表态均经 HER 转述并引用，属刻意的信息二传设计。
- 未设 no_reply 轮。局限：付款方式、车型等细节以转述呈现，无截图类媒体。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A07 negation）、T04（A13 negation）、T06（A18 negation）、T11（A34/A35/A36 confirmed）。
- 跨轮引用原子 7 条，≥3 条红线达成；required_this_turn 原子 7 条，全部来自早前轮次或 initial_kb。

## 6. 已知风险
- 师傅原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 八百/一千一为剧情化报价，不构成任何行情指导；"比高铁贵一截"未核实总价。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(出行拼车, 包车师傅熟人介绍+压价与担保边界+钱款不经手, 边界类) 与既有 64 篇三元组至少一元不同
