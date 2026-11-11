# action_policy_r3 — MECHANISM

## 装载方式

`mechanism/mechanism.py` 是**转发型候选**：运行时 `importlib` 加载
`candidates/action_policy_r2/mechanism/mechanism.py`（r2 再加载 r1，r1 再加载
`candidates/v6_goal_context`），拿到同一条链真正使用的 `v6g` 模块对象后，在加载期继续重绑
`v6g._signal` / `v6g._capabilities` / `v6g._action_block` 与 `v6g.CAP_RULES`。
不复制父本代码，因此不存在"复制后漂移"。
对外接口 `init/run_turn/snapshot/restore/set_memory_access/build_system_prompt` 全部转发 r2。

依赖闭包（staging 按机制源码里的 `candidates/<名>` 字面量拷贝父本树）：
`action_policy_r3 → action_policy_r2 → action_policy_r1 → v6_goal_context → v5 → base → rc`。
本文件只在 `_APR2` 路径常量与 staging trigger 注释里出现 `candidates/action_policy_r2`
一个字面量，**不写其他候选目录名**——写了会被扫描当成真实依赖、多拷一棵树，闭包就与父本不可比。

装配顺序（内层→外层）：
`v6g._action_block`（原始菜单）→ r1 FIX-1 发送合同 → r1 FIX-2 目标让位 → r1 FIX-3/5 钩子与自我状态
→ r2 FIX-6 不回表达改写 → r2 FIX-7/8 追加
→ **r3：绕过 r2 的 nr 触发逻辑，直接从 r1 最外层块 `apr2._r2_orig_block` 取文本**，
①替换"她已在收尾…"说明行、②用正则摘掉父本的【发送合同】整段、③换上 r3 自己的合同，
再逐字追加 r2 的 `_HYGIENE`/`_CALLBACK` 常量。

为什么要绕过而不是继续在 r2 文本上做字符串替换：r2 的【不回怎么写】触发条件是
`caps ∩ {收尾, 停} ≠ ∅`，而 r3 里 `收尾` 回归成"发一句"的普通动作——沿用父本触发条件
就会在"该回一句"的轮次里继续贴"本轮不回怎么写"，正是要修的那类语义打架。
绕过之后，r3 的菜单里只有 `停` 会触发不回表达，`收尾` 触发"正文非空"合同。

## 三个钩子的具体行为

`_r3_signal`（包 v6 原 `_signal`，逐字保留原字段）新增四个字段：
`waiver`（她最近一条是否给了免回授权/指定下次由她开口）、
`result_report`（她最近三条里是否有刚落地的成果）、
`open_ask`（她最近一条是否在问我要主意）、
`silence_ok = may_decline and waiver and not result_report and not open_ask`。

`_r3_capabilities`：以 **v6 原始菜单**（`apr1._apr_orig_caps`，未含 r1/r2 的剔除分支）为底，
`停` 不在 `silence_ok` 时一律摘掉；`收尾` 作为发消息动作保留（含 `may_decline=false` 的
忙碌/暂缓/收尾轮——那正是 r1/r2 泄漏不回的地方）。

`_r3_action_block`：见上面的装配顺序；另在 `result_report or open_ask` 时追加【必须接住】。
父本文案若漂移（收尾说明行找不到、【发送合同】段落数异常、遗留"正文必须完全为空/不发任何消息"
残留）记入 `_R3_CONTRACT_MISS`，由 `tools/probe_r3_fix9.py` 断言为空——不静默放行。

## 继承自父本链的机制（零改动）

真实事件日志、证据记忆（fact_id/subject/relation/evidence/certainty/status/event_time/版本）、
状态机、src_ids 与逐字 quote 硬校验、上下文预算投影（当前窗口永不挤出）、意图作用域
（ACTIVE/ADJUSTED 生效，COMPLETED/CLOSED 等不生效，关闭不复活，已确认约定保留）、
快照/恢复、promise 收敛、r1 的 FIX-2/3/4/5、r2 的 FIX-6/7/8。
r1 的 FIX-1 在本分支被**部分回滚**：删掉的是"把 收尾 改写成不回动作 + 正文必须为空"这部分，
保留的是"may_decline=false 的轮次不得静默"这一意图（改由 FIX-9-C 的泛化剔除实现，覆盖更多 hint）。

## flag

`params.json.mechanism_flags.no_reply_scope_narrowing_fix=false` 时，`_capabilities`/`_action_block`
直接调用 r2 当时生效的函数、`_signal` 不重绑 → 行为逐字节回到 action_policy_r2。
改 flag 即改 params.json → 身份哈希变，消融必须是新身份，不允许同身份偷换行为。

## 验证状态

- 机制级（零调用）：`tools/probe_r3_fix9.py` 18 位点全绿（详见 `IDENTITY.md`）。
- 轨迹级（真实生成）：待 BATCH-R3-DECLINE-20261028 回填。未回填前不得声称已修好。
