# -*- coding: utf-8 -*-
"""一次性复核辅助：把机械命中的“无据断言”连同该节点冻结证据一起打印，供逐条核对。"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PK = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_V1_1')
sys.stdout.reconfigure(encoding='utf-8')

nodes = {}
with open(os.path.join(PK, 'nodes.jsonl'), encoding='utf-8') as f:
    for line in f:
        d = json.loads(line)
        nodes[d['node_id']] = d
mech = json.load(open(os.path.join(PK, 'CLAIMS_MECHANICAL.json'), encoding='utf-8'))

rows = []
for e in mech:
    for who, v in e['answers'].items():
        cl = v if isinstance(v, list) else v['claims']
        for c in cl:
            if c['evidence_status'] == '未提供':
                rows.append((e['node_id'], e['split'], who, c['claim_id'], c['text'],
                             c['severity'], c['rule']))

buf = ['# 机械命中 %d 条（附冻结证据）\n' % len(rows)]
seen = set()
for nid, sp, who, cid, txt, sev, rule in rows:
    ev = nodes[nid]['evidence']
    if nid not in seen:
        seen.add(nid)
        win = ' || '.join('%s:%s' % (x['who'], x['text']) for x in ev['current_window'])
        pri = ' || '.join('%s:%s' % (x['who'], x['text']) for x in (ev['prior_messages'] or [])[-2:])
        seed = json.dumps(ev['public_seed'], ensure_ascii=False) if ev['public_seed'] else '-'
        goal = ev['goal'] if isinstance(ev['goal'], str) else json.dumps(ev['goal'], ensure_ascii=False)
        head = '### {} [{}/{}] goal={} clock={}'.format(nid, sp, who, (goal or '-')[:70], ev['clock'])
        buf.append(head)
        buf.append('  窗口: ' + win[:300])
        buf.append('  前情: ' + pri[:240])
        buf.append('  备注: ' + str(ev['advisor_note'] or '-')[:200])
        buf.append('  公开: ' + seed[:220])
    ans_txt = nodes[nid]['answers'][who]['primary']
    buf.append('   - {} ｜{}｜{}  << {} >>'.format(cid, sev, rule, txt))
    buf.append('     首选全文: ' + (ans_txt or '')[:160])
out = os.path.join(PK, 'REVIEW_FLAGGED_WITH_EVIDENCE.md')
open(out, 'w', encoding='utf-8').write('\n'.join(buf))
print('flagged=%d nodes_touched=%d -> %s' % (len(rows), len(seen), out))
