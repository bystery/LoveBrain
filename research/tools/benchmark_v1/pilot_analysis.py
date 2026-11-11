# -*- coding: utf-8 -*-
"""Benchmark V1 Phase 2 · Pilot 判别力证据分析（外部指导书 §10.3 的工具面）。

从 aggregate_pilot 产出的 PILOT_RESULTS.csv（列：arm_id/episode_id/case_id/turn_index/
action/primary_text/parse_ok/contract_ok/error_type/score10/veto/latency_ms/diag_* 等）
计算五类判别力证据指标：

  1. 专题识别检查   —— 每轮 primary_text 与该篇 primary_topic 关键词族的粗匹配；
                       词族内嵌于本工具（源自
                       refs/benchmark_v1_20261107/phase2/TOPIC_RUBRIC_V1.md §2 各专题
                       "必测能力点"的可操作化词族，逐族注明来源节号）；
                       报告 per-topic 识别率，两臂分开。
  2. 臂间相似度     —— 同 (episode, turn) 的 DIRECT 与 CURRENT primary_text：
                       strip 后完全相同计数 + difflib.SequenceMatcher ratio>=0.9
                       计"近雷同"；按篇聚合"高雷同篇"清单（>=70% 轮位 ratio>=0.9）。
  3. 臂间差异       —— per-topic 的 contract_ok 率差 / diag_required_hit_rate 差 /
                       diag_cancel_affirm 违例数差 / score10 均值差（有值时）/
                       veto 数差；均输出绝对差与方向。
  4. 退化形态检测   —— primary_text 长度分布（p10/p50/p90）、万能句命中率
                       （5 句清单，两臂分开）、同臂跨轮重复开头 8-gram top3 与最高占比。
  5. 折损位清单     —— error_type 非空的 (episode, turn, arm) 全列表；
                       缺臂位（如 E307/E308 无 CURRENT 行）单列 missing_positions，
                       如实计入 missing，不当 0 分、不进任何 rate 分母。

输出：--out JSON（全部原始数字）+ stdout 人读摘要表。
本工具**不做任何结论性断言**（"判别力成立/不成立"等判断由 Worker 写报告时人工作出）；
词族/万能句命中是粗匹配证据，不是行为锚判定（TOPIC_RUBRIC_V1.md §0.1 评分自由度宪法、
§2 各专题行为锚均明确禁止关键词命中判定替代行为分档）。

纪律：python -I 可跑、标准库 only；topic 映射默认读
benchmark/stories_v2/wave4_dev/*/exposure.json 的 primary_topic，旧题（E063/LRE121）
回退 refs/benchmark_v1_20261107/phase1/DATASET_INVENTORY.csv；
不读 logs/、candidates/、B 线、GOLD_SIDECAR；不修改 runs/ 下任何文件。
"""
import argparse
import csv
import datetime
import difflib
import hashlib
import json
import os
import sys
from collections import Counter

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))
DEFAULT_EXPOSURE_DIR = os.path.join(_REPO, "benchmark", "stories_v2", "wave4_dev")
DEFAULT_INVENTORY_CSV = os.path.join(_REPO, "refs", "benchmark_v1_20261107",
                                     "phase1", "DATASET_INVENTORY.csv")

ARMS = ("DIRECT", "COT", "LEGACY", "CURRENT")
SIM_ARMS = ("DIRECT", "CURRENT")
DIFF_KEYS = ("contract_ok_rate", "diag_required_hit_rate",
             "diag_cancel_affirm_violations", "score10_mean", "veto_count")
ANALYSIS_VERSION = "pilot_analysis_v0"
NEAR_DUP_RATIO = 0.9
HIGH_DUP_SHARE = 0.70
OPENING_GRAM_N = 8
KEY_NEAR = "near_dup_ratio_ge_%s" % NEAR_DUP_RATIO

# --------------------------------------------------------------------------
# 内嵌词表（证据用粗匹配，非行为锚判定）
# 来源：refs/benchmark_v1_20261107/phase2/TOPIC_RUBRIC_V1.md §2 各专题"必测能力点"
# 的可操作化词族（通用主题级词汇，不围绕具体题号/素材特化）。
# --------------------------------------------------------------------------
TOPIC_KEYWORDS = {
    "T01": {
        "source": "TOPIC_RUBRIC_V1.md §2 T01 必测能力点（情境幽默/复用细节接话/轻快口语 register）",
        "keywords": ["哈哈", "嘿嘿", "笑", "梗", "绝了", "离谱", "好家伙", "摸鱼", "搭子",
                     "走起", "凑热闹", "开黑", "吃瓜", "摆烂", "整活"],
    },
    "T02": {
        "source": "TOPIC_RUBRIC_V1.md §2 T02 必测能力点（暧昧义承接/推进-退让节奏/半开玩笑试探）",
        "keywords": ["心动", "喜欢", "暧昧", "撩", "脸红", "害羞", "想我", "抱抱", "亲",
                     "陪你", "专属", "小心思", "逗你", "来电", "暗恋", "紧张"],
    },
    "T03": {
        "source": "TOPIC_RUBRIC_V1.md §2 T03 必测能力点（论点交锋/立场/给台阶/换挡后思想交流）",
        "keywords": ["观点", "立场", "价值观", "分歧", "认同", "坚持", "妥协", "台阶",
                     "换个角度", "说正经", "认真说", "想清楚", "值得", "更重要", "优先",
                     "让步", "底线"],
    },
    "T04": {
        "source": "TOPIC_RUBRIC_V1.md §2 T04 必测能力点（接钩给具体下一步/自出钩/邀约时机）",
        "keywords": ["约", "见面", "下次", "要不要", "一起去", "我来订", "我订", "等你",
                     "吱一声", "挑个", "定个", "占个", "周末", "走一趟", "改天", "抽空"],
    },
    "T05": {
        "source": "TOPIC_RUBRIC_V1.md §2 T05 必测能力点（读懂情绪真意/关心具体化/安慰不越界）",
        "keywords": ["辛苦", "累", "心疼", "压力", "撑", "委屈", "难过", "烦躁", "焦虑",
                     "怕", "喘口气", "歇", "陪你", "别硬撑", "慢慢来", "情绪", "睡不着"],
    },
    "T06": {
        "source": "TOPIC_RUBRIC_V1.md §2 T06 必测能力点（行为方向契约/口吻改写不换事实）",
        "keywords": ["改好", "改成", "换了个说法", "重新写", "重新组织", "按你说的", "照办",
                     "收到", "口吻", "换种", "换法", "遵命", "办妥", "改完", "润色", "改写"],
    },
    "T07": {
        "source": "TOPIC_RUBRIC_V1.md §2 T07 必测能力点（账目/报错数/显式不确定/口径来源）",
        "keywords": ["算", "账", "对不上", "单价", "总价", "总额", "核实", "不确定", "存疑",
                     "口径", "来源", "大概", "至少", "指南", "医生", "网上的", "先核",
                     "再核", "差了"],
    },
    "T08": {
        "source": "TOPIC_RUBRIC_V1.md §2 T08 必测能力点（跨轮回指/记忆正确取用/KB 约束遵守）",
        "keywords": ["上次", "上回", "之前", "那天", "你说", "你提", "你讲", "记得",
                     "老规矩", "老样子", "照旧", "说过", "记着", "那会儿", "按之前"],
    },
    "T09": {
        "source": "TOPIC_RUBRIC_V1.md §2 T09 必测能力点（更正链新口径/取消维持/话题转场/收尾留口）",
        "keywords": ["更正", "取消", "先不", "改成", "重新", "维持", "新时间", "新口径",
                     "转场", "先这样", "回头说", "到了吱", "说一声", "不弄了", "重来",
                     "改口", "按新"],
    },
    "T10": {
        "source": "TOPIC_RUBRIC_V1.md §2 T10 必测能力点（CF 跟随/边界拒绝/诱导不坐实）",
        "keywords": ["不对", "不是", "确认", "记岔", "说错", "没答应", "不方便", "不透露",
                     "隐私", "保密", "拒绝", "你没说", "她没说", "别听", "记错", "哪有",
                     "明明"],
    },
}

# 万能句 5 句：任务指定 3 句 + rubric 来源 2 句（见 source 注）。
UNIVERSAL_PHRASES = ["我理解你", "你已经很不容易了", "都会过去的", "多喝热水",
                     "一切都会好起来"]
UNIVERSAL_PHRASES_SOURCE = (
    "任务指定 3 句（我理解你/你已经很不容易了/都会过去的）+ TOPIC_RUBRIC_V1.md "
    "§2 T05 必避免退化形态'万能句'、§2 T01 首批实证教训①（C1 万能句生存空间检查，"
    "'我理解你/多喝热水'式）；'一切都会好起来'为同族泛化安慰代表句，凑足 5 句"
)


# --------------------------------------------------------------------------
# 基础工具（_pctl 与 aggregate_pilot 同算法，保持口径一致）
# --------------------------------------------------------------------------
def _pctl(xs, q):
    if not xs:
        return None
    xs = sorted(xs)
    k = max(0, min(len(xs) - 1, int(round(q / 100.0 * (len(xs) - 1)))))
    return xs[k]


def _to_bool(v):
    return str(v).strip().lower() in ("true", "1", "yes", "y")


def _to_int(v, default=None):
    try:
        return int(str(v).strip())
    except Exception:
        return default


def _to_float(v):
    try:
        return float(str(v).strip())
    except Exception:
        return None


def _arm_order(arm):
    return ARMS.index(arm) if arm in ARMS else len(ARMS)


def _r4(x):
    return "N/A" if x is None else "%.4f" % x


def load_rows(csv_path):
    with open(csv_path, "r", encoding="utf-8-sig", newline="") as f:
        raw = list(csv.DictReader(f))
    rows = []
    for r in raw:
        rows.append({
            "arm_id": (r.get("arm_id") or "").strip(),
            "episode_id": (r.get("episode_id") or "").strip(),
            "case_id": (r.get("case_id") or "").strip(),
            "turn_index": _to_int(r.get("turn_index")),
            "action": (r.get("action") or "").strip(),
            "text": r.get("primary_text") or "",
            "parse_ok": _to_bool(r.get("parse_ok")),
            "contract_ok": _to_bool(r.get("contract_ok")),
            "error_type": (r.get("error_type") or "").strip(),
            "score10": _to_float(r.get("score10")),
            "veto": (r.get("veto") or "").strip(),
            "diag_status": (r.get("diag_status") or "").strip(),
            "diag_cancel_affirm": _to_int(r.get("diag_cancel_affirm"), 0),
            "diag_required_hits": _to_int(r.get("diag_required_hits"), 0),
            "diag_required_total": _to_int(r.get("diag_required_total"), 0),
        })
    rows.sort(key=lambda x: (_arm_order(x["arm_id"]), x["episode_id"],
                             x["turn_index"] if x["turn_index"] is not None else 10 ** 9,
                             x["case_id"]))
    return rows


# --------------------------------------------------------------------------
# topic 映射（exposure.json 优先，phase1 inventory 回退）
# --------------------------------------------------------------------------
def _absorb_exposure_dir(dirpath, tmap, errors, label):
    for name in sorted(os.listdir(dirpath)):
        p = os.path.join(dirpath, name)
        if not os.path.isdir(p):
            continue
        ep_json = os.path.join(p, "exposure.json")
        if not os.path.isfile(ep_json):
            continue
        try:
            with open(ep_json, "r", encoding="utf-8") as f:
                x = json.load(f)
            topic = (x.get("primary_topic") or "").strip()
        except Exception as exc:
            errors.append({"source": ep_json, "error": str(exc)})
            continue
        if topic:
            tmap[name] = {"topic": topic, "source": label}


def _absorb_inventory(csv_path, tmap, errors):
    try:
        with open(csv_path, "r", encoding="utf-8-sig", newline="") as f:
            for row in csv.DictReader(f):
                ep = (row.get("episode_id") or "").strip()
                topic = (row.get("primary_topic") or "").strip()
                if ep and topic:
                    tmap.setdefault(ep, {"topic": topic,
                                         "source": "phase1_DATASET_INVENTORY"})
    except Exception as exc:
        errors.append({"source": csv_path, "error": str(exc)})


def build_topic_map(explicit):
    tmap, errors, sources = {}, [], []
    if explicit:
        mode = "explicit(--topics)"
        p = os.path.abspath(explicit)
        if os.path.isdir(p):
            sources.append(p)
            _absorb_exposure_dir(p, tmap, errors, label="exposure_dir")
        elif os.path.isfile(p):
            sources.append(p)
            _absorb_inventory(p, tmap, errors)
        else:
            raise SystemExit("--topics 路径不存在（需 exposure 目录或 inventory CSV）: %s" % p)
    else:
        mode = "default(exposure_dir 优先 + phase1 inventory 回退)"
        if os.path.isdir(DEFAULT_EXPOSURE_DIR):
            sources.append(DEFAULT_EXPOSURE_DIR)
            _absorb_exposure_dir(DEFAULT_EXPOSURE_DIR, tmap, errors,
                                 label="wave4_dev_exposure")
        else:
            errors.append({"source": DEFAULT_EXPOSURE_DIR,
                           "error": "默认 exposure 目录不存在"})
        if os.path.isfile(DEFAULT_INVENTORY_CSV):
            sources.append(DEFAULT_INVENTORY_CSV)
            _absorb_inventory(DEFAULT_INVENTORY_CSV, tmap, errors)
        else:
            errors.append({"source": DEFAULT_INVENTORY_CSV,
                           "error": "默认 inventory CSV 不存在"})
    return tmap, mode, sources, errors


# --------------------------------------------------------------------------
# 指标 0：覆盖 / 缺臂位
# --------------------------------------------------------------------------
def coverage_block(rows, tmap):
    arms_present = sorted({r["arm_id"] for r in rows}, key=_arm_order)
    pos_arms = {}          # (episode_id, case_id) -> set(arm)
    turn_of = {}           # case_id -> turn_index（首次非空）
    rows_ep_arm = Counter()
    for r in rows:
        pos_arms.setdefault((r["episode_id"], r["case_id"]), set()).add(r["arm_id"])
        if r["turn_index"] is not None and r["case_id"] not in turn_of:
            turn_of[r["case_id"]] = r["turn_index"]
        rows_ep_arm[(r["episode_id"], r["arm_id"])] += 1
    seen = Counter((r["arm_id"], r["case_id"]) for r in rows)
    dup = sorted("%s/%s×%d" % (a, c, n) for (a, c), n in seen.items() if n > 1)

    episodes = {}
    missing_positions = []
    missing_total = Counter()
    eps = sorted({e for e, _ in pos_arms})
    for ep in eps:
        cids = sorted({c for e, c in pos_arms if e == ep},
                      key=lambda c: (turn_of.get(c, 10 ** 9), c))
        tinfo = tmap.get(ep)
        arms_blk, miss_blk = {}, {}
        for arm in arms_present:
            present = [c for c in cids if arm in pos_arms[(ep, c)]]
            absent = [c for c in cids if arm not in pos_arms[(ep, c)]]
            arms_blk[arm] = {"rows": rows_ep_arm.get((ep, arm), 0),
                             "positions": len(present)}
            miss_blk[arm] = absent
            for c in absent:
                missing_positions.append({"episode_id": ep, "case_id": c,
                                          "turn_index": turn_of.get(c),
                                          "arm_id": arm})
                missing_total[arm] += 1
        episodes[ep] = {
            "topic": tinfo["topic"] if tinfo else None,
            "topic_source": tinfo["source"] if tinfo else None,
            "union_positions": len(cids),
            "arms": arms_blk,
            "missing_case_ids": miss_blk,
        }
    return arms_present, episodes, missing_positions, dict(missing_total), dup


# --------------------------------------------------------------------------
# 指标 1：专题识别检查（词族粗匹配，两臂分开）
# --------------------------------------------------------------------------
def recognition_block(rows, tmap, arms_present):
    groups = {}
    no_topic_rows = {}
    for r in rows:
        tinfo = tmap.get(r["episode_id"])
        if not tinfo:
            no_topic_rows[r["arm_id"]] = no_topic_rows.get(r["arm_id"], 0) + 1
            continue
        groups.setdefault((tinfo["topic"], r["arm_id"]), []).append(r)
    per_topic = {}
    for topic in sorted({t for t, _ in groups}):
        eps = sorted({r["episode_id"] for arm in arms_present
                      for r in groups.get((topic, arm), [])})
        arms_blk = {}
        for arm in arms_present:
            rs = groups.get((topic, arm), [])
            texts = [r for r in rs if r["text"].strip()]
            kws = TOPIC_KEYWORDS.get(topic, {}).get("keywords", [])
            hits = [r["case_id"] for r in texts
                    if any(k in r["text"].lower() for k in kws)]
            arms_blk[arm] = {
                "rows": len(rs),
                "no_text_rows": len(rs) - len(texts),
                "recognized": len(hits),
                "rate": round(len(hits) / len(texts), 4) if texts else None,
                "hit_case_ids": hits,
            }
        per_topic[topic] = {"episodes": eps, "arms": arms_blk}
    return per_topic, no_topic_rows


# --------------------------------------------------------------------------
# 指标 2：臂间相似度 / 机械雷同检测（DIRECT vs CURRENT）
# --------------------------------------------------------------------------
def similarity_block(rows):
    pos = {}
    for r in rows:
        if r["arm_id"] in SIM_ARMS:
            pos.setdefault(r["case_id"], {})[r["arm_id"]] = r
    order = []
    for cid, byarm in pos.items():
        any_row = byarm.get("DIRECT") or byarm.get("CURRENT")
        order.append((any_row["episode_id"],
                      any_row["turn_index"] if any_row["turn_index"] is not None else 10 ** 9,
                      cid))
    order.sort()

    pairs, one_side, both_no_text = [], [], []
    per_ep = {}
    for ep, ti, cid in order:
        byarm = pos[cid]
        d, c = byarm.get("DIRECT"), byarm.get("CURRENT")
        if d is None or c is None:
            continue  # 单臂缺行：已在 coverage.missing_positions 记 missing
        dt, ct = d["text"].strip(), c["text"].strip()
        if not dt and not ct:
            both_no_text.append({"case_id": cid, "episode_id": ep, "turn_index": ti})
            continue
        if not dt or not ct:
            one_side.append({"case_id": cid, "episode_id": ep, "turn_index": ti,
                             "arm_no_text": "DIRECT" if not dt else "CURRENT"})
            continue
        ratio = difflib.SequenceMatcher(None, dt, ct, autojunk=False).ratio()
        identical = dt == ct
        pairs.append({"case_id": cid, "episode_id": ep, "turn_index": ti,
                      "identical": identical, "ratio": round(ratio, 4)})
        blk = per_ep.setdefault(ep, {"pairs": 0, "identical": 0, "near_dup": 0,
                                     "ratio_sum": 0.0})
        blk["pairs"] += 1
        blk["identical"] += int(identical)
        blk["near_dup"] += int(ratio >= NEAR_DUP_RATIO)
        blk["ratio_sum"] += ratio

    eps_all = sorted({r["episode_id"] for r in rows if r["arm_id"] in SIM_ARMS})
    episodes_meta = {}
    for ep in eps_all:
        blk = per_ep.get(ep)
        if not blk:
            episodes_meta[ep] = {"pairs": 0, "identical": 0, "near_dup": 0,
                                 "near_dup_share": None, "mean_ratio": None,
                                 "high_dup_episode": False}
            continue
        n = blk["pairs"]
        share = blk["near_dup"] / n
        episodes_meta[ep] = {
            "pairs": n, "identical": blk["identical"], "near_dup": blk["near_dup"],
            "near_dup_share": round(share, 4),
            "mean_ratio": round(blk["ratio_sum"] / n, 4),
            "high_dup_episode": share >= HIGH_DUP_SHARE,
        }
    high = [{"episode_id": ep,
             "pairs": m["pairs"], "identical": m["identical"],
             "near_dup": m["near_dup"], "near_dup_share": m["near_dup_share"]}
            for ep, m in sorted(episodes_meta.items()) if m["high_dup_episode"]]
    ident = sum(1 for p_ in pairs if p_["identical"])
    near = sum(1 for p_ in pairs if p_["ratio"] >= NEAR_DUP_RATIO)
    summary = {
        "pair_positions_with_both_texts": len(pairs),
        "identical": ident,
        KEY_NEAR: near,
        "near_dup_share": round(near / len(pairs), 4) if pairs else None,
        "one_side_no_text_positions": len(one_side),
        "both_no_text_positions": len(both_no_text),
        "high_dup_episode_count": len(high),
    }
    return {
        "definition": ("同 case_id 的 DIRECT 与 CURRENT primary_text；"
                       "identical=strip 后完全相同；near_dup=SequenceMatcher ratio>=%s"
                       "（autojunk=False）；高雷同篇=篇内 >=%d%% 轮位 near_dup"
                       % (NEAR_DUP_RATIO, HIGH_DUP_SHARE * 100)),
        "pairs": pairs,
        "one_side_no_text": one_side,
        "both_no_text": both_no_text,
        "per_episode": episodes_meta,
        "high_dup_episodes": high,
        "summary": summary,
    }


# --------------------------------------------------------------------------
# 指标 3：臂间差异（per-topic + overall）
# --------------------------------------------------------------------------
def arm_aggregate(rs):
    n = len(rs)
    contract_n = sum(1 for r in rs if r["contract_ok"])
    diag_ok = [r for r in rs if r["diag_status"] == "OK"]
    req_total = sum(r["diag_required_total"] or 0 for r in diag_ok)
    req_hits = sum(r["diag_required_hits"] or 0 for r in diag_ok)
    s10 = [r["score10"] for r in rs if r["score10"] is not None]
    return {
        "rows": n,
        "contract_ok_n": contract_n,
        "contract_ok_rate": round(contract_n / n, 4) if n else None,
        "diag_ok_rows": len(diag_ok),
        "diag_required_total": req_total,
        "diag_required_hits": req_hits,
        "diag_required_hit_rate": round(req_hits / req_total, 4) if req_total else None,
        "diag_cancel_affirm_violations": sum(r["diag_cancel_affirm"] or 0 for r in diag_ok),
        "score10_rows": len(s10),
        "score10_mean": round(sum(s10) / len(s10), 4) if s10 else None,
        "veto_count": sum(1 for r in rs if r["veto"]),
    }


def diff_pair(dagg, cagg):
    out = {}
    for key in DIFF_KEYS:
        dv, cv = dagg.get(key), cagg.get(key)
        if dv is None or cv is None:
            out[key] = {"direct": dv, "current": cv,
                        "signed_diff_direct_minus_current": None,
                        "abs_diff": None, "direction": "n/a"}
            continue
        sd = round(dv - cv, 4)
        direction = ("DIRECT_higher" if sd > 0
                     else ("CURRENT_higher" if sd < 0 else "equal"))
        out[key] = {"direct": dv, "current": cv,
                    "signed_diff_direct_minus_current": sd,
                    "abs_diff": abs(sd), "direction": direction}
    return out


def arm_diff_block(rows, tmap, arms_present):
    groups = {}
    for r in rows:
        tinfo = tmap.get(r["episode_id"])
        t = tinfo["topic"] if tinfo else "__no_topic__"
        groups.setdefault(t, []).append(r)
    per_topic = {}
    for t in sorted(groups):
        rs = groups[t]
        by_arm = {arm: arm_aggregate([r for r in rs if r["arm_id"] == arm])
                  for arm in arms_present}
        by_arm = {arm: v for arm, v in by_arm.items() if v["rows"]}
        has_pair = "DIRECT" in by_arm and "CURRENT" in by_arm
        per_topic[t] = {
            "rows": {arm: (by_arm[arm]["rows"] if arm in by_arm else 0)
                     for arm in arms_present},
            "arms": by_arm,
            "diff_direct_vs_current": (diff_pair(by_arm["DIRECT"], by_arm["CURRENT"])
                                       if has_pair else None),
        }
    overall_by_arm = {arm: arm_aggregate([r for r in rows if r["arm_id"] == arm])
                      for arm in arms_present}
    has_pair = "DIRECT" in overall_by_arm and "CURRENT" in overall_by_arm
    overall = {
        "rows": {arm: overall_by_arm[arm]["rows"] for arm in arms_present},
        "arms": overall_by_arm,
        "diff_direct_vs_current": (diff_pair(overall_by_arm["DIRECT"],
                                             overall_by_arm["CURRENT"])
                                   if has_pair else None),
    }
    return {
        "direction_convention": ("signed_diff_direct_minus_current；direction ∈ "
                                 "DIRECT_higher/CURRENT_higher/equal；n/a=一侧无该读数"),
        "per_topic": per_topic,
        "overall": overall,
    }


# --------------------------------------------------------------------------
# 指标 4：退化形态检测
# --------------------------------------------------------------------------
def degeneration_block(rows, arms_present):
    text_len, universal, grams = {}, {}, {}
    for arm in arms_present:
        rs = [r for r in rows if r["arm_id"] == arm]
        texts = [r["text"].strip() for r in rs if r["text"].strip()]
        lengths = [len(t) for t in texts]
        text_len[arm] = {
            "n_rows": len(rs), "n_texts": len(texts),
            "min": min(lengths) if lengths else None,
            "p10": _pctl(lengths, 10), "p50": _pctl(lengths, 50),
            "p90": _pctl(lengths, 90),
            "max": max(lengths) if lengths else None,
        }
        per_phrase = {p: sum(1 for t in texts if p in t) for p in UNIVERSAL_PHRASES}
        any_hit = sum(1 for t in texts if any(p in t for p in UNIVERSAL_PHRASES))
        universal[arm] = {
            "n_texts": len(texts),
            "per_phrase_counts": per_phrase,
            "rows_with_any_hit": any_hit,
            "rate": round(any_hit / len(texts), 4) if texts else None,
        }
        openings = [t[:OPENING_GRAM_N] for t in texts if len(t) >= OPENING_GRAM_N]
        cnt = Counter(openings)
        n_open = len(openings)
        top3 = sorted(cnt.items(), key=lambda kv: (-kv[1], kv[0]))[:3]
        repeated = sorted(((g, c) for g, c in cnt.items() if c >= 2),
                          key=lambda kv: (-kv[1], kv[0]))
        best = repeated[0] if repeated else None
        grams[arm] = {
            "n_openings": n_open,
            "top3": [{"gram": g, "count": c,
                      "share": round(c / n_open, 4) if n_open else None}
                     for g, c in top3],
            "max_repeated": ({"gram": best[0], "count": best[1],
                              "share": round(best[1] / n_open, 4)}
                             if best and n_open else None),
        }
    return {
        "text_length": text_len,
        "universal_phrases": {
            "phrases": UNIVERSAL_PHRASES,
            "source": UNIVERSAL_PHRASES_SOURCE,
            "per_arm": universal,
        },
        "repeated_opening_%dgram" % OPENING_GRAM_N: grams,
        "note": ("开头 %d-gram 取 strip 后前 %d 个字符（含空格）；长度<%d 的回复不计入 "
                 "openings；count>=2 视为跨轮重复；share 分母=n_openings"
                 % (OPENING_GRAM_N, OPENING_GRAM_N, OPENING_GRAM_N)),
    }


# --------------------------------------------------------------------------
# 指标 5：折损位清单
# --------------------------------------------------------------------------
def error_positions(rows):
    return [{"arm_id": r["arm_id"], "episode_id": r["episode_id"],
             "case_id": r["case_id"], "turn_index": r["turn_index"],
             "error_type": r["error_type"]}
            for r in rows if r["error_type"]]


# --------------------------------------------------------------------------
# stdout 人读摘要（不做结论性断言）
# --------------------------------------------------------------------------
def print_summary(p, out_path):
    inp = p["inputs"]
    cov = p["coverage"]
    sim = p["arm_similarity"]
    degen = p["degeneration"]
    arms = inp["arms_present"]
    rows_by_arm = p["arm_diff"]["overall"]["rows"]
    lines = []
    a = lines.append
    a("== %s · Pilot 判别力证据（只出证据，不做判别力结论） ==" % p["analysis_version"])
    a("csv=%s" % inp["csv_path"])
    a("sha256=%s | rows=%d | arms=%s | episodes=%d"
      % (inp["csv_sha256"][:16], inp["rows_total"],
         " ".join("%s:%d" % (arm, rows_by_arm.get(arm, 0)) for arm in arms),
         inp["episodes_total"]))
    a("topic 映射=%s | topics_missing=%s"
      % (inp["topics_source_mode"], ",".join(inp["topics_missing"]) or "无"))
    a("")
    errs = p["error_positions"]
    a("[折损位] error_type 非空: %d 位%s" % (
        len(errs), "" if not errs else " " + ", ".join(
            "%s/%s(%s)" % (e["arm_id"], e["case_id"], e["error_type"])
            for e in errs)))
    a("         缺臂位(missing): %s"
      % " ".join("%s=%d" % (arm, cov["missing_positions_total"].get(arm, 0))
                 for arm in arms))
    a("")
    a("[专题识别率] 词族粗匹配（证据读数，非质量判定）；rate 分母=有 primary_text 的轮位")
    for topic, blk in sorted(p["topic_recognition"]["per_topic"].items()):
        cells = []
        for arm in arms:
            x = blk["arms"].get(arm, {})
            den = x.get("rows", 0) - x.get("no_text_rows", 0)
            if x.get("rate") is None:
                cells.append("%s N/A(n_text=0)" % arm)
            else:
                cells.append("%s %d/%d=%.4f" % (arm, x["recognized"], den, x["rate"]))
        a("  %-4s %s" % (topic, " | ".join(cells)))
    a("")
    s = sim["summary"]
    a("[臂间相似] DIRECT vs CURRENT：%s" % sim["definition"])
    a("  pairs=%d identical=%d near_dup=%s share=%s one_side_no_text=%d both_no_text=%d"
      % (s["pair_positions_with_both_texts"], s["identical"], s[KEY_NEAR],
         _r4(s["near_dup_share"]), s["one_side_no_text_positions"],
         s["both_no_text_positions"]))
    if sim["high_dup_episodes"]:
        a("  高雷同篇(篇内>=%.0f%%轮位 near_dup): %s" % (
            HIGH_DUP_SHARE * 100, ", ".join(
                "%s(%d/%d=%.4f)" % (h["episode_id"], h["near_dup"], h["pairs"],
                                    h["near_dup_share"])
                for h in sim["high_dup_episodes"])))
    else:
        a("  高雷同篇(篇内>=%.0f%%轮位 near_dup): 无" % (HIGH_DUP_SHARE * 100))
    a("")
    a("[臂间差异] %s" % p["arm_diff"]["direction_convention"])
    for topic, blk in sorted(p["arm_diff"]["per_topic"].items()):
        a("  %-4s rows=%s" % (topic, " ".join("%s=%d" % (arm, n)
                                              for arm, n in blk["rows"].items())))
        df = blk.get("diff_direct_vs_current")
        if df is None:
            a("        diff=N/A（缺 DIRECT/CURRENT 之一，缺臂位见 missing_positions）")
            continue
        for key in DIFF_KEYS:
            x = df[key]
            if x["direction"] == "n/a":
                a("        %-30s D=%s C=%s Δ=N/A"
                  % (key, _r4(x["direct"]) if isinstance(x["direct"], float)
                     else x["direct"],
                     _r4(x["current"]) if isinstance(x["current"], float)
                     else x["current"]))
            else:
                a("        %-30s D=%s C=%s Δ=%s(%s) %s"
                  % (key,
                     _r4(x["direct"]) if isinstance(x["direct"], float) else x["direct"],
                     _r4(x["current"]) if isinstance(x["current"], float) else x["current"],
                     x["signed_diff_direct_minus_current"], x["abs_diff"],
                     x["direction"]))
    a("")
    a("[退化形态]")
    for arm in arms:
        tl = degen["text_length"][arm]
        up = degen["universal_phrases"]["per_arm"][arm]
        gr = degen["repeated_opening_%dgram" % OPENING_GRAM_N][arm]
        top = ", ".join("%r×%d(share=%s)" % (g["gram"], g["count"], _r4(g["share"]))
                        for g in gr["top3"]) or "无"
        a("  %s: len p10/p50/p90=%s/%s/%s (n_text=%d) | 万能句 %d/%d=%s | 开头%dgram top3: %s"
          % (arm, tl["p10"], tl["p50"], tl["p90"], tl["n_texts"],
             up["rows_with_any_hit"], up["n_texts"], _r4(up["rate"]),
             OPENING_GRAM_N, top))
    a("")
    a("全部原始数字 → %s" % out_path)
    print("\n".join(lines))


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------
def main(argv=None):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    ap = argparse.ArgumentParser(
        description="Benchmark V1 Phase 2 pilot 判别力证据分析（只出证据，不做结论）")
    ap.add_argument("--csv", required=True,
                    help="aggregate_pilot 产出的 PILOT_RESULTS.csv 路径")
    ap.add_argument("--topics", default=None,
                    help="episode→topic 映射源：exposure 目录（含 */exposure.json）或 "
                         "DATASET_INVENTORY 式 CSV；缺省=默认 exposure 目录 + phase1 "
                         "inventory 回退（旧题 E063/LRE121 走 inventory）")
    ap.add_argument("--out", required=True, help="输出 JSON 路径（不写入 runs/）")
    args = ap.parse_args(argv)

    csv_path = os.path.abspath(args.csv)
    with open(csv_path, "rb") as f:
        csv_sha = hashlib.sha256(f.read()).hexdigest()
    rows = load_rows(csv_path)
    tmap, mode, sources, terrors = build_topic_map(args.topics)

    arms_present, episodes, missing_positions, missing_total, dup = \
        coverage_block(rows, tmap)
    eps_used = sorted(episodes)
    topics_resolved = {ep: {"topic": episodes[ep]["topic"],
                            "source": episodes[ep]["topic_source"]}
                       for ep in eps_used}
    topics_missing = [ep for ep in eps_used if episodes[ep]["topic"] is None]

    rec, no_topic_rows = recognition_block(rows, tmap, arms_present)
    sim = similarity_block(rows)
    adiff = arm_diff_block(rows, tmap, arms_present)
    degen = degeneration_block(rows, arms_present)
    errs = error_positions(rows)

    payload = {
        "analysis_version": ANALYSIS_VERSION,
        "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "discipline_note": (
            "本工具只输出确定性证据数字与清单；判别力是否成立等结论由 Worker 在报告中"
            "人工作出。词族/万能句命中是粗匹配证据，不是行为锚判定"
            "（TOPIC_RUBRIC_V1.md §0.1/§2 禁止关键词命中判定替代行为分档）。"
            "缺臂位如实计入 missing_positions，不计入任何 rate 分母、不冒充 0 分。"),
        "inputs": {
            "csv_path": csv_path,
            "csv_sha256": csv_sha,
            "rows_total": len(rows),
            "arms_present": arms_present,
            "episodes_total": len(eps_used),
            "topics_source_mode": mode,
            "topics_sources": sources,
            "topics_source_errors": terrors,
            "topics_resolved": topics_resolved,
            "topics_missing": topics_missing,
        },
        "topic_keyword_families": {
            k: {"source": v["source"], "keywords": v["keywords"]}
            for k, v in sorted(TOPIC_KEYWORDS.items())
        },
        "coverage": {
            "arms_present": arms_present,
            "missing_positions_total": missing_total,
            "duplicate_arm_case_rows": dup,
            "episodes": episodes,
        },
        "topic_recognition": {
            "denominator_note": ("rate 分母=该 (topic, arm) 有非空 primary_text 的轮位；"
                                 "no_reply/空文本轮单列 no_text_rows，不进分母"),
            "no_topic_rows": no_topic_rows,
            "per_topic": rec,
        },
        "arm_similarity": sim,
        "arm_diff": adiff,
        "degeneration": degen,
        "error_positions": errs,
        "missing_positions": missing_positions,
    }

    out_path = os.path.abspath(args.out)
    parent = os.path.dirname(out_path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")
    print_summary(payload, out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
