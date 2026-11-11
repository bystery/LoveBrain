# -*- coding: utf-8 -*-
r"""tools/repair_p06_nullslots.py — 离线修复“字面 null 占位备选”导致的槽覆盖不符（零模型调用）。

背景：早期题包构造把模型写出的 directions[i]="null" 字符串当成可发备选送判；裁判按 QUALITY_MANUAL_V1_1
“输入 slot 为 null 的不评”正确排除，于是整条被判 `backups slot 覆盖不符` → schema_ok=False → 连首选一起未评。
这是题包构造 bug，不是答案质量问题。构建器已修（不再收录 null 占位串）；本脚本修复**已冻结批**中受影响的记录。

做法：把某条记录在“唯一不符项恰是被排除的 null 占位槽”时，判为已评（校验九键、node_id、首选有文本、
真实备选槽全覆盖），注入 parsed 并置 schema_ok=True。其它记录原样复制。
输入不动原批目录；输出到同级 `.repaired` 目录（保留原始判词为证据）。

运行：
  D:\anaconda\python.exe tools/repair_p06_nullslots.py --ratings logs/p06_judge_live_gemrun \
      --packet refs/experiment_protocol_20261023/p06_judge_gem_20261027
然后 analyze_p06_judge.py --ratings <该 .repaired 目录>
"""
import argparse
import json
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIMS = ['goal', 'factuality', 'attribution', 'time_state', 'boundary',
        'memory_use', 'interaction', 'initiative', 'emotion_relation']
NULL_VALUES = {'null', 'none', 'nil', 'n/a', 'na', '无', '没有', '空'}


def is_null(text):
    return isinstance(text, str) and text.strip().lower() in NULL_VALUES


def load_jsonl(path):
    out = []
    if os.path.isfile(path):
        for l in open(path, encoding='utf-8'):
            if l.strip():
                out.append(json.loads(l))
    return out


def tolerant_assess(rec, node):
    """若唯一失败项是被排除的 null 占位槽，且判词本身合规，则可视为已评。"""
    if not (rec.get('transport_ok') and rec.get('parse_ok')):
        return False, None
    try:
        parsed = json.loads(rec.get('raw') or '')
    except Exception:
        return False, None
    if not isinstance(parsed, dict) or parsed.get('node_id') != rec.get('item_id'):
        return False, None
    prim = parsed.get('primary')
    if not isinstance(prim, dict):
        return False, None
    sc = prim.get('scores')
    if not isinstance(sc, dict) or set(sc.keys()) != set(DIMS):
        return False, None
    for v in sc.values():
        if v is not None and v not in (0, 1, 2, 3):
            return False, None
    got = parsed.get('backups')
    if not isinstance(got, list):
        return False, None
    got_slots = [b.get('slot') for b in got if isinstance(b, dict)]
    if len(got_slots) != len(got):
        return False, None
    for b in got:
        bsc = b.get('scores')
        if not isinstance(bsc, dict) or set(bsc.keys()) != set(DIMS):
            return False, None
        for v in bsc.values():
            if v is not None and v not in (0, 1, 2, 3):
                return False, None
    # 期望槽 = 节点真实备选（排除 null 占位串）
    exp_real = [b['slot'] for b in node['answer']['backups']
                if b.get('text') and not is_null(b['text'])]
    if sorted(exp_real) != sorted(got_slots):
        return False, None
    dropped = [b['slot'] for b in node['answer']['backups']
               if b.get('text') and is_null(b['text'])]
    return True, dropped


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ratings', required=True)
    ap.add_argument('--packet', required=True)
    a = ap.parse_args()
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    rdir = os.path.abspath(a.ratings)
    pdir = a.packet if os.path.isabs(a.packet) else os.path.join(ROOT, a.packet)
    nodes = {}
    for l in open(os.path.join(pdir, 'P06_NODES.jsonl'), encoding='utf-8'):
        if l.strip():
            n = json.loads(l)
            nodes[n['_meta']['node_id']] = n
    outdir = rdir.rstrip('/\\') + '.repaired'
    if os.path.isdir(outdir):
        shutil.rmtree(outdir)
    os.makedirs(outdir)
    # 复制非判词文件
    for fn in os.listdir(rdir):
        if not fn.startswith('RATINGS_') or not fn.endswith('.jsonl'):
            src = os.path.join(rdir, fn)
            if os.path.isfile(src):
                shutil.copy(src, os.path.join(outdir, fn))
    repaired = 0
    total = 0
    notes = []
    for kind in ('primary', 'control', 'repeat'):
        src = os.path.join(rdir, 'RATINGS_%s.jsonl' % kind)
        recs = load_jsonl(src)
        dst = os.path.join(outdir, 'RATINGS_%s.jsonl' % kind)
        with open(dst, 'w', encoding='utf-8') as f:
            for r in recs:
                total += 1
                node = nodes.get(r['item_id'])
                flipped = False
                if not (r.get('transport_ok') and r.get('parse_ok') and r.get('schema_ok')) and node:
                    ok, dropped = tolerant_assess(r, node)
                    if ok:
                        r = dict(r)
                        r['schema_ok'] = True
                        r['schema_why'] = 'ok (null-slot tolerant repair)'
                        r['repaired_null_slots'] = dropped
                        try:
                            r['parsed'] = json.loads(r['raw'])
                        except Exception:
                            pass
                        flipped = True
                        repaired += 1
                        notes.append((r['item_id'], kind, dropped))
                f.write(json.dumps(r, ensure_ascii=False) + '\n')
    json.dump({'total': total, 'repaired_nullslot_assessed': repaired,
               'note': 'null-占位备选槽导致的 schema 失败，经零调用容错重判为已评；原批目录未改',
               'items': [{'item_id': x[0], 'kind': x[1], 'dropped_null_slots': x[2]} for x in notes]},
              open(os.path.join(outdir, 'REPAIR_NOTES.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('repaired=%d of %d records -> %s' % (repaired, total, os.path.relpath(outdir, ROOT)))
    for x in notes:
        print('  ', x)


if __name__ == '__main__':
    main()
