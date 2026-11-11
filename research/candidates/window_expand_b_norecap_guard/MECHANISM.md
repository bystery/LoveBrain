# MECHANISM.md — window_expand_b_norecap_guard

**存储**：与父本完全一致（稳定 uid 事件流；模型回复是草稿，永不写回事件流）。

**检索**：与父本完全一致（历史段 3600 CU 整条预算；无 recap、无相关性排序、无 pin、无账本）。本代唯一变异不触碰检索。

**意图**：与父本完全一致（build_intent_block 独立分区，只取当轮有效 intent）。

**上下文**：与父本完全一致（装配顺序 = 种子 → intent → note → 历史消息 → 当前窗口 → 时钟；notes 记 history_cu）。

**输出契约守卫（唯一变异）**：`mechanism/contract_guard.py::repair_and_audit(raw)` 在 `gw.generate` 之后、reply 落盘之前运行。先调用逐字节复制的 `_vendor/output_repair.py`（围栏 / 弯引号 / 别名 / 缺槽 / directions 保全），再补 W17 漂移族：未知风格键按字符相似度（difflib ≥ 0.5）映射到规范槽（仅槽空时，绝不覆盖非空正文）、directions 截 4 补 null、action↔response 顶层互改、空 response 只标 `empty_response` 不伪造；被移除/截断的文本全文进 meta 并落 sandbox 审计 JSONL。不改 prompt 构造、历史预算、intent/note 装配；raw 原文仍由 gateway 在守卫前写 calls.json。
