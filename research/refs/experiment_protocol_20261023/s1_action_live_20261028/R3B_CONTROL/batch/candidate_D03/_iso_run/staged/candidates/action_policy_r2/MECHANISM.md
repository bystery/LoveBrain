# action_policy_r2 — MECHANISM

## 装载方式

`mechanism/mechanism.py` 是**转发型候选**：运行时 `importlib` 加载
`candidates/action_policy_r1/mechanism/mechanism.py`（r1 再加载 `candidates/v6_goal_context`），
拿到同一份 `v6g` 模块对象后，在加载期继续包装 `v6g.CAP_RULES` / `v6g._capabilities` /
`v6g._action_block`。不复制父本代码，因此不存在"复制后漂移"。
对外接口 `init/run_turn/snapshot/restore/set_memory_access/build_system_prompt` 全部转发 r1。

装配顺序（内层→外层）：
`v6g._action_block`（原始）→ r1 FIX-1 发送合同 → r1 FIX-2 目标让位 → r1 FIX-3/5 钩子与自我状态
→ **r2 FIX-6 不回表达改写**（对 r1 的旧【发送合同】文本做字符串替换，替换失败则原样保留并记
`_r2_contract_miss` 供自检发现）→ **r2 FIX-7/8 追加**。
`run_turn` 内按 `v6g._action_block(...)` 取函数（模块属性运行时查找），所以重绑有效。

## 本分支注入的提示文本（新增部分逐字）

- `CAP_RULES["收尾"] / CAP_RULES["停"]`：不回必须写成
  `"action":"no_reply"` + `"decline_reason"`，`response` 四键空串、`directions` 四条 null；
  禁止"空正文却不写 action"。
- 【不回怎么写】（仅在能力清单含 收尾/停 时出现在动作块尾部）。
- 【单 JSON 硬约束】（每轮都注入）：①顶层右花括号只在 analysis 之后出现一次；
  ②四个 style 键名逐字照抄、不含不可见字符；③directions 固定四槽不多给第五条；
  ④顶层键只允许 action/decline_reason/response/directions/analysis。
- 【回扣有据】（每轮都注入）：旧事/她说过的 X 必须能在【对话记录】或【记忆行】逐字找到。
- `assets/system_prompt/format.md`：两种合法输出、【JSON Schema（不回）】、
  directions 长度固定 4、style 键名逐字要求。
- `assets/system_prompt/core.md`：记忆段新增"回扣旧事的自检"一条。

## 继承自父本链的机制（零改动，原文见 action_policy_r1 / v6_integrated）

真实事件日志、证据记忆（fact_id/subject/relation/evidence/certainty/status/event_time/版本）、
状态机（confirmed_seed/confirmed/tentative/proposed/cancelled/completed/reopened/unverified_src/
superseded）、src_ids 与逐字 quote 硬校验、上下文预算投影（当前窗口永不挤出；500/300/220 CU）、
意图作用域（ACTIVE/ADJUSTED 生效，CLOSED/COMPLETED/EXPIRED/PAUSED 不生效，关闭不复活，
已确认约定保留）、快照/恢复、promise 收敛机制、r1 五项修复（FIX-1..FIX-5）。

## 消融

`params.json.mechanism_flags` 只列本候选真正读取的三项；全 false = 逐字节回到 r1 行为。
r1 的五项 flag 由 r1 自己的 params.json 读取，在本文件里增删无效（避免伪消融）。
