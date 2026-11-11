# candidates/action_policy_r1/mechanism/mechanism.py
#
# action_policy_r1 — W03：v6_integrated 全量继承 + 发送合同对齐 + 目标让位（MAX-288 V3）：在同一目标/行动分支上整合证据记忆、状态更新与上下文选择。
#
# 父本：v6_goal_context（闭包引用；动作层/目标作用域/钩子审计全部继承，不复制后漂移）。
# 本候选新增（全在 user 侧，零额外模型调用）：
#   A. 真实事件日志：HER/ME 原文 + 稳定 uid + role + ts；模型回复是草稿，永不写回。
#   B. 证据记忆：每条事实带 fact_id/subject/relation/evidence_uids/evidence_quotes/certainty/
#      status/event_time/valid_from/valid_to/supersedes；初始画像/共同知识按来源归档。
#   C. 状态机：confirmed_seed/confirmed/tentative/proposed/cancelled/completed/reopened/
#      unverified_src/superseded 与历史版本分离；否定缺席不生成反面事实；更正只改指定 fact_id；
#      旧版本留 history，可审计。
#   D. 硬校验：src_ids 必须属于本轮；quote 必须是所引消息的逐字子串（空白归一后）；
#      主体/引文不成立时存 unverified_src，只留审计、不参与投影。
#   E. 上下文选择：当前窗口永不挤出；相关事实/暂定/最近更正按预算投影；
#      静态种子与 seed 块去重（只存不重复投影）；过期/取消/完成/已被替换的事实不投影。
#   F. 快照/恢复：事件、事实、历史版本、目标历史、动作日志、计划编号全部往返；
#      在真实 runner 里用 --stop-after/--resume 跨进程续跑验证。
#
# 消融开关（params.json，改变即新身份）：
#   mechanism_flags.evidence_memory=false → 不投影/不写回记忆（动作层保留）；
#   mechanism_flags.action_layer=false    → 不注入动作层（目标块回退 v5 行为）；
#   两者全 false = v5 基座行为（回归锚）。
#
# 诚实边界：
#   - 引文存在≠语义主体正确；本机制只做成员/引文/主体枚举的硬校验，主体语义错换仍由评审层判。
#   - 不新增模型调用；不把 analysis 当效果分；不声称已证明比 v5/C2/C3 更好。
import hashlib
import importlib.util
import json
import os
import re
import sys
import time

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)
_ROOT = os.path.dirname(_CAND)
_V6G = os.path.join(_ROOT, "v6_goal_context")  # staging trigger: candidates/v6_goal_context


def _load_v6g():
    spec = importlib.util.spec_from_file_location(
        "v6g_mechanism_for_v6_integrated",
        os.path.join(_V6G, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


v6g = _load_v6g()
v5 = v6g.v5
base = v6g.base
rc = base.rc
build_system_prompt = base.build_system_prompt

# ============================================================================
# action_policy_r1 增量（W03，MAX-288 V3）：父本=v6_integrated 全量继承；以下为本分支
# 唯一差异，全部 flag 门控（params.json mechanism_flags），消融可单独关闭。
# 证据来源：P07 v2 pilot 成对评审 evaluation/P07_V2_PAIR_REVIEW_RESULTS_V1.md §6-8
# 与 W02_EXIT_VERDICT.json。
#
# FIX-1 发送合同对齐（action_contract_fix）：
#   实测根因：I2__fbhesitant__v6_integrated__r1 第3轮 candidate action_taken=收尾 且带正文
#   → 公共消费者 classify_reply 判 action_conflict:no_reply_with_body → delivery_failed；
#   该轮 no_reply_fitness 评审 0-1（双序一致最大短板）。机制给"收尾"的规则写"一句轻总结"，
#   而公共协议把 收尾/停 归入不回动作（正文必须为空）——机制侧合同与消费者错位，模型照
#   机制写正文必被拒发。
#   修法：①"收尾/停"规则改为"正文必须为空、安静收住"；②动作块尾部注入显式发送合同；
#   ③may_decline=false 的收尾轮不提供不回动作，收住意图由"承接"承载一条自然短消息。
#
# FIX-2 目标让位（goal_yield_fix）：
#   实测根因：I2__fbhesitant 中 v6 goal_action 评审 1-2 vs REF 3——她的消息带低落/自嘲
#   色彩时 v6 仍按目标推进。关键词表检不出这类信号（W03 第2条：反馈不是关键词表），
#   修法是把"沉重/低落语境目标让位"的裁量权显式交给模型，允许 goal_category=暂缓。
#
# promise/约定收敛机制（v6 唯一双序稳定优势维）不动。
# ============================================================================

_APR_FLAGS = {}
_apr_p = os.path.join(_CAND, "params.json")
try:
    _apr_d = json.load(open(_apr_p, encoding="utf-8"))
    _APR_FLAGS = (_apr_d or {}).get("mechanism_flags") or {}
except Exception:                                      # noqa: BLE001
    _APR_FLAGS = {}

_APR_CLOSING_OLD = "她已在收尾：自然收住；可留一句下次的话头，不硬留、不加问号。"

if _APR_FLAGS.get("action_contract_fix", True):
    v6g.CAP_RULES = dict(v6g.CAP_RULES)
    v6g.CAP_RULES["收尾"] = ("本轮不发任何消息：最终正文必须完全为空，安静收住；"
                             "下次的话头等她再开口时再说。")
    v6g.CAP_RULES["停"] = ("本轮完全静默：不追问、不自证、不推进；最终正文必须完全为空，"
                           "等她主动。")

    _apr_orig_caps = v6g._capabilities

    def _apr_capabilities(sig, goal_effective):
        caps = _apr_orig_caps(sig, goal_effective)
        if sig.get("hint") == "收尾" and not sig.get("may_decline"):
            # 本轮不允许不回：不回动作不提供（空正文会被判 unlicensed no_reply）。
            caps = [a for a in caps if a not in ("收尾", "停")]
        return caps

    _apr_orig_action_block = v6g._action_block

    def _apr_action_block(sig, caps, gs, contract_extra=""):
        block = _apr_orig_action_block(sig, caps, gs, contract_extra)
        if _APR_CLOSING_OLD in block:
            block = block.replace(_APR_CLOSING_OLD,
                "她已在收尾：按下方发送合同执行——允许不回时正文为空安静收住；"
                "不允许不回时给一条自然短消息。")
        nr = [a for a in caps if a in ("收尾", "停")]
        if nr:
            block += ("\n\n【发送合同】若你选 " + "/".join(nr) +
                      "：最终正文（response.recommended）必须完全为空——一个字都不写；"
                      "写了任何正文都会因“不回+正文”冲突被拒发，等于本轮说错话。"
                      "想说话就别选这两个动作，改选上面其他能力。")
        elif sig.get("hint") == "收尾" and not sig.get("may_decline"):
            block += ("\n\n【发送合同】本轮不允许不回：选一条能力给一条自然收住的短消息"
                      "（不追问、不邀约、不硬留），正文不能为空。")
        return block

    v6g._capabilities = _apr_capabilities
    v6g._action_block = _apr_action_block

if _APR_FLAGS.get("goal_yield_fix", True):
    _apr_orig_action_block2 = v6g._action_block

    def _apr_action_block_yield(sig, caps, gs, contract_extra=""):
        block = _apr_orig_action_block2(sig, caps, gs, contract_extra)
        block += ("\n\n【目标让位】她的消息若带低落/疲惫/自嘲/沉重色彩（不论是否命中上方"
                  "显式信号词，以你的语义读取为准）：本轮目标全部让位——只承接她的情绪与"
                  "内容，不开玩笑不转邀约不追确认；把 analysis.goal_category 写 暂缓，"
                  "这不是失败，硬推才是错。")
        return block

    v6g._action_block = _apr_action_block_yield

# ---- FIX-3 钩子承接 + FIX-5 自我状态有据（证据：评审 issues hook_response n=5 落空、
#      factual_safety "时间好安排/只是路过"无前文依据）----
if _APR_FLAGS.get("hook_catch_fix", True) or _APR_FLAGS.get("self_grounding_fix", True):
    _apr_orig_action_block3 = v6g._action_block

    _APR_HOOK = ("\n\n【钩子承接】她最近一条消息里的具体细节（身体状态/手边物件/正在做的"
                 "动作/提到的人和事）是本轮最高优先钩子：先至少接住一个再谈别的；"
                 "一个都接不住就不要硬转话题或硬推目标。")
    _APR_GROUND = ("\n\n【自我状态有据】凡陈述自己的时间/位置/经历/安排（如'我路过''我有空'"
                   "'我去过'），前文必须有据；没有依据就含糊带过或干脆不提，不现编。")

    def _apr_action_block_fix35(sig, caps, gs, contract_extra=""):
        block = _apr_orig_action_block3(sig, caps, gs, contract_extra)
        if _APR_FLAGS.get("hook_catch_fix", True):
            block += _APR_HOOK
        if _APR_FLAGS.get("self_grounding_fix", True):
            block += _APR_GROUND
        return block

    v6g._action_block = _apr_action_block_fix35



BUDGET_FACTS_CU = 500
BUDGET_TENT_CU = 300
BUDGET_UPDATES_CU = 220
RELEVANCE_FLOOR = 0.12
MAX_FACT_HISTORY = 20
MAX_UPDATE_LOG = 100
MAX_ACTION_LOG = 200

SUBJECTS = ("ME", "HER", "THIRD", "SHARED", "PUBLIC", "UNKNOWN")
SUBJECT_LABEL = {"ME": "我", "HER": "她", "THIRD": "第三人", "SHARED": "双方",
                 "PUBLIC": "公开", "UNKNOWN": "主体未定"}
STATUS_ACTIVE = ("confirmed", "confirmed_seed", "reopened")
STATUS_DEAD = ("cancelled", "completed", "superseded")
UPDATE_ACTIONS = ("correct", "cancel", "complete", "reopen")
UPDATE_LABEL = {"correct": "已更正", "cancel": "已取消", "complete": "已完成", "reopen": "已重开"}
# 不作为"事实"入库的 initial_kb 键（动态状态/指导文本另走原有装配）
KB_SKIP = ("ongoing_plan", "lessons", "topic", "recent", "scene_chain",
           "topic_age_hours", "hours_since_last_exchange")


def _flags():
    p = os.path.join(_CAND, "params.json")
    try:
        d = json.load(open(p, encoding="utf-8"))
        f = d.get("mechanism_flags") if isinstance(d, dict) else None
        return {"action_layer": bool((f or {}).get("action_layer", True)),
                "evidence_memory": bool((f or {}).get("evidence_memory", True))}
    except Exception:                                      # noqa: BLE001
        return {"action_layer": True, "evidence_memory": True}


def _norm_ws(s):
    return re.sub(r"\s+", "", s or "")


def _fact_key(text, subject):
    return hashlib.sha256(("%s|%s" % (_norm_ws(text), subject or "UNKNOWN"))
                          .encode("utf-8")).hexdigest()[:12]


def _bigrams(text):
    t = _norm_ws(text).lower()
    if len(t) < 2:
        return {t} if t else set()
    return {t[i:i + 2] for i in range(len(t) - 1)}


def _relevance(text, signal_bigrams):
    bg = _bigrams(text)
    if not bg or not signal_bigrams:
        return 0.0
    return len(bg & signal_bigrams) / len(bg)


def _next_fid(h):
    h["mem"]["fact_seq"] = int(h["mem"].get("fact_seq") or 0) + 1
    return "f_%04d" % h["mem"]["fact_seq"]


def _find_fact(h, fid):
    return (h.get("mem") or {}).get("facts", {}).get(fid)


def _find_by_key(h, text, subject):
    key = _fact_key(text, subject)
    for fid, f in (h.get("mem") or {}).get("facts", {}).items():
        if _fact_key(f.get("text") or "", f.get("subject") or "UNKNOWN") == key:
            return fid, f
    return None, None


def _new_fact(h, text, subject="UNKNOWN", relation="", origin="reported",
              status="confirmed", certainty="confirmed", uids=None, quotes=None,
              ts="", event_time="", plan_status=""):
    fid = _next_fid(h)
    f = {"fact_id": fid, "text": text or "", "subject": subject if subject in SUBJECTS else "UNKNOWN",
         "relation": relation or "", "evidence_uids": list(uids or []),
         "evidence_quotes": list(quotes or []), "certainty": certainty,
         "status": status, "event_time": event_time or "", "valid_from": ts or "",
         "valid_to": "", "supersedes": "", "origin": origin,
         "plan_status": plan_status or "", "ts": ts or "", "history": []}
    (h["mem"]["facts"])[fid] = f
    return f


def _archive_version(f, status, text, ts, reason="", uids=None, quote=""):
    f.setdefault("history", []).append({
        "status": status, "text": text, "valid_to": ts,
        "reason": reason, "src_uids": list(uids or []), "quote": quote})
    if len(f["history"]) > MAX_FACT_HISTORY:
        del f["history"][:-MAX_FACT_HISTORY]


def _seed_facts(h, initial_kb, persona):
    """初始画像/共同知识按来源归档：kbin:<key> / persona:<key>；
    status=confirmed_seed；静态种子只存不投影（seed 块每轮注入，防重复）。
    ongoing_plan 逐条存为 SHARED 事项，计划行带 fact_id，可被 update 引用。"""
    initial_kb = initial_kb or {}
    for key, val in initial_kb.items():
        if key in KB_SKIP or not isinstance(val, str) or not val.strip():
            continue
        subject = ("ME" if key in ("me", "style") else "HER" if key == "her"
                   else "SHARED" if key in ("warmth", "stage") else "PUBLIC")
        _new_fact(h, val.strip(), subject=subject, origin="kbin:%s" % key,
                  status="confirmed_seed", ts="")
    for key in ("me", "her"):
        t = ((persona or {}).get(key) or "").strip()
        if not t:
            continue
        fid, _f = _find_by_key(h, t, "ME" if key == "me" else "HER")
        if not fid:
            _new_fact(h, t, subject="ME" if key == "me" else "HER",
                      origin="persona:%s" % key, status="confirmed_seed", ts="")
    plan = initial_kb.get("ongoing_plan") or []
    for it in plan if isinstance(plan, list) else []:
        if isinstance(it, dict):
            name = (it.get("name") or it.get("item") or "").strip()
            status = (it.get("status") or "").strip()
        elif isinstance(it, str):
            name, status = it.strip(), ""
        else:
            continue
        if not name:
            continue
        f = _new_fact(h, name, subject="SHARED", relation="约定/事项",
                      origin="kbin:ongoing_plan", status="confirmed_seed",
                      plan_status=status or "进行中", ts="")
        h["plan_items"].append({"name": name, "status": status or "进行中",
                                "fid": f["fact_id"]})


def _seed_block(h):
    """seed 块按当前有效事实渲染：被更正的事实用新文本，被取消/完成的从种子块移除；
    lessons 等非事实键保持原样。这样 update 真的改变下一轮可见画像。"""
    seed = {}
    persona = dict(h.get("persona") or {})
    if persona:
        seed["persona"] = persona
    kb = {k: v for k, v in (h.get("initial_kb") or {}).items() if k != "ongoing_plan"}
    for key, val in list(kb.items()):
        fid = h.get("seed_fids", {}).get("kbin:%s" % key)
        f = _find_fact(h, fid) if fid else None
        if f is None:
            continue                        # 未入库的键（lessons 等）保留原值
        if f.get("status") in STATUS_DEAD:
            kb.pop(key, None)
        elif f.get("text") and f.get("text") != (val or "").strip():
            kb[key] = f["text"]
    for key in ("me", "her"):
        fid = h.get("seed_fids", {}).get("persona:%s" % key)
        f = _find_fact(h, fid) if fid else None
        if f is None:
            continue
        if f.get("status") in STATUS_DEAD:
            (seed.get("persona") or {}).pop(key, None)
        elif f.get("text") and f.get("text") != ((persona.get(key) or "").strip()):
            (seed.get("persona") or {})[key] = f["text"]
    if kb:
        seed["initial_kb"] = kb
    return seed


def _plan_lines(h):
    """计划行带 fact_id；被取消/完成的事项从此块移除（改由最近更正块提示，防复活）。"""
    lines = []
    for it in h.get("plan_items") or []:
        f = _find_fact(h, it.get("fid"))
        if f is None or f.get("status") in STATUS_DEAD:
            continue
        st = f.get("plan_status") or it.get("status") or ""
        lines.append("· [%s] %s%s" % (f["fact_id"], f.get("text") or it.get("name"),
                                      ("（%s）" % st) if st else ""))
    return lines


def _resolve_src(src_ids, alias_map, cur_uids):
    if not isinstance(src_ids, list) or not src_ids:
        return None, "empty_src"
    uids = []
    for s in src_ids:
        u = alias_map.get(str(s)) if alias_map else None
        if u is None and isinstance(s, int):
            u = s
        if u is None:
            return None, "unmapped_src"
        if u not in cur_uids:
            return None, "src_not_current"
        uids.append(u)
    return uids, ""


def _quote_supported(quote, uids, cur_texts):
    q = _norm_ws(quote)
    if not q:
        return False
    return any(q in _norm_ws(cur_texts.get(u, "")) for u in uids)


def _add_evidence(f, uids, quote):
    for u in uids or []:
        if u not in f["evidence_uids"]:
            f["evidence_uids"].append(u)
    if quote and quote not in f["evidence_quotes"]:
        f["evidence_quotes"].append(quote)


def _merge_facts(h, analysis, cur_uids, cur_texts, alias_map, clock):
    added = confirmed = unverified = invalid = 0
    for raw in (analysis.get("facts") or []):
        if not isinstance(raw, dict):
            continue
        text = (raw.get("text") or "").strip()
        if not text:
            continue
        subject = (raw.get("subject") or "UNKNOWN").strip()
        if subject not in SUBJECTS:
            subject = "UNKNOWN"
        uids, why = _resolve_src(raw.get("src_ids"), alias_map, cur_uids)
        quote = (raw.get("quote") or "").strip()
        certainty = (raw.get("certainty") or "confirmed").strip()
        if not uids:
            invalid += 1
            continue                      # 来源成员硬校验：越权/空 src 不入库（只计数）
        supported = _quote_supported(quote, uids, cur_texts)
        if not supported:
            unverified += 1
        status = "confirmed" if (supported and certainty == "confirmed"
                                 and subject not in ("UNKNOWN",)) else (
            "tentative" if supported and certainty != "confirmed" else "unverified_src")
        fid, existing = _find_by_key(h, text, subject)
        if existing is None:
            f = _new_fact(h, text, subject=subject, relation=(raw.get("relation") or ""),
                          origin="reported", status=status, certainty=certainty,
                          uids=uids or [], quotes=[quote] if quote else [],
                          ts=clock, event_time=(raw.get("event_time") or ""))
            added += 1
            if status == "confirmed":
                confirmed += 1
        else:
            f = existing
            if supported:
                _add_evidence(f, uids, quote)
                if f.get("status") in ("unverified_src", "tentative", "proposed"):
                    f["status"] = status
                    f["certainty"] = "confirmed" if status == "confirmed" else certainty
                if status == "confirmed":
                    confirmed += 1
    return {"added": added, "confirmed": confirmed, "unverified": unverified,
            "invalid_src": invalid}


def _merge_tentatives(h, analysis, cur_uids, cur_texts, alias_map, clock):
    added = invalid = 0
    for raw in (analysis.get("tentatives") or []):
        if not isinstance(raw, dict):
            continue
        text = (raw.get("text") or "").strip()
        if not text:
            continue
        uids, _why = _resolve_src(raw.get("src_ids"), alias_map, cur_uids)
        quote = (raw.get("quote") or "").strip()
        if not uids:
            invalid += 1
            continue                      # 来源成员硬校验：越权/空 src 不入库
        status = "proposed" if _quote_supported(quote, uids, cur_texts) else "unverified_src"
        fid, existing = _find_by_key(h, text, "UNKNOWN")
        if existing is None:
            _new_fact(h, text, subject="UNKNOWN", origin="reported", status=status,
                      certainty="tentative", uids=uids, quotes=[quote],
                      ts=clock)
            added += 1
        elif status == "proposed" and existing.get("status") == "unverified_src":
            existing["status"] = "proposed"
            _add_evidence(existing, uids, quote)
    return {"added": added, "invalid": invalid}


def _apply_updates(h, analysis, cur_uids, cur_texts, alias_map, clock):
    applied = rejected = 0
    reasons = []
    for raw in (analysis.get("updates") or []):
        if not isinstance(raw, dict):
            continue
        fid = (raw.get("fact_id") or "").strip()
        action = (raw.get("action") or "").strip()
        f = _find_fact(h, fid)
        if f is None:
            rejected += 1
            reasons.append("unknown_fid:%s" % fid)
            continue
        if action not in UPDATE_ACTIONS:
            rejected += 1
            reasons.append("bad_action:%s" % action)
            continue
        uids, why = _resolve_src(raw.get("src_ids"), alias_map, cur_uids)
        quote = (raw.get("quote") or "").strip()
        if not uids or not _quote_supported(quote, uids, cur_texts):
            rejected += 1
            reasons.append("unsupported_update:%s(%s)" % (fid, why or "quote"))
            continue
        new_text = (raw.get("new_text") or "").strip()
        if action == "correct" and not new_text:
            rejected += 1
            reasons.append("correct_without_new_text:%s" % fid)
            continue
        _archive_version(f, f.get("status"), f.get("text"), clock,
                         reason=action, uids=uids, quote=quote)
        if action == "correct":
            f["supersedes"] = "%s@v%d" % (fid, len(f["history"]))
            f["text"] = new_text
            f["status"] = "confirmed"
            f["certainty"] = "confirmed"
            f["valid_from"] = clock
            f["valid_to"] = ""
        elif action == "cancel":
            f["status"] = "cancelled"
            f["valid_to"] = clock
        elif action == "complete":
            f["status"] = "completed"
            f["valid_to"] = clock
        elif action == "reopen":
            f["status"] = "reopened"
            f["valid_to"] = ""
            f["valid_from"] = clock
        _add_evidence(f, uids, quote)
        if action == "cancel" and f.get("origin") == "kbin:ongoing_plan":
            f["plan_status"] = "已取消"
        if action == "complete" and f.get("origin") == "kbin:ongoing_plan":
            f["plan_status"] = "已完成"
        if action == "reopen" and f.get("origin") == "kbin:ongoing_plan":
            f["plan_status"] = "进行中"
        h["mem"]["updates"].append({
            "fact_id": fid, "action": action, "text": f.get("text"),
            "old_text": f["history"][-1]["text"], "ts": clock,
            "src_uids": uids, "quote": quote})
        if len(h["mem"]["updates"]) > MAX_UPDATE_LOG:
            del h["mem"]["updates"][:-MAX_UPDATE_LOG]
        applied += 1
    return {"applied": applied, "rejected": rejected, "reasons": reasons}


def _facts_projection(h, signal_text, budget, now_clock):
    items = []
    for f in h["mem"]["facts"].values():
        if f.get("status") not in STATUS_ACTIVE:
            continue
        if str(f.get("origin", "")).startswith(("kbin:", "persona:")):
            continue                      # seed/计划块已注入，不重复占预算
        if f.get("valid_to") and f["valid_to"] <= (now_clock or ""):
            continue
        items.append(f)
    sig = _bigrams(signal_text)
    t0 = time.time()
    if sig:
        scored = sorted(items, key=lambda f: (-_relevance(f.get("text", ""), sig),
                                             f.get("valid_from") or ""))
    else:
        scored = sorted(items, key=lambda f: (f.get("valid_from") or ""), reverse=True)
    lines, used, hits, filtered = [], 0, 0, 0
    for f in scored:
        rel = _relevance(f.get("text", ""), sig) if sig else 1.0
        if sig and rel < RELEVANCE_FLOOR:
            filtered += 1
            continue
        src = ("来源 " + ",".join("m_src[%d]" % u for u in f.get("evidence_uids") or [])) \
            if f.get("evidence_uids") else "来源 本候选规则"
        when = ("事件@%s" % f["event_time"]) if f.get("event_time") else (
            "@%s" % f["ts"] if f.get("ts") else "")
        line = "· [%s] %s：%s（%s%s）" % (
            f["fact_id"], SUBJECT_LABEL.get(f.get("subject"), "主体未定"),
            f.get("text"), src, (" " + when) if when else "")
        cu = rc.utf16_len(line) + 1
        if used + cu > budget:
            filtered += 1
            continue
        lines.append(line)
        used += cu
        hits += 1
    return "\n".join(lines), used, hits, filtered, round((time.time() - t0) * 1000, 2)


def _tentatives_projection(h, budget):
    items = [f for f in h["mem"]["facts"].values()
             if f.get("status") in ("tentative", "proposed")]
    items.sort(key=lambda f: f.get("ts") or "", reverse=True)
    lines, used = [], 0
    for f in items:
        line = "· [%s] %s（提出于%s；未敲定）" % (
            f["fact_id"], f.get("text"), f.get("ts") or "未知时间")
        cu = rc.utf16_len(line) + 1
        if used + cu > budget:
            break
        lines.append(line)
        used += cu
    return "\n".join(lines), used, len(lines)


def _updates_projection(h, budget):
    items = (h["mem"].get("updates") or [])[-3:]
    lines, used = [], 0
    for u in items:
        label = UPDATE_LABEL.get(u.get("action"), u.get("action"))
        line = "· [%s] %s（依据 %s）" % (
            label, u.get("text"), ",".join("m_src[%d]" % x for x in u.get("src_uids") or []))
        cu = rc.utf16_len(line) + 1
        if used + cu > budget:
            break
        lines.append(line)
        used += cu
    return "\n".join(lines), used, len(lines)


ANALYSIS_EXTRA = (
    "\n记忆写回（仍在上面的 analysis 内，同一次调用）：另带 "
    '"facts":[{"text","subject","relation","quote","src_ids","certainty","event_time"}],'
    '"tentatives":[{"text","quote","src_ids"}],'
    '"updates":[{"fact_id","action","new_text","quote","src_ids"}] 三数组；'
    "facts 只放双方本轮说过、可逐字引用的可核事实/约定；quote 必须逐字来自 src_ids；"
    "tentatives 放未敲定意向；updates 只在本轮真的更正/取消/完成/重开上方带编号的记忆时填，"
    "没有就 []。不编造。")


def init(assets_root, episode_meta, gateway, sandbox):
    h = v6g.init(assets_root, episode_meta, gateway, sandbox)
    h["flags"] = _flags()
    h["mem"] = {"facts": {}, "fact_seq": 0, "updates": []}
    h["plan_items"] = []
    h["seed_fids"] = {}
    meta = episode_meta or {}
    if h["flags"].get("evidence_memory", True):
        _seed_facts(h, meta.get("initial_kb") or {}, meta.get("persona") or {})
        for fid, f in h["mem"]["facts"].items():
            origin = f.get("origin") or ""
            if origin.startswith(("kbin:", "persona:")):
                h["seed_fids"][origin] = fid
    h["action_log"] = h.get("action_log") or []
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
    cur_texts = {c["uid"]: c["text"] for c in cur}
    prior_events = [e for e in h["events"] if e["uid"] not in cur_uids]
    if h.get("memory_off"):
        prior_events = []

    intent_cfg = v5._intent_cfg_from_input(inp)
    gs = v6g._goal_state(intent_cfg, clock)
    v6g._goal_history_update(h, gs, seq, clock)
    mem_on = bool(h.get("flags", {}).get("evidence_memory", True)) and not h.get("memory_off")

    parts = []
    seed = _seed_block(h)
    if seed:
        parts.append("【画像/初始事实(种子，可公开使用)】\n"
                     + json.dumps(seed, ensure_ascii=False))
    goal_block = v6g._goal_block(gs)
    if goal_block:
        parts.append(goal_block)
    if h.get("flags", {}).get("evidence_memory", True):
        plan_lines = _plan_lines(h)
        plan_header = "【已确认/进行中事项(独立于当前目标，带记忆编号；勿当新目标自动重启)】"
    else:
        plan_lines = list(h.get("plans") or [])
        plan_header = "【已确认/进行中事项(独立于当前目标，勿当新目标自动重启)】"
    if plan_lines:
        parts.append(plan_header + "\n" + "\n".join(plan_lines))

    note = (inp.get("note") or "").strip()
    if note:
        parts.append(
            rc.NOTE_BLOCK_HEADER.strip() + "\n「" + note + "」\n"
            "四类分清：①公开事实(位置/日程/共同偏好)可直接带入正文；"
            "②私密情绪/动机只用来调口吻，不原样外发；③拟发草稿未发出前不算既成约定；"
            "④推测/读心不作成对方已确认。缺信息时承认为‘不足’，不臆造。")

    sig = v6g._signal(cur, inp.get("may_decline"))
    caps = v6g._capabilities(sig, gs["effective"])
    if h.get("flags", {}).get("action_layer", True):
        parts.append(v6g._action_block(sig, caps, gs, ANALYSIS_EXTRA if mem_on else ""))
    elif gs["effective"]:
        parts.append(v5._goal_block(intent_cfg))

    f_body = t_body = u_body = ""
    f_cu = t_cu = u_cu = 0
    f_hits = f_filtered = 0
    t_n = u_n = 0
    rel_ms = 0.0
    if mem_on:
        signal_text = " ".join([c["text"] for c in cur]) + " " + (gs.get("text") or "")
        f_body, f_cu, f_hits, f_filtered, rel_ms = _facts_projection(
            h, signal_text, BUDGET_FACTS_CU, clock)
        t_body, t_cu, t_n = _tentatives_projection(h, BUDGET_TENT_CU)
        u_body, u_cu, u_n = _updates_projection(h, BUDGET_UPDATES_CU)
        if f_body:
            parts.append("【相关已确认事实(带编号与出处；可用 fact_id 做更正/取消/完成/重开)】\n" + f_body)
        if t_body:
            parts.append("【暂定事项(未敲定，别当已约定催办)】\n" + t_body)
        if u_body:
            parts.append("【最近状态变更(旧版本勿再当现行；取消/完成的不复活)】\n" + u_body)

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

    analysis = None
    obj = base._extract_json(reply)
    if isinstance(obj, dict) and isinstance(obj.get("analysis"), dict):
        analysis = obj["analysis"]
    merged = {"added": 0, "confirmed": 0, "unverified": 0, "invalid_src": 0}
    tent = {"added": 0, "invalid": 0}
    upd = {"applied": 0, "rejected": 0, "reasons": []}
    if mem_on and analysis is not None:
        merged = _merge_facts(h, analysis, cur_uids, cur_texts, alias_map, clock)
        tent = _merge_tentatives(h, analysis, cur_uids, cur_texts, alias_map, clock)
        upd = _apply_updates(h, analysis, cur_uids, cur_texts, alias_map, clock)

    va = v6g._validate_analysis(reply, alias_map, caps) if analysis is not None else {
        "parse": "reply_not_json_or_analysis_missing"}
    entry = {"turn": seq, "clock": clock, "signal_hint": sig["hint"],
             "goal_effective": gs["effective"], "goal_status": gs["status"],
             "goal_category": va.get("goal_category"), "action": va.get("action_taken"),
             "action_allowed": va.get("action_allowed"),
             "anchor": va.get("anchor"), "anchor_valid": va.get("anchor_valid"),
             "hook_kind": va.get("hook_kind"), "hook_delivered": va.get("hook_delivered"),
             "facts_added": merged.get("added"), "updates_applied": upd.get("applied")}
    h.setdefault("action_log", []).append(entry)
    if len(h["action_log"]) > MAX_ACTION_LOG:
        del h["action_log"][:-MAX_ACTION_LOG]

    notes = ("v6_integrated[goal_effective=%d goal_status=%s signal_hint=%s caps=%s "
             "action=%s action_allowed=%s anchor_valid=%s hook_kind=%s hook_delivered=%s "
             "analysis_parse=%s mem_on=%d facts_store=%d tent_store=%d facts_proj=%d/%dCU "
             "filtered=%d tent_proj=%d/%dCU updates_proj=%d/%dCU added_f=%d added_t=%d "
             "invalid_src=%d unsupported_src=%d updates_applied=%d updates_rejected=%s "
             "prompt_cu=%d history_cu=%d events=%d plans=%d images=%d rel_ms=%s "
             "current_never_squeezed=1 reply_not_committed=1]"
             % (1 if gs["effective"] else 0, gs["status"], sig["hint"], ",".join(caps),
                va.get("action_taken"), va.get("action_allowed"), va.get("anchor_valid"),
                va.get("hook_kind"), va.get("hook_delivered"), va.get("parse"),
                1 if mem_on else 0, len(h["mem"]["facts"]),
                sum(1 for f in h["mem"]["facts"].values()
                    if f.get("status") in ("tentative", "proposed")),
                f_hits, f_cu, f_filtered, t_n, t_cu, u_n, u_cu,
                merged.get("added"), tent.get("added"), merged.get("invalid_src"),
                merged.get("unverified"), upd.get("applied"),
                ",".join(upd.get("reasons") or []),
                rc.utf16_len(prompt), hist_cu, len(h["events"]),
                len(plan_lines), len(images), rel_ms))
    return {"reply": reply, "notes": notes, "action_self_assessment": va,
            "memory_projection": {"facts_hits": f_hits, "facts_cu": f_cu,
                                  "facts_filtered": f_filtered,
                                  "updates_applied": upd.get("applied"),
                                  "store_facts": len(h["mem"]["facts"])}}


def snapshot(h):
    d = json.loads(v6g.snapshot(h).decode("utf-8"))
    d["mem"] = h.get("mem") or {"facts": {}, "fact_seq": 0, "updates": []}
    d["plan_items"] = h.get("plan_items") or []
    d["seed_fids"] = h.get("seed_fids") or {}
    return json.dumps(d, ensure_ascii=False).encode("utf-8")


def restore(sandbox, snap):
    h = v6g.restore(sandbox, snap)
    d = json.loads(snap.decode("utf-8"))
    h["mem"] = d.get("mem") or {"facts": {}, "fact_seq": 0, "updates": []}
    h["plan_items"] = d.get("plan_items") or []
    h["seed_fids"] = d.get("seed_fids") or {}
    return h


def set_memory_access(h, enabled):
    h["memory_off"] = not bool(enabled)
    return None


# ---- FIX-4 交付失败修复意识（证据：评审 misunderstanding_repair n=4 无修复；
#      环境 v2.1 已把 delivery_failed 事件放进 ctx.input.own_events；
#      此处在 run_turn 定义之后包装：渲染成系统事件注释，模型才知道自己上轮哑了）----
if _APR_FLAGS.get("failure_repair_fix", True):
    _apr_parent_run_turn = run_turn

    def run_turn(h, ctx, gw):                      # noqa: F811 包装本模块 run_turn
        inp = (ctx or {}).get("input") or {}
        oev = inp.get("own_events") or []
        if oev:
            ctx = dict(ctx)
            inp2 = dict(inp)
            ev_txt = "；".join(
                "第%s轮你的候选未发出（%s），对方没有收到"
                % (e.get("turn"), e.get("reason") or "交付失败")
                for e in oev)
            note0 = (inp2.get("note") or "")
            sep = chr(10) if note0 else ""
            inp2["note"] = (note0 + sep
                            + "【系统事件·非聊天】" + ev_txt
                            + "。可自然修复（轻描淡写补上一句）或直接继续话题，"
                              "不假装已发送；是否修复看你上轮想说的内容分量。")
            ctx["input"] = inp2
        return _apr_parent_run_turn(h, ctx, gw)
