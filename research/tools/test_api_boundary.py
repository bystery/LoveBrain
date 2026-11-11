# -*- coding: utf-8 -*-
"""test_api_boundary.py — 模型返回物边界反例（回复只当数据，不执行）。

权威口径：refs/arena_v1/SCOPE_DECISION_API_CANDIDATE_20261022.md §2（v2）。
harness 是参赛对象、可变；本测试只锁"API 返回物不被执行、无工具通道"。
零真实模型调用（本地假上游 + mock 适配器）。

7 项，逐项断言"实际行为"，不看文案：
  T1 出站请求体没有 tool 通道（真实构造请求体 + 捕获出站字节）
  T2 回复里的可执行代码不执行（canary 不落盘、原文逐字不变）
  T3 JSON 里的命令字段只是字符串数据（不被当命令用）
  T4 超长/非 JSON/非字符串回复原样留存，不崩、不求值
  T5 越界请求体一律拒绝（tools/functions/tool_choice/未知键/空 messages）
  T6 边界模块与试考入口源码内无求值原语（eval/exec/compile/pickle/subprocess）
  T7 端到端一轮：turn 目录有 reply_ingest.json 且 manifest 与原文一致
"""
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "arena"))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import api_candidate                                     # noqa: E402
import lb_runner                                          # noqa: E402
import lb_scorer                                          # noqa: E402
import lb_gateway                                         # noqa: E402


def check(name, fn, results):
    try:
        detail = fn()
        results.append((name, True, detail or ""))
        print("  [PASS] %s %s" % (name, (detail or "")[:120]))
    except AssertionError as e:
        results.append((name, False, str(e)))
        print("  [FAIL] %s %s" % (name, str(e)[:160]))
    except Exception as e:                                # noqa: BLE001
        results.append((name, False, "%s: %s" % (type(e).__name__, e)))
        print("  [FAIL] %s %s: %s" % (name, type(e).__name__, str(e)[:150]))


# ---------- T1 出站无 tool 通道 ----------

class _FakeResp(object):
    def __init__(self, payload):
        self._p = payload

    def read(self):
        return json.dumps(self._p).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


def t1_no_tool_channel():
    captured = {}
    real = urllib.request.urlopen

    def fake(req, timeout=None):
        captured["body"] = json.loads(req.data.decode("utf-8"))
        captured["headers"] = dict(req.headers)
        return _FakeResp({"id": "chatcmpl-test", "model": "dsf",
                          "choices": [{"message": {"content": "{\"ok\":1}"},
                                       "finish_reason": "stop"}],
                          "usage": {"prompt_tokens": 3,
                                    "completion_tokens": 2}})

    os.environ.setdefault("LB_API_BASE", "http://127.0.0.1:9/v1")
    os.environ.setdefault("LB_MODEL", "dsf")
    os.environ.setdefault("LB_API_KEY", "local")
    urllib.request.urlopen = fake
    try:
        ad = lb_gateway.OpenAICompatAdapter()
        out = ad.generate("SYS", "USER", {"temperature": None})
    finally:
        urllib.request.urlopen = real
    body = captured["body"]
    # 出站字节里出现这些键 = 给了考生工具能力，直接判失败
    for k in api_candidate.TOOL_CHANNEL_KEYS + api_candidate.CAPABILITY_KEYS:
        assert k not in body, "出站请求体出现能力字段 %r" % k
    assert set(body) == set(api_candidate.REQUEST_BODY_ALLOWED_KEYS), \
        "出站键集越界: %s" % sorted(body)
    assert body["temperature"] == 0.7, "None 必须归一到 0.7，实际 %r" % body["temperature"]
    assert out["text"] == "{\"ok\":1}", "回复文本被改写: %r" % out["text"]
    return "keys=%s temp=0.7 text_intact" % sorted(body)


# ---------- T2 代码不执行 ----------

def t2_no_execution(tmp):
    canary = os.path.join(tmp, "canary_executed.txt")
    payload = (
        "好的，这是你要的方案：\n```python\n"
        "import os\n"
        "open(r'%s','w').write('pwned')\n"
        "```\n"
        "另外 {\"response\": {\"warm\": \"%s\"}}" % (canary, canary))
    td = os.path.join(tmp, "turn_T2")
    info = api_candidate.store_reply(td, payload)
    assert not os.path.exists(canary), "回复里的代码被执行了（canary 落盘）"
    stored = open(os.path.join(td, "reply_raw.txt"), encoding="utf-8",
                  newline="").read()
    assert stored == payload, "原文未逐字保存（长度 %d vs %d）" % (len(stored),
                                                                len(payload))
    turn = {"case_id": "T2", "turn_type": "reply",
            "input": {"messages": [{"id": "m0", "role": "HER",
                                    "text": "在吗", "ts": "2026-10-22 09:00"}]},
            "gold": {}}
    id_map = lb_runner.canonical_id_map(turn["input"]["messages"])
    sc = lb_scorer.run_case_scoring_safe(turn, payload, id_map, None)
    assert isinstance(sc, dict) and "score10" in sc, "评分器未返回结构化结果"
    assert not os.path.exists(canary), "评分路径执行了回复里的代码"
    return "canary_absent kind=%s score10=%s" % (info["kind"], sc["score10"])


# ---------- T3 命令字段只是数据 ----------

def t3_command_is_data(tmp):
    canary = os.path.join(tmp, "canary_cmd.txt")
    obj = {"response": {"warm": "hi"},
           "command": "cmd /c echo pwn > %s" % canary,
           "file": canary,
           "path": "..\\..\\arena\\lb_scorer.py"}
    info = api_candidate.ingest_reply(json.dumps(obj, ensure_ascii=False))
    assert info["kind"] == "json", "合法 JSON 应分类为 json，实际 %s" % info["kind"]
    parsed = info["parsed"]
    assert isinstance(parsed, dict), "解析结果应为 dict"
    assert isinstance(parsed["command"], str), "command 必须是字符串数据"
    assert not os.path.exists(canary), "command 字段被当成命令执行了"
    assert "lb_scorer.py" in parsed["path"], "path 字段被改写（应原样保留为字符串）"
    return "kind=json command_is_str no_side_effect"


# ---------- T4 畸形容复留存 ----------

def t4_degenerate_replies(tmp):
    big = "x" * (api_candidate.MAX_REPLY_CHARS + 10)
    cases = [("non_text", None, 0),
             ("too_large", big, api_candidate.MAX_REPLY_CHARS + 10),
             ("text", "这不是 JSON，只有中文散文。", len("这不是 JSON，只有中文散文。"))]
    got = []
    for want, payload, want_chars in cases:
        info = api_candidate.ingest_reply(payload)
        assert info["kind"] == want, "%s: 分类=%s" % (want, info["kind"])
        assert info["chars"] == want_chars, \
            "%s: chars=%d 期望 %d" % (want, info["chars"], want_chars)
        got.append(info["kind"])
    td = os.path.join(tmp, "turn_T4")
    api_candidate.store_reply(td, big)
    on_disk = os.path.getsize(os.path.join(td, "reply_raw.txt"))
    assert on_disk >= len(big), "超长回复被截断（%d < %d）" % (on_disk, len(big))
    return "kinds=%s oversized_kept=%d" % (got, on_disk)


# ---------- T5 越界请求体 ----------

def t5_request_body_refused():
    base = [{"role": "system", "content": "s"},
            {"role": "user", "content": "u"}]
    bads = [
        {"tools": []},
        {"functions": []},
        {"tool_choice": "auto"},
        {"parallel_tool_calls": True},
        {"code_interpreter": {}},
        {"response_format": {"type": "json_object"}},
        {"seed": 1},
    ]
    refused = []
    for extra in bads:
        body = {"model": "dsf", "messages": base, "temperature": 0.7,
                "stream": False}
        body.update(extra)
        try:
            api_candidate.check_request_body(body)
        except api_candidate.BoundaryViolation:
            refused.append(sorted(extra)[0])
        else:
            raise AssertionError("越界键 %r 未被拒绝" % sorted(extra))
    for body in ({"model": "dsf", "messages": []},
                 {"model": "dsf", "messages": [{"role": "assistant",
                                                "content": "x"}]},
                 {"model": "dsf", "messages": [
                     {"role": "user", "content": "u", "tool_call_id": "1"}]}):
        try:
            api_candidate.check_request_body(body)
        except api_candidate.BoundaryViolation:
            pass
        else:
            raise AssertionError("非法 messages 形态未被拒绝: %r" % body)
    good = api_candidate.build_request_body(base, "dsf", None)
    assert good["temperature"] == 0.7
    return "refused=%d keys=%s" % (len(refused), refused)


# ---------- T6 源码无求值原语 ----------

FORBIDDEN_SOURCE = [
    (r"\bexec\s*\(", "exec("),
    (r"\beval\s*\(", "eval("),
    (r"(?<!re\.)\bcompile\s*\(", "compile("),
    (r"pickle\.loads", "pickle.loads"),
    (r"os\.system", "os.system"),
    (r"subprocess\.", "subprocess."),
    (r"importlib", "importlib"),
    (r"__import__", "__import__"),
]


def t6_no_eval_primitives():
    targets = ["arena/api_candidate.py", "tools/api_trial.py"]
    scanned = []
    for rel in targets:
        p = os.path.join(ROOT, rel)
        assert os.path.isfile(p), "缺文件 %s" % rel
        src = open(p, encoding="utf-8").read()
        # 去掉注释与 docstring 里的说明性文字，只查代码行
        code_lines = [ln for ln in src.splitlines()
                      if not ln.strip().startswith("#")]
        code = "\n".join(code_lines)
        for pat, label in FORBIDDEN_SOURCE:
            assert not re.search(pat, code), "%s 出现求值原语 %s" % (rel, label)
        scanned.append(rel)
    return "scanned=%s" % scanned


# ---------- T7 端到端一轮 ----------

def t7_end_to_end(tmp):
    out = os.path.join(tmp, "out")
    env = dict(os.environ)
    env.pop("LB_SANDBOX", None)
    r = subprocess.run(
        [sys.executable, "-B", "-X", "utf8", "tools/iso_run.py",
         "--episode", "benchmark/episodes/E001.json",
         "--candidate", "reference_product_v1",
         "--out", out, "--adapter", "mock", "--stop-after", "1"],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8",
        errors="replace", env=env, timeout=600)
    assert r.returncode == 0, "iso_run rc=%d tail=%s" % (
        r.returncode, (r.stdout or "")[-300:] + (r.stderr or "")[-300:])
    turns = [d for d in os.listdir(out) if d.startswith("turn_")]
    assert len(turns) == 1, "应产 1 个 turn 目录，实际 %s" % turns
    td = os.path.join(out, turns[0])
    for fn in ("reply_raw.txt", "reply_ingest.json", "manifest.json",
               "scoring.json", "calls.json"):
        assert os.path.isfile(os.path.join(td, fn)), "缺证据文件 %s" % fn
    ingest = json.load(open(os.path.join(td, "reply_ingest.json"),
                            encoding="utf-8"))
    man = json.load(open(os.path.join(td, "manifest.json"), encoding="utf-8"))
    raw = open(os.path.join(td, "reply_raw.txt"), encoding="utf-8",
               newline="").read()
    assert ingest["kind"] == "json", "mock 回复应为 json，实际 %s" % ingest["kind"]
    assert man.get("reply_ingest") == ingest["kind"], \
        "manifest.reply_ingest 与证据不一致"
    assert len(raw) == ingest["chars"], "原文长度与分类记录不一致"
    return "turn=%s kind=%s chars=%d" % (turns[0], ingest["kind"], ingest["chars"])


# ---------- T8 泄漏探测器自身要有牙（负对照） ----------

def t8_leak_detector_has_teeth():
    import api_trial
    ep = api_trial.load_episode("E001")
    turns = ep["turns"]
    # ① 未来的 HER/ME 文本必须被检出
    future = None
    for j in range(2, len(turns)):
        for m in turns[j]["input"]["messages"]:
            txt = (m.get("text") or "").strip()
            if len(txt) >= 12 and txt not in api_trial._visible_text(ep, 0):
                future = txt
                break
        if future:
            break
    assert future, "构造不出未来轮次文本样本"
    leaks = api_trial.leakage_report(
        ep, 0, {"messages": [{"role": "user", "content": future}]}, [])
    assert any(l["kind"] == "future_turn" for l in leaks), \
        "未来轮次文本未被检出（探测器失效）"
    # ② 本轮合法可见文本不得误报
    visible_txt = turns[0]["input"]["messages"][-1]["text"].strip()
    leaks2 = api_trial.leakage_report(
        ep, 0, {"messages": [{"role": "user", "content": visible_txt}]}, [])
    assert not leaks2, "合法可见文本被误报为泄漏: %s" % leaks2[:2]
    # ③ 评分卡独家文本必须被检出
    card = "评分卡独家判定语句：此人尚未承认任何事实也不应出现在输入里"
    leaks3 = api_trial.leakage_report(
        ep, 0, {"messages": [{"role": "user", "content": card}]}, [card])
    assert any(l["kind"] == "grading_card" for l in leaks3), \
        "评分卡文本未被检出（探测器失效）"
    return "future_detected visible_clean card_detected"


def main():
    results = []
    tmp_root = tempfile.mkdtemp(prefix="api_boundary_")
    check("T1_outbound_no_tool_channel", t1_no_tool_channel, results)
    check("T2_reply_code_not_executed", lambda: t2_no_execution(tmp_root), results)
    check("T3_command_field_is_data", lambda: t3_command_is_data(tmp_root), results)
    check("T4_degenerate_replies_kept", lambda: t4_degenerate_replies(tmp_root),
          results)
    check("T5_request_body_allowlist", t5_request_body_refused, results)
    check("T6_no_eval_primitives", t6_no_eval_primitives, results)
    check("T7_end_to_end_one_turn", lambda: t7_end_to_end(tmp_root), results)
    check("T8_leak_detector_has_teeth", t8_leak_detector_has_teeth, results)
    fails = [r for r in results if not r[1]]
    print("api_candidate_boundary: %d/%d PASS" % (len(results) - len(fails),
                                                  len(results)))
    if fails:
        for name, _, detail in fails:
            print("  !! %s: %s" % (name, detail[:200]))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
