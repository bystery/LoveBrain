# candidates/phase0_alpha — Harness 候选 α（Phase 0 验收 V1 之一）
# 架构：纯 dict/JSON 内存存储 + 单阶段调用（最小 v2 生命周期示范）。
# 记忆策略：全量保留（scene_chain 只增不减）——无遗忘，预期在 stale 抑制探针上暴露权衡。
# 存储形态：内存 dict，快照 = JSON bytes。

import copy
import json
import os
import sys

_ARENA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "arena")
sys.path.insert(0, _ARENA)
import lb_assembly          # noqa: E402
import lb_mechanism as v1base   # noqa: E402（复用默认写回策略）
import protocol_public            # noqa: E402


def init(assets_root, episode_meta, gateway, sandbox):
    return {"kb": copy.deepcopy(episode_meta.get("initial_kb") or {}),
            "turn": 0, "assets_root": assets_root}


def run_turn(h, ctx, gw):
    h["turn"] += 1
    ai = dict(ctx["input"])
    ai["kb"] = copy.deepcopy(h["kb"])
    assembled = lb_assembly.build_reply_prompts(ctx["assets_root"], ai)
    res = gw.generate(assembled["system"], assembled["user"], tag="main")
    parsed = protocol_public.extract_json_block(res["text"])
    v1base.update_state(h, ctx["input"], parsed, res["text"])
    return {"reply": res["text"],
            "notes": "alpha: dict/JSON storage, single-stage, full-retention memory",
            "id_map": assembled["id_map"]}


def snapshot(h):
    return json.dumps(h, ensure_ascii=False).encode("utf-8")


def restore(sandbox, snap):
    return json.loads(snap.decode("utf-8"))
