# -*- coding: utf-8 -*-
# 大节点裁判压力探针：真实 7-备选 P06 节点 + 完整手册系统提示，逐个模型测时延/是否空/是否合规JSON。
# 直连网关 /v1/chat/completions，不消耗任何裁判授权额度。
import json
import os
import sys
import time
import urllib.request

ROOT = r'D:\lovebrain-arena'
PK = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'p06_judge_gem_20261027')
URL = 'http://127.0.0.1:17770/v1/chat/completions'

# 复用裁判系统提示（含手册）
sys.path.insert(0, os.path.join(ROOT, 'tools'))
import run_p06_quality_judge as R  # noqa: E402

manual = open(os.path.join(PK, 'QUALITY_MANUAL_V1_1.md'), encoding='utf-8').read()
system_full = R.JUDGE_SYSTEM + '\n\n<MANUAL>\n' + manual + '\n</MANUAL>'

# 取最大的真实节点 P06N0001（7 备选）
node = None
for l in open(os.path.join(PK, 'P06_NODES.jsonl'), encoding='utf-8'):
    l = l.strip()
    if not l:
        continue
    n = json.loads(l)
    if n['_meta']['node_id'] == 'P06N0001':
        node = n
        break

answer = node['answer']
ref = node.get('reference_card') or {}
frozen = {k: node.get(k) for k in ('_meta', 'persona', 'facts', 'context') if k in node}
user = json.dumps({
    'node_id': node['_meta']['node_id'],
    'evidence': node.get('evidence'),
    'frozen_card': frozen,
    'reference_card': ref,
    'answer': {'primary': answer.get('primary'), 'backups': answer.get('backups')},
}, ensure_ascii=False)

print('SYSTEM_LEN=%d USER_LEN=%d backups=%d' % (
    len(system_full), len(user),
    len([b for b in answer.get('backups', []) if b.get('text')])), flush=True)

TARGETS = ['qwen3.8-flash', 'gemini-3.8-flash', 'hy3', 'dsf']
for model in TARGETS:
    body = json.dumps({'model': model, 'temperature': 0.7, 'stream': False,
                       'messages': [{'role': 'system', 'content': system_full},
                                    {'role': 'user', 'content': user}]}).encode('utf-8')
    t0 = time.time()
    status, clen, okj, extra = 'ERR', 0, False, ''
    try:
        req = urllib.request.Request(URL, data=body, headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=220) as r:
            d = json.load(r)
        ch = (d.get('choices') or [{}])[0]
        content = (ch.get('message') or {}).get('content') or ''
        xs = d.get('x_scaffold', {})
        clen = len(content)
        extra = 'finish=%s provider=%s tried=%s ctok=%s' % (
            ch.get('finish_reason'), xs.get('provider'), xs.get('tried'),
            (d.get('usage') or {}).get('completion_tokens'))
        try:
            parsed = json.loads(content)
            okj = isinstance(parsed, dict) and set(parsed.get('primary', {}).get('scores', {})) >= set(R.DIM_KEYS)
            nbak = len(parsed.get('backups', [])) if isinstance(parsed, dict) else 0
            extra += ' backups_returned=%d' % nbak
        except Exception as e:  # noqa: BLE001
            extra += ' json_err=%s' % str(e)[:40]
    except Exception as e:  # noqa: BLE001
        extra = 'EXC %s: %s' % (type(e).__name__, str(e)[:80])
    el = int(time.time() - t0)
    print('%-16s %ds content_len=%d valid_json=%s | %s' % (model, el, clen, okj, extra), flush=True)
