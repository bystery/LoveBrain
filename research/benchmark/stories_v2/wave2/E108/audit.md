# E108 我留的居然是周日 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 dedupe JSON 的 story_id/title/themes 字段）。
- 全部人物、餐馆、老板均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：餐饮聚会；冲突型：时间线倒错更正类。主线：HER 佳一以转正为名组酸汤鱼局，向 ME 和同事称周六晚七点、向老板留桌，并把周六的舞蹈课调到周日为局让路——错误时间承载了应约与调课承诺；周五老板确认'周日晚上'引爆倒错，她与老板改单又碰壁（周六晚满座），最终拍板周日中午缩桌两人、给值班同事打包，局成、老板赠饮认账、同事回话认账，以'便签三遍'新规和窗边位邀约收尾。
- 生活动机真实可信：记错约期且早有行动压身，实证纠正时承诺连环松动，是典型的记忆倒错引爆局。

## 2. 连续性与回扣
- T01 发局（周六口径） → T02 承诺压身（同事应约+调课） → T03 细节钉死 → T04 记录实证倒错 → T05 三口径对撞 → T06 改单碰壁 → T07 两选项悬空 → T08 拍板缩桌+打包 → T09 成局兑现 → T10 调课成常驻（回扣 T02） → T11 打包认账（回扣 T08） → T12 便签新规（回扣 T04 倒错）。
- 跨轮引用原子 8 条（A08/A12/A17/A21/A24/A26/A29/A32）≥3。
- 时间线倒错更正类落实：A08（retracted+negation，周六口径被记录推翻）、A12（asserted，错误周六承载承诺）、A17（asserted，周六路径消失）、A21（打包补救义务）、A24（confirmed，留桌承诺闭环）、A26（confirmed，调课口径更新）、A32（新规溯源于倒错）。

## 3. ME 历史与边界
- ME 大乔：嘴刁、能一口验出锅底正不正、守时烦变卦（initial_kb.me），惯用'行，几点'接约。
- T07 与 T12 may_decline=true：T07 她脑子一团乱来讨倾向，ME 可以只摆代价不拍板；T12 收尾闲问，保留不接路径。
- ME 全程不替她改单、不替她通知同事、不虚构老板与同事言行；T09 '锅底正'判断来源为 initial_kb 的行家设定，不虚构当轮发言。

## 4. no_reply / 媒体局限
- HER 每轮单条消息，全部文本；无语音/图片（留桌记录、群消息均以转述呈现）。
- 未设 no_reply 轮。局限：老板与同事言行均经 HER 转述引语，无独立信源；留桌记录原文未展示全文。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互异、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- required_this_turn 原子全篇 8 条（T04/T05/T07/T08/T09/T10/T11/T12 各 1），全部来自早前轮次，在 6-9 区间。
- unknowns/protected_expressions/forbidden_factual_claims 均为列表，protected 逐条为当轮 HER 台词子串。

## 6. 已知风险
- 周六（ME 与同事口径）与周日（老板留桌）双日期贯穿全篇，语义审查时注意各轮引用的日期归属不串。
- '两个半'为她的玩笑口径（两人吃+打包一份），数字不作严格人数核算。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词、无评审术语
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=餐饮聚会）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] (域, 主题集, 冲突型)=(餐饮聚会, 酸汤鱼探店+留桌承诺+缩桌打包, 时间线倒错更正类) 与既有 64 篇三元组不同
