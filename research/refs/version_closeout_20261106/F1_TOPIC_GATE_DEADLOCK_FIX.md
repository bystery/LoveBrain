# F1 — 话题切换门死锁修复（外部审计第三轮 P0）

日期：2026-11-07　分支：closeout-20261106（HEAD=8cafeec）　状态：已修复，未 commit

## 1. 审计指认核实（属实，逻辑可证）

`domain/TopicSwitchGate.kt` 旧定案判据：

- SIGNAL_SCENE_EMPTY 要求 `newestSceneMs == 0L && !recentHasContent`
- SIGNAL_TIME_GAP 要求 `newestSceneMs > 0`
- 两条件在 `newestSceneMs` 上**互斥** → `weakSignals.size >= 2` 永不可达；
- 确认判据 `strongSignals.isNotEmpty() || weakSignals.size >= 2` 实际只剩 USER_MARKER
  （白名单用语）可定案。

后果：自然话题切换（模型正确输出 new、无白名单用语）被永久否决——归档、场景生命周期、
按话题触发的后台更新全部锁死。与代码逐行核对，指认成立，非猜测。

## 2. 修复（按审计建议，佐证可组合，不退回 AI 单判据）

`TopicSwitchGate.kt` 定案规则改为：

- **确认**：模型主张 topic_status=new + **任一非模型源信号**在场即确认——
  SCENE_EMPTY / TIME_GAP / USER_MARKER 三者任一，可组合、可叠加，不再要求凑两个弱信号；
- **S5 禁令维持**：LABEL_CHANGE 与 topic_status=new 同源共误，永不参与定案；
  仅它命中（无任何非模型佐证）→ 否决，否决原因保留"同源共误"口径；
- **既有防线全部保留**：
  - 审计 #09 的 recent 非空检查：scene 空但 recent.md 仍有当前话题内容 → 不算
    "无可丢记忆"，SCENE_EMPTY 不发（旋转会连带清空真实对话记忆）；
  - "仅标签变化不旋转"反向测试原样保留；
  - 否决原因隐私口径不变（只含信号名与数值）。

`TopicRecorder.kt`：无需代码改动（调用点注释本就描述"至少一个非模型源佐证"语义，
门签名与传参不变）。

审计正向场景口径说明：SCENE_EMPTY 成立要求 scene 空 **且 recent 空**（recent 有内容时
该信号按 #09 防线不成立，此时无佐证 → 不旋转，保留为反向测试）——正向测试据此取
"scene 空 + recent 空"；TIME_GAP 正向取"场景证据陈旧（>60min）"。

## 3. 测试（worktree D:/wt-f1，compileDebugKotlin + 全绿）

TopicSwitchGateTest（20 个）：
- 新正向：`time gap alone confirms a natural switch without user marker`（TIME_GAP 单独 +
  无白名单用语 → 确认）、`empty scene with empty recent confirms a natural switch without
  user marker`（SCENE_EMPTY 单独 → 确认）、`unparsable scene lines count as empty scene and
  corroborate the switch`、`label change plus time gap confirms the natural switch`
  （LABEL_CHANGE 不拖累其他佐证）；
- 保留反向：单判据形态（同标签+新鲜 scene+recent 有内容+无标记）否决、LABEL_CHANGE
  单独否决（同源共误）、scene 空+recent 有内容不算无可丢记忆、59min 低于阈值不佐证、
  隐私红线等。

TopicSwitchHardeningTest（11 个，真实 KnowledgeRepository 端到端）：
- 新正向：`natural topic switch with stale scene rotates without user marker`（自然切换
  端到端旋转 + 归档 + 标签更新）、`stale scene corroborates a same-label switch`、
  `empty scene with empty recent rotates without user marker`；
- 保留反向：无任何非模型佐证（scene 新鲜+recent 有内容）不旋转、仅 LABEL_CHANGE 不旋转、
  scene 空+recent 有内容+误报 new 不旋转、归档完整性（先归档后清四个源文件）、same 状态
  不受门影响。

结果：TopicSwitchGateTest 20/20、TopicSwitchHardeningTest 11/11 全绿；domain 包全量
（BUILD SUCCESSFUL，无 failures/errors）。

## 4. 边界与纪律

- 只动 TopicSwitchGate.kt + 两个测试类；未碰 champion 包、OngoingContextSelector（F2 轨）、
  ComposerStore/BatchImport（F3 轨）；worktree 已清理（D:/wt-f1 移除），未 commit。
- 死锁解锁代价：TIME_GAP/SCENE_EMPTY 单独可定案，误报 new 且恰逢旧证据/空层的旋转
  风险由"主张+佐证"结构承担——佐证全部来自既有数据字段（确定性），非模型自证。
