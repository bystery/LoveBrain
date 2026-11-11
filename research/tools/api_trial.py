# -*- coding: utf-8 -*-
"""api_trial.py — 唯一的薄 API 入口（harness 装配 → API → 留证 → 评分/评审）。

权威口径：refs/harness_api_mainline_20261022/HARNESS_API_MAINLINE_GUIDE_20261022.md
          refs/arena_v1/SCOPE_DECISION_API_CANDIDATE_20261022.md（v2）
调用清单：refs/arena_v1/CALL_LIST_EXAM1_20261022.md

**参赛对象是 harness，不是 API。** 本入口只做编排：
  harness 组织 system/user → 校验授权与剩余额度 → 发一次请求 → 保存原文
  → 确定性评分 → （实盘时）逐位评审。
dsf 端点/model/温度在同批比较中固定；换模型不是harness进步。

三种模式：
  --plan     零网络：输出 24 位清单（题面/轮号/轮型/输入位/带不带 note/intent）待批。
  --capture  离线捕获**即将真实发出的出站请求体**（urlopen 被本进程替身接管，
             零网络），逐位检查：无 gold/未来轮次/评分卡泄漏、无 tool 通道、
             model/温度正确、note/intent 只在应当出现时出现。
  --live     需 --authorization（授权单），端点/model/温度须与清单钉死值逐字一致；
             逐 episode 顺序执行，任一位失败即停批（不自动重试、不补问、不换位）。
"""
import argparse
import datetime
import hashlib
import json
import os
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))

import api_candidate                                   # noqa: E402
from isolation.authorization_budget import (            # noqa: E402
    BudgetRefused, validate_authorization_doc)

# 同一批比较中固定的 API 配置（改这里 = 换考题条件，必须重新审批）
PINNED_ENDPOINT = "http://127.0.0.1:17770/v1"
PINNED_MODEL = "dsf"
PINNED_TEMPERATURE = 0.7
BASELINE_HARNESS = "reference_product_v1"
PINNED_EPISODES = ("E001", "E002", "E003")
CARDS = os.path.join(ROOT, "benchmark", "dev_pilot_v02", "03_GRADING_CARDS.json")
MIN_LEAK_LEN = 8


def load_episode(name):
    p = os.path.join(ROOT, "benchmark", "episodes", name + ".json")
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def _walk_candidate_dir(base):
    entries = {}
    for dirpath, _dirs, files in os.walk(base):
        for fn in sorted(files):
            if not fn.endswith((".py", ".md", ".json")):
                continue
            p = os.path.join(dirpath, fn)
            rel = os.path.relpath(p, ROOT).replace("\\", "/")
            entries[rel] = hashlib.sha256(open(p, "rb").read()).hexdigest()
    return entries


def _params_of(candidate):
    p = os.path.join(ROOT, "candidates", candidate, "params.json")
    if not os.path.isfile(p):
        return {}
    try:
        return json.load(open(p, encoding="utf-8"))
    except Exception:
        return {}


def harness_identity(candidate):
    """记录 harness 源码与资产散列（指导书 §4：记录源码和资产散列后运行）。

    R5.1：候选通过 params.json.parent 引用父本源码/资产 → 身份必须闭包展开：
    从 candidate 出发，递归合并 parent（去重，防循环）。父本任何字节变动
    都会牵动候选 identity_sha256，杜绝"候选目录里只放引用符号、
    换掉父本实现但 identity 不变"这种作弊路径。"""
    entries = {}
    visited = set()
    stack = [candidate]
    closure_order = []
    while stack:
        c = stack.pop(0)
        if c in visited:
            continue
        visited.add(c)
        closure_order.append(c)
        base = os.path.join(ROOT, "candidates", c)
        if not os.path.isdir(base):
            continue
        entries.update(_walk_candidate_dir(base))
        parent = _params_of(c).get("parent")
        if parent:
            stack.append(parent)
    joined = "\n".join("%s:%s" % (k, entries[k]) for k in sorted(entries))
    return {"candidate": candidate,
            "closure": closure_order,               # [self, parent, grandparent, ...]
            "files": entries,
            "identity_sha256": hashlib.sha256(
                joined.encode("utf-8")).hexdigest()}


def plan_rows(episodes):
    """24 位次清单。每位 = 1 次 chat/completions（G11 实测 1 次/轮）。"""
    rows = []
    for ep_name in episodes:
        ep = load_episode(ep_name)
        for i, t in enumerate(ep.get("turns") or []):
            inp = t.get("input") or {}
            msgs = inp.get("messages") or []
            rows.append({
                "position": len(rows) + 1,
                "episode_id": ep.get("episode_id"),
                "turn_index": i + 1,
                "case_id": t.get("case_id"),
                "turn_type": t.get("turn_type", "reply"),
                "messages": len(msgs),
                "has_note": bool((inp.get("note") or "").strip()),
                "has_intent": bool(inp.get("intent")),
                "may_decline": bool(inp.get("may_decline")),
                "media_refs": len([m for m in msgs if m.get("image")]),
                "attempts": 1,
            })
    return rows


def check_text_only(rows):
    bad = [r["position"] for r in rows if r["media_refs"]]
    if bad:
        raise SystemExit("清单第 %s 位带媒体引用：本批是文本试考，不覆盖视觉，"
                         "请拆批后重新审批。" % bad)


def build_manifest(rows, episodes, candidate):
    return {
        "document_type": "api_text_pilot_call_manifest",
        "generated_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "contestant": "harness",
        "harness": candidate,
        "harness_role": ("原版基线（首场唯一参赛者）" if candidate ==
                         BASELINE_HARNESS else "候选版本"),
        "api_service": {"endpoint": PINNED_ENDPOINT, "model": PINNED_MODEL,
                        "temperature": PINNED_TEMPERATURE,
                        "adapter": "openai_compat",
                        "note": "各 harness 共用的模型服务；同批固定"},
        "episodes": list(episodes),
        "positions_total": len(rows),
        "attempts_total": sum(r["attempts"] for r in rows),
        "attempts_per_position": 1,
        "auxiliary_calls": 0,
        "tools": None,
        "file_or_command_access": False,
        "output_handling": "text/JSON data only (json.loads; no eval/exec)",
        "harness_identity": harness_identity(candidate),
        "rows": rows,
        "budget_note": "attempts_total 是硬上限；失败即停批，不预留补跑额度",
    }


def write_plan(out_dir, manifest):
    os.makedirs(out_dir, exist_ok=True)
    p = os.path.join(out_dir, "CALL_MANIFEST.json")
    with open(p, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    lines = ["# 首场 API 文本试考调用清单（%d 位）"
             % manifest["positions_total"], "",
             "参赛 harness：`%s`（%s）｜ 身份 %s"
             % (manifest["harness"], manifest["harness_role"],
                manifest["harness_identity"]["identity_sha256"][:16]), "",
             "| # | episode | 轮 | case_id | 轮型 | 消息 | note | intent | attempts |",
             "|---|---|---|---|---|---|---|---|---|"]
    for r in manifest["rows"]:
        lines.append("| %d | %s | %d | %s | %s | %d | %s | %s | %d |"
                     % (r["position"], r["episode_id"], r["turn_index"],
                        r["case_id"], r["turn_type"], r["messages"],
                        "Y" if r["has_note"] else "-",
                        "Y" if r["has_intent"] else "-", r["attempts"]))
    api = manifest["api_service"]
    lines += ["", "端点 `%s` ｜ model `%s` ｜ 温度 %s ｜ 适配器 `%s`"
              % (api["endpoint"], api["model"], api["temperature"],
                 api["adapter"]),
              "", "合计 **%d 位 / %d 次调用**，辅助调用 0，工具 0，文件/命令能力 0。"
              % (manifest["positions_total"], manifest["attempts_total"]), ""]
    with open(os.path.join(out_dir, "CALL_MANIFEST.md"), "w",
              encoding="utf-8") as f:
        f.write("\n".join(lines))
    return p


# ---------- 泄漏检查 ----------

def _strings(obj, min_len=MIN_LEAK_LEN):
    out = []
    if isinstance(obj, str):
        if len(obj.strip()) >= min_len:
            out.append(obj.strip())
    elif isinstance(obj, dict):
        for v in obj.values():
            out.extend(_strings(v, min_len))
    elif isinstance(obj, list):
        for v in obj:
            out.extend(_strings(v, min_len))
    return out


def _card_strings():
    try:
        return _strings(json.load(open(CARDS, encoding="utf-8")), 12)
    except (OSError, json.JSONDecodeError):
        return []


def _visible_text(ep, turn_index):
    """该位次**合法可见**的全部材料：历次已见消息/note/intent + persona/初始知识。

    用途仅是"判定某串文字是不是泄漏"——若它本来就出现在合法输入里，
    出现在请求体中就不是泄漏（例如评分卡引用了剧情里的一句事实）。
    """
    parts = [json.dumps(ep.get("persona") or {}, ensure_ascii=False),
             json.dumps(ep.get("initial_kb") or {}, ensure_ascii=False)]
    for t in (ep.get("turns") or [])[:turn_index + 1]:
        parts.append(json.dumps(t.get("input") or {}, ensure_ascii=False))
    return "\n".join(parts)


def leakage_report(ep, turn_index, body, card_strs):
    """出站请求体不得含：本轮 gold、未来轮次消息、评分卡文本（合法可见材料除外）。"""
    text = json.dumps(body, ensure_ascii=False)
    visible = _visible_text(ep, turn_index)
    leaks = []
    gold = (ep.get("turns") or [])[turn_index].get("gold") or {}
    for s in _strings(gold):
        if s in text and s not in visible:
            leaks.append({"kind": "gold", "sample": s[:40]})
    for j, t2 in enumerate(ep.get("turns") or []):
        if j <= turn_index:
            continue
        for m in (t2.get("input") or {}).get("messages") or []:
            s = (m.get("text") or "").strip()
            if len(s) >= MIN_LEAK_LEN and s in text and s not in visible:
                leaks.append({"kind": "future_turn", "turn": j + 1,
                              "sample": s[:40]})
    for s in card_strs:
        if s in text and s not in visible:
            leaks.append({"kind": "grading_card", "sample": s[:40]})
    return leaks


# ---------- capture：离线捕获出站请求 ----------

_CANNED_REPLY = json.dumps({
    "response": {"recommended": "（离线捕获替身，不代表质量）",
                 "warm": "（离线捕获替身）", "playful": "（离线捕获替身）",
                 "bad_boy": "（离线捕获替身）"},
    "directions": ["（离线捕获替身）", None, None, None],
    "analysis": {"topic_status": "new", "topic_label": "capture",
                 "scene_facts": [], "ongoing": []}}, ensure_ascii=False)


def _write_test_authorization(path, attempts):
    doc = {"authorization_id": "AUTH-TEST-CAPTURE-%s"
                               % datetime.datetime.now().strftime("%Y%m%d%H%M%S"),
           "authorized": True, "status": "ACTIVE",
           "scope": "离线捕获出站请求（urlopen 被本进程替身接管，零真实调用）",
           "note": "测试授权：不产生真实 API 调用；账本仅为测试占额证据",
           "real_endpoint": {"api_base": PINNED_ENDPOINT, "model": PINNED_MODEL,
                             "temperature": PINNED_TEMPERATURE,
                             "max_attempts": attempts}}
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    return doc


def run_capture(args, manifest):
    from isolation.trusted_controller import IsolatedRun

    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    base = args.out or os.path.join(ROOT, "logs", "pilot_capture_%s" % stamp)
    if not os.path.isabs(base):
        base = os.path.join(ROOT, base)
    os.makedirs(base, exist_ok=True)
    auth_path = os.path.join(base, "_auth", "AUTH_TEST_CAPTURE.json")
    total = manifest["attempts_total"]
    _write_test_authorization(auth_path, total)
    norm = validate_authorization_doc(json.load(open(auth_path, encoding="utf-8")))
    card_strs = _card_strings()

    captured, dispatch = [], {"calls": 0}
    real_urlopen = urllib.request.urlopen

    def fake_urlopen(req, timeout=None):
        body = json.loads(req.data.decode("utf-8"))
        dispatch["calls"] += 1
        captured.append(body)
        payload = {"id": "capture-%d" % dispatch["calls"],
                   "model": PINNED_MODEL,
                   "choices": [{"message": {"content": _CANNED_REPLY},
                                "finish_reason": "stop"}],
                   "usage": {"prompt_tokens": 1, "completion_tokens": 1}}

        class _R(object):
            def read(self):
                return json.dumps(payload).encode("utf-8")

            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

        return _R()

    os.environ["LB_API_BASE"] = PINNED_ENDPOINT
    os.environ["LB_MODEL"] = PINNED_MODEL
    os.environ.setdefault("LB_API_KEY", "local")

    report = {"document_type": "pilot_request_capture",
              "generated_at": datetime.datetime.now().isoformat(timespec="seconds"),
              "contestant": manifest["contestant"],
              "harness": manifest["harness"],
              "harness_identity_sha256":
                  manifest["harness_identity"]["identity_sha256"],
              "api_service": manifest["api_service"],
              "network": "ZERO (urlopen replaced in-process; any real attempt "
                         "would be recorded as a capture, none reached network)",
              "positions": [], "problems": [], "status": "PENDING"}

    urllib.request.urlopen = fake_urlopen
    try:
        for ep_name in manifest["episodes"]:
            ep = load_episode(ep_name)
            out = os.path.join(base, ep_name)
            run_dir = os.path.join(base, "_iso_run_" + ep_name)
            authorization = {"authorization_id": norm["authorization_id"],
                             "max_attempts": norm["max_attempts"],
                             "api_base": norm["api_base"],
                             "model": norm["model"],
                             "temperature": norm["temperature"],
                             "status": norm["status"],
                             "budget_ledger": os.path.join(
                                 ROOT, "logs", "auth_budget",
                                 "%s.jsonl" % norm["authorization_id"]),
                             "batch_need": len(ep.get("turns") or [])}
            run = IsolatedRun(run_dir, out, os.path.join(out, "_control"),
                              manifest["harness"], adapter="openai_compat",
                              quota=len(ep.get("turns") or []), resume=False,
                              authorization=authorization)
            try:
                run.stage()
                run.start()
                run.run_episode(os.path.join(ROOT, "benchmark", "episodes",
                                             ep_name + ".json"),
                                stop_after=None, resume=False,
                                require_assets=True)
            finally:
                run.close()
    finally:
        urllib.request.urlopen = real_urlopen

    pos = 0
    for ep_name in manifest["episodes"]:
        ep = load_episode(ep_name)
        for i, t in enumerate(ep.get("turns") or []):
            if pos >= len(captured):
                report["problems"].append(
                    {"position": pos + 1, "kind": "MISSING_CAPTURE"})
                continue
            body = captured[pos]
            leaks = leakage_report(ep, i, body, card_strs)
            keys = sorted(body)
            problems = []
            if set(keys) != set(api_candidate.REQUEST_BODY_ALLOWED_KEYS):
                problems.append("body keys=%s" % keys)
            if body.get("model") != PINNED_MODEL:
                problems.append("model=%r" % body.get("model"))
            if body.get("temperature") != PINNED_TEMPERATURE:
                problems.append("temperature=%r" % body.get("temperature"))
            if len(body.get("messages") or []) != 2:
                problems.append("messages=%d" % len(body.get("messages") or []))
            system = ((body.get("messages") or [{}])[0].get("content") or "")
            user = ((body.get("messages") or [{}])[1].get("content") or "")
            if not system.strip():
                problems.append("empty system prompt")
            if not user.strip():
                problems.append("empty user prompt")
            req_dir = os.path.join(base, "requests")
            os.makedirs(req_dir, exist_ok=True)
            with open(os.path.join(req_dir, "pos_%02d_%s_body.json"
                                   % (pos + 1, t.get("case_id"))),
                      "w", encoding="utf-8") as f:
                json.dump(body, f, ensure_ascii=False, indent=1)
            entry = {"position": pos + 1, "episode_id": ep_name,
                     "turn_index": i + 1, "case_id": t.get("case_id"),
                     "body_keys": keys, "model": body.get("model"),
                     "temperature": body.get("temperature"),
                     "system_chars": len(system), "user_chars": len(user),
                     "prompt_sha256": hashlib.sha256(
                         (system + "\n" + user).encode("utf-8")).hexdigest(),
                     "has_note": bool(((t.get("input") or {}).get("note")
                                       or "").strip()),
                     "has_intent": bool((t.get("input") or {}).get("intent")),
                     "leaks": leaks, "problems": problems,
                     "body_file": "requests/pos_%02d_%s_body.json"
                                  % (pos + 1, t.get("case_id"))}
            report["positions"].append(entry)
            if leaks or problems:
                report["problems"].append(
                    {"position": pos + 1, "kind": "CHECK", "leaks": leaks,
                     "problems": problems})
            pos += 1
    report["dispatch_count"] = dispatch["calls"]
    if dispatch["calls"] != total:
        report["problems"].append({"kind": "DISPATCH_COUNT",
                                   "expect": total, "actual": dispatch["calls"]})
    report["status"] = "PASS" if not report["problems"] else "FAIL"
    p = os.path.join(base, "REQUEST_CAPTURE.json")
    with open(p, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print(json.dumps({"mode": "capture", "status": report["status"],
                      "positions": len(report["positions"]),
                      "dispatch": dispatch["calls"],
                      "problems": len(report["problems"]),
                      "report": os.path.relpath(p, ROOT),
                      "network": "zero"}, ensure_ascii=False))
    return 0 if report["status"] == "PASS" else 1


# ---------- live：真实批次 ----------

def run_live(args, manifest):
    from isolation.trusted_controller import IsolatedRun

    authp = args.authorization
    if not os.path.isabs(authp):
        authp = os.path.join(ROOT, authp)
    try:
        doc = json.load(open(authp, encoding="utf-8"))
        norm = validate_authorization_doc(doc)
    except (OSError, json.JSONDecodeError) as e:
        raise SystemExit("授权单不可读：%s" % e)
    except BudgetRefused as e:
        raise SystemExit("授权单不合法：%s" % e)

    if norm["api_base"] != PINNED_ENDPOINT or norm["model"] != PINNED_MODEL:
        raise SystemExit("授权单端点/model 与清单不符：%s / %s"
                         % (norm["api_base"], norm["model"]))
    if norm["temperature"] is None or \
            abs(float(norm["temperature"]) - PINNED_TEMPERATURE) > 1e-9:
        raise SystemExit("授权单 temperature 必须为 %s，实为 %r"
                         % (PINNED_TEMPERATURE, norm["temperature"]))
    need = manifest["attempts_total"]
    if norm["max_attempts"] < need:
        raise SystemExit("授权额度 %d < 清单需求 %d：不运行"
                         % (norm["max_attempts"], need))
    if args.expect_attempts is not None and args.expect_attempts != need:
        raise SystemExit("--expect-attempts %d 与清单需求 %d 不符"
                         % (args.expect_attempts, need))

    os.environ["LB_API_BASE"] = PINNED_ENDPOINT
    os.environ["LB_MODEL"] = PINNED_MODEL
    os.environ.setdefault("LB_API_KEY", "local")

    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    base = args.out or os.path.join(ROOT, "logs", "exam1_%s" % stamp)
    if not os.path.isabs(base):
        base = os.path.join(ROOT, base)
    os.makedirs(base, exist_ok=True)
    index = {"started_at": datetime.datetime.now().isoformat(timespec="seconds"),
             "contestant": manifest["contestant"],
             "harness": manifest["harness"],
             "harness_identity_sha256":
                 manifest["harness_identity"]["identity_sha256"],
             "authorization_id": norm["authorization_id"],
             "max_attempts": norm["max_attempts"], "batch_need": need,
             "endpoint": PINNED_ENDPOINT, "model": PINNED_MODEL,
             "temperature": PINNED_TEMPERATURE, "runs": [],
             "status": "RUNNING", "failures": []}
    idx_path = os.path.join(base, "EXAM_INDEX.json")

    for k, ep_name in enumerate(manifest["episodes"]):
        ep = load_episode(ep_name)
        turns = ep.get("turns") or []
        out = os.path.join(base, ep_name)
        authorization = {"authorization_id": norm["authorization_id"],
                         "max_attempts": norm["max_attempts"],
                         "api_base": norm["api_base"], "model": norm["model"],
                         "temperature": norm["temperature"],
                         "status": norm["status"],
                         "budget_ledger": os.path.join(
                             ROOT, "logs", "auth_budget",
                             "%s.jsonl" % norm["authorization_id"]),
                         "batch_need": len(turns)}
        entry = {"episode_id": ep_name, "out": os.path.relpath(out, ROOT),
                 "positions": len(turns), "status": "PENDING"}
        run = None
        try:
            run = IsolatedRun(os.path.join(base, "_iso_run_" + ep_name), out,
                              os.path.join(out, "_control"),
                              manifest["harness"], adapter="openai_compat",
                              quota=len(turns), resume=False,
                              authorization=authorization)
            run.stage()
            run.start()
            m, _proxy = run.run_episode(
                os.path.join(ROOT, "benchmark", "episodes", ep_name + ".json"),
                stop_after=None, resume=False, require_assets=True)
            entry.update({"status": "DONE", "run_id": m["run_id"],
                          "turns": m["turns"], "complete": m["complete"],
                          "mean_score10": m["mean_score10"],
                          "veto_turns": m["veto_turns"],
                          "budget": (m.get("report_metadata") or {})
                          .get("budget")})
            index["runs"].append(entry)
            print("[%s] %s 完成：%d/%d 轮，均分 %s"
                  % (ep_name, m["run_id"], m["turns"], len(turns),
                     m["mean_score10"]))
        except Exception as e:                            # noqa: BLE001
            entry.update({"status": "FAILED",
                          "error": "%s: %s" % (type(e).__name__, str(e)[:400])})
            index["runs"].append(entry)
            index["failures"].append(entry)
            index["status"] = "STOPPED_ON_FAILURE"
            print("[%s] 失败即停：%s" % (ep_name, entry["error"]))
            json.dump(index, open(idx_path, "w", encoding="utf-8"),
                      ensure_ascii=False, indent=1)
            if run is not None:
                run.close()
            return 1, base
        finally:
            if run is not None:
                if k == len(manifest["episodes"]) - 1 and \
                        entry["status"] == "DONE":
                    try:
                        run.close_authorization(
                            "trusted batch close: approved run list complete "
                            "(final run %s)" % entry.get("run_id"))
                    except Exception as e:                # noqa: BLE001
                        index["close_error"] = "%s: %s" % (type(e).__name__, e)
                run.close()

    index["status"] = "DONE"
    index["finished_at"] = datetime.datetime.now().isoformat(timespec="seconds")
    summarize_costs(base, index)
    json.dump(index, open(idx_path, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    build_answer_sheet(base, index)
    return 0, base


def _pct(xs, q):
    """线性插值分位数（与 trusted_controller._latency_summary 同一口径）。"""
    if not xs:
        return 0
    xs = sorted(xs)
    pos = (len(xs) - 1) * q
    lo = int(pos)
    hi = min(lo + 1, len(xs) - 1)
    return int(round(xs[lo] + (xs[hi] - xs[lo]) * (pos - lo)))


def summarize_costs(base, index):
    """质量之外的三本账：真实调用/用量、时延、墙钟。缺失即标 missing，不估算。"""
    turns = []
    for run in index["runs"]:
        if run.get("status") != "DONE":
            continue
        out = os.path.join(ROOT, run["out"])
        for td in sorted(d for d in os.listdir(out) if d.startswith("turn_")):
            man = os.path.join(out, td, "manifest.json")
            if not os.path.isfile(man):
                continue
            m = json.load(open(man, encoding="utf-8"))
            tok = m.get("tokens") or {}
            turns.append({
                "episode": run["episode_id"], "turn": td,
                "case_id": m.get("case_id"),
                "calls": m.get("gateway_calls"),
                "latency_ms": m.get("latency_ms"),
                "prompt_tokens": tok.get("prompt"),
                "completion_tokens": tok.get("completion"),
                "usage_source": tok.get("source"),
                "cache_hit": tok.get("cache_hit"),
                "cache_miss": tok.get("cache_miss"),
                "score10": m.get("score10"), "veto": m.get("veto"),
                "ingest": m.get("reply_ingest"),
            })
    lat = [t["latency_ms"] for t in turns if isinstance(t["latency_ms"], int)]
    srcs = sorted({t["usage_source"] for t in turns if t["usage_source"]})
    summary = {
        "positions_done": len(turns),
        "attempts_used": sum(t["calls"] or 0 for t in turns),
        "auxiliary_calls": 0,
        "tokens": {
            "prompt": sum(t["prompt_tokens"] or 0 for t in turns),
            "completion": sum(t["completion_tokens"] or 0 for t in turns),
            "usage_source": srcs,
            "note": ("usage_source=kotlin_len_chars 表示上游未给 usage，"
                     "是字符数估算，不是真 token；真实调用应以 API 返回 usage 为准"),
        },
        "latency_ms": {"p50": _pct(lat, 0.5), "p95": _pct(lat, 0.95),
                       "max": max(lat) if lat else 0, "n": len(lat)},
        "wall_clock": {"started_at": index.get("started_at"),
                       "finished_at": index.get("finished_at")},
        "money": {"billing_source": "unknown",
                  "note": "本地网关；单价未提供 → 金额未知（本地 URL 不等于免费）"},
        "ttft": "not_measured（非流式请求）",
        "per_turn": turns,
    }
    p = os.path.join(base, "COST_LATENCY.json")
    json.dump(summary, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    index["cost_latency"] = {k: summary[k] for k in
                             ("positions_done", "attempts_used", "tokens",
                              "latency_ms", "wall_clock", "money")}
    return summary


def build_answer_sheet(base, index):
    """把逐位原始答卷整理成人工可读的答卷（原文不加工、不替换）。"""
    lines = ["# 首场 API 文本试考答卷", "",
             "参赛 harness：`%s` ｜ 授权 `%s` ｜ 端点 `%s` ｜ model `%s` ｜ 温度 %s"
             % (index.get("harness"), index.get("authorization_id"),
                index.get("endpoint"), index.get("model"),
                index.get("temperature")), ""]
    summary = []
    for run in index["runs"]:
        out = os.path.join(ROOT, run["out"])
        man_path = os.path.join(out, "episode_manifest.json")
        if not os.path.isfile(man_path):
            continue
        epm = json.load(open(man_path, encoding="utf-8"))
        summary.append("| %s | %s | %s | %s | %s |"
                       % (run["episode_id"], epm.get("turns"),
                          epm.get("mean_score10"), epm.get("veto_turns"),
                          epm.get("run_id")))
        lines.append("## %s（%s）" % (run["episode_id"], epm.get("run_id")))
        lines.append("")
        lines.append("均分 %s ｜ veto 轮 %s ｜ 完整 %s"
                     % (epm.get("mean_score10"), epm.get("veto_turns"),
                        epm.get("complete")))
        lines.append("")
        for td in sorted(d for d in os.listdir(out) if d.startswith("turn_")):
            tdp = os.path.join(out, td)
            man = json.load(open(os.path.join(tdp, "manifest.json"),
                                 encoding="utf-8"))
            raw = open(os.path.join(tdp, "reply_raw.txt"), encoding="utf-8",
                       newline="").read()
            info = api_candidate.ingest_reply(raw)
            lines.append("### %s（%s）得分 %s%s"
                         % (man.get("case_id"), td, man.get("score10"),
                            " ｜ VETO %s" % man["veto"] if man.get("veto")
                            else ""))
            if info["kind"] != "json":
                lines += ["", "> 非 JSON 回复（%s），原文如下" % info["kind"], "",
                          "```", raw.strip()[:2000], "```", ""]
                continue
            parsed = info["parsed"]
            for k, v in (parsed.get("response") or {}).items():
                if isinstance(v, str) and v.strip():
                    lines.append("- **%s**：%s" % (k, v.strip()))
            for i, d in enumerate(parsed.get("directions") or []):
                if isinstance(d, str) and d.strip():
                    lines.append("- 方向%d：%s" % (i + 1, d.strip()))
            lines.append("")
    head = ["| episode | 轮 | 均分 | veto | run_id |", "|---|---|---|---|---|"]
    cost = []
    cl = index.get("cost_latency") or {}
    if cl:
        cost = ["", "## 成本与时延（同一批实测）", "",
                "| 项 | 值 |", "|---|---|",
                "| 完成位次 / attempts | %s / %s（辅助调用 0） |"
                % (cl.get("positions_done"), cl.get("attempts_used")),
                "| tokens（上游口径） | prompt %s + completion %s ｜来源 %s |"
                % ((cl.get("tokens") or {}).get("prompt"),
                   (cl.get("tokens") or {}).get("completion"),
                   ",".join((cl.get("tokens") or {}).get("usage_source") or [])),
                "| 时延 ms | p50 %s ｜p95 %s ｜max %s ｜n %s |"
                % ((cl.get("latency_ms") or {}).get("p50"),
                   (cl.get("latency_ms") or {}).get("p95"),
                   (cl.get("latency_ms") or {}).get("max"),
                   (cl.get("latency_ms") or {}).get("n")),
                "| 墙钟 | %s → %s |"
                % ((cl.get("wall_clock") or {}).get("started_at"),
                   (cl.get("wall_clock") or {}).get("finished_at")),
                "| 金额 | 未知（本地网关未给单价；不按「本地=免费」记账） |",
                ""]
    content = lines[:3] + head + summary + cost + [""] + lines[3:]
    p = os.path.join(base, "ANSWER_SHEET.md")
    with open(p, "w", encoding="utf-8") as f:
        f.write("\n".join(content))
    return p


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--plan", action="store_true")
    g.add_argument("--capture", action="store_true")
    g.add_argument("--live", action="store_true")
    ap.add_argument("--authorization", default=None)
    ap.add_argument("--out", default=None)
    ap.add_argument("--candidate", default=BASELINE_HARNESS,
                    help="参赛 harness（首场默认原版基线 %s）" % BASELINE_HARNESS)
    ap.add_argument("--episodes", default=",".join(PINNED_EPISODES))
    ap.add_argument("--expect-attempts", type=int, default=None)
    args = ap.parse_args()
    episodes = tuple(e.strip() for e in args.episodes.split(",") if e.strip())

    rows = plan_rows(episodes)
    check_text_only(rows)
    manifest = build_manifest(rows, episodes, args.candidate)

    if args.capture:
        return run_capture(args, manifest)
    if not args.live:
        out = args.out or os.path.join(ROOT, "logs", "api_trial_plan")
        p = write_plan(out, manifest)
        print(json.dumps({"mode": "plan",
                          "harness": manifest["harness"],
                          "positions": manifest["positions_total"],
                          "attempts": manifest["attempts_total"],
                          "manifest": os.path.relpath(p, ROOT),
                          "note": "零网络：未构造适配器、未发任何请求"},
                         ensure_ascii=False))
        return 0

    if not args.authorization:
        print(json.dumps({"error": "--live 必须提供 --authorization（授权单）；"
                                   "本次不新增额度，未获批准不运行"},
                         ensure_ascii=False))
        return 2
    rc, base = run_live(args, manifest)
    print(json.dumps({"mode": "live", "out": os.path.relpath(base, ROOT),
                      "rc": rc}, ensure_ascii=False))
    return rc


if __name__ == "__main__":
    sys.exit(main())
