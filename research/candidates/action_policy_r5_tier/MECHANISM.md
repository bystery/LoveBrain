# action_policy_r5_tier — 机制说明

父本（运行时闭包）：action_policy_r2，经本目录 mechanism/mechanism.py 转发，与 e3 完全同源。
本身份唯一新增：在 r4 已修正的【单 JSON 硬约束】文本尾部追加一段“依据分层·写正文前自检”（EVIDENCED / PROPOSAL / NEEDS_CHECK 三层，NEEDS_CHECK 要求写成“分支+触发条件+谁去核实”）。由 params.json 的 mechanism_flags.assertion_tiering 开关；关掉即逐字节复现 e3 的出站文本。

边界：零额外模型调用、不改记忆写回、不改协议字段、不含任何题材词表或句子级拦截；资产文件与 e3 逐字节相同。行为假设与反证条件见 refs/experiment_protocol_20261023/final_mainline_review_20261029/F_C_HYPOTHESIS_FREEZE_20261029.md。
