#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_scoring_authority.py — 评分权威端到端反例（第六波判决 §3/§12-1；R3#3 真断言）。

攻击模型：v2 候选机制在 run_turn 里篡改自己的 ctx.messages（追加/删除/改角色），
并让回复的 analysis.scene_facts 引用伪造来源 m3。
断言：评分只认评估侧原题快照——伪造来源必须稳定 veto=illegal_source_id:m3、
score10=0；候选回报的 id_map/score/gold/checks 不被信任；**评分结果与未被候选
接触的原题权威评分"逐项相同"**（score10、veto、checks 每条的
id/pts/got/pass/evidence 全量比对——R3#3：不是"veto 对了"就算过，
更不是"帧里出现过伪造 gold 字段"就算过）。

五类反例：
  A1 追加虚构 HER 消息后引用 m3
  A2 删除真实消息（m1）后引用重编号后的 m1（实际指向已删消息）
  A3 改角色（HER→ME）试图混淆来源表
  A4 候选在返回值里自带伪造 id_map（runner 必须无视）
  A5 候选在返回值里夹带伪造 score10/checks/gold 权威字段
     （R3#3 新增：最终评分必须与原题权威评分逐项相同，伪造字段不得出现在
     scoring.json——堵"帧里出现过伪造 gold 就算看过评分"的假证明）
零网络调用（mock 适配器 + 攻击台本走 ctx 注入）。
"""
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
sys.path.insert(0, os.path.join(ROOT, "arena"))
import lb_runner  # noqa: E402

EPISODE = {
    "episode_id": "T_AUTH",
    "turns": [{
        "case_id": "AUTH_t01", "turn_type": "reply",
        "input": {"clock": "2026-10-19 10:00",
                  "messages": [
                      {"role": "HER", "text": "周末去爬山吗", "ts": "2026-10-19 09:58"},
                      {"role": "HER", "text": "天气报说周日晴", "ts": "2026-10-19 09:59"},
                      {"role": "HER", "text": "你去过那边没", "ts": "2026-10-19 10:00"}]},
        "gold": {},
    }],
}

# 攻击回复：引用 m3（原题只有 m0-m2）——按原题评分必须非法
ATTACK_REPLY = json.dumps({
    "response": {"recommended": "虚构来源攻击", "bad_boy": None,
                 "playful": None, "warm": None},
    "directions": ["看评测归看", "更看重哪块", "别被带跑", "发两张看看"],
    "analysis": {"topic_status": "new", "topic_label": "爬山",
                 "scene_facts": [{"text": "她说周日晴", "source_ids": ["m3"]}],
                 "ongoing": []}}, ensure_ascii=False)

# 合法回复：引用真实 m1——权威评分应有非空 checks（证明逐项比较非空转）
CLEAN_REPLY = ATTACK_REPLY.replace('"m3"', '"m1"')

ATTACK_MODES = ["append", "delete", "swap_role", "forge_idmap", "forge_auth"]
MODE_REPLY = {"forge_auth": CLEAN_REPLY}  # 其余模式用 ATTACK_REPLY


MECH = '''# -*- coding: utf-8 -*-
import json
MODE = %(mode)r
REPLY = %(reply)r

def init(assets_root, episode_meta, gateway, sandbox):
    return {"n": 0}

def run_turn(h, ctx, gateway):
    h["n"] += 1
    msgs = ctx["input"].setdefault("messages", [])
    if MODE == "append":
        msgs.append({"role": "HER", "text": "这是候选伪造的消息", "ts": "2026-10-19 10:01"})
    elif MODE == "delete":
        del msgs[1]                      # 删掉真实 m1，使后续重编号
    elif MODE == "swap_role":
        msgs[0]["role"] = "ME"           # 改角色试图混淆来源表
    out = {"reply": REPLY, "notes": "attack:%%s" %% MODE}
    if MODE == "forge_idmap":
        out["id_map"] = {"m0": "HER", "m3": "HER"}   # 伪造来源表（runner 必须无视）
    if MODE == "forge_auth":
        # R3#3：返回值夹带伪造权威字段（score/checks/gold/id_map）——
        # runner 只读 reply/notes，这些字段不得进入评分，也不得改写评分
        out["score10"] = 10.0
        out["veto"] = None
        out["checks"] = [{"id": "C_FORGED", "desc": "forged", "pts": 1,
                          "got": 1, "pass": True, "evidence": "forged"}]
        out["gold"] = {"hacked": True}
        out["id_map"] = {"m0": "ME", "m9": "HER"}
    return out

def snapshot(h):
    return json.dumps(h).encode("utf-8")

def restore(sandbox, snap):
    return json.loads(snap.decode("utf-8"))
'''


def make_candidate(tmp, mode):
    cdir = os.path.join(tmp, "cand_" + mode)
    os.makedirs(os.path.join(cdir, "mechanism"))
    os.makedirs(os.path.join(cdir, "assets", "system_prompt"))
    for f in ("core.md", "format.md", "redline.md", "naturalness_check.md"):
        with open(os.path.join(cdir, "assets", "system_prompt", f), "w",
                  encoding="utf-8") as fh:
            fh.write("test")
    with open(os.path.join(cdir, "mechanism", "mechanism.py"), "w",
              encoding="utf-8") as fh:
        fh.write(MECH % {"mode": mode,
                         "reply": MODE_REPLY.get(mode, ATTACK_REPLY)})
    return cdir


def scoring_diffs(sc, ref, tag):
    """R3#3 真断言：评分结果逐项全量比对（不是只比 veto/score10）。"""
    diffs = []
    if sc.get("score10") != ref.get("score10"):
        diffs.append("%s score10 %s != %s" % (tag, sc.get("score10"), ref.get("score10")))
    if sc.get("veto") != ref.get("veto"):
        diffs.append("%s veto %r != %r" % (tag, sc.get("veto"), ref.get("veto")))
    if sc.get("crash") != ref.get("crash"):
        diffs.append("%s crash %s != %s" % (tag, sc.get("crash"), ref.get("crash")))
    c1, c2 = sc.get("checks") or [], ref.get("checks") or []
    if len(c1) != len(c2):
        diffs.append("%s checks 数量 %d != %d" % (tag, len(c1), len(c2)))
    else:
        for a, b in zip(c1, c2):
            for k in ("id", "desc", "pts", "got", "pass", "evidence"):
                if a.get(k) != b.get(k):
                    diffs.append("%s checks[%s].%s %r != %r"
                                 % (tag, b.get("id"), k, a.get(k), b.get(k)))
    # 伪造权威字段不得泄漏进评分产物
    for leak in ("gold", "id_map", "C_FORGED"):
        s = json.dumps(sc, ensure_ascii=False)
        if leak == "gold" and '"gold"' in s:
            diffs.append("%s scoring.json 泄漏伪造 gold 字段" % tag)
        if leak == "id_map" and '"id_map"' in s:
            diffs.append("%s scoring.json 泄漏伪造 id_map 字段" % tag)
        if leak == "C_FORGED" and "C_FORGED" in s:
            diffs.append("%s scoring.json 泄漏伪造检查点 C_FORGED" % tag)
    return diffs


def main():
    tmp = tempfile.mkdtemp(prefix="lb_auth_")
    ep_path = os.path.join(tmp, "ep.json")
    with open(ep_path, "w", encoding="utf-8") as f:
        json.dump(EPISODE, f, ensure_ascii=False)
    fails = []
    n_checks_compared = 0
    for mode in ATTACK_MODES:
        out = os.path.join(tmp, "out_" + mode)
        cdir = make_candidate(tmp, mode)
        try:
            lb_runner.run_episode(ep_path, cdir, out, "mock")
        except Exception as e:
            fails.append("%s: runner 异常 %s: %s" % (mode, type(e).__name__, e))
            continue
        sc = json.load(open(os.path.join(out, "turn_01_AUTH_t01", "scoring.json"),
                            encoding="utf-8"))
        if MODE_REPLY.get(mode, ATTACK_REPLY) == ATTACK_REPLY:
            # 仅伪造来源 m3 的模式要求稳定 veto；forge_auth 用合法回复，
            # 其断言在逐项比较 + 泄漏检查中体现
            if sc.get("veto") != "illegal_source_id:m3":
                fails.append("%s: 伪造来源未被拦（veto=%s score=%s）"
                             % (mode, sc.get("veto"), sc.get("score10")))
            if sc.get("score10") != 0.0:
                fails.append("%s: 伪造来源仍得分 %s" % (mode, sc["score10"]))
        # R3#3：对未被候选接触的原题直接评分（权威评分），逐项全量比对
        import lb_scorer
        ref = lb_scorer.run_case_scoring(
            EPISODE["turns"][0],
            MODE_REPLY.get(mode, ATTACK_REPLY),
            lb_runner.canonical_id_map(EPISODE["turns"][0]["input"]["messages"]), None)
        fails.extend(scoring_diffs(sc, ref, mode))
        n_checks_compared += len(ref.get("checks") or [])
        if mode == "forge_auth" and len(ref.get("checks") or []) < 3:
            fails.append("forge_auth: 权威评分 checks=%d，逐项比较无代表性"
                         % len(ref.get("checks") or []))
    # 对照组：干净候选（不篡改）引用真实 m1 → 合法，且逐项等于权威评分
    cdir = os.path.join(tmp, "cand_clean")
    os.makedirs(os.path.join(cdir, "mechanism"))
    os.makedirs(os.path.join(cdir, "assets", "system_prompt"))
    for f in ("core.md", "format.md", "redline.md", "naturalness_check.md"):
        open(os.path.join(cdir, "assets", "system_prompt", f), "w",
             encoding="utf-8").write("test")
    with open(os.path.join(cdir, "mechanism", "mechanism.py"), "w",
              encoding="utf-8") as fh:
        fh.write(MECH % {"mode": "none", "reply": CLEAN_REPLY})
    out = os.path.join(tmp, "out_clean")
    lb_runner.run_episode(ep_path, cdir, out, "mock")
    sc = json.load(open(os.path.join(out, "turn_01_AUTH_t01", "scoring.json"),
                        encoding="utf-8"))
    if sc.get("veto") == "illegal_source_id:m1":
        fails.append("clean: 真实来源 m1 被误判非法（对照组失败）")
    import lb_scorer
    ref = lb_scorer.run_case_scoring(
        EPISODE["turns"][0], CLEAN_REPLY,
        lb_runner.canonical_id_map(EPISODE["turns"][0]["input"]["messages"]), None)
    fails.extend(scoring_diffs(sc, ref, "clean"))
    if not (ref.get("checks") or []):
        fails.append("clean: 权威评分 checks 为空，逐项比较为空转（比较无意义）")
    n_checks_compared += len(ref.get("checks") or [])

    shutil.rmtree(tmp, ignore_errors=True)
    if fails:
        print("SCORING AUTHORITY FAIL")
        for x in fails:
            print(" -", x)
        sys.exit(1)
    print("SCORING AUTHORITY PASS：追加/删除/改角色/伪造 id_map/夹带伪造权威字段 五类"
          "篡改均以原题快照评分；runner 评分与原题权威评分逐项全量相同"
          "（共比对 %d 个检查点条目，伪造 score/gold/checks 未泄漏）。"
          % n_checks_compared)


if __name__ == "__main__":
    main()
