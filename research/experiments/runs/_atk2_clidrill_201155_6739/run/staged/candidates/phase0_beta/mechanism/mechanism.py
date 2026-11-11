# candidates/phase0_beta — Harness 候选 β（Phase 0 验收 V1 之二）
# 架构：文件型 KV 存储（sandbox 内 jsonl/json 文件）+ 关键词检索式记忆注入 + 两阶段网关编排
#（记忆管理调用 + 主回复调用， demonstrate 多阶段推理 = 审计能力 3）。
# 记忆策略：检索式——每轮只把与当前窗口 LCS≥2 的历史事实注入 scene_chain（相关则记，无关则隐），
# 预期通过 stale 抑制探针；召回探针依赖检索命中。
# 存储形态：磁盘文件；快照 = 文件字节拼接，restore 在 sandbox 重建文件。

import json
import os
import sys

_ARENA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "arena")
sys.path.insert(0, _ARENA)
import lb_assembly          # noqa: E402
import protocol_public            # noqa: E402

FACTS_FILE = "kv_facts.jsonl"
ONGOING_FILE = "kv_ongoing.json"
RETRIEVE_TOPK = 6
LCS_MIN = 2


# ---------- 文件型 KV 存储 ----------

def _facts_path(h):
    return os.path.join(h["sandbox"], FACTS_FILE)


def _ongoing_path(h):
    return os.path.join(h["sandbox"], ONGOING_FILE)


def _load_facts(h):
    p = _facts_path(h)
    if not os.path.exists(p):
        return []
    out = []
    with open(p, "r", encoding="utf-8") as f:
        for ln in f:
            ln = ln.strip()
            if not ln:
                continue
            try:
                out.append(json.loads(ln))
            except json.JSONDecodeError:
                continue   # 崩溃残留的半行：跳过不致命（Phase 0.5 杀进程实测教训）
    return out


def _append_fact(h, fact):
    with open(_facts_path(h), "a", encoding="utf-8") as f:
        f.write(json.dumps(fact, ensure_ascii=False) + "\n")


def _load_ongoing(h):
    p = _ongoing_path(h)
    if not os.path.exists(p):
        return []
    try:
        with open(p, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, ValueError):
        return []   # 强杀部分写入的残缺文件：按空恢复（原子写已把窗口缩到最小）


def _save_ongoing(h, ongoing):
    # 原子写：先落 tmp 再 os.replace，杜绝"写一半被杀"的永久残缺
    tmp = _ongoing_path(h) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(ongoing, f, ensure_ascii=False)
    os.replace(tmp, _ongoing_path(h))


# ---------- 检索（确定性：最长公共子串 ≥ LCS_MIN 视为相关） ----------

def _lcs_len(a, b):
    if not a or not b:
        return 0
    prev = [0] * (len(b) + 1)
    best = 0
    for i in range(1, len(a) + 1):
        cur = [0] * (len(b) + 1)
        ai = a[i - 1]
        for j in range(1, len(b) + 1):
            if ai == b[j - 1]:
                cur[j] = prev[j - 1] + 1
                if cur[j] > best:
                    best = cur[j]
        prev = cur
    return best


def _retrieve(window_text, facts, topk=RETRIEVE_TOPK):
    scored = []
    for f in facts:
        s = _lcs_len(window_text, f["text"])
        if s >= LCS_MIN:
            scored.append((s, f))
    scored.sort(key=lambda x: (-x[0], x[1].get("clock", "")))
    return [f for _, f in scored[:topk]]


# ---------- v2 生命周期 ----------

def init(assets_root, episode_meta, gateway, sandbox):
    return {"assets_root": assets_root, "sandbox": sandbox,
            "turn": 0, "topic": "", "topic_age": 99}


def run_turn(h, ctx, gw):
    h["turn"] += 1
    ai = ctx["input"]
    window = "".join(m.get("text", "") for m in ai.get("messages", [])
                     if isinstance(m, dict))
    facts = _load_facts(h)
    ongoing = _load_ongoing(h)
    retrieved = _retrieve(window, facts)

    # 阶段 1：记忆管理调用（多阶段编排演示；CallLog tag=memory）
    gw.generate("你是记忆管理器，负责决定哪些长期记忆需要注入本轮对话。",
                "当前对话窗口：\n" + window
                + "\n\n长期记忆条目数：%d\n检索命中：%d" % (len(facts), len(retrieved)),
                tag="memory")

    # 阶段 2：主回复调用（检索命中经 scene_chain 通道注入，复用装配管线）
    kb = {"scene_chain": "\n".join("- [%s] %s" % (f["clock"], f["text"])
                                   for f in retrieved),
          "ongoing_plan": "\n".join("%s | %s" % (o["item"], o["status"])
                                    for o in ongoing),
          "topic": h.get("topic", ""), "topic_age_hours": h.get("topic_age", 99)}
    ai2 = dict(ai)
    ai2["kb"] = kb
    assets_root = ctx.get("assets_root") or h["assets_root"]   # resume 后以 ctx 为准
    assembled = lb_assembly.build_reply_prompts(assets_root, ai2)
    res = gw.generate(assembled["system"], assembled["user"], tag="main")

    # 写回：本轮 analysis 的事实/计划入文件存储
    parsed = protocol_public.extract_json_block(res["text"])
    if parsed and isinstance(parsed.get("analysis"), dict):
        ana = parsed["analysis"]
        clock = ai.get("clock", "")
        for f in ana.get("scene_facts") or []:
            t = (f.get("text") or "").strip() if isinstance(f, dict) else ""
            if t:
                _append_fact(h, {"clock": clock, "text": t})
        changed = False
        for o in ana.get("ongoing") or []:
            item = (o.get("item") or "").strip() if isinstance(o, dict) else ""
            if item:
                ongoing = [x for x in ongoing if x["item"] != item]
                ongoing.append({"item": item, "status": o.get("status", "")})
                changed = True
        if changed:
            _save_ongoing(h, ongoing)
        label = (ana.get("topic_label") or "").strip()
        if label:
            h["topic"] = label
            h["topic_age"] = 0
    h["topic_age"] = min(h.get("topic_age", 99) + 3, 99)

    return {"reply": res["text"],
            "notes": "beta: file-KV storage, LCS retrieval, 2-stage orchestration",
            "id_map": assembled["id_map"]}


def snapshot(h):
    parts = []
    for fn in (FACTS_FILE, ONGOING_FILE):
        p = os.path.join(h["sandbox"], fn)
        data = open(p, "rb").read() if os.path.exists(p) else b""
        parts.append(fn.encode("utf-8") + b"\n" + data + b"\n--SNAP--\n")
    meta = json.dumps({"turn": h["turn"], "topic": h["topic"],
                       "topic_age": h["topic_age"]}, ensure_ascii=False).encode("utf-8")
    return b"".join(parts) + b"META\n" + meta


def restore(sandbox, snap):
    h = {"assets_root": None, "sandbox": sandbox, "turn": 0,
         "topic": "", "topic_age": 99}
    chunks = snap.split(b"META\n")
    body, meta = chunks[0], chunks[1]
    for block in body.split(b"\n--SNAP--\n"):
        if not block.strip():
            continue
        nl = block.index(b"\n")
        fn = block[:nl].decode("utf-8")
        data = block[nl + 1:]
        with open(os.path.join(sandbox, fn), "wb") as f:
            f.write(data)
    m = json.loads(meta.decode("utf-8"))
    h.update(m)
    return h
