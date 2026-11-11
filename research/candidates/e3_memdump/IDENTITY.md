# 身份

candidates/e3_memdump：action_policy_r4_guard_e3（冻结冠军 17fd2160…）的仪表化副本（W05-02 内容级 Fidelity 测量专用）。唯一改动：run_turn 包装器在父调用完成后把 h["mem"] 摘要（facts id/text80/status/ts/relation + updates）以 memdump=<json> 追加到 out["notes"]（params.json mechanism_flags.mem_dump 开关）。仅事后追加，模型调用与其输入输出与 E3 逐字节一致；flag off 行为逐字节一致。测量身份，不参赛。
