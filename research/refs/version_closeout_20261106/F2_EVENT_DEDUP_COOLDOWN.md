# F2 — 事件去重丢合法重复消息 + 冷却只写不拦（外部审计第三轮 P1×2）

- 日期：2026-11-06
- 产品仓：D:/LoveBrain，分支 `closeout-20261106`，基线 HEAD=`8cafeec`（worktree `closeout-f2-tmp`→D:/wt-f2，验证后已回写主仓并清理，未 commit）
- 编译验证：`:app:compileDebugKotlin` 通过；12 个目标测试类全绿（Champion 包 5 类 62 例 + selector/cooldown/meeting/prompt/VM 边界 7 类 37 例，0 失败）。

## F2-1 影子事件库丢合法重复消息

### 问题
`ChampionEventStore` 去重身份是 `"$role|$text|$ts"`，而提交侧时间戳只格式化到分钟。对方同一分钟连发两次"好"是两条真实消息，第二条命中同一键被合并，事件 ID 复用、永不入库——违反"真实对话顺序不能丢、不能合并真实事实"。

### 修法
去重身份改为**每条消息的稳定 uid**（`msgUid`），产品侧取 `ChatMessage.id`（输入时分配的 UUID，同一 pending 轮重试稳定携带同一 id）：

- `ChampionEvent` 增加 `msgUid: String = ""` 字段，随事件落盘（重启后去重仍命中）。
- `append` 输入从 `Triple<role,text,ts>` 改为 `ChampionMessage(role, text, ts, msgUid)`；**msgUid 空白直接在入口被拒**（空身份会把多条消息误合成一条）。
- 去重索引按 msgUid 建：同一 pending 轮重提交 → 同 msgUid 命中 → 复用既有事件（uid/turn 取首次落库值）；同分钟同内容的不同消息 → msgUid 不同 → 各成一条事件，永不误合。旧的 `distinctBy role|text|ts`（正是合并同分钟重复消息的那颗）废弃。
- 存量兼容：沿用 R2-2 结论（模式默认 OFF、shadow 未上线、无存量用户数据）——换键不留迁移；旧数据（msgUid 缺省空串）按"永不命中去重"对待，不回填。
- pending uid 对齐口径（`shadowTurn`）：pending uid = maxUid+1+序号，对**全新窗口**与正式入库一一对应的承诺不变；窗口含已提交消息时正式入库按 msgUid 复用旧 uid，注释已如实登记该偏离。

### 改动文件（含 VM 最小改动区段）
| 文件 | 区段 |
|---|---|
| `app/src/main/java/com/lovebrain/app/domain/champion/ChampionEvent.kt` | ChampionEvent 加 msgUid 字段；新增 ChampionMessage；append 重写为 msgUid 去重 |
| `app/src/main/java/com/lovebrain/app/domain/champion/ChampionEngine.kt` | ①commitConfirmedTurn 签名 `Triple`→`ChampionMessage` 透传 + KDoc；②shadowTurn 对齐注释；③custody 登记：`PORT_VERSION`→`F2-DEDUP-20261106`，CHAMPION_SOURCE_SHA256["ChampionEvent.kt"] 更新为 `6c0b1d255f6874a5fb27b4870579ebb0b63b89c74769db5cce1c29a64c272e07`（LF 归一实测） |
| `app/src/main/java/com/lovebrain/app/viewmodel/LoveBrainViewModel.kt` | **最小改动区段**：nextRound 提交点 `championMsgs` 映射（约 1500–1528 行）——Triple→ChampionMessage 并传 `msgUid = m.id`；提交点注释同步。不碰 VM 其它任何区段 |
| 测试 | `ChampionEventStoreTest`（11 例：新增"同分钟同内容两条消息=两条事件""同 msgUid 改文本仍同一事件""空 msgUid 拒收""去重身份重启后仍命中"）、`ChampionEngineTest`（11 例：新增引擎级同分钟两条"好"=两条事件主案情；既有提交点改传 msgUid） |

## F2-2 冷却只写不拦

### 问题
`OngoingContextSelector` 冷却条件含 `!signals.isRelevant`，而后续 ACTIVE/DORMANT 筛选本就跳过不相关项——相关项（持续意图/备注命中）每轮绕开冷却注入，不相关项本来就不注入，冷却形同虚设。

### 修法（产品语义，已写进代码注释与本文件）
**冷却统一生效**：同一事项在无新真实聊天证据时不重复注入，与是否"相关"无关。冷却到期（距 `lastInjectedTurn ≥ COOLDOWN_TURNS=3`）或出现新证据（HER/ME 真实消息关键词命中）后恢复注入；后者是冷却期内唯一的提前放行通道。**用户显式设置的持续意图不构成绕过冷却的理由**——意图表达"保持在意"，不是"每轮必提"；冷却只防连发、到期自动恢复，不丢功能。不做"每三轮禁一次"之类的新发明。防御性补充：`lastInjectedTurn < 0`（只推进过证据、从未注入）不算冷却中。

改动仅 `OngoingContextSelector.kt` 冷却判据一处（`!signals.isRelevant` → `!signals.realMessageEvidence` + `lastInjectedTurn >= 0` 守卫），冷却存储 `OngoingCooldownStore` 未动（暂存→提交点消费两段式保持）。

### 测试（OngoingContextSelectorTest，13→17 例）
- `intent-relevant item within cooldown is still suppressed`——相关+冷却中→不注入（旧形状下此格 eligible=1，当场红）。
- `cooldown expiry restores injection for a relevant item`——冷却到期→注入。
- `new real message evidence bypasses cooldown immediately`——新证据→立即注入，且证据/冷却两条时间线经审计 #19 暂存通道推进。
- `evidence-only entry never counts as cooldown`——从未注入不算冷却中。
- 测试隔离：setup 增加 `OngoingCooldownStore.clearPendingForTest()`。

### 连带测试校准（语义变更的必然后果，非绕过）
`PromptBuilderOngoingDirectiveIntentTest.note text stays out of the chat fence...`（R3 批）原在"冷却窗口内靠备注注入"的场景上验证"备注不伪造证据"；新语义下该场景应被拦（这正是 F2-2 要求的行为）。fixture 增加 `turnCount` 参数，把该格场景移到冷却已到期（turnCount=4，4-1=3≥3），格子原判据（备注只出现在军师段一处、lastInjectedTurn/lastEvidenceTurn 原样保留、pending 只推进 injectedNames）逐条保留。fixture 首格 `frozen advisor note and effective intent make the item injectable` 无冷却记录，不受影响。

### 不碰清单确认
TopicSwitchGate/TopicRecorder（F1 轨）、ComposerStore/BatchImport（F3 轨）零改动；`LoveBrainViewModel` 仅上述一处映射区段（审计预授权）。

## 验证记录
- worktree：`git worktree add D:/wt-f2 -b closeout-f2-tmp 8cafeec`，复制 local.properties，`JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.3.9-hotspot`。
- `:app:compileDebugKotlin` 通过；`:app:testDebugUnitTest`（--tests 过滤 12 类）BUILD SUCCESSFUL，0 失败（ChampionEventStoreTest 11 / ChampionEngineTest 11 / OngoingContextSelectorTest 17 / OngoingCooldownCommitBoundaryTest 5 / MeetingPollutionRegressionTest 5 / PromptBuilderOngoingDirectiveIntentTest 5 / LoveBrainViewModelChampionCommitBoundaryTest 3 等）。
- 8 个改动文件已复制回主仓；worktree 与临时分支已删除；未 commit（纪律：不 commit）。
