# -*- coding: utf-8 -*-
r"""tools/bind_answers.py — 把"这份判词到底判的是哪批答案"固化成可校验的绑定文件（零调用、只读）。

为什么需要它：S1/S2/S2R/S3/D1/R3A/R3B 七批的逐位判词写在独立的 *_WORKER_SCORES.py 里，
旧入口 aggregate_s1_scorecard.py 只按 case_id 取判词，从不校验骨架里的答案有没有被换掉。
独立复核已用副本证明：换掉候选正文、把输入散列清零，旧判词仍原样套上、胜负不变。
旧工具 tools/exam_layered_score.py 的 _match_annotation 本来就做过这类校验（文本/槽位/
运行身份/harness 身份/题面 hash），新汇总路径把它丢了——本工具把这条绑定接回来。

绑定内容（逐位点 × 逐臂）：
  run_id / case_input_sha256            —— 题面与运行身份（来自骨架，同时与真实 manifest 对账）
  raw_reply_sha256                      —— 骨架里保存的原始答卷文本 hash
  primary_sha256                        —— 骨架里的首选文本 hash
  raw_file_sha256 / raw_file_chars      —— 真实 reply_raw.txt 的文件字节 hash（若还在）
  raw_file_matches_skeleton             —— 文件与骨架文本是否一致（只登记，不改写）
再加上每臂 EXAM_<role>/EXAM_INDEX.json 的 harness_identity_sha256。

digest_sha256 只覆盖 {arms, exam_dir, positions}，对同一批答案是确定的。
判词文件可在模块级声明 BINDING_DIGEST = "<digest>"；汇总时若声明值与绑定不符则整批拒收。
绑定只做"同一批答案"的识别，不做内容正确性判断，也不覆盖任何原始答卷。

用法：
  set PYTHONIOENCODING=utf-8
  D:\anaconda\python.exe tools\bind_answers.py \
      --skeleton refs/.../R3A_SCORE_SKELETON.json \
      --out      refs/.../R3A_SCORE_BINDING.json
"""
import argparse
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def sha_text(s):
    if s is None:
        return None
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def sha_file(p):
    if not os.path.isfile(p):
        return None, None
    h = hashlib.sha256()
    n = 0
    with open(p, "rb") as f:
        while True:
            b = f.read(1 << 20)
            if not b:
                break
            h.update(b)
            n += len(b)
    return h.hexdigest(), n


def rel(p):
    try:
        return os.path.relpath(p, ROOT).replace("\\", "/")
    except ValueError:
        return p.replace("\\", "/")


def find_run_dir(live_root, role, episode, case_id):
    base = os.path.join(live_root, "%s_%s" % (role, episode), "out")
    if not os.path.isdir(base):
        return None
    hits = [d for d in os.listdir(base)
            if os.path.isdir(os.path.join(base, d))
            and d.endswith("_" + case_id) and d.startswith("turn_")]
    if len(hits) != 1:
        return None if not hits else os.path.join(base, sorted(hits)[0])
    return os.path.join(base, hits[0])


def binding_digest(arms, exam_dir, positions):
    payload = {"arms": arms, "exam_dir": exam_dir, "positions": positions}
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True,
                     separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--skeleton", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--live-root", default=None,
                    help="默认取 skeleton.exam_dir 的上一级（..._reports 的父目录）")
    ap.add_argument("--label", default=None)
    a = ap.parse_args(argv)

    skp = os.path.abspath(a.skeleton)
    sk = load(skp)
    arms = dict(sk["arms"])                       # role -> harness
    exam_dir = sk["exam_dir"]
    live_root = a.live_root or os.path.dirname(exam_dir.rstrip("/\\"))

    role_identity, idx_notes = {}, {}
    for role in arms:
        ip = os.path.join(ROOT, exam_dir, "EXAM_%s" % role, "EXAM_INDEX.json")
        if os.path.isfile(ip):
            d = load(ip)
            role_identity[role] = {
                "harness": d.get("harness"),
                "harness_identity_sha256": d.get("harness_identity_sha256"),
                "authorization_id": d.get("authorization_id"),
                "batch_id": d.get("batch_id"),
                "status": d.get("status"),
            }
        else:
            role_identity[role] = {"harness": arms[role], "harness_identity_sha256": None}
            idx_notes[role] = "EXAM_INDEX.json 缺失：%s" % rel(ip)

    positions, mismatches, artifact_missing = {}, [], 0
    for pos in sk["positions"]:
        cid, ep = pos["case_id"], pos.get("episode") or pos["case_id"].split("_")[0]
        rec = {}
        for role in arms:
            arm_name = arms[role]
            ar = (pos.get("arms") or {}).get(arm_name) or {}
            raw_text = ar.get("raw_text")
            rec[role] = {
                "harness": arm_name,
                "run_id": ar.get("run_id"),
                "case_input_sha256": ar.get("case_input_sha256"),
                "raw_reply_sha256": sha_text(raw_text),
                "raw_reply_chars": len(raw_text) if isinstance(raw_text, str) else None,
                "primary_sha256": sha_text(ar.get("primary")),
                "primary_chars": len(ar.get("primary") or ""),
            }
            rd = find_run_dir(os.path.join(ROOT, live_root), role, ep, cid)
            if rd:
                man = os.path.join(rd, "manifest.json")
                ing = os.path.join(rd, "reply_ingest.json")
                rawf = os.path.join(rd, "reply_raw.txt")
                if os.path.isfile(man):
                    md = load(man)
                    rec[role]["source"] = "skeleton+artifacts"
                    rec[role]["run_dir"] = rel(rd)
                    rec[role]["manifest_run_id"] = md.get("run_id")
                    rec[role]["manifest_case_input_sha256"] = md.get("case_input_sha256")
                    if md.get("run_id") and ar.get("run_id") and md["run_id"] != ar.get("run_id"):
                        mismatches.append("%s/%s:manifest_run_id" % (cid, role))
                    if (md.get("case_input_sha256") and ar.get("case_input_sha256")
                            and md["case_input_sha256"] != ar["case_input_sha256"]):
                        mismatches.append("%s/%s:manifest_case_input" % (cid, role))
                fsha, fchars = sha_file(rawf)
                rec[role]["raw_file_sha256"] = fsha
                rec[role]["raw_file_chars"] = fchars
                rec[role]["raw_file_matches_skeleton"] = (
                    fsha is not None and fsha == rec[role]["raw_reply_sha256"])
                if os.path.isfile(ing):
                    id_ = load(ing)
                    rec[role]["reply_ingest_sha256"] = id_.get("sha256")
                    rec[role]["reply_ingest_matches_raw_file"] = (
                        id_.get("sha256") is not None and id_.get("sha256") == fsha)
                if fsha is None:
                    artifact_missing += 1
            else:
                rec[role]["source"] = "skeleton_only"
                artifact_missing += 1
        positions[cid] = rec

    dig = binding_digest(arms, exam_dir, positions)
    out = {
        "kind": "score_answer_binding_v1",
        "label": a.label or os.path.basename(skp).replace("_SCORE_SKELETON.json", ""),
        "created_for_skeleton": rel(skp),
        "skeleton_file_sha256": sha_file(skp)[0],
        "exam_dir": exam_dir,
        "live_root": rel(os.path.join(ROOT, live_root)),
        "arms": arms,
        "role_identity": role_identity,
        "counts": {
            "positions": len(positions),
            "cells": sum(len(v) for v in positions.values()),
            "artifact_missing": artifact_missing,
            "skeleton_vs_manifest_mismatches": len(mismatches),
            "raw_file_vs_skeleton_differs": sum(
                1 for v in positions.values() for r in v.values()
                if r.get("raw_file_matches_skeleton") is False),
        },
        "skeleton_vs_manifest_mismatch_detail": mismatches,
        "idx_notes": idx_notes,
        "positions": positions,
        "digest_sha256": dig,
    }
    op = os.path.abspath(a.out)
    os.makedirs(os.path.dirname(op), exist_ok=True)
    with open(op, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("positions=%d cells=%d digest=%s artifact_missing=%d manifest_mismatch=%d raw_diff=%d"
          % (out["counts"]["positions"], out["counts"]["cells"], dig[:16],
             artifact_missing, len(mismatches), out["counts"]["raw_file_vs_skeleton_differs"]))
    print("-> %s" % rel(op))
    return 0


if __name__ == "__main__":
    sys.exit(main())
