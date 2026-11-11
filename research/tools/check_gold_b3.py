# -*- coding: utf-8 -*-
"""annotator-B3 交付自查：E317-E324 reference.json 逐条校验。"""
import json
import os
import sys

BASE = r"D:\lovebrain-arena\benchmark\stories_v2\wave4_dev"
EIDS = ["E317", "E318", "E319", "E320", "E321", "E322", "E323", "E324"]
REQUIRED_FIELDS = [
    "case_id", "current_action", "acceptable_actions", "applicable_dimensions",
    "required_memory_atoms", "private_guidance", "unknowns",
    "protected_expressions", "forbidden_factual_claims", "may_decline",
]
DIMS = {"goal", "factuality", "attribution", "time_state", "boundary",
        "memory_use", "interaction", "initiative", "emotion_relation"}
CANCEL_FAMILY = {"cancelled", "canceled", "superseded", "retracted", "voided",
                 "revoked", "invalidated", "negated", "erroneous"}

report = []
total_fail = 0

for eid in EIDS:
    ep_path = os.path.join(BASE, eid, "episode.json")
    ref_path = os.path.join(BASE, eid, "reference.json")
    exp_path = os.path.join(BASE, eid, "exposure.json")

    with open(ep_path, encoding="utf-8") as f:
        ep = json.load(f)
    with open(ref_path, encoding="utf-8") as f:
        ref = json.load(f)
    with open(exp_path, encoding="utf-8") as f:
        exp = json.load(f)

    fails = []

    # 1. turns 为 dict，键数=12，与 episode case_id 一一对应
    turns = ref.get("turns")
    if not isinstance(turns, dict):
        fails.append("turns 不是 dict")
        turns = {}
    ep_cases = [t["case_id"] for t in ep["turns"]]
    if len(turns) != 12:
        fails.append("turns 键数=%d != 12" % len(turns))
    if sorted(turns.keys()) != sorted(ep_cases):
        fails.append("turns 键与 episode case_id 不一一对应")

    # 每轮消息文本拼接（供 quote 校验）
    msg_by_case = {}
    for t in ep["turns"]:
        texts = []
        for m in t["input"]["messages"]:
            texts.append(m.get("text", ""))
        msg_by_case[t["case_id"]] = texts

    n_atoms = 0
    n_cancel = 0
    n_unknown = 0
    n_required = 0

    for case_id, card in turns.items():
        tag = "%s/%s" % (eid, case_id)
        # 2. 10 字段齐全
        for fld in REQUIRED_FIELDS:
            if fld not in card:
                fails.append("%s 缺字段 %s" % (tag, fld))
        if card.get("case_id") != case_id:
            fails.append("%s case_id 不匹配" % tag)
        # 3. acceptable_actions >= 2
        aa = card.get("acceptable_actions", [])
        if not isinstance(aa, list) or len(aa) < 2:
            fails.append("%s acceptable_actions<2" % tag)
        # 4. applicable_dimensions >= 3 且合法
        dims = card.get("applicable_dimensions", [])
        if not isinstance(dims, list) or len(dims) < 3:
            fails.append("%s applicable_dimensions<3" % tag)
        for d in dims:
            if d not in DIMS:
                fails.append("%s 非法维度 %s" % (tag, d))
        # 5. 原子校验
        for atom in card.get("required_memory_atoms", []):
            n_atoms += 1
            if atom.get("required_this_turn"):
                n_required += 1
            status = atom.get("status")
            neg = atom.get("negation", False)
            is_must_not = (status in CANCEL_FAMILY) or (neg is True)
            if is_must_not:
                n_cancel += 1
                am = atom.get("affirm_markers")
                if not am or not isinstance(am, list) or not all(
                        isinstance(g, list) and len(g) > 0 for g in am):
                    fails.append("%s 取消/否定原子缺 affirm_markers: %s"
                                 % (tag, atom.get("atom_id")))
                if status == "cancelled":
                    im = atom.get("item_markers")
                    if not im or not isinstance(im, list) or not all(
                            isinstance(x, str) for x in im):
                        fails.append("%s cancelled 原子缺 item_markers: %s"
                                     % (tag, atom.get("atom_id")))
            src = atom.get("source", {})
            sc = src.get("case_id")
            quote = src.get("quote", "")
            if sc not in msg_by_case:
                fails.append("%s 原子 %s source.case_id 无效: %s"
                             % (tag, atom.get("atom_id"), sc))
            else:
                joined = "".join(msg_by_case[sc])
                if quote not in joined:
                    fails.append("QUOTE-MISS %s 原子 %s quote 未逐字命中 %s: %r"
                                 % (tag, atom.get("atom_id"), sc, quote))
            mid = src.get("message_id", "")
            if mid and mid.startswith("m") and mid[1:].isdigit():
                idx = int(mid[1:])
                if idx >= len(msg_by_case.get(sc, [])):
                    fails.append("%s 原子 %s message_id 越界: %s@%s"
                                 % (tag, atom.get("atom_id"), sc, mid))
            vt = atom.get("valid_time", "")
            if not vt:
                fails.append("%s 原子 %s 缺 valid_time" % (tag, atom.get("atom_id")))
            if atom.get("subject") not in {"HER", "ME", "THIRD", "SHARED", "PUBLIC"}:
                fails.append("%s 原子 %s 非法 subject" % (tag, atom.get("atom_id")))
        # 6. unknowns 对象数组含 markers
        for u in card.get("unknowns", []):
            n_unknown += 1
            if not isinstance(u, dict) or "topic" not in u:
                fails.append("%s unknowns 非对象: %r" % (tag, u))
            elif "markers" not in u:
                fails.append("%s unknowns 缺 markers: %s" % (tag, u.get("topic")))
        # 7. may_decline 与 episode input 一致
        ep_md = None
        for t in ep["turns"]:
            if t["case_id"] == case_id:
                ep_md = t["input"].get("may_decline", False)
        if card.get("may_decline") != ep_md:
            fails.append("%s may_decline 不一致: card=%r episode=%r"
                         % (tag, card.get("may_decline"), ep_md))

    # exposure 校验
    if exp.get("status") != "annotated_pending_audit":
        fails.append("exposure.status=%r" % exp.get("status"))
    for fld in ("domain", "primary_topic", "lineage_group_id", "frozen_at"):
        if not exp.get(fld):
            fails.append("exposure 缺 %s" % fld)
    if exp.get("primary_topic") not in ("T05", "T06"):
        fails.append("exposure.primary_topic 非法")
    if exp.get("lineage_group_id") != "LG-" + eid:
        fails.append("exposure.lineage_group_id 应为 LG-%s" % eid)

    status = "PASS" if not fails else "FAIL(%d)" % len(fails)
    total_fail += len(fails)
    report.append("%s %s cards=%d atoms=%d cancel=%d unknowns=%d required=%d topic=%s domain=%s"
                  % (eid, status, len(turns), n_atoms, n_cancel, n_unknown,
                     n_required, exp.get("primary_topic"), exp.get("domain")))
    for x in fails:
        report.append("    - " + x)

report.append("")
report.append("TOTAL_FAIL=%d" % total_fail)
print("\n".join(report))
sys.exit(1 if total_fail else 0)
