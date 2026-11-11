# T5 — KNOWN_FAILURES 逐项产品源码审计（2026-11-06）

> 被审对象：`D:/LoveBrain` 分支 `closeout-20261106`（commit 40c1f12），只读，未改任何代码。
> 依据：`refs/KNOWN_FAILURES.md` v0.2 + FK-LR2-01 / FK-INJECT-01。
> 方法：逐项对当前源码定位成因代码（file:line 证据），三值判决 STILL_PRESENT / FIXED / NEEDS_RUNTIME_TEST。
> 注意：arena 已证机制（window_expand_b / uc / guard）只存在于 arena 候选 harness，**产品源码均未移植**，下表"arena 能否消除"均以此为前提。

## 判决汇总

| 判决 | 项 |
|---|---|
| FIXED | F1.1, F1.2, F3.1, F3.2（主因，残留改名旁路） |
| STILL_PRESENT | F1.3, F1.4（部分）, F2.1, F3.3, F5.1, F5.2, F6.1–F6.4（机制缺失）, F6.5, FK-LR2-01 |
| PARTIAL / NEEDS_RUNTIME_TEST | F2.2, F3.4, F4.1–F4.4, FK-INJECT-01 |

## 逐项明细

### F1 角色错位

| 项 | 判决 | 证据与成因核实 |
|---|---|---|
| F1.1 想法被当成她发来的消息 | **FIXED** | `ComposerStore.kt:388` 真实聊天集合 `messages.filter { role != IDEA }`；`ChatTranscriptBlock.kt:45` IDEA 不进 `<chat>` 围栏；`TopicRecorder.kt:115-117` 入库只取 HER/ME。IDEA 现仅表示"输入对象=补充"（ComposerStore.kt:840-846），不是消息角色。 |
| F1.2 历史 IDEA 行残留进对话记录 | **FIXED** | 生产路径不再产生 IDEA 行；过渡期残留由 `ComposerStore.kt:793-811` 按正文折进备注并去重（"同一条备注不可能被注入两次"）。`RecentRoundStore.kt:36-38` IDEA 不写入 recent.md。 |
| F1.3 备注私密感受泄露进可发送话术 | **STILL_PRESENT** | 备注（userHint）逐字注入：`RecentRoundStore.kt:52-54` 以"我的想法：…"持久写入 recent.md 并随"最近对话"进 prompt；资产层唯一约束是 `format.md:12`"本轮想法决定要说什么，所有候选都遵守"——**没有任何"不得把备注原文/私密感受写进候选"的反泄露规则**（core/redline/naturalness_check 均无）。arena guard 只守 JSON 契约，不守此条 → **需产品侧另修**（资产加反泄露规则 + 运行探针验证）。 |
| F1.4 备注猜测被升级为事实入库 | **STILL_PRESENT（部分修复）** | 修复了一半：指向 IDEA 的 source 被拒——`SceneChainStore.kt:78-118` validSourceMap 只含冻结 HER/ME，非法来源整条拒绝。残留：**无来源 fact 仍入库**（`SceneChainStore.kt:94-105` 无 sourceIds → 标记"未核实"照常追加；`174-178` 追加且 evidenceTime=0）；且备注文本随 recent→raw_chat→reflect/lessons 提取链（`PromptBuilder.kt:490-505`）进画像，提取侧无证据门。需产品侧：无来源 fact 拒收或显式降级。 |

### F2 话题污染

| 项 | 判决 | 证据与成因核实 |
|---|---|---|
| F2.1 话题已切换仍纠缠 | **STILL_PRESENT** | 切换判定仍是单判据：`TopicRecorder.kt:123` `shouldRotate = topicLabel.isNotBlank() && topicStatus == "new"`——完全信任模型单轮输出的 topic_status，模型漏报 new 则旧场景链保留。部分缓解：scene 条目 2h TTL + 最多 2 条（`AppConfig.kt:61-62`），污染有界但不清零。arena 无对应冠军机制 → 需产品侧（M3：多信号话题切换判定）。 |
| F2.2 已回应仍反复追问 | **NEEDS_RUNTIME_TEST（部分缓解）** | plan 侧已缓解：`OngoingPlanStore.kt:248-252` FILLER_STATES 填充词不入库、`132-134` 同 state 幂等跳过、`PromptKnowledgeSection.kt:140` 注入头注明"不必每条都提"；资产层 `naturalness_check.md`"最后检查旧事复读和擅自承诺"。但 scene 事实去重要求 sourceIds 完全相同（`SceneChainStore.kt:183-196`），跨轮换源重述同一事项→重复条目，无"已回应过"机制。需运行探针。 |

### F3 记忆污染

| 项 | 判决 | 证据与成因核实 |
|---|---|---|
| F3.1 候选话术当已发事实入库 | **FIXED** | `SceneChainStore.kt:57,78-118`：sourceIds 必须命中本轮冻结 HER/ME 快照，候选/IDEA/不存在 ID 整条拒绝；`RecentRoundStore.kt:35-36,63-72`：scheme=null 不写"最终回复"行，点赞候选显式标注"未确认发送"。 |
| F3.2 计划改期/取消后旧事项复活 | **FIXED（主因；残留改名旁路）** | `OngoingPlanStore.kt:37-44,91-102,120-129`：稳定 itemId（优先模型返回，回退 name hash）+ 已结束事项非终态输出不复活，除非携带非空 sourceIds 新证据。残留：`216-219` itemId 由 name 派生——模型给同一已取消事项**换一个名字**即获得新身份，以"新事项"进 active 区，旧终态名存实亡。建议运行探针量化频率；根治需产品侧语义级事项身份。 |
| F3.3 纠错后旧值在摘要中回流 | **STILL_PRESENT** | 纠正只在**注入侧**生效（`MemoryRefPolicy.kt:53-90` + `PromptKnowledgeSection.kt` 注入前过滤）。提取/重写侧完全不知道纠正表：`PromptBuilder.kt:490-505` reflect 用户提示直接读 me/her/warmth + rawTopic 原文，`KnowledgeProfileStore.kt` / `KnowledgeLessonsRewriteService.kt` 全文无 correction/纠正引用（grep 零命中）→ 画像/经验重写时旧值按原文回流覆盖。**需产品侧另修**：提取链必须消费 corrections 表。 |
| F3.4 同一事实多次摘要重复增信 | **PARTIAL / NEEDS_RUNTIME_TEST** | lessons 提取有提示词级去重（`knowledge_prompt/lessons.md:18,32,133`"不重复本质相同/去重判断"）；scene 侧不同来源重复追加仍累积（`SceneChainStore.kt:197-200`），无证据计数/置信模型。运行验证。 |

### F4 表达缺陷（均属 prompt/行为层）

| 项 | 判决 | 证据 |
|---|---|---|
| F4.1 平淡无调情 | **NEEDS_RUNTIME_TEST** | 资产有风格与阶段策略（stage.md 216 行、format.md 风格差异条款），无机制保障；效果只能运行判定。 |
| F4.2 AI 味 | **PARTIAL / NEEDS_RUNTIME_TEST** | `naturalness_check.md`（17 行）成体系：反客服腔样例、改短改具体、旧事复读检查。属提示词缓解，非机制。 |
| F4.3 四风格趋同 | **PARTIAL / NEEDS_RUNTIME_TEST** | `format.md:97,101-104` 四风格逐一定义并要求"保留差异，清醒不写成普通礼貌回复"。实际分化度需运行探针。 |
| F4.4 舔狗化 | **PARTIAL / NEEDS_RUNTIME_TEST** | `core.md:28,64`"不验货/不迎合"心智 + `redline.md` 尊严底线与止损。同上，运行验证。 |

### F5 装配缺陷

| 项 | 判决 | 证据与成因核实 |
|---|---|---|
| F5.1 N=2 截断丢交流单元 | **STILL_PRESENT** | `AppConfig.kt:60` `MAX_TOPIC_TURNS = 2`；`RecentRoundStore.kt:94-98` 溢出轮整体移入 memory/raw_chat.md；**raw_chat 不进回复 prompt**（仅 `KnowledgeContextReader.kt:29-31` 供提取）。跨轮交流单元（问句在上上轮）对回复不可见。预算侧 `PromptBudget.kt:63-81` 超预算再按整行掐 chat 头部。arena window_expand_b（3600 CU 扩窗 + ≤400 CU 确定性逐字摘录 recap，FK-LR2-01 KEEP 判决）只在 arena 生效 → **需产品侧移植或等价机制**。 |
| F5.2 Lost-in-the-Middle | **STILL_PRESENT** | `PromptBudget.kt:30-83` 装配序 knowledge→scene→intent→idea→chat→timestamp，超支时依次砍知识尾部/较旧 recent/对话头部——关键约束落中段即被整段省略（"…已省略…"标记）；`applyBudget`（90-96）头 50%+尾 40%、**中段直接丢弃**。无关键约束前置/锚定机制。arena recap 锚定只覆盖逐字事实，不覆盖约束位置 → 需产品侧重排或前置机制。 |

### F6 现实一致性盲区

| 项 | 判决 | 证据与成因核实 |
|---|---|---|
| F6.1–F6.4 物理/时空/分离/远程方案 | **STILL_PRESENT（仅静态画像预设）** | 全库 grep"异地"只命中 `knowledge_prompt/onboarding.md:221-236`（问卷期画像预设：阶段放缓、渠道预设、雷区预设）——是**静态画像**，不是运行时机制。`CurrentSceneInjection.kt:29-69` 场景判定纯关键词（睡了/生气/见面/难过…），无位置/时间/分离状态感知；format.md/stage.md 无异地运行期规则。她说"还没下课"之类的邀约事故无任何代码层拦截。需产品侧（M3：意图/场景机制接入现实状态约束）。 |
| F6.5 反过纠（句句异地好难） | **STILL_PRESENT / NEEDS_RUNTIME_TEST** | 无任何反向配重规则；关键词场景双轨（`CurrentSceneInjection.kt`）在异地画像下反而可能把每轮都框进"认真沟通"。 |

### FK-LR2-01 长对话逐字事实塌陷

**STILL_PRESENT（结构性）**。产品回复 prompt 的上下文上限 = recent 2 轮（`AppConfig.kt:60`）+ scene 2 条（:62）+ lessons 末 3 块（`PromptKnowledgeSection.kt:77`）+ 画像。raw_chat/raw_scene/raw_topic 一律不进回复 prompt：raw_topic 仅进 reflect（`PromptBuilder.kt:496,501`），raw_chat/raw_scene 仅进提取上下文（`KnowledgeContextReader.kt:29-31`）。与 LR2 判决确认的塌陷成因（"persona 种子 + 近窗滑窗、概括级记忆承载不了逐字事实"）同构。arena 修复 window_expand_b 已双域 KEEP（>24 轮真实保持 1.0、成本 −41.9%/−44.0%），**未移植进产品** → 产品侧移植是收益/成本证据最全的一项。

### FK-INJECT-01 注入块致契约失败

**NEEDS_RUNTIME_TEST（产品侧结构条件存在、无守卫）**。产品回复 prompt 追加多个注入块（场景块 `PromptBuilder.kt:236,250`、意图块 :419-421、aggressive 围栏 :87-91）后再要求 format.md 的严格 JSON 契约（format.md:44-47 四槽）；`GenerationEngine.kt:302-313` 解析失败仅报错，网络重试（:216-223）不覆盖 schema 漂移，**无输出契约守卫/修复重试**。arena 的 guard（W17c，本族设计靶点）与 uc 锚定未进产品。产品实际失败率需运行探针。

## STILL_PRESENT 项 × arena 冠军机制可消除性

| 项 | b_norecap 扩窗 | uc 锚定 | guard 守卫 | 结论 |
|---|---|---|---|---|
| F5.1 / FK-LR2-01 | **能**（arena 已证 KEEP） | 部分 | 否 | 产品需移植扩窗+逐字摘录 |
| F5.2 | 部分 | 部分 | 否 | 需产品侧约束前置/重排 |
| F1.3 | 否 | 否 | 否 | 产品资产加反泄露规则 |
| F1.4 | 否（arena 有来源账本教训：fact_ledger 被淘汰） | 否 | 否 | 产品侧无来源 fact 门禁 |
| F2.1 | 否 | 否 | 否 | 产品侧 M3 话题判定 |
| F3.3 | 否 | 否 | 否 | 产品侧提取链消费 corrections |
| F6.1–F6.5 | 否 | 否 | 否 | 产品侧现实状态约束机制 |
| FK-INJECT-01 | 否 | 部分（锚定降低漂移） | **能**（设计靶点） | 产品侧引入 guard 或运行验证 |

## 最需产品侧另修 Top 3

1. **FK-LR2-01 / F5.1（+F5.2 联动）**：2 轮窗 + 无逐字召回，长对话事实塌陷结构性复现；arena window_expand_b 已给出双域 KEEP 证据（真实保持 1.0、成本 −42~44%），移植路径最明确。
2. **F3.3**：纠正如实生效于注入、却在 reflect/lessons 重写时被原文回流覆盖——静默的数据完整性缺陷，用户纠正被系统性冲掉。
3. **F6.1–F6.4**：现实一致性只有问卷期静态画像预设，运行时零机制；场景判定纯关键词，异地邀约/时空错位族事故无任何拦截。
