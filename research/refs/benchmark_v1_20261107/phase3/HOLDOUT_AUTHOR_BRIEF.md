# HOLDOUT_AUTHOR_BRIEF · 批 3 作者简报（wave6_holdout，H 段，2026-11-09）

> 适用：Phase 3 第 2b 步 HOLDOUT 120 篇（E401–E520）的 **Story Author（角色 A·H 段）**。
> 规范来源：本简报 + `phase1/NEW_STORY_SPEC_V1.md` + `phase2/TOPIC_RUBRIC_V1.md`（本专题条目）。
> 结构样例：`benchmark/stories_v2/wave3/E117/episode.json`（只学结构，不借情节）。

## 0. H 段强隔离（比 DEV 系更严，硬约束）

你是 HOLDOUT 专属会话（GIA 编号 H 段）。你**从未担任且此后不得担任**任何 DEV 系角色。

禁读（除下述白名单外的一切题库与运行材料）：
- `benchmark/stories_v2/` 下 **wave4_dev/、wave5_selection/** 全部内容（连目录列表都不要浏览）；
- stage1-4/wave1-3 各篇**正文**（E117 结构样例除外）；
- `logs/`、`candidates/`、一切审计/判决/评审/对账/验收文档（含 phase2/STORY_BATCH_REPORTS、phase3 全部报告）、`GOLD_SIDECAR/`、B 线。

允许读：本简报、NEW_STORY_SPEC_V1、TOPIC_RUBRIC_V1 你的专题条目、E117 episode.json、
`phase3/NAME_POOL_V1.json` 你的名字行。

开工 ack：写 `refs/benchmark_v1_20261107/phase3/isolation_acks/GIA-20261109-AUTHOR-H<xx>.json`
（role=story_author，session=AUTHOR-H<xx>，declarations 四项全 true + holdout_vault=true）。

**为什么更严**：HOLDOUT 是唯一一次消费的独立未见集。作者若见过 DEV 系失败模式与
评审口味，会把题造成"评审友好"——那不是独立泛化证据。

## 1. 产出（每篇三件，**直接写金库**）

写到 **`D:/lovebrain-arena_holdout_vault/wave6_holdout/<EID>/`**（仓库外的强隔离目录；
正文与 Gold 永不进 git 仓库——这是 P3-1.6 强隔离的实现，不要写错位置）：

- `episode.json`：结构同 E117 逐字段；每 turn `case_id":"<EID>_T01.."`、
  `turn_type":"reply"`、`gold":{}`、`feedback":{}`、
  `input":{"messages":[1-2条],"clock":"YYYY-MM-DD HH:MM","may_decline":bool}`
  （note 1–3 轮；intent 2–4 轮，T06 篇加密度）；
- `timeline.json`：`{"episode_id":"E4xx|E5xx","events":[...]}`;
- `exposure.json`：`{"status":"authored_pending_review","author":"ZCode bm1-hol author-H<xx> (AI)","author_type":"AI","real_user_material":false,"dev_exposed":false,"experiments_used":[],"themes":[...],"title":"...","may_decline_turns":[...],"created_at":"2026-11-09","domain":"<场景域>","primary_topic":"<Txx>","lineage_group_id":"LG-<EID>（孪生篇按分配）","kind":"standard_12t|standard_24t|standard_32t|twin_12t","batch":"W6-HOL-20261109","session":"AUTHOR-H<xx>","readers":["author"]}`。
- 不写 reference.json、不填 gold（角色 B·H 段的工作）。

## 2. 分配与场景

读 `phase3/NAME_POOL_V1.json` 的 `holdout_assignment`（E401–E520 逐篇预分配 HER/ME 名，
不得改动；配角从 `third_party_pool` 取）。

| 段 | 专题 | 篇 | 轮数 | 场景要求 |
|---|---|---|---|---|
| E401–E405 | T01 | 5 | 12 | 日常接话：5 个互不相同的生活场景 |
| E406–E430 | T02 | 25 | 12 | 调情反撩：25 个互不相同场景；每篇 ≥4 裸信号轮 + ≥1 反诱惑对照位，强度梯度；禁关键词调情 |
| E431–E450 | T03 | 20 | 12 | 深聊价值观：真实立场差 + 交锋位，禁连续提问+万能安慰 |
| E451–E462 | T04 | 12 | 12 | 主动推进：时机判断（接邀约/自留退路邀约/确认/转折/给空间/收尾） |
| E463–E474 | T05 | 12 | 12 | 情绪理解：具体化关心、合法暂停位、禁万能句 |
| E475–E486 | T06 | 12 | 12 | 意图改写：指令轮可核验（对象/时间/数量），改写/确认/撤销/代传 |
| E487–E496 | T07 | 10 | 12 | 事实归属：报错数/除数未知/三手转述/口头vs书面/预估vs终价等归属陷阱 |
| E497–E504 | T08 | 8 | 12 | 记忆提取：多线程事务、干扰位、陷阱位 |
| E505–E509 | T09 | 5 | **24** | 长程状态机（跨月，日期全可验算） |
| E510–E512 | T09 | 3 | **32** | 长程状态机（更长链，双旧事复活陷阱位） |
| E513/E514 | T10 G5 | 2 | 12 | 孪生：**时间变化**（同构，唯一扰动=关键日期），lineage=LG-T10-G5 |
| E515/E516 | T10 G6 | 2 | 12 | 孪生：**人物归属变化**（同构，唯一扰动=当事人），lineage=LG-T10-G6 |
| E517/E518 | T10 G7 | 2 | 12 | 孪生：**否定/状态变化**（同构，唯一扰动=取消或状态翻转），lineage=LG-T10-G7 |
| E519/E520 | T10 G8 | 2 | 12 | 孪生：**目标约束变化**（同构，唯一扰动=目标条件），lineage=LG-T10-G8 |

- 每篇 ≥1 个 may_decline=true 轮（T09 长剧 ≥2 个）；≥2 个跨轮回指（1 短 + 1 长≥4）；
- 场景域全库唯一（120 个互不重复的生活场景；简单生活值，无敏感绑定）；
- 孪生组两篇必须**同一会话**产出（同构轮卡逐字段对称、唯一扰动位变化、
  NEW_STORY_SPEC §7.1 负清单）；twin_role 分别 original/counterfactual。

## 3. 质量红线（同 DEV 系全部红线，另有两条）

- 无出题痕迹/元语；时间自洽（2026-11 月为主，clock 单调，星期-日期对齐；长剧可跨 2026-11 至 2026-12）；
- **T09 长剧**：所有日期/星期/间隔可验算（写完逐个验算）；取消-改期链 cancelled+asserted 配对
  留给标注；剧情要有跨月推进感（不是 24 条独立日常）；
- **孪生**：除扰动位外，两篇 persona 姓名相同、轮数相同、轮卡结构逐字段对称——
  这是为了单变量可判；人名允许同组重复（唯一例外）。

## 4. 自查与交付报告

三件 JSON 校验；轮数/case_id/ts/may_decline 一致；T09 篇日期验算表；孪生组同构自查
（逐字段 diff 只允许扰动位差异）。交付报告：EID/专题/轮数/回指位/may_decline 轮/
note/intent 轮/名字、校验结果、隔离声明（"未读取禁止材料；只写金库"）。
