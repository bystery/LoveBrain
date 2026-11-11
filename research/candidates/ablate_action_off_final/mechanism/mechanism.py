# candidates/ablate_action_off_final/mechanism/mechanism.py
#
# F4 诊断用消融候选，NOT a deliverable.
# Parent: action_policy_r4_guard_final. Only change: after parent init, set
# h["flags"]["action_layer"]=False. v6 then skips the action block and falls
# back to the v5 goal block; contracts/facts/model stay identical.
import importlib.util
import os

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)
_ROOT = os.path.dirname(_CAND)
_R4 = os.path.join(_ROOT, "action_policy_r4_guard_final")  # staging trigger: candidates/action_policy_r4_guard_final


def _load_r4():
    spec = importlib.util.spec_from_file_location(
        "apr4_for_ablate_action_off_final",
        os.path.join(_R4, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


r4 = _load_r4()


def init(assets_root, episode_meta, gateway, sandbox):
    h = r4.init(assets_root, episode_meta, gateway, sandbox)
    flags = h.setdefault("flags", {})
    flags["action_layer"] = False
    h["flags"] = flags
    return h


_parent_restore = r4.restore


def restore(sandbox, snap):
    h = _parent_restore(sandbox, snap)
    flags = h.setdefault("flags", {})
    flags["action_layer"] = False
    h["flags"] = flags
    return h


run_turn = r4.run_turn
snapshot = r4.snapshot
set_memory_access = r4.set_memory_access
build_system_prompt = r4.build_system_prompt
