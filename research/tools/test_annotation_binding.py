# tools/test_annotation_binding.py — R1/R2 收口反例
#
# R1：语义批注必须绑定 case_id + slot + raw_reply_sha256 + slot_text_sha256，
#     不接受"case_id 命中即继承 pass/fail"。reviewer 独立评审里的反例：
#     把 E001_T01 recommended 换成一句明显越权的话（"杯子是我做的，我已经替你
#     刻好名字并答应付款代取了。"）后，旧代码仍显示 pass + 旧判词。
#     这条 test 直接构造四种"文本变→批注失效"的场景。
#
# R2：at_least_one_pass / backup_with_severe_error 必须是三态（true/false/unknown），
#     未评槽存在时不得报 false。当前 exam1 160 backup 槽里只有 1 个 severe，
#     其余 159 未评 → 23 位应报 unknown，只有 E003_T04 报 true。
import hashlib
import importlib.util
import json
import os
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import exam_layered_score as layer  # noqa: E402


def _sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def _ann(case_id, slot, raw_reply, slot_text, label="pass", note="test-note",
         case_input_sha=None, run_id=None, harness=None):
    a = {
        "case_id": case_id, "slot": slot,
        "raw_reply_sha256": _sha(raw_reply),
        "slot_text_sha256": _sha(slot_text),
        "label": label, "note": note,
        "evidence": "unit", "reviewer": "unit",
        "review_status": "test",
    }
    if case_input_sha is not None:
        a["case_input_sha256"] = case_input_sha
    if run_id is not None:
        a["run_id"] = run_id
    if harness is not None:
        a["harness_identity_sha256"] = harness
    return a


def _doc(anns, run_id=None, harness=None):
    idx = {}
    for a in anns:
        idx.setdefault((a["case_id"], a["slot"]), []).append(a)
    d = {"_index": idx, "annotations": anns}
    if run_id is not None:
        d["run_id"] = run_id
    if harness is not None:
        d["harness_identity_sha256"] = harness
    return d


CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


# ───────────── R1：绑定不匹配 → 批注失效 ─────────────

@check
def r1_case_id_hit_wrong_reply_not_inherited():
    """同 case+slot，答卷文本变了 → 旧 pass 批注必须失效（返回 None），
    不得静默继承为 pass。这是 reviewer 独立评审里最核心的那条：
    E001_T01 换一段明显错误文本仍报 pass。"""
    orig_reply = json.dumps({"response": {"recommended": "原句"}}, ensure_ascii=False)
    tampered_reply = json.dumps(
        {"response": {"recommended": "杯子是我做的，我已经替你刻好名字并答应付款代取了。"}},
        ensure_ascii=False)
    ann = _ann("E001_T01", "recommended", orig_reply, "原句", label="pass")
    doc = _doc([ann])
    hit = layer._match_annotation(doc, "E001_T01", "recommended",
                                  _sha(tampered_reply),
                                  _sha("杯子是我做的，我已经替你刻好名字并答应付款代取了。"))
    assert hit is None, "R1 违约：文本改了仍命中旧 pass 批注 → %s" % hit
    return "R1.tampered_reply_unmatched PASS"


@check
def r1_correct_replacement_also_invalidates_old_fail():
    """反向：原答卷判 fail，新文本换成真正正确的写法 → 旧 fail 也不能继承。
    语义评审必须重新做一次，不因'看起来更像对的'就自动翻绿。"""
    old_reply = json.dumps({"response": {"recommended": "上回那句我没记住"}},
                            ensure_ascii=False)
    new_reply = json.dumps({"response": {"recommended": "杯底留空，不刻了"}},
                            ensure_ascii=False)
    ann = _ann("E001_T06", "recommended", old_reply, "上回那句我没记住", label="fail")
    doc = _doc([ann])
    hit = layer._match_annotation(doc, "E001_T06", "recommended",
                                  _sha(new_reply), _sha("杯底留空，不刻了"))
    assert hit is None, "R1 违约：换正确文本继承旧 fail → %s" % hit
    return "R1.correct_replacement_unmatched PASS"


@check
def r1_slot_swap_invalidates_other_slots():
    """跨槽污染防御：把 recommended 槽的批注留在文件中，
    答卷把这段文本挪到 bad_boy 槽（其他槽）—— 命中必须限定 case_id + slot，
    bad_boy 查询不得拿到 recommended 的 pass。"""
    reply = json.dumps({"response": {
        "recommended": "A", "bad_boy": "B-原始-text"}}, ensure_ascii=False)
    ann = _ann("E002_T03", "recommended", reply, "A", label="pass")
    doc = _doc([ann])
    # 现在 bad_boy 槽的文本 = "B-原始-text"（未评），查 bad_boy 不应拿到 recommended 的 pass
    hit = layer._match_annotation(doc, "E002_T03", "bad_boy",
                                  _sha(reply), _sha("B-原始-text"))
    assert hit is None, "R1 违约：跨槽污染 → %s" % hit
    # 命中 recommended 自身仍可
    hit2 = layer._match_annotation(doc, "E002_T03", "recommended",
                                   _sha(reply), _sha("A"))
    assert hit2 is not None and hit2["label"] == "pass", \
        "R1 违约：正确 case+slot+hash 命中失败"
    return "R1.slot_isolation PASS"


@check
def r1_corrupt_hash_in_annotation_file_denied():
    """批注文件里的 reply/slot 散列任一被篡改 → 不匹配当前答卷。
    防止有人绕过 build_semantic_annotations 直接改 JSON 把 fail 刷成 pass。"""
    reply = json.dumps({"response": {"recommended": "原句"}}, ensure_ascii=False)
    ann = _ann("E003_T04", "recommended", reply, "原句", label="pass")
    # 篡改 raw_reply_sha256（保留 slot_text_sha256 正确）
    ann_bad = dict(ann)
    ann_bad["raw_reply_sha256"] = "0" * 64
    doc = _doc([ann_bad])
    hit = layer._match_annotation(doc, "E003_T04", "recommended",
                                  _sha(reply), _sha("原句"))
    assert hit is None, "R1 违约：批注 reply_hash 被抹掉仍命中"
    ann_bad2 = dict(ann)
    ann_bad2["slot_text_sha256"] = "f" * 64
    doc2 = _doc([ann_bad2])
    hit2 = layer._match_annotation(doc2, "E003_T04", "recommended",
                                   _sha(reply), _sha("原句"))
    assert hit2 is None, "R1 违约：批注 slot_hash 被抹掉仍命中"
    return "R1.hash_tamper_denied PASS"


@check
def r1_no_annotations_file_means_all_unrated():
    """未提供批注文件（doc=None）→ summarize 里每一位都标 unrated。
    旧代码会从 SEMANTIC_LEGACY 静默继承 pass；新代码必须显式失败。"""
    res = layer.summarize(os.path.join(ROOT, "logs", "exam1_20261023"),
                          annotations_doc=None, annotations_path=None)
    unrated_primary = sum(1 for t in res["turns"]
                          if t["primary_semantic"] == "unrated")
    assert unrated_primary == len(res["turns"]), \
        "无批注文件时仍有 %d 位继承了旧标签" % (
            len(res["turns"]) - unrated_primary)
    assert res["annotations_source"] == "NONE", \
        "annotations_source 应为 NONE，实得 %s" % res["annotations_source"]
    # 检查 group 汇总里三态：无批注 → 全 unknown，pass_rate_on_rated=None（无 rated）
    tot = res["total"]
    assert tot["primary_rated_n"] == 0, \
        "无批注时 primary_rated_n 应为 0，实得 %s" % tot["primary_rated_n"]
    assert tot["at_least_one_pass_unknown"] == len(res["turns"]), \
        "无批注时全部 at_least_one_pass 应为 unknown"
    return "R1.no_file_all_unrated PASS (turns=%d)" % len(res["turns"])


# ───────────── B：批注上下文（run/身份/题面内容）绑定 ─────────────

_REPLY_OK = json.dumps({"response": {"recommended": "杯底留空"}}, ensure_ascii=False)
_INPUT_SHA = _sha('{"messages":[{"role":"HER","text":"杯子怎么样了"}]}')
_RUN = "AUTH-EXAM1-20261023-01"
_HARNESS = "399c82b784c00223fa534fc28f6a4c05680f245b5bc079fe1acf39699a8c7e42"


def _full_ctx(**over):
    ctx = {"run_id": _RUN, "harness_identity_sha256": _HARNESS,
           "case_input_sha256": _INPUT_SHA}
    ctx.update(over)
    return ctx


@check
def b_full_context_matches():
    """run/身份/题面/文本全一致 + 批注带完整绑定 → 命中。"""
    ann = _ann("E001_T07", "recommended", _REPLY_OK, "杯底留空", label="pass",
               case_input_sha=_INPUT_SHA, run_id=_RUN, harness=_HARNESS)
    doc = _doc([ann], run_id=_RUN, harness=_HARNESS)
    hit = layer._match_annotation(doc, "E001_T07", "recommended",
                                  _sha(_REPLY_OK), _sha("杯底留空"), context=_full_ctx())
    assert hit is not None and hit["label"] == "pass", \
        "B 违约：完整上下文应命中，实得 %s" % hit
    return "B.full_context_match PASS"


@check
def b_same_output_case_content_drift_denied():
    """reviewer 反例：输出与槽位不变，但题面 input 内容变了 →
    批注 case_input_sha 与 context 不符 → 拒绝（旧批注不得跨题面版本继承 pass）。"""
    ann = _ann("E001_T07", "recommended", _REPLY_OK, "杯底留空", label="pass",
               case_input_sha=_INPUT_SHA, run_id=_RUN, harness=_HARNESS)
    doc = _doc([ann], run_id=_RUN, harness=_HARNESS)
    drifted = _full_ctx(case_input_sha256=_sha("题目被人改了"))
    hit = layer._match_annotation(doc, "E001_T07", "recommended",
                                  _sha(_REPLY_OK), _sha("杯底留空"), context=drifted)
    assert hit is None, "B 违约：题面内容变了仍继承旧 pass → %s" % hit
    return "B.case_content_drift_denied PASS"


@check
def b_wrong_run_or_harness_identity_denied():
    """reviewer 精确反例：保持原输出，批注文件声明无关 run_id + 全0 harness 身份，
    匹配必须失败（不得只比文本/槽位就继承）。"""
    # 批注里 run/身份被写成无关值
    ann = _ann("E001_T07", "recommended", _REPLY_OK, "杯底留空", label="pass",
               case_input_sha=_INPUT_SHA, run_id="AUTH-OTHER-BATCH",
               harness="0" * 64)
    doc = _doc([ann], run_id="AUTH-OTHER-BATCH", harness="0" * 64)
    hit = layer._match_annotation(doc, "E001_T07", "recommended",
                                  _sha(_REPLY_OK), _sha("杯底留空"), context=_full_ctx())
    assert hit is None, "B 违约：run/身份不符仍命中 → %s" % hit
    return "B.wrong_run_or_harness_denied PASS"


@check
def b_annotation_missing_context_binding_unverified():
    """reviewer 反例：题面版本漂移的兜底——旧批注缺 case_input/run/harness 绑定，
    即便文本+槽位对得上，也必须按"上下文未验证"拒绝，不凭当前活文件补造历史绑定。"""
    ann = _ann("E001_T07", "recommended", _REPLY_OK, "杯底留空", label="pass")  # 无上下文三件套
    doc = _doc([ann])                          # 文件头也无 run/harness
    hit = layer._match_annotation(doc, "E001_T07", "recommended",
                                  _sha(_REPLY_OK), _sha("杯底留空"), context=_full_ctx())
    assert hit is None, "B 违约：缺上下文绑定的批注被当已验证继承 → %s" % hit
    # 对照：context=None 的历史只读复算仍可纯文本命中（不改变新批次口径）
    hit2 = layer._match_annotation(doc, "E001_T07", "recommended",
                                   _sha(_REPLY_OK), _sha("杯底留空"), context=None)
    assert hit2 is not None, "只读复算模式应允许纯文本命中（不影响新批默认拒绝）"
    return "B.missing_context_unverified PASS"


@check
def b_missing_case_input_context_denied():
    """§3.4 关键反例（reviewer：缺失题面绑定不能沿用语义标签）：批注与评估
    上下文在 run_id / harness 身份 / 答卷散列 / 槽位散列 四项全一致，但评估侧
    的 case_input_sha256 缺失（None，例如运行快照没能算出该 case 题面内容散列）——
    旧代码会 `is not None` 短路跳过题面比较、静默继承旧标签；修复后必须按
    '上下文未验证'拒绝（返回 None → 上层 unrated）。对照：上下文带上匹配的
    case_input_sha256 仍应命中，证明拒绝只针对'缺散列'而非过严。"""
    ann = _ann("E001_T07", "recommended", _REPLY_OK, "杯底留空", label="pass",
               case_input_sha=_INPUT_SHA, run_id=_RUN, harness=_HARNESS)
    doc = _doc([ann], run_id=_RUN, harness=_HARNESS)
    # run/身份/文本全对，但题面内容散列缺失
    ctx_missing = {"run_id": _RUN, "harness_identity_sha256": _HARNESS,
                   "case_input_sha256": None}
    hit = layer._match_annotation(doc, "E001_T07", "recommended",
                                  _sha(_REPLY_OK), _sha("杯底留空"), context=ctx_missing)
    assert hit is None, \
        "§3.4 违约：评估侧题面散列缺失仍继承旧标签 → %s" % hit
    # 对照：上下文带上匹配散列 → 命中（拒绝仅针对缺散列）
    hit_ok = layer._match_annotation(doc, "E001_T07", "recommended",
                                     _sha(_REPLY_OK), _sha("杯底留空"),
                                     context=_full_ctx())
    assert hit_ok is not None and hit_ok["label"] == "pass", \
        "§3.4 对照违约：题面散列齐备却误拒 → %s" % hit_ok
    return "B.missing_case_input_context_denied PASS"


@check
def b_real_annotations_bound_and_run_verified():
    """真实批注文件：每条都带 case_input/run/harness 绑定；用 exam1 的真实 EXAM_INDEX
    上下文（run=AUTH-EXAM1-20261023-01、身份=399c82…）能命中 21 位；
    把评估上下文的 harness 身份换成全 0（模拟"换候选身份"）→ 命中归零。"""
    ann_path = os.path.join(ROOT, "benchmark", "annotations",
                             "exam1_20261023_semantic.json")
    if not os.path.isfile(ann_path):
        return "SKIP annotations file absent"
    doc = layer.load_annotations(ann_path)
    for a in doc["annotations"]:
        assert a.get("case_input_sha256") and a.get("run_id") and \
            a.get("harness_identity_sha256"), \
            "B 违约：批注缺 run/身份/题面绑定 %s/%s" % (a["case_id"], a["slot"])
    good = layer.summarize(os.path.join(ROOT, "logs", "exam1_20261023"),
                           annotations_doc=doc, annotations_path=ann_path)
    bound = [t["case_id"] for t in good["turns"] if t["primary_annotation_bound"]]
    # 24 位里只有 E001_T04/E003_T04 被强制移出 primary 批注（其余 22 位有绑定，
    # 含 E003_T02 带 unrated 标签的历史批注）
    assert "E001_T04" not in bound and "E003_T04" not in bound, \
        "B 违约：归属修正未生效 %s" % bound
    assert len(bound) == 22, "正确上下文应命中 22 位绑定，实得 %d" % len(bound)
    # 语义 rated（非 unrated）= 21：E003_T02 的批注标签本就是 unrated，不计入 rated
    assert good["total"]["primary_rated_n"] == 21, \
        "primary_rated_n 应=21，实得 %s" % good["total"]["primary_rated_n"]
    # 逐条抽真实 reply 文本散列，喂"身份漂移"的 context，验证闸门（命中必为 0）
    drift_honored = 0
    for t in good["turns"]:
        if not t["primary_annotation_bound"]:
            continue
        raw = layer._load_raw_reply(os.path.join(ROOT, "logs", "exam1_20261023"),
                                    t["case_id"].split("_")[0],
                                    "turn_%02d_%s" % (t["turn"], t["case_id"])) or ""
        hit = layer._match_annotation(
            doc, t["case_id"], "recommended", _sha(raw), _sha(t["primary_text"]),
            context={"run_id": _RUN, "harness_identity_sha256": "f" * 64,
                     "case_input_sha256": None})
        if hit:
            drift_honored += 1
    assert drift_honored == 0, "身份漂移后仍有 %d 位继承旧批注" % drift_honored
    return "B.real_annotations_bound PASS bound=%d rated=21 drift_honored=0" % len(bound)


@check
def b_backup_derived_primary_not_inherited():
    """B 归属修正：E001_T04、E003_T04 的 recommended 不再继承 case-level『edge』；
    分层结果里这两位 primary_semantic=unrated，且 edge 计数=0。"""
    ann_path = os.path.join(ROOT, "benchmark", "annotations",
                             "exam1_20261023_semantic.json")
    if not os.path.isfile(ann_path):
        return "SKIP"
    doc = layer.load_annotations(ann_path)
    # 批注文件里不应有这两位 recommended 的 pass/edge 标签
    for cid in ("E001_T04", "E003_T04"):
        assert (cid, "recommended") not in doc["_index"], \
            "B 违约：%s recommended 仍被导出为批注" % cid
    # provenance 记录留痕
    doc_raw = json.load(open(ann_path, encoding="utf-8"))
    prov = {p["case_id"] for p in doc_raw.get("provenance_corrections", [])}
    assert {"E001_T04", "E003_T04"} <= prov, "B 违约：provenance 未记录归属修正 %s" % prov
    res = layer.summarize(os.path.join(ROOT, "logs", "exam1_20261023"),
                          annotations_doc=doc, annotations_path=ann_path)
    sem = res["total"]["semantic"]
    assert sem.get("edge", 0) == 0, \
        "B 违约：仍有 %s 位 edge（应为 0，两条已改 unrated）" % sem.get("edge")
    rows = {t["case_id"]: t for t in res["turns"]}
    for cid in ("E001_T04", "E003_T04"):
        assert rows[cid]["primary_semantic"] == "unrated", \
            "B 违约：%s primary 不是 unrated" % cid
    # 覆盖率因此下调，0.958 不再当作有效首选审查覆盖
    assert abs(res["total"]["primary_coverage_ratio"] - 21 / 24) < 1e-3, \
        "B 违约：primary_coverage_ratio 应≈0.875，实得 %s" % res["total"]["primary_coverage_ratio"]
    return "B.backup_derived_primary_unrated PASS edge=0 coverage=%.3f" % (
        res["total"]["primary_coverage_ratio"])


# ───────────── R2：三态与覆盖率 ─────────────

@check
def r2_tri_any_pass_unknown_when_backup_unrated():
    """primary=fail + backup 全 unrated → at_least_one_pass = unknown（不是 false）。
    false 只在全部已审且都 ≠ pass 时成立。"""
    row_primary = "fail"
    backups = [{"slot": "bad_boy", "semantic": "unrated"},
               {"slot": "playful", "semantic": "unrated"}]
    assert layer._tri_any_pass(row_primary, backups) == "unknown", \
        "R2 违约：unrated backup 存在却报 false"
    # 加入 pass 后立刻 true
    backups2 = backups + [{"slot": "warm", "semantic": "pass"}]
    assert layer._tri_any_pass(row_primary, backups2) == "true"
    # 全评完都非 pass → false
    backups3 = [{"slot": "bad_boy", "semantic": "edge"},
                {"slot": "playful", "semantic": "fail"}]
    assert layer._tri_any_pass(row_primary, backups3) == "false"
    # primary=pass 直接 true，不看 backup
    assert layer._tri_any_pass("pass", [{"slot": "x", "semantic": "unrated"}]) == "true"
    return "R2.tri_any_pass PASS (unknown/true/false 三态)"


@check
def r2_tri_backup_severe_unknown_when_partial():
    """20 个 backup 中 19 个 unrated + 1 个 severe → true；
    19 unrated + 1 pass → unknown；全 pass/edge/fail 无 severe → false。"""
    backups_a = [{"slot": "s%d" % i, "semantic": "unrated"} for i in range(19)]
    backups_a = backups_a + [{"slot": "directions[2]", "semantic": "severe"}]
    assert layer._tri_backup_severe(backups_a) == "true"
    backups_b = [{"slot": "s%d" % i, "semantic": "unrated"} for i in range(19)]
    backups_b = backups_b + [{"slot": "bad_boy", "semantic": "pass"}]
    assert layer._tri_backup_severe(backups_b) == "unknown", \
        "R2 违约：仍有未评槽却报非 unknown"
    backups_c = [{"slot": "a", "semantic": "pass"}, {"slot": "b", "semantic": "edge"}]
    assert layer._tri_backup_severe(backups_c) == "false"
    # 空 backup → unknown（不假装"没有严重问题"）
    assert layer._tri_backup_severe([]) == "unknown"
    return "R2.tri_backup_severe PASS"


@check
def r2_real_exam_coverage_and_invariants():
    """用真实批注文件跑一遍：
    - primary_pass ≤ at_least_one_pass_true；
    - 24 位 primary 全绑定；160 backup 只有 1 severe，其余 159 unrated；
    - annotations_sufficient_for_gate=False（覆盖率不够，不该用这个基线做 gate）；
    - severe_error_backup_true == 1（E003_T04 那条点名），其他为 unknown。"""
    ann_path = os.path.join(ROOT, "benchmark", "annotations",
                             "exam1_20261023_semantic.json")
    if not os.path.isfile(ann_path):
        return "SKIP annotations file not present yet: %s" % ann_path
    doc = layer.load_annotations(ann_path)
    res = layer.summarize(os.path.join(ROOT, "logs", "exam1_20261023"),
                          annotations_doc=doc, annotations_path=ann_path)
    tot = res["total"]
    assert tot["primary_semantic_pass"] <= tot["at_least_one_pass_true"], \
        "R2 不变量违反：primary_pass=%d > at_least_one_pass_true=%d" % (
            tot["primary_semantic_pass"], tot["at_least_one_pass_true"])
    # 覆盖率：160 backup 槽里只有 1 有批注
    assert tot["backup_slots_total"] == 160, \
        "R2 backup 槽总数应为 160，实得 %s" % tot["backup_slots_total"]
    assert tot["backup_slots_rated"] == 1, \
        "R2 已评 backup 应为 1（E003_T04 directions[2]），实得 %s" % tot["backup_slots_rated"]
    assert tot["severe_error_backup_true"] == 1, \
        "R2 severe=true 位应为 1，实得 %s" % tot["severe_error_backup_true"]
    assert tot["severe_error_backup_unknown"] == 23, \
        "R2 unknown 位应为 23（其余未评），实得 %s" % tot["severe_error_backup_unknown"]
    assert tot["severe_error_backup_false"] == 0, \
        "R2 false 位应为 0（没有任何一位全评完且无 severe）"
    assert tot["annotations_sufficient_for_gate"] is False, \
        "R2 gate 标志应 False（覆盖率<1）"
    assert not any("primary_pass" in str(grp.get("_invariant_violation", ""))
                   for grp in res["episodes"] + [res["total"]]), \
        "R2 不变量检查报违规：%s" % [
            g.get("_invariant_violation") for g in res["episodes"] + [res["total"]]
            if g.get("_invariant_violation")]
    return ("R2.coverage PASS primary=%d/%d alop_true=%d sev_true=%d sev_unknown=%d "
            "gate=%s" % (tot["primary_semantic_pass"], tot["primary_rated_n"],
                          tot["at_least_one_pass_true"],
                          tot["severe_error_backup_true"],
                          tot["severe_error_backup_unknown"],
                          tot["annotations_sufficient_for_gate"]))


@check
def r2_lower_bound_severe_rate():
    """severe_error_backup_rate 必须显式为 lower_bound（分母是"总位"而非"已评位"），
    不能让 4.2%（1/24）看起来像权威数——真实未知范围是 [1/24, 24/24]。"""
    ann_path = os.path.join(ROOT, "benchmark", "annotations",
                             "exam1_20261023_semantic.json")
    if not os.path.isfile(ann_path):
        return "SKIP"
    doc = layer.load_annotations(ann_path)
    res = layer.summarize(os.path.join(ROOT, "logs", "exam1_20261023"),
                          annotations_doc=doc, annotations_path=ann_path)
    tot = res["total"]
    key = "severe_error_backup_lower_bound_rate"
    assert key in tot, "字段名必须以 lower_bound_rate 结尾，不叫 bare rate"
    assert abs(tot[key] - 1 / 24) < 1e-3, \
        "lower_bound 应≈ 1/24，实得 %s" % tot[key]
    # 若还留有旧命名的字段，必须显式标 deprecated 或不存在
    if "severe_error_backup_rate" in tot:
        assert tot.get("_severe_rate_deprecated") is True, \
            "severe_error_backup_rate 若保留必须显式标 deprecated"
    return "R2.lower_bound PASS %s=%.3f" % (key, tot[key])


def main():
    results, failed = [], 0
    for fn in CHECKS:
        try:
            detail = fn()
            results.append({"check": fn.__name__, "verdict": "PASS", "detail": detail})
            print("PASS %-45s %s" % (fn.__name__, detail))
        except Exception as e:
            failed += 1
            results.append({"check": fn.__name__, "verdict": "FAIL",
                            "detail": "%s: %s" % (type(e).__name__, e)})
            print("FAIL %-45s %s: %s" % (fn.__name__, type(e).__name__, e))
    out = os.path.join(ROOT, "logs", "annotation_binding_result.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump({"checks": results, "failed": failed},
              open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("ANNOTATION BINDING RESULT: %s (%d checks, failed=%d)"
          % ("PASS" if not failed else "FAIL", len(results), failed))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
