# -*- coding: utf-8 -*-
r"""tools/f3_knowledge_chain.py — F3 长期故事的知识链核验（零调用）。

对 K01/K02 每个“需要回指旧轮”的 atom，核四件事：
1) 来源消息是否真的在本轮之前的真实消息里；
2) 本轮之前的历史是否已超出 1400 CU，来源 quote 是否已离开原始历史窗口；
3) 候选本轮请求里是否出现该事实的投影行（【相关已确认事实】/【最近状态变更】），
   且来源不是本轮输入；
4) 答案是否至少包含该事实的关键 token（只作线索，语义由裁判/人工复核）。
"""
import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAT = os.path.join(ROOT, 'benchmark', 'stories_v2', 'final_20261029')
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')
HISTORY_MARK = '# 【真实历史消息'
FACT_MARKS = ('【相关已确认事实', '【最近状态变更', '【暂定事项')


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def utf16(s):
    return len(s.encode('utf-16-le')) // 2


def section(prompt, start_mark, end_marks):
    i = prompt.find(start_mark)
    if i < 0:
        return ''
    j = len(prompt)
    for m in end_marks:
        k = prompt.find(m, i + len(start_mark))
        if k >= 0:
            j = min(j, k)
    return prompt[i:j]


def key_tokens(value, quote):
    toks = set(re.findall(r'\d+', value or ''))
    for w in re.findall(r'[\u4e00-\u9fff]{2,}', value or ''):
        if len(w) >= 2:
            toks.add(w)
    toks.update(re.findall(r'\d+', quote or ''))
    return {t for t in toks if t}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--run-base', required=True)
    ap.add_argument('--outdir', default=OUT)
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    base = a.run_base if os.path.isabs(a.run_base) else os.path.join(ROOT, a.run_base)
    cards = {c['case_id']: c for c in load(os.path.join(
        MAT, 'GRADING_CARDS_N01_N02_K01_K02.json'))['cards']}
    rows = []
    for sid in ('K01', 'K02'):
        ep = load(os.path.join(ROOT, 'benchmark', 'episodes', '%s.json' % sid))
        msgs_by_case = {t['case_id']: t['input']['messages'] for t in ep['turns']}
        order = {t['case_id']: i for i, t in enumerate(ep['turns'])}
        for i, t in enumerate(ep['turns']):
            cid = t['case_id']
            card = cards[cid]
            suffix = '_resume' if i + 1 > 12 else ''
            for atom in card.get('required_memory_atoms') or []:
                src = atom.get('source') or {}
                if src.get('kind') != 'message':
                    continue
                scid = src.get('case_id')
                if scid == cid or order.get(scid, 999) >= i:
                    continue
                quote = src.get('quote') or ''
                # 历史 CU（本轮之前所有真实消息）
                hist_cu = sum(utf16(m.get('text') or '') + 1
                              for tt in ep['turns'][:i]
                              for m in tt['input']['messages'])
                for arm in ('parent', 'candidate'):
                    p = os.path.join(base, '%s_%s%s' % (arm, sid, suffix),
                                     'turn_%02d_%s' % (i + 1, cid))
                    calls_p = os.path.join(p, 'calls.json')
                    raw_p = os.path.join(p, 'reply_raw.txt')
                    prompt = ''
                    if os.path.isfile(calls_p):
                        calls = load(calls_p)
                        if calls and isinstance(calls, list):
                            prompt = calls[0].get('user') or ''
                    history = section(prompt, HISTORY_MARK, ('# ',))
                    fact_sec = section(prompt, FACT_MARKS[0],
                                       ('# ', '【暂定事项', '【最近状态变更'))
                    upd_sec = section(prompt, FACT_MARKS[1], ('# ', '【'))
                    cur_text = '\n'.join(m.get('text') or ''
                                         for m in msgs_by_case.get(cid) or [])
                    raw = open(raw_p, encoding='utf-8').read() if os.path.isfile(raw_p) else ''
                    notes_p = os.path.join(p, 'mechanism_notes.txt')
                    notes = open(notes_p, encoding='utf-8').read() \
                        if os.path.isfile(notes_p) else ''
                    hist_note = re.search(r'history_cu=(\d+)', notes)
                    facts_note = re.search(r'facts_proj=(\d+)/(\d+)CU', notes)
                    mem_note = re.search(r'mem_on=([01])', notes)
                    in_history = bool(quote) and quote in history
                    in_facts = bool(quote) and quote in (fact_sec + upd_sec)
                    tokens = key_tokens(atom.get('value') or '', quote)
                    hit = [x for x in tokens if x in raw]
                    rows.append({
                        'story': sid, 'test_case': cid, 'arm': arm,
                        'atom_id': atom.get('atom_id'), 'source_case': scid,
                        'source_quote': quote, 'value': atom.get('value'),
                        'status': atom.get('status'),
                        'history_cu_before': hist_cu,
                        'notes_history_cu': int(hist_note.group(1)) if hist_note else None,
                        'notes_facts_proj': (int(facts_note.group(1)), int(facts_note.group(2)))
                        if facts_note else None,
                        'notes_mem_on': int(mem_note.group(1)) if mem_note else None,
                        'source_in_raw_history': in_history,
                        'source_in_current': bool(quote) and quote in cur_text,
                        'source_out_of_window': bool(quote) and (not in_history) and (quote not in cur_text),
                        'fact_projected': in_facts,
                        'facts_section_lines': len([l for l in fact_sec.splitlines()
                                                    if l.strip().startswith('·')]),
                        'answer_token_hits': hit[:8],
                        'answer_token_total': len(tokens),
                        'answer': raw[:600],
                    })
    outdir = a.outdir if os.path.isabs(a.outdir) else os.path.join(ROOT, a.outdir)
    os.makedirs(outdir, exist_ok=True)
    op = os.path.join(outdir, 'F3_KNOWLEDGE_CHAIN.jsonl')
    with open(op, 'w', encoding='utf-8') as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + '\n')
    cand = [r for r in rows if r['arm'] == 'candidate']
    summary = {
        'rows': len(rows), 'candidate_atoms': len(cand),
        'source_out_of_window': sum(1 for r in cand if r['source_out_of_window']),
        'fact_projected': sum(1 for r in cand if r['fact_projected']),
        'projected_but_out_of_window': sum(
            1 for r in cand if r['fact_projected'] and r['source_out_of_window']),
        'current_only': sum(1 for r in cand if r['source_in_current']),
        'by_story': {},
    }
    for r in cand:
        s = summary['by_story'].setdefault(r['story'], {'atoms': 0, 'projected': 0,
                                                        'out_of_window': 0})
        s['atoms'] += 1
        s['projected'] += 1 if r['fact_projected'] else 0
        s['out_of_window'] += 1 if r['source_out_of_window'] else 0
    sp = os.path.join(outdir, 'F3_KNOWLEDGE_CHAIN_SUMMARY.json')
    with open(sp, 'w', encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())
