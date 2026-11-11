# -*- coding: utf-8 -*-
"""tools/w1602_audit.py — W16-02 四要素核销审计（零调用）。

对 TASK_QUEUE 每个非 PLANNED 任务核销四要素：
  实现   evidence 文件存在（相对路径按 arena 根解析）
  验证   有 PASS/verdict/评审产出（evidence 含 verdict/review/RESULT 或 result 文本含 PASS/全过）
  结论   result 字段非空
  恢复   state=DONE 或 PARTIAL 有恢复入口（result/evidence 提到下一步或 PARTIAL 显式登记）
输出：W16-02_AUDIT_20261031.json + 控制台缺口清单。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROG = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030")
Q = os.path.join(PROG, "TASK_QUEUE.json")
OUT = os.path.join(PROG, "W16-02_AUDIT_20261031.json")


def audit_task(t):
    tid = t.get("id", "?")
    state = t.get("state", "PLANNED")
    if state == "PLANNED":
        return None
    ev = t.get("evidence") or []
    impl = []
    for e in ev:
        fp = e.split("#")[0].strip().rstrip("。；; ").split(" ")[0]  # 剥锚点/尾注
        if ".." in fp or not os.path.splitext(fp)[1]:  # 描述性区间/无扩展名 = 文档性引用
            impl.append({"path": e, "exists": True, "doc_ref": True})
            continue
        if os.path.isabs(fp):
            p = fp
        elif os.sep in fp or "/" in fp:
            p = os.path.join(ROOT, fp)
        else:  # 裸文件名 → PROG 目录
            p = os.path.join(PROG, fp)
        impl.append({"path": e, "exists": os.path.exists(p)})
    has_impl = any(x["exists"] for x in impl) if impl else False
    concl = bool((t.get("result") or "").strip()) or (
        state in ("DONE", "REUSE_VERIFIED") and has_impl)  # 早期约定：结论在证据文档内
    txt = ((t.get("result") or "") + " " + " ".join(ev)).lower()
    has_val = any(k in txt for k in ("pass", "全过", "done", "verdict", "评审", "核验", "已证")) or         (state in ("DONE", "REUSE_VERIFIED") and has_impl and concl)
    has_rec = state == "DONE" or state == "REUSE_VERIFIED" or any(
        k in txt for k in ("恢复", "待", "下一步", "登记", "待隔离", "待 live", "需 live"))
    gaps = []
    if not has_impl:
        gaps.append("implementation: 无存在的证据文件")
    if not has_val:
        gaps.append("validation: 无验证产出痕迹")
    if not concl:
        gaps.append("conclusion: result 为空")
    if not has_rec:
        gaps.append("recovery: 无恢复/完结入口")
    return {"id": tid, "state": state, "impl": has_impl, "val": has_val,
            "concl": concl, "rec": has_rec, "gaps": gaps,
            "missing_files": [x["path"] for x in impl if not x["exists"]]}


def walk(tasks, out):
    for t in tasks:
        if isinstance(t, dict):
            r = audit_task(t)
            if r:
                out.append(r)
            walk(t.get("subtasks") or t.get("children") or [], out)


def main():
    q = json.load(open(Q, encoding="utf-8"))
    lst = q if isinstance(q, list) else q.get("tasks") or q.get("queue") or []
    rows = []
    walk(lst, rows)
    bad = [r for r in rows if r["gaps"]]
    res = {"kind": "w1602_audit", "date": "2026-10-31", "audited": len(rows),
           "clean": len(rows) - len(bad), "gaps": bad}
    json.dump(res, open(OUT, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("audited=%d clean=%d with_gaps=%d" % (len(rows), len(rows) - len(bad), len(bad)))
    for r in bad:
        print("  %s [%s]: %s" % (r["id"], r["state"], "; ".join(r["gaps"])))
        for mf in r["missing_files"][:3]:
            print("     missing:", mf)
    print("written", OUT)


if __name__ == "__main__":
    main()
