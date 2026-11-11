# -*- coding: utf-8 -*-
"""tools/run_p06_recovery_review.py — P06 评审尾 recovery：成对短作业(facts/task/interaction)薄驱动。

复用 ModelGateway + AuthorizationBudget；不再造第五套调度。每个 unit 携带双臂 left/right，
同 unit 由同一模型在同一证据下评 → 组内可比；profile 固定裁判模型(facts=dsf, task/interaction=qwen)。
按 §4.4：先 --qualify 各 profile 验 1 小单元再谈批量；模型超时→该 profile 转 worker 原文复核、不硬灌。
hy4-preview/gemini 严禁。V3 W01-1A（FIRST_8H §A）：**一次逻辑计划单元恰一次真实请求**——
reserve 一次=派发一次，无隐式重试；超时/空返=本 attempt 失败，消耗不退款不补跑。
同 scope 已开始的单元不重派（filter_units）；结果文件存在即拒绝覆盖；--aid 必填；
改 SLA/配置=换新 run-tag（新测量 epoch），不得覆盖同 epoch 旧资格文件。
"""
import argparse
import json
import os
import re
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "arena"))
PK = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "p06_fast_recovery_review_20261027")
UNITS = os.path.join(PK, "RECOVERY_UNITS.jsonl")
ALLOWED = {"dsf", "qwen3.8-flash"}
FORBIDDEN = {"hy4-preview", "gemini-3.8-flash", "hy3"}
EP = "http://127.0.0.1:17770/v1"

SYS_TMPL = (
    "你是聊天回复短评审。只按给定维度给每个维度打 0-3 整数或 null，不评其它维度。\n"
    "输入含两个候选首选 left/right，你在同一证据下独立给两侧同序打分（可直接比较）。\n"
    "新提议/观点/疑问/玩笑/合法不回不因'没核实'判失败；只有把无据细节说成已核实才算事实错。\n"
    "判低分必须给逐字引用(quote)+来源id(evidence_id)+维度+严重性(major/minor)+简短说明；没问题 issues 留空。\n"
    "null 维度必须在 na 给理由。严格只输出 JSON，无解释无 markdown：\n"
    "{\"item_id\":\"原样\",\"left\":[按dimensions序的0-3/null],\"right\":[同长],\"issues\":[{\"side\":\"left|right\",\"evidence_id\":\"\",\"dimension\":\"\",\"severity\":\"major|minor\",\"quote\":\"逐字\",\"note\":\"\"}],\"na\":[{\"side\":\"left|right\",\"dimension\":\"\",\"reason\":\"\"}]}"
)


def _extract_json(text):
    if not text:
        return None
    t = text.strip()
    if t.startswith("```"):
        t = re.sub(r"^```[a-zA-Z]*", "", t).strip().rstrip("`").strip()
    try:
        return json.loads(t)
    except Exception:
        m = re.search(r"\{.*\}", t, re.S)
        if m:
            try:
                return json.loads(m.group(0))
            except Exception:
                return None
    return None


def build_user(unit):
    payload = {"item_id": unit["item_id"], "profile": unit["profile"],
               "dimensions": unit["dimensions"], "instruction": unit["instruction"],
               "evidence": unit["evidence"], "reference_card": unit.get("reference_card"),
               "answers": unit["answers"]}
    return json.dumps(payload, ensure_ascii=False)


def validate(obj, unit):
    if not isinstance(obj, dict):
        return False, "not_object"
    # V3 W01-1B：item_id 必须逐字回显（缺字段/仅前缀匹配/错误后缀都拒绝）
    if obj.get("item_id") != unit["item_id"]:
        return False, "item_id_mismatch"
    dims = unit["dimensions"]
    n = len(dims)
    na = obj.get("na")
    if not isinstance(obj.get("issues", []), list) or not isinstance(na, list):
        return False, "issues/na_not_list"
    for side in ("left", "right"):
        arr = obj.get(side)
        if not isinstance(arr, list) or len(arr) != n:
            return False, "%s_len!=%d" % (side, n)
        for idx, v in enumerate(arr):
            if v is None:
                continue
            # 0-3 只接受真正 int；bool/float/str 一律拒绝（True==1、1.0==1 陷阱）
            if isinstance(v, bool) or not isinstance(v, int) or v not in (0, 1, 2, 3):
                return False, "%s_%d_not_int_0_3" % (side, idx)
        # null 必须在 na 给原因（dimension 对得上）
        for idx, v in enumerate(arr):
            if v is not None:
                continue
            if not any(isinstance(x, dict) and x.get("side") == side
                       and x.get("dimension") == dims[idx]
                       and str(x.get("reason") or "").strip() for x in na):
                return False, "%s_%d_null_without_na_reason" % (side, idx)
    for it in obj.get("issues", []):
        if not isinstance(it, dict):
            return False, "issue_not_object"
        if it.get("side") not in ("left", "right"):
            return False, "issue_side_invalid"
        if it.get("dimension") not in dims:
            return False, "issue_dimension_not_in_unit"
        if it.get("severity") not in ("major", "minor"):
            return False, "issue_severity_invalid"
        if not str(it.get("quote") or "").strip():
            return False, "issue_quote_empty"
    for x in na:
        if not isinstance(x, dict) or x.get("side") not in ("left", "right") \
                or x.get("dimension") not in dims \
                or not str(x.get("reason") or "").strip():
            return False, "na_entry_invalid"
    return True, "ok"


def filter_units(units, ledger_path, authorization_id):
    """V3 W01-1A：同 scope 已开始过的逻辑单元不得重派（timestamp nonce 不是重派许可）；
    同批重复逻辑单元直接拒绝。已开始但未落终态的单元标 unknown_consumed，不能重派。
    返回 (dispatch, consumed)。"""
    started_units = set()
    if os.path.isfile(ledger_path):
        with open(ledger_path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                r = json.loads(line)
                if r.get("kind") == "attempt_started" \
                        and r.get("authorization_id") == authorization_id:
                    aid_ = r.get("attempt_id") or ""
                    if aid_.startswith("p06rec:"):
                        started_units.add(aid_.split(":")[1])
    seen = set()
    dispatch, consumed = [], []
    for u in units:
        uid = u["item_id"]
        if uid in seen:
            raise SystemExit("同批重复逻辑单元被拒（V3 禁止 nonce 换名重派）: %s" % uid)
        seen.add(uid)
        if uid in started_units:
            consumed.append({"item_id": uid, "status": "already_started_in_scope",
                             "unknown_consumed": True})
        else:
            dispatch.append(u)
    return dispatch, consumed


class Reviewer(object):
    def __init__(self, budget, ev, nonce):
        from arena.lb_gateway import ADAPTERS, ModelGateway
        self.inner = ModelGateway(ADAPTERS["openai_compat"](), event_log_path=ev)
        self.budget = budget
        self.nonce = nonce
        self.seq = 0

    def run(self, unit, timeout_s):
        model = unit["model"]
        if model in FORBIDDEN or model not in ALLOWED:
            raise RuntimeError("模型越权/禁: %r" % model)
        self.seq += 1
        aid = "p06rec:%s:%s:%d" % (unit["item_id"], self.nonce, self.seq)
        self.budget.reserve(aid, purpose="p06_recovery_short_review")
        sys_p = SYS_TMPL
        usr = build_user(unit)
        t0 = time.time()
        # V3 W01-1A：reserve 一次 = 恰一次真实请求。无隐式重试；
        # 超时/空返 = 本 attempt 失败（消耗不退款，不自动补跑）。
        try:
            res = self.inner.generate(sys_p, usr, model=model, temperature=0.0,
                                      timeout_s=timeout_s, tag="review", attempt_id=aid)
        except Exception as e:
            err = "%s: %s" % (type(e).__name__, str(e)[:200])
            self.budget.record("attempt_failed", aid, error=err, model=model)
            return {"item_id": unit["item_id"], "model": model, "ok": False, "err": err,
                    "ms": int((time.time() - t0) * 1000)}
        raw_text = res.get("text") or ""
        if not raw_text.strip():
            self.budget.record("attempt_failed", aid, error="empty_content_response",
                               model=model)
            return {"item_id": unit["item_id"], "model": model, "ok": False,
                    "err": "empty_content_response", "ms": int((time.time() - t0) * 1000)}
        self.budget.record("attempt_completed", aid, model=model, usage=res.get("usage"))
        obj = _extract_json(raw_text)
        ok, why = validate(obj, unit)
        return {"item_id": unit["item_id"], "profile": unit["profile"], "model": model,
                "ok": ok, "why": why, "left": (obj or {}).get("left"),
                "right": (obj or {}).get("right"), "issues": (obj or {}).get("issues"),
                "na": (obj or {}).get("na"), "ms": int((time.time() - t0) * 1000),
                "raw": raw_text, "raw_display": raw_text[:1500]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--qualify", action="store_true", help="各 profile 先验 1 小单元")
    ap.add_argument("--per-profile", type=int, default=0,
                    help="小批：每个 profile 取前 N 个单元(0=全部)")
    ap.add_argument("--cap", type=int, default=243)
    ap.add_argument("--timeout-s", type=int, default=45)
    ap.add_argument("--aid", required=True,
                    help="授权 ID 必填。旧 AUTH-P06-RECOVERY-20261027-01 已对账收口（漏计9），"
                         "禁止默认复用或复活")
    ap.add_argument("--outdir", default=None,
                    help="默认 logs/p06_recovery_review_<run_tag>（V3：按 run 分目录，不覆盖旧输出）")
    ap.add_argument("--run-tag", default=str(int(time.time())),
                    help="测量 epoch；改 SLA/配置=换新 run-tag，不得覆盖同 epoch 旧资格文件")
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    os.environ.setdefault("LB_API_BASE", EP)
    outdir = a.outdir or os.path.join("logs", "p06_recovery_review_%s" % a.run_tag)
    outdir = outdir if os.path.isabs(outdir) else os.path.join(ROOT, outdir)
    os.makedirs(outdir, exist_ok=True)
    units = [json.loads(l) for l in open(UNITS, encoding="utf-8") if l.strip()]
    from arena.isolation.authorization_budget import AuthorizationBudget
    ledger = os.path.join(ROOT, "logs", "auth_budget", a.aid + ".jsonl")
    scope = {"experiment": "P06_recovery_short_review", "allowed_models": sorted(ALLOWED),
             "forbidden_models": sorted(FORBIDDEN), "endpoint": EP}
    budget = AuthorizationBudget(ledger, a.aid, a.cap, scope=scope)

    sel = units
    if a.qualify:
        sel = [next(u for u in units if u["profile"] == p) for p in ("facts", "task", "interaction")]
    elif a.per_profile:
        sel = []
        for p in ("facts", "task", "interaction"):
            sel += [u for u in units if u["profile"] == p][:a.per_profile]

    # V3 W01-1A：同 scope 已开始单元不重派；同批重复单元拒绝；nonce 不构成重派许可
    dispatch, consumed = filter_units(sel, budget.ledger_path, a.aid)
    for c in consumed:
        print("  SKIP %-14s %s (unknown_consumed，不重派)" % (c["item_id"], c["status"]))
    if not dispatch:
        print("无未开始的计划单元可派发；退出")
        return

    budget.ensure_batch(len(dispatch), purpose="p06_recovery_batch")
    ev = os.path.join(outdir, "REVIEW_GATEWAY.jsonl")
    rev = Reviewer(budget, ev, a.run_tag)
    out = []
    for u in dispatch:
        r = rev.run(u, a.timeout_s)
        out.append(r)
        print("  %-14s model=%-14s ok=%s %s" % (r["item_id"], r["model"], r["ok"],
              ("" if r["ok"] else "ERR=" + str(r.get("err") or r.get("why")))))
    resfile = os.path.join(outdir,
                           "REVIEW_QUALIFY__%s.json" % a.run_tag if a.qualify
                           else "REVIEW_RESULTS__%s.jsonl" % a.run_tag)
    if os.path.exists(resfile):
        raise SystemExit("拒绝覆盖已存在结果文件（V3 W01-1A：存在即拒绝，换 run-tag）: %s"
                         % resfile)
    if a.qualify:
        json.dump(out, open(resfile, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    else:
        with open(resfile, "w", encoding="utf-8") as f:
            for r in out:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
    okc = sum(1 for r in out if r["ok"])
    print("QUALIFY" if a.qualify else "BATCH", "ok=%d/%d" % (okc, len(out)), "budget", budget.summary())


if __name__ == "__main__":
    main()
