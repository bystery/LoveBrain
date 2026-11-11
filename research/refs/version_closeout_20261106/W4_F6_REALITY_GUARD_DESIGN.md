# W4 — F6「现实一致性」轻量防护机制设计（RealityGuard v0）

> 日期：2026-11-06。被设计对象：`D:/LoveBrain` 分支 `closeout-20261106`（commit 40c1f12），只读，本文不改任何代码。
> 审计依据：`T5_KNOWN_FAILURES_AUDIT.md` F6.1–F6.5（现状：运行时场景判定纯关键词 `CurrentSceneInjection.kt:29-69`，异地邀约/时空错位/线下承诺/冒充已发生零拦截，仅 onboarding 静态画像预设 `onboarding.md:221-260`）。
> 设计纪律：能力缺口优先机制结构，不往 prompt 堆规则段；零额外模型调用；复用已验证机制形态（arena uc 未定块，`candidates/window_expand_b_norecap_uc/mechanism/uncertain_rules.py`）。

---

## 1. 机制设计总览

**RealityGuard = 一组纯函数判定器 + 三档处置**，与 `CurrentSceneInjection` 同构：不调模型、不持状态、无命中时输出为空串（prompt 与现状逐字节相同）。

```
输入（全部已在设备上，零新增读取成本）：
  ① 画像文本：understand/warmth.md、understand/her.md（PromptBuilder.kt:302-305 已读出，顺带传入即可）
  ② 场景链：moment/scene.md（结构化事实，带时间戳/src/spk/subj，SceneChainStore.kt:40-50）
  ③ 事项计划：moment/plan.md（进行中/已结束两区，OngoingPlanStore.kt:223-234）
  ④ 本轮消息：List<ChatMessage>（role/content/timestamp，ChatMessage.kt:6-11）
  ⑤ 当前场景标签：CurrentSceneInjection.infer 的返回值（复用，不重算）
  ⑥ 时钟：Clock.wallClock()/epochMs()（PromptBuilder.kt:339 先例）
输出：
  RealitySignals（结构化信号集）→ RealityRules.evaluate() → List<RealityHit(ruleId, tier, evidenceAnchor)>
处置：
  T0 shadow：仅日志（L.w 风格，只记 ruleId+信号长度，正文不进日志，隐私红线沿 SceneChainStore.kt:112-116 先例）
  T1 注入：现实锚点块（uc 块形态），拼进 PromptBudget.byBlocks（PromptBuilder.kt:242-244/256-258）
  T2 标记：对候选四风格卡打 flag（ReplySchemesArrived 发射点 GenerationEngine.kt:246-248）
```

**为何零额外模型调用可行**：F6.1–F6.4 的判定全部只依赖"已知画像/事实/计划 + 确定时钟 + 词类匹配"，与 `CurrentSceneInjection`（纯关键词判定先例）和 arena uc（纯函数未定块、双域 KEEP 已验证）同构。不需要理解语义的裁判，只需要确定性矛盾检测——"她说她在上海"对"候选写我去接你"这类冲突是结构性的，不是语义模糊的。

**为何不做成 prompt 规则段**：审计已证资产层规则（format.md/stage.md）对 F6.1–F6.4 零覆盖，且堆规则段无数据支撑——模型不知道"异地"是运行时事实，因为没有任何机制把画像里的异地状态提取成可判定信号。先做机制信号，prompt 侧只接收锚点块的确定性事实。

---

## 2. 判定规则表（F6.1–F6.4 全覆盖）

信号词类（确定性，仅两类：位置 deixis 与线下动作，不按故事词表枚举）：

| 词类 | 内容（示例，实现时按族整理） |
|---|---|
| `OFFLINE_ACTION`（线下动作/见面动作） | 见面/约饭/出来/去找你/过来找你/到你那边/接你/来我家/看电影/买了票/订了餐厅/飞过来/高铁/机票 |
| `STRONG_COMMIT`（确定时空的强承诺） | OFFLINE_ACTION + 确定时间词（今晚/明晚X点/周六下午/周X）+ 确定地点词（在XX/那家店/地铁口）同句命中 |
| `PAST_EVENT_CLAIM`（冒充已发生） | 昨天/上次/那天 + 见过/见了/一起吃了/在一起了/我们去（过去式断言，指向 HER-我 双方） |
| `FUTURE_TIME`（未来时间词，抑制项） | 明天/后天/周末/下周/改天/有机会/以后 |

| Rule ID | 覆盖 | 触发条件（全部 AND，缺任一不触发） | 反例抑制（误报门） |
|---|---|---|---|
| **R1 地理矛盾** | F6.1 | 画像异地信号=真 ∧ 本轮 HER 消息或候选文本命中 OFFLINE_ACTION ∧ 无进行中"见面安排"类事项（plan.md 进行中区无 见面/机票/请假/探亲 类条目） | 计划中存在进行中的见面安排→抑制（用户真的在筹备见面）；异地信号来自近 7 天场景事实时同样生效；异地信号缺失/写"待了解"→不判 |
| **R2 时间矛盾** | F6.2 | clock 处于深夜窗（23:00–06:00）∧ 线下动作指向**当下时段**（现在/今晚/马上/就出来），FUTURE_TIME 命中→不触发 | 未来时间词（明晚/周末约饭）完全不触发；深夜纯线上聊天/视频邀约不触发（只认 OFFLINE_ACTION） |
| **R3 阶段不匹配承诺** | F6.3 | warmth.md 阶段标签∈{初识,破冰,相识初期}（分层由确定性标签映射表给出，标签缺失→不判）∧ STRONG_COMMIT 命中 | 弱承诺（改天/想见你/有机会）永不触发；阶段标签是画像原文摘录不是猜的；同居/热恋等晚期标签→不触发 |
| **R4 冒充已发生** | F6.4 | PAST_EVENT_CLAIM 命中 ∧ 在证据窗内查无锚点：scene.md + memory/raw_scene.md（**全量**，不受 2h 注入窗限制）+ plan.md 已结束区，bigram 重合 ≥0.34（沿用 uc `_overlap` 口径 uncertain_rules.py:51-55） | 无异地/时间前题要求，但证据窗**必须先查全量历史**——上周真见过但已出 2h 注入窗的不算编造；查无任何相关锚点才判 |

**F6.5 关联（不在本机制主修范围）**：现实锚点块提供确定性"时间/城市/计划状态"事实，天然部分抵消 `CurrentSceneInjection` 双轨在异地画像下把每轮框进"认真沟通"的偏置（T5 审计 F6.5）；根治属 M3 场景判定多信号改造，本文只登记不展开。

---

## 3. 数据可得性核查（file:line）

**已有的：**

| 数据 | 位置 | 可判定性 |
|---|---|---|
| 异地/同城状态 | `understand/warmth.md` "## 背景细节 - 异地/同城：…"（结构模板 `onboarding.md:379`；onboarding 按问卷 dur_distance 写入，`OnboardingGraph.kt:354-358`） | 按行正则可解析，但为自由文本 |
| 她的城市 | `understand/her.md` "## 基本信息 - 年龄/职业/城市：…"（模板 `onboarding.md:302-303`） | 自由文本，常为"待了解"；**异地状态比城市比对可靠，规则只依赖前者** |
| 关系阶段 | `understand/warmth.md` "阶段标签"（`onboarding.md:347`）；assets 阶段节选已注入（`PromptBuilder.kt:306` extractStageSection） | 自由文本→需确定性标签映射表 |
| 场景事实 | `moment/scene.md`，`- [yyyy-MM-dd HH:mm] 标签：事实\|src=…\|spk=…\|subj=…`（解析先例 `SceneChainInjection.kt:28-37`；存储结构 `SceneChainStore.kt:40-50`） | 带时间戳/主体，可作 R4 证据窗 |
| 历史事实 | `memory/raw_scene.md`（全量归档，T5 审计 F5.1 行；读取先例 `KnowledgeContextReader.kt:29-31`） | R4 证据窗必须包含它 |
| 事项计划 | `moment/plan.md`，`itemId~name \| status \| [time]state（当前）`，进行中/已结束两区（`OngoingPlanStore.kt:223-234`） | 可判"有无进行中的见面安排" |
| 消息时间 | `ChatMessage.timestamp: Long`（`ChatMessage.kt:10`）；时钟 `Clock` 端口（`PromptBuilder.kt:55,339`） | R2 深夜窗判定确定 |
| 现有场景标签 | `CurrentSceneInjection.infer`（`CurrentSceneInjection.kt:29-69`） | 复用"主动邀约"作为 R2/R3 的辅助信号，不重算 |

**缺的（如实登记，缺→不判，绝不猜）：**
1. **她的"当前位置"状态**：只有静态城市字段，无运行时"她在出差/在上海"结构化状态——目前只能从 scene.md 自由文本事实里抓，抓不到就放弃 R1 的位置细分，只做"异地↔线下动作"粗判。
2. **她的作息/时区**：深夜窗按用户设备时钟，跨国时差不感知——R2 对跨国异地会误报，shadow 期必须重点观察；本版不做时区推断。
3. **阶段标签枚举**：无结构化阶段字段，需一张确定性映射表（初识/破冰/暧昧/热恋/磨合/危机→tier），表外的自造标签一律归"未知→不判"。
4. **候选文本的时序属性**：候选是生成结果，"今晚出来"在 R2 里只能按词类判当下指向，无法解析真实语义时间——这是词类法的固有上限，靠 shadow 数据决定是否值得引入极小的结构化输出扩展（不在本版）。

---

## 4. 三档处置设计

### T0 仅日志（shadow）——**本版默认启用档**
- 接线点：`GenerationEngine.replyStream` 拿到 messages 与画像读出后调一次 `RealityRules.evaluate()`；命中即 `L.w("RealityGuard R1 hit: signals=…")`（只记 ruleId/信号词类数/场景标签，**正文与专有名词不进日志**，沿 `SceneChainStore.kt:112-116` 隐私先例）。
- 成本：一次纯函数调用（<1ms），零模型调用，零 UI 变化，零 prompt 变化。

### T1 静默注入"现实锚点"块——shadow 验证后按规则逐个开启
- 形态复用 arena uc 未定块（`uncertain_rules.py:14-17`）：标题 + 逐字摘录行 + 口径尾句，预算 ≤120 CU，最多 3 行；**只列确定性事实原文**（异地状态行、当前时段行、进行中见面计划行），不改写、不写禁令正文；无命中返回空串，prompt 与现状逐字节相同。
- 标题：`# 【现实锚点：系统判定的当前事实，不是对话内容】`；尾句口径：`以上为系统读到的当前时间/位置/计划状态，与它们矛盾的表述不得写入任何候选。`
- 注入位置：紧邻 sceneBlock 进 `PromptBudget.byBlocks`（`PromptBuilder.kt:242-244,256-258`；主动开场分支 `:419`、仅看本轮 `:276` 是否注入待 shadow 数据定）。注意：锚点块为非空时 `PromptByteFreezeBaselineTest` 冻结表需新增固定用例重录（空串用例不变）。

### T2 拦截候选标记——本版只留接口，不默认开
- 接线点：`ReplySchemesArrived` 发射前（`GenerationEngine.kt:246-248`）对四个 scheme 正文跑 OFFLINE_ACTION/STRONG_COMMIT 词类；命中在 `ReplySchemes`（`Models.kt:114`）上加 `realityFlags: List<String>`。
- **设计为"标记"而非"丢弃"**：丢弃会造成四卡空屏、打断用户流；标记由 UI 在风格卡上渲染角标，用户自行判断——符合"参考答案是证据与合理行动集合"的自由度纪律。

### 本版启用建议
**全规则 T0 起步（`AppConfig.REALITY_GUARD_TIER = 0`）**；R4（冒充已发生）因证据窗机制最扎实，shadow 误报率达标后第一个升 T1；R2 次之（时钟是硬事实但存在跨国时差误报风险）；R1/R3 依赖画像文本质量，最后升级。T2 本版不启用，仅落 schema 字段。

---

## 5. 误报边界与红线对齐

用户红线是"**不编造**"，不是"不许调情邀约"——所有规则的共同边界：**只在与已知现实信号结构性冲突时触发；信号缺失/写"待了解"/表外标签 → 一律不判**。宁可放走 10 个真矛盾，不拦 1 个正常邀约（shadow 期就是量化这个比例）。

| 规则 | 主要误报源 | 边界设计 |
|---|---|---|
| R1 | 异地状态过期（搬家/用户正在出差见面）；onboarding 误判异地 | 见面安排类进行中事项→抑制；异地信号要求 warmth.md 明确字段或近 7 天场景事实 |
| R2 | 深夜约的是"明晚"；跨国时差 | FUTURE_TIME 命中不触发；只认当下时段指向词；shadow 期分"跨国异地"子样本观察 |
| R3 | 用户恰在推进期主动约；阶段标签滞后 | 只判 STRONG_COMMIT（确定时间+地点同句）；标签缺失不判；弱承诺词永不触发 |
| R4 | 旧事真实发生但已出 2h 注入窗 | 证据窗查 raw_scene.md **全量**+plan 已结束区，不只查注入窗；重合阈值 0.34 偏保守（宁可漏判不冤枉） |

调整纪律：词类表/阈值改动按故障族版本化 + 离线重放重估（§6），不围绕单条对话特化（§0.1 第 4 条）。

---

## 6. Shadow 期验证方案

1. **先离线回放再上线**：规则是纯函数，先在既有候选 harness 答卷对话集上离线重放 R1–R4，估各规则触发率与表面误报，触发率异常（如 R2 每轮都响）先修词类再进产品。
2. **产品内 shadow 收集**：目标 ≥2 周或 ≥200 次回复生成；每周导出 ruleId 命中计数、触发率、按场景标签分布。
3. **人工标注**：worker 对每条命中按上下文判真/误报。升档门槛：每规则误报率 <20% 才升 T1（R4 因历史证据窗弱允许 <30%）；连续两周零命中的规则降级为"保留代码、不接线"。
4. **成功判据**：shadow 期结束交付三值结论——各规则 触发率/误报率/升档建议；不达标如实保留 T0 或淘汰该规则，不靠调阈值凑数。

---

## 7. 实现工作量估计（产品侧，Kotlin）

| 件 | 内容 | 估时 |
|---|---|---|
| `domain/RealitySignals.kt` | 纯函数：解析 warmth.md 异地行/阶段标签、plan.md 见面安排扫描、消息词类匹配 | 0.5 天 |
| `domain/RealityRules.kt` | R1–R4 纯判定 + 词类表常量 + 标签映射表 | 1 天 |
| `domain/prompt/RealityAnchorBlock.kt` | T1 注入块（uc 形态，≤120 CU，空串保字节不变） | 0.5 天 |
| 接线（T0+T1） | PromptBuilder 三分支 + GenerationEngine shadow 日志 + AppConfig 开关 | 1 天 |
| T2 接口 | ReplySchemes 加 realityFlags + 解析点标记（UI 角标另计） | 0.5 天 |
| 测试 | 纯函数单测（R1–R4 正反例+抑制例）+ 冻结表新增非空锚点用例重录 + 预算计入验证 | 1 天 |
| **合计** | T0+T1 全量约 **4.5 人日**；T2 若含 UI 角标 +1 天 | |

风险登记：①冻结字节基线需重录（仅非空锚点用例）；②锚点块计入 9000 预算（byBlocks 传参）；③warmth.md 无"背景细节"节的旧库 → 信号缺失不判，属预期行为。
