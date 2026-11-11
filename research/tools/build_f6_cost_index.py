# -*- coding: utf-8 -*-
r"""tools/build_f6_cost_index.py — F6 成本/口径索引（零调用，只读已落盘账本与答卷）。

输出三件事，供 F6 终稿直接引用：
  1. 本主线（2026-10-29 F0–F6）每笔授权的 attempt 计数与账本口径
     （started / completed / failed / refused_duplicate / batch_rejected / closed）；
  2. token 用量：裁判侧取账本 usage；生成侧账本没有 usage，按真实答卷 calls.json 汇总，
     **并显式声明去重口径**（长故事 resume 会复制前 12 轮目录，只数最终链一份）；
  3. 失败/作废清单：哪些 attempt 花了钱但没有进入结论（不退款、不补跑的凭据）。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/build_f6_cost_index.py \
      --out refs/experiment_protocol_20261023/final_mainline_review_20261029/F6_COST_INDEX.json
"""
import argparse
import glob
import json
import os
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join("refs", "experiment_protocol_20261023", "final_mainline_review_20261029")

# 本主线授权单（2026-10-29 当天新建的才算，历史单不复活不抵扣）
PHASE_AIDS = [
    ("F1", "AUTH-F1-BLIND-20261029-01", "盲审 158（155 完成 + 3 孤儿不重试）"),
    ("F3-gen-aborted", "AUTH-F3-NEWMAT-20261029-01", "首批被上游超时与主机内存事故打断，作废不补跑"),
    ("F3-gen", "AUTH-F3-RESTART-20261029-01", "内存修复后整批重跑 144/144"),
    ("F3-judge", "AUTH-F3-JUDGE-20261029-01", "72 对新材料盲评"),
    ("F4-gen", "AUTH-F4-LOCAL-20261029-01", "组件消融 24 次实调用（另有 8 次重复派发被拒，0 调用）"),
    ("F4-knowfix", "AUTH-F4-KNOWFIX-20261029-01", "知识探针去答案数字重跑 12 次"),
    ("F4-judge", "AUTH-F4-JUDGE-20261029-01", "12 对消融盲评"),
    ("F5-gen", "AUTH-F5-RECHECK-20261029-01", "E3 修复身份复测：N01 全链 12 次"),
    ("F5-judge", "AUTH-F5-JUDGE-20261029-01", "同臂重抽 12 对盲评"),
    ("F5-judge-swap", "AUTH-F5-JUDGE-SWAP-20261029-01", "同批 12 对 A/B 顺序复测"),
]

# 生成侧 token 汇总：目录 -> (是否只数终链, 通配)
GEN_TOKEN_SOURCES = [
    ("F3-gen", "logs/f3_newmat_live_restart_20261029",
     ["candidate_N01", "candidate_N02", "parent_N01", "parent_N02",
      "candidate_K01_resume", "candidate_K02_resume",
      "parent_K01_resume", "parent_K02_resume"],
     "长故事只数 *_resume 终链（前 12 轮是 checkpoint 副本，避免重复计数）"),
    ("F3-gen-aborted", "logs/f3_newmat_live_live_20261029", None, "作废批：只记 attempt，不进结论"),
    ("F5-gen", "logs/f5_recheck_live2_20261029_133653", ["candidate_N01"], "E3 身份复测链"),
]


def ledger_rows(aid):
    p = os.path.join(ROOT, "logs", "auth_budget", "%s.jsonl" % aid)
    if not os.path.exists(p):
        return None, None
    rows = [json.loads(l) for l in open(p, encoding="utf-8") if l.strip()]
    return rows, p


def tok_from_ledger(rows):
    tp = tc = 0
    for r in rows:
        u = r.get("usage") or {}
        tp += u.get("prompt_tokens") or u.get("prompt") or 0
        tc += u.get("completion_tokens") or u.get("completion") or 0
    return tp, tc


def tok_from_runs(chain_dirs):
    tp = tc = n = 0
    for cd in chain_dirs:
        for cp in glob.glob(os.path.join(cd, "turn_*", "calls.json")):
            try:
                calls = json.load(open(cp, encoding="utf-8"))
            except Exception:
                continue
            if isinstance(calls, dict):
                calls = [calls]
            for c in calls:
                u = c.get("usage") or {}
                if u.get("prompt") is None and u.get("completion") is None:
                    continue
                tp += u.get("prompt") or 0
                tc += u.get("completion") or 0
                n += 1
    return tp, tc, n


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(BASE, "F6_COST_INDEX.json"))
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    rows = []
    tot_started = tot_completed = tot_failed = tot_refused = 0
    for phase, aid, note in PHASE_AIDS:
        led, path = ledger_rows(aid)
        if led is None:
            rows.append({"phase": phase, "authorization_id": aid, "missing": True,
                         "note": note})
            continue
        c = Counter(r.get("kind") for r in led)
        tp, tc = tok_from_ledger(led)
        closed = next((r for r in led if r.get("kind") == "authorization_closed"), None)
        rows.append({
            "phase": phase, "authorization_id": aid, "note": note,
            "ledger": os.path.relpath(path, ROOT).replace("\\", "/"),
            "attempt_started": c.get("attempt_started", 0),
            "attempt_completed": c.get("attempt_completed", 0),
            "attempt_failed": c.get("attempt_failed", 0),
            "dispatch_refused_duplicate": c.get("dispatch_refused_duplicate", 0),
            "batch_rejected": c.get("batch_rejected", 0),
            "closed": bool(closed),
            "close_reason": (closed or {}).get("reason"),
            "prompt_tokens_ledger": tp, "completion_tokens_ledger": tc,
        })
        tot_started += c.get("attempt_started", 0)
        tot_completed += c.get("attempt_completed", 0)
        tot_failed += c.get("attempt_failed", 0)
        tot_refused += c.get("dispatch_refused_duplicate", 0) + c.get("batch_rejected", 0)

    gen = []
    for phase, base, keep, note in GEN_TOKEN_SOURCES:
        bdir = os.path.join(ROOT, base.replace("/", os.sep))
        if keep is None:
            found = sorted(glob.glob(os.path.join(bdir, "*", "turn_*", "calls.json")))
            tp = tc = n = 0
            for cp in found:
                try:
                    calls = json.load(open(cp, encoding="utf-8"))
                except Exception:
                    continue
                if isinstance(calls, dict):
                    calls = [calls]
                for c in calls:
                    u = c.get("usage") or {}
                    tp += u.get("prompt") or 0
                    tc += u.get("completion") or 0
                    n += 1
            gen.append({"phase": phase, "run_dir": base, "note": note,
                        "calls_with_usage": n, "prompt_tokens": tp,
                        "completion_tokens": tc, "basis": "全目录计数（作废批）"})
            continue
        dirs = [os.path.join(bdir, k) for k in keep if os.path.isdir(os.path.join(bdir, k))]
        tp, tc, n = tok_from_runs(dirs)
        gen.append({"phase": phase, "run_dir": base, "note": note,
                    "chains": keep, "calls_with_usage": n,
                    "prompt_tokens": tp, "completion_tokens": tc,
                    "basis": "只数最终交付链（去重）"})

    doc = {
        "kind": "f6_cost_index", "date": "2026-10-29",
        "rule": "一物理请求一 attempt；失败与作废一律消耗，不重试、不补跑、不退款；"
                "已关闭授权单不得复活，也不得用新单覆盖旧结论。",
        "authorizations": rows,
        "totals": {"attempt_started": tot_started, "attempt_completed": tot_completed,
                   "attempt_failed": tot_failed, "dispatch_or_batch_refused": tot_refused},
        "generation_token_basis": gen,
        "token_grand_total": {
            "tokens_from_ledger_usage": sum(r.get("prompt_tokens_ledger", 0) +
                                            r.get("completion_tokens_ledger", 0)
                                            for r in rows if not r.get("missing")),
            "tokens_from_answer_files": sum(g["prompt_tokens"] + g["completion_tokens"]
                                            for g in gen if "作废" not in g["note"]),
            "basis": "账本 usage 覆盖全部裁判调用与 F4 的实调用生成；"
                     "F3/F5 生成走 iso_run（账本不带 usage），token 由答卷 calls.json 汇总",
        },
    }
    dst = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out.replace("/", os.sep))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    json.dump(doc, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(json.dumps({"totals": doc["totals"],
                      "token_grand_total": doc["token_grand_total"],
                      "phases": [r["phase"] for r in rows]},
                     ensure_ascii=False, indent=1))
    print("WROTE", os.path.relpath(dst, ROOT).replace("\\", "/"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
