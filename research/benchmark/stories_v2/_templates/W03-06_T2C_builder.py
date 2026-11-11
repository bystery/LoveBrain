#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""T2-C shard builder: timeline/exposure/runner generation + quote self-checks.
Whitelist tooling only: reads episode/reference written by this session, no logs/candidates/review data."""
import hashlib, json, os, sys, time

ROOT = r"D:\lovebrain-arena"
BASE = os.path.join(ROOT, "benchmark", "stories_v2", "wave3")

STORIES = {
 "E169": {
  "kind": "standard_12t", "pair": None, "role": None,
  "title": "白色小白的桨叶谁担责", "domain": "数码消费", "conflict_type": "信息不确定",
  "themes": ["数码消费", "无人机保修", "人为损坏认定", "官方寄修检测", "保内保外差价"],
  "events": [
   {"event_id": "E169_EV1", "source_turn": "E169_T04", "description": "寄修范围由只寄桨叶更正为整机和遥控都寄检测，判人为维修费四百八、保内运费全包"},
   {"event_id": "E169_EV2", "source_turn": "E169_T07", "description": "顺丰取件由周四下午更正为周五上午十点"},
   {"event_id": "E169_EV3", "source_turn": "E169_T09", "description": "HER 自定先按修的上限走并为周年活动租机一天两百"},
   {"event_id": "E169_EV4", "source_turn": "E169_T12", "description": "官方判定非人为，免费更换整套桨臂"}
  ],
  "revisions": [
   {"case_id": "E169_T04", "what": "寄修范围", "from": "只寄桨叶", "to": "整机和遥控都寄检测"},
   {"case_id": "E169_T07", "what": "取件时间", "from": "周四下午", "to": "周五上午十点"},
   {"case_id": "E169_T12", "what": "归因结论", "from": "未知", "to": "判定非人为免费换桨臂"}
  ]
 },
 "E170": {
  "kind": "standard_12t", "pair": None, "role": None,
  "title": "烟机位差两公分", "domain": "家居装修", "conflict_type": "费用类",
  "themes": ["家居装修", "定制橱柜安装", "尺寸误差返工", "二次上门费用归属", "工期连锁"],
  "events": [
   {"event_id": "E170_EV1", "source_turn": "E170_T04", "description": "门店电话认责复尺漏项，承诺书面责任单"},
   {"event_id": "E170_EV2", "source_turn": "E170_T05", "description": "返工范围由一块封板更正为侧板和顶线都动，工期多七天，六百二次上门费由门店出"},
   {"event_id": "E170_EV3", "source_turn": "E170_T07", "description": "拉篮改三层加三百五自担并签变更单"},
   {"event_id": "E170_EV4", "source_turn": "E170_T09", "description": "燃气改造改约至20号上午，五十元改约费 HER 自担"}
  ],
  "revisions": [
   {"case_id": "E170_T05", "what": "返工范围", "from": "只换一块封板", "to": "侧板和顶线都要动"},
   {"case_id": "E170_T05", "what": "完工口径", "from": "11月15日前", "to": "17号二次安装"},
   {"case_id": "E170_T09", "what": "燃气改造", "from": "18号上门", "to": "20号上午"}
  ]
 },
 "E171": {
  "kind": "standard_12t", "pair": None, "role": None,
  "title": "智齿改了三回期", "domain": "医疗健康", "conflict_type": "取消改期类",
  "themes": ["医疗健康", "拔智齿", "术前化验有效期", "麻醉史更正", "术后复诊改期"],
  "events": [
   {"event_id": "E171_EV1", "source_turn": "E171_T02", "description": "改期日期由 HER 看岔的这周三更正为11月12日下周三下午两点"},
   {"event_id": "E171_EV2", "source_turn": "E171_T05", "description": "如实登记血压史后麻药由含肾上腺素换为不含，手术或拖至四十分钟"},
   {"event_id": "E171_EV3", "source_turn": "E171_T06", "description": "12日手术当天因生理期临近被医生劝返，二次改期"},
   {"event_id": "E171_EV4", "source_turn": "E171_T07", "description": "手术三定11月25日周二上午九点半"},
   {"event_id": "E171_EV5", "source_turn": "E171_T11", "description": "值班医生判定肿胀属正常范围，复诊维持28号"}
  ],
  "revisions": [
   {"case_id": "E171_T02", "what": "手术日期", "from": "看岔的这周三", "to": "11月12日下周三下午两点"},
   {"case_id": "E171_T06", "what": "手术状态", "from": "12日照常", "to": "劝返改期"},
   {"case_id": "E171_T07", "what": "手术日期", "from": "11月12日", "to": "11月25日周二上午九点半"}
  ]
 },
 "E172": {
  "kind": "standard_12t", "pair": None, "role": None,
  "title": "芝麻的托运三改", "domain": "宠物", "conflict_type": "更正类",
  "themes": ["宠物", "猫咪航空托运", "免疫证时效", "航班改签", "航空箱尺寸"],
  "events": [
   {"event_id": "E172_EV1", "source_turn": "E172_T02", "description": "机票由1月30日更正为1月28日早班，托运手续随轨"},
   {"event_id": "E172_EV2", "source_turn": "E172_T05", "description": "借用42公分闲置航空箱方案因超限两公分撤回，改自购新箱"},
   {"event_id": "E172_EV3", "source_turn": "E172_T07", "description": "28号航班因延误风险改签至29日早七点五十，检疫窗口重算为26到29"},
   {"event_id": "E172_EV4", "source_turn": "E172_T08", "description": "检疫证26号办下，有效期至29号覆盖航班"}
  ],
  "revisions": [
   {"case_id": "E172_T02", "what": "航班日期", "from": "1月30日", "to": "1月28日"},
   {"case_id": "E172_T05", "what": "航空箱来源", "from": "借 ME 家42公分箱", "to": "自购39公分新箱"},
   {"case_id": "E172_T07", "what": "航班日期", "from": "1月28日", "to": "1月29日早七点五十"}
  ]
 },
 "E173": {
  "kind": "cross_story_pair", "pair": "E173+E174", "role": "A",
  "title": "2549 的锁单三小时", "domain": "数码消费", "conflict_type": "启停类",
  "themes": ["数码消费", "平板以旧换新", "补贴资格核实", "订单型号更正", "限时锁单"],
  "events": [
   {"event_id": "E173_EV1", "source_turn": "E173_T03", "description": "版本选择翻转：学习版不参加国补出局，改选普通版自装软件"},
   {"event_id": "E173_EV2", "source_turn": "E173_T04", "description": "旧平板抵扣由600更正为450，到手价更新为2549"},
   {"event_id": "E173_EV3", "source_turn": "E173_T05", "description": "当日国补名额20个停发，转次日清晨排队锁单"},
   {"event_id": "E173_EV4", "source_turn": "E173_T06", "description": "手滑选8+128退单重拍8+256，30分钟窗口内名额保留"},
   {"event_id": "E173_EV5", "source_turn": "E173_T10", "description": "发货地址误为公司改派至家，旧机回收取件定21号上午"}
  ],
  "revisions": [
   {"case_id": "E173_T03", "what": "目标版本", "from": "学习版", "to": "普通版"},
   {"case_id": "E173_T04", "what": "旧机抵扣", "from": "600", "to": "450"},
   {"case_id": "E173_T06", "what": "下单内存", "from": "8+128", "to": "8+256"},
   {"case_id": "E173_T10", "what": "收货地址", "from": "公司", "to": "家"}
  ]
 },
 "E174": {
  "kind": "cross_story_pair", "pair": "E173+E174", "role": "B",
  "title": "两个亮点与五十块", "domain": "数码消费", "conflict_type": "边界类",
  "themes": ["数码消费", "到手换货", "屏幕亮点验收", "价保窗口", "旧机残值跟进"],
  "events": [
   {"event_id": "E174_EV1", "source_turn": "E174_T02", "description": "亮点由一处更正为两处，证据视频补齐"},
   {"event_id": "E174_EV2", "source_turn": "E174_T04", "description": "门店与线上口径不一致，HER 拍板走官方寄检并全单留证"},
   {"event_id": "E174_EV3", "source_turn": "E174_T05", "description": "引用 E173 批次旧机回收：承诺450仅到账400，扣款理由缺原装充电头"},
   {"event_id": "E174_EV4", "source_turn": "E174_T07", "description": "凭10月21日回收确认短信申诉成功，50元补回"},
   {"event_id": "E174_EV5", "source_turn": "E174_T08", "description": "平台小二裁决支持换新机，恰在15天窗口最后一天"}
  ],
  "revisions": [
   {"case_id": "E174_T02", "what": "亮点数量", "from": "一处", "to": "两处"},
   {"case_id": "E174_T07", "what": "回收款", "from": "到账400", "to": "补回450整"},
   {"case_id": "E174_T08", "what": "换货申请", "from": "门店拒换", "to": "平台裁决支持换新"}
  ]
 }
}


def sha(p):
    with open(p, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def main():
    problems = []
    manifest = {
        "kind": "story_v2_wave3_stage1_shard",
        "stage": "wave3",
        "date": "2026-11-02",
        "built_by": "W3-T2-C isolated generation shard",
        "count": 6,
        "turn_per_story": 12,
        "stories": []
    }
    for sid, meta in STORIES.items():
        sdir = os.path.join(BASE, sid)
        ep = json.load(open(os.path.join(sdir, "episode.json"), encoding="utf-8"))
        ref = json.load(open(os.path.join(sdir, "reference.json"), encoding="utf-8"))
        cases = [t["case_id"] for t in ep["turns"]]
        assert len(cases) == 12 and len(set(cases)) == 12
        assert all(cid.startswith(sid + "_T") for cid in cases)

        # --- self-check: quotes verbatim, message_id, required-this-turn, protected ---
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
        if n_cross < 3:
            problems.append("%s cross-turn atom references < 3: %d" % (sid, n_cross))

        # --- timeline ---
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
        tl_path = os.path.join(sdir, "timeline.json")
        with open(tl_path, "w", encoding="utf-8") as f:
            json.dump(timeline, f, ensure_ascii=False, indent=1)

        # --- exposure ---
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
            "batch": "W3-T2-20261102",
            "session": "W3-T2-C"
        }
        if meta["pair"]:
            exposure["pair"] = meta["pair"]
            exposure["role"] = meta["role"]
        with open(os.path.join(sdir, "exposure.json"), "w", encoding="utf-8") as f:
            json.dump(exposure, f, ensure_ascii=False, indent=1)

        # --- runner copy (byte-deep equal content) ---
        with open(os.path.join(sdir, "runner_episode.json"), "w", encoding="utf-8") as f:
            json.dump(ep, f, ensure_ascii=False, indent=1)

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
        if meta["pair"]:
            entry["cross_story_pair"] = meta["pair"]
            entry["pair_role"] = meta["role"]
        manifest["stories"].append(entry)

    if problems:
        print("SELF-CHECK FAILED:")
        for p in problems:
            print("  ", p)
        sys.exit(1)

    with open(os.path.join(BASE, "STAGE1_MANIFEST_T2C.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    print("built manifest for", [e["story_id"] for e in manifest["stories"]])


if __name__ == "__main__":
    main()
