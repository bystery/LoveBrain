# action_policy_r4_guard_e3

- 2026-10-29 F5 修复身份；父本 `action_policy_r2`（运行时闭包，不复制代码）。
- 与冻结身份 `action_policy_r4_guard_final`（9b8f4e1e…）的唯一代码差异：vendored `output_repair.py` 新增 E3；
  该冻结目录已按 `FINAL_CANDIDATE_FREEZE_20261029.json` 逐字节还原，两个身份同时可复现。
- 不选 r3：F0 已证明 D01_T08/D02_T06/D02_T08 的“过度沉默”不成立，FIX-9 的 2→0 是重贴标签，不是实测收益。
- 机制 DNA：r2 发送合同/动作层 + 修正后的嵌套 JSON 规范 + 确定性输出守卫 + 关系主体证据守卫。
- 输出守卫在父机制解析 analysis 之前运行；原文仍由 ModelGateway 写入 calls.json，规范结果才交给记忆写回。
- 守卫复用 `tools/output_repair.py` 与 `tools/normalize_reply_contract.py`（vendored 原文），不新增第三套解析器。
- 不改评分器、rubric、题面、卡；不增加模型调用；旧 r2/r3 身份原样保留。
- 待验证：F1 历史盲审、F3 新材料、F4 组件消融；未证前不宣称优于父本。
