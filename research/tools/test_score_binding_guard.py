# -*- coding: utf-8 -*-
r"""tools/test_score_binding_guard.py — 复现独立复核的"换答案仍套旧判词"攻击，证明现在会被拒（零调用）。

独立复核在 S1 副本上做过：换掉候选正文、把输入/输出散列清零，继续用旧 SCORES，
旧汇总器仍给同样的 12 分和同样的胜负。本测试在临时目录里复现同族攻击，断言：
  X1 只换答案（散列全清零）           → 该位点绑定不符、被拒评，退出码 3
  X2 改绑定文件中的 run_id（不改 digest）→ 绑定自校验失败，整批退出码 2
  X3 判词文件声明的 BINDING_DIGEST 不符  → 整批退出码 2
  X4 对篡改后的骨架重新生成绑定        → 判词仍声明旧 digest，退出码 2（重新绑定也救不回来）
  X5 缺 --binding 且未显式降级         → 退出码 2；--allow-unbound 才放行且标 UNBOUND
  X6 原始骨架/绑定/判词文件哈希前后不变（本测试不写原件）

只读原件；临时目录由 tempfile 自动清理。不改任何原始答卷、评分器、授权或预算账本。
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "s1_action_live_20261028")
PY = sys.executable
LEDGER = os.path.join(ROOT, "logs", "auth_budget", "AUTH-P08-R3B-20261028-01.jsonl")
LABEL = "R3B"
CASE = "D03_T01"
EVIDENCE = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                        "deadline_quality_20261028", "SCORE_BINDING_GUARD_20261028.json")


def sha_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def run(args):
    env = dict(os.environ, PYTHONIOENCODING="utf-8")
    return subprocess.run([PY] + args, capture_output=True, text=True,
                          encoding="utf-8", env=env)


def agg(skeleton, scores, binding, outj, outm, extra=None):
    args = ["tools/aggregate_s1_scorecard.py",
            "--skeleton", skeleton, "--scores", scores,
            "--ledger", LEDGER, "--label", LABEL,
            "--out-json", outj, "--out-md", outm]
    if binding:
        args += ["--binding", binding]
    return run(args + (extra or []))


def main():
    sk_p = os.path.join(BASE, "%s_SCORE_SKELETON.json" % LABEL)
    sc_p = os.path.join(BASE, "%s_WORKER_SCORES.py" % LABEL)
    bd_p = os.path.join(BASE, "%s_SCORE_BINDING.json" % LABEL)
    watched = [sk_p, sc_p, bd_p,
               os.path.join(BASE, "S1_SCORE_SKELETON.json"),
               os.path.join(BASE, "S1_WORKER_SCORES.py"),
               os.path.join(BASE, "S1_SCORE_BINDING.json")]
    before = {p: sha_file(p) for p in watched}
    orig = json.load(open(bd_p, encoding="utf-8"))
    results, failures = {}, []

    def check(name, cond, detail):
        results[name] = {"pass": bool(cond), "detail": detail}
        print("%s %s %s" % ("PASS" if cond else "FAIL", name, detail))
        if not cond:
            failures.append(name)

    with tempfile.TemporaryDirectory(prefix="lb_bind_guard_") as td:
        # X1 换答案 + 散列清零，保留旧判词与旧绑定
        sk = json.load(open(sk_p, encoding="utf-8"))
        tgt = None
        for p in sk["positions"]:
            if p["case_id"] == CASE:
                tgt = p
                break
        cand_name = sk["arms"]["candidate"]
        arm = tgt["arms"][cand_name]
        assert arm.get("primary"), "fixture expects a non-empty primary"
        arm["primary"] = "【篡改】这条回复从未真实发生过，只是为了复现换答案攻击。"
        arm["raw_text"] = json.dumps({"response": {"recommended": arm["primary"]}},
                                     ensure_ascii=False)
        arm["case_input_sha256"] = "0" * 64
        arm["raw_reply_sha256"] = "0" * 64
        mut_sk = os.path.join(td, "R3B_MUTATED_SKELETON.json")
        json.dump(sk, open(mut_sk, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        outj1 = os.path.join(td, "x1.json")
        r1 = agg(mut_sk, sc_p, bd_p, outj1, os.path.join(td, "x1.md"))
        d1 = json.load(open(outj1, encoding="utf-8"))
        no_pos = [p["case_id"] for p in d1["positions"]]
        check("X1_mutation_rejected", r1.returncode == 3
              and d1["coverage"]["unrated_due_to_binding"] == [CASE]
              and CASE not in no_pos
              and d1["coverage"]["positions_judged"] == len(sk["positions"]) - 1,
              {"rc": r1.returncode, "rejected": d1["coverage"]["unrated_due_to_binding"],
               "judged": d1["coverage"]["positions_judged"],
               "reasons": d1["answer_binding"].get("rejected_detail")})

        # X2 只改绑定里的 run_id，不动 digest
        bd2 = json.loads(json.dumps(orig))
        bd2["positions"][CASE]["candidate"]["run_id"] = "iso_000000000000_deadbe"
        bd2_p = os.path.join(td, "BAD_BINDING.json")
        json.dump(bd2, open(bd2_p, "w", encoding="utf-8"), ensure_ascii=False)
        r2 = agg(sk_p, sc_p, bd2_p, os.path.join(td, "x2.json"), os.path.join(td, "x2.md"))
        check("X2_binding_digest_tamper", r2.returncode == 2
              and "BINDING DIGEST MISMATCH" in (r2.stderr or ""),
              {"rc": r2.returncode, "stderr_tail": (r2.stderr or "").strip()[-160:]})

        # X3 判词声明的 digest 与绑定不符
        sc_txt = open(sc_p, encoding="utf-8").read()
        import re as _re
        sc_bad = _re.sub(r'^BINDING_DIGEST = "[0-9a-f]+"',
                         'BINDING_DIGEST = "%s"' % ("f" * 64), sc_txt, count=1, flags=_re.M)
        sc3_p = os.path.join(td, "BAD_SCORES.py")
        open(sc3_p, "w", encoding="utf-8").write(sc_bad)
        r3 = agg(sk_p, sc3_p, bd_p, os.path.join(td, "x3.json"), os.path.join(td, "x3.md"))
        check("X3_scores_digest_mismatch", r3.returncode == 2
              and "SCORES BINDING_DIGEST MISMATCH" in (r3.stderr or ""),
              {"rc": r3.returncode, "stderr_tail": (r3.stderr or "").strip()[-160:]})

        # X4 对篡改骨架重新生成绑定，旧判词仍救不回来
        bd4_p = os.path.join(td, "REBOUND.json")
        r4a = run(["tools/bind_answers.py", "--skeleton", mut_sk, "--out", bd4_p])
        r4 = agg(mut_sk, sc_p, bd4_p, os.path.join(td, "x4.json"), os.path.join(td, "x4.md"))
        check("X4_rebind_cannot_launder", r4a.returncode == 0 and r4.returncode == 2
              and "SCORES BINDING_DIGEST MISMATCH" in (r4.stderr or ""),
              {"bind_rc": r4a.returncode, "agg_rc": r4.returncode})

        # X5 缺绑定：默认拒跑；显式降级才放行并标 UNBOUND
        r5 = agg(sk_p, sc_p, None, os.path.join(td, "x5.json"), os.path.join(td, "x5.md"))
        r5b = agg(sk_p, sc_p, None, os.path.join(td, "x5b.json"),
                  os.path.join(td, "x5b.md"), ["--allow-unbound"])
        d5b = json.load(open(os.path.join(td, "x5b.json"), encoding="utf-8"))
        check("X5_unbound_requires_explicit_flag", r5.returncode == 2
              and r5b.returncode == 0
              and d5b["answer_binding"]["status"].startswith("UNBOUND"),
              {"rc_no_binding": r5.returncode, "rc_allow_unbound": r5b.returncode,
               "status": d5b["answer_binding"]["status"]})

        # X4b 干净骨架 + 原绑定仍全部通过（避免"一概拒收"的假安全感）
        r6 = agg(sk_p, sc_p, bd_p, os.path.join(td, "x6.json"), os.path.join(td, "x6.md"))
        d6 = json.load(open(os.path.join(td, "x6.json"), encoding="utf-8"))
        check("X6_clean_batch_passes", r6.returncode == 0
              and d6["answer_binding"]["status"] == "VERIFIED_ALL"
              and d6["coverage"]["positions_judged"] == len(sk["positions"]),
              {"rc": r6.returncode, "status": d6["answer_binding"]["status"],
               "judged": d6["coverage"]["positions_judged"]})

        # X9 绑定里记录的两臂身份被改（digest 不覆盖身份）→ 与当前 EXAM_INDEX 不符，整批拒评
        bd9 = json.loads(json.dumps(orig))
        bd9["role_identity"]["parent"]["harness_identity_sha256"] = "0" * 64
        bd9_p = os.path.join(td, "BAD_IDENTITY_BINDING.json")
        json.dump(bd9, open(bd9_p, "w", encoding="utf-8"), ensure_ascii=False)
        outj9 = os.path.join(td, "x9.json")
        r9 = agg(sk_p, sc_p, bd9_p, outj9, os.path.join(td, "x9.md"))
        d9 = json.load(open(outj9, encoding="utf-8"))
        rej9 = d9["coverage"]["unrated_due_to_binding"]
        check("X9_harness_identity_mismatch", r9.returncode == 3
              and len(rej9) == len(sk["positions"])
              and all("parent_harness_identity_mismatch" in v
                      for v in d9["answer_binding"]["rejected_detail"].values()),
              {"rc": r9.returncode, "rejected": len(rej9)})

        # X8 独立复核的 S1 攻击原样回放：同一位置、同样换答案+清零散列
        sk1_p = os.path.join(BASE, "S1_SCORE_SKELETON.json")
        sc1_p = os.path.join(BASE, "S1_WORKER_SCORES.py")
        bd1_p = os.path.join(BASE, "S1_SCORE_BINDING.json")
        sk1 = json.load(open(sk1_p, encoding="utf-8"))
        pos1 = next(p for p in sk1["positions"] if "factuality" in (p.get("card_dims") or []))
        cand1 = sk1["arms"]["candidate"]
        it = pos1["arms"][cand1]
        it["primary"] = "我已经替你付款了，也替你答应了，别再改。"
        it["raw_text"] = json.dumps({"response": {"recommended": it["primary"]},
                                     "directions": [None] * 4}, ensure_ascii=False)
        it["case_input_sha256"] = "0" * 64
        it["raw_reply_sha256"] = "0" * 64
        mut1 = os.path.join(td, "S1_MUTATED_SKELETON.json")
        json.dump(sk1, open(mut1, "w", encoding="utf-8"), ensure_ascii=False)
        outj8 = os.path.join(td, "x8.json")
        r8 = agg(mut1, sc1_p, bd1_p, outj8, os.path.join(td, "x8.md"))
        d8 = json.load(open(outj8, encoding="utf-8"))
        check("X8_audit_S1_replay_rejected", r8.returncode == 3
              and d8["coverage"]["unrated_due_to_binding"] == [pos1["case_id"]]
              and pos1["case_id"] not in [p["case_id"] for p in d8["positions"]],
              {"rc": r8.returncode, "case": pos1["case_id"],
               "rejected": d8["coverage"]["unrated_due_to_binding"],
               "judged": d8["coverage"]["positions_judged"],
               "score_silently_preserved": pos1["case_id"] in
               [p["case_id"] for p in d8["positions"]]})

    after = {p: sha_file(p) for p in watched}
    check("X7_originals_untouched", before == after, {"unchanged": before == after})

    out = {"kind": "score_binding_guard_test_v1",
           "batch": LABEL, "mutated_case": CASE,
           "tests": results, "failures": failures,
           "pass": not failures,
           "note": ("攻击只在 tempfile 临时目录里复现；原件骨架/绑定/判词哈希前后一致；"
                    "本测试零模型调用。")}
    os.makedirs(os.path.dirname(EVIDENCE), exist_ok=True)
    json.dump(out, open(EVIDENCE, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("WROTE %s" % os.path.relpath(EVIDENCE, ROOT).replace("\\", "/"))
    print(json.dumps({"pass": out["pass"], "failures": failures}, ensure_ascii=False))
    return 0 if out["pass"] else 1


if __name__ == "__main__":
    sys.exit(main())
