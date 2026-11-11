# BASELINE_FIDELITY — round_0_baseline（B）对 Android 原版（A）保真度审计
审计日期 2026-10-17。A=D:/LoveBrain（Kotlin）；B=candidates/round_0_baseline（无 mechanism 目录 → 用 arena\lb_mechanism.py 默认机制 + lb_assembly.py 装配）。B 的 assets/ 与 A 的 assets/engine 逐字节一致，唯一例外 proactive.md（A 已改证据门控措辞，B 冻结在 2026-10-07 旧版；但 B 回复链不读 proactive.md，差异休眠）。

## 一、能力差异表

| # | 能力 | A（原版）实现位置 | B（基线）实现位置 | 差异说明 | 保真度 |
|---|------|------------------|------------------|----------|--------|
| ① | 持续意图 | Models.kt isIntentEffective：enabled+text 非空+status==ACTIVE；expiry/expiryDate 已退役为兼容字段（IntentController.kt:22-29 写侧统一 ACTIVE 无期限） | lb_assembly.py:224-240 build_intent_block：enabled+text，另抑制 status∈{COMPLETED,EXPIRED,PAUSED} 及 expiry<now；兼容 str 形态视同 enabled | B 保留 A 已退役的 expiry/status 抑制语义；dict 带 expiry 的题 B 会静默丢弃，A 反而注入。str 形态是 B 侧题库适配，A 无对应物 | 部分复刻（B 多出已退役判定） |
| ② | 进攻开关 | aggressive.md 注入 user 知识段"记忆之后、此刻之前"，system 普通进攻字节一致（PromptKnowledgeSection.kt:86-91；GenerationEngine.kt:154） | lb_assembly.py:179-180,367 同位置注入，aggressive.md 资产相同 | 位置/字节一致；params 默认 false 仅是默认值 | 完整复刻 |
| ③ | 军师备注四类处理 | IntentIdeaBlock.kt:44-54：背景/限制/目的/提醒四类，逐字全量注入，不改方案数量与格式 | lb_assembly.py:243-251 逐字同文 | 无差异 | 完整复刻 |
| ④ | 知识库结构 | understand/{me,her,warmth,style}.md 画像 + moment/{scene,recent,plan}.md + memory/{lessons,raw_topic,raw_scene}.md 暂存 + stage.md 阶段 + schema/ 模板（PromptBuilder.kt:302-314；assets/schema/） | 扁平 kb dict：me/her/warmth/style/lessons/stage/topic/topic_age_hours/scene_chain/recent/ongoing_plan（lb_assembly.py:159-214） | B 无 raw_* 暂存层、无 schema 模板、无多库；scene_chain/recent 退化成纯文本字段 | 部分复刻 |
| ⑤ | 记忆写入 | nextRound→TopicRecorder.record：WAL 事务六投影（话题归档/标签/recent/scene/plan/计数），崩溃恢复（LoveBrainViewModel.kt:1462；TopicRecorder.kt:188-247）；scene_facts 带 sourceIds 逐条校验（SceneChainStore.kt:56-63）；topic_status same/drift/new 判定 | lb_mechanism.py:42-81 update_state v0：topic_label→覆盖 topic+age 归零；scene_facts 仅取 text 拼"- [clock] f1；f2"；ongoing 同 item 覆盖；lessons 不写；无 recent/轮次/状态判定/来源校验/幂等 | B 自述"确定性近似"；丢 sourceIds、丢 topic_status（一律当 new 覆盖）、不写 recent 轮次与点赞偏好 | 部分复刻 |
| ⑥ | 检索与裁剪 | PromptBudget.kt:21-84 三步裁（旧记忆尾→旧 recent→chat 头部整行），TOTAL_BUDGET=9000、REPLY_MAX_MESSAGES=60、lessons lastH1Blocks=3 | lb_assembly.py:299-343 逐行复刻（kotlin_len UTF-16、围栏不切半、remaining 忠实冗余） | 装配侧声明与 PromptByteFreezeBaselineTest 对拍 | 完整复刻 |
| ⑦ | 记忆纠错/冲突 | MemoryCorrectionPolicy.kt（WRONG/FINISHED/MUTED/WRONG_PERSON 四动作+本轮瞬时 mute）、MemoryRefPolicy 注入前过滤（PromptBuilder.kt:225-262）、corrections revision 防迟到覆盖（KnowledgeTriggerCoordinator.kt:81；KnowledgeRevisionCheckedWriteService.kt:36-54） | 无任何机制（scorer 侧 C11 stale_fact_must_not 只测不修） | 纠错类考题 B 完全裸奔 | 未复刻 |
| ⑧ | ongoing 维护 | OngoingContextSelector.kt：默认拒绝注入，三类相关信号（真实消息证据/备注/意图）才放行，DORMANT 休眠、cooldown、eventDate 仅从事项名提取；OngoingPlanStore.kt:34-44：稳定 itemId、幂等、防旧任务复活、状态链截断 | 注入端仅过滤空行与 FINISHED/CANCELLED（lb_assembly.py:206-209）；写入端同 item 覆盖（lb_mechanism.py:74-78） | 注入格式一致但语义大幅弱化：无 gating、无防复活（模型重提旧事项即可复活）、无休眠 | 部分复刻（注入形似，维护语义缺失） |
| ⑨ | 时间/时效 | Clock 端口注入固定时间戳（PromptBuilder.kt:338-341）；topic_age >4/>6 时效标注（PromptKnowledgeSection.kt:95-109）；scene 链加龄与 2h TTL（SceneChainInjection；AppConfig.kt:61-62） | lb_assembly.py:66-109 transform 复刻、183-193 时效标注、287-290 时间戳段；时钟由题面 clock 固定 | 读侧一致；A 的 SCENE_CHAIN_MAX_HOURS=2 写侧归档 B 无对应（B 无 TTL，链只增不减） | 部分复刻（读侧完整，写侧 TTL 缺失） |
| ⑩ | 输出契约 | 四风格 schemes+四方向 directions+analysis{topic_status,topic_label,scene_facts(sourceIds),ongoing}（Models.kt:216-282）；解析完整性检查与 directions 降级（DeepSeekRepository.kt:309-341） | scorer LEGACY_CONTRACT 四风格+4方向+min_nonempty=1（lb_scorer.py:26-32）；analysis 由 mechanism 消费 | B 丢 source_ids（lb_mechanism.py:64-65 只取 text）、不处理 topic_status；八项完整性检查 A 在解析层做，B 只在计分层 | 部分复刻 |
| ⑪ | system 结构/缓存锚 | core+naturalness+redline+format+安全声明，全静态缓存锚（PromptBuilder.kt:80-94） | lb_assembly.py:145-156 同顺序同分隔符，安全声明逐字同文；四份资产字节一致 | 无差异 | 完整复刻 |
| ⑫ | A 多出、B 未覆盖 | ①三流独立 system：谈心 counseling/润色 polish/主动开场 proactive（PromptBuilder.kt:115-122,351-446）；②后台三引擎：向量重估/经验提取/画像 reflect 周期触发（KnowledgeTriggerCoordinator.kt:74-100；AppConfig.kt:63-64,84-87）；③点赞偏好写入 recent（LoveBrainViewModel.kt:1414-1429）；④仅看本轮开关（PromptBuilder.kt:268-286）；⑤MemoryRef 引用清单与纠正中心 UI；⑥WAL RoundCommitJournal、多库切换/归档/备份；⑦改写链 RewriteStore | ⑫中 B 仅有场景推断 infer_scene 复刻（lb_assembly.py:114-140）；其余全无 | episode 多轮记忆演化、谈心/主动开场/画像类考题 B 无行为来源 | 未复刻 |

## 二、结论：对拍公平性

B 偏弱（对拍会低估基线）：
1. 记忆写入丢 sourceIds/topic_status（lb_mechanism.py:59-69）：多轮 episode 中 B 的场景链会把模型重述当新事实、无法区分话题切换，直接恶化 C11 旧事重提/记忆污染探针与场景链演化类考题。
2. ongoing 无防复活与 gating（lb_mechanism.py:74-78）：旧事项被随口复述即复活并注入，长期追踪类考题 B 更易失分。
3. 无纠错机制：任何依赖"用户纠正后记忆生效"的考题对 B 不可考，若题库含此类题则对拍直接不公平。
4. 不写 recent 轮次与点赞：多轮 episode 的"# 最近对话"段在 B 中为空，风格一致性/连续性考题 B 偏弱。

B 偏强（对拍会高估基线）：
5. 意图 expiry/status 抑制（lb_assembly.py:235-239）：对"过期意图该不该继续生效"类考题，B 按旧语义静默不注入，反而可能更"守规矩"；A 按新语义照常注入。若 gold 按旧语义出题，B 偏强；按新语义出题，B 偏弱——同一能力两个方向都可翻。
6. B 接受 str 意图与 may_decline 等契约扩展（lb_assembly.py:228-229；lb_scorer.py:13-17），这些是题库/评分侧适配，A 无对应——不是保真度问题，但对拍解读时须区分"机制差异"与"接口糖"。

建议：episode 模式多轮对拍前，至少把 update_state 升级到带 sourceIds 校验、topic_status 判定与 ongoing 防复活；单题模式（state=None）下 B 装配侧与 A 字节对齐度足够高（①③⑥⑪ 已达完整复刻），可直接对拍。proactive.md 版本差在 B 回复链休眠，仅当考题显式考 hold_back 证据门控措辞时才构成偏差。
