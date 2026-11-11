# R2 — Champion 引擎外部审计三项修复（持久化时机 / KB 身份碰撞 / 模式诚实化）

日期：2026-11-07 ｜ 产品：D:/LoveBrain 分支 `closeout-20261106`（HEAD=d9ff3db）
实施：隔离 worktree `closeout-r2-tmp`→D:/wt-r2（同标准流程），全绿后复制回主仓对应路径，**未 commit**。
硬约束遵守：未改 BatchImportSheet / ReplyInput / BatchTranscriptParser / PromptBuilder.kt。

## 0. 审计核实结论（逐条先核实再修）

| # | 审计指控 | 核实结果 |
|---|---|---|
| 1 | 事件在 shadow 生成时提前持久化 | **成立**。`ChampionEngine.shadowTurn` 旧实现第 175 行 `eventStore.append(key, messages, resolvedTurn)` 在生成路径落库；产品语义的正式提交点是 `LoveBrainViewModel.nextRound()`（topicRecorder.record 事务点）。T4 §1.1/§2.4 本就要求挂 actualSent 同一判定（W1 §4.5 自记"因并行轨冲突未动 VM"的偏离）。 |
| 2 | KB 显示名特殊字符替换下划线致碰撞 | **部分成立，实害点在文件名而非键**。键 `contactId__story` 的 contactId 实为 `KbContext.name` = KB 目录名（kb.json `name`，建库时 `kb_<时间戳36>_<随机>` 自动生成）——已是稳定身份，显示名并不参与键。但 `FileChampionEventStorage.fileFor` 的清洗（非 `[A-Za-z0-9._-]`→`_`）是多对一映射：不同 key（如 `a/b c` 与 `a_b_c`）可洗成同一文件名互相覆盖；且键不含 KB 删除语义，删库后旧事件文件残留、同名（同目录名）情形下可被读到。 |
| 3 | SINGLE_STYLE/FULL 未真正接线却保留档位 | **成立**。主请求 prompt 仍是原 PromptBuilder，W1 只对原返回应用 ContractGuard（§4 偏差说明第 3 条自己写了"装配层替换本批不接线"），但模式枚举与设置侧保留了档位名，读到即生效路径存在（guard 会插进主链路）——属宣称失实。另核实：shadow 路径 **ChampionSeed 未传**（W1 §4.6 置空偏离），而产品侧数据可得（`KbContext.profile` = `KnowledgeRepository.readProfile` 冻结快照，规格书 §2.5/§125 指定其为 initial_kb 种子来源）。 |

## 1. 修复内容

### 1.1 P0-1 事件提前持久化 → 移到正式提交点

- `ChampionEngine.shadowTurn`：**删除生成路径的 `eventStore.append`**。当前窗口以未持久化 pending 事件
  参与装配（uid = 已入库最大 uid + 1 + 序号；store 不追加时 `uidSeq == maxUid+1`，与随后正式提交分配的
  uid 一致，shadow_log 的 source_id 与正式历史可对齐）；历史 = 已确认事件。角色白名单在装配入口同样拒收。
- 新增 `ChampionEngine.commitConfirmedTurn(contactId, story, messages, turn=-1)`：事件入库**唯一入口**，
  `turn<0` 时按事件库 nextTurn 取号（与 shadow 同口径，同一轮多次生成/重试取到同一号）。
- `LoveBrainViewModel.nextRound()`：在 `topicRecorder.record` 成功之后、`commitReplyRound` 之前调用
  `championEngine.commitConfirmedTurn`（Koin `getOrNull` 可空注入，缺位恒 OFF）。提交失败只记日志、
  **不影响本轮产品落盘**（冠军事件是辅助账）。
- 保证：用户删掉重来的未确认输入永不进事件库；同一输入多次生成/提交只在提交点记一次（`role|text|ts` 去重）。

### 1.2 P0-2 KB 身份碰撞 → 哈希命名

- `FileChampionEventStorage.fileFor`：`<清洗前缀(≤48)>__<SHA-256(key) 前 24 hex>.json`。SHA-256 抗碰撞，
  键↔文件一一对应；键换名即换文件，旧文件失联不复活。清洗前缀仅留人读对账，不承担身份职责。
- 键语义文档化：contactId = KB 目录名（产品全局稳定身份），显示名仅日志展示；删库重建拿新目录名，
  不复用旧历史。
- 存量兼容：当前模式默认 OFF、shadow 未上线，**无存量用户数据**——直接换键，不留迁移代码；
  旧命名文件（内部验证机如有）按不可达废文件对待，不读不删。

### 1.3 模式诚实化 + seed 接线

- 模式枚举保留（`ChampionMode` 四档不动，`fromRaw` 仍解析），但可用档位收敛为 OFF/SHADOW：
  - 读侧：`ChampionEngine.mode()` 读到 SINGLE_STYLE/FULL → 记日志 + 按 OFF（`effectiveMode` 随之收敛，
    `isMainPathGuarded` 分支成为不可达 dormant 结构，保留至 S2 接线）；
  - 设置侧：`SecurePrefs.championEngineMode` 读写两侧都把 SINGLE_STYLE/FULL 落成 OFF 并记日志；
    内存 fake `InMemorySettingsStore` 同形（合同两侧一致）。
- 显式登记：`PORT_IDENTITY.json` 运行时新增 `wired_modes:["OFF","SHADOW"]` 与
  `s2_s3_status:"NOT_WIRED — 主请求 prompt 未切 ChampionPromptAssembler…"`；`W1_CHAMPION_ENGINE_IMPL.md`
  追加 §7 登记。**S2/S3 接线前不得宣称可用。**
- seed：`GenerationEngine.replyStream` champion 块由冻结 `KbContext.profile` 构造
  `ChampionSeed(initialKb = {"understand_profile": <understand/me|her|warmth|style.md 合并正文>})`，
  经 `launchShadow`/`shadowTurn` 新增的 `seed` 参数传入装配器（persona 缺省——产品无独立 persona 数据源，
  该段非空才出现）。装配所需数据全部取自 KnowledgeRepository 既有读取口（readProfile → buildGenerationInput
  冻结进 KbContext.profile），生成路径零新增 IO。

## 2. 版本与指纹

- `PORT_VERSION`：`W1-SHADOW-20261106` → `R2-SHADOW-20261106`。
- `CHAMPION_SOURCE_SHA256["ChampionEvent.kt"]` 重测 =
  `96f556099927f5b0134583695d82b12ff7aef26a1e5004cc2e527461c4c65c6a`（LF 归一）。
- `ChampionEngine.kt`（R2 后）= `4582fd6f0026d9c8c90b05861bd1bbf7000b5c84bb284322c0a5c370688d5f06`
  （登记于 W1 §7，替换 W1 §2.4 旧值）。其余三件机制源文件未动，登记值不变。
- **对拍义务提示**：`ChampionEvent.kt`/`ChampionEngine.kt` 源文件已改——按 §3 铁律，S2 升级前需随 custody
  重走对拍；本批是产品侧接线修复，不改变 arena 侧被验证机制（ARENA_MECHANISM_SHA256 未动）。

## 3. 改动文件清单（已复制回 D:/LoveBrain，未 commit）

| 文件 | 改动 |
|---|---|
| `domain/champion/ChampionEngine.kt` | shadowTurn 去持久化+pending uid+seed 参数；commitConfirmedTurn；mode() 钳制；PORT_IDENTITY 登记；PORT_VERSION/SHA 表更新 |
| `domain/champion/ChampionEvent.kt` | fileFor 哈希命名 + 键身份文档 |
| `domain/GenerationEngine.kt` | champion 块传 seed；注释更新（S2/S3 dormant、入库点迁移） |
| `viewmodel/LoveBrainViewModel.kt` | 构造器可空 championEngine；nextRound 提交点接线 |
| `data/SecurePrefs.kt` | championEngineMode 读写两侧钳制 SINGLE_STYLE/FULL→OFF |
| `di/AppModule.kt` | viewModel 装配补 `championEngine = getOrNull()` |
| `test/.../ChampionEngineTest.kt` | 重写 2 个持久化测试（shadow 零落库、未确认输入永不入库、重复提交去重、提交后取号）、新增 S2/S3 钳制测试、PORT_IDENTITY 接线状态断言 |
| `test/.../ChampionEventStoreTest.kt` | 新增清洗碰撞不塌缩测试 |
| `test/.../SettingsStorePortContractTest.kt` | 合同改为钉 OFF 收敛语义；内存 fake 同形钳制 |

## 4. 验证

- worktree D:/wt-r2：`local.properties` 已复制；`JAVA_HOME=JDK21 Temurin 21.0.3.9`。
- `./gradlew compileDebugKotlin`：BUILD SUCCESSFUL。
- `./gradlew testDebugUnitTest`（**全量**，非仅 champion）：BUILD SUCCESSFUL。期间修复两处合同测试
  随语义更新（ProductionSettingsStoreContractTest、InMemorySettingsStoreContractTest 的 FULL 往返断言
  → 钉 OFF 收敛）后全绿。champion 专项（ChampionEngine/ChampionEventStore/Assembler/Guard/History/Uc
  六类）全绿。
- 回写核对：主仓并行轨在改 PromptBuilder/TopicRecorder/TopicSwitchGate/BatchImportSheet/ReplyInput 等
  12 个文件，与本批 9 个文件**零交集**，整文件复制、无手工合并。

## 5. 遗留 / 交接

- **[S2] 装配层替换**仍未做（主请求 prompt 仍是原 PromptBuilder）——本批只是把"未接线"如实化并登记。
- **[S2] ME 侧入库点语义**：R2 已把提交点挂到 `nextRound`（topicRecorder.record 后）；T4 §1.1 提到的
  `actualSent.record`（:1593 链路）是另一条"实际发送"记账线，若 S2 要区分「记入知识库」与「实际发送」
  两个提交语义，再单独版本化，本批不扩scope。
- **[运维] pending uid 与提交 uid 的一致性**依赖"shadow 与提交之间 store 未变"；若未来引入并发提交
  （多窗口同库），须改为提交后回读对账。
- **[真机] shadow 全链路真机验证**仍是 W1 §6 [U] 项。
