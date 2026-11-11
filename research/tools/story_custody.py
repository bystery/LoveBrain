# -*- coding: utf-8 -*-
"""tools/story_custody.py — W03-05 故事保管与暴露账本。

生命周期（单向，消费后不再称封存）：
  SEALED    — 生成后未向任何候选批暴露
  EXPOSED   — 至少一个候选批消费过；对同谱系候选不再是"未见"测试面
用途：
  1) 新故事生成隔离——生成会话只读 stage*_src/规格，禁止读 logs/ 与 candidates/ 的失败输出；
  2) 暴露账本——append-only 事件流（story × batch × arms × date），可审计；
  3) 消费纪律——一旦 CONSUMED，不得再对该故事声称"候选未见"；只可用于回归/回放/跨代对比。

CLI:
  python tools/story_custody.py seed                 # 从 manifests 注册全部故事（幂等）
  python tools/story_custody.py expose S B --arms a  # 记录暴露事件
  python tools/story_custody.py status               # 汇总：sealed/exposed 清单
  python tools/story_custody.py verify               # 重算 hash 对账
"""
import argparse
import datetime
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROG = os.path.join(ROOT, "refs", "experiment_protocol_20261023", "global_harness_program_20261030")
LEDGER = os.path.join(PROG, "STORY_CUSTODY_LEDGER.json")
MANIFESTS = [("stage2", "STAGE2_MANIFEST.json"), ("stage3", "STAGE3_MANIFEST.json"),
             ("stage4", "STAGE4_MANIFEST.json"), ("wave1", "STAGE1_MANIFEST.json"),
             ("wave2", "STAGE1_MANIFEST.json"), ("wave3", "STAGE1_MANIFEST.json"),
             ("wave3", "STAGE1_MANIFEST_CF.json"),
             ("wave3_ext", "MANIFEST_LRE117.json"), ("wave3_ext", "MANIFEST_LRE119.json"), ("wave3_ext", "MANIFEST_LRE121.json"), ("wave3_ext", "MANIFEST_LRE123.json"), ("wave3_ext", "MANIFEST_LRE125.json"), ("wave3_ext", "MANIFEST_LRE126.json"), ("wave3_ext", "MANIFEST_LRE127.json"), ("wave3_ext", "MANIFEST_LRE128.json"), ("wave3_ext", "MANIFEST_LRE138.json"), ("wave3_ext", "MANIFEST_LRE139.json"), ("wave3_ext", "MANIFEST_LRE141.json"), ("wave3_ext", "MANIFEST_LRE142.json"), ("wave3_ext", "MANIFEST_LRE145.json"), ("wave3_ext", "MANIFEST_LRE146.json"), ("wave3_ext", "MANIFEST_LRE147.json"), ("wave3_ext", "MANIFEST_LRE148.json"),
             ("wave1_ext", "MANIFEST_LRE060.json"), ("wave1_ext", "MANIFEST_LRE048.json")]


def sha256_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def load():
    if os.path.exists(LEDGER):
        return json.load(open(LEDGER, encoding="utf-8"))
    return {"kind": "story_custody_ledger", "created": "2026-10-31", "stories": {}, "events": []}


def save(l):
    os.makedirs(PROG, exist_ok=True)
    json.dump(l, open(LEDGER, "w", encoding="utf-8"), indent=1, ensure_ascii=False)


def seed(l):
    n = 0
    for stage, mf in MANIFESTS:
        fp = os.path.join(ROOT, "benchmark", "stories_v2", stage, mf)
        if not os.path.exists(fp):
            continue
        m = json.load(open(fp, encoding="utf-8"))
        for s in m.get("stories", []):
            sid = s["story_id"]
            sdir = os.path.join(ROOT, "benchmark", "stories_v2", stage, sid)
            ep = os.path.join(ROOT, s.get("runner_copy", "") or os.path.join(sdir, "episode.json"))
            spec = os.path.join(sdir, "reference.json")
            h = {"episode": sha256_file(ep) if ep and os.path.isfile(ep) else None,
                 "reference": sha256_file(spec) if os.path.isfile(spec) else None,
                 "manifest_episode": s.get("episode_sha256")}
            if sid not in l["stories"]:
                l["stories"][sid] = {"stage": stage, "title": s.get("title", ""),
                                     "themes": s.get("themes", []), "turns": s.get("turns"),
                                     "hashes": h, "lifecycle": "SEALED", "consumed_by": []}
                n += 1
            else:
                l["stories"][sid]["hashes"] = h  # hash 对账随时刷新，生命周期不动
    return n


def expose(l, story, batch, arms, note=""):
    if story not in l["stories"]:
        print("ERR story not registered:", story)
        return 1
    ev = {"story": story, "batch": batch, "arms": arms,
          "date": datetime.date.today().isoformat(), "note": note}
    l["events"].append(ev)
    st = l["stories"][story]
    if batch not in st["consumed_by"]:
        st["consumed_by"].append(batch)
    st["lifecycle"] = "EXPOSED"
    return 0


def status(l):
    sealed = [s for s, v in sorted(l["stories"].items()) if v["lifecycle"] == "SEALED"]
    exposed = {s: v["consumed_by"] for s, v in sorted(l["stories"].items()) if v["lifecycle"] == "EXPOSED"}
    print("registered=%d  SEALED=%d  EXPOSED=%d" % (len(l["stories"]), len(sealed), len(exposed)))
    print("SEALED pool:", " ".join(sealed))
    for s, b in exposed.items():
        print("EXPOSED %s <- %s" % (s, ", ".join(b)))


def verify(l):
    bad = 0
    for sid, v in sorted(l["stories"].items()):
        h = v["hashes"]
        for key, rel in (("episode", v.get("_episode_path")), ("reference", None)):
            pass  # paths not stored; recompute from manifests
    # 重扫 manifests 对账
    for stage, mf in MANIFESTS:
        fp = os.path.join(ROOT, "benchmark", "stories_v2", stage, mf)
        if not os.path.exists(fp):
            continue
        m = json.load(open(fp, encoding="utf-8"))
        for s in m.get("stories", []):
            sid = s["story_id"]
            if sid not in l["stories"]:
                print("UNREGISTERED", sid)
                bad += 1
                continue
            ep = os.path.join(ROOT, s.get("runner_copy", "")
                              or os.path.join(ROOT, "benchmark", "stories_v2", stage, sid, "episode.json"))
            reg = l["stories"][sid]["hashes"].get("manifest_episode")
            if s.get("episode_sha256") and reg and s["episode_sha256"] != reg:
                print("HASH-DRIFT", sid, "manifest vs ledger")
                bad += 1
            if os.path.exists(ep) and l["stories"][sid]["hashes"].get("episode"):
                cur = sha256_file(ep)
                if cur != l["stories"][sid]["hashes"]["episode"]:
                    print("HASH-DRIFT", sid, "episode file changed since registration")
                    bad += 1
    print("verify: %s" % ("OK" if bad == 0 else "%d problems" % bad))
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["seed", "expose", "status", "verify"])
    ap.add_argument("--story")
    ap.add_argument("--batch")
    ap.add_argument("--arms", default="")
    ap.add_argument("--note", default="")
    a = ap.parse_args()
    l = load()
    if a.cmd == "seed":
        n = seed(l)
        save(l)
        print("seeded %d new stories" % n)
    elif a.cmd == "expose":
        sys.exit(expose(l, a.story, a.batch, a.arms, a.note) or (save(l) or 0))
    elif a.cmd == "status":
        status(l)
    elif a.cmd == "verify":
        sys.exit(1 if verify(l) else 0)


if __name__ == "__main__":
    main()
