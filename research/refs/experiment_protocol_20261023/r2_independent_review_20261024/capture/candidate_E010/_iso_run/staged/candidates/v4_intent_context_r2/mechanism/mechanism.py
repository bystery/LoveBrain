# candidates/v4_intent_context/mechanism/mechanism.py
#
# v4_intent_context — 下一代意图与上下文候选【初稿】(VERDICT §6-C)。
# 主假设：当前目标的"作用域与上下文优先级"。复用已签收机制/资产的合适部分，
# 不改动正在被比较的 reference_product_v1 / v3_system_slim（父本闭包，不复制后漂移）。
#
# 相对 v3(=父本机制+精简system) 与 v0(=精简system+简单事件记忆)，v4 只加一层
# 【目标作用域与上下文优先级】的组织规则，其余尽量保持简单、可解释、无额外模型调用：
#   1) 正确解析有效意图（enabled+text+status），把"当前目标"真实放进预算保护的请求区，
#      而不是 notes 里一个标志；禁用/关闭/替换 → 当前目标区随之改变，不留旧目标。
#   2) 用户目标可【指导行动】（提新邀约/表达兴趣/反撩/深入交流），不一律缩成只调口吻；
#      但不复述军师目标原文、不把新提议说成对方已同意（明确红线写进装配指令）。
#   3) 区分"当前指令/目标"与"已确认真实历史事项"：关闭一个目标≠删除已确认约定；
#      已确认事项(来自 initial_kb.ongoing_plan 种子)持续存在，但不自动复活旧目标。
#   4) note 分四类（公开事实/私密情绪/拟发草稿/推测），不一概泄露、不一概禁用，
#      不要求用户额外填复杂表单。
#   5) 不为识别"调情/邀约/深聊"加题材词特判，不多一次模型调用；未知时不假装已确立关系。
#   6) 记忆保持简单：真实消息事件 + 种子事项；模型回复是草稿，绝不写回成真实 ME 历史
#      （初稿不做自动摘要/反思/辅助生成——那是后续对照，不在本稿声称）。
#
# 共享依赖 rc / build_system_prompt / 各语义块来自父本 reference_product_v1（进闭包）。
import importlib.util
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)                       # candidates/v4_intent_context
_ROOT = os.path.dirname(_CAND)                       # candidates/
_BASE = os.path.join(_ROOT, "reference_product_v1")  # staging trigger: candidates/reference_product_v1 / candidates/v3_system_slim
sys.path.insert(0, _BASE)


def _load_base():
    spec = importlib.util.spec_from_file_location(
        "ref_product_v1_mechanism_v4",
        os.path.join(_BASE, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


base = _load_base()
rc = base.rc
build_system_prompt = base.build_system_prompt

MAX_EVENT_HISTORY_CU = 1400
MAX_GOAL_CU = 600          # 当前目标区预算（保护，不被旧史挤掉；超限则整段截目标句）
PLAN_LINE_PREFIX = "· "


def _intent_cfg_from_input(inp):
    it = inp.get("intent")
    if it is None:
        return {}
    if isinstance(it, str):
        return {"enabled": True, "text": it, "status": "ACTIVE"} if it.strip() else {}
    if isinstance(it, dict):
        cfg = dict(it)
        cfg.setdefault("status", "ACTIVE")
        return cfg
    return {}


def _goal_block(cfg):
    """有效目标→行动导向装配（复用父本【持续意图】格式，再加作用域/红线指令）。
    关闭/缺省 → 返回空串（不留旧目标；也不删 plans）。"""
    if not rc.is_intent_effective(cfg):
        return ""
    text = (cfg.get("text") or "").strip()
    if rc.utf16_len(text) > MAX_GOAL_CU:
        # 整段保留下限：截断到预算并标注（简单、可解释，不切代理对由 utf16 计算近似）
        text = text[:MAX_GOAL_CU] + "…（目标描述过长已截断）"
    return ("【当前目标(仅本轮有效，可指导行动)】\n" + text + "\n"
            "使用规则：可据此主动创造机会、表达兴趣、适度反撩或深入交流；"
            "但这只是本轮目标——不得把目标原文复述成要发的话，不得把新提议说成对方已同意，"
            "未知对方意愿时不假装关系已确立。")


def _plan_lines(meta):
    """已确认/进行中事项：来自 initial_kb.ongoing_plan 种子（产品双分区格式的等价输入）。
    与"当前目标"分离：关闭目标不清此区；此区条目不自动变成新目标。"""
    plan = (meta.get("initial_kb") or {}).get("ongoing_plan") or []
    lines = []
    if isinstance(plan, list):
        for item in plan:
            if isinstance(item, dict):
                name = item.get("name") or item.get("item") or ""
                status = item.get("status") or ""
                lines.append("%s%s%s" % (PLAN_LINE_PREFIX, name,
                                         ("（%s）" % status) if status else ""))
            elif isinstance(item, str):
                lines.append("%s%s" % (PLAN_LINE_PREFIX, item))
    return [x for x in lines if x.strip()]


def init(assets_root, episode_meta, gateway, sandbox):
    os.makedirs(sandbox, exist_ok=True)
    meta = episode_meta or {}
    return {
        "assets_root": assets_root,
        "sandbox": sandbox,
        "persona": meta.get("persona") or {},
        "initial_kb": meta.get("initial_kb") or {},
        "plans": _plan_lines(meta),           # 已确认/进行中事项（持续，独立于当前目标）
        "system_prompt": base.build_system_prompt(assets_root),
        "events": [],                          # 有序真实消息 [{uid, role, text, ts}]
        "uid_by_key": {},
        "uid_seq": 0,
        "turn_count": 0,
    }


def _key_of(role, text, ts):
    return "%s|%s|%s" % (role, text, ts)


def _uid_for(h, role, text, ts):
    key = _key_of(role, text, ts)
    uid = h["uid_by_key"].get(key)
    if uid is None:
        uid = h["uid_seq"]
        h["uid_seq"] += 1
        h["uid_by_key"][key] = uid
        h["events"].append({"uid": uid, "role": role or "",
                            "text": text or "", "ts": ts or ""})
    return uid


def _ev_line(m):
    return "m_src[%d] | %s | %s | %s" % (m["uid"], m.get("ts", ""),
                                         "PARTNER" if m["role"] == "HER" else "USER",
                                         m["text"])


def _budget_history(events, budget):
    """整条消息为单位从新到旧累加；连最近一条都放不下时省略整段旧史（当前输入独立段完整保留）。"""
    lines, used, omitted = [], 0, 0
    for m in reversed(events):
        line = _ev_line(m)
        add = rc.utf16_len(line) + 1
        if used + add > budget:
            if not lines:
                omitted = 1
            break
        lines.append(line)
        used += add
    body = "\n".join(reversed(lines))
    if omitted and not body:
        body = "（最近一条旧史单独超出历史预算，本段省略；当前输入在下方完整保留）"
    return body, used


def _media_line(images, has_text_share):
    """r2 加固①：媒体可用性按本轮真实输入表达（治 E004_T08 无图装看图）。"""
    n = len(images or [])
    if n > 0:
        return ("# 本轮媒体：实际附带 %d 张图片，仅可依据这些真实图片描述画面；"
                "未见的内容不得臆测。" % n)
    if has_text_share:
        return ("# 本轮媒体：无附带图片。她说‘拍了/发给你’只是文字分享——可以期待、追问、"
                "顺着聊内容，但没有画面，禁止评价构图/摄影/‘看着不错’等未见细节。")
    return "# 本轮媒体：无附带图片，不评价任何画面。"


def _source_rule(cur_aliases):
    """r2 加固②：来源 ID 合法（治 E006_T02 illegal_source_id）。当前窗口 id 才可作
    scene_facts.source_ids；历史 m_src[uid] 仅供理解/相关回扣，不作本轮新增事实来源。"""
    legal = "、".join(cur_aliases) if cur_aliases else "（本轮无消息）"
    return ("# 来源规则：本轮 analysis.scene_facts 的 source_ids 只能引用下方 <chat> 里的 "
            "id(%s)；【真实历史消息】里的 m_src[..] 仅用于理解与相关回扣，不得当作本轮新证据、"
            "不得写入 source_ids。旧史可回扣但不冒充本轮新发生。" % legal)


def _time_scope_rule():
    """r2 加固③：时间作用域（治 E005_T06 换日期迁移旧空闲 + 职业编造亲历）。"""
    return ("# 时间与亲历边界：空闲/日程/排班/上班/带课等时间性事实属于它出现那天的日期；"
            "换到新的日期不得把旧日的‘有空/时间’自动继承过来，除非本轮消息重新确认。"
            "职业或身份不证明本人某次实际上班/出席/亲历，不据此补出具体经历；"
            "不确定的时间宁可就说不确定，不编具体场次/时点。")


def run_turn(h, ctx, gw):
    inp = ctx.get("input") or {}
    clock = (inp.get("clock") or "").strip()
    seq = ctx.get("turn_index") or (h["turn_count"] + 1)
    raw_msgs = [m for m in (inp.get("messages") or []) if isinstance(m, dict)]

    cur = []
    for m in raw_msgs:
        uid = _uid_for(h, m.get("role"), m.get("text"), m.get("ts"))
        cur.append({"uid": uid, "role": m.get("role") or "",
                    "text": m.get("text") or "", "ts": m.get("ts") or ""})
    cur_uids = set(c["uid"] for c in cur)
    prior_events = [e for e in h["events"] if e["uid"] not in cur_uids]

    parts = []
    # 1) 种子（画像 + 初始事实）
    seed = {}
    if h["persona"]:
        seed["persona"] = h["persona"]
    kb = {k: v for k, v in (h["initial_kb"] or {}).items() if k != "ongoing_plan"}
    if kb:
        seed["initial_kb"] = kb
    if seed:
        parts.append("【画像/初始事实(种子，可公开使用)】\n"
                     + json.dumps(seed, ensure_ascii=False))

    # 2) 当前目标（作用域=本轮；关闭→空；可指导行动但不得谎称已同意/复述）
    intent_cfg = _intent_cfg_from_input(inp)
    goal = _goal_block(intent_cfg)
    if goal:
        parts.append(goal)

    # 3) 已确认/进行中事项（独立于当前目标，持续；关闭目标不清此区，也不自动复活旧目标）
    if h["plans"]:
        parts.append("【已确认/进行中事项(独立于当前目标，勿当新目标自动重启)】\n"
                     + "\n".join(h["plans"]))

    # 4) note（军师备注，四类区分；只约束本轮，不当已说过的话/既成事实）
    note = (inp.get("note") or "").strip()
    if note:
        parts.append(
            rc.NOTE_BLOCK_HEADER.strip() + "\n「" + note + "」\n"
            "四类分清：①公开事实(位置/日程/共同偏好)可直接带入正文；"
            "②私密情绪/动机只用来调口吻，不原样外发；③拟发草稿未发出前不算既成约定；"
            "④推测/读心不作成对方已确认。缺信息时承认为‘不足’，不臆造。")

    # 5) 历史真实消息（整段预算；含来源 uid + ts）
    hist, hist_cu = _budget_history(prior_events, MAX_EVENT_HISTORY_CU)
    parts.append("# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + hist)

    # 6) 当前窗口（最高优先，整条不被裁；逐条 alias + source_id + ts）
    cur_lines, alias_map, cur_aliases = [], {}, []
    for i, m in enumerate(cur):
        alias = "m%d" % i
        cur_aliases.append(alias)
        alias_map[alias] = m["uid"]
        speaker = "PARTNER" if m["role"] == "HER" else "USER"
        cur_lines.append(json.dumps({"id": alias, "source_id": m["uid"],
                                     "speaker": speaker, "ts": m["ts"],
                                     "text": m["text"]}, ensure_ascii=False))
    parts.append("# 本轮输入(最高优先，整条不被裁；id=来源ID，PARTNER=对方/USER=本人)\n"
                 "<chat>\n" + "\n".join(cur_lines) + "\n</chat>")

    # r2 加固②来源合法 + ①媒体可用（当前窗口之后，id 已可见）
    parts.append(_source_rule(cur_aliases))
    images = [m["image"] for m in raw_msgs if m.get("image")]
    _share_kw = ("拍", "照片", "图", "发你", "发过去", "构图", "看看这")
    has_text_share = not images and any(any(k in (c["text"] or "") for k in _share_kw)
                                        for c in cur)
    parts.append(_media_line(images, has_text_share))

    # 7) 当前时间 + r2 加固③时间作用域
    if clock:
        parts.append(rc.build_timestamp_prompt(clock).strip())
    parts.append(_time_scope_rule())

    prompt = "\n\n".join(parts)
    res = gw.generate(h["system_prompt"], prompt, images=images or None, tag="main")
    reply = res["text"]
    h["turn_count"] = seq
    notes = ("v4_intent_context_r2[events=%d goal_active=%d plans=%d prompt_cu=%d "
             "history_cu=%d images=%d media_honest=1 source_rule=1 time_scope=1 "
             "current_ids=%s canonical_id_map=%s reply_not_committed=1]"
             % (len(h["events"]), 1 if rc.is_intent_effective(intent_cfg) else 0,
                len(h["plans"]), rc.utf16_len(prompt), hist_cu, len(images),
                [c["uid"] for c in cur], alias_map))
    return {"reply": reply, "notes": notes}


def snapshot(h):
    return json.dumps({
        "persona": h["persona"], "initial_kb": h.get("initial_kb", {}),
        "plans": h["plans"], "assets_root": h["assets_root"], "events": h["events"],
        "uid_by_key": h["uid_by_key"], "uid_seq": h["uid_seq"],
        "turn_count": h["turn_count"],
    }, ensure_ascii=False).encode("utf-8")


def restore(sandbox, snap):
    d = json.loads(snap.decode("utf-8"))
    os.makedirs(sandbox, exist_ok=True)
    ar = d.get("assets_root")
    return {
        "assets_root": ar, "sandbox": sandbox,
        "persona": d.get("persona") or {}, "initial_kb": d.get("initial_kb") or {},
        "plans": d.get("plans") or [],
        "system_prompt": base.build_system_prompt(ar) if ar else "",
        "events": d.get("events") or [], "uid_by_key": d.get("uid_by_key") or {},
        "uid_seq": d.get("uid_seq", 0), "turn_count": d.get("turn_count", 0),
    }
