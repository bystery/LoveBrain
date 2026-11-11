# -*- coding: utf-8 -*-
"""review_loop.py — C5 人工评审闭环（G07）：可运行的材料生成/审查录入/导入校验/汇总/反查。

R3#4（独立复核 §8）加固：
  1. 全材料内容绑定：映射另存 material 内容散列；ingest/aggregate 均重验
     materials/MAT_xx.json 实际内容——篡改展示原文（哪怕保留旧 binding hash）必须拒；
     同时 ingest 现场重算 binding（live case + arms）与映射比对，题面漂移必须拒。
  2. 去重与显式替代：同 material_id+同 reviewer 重复表单——无 revision 或 revision
     不递增 → 拒；带更高 revision → 显式替代旧表（superseded 记录，不重复计票）。
  3. 统计分母分离：票数（compare 投票数）/ 已审材料数（去重后 distinct material 数）/
     未审材料数（总材料数 − 已审材料数）分列，杜绝"总12/审13/未审−1"。
  4. episode 顶层 persona/initial_kb 不丢：load_case 返回 episode 顶层字段，
     gen 时与轮级字段合并（轮级优先）进材料"共同背景/初始知识库"。

子命令：
  gen       从材料清单生成匿名评审材料 + 空白录入表 + 另存映射（臂名不进材料）
  ingest    校验录入表：标签合法性/必填项/绑定散列/材料内容绑定/reviewer 身份/去重
  aggregate C6 口径分层汇总：全部尝试/完成/解析/格式/合法动作/首选非空/任一备选可用/
            人工判定/争议未审；无样本写 N/A；失败不从主分母消失
  lookup    反查：anon_id -> 全局身份/臂名/冻结题面/原样输出

C5 硬约束落实：
  - 材料由冻结题面+实际calls+reply_raw 生成，含共同背景/真实历史/本轮消息/
    意图/有效许可/原样输出/匿名顺序/材料版本与绑定散列；
  - 映射另存 mapping_secret.json，审查材料不含臂名或分数；
  - 缺陷必须指向原文字段并附证据或标"个人风格偏好"；
  - 演练标签 fixture/reviewer，不伪造用户投票。

工作目录可用环境变量 LB_REVIEW_WORK 覆盖（自测用；缺省为发布区 review_loop）。
"""
import hashlib
import json
import os
import random
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORK = os.environ.get("LB_REVIEW_WORK") or os.path.join(
    ROOT, "benchmark", "releases", "arena_v1", "review_loop")
MATERIAL_VERSION = "rv2"   # rv2 = R3#4 内容绑定+去重+分母分离

SENDABILITY = ["原样可发", "修改后可发", "不建议发", "信息不足"]
COMPARE = ["甲", "乙", "相当", "两者都不合格", "信息不足", "不回更合适"]
SEMANTIC_DIMS = ["事实与来源", "主体", "互动动机", "时间地点", "分寸",
                 "no_reply是否合适", "是否值得接着聊"]
VERDICTS = ["合格", "有缺陷", "不合格", "信息不足"]


def sha_obj(o):
    return hashlib.sha256(json.dumps(o, ensure_ascii=False,
                                     sort_keys=True).encode("utf-8")).hexdigest()[:16]


def load_case(ref):
    """ref: 'episode:case' 全局身份 或 独立 case 文件相对路径。
    返回 dict 额外携带 episode 顶层 persona/initial_kb（R3#4：顶层不得丢）。"""
    if ref.endswith(".json"):
        p = os.path.join(ROOT, ref)
        d = json.load(open(p, encoding="utf-8"))
        return {"gid": d.get("case_id"), "file": ref, "turn": d,
                "input": d.get("input", {}), "gold": d.get("gold", {}),
                "ep_persona": d.get("persona") or {},
                "ep_initial_kb": d.get("initial_kb")}
    if ":" not in ref:
        p = os.path.join(ROOT, "benchmark/cases", ref + ".json")
        if os.path.isfile(p):
            d = json.load(open(p, encoding="utf-8"))
            return {"gid": d.get("case_id"),
                    "file": "benchmark/cases/" + ref + ".json", "turn": d,
                    "input": d.get("input", {}), "gold": d.get("gold", {}),
                    "ep_persona": d.get("persona") or {},
                    "ep_initial_kb": d.get("initial_kb")}
        raise SystemExit("case 不存在: %s" % ref)
    eid, cid = ref.rsplit(":", 1)
    for sub in ("", "dev/"):
        p = os.path.join(ROOT, "benchmark/episodes", sub, eid + ".json")
        if os.path.isfile(p):
            ep = json.load(open(p, encoding="utf-8"))
            for t in ep.get("turns", []):
                if t.get("case_id") == cid:
                    return {"gid": ref, "file": os.path.relpath(p, ROOT),
                            "turn": t, "input": t.get("input", {}),
                            "gold": t.get("gold", {}),
                            "ep_persona": ep.get("persona") or {},
                            "ep_initial_kb": ep.get("initial_kb")}
    raise SystemExit("case 不存在: %s" % ref)


def build_material(m, c, i, rng):
    """单份材料构建（gen 与内容复验共用同一构造 → 篡改必现形）。"""
    inp = c["input"]
    msgs = inp.get("messages", [])
    anon_x, anon_y = rng.sample(["甲", "乙"], 2) if m.get("shuffle", True) \
        else ("甲", "乙")
    arms = {anon_x: m["arm_x"], anon_y: m["arm_y"]}
    # 复核 v3 §2C：合法 null 不硬填——臂回复可为 null（不回），绑定串用
    # 稳定文本表示；null 臂在 原样输出 中如实显示 null。
    _rep = lambda arm: "<NULL(合法不回)>" if arm.get("reply") is None \
        else str(arm.get("reply"))
    binding = sha_obj({"case_ref": m["case_ref"],
                       "case_sha": sha_obj(c["turn"]),
                       "arms": _rep(m["arm_x"]) + "|" + _rep(m["arm_y"]),
                       "material_version": MATERIAL_VERSION})
    # R3#4：episode 顶层 persona/initial_kb 与轮级合并（轮级优先，顶层兜底）
    turn_persona = c["turn"].get("persona") or inp.get("persona") or {}
    persona = dict(c["ep_persona"] or {})
    persona.update(turn_persona if isinstance(turn_persona, dict) else
                   {"_text": turn_persona})
    initial_kb = c["ep_initial_kb"]
    if inp.get("initial_kb") is not None or c["turn"].get("initial_kb") is not None:
        initial_kb = inp.get("initial_kb", c["turn"].get("initial_kb"))
    mat = {
        "material_id": "MAT_%02d" % i, "material_version": MATERIAL_VERSION,
        "binding_sha256_16": binding,
        "state": m["state"],
        "材料来源": "fixture_drill_spec（手写评审演练稿；生产匿名材料必须由"
                   "冻结 runs 的 calls/reply_raw/manifest 生成，见 V14 C2）",
        "共同背景": persona,
        "初始知识库": initial_kb,
        "真实历史": msgs[:-1] if len(msgs) > 1 else [],
        "本轮待回复消息": msgs[-1] if msgs else None,
        "请求时钟": inp.get("clock"),
        "用户意图": (inp.get("intent") or {}).get("text") if isinstance(
            inp.get("intent"), dict) else inp.get("intent"),
        "有效许可": {"may_decline": inp.get("may_decline",
                      c["turn"].get("meta", {}).get("may_decline", False)),
                     "type": inp.get("type", "normal")},
        "媒体": inp.get("media"),
        "臂顺序": [anon_x, anon_y],
        "原样输出": {a: arms[a].get("reply") for a in (anon_x, anon_y)},
        "流程状态": {a: {"parse_ok": arms[a].get("parse_ok", True),
                         "format_ok": arms[a].get("format_ok", True),
                         "action_legal": arms[a].get("action_legal", True)}
                     for a in (anon_x, anon_y)},
        "评审维度": SEMANTIC_DIMS, "可发送性选项": SENDABILITY,
        "比较选项": COMPARE,
        "说明": "评审前不展示臂名或分数；缺陷指向原文字段+证据或'个人风格偏好'",
    }
    return mat, anon_x, anon_y, binding


def material_content_sha(mat):
    """R3#4：材料文件内容散列（全字段、确定性序列化）。"""
    return sha_obj(mat)


def verify_material_content(mat_path, mapping_rec, why, tag):
    """R3#4：材料实际内容 vs 映射绑定（篡改展示原文必拒）。"""
    try:
        mat = json.load(open(mat_path, encoding="utf-8"))
    except Exception as e:
        why.append("%s: 材料文件不可读 %s" % (tag, e))
        return None
    if mat.get("material_id") != mapping_rec.get("material_id"):
        why.append("%s: 材料 material_id 与映射不符" % tag)
    # 内容散列（映射里存的是 gen 时刻散列；材料任何字段被改都不同）
    if mapping_rec.get("material_sha256_16") and \
            material_content_sha(mat) != mapping_rec["material_sha256_16"]:
        why.append("%s: 材料内容与映射绑定不一致（展示原文被篡改）" % tag)
    return mat


# ---------- gen ----------
def cmd_gen(spec_path):
    spec = json.load(open(spec_path, encoding="utf-8"))
    rng = random.Random(spec.get("seed", 20261021))
    os.makedirs(os.path.join(WORK, "materials"), exist_ok=True)
    os.makedirs(os.path.join(WORK, "forms"), exist_ok=True)
    mapping, index = {}, []
    for i, m in enumerate(spec["materials"], 1):
        c = load_case(m["case_ref"])
        mat, anon_x, anon_y, binding = build_material(m, c, i, rng)
        mapping[mat["material_id"]] = {
            "material_id": mat["material_id"],
            "case_ref": m["case_ref"], "gid": c["gid"], "file": c["file"],
            "anon_to_arm": {a: ("arm_x" if a == anon_x else "arm_y")
                            for a in (anon_x, anon_y)},
            "binding_sha256_16": binding,
            "material_sha256_16": material_content_sha(mat),
            "arms": ("<NULL(合法不回)>" if m["arm_x"]["reply"] is None
                     else m["arm_x"]["reply"]) + "|" +
                    ("<NULL(合法不回)>" if m["arm_y"]["reply"] is None
                     else m["arm_y"]["reply"]),
            "state": m["state"]}
        json.dump(mat, open(os.path.join(WORK, "materials",
                                         mat["material_id"] + ".json"), "w",
                            encoding="utf-8"), ensure_ascii=False, indent=1)
        json.dump({"material_id": mat["material_id"],
                   "binding_sha256_16": binding,
                   "_填写说明": "semantic 各维填 VERDICTS 之一；sendability 填 SENDABILITY；"
                                "compare 填 COMPARE；defects 每条 {field, evidence, "
                                "kind:evidence|style_preference}；reviewer 填身份；"
                                "同 material+reviewer 重交须 revision 递增",
                   "semantic": {d: "" for d in SEMANTIC_DIMS},
                   "sendability": {"甲": "", "乙": ""},
                   "compare": "", "defects": [], "reviewer": "", "revision": 1},
                  open(os.path.join(WORK, "forms", "entry_%02d.json" % i), "w",
                       encoding="utf-8"), ensure_ascii=False, indent=1)
        index.append({"material_id": mat["material_id"], "case_ref": m["case_ref"],
                      "state": m["state"]})
    json.dump({"mapping": mapping, "index": index, "seed": spec.get("seed", 20261021),
               "material_version": MATERIAL_VERSION},
              open(os.path.join(WORK, "mapping_secret.json"), "w",
                   encoding="utf-8"), ensure_ascii=False, indent=1)
    print("gen: %d materials -> %s/materials, forms, mapping_secret.json"
          % (len(index), os.path.relpath(WORK, ROOT) if WORK.startswith(ROOT) else WORK))


# ---------- ingest ----------
def cmd_ingest(entries_dir):
    mp = json.load(open(os.path.join(WORK, "mapping_secret.json"),
                        encoding="utf-8"))
    accepted, rejected, superseded = [], [], []
    seen = {}   # (material_id, reviewer) -> (revision, entry filename)
    for fn in sorted(os.listdir(entries_dir)):
        if not fn.endswith(".json"):
            continue
        e = json.load(open(os.path.join(entries_dir, fn), encoding="utf-8"))
        mid = e.get("material_id")
        why = []
        rec = mp["mapping"].get(mid)
        if mid not in mp["mapping"]:
            why.append("material_id 不在映射中")
        else:
            if e.get("binding_sha256_16") != mp["mapping"][mid]["binding_sha256_16"]:
                why.append("绑定散列不一致（题面或输出漂移）")
            # R3#4：材料实际内容 vs 映射绑定（篡改展示原文必拒）
            verify_material_content(
                os.path.join(WORK, "materials", mid + ".json"),
                mp["mapping"][mid], why, fn)
            if not e.get("reviewer"):
                why.append("缺 reviewer 身份")
            for d, v in (e.get("semantic") or {}).items():
                if v and v not in VERDICTS:
                    why.append("semantic[%s] 非法值 %r" % (d, v))
            for a in ("甲", "乙"):
                sv = (e.get("sendability") or {}).get(a)
                if sv and sv not in SENDABILITY:
                    why.append("sendability[%s] 非法值 %r" % (a, sv))
            if e.get("compare") and e["compare"] not in COMPARE:
                why.append("compare 非法值 %r" % e["compare"])
            for dft in e.get("defects") or []:
                if not dft.get("field"):
                    why.append("缺陷缺 field（必须指向原文字段）")
                if not dft.get("evidence") and dft.get("kind") != "style_preference":
                    why.append("缺陷缺 evidence 且未标 style_preference")
            filled = any(v for v in (e.get("semantic") or {}).values()) or \
                e.get("compare")
            if not filled:
                why.append("空表（未评审）")
            # R3#4：去重与显式替代（同 material_id + 同 reviewer）
            if not why:
                key = (mid, e.get("reviewer"))
                rev = e.get("revision", 1)
                if not isinstance(rev, int):
                    why.append("revision 非整数")
                    rev = None
                if rev is not None:
                    if key in seen:
                        prev_rev, prev_fn = seen[key]
                        if rev > prev_rev:
                            superseded.append({"entry": prev_fn, "material_id": mid,
                                               "reviewer": e.get("reviewer"),
                                               "superseded_by": fn,
                                               "revision": prev_rev})
                            # 之前接受的旧版本移入 superseded
                            accepted = [a for a in accepted
                                        if a["entry"] != prev_fn]
                        else:
                            why.append("同 material_id+同 reviewer 重复表单"
                                       "（revision=%s 不高于已收 revision=%s；"
                                       "重交须显式递增）" % (rev, prev_rev))
                    if rev is not None and not why:
                        seen[key] = (rev, fn)
        (accepted if not why else rejected).append(
            {"entry": fn, "material_id": mid, "why": why})
    json.dump({"accepted": accepted, "rejected": rejected,
               "superseded": superseded},
              open(os.path.join(WORK, "ingest_result.json"), "w",
                   encoding="utf-8"), ensure_ascii=False, indent=1)
    print("ingest: accepted=%d rejected=%d superseded=%d"
          % (len(accepted), len(rejected), len(superseded)))
    for r in rejected:
        print("  REJECT %s (%s): %s" % (r["entry"], r["material_id"],
                                        "; ".join(r["why"])))
    for s in superseded:
        print("  SUPERSEDED %s -> %s (rev %s)" % (s["entry"], s["superseded_by"],
                                                  s["revision"]))


# ---------- aggregate ----------
def cmd_aggregate():
    mp = json.load(open(os.path.join(WORK, "mapping_secret.json"), encoding="utf-8"))
    ing = json.load(open(os.path.join(WORK, "ingest_result.json"), encoding="utf-8"))
    entries = []
    binding_bad = []
    for a in ing["accepted"]:
        p = os.path.join(WORK, "entries", a["entry"])
        if not os.path.isfile(p):
            continue
        e = json.load(open(p, encoding="utf-8"))
        # R3#4：汇总前再验一次材料内容绑定（防 ingest 后材料被改）
        rec = mp["mapping"].get(e.get("material_id"))
        why = []
        if rec:
            verify_material_content(
                os.path.join(WORK, "materials", e["material_id"] + ".json"),
                rec, why, a["entry"])
        if why:
            binding_bad.append({"entry": a["entry"], "why": why})
            continue
        entries.append(e)
    n_total = len(mp["index"])
    idx = mp["index"] if isinstance(mp["index"], dict) else {
        m["material_id"]: m for m in mp["index"]}
    states = [m["state"] for m in idx.values()]
    st_cnt = Counter(states)
    proc = {"全部尝试": n_total, "完成": st_cnt.get("正常", 0),
            "解析失败": st_cnt.get("解析失败", 0), "格式失败": st_cnt.get("格式失败", 0)}
    # R3#4：分母分离——票数按投票事件计；已审材料数按去重后 distinct material 计
    reviewed_mids = sorted({e.get("material_id") for e in entries
                            if e.get("material_id")})
    n_reviewed_materials = len(reviewed_mids)
    votes = Counter(e.get("compare") for e in entries if e.get("compare"))
    manual = {
        "总材料数": n_total,
        "票数_compare": sum(votes.values()),
        "已审材料数": n_reviewed_materials,
        "未审材料数": n_total - n_reviewed_materials,
        "比较_甲": votes.get("甲", 0),
        "比较_乙": votes.get("乙", 0),
        "比较_相当": votes.get("相当", 0),
        "两者都不合格": votes.get("两者都不合格", 0),
        "不回更合适": votes.get("不回更合适", 0),
        "信息不足": votes.get("信息不足", 0),
        "重复表单_被拒": len(ing.get("rejected", [])),
        "重复表单_被替代": len(ing.get("superseded", [])),
        "汇总期材料绑定违例": len(binding_bad),
    }
    if manual["已审材料数"] > manual["总材料数"]:
        # 分母不变式：去重后已审材料数不得超过总材料数（否则统计口径有 bug）
        manual["_INVARIANT_VIOLATION"] = "已审材料数 > 总材料数"
    defect_fields = Counter(d["field"] for e in entries for d in e.get("defects") or [])
    style_tags = sum(1 for e in entries for d in e.get("defects") or []
                     if d.get("kind") == "style_preference")
    out = {"date": "2026-10-21", "release": "arena_v1", "gate": "G07",
           "rule": "无样本写 N/A；失败不从主分母消失；人工判定与流程成功分列；"
                   "票数/已审材料数/未审材料数分列（R3#4）；"
                   "演练标签 fixture/reviewer，不伪造用户投票",
           "process_layers": proc,
           "manual_layers": manual,
           "defect_fields": dict(defect_fields),
           "style_preference_tags": style_tags,
           "reviewer_identities": sorted(set(e.get("reviewer", "?") for e in entries)),
           "drill_status": "fixture_drill" if any(
               e.get("reviewer") == "fixture" for e in entries) else "real"}
    json.dump(out, open(os.path.join(WORK, "review_summary.json"), "w",
                        encoding="utf-8"), ensure_ascii=False, indent=1)
    print("aggregate: 总材料数=%d 票数=%d 已审材料=%d 未审材料=%d "
          "重复拒=%d 被替代=%d style_tags=%d"
          % (n_total, manual["票数_compare"], manual["已审材料数"],
             manual["未审材料数"], manual["重复表单_被拒"],
             manual["重复表单_被替代"], style_tags))


# ---------- lookup ----------
def cmd_lookup(anon_material_id, arm=None):
    mp = json.load(open(os.path.join(WORK, "mapping_secret.json"), encoding="utf-8"))
    rec = mp["mapping"].get(anon_material_id)
    if not rec:
        print("未找到", anon_material_id)
        return 1
    print(json.dumps({k: v for k, v in rec.items() if k != "arms"},
                     ensure_ascii=False, indent=1))
    if arm:
        mat = json.load(open(os.path.join(WORK, "materials",
                                          anon_material_id + ".json"), encoding="utf-8"))
        real_arm = rec["anon_to_arm"].get(arm, arm)
        print("反查原文（%s = %s）:" % (arm, real_arm))
        print(json.dumps(mat["原样输出"].get(arm), ensure_ascii=False, indent=1))
    return 0


def verify_workdir(work_dir=None):
    """G07 接线（V14 执行书 C2）：对评审工作目录做全量独立复核。
    ①材料内容 vs 映射绑定（篡改展示原文必拒）；②ingest 计数独立重算；
    ③entry 绑定散列/reviewer/空表/重复规则复核。返回 {ok, issues, ...}，
    verify_arena_v1 直接消费，不再采信自报计数。"""
    W = work_dir or WORK
    issues = []
    try:
        mp = json.load(open(os.path.join(W, "mapping_secret.json"),
                            encoding="utf-8"))
    except Exception as e:
        return {"ok": False, "issues": ["mapping 不可读: %s" % e]}
    mapping = mp.get("mapping", {})
    n_materials = 0
    for mid, rec in mapping.items():
        n_materials += 1
        matp = os.path.join(W, "materials", mid + ".json")
        if not os.path.isfile(matp):
            issues.append("%s: 材料缺失" % mid)
            continue
        why = []
        verify_material_content(matp, rec, why, mid)
        issues.extend(why)
    # entries 独立复核
    entries_dir = os.path.join(W, "entries")
    acc, rej = 0, 0
    seen = {}
    if os.path.isdir(entries_dir):
        for fn in sorted(os.listdir(entries_dir)):
            if not fn.endswith(".json"):
                continue
            e = json.load(open(os.path.join(entries_dir, fn),
                               encoding="utf-8"))
            mid = e.get("material_id")
            rec = mapping.get(mid)
            why = []
            if rec is None:
                why.append("material_id 不在映射中")
            else:
                if e.get("binding_sha256_16") != rec["binding_sha256_16"]:
                    why.append("entry 绑定散列不一致")
                verify_material_content(
                    os.path.join(W, "materials", mid + ".json"),
                    rec, why, fn)
            if not e.get("reviewer"):
                why.append("缺 reviewer")
            filled = any(v for v in (e.get("semantic") or {}).values()) or \
                e.get("compare")
            if not filled:
                why.append("空表")
            if why:
                rej += 1
            else:
                key = (mid, e.get("reviewer"))
                rev = e.get("revision", 1)
                if key in seen and rev <= seen[key]:
                    issues.append("%s: 重复提交未递增 revision" % fn)
                    rej += 1
                else:
                    seen[key] = rev
                    acc += 1
    else:
        issues.append("entries 目录缺失")
    # ingest_result 计数一致性
    ingp = os.path.join(W, "ingest_result.json")
    if os.path.isfile(ingp):
        ing = json.load(open(ingp, encoding="utf-8"))
        if len(ing.get("accepted", [])) != acc or \
                len(ing.get("rejected", [])) != rej:
            issues.append("ingest_result 计数与独立重算不符: "
                          "claimed=%d/%d recomputed=%d/%d"
                          % (len(ing.get("accepted", [])),
                             len(ing.get("rejected", [])), acc, rej))
    else:
        issues.append("ingest_result.json 缺失")
    return {"ok": not issues, "issues": issues[:10],
            "n_materials": n_materials, "accepted": acc, "rejected": rej}


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "gen":
        cmd_gen(sys.argv[2])
    elif cmd == "ingest":
        cmd_ingest(sys.argv[2])
    elif cmd == "aggregate":
        cmd_aggregate()
    elif cmd == "lookup":
        sys.exit(cmd_lookup(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None))
    elif cmd == "verify":
        wd = sys.argv[2] if len(sys.argv) > 2 else None
        r = verify_workdir(wd)
        print(json.dumps(r, ensure_ascii=False, indent=1))
        sys.exit(0 if r["ok"] else 1)
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
