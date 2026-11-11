# -*- coding: utf-8 -*-
"""Benchmark V1 · USTC 服务接入检查（指导书 §4.3）。

步骤：① GET /v1/models 核对 deepseek-flash-2 是否存在；② 一次最小 chat/completions
真实请求（复用 arena/api_candidate.build_request_body 唯一出站白名单，无任何额外字段）。
密钥只从环境变量 LB_API_KEY 读取；任何输出/日志/报告不得包含密钥（redact 强制遮盖）。

结论三种：OK / BLOCKED（模型不存在、无权限、网络失败、Key 未设置）/ FAIL。
退出码：0=OK，3=BLOCKED，4=FAIL。禁止静默换模型。
"""
import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "arena"))
import api_candidate  # noqa: E402  唯一出站构造入口（白名单）

REPORT_VERSION = "provider_check_v1"
DEFAULT_BASE = "https://api.llm.ustc.edu.cn/v1"
DEFAULT_MODEL = "deepseek-flash-2"


def redact(text, key):
    """异常/日志遮盖：任何出现的 key 子串替换为 ***REDACTED***。"""
    if not key or not isinstance(text, str):
        return text
    return text.replace(key, "***REDACTED***")


def _err_text(e, key):
    body = ""
    resp = getattr(e, "read", None)
    if callable(resp):
        try:
            body = e.read().decode("utf-8", "replace")[:2000]
        except Exception:
            body = ""
    return redact("%s: %s %s" % (type(e).__name__, e, body), key)


def _http(url, key, payload=None, timeout=60):
    headers = {"Content-Type": "application/json"}
    if key:
        headers["Authorization"] = "Bearer " + key
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8")), r.status


def run_check(base=None, model=None, key=None, do_chat=True):
    """执行检查，返回 (result_dict, exit_code)。key 缺省读环境变量。"""
    base = (base or os.environ.get("LB_API_BASE") or DEFAULT_BASE).rstrip("/")
    model = model or os.environ.get("LB_MODEL") or DEFAULT_MODEL
    key = key if key is not None else os.environ.get("LB_API_KEY", "")
    res = {"report_version": REPORT_VERSION, "base": base, "model": model,
           "key_present": bool(key), "steps": [], "verdict": None, "reason": None}

    def step(name, **kw):
        rec = {"step": name}
        rec.update(kw)
        res["steps"].append(rec)
        return rec

    if not key:
        res["verdict"] = "BLOCKED"
        res["reason"] = ("LB_API_KEY 未设置：旧 key 已在聊天文本中泄露，须由用户在 USTC 平台"
                         "撤销并重新生成，再以环境变量 LB_API_KEY 配置新 key 后重跑本检查。")
        return res, 3

    # ① /v1/models
    try:
        data, status = _http(base + "/models", key)
        ids = [m.get("id") for m in data.get("data", []) if isinstance(m, dict)]
        step("list_models", http=status, n_models=len(ids),
             target_present=model in ids,
             target_matches=[i for i in ids if isinstance(i, str) and model in i])
        if model not in ids:
            near = [i for i in ids if isinstance(i, str) and model in i]
            res["verdict"] = "BLOCKED"
            res["reason"] = ("模型 %r 不在该 Key 的可用模型列表（禁止静默换模型）；"
                             "近似匹配=%r" % (model, near))
            return res, 3
    except urllib.error.HTTPError as e:
        step("list_models", http=e.code, error=_err_text(e, key))
        res["verdict"] = "BLOCKED"
        res["reason"] = "/models 返回 HTTP %d（Key 可能无权限或无效）" % e.code
        return res, 3
    except Exception as e:
        step("list_models", error=_err_text(e, key))
        res["verdict"] = "BLOCKED"
        res["reason"] = "/models 连接失败（网络/服务不可达）"
        return res, 3

    # ② 最小 chat 请求
    if do_chat:
        try:
            body = api_candidate.build_request_body(
                [{"role": "user", "content": "只回复两个字：成功"}],
                model=model, temperature=0.7, stream=False)
            data, status = _http(base + "/chat/completions", key, payload=body)
            choice = (data.get("choices") or [{}])[0]
            msg = choice.get("message") or {}
            step("minimal_chat", http=status,
                 resp_model=data.get("model"), resp_id=data.get("id"),
                 finish_reason=choice.get("finish_reason"),
                 usage_present=bool(data.get("usage")),
                 content_chars=len(msg.get("content") or ""))
            ok = (status == 200 and isinstance(msg.get("content"), str)
                  and choice.get("finish_reason") in ("stop", "length", None, ""))
            res["verdict"] = "OK" if ok else "FAIL"
            if not ok:
                res["reason"] = "最小请求返回形状不符合预期（详见 steps）"
                return res, 4
        except urllib.error.HTTPError as e:
            code = e.code
            step("minimal_chat", http=code, error=_err_text(e, key))
            res["verdict"] = "BLOCKED"
            res["reason"] = "最小请求 HTTP %d（%s）" % (
                code, {401: "未授权", 403: "禁止", 404: "路径/模型不存在",
                       429: "限流"}.get(code, "服务错误"))
            return res, 3
        except Exception as e:
            step("minimal_chat", error=_err_text(e, key))
            res["verdict"] = "BLOCKED"
            res["reason"] = "最小请求失败（网络/超时）"
            return res, 3

    res["verdict"] = "OK"
    return res, 0


def write_report(res, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "provider_check.json"), "w", encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=2)
    lines = ["# API 兼容性检查报告（%s）" % REPORT_VERSION, "",
             "- base: `%s`" % res["base"],
             "- 目标模型: `%s`" % res["model"],
             "- Key 状态: %s" % ("已配置（内容不入任何报告）" if res["key_present"] else "未配置"),
             "- **结论: %s**" % res["verdict"],
             ("- 原因: %s" % res["reason"]) if res.get("reason") else "", ""]
    for s in res["steps"]:
        lines.append("## step: %s" % s["step"])
        for k, v in s.items():
            if k != "step":
                lines.append("- %s: `%s`" % (k, v))
        lines.append("")
    with open(os.path.join(out_dir, "API_COMPATIBILITY_REPORT.md"), "w",
              encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    res, code = run_check()
    write_report(res, out_dir)
    print("verdict=%s reason=%s" % (res["verdict"], res.get("reason")))
    sys.exit(code)


if __name__ == "__main__":
    main()
