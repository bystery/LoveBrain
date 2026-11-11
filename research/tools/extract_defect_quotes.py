# -*- coding: utf-8 -*-
"""extract_defect_quotes.py — 缺陷登记引用按字段自动抽取（独立复核 §4.1 执行令）。

问题：L3B_REPLY_DEFECTS_20261020.md 的引文系人工转述，部分与原字段不符，
且"定时让猫进房""她室友自己会安排的"两条在原始日志中不存在（转述失真）。
本工具从原始 reply_raw.txt JSON 逐字段抽取 verbatim 文本，产出：
  logs/defect_quotes_20261020.json   —— 每条缺陷引用的原文/字段路径/来源文件
  stdout 打印 Markdown 引用块（可直接粘贴进登记文档）

坏 JSON 节点（A2-t6）按可读子串抽取（标记 raw_substring，不伪装成字段值）。
零真实调用；原始日志只读。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN = os.path.join(ROOT, "logs", "l3b_fork", "l3bfork_20261020_175009_pid8776")
BRANCH = {"A1": "A_full_p1", "A2": "A_full_p2", "A3": "A_full_p3",
          "A4": "A_full_p4", "B1": "B_memory_off_p1", "B2": "B_memory_off_p2",
          "B3": "B_memory_off_p3", "B4": "B_memory_off_p4"}
TURN = {"t5": "turn_05_L3B_t05", "t6": "turn_06_L3B_t06"}

# 缺陷登记引用清单：(族, 节点, 字段路径)
QUOTE_SPEC = [
    ("F1_军师视角", "A1", "t5", "directions[0]"),
    ("F1_军师视角", "A1", "t5", "directions[3]"),
    ("F1_军师视角", "A2", "t5", "directions[0]"),
    ("F1_军师视角", "A3", "t5", "directions[0]"),
    ("F1_军师视角", "A3", "t5", "directions[2]"),
    ("F1_军师视角", "A3", "t5", "directions[3]"),
    ("F1_军师视角", "A4", "t5", "directions[0]"),
    ("F1_军师视角", "A4", "t5", "directions[3]"),
    ("F2_ME经历编造", "A2", "t6", "RAW_SUBSTRING:我一般必加土豆片"),
    ("F2_ME经历编造", "A3", "t6", "directions[2]"),
    ("F2_ME经历编造", "A4", "t6", "directions[2]"),
    ("F2_ME经历编造", "B1", "t6", "directions[2]"),
    ("F2_ME经历编造", "B2", "t6", "directions[2]"),
    ("F2_ME经历编造", "B3", "t6", "directions[2]"),
    ("F2_ME经历编造", "B3", "t6", "directions[3]"),
    ("F3_他人动机断言", "A2", "t5", "response.bad_boy"),
    ("F3_他人动机断言", "A4", "t5", "response.bad_boy"),
    ("F3_他人动机断言", "B4", "t5", "directions[0]"),
    ("F3_他人动机断言", "B4", "t6", "response.bad_boy"),
    ("F4_旧事主体错误", "A4", "t5", "directions[1]"),
    ("F5_轻度升级", "A4", "t6", "response.recommended"),
]


def _load_node(node, turn):
    p = os.path.join(RUN, BRANCH[node], TURN[turn], "reply_raw.txt")
    raw = open(p, encoding="utf-8").read()
    try:
        return raw, json.loads(raw), None
    except json.JSONDecodeError as e:
        return raw, None, "%s (col %d)" % (e.msg, e.colno)


def _get_field(parsed, path):
    """路径：directions[i] / response.<key>。"""
    if path.startswith("directions["):
        i = int(path[len("directions["):-1])
        v = (parsed.get("directions") or [])[i]
    else:
        v = (parsed.get("response") or {}).get(path.split(".", 1)[1])
    return v


def main():
    out = []
    for fam, node, turn, path in QUOTE_SPEC:
        raw, parsed, perr = _load_node(node, turn)
        if path.startswith("RAW_SUBSTRING:"):
            sub = path.split(":", 1)[1]
            idx = raw.find(sub)
            if idx < 0:
                out.append({"family": fam, "node": node, "turn": turn,
                            "path": path, "error": "substring_not_found"})
                continue
            quote = raw[max(0, idx - 20):idx + len(sub) + 20]
            out.append({"family": fam, "node": node, "turn": turn,
                        "path": path, "quote": quote, "extract": sub,
                        "mode": "raw_substring(broken JSON)",
                        "parse_error": perr})
            continue
        if parsed is None:
            out.append({"family": fam, "node": node, "turn": turn,
                        "path": path, "error": "parse_error: %s" % perr})
            continue
        v = _get_field(parsed, path)
        out.append({"family": fam, "node": node, "turn": turn, "path": path,
                    "quote": v, "mode": "field_verbatim",
                    "source": "logs/l3b_fork/l3bfork_20261020_175009_pid8776/"
                              "%s/%s/reply_raw.txt" % (BRANCH[node], TURN[turn])})
    dest = os.path.join(ROOT, "logs", "defect_quotes_20261020.json")
    json.dump({"scope": "16 节点候选臂（与 L3B_REPLY_DEFECTS_20261020.md 同源）",
               "policy": "字段 verbatim 抽取；坏 JSON 节点用 raw_substring 并显式标记",
               "quotes": out}, open(dest, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("->", os.path.relpath(dest, ROOT))
    for q in out:
        print("%s %s-%s %s: %r" % (q["family"], q["node"], q["turn"],
                                   q["path"], q.get("quote", q.get("error"))))


if __name__ == "__main__":
    sys.exit(main())
