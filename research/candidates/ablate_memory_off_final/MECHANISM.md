# Mechanism

- Parent: `action_policy_r4_guard_final` (full runtime closure).
- `init`: parent init, then `h["memory_off"] = True`.
- Everything else forwarded unchanged.

Effect in v6: `prior_events=[]` and `mem_on=False`, so no fact/tentative/update
projection. This is the "knowledge channel off" branch used by F4.
