# T4 Kotlin 移植规格书 — arena 已验证双冠军机制 → LoveBrain 产品

日期：2026-11-06 ｜ 性质：只读研究 + 设计文档，不改任何代码（产品侧改动仅在本规格获批后按 W15-06 节奏排期）
被移植对象（arena 真实对照保留的双冠军）：
- `window_expand_b_norecap_uc`（>24 轮域冠军：3600 CU 事件历史 + 未定事项注入段）
- `window_expand_b_norecap_guard`（≤24 轮域冠军：确定性输出契约守卫）
- 合并版 `window_expand_b_norecap_uc_guard`（W19-C1，= uc 全部装配 + guard 唯一增量）——**以合并版为唯一移植蓝本**，两域差异只体现在 guard 可独立开关。
上游依据：`candidates/window_expand_b_norecap_uc_guard/{mechanism/mechanism.py, contract_guard.py, MECHANISM.md, params.json}`、`candidates/window_expand_b_norecap_uc/mechanism/uncertain_rules.py`、`integration_preview/KOTLIN_INTEGRATION_MAP.md`、`integration_preview/PRODUCT_WIRING_INVENTORY_V1.md`、`W15-06_INTEGRATION_DESIGN_20261031.md`。
产品落点核对（只读，D:/LoveBrain v1.4.2）：`domain/PromptBuilder.kt`、`domain/PromptBudget.kt`、`domain/TopicRecorder.kt`、`domain/RecentRoundStore.kt`、`domain/GenerationEngine.kt`、`data/DeepSeekRepository.kt`、`domain/prompt/ChatTranscriptBlock.kt`、`AppConfig.kt`。

---

## 1. 机制移植映射（Python → Kotlin）

### 1.0 新增包与总览

全部新代码放 `com.lovebrain.app.domain.champion`（不改动现有 domain 类的签名，见 §2），五个新类 + 一个数据模型：

| Python 源 | Kotlin 落点 | 职责 |
|---|---|---|
| `mechanism.py::_uid_for/_key_of/events` | `champion/ChampionEventStore.kt` | 事件有序真实消息存储 |
| `mechanism.py::_budget_history/_line` | `champion/EventHistoryAssembler.kt` | UTF-16 3600 CU 整条历史预算 |
| `uncertain_rules.py`（全文件） | `champion/UncertainBlockDetector.kt` | 未定/待确认事项检测与注入块（纯函数） |
| `mechanism.py::run_turn 的 parts 拼装` | `champion/ChampionPromptAssembler.kt` | 冠军 prompt 装配 |
| `contract_guard.py` + `_vendor/output_repair.py` + `_vendor/normalize_reply_contract.py` | `champion/ContractGuard.kt`（内聚为一个文件，保留三层规则注释） | 零模型调用 JSON/槽位修复 + 审计 |

### 1.1 ChampionEventStore（对应 `events`）

```kotlin
data class ChampionEvent(
    val uid: Long,        // 稳定整型 id（uid_seq 递增）
    val role: String,     // "HER" | "ME"（product ChatMessage.Role.HER/ME 映射）
    val text: String,
    val ts: String,       // yyyy-MM-dd HH:mm，来自 Clock 端口
    val turn: Int         // 仅供未定段「（T<turn>）」标注，不进历史行文本
)
class ChampionEventStore(private val storage: ChampionEventStorage) {
    suspend fun append(role: String, text: String, ts: String, turn: Int): Long
    // 去重键 key = "$role|$text|$ts"（照 _key_of），同键复用既有 uid，不重复入库
    suspend fun events(): List<ChampionEvent>          // 按 uid 升序 = 时间序
}
interface ChampionEventStorage { /* 落盘：每 contactId+story 一个 JSON Lines，见 §2.2 */ }
```

移植不变量（逐条对照 Python）：
- **回复不入库**：模型草稿、候选、点赞偏好永不 append；只有用户实际发送（或对方真实消息）才入库。产品侧 append 调用点 = 用户确认发送处（`LoveBrainViewModel.kt:1593` `actualSent.record` 同一判定），不挂在 `GenerationEngine` 流上。
- `uid_by_key` 去重语义保留：同 (role,text,ts) 复用同一 uid，跨轮不重复（用于未定段 seq 与历史行 `m_src[uid]` 稳定）。
- guard 不新增持久状态；审计只落日志文件（§2.3）。

### 1.2 EventHistoryAssembler（对应 `_budget_history`，delta ①）

```kotlin
object EventHistoryAssembler {
    const val MAX_EVENT_HISTORY_CU = 3600
    fun budgetHistory(events: List<ChampionEvent>, budget: Int = MAX_EVENT_HISTORY_CU):
        HistoryBudget   // data class HistoryBudget(body, usedCu, dropped: List<ChampionEvent>)
}
```
逐字规则：行格式 `"m_src[%d] | %s | %s | %s".format(uid, ts, if(role=="HER")"PARTNER" else "USER", text)`；从最新往旧整条累加（每条 `utf16Len(line)+1`），越界即停；连一条都放不下 → 整段省略并输出占位句 `（最近一条旧史单独超出历史预算，本段省略；当前输入在下方完整保留）`。**不切代理对、不压缩、无 recap、无相关性排序。**
CU 计量：Kotlin `String.length` 天然 = UTF-16 码元数，与 Python `rc.utf16_len`（`encode('utf-16-le')//2`）等价；不必另写计数器，但单测须含 emoji/代理对样例钉死。

### 1.3 UncertainBlockDetector（对应 `uncertain_rules.py`，W16 增量）

```kotlin
object UncertainBlockDetector {
    const val UNCERTAIN_BUDGET_CU = 120; const val MAX_ITEMS = 3
    fun buildBlock(prior: List<ChampionEvent>, cur: List<ChampionEvent>, curTurn: Int): UncertainResult
    // data class UncertainResult(block: String, items: List<UncertainItem>, cu: Int)
}
```
逐字移植全部 9 条正则（hedge/world/anchor/third/confirm/quote/ask/answer/decision）、clause 切分、bigram `_overlap`(≥0.34 关闭判定) 与 `_overlap_cont`(≥0.50 场上判定)、三条结构信号 A/B/C、R3 相关性过滤、R4 引号转述防误注入、新→旧去重取 3 条、逐字引用不改写。标题/脚注文案逐字节保留：
`# 【未定/待确认事项：尚未核实，不得当作已发生】` + 口径脚注。**无未定项返回空串——prompt 必须与无该机制的父本逐字节相同**（对拍验收项，见 §5）。
风险登记：Kotlin `Regex` 与 Python `re` 对同一字符类行为基本一致，但 `[零一二两三四五六七八九十百千万]+`、`「」“”` 等必须在 JVM 与 CPython 下跑同一组正反例夹具对拍（§5）。

### 1.4 ChampionPromptAssembler（对应 `run_turn` 的 parts 拼装）

```kotlin
class ChampionPromptAssembler(private val clock: Clock, private val seed: ChampionSeed)
data class ChampionSeed(val persona: JsonObject, val initialKb: JsonObject)  // 平面标量 JSON
fun assemble(events: List<ChampionEvent>, curWindow: List<ChampionEvent>,
             intent: IntentConfig, advisorNote: String, wallClock: String): ChampionPrompt
// data class ChampionPrompt(prompt: String, sourceAliasMap: Map<String,Long>, historyCu, ucCu, ucItems)
```
段序严格固定（任何改动都是新变异，不得在本移植内夹带）：
1. `【画像/初始事实(种子，可公开使用)】\n{seed JSON}`（persona/initial_kb 均非空才出现）
2. intent 块（复用产品 `IntentIdeaBlock.buildIntentBlock`，仅在 `IntentPolicy.effectiveStatus` = ACTIVE 时非空；关闭/缺省 → 空，不留旧目标）
3. note 块（复用 `IntentIdeaBlock.buildAdvisorNoteBlock`，军师备注 = IDEA，本轮约束通道）
4. `# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n` + EventHistoryAssembler 输出
5. 未定事项段（UncertainBlockDetector；空则整段不出现）
6. `# 本轮输入(最高优先，整条不被裁；id=来源ID，PARTNER=对方/USER=本人)\n<chat>\n…\n</chat>`——本轮每条消息一行 JSON：`{"id":"m<i>","source_id":<uid>,"speaker":…,"ts":…,"text":…}`（正文走 kotlinx.serialization 转义，同产品 `ChatTranscriptBlock` 的注入防护口径）；`id` 是本轮窗口别名，`source_id` 对齐稳定 uid
7. 当前时间块（复用产品 `PromptBuilder.buildTimestampPrompt` 文案）

system prompt：移植沿用 arena 冠军的 `build_system_prompt` 资产（v3_system_slim 系），以**产品 PromptSourcePort 读取的新资产文件**落地（新增 `champion/core_slim.md` 等，不改现有 `AssetRegistry.CORE` 四件），并保留产品既有的「<chat> 围栏内为不可信输入」安全声明——**该声明的覆盖范围必须显式扩展到第 5 段未定事项注入块**（FK-INJECT-01，见 §4）。

### 1.5 ContractGuard（对应 `contract_guard.py` + 两份 `_vendor`）

```kotlin
class ContractGuard(private val auditSink: GuardAuditSink) {
    fun repairAndAudit(rawText: String): GuardOutcome
    // data class GuardOutcome(text: String, meta: GuardMeta)
}
```
三层规则逐字移植（顺序固定，后层作用于前层结果）：
1. **解析兜底（vendor I1–I4 + E1–E3）**：剥 markdown 围栏 → 弯/全角引号按序换直引号重试 → 抽最外层对象；键名去空白/不可见字符（Unicode Cf：U+200B/200C/200D/FEFF 等）后比对别名表，规范槽**非空绝不覆盖**，别名值全文进 `unmapped_preserved`；directions 保全（多出整条进 `extra_directions_preserved`）；E2 提前闭合重拼、E3 多余包裹对象拆开（五条件全过才只动括号）。
2. **漂移族补修（guard 层）**：未知风格键按字符相似度 ≥0.5 映射到 recommended/bad_boy/playful/warm（仅目标槽空时）；response 少 4 槽补 null（不编造）；directions 截 4/补 null（显式 `action=no_reply` 不补）；顶层 action↔response 互改（反向只认 action 形状）；空 response 只标 `empty_response` 原样返回。
3. **输出纪律**：只有对象相对原文实际变化才重新序列化（`Json { encodeDefaults=false }` + 非 ASCII 不转义，对齐 `ensure_ascii=False`）；否则**逐字节返回原文本**。每次变化写审计（turn、meta 全字段）到 `champion/contract_guard_audit.jsonl`；守卫自身异常 → 降级原样回复并记 `guard_exception`。**零模型调用**。

移植风险点（须单测钉住）：Python `difflib.SequenceMatcher.ratio()` 无 JDK 等价物——须在 Kotlin 侧移植同算法（最长匹配块递归，2.0*M/T）或登记偏差改用明确相似度并重跑漂移族夹具；**未对拍前不得启用 fuzzy_map 规则**（可整体降级为 `unmapped_style_removed` 保守路径）。

---

## 2. 与产品现有机制的关系

### 2.1 3600 CU 事件预算 vs 现有 9000 字符 PromptBudget（替换关系，非包裹）

- 现状：`PromptBudget.byBlocks`（`PromptBudget.kt:21`）在 `AppConfig.TOTAL_BUDGET=9000` 字符下按「知识段→recent→scene→chat 头部」优先级裁剪，chat 行还有 `REPLY_MAX_MESSAGES=60` 掐尾与 `CHAT_LINE_ESTIMATE=60` 估宽。
- 冠军机制**不走这条裁剪链**：历史段有自己的 3600 CU 整条预算（越界即停、不切半条），当前窗口整条永不裁。二者是**两套互斥的上下文组织哲学**（确定性投影裁剪 vs 事件有序整条预算），不是参数替换。
- 接线：S2/S3 生效轮，`GenerationEngine.replyStream` 的装配分支由 flag 路由到 `ChampionPromptAssembler`（替换 `buildReplyUserPromptWithRefs`/`OnlyThisRound` 两条 user 侧路径）；`PromptBudget` 与原 `PromptBuilder` 原样保留给 flag-off 轮，不删不改（回退即零迁移）。`byBlocks` 对新链路**一次都不调用**；若最终 prompt 仍超模型上下文，那是 3600 CU 预算自己的责任边界，不回退到 9000 字符兜底。

### 2.2 events 存储与 TopicRecorder 六文件投影的衔接（并存隔离，不合并）

- 现状：`TopicRecorder.record`（`TopicRecorder.kt:101`）经 `RoundCommitJournal` WAL 把一轮提交到 moment/recent.md、scene.md、plan.md、raw_topic 等六个投影；recent 溢出写 `memory/raw_chat.md`（`RecentRoundStore.kt:97`）。
- 衔接原则（承 KOTLIN_INTEGRATION_MAP「新旧存储互不覆盖」）：
  1. `ChampionEventStore` 是**新增的第 7 个写入面**，目录键带 engine 维度（`champion_events/<contactId>__<story>.jsonl`），与 kb 投影物理分离；写入口挂在**用户确认发送/收到对方消息**的同一事务点（`actualSent.record` 旁，`LoveBrainViewModel.kt:1593`），与 `TopicRecorder.record`（`:1462`）并列调用，各自幂等（events 用 `role|text|ts` 键去重，WAL 用 roundId，互不依赖）。
  2. 冠军 prompt **不读** recent.md/raw_chat.md/scene.md/plan.md/corrections——历史唯一来源是 events。TopicRecorder 继续为旧引擎、知识库画像（`understand/*.md`，作为 seed 的 initial_kb 快照来源）、经验提取运行；flag-off 轮行为零变化。
  3. 回退（flag→off）时 events 文件保留不清；重开时按 `role|text|ts` 续用，不回填、不迁移旧投影数据成 events（差量见 §4）。
- sourceAliasMap：冠军 prompt 的 `m<i>`→uid 映射与产品 `ReplySourceAliasMap` 事件同通道上报（`GenerationEngine.kt:204`），供审计对账；TopicRecorder 的 scene 来源校验在新引擎轮**不消费**该映射（scene 投影照旧走旧链路自己的映射）。

### 2.3 guard 插入点：GenerationEngine 落盘/渲染之前、parseReplyResponse 之前

- 精确位置：`GenerationEngine.replyStream`（`GenerationEngine.kt:136`）在 `fullText` 收集完成后、`deepSeekRepo.parseReplyResponse(fullText)`（`:302`）**之前**插入：
  `fullText = guard.repairAndAudit(fullText).text`（flag 仅在 S2/S3 或 S1-shadow 标记轮启用；off 轮零调用）。
- 理由：守卫修复的是**原始返回文本的 JSON 契约**（围栏/键名/槽位），必须在产品解析器之前跑，才能让 `Parse` 失败分支（`:307`）少吞掉正文完好的轮次；同时原始文本已被 `DeepSeekRepository` 流式落盘/记账（usageTracker + PERF 日志），满足「原文在守卫前已留痕」的 arena 不变量（对应 gateway calls.json 先写）。
- 审计：`GuardMeta`（changed/rules/unmapped_preserved/extra_directions_preserved/…）随 `ReplyCompleted` 之外**独立落盘**到 `champion/contract_guard_audit.jsonl`（含 requestId/turn），不进 UI；`guard_changed/guard_rules` 并入 PERF 日志行。
- 渲染与写库边界：guard 修复后的文本照常 parse→渲染候选；用户未发送的候选仍**不进** events（§2.1.1）。

---

## 3. 特征开关 S1/S2/S3（W15-06 §2 三阶段 + §3 回退的产品侧实现）

### 3.1 开关放哪

- 产品现无引擎级开关（PRODUCT_WIRING_INVENTORY §2.5 核查），须新增。落点：
  - 持久化：`SettingsStorePort`（实装 SecurePrefs）新增键 `champion_engine_mode`（`OFF | SHADOW | SINGLE_STYLE | FULL`）+ `champion_engine_scope`（contactId+story 粒度白名单，空 = 全体）。按轮冻结进生成输入（同 `GenerationInput` 冻结模式），轮中不改。
  - 路由：`di/AppModule.kt` 的 `GenerationEngine(get(), get())` 增加第三个依赖 `ChampionEngine`（由 `ChampionPromptAssembler + ChampionEventStore + ContractGuard + 同一 AiGateway` 组成）；`replyStream` 开头按冻结 flag 分支。Koin 绑定不替换既有 `AiGateway`/`PromptBuilder` 单例。
  - 门禁：非 FULL 模式仅在 `BuildConfig.BUILD_TYPE != "release"` 或显式白名单下允许（复用 §2.5 建议的 BUILD_TYPE 判据），开关状态每次轮转写审计。

### 3.2 S1 shadow：只记不生效的实现

- 原链路完整照走（原 PromptBuilder + 原记忆管线 + 原渲染），用户看到的一切不变。
- 在 `replyStream` 发出原链路请求**之后**（成功或失败都算），fire-and-forget 追加一次 `ChampionEngine` 生成：用同一批输入装配冠军 prompt（events 正常 append——真实消息本就要入库；shadow 回复**不入库**）→ 调 guard → 结果与 guardMeta、prompt_cu/history_cu/uc_cu、usage、两端耗时整体写 `champion/shadow_log.jsonl`。**不 emit 任何 ReplyEvent、不进 UI、不触发 TopicRecorder 的 shadow 侧写入**（analysis/facts 消费边界照 KOTLIN_INTEGRATION_MAP：shadow 轮不触发旧 summary 更新或清库）。
- 失败语义：shadow 生成失败只记日志，绝不影响原链路结果；超时用同一 `generateTimeoutMs`。
- S2 SINGLE_STYLE：主链路换 ChampionEngine，仅取 `response.recommended` 替换产出，directions 只进埋点；S3 FULL：四风格+directions 全接入。

### 3.3 回退触发器（继承 W15-06 §3 + 本移植新增）

| 触发条件（任一命中） | 动作 |
|---|---|
| 保护维（污染/记忆正确性）judge 对比 ≤−0.5 | flag 写回 OFF |
| severe 错误率相对原链路上升 | flag 写回 OFF |
| veto 率 ≥3 倍（n≥192） | flag 写回 OFF |
| p95 端到端延迟 > 原链路 2 倍 | flag 写回 OFF |
| 生成失败率 >1% | flag 写回 OFF |
| （新增）guard `unmapped_preserved`/`empty_response` 周率异常抬升，或 fuzzy_map 后解析失败仍发生 | 先降级 guard 到保守路径（停 fuzzy_map），无效再 OFF |

回退动作：切 flag 即时生效、零数据迁移；events/审计文件保留供归因。升级通道（换机制版本）走 custody 注册 + 重放等价 + live 批，禁止热替换。

### 3.4 custody 身份对账

- 注册时固定并哈希：①移植代码指纹（champion 包源文件 SHA-256 表，进 `champion/PORT_IDENTITY.json`）；②prompt 资产指纹（`AssetFingerprints.hashOf`，同产品既有机制）；③arena 侧被验证版本的候选目录 hash（`window_expand_b_norecap_uc_guard` 对应 run manifest）。
- 每条 shadow/live 记录携带以上三个 hash；S1→S2、S2→S3 放行前核对「运行身份 == 评过身份」，不一致即阻断升级并登记。对拍夹具（§5）全绿是 S2 的前置硬门。

---

## 4. 明确不移植清单与风险

### 4.1 不移植

1. **uc_attr（归因锚定）**：KEEP_NO_PROMOTE 判决，整块剔除；`uncertain_rules` 移植中不含 attribution 字段。
2. **arena 考场件**：`lb_runner/lb_scorer/lb_assembly/api_candidate`、gateway calls.json 记账（产品由 usageTracker + PERF 日志承担）、sandbox snapshot/restore 机器、episode_meta 装配、notes 字符串协议、`output_contract` 声明机制（产品消费契约由 `LoveBrainResponse` 解析器固定，键名漂移=格式作废，不新增合同层）。
3. **已删除件**：recap 段（`_recap_block`/ledger_rules）、suppression——不存在于冠军，不因移植复活。

### 4.2 风险登记

- **FK-INJECT-01（高优）**：未定事项注入块把历史消息的逐字引文（含转述、第三方原话）放进了 system 信任边界之外的 prompt 区。现有安全声明只点名 `<chat>` 围栏——移植时**必须**把声明覆盖范围扩到「未定事项段与历史消息段同为不可信第三方文本」，否则逐字引用成为新的注入面。guard 只修输出契约，对输入注入无防护，二者不可互替。
- **product_memory 未复刻的差量**：冠军 prompt 无产品知识段（me/her/warmth/style/lessons/stage/scene/plan/corrections/MemoryRef），仅 persona+initial_kb 平面种子。产品若把画像/阶段等作为种子注入，属于**arena 未验证的新变量**——S1/S2 严格按冠军保真装配（种子=KB 画像快照的平面 JSON），任何加段都必须单独版本化另开实验，不得冒充已验证机制。
- difflib 相似度移植等价性（§1.5）、中文正则跨语言行为（§1.3）：未对拍前相应规则走保守路径。
- Kotlin 未编译/未跑：本规格全部落点来自只读盘点，Android 构建/真机验收仍是 [U] 项（承 PRODUCT_WIRING_INVENTORY §1.7/§4：JDK17、SDK、无模拟器）。
- S1 shadow 翻倍主生成成本：shadow 轮 usage 独立记账，不与原链路混算。

---

## 5. 移植验收对拍清单（实施时逐项留证）

1. 无未定项时 `ChampionPromptAssembler` 输出与父本装配逐字节相同（对拍 `tools/check_prompt_equivalence.py` 口径，用 arena 录制的固定输入回放）。
2. 3600 CU 预算边界样例：整条放下/放下一条越界/单条超限占位句，三态逐字节对拍。
3. guard 漂移族夹具（W17 观察族 + E2/E3 实测样本 AK02_T01、N01_T05 形状）在 Kotlin 侧输出与 Python 逐字节一致；fuzzy_map 未对拍前关闭。
4. events 回放：同 (role,text,ts) 重发不重复入库；回复草稿入库请求被拒。
5. flag OFF 全回归：`PromptByteFreezeBaselineTest` 冻结表零漂移；TopicRecorder 六投影幂等不受新增第 7 写入面影响。
