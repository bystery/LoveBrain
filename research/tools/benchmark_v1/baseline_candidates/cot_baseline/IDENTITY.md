# COT baseline — 身份卡

- 组别：B · CoT-style（Benchmark V1 Phase 1 新建轻量适配器）
- 假设：在 DIRECT 之上只加一行固定思考提示，不引入任何 LoveBrain 机制/检索/记忆/示例。
- 机制：与 DIRECT 逐字同构（自包含副本），唯一差异=system 末尾 COT_INSTRUCTION
  （指导书原文，版本与哈希随 mechanism.py 固定）。
- 原生推理纪律：出站白名单无 reasoning 开关通道——四组服务端配置相同，差异只能来自这一行。
- 输出契约：与 DIRECT 相同 `{"action","recommended"}`；不展示思考过程。
- 禁止：同 DIRECT。
