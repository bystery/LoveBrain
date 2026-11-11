# KOTLIN_INTEGRATION_MAP — arena→D:/LoveBrain 新旧引擎切换移植点与未验证项

2026-10-25。对应 `integration_preview/engine_adapter.py`（arena 侧已用替身 I/O 跑通五接口）。
本轮**不改生产、不迁用户真实库**；此文件把 arena 的 harness 行为映射到产品真实入口，供实测后接入。
产品仓库核对：branch `main`、HEAD `176e521`(v1.4.2)、无未提交改动；中央根解析见 `D:/LoveBrain/AGENTS.md`
（`TEAM_WORKSPACE_ROOT` 或 `<repo>/../TeamWorkspace`）；正式入口 `<中央根>/team/projects/lovebrain/START_HERE.md`。

## 五接口 → 产品真实入口

| arena adapter 类 | 产品入口（只读定位，不假定等价） | 移植动作 |
|---|---|---|
| `normalize_events` / `Event` | `data/KnowledgeMemoryStore.kt` 的 read/raw_chat 事件、UI 层 ChatMessage | 定义 speaker/text/ts/committed + **typed `kind`（"chat" / "note"）**归一；**生成草稿与用户确认发送分开**（草稿 committed=false 永不写进已发送历史）。**IDEA/ADVISOR_NOTE = note-kind 结构边界**：只作本轮约束通道进 note，永不转成聊天消息、不进已发送/committed 历史、不落真实事件存储（`to_msg` 对 note-kind 直接拒绝，不靠字符串匹配 speaker） |
| `IntentInput.effective_status` | `domain/IntentPolicy.kt` `effectiveStatus(expiry,status)` | 复用产品既有终态归一（COMPLETED/EXPIRED/PAUSED→非 ACTIVE）；有效意图只在 ACTIVE 进 `PromptBuilder.buildReplyUserPromptWithRefs` 的 intent 块；**关闭≠删真实约定**（约定走 raw_chat 事件，不随 intent 清） |
| `EventStore` | `data/KnowledgeRepository.kt` + `KnowledgeMemoryStore.kt` `writeTransaction`/`revision` | 新引擎事件存储与原版存储**分目录/分 kb，互不覆盖**；按 (engine, contactId, story) 隔离；snapshot/restore 用产品既有 revision-check 写 |
| `OutputConsumer.parse` / `memory_should_update` | `data/DeepSeekRepository.kt` `generateRaw/generateRawWithMetadata` + 现有 JSON 解析 | **空 analysis 在新引擎合法**：解析时把 `{}`/缺失 视为"本轮不消费内部摘要"，**不得**触发旧 summary 更新或清库；仍保留 response 四风格 / directions 可发选项。**消费边界**：合法 JSON 形状变体（顶层数组 `[]`、`response` 为列表、标量/null）一律**不崩**，分类 `ok`/`partially-consumable`/`unconsumable-schema`，**原文逐字节保留**并记录原因。**协议键名容错=可审计消费适配**：仅在匹配必需键名时去不可见/仅格式字符（Unicode 类别 Cf，如 U+200B/U+200C/U+FEFF）与首尾空白，**值字节不动**、正文校验不放宽、缺槽不臆造，每处归一写入 `key_normalization_audit`（original_repr/normalized/slot_affected）。**仅动消费端，未碰考场评分器 `tools/`**（对评分尺施加同样归一是另行版本化的离线改动，不在本轮） |
| `EngineRouter` (use_v5) | 产品某处 feature flag / Repository 选择 | 实验开关：`use_v5=false` 回退原版 `PromptBuilder`+原记忆管线；开关关闭时产品仍可用原版 |

## 关键边界（照 v5_independent_review §4.3–4.5 与 arena AGENTS §6）

- 出站体白名单、返回物只 `json.loads`、不 eval/exec/import、不补问修 JSON；坏输出→不写回、不清库。
- 独立复核 2026-10-25 §3 三条接入原型缺陷已在 arena 侧离线修复并用替身 I/O 复现/证明（见
  `tools/test_integration_preview_boundaries.py` 与 `replay_fixture.py`）：①IDEA 私密备注经 `kind` 结构边界
  只走 note、不进已发送 `<chat>`、不落事件存储、恢复后不残留；②消费者对合法 JSON 形状变体（`[]`/`response`
  为列表/标量/null）不崩，分类 ok/partially-consumable/unconsumable-schema 且原文逐字节保留；③协议键名容错
  只在消费端做（值不动、正文不放宽、缺槽不臆造）并写 `key_normalization_audit`；**未改考场评分器 `tools/`**
  （把同样归一施加到评分尺是另行版本化、需离线重评的改动，不在本轮）。
- 新旧引擎**存储互不覆盖**、可并存回退；不迁移用户真实库、不整批搬进产品，只移植实际需要的语义与状态逻辑
  （arena 身份闭包含 25 个追溯文件 ≠ 产品要搬 25 个）。
- 实验切换是**接入积木**，不是把整座考场/评分器搬进产品。

## 未验证项（如实登记，不冒称已验收）

1. Kotlin 侧未编译/未跑（本机以 arena Python 原型证明接口形状与恢复行为，不证 Android 构建通过）。
2. 未做真实产品 UI 体验验收；固定回放后续不能当"她真被聊动"的证据。
3. 尚未接入产品 feature flag 的真实读取路径（`START_HERE.md` 正式规则本轮未改产品，接入前须按中央工作流加载并核对当前仓库）。
4. 空 analysis 在**产品旧消费端**是否已被别处当作损坏——需接入时先在产品回放/状态恢复夹具验证再上开关（arena §5 第 3 点）。
5. 事件存储/恢复与产品 KnowledgeRepository 的 kb 语义、corrections 策略的等价性尚未逐字段对拍。
   （arena 侧恢复测试已加固：restore 后用替身网关**真正再发一轮**，断言续用历史与私密备注不残留；
   但这仍只证 arena Python 原型，不证产品 KnowledgeRepository 的等价。）
6. 本轮三条边界修复（IDEA 备注归属、消费不崩分类、协议键名可审计归一）只在 **arena harness + 替身 I/O**
   证明；未在真实产品 Kotlin 读/写路径、Android 或 `127.0.0.1:17770` 服务上验证（该服务本轮未触碰）。

## 下一步（真实 48 次出结果且开发保留后）

冻结胜出版本 → 用独立出题交接做有限 32 次验证 → 在此原型上补 Kotlin 侧最小适配与回放/状态恢复验证 →
按产品 AGENTS 中央入口接实验开关、保留原版回退；通过范围明确前不接默认生产。
