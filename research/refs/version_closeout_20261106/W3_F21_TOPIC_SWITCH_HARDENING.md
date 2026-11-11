# W3 — F2.1 话题切换判据加固（产品侧修复，2026-11-06）

> 修复对象：`D:/LoveBrain` 分支 `closeout-20261106`（产品 F2.1 故障）。
> 审计依据：`T5_KNOWN_FAILURES_AUDIT.md` F2.1——话题切换仅凭回复模型单判据
> `topic_status=new`（原 TopicRecorder.kt:123），模型误判即清空 moment/recent.md 与
> moment/scene.md，造成场景记忆丢失。判决 STILL_PRESENT。
> 本轨改动文件（仅 4 个，未触碰 OngoingPlanStore.kt / PromptBuilder.kt /
> knowledge_prompt/ / com.lovebrain.app.domain.champion）：
>
> - 新增 `app/src/main/java/com/lovebrain/app/domain/TopicSwitchGate.kt`
> - 修改 `app/src/main/java/com/lovebrain/app/domain/TopicRecorder.kt`（仅 record() 决策点与注释）
> - 新增 `app/src/test/java/com/lovebrain/app/domain/TopicSwitchGateTest.kt`（14 例）
> - 新增 `app/src/test/java/com/lovebrain/app/domain/TopicSwitchHardeningTest.kt`（6 例）
>
> 未 commit。worktree `D:/wt-w3`（分支 closeout-w3-tmp）编译+全量单测验证后已按纪律清理。

## 1. 新判据逻辑（TopicSwitchGate）

`topic_status=new` 现在只是**主张**，必须命中**至少一个佐证信号**才允许旋转
（归档+清空）。佐证全部来自既有数据字段，**不新增模型调用**；门是纯函数
（`internal object TopicSwitchGate.evaluate`），不碰任何端口：

| 信号 | 判据 | 语义 |
|---|---|---|
| `SCENE_EMPTY` | moment/scene.md 无可解析条目（`- [yyyy-MM-dd HH:mm]` 行缺失或不可解析） | 无场景记忆可丢，误判清空代价为零；不阻塞长冷却后的合法新话题 |
| `TIME_GAP` | scene 链最新条目时间距 `max(本轮最新消息时刻, 当前时刻)` ≥ `MIN_SCENE_GAP_MS = 60min` | 话题自然冷却，切换可信；<60min 不佐证（含 59min 边界用例） |
| `LABEL_CHANGE` | 新 topic_label（trim 后）≠ 当前话题标签 | 模型自己在说"换了个话题"，与 status=new 相互印证；**同标签的 new 是最主要误判形态** |
| `USER_MARKER` | 本轮 HER/ME 消息正文含保守白名单（"换个话题/换话题/说点别的/聊点别的/说别的/聊点其他/新话题/不聊这个"） | 用户显式换话题标记；确定性、只做佐证，与已废弃的"关键词判场景"不同——未命中不扣分 |

决策点在 `TopicRecorder.record()` 步骤 1（WAL PREPARED 之前）：仅当
`topicLabel.isNotBlank() && topicStatus == "new"` 时调用门；结论
`shouldRotate` 随 `RoundCommitJournal.RoundCommitEvent` 进 WAL，**崩溃恢复
（recoverIfNeeded→applyRound）与首提走同一份 event.shouldRotate**，不存在恢复路径重新判定导致的首提/恢复分歧。`same`/`drift` 状态与空标签路径完全不受门影响（有回归用例钉住）。

比较口径：标签比较只做 trim；`正在聊：` 标记的剥离归 KbTextOps.topicLabel
单一所有者（TopicLineFormatTest 把该字面量钉死在 KbTextOps.kt 一个文件——
本门不抄第二份，曾因字面量复制被该架构尺拦下，已改正）。

## 2. 误判保护行为

- **未确认切换**：保持现状——moment/scene.md、moment/recent.md 均不清，
  当前话题标签不改（投影 2 的 else→null 分支），本轮照常记 recent/scene/plan/count
  （误判保护不影响正常记账）。诊断日志落
  `L.w("疑似新话题被否决（topic_status=new 无佐证）：<原因>；signals=[]")`，
  原因只含信号名与分钟数值（如 `scene最新条目间隔=10min<60min、topic_label未变化、
  无显式换话题标记`），**不含消息正文与标签正文**（util/L.kt 隐私红线），
  供后续按日志校准白名单与阈值。
- **确认切换（归档完整性）**：生产归档路径 `KnowledgeArchiveService.rotate` 的四步
  状态机本就"先追加归档、后清空"（步骤 2 APPEND_ARCHIVE 在步骤 4 CLEAR_SOURCES
  之前，且步骤 4 幂等可恢复）：`moment/scene.md` + `memory/raw_scene.md` 经
  `KbTextOps.mergeSceneEntries` 合入 `memory/raw_topic.md` 的"状态变化"段，
  `moment/recent.md` + `memory/raw_chat.md` 合入"对话记录"段，旧话题名进条目标题，
  topicCount +1，然后才清空四个源文件。**本轨验证并保持该顺序未动**，
  并新增端到端用例（真实 KnowledgeRepository + 临时目录）断言四份被清内容
  在清空后均完整存在于 raw_topic.md。

## 3. 验收探针（复测命令）

```bash
git -C D:/LoveBrain worktree add -b closeout-w3-tmp D:/wt-w3 closeout-20261106
# 复制上述 4 个文件到 worktree 同路径 + local.properties
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.3.9-hotspot"
cd D:/wt-w3 && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest \
  --tests "com.lovebrain.app.domain.TopicSwitch*" \
  --tests "com.lovebrain.app.data.TopicLineFormatTest"
```

- `TopicSwitchGateTest`（纯 JVM，判据单测）：单判据形态（同标签+新鲜 scene+无标记）
  被否决且 signals 为空；空标签否决；否决原因不含正文；LABEL_CHANGE / TIME_GAP
  （含 59/90min 边界）/ SCENE_EMPTY（含不可解析行）/ USER_MARKER（HER 与 ME 侧）
  单独命中即确认；多信号并列上报；newestSceneTimeMs 取最新条目。
- `TopicSwitchHardeningTest`（真实仓库端到端）：
  ① 同标签 new 无佐证 → 返回 false，scene/recent/topic.md 原样，raw_topic 无新条目；
  ② 标签变化佐证 → 旋转发生、标签更新、raw_topic 出现归档条目；
  ③ scene 90min 陈旧佐证同标签切换 → 旋转；
  ④ HER 消息"我们换个话题吧"佐证同标签切换 → 旋转；
  ⑤ 归档完整性：确认切换后 raw_topic.md 同时含 scene/recent/raw_chat/raw_scene
  四份内容与旧话题名，且四个源文件清空（recent 由投影 3 重写为本轮新条目）；
  ⑥ same 状态永不触发门。
- 相关回归全绿：RoundCommitJournalTest（19）、SceneUpdateRegressionTest（10）、
  TopicRecorderStoreOwnershipTest（6）、ArchiveOperationStateTest（6）、
  TopicLineFormatTest（5）、PackageDependencyTest。
- 全量 `:app:testDebugUnitTest`：2797 例中仅环境性失败（
  `ReplyInputRowCaptureTest`——Roborazzi 输出目录 build/outputs/roborazzi 在全新
  worktree 不存在的 FileNotFoundException，属 UI 截图基础设施，与本轨 domain 层
  改动无关；另有一次 `TopicSwitchHardeningTest` 的
  `atomic write backup failed: .round_commit_journal.json` IOException——Windows
  临时目录原子写备份竞态，隔离复跑 3/3 全绿、首轮全量亦绿，与本轨判据逻辑无关）。
  两类 flake 均不影响 F2.1 修复判决。

## 4. 已知边界（诚实登记）

- scene.md 为空即放行（SCENE_EMPTY）：此时误判旋转只损失 recent 2 轮的 prompt
  可见性（已完整归档 raw_topic），不损失场景链；换来长冷却后合法新话题不被卡死
  （否则 F2.1 反向恶化）。若后续日志显示该信号被滥用，可在校准时收紧为
  "SCENE_EMPTY + LABEL_CHANGE 至少其一"。
- 白名单与 60min 阈值为初版常量，依据是否决日志的分布数据再校准；
  门是纯函数，调整只动 TopicSwitchGate 一处。
- InMemoryKnowledgePort.getCurrentTopic 返回带 `正在聊：` 前缀的标签，与生产
  KbTextOps.topicLabel 口径有既有偏差（本轨未修，属 port 合同轨）；生产路径无此问题。
