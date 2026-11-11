# ablate_memory_off_final — F4 diagnostic ablation (not a deliverable)

This candidate exists only for the F4 component-value test. It forwards to
`action_policy_r4_guard_final` and sets `memory_off=True` after init, so v6
drops prior events and disables fact projection. The model, contract, assets
and output guard are unchanged. Do not report its results as default-config
quality.
