# MECHANISM — RH-B-15 原生多路判别选一

- 存储：无持久记忆（状态=当轮累积可见聊天）
- 检索：全量注入（同 direct_baseline）；无检索机制
- 意图：意图/备注入装配段
- 上下文：单次调用生成 4 条异构候选 → 确定性验证器（长度/AI腔黑名单/JSON残渣/去重）→ 按序选第一通过者；全败回退 recommended
- 输出契约：对外仍为轻量 {"action","recommended"}（选中文本）；内部四路 trace 记入 mechanism_notes
