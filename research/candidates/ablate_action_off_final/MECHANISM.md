# Mechanism

- Parent: `action_policy_r4_guard_final` (full runtime closure).
- `init`: parent init, then `h["flags"]["action_layer"] = False`.
- Everything else forwarded unchanged.

Effect in v6: the action block is replaced by the v5 goal block. Contracts,
public facts, model and temperature stay identical. This is the "action layer
off" branch used by F4.
