# MECHANISM — window_expand_b_norecap_uc_guard

四决策（相对父本 window_expand_b_norecap_uc 逐字一致，唯一差异点=输出契约守卫，见文末）。

## 存储
同父本：`events` 有序真实消息 `{uid, role, text, ts, turn}`（turn 字段仅用于未定段引用标注，
不进入 prompt 的历史行文本）；模型回复不写入 events。守卫不新增持久状态（审计只落 sandbox）。

## 检索
同父本：历史整条消息按 UTF-16 预算 3600 CU 从新到旧保留；无 recap、无相关性排序、无 pin、无账本。

## 意图
同父本：沿用共享 `build_intent_block`，本轮 intent 缺省/关闭即空，不留旧目标。

## 上下文组织
同父本：种子（画像/初始知识）→ intent → note → 历史消息（3600 CU 整条预算）→ 未定事项段
（≤120 CU、逐字引用，无未定项则整段不出现，prompt 与父本逐字节相同）→ 当前窗口 → 时钟。

## guard 差异点（唯一变异）
`mechanism/contract_guard.py::repair_and_audit(raw)`（照搬 window_expand_b_norecap_guard）
在 `gw.generate` 返回后、reply 返回前运行；prompt 构造完全不动，守卫只作用于返回文本：
- 先调逐字节复制的 `_vendor/output_repair.py`（围栏 / 弯引号 / 别名 / 缺槽 / directions 保全）；
- 补 W17 漂移族：未知风格键按字符相似度（difflib ≥ 0.5）映射到规范槽（仅槽空时，绝不覆盖
  非空正文）、response 少 4 槽补 null、directions 截 4 补 null、action↔response 顶层互改、
  空 response 只标 `empty_response` 不伪造；
- 变化时才重写 JSON（ensure_ascii=False），否则逐字节返回原文本；被移除/截断的文本全文进
  meta，并落 sandbox `contract_guard_audit.jsonl`；notes 增 `guard_changed` / `guard_rules`；
  守卫异常时降级为原样回复。raw 原文仍由 gateway 在守卫前写 calls.json；零模型调用。

## 边界
不加 suppression、不恢复 recap；不读网络；不新增工具白名单；不改输出契约声明；
`snapshot/restore` 沿用父本字段。
