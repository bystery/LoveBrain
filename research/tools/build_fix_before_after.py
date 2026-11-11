# -*- coding: utf-8 -*-
r"""tools/build_fix_before_after.py — 故障族"修复前/修复后"分列对照表（零调用，只读已付费原始答卷）。

对照对象：同一 12 反馈锚点、同一份冻结卡（sha 231e03865425…）、同一 dsf/0.7/17770：
  修复前 = BATCH-S2-ANCHORS-20261028  candidate=action_policy_r1(6e450c8f…)
  修复后 = BATCH-S2R-FIX-20261028     candidate=action_policy_r2(352c0c06…) ← 新身份，不套旧成绩
父本 reference_product_v1(501bc426…) 在两批各 12 次，作同条件参照（不跨批拼接成绩）。

故障族按**原始出站文本机械判定**，不采信任何人的描述：
  F1 style 键名写坏     json 可解析但四规范键不齐（实测 'bad_bo y' / 'bad_boo'）
  F2 directions 槽数错  len(directions) != 4
  F3 顶层提前闭合 }     json.loads 报 Extra data 且 raw_decode 首段缺 analysis、其后紧跟 ','
  F4 不回无法表达       四键全空 + directions 全 null + 无 action → 合同判坏、零可发正文（交付失败）
  F5 合法不回已表达     action == "no_reply" → 本轮不发消息是设计结果（不是故障）
退化对照（防"允许不回"把候选训成一律收声）：非 may_decline 位点的可发正文数、
may_decline 位点里"仍选择回复"与"选择不回"的分布。

用法：
  D:\anaconda\python.exe tools\build_fix_before_after.py ^
    --before refs\...\S2_SCORE_SKELETON.json --after refs\...\S2R_SCORE_SKELETON.json ^
    --out refs\...\FIX_BEFORE_AFTER_20261028.json
"""
import argparse
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CANON = ["recommended", "bad_boy", "playful", "warm"]
DEC = json.JSONDecoder()


def parse_obj(raw):
    if not (raw or "").strip():
        return None
    try:
        o = json.loads(raw)
        return o if isinstance(o, dict) else None
    except Exception:                                            # noqa: BLE001
        return None


def families(raw):
    fam, note = [], []
    if not (raw or "").strip():
        return ["empty_transport"], []
    o = parse_obj(raw)
    if o is None:
        try:
            head, end = DEC.raw_decode(raw)
            if isinstance(head, dict) and "analysis" not in head and raw[end:end + 1] == ",":
                fam.append("F3_premature_top_level_brace")
                note.append("首段键=%s" % ",".join(head.keys()))
        except Exception:                                        # noqa: BLE001
            pass
        return fam, note
    resp = o.get("response") if isinstance(o.get("response"), dict) else {}
    dirs = o.get("directions")
    act = o.get("action")
    if act == "no_reply":
        fam.append("F5_legal_no_reply_expressed")
        return fam, note
    if [k for k in CANON if k not in resp]:
        fam.append("F1_style_key_broken")
    if isinstance(dirs, list) and len(dirs) != 4:
        fam.append("F2_directions_count")
    empt = bool(resp) and all(not str(resp.get(k) or "").strip() for k in CANON if k in resp)
    allnull = isinstance(dirs, list) and len(dirs) > 0 and all(d is None for d in dirs)
    if empt and allnull:
        fam.append("F4_no_reply_unrepresentable")
    return fam, note


def collect(skel, arm, may_decline):
    rows, cnt = {}, {}
    for p in skel["positions"]:
        cid = p["case_id"]
        d = p["arms"][arm]
        raw = d.get("raw_text")
        fam, note = families(raw)
        o = parse_obj(raw) or {}
        resp = o.get("response") if isinstance(o.get("response"), dict) else {}
        sendable_texts = sum(1 for k in CANON if str(resp.get(k) or "").strip())
        rows[cid] = {
            "families": fam, "note": note,
            "veto": (d.get("program") or {}).get("veto"),
            "score10": (d.get("program") or {}).get("score10"),
            "sendable_style_texts": sendable_texts,
            "primary_chars": len((d.get("primary") or "").strip()),
            "nonempty_slots_total": sendable_texts + sum(
                1 for s, t in (d.get("backups") or [])
                if s.startswith("directions") and isinstance(t, str) and t.strip()),
            "analysis_present": isinstance(o.get("analysis"), dict),
            "may_decline": cid in may_decline,
            "latency_ms": d.get("latency_ms"),
        }
        for x in (fam or ["none"]):
            cnt[x] = cnt.get(x, 0) + 1
        cnt["_veto_any"] = cnt.get("_veto_any", 0) + (1 if rows[cid]["veto"] else 0)
    return rows, cnt


def degradation(rows):
    nd = [c for c, r in rows.items() if not r["may_decline"]]
    dl = [c for c, r in rows.items() if r["may_decline"]]
    return {
        "non_decline_positions": len(nd),
        "non_decline_with_sendable_primary": sum(1 for c in nd if rows[c]["primary_chars"] > 0),
        "non_decline_lost_its_voice": [c for c in nd if rows[c]["primary_chars"] == 0],
        "decline_allowed_positions": dl,
        "decline_allowed_still_replied": [c for c in dl if rows[c]["primary_chars"] > 0],
        "decline_allowed_chose_no_reply": [c for c in dl if rows[c]["primary_chars"] == 0],
    }


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--before", required=True)
    ap.add_argument("--after", required=True)
    ap.add_argument("--before-arm", default="action_policy_r1")
    ap.add_argument("--after-arm", default="action_policy_r2")
    ap.add_argument("--ref-arm", default="reference_product_v1")
    ap.add_argument("--may-decline", default="AK06_T01,AK09_T01,AK12_T01")
    ap.add_argument("--out", required=True)
    a = ap.parse_args(argv)

    md = set(x.strip() for x in a.may_decline.split(","))
    b = json.load(io.open(os.path.join(ROOT, a.before), encoding="utf-8"))
    f = json.load(io.open(os.path.join(ROOT, a.after), encoding="utf-8"))

    before_rows, before_cnt = collect(b, a.before_arm, md)
    after_rows, after_cnt = collect(f, a.after_arm, md)
    p0_rows, p0_cnt = collect(b, a.ref_arm, md)
    p1_rows, p1_cnt = collect(f, a.ref_arm, md)

    out = {
        "kind": "fault_family_before_after_v1",
        "zero_network": True,
        "comparability": {
            "cards_before": b.get("cards_file"), "cards_after": f.get("cards_file"),
            "cards_identical": b.get("cards_file") == f.get("cards_file"),
            "endpoint_model_temp": "127.0.0.1:17770/v1 · dsf · 0.7（两批授权单各自登记一致）",
            "before_batch": b.get("batch_id"), "after_batch": f.get("batch_id"),
            "before_candidate": a.before_arm, "after_candidate": a.after_arm,
            "note": "修复前后是不同 harness 身份；逐位分列不合并成绩。两批交错方向整体相反，"
                    "因此'谁先派'不成为混淆变量。",
        },
        "family_counts": {
            "candidate_before": before_cnt, "candidate_after": after_cnt,
            "parent_batch_before": p0_cnt, "parent_batch_after": p1_cnt,
        },
        "per_position": {"candidate_before": before_rows, "candidate_after": after_rows,
                         "parent_before": p0_rows, "parent_after": p1_rows},
        "degradation_control": {"candidate_before": degradation(before_rows),
                                 "candidate_after": degradation(after_rows),
                                 "parent_before": degradation(p0_rows),
                                 "parent_after": degradation(p1_rows)},
        "reading_rules": [
            "F4=候选想不回但合同表达不出来 → 零可发正文，属交付失败；",
            "F5=候选显式 action=no_reply → 本轮不发消息是设计结果，不算失败也不算得分；",
            "F1/F2/F3 属结构层：原始协议层照记 veto，消费侧可否交付另见 "
            "PROTOCOL_REPAIR_OFFLINE_20261028.json（两列不互冒充）；",
            "n=12 的单批差异只回答'是否复现'，不足以宣布统计显著或已根治。",
        ],
    }
    json.dump(out, io.open(os.path.join(ROOT, a.out), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("BEFORE/AFTER WRITTEN", a.out)
    print("  candidate before", json.dumps(before_cnt, ensure_ascii=False))
    print("  candidate after ", json.dumps(after_cnt, ensure_ascii=False))
    print("  parent before   ", json.dumps(p0_cnt, ensure_ascii=False))
    print("  parent after    ", json.dumps(p1_cnt, ensure_ascii=False))
    print("  after degradation", json.dumps(out["degradation_control"]["candidate_after"],
                                            ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
