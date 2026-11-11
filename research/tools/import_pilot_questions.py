# -*- coding: utf-8 -*-
"""import_pilot_questions.py — 公开开发题（pilot）导入机械检查器。

职责边界（指导书 v2 §6）：
- 只做确定性机械检查：数量/schema/角色/时间/媒体引用/全局唯一性；
- 不调用考生/模型判断语义；不改评分器；不替内容方改故事；
- 命题方的语义判据（评审卡）留评估侧附件，本工具只登记其存在；
- 输出机器可读报告 + 人工可读摘要，供导入登记与内容方确认。

用法：
  python tools/import_pilot_questions.py --in <目录或json文件...> \
      --report refs/pilot_import/report.json
支持：.json 文件、.md 中 ```json 围栏块（文件名自块前导注释取）。
"""
import argparse
import json
import os
import re
import sys
from datetime import datetime as _dt

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

HARD, WARN = [], []


def hard(name, ok, detail=""):
    HARD.append((name, bool(ok), detail))
    return bool(ok)


def warn(name, ok, detail=""):
    WARN.append((name, bool(ok), detail))


_TS_RE = re.compile(r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$")


def _ts_valid(s):
    """格式 + 真实日历日期（拒 11-99 / 13 月 / 2 月 30 日等）。"""
    if not _TS_RE.match(s or ""):
        return False
    try:
        _dt.strptime(s, "%Y-%m-%d %H:%M")
        return True
    except ValueError:
        return False


def _kb_value_ok(v):
    """initial_kb 值必须是标量事实（复核 v3 §2C：嵌套结构如 initial_kb.me
    应拒绝——知识库是平面键值，嵌套请拆成平面键）。"""
    return v is None or isinstance(v, (str, int, float, bool))


def extract_json_blocks(path):
    """从 .json 文件或 .md 的 ```json 围栏提取 (source_name, obj)。"""
    out = []
    if path.endswith(".json"):
        try:
            out.append((os.path.basename(path),
                        json.load(open(path, encoding="utf-8"))))
        except json.JSONDecodeError as e:
            hard("json_parse:%s" % os.path.basename(path), False, str(e)[:120])
        return out
    txt = open(path, encoding="utf-8").read()
    for m in re.finditer(r"```json\s*\n(.*?)```", txt, re.S):
        body = m.group(1)
        # 块内首行注释可带文件名：// episode_E001.json
        name = "fenced@%s#%d" % (os.path.basename(path), m.start())
        nm = re.match(r"\s*//\s*([\w\-\.]+\.json)", body)
        if nm:
            name = nm.group(1)
            body = re.sub(r"^\s*//.*$", "", body, flags=re.M)
        try:
            out.append((name, json.loads(body)))
        except json.JSONDecodeError as e:
            hard("json_parse:%s" % name, False, str(e)[:120])
    return out


def check_episode(ep, src, seen_case, seen_ep, totals):
    tag = ep.get("episode_id", "<missing_id>")
    # ---- episode 级 ----
    hard("ep_id:%s" % tag, isinstance(ep.get("episode_id"), str)
         and bool(ep.get("episode_id")), "episode_id 缺失或非字符串")
    if ep.get("episode_id") in seen_ep:
        hard("ep_id_unique:%s" % tag, False, "episode_id 重复")
    seen_ep.add(ep.get("episode_id"))
    warn("ep_id_neutral:%s" % tag,
         not re.search(r"(gold|probe|memory|trap|测试|考)", str(tag), re.I),
         "编号疑似泄漏考点，建议中性编号 E0xx")
    hard("persona_obj:%s" % tag, isinstance(ep.get("persona"), dict))
    hard("initial_kb_obj:%s" % tag,
         ep.get("initial_kb") is None or isinstance(ep.get("initial_kb"), dict))
    if isinstance(ep.get("initial_kb"), dict):
        bad_kb = {k: type(v).__name__
                  for k, v in ep["initial_kb"].items()
                  if not isinstance(k, str) or not _kb_value_ok(v)}
        hard("initial_kb_flat:%s" % tag, not bad_kb,
             "initial_kb 值必须是标量事实（嵌套/容器键拆成平面键）: %s"
             % list(bad_kb.items())[:3])
    hard("gold_side_obj:%s" % tag,
         ep.get("gold") is None or isinstance(ep.get("gold"), dict),
         "gold 属评估侧，必须为对象（建议空对象+独立评审卡）")
    if isinstance(ep.get("gold"), dict) and ep.get("gold"):
        warn("gold_empty_default:%s" % tag, False,
             "gold 非空——确认语义判据已同时登记于独立评审卡")
    turns = ep.get("turns")
    hard("turns_array:%s" % tag, isinstance(turns, list) and bool(turns))
    if not isinstance(turns, list):
        return
    totals["requests"] += len(turns)
    prev_clock = None
    for i, t in enumerate(turns):
        cid = t.get("case_id")
        hard("case_id_str:%s#%d" % (tag, i + 1),
             isinstance(cid, str) and bool(cid.strip()),
             "case_id 必须是非空字符串（缺位/空串拒绝）")
        tid = "%s:%s" % (tag, cid if isinstance(cid, str) else "<missing>")
        if cid in seen_case:
            hard("case_unique:%s" % tid, False, "case_id 全局重复")
        seen_case.add(cid)
        hard("turn_type_reply:%s" % tid, t.get("turn_type") == "reply",
             "首批统一 reply")
        # 复核 v3 §2C：turn 级 gold 错型必须拒绝（评估侧对象）
        if "gold" in t:
            hard("turn_gold_obj:%s" % tid,
                 t["gold"] is None or isinstance(t["gold"], dict),
                 "turn 级 gold 必须为对象/null，实得 %s"
                 % type(t["gold"]).__name__)
        inp = t.get("input")
        hard("input_obj:%s" % tid, isinstance(inp, dict))
        if not isinstance(inp, dict):
            continue
        msgs = inp.get("messages")
        hard("messages_array:%s" % tid, isinstance(msgs, list) and bool(msgs))
        if not isinstance(msgs, list) or not msgs:
            continue
        # 复核 v3 §2C：消息项必须是对象（字符串项此前直接崩溃）
        hard("msg_items_obj:%s" % tid,
             all(isinstance(m, dict) for m in msgs),
             "每条消息必须是对象（role/text/ts）")
        if not all(isinstance(m, dict) for m in msgs):
            continue
        roles = [m.get("role") for m in msgs]
        hard("roles_valid:%s" % tid,
             all(r in ("HER", "ME") for r in roles),
             "角色只能是 HER/ME（IDEA/note 走 input.note，不得混入消息）")
        hard("last_msg_her:%s" % tid, roles[-1] == "HER",
             "主链路请求最后一条真实消息必须是 HER")
        hard("text_str:%s" % tid,
             all(isinstance(m.get("text"), str) and m.get("text", "").strip()
                 for m in msgs))
        hard("ts_str:%s" % tid,
             all(isinstance(m.get("ts"), str) and m.get("ts", "").strip()
                 for m in msgs))
        # ---- 时间机械一致性 ----
        hard("clock_format:%s" % tid, _ts_valid(inp.get("clock")),
             "统一 YYYY-MM-DD HH:MM 且为真实日历日期（拒 11-99 等）")
        for m in msgs:
            if not _ts_valid(m.get("ts")):
                hard("msg_ts_format:%s" % tid, False,
                     "消息 ts 须同一格式且真实日期: %r" % m.get("ts"))
        if _ts_valid(inp.get("clock")):
            if prev_clock and inp["clock"] < prev_clock:
                hard("clock_monotonic:%s" % tid, False,
                     "跨请求时钟倒退 %s < %s" % (inp["clock"], prev_clock))
            prev_clock = inp["clock"]
            for m in msgs:
                if _ts_valid(m.get("ts")) and m["ts"] > inp["clock"]:
                    hard("clock_after_msgs:%s" % tid, False,
                         "请求 clock 早于可见消息 %s" % m["ts"])
            # 复核 v3 §2C：窗口内消息乱序必须拒绝（ts 单调不减）
            ts_list = [m["ts"] for m in msgs if _ts_valid(m.get("ts"))]
            if any(a > b for a, b in zip(ts_list, ts_list[1:])):
                hard("msgs_ordered:%s" % tid, False,
                     "窗口内消息 ts 乱序: %s" % ts_list)
        # ---- 可选字段类型 ----
        if "note" in inp:
            hard("note_str:%s" % tid, isinstance(inp["note"], str),
                 "note 为私密备注字符串，不得藏评分标准（语义人工核）")
        if "intent" in inp and inp["intent"] is not None:
            hard("intent_obj:%s" % tid, isinstance(inp["intent"], dict))
            it = inp.get("intent") or {}
            if isinstance(it, dict) and it.get("enabled"):
                hard("intent_text:%s" % tid,
                     isinstance(it.get("text"), str) and bool(it["text"]))
        if "may_decline" in inp:
            hard("may_decline_bool:%s" % tid,
                 isinstance(inp["may_decline"], bool))
        if "kb" in inp and inp["kb"] is not None:
            hard("kb_obj:%s" % tid, isinstance(inp["kb"], dict))
        # ---- 媒体引用登记（许可/文件由导入登记表核，不自动过） ----
        imgs = re.findall(r"(assets/[\w\.\-/]+|\bimg[\w\.\-]*\.(?:png|jpe?g))",
                          json.dumps(inp, ensure_ascii=False))
        if imgs:
            warn("media_refs:%s" % tid, True,
                 "含媒体引用 %s——导入时须核对文件存在+许可登记+映射表"
                 % sorted(set(imgs))[:3])


def check_cards(cards_path, expect_cards, seen_case, ep_case_map):
    """复核 v3 §2C：评审卡齐全性与绑定检查（工具此前只"登记存在"不核对）。
    - 数量 = 期望（首批 24）
    - 每卡绑定唯一 case_id，且该 case_id 存在于题面
    - 题面每个请求位都有卡（双向齐全）
    - 卡 split 必须标 dev_exposed（内容版本登记口径）
    - 卡不得内嵌 gold 评分值进入考生输入侧的字段（卡属评估侧附件，
      本检查只登记其存在与绑定，不改变运行时可见性控制）"""
    hard("cards_file_exists", os.path.isfile(cards_path),
         "评审卡文件不存在: %s" % cards_path)
    if not os.path.isfile(cards_path):
        return
    try:
        cards = json.load(open(cards_path, encoding="utf-8"))
    except json.JSONDecodeError as e:
        hard("cards_parse", False, str(e)[:120])
        return
    hard("cards_array", isinstance(cards, list) and bool(cards),
         "评审卡必须是数组")
    if not isinstance(cards, list):
        return
    hard("cards_count", len(cards) == expect_cards,
         "期望 %d 张卡，实得 %d" % (expect_cards, len(cards)))
    card_case = {}
    for j, c in enumerate(cards):
        cid = c.get("case_id") if isinstance(c, dict) else None
        tag = "card#%d:%s" % (j + 1, cid)
        hard("card_obj:%s" % tag, isinstance(c, dict))
        if not isinstance(c, dict):
            continue
        hard("card_case_id:%s" % tag,
             isinstance(cid, str) and bool(cid.strip()),
             "卡缺 case_id")
        hard("card_episode:%s" % tag,
             isinstance(c.get("episode_id"), str) and bool(c["episode_id"]),
             "卡缺 episode_id")
        hard("card_split:%s" % tag, c.get("split") == "dev_exposed",
             "卡 split 应为 dev_exposed，实得 %r" % c.get("split"))
        hard("card_families:%s" % tag,
             isinstance(c.get("families"), list) and bool(c["families"]),
             "卡缺 families（评审维度）")
        hard("card_evidence:%s" % tag,
             isinstance(c.get("evidence"), list),
             "卡缺 evidence（判断依据）")
        if isinstance(cid, str):
            if cid in card_case:
                hard("card_case_unique:%s" % tag, False, "卡 case_id 重复")
            card_case[cid] = c
    # 双向绑定：卡→题面、题面→卡
    orphan = sorted(set(card_case) - set(seen_case))
    hard("cards_bound_to_episodes", not orphan,
         "卡 case_id 不存在于任何题面: %s" % orphan[:5])
    missing = sorted(set(seen_case) - set(card_case))
    hard("episodes_fully_carded", not missing,
         "题面请求位缺卡: %s" % missing[:5])
    # episode 归属一致：卡的 episode_id 与题面中该 case 所在 episode 一致
    mismatch = [cid for cid, c in card_case.items()
                if ep_case_map.get(cid) not in (None, c.get("episode_id"))]
    hard("card_episode_binding", not mismatch,
         "卡 episode_id 与题面归属不符: %s" % mismatch[:5])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", nargs="+", required=True,
                    help="目录或 json/md 文件（md 取 ```json 围栏）")
    ap.add_argument("--report", default=None)
    ap.add_argument("--expect-episodes", type=int, default=3)
    ap.add_argument("--expect-requests", type=int, default=24)
    ap.add_argument("--cards", default=None,
                    help="评审卡 JSON（数组；复核 v3 §2C：核对齐全性与绑定）")
    ap.add_argument("--expect-cards", type=int, default=24)
    args = ap.parse_args()

    paths = []
    for p in args.__dict__["in"]:
        if os.path.isdir(p):
            for root, _, files in os.walk(p):
                for f in sorted(files):
                    if f.endswith((".json", ".md")):
                        paths.append(os.path.join(root, f))
        else:
            paths.append(p)

    seen_case, seen_ep = set(), set()
    totals = {"requests": 0, "episodes": 0}
    ep_case_map = {}
    eps = []
    for p in paths:
        for name, obj in extract_json_blocks(p):
            if isinstance(obj, list):
                for e in obj:
                    if isinstance(e, dict) and "turns" in e:
                        eps.append((name, e))
            elif isinstance(obj, dict) and "turns" in obj:
                eps.append((name, obj))
    totals["episodes"] = len(eps)
    for src, ep in eps:
        check_episode(ep, src, seen_case, seen_ep, totals)
        eid = ep.get("episode_id")
        for t in ep.get("turns", []):
            if isinstance(t, dict):
                ep_case_map[t.get("case_id")] = eid

    if args.cards:
        check_cards(args.cards, args.expect_cards, seen_case, ep_case_map)

    hard("episode_count", totals["episodes"] == args.expect_episodes,
         "首批 %d 条故事，实得 %d" % (args.expect_episodes, totals["episodes"]))
    hard("request_count_approx",
         abs(totals["requests"] - args.expect_requests) <= 4,
         "首批约 %d 请求位，实得 %d（±4 容差）"
         % (args.expect_requests, totals["requests"]))

    n_fail = sum(1 for _, ok, _ in HARD if not ok)
    report = {
        "generated": __import__("datetime").datetime.now().strftime(
            "%Y-%m-%d %H:%M:%S"),
        "episodes": totals["episodes"],
        "requests": totals["requests"],
        "hard_checks": [{"check": n, "pass": ok, "detail": d}
                        for n, ok, d in HARD],
        "warnings": [{"check": n, "pass": ok, "detail": d}
                     for n, ok, d in WARN],
        "verdict": "IMPORT_OK" if n_fail == 0 else "IMPORT_BLOCKED(%d)" % n_fail,
        "cards_checked": bool(args.cards),
        "note": "机械检查不判语义；评审卡/许可/来源登记由内容方与评估侧确认",
    }
    if args.report:
        os.makedirs(os.path.dirname(os.path.abspath(args.report)),
                    exist_ok=True)
        json.dump(report, open(args.report, "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
    print("import_pilot: episodes=%d requests=%d hard_fail=%d warn=%d -> %s"
          % (totals["episodes"], totals["requests"], n_fail, len(WARN),
             report["verdict"]))
    for n, ok, d in HARD:
        if not ok:
            print("  HARD-FAIL %s | %s" % (n, d))
    return 0 if n_fail == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
