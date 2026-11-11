# R5 — 话题切换门佐证独立性加固（产品侧修复，2026-11-06）

> 修复对象：`D:/LoveBrain` 分支 `closeout-20261106`（HEAD=d9ff3db），基于 W3
> `TopicSwitchGate.kt` 四信号门（SCENE_EMPTY/TIME_GAP/LABEL_CHANGE/USER_MARKER）。
> 审计发现：LABEL_CHANGE（新 topic_label ≠ 当前标签）与 `topic_status=new`
> **出自同一次模型输出**——模型一起错时 LABEL_CHANGE 反而"佐证"了错误切换
> （同源共误）。本轨把 LABEL_CHANGE 降级为模型源信号：**单独命中不可定案**。
> 本轨改动文件（仅 4 个；未触碰 PromptBuilder.kt、KnowledgeContextReader.kt、
> champion 包、BatchImport/ReplyInput/解析器）：
>
> - 修改 `app/src/main/java/com/lovebrain/app/domain/TopicSwitchGate.kt`（定案规则 + 文档）
> - 修改 `app/src/main/java/com/lovebrain/app/domain/TopicRecorder.kt`（仅决策点注释）
> - 修改 `app/src/test/java/com/lovebrain/app/domain/TopicSwitchGateTest.kt`（16 例，改 2 新 3）
> - 修改 `app/src/test/java/com/lovebrain/app/domain/TopicSwitchHardeningTest.kt`（7 例，改 2 新 1）
>
> 未 commit。worktree `D:/wt-r5`（分支 closeout-r5-tmp）编译
> （:app:compileDebugKotlin）+ 全量单测（:app:testDebugUnitTest 2917 例，
> 0 failures 0 errors）后已按纪律清理。中途一次 Gradle daemon 被并行会话
> 停止（"stop command received"），重跑即过，非本轨代码问题。

## 1. 新定案规则（TopicSwitchGate.evaluate）

W3 规则是"任一佐证信号命中即确认"，R5 收紧为：

**触发切换需要至少一个非模型源信号**（SCENE_EMPTY / TIME_GAP / USER_MARKER
任一）。LABEL_CHANGE 与 topic_status=new 同一次输出、同源共误，只能与
非模型源信号共同出现（锦上添花，不能独立定案）。

- 审计建议的规则原表述是"(a) 至少一个非模型源信号，或 (b) 两个以上信号且
  至少一个非模型源"。规则 (b) 在逻辑上被 (a) **完全蕴含**（两个以上信号且
  含非模型源 ⇒ 至少一个非模型源），故实现只保留 (a)，行为等价且更简单。
- 命中 LABEL_CHANGE 且无非模型源信号时，给出**专属否决原因**：
  `仅 LABEL_CHANGE（与 topic_status=new 同一次模型输出，同源共误，不可独立佐证）；
  scene最新条目间隔=<N>min<60min、无显式换话题标记`——仍只含信号名与分钟
  数值，不含消息正文与标签正文（util/L.kt 隐私红线不变）。
- 非 LABEL_CHANGE 的单信号否决原因文案不变（W3 兼容）。
- Decision.signals 语义不变：确认时报告全部命中信号（含锦上添花的
  LABEL_CHANGE）；否决时为空。
- 调用方 TopicRecorder.record() 代码路径零改动——门是纯函数，结论仍随
  `event.shouldRotate` 进 WAL，崩溃恢复与首提同一决定；仅更新决策点注释。

## 2. 测试更新

TopicSwitchGateTest（纯 JVM 判据单测，14→16 例）：

| 用例 | 断言 |
|---|---|
| `label change alone no longer confirms the switch`（改写原"单独确认"用例） | 仅 LABEL_CHANGE → 否决，原因含 "LABEL_CHANGE" 与 "同源"，signals 为空 |
| `label change plus time gap confirms the switch`（新增） | LABEL_CHANGE+TIME_GAP → 确认，两信号齐报 |
| `label change plus user marker confirms the switch`（新增） | LABEL_CHANGE+USER_MARKER → 确认（新鲜 scene 下，非模型源在场即可） |
| `whitespace around labels does not mask a label change`（改写） | trim 口径用 TIME_GAP 场景钉住：空白不掩盖标签变化，signals 含 LABEL_CHANGE |
| 其余 12 例（同标签单判据否决、TIME_GAP/SCENE_EMPTY/USER_MARKER 单独确认、59min 边界、解析口径、隐私红线等） | 原样通过 |

TopicSwitchHardeningTest（真实 KnowledgeRepository + 临时目录端到端，6→7 例）：

| 用例 | 断言 |
|---|---|
| `label change alone no longer triggers rotation`（改写原"标签变化佐证旋转"用例） | 仅 LABEL_CHANGE：不归档、scene/recent 原样、标签保持现状、raw_topic 为空 |
| `label change plus stale scene triggers rotation`（新增） | sceneOffset=90min + 标签变化 → 照常旋转、归档、标签更新 |
| `confirmed rotation archives … before clearing`（调整前置） | 归档完整性用例改用 sceneOffset=90min（TIME_GAP 在场）以满足新定案规则；四份被清内容先进 raw_topic.md 的断言不变 |
| 其余 4 例（同标签单判据保护、TIME_GAP 同标签切换、USER_MARKER 切换、same 状态回归） | 原样通过 |

全量单测 2917 例 0 失败——RoundCommitJournalTest 等依赖"确认切换"的既有
用例不受影响（其 setupKb scene.md 为空 → SCENE_EMPTY 非模型源仍确认）。

## 3. 已登记欠账：真实误切案例核查（未做，无数据）

审计同时建议"核查真实误切案例后完善"。当前**没有真实误切案例数据**，
本轨用例全部是构造输入，阈值（60min）与白名单（8 个换话题标记）未经真机
校准。欠账登记如下：

- **欠账**：收集真实误切案例（漏切：该切没切；误切：不该切切了），据此
  回放校准 `MIN_SCENE_GAP_MS` 与 `EXPLICIT_NEW_TOPIC_MARKERS`。
- **收集方法（基础设施已就绪）**：`TopicRecorder.record()` 在每次否决时落
  `L.w("疑似新话题被否决（topic_status=new 无佐证）：<原因>；signals=…")`，
  原因含信号名、scene 间隔分钟数与结论（不含正文）；确认切换同样可从
  raw_topic.md 归档时间戳与 topic.md 标签变更时间线对出。真机使用一段时间
  后导出日志，按"用户实际认为该切没切/不该切切了"标注案例，回放到
  TopicSwitchGate.evaluate 网格扫阈值即可校准。**在该数据到手前，不改阈值
  与白名单**，避免向构造用例过拟合。

## 4. 复测探针

```bash
git -C D:/LoveBrain worktree add -b closeout-r5-tmp D:/wt-r5 closeout-20261106
cp D:/LoveBrain/local.properties D:/wt-r5/   # sdk.dir=D:/Android/Sdk
# 回填 4 个文件（见 §0 清单）或直接在主仓改动上建 worktree
cd /d/wt-r5 && JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.3.9-hotspot" \
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
git -C D:/LoveBrain worktree remove --force /d/wt-r5 && git -C D:/LoveBrain branch -D closeout-r5-tmp
```
