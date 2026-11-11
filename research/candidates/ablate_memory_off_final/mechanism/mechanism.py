# candidates/ablate_memory_off_final/mechanism/mechanism.py
#
# F4 诊断用消融候选，NOT a deliverable.
# Parent: action_policy_r4_guard_final. Only change: after parent init, set
# h["memory_off"]=True (v6 reads this flag and drops prior_events; fact
# projection is disabled because mem_on becomes False in v6_integrated).
# This is the declared "knowledge channel off" branch. It must not be reported
# as a default-config result; see F4 protocol.
import importlib.util
import os

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)
_ROOT = os.path.dirname(_CAND)
_R4 = os.path.join(_ROOT, "action_policy_r4_guard_final")  # staging trigger: candidates/action_policy_r4_guard_final


def _load_r4():
    spec = importlib.util.spec_from_file_location(
        "apr4_for_ablate_memory_off_final",
        os.path.join(_R4, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


r4 = _load_r4()


def init(assets_root, episode_meta, gateway, sandbox):
    h = r4.init(assets_root, episode_meta, gateway, sandbox)
    h["memory_off"] = True
    return h


_parent_restore = r4.restore


def restore(sandbox, snap):
    h = _parent_restore(sandbox, snap)
    h["memory_off"] = True
    return h


run_turn = r4.run_turn
snapshot = r4.snapshot
set_memory_access = r4.set_memory_access
build_system_prompt = r4.build_system_prompt
