# E078 华姐说压到三分钟 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 48 篇的 story_id/title/themes 防撞字段）。
- 全部人物（栗子、大树、老周、小都、华姐、王总监）、公司、年会均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：工作职场；主线：HER 栗子被点名牵头年会节目，串烧《打工魂》初成后，行政统筹华姐以全场超时为由把歌舞压到3分钟并重排顺序，同时转达王总监"老周必须露脸"的意愿——两条第三方要求涌入，她起初把主人张冠李戴；理清归属后两头谈判，rap改念白、小都顶段、2分50获批，经伴奏文件事故与带妆走台，年会演出成功并获"最佳气氛奖"。
- 生活动机真实可信：统筹压时与领导点名露脸是年会季最常见的双重挤压；转述走样、归属混谈是牵头人最典型的沟通故障。

## 2. 连续性与回扣
- T01 被点名求方向 → T02 串烧定案3分半 → T03 首排老周缺位 → T04 老周请删、统筹已报 → T05 第三方要求涌入（爆发）→ T06 归属捋清 → T07 结构变通 → T08 老周与华姐双线成交 → T09 规格明朗、组内转顺 → T10 伴奏文件事故 → T11 演出成功 → T12 获奖与饭约收尾。
- 跨轮引用原子 6 条（A16/A17/A24/A38/A39/A40）≥3。
- 冲突型=第三方插入类落实：T05（A14 superseded，顺位由三改七；A16 negation，老周意愿与王总监要求相抵）、T06（A17/A18 confirmed，归属更正）、T08（A24 superseded，老周立场反转；A25 confirmed，华姐批准）。THIRD 主体（华姐/王总监/老周）各自的要求被逐轮正确归属。

## 3. ME 历史与边界
- ME 大树：大学文艺部部长，串烧编排与伴奏剪辑在行；initial_kb 中"给建议先问要达成什么，再给两个选项"是他的风格锚点。
- T01/T07/T12 may_decline=true：军师之邀、编曲求援与饭约均可商量；T10 成品伴奏补发属已承诺的编外组员份内事，不设拒绝路径。
- ME 是公司外人，不越权判断公司政治，不替华姐或王总监补话；归属判断全部基于 HER 转述的群公告原文，并提示她以原文为准。

## 4. no_reply / 媒体局限
- HER 多轮单条或两条长消息为主；全部为文本消息，无语音/图片（群公告、统筹表以转述呈现，伴奏文件仅文字提及）。
- 未设 no_reply 轮。局限：华姐通知与王总监意愿均经 HER 二传，T05 特意保留她的转述混淆作为信息归属故障的载体。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T05（A14 superseded、A16 negation）、T06（A17/A18 confirmed 归属更正）、T08（A24 superseded、A25 confirmed）。
- 跨轮引用原子 6 条，≥3 条红线达成；required_this_turn 原子 6 条，全部引用早前轮次。

## 6. 已知风险
- 华姐、王总监、老周的原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 年会流程、预算归口、统筹规则均为剧情化简化，不构成职场实务指导；演出细节（唱劈、encore）为合成桥段。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(工作职场, 年会节目统筹+时长压缩+归属捋清+伴奏事故, 第三方插入类) 与既有篇三元组不同
