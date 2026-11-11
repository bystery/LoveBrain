# Capture 校验报告

- 结论: **PASS**（9/9 检查通过，轮位覆盖 72）

- [PASS] b_line_and_sealed_excluded — {"violations": []}
- [PASS] candidate_hash_pinned — {"drift": []}
- [PASS] direct_cot_input_equivalence — {"failures": []}
- [PASS] no_evalside_gold_leak — {"hits": []}
- [PASS] no_future_turn_leak — {"hits": []}
- [PASS] no_unexplained_gold_atom — {"suspicious": [], "note": "断言值出现在 prompt 且不在截至本轮可见历史=可疑；等义转述不在本检查能力内"}
- [PASS] four_arm_state_isolated — {"missing": [], "recorded_init_failures": []}
- [PASS] api_key_never_persisted — {"files": []}
- [PASS] attempts_consistent — {"actual": 236, "expected_from_records": 236, "planned_full": 288, "lost_to_recorded_failures": 52, "note": "mock 下每组每轮恰 1 次调用；live 下 harness 组内部附加调用会使 actual >= expected（如实记录）"}
