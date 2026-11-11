# E112 邻居的漆匠 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 dedupe JSON 的 story_id/title/themes 字段 + E059 五文件作格式参照）。
- 全部人物、施工方、报价、保修条款均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：家居装修；主线：HER 佳佳家刷漆在即，对门陈姐推荐熟识漆匠甘师傅（工钱省小两千），她动了删项换人的念头；中段甘师傅实质卷入——上门看现场并声明'只保面层'，罗工长随即声明别人刷漆不背锅，责任断层明牌把她夹在中间；算账权衡与 ME 一次干脆的立场后她决定不删项，用甘师傅自己的原话当台阶回绝陈姐，人情无损；完工后'基层加面层保修两年'写入单子，陈姐反而效仿选罗工长，决策被生活验证。
- 生活动机真实可信：省小钱换责任断层、驳不驳邻居面子，是装修季最常见的双重分寸题。

## 2. 连续性与回扣
- T01 进度同步 → T02 邻居推荐介入 → T03 删项念头 → T04 改单实操遇'你想好了?' → T05 甘师傅上门立责任线 → T06 罗工长不背锅 → T07 算账与邻里人情 → T08 要一句准话 → T09 不删项+话术 → T10 人情落地 → T11 完工保修落单 → T12 邻居效仿收尾。
- 跨轮引用原子 9 条（message 跨轮 6 条：A18/A21/A23/A27/A30/A33；initial_kb 跨轮 3 条：A09/A15/A24）≥3。
- 冲突型=第三方插入类落实：E112_A04（THIRD 甘师傅由陈姐引入）、A13（negation，甘师傅只保面层的责任声明）、A16（negation，罗工长不背锅）、A17（被夹住）、A26/A27（原话转化为台阶）、A29（THIRD 退回备选）、A31-A32（confirmed 完工落单）——中段第三方实质卷入改变 HER 对罗工长的义务，THIRD 归属清晰（甘师傅经陈姐引入、陈姐持有关系）。

## 3. ME 历史与边界
- ME 老白：前年自装全程盯工地，知道删项要走书面、保修要一家认账；分寸是帮人只摆账不替人定（initial_kb.me）。
- T08 与 T12 may_decline=true：T08 '要一句准话'可答可不答，参考卡给了给立场的路径；T12 收尾乐子可轻答。
- ME 全程不替她拍板（T08 给的是'我家不删'的偏好+决定权归还）、不贬低任何一方、不虚构保修与报价细节。

## 4. no_reply / 媒体局限
- HER 每轮单条消息，全部为文本；单子、样品、漆色均以转述呈现，无照片类媒体（T11 提到拍照发你，属转述）。
- 未设 no_reply 轮。局限：施工方原话均经 HER 转述引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T05（A13 negation，责任线公开化）、T06（A16 negation+责任冲突）、T08（A22 索要立场+A23 confirmed）、T11/T12（A31/A32/A36 confirmed 决策闭环）。
- 跨轮引用原子 7 条，≥3 条红线达成；required_this_turn 原子 8 条，全部来自早前轮次或 initial_kb。

## 6. 已知风险
- 两千、保修两年均为剧情化数字，不构成装修指导；罗工长与甘师傅原话均出自 HER 转述引号，语义审查时注意引用层级。
- '跑水帮忙'的邻里旧事仅一笔带过，不展开成新事件线。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家居装修, 刷漆删项犹豫+漆匠责任分界+邻里人情台阶+保修落单, 第三方插入类) 与既有 64 篇三元组至少一元不同
