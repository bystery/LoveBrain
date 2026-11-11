# -*- coding: utf-8 -*-
r"""一次性修订 E027_T03 的语义无支撑原子（封板前修题，不触碰任何已付费答卷）。

发现（worker 逐位审查 48 张卡时）：reference 要求模型知道"出口互动屏只对会员开放扫码"，
但这条事实在 persona/initial_kb 里**从未出现**，原子的 source 却标 initial_kb/me 引
"看展自备讲解器"——字面在库里、语义不支持。按这种卡评分会把"诚实说不知道"判成
factuality/memory_use 失败，属于题目缺陷，不是候选缺陷（AGENTS §4/rubric：卡缺陷单列）。

修法（最小、可核）：把这条事实合法地写进 initial_kb.me（他上次跟她来试过），
原子源改为逐字指向该句；episode/reference 同步，runner 副本同步，manifest/exposure
散列重算，整 stage 机械验证重跑。旧文件全部 .bak 保留，历史答卷不改写。
"""
import datetime
import hashlib
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SID = "E027"
SDIR = os.path.join(ROOT, "benchmark", "stories_v2", "stage2", SID)
RUNNER = os.path.join(ROOT, "benchmark", "episodes", SID + ".json")
KB_KEY = "me"
ADDED = "上次陪她逛展时试过，出口那台互动屏只对会员开放扫码。"
OLD_QUOTE = "看展自备讲解器"

TS = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def dump(p, obj):
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)


def main():
    ep_path = os.path.join(SDIR, "episode.json")
    ref_path = os.path.join(SDIR, "reference.json")
    exp_path = os.path.join(SDIR, "exposure.json")
    man_path = os.path.join(ROOT, "benchmark", "stories_v2", "stage2",
                            "STAGE2_MANIFEST.json")

    # 备份（原文件不动，改前留底）
    for p in (ep_path, ref_path, exp_path, man_path, RUNNER):
        shutil.copyfile(p, p + ".bak-" + TS + "-e027cardfix")

    ep = load(ep_path)
    if ADDED not in ep["initial_kb"][KB_KEY]:
        ep["initial_kb"][KB_KEY] = ep["initial_kb"][KB_KEY].rstrip("。；; ") \
            + "。" + ADDED
    dump(ep_path, ep)

    ref = load(ref_path)
    t3 = ref["turns"]["E027_T03"]
    hit = 0
    for atom in t3["required_memory_atoms"]:
        if atom["atom_id"] == "E027_A02":
            atom["source"] = {"kind": "initial_kb", "key": KB_KEY, "quote": ADDED}
            hit += 1
    if hit != 1:
        raise SystemExit("E027_A02 未唯一命中（found=%d）——零修改退出" % hit)
    t3["revision_note"] = ("2026-10-28 封板前修题：A02 原引 initial_kb『%s』字面在库但语义不"
                           "支持『会员制』事实；改为把该事实写入 initial_kb.%s 并逐字引用。"
                           "旧文件 .bak-%s 保留。" % (OLD_QUOTE, KB_KEY, TS))
    dump(ref_path, ref)

    # runner 副本逐字同步
    shutil.copyfile(ep_path, RUNNER)

    # 散列重算
    exp = load(exp_path)
    files = {"episode.json": os.path.join(SDIR, "episode.json"),
             "reference.json": ref_path,
             "timeline.json": os.path.join(SDIR, "timeline.json"),
             "audit.md": os.path.join(SDIR, "audit.md")}
    exp.setdefault("hashes", {})
    for name, p in files.items():
        if os.path.isfile(p):
            exp["hashes"][name] = sha(p)
    exp["revision"] = {"at": TS, "kind": "reference_atom_support_fix",
                       "case_id": "E027_T03", "atom_id": "E027_A02",
                       "note": "封板前修题；此前无任何批次使用 E027（experiments_used=[]）"}
    dump(exp_path, exp)

    man = load(man_path)
    for s in man["stories"]:
        if s["story_id"] == SID:
            s["files"] = {n: sha(p) for n, p in files.items() if os.path.isfile(p)}
    man["revisions"] = man.get("revisions", []) + [
        {"story_id": SID, "at": TS, "changed": ["episode.json", "reference.json",
                                                "exposure.json", "runner_copy"]}]
    dump(man_path, man)

    print("E027 kb.me =", ep["initial_kb"]["me"])
    print("A02 source =", json.dumps(
        [a["source"] for a in ref["turns"]["E027_T03"]["required_memory_atoms"]
         if a["atom_id"] == "E027_A02"][0], ensure_ascii=False))
    print("runner copy == stage2 episode:", sha(ep_path) == sha(RUNNER))
    print("episode sha:", sha(ep_path)[:16], "reference sha:", sha(ref_path)[:16])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
