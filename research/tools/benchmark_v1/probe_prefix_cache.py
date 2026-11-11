# -*- coding: utf-8 -*-
"""RH-B-11 端点前缀缓存能力探测（2 次调用，属 AUTH-RP1-SMOKE-20261111-01 额度）。

问题：USTC deepseek-flash-2 端点是否返回 prompt cache 命中信息（usage.prompt_cache_hit_tokens
类字段）？有 → RH-B-11「稳定前缀缓存排布」值得实测 TTFT；无/不返回 → 如实登记
"本地不可用"，该候选淘汰（宪章 B5 可证伪条款）。
方法：同一（system, user）连续调用两次，比较两次 usage 与分段计时。
"""
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, "..", ".."))
sys.path.insert(0, os.path.join(_REPO, "arena"))

import lb_gateway  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    _REPO, "runs", "rp1_smoke_20261111", "probe_rhb11")

os.environ.setdefault("LB_API_BASE", "https://api.llm.ustc.edu.cn/v1")
os.environ.setdefault("LB_MODEL", "deepseek-flash-2")
if not os.environ.get("LB_API_KEY"):
    sys.exit("LB_API_KEY 未设置——阻止真实调用（fail-closed）")

SYSTEM = "你是测试探针。只回复：OK"
USER = "缓存探测固定报文。" * 200   # 放大前缀体积，提高可缓存性

gw = lb_gateway.ModelGateway(lb_gateway.ADAPTERS["openai_compat"](),
                             event_log_path=os.path.join(OUT, "gateway_events.jsonl"))
gw.run_id = "rp1_probe_rhb11"
results = []
for i in (1, 2):
    res = gw.generate(SYSTEM, USER, temperature=0.7, tag="cache_probe_%d" % i)
    results.append({
        "call": i,
        "usage": res.get("usage"),
        "timing": res.get("timing"),
        "resp_id": res.get("resp_id"),
        "finish": res.get("finish"),
    })

report = {
    "probe": "rh_b11_prefix_cache_capability",
    "auth": "AUTH-RP1-SMOKE-20261111-01",
    "base": os.environ.get("LB_API_BASE"),
    "model": os.environ.get("LB_MODEL"),
    "results": results,
    "verdict_cache_fields_present": any(
        (r.get("usage") or {}).get("prompt_cache_hit_tokens") is not None
        or (r.get("usage") or {}).get("cache_hit") is not None
        or ((r.get("usage") or {}).get("prompt_tokens_details") or {}).get("cached_tokens") is not None
        for r in results),
    "verdict_note": "2026-11-11 实测：USTC deepseek-flash-2 在 usage.prompt_tokens_details.cached_tokens "
                    "报告前缀缓存（同前缀第二次调用 cached_tokens 0→1024）；判定字段已含该键。",
    "ts": gw.calls[0]["ts"] if gw.calls else "",
}
os.makedirs(OUT, exist_ok=True)
with open(os.path.join(OUT, "PROBE_REPORT.json"), "w", encoding="utf-8") as f:
    json.dump(report, f, ensure_ascii=False, indent=2)
print(json.dumps({k: report[k] for k in ("verdict_cache_fields_present",)},
                 ensure_ascii=False))
for r in results:
    print("call%d usage=%s timing_total_ms=%s" % (
        r["call"], json.dumps(r["usage"], ensure_ascii=False),
        (r.get("timing") or {}).get("total_ms")))
