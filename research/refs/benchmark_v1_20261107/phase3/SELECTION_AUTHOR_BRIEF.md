# SELECTION_AUTHOR_BRIEF · 批 2 作者简报（wave5_selection，2026-11-09）

> 适用：Phase 3 第 2 步 SELECTION 40 篇（E339–E378）的 **Story Author（角色 A）**。
> 规范来源：本简报 + `phase1/NEW_STORY_SPEC_V1.md` + `phase2/TOPIC_RUBRIC_V1.md`（本专题条目）；
> 冲突时以本简报为准。结构样例：`benchmark/stories_v2/wave3/E117/episode.json`（只学结构，不借情节）。

## 0. 角色隔离（硬约束）

你不得读取：`logs/`、`candidates/`、任何判决/评审/审计文档（含 `STORY_BATCH_REPORTS/`）、
`GOLD_SIDECAR/`、**任何其他 episode 正文**（唯一例外 E117 结构样例）、wave4_dev 各篇 audit.md、
phase3 的对账/验收报告。允许读：本简报、NEW_STORY_SPEC_V1、TOPIC_RUBRIC_V1 你的专题条目、
E117、`phase3/NAME_POOL_V1.json` 你的名字行。
**为什么**：看过失败材料的作者会把题造成"专治某失败"，污染独立测试。

开工 ack：写 `refs/benchmark_v1_20261107/phase3/isolation_acks/<你的ACK_ID>.json`：
`{"ack":"generation_isolation_ack","role":"story_author","session":"<你的SESSION>","episodes":[...],"date":"2026-11-09","declarations":{"no_logs_read":true,"no_candidates_read":true,"no_other_episode_read":true,"no_audit_read":true}}`

## 1. 产出（每篇三件 → `benchmark/stories_v2/wave5_selection/<EID>/`）

- `episode.json`：结构同 E117 逐字段（persona/initial_kb/turns），turns 数与分配表一致；
  每 turn：`case_id":"<EID>_T01.."`、`turn_type":"reply"`、`gold":{}`、`feedback":{}`、
  `input":{"messages":[...],"clock":"YYYY-MM-DD HH:MM","may_decline":bool}`；
  `messages` 是自上轮以来的新增消息（1–2 条/轮，role=HER/ME，ME 极少 0–1 次/篇）；
  note（`input.note`，军师备注第一人称）1–3 轮；intent（`input.intent":{"enabled":true,"text":"..."}`）2–4 轮
  （T06 篇按 rubric 提高密度）。
- `timeline.json`：`{"episode_id":"E3xx","events":[{"turn":"E3xx_T01","ts":"...","event":"..."}]}`。
- `exposure.json`：`{"status":"authored_pending_review","author":"ZCode bm1-sel author-<X> (AI)","author_type":"AI","real_user_material":false,"dev_exposed":false,"experiments_used":[],"themes":[...],"title":"...","may_decline_turns":[...],"created_at":"2026-11-09","domain":"<场景域>","primary_topic":"<Txx>","lineage_group_id":"LG-<EID>（孪生篇按分配表）","kind":"standard_12t|standard_24t|twin_12t","batch":"W5-SEL-20261109","session":"<你的SESSION>","readers":["author"]}`。
- **不写 reference.json、不填 gold**（角色 B 的工作）。写完三件跑 JSON 校验。

## 2. 分配表（篇/专题/轮数/名字/场景种子）

读 `refs/benchmark_v1_20261107/phase3/NAME_POOL_V1.json` 的 `selection_assignment`：
HER/ME 名字**逐篇预分配，不得改用池外人名**；配角从 `third_party_pool` 取（可跨篇低频复用），
不得借用全库已有主角名。每篇的场景域（domain）按下方"场景种子"展开，**不得与其他篇同场景**：

| 段 | 专题 | 篇 | 场景种子（逐篇各选其一展开，同段内不得重复） |
|---|---|---|---|
| E339 | T01 | 1 | 业余无线电爱好者社区 |
| E340–E346 | T02 | 7 | 社区合唱团 / 城市观鸟小组 / 烘焙工作室 / 桨板俱乐部 / 古筝琴行 / 剧本杀店 / 天文观测同好 |
| E347–E350 | T03 | 4 | 独立书店店员 / 夜校哲学课 / 纪录片观影小组 / 社区博物馆讲解志愿者 |
| E351–E353 | T04 | 3 | 业余戏剧社 / 城市骑行团 / 手作皮具课 |
| E354–E359 | T05 | 6 | 宠物寄养互助群 / 社区健身角 / 夜班通勤拼车 / 老宅翻新互助 / 考研自习室搭子 / 医院陪护 / 社区业委会（按需取 6） |
| E360–E365 | T06 | 6 | 搬家互助 / 团购开团 / 拼单旅行 / 借还器材 / 代收快递 / 改约课表（指令改写类场景，逐篇各一） |
| E366–E370 | T07 | 5 | 小区公告口径 / 活动规则归属 / 转让信息核实 / 场地预订口径 / 二手交易条款 |
| E371–E373 | T08 | 3 | 器材保养经验 / 多线程室友事务 / 家庭账本对账 |
| E374 | T09 | 1（24 轮） | 社区图书馆志愿排班长线（跨月状态机） |
| E375/E376 | T10 G3 目标变化 | 2（孪生） | 同一马拉松备赛训练群（同构故事，唯一扰动=训练目标改变） |
| E377/E378 | T10 G4 时间变化 | 2（孪生） | 同一社区花园认养计划（同构故事，唯一扰动=关键日期改变） |

孪生规则：同组两篇**必须同一会话产出**（同构轮卡逐字段对称，唯一扰动位变化；
NEW_STORY_SPEC §7.1 负清单）；两篇的 lineage_group_id 分别为 `LG-T10-G3` / `LG-T10-G4`，
twin_role 分别 `original` / `counterfactual`。

## 3. 故事质量要求（同 BATCH1 §2，全部适用）

连续故事（关系背景/事件推进/情绪分寸变化，不是 12 条独立问答）；双方成年人；角色声音一致；
自然嵌入可核验事实（日期/金额/地点/约定/第三方/承诺）；≥2 个跨轮回指位（1 短距 1-2 轮 +
1 长距 ≥4 轮）；**≥1 个 may_decline=true 轮**（写入 exposure.may_decline_turns）；时间自洽
（2026-11 月内为主，clock 递增）；无出题痕迹/元语；金额日期用普通生活值。

**T02 特别要求**（rubric §0.5 行为锚 + C1 判别力标准）：每篇 T02 ≥4 个"裸信号轮"
（信号在对话面可见，不藏在私有 kb）+ ≥1 个反诱惑对照位（对方收信号/变冷淡/转移话题，
测试是否知进退）；信号强度有梯度；禁止把普通礼貌冒充调情；不靠暧昧关键词。

**T03 特别要求**：有真实观点与分歧交锋位，不是连续提问+万能安慰；观点交换要有可判的立场差。

**T06 特别要求**：每篇含明确指令轮（改写/确认/撤销/代传），指令内容可核验（对象/时间/数量）。

**T09（E374，24 轮）**：跨月状态机（排班/值班/调换/请假/恢复），日期全部可验算且自洽。

## 4. 自查与交付报告

三件 JSON 校验通过（`python -c "import json;json.load(open(r'<file>',encoding='utf-8'))"`）；
轮数/case_id 序号/ts 时序/may_decline 标记一致。交付报告列出：EID/专题/域/轮数/回指位轮号/
may_decline 轮/名字使用（HER/ME）、三件校验结果、隔离声明。
