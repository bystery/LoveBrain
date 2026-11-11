# -*- coding: utf-8 -*-
"""Benchmark V1 · 输出归一化（Phase 1）。

统一四组首选回复抽取规则（指导书 §3.5），固定、不可挑选：
- DIRECT/COT：primary_text ← recommended（轻量契约 {"action","recommended"}）；
- LEGACY/CURRENT：primary_text ← response.recommended（LoveBrain 契约；decline 走 no_reply）；
- 禁止看哪个备选好挑哪个：其他风格/方向只记 secondary_channels 计数，不参与主比较；
- 拒答/合法不回 = action=no_reply 独立状态，空字符串不得当作高质量回复；
- parse 失败/契约失败如实落 error_type，不补救、不重试。

版本：normalize_v1（Phase 1 冻结；改规则必须升版本并声明）。
"""
import json
import os
import re

NORMALIZE_VERSION = "normalize_v1"

# 2026-11-11 RESEARCH-P1 加法式扩展（宪章 C3 允许的必要兼容修复，不改既有臂行为）：
# 轻量契约 {"action","recommended"} 解析面从硬编码 ("DIRECT","COT") 扩到注册的
# 研究原型臂。DIRECT/COT/LEGACY/CURRENT 的解析路径逐字节不变。
LIGHT_PARSE_ARMS = frozenset({
    "DIRECT", "COT",
    "rh_b04_anchored_layout", "rh_b06_tiny_cascade", "rh_b09_extractive_slim",
    "rh_b06b_tiny_cascade_v2", "rh_b11a_stable_prefix", "rh_b11b_variable_first",
    "rh_b15_multicast_validator", "rh_b15b_selector", "rh_b12_invalidation_track", "rh_b17_memorycard", "rh_pipe_v1",
    "rh_b18_refine_select",
    "rh_b19_strategy_driven",
    "rh_b20_fewshot",
    "rh_b21_fewshot_multicast",
    "rh_converge",
})

_FENCE_RE = re.compile(r"```(?:json)?\s*(\{.*?\})\s*```", re.DOTALL)


def _first_json_object(text):
    """宽容提取首个 JSON 对象（含 ``` 围栏），不修补内容。"""
    if not isinstance(text, str) or not text.strip():
        return None
    m = _FENCE_RE.search(text)
    cand = m.group(1) if m else text
    try:
        obj = json.loads(cand)
        return obj if isinstance(obj, dict) else None
    except Exception:
        try:
            start = cand.index("{")
            end = cand.rindex("}")
            obj = json.loads(cand[start:end + 1])
            return obj if isinstance(obj, dict) else None
        except Exception:
            return None


def normalize_light(raw_text, scoring=None):
    """DIRECT/COT 轻量契约 → 归一化字段。"""
    obj = _first_json_object(raw_text)
    row = {"action": None, "primary_text": None, "primary_source": "recommended",
           "parse_ok": False, "contract_ok": False, "error_type": None,
           "secondary_channels": 0}
    if obj is None:
        row["error_type"] = "parse"
        if isinstance(raw_text, str) and raw_text.strip() == "":
            row["error_type"] = "empty"
        return row
    row["parse_ok"] = True
    action = obj.get("action")
    rec = obj.get("recommended")
    if action not in ("reply", "no_reply"):
        row["error_type"] = "contract"
        row["action"] = None if action is None else str(action)
        return row
    row["action"] = action
    if action == "reply":
        if not isinstance(rec, str) or not rec.strip():
            row["error_type"] = "contract"
            return row
        row["primary_text"] = rec
        row["contract_ok"] = True
    else:
        if rec not in (None, ""):
            row["error_type"] = "contract"   # 不回动作与非空正文并存
            return row
        row["primary_text"] = None
        row["contract_ok"] = True
    return row


def normalize_harness(raw_text, scoring=None):
    """LEGACY/CURRENT LoveBrain 契约 → 归一化字段。

    固定规则：primary_text ← response.recommended（允许 null=decline/no_reply）。
    其他三风格与四方向只计数（secondary_channels），绝不参与主比较。
    """
    obj = _first_json_object(raw_text)
    row = {"action": None, "primary_text": None, "primary_source": "response.recommended",
           "parse_ok": False, "contract_ok": False, "error_type": None,
           "secondary_channels": 0}
    if obj is None:
        row["error_type"] = "parse"
        if isinstance(raw_text, str) and raw_text.strip() == "":
            row["error_type"] = "empty"
        return row
    row["parse_ok"] = True
    resp = obj.get("response")
    if not isinstance(resp, dict):
        row["error_type"] = "contract"
        return row
    rec = resp.get("recommended")
    styles = [resp.get(k) for k in ("bad_boy", "playful", "warm")]
    dirs = obj.get("directions") if isinstance(obj.get("directions"), list) else []
    row["secondary_channels"] = sum(1 for s in styles if isinstance(s, str) and s.strip()) \
        + sum(1 for d in dirs if isinstance(d, str) and d.strip())
    decline_reason = obj.get("decline_reason")
    if (rec is None or (isinstance(rec, str) and not rec.strip())) and \
            isinstance(decline_reason, str) and decline_reason.strip():
        row["action"] = "no_reply"
        row["primary_text"] = None
        row["contract_ok"] = True
        return row
    if not isinstance(rec, str) or not rec.strip():
        row["error_type"] = "contract"
        return row
    if isinstance(decline_reason, str) and decline_reason.strip():
        # action=reply 与非空 decline_reason 并存 = 契约冲突（评分器同规则）
        row["error_type"] = "contract"
        row["action"] = "reply"
        row["primary_text"] = rec
        return row
    row["action"] = "reply"
    row["primary_text"] = rec
    row["contract_ok"] = True
    return row


def scoring_flags(scoring_path):
    """读 scoring.json → (contract_veto, score10, gold_flags)（只读，不改判）。"""
    if not scoring_path or not os.path.isfile(scoring_path):
        return None, None, {}
    try:
        with open(scoring_path, "r", encoding="utf-8") as f:
            s = json.load(f)
    except Exception:
        return None, None, {}
    veto = s.get("veto")
    return (veto if veto else None), s.get("score10"), {
        k: s.get(k) for k in ("gold", "assertion_checks", "expect_decline_checks")
        if k in s}


def normalize_turn(arm_id, episode_id, case_id, turn_dir):
    """从 turn 目录产出指导书 §3.5 形状的归一化行。arm 决定固定抽取规则。"""
    raw_path = os.path.join(turn_dir, "reply_raw.txt")
    raw = None
    if os.path.isfile(raw_path):
        with open(raw_path, "r", encoding="utf-8") as f:
            raw = f.read()
    if raw is None:
        row = {"action": None, "primary_text": None,
               "primary_source": None, "parse_ok": False, "contract_ok": False,
               "error_type": "no_output", "secondary_channels": 0}
    elif arm_id in LIGHT_PARSE_ARMS:
        row = normalize_light(raw)
    else:
        row = normalize_harness(raw)
    veto, score10, gold_flags = scoring_flags(os.path.join(turn_dir, "scoring.json"))
    row["scorer_veto"] = veto
    if arm_id in ("LEGACY", "CURRENT"):
        # harness 臂：权威评分器 veto 即契约失败（同一契约）
        if row["error_type"] is None and veto:
            row["contract_ok"] = False
            row["error_type"] = "veto:%s" % veto
    else:
        # 轻量臂（DIRECT/COT）：harness 评分器协议键不含 {action, recommended} 形状，
        # 其 veto 不适用（记录为 scorer_veto 供审计）；契约判据=归一化规则（指导书 §3.5）
        pass
    manifest = {}
    mp = os.path.join(turn_dir, "manifest.json")
    if os.path.isfile(mp):
        try:
            with open(mp, "r", encoding="utf-8") as f:
                manifest = json.load(f)
        except Exception:
            pass
    real_case_id = manifest.get("case_id") or case_id   # 目录名≠case_id（turn_NN_ 前缀）
    row.update({
        "arm_id": arm_id, "episode_id": episode_id, "case_id": real_case_id,
        "turn_dir": turn_dir,
        "score10": score10, "veto": veto, "gold_flags": gold_flags,
        "latency_ms": manifest.get("latency_ms"),
        "tokens": manifest.get("tokens"),
        "gateway_calls": manifest.get("gateway_calls"),
        "model": manifest.get("model"),
        "adapter": manifest.get("adapter"),
        "normalize_version": NORMALIZE_VERSION,
    })
    return row


def iter_run_dir(run_dir, arms=("DIRECT", "COT", "LEGACY", "CURRENT")):
    """遍历 <run_dir>/<arm>/<episode>/*/ turn 目录，yield 归一化行。"""
    for arm in arms:
        arm_dir = os.path.join(run_dir, arm)
        if not os.path.isdir(arm_dir):
            continue
        for ep in sorted(os.listdir(arm_dir)):
            ep_dir = os.path.join(arm_dir, ep)
            if not os.path.isdir(ep_dir):
                continue
            for td in sorted(os.listdir(ep_dir)):
                turn_dir = os.path.join(ep_dir, td)
                if not os.path.isdir(turn_dir) or td.startswith("_"):
                    continue
                if not os.path.isfile(os.path.join(turn_dir, "reply_raw.txt")):
                    continue
                yield normalize_turn(arm, ep, td, turn_dir)
