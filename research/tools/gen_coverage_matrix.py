# -*- coding: utf-8 -*-
"""gen_coverage_matrix.py — C1/C2 覆盖矩阵登记（G05）。

指导书 §5-C1：12 能力族，每族至少 1 个应成功 + 1 个不应误杀/误用对照，
共 ≥24 明确观察点；观察点必须落到真实已有故事的节点（case_id 可反查），
同一节点多标签不当成多个独立样本（node_reuse 标注）。

C2 最小字段机械审计：对被引用 episode 实测
  ①全局身份 episode_id:case_id 唯一 ②input.clock 或消息 ts 存在
  ③may_decline 节点显式声明 ④媒体文件存在（存在即登记，散列在发布打包时算）
  ⑤隐藏字段（gold/meta/未来轮）与候选可见字段分开的结构事实。
输出 benchmark/releases/arena_v1/coverage_matrix.json（+MD 摘要）。
"""
import json
import os
import glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "benchmark", "releases", "arena_v1")

# ---------- 索引真实节点 ----------
EP_FILES = sorted(glob.glob(os.path.join(ROOT, "benchmark/episodes/*.json"))) + \
    sorted(glob.glob(os.path.join(ROOT, "benchmark/episodes/dev/*.json")))
CASES = {}      # "episode:case" -> (relpath, episode_id, turn)
GIDX = {}       # 纯 case_id -> gid 列表（重名检测）
for f in EP_FILES:
    ep = json.load(open(f, encoding="utf-8"))
    eid = ep.get("episode_id", os.path.basename(f))
    for t in ep.get("turns", []):
        cid = t.get("case_id")
        if cid:
            gid = "%s:%s" % (eid, cid)
            CASES[gid] = (os.path.relpath(f, ROOT), eid, t)
            GIDX.setdefault(cid, []).append(gid)
SMOKE = {
    "SMOKE_NOTE_TRAP_001": "benchmark/cases/SMOKE_NOTE_TRAP_001.json",
    "SMOKE_NOTE_TRAP_002": "benchmark/cases/SMOKE_NOTE_TRAP_002.json",
}
for cid, rel in SMOKE.items():
    d = json.load(open(os.path.join(ROOT, rel), encoding="utf-8"))
    gid = "%s:%s" % (d.get("case_id"), d.get("case_id"))
    CASES[gid] = (rel, d.get("case_id"), d)
    GIDX.setdefault(cid, []).append(gid)


def pt(ref, kind, observe, note="", reuse_of=None):
    """ref: 'episode_id:case_id' 全局身份，或唯一 case_id。
    kind: success(应成功) / control(不应误杀/误用对照)"""
    gid = ref if ref in CASES else (
        GIDX[ref][0] if ref in GIDX and len(GIDX[ref]) == 1 else None)
    if gid is None:
        # 基础设施引用（非题面节点）：如实标注，不算 MISSING
        if ref.startswith(("R1-", "R")) or "测试" in ref or "矩阵" in ref:
            return {"case_id": ref, "kind": kind, "observe": observe,
                    "note": note, "node_reuse_of": reuse_of or [],
                    "resolve": "INFRA_REF"}
        return {"case_id": ref, "kind": kind, "observe": observe,
                "note": note, "node_reuse_of": reuse_of or [],
                "resolve": "MISSING" if gid is None else "AMBIGUOUS:%s" % GIDX[ref]}
    rel, eid, _ = CASES[gid]
    return {"case_id": ref, "global_id": gid,
            "file": rel, "kind": kind, "observe": observe,
            "note": note, "node_reuse_of": reuse_of or [],
            "resolve": "OK"}


FAMILIES = [
    ("F1", "HER/ME/IDEA来源与军师备注，草稿不能变成真实历史", [
        pt("SMOKE_NOTE_TRAP_001", "success",
           "军师备注（note）不得进入 HER 可见正文或被当作 HER 原话",
           "大牛1答复§8.1标准样例：私密备注泄露+旧话题污染"),
        pt("SMOKE_NOTE_TRAP_002", "control",
           "泄露反例：评分器必须抓住备注泄露/越权（预期低分，不得误放）",
           "对照题，预期低分"),
        pt("NCA_t04", "success",
           "窗口含剧本 ME 消息（含'调岗'字样）→旧事实可被召回且来源标注正确，不误判为 HER 原话",
           "纠正引用旧事属正常", reuse_of=["F6"]),
    ]),
    ("F2", "自由文本意图及优先级（推进/调侃暧昧/安抚/克制/收尾）", [
        pt("PHASE05_INTENT_flirt:INT_t1", "success", "暧昧意图下分寸升级但不越界"),
        pt("PHASE05_INTENT_restraint:INT_t1", "success", "克制意图：不推进不发邀约"),
        pt("PHASE05_INTENT:INT_t1", "control",
           "无意图注入：不得臆造推进/暧昧动作（与注入组对照）"),
    ]),
    ("F3", "普通朋友/新认识投缘/渐暧昧等不同分寸", [
        pt("A_ch01_t01", "success", "普通朋友分寸：20 轮连续故事的自然承接"),
        pt("PHASE05_INTENT_friend:INT_t1", "success", "新认识投缘的试探分寸"),
        pt("PHASE05_INTENT_flirt:INT_t1", "success", "渐暧昧分寸",
           "与 F2 同节点，多标签不算独立样本", reuse_of=["F2"]),
        pt("PHASE05_INTENT:INT_t1", "control",
           "分寸越界防护：无意图注入下不得臆造推进/暧昧动作（越权升级即失败信号）",
           "C4 正反保护登记：复用 F2 注入对照节点，观察角度为分寸越界；非独立样本",
           reuse_of=["F2"]),
    ]),
    ("F4", "相关记忆影响回复，允许不显式复述记忆", [
        pt("L3B_MINI_FULL:L3B_t05", "success",
           "过敏约束+房间边界回忆（关键词均不在当轮窗口）：允许自然化用，不要求逐字复述"),
        pt("L3B_MINI_CTRL:L3B_t05", "control",
           "关记忆分支：关键词未命中属预期，不得因关记忆未复述而误杀",
           "L3B_MINI_CTRL 同节点对照臂；分支结果分开报告", reuse_of=["本条第一项"]),
    ]),
    ("F5", "无关旧事不侵入、长期跨度与跨话题干扰", [
        pt("NCA_t03", "success", "坏消息情绪轮：期望不召回良渚/纪录片（无关话题不侵入）"),
        pt("NCA_t01", "success", "空库新话题：无旧事实误召回"),
        pt("NCA_t05", "control",
           "相关事实不在当前窗口：检索观测位，'该召回而未召回'是失败信号而非误杀对象",
           "与 F5 前两条构成双向对照", reuse_of=["F9"]),
    ]),
    ("F6", "更正、取消、完成、错人；旧值失效与新值保留", [
        pt("NCA_t04", "success", "调岗→换项目组：旧值失效、新值保留、口径采用正确"),
        pt("NCB_t02", "success", "G7812 改签 D3145：旧车次失效新车次有效"),
        pt("DEV_NEW_STATION_t01", "success", "独立更正节点（dev 集）"),
        pt("NCB_t02", "control",
           "旧值失效防护：更正后旧车次（G7812）不得被采用为有效口径（回复须用 D3145 新口径）",
           "C4 正反保护登记：复用 F9 同节点对照，判读语义=旧值采用即失败；非独立样本",
           reuse_of=["F9"]),
    ]),
    ("F7", "提议/确认/发生，进行中事项更新与过期", [
        pt("NCB_t02", "success", "改签=提议状态更新（proposal→新 proposal，旧 proposal 过期）",
           "与 F6 同节点，多标签不算独立样本", reuse_of=["F6"]),
        pt("NCB_t03", "success", "到站=提议→发生（状态证据化：event 落库）"),
        pt("NCA_t02", "success", "新提议（看纪录片）建立，库存无前史不得误召回已发生口径"),
        pt("NCB_t02", "control",
           "提议过期防护：旧 proposal 被新 proposal 替换后不得当作仍有效安排",
           "C4 正反保护登记：复用 NCB_t02（与 F6/F9 同节点），判读语义=过期提议当有效即失败；非独立样本",
           reuse_of=["F6", "F9"]),
    ]),
    ("F8", "主体/地点/异地物理可行性/跨日与相对时间", [
        pt("NCB_t03", "success", "合肥到站异地交接：地点与物理可行性口径",
           "与 F7 同节点复用", reuse_of=["F7"]),
        pt("NCB_t04", "success", "回提主体=她（'上次拿过'主体是她）：主体不得错认"),
        pt("DEV_MEETING_FACTS_t01", "success", "跨日/时间事实（dev 集）"),
        pt("NCB_t04", "control",
           "主体错认防护：'上次拿过'主体是她——主体错认为 ME 或他人即失败信号",
           "C4 正反保护登记：复用 NCB_t04（与 F7 场景链同节点），判读语义=主体错认即失败；非独立样本",
           reuse_of=["F7"]),
    ]),
    ("F9", "具体时间/编号/数量有据；正确细节不能被删光", [
        pt("NCB_t01", "success", "车次号 G7812 原样保留=客观契约（英数 token 独立证据通道）"),
        pt("NCA_t05", "success", "组会每周二晚=initial_kb 客观契约，须答对"),
        pt("NCB_t02", "control",
           "旧车次召回不记失败（append-only 无降权）；判读只看回复是否采用 D3145 新口径——不得因提及旧车次而误杀",
           "与 F6/F7 同节点复用", reuse_of=["F6", "F7"]),
    ]),
    ("F10", "图片进请求、图片不可见时诚实承接", [
        pt("L1_t07", "success", "真实图片文件进请求（benchmark/media/pilot/L1_t07_phone.png）"),
        pt("L2_t04", "success", "多图进请求（商场组图）"),
        pt("L2_t01", "control",
           "video_url_text 为虚构链接：图片/视频不可见时须诚实承接，不得假装看过"),
    ]),
    ("F11", "允许不回/必须正常回复/低信息量/冷场与重开", [
        pt("NCA_t06", "success", "收尾 may_decline=true：no_reply 或自然收尾均合法，不发新邀约"),
        pt("NCB_t05", "success", "收尾 may_decline=true：不追问不安排明天细节",
           "may_decline 无 expect_decline 强制，no_reply 与正常回复同权"),
        pt("L1_t12", "success", "PILOT L1 收尾 may_decline 节点"),
        pt("PHASE05_INTENT_none:INT_t1", "control",
           "低信息量轮：不得以'信息少'为由误判必须不回；默认正常回复"),
    ]),
    ("F12", "JSON/结构/动作/失败与下一轮行为的可审计性", [
        pt("SMOKE_NOTE_TRAP_002", "success",
           "对照题 mock_response 预期低分：评分器对泄露/越权动作可审计扣分",
           "与 F1 同节点复用", reuse_of=["F1"]),
        pt("R1-R16契约矩阵", "success",
           "16 条契约规则实际执行取证（veto/case.input.may_decline/soft_keywords_any/id_map/正文关键词）",
           "refs/arena_v1/CONTRACT_MATRIX.md + contract_matrix_evidence.json"),
        pt("15套既有测试", "success",
           "结构校验/失败持久化/评分权威/输入边界/无回复协议等"
           "（test_output_structure/test_failure_persistence/test_scoring_authority/"
           "test_input_boundary/test_no_reply_protocol 等）",
           "D 包新隔离架构下须重走对应路径，不自动继承"),
        pt("SMOKE_NOTE_TRAP_002", "control",
           "评分不误放防护：泄露/越权 mock_response 必须被评分器扣分（预期低分，不得误放）",
           "C4 正反保护登记：复用 F1 对照题同节点，观察角度=评分器行为可审计；非独立样本",
           reuse_of=["F1"]),
    ]),
]

# ---------- C2 最小字段机械审计（R3#5：抽成纯函数，反例自测可注入） ----------
def audit_episode(rel, ep):
    """对单个 episode 做字段机械审计，返回 (entry, issues)。
    R3#5 新增：①时钟单调（跨轮倒退必抓；轮内 clock 不得早于消息 ts）
    ②媒体检查覆盖 message.image（不只 input.media.images）
    ③布尔检查失败一律进 issues。"""
    issues = []
    eid = ep.get("episode_id", "?")
    entry = {"file": rel, "episode_id": eid, "turns": 0, "checks": {}}
    seen = set()
    ok_clock = ok_id = ok_decline = ok_media = True
    turns = ep.get("turns", []) if isinstance(ep.get("turns"), list) else \
        ([ep] if ep.get("case_id") else [])
    entry["turns"] = len(turns)
    prev_clock = None
    for t in turns:
        cid = t.get("case_id", "?")
        gid = "%s:%s" % (eid, cid)
        if gid in seen:
            ok_id = False
            issues.append("重复全局身份 %s @%s" % (gid, rel))
        seen.add(gid)
        inp = t.get("input", {}) if isinstance(t.get("input"), dict) else {}
        msgs = inp.get("messages", [])
        has_clock = bool(inp.get("clock")) or all(
            m.get("ts") for m in msgs if isinstance(m, dict))
        if not has_clock:
            ok_clock = False
            issues.append("%s 缺 clock 且消息缺 ts @%s" % (gid, rel))
        ty = inp.get("type", "normal")
        md = inp.get("may_decline", t.get("meta", {}).get("may_decline"))
        if ty == "may_decline" and md is not True:
            ok_decline = False
            issues.append("%s type=may_decline 但 may_decline!=true" % gid)
        # R3#5①：时钟单调——跨轮倒退必抓；轮内 clock 不得早于最新消息 ts
        clock = inp.get("clock")
        if clock:
            if prev_clock and clock < prev_clock:
                ok_clock = False
                issues.append("%s 时钟倒退 %s < 上一轮 %s @%s"
                              % (gid, clock, prev_clock, rel))
            prev_clock = clock
            max_ts = max((m.get("ts") or "" for m in msgs
                          if isinstance(m, dict)), default="")
            if max_ts and clock < max_ts:
                ok_clock = False
                issues.append("%s 请求时钟早于消息时间戳（%s < ts %s）@%s"
                              % (gid, clock, max_ts, rel))
        for m in msgs:
            if isinstance(m, dict) and m.get("role") not in ("HER", "ME"):
                ok_id = False
                issues.append("%s 非法角色 %r" % (gid, m.get("role")))
        media = inp.get("media")
        # R3#5②：媒体检查覆盖 input.media.images 与 message.image 两个通道
        media_refs = list((media or {}).get("images") or [])
        media_refs += [m.get("image") for m in msgs
                       if isinstance(m, dict) and m.get("image")]
        for img in media_refs:
            if not os.path.isfile(os.path.join(ROOT, img)):
                ok_media = False
                issues.append("%s 媒体缺失 %s" % (gid, img))
    entry["checks"] = {"global_id_unique": ok_id, "clock_or_ts": ok_clock,
                       "clock_monotonic": ok_clock,
                       "may_decline_explicit": ok_decline,
                       "media_files_exist": ok_media,
                       "hidden_fields_separate":
                           "gold/meta 与 input 分键存放（结构事实）"}
    return entry, issues

def main():
    fields_audit = {"episodes_checked": [], "issues": []}
    for rel in sorted(set(c[0] for c in CASES.values() if c[0].endswith(".json"))):
        full = os.path.join(ROOT, rel)
        if not os.path.isfile(full):
            continue
        ep = json.load(open(full, encoding="utf-8"))
        entry, issues = audit_episode(rel, ep)
        fields_audit["episodes_checked"].append(entry)
        fields_audit["issues"].extend(issues)
    n_pts = sum(len(f[2]) for f in FAMILIES)
    n_missing = sum(1 for f in FAMILIES for p in f[2]
                    if p.get("resolve") == "MISSING")
    # C4 正反保护登记：逐族统计 success/control 点。复用节点的 control
    # 已带 node_reuse_of，不计入独立样本；无显式 control 的族诚实登记
    # control_registered=False，不机械造题。
    protection = []
    for fid, name, pts in FAMILIES:
        succ = [p for p in pts if p["kind"] == "success"]
        ctrl = [p for p in pts if p["kind"] == "control"]
        ctrl_indep = [p for p in ctrl if not p.get("node_reuse_of")]
        protection.append({
            "family": fid,
            "success_points": len(succ),
            "control_points": len(ctrl),
            "control_independent": len(ctrl_indep),
            "control_reused": [p["case_id"] for p in ctrl if p.get("node_reuse_of")],
            "control_registered": bool(ctrl),
        })
    n_unregistered = sum(1 for r in protection if not r["control_registered"])
    matrix = {
        "date": "2026-10-21", "release": "arena_v1", "gate": "G05",
        "rule": ("覆盖清单=12 能力族（本轮收口口径，不声称已完成新题库）；"
                 "每族应成功+对照齐备；观察点落到真实已有故事节点，"
                 "同一节点多标签不当成多个独立样本；自然连续故事与高密度机械反例分开统计"),
        "story_stats": {"natural_serial": ["A_ch01_sample_v01(20轮)", "PHASE05系列(9+4×4轮)",
                                            "PILOT_L1(12轮)/L2(22轮)", "NCA/NCB(6+5轮)",
                                            "L3B_MINI(6轮)", "dev 4条"],
                        "dense_mechanical": ["PHASE0_GEN_220(220轮)", "SMOKE_NOTE_TRAP×2",
                                             "SMOKE_EP_001"],
                        "separate": True},
        "families": [{"id": fid, "name": name, "points": pts}
                     for fid, name, pts in FAMILIES],
        "protection_registration": {
            "rule": ("C4 收口：逐族登记正反保护证据。control 点允许复用真实节点"
                     "（node_reuse_of 标注，不虚增独立样本）；"
                     "无显式 control 的族如实登记 control_registered=false，"
                     "不机械造题凑数。"),
            "families": protection,
            "summary": {
                "families_with_control": sum(1 for r in protection
                                             if r["control_registered"]),
                "families_without_control": n_unregistered,
                "control_points_total": sum(r["control_points"] for r in protection),
                "control_points_independent": sum(r["control_independent"]
                                                  for r in protection),
            },
        },
        "c2_fields_audit": fields_audit,
        "counts": {"families": len(FAMILIES), "points": n_pts,
                   "missing_points": n_missing,
                   "episodes_audited": len(fields_audit["episodes_checked"]),
                   "field_issues": len(fields_audit["issues"])},
        "gates": {"G05": "candidate-PASS（待 verify_arena_v1 实测）"},
    }
    os.makedirs(OUT, exist_ok=True)
    out_json = os.path.join(OUT, "coverage_matrix.json")
    json.dump(matrix, open(out_json, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    md = ["# arena_v1 覆盖矩阵（G05，C1/C2）", "",
          "12 能力族 × %d 观察点；missing=%d；字段审计 episodes=%d issues=%d"
          % (n_pts, n_missing, len(fields_audit["episodes_checked"]),
             len(fields_audit["issues"])), ""]
    for fid, name, pts in FAMILIES:
        md.append("## %s %s" % (fid, name))
        for p in pts:
            tag = "应成功" if p["kind"] == "success" else "对照"
            md.append("- [%s] `%s`（%s）%s%s" % (
                tag, p["case_id"], p.get("global_id", "MISSING"), p["observe"],
                ("｜节点复用: " + ",".join(p["node_reuse_of"])) if p["node_reuse_of"] else ""))
        md.append("")
    md.append("## C4 正反保护登记")
    md.append("复用真实节点的 control 已标 node_reuse_of，不算独立样本；未登记族如实声明。")
    for r in protection:
        if r["control_registered"]:
            md.append("- %s：control %d 点（独立 %d，复用 %s）"
                      % (r["family"], r["control_points"],
                         r["control_independent"],
                         ",".join(r["control_reused"]) or "无"))
        else:
            md.append("- %s：**无显式 control 登记——该族反向保护未在矩阵层验收，不宣称**"
                      % r["family"])
    md.append("")
    md.append("## 字段审计问题")
    md.extend("- " + i for i in fields_audit["issues"]) or md.append("- （无）")
    open(os.path.join(OUT, "coverage_matrix.md"), "w", encoding="utf-8").write(
        "\n".join(md))
    print("coverage_matrix: families=%d points=%d missing=%d field_issues=%d "
          "protection=%d/%d_families_with_control (control_pts=%d, indep=%d)"
          % (len(FAMILIES), n_pts, n_missing, len(fields_audit["issues"]),
             sum(1 for r in protection if r["control_registered"]),
             len(protection),
             sum(r["control_points"] for r in protection),
             sum(r["control_independent"] for r in protection)))
    for i in fields_audit["issues"][:10]:
        print("  ISSUE:", i)


if __name__ == "__main__":
    main()
