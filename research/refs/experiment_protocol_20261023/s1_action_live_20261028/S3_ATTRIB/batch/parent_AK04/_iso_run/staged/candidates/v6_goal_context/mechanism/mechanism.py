# candidates/v6_goal_context/mechanism/mechanism.py
#
# v6_goal_context — MAX-288 V2 P05-A：目标/行动与自然互动分支（可实测版）。
#
# 基座：v5_purpose_aligned 闭包（不复制后漂移；四 system 资产自带副本，身份独立）。
# 相对 v5 的真实差异（都在 user 侧本轮装配，零额外模型调用）：
#   1) 目标作用域：ACTIVE/ADJUSTED 生效、CLOSED/COMPLETED/EXPIRED/PAUSED 不生效；
#      expiry<now 视为失效（v5 只查 status，不看 expiry）；记录 goal_history 供恢复审计；
#      关闭不复活；已确认真实约定仍在事项区。
#   2) 反馈优先：机制从本轮 HER 最近消息检测【显式】收尾/拒绝/忙碌/暂缓/积极/含糊信号；
#      显式拒绝/忙碌/收尾时，邀约能力不出现，目标只能暂缓；含糊不自动降温，给多动作；
#      不按单个"哈哈/切"路由到唯一动作——含糊时提供多动作由模型按语境选。
#   3) 条件化能力集：每轮只展示适用的 2—5 个动作能力（承接/展开/反抛/轻试探/自我表达/
#      深聊/转场/邀约/收尾/停），不是全菜单；规则通用，无领域词。
#   4) 钩子审计：analysis 记录 signal/goal_category/action_taken/anchor/hook；
#      消费端校验枚举、anchor 属于本轮、hook.text 逐字出现在最终可发文本；
#      未兑现只记 notes（不罚分、不二次调用）。
#   5) 私密/公开/草稿/猜测分区沿用 v5 的四类 note 与事实-行动边界。
#
# 诚实边界：
#   - 本候选不证明"更会聊"；真实质量由 P06 同批对照判定。
#   - 信号检测只覆盖显式短语；反讽/绕弯/上下文否定仍靠模型语义判断；检测结果写进 prompt
#     作为"显式反馈提示"，不是唯一路由。
#   - analysis 是审计记录，不是效果分；最终文本兑现情况由 hook 逐字检查与评审层判断。
import importlib.util
import json
import os
import re
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)
_ROOT = os.path.dirname(_CAND)
_V5 = os.path.join(_ROOT, "v5_purpose_aligned")  # staging trigger: candidates/v5_purpose_aligned


def _load_v5():
    spec = importlib.util.spec_from_file_location(
        "v5_mechanism_for_v6_goal_context",
        os.path.join(_V5, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


v5 = _load_v5()
base = v5.base
rc = base.rc
build_system_prompt = base.build_system_prompt

MAX_GOAL_CU = 600
MAX_ACTION_LOG = 200
MAX_GOAL_HISTORY = 100

# ── 显式反馈短语（通用生活语言，无领域/题号特判） ───────────────────────────
CLOSING = ("睡了", "睡觉", "晚安", "先不聊", "不聊了", "去忙", "先忙", "明天再说",
           "明天说", "先这样", "回头聊", "回头说", "下次聊", "下次说", "去洗澡",
           "去洗漱", "先走了", "去赶车", "去赶班车", "去吃饭", "改天再")
REFUSAL = ("不想", "不太想", "没兴趣", "没那个意思", "不用了", "算了吧", "先不了",
           "不去了", "不约", "别安排", "别约", "没这个想法", "不合适", "不要了",
           "拒绝", "没戏")
BUSY = ("在忙", "忙着", "有点忙", "太忙", "很忙", "加班", "没空", "出差", "开会",
        "赶项目", "没时间", "顾不上", "抽不出")
DEFER = ("改天", "下次", "再看", "再说", "看情况", "到时候", "过阵子", "缓一缓",
         "等有空", "有空再说", "晚点说")
POSITIVE = ("好啊", "好呀", "好哦", "可以啊", "可以呀", "行啊", "没问题", "期待",
            "想去看", "想看", "一起去", "带我去", "等你", "就这么定", "说定了",
            "要得", "成啊", "走起", "冲")
AMBIG = ("嗯", "哦", "哈", "切", "还行", "不知道", "看看吧", "也许", "随便",
         "都行", "再说吧", "看情况")

FEEDBACK_SIGNALS = ("积极", "接纳", "中性", "含糊", "忙碌", "暂缓", "拒绝", "收尾", "不明")
GOAL_CATEGORIES = ("拉近", "了解", "推进见面", "暂缓", "收线", "无目标")
ACTIONS = ("承接", "展开", "反抛", "轻试探", "自我表达", "邀约", "深聊", "转场", "收尾", "停")
HOOK_KINDS = ("观察", "观点", "小选择", "未展开角度", "无")

CAP_RULES = {
    "承接": "先接住她这句话的实际内容与情绪；可轻问一句延伸，不急着换话题。",
    "展开": "就她给的内容补充一个具体观察/看法，给她可接的入口；不堆砌。",
    "反抛": "用调侃/回敬轻推回一个话头，允许张力和留白；不逐字套模板，不人身攻击。",
    "轻试探": "用轻量问题或小选择试探意愿（时间/偏好二选一），只推进一层，不逼表态。",
    "自我表达": "说一点你自己真实的看法/感受（仅真有依据的）；不编造亲历。",
    "邀约": "给具体、轻量、可选项式的提议（时间/形式给选择），给她拒绝空间；未确认不冒称已约成。",
    "深聊": "就她刚给的内容往深一层（感受/原因/细节任一），一次只推进一层。",
    "转场": "借当前话茬自然引到相邻话题，给一句轻钩子；不硬切。",
    "收尾": "自然收住本轮（一句轻总结/下次的话头），不硬留也不突然消失。",
    "停": "不追加追问、不自证、不推进；如允许不回且判断自然，用最短的自然收住。",
}
ACTION_ORDER = ("承接", "展开", "反抛", "轻试探", "自我表达", "深聊", "转场", "邀约", "收尾", "停")


def _flags():
    """params.json 的机制开关（默认动作层开）。改 flags → params.json 变 → 身份变，
    消融必须是新身份，不能在同一身份下偷偷换行为。"""
    p = os.path.join(_CAND, "params.json")
    try:
        d = json.load(open(p, encoding="utf-8"))
        f = d.get("mechanism_flags") if isinstance(d, dict) else None
        return {"action_layer": bool((f or {}).get("action_layer", True))}
    except Exception:                                      # noqa: BLE001
        return {"action_layer": True}


def _truncate_utf16(text, limit):
    """按 UTF-16 码元截断且不切代理对（Kotlin 长度语义）。"""
    if rc.utf16_len(text) <= limit:
        return text
    out, used = [], 0
    for ch in text:
        w = 2 if ord(ch) >= 0x10000 else 1
        if used + w > limit:
            break
        out.append(ch)
        used += w
    return "".join(out) + "…（过长已截断）"


def _goal_state(cfg, clock):
    """解析 intent → 本轮目标状态。关闭终态不生效；expiry<now 失效。"""
    cfg = cfg or {}
    status = (cfg.get("status") or "ACTIVE").strip()
    text = (cfg.get("text") or "").strip()
    if not cfg or (not text and not cfg.get("enabled")):
        status = "NONE"
    expiry = (cfg.get("expiry") or "").strip()
    now = (cfg.get("now") or clock or "").strip()
    expired_by_time = bool(expiry and now and expiry < now)
    effective = bool(rc.is_intent_effective(cfg)) and not expired_by_time
    closed = status in ("CLOSED", "COMPLETED", "EXPIRED", "PAUSED") or expired_by_time
    return {"effective": effective, "closed": closed, "status": status,
            "text": text, "adjusted": status == "ADJUSTED",
            "expired_by_time": expired_by_time}


def _goal_block(gs):
    if not gs["effective"]:
        return ""
    text = _truncate_utf16(gs["text"], MAX_GOAL_CU)
    tail = "（本轮已调整后的目标）" if gs["adjusted"] else ""
    return ("【当前目标(仅本轮有效，可指导行动)】\n" + text + tail + "\n"
            "使用规则：可据此主动创造机会、表达兴趣、适度反撩或深入交流；"
            "但**她当前的话与明确反馈优先于本目标**——她明确拒绝/忙碌/收尾时目标暂缓或停止推进。"
            "这只是本轮目标：不得把目标原文复述成要发的话，不得把新提议说成对方已同意，"
            "未知对方意愿时不假装关系已确立。")


def _goal_history_update(h, gs, turn, clock):
    status = gs["status"] if (gs["text"] or gs["closed"]) else "NONE"
    if not gs["effective"] and not gs["closed"] and not gs["text"]:
        status = "NONE"
    last = h["goal_history"][-1] if h.get("goal_history") else None
    if last is None or last.get("status") != status or last.get("text") != gs["text"] \
            or last.get("effective") != gs["effective"]:
        h.setdefault("goal_history", []).append({
            "turn": turn, "clock": clock, "status": status,
            "text": gs["text"], "effective": gs["effective"],
            "adjusted": gs["adjusted"], "closed": gs["closed"]})
        if len(h["goal_history"]) > MAX_GOAL_HISTORY:
            del h["goal_history"][:-MAX_GOAL_HISTORY]


def _last_her_text(cur):
    for m in reversed(cur):
        if m.get("role") == "HER":
            return m.get("text") or ""
    return ""


def _signal(cur, may_decline):
    text = _last_her_text(cur)
    low = (text or "").lower()
    hit = lambda words: any(w in low for w in words)          # noqa: E731
    closing = hit(CLOSING)
    refusal = hit(REFUSAL)
    busy = hit(BUSY)
    defer = hit(DEFER)
    positive = hit(POSITIVE)
    short = 0 < len((text or "").strip()) <= 6
    ambiguous = hit(AMBIG) or (short and not (closing or refusal or busy or defer or positive))
    if not (text or "").strip():
        hint = "不明"
    elif closing:
        hint = "收尾"
    elif refusal:
        hint = "拒绝"
    elif busy:
        hint = "忙碌"
    elif defer:
        hint = "暂缓"
    elif positive:
        hint = "积极"
    elif ambiguous:
        hint = "含糊"
    else:
        hint = "中性"
    return {"hint": hint, "closing": closing, "refusal": refusal, "busy": busy,
            "defer": defer, "positive": positive, "ambiguous": ambiguous,
            "last_her": text, "may_decline": bool(may_decline)}


def _capabilities(sig, goal_effective):
    hint = sig["hint"]
    if hint == "收尾":
        caps = ["承接", "收尾"]
        if sig["may_decline"]:
            caps.append("停")
    elif hint == "拒绝":
        caps = ["承接", "收尾", "停"]
    elif hint in ("忙碌", "暂缓"):
        caps = ["承接", "转场", "收尾"]
    elif hint == "积极":
        caps = ["承接", "展开", "自我表达", "深聊"]
        caps.append("邀约" if goal_effective else "反抛")
    elif hint == "含糊":
        caps = ["承接", "反抛", "轻试探", "转场"]
    else:
        caps = ["承接", "展开", "自我表达", "转场"]
        if goal_effective:
            caps.append("邀约")          # 中性轮最多 5 项：不把"深聊"也塞进同一菜单
    seen, out = set(), []
    for a in ACTION_ORDER:
        if a in caps and a not in seen:
            seen.add(a)
            out.append(a)
    return out


def _action_block(sig, caps, gs, contract_extra=""):
    lines = ["【本轮可用动作（只启用下列能力；不是每轮全塞菜单）】",
             "机制检测到的显式反馈：%s（依据：她最近一条消息）。若你读出的语义不同，以语义为准；"
             "但明确拒绝/忙碌/收尾不得被改写成欲擒故纵。" % sig["hint"]]
    if gs["effective"]:
        lines.append("当前目标：见上方【当前目标】；她当前的话与明确反馈优先。")
    elif gs["closed"]:
        lines.append("上一目标已关闭/失效：不得复活；已确认真实约定仍保留在事项区。")
    else:
        lines.append("当前无目标：不凭旧聊天复活邀约。")
    if sig["hint"] == "拒绝":
        lines.append("她明确拒绝/不愿继续：不邀约、不加压；先接住，再自然收住。")
    elif sig["hint"] in ("忙碌", "暂缓"):
        lines.append("她忙/暂缓：只轻接或改期，不催不定，不把暂缓当已答应。")
    elif sig["hint"] == "含糊":
        lines.append("含糊反馈：在 承接/反抛/轻试探/转场 中结合语境选，不自动降温，也不无条件加压。")
    elif sig["hint"] == "积极":
        lines.append("积极信号：可具体推进；有新提议就说成提议，不冒称已同意。")
    elif sig["hint"] == "收尾":
        lines.append("她已在收尾：自然收住；可留一句下次的话头，不硬留、不加问号。")
    lines.append("本轮能力：")
    for a in caps:
        lines.append("· " + a + "：" + CAP_RULES[a])
    lines.append(
        "把本轮选择写进 analysis（同一次回复内完成，不另起调用）："
        '"analysis":{"signal":"积极|接纳|中性|含糊|忙碌|暂缓|拒绝|收尾|不明",'
        '"goal_category":"拉近|了解|推进见面|暂缓|收线|无目标",'
        '"action_taken":"' + "|".join(ACTIONS) + '",'
        '"anchor":"m0","hook":{"kind":"观察|观点|小选择|未展开角度|无","text":""}}；'
        "anchor 必须是本轮 <chat> 的 id；hook.text 必须逐字出现在你最终某个可发文本里；"
        "没有合适钩子就写无，不硬造。" + (contract_extra or ""))
    return "\n".join(lines)


def _norm(t):
    return re.sub(r"\s+", "", t or "")


def _validate_analysis(reply, alias_map, caps):
    obj = base._extract_json(reply)
    if not isinstance(obj, dict):
        return {"parse": "reply_not_json"}
    a = obj.get("analysis")
    if not isinstance(a, dict):
        return {"parse": "analysis_missing_or_not_dict"}
    hook = a.get("hook") if isinstance(a.get("hook"), dict) else {}
    hk = (hook.get("kind") or "").strip()
    ht = (hook.get("text") or "").strip()
    resp_texts = []
    r = obj.get("response") if isinstance(obj.get("response"), dict) else {}
    for k in ("recommended", "bad_boy", "playful", "warm"):
        v = r.get(k)
        if isinstance(v, str):
            resp_texts.append(v)
    for v in (obj.get("directions") or []):
        if isinstance(v, str):
            resp_texts.append(v)
    hook_delivered = None
    if hk and hk != "无":
        hook_delivered = bool(ht) and any(_norm(ht) in _norm(t) for t in resp_texts)
    action = (a.get("action_taken") or "").strip()
    anchor = (a.get("anchor") or "").strip()
    out = {
        "parse": "ok",
        "signal": (a.get("signal") or "").strip(),
        "goal_category": (a.get("goal_category") or "").strip(),
        "action_taken": action,
        "anchor": anchor,
        "hook_kind": hk,
        "hook_text": ht,
        "signal_valid": (a.get("signal") or "").strip() in FEEDBACK_SIGNALS,
        "goal_valid": (a.get("goal_category") or "").strip() in GOAL_CATEGORIES,
        "action_valid": action in ACTIONS,
        "action_allowed": action in (caps or []),
        "anchor_valid": anchor in alias_map if anchor else False,
        "hook_kind_valid": hk in HOOK_KINDS,
        "hook_delivered": hook_delivered,
    }
    out["all_valid"] = (out["signal_valid"] and out["goal_valid"] and out["action_valid"]
                        and out["anchor_valid"] and out["hook_kind_valid"])
    return out


def init(assets_root, episode_meta, gateway, sandbox):
    h = v5.init(assets_root, episode_meta, gateway, sandbox)
    h["goal_history"] = []
    h["action_log"] = []
    h["memory_off"] = False
    h["flags"] = _flags()
    return h


def run_turn(h, ctx, gw):
    inp = ctx.get("input") or {}
    clock = (inp.get("clock") or "").strip()
    seq = ctx.get("turn_index") or (h["turn_count"] + 1)
    raw_msgs = [m for m in (inp.get("messages") or []) if isinstance(m, dict)]

    cur = []
    for m in raw_msgs:
        uid = v5._uid_for(h, m.get("role"), m.get("text"), m.get("ts"))
        cur.append({"uid": uid, "role": m.get("role") or "",
                    "text": m.get("text") or "", "ts": m.get("ts") or ""})
    cur_uids = set(c["uid"] for c in cur)
    prior_events = [e for e in h["events"] if e["uid"] not in cur_uids]
    if h.get("memory_off"):
        prior_events = []

    intent_cfg = v5._intent_cfg_from_input(inp)
    gs = _goal_state(intent_cfg, clock)
    _goal_history_update(h, gs, seq, clock)

    parts = []
    seed = {}
    if h["persona"]:
        seed["persona"] = h["persona"]
    kb = {k: v for k, v in (h["initial_kb"] or {}).items() if k != "ongoing_plan"}
    if kb:
        seed["initial_kb"] = kb
    if seed:
        parts.append("【画像/初始事实(种子，可公开使用)】\n"
                     + json.dumps(seed, ensure_ascii=False))

    goal_block = _goal_block(gs)
    if goal_block:
        parts.append(goal_block)
    if h["plans"]:
        parts.append("【已确认/进行中事项(独立于当前目标，勿当新目标自动重启)】\n"
                     + "\n".join(h["plans"]))

    note = (inp.get("note") or "").strip()
    if note:
        parts.append(
            rc.NOTE_BLOCK_HEADER.strip() + "\n「" + note + "」\n"
            "四类分清：①公开事实(位置/日程/共同偏好)可直接带入正文；"
            "②私密情绪/动机只用来调口吻，不原样外发；③拟发草稿未发出前不算既成约定；"
            "④推测/读心不作成对方已确认。缺信息时承认为‘不足’，不臆造。")

    sig = _signal(cur, inp.get("may_decline"))
    caps = _capabilities(sig, gs["effective"])
    if h.get("flags", {}).get("action_layer", True):
        parts.append(_action_block(sig, caps, gs))
    elif gs["effective"]:
        parts.append(v5._goal_block(intent_cfg))

    hist, hist_cu = v5._budget_history(prior_events, v5.MAX_EVENT_HISTORY_CU)
    parts.append("# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + hist)

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

    parts.append(v5._source_rule(cur_aliases))
    images = [m["image"] for m in raw_msgs if m.get("image")]
    _share_kw = ("拍", "照片", "图", "发你", "发过去", "构图", "看看这")
    has_text_share = not images and any(any(k in (c["text"] or "") for k in _share_kw)
                                        for c in cur)
    parts.append(v5._media_line(images, has_text_share))
    if clock:
        parts.append(rc.build_timestamp_prompt(clock).strip())
    parts.append(v5._time_scope_rule())
    parts.append(v5._fact_action_rule())

    prompt = "\n\n".join(parts)
    res = gw.generate(h["system_prompt"], prompt, images=images or None, tag="main")
    reply = res["text"]
    h["turn_count"] = seq

    va = _validate_analysis(reply, alias_map, caps)
    entry = {"turn": seq, "clock": clock, "signal_hint": sig["hint"],
             "goal_effective": gs["effective"], "goal_status": gs["status"],
             "goal_category": va.get("goal_category"), "action": va.get("action_taken"),
             "action_allowed": va.get("action_allowed"),
             "anchor": va.get("anchor"), "anchor_valid": va.get("anchor_valid"),
             "hook_kind": va.get("hook_kind"), "hook_delivered": va.get("hook_delivered")}
    h.setdefault("action_log", []).append(entry)
    if len(h["action_log"]) > MAX_ACTION_LOG:
        del h["action_log"][:-MAX_ACTION_LOG]

    notes = ("v6_goal_context[goal_effective=%d goal_status=%s adjusted=%d closed=%d "
             "signal_hint=%s caps=%s action=%s action_allowed=%s anchor_valid=%s "
             "hook_kind=%s hook_delivered=%s analysis_parse=%s prompt_cu=%d history_cu=%d "
             "events=%d plans=%d images=%d memory_off=%d reply_not_committed=1]"
             % (1 if gs["effective"] else 0, gs["status"], 1 if gs["adjusted"] else 0,
                1 if gs["closed"] else 0, sig["hint"], ",".join(caps),
                va.get("action_taken"), va.get("action_allowed"), va.get("anchor_valid"),
                va.get("hook_kind"), va.get("hook_delivered"), va.get("parse"),
                rc.utf16_len(prompt), hist_cu, len(h["events"]), len(h["plans"]),
                len(images), 1 if h.get("memory_off") else 0))
    return {"reply": reply, "notes": notes, "action_self_assessment": va}


def snapshot(h):
    d = json.loads(v5.snapshot(h).decode("utf-8"))
    d["goal_history"] = h.get("goal_history") or []
    d["action_log"] = h.get("action_log") or []
    d["memory_off"] = bool(h.get("memory_off"))
    d["flags"] = h.get("flags") or _flags()
    return json.dumps(d, ensure_ascii=False).encode("utf-8")


def restore(sandbox, snap):
    h = v5.restore(sandbox, snap)
    d = json.loads(snap.decode("utf-8"))
    h["goal_history"] = d.get("goal_history") or []
    h["action_log"] = d.get("action_log") or []
    h["memory_off"] = bool(d.get("memory_off"))
    h["flags"] = d.get("flags") or _flags()
    return h


def set_memory_access(h, enabled):
    h["memory_off"] = not bool(enabled)
    return None
