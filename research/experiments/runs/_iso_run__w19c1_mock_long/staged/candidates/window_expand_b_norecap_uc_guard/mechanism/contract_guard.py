# -*- coding: utf-8 -*-
"""Deterministic output-contract guard for window_expand_b_norecap_guard (W17b 阶段一).

机制侧适配器，不是评分器改动：
  1. 先调 `mechanism/_vendor/output_repair.py::repair`（与 action_policy_r9_slim
     的 _vendor 逐字节相同），复用其围栏/弯引号/键别名/缺槽/directions 保全；
  2. 在 repair 结果上**额外**处理 W17 合并批在 b_norecap 上观察到的漂移族：
     - 风格键模糊映射：bad_but / bad_bo / bad_boyy / bad_girl / bad 等未知键按
       与 recommended/bad_boy/playful/warm 的字符相似度（difflib ratio ≥ 0.5）
       映射到最接近的规范槽；**只在规范槽为空时映射**。映射不上的非空文本全文
       进 meta["unmapped_preserved"]，并从返回 JSON 中移除（否则非空未知键会被
       arena/protocol_public.py 判 nested_type_violation）；
     - response 少于 4 个规范槽 → 补 null 占位（不编造文本）；
     - directions > 4 → 截到 4，多出的整条进 meta["extra_directions_preserved"]；
       < 4 → 补 null；缺失 → 补 [null]*4（显式 action=no_reply 时不补）；
     - 顶层只有 action（对象）而无 response → 改名为 response；反向只在
       response 对象呈 action 形状（无风格槽、含 action/decline_reason/reason 键）
       且 action 缺失时改名，避免把正常回复改坏；
     - 空 response（四槽都空/缺失）→ meta["empty_response"]=True，原文本原样
       返回，不伪造内容；显式 action=no_reply 不算空回复。
  3. 返回的 text 只在对象相对 raw_text 实际变化时才重新 json.dumps
     (ensure_ascii=False)；不变则逐字节返回原文本。

原始模型文本仍由 ModelGateway 在守卫运行前写入 calls.json；本模块零模型调用。
未覆盖的漂移形状（不猜、不修）：response/directions/analysis 顶层类型本身写坏
（如 response 为字符串）、source_ids 指向不存在的消息、JSON 结构性残句、
空 response 的语义成因。
"""
from __future__ import annotations

import difflib
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_VENDOR = os.path.join(_HERE, "_vendor")
if _VENDOR not in sys.path:
    sys.path.insert(0, _VENDOR)

import output_repair  # noqa: E402

CANON = ("recommended", "bad_boy", "playful", "warm")
DIRECTIONS_LEN = 4
STYLE_SIM_THRESHOLD = 0.5
_STRIP_CHARS = " \t\r\n\u200b\u200c\u200d\ufeff\u00a0"
_ACTION_HINT_KEYS = ("action", "decline_reason", "reason")


def _slot_empty(v):
    return v is None or (isinstance(v, str) and not v.strip())


def _clean_key(k):
    return "".join(ch for ch in str(k) if ch not in _STRIP_CHARS).strip().lower()


def _best_style_slot(key):
    """按字符相似度找最接近的规范槽；低于阈值返回 None（不猜）。"""
    ck = _clean_key(key)
    if not ck:
        return None, 0.0
    best, best_score = None, 0.0
    for c in CANON:
        score = difflib.SequenceMatcher(None, ck, c).ratio()
        if score > best_score:
            best, best_score = c, score
    if best_score >= STYLE_SIM_THRESHOLD:
        return best, best_score
    return None, best_score


def _action_shaped(d):
    """response 对象是否其实是动作决策（无风格槽正文、含 action/decline_reason/reason 键）。"""
    if not isinstance(d, dict) or not d:
        return False
    if any(not _slot_empty(d.get(k)) for k in CANON):
        return False
    return any(k in d for k in _ACTION_HINT_KEYS)


def _add_rule(meta, name):
    if name not in meta["rules"]:
        meta["rules"].append(name)


def _map_style_keys(obj, meta):
    """未知 response 键 → 规范槽（仅槽空时）；不可映射的非空值全文进 meta 后移除。"""
    resp = obj.get("response")
    if not isinstance(resp, dict):
        return
    new_resp = {}
    changed = False
    # 先收规范槽（含 repair 补的 null 占位），再处理未知键——避免占位 null
    # 在迭代顺序上后到、把别名映射出的正文覆盖掉。
    for k, v in resp.items():
        if k in CANON:
            new_resp[k] = v
    for k, v in resp.items():
        if k in CANON:
            continue
        if _slot_empty(v):
            changed = True                      # 空别名键：删掉不丢文本
            _add_rule(meta, "empty_alias_key_dropped")
            continue
        target, score = _best_style_slot(k)
        if target is None:
            meta["unmapped_preserved"].append(
                {"key": k, "value": v, "reason": "no_style_match"})
            changed = True
            _add_rule(meta, "unmapped_style_removed")
            continue
        if not _slot_empty(new_resp.get(target)):
            # 规范槽已有非空文本：绝不覆盖，别名值全文保留到审计
            meta["unmapped_preserved"].append(
                {"key": k, "value": v, "reason": "canonical_slot_nonempty",
                 "canonical_key": target,
                 "canonical_value": new_resp.get(target)})
            changed = True
            _add_rule(meta, "unmapped_style_preserved")
            continue
        new_resp[target] = v
        meta["style_key_map"].append(
            {"from": k, "to": target, "similarity": round(score, 3)})
        changed = True
        _add_rule(meta, "style_key_fuzzy_map")
    if changed:
        obj["response"] = new_resp


def repair_and_audit(raw_text):
    """返回 (text, meta)。零模型调用；只在对象实际变化时重写文本。"""
    meta = {
        "raw_chars": len(raw_text) if isinstance(raw_text, str) else 0,
        "changed": False,
        "rules": [],
        "unparseable": False,
        "empty_response": False,
        "no_reply_action": False,
        "repair_loss": [],
        "style_key_map": [],
        "unmapped_preserved": [],
        "extra_directions_preserved": [],
        "response_slots_added": [],
        "directions_padded": 0,
        "directions_truncated": 0,
        "action_to_response": False,
        "response_to_action": False,
    }
    if not isinstance(raw_text, str):
        meta["unparseable"] = True
        _add_rule(meta, "non_string_input")
        return raw_text, meta

    try:
        obj, loss, _fixed = output_repair.repair(raw_text)
    except Exception as exc:                    # noqa: BLE001  保底：守卫绝不吞回复
        meta["unparseable"] = True
        meta["repair_error"] = str(exc)
        _add_rule(meta, "repair_exception")
        return raw_text, meta
    meta["repair_loss"] = loss
    if obj is None or not isinstance(obj, dict):
        meta["unparseable"] = True
        _add_rule(meta, "unparseable_raw_left_untouched")
        return raw_text, meta

    # 0) repair 的审计载荷不进入返回契约 JSON（全文进 meta）
    extra = obj.pop("_audit_extra_directions", None)
    if isinstance(extra, list) and extra:
        meta["extra_directions_preserved"].extend(extra)
        _add_rule(meta, "directions_overflow")
    unmapped = obj.pop("_audit_unmapped", None)
    if isinstance(unmapped, list) and unmapped:
        meta["unmapped_preserved"].extend(unmapped)
        _add_rule(meta, "unmapped_style_preserved")

    # 1) action <-> response 顶层键互改（仅当另一个不存在；反向只认 action 形状）
    resp = obj.get("response")
    act = obj.get("action")
    if isinstance(act, dict) and ("response" not in obj or obj.get("response") is None):
        obj["response"] = obj.pop("action")
        meta["action_to_response"] = True
        _add_rule(meta, "action_to_response")
    elif (isinstance(resp, dict) and _action_shaped(resp)
          and ("action" not in obj or obj.get("action") is None)):
        obj["action"] = obj.pop("response")
        meta["response_to_action"] = True
        _add_rule(meta, "response_to_action")

    # 2) 风格键漂移（W17：bad_but/bad_bo/bad_young/bad_girl/bad/...）
    _map_style_keys(obj, meta)

    no_reply_action = (isinstance(obj.get("action"), str)
                       and obj["action"].strip() == "no_reply")
    meta["no_reply_action"] = bool(no_reply_action)

    # 3) response 规范槽补 null（不编造文本；合法 no_reply 不动）
    if isinstance(obj.get("response"), dict) and not no_reply_action:
        added = []
        for k in CANON:
            if k not in obj["response"]:
                obj["response"][k] = None
                added.append(k)
        if added:
            meta["response_slots_added"] = added
            _add_rule(meta, "response_pad_null")

    # 4) directions 截到 4 / 补 null（合法 no_reply 不补缺省键）
    dirs = obj.get("directions")
    if isinstance(dirs, list):
        if len(dirs) > DIRECTIONS_LEN:
            overflow = dirs[DIRECTIONS_LEN:]
            del dirs[DIRECTIONS_LEN:]
            meta["extra_directions_preserved"].extend(overflow)
            meta["directions_truncated"] = len(overflow)
            _add_rule(meta, "directions_truncated")
        if len(dirs) < DIRECTIONS_LEN:
            pad = DIRECTIONS_LEN - len(dirs)
            dirs.extend([None] * pad)
            meta["directions_padded"] = pad
            _add_rule(meta, "directions_pad_null")
    elif dirs is None and not no_reply_action:
        obj["directions"] = [None] * DIRECTIONS_LEN
        meta["directions_padded"] = DIRECTIONS_LEN
        _add_rule(meta, "directions_added_null")
    elif dirs is not None:
        meta["directions_not_list"] = type(dirs).__name__
        _add_rule(meta, "directions_not_list")

    # 5) 空 response：只登记，不伪造内容，原文本原样返回
    resp = obj.get("response")
    if no_reply_action:
        empty_resp = False
    elif resp is None:
        empty_resp = True
    elif isinstance(resp, dict):
        empty_resp = all(_slot_empty(resp.get(k)) for k in CANON)
    else:
        empty_resp = False
        meta["response_not_object"] = type(resp).__name__
        _add_rule(meta, "response_not_object")
    meta["empty_response"] = bool(empty_resp)
    if empty_resp:
        _add_rule(meta, "empty_response_raw_kept")
        meta["changed"] = False
        return raw_text, meta

    # 6) 只有对象实际变化才重写；否则逐字节返回原文本
    try:
        raw_obj = json.loads(raw_text)
    except Exception:                           # noqa: BLE001
        raw_obj = None
    if raw_obj is not None and raw_obj == obj:
        meta["changed"] = False
        return raw_text, meta

    harness_text = json.dumps(obj, ensure_ascii=False)
    meta["harness_chars"] = len(harness_text)
    meta["changed"] = True
    _add_rule(meta, "harness_text_rewritten")
    return harness_text, meta
