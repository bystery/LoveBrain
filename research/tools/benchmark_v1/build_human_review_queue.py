# -*- coding: utf-8 -*-
"""Benchmark V1 · 人工审核锚点包生成（指导书 §11）。

从 wave4_dev 数据集（+旧暴露题补位）与 pilot 试跑输出，确定性生成 40 个人工
审核锚点包：每锚点 = 聊天上下文 + 意图 + 关系阶段 + 预期能力 + Gold 允许行为 +
关键事实来源 + DIRECT/CURRENT 两臂回复并排。人工判定一律 PENDING_USER，
工具不代填（同 make_blind_review.py 的"Worker 不代投"纪律）。

确定性（禁止随机默认）：
- 唯一选取公式 = int(i*(n+1)/(k+1)+0.5)（与 make_blind_review.py 同风格），
  并列取靠前；全部遍历用升序排序，无任何随机源；
- 锚点配额（§11）：T02×12、T03×8、T04×6、T01×4、T05×4、T07×2、T08×2、
  T09×1、T10×1，共 40；
- 候选池 = wave4_dev 全部 38 篇（exposure.json 的 primary_topic 分组，EID 升序）；
  专题组内先用步长公式选出 u=min(|pool|,配额) 篇（位次=int(i*(n+1)/(u+1)+0.5)），
  每篇先给 1 个锚点，余量按同一次序追加第 2 个锚点（单篇上限=--max-per-episode，默认 2）；
- 每篇锚点轮位：单锚=int((T+1)/2+0.5)（中位）；双锚首位=int((T+1)/3+0.5)；
  **同篇第 2 锚点=距第 1 锚点最远的轮位（并列取靠后）**——即 §11"相距最远的
  另一轮位"。数据集没有机器可读的轮级 topic 标签（primary_topic 是篇级字段），
  "另一专题"在工具中落实为"同一篇内相距最远的另一轮位"，此解释如实登记
  于此，供审计复核；
- 配额不足时从旧暴露题补足：T02 从 E032/E039/E042 补（**只读其 episode.json +
  reference.json**，不读 B 线、不读 GOLD_SIDECAR、不读其余任何件），按同规则选取；
- T09 配额 1：由同一公式落在长程篇 E338（E337/E338 组内位次=int(3/2+0.5)=2），
  轮位=int(33/2+0.5)=17；
- T10 配额 1：取孪生组 T10-G1 的变量轮——反事实篇（exposure.twin_role=
  counterfactual）与原始篇逐轮比对 input，首个分叉轮位即变量轮（E333/E334
  实测=T04，与 exposure.variable_changed 的"T04 轮位"声明一致）。

两臂文本：--run-dir 给定时读 <arm>/<EID>/<turn_dir>/，优先 record.json 的
primary_text（live 格式），否则 reply_raw.txt 经 normalize_output（normalize_v1，
与 make_blind_review/aggregate 同源）取首选；只透传文本，不做质量判断。
--run-dir 缺省或该位无输出时显示"（待 live 输出）"。

输出：<out-dir>/HUMAN_REVIEW_QUEUE.md（40 节）+ HUMAN_REVIEW_QUEUE.json
（机读版）。同输入两次运行输出逐字节一致（仅 generated_at 时间戳除外）。

隔离纪律：只读 wave4_dev 五件套+exposure、E032/E039/E042 的 episode+reference、
--run-dir 输出；不读 logs/、candidates/、B 线正文、GOLD_SIDECAR。
标准库 only；python -I 可跑（normalize_output 为同目录冻结模块，经
__file__ 显式加入 sys.path，不依赖 cwd）。
"""
import argparse
import json
import os
import sys
import time

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)
import normalize_output  # noqa: E402  冻结的 normalize_v1（唯一首选抽取规则）

REPO_ROOT = os.path.dirname(os.path.dirname(_HERE))          # tools/benchmark_v1 -> repo
WAVE4_DIR = os.path.join(REPO_ROOT, "benchmark", "stories_v2", "wave4_dev")
DEFAULT_OUT_DIR = os.path.join(REPO_ROOT, "refs", "benchmark_v1_20261107", "phase2")

# §11 配额（顺序=锚点编号顺序）
TOPIC_QUOTA = [
    ("T02", 12), ("T03", 8), ("T04", 6), ("T01", 4), ("T05", 4),
    ("T07", 2), ("T08", 2), ("T09", 1), ("T10", 1),
]
TOPIC_LABELS = {
    "T01": "日常聊天/自然接话/幽默",
    "T02": "调情/暧昧/反撩/拉扯",
    "T03": "深聊/价值观/思想交流",
    "T04": "主动推进/邀约/关系发展",
    "T05": "情绪理解/关心/安慰",
    "T06": "用户意图/口吻/改写/多候选",
    "T07": "事实/人物归属/不确定性",
    "T08": "记忆/知识库/相关信息提取",
    "T09": "长程聊天/状态变化/纠错取消",
    "T10": "反事实/边界/抗干扰/状态污染",
}
ARMS = ("DIRECT", "CURRENT")
T10_TWIN_GROUP = "T10-G1"
# 旧暴露题补位池（T02）：只允许读 episode.json + reference.json 两个文件
OLD_T02_POOL = [
    ("E032", os.path.join(REPO_ROOT, "benchmark", "stories_v2", "stage2", "E032")),
    ("E039", os.path.join(REPO_ROOT, "benchmark", "stories_v2", "stage3", "E039")),
    ("E042", os.path.join(REPO_ROOT, "benchmark", "stories_v2", "stage3", "E042")),
]
OLD_ALLOWED_FILES = ("episode.json", "reference.json")
T02_PROBE_LINE = "本锚点检验：这是调情，还是礼貌聊天？（对照行为锚 0/1/2）"
PENDING_TEXT = "（待 live 输出）"


def load_json(path, what):
    if not os.path.isfile(path):
        raise SystemExit("[build_human_review_queue] 缺少 %s: %s" % (what, path))
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def stride_positions(n, u):
    """固定步长：n 个里等间距取 u 个位次（1-based），与 make_blind_review.py 同公式。"""
    if u <= 0 or n <= 0:
        return []
    u = min(u, n)
    return [min(max(int(i * (n + 1) / (u + 1) + 0.5), 1), n) for i in range(1, u + 1)]


def turn_positions(n_turns, n_anchors):
    """单篇 m 个锚点的轮位：单锚=中位；双锚首位按步长公式、第 2 锚=距首位最远（并列取靠后）。"""
    if n_turns <= 0 or n_anchors <= 0:
        return []
    if n_anchors == 1:
        return [min(max(int((n_turns + 1) / 2 + 0.5), 1), n_turns)]
    t1 = min(max(int((n_turns + 1) / 3 + 0.5), 1), n_turns)
    t2 = n_turns if (n_turns - t1) >= (t1 - 1) else 1
    pos = [t1] if t2 == t1 else sorted([t1, t2])
    # m>2（仅当显式调大 --max-per-episode）：其余位次按步长公式补齐并去重
    for j in range(3, n_anchors + 1):
        cand = min(max(int(j * (n_turns + 1) / (n_anchors + 1) + 0.5), 1), n_turns)
        if cand not in pos:
            pos.append(cand)
    return sorted(pos)[:n_anchors]


def distribute_capacity(pool, room, need):
    """在池（EID 升序、各篇剩余容量 room）中按步长轮转分配 need 个锚点，尽量多用篇。

    返回 [(eid, 本轮分得锚点数)]，选取次序=步长位次。
    """
    order = []
    if not pool or need <= 0:
        return order
    u = min(len(pool), need)
    sel = [pool[p - 1] for p in stride_positions(len(pool), u)]
    room_map = dict(zip(pool, room))
    order = [(e, 1) for e in sel]
    remaining = need - u
    for i in range(len(sel)):
        if remaining <= 0:
            break
        e, cur = order[i]
        add = min(room_map.get(e, 0) - cur, remaining)
        if add > 0:
            order[i] = (e, cur + add)
            remaining -= add
    return order


def load_episode_pair(eid, root, allowed):
    """读一篇 episode+reference。旧暴露题仅放行白名单两件（隔离纪律）。"""
    ep_path = os.path.join(root, "episode.json")
    ref_path = os.path.join(root, "reference.json")
    if allowed is not None:
        for p in (ep_path, ref_path):
            if os.path.basename(p) not in allowed:
                raise SystemExit("[build_human_review_queue] 白名单外文件请求: %s" % p)
    return load_json(ep_path, eid + " episode.json"), load_json(ref_path, eid + " reference.json")


def load_wave4_pool():
    """wave4_dev 全部篇 -> ({topic: [eid 升序]}, {eid: exposure})。"""
    if not os.path.isdir(WAVE4_DIR):
        raise SystemExit("[build_human_review_queue] 缺少 wave4_dev 目录: %s" % WAVE4_DIR)
    pool, meta = {}, {}
    for eid in sorted(os.listdir(WAVE4_DIR)):
        exp_path = os.path.join(WAVE4_DIR, eid, "exposure.json")
        ep_path = os.path.join(WAVE4_DIR, eid, "episode.json")
        if not (os.path.isfile(exp_path) and os.path.isfile(ep_path)):
            continue
        exp = load_json(exp_path, "exposure.json")
        topic = exp.get("primary_topic")
        if not topic:
            continue
        pool.setdefault(topic, []).append(eid)
        meta[eid] = exp
    return pool, meta


def find_twin_variable_anchor(wave4_meta):
    """T10：孪生组 T10-G1 的变量轮 = 反事实篇 vs 原始篇首个 input 分叉轮。"""
    pair = {}
    for eid, exp in wave4_meta.items():
        if exp.get("twin_group") == T10_TWIN_GROUP:
            pair[exp.get("twin_role") or "unknown"] = eid
    cf, orig = pair.get("counterfactual"), pair.get("original")
    if not (cf and orig):
        return None
    ep_cf = load_json(os.path.join(WAVE4_DIR, cf, "episode.json"), cf + " episode.json")
    ep_or = load_json(os.path.join(WAVE4_DIR, orig, "episode.json"), orig + " episode.json")
    turns_cf, turns_or = ep_cf.get("turns") or [], ep_or.get("turns") or []
    for i in range(min(len(turns_cf), len(turns_or))):
        a = json.dumps(turns_cf[i].get("input") or {}, sort_keys=True, ensure_ascii=False)
        b = json.dumps(turns_or[i].get("input") or {}, sort_keys=True, ensure_ascii=False)
        if a != b:
            return {"episode": cf, "turn_index": i + 1, "twin_original": orig,
                    "variable_note": (wave4_meta.get(cf) or {}).get("variable_changed") or ""}
    return None


def select_anchors(max_per_episode):
    """确定性选取锚点位。返回 (plan, shortfall)；plan 元素含 anchor_id。"""
    pool, wave4_meta = load_wave4_pool()
    used = {}                                    # eid -> 已贡献锚点数（全局口径）
    plan, shortfall = [], {}

    def take(topic, quota, eids, root_of, source_tag, allowed=None, with_ref=False):
        got = []
        left = quota - sum(1 for a in plan if a["topic"] == topic)
        if left <= 0:
            return got
        avail, room = [], []
        for e in eids:
            r = max_per_episode - used.get(e, 0)
            if r > 0:
                avail.append(e)
                room.append(r)
        alloc = distribute_capacity(avail, room, left)
        for eid, m in alloc:
            root = root_of(eid)
            if with_ref:
                load_episode_pair(eid, root, allowed)   # 旧题：先强制白名单校验
                ep = load_json(os.path.join(root, "episode.json"), eid + " episode.json")
            else:
                ep = load_json(os.path.join(root, "episode.json"), eid + " episode.json")
            n_turns = len(ep.get("turns") or [])
            for t in turn_positions(n_turns, m):
                got.append({"topic": topic, "episode": eid, "turn_index": t,
                            "source": source_tag, "root": root})
                used[eid] = used.get(eid, 0) + 1
        return got

    for topic, quota in TOPIC_QUOTA:
        if topic == "T10":
            twin = find_twin_variable_anchor(wave4_meta)
            if twin and used.get(twin["episode"], 0) < max_per_episode:
                plan.append({"topic": topic, "episode": twin["episode"],
                             "turn_index": twin["turn_index"],
                             "source": "twin_" + T10_TWIN_GROUP,
                             "root": os.path.join(WAVE4_DIR, twin["episode"]),
                             "twin_original": twin["twin_original"],
                             "variable_note": twin["variable_note"]})
                used[twin["episode"]] = used.get(twin["episode"], 0) + 1
        plan.extend(take(topic, quota, pool.get(topic, []),
                         lambda e: os.path.join(WAVE4_DIR, e), "wave4_dev"))
        left = quota - sum(1 for a in plan if a["topic"] == topic)
        if left > 0 and topic == "T02":
            old_roots = dict(OLD_T02_POOL)
            plan.extend(take(topic, quota, [eid for eid, _ in OLD_T02_POOL],
                             lambda e: old_roots[e], "old_exposed_T02",
                             allowed=OLD_ALLOWED_FILES, with_ref=True))
        left = quota - sum(1 for a in plan if a["topic"] == topic)
        if left > 0:
            shortfall[topic] = left

    # 稳定排序：配额顺序内按 (episode, turn_index)；并登记该篇第几次贡献
    order = {t: i for i, (t, _) in enumerate(TOPIC_QUOTA)}
    plan.sort(key=lambda a: (order[a["topic"]], a["episode"], a["turn_index"]))
    by_ep = {}
    for a in plan:
        by_ep.setdefault(a["episode"], []).append(a["turn_index"])
    for a in plan:
        a["slot"] = by_ep[a["episode"]].index(a["turn_index"]) + 1
        a["episode_anchors"] = len(by_ep[a["episode"]])
    for i, a in enumerate(plan, 1):
        a["anchor_id"] = "A%02d" % i
    return plan, shortfall


def build_run_index(run_dir):
    """扫 run-dir：{(episode, case_id): {arm: {text, action, error_type, extract_from}}}。

    只透传，不判质量。record.json（live 格式）优先，否则 reply_raw.txt 走
    normalize_v1（同 make_blind_review/aggregate 的唯一抽取规则）。
    """
    index = {}
    if not run_dir:
        return index
    for arm in ARMS:
        arm_dir = os.path.join(run_dir, arm)
        if not os.path.isdir(arm_dir):
            continue
        for ep in sorted(os.listdir(arm_dir)):
            ep_dir = os.path.join(arm_dir, ep)
            if not os.path.isdir(ep_dir):
                continue
            for td in sorted(os.listdir(ep_dir)):
                turn_dir = os.path.join(ep_dir, td)
                if not os.path.isdir(turn_dir) or td.startswith("_"):
                    continue
                text = action = error = src = None
                rec_path = os.path.join(turn_dir, "record.json")
                if os.path.isfile(rec_path):
                    try:
                        with open(rec_path, "r", encoding="utf-8") as f:
                            rec = json.load(f)
                        if isinstance(rec, dict) and "primary_text" in rec:
                            text, action, src = rec.get("primary_text"), rec.get("action"), "record.json"
                    except Exception:
                        pass
                if src is None and os.path.isfile(os.path.join(turn_dir, "reply_raw.txt")):
                    row = normalize_output.normalize_turn(arm, ep, td, turn_dir)
                    text, action = row.get("primary_text"), row.get("action")
                    error, src = row.get("error_type"), "normalize_v1"
                if src is None:
                    continue
                case_id = td
                mp = os.path.join(turn_dir, "manifest.json")
                if os.path.isfile(mp):
                    try:
                        with open(mp, "r", encoding="utf-8") as f:
                            case_id = json.load(f).get("case_id") or case_id
                    except Exception:
                        pass
                if isinstance(text, str):
                    text = text.strip() or None
                index.setdefault((ep, case_id), {})[arm] = {
                    "text": text, "action": action, "error_type": error, "extract_from": src}
    return index


def display_arm(info):
    """两臂文本展示形态：只透传，不修饰内容。"""
    if info is None:
        return PENDING_TEXT, False
    if info.get("text"):
        return info["text"].replace("\n", " "), True
    if info.get("action") == "no_reply":
        return "（no_reply：合法不回，无正文）", True
    if info.get("error_type"):
        return "（无有效正文：error_type=%s）" % info["error_type"], True
    return PENDING_TEXT, False


def fmt_messages(turn):
    lines = []
    for m in (turn.get("input") or {}).get("messages") or []:
        ts = m.get("ts") or ""
        head = "[%s]%s" % (m.get("role") or "?", "（%s）" % ts if ts else "")
        lines.append("- %s %s" % (head, (m.get("text") or "").strip()))
    return lines


def intent_lines(turn, ref_turn):
    inp = turn.get("input") or {}
    out = []
    it = inp.get("intent")
    if isinstance(it, dict) and it.get("text"):
        if it.get("enabled", True):
            out.append("- 用户本轮意图（%s）：%s" % (it.get("status") or "explicit", it["text"]))
        else:
            out.append("- 用户本轮意图：（意图轮未启用）")
    elif isinstance(it, str) and it.strip():
        out.append("- 用户本轮意图：%s" % it.strip())
    note = inp.get("note")
    if isinstance(note, str) and note.strip():
        out.append("- IDEA 备注：%s" % note.strip())
    if not out:
        rit = (ref_turn or {}).get("intent")
        if isinstance(rit, dict) and rit.get("text"):
            out.append("- 用户本轮意图（参考卡登记）：%s" % rit["text"])
        elif isinstance(rit, str) and rit.strip():
            out.append("- 用户本轮意图（参考卡登记）：%s" % rit.strip())
        else:
            out.append("- 用户本轮意图：（未登记）")
    if (turn.get("input") or {}).get("may_decline"):
        out.append("- 本轮 may_decline=true（可合法不回）")
    return out


def persona_lines(episode, source):
    persona = episode.get("persona") or {}
    if source.startswith("old_"):
        return ["- 关系阶段：（旧题未登记 relationship_stage）",
                "- 人设：ME=%s ｜ HER=%s" % (persona.get("me") or "？",
                                             persona.get("her") or "？")]
    her, me = persona.get("her") or {}, persona.get("me") or {}
    names = "HER=%s ｜ ME=%s" % (her.get("name") or "？", me.get("name") or "？")
    return ["- 关系阶段：%s（%s）" % (persona.get("relationship_stage") or "（未登记）", names)]


def build_anchor_md(anchor, run_index, max_per_episode):
    eid, ti = anchor["episode"], anchor["turn_index"]
    is_old = anchor["source"].startswith("old_")
    ep, ref = load_episode_pair(eid, anchor["root"], OLD_ALLOWED_FILES if is_old else None)
    turns = ep.get("turns") or []
    turn = turns[ti - 1]
    case_id = turn.get("case_id") or "%s_T%02d" % (eid, ti)
    ref_turn = (ref.get("turns") or {}).get(case_id) or {}
    topic = anchor["topic"]
    exp = load_json(os.path.join(anchor["root"], "exposure.json"), "exposure.json") \
        if not is_old else {}
    title = exp.get("title") or ""

    src_desc = {
        "wave4_dev": "wave4_dev（候选池固定步长轮转）",
        "old_exposed_T02": "旧暴露题补位（E032/E039/E042，只读 episode+reference）",
        "twin_" + T10_TWIN_GROUP: "T10 孪生组 %s 变量轮（反事实篇，首个 input 分叉轮）" % T10_TWIN_GROUP,
    }.get(anchor["source"], anchor["source"])

    lines = []
    lines.append("## %s ｜ %s %s ｜ %s（%s 第 %d/%d 轮）" % (
        anchor["anchor_id"], topic, TOPIC_LABELS.get(topic, ""), case_id, eid, ti, len(turns)))
    lines.append("")
    lines.append("- 来源：%s（%s%s）｜ 该篇第 %d 个锚点 / 共 %d 个（上限 %d）" % (
        src_desc, eid, "《%s》" % title if title else "", anchor["slot"],
        anchor["episode_anchors"], max_per_episode))
    lines.extend(persona_lines(ep, anchor["source"]))
    dims = ref_turn.get("applicable_dimensions") or []
    lines.append("- 预期测试能力：%s %s ｜ applicable_dimensions：%s" % (
        topic, TOPIC_LABELS.get(topic, ""), "、".join(dims) if dims else "（未登记）"))
    if anchor.get("twin_original"):
        lines.append("- 孪生对照：原始篇=%s ｜ 变量说明：%s" % (
            anchor["twin_original"], anchor.get("variable_note") or "（未登记）"))
    lines.append("")

    lines.append("### 实际聊天上下文（episode.json 原文）")
    if ti >= 2:
        prev = turns[ti - 2]
        prev_case = prev.get("case_id") or "%s_T%02d" % (eid, ti - 1)
        clock = (prev.get("input") or {}).get("clock") or ""
        lines.append("**上一轮 %s%s**" % (prev_case, "（clock %s）" % clock if clock else ""))
        lines.extend(fmt_messages(prev))
    clock = (turn.get("input") or {}).get("clock") or ""
    lines.append("**本轮 %s%s**" % (case_id, "（clock %s）" % clock if clock else ""))
    lines.extend(fmt_messages(turn))
    lines.extend(intent_lines(turn, ref_turn))
    lines.append("")

    lines.append("### Gold 允许行为（reference.json）")
    lines.append("- 参考动作（current_action）：%s" % (ref_turn.get("current_action") or "（未登记）"))
    acts = ref_turn.get("acceptable_actions") or []
    if acts:
        lines.append("- 允许行为（acceptable_actions，逐条）：")
        for i, a in enumerate(acts, 1):
            lines.append("  %d. %s" % (i, a))
    else:
        lines.append("- 允许行为（acceptable_actions）：（未登记）")
    lines.append("- private_guidance：%s" % (ref_turn.get("private_guidance") or "（未登记）"))
    lines.append("")

    lines.append("### 关键事实及来源（required_memory_atoms）")
    atoms = ref_turn.get("required_memory_atoms") or []
    if atoms:
        for at in atoms:
            src = at.get("source") or {}
            loc = "%s/%s" % (src.get("case_id") or "？", src.get("message_id") or "？")
            lines.append("- [%s] %s：%s ｜ 出处：%s \"%s\"" % (
                at.get("atom_id") or "？", at.get("relation") or "？",
                at.get("value") or "？", loc, src.get("quote") or "？"))
    else:
        lines.append("- （该轮未登记 required_memory_atoms）")
    lines.append("")

    lines.append("### 两臂实际回复对比（%s）" % " vs ".join(ARMS))
    by_arm = run_index.get((eid, case_id)) or {}
    arms_texts, arms_avail = {}, {}
    for arm in ARMS:
        disp, avail = display_arm(by_arm.get(arm))
        arms_texts[arm] = disp if avail else None
        arms_avail[arm] = avail
        lines.append("- **%s**：%s" % (arm, disp))
    lines.append("")

    lines.append("### 人工审核（fill_status=PENDING_USER，工具不代填）")
    if topic == "T02":
        lines.append("> %s" % T02_PROBE_LINE)
    lines.append("- 判定：____（DIRECT=____ / CURRENT=____，记 PASS / FAIL / UNSURE）")
    lines.append("- 更愿发送：____（DIRECT / CURRENT / 均不）")
    lines.append("- 理由：____")
    lines.append("")
    return lines, {"arms_texts": arms_texts, "arms_available": arms_avail,
                   "case_id": case_id, "turn_count": len(turns)}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Benchmark V1 人工审核锚点包生成（§11，确定性）")
    ap.add_argument("--run-dir", default=None,
                    help="试跑输出目录（<arm>/<EID>/<turn>/）；缺省=两臂显示（待 live 输出）")
    ap.add_argument("--out-dir", default=DEFAULT_OUT_DIR,
                    help="输出目录（默认 refs/benchmark_v1_20261107/phase2）")
    ap.add_argument("--max-per-episode", type=int, default=2,
                    help="单篇最大贡献锚点数（默认 2）")
    args = ap.parse_args(argv)
    if args.max_per_episode < 1:
        raise SystemExit("--max-per-episode 必须 >= 1")

    plan, shortfall = select_anchors(args.max_per_episode)
    run_index = build_run_index(args.run_dir)

    md = ["# Benchmark V1 · 人工审核锚点队列（HUMAN_REVIEW_QUEUE，指导书 §11）", ""]
    md.append("> 生成：build_human_review_queue.py（确定性：固定步长轮转 "
              "int(i*(n+1)/(k+1)+0.5) + 同篇第 2 锚点取相距最远轮位；无随机源）。")
    md.append("> 锚点配额：" + "、".join("%s×%d" % (t, k) for t, k in TOPIC_QUOTA) + "，共 40。")
    md.append("> run_dir：%s（两臂文本原样透传，未做质量判断；缺失处显示%s）"
              % (args.run_dir or "（未提供）", PENDING_TEXT))
    md.append("> fill_status 全部=PENDING_USER：判定由人工填写，工具不代填（同盲评包纪律）。")
    md.append("> T02 锚点判定提示：%s" % T02_PROBE_LINE)
    md.append("")
    md.append("| 锚点 | 专题 | 位置 | 来源 | DIRECT | CURRENT |")
    md.append("|---|---|---|---|---|---|")

    json_anchors, dist, sections = [], {}, []
    for a in plan:
        md_lines, pkg = build_anchor_md(a, run_index, args.max_per_episode)
        sections.append("\n".join(md_lines))
        dist[a["topic"]] = dist.get(a["topic"], 0) + 1
        md.append("| %s | %s | %s（%s 第%d轮） | %s | %s | %s |" % (
            a["anchor_id"], a["topic"], pkg["case_id"], a["episode"], a["turn_index"],
            a["source"],
            "有" if pkg["arms_available"]["DIRECT"] else "待",
            "有" if pkg["arms_available"]["CURRENT"] else "待"))
        json_anchors.append({
            "anchor_id": a["anchor_id"],
            "topic": a["topic"],
            "topic_label": TOPIC_LABELS.get(a["topic"], ""),
            "episode": a["episode"],
            "case_id": pkg["case_id"],
            "turn_index": a["turn_index"],
            "turn_count": pkg["turn_count"],
            "source": a["source"],
            "arms_texts": {"DIRECT": pkg["arms_texts"]["DIRECT"],
                           "CURRENT": pkg["arms_texts"]["CURRENT"]},
            "arms_available": pkg["arms_available"],
            "fill_status": "PENDING_USER",
        })
    md.append("")
    md.append("---")
    md.append("")
    md.extend(sections)

    summary = ["## 汇总", ""]
    summary.append("- 实际分布：" + "、".join("%s×%d" % (t, dist.get(t, 0)) for t, _ in TOPIC_QUOTA)
                   + "（合计 %d / 40）" % len(plan))
    if shortfall:
        summary.append("- **配额短缺（如实登记，不代填不凑数）**：" + "、".join(
            "%s缺%d" % (t, n) for t, n in sorted(shortfall.items()))
            + "（提示：调大 --max-per-episode 或补充候选池）")
    else:
        summary.append("- 配额短缺：无")
    n_direct = sum(1 for x in json_anchors if x["arms_available"]["DIRECT"])
    n_cur = sum(1 for x in json_anchors if x["arms_available"]["CURRENT"])
    summary.append("- 两臂文本就位：DIRECT %d/%d、CURRENT %d/%d（其余=待 live 输出）"
                   % (n_direct, len(plan), n_cur, len(plan)))
    summary.append("")
    md.extend(summary)

    os.makedirs(args.out_dir, exist_ok=True)
    md_path = os.path.join(args.out_dir, "HUMAN_REVIEW_QUEUE.md")
    js_path = os.path.join(args.out_dir, "HUMAN_REVIEW_QUEUE.json")
    with open(md_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(md) + "\n")
    doc = {
        "kind": "benchmark_v1_human_review_queue",
        "version": "hrq_v1",
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "run_dir": args.run_dir,
        "max_per_episode": args.max_per_episode,
        "quota": {t: k for t, k in TOPIC_QUOTA},
        "distribution_actual": dist,
        "shortfall": shortfall,
        "fill_discipline": "PENDING_USER=人工填写，工具不代填",
        "anchors": json_anchors,
    }
    with open(js_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("human review queue: %d anchors -> %s" % (len(plan), md_path))
    print("  distribution: " + ", ".join("%s=%d" % (t, dist.get(t, 0)) for t, _ in TOPIC_QUOTA))
    if shortfall:
        print("  SHORTFALL: " + ", ".join("%s missing %d" % (t, n) for t, n in sorted(shortfall.items())))
    print("  arms ready: DIRECT %d, CURRENT %d (of %d)" % (n_direct, n_cur, len(plan)))
    print("  json: %s" % js_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
