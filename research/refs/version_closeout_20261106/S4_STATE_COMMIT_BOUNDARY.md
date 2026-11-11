# S4 — 外部审计第二轮修复：状态变更无提交边界（#19 + #09）

- 日期：2026-11-06
- 产品仓：D:/LoveBrain，分支 `closeout-20261106`，基线 HEAD=`6ed7fbc`
- 实施分支：`closeout-s4-tmp`（worktree D:/wt-s4），**未 commit**
- 约束遵守：未触碰 LoveBrainViewModel（S1）、GenerationEngine/champion（S1）、PromptBuilder/CorrectionExtractionFilter（S3）、BatchImport/LoveBrainPanelScreen（S2）、RoundCommitJournal。

## 共同根因

读取/准备请求 ≠ 确认一次真实状态变化。两处都在"只有准备、没有确认"的路径上推进了持久状态。

## #19 事项冷却在 Prompt 构造时提前写盘

**旧形状**：`OngoingContextSelector.selectForInjection`（旧 204-212 行）完成选择即调 `updateCooldown()`，
`lastInjectedTurn`/`lastEvidenceTurn` 直接落 `moment/ongoing_cooldown.json`。用户生成失败、取消或改写消息时，
从未确认的输入已提前改变冷却/证据状态。

**修法（两段式：暂存 → 确认事务点落盘）**：

1. `OngoingContextSelector.kt`：选择不再写盘。本轮推进量（injectedNames / evidenceNames / turn）打包为
   `PendingCooldownUpdate`，经 `OngoingCooldownStore.stage()` 暂存（进程内存，同库单槽，新选择覆盖旧暂存）。
   `SelectionResult` 与 `selectForInjection` 签名不变——PromptBuilder（S3 禁触）零改动。
2. 冷却持久化原样抽出为 `OngoingCooldownStore`（仍在 OngoingContextSelector.kt，即"冷却存储所在文件"）：
   JSON 字段名、20 条上限、注入/证据两条独立时间线、损坏 fail-closed 全部不变。
3. `TopicRecorder.kt`：`record()` 在 journal 提交成功后（Committed 与 AlreadyCommitted 两条出口都覆盖，
   consume 幂等防重复推进）调 `commitPendingOngoingCooldown`，用 **两侧共用的
   `OngoingCooldownStore.roundFingerprint(HER/ME 消息 id+内容)`** 匹配暂存：
   - 同一轮重试/重生成 → 指纹一致 → 提交成功才落盘；
   - 改写消息 / 换轮 / 生成失败取消（永不 record）→ 指纹失配或暂存被新选择覆盖 → **冷却与证据状态不推进**；
   - 冷却写失败只记 L.w，不把已提交轮次拖成失败。
   接线在 record（确认事务点）内部完成，绕开 VM——LoveBrainViewModel 零改动。

**诚实边界**：暂存是进程内存，进程死亡丢失暂存的方向是"冷却不推进"而非误推进（fail-safe）；
崩溃恢复（recoverIfNeeded）不重放冷却推进——同一方向。冷却写盘在 journal 事务之外（journal 属禁触文件），
已用 consume 幂等 + 失败只记日志收敛。

**回归测试**（先按审计建议补测再改实现，最终同批交付）：
新增 `domain/OngoingCooldownCommitBoundaryTest.kt`（真实 KnowledgeRepository 落盘语义，5 用例）：
①准备后不写盘且 pending 在场；②失败/取消（不 record）→ 随后不相关轮提交也不推进；
③确认提交 → 注入与证据两条时间线一起推进到本轮；④改写消息后提交 → 冷却文件不动；
⑤确认后再选择只暂存、盘面保持上一轮确认值。
`PromptBuilderOngoingDirectiveIntentTest` 相应用例改为钉住"Prompt 构造不写盘（lastInjectedTurn 保持 seed 值）+ pending 暂存、备注不算证据"。

## #09 SCENE_EMPTY 误判回归

**旧形状**：`TopicSwitchGate.evaluate` 中 scene 链无条目即发 `SCENE_EMPTY` 且可单独定案；
`TIME_GAP` 也可单独定案。scene 空但 recent.md 仍有当前话题内容时被当成"无可丢记忆"——
旋转会把 recent 一并归档清空，丢掉真实对话记忆。

**修法（按审计建议：不堆复杂规则，收紧两颗判据）**：

1. `SCENE_EMPTY` 判据补查 recent 层：`newestSceneTimeMs(scene)==0` **且**
   `hasRecentLayerContent(recent)==false`（任何非空行都算，含 round marker 注释行）才发信号。
   `evaluate` 新增**必传**参数 `recentContent`，TopicRecorder 传 `moment/recent.md` 原文——
   调用方必须明示这一事实，不给默认值。
2. 弱信号规则：`SCENE_EMPTY` 与 `TIME_GAP` 单独命中**不得定案**，需第二个非模型信号；
   `USER_MARKER`（确定性用户表述）仍可单独定案；`LABEL_CHANGE` 维持 R5 同源共误、永不参与定案。
   否决原因同步区分"弱信号需第二信号"与"无佐证信号"，仍只含信号名/数值/布尔（隐私红线）。

**回归测试**：
- `TopicSwitchGateTest`（20 用例）：TIME_GAP 单独否决、SCENE_EMPTY 单独否决、
  **scene 空 + recent 有内容 → 不得当无可丢记忆**（核心回归）、TIME_GAP+USER_MARKER 确认、
  真空层+USER_MARKER 确认、LABEL_CHANGE+TIME_GAP 仍否决、reason 口径与隐私断言。
- `TopicSwitchHardeningTest`（9 用例，真实仓库端到端）：**旧场景空 + 最近对话仍是原话题 +
  模型误报 new → 不旋转**（新增）；TIME_GAP+LABEL_CHANGE 不再触发；TIME_GAP+显式标记照常旋转
  （归档完整性用例消息补显式标记）；原"单判据保持现状"回归保留。
- `RoundCommitJournalTest`：故障矩阵基线依赖旋转发生，其消息补显式换话题标记（判据变化下基线仍可旋转）。

## 验证

- `:app:compileDebugKotlin`：PASS。
- 相关 15 个测试类定向运行：PASS（TopicSwitchGateTest 20、TopicSwitchHardeningTest 9、
  OngoingCooldownCommitBoundaryTest 5、RoundCommitJournalTest 19、TopicRecorderStoreOwnershipTest、
  SceneUpdateRegressionTest、ClockWiringTest、OngoingContextSelectorTest 13、MeetingPollutionRegressionTest 5、
  IntentInjectionAgreementTest、OngoingEvidenceSignalTest、PromptBuilderOngoingDirectiveIntentTest 5、
  PromptAssemblyOrderContractTest、PromptByteFreezeBaselineTest、ProactiveRoundScopeTest，0 failures 0 errors）。
- 全量 `:app:testDebugUnitTest`：PASS——371 个测试类 2962 用例，0 failures 0 errors。

## 改动清单（全部在授权范围内）

| 文件 | 改动 |
| --- | --- |
| app/src/main/java/com/lovebrain/app/domain/OngoingContextSelector.kt | 选择改暂存；抽出 OngoingCooldownStore + PendingCooldownUpdate + 指纹函数 |
| app/src/main/java/com/lovebrain/app/domain/TopicRecorder.kt | gate 传 recentContent；提交成功后 commitPendingOngoingCooldown |
| app/src/main/java/com/lovebrain/app/domain/TopicSwitchGate.kt | SCENE_EMPTY 补查 recent；弱信号不得单独定案；reason 更新 |
| app/src/test/.../OngoingCooldownCommitBoundaryTest.kt | 新增（#19 回归，5 用例） |
| app/src/test/.../TopicSwitchGateTest.kt | 重写对齐新判据 + #09 回归 |
| app/src/test/.../TopicSwitchHardeningTest.kt | #09 端到端回归 + 基线消息补显式标记 |
| app/src/test/.../RoundCommitJournalTest.kt | 基线消息补显式标记（故障矩阵不受判据收紧阻塞） |
| app/src/test/.../prompt/PromptBuilderOngoingDirectiveIntentTest.kt | 钉住"Prompt 构造不写盘 + pending 暂存" |

## 保留风险（如实登记）

1. 冷却暂存为进程内存：应用被杀时该轮冷却不推进（宁缺勿假推进）。
2. 后续若有第三处需要"选择后不提交"的状态推进，应复用 pending→确认事务点模式，不再各写各的。
