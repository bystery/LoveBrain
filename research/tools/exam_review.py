"""tools/exam_review.py — 把一次真实答卷摊成"逐位初审表"

只做搬运与对齐：程序分、原始回复、评分卡判据、本轮实际注入的段落。
语义/互动判断由 worker 逐位填（不自动打分、不冒充用户口味）。"""
import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CARDS = os.path.join(ROOT, "benchmark", "dev_pilot_v02", "03_GRADING_CARDS.json")


def load_cards():
    with open(CARDS, encoding="utf-8") as f:
        return {c["case_id"]: c for c in json.load(f)}


def read_json(p, default=None):
    try:
        with open(p, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError):
        return default


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--exam", required=True, help="考试输出目录")
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    base = a.exam if os.path.isabs(a.exam) else os.path.join(ROOT, a.exam)
    cards = load_cards()
    index = read_json(os.path.join(base, "EXAM_INDEX.json"), {})
    out = a.out or os.path.join(base, "REVIEW_SHEET.md")

    L = []
    A = L.append
    A("# 首场基线答卷 · 逐位初审表（工程搬运，不含自动语义判决）")
    A("")
    A("授权 `%s` ｜ harness `%s` ｜ 身份 `%s…` ｜ model `%s` ｜ 温度 %s"
      % (index.get("authorization_id"), index.get("harness"),
         (index.get("harness_identity_sha256") or "")[:16],
         index.get("model"), index.get("temperature")))
    A("")
    A("读法：**程序分只管它能检查的合同**；事实语义、互动质量按卡判；"
      "「愿发」是用户口味，worker 不代投。合法 null / 短回复 / 合法不回不扣分。")
    A("")

    rows = []
    for run in index.get("runs") or []:
        out_dir = os.path.join(ROOT, run["out"])
        epm = read_json(os.path.join(out_dir, "episode_manifest.json"), {})
        A("## %s（%s）" % (run["episode_id"], epm.get("run_id")))
        A("")
        A("均分 %s ｜ veto %s ｜ 完整 %s ｜ 时延 %s"
          % (epm.get("mean_score10"), epm.get("veto_turns"),
             epm.get("complete"),
             json.dumps(epm.get("latency_ms"), ensure_ascii=False)))
        A("")
        for td in sorted(d for d in os.listdir(out_dir) if d.startswith("turn_")):
            tdp = os.path.join(out_dir, td)
            man = read_json(os.path.join(tdp, "manifest.json"), {})
            sc = read_json(os.path.join(tdp, "scoring.json"), {})
            cid = man.get("case_id")
            card = cards.get(cid) or {}
            raw = ""
            rp = os.path.join(tdp, "reply_raw.txt")
            if os.path.isfile(rp):
                raw = open(rp, encoding="utf-8", newline="").read()
            notes = ""
            npth = os.path.join(tdp, "mechanism_notes.txt")
            if os.path.isfile(npth):
                notes = open(npth, encoding="utf-8").read().strip()
            fails = [c for c in (sc.get("checks") or []) if not c.get("pass")]
            A("### %s ｜ 程序 %s/10%s" % (cid, man.get("score10"),
                                          " ｜ VETO" if man.get("veto") else ""))
            A("")
            A("- 本轮注入：`%s`" % (notes or "（无 notes）"))
            A("- 程序合同未过：%s" % ("; ".join(
                "%s(%s)" % (c.get("id"), c.get("desc")) for c in fails)
                if fails else "无"))
            A("- 用量/时延：tokens=%s ｜ %s ms ｜ calls=%s"
              % (json.dumps(man.get("tokens"), ensure_ascii=False),
                 man.get("latency_ms"), man.get("gateway_calls")))
            if card.get("required_reply_phrase"):
                A("- 卡要求措辞：**%s**" % card["required_reply_phrase"])
            if card.get("allowed_behaviors"):
                A("- 卡允许：%s" % " / ".join(card["allowed_behaviors"]))
            if card.get("failure_conditions"):
                A("- 卡失败条件：%s" % " / ".join(card["failure_conditions"]))
            if card.get("next_behavior_check"):
                A("- 后续检查：%s" % card["next_behavior_check"])
            A("")
            A("```text")
            A(raw.strip()[:1600] if raw.strip() else "（空回复）")
            A("```")
            A("")
            A("| 层 | 判决 | 依据 |")
            A("|---|---|---|")
            A("| 程序合同 |  |  |")
            A("| 事实语义 |  |  |")
            A("| 互动质量 |  |  |")
            A("")
            rows.append({"case_id": cid, "score10": man.get("score10"),
                         "veto": man.get("veto")})
        A("")

    A("## 程序分分布")
    A("")
    A("| case_id | score10 | veto |")
    A("|---|---|---|")
    for r in rows:
        A("| %s | %s | %s |" % (r["case_id"], r["score10"], r["veto"] or ""))
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")
    print(json.dumps({"review_sheet": os.path.relpath(out, ROOT),
                      "positions": len(rows)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
