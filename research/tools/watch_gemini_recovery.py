# -*- coding: utf-8 -*-
# 纯探测脚本：不消耗裁判额度，只轮询 gemini/Antigravity 上游是否恢复。
# 一旦 /chat/completions 返回非空正文即退出 0（通知裁判续跑）。最多等 45 分钟。
import json
import time
import urllib.request

URL = 'http://127.0.0.1:17770/v1/chat/completions'
BODY = json.dumps({
    'model': 'gemini-3.8-flash',
    'messages': [
        {'role': 'system', 'content': 'Reply with the single word OK.'},
        {'role': 'user', 'content': 'ping'},
    ],
}).encode('utf-8')
MAX_S = 45 * 60
INTERVAL = 40


def probe():
    req = urllib.request.Request(URL, data=BODY, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=60) as r:
        d = json.load(r)
    ch = (d.get('choices') or [{}])[0]
    content = ((ch.get('message') or {}).get('content') or '').strip()
    ctok = (d.get('usage') or {}).get('completion_tokens')
    return content, ctok


t = 0
while t < MAX_S:
    try:
        content, ctok = probe()
    except Exception as exc:  # noqa: BLE001
        content, ctok = '<<err %s>>' % exc, None
    healthy = bool(content) and content != '<<' and not content.startswith('<<err')
    print('t+%ds healthy=%s content=%r ctok=%s' % (t, healthy, content[:30], ctok), flush=True)
    if healthy:
        print('GEMINI_RECOVERED')
        raise SystemExit(0)
    time.sleep(INTERVAL)
    t += INTERVAL
print('GEMINI_STILL_DOWN_AFTER_WAIT')
raise SystemExit(3)
