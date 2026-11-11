# -*- coding: utf-8 -*-
r"""tools/knowledge_probe_precheck.py — 跨窗口知识探针的零调用前置闸门（收口单 B）。

目的：在烧任何真实生成之前，用已付费的真实状态 + 离线夹具消息，证明 6 个探针确实
"只有记忆投影能答、原文窗口里查不到"。派发 24 次真实生成前，四项前置必须全部满足，
并把实际出站请求文本保存下来作为断言对象（不靠轮数猜、不靠事后改判据）。

四项前置（逐字来自 WORKER_CORE_CLOSEOUT_NEXT.md §B）：
  P1 要问的值只来自早期真实 HER/ME 消息；current_input、永久画像/种子、note、问题文字
     不能带答案。
  P2 长度足够的中性真实消息夹具使来源句与同义复述均离开实际 1400CU 原文段；检查实际
     出站段，不靠轮数猜。
  P3 on 请求有支持该值的投影证据；没有投影证据就如实记 Retrieval 失败，不派无效消融。
  P4 off 精确只删除声明投影段，seed/历史/current/目标均同一份。
  P5 更正值的原/新状态、来源与机会在发出答案前登记；不得看答案后修改判据。

零真实调用：网关是脚本夹具（固定返回空 analysis 的合法 JSON），只用于让机制把出站
prompt 拼出来。付费 checkpoint 只读恢复，写盘全部落在 --outdir 下。

用法：
  D:/anaconda/python.exe tools/knowledge_probe_precheck.py \
      --candidate action_policy_r4_guard_e3 --outdir logs/kp_precheck_20261029
"""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

CANDIDATES = {
    "action_policy_r4_guard_e3": os.path.join(
        ROOT, "candidates", "action_policy_r4_guard_e3", "mechanism", "mechanism.py"),
    "action_policy_r4_guard_final": os.path.join(
        ROOT, "candidates", "action_policy_r4_guard_final", "mechanism", "mechanism.py"),
    "action_policy_r5_tier": os.path.join(
        ROOT, "candidates", "action_policy_r5_tier", "mechanism", "mechanism.py"),
}
PAID_STATES = {
    "K01": os.path.join(ROOT, "logs", "f3_newmat_live_restart_20261029",
                        "candidate_K01_resume", "checkpoint.bin"),
    "K02": os.path.join(ROOT, "logs", "f3_newmat_live_restart_20261029",
                        "candidate_K02_resume", "checkpoint.bin"),
    "N01": os.path.join(ROOT, "logs", "f3_newmat_live_restart_20261029",
                        "candidate_N01", "checkpoint.bin"),
    "N02": os.path.join(ROOT, "logs", "f3_newmat_live_restart_20261029",
                        "candidate_N02", "checkpoint.bin"),
}

PROJ_HEADERS = ("【相关已确认事实", "【暂定事项", "【最近状态变更")
HEADER_KINDS = [
    ("seed", "【画像/初始事实"),
    ("goal", "【当前目标"),
    ("plan", "【已确认/进行中事项"),
    ("note", "# 军师备注"),
    ("action", "【本轮可用动作"),
    ("proj_facts", "【相关已确认事实"),
    ("proj_tent", "【暂定事项"),
    ("proj_upd", "【最近状态变更"),
    ("history", "# 【真实历史消息"),
    ("current", "# 本轮输入"),
    ("media", "# 本轮媒体"),
    ("timestamp", "# 当前时间"),
]

# ---------------------------------------------------------------------------
# 中性真实消息夹具：口吻与故事正文一致（短句、空格断句），内容与六个探针的
# 主题/数值/同义复述均无关；每条都会程序化断言不含 leak_forms。
# ---------------------------------------------------------------------------
FILLERS = [
    "今天降温了 风挺大",
    "中午吃了碗面 汤有点咸",
    "地铁晚点 在门口站了半天",
    "新买的鞋有点磨脚",
    "楼下超市鸡蛋在打折",
    "追的那部剧更了一集",
    "周末想睡个懒觉再说",
    "同事换了个新杯子",
    "傍晚沿河走了两圈",
    "手机快没电了 先充会儿",
    "阳台的衣服忘了收",
    "早上闹钟居然没响",
    "食堂今天的菜太淡",
    "快递在驿站放了三天",
    "新换的床单有点皱",
    "办公室空调开得太冷",
    "晚上十点才吃上饭",
    "路上堵了一个多小时",
    "把抽屉翻了个底朝天",
    "旧杂志堆了一摞没扔",
    "楼下装修队在楼道说话",   # 不含工具主题词，只写楼道说话
    "今晚月亮挺亮",
    "洗了两大盆衣服",
    "明天还得早起排队",
    "把这周的家务理顺了一下",
    "钥匙又找不到 翻了一遍包",
    "水壶水垢厚了一层",
    "买了把新雨伞",
    "门口那家店换招牌了",
    "一晚上打了三个喷嚏",
    "把阳台那盆水浇多了",
    "周末打算在家收拾屋子",
]

# ---------------------------------------------------------------------------
# 探针登记表（P5：原/新状态、来源与机会在发出任何答案前登记）。
# value_forms：答案值，只允许出现在投影段，且必须出现在投影段。
# leak_forms：同义复述/主题线索，允许出现在投影段以外，但必须离开出站原文段。
# stale_forms：已被更正/取消的旧值，只用于派发时的污染计分，不参与前置判定。
# ---------------------------------------------------------------------------
PROBES = [
    {
        "probe_id": "KP-A1", "class": "attribution", "story": "K02", "fact_id": "f_0029",
        "domain": "装修/宠物（豆豆）",
        "question": "周末师傅要来开电钻，家里那个小的受不受得了？",
        "value_forms": ["怕电钻"],
        "leak_forms": ["电钻"],
        "stale_forms": [],
        "opportunity": ("ME 若要安排打孔时段/提出替她盯着，必须把“怕电钻”的主体落在豆豆身上，"
                        "不得说成她本人或她妈怕，也不得当成大人无所谓的事。"),
        "answer_must_contain": ["电钻"],
        "attribution_targets": ["豆豆"],
        "wrong_subjects": ["她妈", "家长"],
    },
    {
        "probe_id": "KP-A2", "class": "attribution", "story": "K01", "fact_id": "f_0020",
        "domain": "旅行/朋友家（阿岚）",
        "question": "14号去阿岚那儿吃饭，她家那只最近怎么样，我要不要提前准备点什么？",
        "value_forms": ["掉毛"],
        "leak_forms": ["掉毛", "阿岚家猫", "猫最近"],
        "stale_forms": [],
        "opportunity": ("ME 若回应“要不要准备”，应把“掉毛”归属于阿岚家的猫（不是她的猫、"
                        "不是客栈的猫），并可自然衔接她本人过敏这件旧事。"),
        "answer_must_contain": ["掉毛"],
        "attribution_targets": ["阿岚"],
        "wrong_subjects": ["客栈", "她姐姐"],
    },
    {
        "probe_id": "KP-C1", "class": "correction", "story": "K01", "fact_id": "f_0015",
        "domain": "旅行/客栈退订",
        "question": "客栈退款的截止日期，我到底跟你说过没有？别到时候退不掉。",
        "value_forms": ["11月10号", "10号"],
        "leak_forms": ["退款截止", "可退", "11月10号", "11月8号"],
        "stale_forms": ["11月8号", "8号"],
        "opportunity": ("ME 复述退款时限时必须用新值（11月10号），并把旧值当已作废，"
                        "不得按 11月8号 催她或声称还有 8号 那个期限。"),
        "answer_must_contain": ["10号"],
        "attribution_targets": [],
        "wrong_subjects": [],
    },
    {
        "probe_id": "KP-C2", "class": "correction", "story": "K02", "fact_id": "f_0011",
        "domain": "装修/材料到货",
        "question": "瓷砖到货改到几号了来着？我这边要重新约铺砖的师傅。",
        "value_forms": ["11月8号"],
        "leak_forms": ["瓷砖到货", "到货改到", "11月8号", "11月5号", "来不及"],
        "stale_forms": ["11月5号", "5号"],
        "opportunity": ("ME 报到货日期必须给新值 11月8号；把 5号 当现行日期即为污染。"),
        "answer_must_contain": ["8号"],
        "attribution_targets": [],
        "wrong_subjects": [],
    },
    {
        "probe_id": "KP-T1", "class": "time_state", "story": "K01", "fact_id": "f_0023",
        "domain": "旅行/抢票",
        "question": "我让你盯的那班高铁，票是几号开售来着？",
        "value_forms": ["11月1号", "1号开售"],
        "leak_forms": ["开售", "高铁票", "11月1号"],
        "stale_forms": [],
        "opportunity": ("ME 说开售日必须是 11月1号，并据此判断现在还能不能买；"
                        "不得把“提前几天”当成已知规则蒙。"),
        "answer_must_contain": ["1号"],
        "attribution_targets": [],
        "wrong_subjects": [],
    },
    {
        "probe_id": "KP-T2", "class": "time_state", "story": "K02", "fact_id": "f_0025",
        "domain": "家庭/学校日程",
        "question": "家长会到底具体是几号来着？我拿笔记一下，别再跟你记岔。",
        "value_forms": ["11月7号"],
        "leak_forms": ["家长会", "11月7号", "7号要开"],
        "stale_forms": [],
        "opportunity": ("ME 要把日程值说成 11月7号 的家长会，并由此判定她那天去不了工地；"
                        "不得含糊成“下周某天”或编造别的日期。"),
        "answer_must_contain": ["7号"],
        "attribution_targets": [],
        "wrong_subjects": [],
    },
    # 备用（配额内不入选时不派发，供前置失败替换）
    {
        "probe_id": "KP-B1", "class": "attribution", "story": "N02", "fact_id": "f_0015",
        "domain": "救助站/领养",
        "question": "救助站那只小猫到底叫什么来着，我怕又叫错。",
        "value_forms": ["年糕"],
        "leak_forms": ["年糕", "猫叫"],
        "stale_forms": [],
        "opportunity": "ME 直接叫出猫名“年糕”，不得反问或说成别的名字。",
        "answer_must_contain": ["年糕"],
        "attribution_targets": [],
        "wrong_subjects": [],
        "backup": True,
    },
    {
        "probe_id": "KP-B2", "class": "time_state", "story": "N01", "fact_id": "f_0019",
        "domain": "家庭/带娃",
        "question": "孩子那边要接半天还是全天？下午我得几点之前把人送回去？",
        "value_forms": ["三点前", "三点"],
        "leak_forms": ["下午三点", "接孩子", "带半天"],
        "stale_forms": [],
        "opportunity": "ME 给的具体时限必须是下午三点前，不得含糊或另编时间。",
        "answer_must_contain": ["三点"],
        "attribution_targets": [],
        "wrong_subjects": [],
        "backup": True,
    },
]

FIXED_REPLY = json.dumps({
    "action": "no_reply", "decline_reason": "离线夹具：只出站 prompt，不产出内容",
    "response": {"recommended": None, "bad_boy": None, "playful": None, "warm": None},
    "directions": [None, None, None, None],
    "analysis": {"facts": [], "tentatives": [], "updates": []},
}, ensure_ascii=False)

TS_RE = re.compile(r"^\s*(\d{4}-\d{2}-\d{2})[ T](\d{2}):(\d{2})")


def sha(s):
    return hashlib.sha256((s or "").encode("utf-8")).hexdigest()


def load_mod(name):
    path = CANDIDATES[name]
    spec = importlib.util.spec_from_file_location("kp_%s" % name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def classify(seg):
    for kind, head in HEADER_KINDS:
        if seg.startswith(head):
            return kind
    return "other"


def split_segments(prompt):
    return [(classify(s), s) for s in prompt.split("\n\n")]


def parse_ts(text):
    m = TS_RE.match(text or "")
    if not m:
        return None
    d, hh, mm = m.group(1), int(m.group(2)), int(m.group(3))
    return dt.datetime.strptime(d, "%Y-%m-%d") + dt.timedelta(hours=hh, minutes=mm)


def next_ts(last, step_min):
    if last is None:
        return "2026-11-08 20:00"
    t = last + dt.timedelta(minutes=step_min)
    return t.strftime("%Y-%m-%d %H:%M")


def ctx_for(seq, clock, text, role="HER"):
    return {
        "turn_index": seq,
        "input": {"clock": clock, "may_decline": True,
                  "note": None, "intent": None,
                  "messages": [{"role": role, "text": text, "ts": clock}]},
    }


class FixtureGW(object):
    """零网络：只记录出站 user prompt，返回固定空 analysis 的合法 JSON。"""

    def __init__(self):
        self.inputs = []
        self.system = None

    def generate(self, system, user, images=None, tag=None, **kw):
        self.inputs.append(user)
        self.system = system
        return {"text": FIXED_REPLY, "finish": "stop", "usage": None}


def retained_uids(v5mod, events):
    body, used = v5mod._budget_history(events, v5mod.MAX_EVENT_HISTORY_CU)
    return body, used, set(int(x) for x in re.findall(r"m_src\[(\d+)\]", body))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--candidate", default="action_policy_r4_guard_e3")
    ap.add_argument("--outdir", required=True)
    ap.add_argument("--max-fillers", type=int, default=40)
    a = ap.parse_args()

    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    outdir = a.outdir if os.path.isabs(a.outdir) else os.path.join(ROOT, a.outdir)
    os.makedirs(outdir, exist_ok=True)
    prompts_dir = os.path.join(outdir, "probe_prompts")
    os.makedirs(prompts_dir, exist_ok=True)

    m = load_mod(a.candidate)
    r1 = m.r2.apr1                      # 同一份机制代码，用于诊断 rel/budget 口径
    v5mod = m.v5
    cand_dir = os.path.join(ROOT, "candidates", a.candidate)
    assets = os.path.join(cand_dir, "assets")

    reg_path = os.path.join(outdir, "F_B_PROBE_REGISTRATION.json")
    reg = {
        "kind": "f_b_probe_registration",
        "created_before_any_prompt_capture": True,
        "candidate": a.candidate,
        "paid_state_sources": {k: os.path.relpath(v, ROOT).replace("\\", "/")
                               for k, v in PAID_STATES.items()},
        "criteria_note": ("判据=本登记表+机制源码，全部在零调用夹具下登记；"
                          "没有任何模型答卷可供事后修改。"),
        "mechanism_source_sha256": sha(open(__file__, encoding="utf-8").read()),
        "probes": [],
    }
    for p in PROBES:
        reg["probes"].append({
            "probe_id": p["probe_id"], "class": p["class"], "story": p["story"],
            "fact_id": p["fact_id"], "domain": p["domain"],
            "question_text": p["question"],
            "value_forms": p["value_forms"], "leak_forms": p["leak_forms"],
            "stale_forms": p["stale_forms"], "opportunity": p["opportunity"],
            "dispatch_scoring": {
                "use_pass": "正文含 value_form 之一且语义上据以行动",
                "contamination_fail": "把 stale_form 当现行值，或断言窗口内没有的内容",
                "attribution_pass": (p["attribution_targets"] and
                                     "主体归给 %s" % "/".join(p["attribution_targets"])) or "n/a",
            },
            "backup": bool(p.get("backup")),
        })
    json.dump(reg, open(reg_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    reg_sha = sha(open(reg_path, encoding="utf-8").read())

    report = {"kind": "f_b_precheck", "candidate": a.candidate,
              "registration": os.path.relpath(reg_path, ROOT).replace("\\", "/"),
              "registration_sha256": reg_sha,
              "history_budget_cu": v5mod.MAX_EVENT_HISTORY_CU,
              "facts_projection_budget_cu": getattr(r1, "BUDGET_FACTS_CU", None),
              "relevance_floor": getattr(r1, "RELEVANCE_FLOOR", None),
              "zero_real_calls": True, "probes": [], "gate": {}}
    results = []

    for p in PROBES:
        sid = p["story"]
        snap_path = PAID_STATES[sid]
        sandbox = os.path.join(prompts_dir, "_sandbox_%s" % p["probe_id"])
        os.makedirs(sandbox, exist_ok=True)
        h = m.restore(sandbox, open(snap_path, "rb").read())
        paid_uids = {e["uid"] for e in h["events"]}
        last_t = None
        for e in h["events"]:
            t = parse_ts(e.get("ts"))
            if t and (last_t is None or t > last_t):
                last_t = t
        fact = (h.get("mem") or {}).get("facts", {}).get(p["fact_id"])
        rec = {"probe_id": p["probe_id"], "class": p["class"], "story": sid,
               "fact_id": p["fact_id"], "domain": p["domain"],
               "paid_turn_count": h["turn_count"], "paid_events": len(h["events"]),
               "failures": [], "notes": []}
        if fact is None:
            rec["failures"].append("fact_missing_in_paid_state")
            results.append(rec)
            continue
        rec["fact_current_text"] = fact.get("text")
        rec["fact_status"] = fact.get("status")
        rec["fact_evidence_uids"] = list(fact.get("evidence_uids") or [])
        hist_entries = fact.get("history") or []
        rec["registered_prior_state"] = [
            {"text": e.get("text"), "status": e.get("status"),
             "reason": e.get("reason"), "src_uids": e.get("src_uids")}
            for e in hist_entries]
        rec["prior_state_registered"] = bool(hist_entries) or p["class"] != "correction"

        events_by_uid = {e["uid"]: e for e in h["events"]}
        forms = list(dict.fromkeys(p["value_forms"] + p["leak_forms"]))
        src_hits = [uid for uid in (fact.get("evidence_uids") or [])
                    if any(f in (events_by_uid.get(uid, {}).get("text") or "") for f in forms)]
        rec["source_uid_hits"] = src_hits
        if not src_hits:
            rec["failures"].append("P1_support:no_paid_message_carries_the_value")
        else:
            rec["source_messages"] = [{"uid": u, "role": events_by_uid[u]["role"],
                                       "ts": events_by_uid[u]["ts"],
                                       "text": events_by_uid[u]["text"]} for u in src_hits]

        # 需要被挤出窗口的全部原文（含同义复述），不限于该 fact 的证据 uid
        carriers = [e["uid"] for e in h["events"]
                    if any(f in (e.get("text") or "") for f in forms)]
        rec["carrier_uids_before"] = carriers

        gw = FixtureGW()
        clock = next_ts(last_t, 20)
        seq = h["turn_count"]
        used_fillers, filler_log = [], []
        max_idx = min(a.max_fillers, 4 * len(FILLERS))
        for idx in range(max_idx):
            _, _, kept = retained_uids(v5mod, h["events"])
            if not (set(carriers) & kept):
                break
            base_txt = FILLERS[idx % len(FILLERS)]
            filler = base_txt if idx < len(FILLERS) else base_txt + " 又想起一条"
            if any(f in filler for f in forms):
                rec["notes"].append("filler_rejected_leak:%s" % idx)
                continue
            seq += 1
            clock = next_ts(last_t, 20 + 30 * (len(filler_log) + 1))
            m.run_turn(h, ctx_for(seq, clock, filler), gw)
            filler_log.append({"seq": seq, "clock": clock, "text": filler})
            used_fillers.append(filler)

        _, _, kept_before = retained_uids(v5mod, h["events"])
        rec["fillers_used"] = len(filler_log)
        rec["filler_log"] = filler_log
        rec["carriers_still_in_window"] = sorted(set(carriers) & kept_before)
        if rec["carriers_still_in_window"]:
            rec["failures"].append("P2:source_still_inside_1400CU_window")

        # 真正的探针轮：夹具跑一轮，只为抓实际出站 prompt
        seq += 1
        probe_clock = next_ts(last_t, 20 + 30 * (len(filler_log) + 1) + 40)
        m.run_turn(h, ctx_for(seq, probe_clock, p["question"]), gw)
        on_prompt = gw.inputs[-1]
        segs = split_segments(on_prompt)
        rec["segment_kinds"] = [k for k, _ in segs]
        rec["unknown_segments"] = [s[:60] for k, s in segs if k == "unknown" or k == "other"]

        proj_text = "\n\n".join(s for k, s in segs if k.startswith("proj_"))
        non_proj = [(k, s) for k, s in segs if not k.startswith("proj_")]

        # P3 投影证据
        rel = r1._relevance(fact.get("text") or "", r1._bigrams(p["question"]))
        rec["relevance_question_vs_fact"] = round(rel, 4)
        proj_line = [ln for ln in proj_text.split("\n")
                     if ("[%s]" % p["fact_id"]) in ln]
        rec["projection_line"] = proj_line[0] if proj_line else ""
        if not proj_line:
            rec["failures"].append("P3:no_projection_evidence_for_the_fact")
        elif not any(v in proj_line[0] for v in p["value_forms"]):
            rec["failures"].append("P3:projection_line_lacks_the_value")
        rec["projection_fact_lines"] = sum(1 for ln in proj_text.split("\n")
                                           if ln.startswith("· ["))
        # Fidelity（离线）：投影里的值必须逐字能在被引来源消息里核到
        fid_ok, fid_src = [], []
        for v in p["value_forms"]:
            hit_uids = [u for u in (fact.get("evidence_uids") or [])
                        if v in (events_by_uid.get(u, {}).get("text") or "")]
            if hit_uids:
                fid_ok.append(v)
                fid_src.append({v: hit_uids})
        rec["fidelity_value_verbatim_in_source"] = fid_ok
        rec["fidelity_source_map"] = fid_src
        rec["fidelity_ok"] = bool(fid_ok)
        rec["na_conditions"] = {
            "Capture": "n/a：原子来自已付费写回，本批不再测写回机会",
            "Fidelity": "离线按原文逐字核验，见 fidelity_value_verbatim_in_source",
            "Update": "仅更正类探针适用" if p["class"] == "correction" else "NA：非更正类探针",
            "Retrieval": "on 侧有投影证据即成立；off 侧为对照",
            "Use": "需真实生成答卷，本前置不判",
            "Contamination": "需真实生成答卷，本前置不判",
        }

        # P1 值只来自投影：非投影段一律不得含 value_form
        leaks = []
        for k, s in non_proj:
            for v in p["value_forms"]:
                if v in s:
                    leaks.append({"segment": k, "form": v})
        rec["P1_value_outside_projection"] = leaks
        if leaks:
            rec["failures"].append("P1:value_present_outside_projection")
        # 问题文字/本轮输入不得带答案
        cur = "\n\n".join(s for k, s in segs if k == "current")
        rec["P1_question_carries_answer"] = any(v in cur for v in p["value_forms"])
        if rec["P1_question_carries_answer"]:
            rec["failures"].append("P1:question_carries_answer")
        for k, s in segs:
            if k in ("seed", "note", "goal", "plan") and any(
                    v in s for v in p["value_forms"]):
                rec["failures"].append("P1:answer_in_%s_block" % k)

        # P2 出站原文段既无来源句也无同义复述
        hist_seg = "\n\n".join(s for k, s in segs if k == "history")
        hist_uids = set(int(x) for x in re.findall(r"m_src\[(\d+)\]", hist_seg))
        rec["retained_history_uids"] = sorted(hist_uids)
        rec["history_cu"] = v5mod.rc.utf16_len(hist_seg)
        rec["fixture_lines_in_history"] = sum(
            1 for u in hist_uids if u not in paid_uids)
        p2_hits = [f for f in forms if f in hist_seg]
        rec["P2_forms_inside_history_segment"] = p2_hits
        if p2_hits:
            rec["failures"].append("P2:source_or_paraphrase_inside_history_segment")
        rec["probe_clock"] = probe_clock
        rec["stale_forms_inside_history_segment"] = [
            s for s in p["stale_forms"] if s in hist_seg]

        # P4 off = on 精确删除声明投影段
        off_lines = []
        removed = []
        for k, s in segs:
            if k.startswith("proj_") and s.startswith(PROJ_HEADERS):
                removed.append(k)
            else:
                off_lines.append(s)
        off_prompt = "\n\n".join(off_lines)
        rec["P4_removed_sections"] = removed
        rec["P4_projection_sections_present"] = [k for k, _ in segs
                                                 if k.startswith("proj_")]
        if not removed:
            rec["failures"].append("P4:no_declared_projection_section_to_remove")
        keep_kinds = ("seed", "goal", "plan", "note", "history", "current", "action")
        for k in keep_kinds:
            on_part = "\n\n".join(s for kk, s in segs if kk == k)
            off_part = "\n\n".join(s for kk, s in split_segments(off_prompt) if kk == k)
            if on_part != off_part:
                rec["failures"].append("P4:%s_differs_between_on_and_off" % k)
        if any(v in off_prompt for v in p["value_forms"]):
            rec["failures"].append("P4:off_prompt_still_contains_value")

        # P5 更正类：原/新状态+来源+机会在发出答案前登记
        if p["class"] == "correction":
            if not hist_entries:
                rec["failures"].append("P5:correction_without_archived_prior_state")
            if not p["stale_forms"]:
                rec["failures"].append("P5:correction_without_registered_stale_value")

        rec["passed"] = not rec["failures"]
        tag = "%s_%s" % (p["probe_id"], sid)
        with open(os.path.join(prompts_dir, "%s_on.txt" % tag), "w",
                  encoding="utf-8") as f:
            f.write(on_prompt)
        with open(os.path.join(prompts_dir, "%s_off.txt" % tag), "w",
                  encoding="utf-8") as f:
            f.write(off_prompt)
        rec["on_prompt_sha256"] = sha(on_prompt)
        rec["off_prompt_sha256"] = sha(off_prompt)
        rec["on_prompt_cu"] = v5mod.rc.utf16_len(on_prompt)
        rec["off_prompt_cu"] = v5mod.rc.utf16_len(off_prompt)
        rec["failures"] = sorted(set(rec["failures"]))
        results.append(rec)

    report["probes"] = results
    report["measurement_scope"] = {
        "ablation": ("off = on 精确删除【相关已确认事实】【暂定事项】【最近状态变更】三段；"
                     "seed/目标/计划/note/动作层/原文历史/本轮输入/规则段逐字节同一份。"),
        "asymmetry_1": ("on 侧投影不止被问那一条（实测每探针 1–5 行），off 侧一并删除，"
                        "所以 on/off 差值是“整层投影”的效应，不是单条事实的效应。"),
        "asymmetry_2": ("夹具消息占据原文窗口的一部分（见各探针 fixture_lines_in_history、"
                        "history_cu），且各探针保留窗口内容不同；跨探针只比“同一探针的 on/off”。"),
        "asymmetry_3": ("投影选择是词面二元组重合（RELEVANCE_FLOOR 以下不注入）；"
                        "问题不复述事实用词就没有投影证据，这类失败记“测量设计未过”，"
                        "不能算模型检索失败。"),
        "correction_probe_note": ("更正类探针故意让旧值留在窗口内（见 "
                                  "stale_forms_inside_history_segment），"
                                  "新值只在投影里——这样 on/off 与污染才有区分度。"),
    }
    passing = [r for r in results if r["passed"]]

    def quota_fill(cls, want):
        got = [r for r in passing if r["class"] == cls and not any(
            q.get("probe_id") == r["probe_id"] for q in PROBES if q.get("backup"))]
        backups = [r for r in passing if r["class"] == cls and any(
            q.get("probe_id") == r["probe_id"] for q in PROBES if q.get("backup"))]
        pool = got + backups
        pool.sort(key=lambda r: (r["story"], r["fact_id"]))
        return pool[:want]

    selected = []
    for cls, want in (("attribution", 2), ("correction", 2), ("time_state", 2)):
        selected += quota_fill(cls, want)
    domains = sorted({r["domain"] for r in selected})
    report["gate"] = {
        "probes_defined": len(PROBES),
        "probes_passing_all_preconditions": len(passing),
        "selection_rule": ("通过全部前置者按类别配额(归属2/更正2/时间2)选取，"
                           "同类按 story+fact_id 字典序，正选探针优先、备用其次；"
                           "此规则在派发前写死，不看分数临时挑。"),
        "selected": [r["probe_id"] for r in selected],
        "selected_domains": domains,
        "requirement_6_probes": len(selected) >= 6,
        "requirement_3_domains": len(domains) >= 3,
        "dispatch_allowed": len(selected) >= 6 and len(domains) >= 3,
        "physical_generations_if_dispatched": len(selected) * 2 * 2,
        "note": ("24 次=选中探针数×on/off×2 重复；夹具轮为 0 成本，不计入授权额度，"
                 "但会在账本外单独登记为 fixture_turns。"),
    }
    report["fixture_turns_total"] = sum(r["fillers_used"] for r in results)
    out = os.path.join(outdir, "F_B_PRECHECK.json")
    json.dump(report, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    sys.stdout.write("registration_sha256=%s\n" % reg_sha[:16])
    for r in results:
        sys.stdout.write("%-6s %-10s %-4s %-7s passed=%-5s fillers=%-2d rel=%s fail=%s\n" % (
            r["probe_id"], r["class"], r["story"], r["fact_id"], r["passed"],
            r.get("fillers_used", -1), r.get("relevance_question_vs_fact"),
            ",".join(r["failures"]) or "-"))
    g = report["gate"]
    sys.stdout.write("GATE pass=%d/%d selected=%s domains=%d dispatch_allowed=%s gen=%d\n" % (
        g["probes_passing_all_preconditions"], g["probes_defined"],
        "|".join(g["selected"]), len(g["selected_domains"]),
        g["dispatch_allowed"], g["physical_generations_if_dispatched"]))
    sys.stdout.write("WROTE %s\n" % out.replace("\\", "/"))
    return 0 if g["dispatch_allowed"] else 3


if __name__ == "__main__":
    sys.exit(main())
