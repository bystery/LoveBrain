# -*- coding: utf-8 -*-
"""test_input_boundary.py — 判决令 §2.1 验收 + 第五波判决 §7/§10 验收：
候选输入边界白名单反例验证。

用带独特隐藏标记（XXHIDDENMARKERXX）的假 episode 走完整 runner 路径（mock 适配器），
断言：
  1) 作者 meta / gaps / 隐藏原因不出现在候选可见对象、init 负载、网关请求、沙箱文件；
  2) persona / 消息 ts / may_decline 这三类"应见信息"确实到达；
  3) 第五波 §7：嵌套在 messages[i].meta / intent.hidden / kb 未知键内部的作者元数据
     同样被剥掉（深重建按允许字段），且候选可见对象与评估侧不再共享可变对象（深拷贝）；
  4) 第五波 §10：媒体单向登记（input.media.images 登记了但消息未引用）= fail-fast，
     图片不会静默丢失。
这是接口级验证，不以禁词扫描代替（标记是唯一可信探针）。"""
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "arena"))
import lb_runner  # noqa: E402

MARK = "XXHIDDENMARKERXX"
IMG = os.path.join("benchmark", "media", "pilot", "L1_t07_phone.png").replace("\\", "/")

FAKE_EP = {
    "episode_id": "BOUNDARY_TEST",
    "note": "布景备注 " + MARK,
    "city": "某市 " + MARK,
    "relation_stage": "测试 " + MARK,
    "gaps": [{"from": "2026-10-01 10:00", "to": "2026-10-02 10:00",
              "reason": "补觉+家务 " + MARK}],
    "persona": {"关系": "高中同学", "她坐标": "苏州", "我坐标": "合肥"},
    "initial_kb": {},
    "turns": [
        {"case_id": "B_t01",
         "input": {"clock": "2026-10-05 21:47",
                   "messages": [{"role": "HER", "text": "在吗", "ts": "2026-10-05 21:41",
                                 # 第五波 §7：藏在消息容器内部的作者元数据
                                 "meta": {"hidden": MARK, "strategy": "正确策略 " + MARK}}],
                   "intent": {"enabled": True, "text": "正常意图",
                              # 第五波 §7：藏在意图容器内部的作者元数据
                              "hidden": {"strategy": MARK}},
                   "kb": {"me": "研一在读", "evil_unknown_key": MARK},
                   "may_decline": True},
         "meta": {"note": "作者备注 " + MARK, "strategy": "正确策略 " + MARK},
         "gold": {"expect_decline": True}},
    ],
}


def scan(obj, mark=MARK):
    """递归找标记，返回出现的路径列表。"""
    hits = []
    def walk(o, path):
        if isinstance(o, str):
            if mark in o:
                hits.append(path)
        elif isinstance(o, dict):
            for k, v in o.items():
                walk(v, path + "." + str(k))
        elif isinstance(o, (list, tuple)):
            for i, v in enumerate(o):
                walk(v, "%s[%d]" % (path, i))
    walk(obj, "$")
    return hits


def main():
    fails = []
    # 1) sanitize_turn：meta/gold 不得出现（含嵌套容器内部）
    ctx = lb_runner.sanitize_turn(FAKE_EP["turns"][0])
    hits = scan(ctx)
    if hits:
        fails.append("sanitize_turn 泄漏(含嵌套): %s" % hits)
    if "meta" in ctx or "gold" in ctx:
        fails.append("sanitize_turn 顶层键含 meta/gold: %s" % list(ctx.keys()))
    if not ctx.get("input", {}).get("may_decline"):
        fails.append("may_decline 未透传（应见信息丢失）")
    if ctx["input"]["messages"][0].get("ts") != "2026-10-05 21:41":
        fails.append("消息 ts 未透传（应见信息丢失）")
    if ctx["input"].get("kb", {}).get("me") != "研一在读":
        fails.append("kb 已知字段未透传（应见信息丢失）")

    # 2) 第五波 §7：深拷贝——候选侧修改不得影响评估侧原对象
    orig_text = FAKE_EP["turns"][0]["input"]["messages"][0]["text"]
    ctx["input"]["messages"][0]["text"] = "被候选篡改"
    ctx["input"].setdefault("kb", {})["me"] = "被候选篡改"
    if FAKE_EP["turns"][0]["input"]["messages"][0]["text"] != orig_text:
        fails.append("消息对象与评估侧共享（无深拷贝）：候选可改写评估侧原数据")
    if FAKE_EP["turns"][0]["input"]["kb"]["me"] != "研一在读":
        fails.append("kb 对象与评估侧共享（无深拷贝）")

    # 3) ep_meta 白名单：gaps/note/city/relation_stage 不得出现；persona 必须在
    ep = json.loads(json.dumps(FAKE_EP, ensure_ascii=False))
    ep_meta = {k: v for k, v in ep.items() if k in lb_runner.EP_META_VISIBLE_KEYS}
    hits = scan(ep_meta)
    if hits:
        fails.append("ep_meta 泄漏: %s" % hits)
    if "gaps" in ep_meta or "note" in ep_meta:
        fails.append("ep_meta 含 gaps/note: %s" % list(ep_meta.keys()))
    if not ep_meta.get("persona"):
        fails.append("persona 未进入 init 负载（应见信息丢失）")

    # 4) 端到端：mock 跑一轮，扫网关 calls + 落盘文件
    tmp = tempfile.mkdtemp(prefix="lb_boundary_")
    ep_path = os.path.join(tmp, "ep.json")
    with open(ep_path, "w", encoding="utf-8") as f:
        json.dump(FAKE_EP, f, ensure_ascii=False)
    out = os.path.join(tmp, "run")
    os.environ.pop("LB_API_BASE", None)
    m = lb_runner.run_episode(ep_path, "candidates/round_0_baseline", out, "mock")
    leaks = []
    for root, _, files in os.walk(out):
        for fn in files:
            p = os.path.join(root, fn)
            data = open(p, "rb").read()
            if MARK.encode("utf-8") in data and "_sandbox" not in root:
                leaks.append(os.path.relpath(p, out))
            if fn == "calls.json":
                c = json.loads(data.decode("utf-8"))
                for call in c:
                    blob = json.dumps(call, ensure_ascii=False)
                    if MARK in blob:
                        leaks.append(p + " (calls)")
                    if call.get("user") and "高中同学" not in call["user"]:
                        leaks.append(p + " (persona 未进模型请求)")
                    if '"ts"' not in (call.get("user") or ""):
                        leaks.append(p + " (消息 ts 未进模型请求)")
    if leaks:
        fails.append("端到端泄漏/缺失: %s" % leaks)

    # 5) 第五波 §10：媒体单向登记 → fail-fast（图片不静默丢失）
    bad_ep = json.loads(json.dumps(FAKE_EP, ensure_ascii=False))
    bad_ep["turns"][0]["input"]["media"] = {"images": [IMG]}   # 消息未引用
    bad_path = os.path.join(tmp, "ep_bad_media.json")
    with open(bad_path, "w", encoding="utf-8") as f:
        json.dump(bad_ep, f, ensure_ascii=False)
    try:
        lb_runner.run_episode(bad_path, "candidates/round_0_baseline",
                              os.path.join(tmp, "run_bad"), "mock")
        fails.append("媒体单向登记未被拦截（登记未引用应 fail-fast）")
    except ValueError:
        pass

    # 6) 第六波 §5：对象型标量反例 → 入口拒绝（此前零错误放行到达候选）
    # 6a messages[0].text = {"secret": "标记"}
    bad1 = json.loads(json.dumps(FAKE_EP, ensure_ascii=False))
    bad1["turns"][0]["input"]["messages"][0]["text"] = {"secret": "标记"}
    bad1_path = os.path.join(tmp, "ep_bad_text.json")
    with open(bad1_path, "w", encoding="utf-8") as f:
        json.dump(bad1, f, ensure_ascii=False)
    try:
        lb_runner.run_episode(bad1_path, "candidates/round_0_baseline",
                              os.path.join(tmp, "run_bad1"), "mock")
        fails.append("6a: message.text 对象型未被拒绝")
    except ValueError:
        pass
    # 6b intent.expiry = {"secret": "标记"}（intent.text 保持正常字符串）
    bad2 = json.loads(json.dumps(FAKE_EP, ensure_ascii=False))
    bad2["turns"][0]["input"]["intent"] = {"enabled": True, "text": "正常意图",
                                           "expiry": {"secret": "标记"}}
    bad2_path = os.path.join(tmp, "ep_bad_intent.json")
    with open(bad2_path, "w", encoding="utf-8") as f:
        json.dump(bad2, f, ensure_ascii=False)
    try:
        lb_runner.run_episode(bad2_path, "candidates/round_0_baseline",
                              os.path.join(tmp, "run_bad2"), "mock")
        fails.append("6b: intent.expiry 对象型未被拒绝")
    except ValueError:
        pass
    # 6c 对照组：正常中文自由文本不被误杀
    ok_ep = json.loads(json.dumps(FAKE_EP, ensure_ascii=False))
    ok_ep["turns"][0]["input"]["messages"][0]["text"] = "你这人怎么什么都怼，服了"
    ok_ep["turns"][0]["input"]["intent"] = {"enabled": True, "text": "顺着她的损友语气接"}
    ok_path = os.path.join(tmp, "ep_ok.json")
    with open(ok_path, "w", encoding="utf-8") as f:
        json.dump(ok_ep, f, ensure_ascii=False)
    try:
        lb_runner.run_episode(ok_path, "candidates/round_0_baseline",
                              os.path.join(tmp, "run_ok"), "mock")
    except Exception as e:
        fails.append("6c: 正常中文自由文本被误杀（%s: %s）" % (type(e).__name__, e))

    # 7) 第七波 §5：容器本身错型 → 入口拒绝（此前容器类型错误被静默放行）
    container_bads = [
        ("7a messages=字符串", lambda ep: ep["turns"][0]["input"].__setitem__(
            "messages", "在吗 她说")),
        ("7b messages=[字符串]", lambda ep: ep["turns"][0]["input"].__setitem__(
            "messages", ["在吗"])),
        ("7c intent=列表", lambda ep: ep["turns"][0]["input"].__setitem__(
            "intent", ["陪她吐槽"])),
        ("7d clock=对象", lambda ep: ep["turns"][0]["input"].__setitem__(
            "clock", {"ts": "2026-10-05 21:47"})),
        ("7e may_decline=字符串false", lambda ep: ep["turns"][0]["input"].__setitem__(
            "may_decline", "false")),
    ]
    for name, mut in container_bads:
        bad = json.loads(json.dumps(FAKE_EP, ensure_ascii=False))
        mut(bad)
        p = os.path.join(tmp, "ep_%s.json" % name.split()[0])
        with open(p, "w", encoding="utf-8") as f:
            json.dump(bad, f, ensure_ascii=False)
        try:
            lb_runner.run_episode(p, "candidates/round_0_baseline",
                                  os.path.join(tmp, "run_" + name.split()[0]), "mock")
            fails.append("%s: 容器错型未被入口拒绝" % name)
        except ValueError:
            pass
    # 7f 对照：legacy 字符串 intent（版本协议明确保留）不被误杀
    ok2 = json.loads(json.dumps(FAKE_EP, ensure_ascii=False))
    ok2["turns"][0]["input"]["intent"] = "陪她一起吐槽，别讲道理"
    ok2_path = os.path.join(tmp, "ep_ok2.json")
    with open(ok2_path, "w", encoding="utf-8") as f:
        json.dump(ok2, f, ensure_ascii=False)
    try:
        lb_runner.run_episode(ok2_path, "candidates/round_0_baseline",
                              os.path.join(tmp, "run_ok2"), "mock")
    except Exception as e:
        fails.append("7f: legacy 字符串 intent 被误杀（%s: %s）"
                     % (type(e).__name__, e))

    if fails:
        print("BOUNDARY TEST FAIL")
        for x in fails:
            print(" -", x)
        sys.exit(1)
    print("BOUNDARY TEST PASS：meta/gaps/gold/嵌套元数据 零泄漏；深拷贝生效；"
          "persona/消息 ts/may_decline 均到达模型请求；媒体单向登记 fail-fast；"
          "容器错型（messages 字符串/字符串列表/intent 列表/clock 对象/"
          "may_decline 字符串）入口拒绝；legacy 字符串 intent 保留不误杀。")


if __name__ == "__main__":
    main()
