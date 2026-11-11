# 冒烟假机制：包装默认机制，仅在 system 尾部加标记验证接管生效
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "arena"))
import lb_mechanism as base

def init_state(assets_root, episode_meta):
    return base.init_state(assets_root, episode_meta)

def build_prompts(assets_root, case_input, state):
    out = base.build_prompts(assets_root, case_input, state)
    out["system"] = out["system"] + "\n<!-- MECH_TAKEOVER_OK -->\n"
    return out

def update_state(state, case_input, parsed_output, raw_reply):
    return base.update_state(state, case_input, parsed_output, raw_reply)
