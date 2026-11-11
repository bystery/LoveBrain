# ablate_action_off_final — F4 diagnostic ablation (not a deliverable)

This candidate exists only for the F4 component-value test. It forwards to
`action_policy_r4_guard_final` and sets `h["flags"]["action_layer"]=False`
after init, so v6 skips the action block and falls back to the v5 goal block.
The model, contract, facts and output guard are unchanged. Do not report its
results as default-config quality.
