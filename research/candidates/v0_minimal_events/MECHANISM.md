# v0_minimal_events — MECHANISM

> 身份：机制于 2026-10-24 做过 §4 输入公平性收口；当前 `identity_sha256` 见外部冻结记录 `refs/experiment_protocol_20261023/V0_MINIMAL_FREEZE_20261024.json`（本文件属候选树，故不在此钉死自身 hash 以免循环；父本 superset 17 文件）。
> 允许换记忆实现，但**不允许**丢掉同批用户给的目标/知识/时间来"声称只比较记忆"。

## 它做什么

| 环节 | v0 最小策略 |
|---|---|
| 记忆 | 每故事一份有序【真实消息事件】列表；只收实际发送的 HER/ME（来自各轮 input.messages），整条消息为单位去重（role|text|ts） |
| 画像/初始事实 | `ep_meta.persona` + `ep_meta.initial_kb`（平面种子事实）逐轮原样带入，作【画像/初始事实(种子)】块 |
| 目标(intent) | 本轮 `input.intent` 文本/状态/启停**进入请求**，复用父本 `build_intent_block`（【持续意图】，逐字同格式）；只取当轮 → 关闭/替换后不留旧目标 |
| 备注(note) | 本轮 `input.note` 走父本 `build_advisor_note_block`（军师备注），与 intent 分块区分：只约束这一轮怎么想/怎么回，不当已说过的话 |
| 时间 | 本轮 `input.clock` 走父本 `build_timestamp_prompt`（【当前时间】），与父本同一 clock |
| 来源 | 当前窗口每条消息给逐条 `m<i>` alias + 稳定 `source_id` + `canonical_id_map`（历史来源与当前来源分开，不用轮序号冒充消息 ID，可区分同轮各条）；历史每条含其 ts |
| 组装 | system(精简资产) + 种子(画像+初始事实) + intent + note + 预算内真实历史（UTF-16 码元 ≤1400，从新到旧整条裁；连最近一条旧史单独超限也省略、不切代理对）+ 本轮输入(最高优先，独立整段完整保留不裁) + 当前时间 |
| 生成 | 单次 gw.generate(tag=main)，与原版同一网关/同一契约 |
| 写回 | 事件只追加真实消息；**模型回复是草稿，绝不 append 进 events** |
| 生命周期 | init/run_turn/snapshot/restore，走现有运行链（snapshot 含 initial_kb/uid_by_key/uid_seq） |

## 明确不做（能力缺口，公开声明）

- 不做 topic / ongoing / scene 多层摘要与归档召回（v2 的核心能力这里没有）。
- 不做自动画像重估、跨轮反思、RoundCorrection、moment 分层。
- 不做辅助生成 / 多阶段推理。
- 没有长期"进行中事项"追踪：跨话题长事项不会像原版那样被结构化续存。

允许 v0 在真实对照里失败——这正是问题：这些复杂度买来了什么。

## 与父本/v3 的关系（可解释三方对照）

- system：v0 与 v3 用**同一份精简资产**（v0 自带副本，`tools/test_v0_minimal.py` 断言二者 `build_system_prompt` 逐字节相等，防漂移）。
- 记忆：v3=父本原多层记忆；v0=简单事件记忆。
- 于是三方可拆变量：
  - reference_product_v1 = 重 system + 原多层记忆
  - v3_system_slim = 精简 system + 原多层记忆（B 线，只动 system）
  - v0_minimal_events = 精简 system + 简单事件记忆（C 线，只动记忆）

## 真实实验假设（未证，留实跑）

> 前提：外部信息（有效 intent/知识/时间/来源）已与父本对齐——差异只剩**内部组织**，因此下面的对照才是"记忆复杂度买来了什么"的干净问题，不是输入不公平导致的伪差。

- 假设 C1：多数日常承接轮，v0 与原版/v3 在"当前输入处理 + 情绪/调侃分寸"上接近——复杂度未必增值。
- 假设 C2：在**跨话题长事项续存、相关回忆主动回访 vs 无关回忆抑制、意图切换/关闭后的跟随**上，v0（原始流水、无 topic/ongoing/scene 摘要）应弱于原版多层记忆——那正是复杂度买来的价值，v0 在此如实崩。
- 假设 C3：v0 user prompt 更短、更快、更可维护；真实 token/时延差按 §4 逐位实测（非 capture 罐头值）。
- 判据与卡：D/E 线扩题（E004–E006 修订 + E007–E012 新增）提供异题材样本；§6 工作表逐位评互动维度、程序分独立、合法不回与交付失败都进分母。允许 v0 某些故事更自然、某些崩掉，如实记录。
