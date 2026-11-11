# -*- coding: utf-8 -*-
r"""tools/output_repair.py — 从**保留的原始答卷文本**做可交付性修复测算（零调用、只读）。

为什么需要它：S1/S2 真实批里三组协议故障（键名被写坏、directions 多给一条、
directions 之后提前闭合顶层 }）都在**正文其实完好**的情况下让整轮变成 json_schema_broken。
评分器（arena/lb_scorer.py）按原始协议记账是对的——P 列必须记失败；
但真实产品消费者面对的是"这条回复能不能发给用户"，那是 C 列。两列不能互相冒充。

本模块只做一件事：给一份原始文本，返回
  - 修复后的对象（修不动就 None）
  - 每一条修复动作的留损记录（改了什么、动了几个字符、丢了什么）
绝不覆盖任何原始答卷文件；调用方只拿它算 C 列，不改 P 列。

## 继承关系（2026-10-28 晚核销：本文件是扩展，不是并行重写）

解析与键/directions 保全**直接复用** `tools/normalize_reply_contract.py`（P06/P07 已在用的归一器）：
  I1 解析兜底（继承）：去 markdown 围栏 → 结构弯/全角引号按序换直引号重试 → 抽取最外层对象。
  I2 键别名（继承）：别名表映射；规范槽空才安全映射，**双方非空时别名值保留到
     obj["_audit_unmapped"]（全文，不截断）**。
  I3 directions 保全（继承）：少于 4 条补 null 并登记缺槽；多于 4 条把**多出的整条**
     保留到 obj["_audit_extra_directions"]，不截断、不静默丢弃。
  I4 缺槽登记（继承）：response 缺键补 null 并写 missing_slots。

本文件新增的只有三项（**明确标为扩展**）：
  E1 通用键名归一：去掉键名里的空格/不可见字符后再与四个规范名比对（旧别名表没覆盖
     'bad_bo​y' 这类夹零宽字符的写法）；规范槽非空时同样把别名值全文保留到 _audit_unmapped。
  E2 提前闭合重拼：json.loads 报 Extra data、且第一段只缺 analysis 时，删掉多余的顶层 }
     把剩余 ,"analysis":{...} 原样接回（实测样本：AK02_T01，raw_decode 断点 char330）。
  E3 多余包裹对象拆开：模型把最后一个顶层键包进多一层花括号——形如
     `...,"directions":[...],"analysis":{...}` 被写成 `...,"directions":[...],{"analysis":{...}}`，
     实测样本 N01_T05 还同时把 directions 数组的闭合 `]` 打成 `}`。E3 只在同时满足
     （a）继承解析与 E2 都失败、（b）逗号后紧跟的 `{` 里第一眼就是 `"analysis":`、
     （c）该对象的配对 `}` 之后全文只剩空或一个 `}`、（d）至多 1 处 `[` 被 `}` 收尾
     （出现第 2 处即放弃）、（e）重拼后 json.loads 成功且顶层同时含
     response/directions/analysis 这五条时，才只动括号（1 个 } → ]，删 1 对多余 { }），
     文字与键值一律不碰。任一条不满足就原样返回失败。

刻意不修：
  R4 不回表达：response 四键全空串 + directions 全 null 且无 action → **不在这里补 action**。
     "不回"是模型的决策表达，只能由生成侧合同写清（见 action_policy_r2 FIX-6），
     消费侧替它补一个动作字段等于把候选没说的话替它说了。此处只登记
     no_reply_expression_missing，交分列报告。

留损记录**一律保存原文全文**（旧版本曾把冲突文本截到 80/120 字，与继承来的"全文保全"相矛盾，
2026-10-28 晚核销时删除截断）。
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import normalize_reply_contract as nrc      # noqa: E402  旧归一器（继承，不复制）

CANON = ("recommended", "bad_boy", "playful", "warm")
_DEC = json.JSONDecoder()
_INVISIBLE = {"\u200b", "\u200c", "\u200d", "\ufeff", "\u00a0", " ", "\t", "\r", "\n"}


def _canon_key(k):
    """E1：把被写坏的 style 键名映射回规范名（去不可见字符后的全等/前缀/包含）。"""
    if not isinstance(k, str):
        return None
    if k in CANON:
        return k
    cleaned = "".join(ch for ch in k if ch not in _INVISIBLE).lower()
    if cleaned in CANON:
        return cleaned
    for c in CANON:
        if cleaned and (c.startswith(cleaned) or cleaned.startswith(c)):
            return c
    return None


def _normalize_one_mismatched_closer(text):
    """字符串状态感知的栈扫描：把"在 [ 内部用 } 收尾"的那一个括号改成 ]。

    只允许修 1 处；出现第 2 处立即放弃（返回 None），避免变成通用 JSON 重写器。
    """
    stack, out, n = [], list(text), 0
    in_str = esc = False
    for idx, ch in enumerate(text):
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch in "[{":
            stack.append(ch)
        elif ch in "]}":
            if not stack:
                return None, 0
            op = stack[-1]
            need = "]" if op == "[" else "}"
            if ch == need:
                stack.pop()
                continue
            if op == "[" and ch == "}":          # 唯一的窄情形：[ 被 } 收尾
                out[idx] = "]"
                stack.pop()
                n += 1
                if n > 1:
                    return None, n
                continue
            return None, n                       # 其它错配一律不猜
    return "".join(out), n


def _try_unwrap_stray_object(raw_text):
    """E3：拆开 `...],{"analysis":{...}}` 里多余的一层花括号。

    实测形态（N01_T05）同时带一个错配闭合符：directions 数组用 `}` 收尾。
    因此 E3 = 至多 1 处错配闭合符归一 + 至多 1 对多余包裹括号，且必须
    （a）包裹对象以 `"analysis":` 开头、（b）包裹的配对 `}` 之后全文只剩空或一个 `}`、
    （c）重拼后 json.loads 成功、（d）顶层对象同时含 response/directions/analysis。
    任何一条不满足就返回 None（不改任何字符）。
    返回 (new_text, actions[list of dict]) 或 (None, None)。
    """
    for i, ch in enumerate(raw_text):
        if ch != ",":
            continue
        j = i + 1
        while j < len(raw_text) and raw_text[j] in " \n\r\t":
            j += 1
        if j >= len(raw_text) or raw_text[j] != "{":
            continue
        if not re.match(r'\{\s*"analysis"\s*:', raw_text[j:j + 32]):
            continue
        try:
            val, end = _DEC.raw_decode(raw_text, j)
        except Exception:                            # noqa: BLE001
            continue
        if not isinstance(val, dict) or "analysis" not in val:
            continue
        tail = raw_text[end:].strip()
        if tail not in ("", "}"):                    # 只允许末尾还剩一个顶层右括号
            continue
        prefix = raw_text[:i + 1]
        fixed_prefix, n_fix = _normalize_one_mismatched_closer(prefix)
        if n_fix > 1 or fixed_prefix is None:
            continue
        actions = []
        if n_fix == 1:
            actions.append({"op": "mismatched_closer_normalized",
                            "detail": "directions 数组的 } 归一为 ]", "chars_moved": 1})
        actions.append({"op": "stray_wrapper_removed",
                        "cut_index": j, "detail": "删掉 analysis 外面多的一对 { }",
                        "chars_moved": 2})
        inner = raw_text[j + 1:end - 1]      # 去掉包裹对象的左右两个花括号
        cand = fixed_prefix + inner + (tail if tail else "}")
        try:
            obj = json.loads(cand)
        except Exception:                            # noqa: BLE001
            continue
        if (isinstance(obj, dict) and {"response", "directions", "analysis"}
                                .issubset(obj.keys())):
            return cand, actions
    return None, None


def _parse_with_inheritance(raw_text, loss):
    """I1 解析兜底 →（失败时）E2 提前闭合重拼 → E3 多余包裹对象 → 再走一次 I1。返回 obj 或 None。"""
    obj, repairs = nrc.repair_json(raw_text)
    for r in repairs:
        if not str(r).startswith("unparseable"):
            loss.append({"rule": "I1_inherited_parse", "detail": r})
    if obj is not None:
        return obj

    # E2：提前闭合重拼（只在继承解析失败后启用）
    try:
        _head, end = _DEC.raw_decode(raw_text)
        tail = raw_text[end:]
        if tail.startswith(","):
            joined = raw_text[:end - 1] + tail
            cand, end2 = _DEC.raw_decode(joined)
            if end2 == len(joined) and isinstance(cand, dict):
                loss.append({"rule": "E2_premature_top_level_brace",
                             "cut_index": end - 1, "bytes_moved": 1,
                             "detail": "删掉 directions 之后多余的顶层 }，analysis 原样接回"})
                # 重拼后的文本再走一遍继承解析（覆盖弯引号/围栏等情况）
                obj2, repairs2 = nrc.repair_json(joined)
                if obj2 is not None:
                    for r in repairs2:
                        if not str(r).startswith("unparseable"):
                            loss.append({"rule": "I1_inherited_parse_after_E2", "detail": r})
                    return obj2
                return cand
    except Exception as e:                  # noqa: BLE001
        loss.append({"rule": "E2_failed", "detail": str(e)})

    # E3：多余包裹对象（只在 I1 与 E2 都失败后启用；只动括号，不改任何文字）
    unwrapped, actions = _try_unwrap_stray_object(raw_text)
    if unwrapped is not None:
        loss.append({"rule": "E3_stray_wrapper_object_removed",
                     "actions": actions,
                     "detail": "拆开 directions 之后多出来的一层 { }，analysis 回到顶层"})
        obj3, repairs3 = nrc.repair_json(unwrapped)
        if obj3 is not None:
            for r in repairs3:
                if not str(r).startswith("unparseable"):
                    loss.append({"rule": "I1_inherited_parse_after_E3", "detail": r})
            return obj3
        try:
            return json.loads(unwrapped)
        except Exception:                   # noqa: BLE001
            pass

    loss.append({"rule": "unparseable", "detail": "；".join(str(x) for x in repairs)})
    return None


def repair(raw_text):
    """返回 (repaired_obj_or_None, loss_log[list of dict], repaired_json_str_or_None)。"""
    loss = []
    if not isinstance(raw_text, str) or not raw_text.strip():
        return None, [{"rule": "empty_raw", "detail": "无原始文本"}], None

    obj = _parse_with_inheritance(raw_text, loss)
    if obj is None:
        return None, loss, None
    if not isinstance(obj, dict):
        return None, loss + [{"rule": "not_object", "detail": type(obj).__name__}], None

    # I2/I3/I4：继承旧归一器的键别名、全文保全、缺槽登记
    obj, n_rep, n_unmapped, n_missing = nrc.normalize(obj)
    if n_rep:
        loss.append({"rule": "I2_I3_I4_inherited_normalize", "actions": n_rep,
                     "unmapped_style_keys": n_unmapped,
                     "missing_slots": n_missing})

    # E1：旧别名表没覆盖的键名（夹零宽字符、截断等）再归一一遍；
    # 与旧器同规矩：规范槽非空时不丢别名值，全文进 _audit_unmapped。
    resp = obj.get("response")
    if isinstance(resp, dict):
        new = {}
        unmapped = list(obj.get("_audit_unmapped") or [])
        for k, v in resp.items():
            ck = _canon_key(k)
            if ck is None:
                loss.append({"rule": "E1_unmappable_key", "key": k,
                             "detail": "键名无法归一到四规范名，按原样保留（不猜）"})
                new[k] = v
                continue
            if ck != k:
                loss.append({"rule": "E1_style_key_normalized", "from": k, "to": ck,
                             "text_unchanged": True})
            cur = new.get(ck)
            cur_empty = cur is None or (isinstance(cur, str) and not cur.strip())
            v_empty = v is None or (isinstance(v, str) and not str(v).strip())
            if ck in new and not cur_empty and not v_empty:
                unmapped.append({"alias_key": k, "alias_value": v,
                                 "canon_key": ck, "canon_value": cur})
                loss.append({"rule": "E1_duplicate_canon_key_preserved_to_audit",
                             "to": ck, "preserved_text": v})
                continue
            if ck in new and v_empty:
                continue
            new[ck] = v
        obj["response"] = new
        if unmapped:
            obj["_audit_unmapped"] = unmapped

    # I3 的全文保全由旧器写进 _audit_extra_directions；这里只把它记进留损日志（不截断）
    extra = obj.get("_audit_extra_directions")
    if isinstance(extra, list) and extra:
        loss.append({"rule": "I3_directions_overflow_preserved", "given": len(extra) + 4,
                     "preserved_directions": extra})

    # R4：只登记，不代候选做决定
    dirs = obj.get("directions")
    ks = [k for k in CANON if k in (obj.get("response") or {})]
    empty_body = all(not str((obj.get("response") or {}).get(k) or "").strip() for k in ks) \
        if ks else True
    all_null_dirs = isinstance(dirs, list) and len(dirs) and all(d is None for d in dirs)
    if "action" not in obj and empty_body and all_null_dirs:
        loss.append({"rule": "R4_no_reply_expression_missing",
                     "detail": "空正文+全 null 方向：不回只能由生成侧显式 action 表达，"
                               "消费侧不代补", "repaired_here": False})

    return obj, loss, json.dumps(obj, ensure_ascii=False)
