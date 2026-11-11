# -*- coding: utf-8 -*-
r"""repair_quality_panel_ratings.py — 批后离线修复通道（只新增文件，不改原 RATINGS_*.jsonl）。

背景（2026-10-27 00:37 批，AUTH-QUALITY-PANEL-20261027-01）：
- 173/173 传输成功、0 停批；runner 现场校验报 parse_fail=14、schema_fail=159。
- 离线诊断：159 条解析成功者**核心 schema 全部合格**（scores 九键+分值合法），
  失败主因=模型把 node_id 回显成 case_id（输入材料含 case_id），runner 把回显错当内容错——校验器过严；
  14 条为 JSON 后带尾随文本（"Extra data"），用 raw_decode 提取首个完整 JSON 对象，
  尾随原文保留在 raw_tail，不重发、不改写内容。
- 本脚本重建 RATINGS_REPAIRED.jsonl：重新解析 raw + 修正版 schema 校验
  （node_id 回显=警告不计失败；scores 键/分值、backups slot 覆盖、控制样本空 backups 仍硬校验），
  逐条登记 parse_method / echo_note / schema_why，账本 attempt 事件不动。

运行： set PYTHONIOENCODING=utf-8 && D:\anaconda\python.exe repair_quality_panel_ratings.py --outdir <批目录>
"""
import argparse
import json
import os
import re
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PK = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                  'knowledge_quality_independent_review_20261026')
DIM_KEYS = ['goal', 'factuality', 'attribution', 'time_state', 'boundary',
            'memory_use', 'interaction', 'initiative', 'emotion_relation']


def strip_fence(s):
    s = (s or '').strip()
    m = re.match(r'^```(?:json)?\s*(.*?)\s*```$', s, re.S)
    if m:
        s = m.group(1)
    return s


def parse_raw(raw):
    """返回 (parsed|None, method, tail)。"""
    s = strip_fence(raw)
    try:
        return json.loads(s), 'json.loads', ''
    except Exception:
        pass
    try:
        dec = json.JSONDecoder()
        obj, end = dec.raw_decode(s)
        tail = s[end:].strip()
        return obj, 'raw_decode_first_object', tail[:400]
    except Exception:
        return None, 'unparseable', ''


def check_schema(parsed, kind, item_id, nodes_by_id):
    why = []
    echo = ''
    if not isinstance(parsed, dict):
        return False, 'not a dict', ''
    got_id = parsed.get('node_id')
    if got_id != item_id:
        case = None
        if kind != 'control':
            try:
                b = [json.loads(l) for l in open(os.path.join(PK, 'PRIVATE_BINDINGS.json'),
                                                 encoding='utf-8') if l.strip()]
                case = next((x['case_id'] for x in b if x['node_id'] == item_id), None)
            except Exception:
                case = None
        if got_id == case:
            echo = 'node_id echoed case_id (%s)' % got_id
        else:
            echo = 'node_id echoed %r (not item_id, not case_id) — binding is by attempt_id, not echo' % got_id
    prim = parsed.get('primary')
    if not isinstance(prim, dict):
        why.append('missing primary')
    else:
        sc = prim.get('scores')
        if not isinstance(sc, dict):
            why.append('scores not dict')
        else:
            if set(sc.keys()) != set(DIM_KEYS):
                why.append('scores keys=%s' % sorted(sc.keys()))
            for k, v in sc.items():
                if v is not None and v not in (0, 1, 2, 3):
                    why.append('score range %s=%r' % (k, v))
    if kind == 'control':
        if parsed.get('backups') not in ([], None):
            why.append('control backups should be empty')
    else:
        n = nodes_by_id.get(item_id)
        exp = [b['slot'] for b in n['answer']['backups'] if b.get('text')] if n else []
        got = parsed.get('backups')
        if not isinstance(got, list):
            why.append('backups not list')
        else:
            got_slots = [b.get('slot') for b in got if isinstance(b, dict)]
            if sorted(got_slots) != sorted(exp):
                why.append('backup slots mismatch exp=%d got=%d' % (len(exp), len(got_slots)))
    return (not why), '; '.join(why) or 'ok', echo


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--outdir', required=True)
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    outdir = os.path.abspath(a.outdir)
    nodes = {n['node_id']: n for n in
             (json.loads(l) for l in open(os.path.join(PK, 'QUALITY_NODES.jsonl'), encoding='utf-8')
              if l.strip())}
    recs = []
    for kind in ('control', 'primary', 'repeat_same'):
        p = os.path.join(outdir, 'RATINGS_%s.jsonl' % kind)
        if os.path.isfile(p):
            for l in open(p, encoding='utf-8'):
                if l.strip():
                    recs.append(json.loads(l))
    cat = Counter()
    out = []
    for r in recs:
        nr = dict(r)
        raw = r.get('raw') or ''
        if not r.get('transport_ok'):
            cat['transport_fail_kept'] += 1
            nr.update({'parse_method': 'transport_failed', 'parsed': None,
                       'schema_ok_corrected': False, 'schema_why': 'transport failed'})
            out.append(nr)
            continue
        parsed, method, tail = parse_raw(raw)
        if parsed is None:
            cat['unparseable'] += 1
            nr.update({'parse_method': 'unparseable', 'parsed': None,
                       'schema_ok_corrected': False, 'schema_why': 'unparseable (raw kept)'})
            out.append(nr)
            continue
        ok, why, echo = check_schema(parsed, r['kind'], r['item_id'], nodes)
        nr.update({'parse_method': method, 'raw_tail': tail,
                   'parsed': parsed if ok else None,
                   'schema_ok_corrected': ok,
                   'schema_why': why,
                   'schema_why_original': r.get('schema_why'),
                   'echo_note': echo or None})
        cat['assessed_corrected' if ok else 'schema_fail_corrected'] += 1
        if echo:
            cat['echo_' + ('case_id' if 'case_id' in echo else 'other')] += 1
        if tail:
            cat['raw_decode_with_tail'] += 1
        out.append(nr)
    dst = os.path.join(outdir, 'RATINGS_REPAIRED.jsonl')
    with open(dst, 'w', encoding='utf-8') as f:
        for r in out:
            f.write(json.dumps(r, ensure_ascii=False) + '\n')
    print('REPAIRED %d records -> %s' % (len(out), dst))
    for k, v in cat.most_common():
        print('  %4d %s' % (v, k))


if __name__ == '__main__':
    main()
