# MECHANISM — RH-B-04 证据锚定排布

- 存储：无持久记忆（状态=当轮累积的可见真实聊天，进程内 handle；与 direct_baseline 同口径）
- 检索：必答/意图/时间/最近K=3条锚定尾部贴近生成点；历史与kb置中（装配顺序为唯一变量）
- 意图：意图/备注作为装配段进入提示（或作为升级门信号——见 mechanism.py 头注释）
- 上下文：必答/意图/时间/最近K=3条锚定尾部贴近生成点；历史与kb置中（装配顺序为唯一变量）
- 输出契约：轻量 {"action","recommended"}；主比较只用 recommended（normalize_v1 固定规则）
