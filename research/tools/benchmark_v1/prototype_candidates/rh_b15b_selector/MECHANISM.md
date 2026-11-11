# MECHANISM — RH-B-15b

- 存储/检索：同 rh_b15（全量注入，无持久记忆）
- 上下文：第 1 次调用四路生成 → 确定性验证 → 第 2 次极小判别调用（近期语境+候选带标签，只回序号，temp 0.2）→ 解析失败回退 natural-first
- 输出契约：对外轻量 {"action","recommended"}；选择 trace 进 mechanism_notes
