#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""T4-G1 shard builder: timeline/exposure/runner generation + quote self-checks.
Whitelist tooling only: reads episode/reference written by this session, no logs/candidates/review data."""
import hashlib, json, os, sys

ROOT = r"D:\lovebrain-arena"
BASE = os.path.join(ROOT, "benchmark", "stories_v2", "wave3")

STORIES = {
 "E181": {
  "kind": "standard_12t", "pair": None, "role": None,
  "title": "换了个表哥来交机", "domain": "数码消费", "conflict_type": "第三方类",
  "themes": ["数码消费", "游戏主机代购", "第三方卖家换人", "版本型号更正", "当面验机"],
  "events": [
   {"event_id": "E181_EV1", "source_turn": "E181_T04", "description": "交机人由卖家本人更正为其表哥代交，钱仍走小柯中转"},
   {"event_id": "E181_EV2", "source_turn": "E181_T06", "description": "主机版本由国行更正为日版，报价由1250更正为1150，定金不退抵货款"},
   {"event_id": "E181_EV3", "source_turn": "E181_T08", "description": "交机时间维持周六下午三点，表哥两点先到店"},
   {"event_id": "E181_EV4", "source_turn": "E181_T11", "description": "划痕再让50后以1100成交，定金100抵扣另转1000"}
  ],
  "revisions": [
   {"case_id": "E181_T04", "what": "交机人", "from": "卖家本人", "to": "卖家表哥代交"},
   {"case_id": "E181_T06", "what": "主机版本", "from": "国行", "to": "日版"},
   {"case_id": "E181_T06", "what": "报价", "from": "1250", "to": "1150"},
   {"case_id": "E181_T11", "what": "成交价", "from": "1150", "to": "1100"}
  ]
 },
 "E182": {
  "kind": "standard_12t", "pair": None, "role": None,
  "title": "瓦工三次没进门", "domain": "家居装修", "conflict_type": "取消改期类",
  "themes": ["家居装修", "卫生间瓷砖铺贴", "瓦工档期改期", "闭水试验返工", "邻里赔偿留证"],
  "events": [
   {"event_id": "E182_EV1", "source_turn": "E182_T02", "description": "实测16平按每箱0.72平购23箱加2箱损耗共25箱"},
   {"event_id": "E182_EV2", "source_turn": "E182_T03", "description": "瓦工进场由周六更改为下周一，砖搬空楼道"},
   {"event_id": "E182_EV3", "source_turn": "E182_T05", "description": "闭水试验失败，防水铲了重刷再养48小时，贴砖推后"},
   {"event_id": "E182_EV4", "source_turn": "E182_T08", "description": "二次闭水通过，定档周四早八点进场并留文字确认"}
  ],
  "revisions": [
   {"case_id": "E182_T03", "what": "进场时间", "from": "周六早上八点", "to": "下周一早上"},
   {"case_id": "E182_T05", "what": "贴砖计划", "from": "周三看结果再贴", "to": "防水重做后再推"},
   {"case_id": "E182_T08", "what": "进场时间", "from": "待定", "to": "周四早上八点"}
  ]
 },
 "E183": {
  "kind": "standard_12t", "pair": None, "role": None,
  "title": "0.62 的临界值", "domain": "医疗健康", "conflict_type": "信息不确定类",
  "themes": ["医疗健康", "过敏原检测", "报告解读口径矛盾", "医生复诊收口", "环境控制一个月"],
  "events": [
   {"event_id": "E183_EV1", "source_turn": "E183_T02", "description": "报告提前出：屋尘螨0.62对参考0.35以下卡临界，牛肉虾阴性"},
   {"event_id": "E183_EV2", "source_turn": "E183_T03", "description": "家人称临界即过敏与同事称需加查IgE两说法矛盾"},
   {"event_id": "E183_EV3", "source_turn": "E183_T07", "description": "复诊医生判定仅轻度致敏非唯一原因，环境控制一个月后复查，否掉加查全套IgE"},
   {"event_id": "E183_EV4", "source_turn": "E183_T12", "description": "复查指标下降，地毯可铺回，下下月随诊一次"}
  ],
  "revisions": [
   {"case_id": "E183_T07", "what": "临界值解读", "from": "两说矛盾", "to": "医生判轻度致敏先环境控制"},
   {"case_id": "E183_T07", "what": "加查全套IgE", "from": "同事建议", "to": "医生判定不必做"},
   {"case_id": "E183_T08", "what": "忌口范围", "from": "疑似全戒", "to": "只停酒和辛辣"},
   {"case_id": "E183_T12", "what": "地毯状态", "from": "卷起收纳", "to": "可以铺回"}
  ]
 },
 "E184": {
  "kind": "standard_12t", "pair": None, "role": None,
  "title": "多出来的五十块补课费", "domain": "宠物", "conflict_type": "费用类",
  "themes": ["宠物", "柯基训犬课", "合同外补课费争议", "扣课恢复更正", "老学员锁价续费"],
  "events": [
   {"event_id": "E184_EV1", "source_turn": "E184_T01", "description": "报训犬幼训班1980元12节课"},
   {"event_id": "E184_EV2", "source_turn": "E184_T03", "description": "缺席未取消被扣1节并冒出合同外50元补课费待缴"},
   {"event_id": "E184_EV3", "source_turn": "E184_T06", "description": "店长确认50不在合同内予以免除，被扣课时恢复"},
   {"event_id": "E184_EV4", "source_turn": "E184_T11", "description": "老学员12月10日前续费可锁1980旧价并要求落成聊天记录"}
  ],
  "revisions": [
   {"case_id": "E184_T04", "what": "50元补课费", "from": "待缴", "to": "免但推380私教"},
   {"case_id": "E184_T06", "what": "50元补课费", "from": "待缴", "to": "确认合同外免除"},
   {"case_id": "E184_T06", "what": "被扣课时", "from": "扣1节", "to": "恢复"}
  ]
 }
}


def sha(p):
    with open(p, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def build_story(sid, meta, problems):
    sdir = os.path.join(BASE, sid)
    ep = json.load(open(os.path.join(sdir, "episode.json"), encoding="utf-8"))
    ref = json.load(open(os.path.join(sdir, "reference.json"), encoding="utf-8"))
    cases = [t["case_id"] for t in ep["turns"]]
    assert len(cases) == 12 and len(set(cases)) == 12
    assert all(cid.startswith(sid + "_T") for cid in cases)

    msgs_by_case = {t["case_id"]: t["input"]["messages"] for t in ep["turns"]}
    case_index = {c: i for i, c in enumerate(cases)}
    n_cross = 0
    for cid, r in ref["turns"].items():
        her_text = "\n".join(m["text"] for m in msgs_by_case[cid] if m["role"] == "HER")
        for pexp in r["protected_expressions"]:
            if pexp not in her_text:
                problems.append("%s %s protected not in HER msg: %r" % (sid, cid, pexp))
        for atom in r["required_memory_atoms"]:
            src = atom["source"]
            if src["kind"] == "message":
                scid = src["case_id"]
                if scid not in case_index:
                    problems.append("%s %s atom %s bad source case" % (sid, cid, atom["atom_id"]))
                    continue
                if case_index[scid] > case_index[cid]:
                    problems.append("%s %s atom %s future source" % (sid, cid, atom["atom_id"]))
                if atom.get("required_this_turn") and scid == cid:
                    problems.append("%s %s atom %s required from current turn" % (sid, cid, atom["atom_id"]))
                hits = [k for k, m in enumerate(msgs_by_case[scid]) if src["quote"] in m["text"]]
                if not hits:
                    problems.append("%s %s atom %s quote NOT verbatim: %r" % (sid, cid, atom["atom_id"], src["quote"]))
                elif src.get("message_id") != "m%d" % hits[0]:
                    problems.append("%s %s atom %s message_id mismatch" % (sid, cid, atom["atom_id"]))
                if scid != cid:
                    n_cross += 1
            else:
                kb = ep["initial_kb"]
                if src.get("key") not in kb or src["quote"] not in kb.get(src["key"], ""):
                    problems.append("%s %s atom %s initial_kb quote bad" % (sid, cid, atom["atom_id"]))
                if atom.get("required_this_turn") is False:
                    pass
    if n_cross < 3:
        problems.append("%s cross-turn atom references < 3: %d" % (sid, n_cross))

    timeline = {
        "story_id": sid,
        "turn_clocks": [t["input"]["clock"] for t in ep["turns"]],
        "message_index": [],
        "events": meta["events"],
        "revisions": meta["revisions"]
    }
    for t in ep["turns"]:
        for k, m in enumerate(t["input"]["messages"]):
            timeline["message_index"].append({
                "case_id": t["case_id"], "message_id": "m%d" % k,
                "speaker": m["role"], "quote": m["text"], "ts": m["ts"]})
    with open(os.path.join(sdir, "timeline.json"), "w", encoding="utf-8") as f:
        json.dump(timeline, f, ensure_ascii=False, indent=1)

    hashes = {}
    for name in ("episode.json", "reference.json", "timeline.json"):
        hashes[name] = sha(os.path.join(sdir, name))
    exposure = {
        "author": "AI",
        "author_type": "AI",
        "readers": ["author"],
        "frozen_at": None,
        "experiments_used": [],
        "dev_exposed": False,
        "real_user_material": False,
        "hashes": hashes,
        "runner_copy": "benchmark/stories_v2/wave3/%s/runner_episode.json" % sid,
        "kind": meta["kind"],
        "conflict_type": meta["conflict_type"],
        "domain": meta["domain"],
        "batch": "W3-T4-20261102",
        "session": "W3-T4-1"
    }
    with open(os.path.join(sdir, "exposure.json"), "w", encoding="utf-8") as f:
        json.dump(exposure, f, ensure_ascii=False, indent=1)

    with open(os.path.join(sdir, "runner_episode.json"), "w", encoding="utf-8") as f:
        json.dump(ep, f, ensure_ascii=False, indent=1)
    return hashes


def main():
    args = [a for a in sys.argv[1:]]
    write_manifest = "--manifest" in args
    only = [a for a in args if not a.startswith("--")]
    problems = []
    manifest = {
        "kind": "story_v2_wave3_stage1_shard",
        "stage": "wave3",
        "date": "2026-11-02",
        "built_by": "W3-T4-1 isolated generation shard",
        "count": len(STORIES),
        "turn_per_story": 12,
        "stories": []
    }
    for sid, meta in STORIES.items():
        if only and sid not in only:
            continue
        hashes = build_story(sid, meta, problems)
        entry = {
            "story_id": sid,
            "title": meta["title"],
            "themes": meta["themes"],
            "domain": meta["domain"],
            "conflict_type": meta["conflict_type"],
            "dir": "benchmark/stories_v2/wave3/%s" % sid,
            "turns": 12,
            "files": hashes
        }
        manifest["stories"].append(entry)

    if problems:
        print("SELF-CHECK FAILED:")
        for p in problems:
            print("  ", p)
        sys.exit(1)

    if write_manifest:
        with open(os.path.join(BASE, "STAGE1_MANIFEST_T4G1.json"), "w", encoding="utf-8") as f:
            json.dump(manifest, f, ensure_ascii=False, indent=1)
        print("built manifest for", [e["story_id"] for e in manifest["stories"]])
    else:
        print("built timeline/exposure/runner for", [e["story_id"] for e in manifest["stories"]])


if __name__ == "__main__":
    main()
