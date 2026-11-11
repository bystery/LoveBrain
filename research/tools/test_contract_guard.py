# -*- coding: utf-8 -*-
"""tools/test_contract_guard.py — window_expand_b_norecap_guard 输出契约守卫的纯离线单测。

零模型调用：只加载候选 mechanism/contract_guard.py 与其 _vendor，喂固定文本。
覆盖（W17b 阶段一验收）：
  围栏 JSON / 弯引号 / bad_but→bad_boy / bad_bo→bad_boy / 5 条 directions 截断 /
  directions 补 null / action→response / 空 response / 干净文本逐字节不变 /
  repair 失败原样返回 / 不可映射键全文保全 / 规范槽冲突保全 / 合法 no_reply 不误报 /
  _vendor 与 action_policy_r9_slim 逐字节相同 / mechanism 接线（notes 与 reply）。

运行：python tools/test_contract_guard.py
"""
import hashlib
import importlib.util as ilu
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAND = os.path.join(ROOT, "candidates", "window_expand_b_norecap_guard")
VENDOR_PARENT = os.path.join(ROOT, "candidates", "action_policy_r9_slim",
                             "mechanism", "_vendor")
fails = []
checks = [0]


def check(name, ok, detail=""):
    checks[0] += 1
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else " | " + str(detail)[:300]))
    if not ok:
        fails.append(name)


def load(name, path):
    spec = ilu.spec_from_file_location(name, path)
    mod = ilu.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def sha(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


guard = load("wbng_guard_under_test",
             os.path.join(CAND, "mechanism", "contract_guard.py"))


def run(raw):
    return guard.repair_and_audit(raw)


def parse(text):
    try:
        return json.loads(text)
    except Exception:                       # noqa: BLE001
        return None


def raw_reply(resp, directions=None, analysis=None, **top):
    obj = {"response": resp,
           "directions": [None, None, None, None] if directions is None else directions,
           "analysis": {} if analysis is None else analysis}
    obj.update(top)
    return json.dumps(obj, ensure_ascii=False)


def canonical_resp(**over):
    resp = {"recommended": "A", "bad_boy": "B", "playful": "C", "warm": "D"}
    resp.update(over)
    return resp


# ---- 1. 围栏 JSON ----
raw = "```json\n" + raw_reply(canonical_resp()) + "\n```"
text, meta = run(raw)
p = parse(text)
check("1/围栏 JSON 解析成功", p is not None and p["response"]["recommended"] == "A", text)
check("1/围栏 JSON 触发重写", meta["changed"] is True and meta["unparseable"] is False, meta)

# ---- 2. 弯引号（结构引号全为 “） ----
raw = ('{"response":{"recommended":"好","bad_boy":null,"playful":null,"warm":null},'
       '"directions":[null,null,null,null],"analysis":{}}').replace('"', "\u201c")
text, meta = run(raw)
p = parse(text)
check("2/弯引号解析成功且正文保留",
      p is not None and p["response"]["recommended"] == "好", text)
check("2/弯引号不再 unparseable", meta["unparseable"] is False, meta)

# ---- 3. bad_but → bad_boy ----
raw = raw_reply({"recommended": "A", "bad_but": "B", "playful": "C", "warm": "D"})
text, meta = run(raw)
p = parse(text)
check("3/bad_but 映射到 bad_boy",
      p is not None and p["response"].get("bad_boy") == "B"
      and "bad_but" not in p["response"], text)
check("3/bad_but 记录 style_key_map",
      any(m["from"] == "bad_but" and m["to"] == "bad_boy" for m in meta["style_key_map"]), meta)

# ---- 4. bad_bo → bad_boy ----
raw = raw_reply({"recommended": "A", "bad_bo": "B", "playful": "C", "warm": "D"})
text, meta = run(raw)
p = parse(text)
check("4/bad_bo 映射到 bad_boy",
      p is not None and p["response"].get("bad_boy") == "B"
      and "bad_bo" not in p["response"], text)

# ---- 5. 5 条 directions 截断 ----
raw = raw_reply(canonical_resp(), directions=["1", "2", "3", "4", "5"])
text, meta = run(raw)
p = parse(text)
check("5/directions 截到 4 条",
      p is not None and p["directions"] == ["1", "2", "3", "4"], text)
check("5/第 5 条全文进 meta",
      meta["extra_directions_preserved"] == ["5"], meta)

# ---- 6. directions 少于 4 条补 null ----
raw = raw_reply(canonical_resp(), directions=["1"])
text, meta = run(raw)
p = parse(text)
check("6/directions 补 null 到 4 条",
      p is not None and p["directions"] == ["1", None, None, None], text)

# ---- 7. action（对象）→ response ----
raw = raw_reply({"recommended": "A", "bad_boy": "B"}, directions=[None] * 4)
raw_obj = json.loads(raw)
raw_obj.pop("response")
raw_obj["action"] = {"recommended": "A", "bad_boy": "B"}
raw = json.dumps(raw_obj, ensure_ascii=False)
text, meta = run(raw)
p = parse(text)
check("7/action 对象改名为 response",
      p is not None and isinstance(p.get("response"), dict)
      and "action" not in p and p["response"].get("recommended") == "A", text)
check("7/缺槽补 null 且记录规则",
      p is not None and p["response"].get("playful") is None
      and meta["action_to_response"] is True, meta)

# ---- 8. 空 response：原文本返回 + meta 标记，不伪造 ----
raw = '{"response":{},"directions":[],"analysis":{}}'
text, meta = run(raw)
check("8/空 response 原文本逐字节返回", text == raw, text)
check("8/空 response 标记且未编造",
      meta["empty_response"] is True and meta["changed"] is False
      and parse(text)["response"] == {}, meta)

# ---- 9. 干净文本逐字节不变 ----
raw = ('{"response": {"recommended": "A", "bad_boy": null, "playful": "C", '
       '"warm": "D"}, "directions": ["d1", null, null, null], "analysis": {}}')
text, meta = run(raw)
check("9/干净文本逐字节不变", text == raw, text)
check("9/干净文本 changed=False 且无规则",
      meta["changed"] is False and meta["rules"] == [], meta)

# ---- 10. repair 失败：原样返回 ----
raw = "我刚到家，先不聊了，你也早点睡。"
text, meta = run(raw)
check("10/不可解析文本原样返回", text == raw, text)
check("10/不可解析标记", meta["unparseable"] is True and meta["changed"] is False, meta)

# ---- 11. 映射不上的键：全文进 meta，并从契约 JSON 移除 ----
raw = raw_reply({"recommended": "A", "cute": "B", "bad_boy": None,
                 "playful": None, "warm": None})
text, meta = run(raw)
p = parse(text)
check("11/不可映射键从 JSON 移除、正文保留在 meta",
      p is not None and "cute" not in p["response"]
      and any(u.get("key") == "cute" and u.get("value") == "B"
              for u in meta["unmapped_preserved"]), meta)

# ---- 12. 规范槽非空时绝不覆盖别名正文 ----
raw = raw_reply({"recommended": "A", "bad_boy": "KEEP", "bad_but": "DUP",
                 "playful": "C", "warm": "D"})
text, meta = run(raw)
p = parse(text)
check("12/规范槽非空时别名值保全不覆盖",
      p is not None and p["response"]["bad_boy"] == "KEEP"
      and "bad_but" not in p["response"]
      and any(u.get("value") == "DUP" for u in meta["unmapped_preserved"]), meta)

# ---- 13. 合法 action=no_reply 不误报空回复 ----
raw = json.dumps({"action": "no_reply", "decline_reason": "她说明天早起"},
                 ensure_ascii=False)
text, meta = run(raw)
check("13/合法 no_reply 原样返回且不标空回复",
      text == raw and meta["no_reply_action"] is True
      and meta["empty_response"] is False, meta)

# ---- 14. _vendor 与 action_policy_r9_slim 逐字节相同 ----
for fn in ("output_repair.py", "normalize_reply_contract.py"):
    a = os.path.join(VENDOR_PARENT, fn)
    b = os.path.join(CAND, "mechanism", "_vendor", fn)
    check("14/_vendor %s 逐字节相同" % fn, sha(a) == sha(b), "%s vs %s" % (a, b))

# ---- 15. mechanism 接线：reply 被修复、notes 记 guard_changed/rules ----
mech = load("wbng_mechanism_under_test",
            os.path.join(CAND, "mechanism", "mechanism.py"))
BAD_REPLY = raw_reply({"recommended": "A", "bad_but": "B", "playful": "C", "warm": "D"})


class CaptureGateway:
    def __init__(self, reply):
        self.reply = reply
        self.calls = []

    def generate(self, system, prompt, images=None, tag="main"):
        self.calls.append({"system": system, "prompt": prompt, "tag": tag})
        return {"text": self.reply}


with tempfile.TemporaryDirectory(prefix="wbng_guard_test_") as tmp:
    gw = CaptureGateway(BAD_REPLY)
    h = mech.init(os.path.join(CAND, "assets"),
                  {"persona": {}, "initial_kb": {}}, gw, tmp)
    out = mech.run_turn(h, {"input": {"messages": [
        {"role": "HER", "text": "在吗", "ts": "2026-11-02 20:00"}],
        "clock": "2026-11-02 20:01"}, "turn_index": 1}, gw)
    p = parse(out["reply"])
    check("15/mechanism 返回修复后的 reply",
          p is not None and p["response"].get("bad_boy") == "B", out.get("reply"))
    check("15/notes 含 guard_changed=1 与 guard_rules",
          "guard_changed=1" in out["notes"] and "guard_rules=" in out["notes"],
          out.get("notes"))
    check("15/guard_rules 不超过 120 字符",
          len(out["notes"].split("guard_rules=")[1].rstrip("]")) <= 120, out.get("notes"))
    check("15/审计 meta 挂到输出",
          isinstance(out.get("contract_guard"), dict)
          and out["contract_guard"].get("changed") is True, out.get("contract_guard"))

print("\n%d checks, %d failed" % (checks[0], len(fails)))
if fails:
    print("FAILED:", fails)
    sys.exit(1)
print("ALL PASS")
sys.exit(0)
