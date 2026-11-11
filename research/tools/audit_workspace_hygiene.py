# -*- coding: utf-8 -*-
"""tools/audit_workspace_hygiene.py — 工作区污染体检（只读，零模型调用）。

把 2026-11-02 人工体检里反复出现的四类污染做成机械检查：
  C1 入口新鲜度：RUNBOOK 指针 / RESEARCH_STATE.as_of / 冠军口径是否互相矛盾；
  C2 墓碑纪律：已废止的注册表必须留 tombstone（kind/superseded_by 指向现存文件）；
  C3 状态散文：根目录叙述文档不得自带"当前冠军/正在运行"这类会烂掉的状态声明；
  C4 证据路径：TASK_QUEUE.evidence 里的路径必须真实存在（悬空=证据失联）；
  C5 文件卫生：.bak-/__pycache__/.tmp 残留；
  C6 工具契约：带 argparse 的 tools/*.py 必须有模块 docstring（--help 契约），
     且本工具绝不执行任何 tools/*.py（历史上 --help 触发过真实写盘）。

用法：python tools/audit_workspace_hygiene.py [--strict] [--write-log-index] [--out 路径]
  --strict         存在 error 级问题时退出码 1
  --write-log-index 同时生成 logs/INDEX.json（599 个条目的一级索引，只增不改）
只读约束：不删除、不改写任何既有文件（仅 --write-log-index 时新建 logs/INDEX.json）。
"""
import argparse
import datetime
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GS_REL = "refs/experiment_protocol_20261023/global_harness_program_20261030"

FORBIDDEN_IN_RUNBOOK = ("r4_guard_e3", "r5_tier", "r7_supersede")
STATUS_CLAIM_RE = re.compile(r"当前冠军|当前候选|当前状态|当前入口|当前主线|正在运行|RUNNING|in progress")
STATUS_CLAIM_STRICT = re.compile(r"当前冠军|当前候选|正在运行|in progress")   # 任务书里"当前入口"类指针合法
STATUS_EXEMPT = {"AGENTS.md", "WORKER_NEW_WINDOW_START.md"}
SNAPSHOT_RE = re.compile(r"^(WORKER_HANDOFF|HANDOFF_TO_NEXT_SESSION)_.*\.md$")   # 带日期快照，冻结不改
EVIDENCE_EXT = (".md", ".json", ".jsonl", ".py", ".csv", ".json5", ".txt")
DEAD_ROOT_DOCS = ("HANDOFF_TO_NEXT_SESSION_20261026_FULL.md", "HANDOFF_TO_NEXT_SESSION_20261026_M0_CLOSE.md",
                  "STATE_MAP_20261025.md", "SESSION_COORDINATION_V1.md", "BASELINE_SCOPE_V2.md", "START_PROMPT.md")


def _read_json(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return json.load(f)


def _parse_ts(s):
    s = (s or "").strip()
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%dT%H:%M:%S.%f%z", "%Y-%m-%dT%H:%M:%S%z",
                "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"):
        try:
            dt = datetime.datetime.strptime(s.replace("+08:00", "")[:len(s.replace("+08:00", ""))], fmt)
            return dt
        except Exception:
            continue
    return None


def _cands(text):
    """从 main_incumbent 提取"当前"候选名；含 历史/退役/取代/not_promoted 的分句整体跳过。"""
    keep = [seg for seg in re.split(r"[；;]", text or "")
            if not re.search(r"历史|退役|转|取代|not_promoted", seg)]
    return {w for w in re.findall(r"[a-z][a-z0-9_]{5,}", "；".join(keep)) if "_" in w}


def check_entry_freshness():
    out = []
    rb = _read_json("WORKER_CURRENT_RUNBOOK.json")
    rs = _read_json(os.path.join(GS_REL, "RESEARCH_STATE.json"))
    rb_ts, rs_ts = _parse_ts(rb.get("as_of") or rb.get("updated_at")), _parse_ts(rs.get("as_of"))
    now = datetime.datetime.now()
    if not rs_ts:
        out.append({"level": "error", "check": "C1", "msg": "RESEARCH_STATE.as_of 缺失或不可解析"})
    else:
        if (now - rs_ts).days > 2:
            out.append({"level": "error", "check": "C1", "msg": "RESEARCH_STATE.as_of 距今 %d 天（>2 天冻结阈值）" % (now - rs_ts).days})
        if rb_ts and rb_ts < rs_ts:
            out.append({"level": "error", "check": "C1", "msg": "RUNBOOK %s 早于 RESEARCH_STATE %s" % (rb.get("as_of"), rs.get("as_of"))})
    inc = rs.get("main_incumbent") or ""
    rb_cand = rb.get("current_candidate") or ""
    for name in sorted(_cands(inc)):
        if name not in rb_cand:
            out.append({"level": "error", "check": "C1", "msg": "RUNBOOK.current_candidate 未包含权威冠军名 %r" % name})
    for bad in FORBIDDEN_IN_RUNBOOK:
        if bad in rb_cand:
            out.append({"level": "error", "check": "C1", "msg": "RUNBOOK.current_candidate 仍写已退役候选 %r" % bad})
    return out, {"runbook_as_of": rb.get("as_of"), "research_as_of": rs.get("as_of"), "main_incumbent": inc}


def check_tombstones():
    out = []
    p = os.path.join(ROOT, "RUN_STATE.json")
    if os.path.exists(p):
        try:
            d = _read_json("RUN_STATE.json")
        except Exception as e:  # noqa: BLE001
            out.append({"level": "error", "check": "C2", "msg": "RUN_STATE.json 不可解析: %s" % e})
            d = {}
        if d.get("kind") != "deprecated_registry":
            out.append({"level": "error", "check": "C2", "msg": "RUN_STATE.json 非 deprecated_registry tombstone（可能是活的旧注册表）"})
        sup = d.get("superseded_by") or ""
        for ref in [x.strip() for x in re.split(r"[+，,;；]", sup) if x.strip()]:
            if not ref.endswith(".json"):
                continue
            ok = any(os.path.exists(os.path.join(ROOT, base, ref)) for base in ("", GS_REL))
            if not ok:
                out.append({"level": "error", "check": "C2", "msg": "tombstone superseded_by 指向不存在的 %r" % ref})
    tb = os.path.join(ROOT, "TASK_BOARD.md")
    if os.path.exists(tb):
        head = open(tb, encoding="utf-8", errors="replace").read(600)
        if "废止" not in head and "deprecated" not in head.lower():
            out.append({"level": "error", "check": "C2", "msg": "TASK_BOARD.md 无废止横幅（会与 TASK_QUEUE 竞争权威）"})
    return out


def check_status_claims():
    out = []
    targets = [f for f in os.listdir(ROOT) if f.endswith(".md")]
    files = [(f, os.path.join(ROOT, f), STATUS_CLAIM_RE) for f in targets
             if f not in STATUS_EXEMPT and not SNAPSHOT_RE.match(f)]
    master_rel = os.path.join(GS_REL, "MASTER_CONTINUOUS_HARNESS_PROGRAM.md")
    if os.path.exists(os.path.join(ROOT, master_rel)):
        files.append((master_rel, os.path.join(ROOT, master_rel), STATUS_CLAIM_STRICT))
    for name, path, pat in files:
        try:
            lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
        except Exception:  # noqa: BLE001
            continue
        head = "\n".join(lines[:40])
        if "废止" in head or "deprecated" in head.lower():
            continue
        if pat is STATUS_CLAIM_STRICT:
            lines = lines[:60]        # 任务书只查头部状态段；任务表的"当前候选"是任务名不是状态
        for i, line in enumerate(lines, 1):
            if pat.search(line):
                out.append({"level": "warn", "check": "C3", "msg": "%s:%d 状态散文：%s" % (name, i, line.strip()[:90])})
    return out


def _norm_evidence_path(ev):
    s = (ev or "").strip().strip("`")
    if not s or " " in s or not s.lower().endswith(EVIDENCE_EXT):
        return None
    s = s.split("#")[0]
    if s.startswith("D:/lovebrain-arena/") or s.startswith("D:\\lovebrain-arena\\"):
        s = s.replace("\\", "/").split("D:/lovebrain-arena/", 1)[1]
    if os.path.isabs(s) or "://" in s:
        return None
    return s


def _basename_index():
    """refs/experiment_protocol_20261023 全树 basename → 相对路径列表（解决 evidence 写裸文件名的历史习惯）。"""
    idx = {}
    base = os.path.join(ROOT, "refs", "experiment_protocol_20261023")
    for b, _dirs, files in os.walk(base):
        for fn in files:
            idx.setdefault(fn, []).append(os.path.relpath(os.path.join(b, fn), ROOT).replace("\\", "/"))
    return idx


def check_evidence_paths():
    out, checked, missing = [], 0, []
    q = _read_json(os.path.join(GS_REL, "TASK_QUEUE.json"))
    bidx = _basename_index()
    for t in q.get("tasks", []):
        for ev in t.get("evidence", []):
            rel = _norm_evidence_path(ev)
            if not rel:
                continue
            checked += 1
            if os.path.exists(os.path.join(ROOT, rel)):
                continue
            # evidence 允许两类历史写法：相对包目录的裸文件名、相对 long_probe 目录的路径
            if os.path.exists(os.path.join(ROOT, GS_REL, rel)):
                continue
            if os.path.exists(os.path.join(ROOT, os.path.dirname(GS_REL), "long_probe_20261030", rel)):
                continue
            hits = bidx.get(os.path.basename(rel)) or []
            if hits:
                out.append({"level": "warn", "check": "C4",
                            "msg": "%s evidence 写法不唯一：%s → 实际在 %s（建议改写成全路径）" % (t["id"], rel, hits[0])})
                continue
            missing.append((t["id"], rel))
    for tid, rel in missing:
        out.append({"level": "error", "check": "C4", "msg": "%s evidence 悬空：%s" % (tid, rel)})
    return out, {"checked": checked, "missing": len(missing), "basenames_indexed": len(bidx)}


def check_file_hygiene():
    out = []
    tools_dir = os.path.join(ROOT, "tools")
    baks = sorted(f for f in os.listdir(tools_dir) if ".bak-" in f)
    if baks:
        out.append({"level": "warn", "check": "C5", "msg": "tools/*.bak-* 残留 %d 个（建议归档，待用户确认）" % len(baks)})
    pyc = []
    for base, dirs, _files in os.walk(ROOT):
        depth = base[len(ROOT):].count(os.sep)
        if depth >= 3:
            dirs[:] = []
        if "__pycache__" in dirs:
            pyc.append(os.path.relpath(os.path.join(base, "__pycache__"), ROOT))
    if pyc:
        out.append({"level": "warn", "check": "C5", "msg": "__pycache__ 残留 %d 处：%s" % (len(pyc), ", ".join(pyc))})
    dead = [d for d in DEAD_ROOT_DOCS if os.path.exists(os.path.join(ROOT, d))]
    if dead:
        out.append({"level": "warn", "check": "C5", "msg": "根目录死文档 %d 篇待归档：%s" % (len(dead), ", ".join(dead))})
    return out


def check_tool_contracts():
    out, n = [], 0
    tdir = os.path.join(ROOT, "tools")
    for f in sorted(os.listdir(tdir)):
        if not f.endswith(".py"):
            continue
        src = open(os.path.join(tdir, f), encoding="utf-8", errors="replace").read()
        if "argparse.ArgumentParser(" not in src:
            continue
        n += 1
        # 注：冻结执行器（sha256 被 plan 绑定，如 exam_interleave_plan.py）禁止为补 docstring 改文件，
        # 否则全部旧 plan 触发"配置漂移"。C6 只约束新写工具。
        if '"""' not in src.split("argparse.ArgumentParser(")[0]:
            out.append({"level": "warn", "check": "C6",
                        "msg": "tools/%s argparse 前无模块 docstring（新工具须补；冻结执行器不得改）" % f})
    return out, {"argparse_tools": n}


def build_log_index():
    ldir = os.path.join(ROOT, "logs")
    entries, total_files, total_bytes = [], 0, 0
    for name in sorted(os.listdir(ldir)):
        p = os.path.join(ldir, name)
        if os.path.isdir(p):
            n, b, mt = 0, 0, 0.0
            for base, dirs, files in os.walk(p):
                if os.path.relpath(base, p).count(os.sep) > 2:
                    dirs[:] = []
                for fn in files:
                    fp = os.path.join(base, fn)
                    try:
                        st = os.stat(fp)
                    except OSError:
                        continue
                    n += 1
                    b += st.st_size
                    mt = max(mt, st.st_mtime)
            entries.append({"name": name, "kind": "dir", "n_files": n, "bytes": b,
                            "mtime": datetime.datetime.fromtimestamp(mt).strftime("%Y-%m-%d %H:%M") if mt else ""})
            total_files += n
            total_bytes += b
        else:
            st = os.stat(p)
            entries.append({"name": name, "kind": "file", "n_files": 1, "bytes": st.st_size,
                            "mtime": datetime.datetime.fromtimestamp(st.st_mtime).strftime("%Y-%m-%d %H:%M")})
            total_files += 1
            total_bytes += st.st_size
    idx = {"kind": "logs_index", "generated_at": datetime.datetime.now().isoformat(timespec="seconds"),
           "note": "一级索引（目录内最多下钻 2 层计数）；历史批目录只增不改，判决只认 refs/ 下 verdict 文档",
           "n_entries": len(entries), "n_files": total_files, "bytes": total_bytes, "entries": entries}
    path = os.path.join(ldir, "INDEX.json")
    json.dump(idx, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    return path, idx


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="存在 error 时退出码 1")
    ap.add_argument("--write-log-index", action="store_true", help="生成 logs/INDEX.json")
    ap.add_argument("--out", default=os.path.join("logs", "hygiene_audit_%s.json" % datetime.date.today().strftime("%Y%m%d")))
    a = ap.parse_args()

    issues, facts = [], {}
    for fn in (check_entry_freshness, check_tombstones, check_status_claims, check_file_hygiene, check_tool_contracts):
        r = fn()
        if isinstance(r, tuple):
            issues.extend(r[0])
            facts[fn.__name__] = r[1]
        else:
            issues.extend(r)
    ev_issues, ev_facts = check_evidence_paths()
    issues.extend(ev_issues)
    facts["evidence"] = ev_facts

    errs = [i for i in issues if i["level"] == "error"]
    warns = [i for i in issues if i["level"] == "warn"]
    report = {"kind": "workspace_hygiene_audit", "generated_at": datetime.datetime.now().isoformat(timespec="seconds"),
              "readonly": True, "n_error": len(errs), "n_warn": len(warns),
              "errors": errs, "warnings": warns, "facts": facts}
    out = os.path.join(ROOT, a.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump(report, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("error=%d warn=%d" % (len(errs), len(warns)))
    for i in errs:
        print("  [E][%s] %s" % (i["check"], i["msg"]))
    for i in warns[:40]:
        print("  [W][%s] %s" % (i["check"], i["msg"]))
    if len(warns) > 40:
        print("  ... 其余 %d 条见报告" % (len(warns) - 40))
    print("report:", os.path.relpath(out, ROOT).replace("\\", "/"))
    if a.write_log_index:
        p, idx = build_log_index()
        print("log_index: %s (%d entries, %d files)" % (os.path.relpath(p, ROOT).replace("\\", "/"),
                                                        idx["n_entries"], idx["n_files"]))
    return 1 if (a.strict and errs) else 0


if __name__ == "__main__":
    sys.exit(main())
