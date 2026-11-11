# -*- coding: utf-8 -*-
r"""tools/build_stageD_dataset.py — 为 D 批复验材料生成 timeline.json / exposure.json / 故事清单（零调用）。

只做**派生簿记**，不创作内容：
  timeline.turn_clocks / message_index 逐字取自 episode.json（不新增、不改写、不概括）；
  events/revisions 留空数组（作者没写事件账，工具不替她编），future_policy 写明出题时点；
  exposure 如实登记：作者是未接触候选资产与答卷的子代理，读者包含看过候选答卷的实施 worker，
  因此**不声称独立封存**；experiments_used=[] 且 dev_exposed=false（本批之前从未暴露给任何候选）；
  real_user_material=false；frozen_at 先留 null，建卡通过后由 --freeze 写时间戳。
每题生成后再算 hashes（episode/reference/timeline/audit），供卡与清单复核。
"""
import argparse
import datetime
import hashlib
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "benchmark", "stories_v2", "stageD")
STAGE_NAME = "stageD"      # --src 切换（final_20261029 等）；影响 exposure 的 stage 标识
CREATED_AT = "2026-10-28"


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def build(sid, freeze, author_mode="subagent_blind"):
    d = os.path.join(SRC, sid)
    ep = json.load(io.open(os.path.join(d, "episode.json"), encoding="utf-8"))
    turns = ep["turns"]
    tl = {
        "kind": "story_timeline_v2", "story_id": sid, "timezone": "+08:00",
        "turn_clocks": [{"case_id": t["case_id"], "clock": t["input"]["clock"]}
                        for t in turns],
        "message_index": [{"case_id": t["case_id"], "message_id": "m%d" % i,
                           "speaker": m["role"], "ts": m["ts"], "quote": m["text"]}
                          for t in turns for i, m in enumerate(t["input"]["messages"])],
        "events": [], "relationship": [], "revisions": [],
        "future_policy": ("全部轮次与卡由出题子代理在 2026-10-28 一次性写定并冻结；"
                          "后续任何批次不得据候选表现回改本题面或本卡。"),
    }
    json.dump(tl, io.open(os.path.join(d, "timeline.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)

    names = ("episode.json", "reference.json", "timeline.json", "audit.md")
    if author_mode == "final_worker":
        # final_20261029 新材料：作者=实施 worker 本人，写题前已看过候选答卷与旧题。
        # 诚实登记为"非盲化"，避免把前瞻新材料冒充独立封存集。
        author = ("实施 worker 本人（2026-10-29；写题前已读全部候选答卷、旧故事与"
                  "全局复核报告，见本目录 audit.md §0）")
        author_type = "AI_worker_new_material_final"
        source_notes = ("虚构人物与对话；素材非真实用户材料；作者知道答案。"
                        "本批用于长期知识、恢复、正向互动与组件价值评估，"
                        "不用于回溯修改旧批结论。")
        readers = ["实施 worker（作者兼派发者）", "独立复核者"]
        review_note = ("诚实边界：作者看过候选答卷，本集不是盲化材料、也不是独立封存；"
                       "它只能回答'在同类现实局面上，新机制是否比原版更好'，"
                       "不能证明跨域泛化。")
        status = "final_new_material_20261029"
    elif author_mode == "targeted_worker":
        # 定向复验材料：作者=实施 worker 本人（已读候选答卷与 r2 机制源码）。
        # 不得写"盲于候选"，否则暴露台账造假。
        author = ("实施 worker 本人（本 session；写本故事前已读 D01/D02、AK01–AK12、S1 全部"
                  "候选答卷与 r2 机制源码——见本目录 audit.md §0）")
        author_type = "AI_worker_targeted_retest"
        source_notes = ("虚构人物与对话；素材非真实用户材料；**作者知道答案**，本故事只用于对"
                        "FIX-9 做定点证伪（换说法重测同类局面），不构成泛化样本。")
        readers = ["实施 worker（作者兼派发者）",
                   "复验读者：任何复核本批数据的人"]
        review_note = ("诚实边界：作者看过候选答卷，因此本集不是盲化材料、也不是独立封存；"
                       "它能证明'是否只在原格子上成立'，不能证明跨域泛化。")
        status = "targeted_retest_material_stageD"
    else:
        author = ("QoderWork 独立出题子代理 stageD-author（任务书只给题面/判分格式与"
                  "质量要求，未给候选资产、答卷原文、故障原句或评审结论）")
        author_type = "AI_subagent_candidate_blind"
        source_notes = ("虚构人物与对话；素材非真实用户材料；作者侧未读 candidates/、logs/、"
                        "以及旧故事与锚点内容（见 STAGED0_AUTHOR_SPEC.md §0/§5）。")
        readers = ["出题子代理（盲候选）",
                   "实施 worker v4-impl-20261028-1057（已看过候选答卷，负责派生卡与派发）"]
        review_note = ("诚实边界：出题者盲于候选，但审题与派卡由看过候选答卷的实施 worker 完成，"
                       "且两故事同由同一 session 委托产出——因此本集只能称'前瞻新材料/作者侧盲化'，"
                       "不得称'独立人类封存'。")
        status = "prospective_new_material_stageD"
    exp = {
        "kind": "story_exposure_v2", "story_id": sid,
        "title": sid,
        "themes": [],
        "author": author,
        "author_type": author_type,
        "real_user_material": False,
        "source_notes": source_notes,
        "readers": readers,
        "independent_review": False,
        "review_note": review_note,
        "created_at": CREATED_AT,
        "frozen_at": datetime.datetime.now().isoformat(timespec="seconds") if freeze else None,
        "status": status,
        "experiments_used": [],
        "dev_exposed": False,
        "validation_claim": False,
        "turns": len(turns),
        "may_decline_turns": [t["case_id"] for t in turns if t["input"].get("may_decline")],
        "hashes": {n: sha(os.path.join(d, n)) for n in names},
        "runner_copy": "benchmark/episodes/%s.json" % sid,
    }
    json.dump(exp, io.open(os.path.join(d, "exposure.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    # runner copy 必须与 canonical episode 逐字相同
    ep_dst = os.path.join(ROOT, "benchmark", "episodes", "%s.json" % sid)
    with io.open(ep_dst, "w", encoding="utf-8") as f:
        json.dump(ep, f, ensure_ascii=False, indent=1)
    print("%s timeline=%d msgs=%d exposure hashes=%s"
          % (sid, len(tl["turn_clocks"]), len(tl["message_index"]),
             {k: v[:8] for k, v in exp["hashes"].items()}))


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--stories", default="D01,D02")
    ap.add_argument("--src", default="stageD",
                    help="benchmark/stories_v2 下的材料目录（stageD / final_20261029）")
    ap.add_argument("--created-at", default="2026-10-28")
    ap.add_argument("--freeze", action="store_true",
                    help="写 frozen_at（建卡通过、派发之前打时间戳；打完不再改题面与卡）")
    ap.add_argument("--author-mode", default="subagent_blind",
                    choices=("subagent_blind", "targeted_worker", "final_worker"),
                    help="暴露台账口径：subagent_blind=盲化出题（D01/D02）；"
                         "targeted_worker=实施 worker 定向复验材料（D03，作者知道答案）；"
                         "final_worker=实施 worker 新写前瞻材料（作者知道答案，非盲化）")
    a = ap.parse_args(argv)
    global SRC, STAGE_NAME, CREATED_AT
    SRC = os.path.join(ROOT, "benchmark", "stories_v2", a.src)
    STAGE_NAME = a.src
    CREATED_AT = a.created_at
    if not os.path.isdir(SRC):
        raise SystemExit("材料目录不存在：%s" % SRC)
    for sid in [s.strip() for s in a.stories.split(",") if s.strip()]:
        build(sid, a.freeze, a.author_mode)
    return 0


if __name__ == "__main__":
    sys.exit(main())
