"""改动前快照（2026-10-21，A3 等价比较基线）。禁止运行期引用。"""
import json
import re
from typing import Optional

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


# ---------- 基础工具 ----------


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
    """收集全部可发送文本：response 各 style_key + directions + extra_text_fields。"""
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
    覆盖：directions 各项、response 已声明风格键、analysis 内层
    （scene_facts[].text/source_ids、ongoing[].item/status、topic_status/topic_label）、
    已声明扩展文本字段（extra_text_fields）自身的值类型（第八波判决 §4：
    扩展字符串保持通过；对象等非字符串值判结构错误，不再漏检）。"""
    c = normalize_contract(contract)
    errs = []
    for k in c["extra_text_fields"]:
        v = out.get(k)
        if v is not None and not isinstance(v, str):
            errs.append("%s:%s" % (k, type(v).__name__))
    dirs = out.get("directions")
    if isinstance(dirs, list):
        # v0.9.2（第九波判决 §3.1 契约统一）：实际生成提示（format.md §directions）
        # 明确允许"不适合本轮的方向输出 null，不凑满四个"——契约定为 string|null，
        # null = 该方向本轮不交付（合法缺省，与提示一致），不再判结构错误；
        # v0.9.1 的 null 判零属契约冲突，撤销。非字符串（数字/对象/列表等）
        # 在任何路径下拒绝。可发送文本采集与动作判定统一以 all_reply_texts
        # 为唯一权威（null 方向不进文本池、不算正文）——不再在此处手写判定。
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
                # 第八波判决 §4：事实条目 text 必须为字符串——显式 null / 缺失键
                # 同样判结构错误（此前 null 穿过校验器得 10 分，基线 .strip() 崩溃）。
                # 机制写回共用本判定：坏事实跳过提交、第二轮继续，不存在"满分+崩溃"。
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


# ---------- 检查器 ----------

