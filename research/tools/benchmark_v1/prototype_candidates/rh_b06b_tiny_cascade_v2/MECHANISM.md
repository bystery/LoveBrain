# MECHANISM — RH-B-06b 小步快答两档级联v2

- 存储：无持久记忆（状态=当轮累积可见聊天）
- 检索：v2修复盲答Bug（当轮消息入历史）+第一档消费note/intent+升级门只看草稿内容信号
- 意图：见 mechanism.py 头注释
- 上下文：v2修复盲答Bug（当轮消息入历史）+第一档消费note/intent+升级门只看草稿内容信号
- 输出契约：轻量 {"action","recommended"}；主比较只用 recommended
