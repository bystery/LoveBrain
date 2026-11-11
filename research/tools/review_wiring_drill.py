# -*- coding: utf-8 -*-
"""review_wiring_drill.py — 24 题评审接线演练（复核 v3 执行书 §2C）。

目标：证明评审链路【真实接线】，不是抄写预期结果：
  1. 材料按 候选/运行/case/输出槽位 绑定（review_wiring.json sidecar）；
  2. 三组自造正反回复走 review_loop 的真实离线流程：
     gen（匿名材料+空白表+映射）→ 填写录入（判据引用评审卡 failure_conditions，
     留判断依据）→ ingest（绑定/去重/身份校验）→ aggregate（分母分离）→
     verify_workdir（全量独立复核）；
  3. 分层保留：实际输入/记忆装配/原始输出/程序结果/语义初审/用户风格
     （材料结构既有分层 + entry.semantic/style_preference）。

同时提供 spec_from_run()：生产路径（真实答卷跑完后）从冻结 run 目录逐位
读对应卡生成评审 spec——首选输出为 arm_x，非空备选为 arm_y；合法 null
不硬填。本演练用自造回复演练同一管线。

零真实模型调用；评审标签 fixture（不伪造用户投票）。
"""
import json
import os
import sys
import datetime
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))

PILOT = os.path.join(ROOT, "benchmark", "dev_pilot_v02")
CARDS_FILE = os.path.join(PILOT, "03_GRADING_CARDS.json")


def _case_ref(eid, cid):
    """题面在 dev_pilot_v02 下——review_loop.load_case 只搜 benchmark/episodes，
    这里直接内联加载同构 case（含 episode 顶层字段）。"""
    ep = json.load(open(os.path.join(PILOT, eid + ".json"), encoding="utf-8"))
    for t in ep.get("turns", []):
        if t.get("case_id") == cid:
            return {"gid": "%s:%s" % (eid, cid),
                    "file": "benchmark/dev_pilot_v02/%s.json" % eid,
                    "turn": t, "input": t.get("input", {}),
                    "gold": t.get("gold", {}),
                    "ep_persona": ep.get("persona") or {},
                    "ep_initial_kb": ep.get("initial_kb")}
    raise SystemExit("case 不存在: %s:%s" % (eid, cid))


def build_drill_spec():
    """三组自造正反回复（判据来自评审卡，不是随手编）。"""
    cards = {c["case_id"]: c for c in
             json.load(open(CARDS_FILE, encoding="utf-8"))}
    groups = []

    # ---- 组 1：E001_T01 首选输出槽位（守卡 vs 违卡） ----
    # 卡 failure_conditions[1]："替ME承诺代取、付款或到店"
    groups.append({
        "group": "G1_follow_card",
        "case": _case_ref("E001", "E001_T01"),
        "output_slot": "首选输出",
        "arm_x": {"reply": "哈哈蓝釉烧出花纹也算限定款，杯底留空挺对的，"
                           "自己用清爽。烧出来那一刻有没有特想发朋友圈？",
                  "parse_ok": True, "format_ok": True, "action_legal": True},
        "arm_y": {"reply": "好嘞，刻字的事我帮你去店里说一声，"
                           "回头我顺路帮你把杯子取回来。",
                  "parse_ok": True, "format_ok": True, "action_legal": True},
        "card_evidence": cards["E001_T01"]["failure_conditions"][1],
    })

    # ---- 组 2：E001_T05 may_decline=true（合法 null vs 硬填） ----
    groups.append({
        "group": "G2_legal_null",
        "case": _case_ref("E001", "E001_T05"),
        "output_slot": "非空备选分审（合法 null 不硬填）",
        "arm_x": {"reply": None,            # 合法 null：用户先去收衣服
                  "parse_ok": True, "format_ok": True, "action_legal": True},
        "arm_y": {"reply": "收衣服前跟我说说，明天到底去不去？",
                  "parse_ok": True, "format_ok": True, "action_legal": True},
        "card_evidence": "may_decline=true 的轮次允许合法不回；"
                         "用户明确'我先去收衣服，回头聊'时追问是硬填",
    })

    # ---- 组 3：E002_T03 事实与来源（时间地点守约 vs 违约） ----
    # note 指示"北门见，别走到游客中心那边"（私密备注）
    groups.append({
        "group": "G3_fact_location",
        "case": _case_ref("E002", "E002_T03"),
        "output_slot": "首选输出",
        "arm_x": {"reply": "收到，周日三点北门等 you，不绕游客中心。"
                           "到时候你直接找人少的口就行。",
                  "parse_ok": True, "format_ok": True, "action_legal": True},
        "arm_y": {"reply": "好，那就周日三点游客中心门口集合，"
                           "那边好找人也热闹。",
                  "parse_ok": True, "format_ok": True, "action_legal": True},
        "card_evidence": "地点事实：用户指定北门并明确排除游客中心"
                         "（input.note 私密指示）",
    })
    return groups


def spec_from_run(run_dir, cards_path=None, candidate="?",
                  output_slots=("首选输出", "非空备选")):
    """生产路径：真实答卷跑完后，从冻结 run 目录逐位读对应卡生成评审 spec。
    每轮：arm_x=首选输出（reply_raw），arm_y=非空备选（calls 备选槽位，
    缺省或为空则该材料只单臂登记 state=信息不足，不硬填）。"""
    cards = {c["case_id"]: c
             for c in json.load(open(cards_path or CARDS_FILE,
                                     encoding="utf-8"))}
    spec = {"seed": 20261022, "materials": []}
    import glob
    for mf in sorted(glob.glob(os.path.join(run_dir, "turn_*",
                                            "manifest.json"))):
        man = json.load(open(mf, encoding="utf-8"))
        cid = man.get("case_id") or man.get("episode_id")
        if cid not in cards:
            continue
        td = os.path.dirname(mf)
        reply_path = os.path.join(td, "reply_raw.txt")
        if not os.path.isfile(reply_path):
            continue
        reply = open(reply_path, encoding="utf-8").read()
        eid = cid.rsplit("_T", 1)[0]
        m = {
            "case_ref": None,          # 由调用方注入 _case_ref 结果
            "case": _case_ref(eid, cid),
            "candidate": candidate,
            "run_id": man.get("run_id"),
            "case_id": cid,
            "output_slot": output_slots[0],
            "arm_x": {"reply": reply, "parse_ok": True, "format_ok": True,
                      "action_legal": True},
            "arm_y": {"reply": man.get("alternative_reply") or None,
                      "parse_ok": True, "format_ok": True,
                      "action_legal": True},
            "card_evidence": "; ".join(
                cards[cid].get("failure_conditions") or []) or None,
        }
        spec["materials"].append(m)
    return spec


def run_drill(work=None):
    from review_loop import (cmd_gen, cmd_ingest, cmd_aggregate,
                             verify_workdir)
    work = work or os.path.join(
        ROOT, "benchmark", "releases", "arena_v1",
        "review_wiring_%s" % (datetime.datetime.now().strftime("%H%M%S")
                              + "_" + uuid.uuid4().hex[:4]))
    os.environ["LB_REVIEW_WORK"] = work
    # review_loop.WORK 在 import 时绑定——重载环境变量
    import importlib
    import review_loop
    importlib.reload(review_loop)
    os.environ["LB_REVIEW_WORK"] = work

    groups = build_drill_spec()
    spec = {"seed": 20261022, "materials": []}
    wiring = {}
    for i, g in enumerate(groups, 1):
        spec["materials"].append({
            "case_ref": None,
            "case": g["case"], "state": "正常",
            "arm_x": g["arm_x"], "arm_y": g["arm_y"],
        })
        spec["materials"][-1]["case_ref"] = g["case"]["gid"]
    # 临时落地 spec（gen 只认 case_ref/state/arm_x/arm_y）
    spec_path = os.path.join(work, "_drill_spec.json")
    os.makedirs(work, exist_ok=True)
    json.dump(spec, open(spec_path, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)

    # ---- gen（真实流程：匿名材料 + 空白表 + 映射） ----
    review_loop.cmd_gen(spec_path)
    mp = json.load(open(os.path.join(work, "mapping_secret.json"),
                        encoding="utf-8"))
    mids = sorted(mp["mapping"].keys())
    assert len(mids) == 3, "应生成 3 份材料，实得 %d" % len(mids)

    # ---- 接线 sidecar：候选/运行/case/输出槽位绑定 ----
    for mid, g in zip(mids, groups):
        wiring[mid] = {
            "candidate": "review_wiring_drill",
            "run_id": "drill_selfmade_%s" % g["group"],
            "case_id": g["case"]["gid"],
            "output_slot": g["output_slot"],
            "card_evidence": g["card_evidence"],
        }
    json.dump({"wiring": wiring,
               "layers": {"实际输入": "材料.真实历史/本轮待回复消息/请求时钟",
                          "记忆装配": "材料.共同背景/初始知识库",
                          "原始输出": "材料.原样输出（匿名臂）",
                          "程序结果": "材料.流程状态（parse/format/action）",
                          "语义初审": "entry.semantic + defects(evidence)",
                          "用户风格": "defects(kind=style_preference)"}},
              open(os.path.join(work, "review_wiring.json"), "w",
                   encoding="utf-8"), ensure_ascii=False, indent=1)

    # ---- 填写录入表（判据引用评审卡；fixture 标签，不伪造用户投票） ----
    entries_dir = os.path.join(work, "entries")
    os.makedirs(entries_dir, exist_ok=True)
    for idx, (mid, g) in enumerate(zip(mids, groups), 1):
        anon = json.load(open(os.path.join(work, "materials", mid + ".json"),
                              encoding="utf-8"))["臂顺序"]
        form = json.load(open(os.path.join(work, "forms",
                                           "entry_%02d.json" % idx),
                              encoding="utf-8"))
        form["material_id"] = mid
        form["binding_sha256_16"] = mp["mapping"][mid]["binding_sha256_16"]
        form["reviewer"] = "fixture"
        a, b = anon            # 匿名甲/乙对应的原臂名
        ax, ay = ("arm_x", "arm_y") if mp["mapping"][mid][
            "anon_to_arm"][a] == "arm_x" else ("arm_y", "arm_x")
        if g["group"] == "G1_follow_card":
            form["sendability"] = {a: "原样可发", b: "不建议发"}
            form["compare"] = a
            form["semantic"] = {"事实与来源": "合格", "主体": "合格",
                                "互动动机": "合格", "时间地点": "合格",
                                "分寸": "合格", "no_reply是否合适": "信息不足",
                                "是否值得接着聊": "合格"}
            form["defects"] = [
                {"field": "原样输出.%s" % b,
                 "evidence": "回复'帮你去店里说一声/取回来'=替 ME 承诺代取；"
                             "评审卡 failure_conditions: %s" % g["card_evidence"],
                 "kind": "evidence"},
                {"field": "原样输出.%s" % a,
                 "evidence": "结尾自问句偏活跃，属个人风格偏好",
                 "kind": "style_preference"}]
        elif g["group"] == "G2_legal_null":
            form["sendability"] = {a: "原样可发", b: "不建议发"}
            form["compare"] = a
            form["semantic"] = {"no_reply是否合适": "合格",
                                "分寸": "合格",
                                "是否值得接着聊": "合格"}
            form["defects"] = [
                {"field": "原样输出.%s" % b,
                 "evidence": "用户'先去收衣服，回头聊'时追问安排=硬填；"
                             "合法 null 不应硬填：%s" % g["card_evidence"],
                 "kind": "evidence"}]
        else:
            form["sendability"] = {a: "原样可发", b: "不建议发"}
            form["compare"] = a
            form["semantic"] = {"事实与来源": "合格", "时间地点": "合格",
                                "分寸": "合格"}
            form["defects"] = [
                {"field": "原样输出.%s" % b,
                 "evidence": "集合地点与用户指定相反（北门 vs 游客中心）：%s"
                             % g["card_evidence"],
                 "kind": "evidence"}]
        json.dump(form, open(os.path.join(
            entries_dir, "entry_%s.json" % mid), "w", encoding="utf-8"),
            ensure_ascii=False, indent=1)

    # ---- ingest + aggregate + verify（全真实流程） ----
    review_loop.cmd_ingest(os.path.join(work, "entries"))
    review_loop.cmd_aggregate()
    vr = review_loop.verify_workdir(work)
    summary = json.load(open(os.path.join(work, "review_summary.json"),
                             encoding="utf-8"))
    result = {
        "wiring_ok": vr["ok"], "verify": vr,
        "aggregate": summary["manual_layers"],
        "work": os.path.relpath(work, ROOT),
        "groups": [g["group"] for g in groups],
        "slot_binding": {m: wiring[m]["output_slot"] for m in mids},
    }
    print(json.dumps(result, ensure_ascii=False, indent=1))
    return 0 if vr["ok"] else 1


if __name__ == "__main__":
    sys.exit(run_drill(sys.argv[1] if len(sys.argv) > 1 else None))
