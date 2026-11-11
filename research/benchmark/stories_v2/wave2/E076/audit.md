# E076 红叶改了两回期 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审/判决文档，仅使用任务书白名单（模板 + dedupe JSON 的 title/themes 字段 + wave1 E059 格式参照）。
- 全部人物（建哥、小白）、青梧山、入海口均为合成地点与合成人物；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：出行拼车；主线：HER 小满牵头 11 月 7 日青梧山红叶四人拼车，垫两人份车费并立 72 小时退出线；车主家事引发改期三连——延后两小时砍徒步（#1）、整体取消迁 14 号（#2，引爆费用：小白依规全退、四人变三人每人补 50、山宿退订重订涨 20）、预报有雨后再改时刻凌晨抢晴（#3），最终雨雾红叶如仙境兑现，尾声她升级出"先收钱再发车"的新规。
- 取消改期类的关键设定：取消与改期层层叠加且每次都牵动已生效的钱与房（车费 300→补差 100、山宿退订重订），红叶季节窗口给每次改动加上不可逆的代价感。

## 2. 连续性与回扣
- T01 成局垫钱 → T02 第四人变数 → T03 车主家事冲击 → T04 改期#1 → T05 改期#2 取消 → T06 退出与补差 → T07 补差拍板 → T08 空转日善后 → T09 雨情动摇 → T10 改期#3 凌晨抢晴 → T11 赌赢兑现 → T12 新局与新规收尾。
- 跨轮引用原子 8 条（A09/A12/A15/A20/A24/A27/A30/A36/A39，其中 8 条 required）≥3。
- 冲突型落实：E076_A12（superseded，改周日备选被否）、A15（cancelled+negation，7 号行程整体取消）、A20（superseded，四人结构塌成三人）、A27（confirmed，14 号新锚点）、A33（superseded，降雨威胁被凌晨方案规避）、A36（confirmed，凌晨方案兑现）。

## 3. ME 历史与边界
- ME 阿澈：爬山认识的靠谱搭子，遇变数先问她保什么舍什么，判断留给她拍板（initial_kb.me）。
- T01/T09/T12 may_decline=true：T01 入局自主、T09 改不改她定、T12 去不去他一句话；其余轮次以接报备、算取舍账、钉新锚点为主。
- ME 全程不替建哥做改期决定（T03 关键）、不断言天气与红叶状态（T05/T09 关键）、不虚构山路与景致细节。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，全部文本消息；天气预报、云图均以转述呈现，无图片。
- 未设 no_reply 轮。局限：车主与成员表态均经 HER 转述，属信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥12 字。
- 冲突轮落实：T04（A12 superseded）、T05（A15 cancelled+negation）、T06（A20 superseded）、T09（A30 旧担忧重燃）、T10（A33 superseded）。
- required_this_turn 原子 8 条（A09/A12/A15/A20/A24/A27/A30/A36/A39 中 8 个），全部来自早前轮次，6-9 区间内。

## 6. 已知风险
- 2026-11-07、11-14 均为周六，与台词"7号周六/14号周六"一致；2026-11-12 为周四、11-16 为周一。
- 包车 600/四人、补差 50/人、房涨 20/晚为剧情化数字，内部自洽（600=4×150；三人摊 200=150+50）。
- 凌晨 5 点上山属剧情化冒险桥段，参考卡已要求 ME 带一句安全提醒，不构成户外指导。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注与三次改期事件
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(出行拼车, 周边红叶拼车+车主改期+成员退出补差+凌晨抢晴, 取消改期类) 与既有篇三元组不同
