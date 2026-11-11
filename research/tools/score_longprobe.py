# -*- coding: utf-8 -*-
"""tools/score_longprobe.py — 长剧探针批机械评分（零模型调用）。

检查三类（LONGPROBE_PREREGISTRATION 主比较的机械部分）：
1. 更正链遵守度：更正后旧值字符串在双臂全部回复（首选+备选）中的复活数；
2. 出窗探针召回初筛：探针位回复对目标原子显著词的命中（机械初筛，语义判定归 judge/亲读）；
3. 程序分对照：scoring.json 每臂均值。
旧值清单在脚本内逐条登记（与 spec corrections 同源），每条注明合法性豁免场景。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
import argparse
_ap = argparse.ArgumentParser()
_ap.add_argument("--out", default=os.path.join(ROOT, "logs", "longprobe_a_live_20261030"))
_ap.add_argument("--stories", default="E061,E062")
_ap.add_argument("--result", default=None)
_a = _ap.parse_args()
OUT_DIR = _a.out
STORY_LIST = _a.stories.split(",")
RESULT_PATH = _a.result or os.path.join(
    ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030",
    "LONGPROBE_SCORE_MECHANICAL_%s.json" % "_".join(STORY_LIST))
PREREG = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "long_probe_20261030",
                      "LONGPROBE_PREREGISTRATION.json")


# 更正链旧值清单（更正轮之后不得复活；豁免=取消/更正语境中的必要提及）
OLD_VALUES = {
    "E061": [
        {"family": "旧日期", "token": "5 月 20", "valid_from": 8,
         "exempt_if_contains": ["改期", "白约", "白约了", "退", "改到 5 月 6", "旧日期"]},
        {"family": "旧口径", "token": "半年", "valid_from": 15,
         "exempt_if_contains": ["不用半年", "从半年改", "改成了三个月"]},
    ],
    "E062": [
        {"family": "旧参数", "token": "2.8", "valid_from": 7,
         "exempt_if_contains": ["不是 2.8", "f4 不是", "2.8 那颗", "记成 2.8", "跟 2.8 比",
                                "拿它跟 2.8", "2.8 的期待", "比 2.8", "2.8 差", "别惦记 2.8",
                                "那颗 2.8", "不值 4600", "f4 和 2.8"]},
        {"family": "旧日期", "token": "周六", "valid_from": 10,
         "exempt_if_contains": ["改到周日", "周六改", "挪周日", "黄了"]},
        {"family": "旧价", "token": "4600", "valid_from": 14,
         "exempt_if_contains": ["不值", "不是"]},
        {"family": "旧价", "token": "4300", "valid_from": 15,
         "exempt_if_contains": ["4300+300", "4300 加", "降到 3600", "挂 3800", "死守 3800"]},
    ],
    "E063": [
        {"family": "旧日期", "token": "11 月 28", "valid_from": 8,
         "exempt_if_contains": ["改到", "重考", "作废"]},
        {"family": "旧时段", "token": "工作日晚上", "valid_from": 10,
         "exempt_if_contains": ["停了", "改成", "周末早上"]},
        {"family": "旧教练", "token": "李教练", "valid_from": 14,
         "exempt_if_contains": ["离职", "换", "原来"]},
        {"family": "旧口径", "token": "半年", "valid_from": 15,
         "exempt_if_contains": ["不用半年", "改成", "三个月", "比半年"]},
    ],
    "E064": [
        {"family": "旧手术日", "token": "12 月 3", "valid_from": 7,
         "exempt_if_contains": ["改到", "推迟", "改期"]},
    ],
    "E065": [
        {"family": "旧价", "token": "3800", "valid_from": 9,
         "exempt_if_contains": ["降到", "改成", "死守", "挂 3800", "原价", "回 3800"]},
        {"family": "旧起租", "token": "2 月 1", "valid_from": 12,
         "exempt_if_contains": ["改到", "来不及", "作废"]},
        {"family": "旧租客", "token": "情侣", "valid_from": 15,
         "exempt_if_contains": ["以为是", "不是", "同事合租"]},
    ],
    "E066": [
        {"family": "旧出发", "token": "12 月 20", "valid_from": 7,
         "exempt_if_contains": ["改到", "退了", "暴雪"]},
        {"family": "旧路线", "token": "318", "valid_from": 11,
         "exempt_if_contains": ["不走", "改", "结冰", "原路", "没走过"]},
        {"family": "旧链尺寸", "token": "215", "valid_from": 14,
         "exempt_if_contains": ["换成", "退", "差一号"]},
    ],
    "E067": [
        {"family": "旧期限", "token": "12 月 15", "valid_from": 8,
         "exempt_if_contains": ["延期", "延", "改"]},
        {"family": "旧职级", "token": "P6 不是", "valid_from": 13, "exempt_if_contains": []},
        {"family": "旧base", "token": "2 万 4 十四薪", "valid_from": 16,
         "exempt_if_contains": ["看成", "是 2 万 45"]},
    ],
    "E068": [
        {"family": "旧手术日", "token": "12 月 18", "valid_from": 7,
         "exempt_if_contains": ["推迟到", "改", "调"]},
    ],
}

PROBES_EXTRA = {
    "E063": {20: ["实习期", "12 分"], 23: ["1 月 9", "拿本"], 24: ["1 月 9"]},
    "E064": {18: ["回医院", "拆线"], 20: ["每年"], 24: ["疫苗"]},
    "E065": {19: ["法定金", "定金"], 22: ["街道办", "备案"], 24: ["3600"]},
    "E066": {19: ["见站", "半箱"], 23: ["两小时", "文档"], 24: ["胎侧", "205"]},
    "E067": {18: ["vesting", "行权"], 19: ["12 月 19", "P6+", "2 万 45"], 21: ["vesting", "行权"]},
    "E068": {18: ["直接结", "备案"], 19: ["四件套", "备案"]},
}
# 出窗探针：turn -> 显著词（正确使用应包含；机械初筛）
PROBES = {
    "E061": {
        20: {"goal_tokens": ["空腹"], "note": "应答抽血空腹/超声不用/以新单为准"},
        22: {"goal_tokens": ["医生", "不用忌碘"], "note": "应引医生口径而非自证"},
        23: {"goal_tokens": ["8 月 6", "8月6"], "note": "日期一致"},
        24: {"goal_tokens": ["云南菜", "七点"], "note": "生日细节一致"},
    },
    "E062": {
        19: {"goal_tokens": ["f4", "F4", "转接"], "note": "转述参数不走样"},
        24: {"goal_tokens": ["8200"], "note": "快门数凭记忆取出"},
    },
}


def texts_of(story, turn):
    """该位双臂的 primary + 备选文本。"""
    out = {}
    for arm in ("parent", "candidate"):
        d = os.path.join(OUT_DIR, "%s_%s" % (arm, story), "out",
                         "turn_%02d_%s_T%02d" % (turn, story, turn))
        p = os.path.join(d, "reply_raw.txt")
        if not os.path.isfile(p):
            out[arm] = None
            continue
        raw = open(p, encoding="utf-8").read()
        try:
            j = json.loads(raw)
            prim = (j.get("response") or {}).get("recommended", "")
            bks = [v for k, v in (j.get("response") or {}).items()
                   if k != "recommended" and isinstance(v, str) and v.strip()]
            bks += [v for v in (j.get("directions") or []) if isinstance(v, str) and v.strip()]
            out[arm] = {"primary": prim, "backups": bks}
        except Exception:  # noqa: BLE001
            out[arm] = {"primary": raw[:400], "backups": []}
    return out


def main():
    prereg = json.load(open(PREREG, encoding="utf-8"))
    rep = {"kind": "long_probe_mechanical_score", "date": "2026-10-30",
           "revival": {}, "probe_recall_prescreen": {}, "program": {}}
    # 1) 更正链遵守
    for story in STORY_LIST:
        fams = OLD_VALUES.get(story, [])
        stats = {}
        for arm in ("parent", "candidate"):
            hits = []
            n_texts = 0
            for turn in range(1, 25):
                tx = texts_of(story, turn).get(arm)
                if not tx:
                    continue
                all_txts = ([tx["primary"]] if tx["primary"] else []) + tx["backups"]
                n_texts += len(all_txts)
                for t in all_txts:
                    for fam in fams:
                        if turn < fam["valid_from"]:
                            continue
                        if fam["token"] in t:
                            if any(x in t for x in fam["exempt_if_contains"]):
                                continue
                            hits.append({"turn": turn, "family": fam["family"],
                                         "token": fam["token"], "text": t[:80]})
            stats[arm] = {"revival_count": len(hits), "texts_scanned": n_texts,
                          "hits": hits[:12]}
        rep["revival"][story] = stats
    # 2) 探针召回初筛
    for story in STORY_LIST:
        probes = dict(PROBES.get(story, {}))
        for t, toks in PROBES_EXTRA.get(story, {}).items():
            probes.setdefault(t, {"goal_tokens": toks, "note": "spec-required recall"})
        rows = {}
        for arm in ("parent", "candidate"):
            per = {}
            for turn, spec in probes.items():
                tx = texts_of(story, turn).get(arm)
                if not tx:
                    per[turn] = "MISSING"
                    continue
                prim = tx["primary"]
                per[turn] = {"hit": [k for k in spec["goal_tokens"] if k in prim],
                             "primary_empty": not bool(prim.strip())}
            rows[arm] = per
        rep["probe_recall_prescreen"][story] = rows
    # 3) 程序分
    for story in STORY_LIST:
        sums = {"parent": [0, 0], "candidate": [0, 0]}
        for arm in ("parent", "candidate"):
            for turn in range(1, 25):
                p = os.path.join(OUT_DIR, "%s_%s" % (arm, story), "out",
                                 "turn_%02d_%s_T%02d" % (turn, story, turn), "scoring.json")
                if os.path.isfile(p):
                    try:
                        sc = json.load(open(p, encoding="utf-8"))
                        if not sc.get("veto"):
                            sums[arm][0] += sc.get("score10", 0)
                            sums[arm][1] += 1
                    except Exception:  # noqa: BLE001
                        pass
        rep["program"][story] = {
            arm: {"mean10": round(s[0] / s[1], 3) if s[1] else None, "n": s[1]}
            for arm, s in sums.items()}
    json.dump(rep, open(RESULT_PATH, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.load(open(RESULT_PATH, encoding="utf-8"))
    for st in STORY_LIST:
        if st in rep["revival"]:
            print(st, "revival:", {a: rep["revival"][st][a]["revival_count"]
                                   for a in ("parent", "candidate")},
                  "program:", rep["program"].get(st))
    print("result:", RESULT_PATH)
    return 0


if __name__ == "__main__":
    sys.exit(main())
