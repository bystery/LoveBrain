# W1 — LoveBrain 冠军引擎 Kotlin 移植实施记录

日期：2026-11-07 ｜ 依据：`refs/version_closeout_20261106/T4_KOTLIN_PORT_SPEC.md`（严格按规格书实现）
移植蓝本：`candidates/window_expand_b_norecap_uc_guard`（W19-C1 合并版，唯一移植蓝本）
产品：D:/LoveBrain 分支 `closeout-20261106`（在隔离 worktree `closeout-w1-tmp` 内实施并验证，未 commit）

## 1. 类清单（新包 `com.lovebrain.app.domain.champion`）

| 文件 | 内容 | 移植来源 |
|---|---|---|
| `ChampionEvent.kt` | `ChampionEvent(uid,role,text,ts,turn)`、`ChampionEventState`、`ChampionEventStorage` 端口、`FileChampionEventStorage`（`champion_events/<contactId>__<story>.json` 原子替换写）、`ChampionEventStore`（`role|text|ts` 去重、uid 复用、**HER/ME 白名单外一律拒收**=回复草稿永不入库） | `mechanism.py::_uid_for/_key_of/events` |
| `EventHistoryAssembler.kt` | `MAX_EVENT_HISTORY_CU=3600`、`line()` 逐字格式、`budgetHistory()` 整条累加越界即停、单条超限占位句、dropped 对账；CU=Kotlin `String.length`（UTF-16 码元） | `mechanism.py::_budget_history/_line` |
| `UncertainBlockDetector.kt` | 9 条正则 + `_JUDGE_RE`、clause 切分、bigram `overlap`(0.34 关闭)/`overlap_cont`(0.50 场上)、信号 A/B/C、R3 相关性过滤、R4 引号转述防误注入、新→旧去重取 3、逐字引用、标题/脚注逐字节；无未定项返回空串（prompt 与父本逐字节相同）。不含 uc_attr（规格书 §4.1 剔除） | `uncertain_rules.py` 全文件 |
| `ChampionPromptAssembler.kt` | 7 段固定序装配 + `buildSystemPrompt()`（v3_system_slim 四件 + FK-INJECT-01 扩展安全声明）；intent/note 复用产品 `IntentIdeaBlock`，时间块经 Koin 注入 `PromptBuilder.buildTimestampPrompt` 真源（零复制）；本轮行 JSON 转义与守卫共用同一套 Python-`json.dumps(ensure_ascii=False)` 等价序列化 | `mechanism.py::run_turn` parts |
| `ContractGuard.kt` | 内聚 MiniJson（严格全量解析=raw_decode 双模式 + Python dumps 等价序列化）+ `SequenceMatcher.ratio` 同算法移植（含 autojunk）+ 三层规则（vendor I1–I4/E1–E3 → 漂移族补修 → 输出纪律）+ `GuardAuditSink`/`FileGuardAuditSink`（`champion/contract_guard_audit.jsonl`）。**零模型调用** | `contract_guard.py` + `_vendor/output_repair.py` + `_vendor/normalize_reply_contract.py` |
| `ChampionEngine.kt` | `ChampionMode`（OFF/SHADOW/SINGLE_STYLE/FULL，脏值 fail-closed 落 OFF）、facade（gateway+store+assembler+guard+settings+clock）、`shadowTurn`/`launchShadow`（fire-and-forget，失败只记日志）、`repairAndAudit`（S2/S3 主链路守卫入口）、shadow_log/PORT_IDENTITY 落盘 | W15-06 §2/§3 产品侧接线 |

新增资产：`assets/engine/champion/{core_slim,naturalness_check,redline,format}.md`（逐字节复制自 arena `v3_system_slim`，不改 `AssetRegistry.CORE` 四件；新路径已登记进 `AssetRegistry` 常量与 `ALL` 启动自检清单）。

## 2. 接线点

1. **SecurePrefs/SettingsStorePort**：新增 `champion_engine_mode`（默认 `"OFF"`，fail-closed：没写过/写错=OFF）与 `champion_engine_scope`（`<contactId>|<story>` 条目，`*`=全体，**空集=全体**——规格书 §3.1 原文口径）。合同测试补两格（默认值+往返），生产/内存 fake 两侧同过。
2. **di/AppModule.kt**：注册 `single<ChampionEventStorage>` / ChampionEventStore / ChampionPromptAssembler / ContractGuard / ChampionEngine 五颗单例（ChampionEventStorage 必须显式绑接口——`AppModuleGraphTest` 抓的就是这个）；时间块经 `get<PromptBuilder>().buildTimestampPrompt()` 复用；`GenerationEngine(get(), get(), getOrNull())` 第三依赖可空（旧测试图不注册=恒 OFF）。shadow 的 fire-and-forget 按 `SingleOwnerContractTest`（domain 层唯一 Job owner 合同）不接 CoroutineScope、不自己 launch：ChampionEngine 收 `shadowExecutor: (suspend () -> Unit) -> Unit` 执行口，di 侧用 `(androidApplication() as LoveBrainApp).applicationScope` 执行——scope 所有权与 ForegroundOperationCoordinator 同源，仍是图上可见的唯一 owner。
3. **GenerationEngine.replyStream**（仅 flag 非 OFF 时生效，OFF 轮逐字节零变化）：
   - SHADOW：原链路照走；主请求结束后 fire-and-forget 追加冠军生成（同批冻结输入：dialogue→HER/ME 入库（`role|text|ts` 去重）、intent/note/时间取自冻结 GenerationInput）→ 过 guard → 双侧输出+guardMeta+history/uc CU+usage+耗时+identity 写 `champion/shadow_log.jsonl`；**不 emit 任何 ReplyEvent、不进 UI、不触发 TopicRecorder**。
   - SINGLE_STYLE/FULL：`fullText` 收集完成后、`parseReplyResponse` **之前**插 `guard.repairAndAudit`（守卫修 JSON 契约必须在产品解析器前；原始文本已被流式落盘/记账，满足「原文在守卫前已留痕」）。装配层替换（ChampionPromptAssembler 接管 user prompt）**本批不接线**——属 S2。
4. **PORT_IDENTITY.json**：`champion/PORT_IDENTITY.json` 每轮 shadow 前幂等落盘，含 ①champion 包源文件 SHA-256 表（LF 归一，2026-11-06 实测登记于 `ChampionIdentity.CHAMPION_SOURCE_SHA256`：ChampionEvent/EventHistoryAssembler/UncertainBlockDetector/ChampionPromptAssembler/ContractGuard 五件；`ChampionEngine.kt` 不自嵌自 hash，其 SHA-256=`588a02eaff6d08d4b782d08843284609fefdbc94e4e1cd7a18e08bda710d0af6` 登记于此，升级时随 custody 重新登记）②资产指纹（`AssetFingerprints.hashOf`，四件 slim 资产）③arena 被验证候选机制 5 文件 SHA-256 表（`window_expand_b_norecap_uc_guard`，2026-11-06 实测）。每条 shadow_log 携带 ②③摘要（`identity` 字段）。

## 3. shadow_log.jsonl 格式（一行一轮）

`{ts, requestId, contact_id, story, mode:"SHADOW", turn, prompt_chars, history_cu, uc_cu, uc_items, uc_item_texts[], source_alias_map{m<i>→uid}, original_reply, shadow_reply, guard_changed, guard_meta{…全字段}, generate_ok, generate_error, prompt_tokens, completion_tokens, elapsed_ms, identity{port_version, champion_asset_hash, arena_candidate}}`。shadow 失败轮降级为含 `shadow_error` 的记录行。guard 审计另落 `contract_guard_audit.jsonl`（`{requestId, stage, turn|contact_id, meta{raw_chars, changed, rules[], unparseable, empty_response, no_reply_action, repair_loss[], style_key_map[], unmapped_preserved[], extra_directions_preserved[], response_slots_added[], directions_padded, directions_truncated, action_to_response, response_to_action, …}}`，仅 changed/unmapped/extra 非空时写——Python 同判据）。

## 4. 与规格书的偏差说明

1. **fuzzy_map 整体关闭**（规格书授权的保守路径）：`ContractGuard.FUZZY_MAP_ENABLED=false`。`SequenceMatcher.ratio` 已按同算法移植并有 CPython 实测值单测（0.75 / 10÷14 / 0 / 1.0 夹具），但按 §1.5 未做跨语言夹具批量对拍前不启用；启用前未知风格键一律 `unmapped_style_removed` 进审计。
2. **MiniJson 严格性**：Python `json.loads` 接受 NaN/Infinity；本实现按严格 JSON 处理（契约槽位不会出现，出现即按 unparseable 原样返回，行为保守）。非对象顶层 JSON（如数组）Python 走 `not_object` 留损，本侧并入 unparseable——对外行为一致（原样返回），审计字段形状略异。数字序列化保留原词法（Python 会把 `1e2` 规范成 `100.0`；契约数字槽罕见）。
3. **弯引号修复触发形状**：与 Python 逐字同构——单遍单对替换只对「同字符全宽引号（＂）」「孤立尾弯引号」等形状生效；混合 `“…”` 结构引号 Python 本身也修不动（extract_outer/None），已按原样移植并有测试钉住。
4. **I2 别名值的保全通道**：与 Python 逐字一致——精确别名（bad_boys 等）冲突值只进留损计数 `unmapped_style_keys`，不进 `meta.unmapped_preserved`（后者是未知键/E1 重复键路径的全文保全通道）。测试按此钉死。
5. **S1 事件入库时点**：arena 冠军在 turn 开始时把当前窗口真实消息入库；产品侧规格书 §1.1 更偏好挂在「用户确认发送」（`actualSent.record` 旁）。本批未动 `LoveBrainViewModel`（该文件正被并行轨修改，回写会覆盖他人改动），shadow 沿用 arena 等价的 turn-start 入库 + `role|text|ts` 幂等去重（用户确认发送后同键不重复）。**S2 前须把 ME 侧入库点迁到 actualSent.record 同一判定**（见 §6）。
6. **种子（seed）**：W1 置空（arena `episode_meta` 的平面 JSON 在产品侧无对应来源），种子段整段不出现=arena 空种子同形。规格书 §4.2：任何产品侧加段都属未验证新变量，S2 时以「KB 画像快照平面 JSON」单独版本化。
7. **contactId×story 语义**：产品当前无独立 contactId，W1 取 `kbContext.name` 同时充当 contactId 与 story（键 `champion_events/<kb>__<kb>.json`）；白名单条目格式已按 `<contactId>|<story>` 预留。

## 5. 验证结果（隔离 worktree `D:/wt-w1`，JDK 21，compileDebugKotlin + testDebugUnitTest）

- `:app:compileDebugKotlin` 通过；全量 `:app:testDebugUnitTest` 通过（含 PackageDependencyTest 架构棘轮——champion 包未引入 domain→data/ui 违例、PromptByteFreezeBaseline 冻结读数零漂移、SettingsStorePortContract 生产/fake 双侧）。
- 冠军专项 64+ 测试全绿：事件去重/顺序/回复拒收/落盘往返；3600 CU 三态（整条放下/越界停/单条超限占位句）+ emoji 代理对 CU 钉死；未定检测阈值边界（0.34/0.50、R3/R4、MAX_ITEMS=3、120 CU、空结果空串）；guard 三层（围栏、全宽引号、E1 零宽键、E2 AK02 形、E3 N01_T05 形、directions 截/补、action↔response、空 response 原样保留、未变化逐字节返回、审计 jsonl、difflib 实测值）；装配段序/JSON 行转义/种子形；shadow 全链路（入库不入回复、去重、失败降级、双 log 落盘、PORT_IDENTITY 三 hash）。
- 改动文件随后复制回 `D:/LoveBrain` 对应路径（仅本批文件，未触碰其他并行轨文件），worktree `closeout-w1-tmp` 移除、临时分支删除；未 commit。
- **收尾补验（2026-11-07，主仓 D:/LoveBrain）**：复制前逐文件核对——6 个共享文件（SecurePrefs/AppModule/AssetRegistry/GenerationEngine/SettingsStorePort/SettingsStorePortContractTest）主仓工作区未提交版 == HEAD == worktree 基线（git hash-object 逐一相等），无并行轨同名冲突，直接整文件复制、无手工合并。主仓整仓 `compileDebugKotlin + assembleDebug`（含输入框修复/批量导入/F3.3/F2.1 等并行轨未提交改动一起编译）BUILD SUCCESSFUL。
- 交接时登记的两个遗留问题在收尾轮核实均已被前任修复到位并经测试钉死：①Koin `ChampionEventStorage` 接口绑定已在 AppModule.kt 显式注册（`single<ChampionEventStorage>`）；②架构守卫 `SingleOwnerContractTest` 4/4 绿——champion 域代码不持 CoroutineScope、不 launch，shadow 经注入的 `shadowExecutor` 由 di 层 `applicationScope` 执行。

## 6. 待真机 / 待 S2 项

- **[U] 真机构建**：本批验证止于 JVM 编译+单测；APK 构建、启动自检（AssetRegistry.ALL 新增四件资产的存在性）、真机 shadow 跑通待真机环境。
- **[S2] ME 侧入库点迁移**：从 turn-start 迁到 `actualSent.record` 同一判定（LoveBrainViewModel 与并行轨冲突，W1 未动）。
- **[S2] 装配层替换**：SINGLE_STYLE/FULL 主链路 user prompt 换 `ChampionPromptAssembler`（现仅 guard 接入）；`OnlyThisRound` 分支的冠军语义、directions 消费。
- **[S2] 种子来源**：KB 画像快照 → 平面 JSON（persona/initial_kb），任何加段单独版本化另开实验。
- **[对拍] fuzzy_map 启用前置**：Python↔Kotlin 漂移族夹具批量对拍全绿后翻 `FUZZY_MAP_ENABLED`，并重跑 W17 观察族。
- **[对拍] 无未定项逐字节对拍**：`tools/check_prompt_equivalence.py` 口径、arena 录制输入回放（单测已钉结构等价，字节级回放待 S2 硬门）。
- **[运维] 回退触发器**（规格书 §3.3 表）未接自动监控——W1 为 shadow 只记不生效，人工切 flag 即时生效、零迁移；events/审计文件保留供归因。
- **[账] shadow 成本**：shadow 轮 usage 独立记账（shadow_log 内 prompt/completion tokens），未并入产品今日花费统计（避免用户可见成本翻倍）。

## 7. R2 批更新（2026-11-07，外部审计三项修复；worktree `closeout-r2-tmp`→D:/wt-r2，未 commit）

**显式登记：S2/S3 未接线。** `SINGLE_STYLE`/`FULL` 两档**没有可用实现**——主请求 prompt 至今是原
PromptBuilder，W1 只对原返回应用了 ContractGuard，不构成冠军主链路。可用档位只有 `OFF`/`SHADOW`：
①引擎读到 SINGLE_STYLE/FULL 一律按 OFF 处理并记日志（`ChampionEngine.mode()` 钳制）；
②设置侧 `SecurePrefs.championEngineMode` 读写两侧把 SINGLE_STYLE/FULL 收敛成 OFF 并记日志，
内存 fake 同形（合同测试已改为钉 OFF）；③`PORT_IDENTITY.json` 运行时新增 `wired_modes:["OFF","SHADOW"]`
与 `s2_s3_status:"NOT_WIRED…"` 显式登记。装配层替换仍列 §6 [S2] 待办，接线前不得宣称 S2/S3 可用。

R2 三项修复与本文件的对应：

1. **事件入库时点（覆盖 §4.5 偏差）**：shadow 生成路径**不再写事件库**。`shadowTurn` 只装配不入库
   （当前窗口以 pending uid = 已入库最大 uid+1+序号 的未持久化事件参与装配，与随后正式提交分配的
   uid 一致）；事件入库唯一入口改为 `ChampionEngine.commitConfirmedTurn`，挂在
   `LoveBrainViewModel.nextRound` 里 `topicRecorder.record` 成功之后的同一事务点（T4 §1.1 要求的
   actualSent/下一轮正式提交点；W1 当初因并行轨冲突未动 VM，R2 批已接）。未确认输入永不进事件库；
   同一输入多次生成只在提交点记一次（`role|text|ts` 去重）。
2. **存储文件名身份（审计 R2-2）**：`FileChampionEventStorage.fileFor` 由「特殊字符替换下划线」改为
   `清洗前缀(≤48) + "__" + SHA-256(key) 前 24 hex`——清洗是多对一映射，不同 key 可撞同一文件。
   键本身已是 KB 稳定身份（`KbContext.name` = KB 目录名 = kb.json `name`，建库时
   `kb_<时间戳36>_<随机>` 生成；显示名不参与存储键）；删库重建拿新目录名，不复用旧历史。
   存量兼容：当前 OFF 默认 + shadow 未上线，无存量用户数据，直接换键不留迁移。
3. **种子接线（覆盖 §4.6 偏差）**：shadow 路径现在传入真实 KB 画像种子——`KbContext.profile`
   （`KnowledgeRepository.readProfile` 冻结快照 = understand/me|her|warmth|style.md 合并正文）按
   规格书 §2.5 要求的平面标量 JSON 传 `initial_kb={"understand_profile": <正文>}`；产品无独立
   persona 数据源，persona 缺省（段非空才出现）。证据：`GenerationEngine.replyStream` champion 块
   构造 `ChampionSeed(initialKb=…)`，`launchShadow`/`shadowTurn` 带 `seed` 参数传装配器；
   `LoveBrainViewModel` 建 GenerationInput 时 `kbProfile = knowledgeRepo.readProfile(kbName)`。

版本/指纹更新：`PORT_VERSION` = `R2-SHADOW-20261106`；`CHAMPION_SOURCE_SHA256["ChampionEvent.kt"]`
重测 = `96f556099927f5b0134583695d82b12ff7aef26a1e5004cc2e527461c4c65c6a`；
`ChampionEngine.kt`（本批改动后）SHA-256 = `4582fd6f0026d9c8c90b05861bd1bbf7000b5c84bb284322c0a5c370688d5f06`
（替换 §2.4 登记的 W1 旧值 588a02ea…，升级时随 custody 重新登记）。其余四件源文件未动，登记值不变。
验证：worktree `compileDebugKotlin` + 全量 `testDebugUnitTest`（含 SettingsStorePortContract
生产/fake 双侧、champion 专项）全绿后复制回主仓对应路径（未触碰并行轨文件），未 commit。
详见 `R2_CHAMPION_PERSISTENCE_IDENTITY_FIX.md`。
