# W2 — F3.3 纠错后旧值在摘要中回流：产品修复交付（2026-11-06）

## 对象与依据

- 产品：`D:/LoveBrain` 分支 `closeout-20261106`（worktree `D:/wt-w2`，分支 `closeout-w2-tmp`，未 commit）
- 依据：`T5_KNOWN_FAILURES_AUDIT.md` F3.3 —— 纠正（corrections，WRONG/MUTED/FINISHED/WRONG_PERSON）只在注入侧生效（`MemoryRefPolicy` + `PromptKnowledgeSection`），后台提取链（lessons 经验提取 / reflect 画像 / vector 重估）读源材料时不消费纠正表，被纠正过的旧内容按原文回流进经验与画像，静默覆盖用户纠正。

## 改动点

| # | 文件 | 改动 |
|---|---|---|
| 1 | `app/src/main/java/com/lovebrain/app/domain/prompt/CorrectionExtractionFilter.kt` | **新增**。提取链统一纠正过滤的纯函数单一真源：复用 `MemoryRefPolicy.isCorrected` / `isMuteExpired` 既有语义（不发明新状态），未纠正→原文逐字放行；WRONG 带补正→原文不出现、以注入侧同款 `（已纠正：…）` 传导纠正值；WRONG 无补正 / FINISHED / WRONG_PERSON / MUTED 未过期→整段排除；MUTED 过期→与注入侧同判恢复。 |
| 2 | `app/src/main/java/com/lovebrain/app/domain/port/KnowledgePorts.kt` | `KnowledgeReadPort` 新增只读口 `readCorrections(kbName)`。此前纠正表只在页面侧 `KnowledgeRuntimePort` 可见，domain 读取层拿不到它（ F3.3 成因之一）。生产实现 `KnowledgeRepository` 已有同名方法（转手，无第二实现）。 |
| 3 | `app/src/main/java/com/lovebrain/app/domain/KnowledgeContextReader.kt` | `getTopicFullContext`（lessons 提取）与 `getVectorContext`（向量重估）读 `moment/scene.md` 时过 `correctionFiltered` 私有口 → `CorrectionExtractionFilter`。recent / raw_chat / raw_scene / raw_topic / 当前话题无 MemoryRef 纠正身份（纠正表不可能命中），原样读取并在注释注明。 |
| 4 | `app/src/main/java/com/lovebrain/app/domain/PromptBuilder.kt` | **最小改动**（仅 `buildReflectUserPrompt` 提取 prompt 一支，回复装配主函数未动）：读纠正表一次，me/her/warmth（PROFILE）与 lessons（LESSON，先 `LessonDoc.purify` 再过滤）过同一颗 `CorrectionExtractionFilter`；新增私有薄转发 `correctionFiltered`。raw_topic / 谈心分析无纠正身份，原样读取。 |
| 5 | `app/src/test/java/com/lovebrain/app/domain/port/InMemoryKnowledgePort.kt` | 测试 fake 补 `readCorrections` override + `seedCorrections` 预置口（与生产同口）。 |
| 6 | `app/src/test/java/com/lovebrain/app/domain/prompt/PromptByteFreezeBaselineTest.kt` | `domain/prompt` 目录白名单加入 `CorrectionExtractionFilter.kt`（消息注明是 F3.3 新增纯过滤口，非第二份搬移）。 |
| 7-9 | `CorrectionExtractionFilterTest.kt`（新）、`KnowledgeContextReaderCorrectionTest.kt`（新）、`ReflectCorrectionsFilterTest.kt`（新） | 单元测试 18 条，见下。 |

未触碰（硬约束）：`TopicRecorder.kt`、`OngoingPlanStore.kt`、`PromptBuilder` 回复装配主函数、`com.lovebrain.app.domain.champion` 包（W1 轨）。`KnowledgeTriggerCoordinator` 与 `KnowledgeProfileTransactionService` **零改动**：过滤统一收在读取层，协调器与写事务拿到的源材料已被净化。

## 修复前后行为对比

| 场景 | 修复前 | 修复后 |
|---|---|---|
| `moment/scene.md` 被标 WRONG（带补正） | 原文照常进 lessons 提取与 vector 重估输入，经验/向量按旧值重算 | 原文不出现，输入只见 `（已纠正：补正值）` |
| `moment/scene.md` WRONG 无补正 / FINISHED / WRONG_PERSON / MUTED 未过期 | 同上 | 整段不进提取材料（vector 上下文连"【此刻状态】"节头一并省略） |
| MUTED（TODAY）跨天 | 提取侧根本不识别 MUTED | 与注入侧同判恢复放行 |
| `understand/her.md` 被标 MUTED | reflect 输入照含原文，画像重写反复把静音内容写回 | her.md 内容不出现在 reflect 输入；me.md / warmth.md 逐字保留 |
| her.md WRONG 带补正 | 旧值回流进画像覆写 | reflect 只见补正值，画像更新不再覆盖用户纠正 |
| `memory/lessons.md` 整文件被纠正 | 历史经验原文回流 | 该文件不进 reflect 经验段 |
| 未纠正的一切内容 | — | 逐字节不变（`PromptByteFreezeBaselineTest` 冻结表全绿） |

注入侧行为零改动：`MemoryRefPolicy` / `PromptKnowledgeSection` 未动，只是被复用。

## 单元测试（纯 JVM，18 条全绿）

- `CorrectionExtractionFilterTest`（9）：未纠正逐字放行；空串直通；WRONG+补正＝原文不泄漏＋`（已纠正：…）`；WRONG 无补正/FINISHED/WRONG_PERSON＝null；MUTED 未过期＝null；MUTED TODAY 跨天＝恢复；ONGOING 条目级只命中本条；LESSON 文件级命中。
- `KnowledgeContextReaderCorrectionTest`（5）：未纠正场景链原样进两路上下文；WRONG 后 lessons 提取材料无旧值、有补正值、其余源材料与库文件不受影响；MUTED 不出现在 vector 重估输入；TODAY 跨天恢复；WRONG 无补正时场景段整段省略。
- `ReflectCorrectionsFilterTest`（4）：**被 MUTED 条目不出现在 reflect 输入**；WRONG 旧值不回流且补正值传导；被纠正经验文件不进 reflect 且经验段省略；无纠正时逐字保留。夹具用"一读就抛"的假资产口钉住"提取支不读资产"。

## 验收探针（运行时建议）

1. 对某条场景事实（如"她在杭州出差"）发起 WRONG+补正"她在苏州出差"→ 等下次 lessons 提取/向量重估触发 → `memory/lessons.md` 新批与 `memory/vector_history.md` 依据中不得出现"杭州"，应出现"苏州"。
2. 对画像 her.md 某条 MUTED → 手动触发画像刷新 → `memory/reflect_history.md` 的建议不得复述被静音内容。
3. 撤销纠正（TODAY 跨天）→ 提取链恢复读原文。

## 编译与测试证据

- worktree `D:/wt-w2`（JDK 21，`./gradlew :app:testDebugUnitTest`）：新增 18 条 + `com.lovebrain.app.domain.*` / `domain.port.*` / `domain.prompt.*` 全量回归绿；全套件结果见交付时记录。
- 改动已复制回 `D:/LoveBrain` 对应路径（只含上表 9 个文件）；未 commit；worktree 已清理。
