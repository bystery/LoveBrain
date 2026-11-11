# AUDIT_HC1 独立审计报告 — E401-E430（30篇 HOLDOUT）

> **审计会话**：AUDITOR-HC1（GIA-20261110-AUDITOR-HC1）
> **审计日期**：2026-11-10
> **审计范围**：E401-E430（E401-E405 T01日常接话 5篇 + E406-E430 T02调情反撩 25篇）
> **金库路径**：`D:/lovebrain-arena_holdout_vault/wave6_holdout/E401-E430/`

---

## 0. 隔离声明

本审计会话为 HOLDOUT 专属独立审计，已写入隔离确认至
`refs/benchmark_v1_20261107/phase3/isolation_acks/GIA-20261110-AUDITOR-HC1.json`。

- **no_dev_read**：true — 未读取 wave4_dev/ 任何文件
- **no_selection_read**：true — 未读取 wave5_selection/ 任何文件
- **no_logs_candidates_read**：true — 未读取 logs/、candidates/、GOLD_SIDECAR
- **holdout_vault**：true — 仅读取金库内 E401-E430 的 episode/reference/audit/timeline/exposure/runner_episode
- **辅助文件**：仅读取 NAME_POOL_V1.json（名字池，用于名字一致性校验），不含 DEV/SELECTION 正文

---

## 1. 审计汇总

| 指标 | 值 |
|---|---|
| 总篇数 | 30 |
| **PASS** | **16** |
| **FAIL** | **14** |
| 跨篇场景域重复 | 0（30篇全部唯一）
| 跨篇名污染 | 0（30篇 HER/ME 名互不交叉）

### 1.1 PASS/FAIL 一览

| 篇号 | 结果 | 专题 | 场景域 | 失败原因 |
|---|---|---|---|---|
| E401 | PASS | T01 | 阳台多肉养护 | — |
| E402 | PASS | T01 | 菜场周末买菜 | — |
| E403 | PASS | T01 | 小区流浪猫投喂 | — |
| E404 | PASS | T01 | 快递柜取件退换货 | — |
| E405 | PASS | T01 | 夜跑受伤休养 | — |
| E406 | PASS | T02 | 雨天下班共伞 | — |
| E407 | **FAIL** | T02 | 深夜线上听歌 | 跨轮回指缺长距(d≥4) |
| E408 | **FAIL** | T02 | 合租厨房做饭 | 跨轮回指缺长距(d≥4) |
| E409 | **FAIL** | T02 | 健身房力量训练 | 跨轮回指缺长距(d≥4) |
| E410 | **FAIL** | T02 | 深夜加班送宵夜 | 跨轮回指缺长距(d≥4) |
| E411 | **FAIL** | T02 | 社区烘焙兴趣班 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E412 | **FAIL** | T02 | 城南植物市集与阳台多肉 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E413 | **FAIL** | T02 | 沿江夜跑与跑后便利店 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E414 | **FAIL** | T02 | 文庙旧书摊与旧式茶馆 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E415 | **FAIL** | T02 | 高铁出差邻座与出站口 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E416 | **FAIL** | T02 | 河边外拍取景与小馆子 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E417 | **FAIL** | T02 | 咖啡馆拼桌加班与面馆 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E418 | **FAIL** | T02 | 早市买菜与早点铺 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E419 | **FAIL** | T02 | 社区琴房合奏与面包店 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E420 | **FAIL** | T02 | 裁缝铺量体裁衣与茶馆 | 跨轮回指缺短距(d=1)+缺长距(d≥4) |
| E421 | PASS | T02 | 二手黑胶唱片店淘碟 | — |
| E422 | PASS | T02 | 攀岩馆搭档较劲 | — |
| E423 | PASS | T02 | 社区夜校书法课同桌 | — |
| E424 | PASS | T02 | 24h便利店夜班守店 | — |
| E425 | PASS | T02 | 合租屋阳台深夜晾衣 | — |
| E426 | PASS | T02 | 天文台公众开放夜观星 | — |
| E427 | PASS | T02 | 公司团建密室逃脱分组 | — |
| E428 | PASS | T02 | 长途夜班卧铺大巴邻座 | — |
| E429 | PASS | T02 | 社区早市菜市场拼单 | — |
| E430 | PASS | T02 | 周末citywalk胶片约拍 | — |

### 1.2 失败项分类汇总

全部14篇FAIL的唯一失败项均为**审计项17（跨轮回指）**。所有其他17项（1-16、18）全部通过。

失败分两批：
- **E407-E410（4篇）**：`required_memory_atoms` 中 `required_this_turn=true` 的原子回指最大距离分别为 2/2/2/1，均不足 d≥4。这些篇有大量短距回指（s=9-11），但缺少长距回指。
- **E411-E420（10篇）**：`required_memory_atoms` 中 `required_this_turn=true` 原子数为 0。audit.md 明确声明"required_this_turn=0（来源均为当轮→回声原子按 GOLD_GUIDE §2.1 不标 required）"，跨轮回指改由 `t02_signal_gate.cross_ref` 字段承载。

---

## 2. 逐篇审计明细

### 审计项说明（18项）

| # | 审计项 | 方法 |
|---|---|---|
| 1 | 名字一致性 | persona.her.name/me.name vs NAME_POOL_V1.json holdout_assignment |
| 2 | 轮数正确 | 12轮 |
| 3 | case_id 唯一 | E401_T01..E401_T12 格式 |
| 4 | turn_type=reply | 所有轮 |
| 5 | gold={} | 所有轮空 |
| 6 | clock 单调不减 | 跨轮时间戳递增 |
| 7 | may_decline≥2 | 每篇至少2个may_decline=true轮 |
| 8 | intent 2-4 轮 | intent轮数在范围内 |
| 9 | note 1-3 轮 | note轮数在范围内 |
| 10 | 禁关键词 | 暧昧/心动/喜欢你/调情/撩拨 0命中 |
| 11 | quote strict | reference原子source.quote在episode消息文本中逐字可找到 |
| 12 | required来源早于当前轮 | required_this_turn=true原子source轮位早于当前轮 |
| 13 | runner_episode.json | 与episode.json逐字节一致（sha256对比） |
| 14 | audit.md 8节齐全 | 标题精确匹配 |
| 15 | 场景域唯一 | 30篇场景域互不重复 |
| 16 | T02信号门 | t02_signal_gate九项齐全（E406-E430） |
| 17 | 跨轮回指 | 每篇≥2（1短d=1 + 1长d≥4） |
| 18 | 跨篇名污染 | 30篇主角名互不交叉 |

### 通用通过项（30篇全部通过）

以下审计项在全部30篇中均通过，不再逐篇重复：

- **项1 名字一致性**：30篇 HER/ME 名与 NAME_POOL_V1.json holdout_assignment 逐篇一致
- **项2 轮数**：30篇均为12轮
- **项3 case_id**：30篇 case_id 格式正确且唯一（EID_T01..EID_T12）
- **项4 turn_type**：30篇所有轮 turn_type=reply
- **项5 gold={}**：30篇所有轮 gold 为空对象
- **项6 clock单调**：30篇时间戳跨轮单调不减
- **项10 禁关键词**：30篇 episode.json 内 暧昧/心动/喜欢你/调情/撩拨 0命中
- **项11 quote strict**：30篇 reference 原子 source.quote 在 source 轮消息文本中逐字可找到（0失败）
- **项12 required来源早于当前轮**：30篇 required_this_turn=true 原子的 source 轮位均早于当前轮（0失败）
- **项13 runner_episode一致**：30篇 runner_episode.json 与 episode.json sha256 hash 完全一致
- **项14 audit.md 8节**：30篇 audit.md 均含 8 节标题（## 0. 性质 至 ## 7. 出题痕迹检查）
- **项15 场景域唯一**：30篇场景域互不重复（0重复）
- **项18 跨篇名污染**：30篇主角名互不交叉（0冲突）

### 逐篇明细（仅列差异化项）

#### E401 — PASS
- HER: 凌霜晚 / ME: 湛砚洲 | T01 | 阳台多肉养护
- clock: 2026-11-09 19:36 -> 2026-11-15 21:37（7天连续）
- may_decline: T5, T8, T12（3轮） | intent: T4, T11（2轮） | note: T3, T9（2轮）
- 跨轮回指: 短d=1 有4个, 长d≥4 有3个 — PASS

#### E402 — PASS
- HER: 蔚芦月 / ME: 阚浔野 | T01 | 菜场周末买菜
- clock: 2026-11-14 08:37 -> 2026-11-22 11:07
- may_decline: T6, T12 | intent: T5, T10 | note: T3, T9
- 跨轮回指: 短d=1 有5个, 长d≥4 有3个 — PASS

#### E403 — PASS
- HER: 满苓溪 / ME: 经洄浦 | T01 | 小区流浪猫投喂
- clock: 2026-11-16 19:27 -> 2026-11-22 19:48
- may_decline: T5, T10 | intent: T6, T11 | note: T4, T9
- 跨轮回指: 短d=1 有6个, 长d≥4 有4个 — PASS

#### E404 — PASS
- HER: 弓莼岸 / ME: 逄连浦 | T01 | 快递柜取件退换货
- clock: 2026-11-09 12:38 -> 2026-11-15 20:38
- may_decline: T4, T12 | intent: T5, T10 | note: T3, T9
- 跨轮回指: 短d=1 有7个, 长d≥4 有1个 — PASS

#### E405 — PASS
- HER: 蒯庭芳 / ME: 相临川 | T01 | 夜跑受伤休养
- clock: 2026-11-09 20:38 -> 2026-11-17 20:08
- may_decline: T5, T10, T12（3轮） | intent: T6, T12 | note: T3, T9
- 跨轮回指: 短d=1 有4个, 长d≥4 有4个 — PASS

#### E406 — PASS
- HER: 查知雪 / ME: 逯云开 | T02 | 雨天下班共伞
- clock: 2026-11-09 18:28 -> 2026-11-11 22:38（2.5天）
- may_decline: T5, T12 | intent: T8, T11 | note: T3, T11
- t02_signal_gate: 9项齐全, signal_rounds=6, counter_signal=2
- 跨轮回指: 短d=1 有6个, 长d≥4 有2个 — PASS

#### E407 — FAIL
- HER: 桓映蓉 / ME: 况云舟 | T02 | 深夜线上听歌
- clock: 2026-11-09 23:12 -> 2026-11-14 00:18
- may_decline: T5, T12 | intent: T8, T11 | note: T3
- t02_signal_gate: 9项齐全, signal_rounds=6, counter_signal=2
- 跨轮回指: 短d=1 有9个, **长d≥4 有0个** — FAIL
  - required_this_turn 原子回指距离: [1,1,2,1,2,1,1,1,1,1,1]，最大距离=2（T4←T2）
  - t02_signal_gate.cross_ref 字符串引用 T02→T06→T07（距离5）和 T04→T10（距离6），但未编码为 required_this_turn=true 原子

#### E408 — FAIL
- HER: 麴若云 / ME: 堵其琛 | T02 | 合租厨房做饭
- clock: 2026-11-09 19:08 -> 2026-11-13 22:08
- may_decline: T5, T12 | intent: T8, T11 | note: T3
- t02_signal_gate: 9项齐全, signal_rounds=6, counter_signal=2
- 跨轮回指: 短d=1 有10个, **长d≥4 有0个** — FAIL
  - required_this_turn 原子回指距离最大=2（T4←T2）
  - cross_ref 字符串引用 T01→T12（距离11）和 T04→T11（距离7），但未编码为 required_this_turn 原子

#### E409 — FAIL
- HER: 冉清欢 / ME: 宰冬岭 | T02 | 健身房力量训练
- clock: 2026-11-11 19:38 -> 2026-11-13 22:08
- may_decline: T5, T12 | intent: T8, T11 | note: T3
- t02_signal_gate: 9项齐全, signal_rounds=6, counter_signal=2
- 跨轮回指: 短d=1 有10个, **长d≥4 有0个** — FAIL
  - required_this_turn 原子回指距离最大=2（T4←T2）
  - cross_ref 字符串引用 T02→T09（距离7）和 T04→T10（距离6），但未编码为 required_this_turn 原子

#### E410 — FAIL
- HER: 灌慕晴 / ME: 厍前子 | T02 | 深夜加班送宵夜
- clock: 2026-11-09 21:38 -> 2026-11-10 23:23
- may_decline: T5, T12 | intent: T8, T11 | note: T3
- t02_signal_gate: 9项齐全, signal_rounds=7, counter_signal=3
- 跨轮回指: 短d=1 有11个, **长d≥4 有0个** — FAIL
  - required_this_turn 原子回指距离全部=1（最大距离=1）
  - cross_ref 字符串引用 T04→T12（距离8）和 T05→T09（距离4），但未编码为 required_this_turn 原子

#### E411 — FAIL
- HER: 郁念慈 / ME: 越千山 | T02 | 社区烘焙兴趣班
- clock: 2026-11-07 14:15 -> 2026-11-14 21:48
- may_decline: T3, T12 | intent: T4, T10, T11（3轮） | note: T2, T7
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0（audit.md 明确声明"required_this_turn=0，来源均为当轮→回声原子按 GOLD_GUIDE §2.1 不标 required"）
  - t02_signal_gate.cross_ref 为结构化列表，含5条跨轮回指（1短d=1 + 3长d≥4），但未通过 required_memory_atoms 编码

#### E412 — FAIL
- HER: 桂微澜 / ME: 虞叙之 | T02 | 城南植物市集与阳台多肉
- clock: 2026-11-07 09:38 -> 2026-11-14 20:08
- may_decline: T3, T12 | intent: T4, T9, T10 | note: T7
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0
  - cross_ref 结构化列表含5条（0短d=1 + 2长d≥4）

#### E413 — FAIL
- HER: 屠静秋 / ME: 麋叙白 | T02 | 沿江夜跑与跑后便利店
- clock: 2026-11-07 19:38 -> 2026-11-14 22:38
- may_decline: T3, T12 | intent: T4, T9, T10 | note: T2
- t02_signal_gate: 9项齐全, signal_rounds=9, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0
  - cross_ref 结构化列表含5条（0短d=1 + 3长d≥4）

#### E414 — FAIL
- HER: 桑语冰 / ME: 濮台生 | T02 | 文庙旧书摊与旧式茶馆
- clock: 2026-11-07 14:28 -> 2026-11-14 21:08
- may_decline: T3, T12 | intent: T4, T7, T10 | note: T6
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0
  - cross_ref 结构化列表含5条（0短d=1 + 4长d≥4）

#### E415 — FAIL
- HER: 蔺以宁 / ME: 燕在野 | T02 | 高铁出差邻座与出站口
- clock: 2026-11-07 08:22 -> 2026-11-08 18:38
- may_decline: T3, T12 | intent: T9, T11 | note: T5
- t02_signal_gate: 9项齐全, signal_rounds=11, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0
  - cross_ref 结构化列表含5条（1短d=1 + 4长d≥4）

#### E416 — FAIL
- HER: 储晚舟 / ME: 舒子川 | T02 | 河边外拍取景与小馆子
- clock: 2026-11-07 15:08 -> 2026-11-14 21:38
- may_decline: T3, T12 | intent: T4, T9, T10 | note: T2
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0
  - cross_ref 结构化列表含5条（0短d=1 + 2长d≥4）

#### E417 — FAIL
- HER: 元如素 / ME: 车山关 | T02 | 咖啡馆拼桌加班与面馆
- clock: 2026-11-07 14:38 -> 2026-11-14 21:38
- may_decline: T3, T12 | intent: T4, T9, T10 | note: T2
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0
  - cross_ref 结构化列表含5条（0短d=1 + 2长d≥4）

#### E418 — FAIL
- HER: 丰南乔 / ME: 邵山月 | T02 | 早市买菜与早点铺
- clock: 2026-11-07 07:38 -> 2026-11-14 12:08
- may_decline: T3, T12 | intent: T4, T9, T10 | note: T2
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0
  - cross_ref 结构化列表含5条（0短d=1 + 2长d≥4）

#### E419 — FAIL
- HER: 鲍听竹 / ME: 甘岸闻 | T02 | 社区琴房合奏与面包店
- clock: 2026-11-07 14:08 -> 2026-11-14 21:08
- may_decline: T3, T12 | intent: T4, T9, T10 | note: T2
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0
  - cross_ref 结构化列表含5条（0短d=1 + 3长d≥4）

#### E420 — FAIL
- HER: 戎望舒 / ME: 祖知行 | T02 | 裁缝铺量体裁衣与茶馆
- clock: 2026-11-07 10:08 -> 2026-11-14 16:38
- may_decline: T3, T12 | intent: T4, T9, T10 | note: T2
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: **短d=1 有0个, 长d≥4 有0个** — FAIL
  - required_this_turn=true 原子数=0
  - cross_ref 结构化列表含6条（0短d=1 + 4长d≥4）

#### E421 — PASS
- HER: 巫月明 / ME: 乌思齐 | T02 | 二手黑胶唱片店淘碟
- clock: 2026-11-14 13:30 -> 2026-11-14 22:13（同日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

#### E422 — PASS
- HER: 焦星河 / ME: 巴见山 | T02 | 攀岩馆搭档较劲
- clock: 2026-11-15 14:15 -> 2026-11-15 22:19（同日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

#### E423 — PASS
- HER: 牧清浅 / ME: 山望岳 | T02 | 社区夜校书法课同桌
- clock: 2026-11-17 18:50 -> 2026-11-17 23:39（同日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

#### E424 — PASS
- HER: 谷疏影 / ME: 夔听涛 | T02 | 24h便利店夜班守店
- clock: 2026-11-18 22:12 -> 2026-11-19 02:32（跨日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

#### E425 — PASS
- HER: 鄂暗香 / ME: 索观澜 | T02 | 合租屋阳台深夜晾衣
- clock: 2026-11-19 22:12 -> 2026-11-20 02:32（跨日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

#### E426 — PASS
- HER: 咸初雪 / ME: 籍临风 | T02 | 天文台公众开放夜观星
- clock: 2026-11-21 19:15 -> 2026-11-22 00:52（跨日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

#### E427 — PASS
- HER: 赖未央 / ME: 卓对雨 | T02 | 公司团建密室逃脱分组
- clock: 2026-11-22 13:45 -> 2026-11-22 18:17（同日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

#### E428 — PASS
- HER: 蒙长安 / ME: 池迎霜 | T02 | 长途夜班卧铺大巴邻座
- clock: 2026-11-23 20:15 -> 2026-11-24 01:17（跨日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

#### E429 — PASS
- HER: 乔思远 / ME: 阴送秋 | T02 | 社区早市菜市场拼单
- clock: 2026-11-24 07:15 -> 2026-11-24 11:17（同日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

#### E430 — PASS
- HER: 胥怀瑾 / ME: 苍接冬 | T02 | 周末citywalk胶片约拍
- clock: 2026-11-28 13:45 -> 2026-11-28 18:17（同日）
- may_decline: T4, T10 | intent: T6, T9 | note: T3, T8
- t02_signal_gate: 9项齐全, signal_rounds=10, counter_signal=0
- 跨轮回指: 短d=1 有4个, 长d≥4 有2个 — PASS

---

## 3. 跨篇审计结果

### 项15 场景域唯一性 — PASS

30篇场景域全部互不重复（0重复）。完整列表：

| 篇号 | 场景域 |
|---|---|
| E401 | 阳台多肉养护 |
| E402 | 菜场周末买菜 |
| E403 | 小区流浪猫投喂 |
| E404 | 快递柜取件退换货 |
| E405 | 夜跑受伤休养 |
| E406 | 雨天下班共伞 |
| E407 | 深夜线上听歌 |
| E408 | 合租厨房做饭 |
| E409 | 健身房力量训练 |
| E410 | 深夜加班送宵夜 |
| E411 | 社区烘焙兴趣班 |
| E412 | 城南植物市集与阳台多肉 |
| E413 | 沿江夜跑与跑后便利店 |
| E414 | 文庙旧书摊与旧式茶馆 |
| E415 | 高铁出差邻座与出站口 |
| E416 | 河边外拍取景与小馆子 |
| E417 | 咖啡馆拼桌加班与面馆 |
| E418 | 早市买菜与早点铺 |
| E419 | 社区琴房合奏与面包店 |
| E420 | 裁缝铺量体裁衣与茶馆 |
| E421 | 二手黑胶唱片店淘碟 |
| E422 | 攀岩馆搭档较劲 |
| E423 | 社区夜校书法课同桌 |
| E424 | 24h便利店夜班守店 |
| E425 | 合租屋阳台深夜晾衣 |
| E426 | 天文台公众开放夜观星 |
| E427 | 公司团建密室逃脱分组 |
| E428 | 长途夜班卧铺大巴邻座 |
| E429 | 社区早市菜市场拼单 |
| E430 | 周末citywalk胶片约拍 |

### 项18 跨篇名污染 — PASS

30篇 HER/ME 主角名互不交叉：
- 无 HER 名与任何 ME 名重复
- 无同一 HER 名出现于多篇
- 无同一 ME 名出现于多篇
- HER 名池与 ME 名池零交集

---

## 4. T02 信号门审计结果（E406-E430，25篇）

### 项16 t02_signal_gate 九项齐全 — PASS（25/25）

全部25篇 T02 故事的 t02_signal_gate 均含以下九项：
- signal_rounds, anti_temptation, intensity_gradient, forbidden_keywords,
  cross_ref, may_decline, intent, scene_unique, third_party

signal_rounds 数量分布：
- E406-E410：6-7个（含 counter_signal 2-3个）
- E411-E430：9-11个（counter_signal 均为0）

---

## 5. 审计项17 跨轮回指 — 深入分析

### 5.1 判定标准

审计项17要求每篇≥2个跨轮回指（1短d=1 + 1长d≥4），通过 `required_memory_atoms` 中 `required_this_turn=true` 原子的 source 轮位与当前轮的距离判定。

### 5.2 三种 source schema 变体

审计中发现 reference.json 的 `required_memory_atoms[].source` 存在三种 schema 变体：

| 批次 | source.key | source.message_id | source.quote | 判定方法 |
|---|---|---|---|---|
| E401-E410 (Schema A) | case_id 格式 (如 "E401_T01") | "m0" (仅消息序号) | 完整逐字短语 | source.key 定位源轮 |
| E411-E420 (Schema B) | 描述性标签 (如 "奶油多打匀一半") | "E411_T02_M00" (编码轮次) | 完整逐字短语 | source.message_id 定位源轮 |
| E421-E430 (Schema C) | 无 | "E421_T01_M00" (编码轮次) | 短子串片段 | source.message_id 定位源轮 |

审计脚本已统一处理三种 schema：优先检查 source.key 是否为 case_id 格式，其次从 source.message_id 解析轮次编码，最后回退到当前轮。

### 5.3 失败批次分析

#### E407-E410（4篇）：有 required_this_turn 原子但缺长距

这4篇有 11 个 required_this_turn=true 原子（全部为短距 d=1 或 d=2），但无一达到 d≥4。

| 篇号 | required_this_turn 原子数 | 回指距离分布 | 最大距离 | cross_ref 字段长距证据 |
|---|---|---|---|---|
| E407 | 11 | [1,1,2,1,2,1,1,1,1,1,1] | 2 | T02→T06(d5), T04→T10(d6) |
| E408 | 11 | [1,1,2,1,1,1,1,1,1,1,1] | 2 | T01→T12(d11), T04→T11(d7) |
| E409 | 11 | [1,1,2,1,1,1,1,1,1,1,1] | 2 | T02→T09(d7), T04→T10(d6) |
| E410 | 11 | [1,1,1,1,1,1,1,1,1,1,1] | 1 | T04→T12(d8), T05→T09(d4) |

注意：E407-E410 的 t02_signal_gate.cross_ref 为字符串格式（如 "T02深夜想你→T06打字删掉"），确实记录了长距跨轮回指（最大距离 6-11），但这些引用未编码为 required_this_turn=true 原子，因此按审计项17的主机制判定为 FAIL。

#### E411-E420（10篇）：required_this_turn 原子数为0

这10篇的 audit.md 明确声明 "required_this_turn=0（来源均为当轮→回声原子按 GOLD_GUIDE §2.1 不标 required）"，即所有 memory atom 的 source 均为当轮消息，不存在跨轮回指原子。

跨轮回指改由 t02_signal_gate.cross_ref 结构化列表承载（每篇 5-6 条，含 turn/ref_to/type/note 字段）。从 cross_ref 提取的距离：

| 篇号 | cross_ref 条目数 | 短d=1 | 长d≥4 | 最长距离 |
|---|---|---|---|---|
| E411 | 5 | 1 | 3 | d8 (T09←T01) |
| E412 | 5 | 0 | 2 | — |
| E413 | 5 | 0 | 3 | — |
| E414 | 5 | 0 | 4 | — |
| E415 | 5 | 1 | 4 | d7 (T09←T02) |
| E416 | 5 | 0 | 2 | — |
| E417 | 5 | 0 | 2 | — |
| E418 | 5 | 0 | 2 | — |
| E419 | 5 | 0 | 3 | — |
| E420 | 6 | 0 | 4 | — |

这些 cross_ref 条目确实包含长距引用，但：
1. 未通过 required_memory_atoms 的 required_this_turn 机制编码
2. E412-E414、E416-E420 的 cross_ref 中无 d=1 短距条目

### 5.4 裁定

按审计项17的严格定义（通过 required_this_turn=true 原子判定跨轮回指），E407-E420 共14篇 FAIL。

如将 t02_signal_gate.cross_ref 也纳入跨轮回指判定机制，则：
- E407-E410：长距需求可通过 cross_ref 满足（但 cross_ref 为字符串格式，非结构化原子）
- E411-E420：长距需求可通过 cross_ref 满足，但 E412-E414、E416-E420 仍缺短距 d=1

此裁定差异需由 benchmark 维护者裁决 cross_ref 是否为审计项17的合规载体。

---

## 6. 质量观察（非门禁）

以下为审计过程中的质量观察，不构成 PASS/FAIL 判定：

1. **schema 一致性**：30篇存在三种 source schema 变体（A/B/C），建议统一为一种以降低评估器复杂度。Schema A（E401-E410）最完整，source.key 同时提供 case_id。
2. **T02 信号密度梯度**：E406-E410 的 signal_rounds 为 6-7 个且含 2-3 counter_signal；E411-E430 的 signal_rounds 为 9-11 个且 counter_signal 为 0。后者信号密度更高但缺少反信号降温位。
3. **时间跨度设计**：E401-E405（T01）时间跨度 7 天，符合日常场景；E406-E410（T02）跨度 2-5 天；E411-E420 跨度 7-8 天；E421-E430 多为同日或跨日单次场景。E415 时间跨度最短（约1天），适合出差邻座场景。
4. **may_decline 分布**：E401-E410 的 may_decline 轮多为 T5+T12 或 T5+T8+T12；E411-E430 统一为 T3+T12 或 T4+T10，模式较固定。
5. **intent/note 分布**：E421-E430 的 intent（T6,T9）和 note（T3,T8）轮位完全一致，10篇结构高度同构；E401-E420 的轮位有合理变化。

---

## 7. 审计结论

### 最终裁定

| 结果 | 篇数 | 篇号 |
|---|---|---|
| PASS | 16 | E401-E406, E421-E430 |
| FAIL | 14 | E407-E420 |

### PASS 批次特征
- E401-E406（6篇）：Schema A，required_this_turn 原子充足，短距+长距回指完备
- E421-E430（10篇）：Schema C，required_this_turn=7，每篇4短+2长回指

### FAIL 批次特征
- E407-E410（4篇）：Schema A，required_this_turn=11 但全部短距（max d=2），长距引用仅存于 cross_ref 字符串
- E411-E420（10篇）：Schema B，required_this_turn=0，跨轮回指完全转移至 cross_ref 结构化列表

### 非门禁项全部通过
- 名字一致性、轮数、case_id、turn_type、gold、clock、may_decline、intent、note、禁关键词、quote strict、required来源顺序、runner_episode一致、audit.md 8节、场景域唯一、T02信号门、跨篇名污染 — 全部30篇通过

### 待裁决项
审计项17（跨轮回指）的失败是否成立，取决于 t02_signal_gate.cross_ref 是否被接受为合规的跨轮回指载体：
- 若仅 required_this_turn 原子为合规载体：14篇 FAIL 维持
- 若 cross_ref 也为合规载体：E407-E410 可能改为 PASS（有长距），E411-E420 中 E412-E414、E416-E420 仍 FAIL（缺短距 d=1），E411、E415 可能改为 PASS

---

*审计脚本：`refs/benchmark_v1_20261107/phase3/_audit_hc1.py`*
*审计结果JSON：`refs/benchmark_v1_20261107/phase3/_audit_hc1_results.json`*
*隔离确认：`refs/benchmark_v1_20261107/phase3/isolation_acks/GIA-20261110-AUDITOR-HC1.json`*
