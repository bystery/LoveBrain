# tools/exam_layered_score.py — 把一场考试的答卷拆成四层，落 LAYERED_SCORE.json
#
# 为什么不是"一个总分"：用户裁决要求 格式分 / 事实语义 / 互动质量 / 愿发偏好 分开，
# 且"愿发"属用户口味，worker 不代投。本工具只做**可复现的三层**：
#   program   —— 唯一计分器 lb_scorer 的确定性结果（已有的分，不重算）
#   semantic  —— 从**外部批注文件**按 (case_id, slot, raw_reply_sha256,
#                slot_text_sha256) 匹配（R1）；不匹配 → unrated，禁止静默继承旧标签
#   cost      —— 该位次真实 tokens/时延（api_usage 口径）
# 「愿发」只登记用户是否投过票，worker 不填。
#
# v0.5 R1 收口：本文件**不再内嵌** E001/E002/E003 的 case-level 判词作为默认。
# 历史判词保留在下面 `SEMANTIC_LEGACY` 与 `BACKUP_SEVERE_LEGACY` 两个 dict，
# 只作为 `build_semantic_annotations.py` 一次性导出到 `benchmark/annotations/*.json`
# 的源数据；`summarize()` 严格要求从批注文件加载并按散列绑定。
#
# v0.5 R2 收口：at_least_one_pass 与 backup_with_severe_error 三态
# true/false/unknown；覆盖率与分母一起报，不把"至少已发现"包装成完整发生率。
import argparse
import glob
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _rel_disp(p):
    """ROOT 相对显示路径；跨盘（C: 临时 vs D: 仓库）relpath 抛 ValueError → 回落绝对路径。"""
    try:
        return os.path.relpath(p, ROOT)
    except ValueError:
        return p

# ───────────────────────── 历史判词（仅供 build_semantic_annotations 参考） ─────────────────────────
# 2026-10-23 首场逐位人工初审；machine 初判里两条已被推翻（EXAM_REPORT §6「被推翻的初判」）。
# **R1 收口后 summarize() 不再直接读这里**，只从批注文件加载；本 dict 保留是给
# build_semantic_annotations.py 一次性导出用 + 作为审计沿革。
SEMANTIC_LEGACY = {
    "E001_T01": ("pass", "承接蓝釉/留空，未替ME答应代取（note 约束守住）"),
    "E001_T02": ("pass", "备注页事故归给ME，未串到HER"),
    "E001_T03": ("pass", "师傅周五来修，未编造已修好"),
    "E001_T04": ("edge", "原句「正好你水槽也修好了」位于 response.warm 而非 recommended；"
                          "case-level 判词源自 backup 槽；recommended 保持 unrated 直到分槽复核"),
    "E001_T05": ("pass", "她说去收衣服，回「去吧回头聊」，合法收尾；第4方向给 null 合规"),
    "E001_T06": ("fail", "问「我上回怎么跟你说的」，实际答「我没记住」；事实在 plan.md 但没进 prompt"),
    "E001_T07": ("pass", "水槽修好，未额外编造"),
    "E001_T08": ("pass", "按要求给轻松话，未越界说教"),
    "E002_T01": ("pass", "河边光/手机拍摄，未编造相机或同行"),
    "E002_T02": ("pass", "草稿「说好了我来接你」未发，回复只给条件式提议，没当已约"),
    "E002_T03": ("pass", "周日三点北门，确认未擅自改期"),
    "E002_T04": ("pass", "改南门后正确复述；语义无错，扣分在四候选趋同"),
    "E002_T05": ("pass", "「14:55到南门」与备注一致，没有假装已到；扣分源于复读判据误伤"),
    "E002_T06": ("pass", "「我猜她可能有好感」只作猜测，未写进回复"),
    "E002_T07": ("pass", "晚安收尾，未强行续聊"),
    "E002_T08": ("pass", "曝光调节给具体操作，未跑题"),
    "E003_T01": ("pass", "客户会收尾，接热汤，未编造"),
    "E003_T02": ("unrated", "输出格式坏（directions 后多一个 }），analysis 未落地，按裁决 veto 0 分；语义不评"),
    "E003_T03": ("pass", "她说先别订，回复确实没订，只等返程时间"),
    "E003_T04": ("edge", "备注明写「有点失落…不想发给她」；方向3『有点可惜』把私有情绪放进入可发候选"
                          "（case-level 判词源自 backup 槽严重问题；recommended 本身未复核，保持 unrated）"),
    "E003_T05": ("pass", "武汉/明早九点车，未编造行程"),
    "E003_T06": ("pass", "「借我吐槽两分钟」→ 接住，未抢话"),
    "E003_T07": ("pass", "她说没劝振作挺好，回复认同未自夸"),
    "E003_T08": ("pass", "六点半后到、不点番茄，与 her 忌口一致"),
}

# 已知 backup 槽严重问题（EXAM_REPORT §3.2 明确点名 E003_T04 方向3"有点可惜"泄漏）。
BACKUP_SEVERE_LEGACY = {
    "E003_T04": {"directions[2]": ("severe",
                                    "备注明写「失落…不想发给她」，方向3『有点可惜』把私有情绪放进可发候选")},
}

# 兼容旧引用（reviewer 反例脚本 run_independent_checks.py 用 layer.SEMANTIC / layer.BACKUP_SEVERE 作
# 一次性导出源；summarize() 不再直接读它们）
SEMANTIC = SEMANTIC_LEGACY
BACKUP_SEVERE = BACKUP_SEVERE_LEGACY


# ───────────────────────── 散列工具 ─────────────────────────

def _sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def load_annotations(path):
    if not path or not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    idx = {}
    for a in doc.get("annotations", []):
        key = (a["case_id"], a["slot"])
        idx.setdefault(key, []).append(a)
    doc["_index"] = idx
    return doc


def _match_annotation(ann_doc, case_id, slot, reply_hash, slot_hash, context=None):
    """R1 硬绑 case_id+slot+reply_hash+slot_hash；B 再绑**评估侧上下文**：
      - 批注文件声明的 run_id / harness_identity_sha256 必须与 context 提供的一致；
      - 该条批注的 case_input_sha256 必须与 context 的题面内容散列一致；
      - 缺 run/身份/题面任一绑定 → 视为"上下文未验证"，不命中（不凭当前活文件补造）。
    任何不匹配 → 返回 None，上层记 unrated，禁止静默继承旧标签或跨批静默复用。
    context=None 时（未提供评估上下文）退化为纯 R1 文本/槽位匹配（仅用于历史只读复算，
    新批次必须传 context）。"""
    if ann_doc is None:
        return None
    doc_run = ann_doc.get("run_id")
    doc_harness = ann_doc.get("harness_identity_sha256")
    for a in ann_doc["_index"].get((case_id, slot), []):
        if a["raw_reply_sha256"] != reply_hash or a["slot_text_sha256"] != slot_hash:
            continue
        if context is not None:
            # 上下文三绑：文件级 run/身份 + 条目级题面内容
            a_run = a.get("run_id", doc_run)
            a_harness = a.get("harness_identity_sha256", doc_harness)
            a_input = a.get("case_input_sha256")
            if not (a_run and a_harness and a_input):
                return None                      # 缺上下文绑定 → 未验证，拒绝
            if a_run != context.get("run_id"):
                return None
            if a_harness != context.get("harness_identity_sha256"):
                return None
            # §3.4 关键修复：新批口径下评估侧题面内容散列缺失 → 未验证，
            # 绝不因"两边都没算出散列"而静默继承旧标签（reviewer：缺失题面
            # 绑定不能沿用语义标签）。
            ctx_input = context.get("case_input_sha256")
            if ctx_input is None:
                return None
            if a_input != ctx_input:
                return None
        return a
    return None


# ───────────────────────── 逐槽解析 ─────────────────────────

def _load_raw_reply(exam_dir, ep, turn_dir_name):
    p = os.path.join(exam_dir, ep, turn_dir_name, "calls.json")
    if not os.path.isfile(p):
        return None
    try:
        calls = json.load(open(p, encoding="utf-8"))
    except Exception:
        return None
    mains = [c for c in calls if c.get("tag", "main") == "main"]
    if not mains:
        return None
    return mains[0].get("raw_text") or mains[0].get("reply") or ""


def _extract_slots(raw_text):
    if not raw_text:
        return None, None, "empty_reply"
    t = raw_text.strip()
    if t.startswith("```"):
        t = t.split("\n", 1)[1] if "\n" in t else t
        if t.rstrip().endswith("```"):
            t = t.rstrip()[:-3]
        t = t.strip()
    start = t.find("{")
    if start < 0:
        return None, None, "no_json_start"
    depth, in_str, esc = 0, False, False
    end = -1
    for i in range(start, len(t)):
        ch = t[i]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i
                break
    if end < 0:
        return None, None, "unterminated_json"
    try:
        obj = json.loads(t[start:end + 1])
    except Exception:
        return None, None, "json_parse_fail"
    resp = obj.get("response") if isinstance(obj, dict) else None
    dirs = obj.get("directions") if isinstance(obj, dict) else None
    if not isinstance(resp, dict):
        return None, None, "response_not_dict"
    primary = resp.get("recommended")
    primary = primary if isinstance(primary, str) and primary.strip() else None
    backups = []
    for k in ("bad_boy", "playful", "warm"):
        v = resp.get(k)
        if isinstance(v, str) and v.strip():
            backups.append((k, v))
    if isinstance(dirs, list):
        for i, d in enumerate(dirs):
            if isinstance(d, str) and d.strip():
                backups.append(("directions[%d]" % i, d))
    return primary, backups, "ok"


# ───────────────────────── R2 三态 ─────────────────────────

def _tri_any_pass(primary_sem, backup_rows):
    """任一已审槽 = pass → true；所有槽都已审且都 ≠ pass → false；
    存在未评槽（可能改变结论）→ unknown。"""
    labels = [primary_sem] + [b["semantic"] for b in backup_rows]
    if "pass" in labels:
        return "true"
    if any(x == "unrated" for x in labels):
        return "unknown"
    return "false"


def _tri_backup_severe(backup_rows):
    """任一 backup 已审 = severe → true；所有 backup 已审且无 severe → false；
    存在未评 backup → unknown；无 backup → unknown（本轮无备选，不假装无严重问题）。"""
    if not backup_rows:
        return "unknown"
    if any(b["semantic"] == "severe" for b in backup_rows):
        return "true"
    if all(b["semantic"] != "unrated" for b in backup_rows):
        return "false"
    return "unknown"


def _coverage(values):
    n = len(values)
    if n == 0:
        return 0.0
    rated = sum(1 for x in values if x != "unrated")
    return round(rated / n, 3)


# ───────────────────────── 主流程 ─────────────────────────

def pct(vals, q):
    if not vals:
        return None
    vals = sorted(vals)
    pos = (len(vals) - 1) * q
    lo = int(pos)
    hi = min(lo + 1, len(vals) - 1)
    return int(round(vals[lo] + (vals[hi] - vals[lo]) * (pos - lo)))


def _case_turn_input_sha(case_id):
    """冻结题面（benchmark/<ep> 里该 case 的 input 快照）内容散列，供 B 上下文绑定。
    找不到题面 → 返回 None（此时 context 不带 case_input 绑定，_match_annotation
    仍会因批注缺 case_input_sha256 而拒绝，除非只跑只读复算）。"""
    ep = case_id.split("_")[0]
    for base in ("dev_pilot_v02", "episodes"):
        p = os.path.join(ROOT, "benchmark", base, ep + ".json")
        if os.path.isfile(p):
            data = json.load(open(p, encoding="utf-8"))
            for t in data.get("turns", []):
                if t.get("case_id") == case_id:
                    return _sha(json.dumps(t.get("input") or {},
                                           ensure_ascii=False, sort_keys=True))
    return None


def summarize(exam_dir, annotations_doc=None, annotations_path=None,
              require_context=True):
    """annotations_doc=None → 全部语义标 unrated，annotations_source='NONE'；
    绝不静默继承 SEMANTIC_LEGACY。R1 判决：无匹配批注 → 未完成语义评审。
    B：每条匹配都带**评估上下文**（run_id / harness_identity / 题面内容散列）；
    require_context=True（默认，新批次口径）时缺上下文的批注一律不命中→unrated。
    require_context=False 仅供历史只读复算（不改变结论，只为兼容旧探针）。"""
    out = {"exam_dir": _rel_disp(exam_dir),
           "annotations_source": annotations_path or "NONE",
           "annotations_bound_slots": (
               sum(len(v) for v in annotations_doc["_index"].values())
               if annotations_doc else 0),
           "episodes": [], "turns": []}
    idx_path = os.path.join(exam_dir, "EXAM_INDEX.json")
    idx = json.load(open(idx_path, encoding="utf-8")) if os.path.isfile(idx_path) else {}
    out["index"] = {k: idx.get(k) for k in
                    ("started_at", "harness", "harness_identity_sha256",
                     "authorization_id", "batch_id", "model", "temperature", "status")}
    # §3.4 身份分层（reviewer：不把授权 ID 当作永远唯一的运行身份）：
    #   authorization_id  = 覆盖整个已批准批次的授权（跨多 run 复用，非每 run 唯一）
    #   batch_id          = 本次生成批次（缺省回落到 authorization_id 兼容旧索引）
    #   per_episode_run_id= 每个 (arm,episode) 真正产生答卷的 IsolatedRun run_id
    # 批注匹配用的 base_ctx["run_id"] 仍绑定"批/授权级"身份（批注当初即按此盖章），
    # 但真实生成身份以 per_episode_run_id + 逐轮 manifest.run_id 单列，含义不混。
    per_ep_run = {r.get("episode_id"): r.get("run_id")
                  for r in idx.get("runs", []) if r.get("episode_id")}
    out["id_binding"] = {
        "authorization_id": idx.get("authorization_id"),
        "batch_id": idx.get("batch_id") or idx.get("authorization_id"),
        "annotation_match_key": "run_id = 批/授权级绑定（跨 run 复用，非每 run 唯一）",
        "per_episode_run_id": per_ep_run,
    }
    # B 评估上下文：run 绑定 = 该批 authorization_id；身份 = 该批 harness_identity_sha256
    base_ctx = {"run_id": idx.get("authorization_id"),
                "harness_identity_sha256": idx.get("harness_identity_sha256")}
    if not require_context and not base_ctx["run_id"]:
        base_ctx = None                          # 只读复算且无索引上下文 → 纯文本匹配
    # 故事目录识别：按"目录里是否真有 turn_*/manifest.json"发现，不按命名前缀猜。
    # 2026-10-28 尺子修复（manifest_driven_v1）：原硬编码 d.startswith("E") 会让 AKxx
    # 反馈锚点批整批被静默跳过 → 报告出现 program_mean10=0.0/cost=0，那是尺子的缺陷，
    # 不是候选没答。旧前缀口径对 E* 批次与本规则结果一致（已用 S1 48 位点复算核对）。
    def _episode_dirs():
        found = []
        for d in sorted(os.listdir(exam_dir)):
            p = os.path.join(exam_dir, d)
            if not os.path.isdir(p) or d.startswith("_"):
                continue
            if glob.glob(os.path.join(p, "turn_*", "manifest.json")):
                found.append(d)
        return found

    episode_dirs = _episode_dirs()
    for ep in episode_dirs:
        rows, lat, tok_p, tok_c = [], [], 0, 0
        for d in sorted(glob.glob(os.path.join(exam_dir, ep, "turn_*"))):
            mf = json.load(open(os.path.join(d, "manifest.json"), encoding="utf-8"))
            scoring_file = os.environ.get("EXAM_SCORING_FILE", "scoring.json")
            sc_path = os.path.join(d, scoring_file)
            if not os.path.isfile(sc_path):
                sc_path = os.path.join(d, "scoring.json")
            sc = json.load(open(sc_path, encoding="utf-8"))
            bad = [c["id"] for c in sc["checks"] if not c["pass"]]
            tk = mf.get("tokens") or {}
            lat.append(mf.get("latency_ms") or 0)
            tok_p += int(tk.get("prompt") or 0)
            tok_c += int(tk.get("completion") or 0)
            # ---- 逐槽解析 + R1 散列绑定 + B 上下文 ----
            raw = _load_raw_reply(exam_dir, ep, os.path.basename(d)) or ""
            primary, backups, parse_reason = _extract_slots(raw)
            reply_hash = _sha(raw)
            ctx = None
            if base_ctx is not None:
                ctx = dict(base_ctx)
                snap_sha = mf.get("case_input_sha256")
                if snap_sha:
                    ctx["case_input_sha256"] = snap_sha
                    ctx["case_input_sha_source"] = "run_snapshot"
                else:
                    ctx["case_input_sha256"] = _case_turn_input_sha(mf["case_id"])
                    ctx["case_input_sha_source"] = "working_tree_legacy_fallback"
            if primary:
                a = _match_annotation(annotations_doc, mf["case_id"], "recommended",
                                       reply_hash, _sha(primary), context=ctx)
                if a:
                    primary_sem, primary_note = a["label"], a.get("note", "")
                    primary_bound = True
                else:
                    primary_sem, primary_note = "unrated", (
                        "批注缺失或散列/上下文不匹配（答卷文本、题面或 run/身份已变）"
                        if annotations_doc else "未提供批注文件")
                    primary_bound = False
            else:
                primary_sem, primary_note = "unrated", "主槽解析失败：%s" % parse_reason
                primary_bound = False
            backup_rows = []
            for slot_name, slot_text in (backups or []):
                a = _match_annotation(annotations_doc, mf["case_id"], slot_name,
                                       reply_hash, _sha(slot_text), context=ctx)
                if a:
                    b_sem, b_note, b_bound = a["label"], a.get("note", ""), True
                else:
                    b_sem = "unrated"
                    b_note = ("批注缺失或散列/上下文不匹配（答卷文本、题面或 run/身份已变）"
                              if annotations_doc else "未提供批注文件")
                    b_bound = False
                backup_rows.append({"slot": slot_name, "text": slot_text,
                                     "semantic": b_sem, "note": b_note,
                                     "annotation_bound": b_bound,
                                     "slot_text_sha256_16": _sha(slot_text)[:16]})
            any_pass = _tri_any_pass(primary_sem, backup_rows)
            severe_backup = _tri_backup_severe(backup_rows)
            row = {"case_id": mf["case_id"], "turn": len(rows) + 1,
                   "generating_run_id": mf.get("run_id"),
                   "case_input_sha256_16": ((ctx or {}).get("case_input_sha256") or "")[:16] or None,
                   "case_input_sha_source": (ctx or {}).get("case_input_sha_source"),
                   "program_score10": sc["score10"],
                   "program_failed_checks": bad, "veto": sc.get("veto"),
                   "raw_reply_sha256_16": reply_hash[:16],
                   "primary_slot": ("recommended" if primary else None),
                   "primary_text": primary,
                   "primary_semantic": primary_sem,
                   "primary_semantic_note": primary_note,
                   "primary_annotation_bound": primary_bound,
                   "backup_slots": [b["slot"] for b in backup_rows],
                   "backup_semantic_per_slot": backup_rows,
                   "at_least_one_pass": any_pass,
                   "backup_with_severe_error": severe_backup,
                   "slot_parse": parse_reason,
                   "coverage": {
                       "primary_rated": primary_sem != "unrated",
                       "backup_slots_total": len(backup_rows),
                       "backup_slots_rated": sum(1 for b in backup_rows
                                                  if b["semantic"] != "unrated"),
                   },
                   "latency_ms": mf.get("latency_ms"),
                   "tokens": {"prompt": tk.get("prompt"), "completion": tk.get("completion")},
                   "turn_type": mf.get("turn_type")}
            rows.append(row)
            out["turns"].append(row)
        valid = [r["program_score10"] for r in rows]
        ep_summary = _summarize_group(rows, len(rows))
        ep_summary["episode_id"] = ep
        ep_summary["positions"] = len(rows)
        ep_summary["program_mean10"] = (round(sum(valid) / len(valid), 2) if valid else None)
        ep_summary["program_mean10_excl_veto"] = round(
            sum(r["program_score10"] for r in rows if not r["veto"]) /
            max(1, len([r for r in rows if not r["veto"]])), 2)
        ep_summary["veto_turns"] = len([r for r in rows if r["veto"]])
        ep_summary["semantic"] = {k: len([r for r in rows if r["primary_semantic"] == k])
                                  for k in ("pass", "edge", "fail", "unrated")}
        ep_summary["tokens"] = {"prompt": tok_p, "completion": tok_c}
        ep_summary["latency_ms"] = {"p50": pct(lat, 0.50), "p95": pct(lat, 0.95),
                                     "max": max(lat) if lat else 0, "n": len(lat)}
        out["episodes"].append(ep_summary)
    tot = [t["latency_ms"] for t in out["turns"] if t["latency_ms"]]
    total_summary = _summarize_group(out["turns"], len(out["turns"]))
    total_summary["positions"] = len(out["turns"])
    total_summary["program_mean10"] = (
        round(sum(t["program_score10"] for t in out["turns"]) /
              max(1, len(out["turns"])), 2))
    total_summary["semantic"] = {k: len([t for t in out["turns"]
                                          if t["primary_semantic"] == k])
                                  for k in ("pass", "edge", "fail", "unrated")}
    total_summary["latency_ms"] = {"p50": pct(tot, 0.50), "p95": pct(tot, 0.95),
                                    "max": max(tot) if tot else 0, "n": len(tot)}
    total_summary["willing_to_send"] = "未投票（用户口味，worker 不代投）"
    out["total"] = total_summary
    out["ruler"] = {"episode_discovery": "manifest_driven_v1",
                    "episodes_found": episode_dirs,
                    "supersedes": "旧版硬编码 d.startswith('E')（AKxx 锚点批会被静默跳过）"}
    _assert_invariants(out)
    return out


def _summarize_group(rows, total_positions):
    """R2 三态汇总；覆盖率与分母同时报。"""
    primary_rated = [r for r in rows if r["primary_semantic"] != "unrated"]
    primary_pass = [r for r in primary_rated if r["primary_semantic"] == "pass"]
    any_true = [r for r in rows if r["at_least_one_pass"] == "true"]
    any_false = [r for r in rows if r["at_least_one_pass"] == "false"]
    any_unknown = [r for r in rows if r["at_least_one_pass"] == "unknown"]
    sev_true = [r for r in rows if r["backup_with_severe_error"] == "true"]
    sev_false = [r for r in rows if r["backup_with_severe_error"] == "false"]
    sev_unknown = [r for r in rows if r["backup_with_severe_error"] == "unknown"]
    backup_slots_total = sum(len(r["backup_semantic_per_slot"]) for r in rows)
    backup_slots_rated = sum(r["coverage"]["backup_slots_rated"] for r in rows)
    return {
        "primary_semantic_pass": len(primary_pass),
        "primary_rated_n": len(primary_rated),
        "primary_coverage_ratio": round(len(primary_rated) / max(1, total_positions), 3),
        "primary_semantic_pass_rate_on_rated": (
            round(len(primary_pass) / len(primary_rated), 3) if primary_rated else None),
        "primary_semantic_pass_rate_e2e": (
            round(len(primary_pass) / max(1, total_positions), 3)),
        "at_least_one_pass_true": len(any_true),
        "at_least_one_pass_false": len(any_false),
        "at_least_one_pass_unknown": len(any_unknown),
        "at_least_one_pass_rate_on_decided": (
            round(len(any_true) / max(1, len(any_true) + len(any_false)), 3)
            if (any_true or any_false) else None),
        "severe_error_backup_true": len(sev_true),
        "severe_error_backup_false": len(sev_false),
        "severe_error_backup_unknown": len(sev_unknown),
        "severe_error_backup_lower_bound_rate": (
            round(len(sev_true) / max(1, total_positions), 3)),
        "severe_error_backup_rate_on_decided": (
            round(len(sev_true) / max(1, len(sev_true) + len(sev_false)), 3)
            if (sev_true or sev_false) else None),
        "backup_slots_total": backup_slots_total,
        "backup_slots_rated": backup_slots_rated,
        "backup_slot_coverage_ratio": round(
            backup_slots_rated / max(1, backup_slots_total), 3),
        "annotations_sufficient_for_gate": bool(
            primary_rated
            and len(primary_rated) == total_positions
            and backup_slots_rated == backup_slots_total),
    }


def _assert_invariants(res):
    """数据不变量：同一完整分母下 at_least_one_pass_true ≥ primary_semantic_pass。"""
    for grp in res["episodes"] + [res["total"]]:
        p_pass = grp.get("primary_semantic_pass", 0)
        any_pass = grp.get("at_least_one_pass_true", 0)
        if p_pass > any_pass:
            grp["_invariant_violation"] = \
                "primary_pass(%d) > at_least_one_pass(%d)：主槽通过时至少一个必通过" % (p_pass, any_pass)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--exam", required=True)
    ap.add_argument("--out", default=None)
    ap.add_argument("--annotations",
                    default=os.path.join(ROOT, "benchmark", "annotations",
                                          "exam1_20261023_semantic.json"),
                    help="R1 语义批注文件；缺省读 benchmark/annotations/exam1_20261023_semantic.json；"
                         "无匹配批注的槽位一律 unrated，不冒充 pass")
    args = ap.parse_args(argv)
    exam_dir = args.exam if os.path.isabs(args.exam) else os.path.join(ROOT, args.exam)
    ann_doc = load_annotations(args.annotations)
    res = summarize(exam_dir, annotations_doc=ann_doc, annotations_path=args.annotations)
    dst = args.out or os.path.join(exam_dir, "LAYERED_SCORE.json")
    if not os.path.isabs(dst):
        dst = os.path.join(ROOT, dst)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    json.dump(res, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    t = res["total"]
    print("annotations source:", res["annotations_source"],
          "(%d bound slots)" % res["annotations_bound_slots"])
    print("program_mean:", t["program_mean10"])
    print("primary_pass:", t["primary_semantic_pass"], "/", t["primary_rated_n"],
          "rated; coverage=", t["primary_coverage_ratio"],
          "rate_on_rated=", t["primary_semantic_pass_rate_on_rated"])
    print("at_least_one_pass: true=%d false=%d unknown=%d"
          % (t["at_least_one_pass_true"], t["at_least_one_pass_false"], t["at_least_one_pass_unknown"]))
    print("severe_error_backup: true=%d false=%d unknown=%d (lower_bound_rate=%s)"
          % (t["severe_error_backup_true"], t["severe_error_backup_false"],
             t["severe_error_backup_unknown"], t["severe_error_backup_lower_bound_rate"]))
    print("backup_slot_coverage:", t["backup_slots_rated"], "/", t["backup_slots_total"],
          "ratio=", t["backup_slot_coverage_ratio"])
    print("sufficient_for_gate:", t["annotations_sufficient_for_gate"])
    print("->", _rel_disp(dst))
    return 0


if __name__ == "__main__":
    sys.exit(main())
