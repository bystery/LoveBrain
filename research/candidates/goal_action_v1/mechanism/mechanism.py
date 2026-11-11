# candidates/goal_action_v1/mechanism/mechanism.py
#
# goal_action_v1 — C2 目标驱动聊天动作（设计稿 C2_C3_CANDIDATE_DESIGN_V1.md §C2）。
# 基座：v5_purpose_aligned 闭包（不复制后漂移）；system 资产逐字节继承 v5
# （system 前缀字节稳定=缓存锚；与 v5 的全部差异在 user 侧目标-动作层，单独可归因）。
#
# 机制：GoalActionSelector = 目标类别 × 对方反馈信号 → 本轮动作类型（条件化能力段）。
# 信号/类别由模型同一次调用内自评（本地零额外调用），输出进 analysis 三字段
# （feedback_signal/goal_category/action_taken），消费端（本文件 notes）校验枚举。
# 信号=不明 ⇒ 动作保守承接（"切"不自动=降温，P003 教训）。
import importlib.util
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)                       # candidates/goal_action_v1
_ROOT = os.path.dirname(_CAND)                       # candidates/
_V5 = os.path.join(_ROOT, "v5_purpose_aligned")  # staging trigger: candidates/v5_purpose_aligned


def _load_v5():
    spec = importlib.util.spec_from_file_location(
        "v5_mechanism_for_goal_action",
        os.path.join(_V5, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


v5 = _load_v5()
base = v5.base                                      # reference_product_v1（闭包链）
rc = base.rc
build_system_prompt = base.build_system_prompt      # 用 v5 同一资产骨架（资产=继承的四份）

FEEDBACK_SIGNALS = ("接纳", "试探", "降温", "拒绝", "中性", "不明")
GOAL_CATEGORIES = ("拉近", "了解", "推进见面", "暂缓", "收线", "无目标")
ACTIONS = ("承接", "反抛", "自我表达", "转场", "深聊", "邀约", "收尾", "停")

# 目标×信号 → 建议动作（通用矩阵，无领域词；模型可依语境在建议集内选，不明→保守承接）
MENU = {
    "拉近":     {"接纳": ("反抛", "自我表达"), "试探": ("反抛",), "中性": ("承接",),
                 "降温": ("承接",), "拒绝": ("停", "收尾"), "不明": ("承接",)},
    "了解":     {"接纳": ("深聊",), "试探": ("反抛",), "中性": ("深聊",),
                 "降温": ("承接",), "拒绝": ("停",), "不明": ("承接",)},
    "推进见面": {"接纳": ("邀约",), "试探": ("邀约",), "中性": ("邀约",),
                 "降温": ("转场",), "拒绝": ("收尾",), "不明": ("承接",)},
    "暂缓":     {s: ("承接", "收尾") for s in FEEDBACK_SIGNALS},
    "收线":     {"接纳": ("收尾",), "试探": ("收尾",), "中性": ("收尾",),
                 "降温": ("收尾",), "拒绝": ("停",), "不明": ("承接",)},
    "无目标":   {s: ("承接", "转场") for s in FEEDBACK_SIGNALS},
}

# 能力段（每类 ≤3 行，条件化启用；张力空间与禁逐字话术写在规则内）
CAPABILITY_SEGMENTS = {
    "承接":     "承接：先接住她这句话的实际内容与情绪，可轻问一句延伸；不急着换话题。",
    "反抛":     "反抛：以调侃/回敬轻推回一个话头，允许张力与留白；不逐字抄任何模板，不人身攻击。",
    "自我表达": "自我表达：说一点你自己真实的看法/经历（仅真有依据的），可与她的点形成对照。",
    "转场":     "转场：借当前话茬自然引到相邻话题，给一句钩子让她好接；不硬切。",
    "深聊":     "深聊：就她刚给的内容往深问一层（感受/原因/细节任一），一次只推进一层。",
    "邀约":     "邀约：给具体、轻量、可选项式的提议（时间/形式给选择），给她拒绝空间；不冒称已约成。",
    "收尾":     "收尾：自然收住本轮（一句轻总结/下次的话头），不硬留也不突然消失。",
    "停":       "停：本轮少说甚至不说，尊重边界；可以一句轻回应后停住，不追问不自证。",
}

ACTION_RULE = ("【目标-动作自评与选择】（同一次回复内完成，不另起调用）\n"
               "第一步 自评她最近 1-2 条消息的反馈信号：接纳/试探/降温/拒绝/中性/不明——"
               "只按语义与语气判断（如玩笑降温≠拒绝，'切/哈哈'等不明信号一律判'不明'，不许脑补）；"
               "第二步 若上方有【当前目标】，自评其类别：拉近/了解/推进见面/暂缓/收线；无目标=无目标；\n"
               "第三步 按下方矩阵选本轮动作类型（可在建议集内依语境选；信号'不明'一律保守承接；"
               "'拒绝'信号下禁选邀约；'停'恒可选），并只启用所选动作的能力规则行：\n")

MATRIX_LINES = []
for _g in ("拉近", "了解", "推进见面", "暂缓", "收线", "无目标"):
    _cells = []
    for _s in FEEDBACK_SIGNALS:
        _cells.append("%s→%s" % (_s, "/".join(MENU[_g][_s]) if len(MENU[_g][_s]) > 1 else MENU[_g][_s][0]))
    MATRIX_LINES.append("%s：%s" % (_g, "；".join(_cells)))
MATRIX_BLOCK = "\n".join(MATRIX_LINES)

CAPABILITIES_BLOCK = "\n".join("· " + CAPABILITY_SEGMENTS[a] for a in ACTIONS)

OUTPUT_CONTRACT = ("自评与所选动作写进 analysis（三字段）："
                   '"analysis":{"feedback_signal":"接纳|试探|降温|拒绝|中性|不明",'
                   '"goal_category":"拉近|了解|推进见面|暂缓|收线|无目标",'
                   '"action_taken":"承接|反抛|自我表达|转场|深聊|邀约|收尾|停"}；'
                   "正文四风格与方向仍按原格式输出；能力规则只约束风格与动作方向，不改输出结构。")


def _validate_analysis(reply):
    """消费端校验：analysis 三字段枚举合法才算机制生效；不合法/缺失记 notes（不改判 reply）。"""
    try:
        obj = json.loads(reply)
    except Exception:                                     # noqa: BLE001
        return {"parse": "reply_not_json"}
    a = obj.get("analysis") if isinstance(obj, dict) else None
    if not isinstance(a, dict):
        return {"parse": "analysis_missing_or_not_dict"}
    out = {"parse": "ok",
           "feedback_signal": a.get("feedback_signal"),
           "goal_category": a.get("goal_category"),
           "action_taken": a.get("action_taken"),
           "signal_valid": a.get("feedback_signal") in FEEDBACK_SIGNALS,
           "goal_valid": a.get("goal_category") in GOAL_CATEGORIES,
           "action_valid": a.get("action_taken") in ACTIONS}
    out["all_valid"] = (out["signal_valid"] and out["goal_valid"] and out["action_valid"])
    return out


def run_turn(h, ctx, gw):
    inp = ctx.get("input") or {}
    seq = ctx.get("turn_index") or (h["turn_count"] + 1)

    # v5 的整套装配（种子/目标/事项/note/历史/当前窗/来源/媒体/时间/事实-行动边界）
    # 通过复用 v5.run_turn 不可注入——此处按同一零件组装，但插入目标-动作层。
    # 零件全部来自 v5 模块（闭包共享，未复制漂移）。
    intent_cfg = v5._intent_cfg_from_input(inp)
    goal_block = v5._goal_block(intent_cfg)
    clock = (inp.get("clock") or "").strip()
    raw_msgs = [m for m in (inp.get("messages") or []) if isinstance(m, dict)]

    cur = []
    for m in raw_msgs:
        uid = v5._uid_for(h, m.get("role"), m.get("text"), m.get("ts"))
        cur.append({"uid": uid, "role": m.get("role") or "",
                    "text": m.get("text") or "", "ts": m.get("ts") or ""})
    cur_uids = set(c["uid"] for c in cur)
    prior_events = [e for e in h["events"] if e["uid"] not in cur_uids]

    parts = []
    seed = {}
    if h["persona"]:
        seed["persona"] = h["persona"]
    kb = {k: v for k, v in (h["initial_kb"] or {}).items() if k != "ongoing_plan"}
    if kb:
        seed["initial_kb"] = kb
    if seed:
        parts.append("【画像/初始事实(种子，可公开使用)】\n" + json.dumps(seed, ensure_ascii=False))

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
            "④推测/读心不作成对方已确认。缺信息时承认为'不足'，不臆造。")

    # ── C2 新增层：目标-动作自评与选择（在历史/当前窗之前给方向，正文仍受全部边界约束）──
    parts.append(ACTION_RULE + MATRIX_BLOCK + "\n能力规则（只启用你选的那一行）：\n"
                 + CAPABILITIES_BLOCK + "\n" + OUTPUT_CONTRACT)

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
    va = _validate_analysis(reply)
    notes = ("goal_action_v1[goal_active=%d va=%s prompt_cu=%d history_cu=%d images=%d "
             "action_layer=1 fallback_conservative_on_unknown=1 "
             "current_ids=%s reply_not_committed=1]"
             % (1 if rc.is_intent_effective(intent_cfg) else 0,
                json.dumps(va, ensure_ascii=False), rc.utf16_len(prompt), hist_cu,
                len(images), [c["uid"] for c in cur]))
    return {"reply": reply, "notes": notes,
            "action_self_assessment": va}


def init(assets_root, episode_meta, gateway, sandbox):
    # 与 v5.init 相同的状态形状（闭包复用 v5 的装配零件需要同一结构）
    return v5.init(assets_root, episode_meta, gateway, sandbox)


def snapshot(h):
    return v5.snapshot(h)


def restore(sandbox, snap):
    return v5.restore(sandbox, snap)
