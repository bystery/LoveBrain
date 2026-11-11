# -*- coding: utf-8 -*-
"""构建语义守卫控制集（P3-3.2：≥100 正负控制样本，2026-11-09）。

数据驱动：原子取自 GOLD_SIDECAR 全库真实原子（DEV 侧材料，评估侧可读），
回复文本按 6 类模板合成并贴标签；expected 判决由**类别定义**决定，非由守卫运行结果
反推（防止凑分）。输出 SEMANTIC_GUARD_CONTROLS_V1.json + 统计摘要打印。

类别 × 期望（真值表）：
  statement_affirm  → affirm（真坐实：取消族原子被重新坐实 = 应记违例）
  negation          → negated_mention（否定陈述不是坐实）
  question          → question_mention（疑问不是坐实）
  conditional       → hypothetical_mention（假设/条件不是坐实）
  quote             → quoted_mention（转述提及不是坐实）
  past_mention      → past_mention（取消事项的过去式提及 ≠ 复活）

已知限制类（rhetorical）：反问式肯定（"不是说X吗"）按 guard 期望登记——若守卫判 affirm
属诚实 FAIL，量化后决定该类是否降级人工复核（指导书 P3-3.2 尾款）。
"""
import glob
import json
import os
import random
import sys

GOLD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..",
                        "benchmark", "stories_v2", "GOLD_SIDECAR")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "SEMANTIC_GUARD_CONTROLS_V1.json")
random.seed(20261109)


def flatten_markers(atom):
    m = atom.get("affirm_markers") or (atom.get("enrichment") or {}).get("affirm_markers") or []
    out = []
    for g in m:
        if isinstance(g, str):
            out.append(g)
        elif isinstance(g, (list, tuple)):
            out.extend(x for x in g if isinstance(x, str))
    return out


def load_cancel_and_required_atoms():
    cancels, requireds = [], []
    for p in sorted(glob.glob(os.path.join(GOLD_DIR, "GOLD_*.json"))):
        base = os.path.basename(p)
        if base in ("GOLD_INDEX.json", "GOLD_QC_SAMPLE.json"):
            continue
        try:
            data = json.load(open(p, encoding="utf-8"))
        except Exception:
            continue
        for t in data.get("turns") or []:
            for a in (t.get("assertions") or []):
                if not isinstance(a, dict):
                    continue
                st = (a.get("status") or "").lower()
                mk = [x.strip() for x in flatten_markers(a) if isinstance(x, str) and 2 <= len(x.strip()) <= 24]
                if (st in {"cancelled", "canceled", "superseded", "retracted", "voided",
                           "revoked", "invalidated", "negated", "erroneous"} or a.get("negation") is True) and mk:
                    cancels.append({"atom_id": a.get("atom_id"), "markers": mk})
                elif a.get("required_this_turn") and st in {"asserted", "confirmed", "active",
                                                            "fulfilled", "resolved"} and mk:
                    requireds.append({"atom_id": a.get("atom_id"), "markers": mk})
    return cancels, requireds


def build_cases(cancels, requireds):
    cases = []
    random.shuffle(cancels)
    random.shuffle(requireds)
    # 15 个取消族原子 × 6 类
    used_markers = set()
    n_cancel_atoms = 0
    for a in cancels:
        if n_cancel_atoms >= 15:
            break
        m = a["markers"][0].strip()
        if m in used_markers or len(m) < 3:
            continue
        used_markers.add(m)
        n_cancel_atoms += 1
        cases += [
            {"atom": a["atom_id"], "type": "statement_affirm", "expect": "affirm",
             "markers": [m], "text": "嗯嗯，%s，就这么说定了。" % m},
            {"atom": a["atom_id"], "type": "negation", "expect": "guard",
             "markers": [m], "text": "其实没到%s那一步，你别多想。" % m},
            {"atom": a["atom_id"], "type": "question", "expect": "guard",
             "markers": [m], "text": "%s？这事定了吗？" % m},
            {"atom": a["atom_id"], "type": "conditional", "expect": "guard",
             "markers": [m], "text": "要是%s就麻烦了，得提前说。" % m},
            {"atom": a["atom_id"], "type": "quote", "expect": "guard",
             "markers": [m], "text": "她原话是「%s」，我复述一下。" % m},
            {"atom": a["atom_id"], "type": "past_mention", "expect": "guard",
             "markers": [m], "text": "上次取消的%s我还记得，挺可惜的。" % m},
        ]
    # 10 个 required 族原子 × 2 类（hit=必须命中 / miss=不出现）
    used2 = set()
    n_req = 0
    for a in requireds:
        if n_req >= 10:
            break
        m = a["markers"][0].strip()
        if m in used2 or len(m) < 3:
            continue
        used2.add(m)
        n_req += 1
        cases += [
            {"atom": a["atom_id"], "type": "required_hit", "expect": "affirm",
             "markers": [m], "text": "好，%s之前我把东西都准备好。" % m},
            {"atom": a["atom_id"], "type": "required_miss", "expect": "none",
             "markers": [m], "text": "收到，我这边尽快安排。"},
        ]
    # 已知限制类：反问式肯定 5 例（expect=guard，可能诚实 FAIL）
    rhet = [("周六爬山", "不是说周六爬山吗，怎么又变了"),
            ("周三交稿", "你不是说周三交稿吗"),
            ("爸妈知情", "不是说爸妈知情了吗"),
            ("改成周五", "不是说改成周五了吗"),
            ("他来取件", "不是他来取件吗")]
    for m, t in rhet:
        cases.append({"atom": None, "type": "rhetorical", "expect": "guard",
                      "markers": [m], "text": t})
    return cases


def main():
    cancels, requireds = load_cancel_and_required_atoms()
    print("available atoms: cancel-family=%d required=%d" % (len(cancels), len(requireds)))
    cases = build_cases(cancels, requireds)
    n_by_type = {}
    for c in cases:
        n_by_type[c["type"]] = n_by_type.get(c["type"], 0) + 1
    out = {"kind": "semantic_guard_controls", "version": "controls_v1_20261109",
           "guards_version_target": "semantic_guards_v1",
           "source": "GOLD_SIDECAR 全库真实原子（评估侧只读）+ 6 类模板（标签=类别定义）",
           "n_cases": len(cases), "n_by_type": n_by_type, "cases": cases}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("written:", OUT, "n_cases=", len(cases), n_by_type)
    assert len(cases) >= 100, "控制集不足 100"


if __name__ == "__main__":
    main()
