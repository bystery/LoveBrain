# -*- coding: utf-8 -*-
"""tools/measure_w0504_projection.py — W05-04 记忆架构投影对比（零模型调用）。

对比对象（同一批 FS1 运行，同故事同轮）：
  E3（相关检索系）：calls.json user 字段的【相关已确认事实】/【暂定事项】投影块
  Bmin（最小事件原文窗）：calls.json user 字段的【真实历史消息】窗口
真值：stage2/<story>/reference.json 的 required_memory_atoms（source.case_id → 断言轮）。
指标：
  recall_by_age    = 全原子按 (当前轮 - 断言轮) 年龄分桶的召回曲线（架构差异核心）
  recall_required  = 本轮 required_this_turn 原子命中率（全库仅 E021 有 1 例，参考值）
  noise            = E3 投影行中与任何原子 bigram 重叠 <0.30 的行占比；投影/窗口字符预算对比
"""
import glob
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORIES = ["E021", "E026", "E032", "E033"]
_PUNCT = (chr(0x201C) + chr(0x201D) + chr(0x2018) + chr(0x2019)
          + chr(0x300C) + chr(0x300D) + chr(0xFF08) + chr(0xFF09)
          + chr(0x3001) + chr(0x3002) + chr(0xFF0C) + chr(0xFF1B) + chr(0xFF1A)
          + chr(0xFF01) + chr(0xFF1F) + chr(0x00B7) + chr(0x2014) + chr(0x2026)
          + "()-+")


def norm(t):
    t = t or ""
    for ch in _PUNCT:
        t = t.replace(ch, "")
    return re.sub(r"\s+", "", t)


def bigrams(s):
    return {s[i:i + 2] for i in range(len(s) - 1)} if len(s) >= 2 else ({s} if s else set())


def bg_recall(probe, hay):
    pb = bigrams(norm(probe))
    if not pb:
        return 0.0
    hb = bigrams(norm(hay))
    return len(pb & hb) / len(pb)


def calls_user(run_dir):
    p = os.path.join(run_dir, "calls.json")
    if not os.path.exists(p):
        return None
    j = json.load(open(p, encoding="utf-8"))
    for c in j:
        if c.get("tag") == "main":
            return c.get("user", "")
    return j[0].get("user", "") if j else ""


def extract_e3_facts(user):
    lines = []
    for marker in ("相关已确认事实", "暂定事项", "最近状态变更"):
        i = user.find("【" + marker)
        if i < 0:
            continue
        seg = user[i:]
        j = seg.find("【", 5)
        seg = seg[:j] if j > 0 else seg
        for ln in seg.split("\n"):
            if ln.strip().startswith("·"):
                lines.append(ln.strip())
    return lines


def extract_bmin_window(user):
    i = user.find("【真实历史消息")
    if i < 0:
        return ""
    seg = user[i:]
    j = seg.find("【", 6)
    return seg[:j] if j > 0 else seg


def load_atoms(story):
    p = os.path.join(ROOT, "benchmark", "stories_v2", "stage2", story, "reference.json")
    if not os.path.exists(p):
        return {}
    j = json.load(open(p, encoding="utf-8"))
    out = {}
    for cid, spec in (j.get("turns") or {}).items():
        m = re.search(r"T(\d+)$", cid)
        if not m:
            continue
        out[int(m.group(1))] = spec.get("required_memory_atoms") or []
    return out


def turn_no(run_dir):
    m = re.search(r"turn_(\d+)", run_dir)
    return int(m.group(1)) if m else 0


def atom_hit(atom, hay):
    probes = [atom.get("value", ""), (atom.get("source") or {}).get("quote", "")]
    probes = [p for p in probes if p and len(norm(p)) >= 4]
    if not probes:
        return None  # 无法判定的原子
    return any(bg_recall(p, hay) >= 0.60 for p in probes)


def measure():
    out = {"kind": "w0504_projection_comparison", "date": "2026-10-31",
           "hit_rule": "value/quote bigram-recall >= 0.60 vs arm memory medium",
           "stories": {}}
    for story in STORIES:
        atoms_by_turn = load_atoms(story)
        runs = {
            "E3": sorted(glob.glob(os.path.join(ROOT, "logs", "fs1_a_live2_20261030",
                                                "candidate_%s" % story, "out", "turn_*"))),
            "Bmin": sorted(glob.glob(os.path.join(ROOT, "logs", "fs1_b_live2_20261030",
                                                  "candidate_%s" % story, "out", "turn_*"))),
        }
        story_res = {}
        for arm, dirs in runs.items():
            rows = []
            for d in dirs:
                t = turn_no(d)
                user = calls_user(d)
                if user is None:
                    continue
                if arm == "E3":
                    lines = extract_e3_facts(user)
                    hay = "\n".join(lines)
                    med_chars = sum(len(l) for l in lines)
                else:
                    hay = extract_bmin_window(user)
                    lines = []
                    med_chars = len(hay)
                # 逐原子召回
                det = []
                age_hits = {}
                for a in atoms_by_turn.get(t, []):
                    src = (a.get("source") or {}).get("case_id", "")
                    m = re.search(r"T(\d+)$", src)
                    hit = atom_hit(a, hay)
                    age = t - int(m.group(1)) if m else None
                    det.append({"atom_id": a.get("atom_id"), "age": age, "hit": hit,
                                "required": bool(a.get("required_this_turn"))})
                    if hit is not None and age is not None and age >= 0:
                        b = min(age // 3, 4)
                        age_hits.setdefault(b, [0, 0])
                        age_hits[b][1] += 1
                        if hit:
                            age_hits[b][0] += 1
                # E3 噪声行：投影行与任何原子 bg<0.30
                noise = None
                if arm == "E3":
                    all_atoms = [x for v in atoms_by_turn.values() for x in v]
                    probes = [x.get("value", "") for x in all_atoms if x.get("value")]
                    noise_rows = [l for l in lines
                                  if probes and max(bg_recall(p, l) for p in probes) < 0.30]
                    noise = "%d/%d" % (len(noise_rows), len(lines))
                rows.append({"turn": t, "medium_chars": med_chars, "proj_lines": len(lines),
                             "noise": noise, "recall_by_age": {"%d-%d" % (3 * k, 3 * k + 2): "%d/%d" % tuple(v)
                                                              for k, v in sorted(age_hits.items())},
                             "detail": det})
            story_res[arm] = rows
        out["stories"][story] = story_res
    return out


def summarize(res):
    print("=" * 72)
    agg = {}
    for story, arms in res["stories"].items():
        for arm, rows in arms.items():
            chars = [r["medium_chars"] for r in rows]
            hh, nn, nd = 0, 0, 0
            for r in rows:
                for d in r["detail"]:
                    if d["hit"] is None:
                        nd += 1
                        continue
                    nn += 1
                    if d["hit"]:
                        hh += 1
            print("%s %-4s atoms_hit=%d/%d (unk %d) mean_medium_chars=%.0f" % (
                story, arm, hh, nn, nd, sum(chars) / max(1, len(chars))))
            a = agg.setdefault(arm, [hh, nn, nd, 0, 0])
            a[0] += hh; a[1] += nn; a[2] += nd
            a[3] += sum(chars); a[4] += len(chars)
    print("-" * 72)
    for arm, (hh, nn, nd, cs, cn) in agg.items():
        print("TOTAL %-4s recall=%d/%d=%.0f%% unk=%d mean_chars=%.0f" % (
            arm, hh, nn, 100 * hh / max(1, nn), nd, cs / max(1, cn)))


if __name__ == "__main__":
    res = measure()
    out = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030",
                       "W05-04_PROJECTION_COMPARISON_20261031.json")
    json.dump(res, open(out, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("written", out)
    summarize(res)
