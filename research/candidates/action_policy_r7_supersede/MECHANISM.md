# action_policy_r7_supersede — 机制说明

依据：W05-02_FIDELITY_CLOSURE_20261031.md §3——取代双态（原位更新 ✓ / 并行添加旧值残留 ⚠，f_0009||f_0025 实证）；r1 引擎 STATUS_DEAD 含 superseded 但无赋值路径。
行为假设：①F1 重放中并行残留对（E067 base、E061 若干）被降级；②capture 30/31 不回退（新 fact 不受影响）；③种子事实零降级；④合法历史叙述（旧单归档等）不误杀——线索词+重叠双门控。反证条件：重放 capture 回退或种子被降级即撤回。
边界：不改提示词；不改 _apply_updates；pass 异常静默跳过（不阻塞回合）；模型调用与 E3 完全相同。
