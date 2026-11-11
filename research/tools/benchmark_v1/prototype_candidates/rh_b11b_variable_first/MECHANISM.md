# MECHANISM — RH-B-11b 易变前缀排布(B臂)

- 存储：无持久记忆（状态=当轮累积可见聊天）
- 检索：同内容；易变块(history)前置→前缀互异→缓存应不命中（对照组）
- 意图：见 mechanism.py 头注释
- 上下文：同内容；易变块(history)前置→前缀互异→缓存应不命中（对照组）
- 输出契约：轻量 {"action","recommended"}；主比较只用 recommended
