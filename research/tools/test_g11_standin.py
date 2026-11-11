#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""test_g11_standin.py — G11 真实集成入口 · 本地替身完整演练（R3#8）。

独立复核 §9：G11"未执行时不得 exit=0"。本演练用**本地 OpenAI 兼容 HTTP 替身**
（真实 socket、真实 HTTP 编解码，非 mock 进程内调用）走**同一条隔离链**
（iso_run → trusted_controller → openai_compat 适配器 → 参考基线候选），
验证五件事：
  1. 出站请求携带 model 与 temperature（=0.7，判决令口径），Bearer 认证头在位；
  2. 图片以 image_url/data URL part 进请求（episode L1_t07 真实图片文件）；
  3. 响应 usage 三件套（prompt/completion/total tokens）完整并被账本记录；
  4. 原文不截断：替身返回定长（9000 字符）+ 尾标记，reply_raw 长度与内容逐字一致；
  5. attempt 账本：started 先落盘、每轮唯一 attempt、completed 收口。
结果绑定同一 release 离线证据（release.json sha256 写进演练结果）。
真实上游批次（≤8 attempts）另行按授权清单执行，不在本演练内。
"""
import datetime
import hashlib
import json
import uuid
import os
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))

MODEL = "g11-standin-model"
TEMP = 0.7
CONTENT_LEN = 9000
TAIL = "G11TAIL//OK"

REPLY_CORE = json.dumps({
    "response": {"recommended": "收到，图片看到了，回头细说",
                 "bad_boy": None, "playful": None, "warm": None},
    "directions": ["你拍的这是哪里呀", None, None, None],
    "analysis": {"topic_status": "new", "topic_label": "图片分享",
                 "scene_facts": [{"text": "她发了一张手机照片",
                                  "source_ids": ["m0"]}],
                 "ongoing": []}}, ensure_ascii=False)


class StandinHandler(BaseHTTPRequestHandler):
    server_version = "G11Standin/1.0"
    observed = []          # 每次请求的出站事实（父进程读取）
    lock = threading.Lock()

    def log_message(self, *a):
        pass

    def do_POST(self):
        if not self.path.endswith("/chat/completions"):
            self.send_response(404)
            self.end_headers()
            return
        auth = self.headers.get("Authorization", "")
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        # 1) 出站事实登记
        rec = {"path": self.path,
               "bearer_ok": auth.startswith("Bearer ") and len(auth) > 10,
               "model": body.get("model"),
               "temperature": body.get("temperature"),
               "stream": body.get("stream"),
               "n_messages": len(body.get("messages") or [])}
        user_msg = (body.get("messages") or [{}])[-1]
        content = user_msg.get("content")
        rec["n_image_parts"] = 0
        if isinstance(content, list):
            rec["n_image_parts"] = sum(
                1 for p in content if p.get("type") == "image_url")
            img = next((p for p in content if p.get("type") == "image_url"), None)
            if img:
                rec["image_data_prefix"] = str(
                    img.get("image_url", {}).get("url", ""))[:30]
        with self.lock:
            StandinHandler.observed.append(rec)
        # 定长回复 + 尾标记（验证不截断）
        want = CONTENT_LEN - len(TAIL)
        filler = REPLY_CORE
        while len(filler) < want:
            filler += "‖" + ("详情稍后补充，第 %d 段。" % (len(filler) % 97 + 1)) * 8
        content_out = filler[:want] + TAIL
        assert len(content_out) == CONTENT_LEN
        resp = {"id": "chatcmpl-g11-standin",
                "object": "chat.completion",
                "model": body.get("model"),
                "choices": [{"index": 0,
                             "message": {"role": "assistant",
                                         "content": content_out},
                             "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 1234, "completion_tokens": 567,
                          "total_tokens": 1801}}
        data = json.dumps(resp, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main():
    fails = []
    srv = HTTPServer(("127.0.0.1", 0), StandinHandler)
    port = srv.server_address[1]
    th = threading.Thread(target=srv.serve_forever, daemon=True)
    th.start()

    # 指导书 v2 §4：新建审计目录零删除——每次演练用唯一后缀目录
    out = os.path.join(ROOT, "runs", "_g11_standin_%s" % (
        datetime.datetime.now().strftime("%H%M%S")
        + "_" + uuid.uuid4().hex[:4]))
    env = dict(os.environ)
    env.update({"LB_API_BASE": "http://127.0.0.1:%d/v1" % port,
                "LB_API_KEY": "g11-standin-key-not-real",
                "LB_MODEL": MODEL,
                "PYTHONIOENCODING": "utf-8"})
    # 复核 v3 §2A 入口强制：替身演练同样挂授权级预算权威（本地替身上游，
    # 零真实模型成本）；替身结果不冒充真实集成。
    api_base = "http://127.0.0.1:%d/v1" % port
    auth_id = "AUTH-G11-STANDIN-%s" % (
        datetime.datetime.now().strftime("%H%M%S") + uuid.uuid4().hex[:4])
    auth_path = out + "_authorization.json"
    json.dump({
        "document_type": "G11_STANDIN_TEST_AUTHORIZATION",
        "authorization_id": auth_id,
        "authorized": True, "status": "ACTIVE",
        "real_endpoint": {"api_base": api_base, "model": MODEL,
                          "temperature": 0.7, "max_attempts": 12},
    }, open(auth_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    try:
        r = subprocess.run(
            [sys.executable, "-B", "-X", "utf8", "tools/iso_run.py",
             "--episode", "benchmark/episodes/PILOT_L1_v1.json",
             "--candidate", "reference_product_v1",
             "--out", out, "--adapter", "openai_compat",
             "--authorization", auth_path,
             "--expect-attempts", "7",
             "--stop-after", "7", "--quota", "12"],
            cwd=ROOT, capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=900, env=env)
        if r.returncode != 0:
            fails.append("iso_run rc=%d tail=%s" % (r.returncode,
                                                    (r.stderr or "")[-300:]))
        # 5) attempt 账本（control dir）
        ctrl = os.path.join(out, "_control")
        led_path = os.path.join(ctrl, "attempt_ledger.jsonl")
        attempts = []
        if os.path.isfile(led_path):
            attempts = [json.loads(l) for l in open(led_path, encoding="utf-8")
                        if l.strip()]
        started = [a for a in attempts if a.get("kind") == "attempt_started"]
        finished = [a for a in attempts if a.get("kind") in
                    ("attempt_completed", "attempt_failed")]
        if len(started) != 7:
            fails.append("5: attempt_started=%d（预期 7）" % len(started))
        if len(finished) < len(started):
            fails.append("5: 收口 %d < started %d（失败记录不丢口径）"
                         % (len(finished), len(started)))
        # 3) usage 完整性（任一 completed 事件带 usage 三件套）
        with_usage = [a for a in finished
                      if isinstance(a.get("usage"), dict)
                      and all(isinstance(a["usage"].get(k), int)
                              for k in ("prompt_tokens", "completion_tokens",
                                        "total_tokens"))]
        if not with_usage:
            fails.append("3: 账本未记录完整 usage 三件套")
        obs = list(StandinHandler.observed)
        if len(obs) != 7:
            fails.append("替身收到请求数=%d（预期 7）" % len(obs))
        # 1) model/temperature/bearer
        for i, o in enumerate(obs):
            if o.get("model") != MODEL:
                fails.append("1: 请求#%d model=%r（预期 %s）"
                             % (i, o.get("model"), MODEL))
            if abs((o.get("temperature") or -1) - TEMP) > 1e-9:
                fails.append("1: 请求#%d temperature=%r（预期 %s）"
                             % (i, o.get("temperature"), TEMP))
            if not o.get("bearer_ok"):
                fails.append("1: 请求#%d Bearer 认证头缺失" % i)
        # 2) 图片 part（含图片的轮：L1_t07 至少 1 个 data URL part）
        img_reqs = [o for o in obs if o.get("n_image_parts")]
        if not img_reqs:
            fails.append("2: 无任何请求携带 image part（L1_t07 应有）")
        elif not all(str(o.get("image_data_prefix", "")).startswith("data:image/")
                     for o in img_reqs):
            fails.append("2: image part 非 data URL 形态: %r"
                         % [o.get("image_data_prefix") for o in img_reqs][:3])
        # 4) 原文不截断：每个 turn 的 reply_raw 长度与尾标记
        tdir = None
        for root, _d, files in os.walk(out):
            if "reply_raw.txt" in files:
                p = os.path.join(root, "reply_raw.txt")
                txt = open(p, encoding="utf-8").read()
                if len(txt) != CONTENT_LEN:
                    fails.append("4: %s 长度 %d ≠ %d（截断/改写）"
                                 % (os.path.relpath(p, out), len(txt),
                                    CONTENT_LEN))
                if not txt.endswith(TAIL):
                    fails.append("4: %s 缺尾标记（原文不完整）"
                                 % os.path.relpath(p, out))
        n_replies = sum(1 for root, _d, files in os.walk(out)
                        if "reply_raw.txt" in files)
        if n_replies != 7:
            fails.append("4: reply_raw 数=%d（预期 7）" % n_replies)
    finally:
        srv.shutdown()
        srv.server_close()

    # 绑定同一 release 离线证据
    rel = os.path.join(ROOT, "refs", "arena_v1", "release.json")
    result = {"gate": "G11", "drill": "local_standin_openai_compat",
              "model": MODEL, "temperature": TEMP,
              "requests_observed": len(StandinHandler.observed),
              # V14 收口：字段名与值必须一致——release_sha256 写全量 64 位
              # 散列（此前写 16 位截断导致 verify 全量绑定比对恒 False）
              "release_sha256": hashlib.sha256(
                  open(rel, "rb").read()).hexdigest() if os.path.isfile(rel) else None,
              "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
              "pass": not fails}
    os.makedirs(os.path.join(ROOT, "logs"), exist_ok=True)
    rp = os.path.join(ROOT, "logs", "g11_standin_result.json")
    json.dump(result, open(rp, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    if fails:
        print("G11 STANDIN FAIL")
        for x in fails[:10]:
            print(" -", x)
        return 1
    print("G11 STANDIN PASS：7 轮真实 HTTP 往返；model/温度/Bearer 在位；"
          "图片 data URL part 在位；usage 三件套完整入账；"
          "reply_raw 定长 %d 字符尾标记完整（原文不截断）；attempt 账本 7 started/7 收口；"
          "结果绑定 release sha=%s（%s）"
          % (CONTENT_LEN, result["release_sha256"],
             os.path.relpath(rp, ROOT)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
