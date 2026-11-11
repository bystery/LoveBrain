# MECHANISM.md — window_expand_b_norecap

**存储**：与父本完全一致（稳定 uid 事件流，草稿永不写回）。

**检索**：历史段 3600 CU 整条预算（delta ① 保留）；**无 recap 段**（delta ② 删除）——被预算裁掉的消息不留摘录。无相关性排序、无 pin、无账本。

**意图**：与父本完全一致（build_intent_block 独立分区，只取当轮）。

**上下文**：装配顺序 = 种子 → intent → note → 历史消息 → 当前窗口 → 时钟（少 recap 一段）；notes 记 history_cu 可复核。
