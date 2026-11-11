# protocol_public.py — 候选可见的纯协议库（ARENA_V1_CLOSEOUT 工作包 A3）
# 宪法地位：输出协议解析/校验的唯一权威实现。控制器评分（lb_scorer）与候选写回
# 共用本模块；本模块与 gold、评分权重、评审权威完全无关——只做结构与契约类型判定。
#
# 候选运行包只允许携带本模块（或其显式导出），不允许携带完整 lb_scorer.py、
# rubric、题目 gold 或整库（v0.13 收口指导书 §3-A3：不能一边声称评分器不可见，
# 一边为兼容导入把整份评分器复制进去）。
#
# 兼容政策：lb_scorer 自本版起从本模块 re-export 这些名字（同一函数对象，
# 非 copy）——历史调用 `lb_scorer.extract_json_block` 的行为不变；
# 候选侧规范导入为 `import protocol_public`（或 `from protocol_public import ...`）。
# 禁止 `from lb_scorer import *` 式兼容（会把评分实现带进候选进程）。
#
# 迁移基线：refs/arena_v1/protocol_baseline/lb_scorer_pure_snapshot.py
# （改动前快照）。等价证明见 tools/test_protocol_public.py：
# 快照实现与本模块在有效/无效输出矩阵上逐例输出一致。

import json
import re
from typing import Optional

# ---------- 输出契约（rubric v0.3） ----------

LEGACY_CONTRACT = {
    "style_keys": ["recommended", "bad_boy", "playful", "warm"],
    "directions": 4,
    "min_nonempty": 1,
    "extra_text_fields": [],
    "may_decline": False,
}


def normalize_contract(contract: Optional[dict]) -> dict:
    """候选 params.json 的 output_contract → 规范化契约；未声明按 legacy。
    硬约束：可发送候选总数（style_keys + directions + extra_text_fields）≥ 8 仅对
    显式声明强制；legacy 契约豁免（基线对拍需要）。"""
    if not contract:
        return dict(LEGACY_CONTRACT)
    c = {
        "style_keys": list(contract.get("style_keys") or LEGACY_CONTRACT["style_keys"]),
        "directions": int(contract.get("directions", 4)),
        "min_nonempty": int(contract.get("min_nonempty", 1)),
        "extra_text_fields": list(contract.get("extra_text_fields") or []),
        "may_decline": bool(contract.get("may_decline", False)),
    }
    total = len(c["style_keys"]) + c["directions"] + len(c["extra_text_fields"])
    if total < 8:
        raise ValueError("output_contract 可发送候选总数 %d < 8（rubric v0.3 硬约束）" % total)
    return c


def extract_json_block(text: str):
    """从模型输出中提取第一个完整 JSON 对象（容错：允许 ```json 围栏）。"""
    m = re.search(r"\{.*\}", text, re.DOTALL)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return None


def all_reply_texts(out: dict, contract: Optional[dict] = None):
    """收集全部可发送文本：response 各 style_key + directions + extra_text_fields。
    唯一权威（v0.9.2）：null 方向不进文本池、不算正文。"""
    c = normalize_contract(contract)
    texts = []
    resp = out.get("response") or {}
    for k in c["style_keys"]:
        v = resp.get(k)
        if isinstance(v, str) and v.strip():
            texts.append((k, v))
    dirs = out.get("directions") or []
    if isinstance(dirs, list):
        for i, d in enumerate(dirs):
            if isinstance(d, str) and d.strip():
                texts.append(("directions[%d]" % i, d))
    for k in c["extra_text_fields"]:
        v = out.get(k)
        if isinstance(v, str) and v.strip():
            texts.append((k, v))
    return texts


def output_structure_errors(out: dict, contract: Optional[dict] = None) -> list:
    """v0.9 嵌套结构校验（唯一权威，评分与机制写回共用；第七波判决 §3）。
    返回错误描述列表（空 = 结构合格）。只查类型不查内容，正常中文不受影响。
    directions 契约为 string|null（v0.9.2）：null=该方向本轮不交付（合法缺省），
    非字符串一律拒绝。scene_facts[].text 必须为字符串（null/缺失同判错）。"""
    c = normalize_contract(contract)
    errs = []
    for k in c["extra_text_fields"]:
        v = out.get(k)
        if v is not None and not isinstance(v, str):
            errs.append("%s:%s" % (k, type(v).__name__))
    dirs = out.get("directions")
    if isinstance(dirs, list):
        for i, d in enumerate(dirs):
            if d is not None and not isinstance(d, str):
                errs.append("directions[%d]:%s" % (i, type(d).__name__))
    resp = out.get("response")
    if isinstance(resp, dict):
        for k in c["style_keys"]:
            v = resp.get(k)
            if v is not None and not isinstance(v, str):
                errs.append("response.%s:%s" % (k, type(v).__name__))
        for k, v in resp.items():
            if k not in c["style_keys"] and isinstance(v, str) and v.strip():
                errs.append("response.%s:未声明文本字段(非空字符串)" % k)
    ana = out.get("analysis")
    if isinstance(ana, dict):
        errs.extend("analysis." + e for e in analysis_type_errors(ana))
    return errs


def analysis_type_errors(ana: dict) -> list:
    """analysis 内层结构校验（机制写回消费前必须过同一道门，v0.9）。
    覆盖 scene_facts / ongoing / topic_status / topic_label 的元素与字段类型。"""
    errs = []
    if not isinstance(ana, dict):
        return ["analysis:%s" % type(ana).__name__]
    for k in ("topic_status", "topic_label"):
        v = ana.get(k)
        if v is not None and not isinstance(v, str):
            errs.append("%s:%s" % (k, type(v).__name__))
    sf = ana.get("scene_facts")
    if sf is not None:
        if not isinstance(sf, list):
            errs.append("scene_facts:%s" % type(sf).__name__)
        else:
            for i, f in enumerate(sf):
                if not isinstance(f, dict):
                    errs.append("scene_facts[%d]:%s" % (i, type(f).__name__))
                    continue
                t = f.get("text")
                if not isinstance(t, str):
                    errs.append("scene_facts[%d].text:%s" % (i, type(t).__name__))
                sids = f.get("source_ids")
                if sids is not None:
                    if not isinstance(sids, list):
                        errs.append("scene_facts[%d].source_ids:%s" % (i, type(sids).__name__))
                    else:
                        for j, s in enumerate(sids):
                            if not isinstance(s, str):
                                errs.append("scene_facts[%d].source_ids[%d]:%s"
                                            % (i, j, type(s).__name__))
    og = ana.get("ongoing")
    if og is not None:
        if not isinstance(og, list):
            errs.append("ongoing:%s" % type(og).__name__)
        else:
            for i, o in enumerate(og):
                if not isinstance(o, dict):
                    errs.append("ongoing[%d]:%s" % (i, type(o).__name__))
                    continue
                for k in ("item", "status"):
                    v = o.get(k)
                    if v is not None and not isinstance(v, str):
                        errs.append("ongoing[%d].%s:%s" % (i, k, type(v).__name__))
    return errs


# ---------- 显式导出面（候选兼容层只许这些） ----------

__all__ = ["LEGACY_CONTRACT", "normalize_contract", "extract_json_block",
           "all_reply_texts", "output_structure_errors", "analysis_type_errors"]
