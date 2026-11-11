# MECHANISM — RH-B-11a 稳定前缀排布(A臂)

- 存储：无持久记忆（状态=当轮累积可见聊天）
- 检索：内容同 direct_baseline；稳定块(persona/kb)前置→同episode多次调用共享前缀→端点缓存应命中
- 意图：见 mechanism.py 头注释
- 上下文：内容同 direct_baseline；稳定块(persona/kb)前置→同episode多次调用共享前缀→端点缓存应命中
- 输出契约：轻量 {"action","recommended"}；主比较只用 recommended
