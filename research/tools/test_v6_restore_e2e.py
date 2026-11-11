# -*- coding: utf-8 -*-
r"""tools/test_v6_restore_e2e.py — v6_integrated 跨进程 stop-after/resume 验证（mock 适配器，零真实调用）。

证明的不是"文件存在"，而是：
  1) 第一次进程只跑第 1 轮后退出（--stop-after 1）；
  2) 第二个独立进程 --resume 从 checkpoint 恢复，跑完其余轮；
  3) 第 1 轮写入的事实进入第 3 轮真实出站 prompt（calls.json 可核）；
  4) 第 1 轮与第 3 轮 manifest 的 runner_pid 不同（真的是两个进程）；
  5) 恢复事件出现在 gateway_events.jsonl；最终 episode_manifest complete=true。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe tools\test_v6_restore_e2e.py
"""
import json
import os
import subprocess
import sys
import tempfile
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CAND = os.path.join(ROOT, "candidates", "v6_integrated")
FAILS = []


def check(name, cond, detail=""):
    print("%s %s %s" % ("PASS" if cond else "FAIL", name, detail))
    if not cond:
        FAILS.append(name)


def mock_reply(text="嗯", action="承接", facts=None, signal="中性", goal="无目标"):
    return {"response": {"recommended": text, "bad_boy": None, "playful": None, "warm": None},
            "directions": [None, None, None, None],
            "analysis": {"signal": signal, "goal_category": goal,
                         "action_taken": action, "anchor": "m0",
                         "hook": {"kind": "无", "text": ""},
                         "facts": facts or [], "tentatives": [], "updates": []}}


def turn(cid, her, clock, mock, intent=None):
    return {"case_id": cid, "turn_type": "reply",
            "input": {"messages": [{"role": "HER", "text": her, "ts": clock}],
                      "clock": clock, "intent": intent, "may_decline": False},
            "mock_response": json.dumps(mock, ensure_ascii=False)}


def child(code, env=None):
    e = dict(os.environ)
    e["PYTHONIOENCODING"] = "utf-8"
    if env:
        e.update(env)
    p = subprocess.run([sys.executable, "-c", code], capture_output=True,
                       text=True, encoding="utf-8", env=e, cwd=ROOT)
    if p.returncode != 0:
        print(p.stdout)
        print(p.stderr)
        raise SystemExit("child failed")
    return p.stdout.strip()


def main():
    tmp = tempfile.mkdtemp(prefix="v6_e2e_")
    ep = os.path.join(tmp, "ep.json")
    out = os.path.join(tmp, "out")
    doc = {
        "episode_id": "V6-E2E",
        "persona": {"me": "29岁，男，南京，产品经理", "her": "28岁，女，南京，编辑"},
        "initial_kb": {"her": "喜欢后摇和现场音乐",
                       "ongoing_plan": [{"name": "周六看展（时间待定）", "status": "进行中"}]},
        "turns": [
            turn("V6E2E_T01", "我下周三有空", "2026-11-20 20:00",
                 mock_reply("下周三怎么样", "邀约",
                            [{"text": "她下周三有空", "subject": "HER", "quote": "下周三有空",
                              "src_ids": ["m0"], "certainty": "confirmed"}],
                            signal="积极", goal="推进见面"),
                 {"enabled": True, "text": "想约她", "status": "ACTIVE"}),
            turn("V6E2E_T02", "那下周三见", "2026-11-20 20:10",
                 mock_reply("好 就下周三", "承接", signal="积极")),
            turn("V6E2E_T03", "下周三几点到", "2026-11-20 20:20",
                 mock_reply("七点吧", "承接")),
        ],
    }
    json.dump(doc, open(ep, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    code1 = (
        "import sys, json\n"
        "sys.path.insert(0, r'%s')\n"
        "import lb_runner\n"
        "m=lb_runner.run_episode(r'%s', r'%s', r'%s', 'mock', stop_after=1)\n"
        "print(json.dumps({'turns':m['turns'],'complete':m['complete']}))\n"
        % (os.path.join(ROOT, "arena"), ep, CAND, out))
    code2 = (
        "import sys, json\n"
        "sys.path.insert(0, r'%s')\n"
        "import lb_runner\n"
        "m=lb_runner.run_episode(r'%s', r'%s', r'%s', 'mock', resume=True)\n"
        "print(json.dumps({'turns':m['turns'],'complete':m['complete'],'resumed_from':m['resumed_from']}))\n"
        % (os.path.join(ROOT, "arena"), ep, CAND, out))

    r1 = json.loads(child(code1))
    check("E2E stop-after wrote 1 turn", r1["turns"] == 1 and not r1["complete"], str(r1))
    r2 = json.loads(child(code2))
    check("E2E resume completed 3 turns", r2["turns"] == 3 and r2["complete"]
          and r2["resumed_from"] == 1, str(r2))

    ck = json.load(open(os.path.join(out, "checkpoint.json"), encoding="utf-8"))
    check("E2E checkpoint turns_done=3", ck.get("turns_done") == 3, str(ck))
    m1 = json.load(open(os.path.join(out, "turn_01_V6E2E_T01", "manifest.json"), encoding="utf-8"))
    m3 = json.load(open(os.path.join(out, "turn_03_V6E2E_T03", "manifest.json"), encoding="utf-8"))
    check("E2E two distinct runner processes", m1["runner_pid"] != m3["runner_pid"],
          "%s vs %s" % (m1["runner_pid"], m3["runner_pid"]))

    calls = json.load(open(os.path.join(out, "turn_03_V6E2E_T03", "calls.json"), encoding="utf-8"))
    user = calls[0]["user"] if calls else ""
    check("E2E fact survived restart into turn 3 prompt", "她下周三有空" in user)
    check("E2E fact carries source uid", "m_src[" in user)
    ev = open(os.path.join(out, "gateway_events.jsonl"), encoding="utf-8").read()
    check("E2E restore event persisted", "restore_completed" in ev)
    man = json.load(open(os.path.join(out, "episode_manifest.json"), encoding="utf-8"))
    check("E2E manifest complete", man.get("complete") is True and man.get("turns") == 3)
    check("E2E no real API configured", man.get("adapter") == "mock", str(man.get("adapter")))

    shutil.rmtree(tmp, ignore_errors=True)
    print("---")
    if FAILS:
        print("FAILED: %d checks: %s" % (len(FAILS), FAILS))
        sys.exit(1)
    print("ALL PASS")


if __name__ == "__main__":
    main()
