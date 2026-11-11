# -*- coding: utf-8 -*-
r"""tools/build_f3_blind_materials.py — 从 F3 真实生成结果构建 72 个盲评对。

规则：
- 只用两臂交付文本（reply_raw.txt = 机制返回给消费者的文本），不读 calls.json 原始模型文本；
- payload 不含臂名、候选版本、旧分、worker 判决、成本；
- 顺序逐位交替（偶数位原版=A，奇数位候选=A），绑定单独落盘；
- 长故事给完整前史（不截断），裁判才能核“事实是否真的在早期消息里”。
"""
import argparse
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAT = os.path.join(ROOT, 'benchmark', 'stories_v2', 'final_20261029')
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')
STORIES = ['N01', 'N02', 'K01', 'K02']
ARMS = {'parent': 'reference_product_v1',
        'candidate': 'action_policy_r4_guard_final'}


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def reply_payload(turn_dir):
    raw_path = os.path.join(turn_dir, 'reply_raw.txt')
    if not os.path.exists(raw_path):
        return {'kind': 'format_fail', 'error': 'reply_raw_missing',
                'raw_excerpt': ''}
    raw = open(raw_path, encoding='utf-8').read()
    try:
        obj = json.loads(raw)
    except Exception:
        return {'kind': 'format_fail', 'error': 'json_parse_error',
                'raw_excerpt': raw[:300]}
    if not isinstance(obj, dict):
        return {'kind': 'format_fail', 'error': 'top_level_not_object',
                'raw_excerpt': raw[:300]}
    if str(obj.get('action') or '').lower() == 'no_reply':
        return {'kind': 'no_reply',
                'decline_reason': (obj.get('decline_reason') or '').strip()}
    resp = obj.get('response') if isinstance(obj.get('response'), dict) else {}
    primary = ''
    for k in ('recommended', 'warm', 'playful', 'bad_boy'):
        v = resp.get(k)
        if isinstance(v, str) and v.strip():
            primary = v.strip()
            break
    backups = []
    for k in ('recommended', 'bad_boy', 'playful', 'warm'):
        v = resp.get(k)
        if isinstance(v, str) and v.strip() and v.strip() != primary:
            backups.append({'slot': 'response.%s' % k, 'text': v.strip()})
    dirs = obj.get('directions')
    if isinstance(dirs, list):
        for i, d in enumerate(dirs):
            if isinstance(d, str) and d.strip():
                backups.append({'slot': 'directions[%d]' % i, 'text': d.strip()})
    if not primary and not backups:
        return {'kind': 'format_fail', 'error': 'empty_response_no_action',
                'raw_excerpt': raw[:300]}
    if not primary:
        primary = backups.pop(0)['text']
    return {'kind': 'reply', 'primary': primary, 'backups': backups}


def card_for(ref_turn):
    keys = ('current_action', 'acceptable_actions', 'applicable_dimensions',
            'required_memory_atoms', 'private_guidance', 'unknowns',
            'protected_expressions', 'forbidden_factual_claims',
            'may_decline', 'intent')
    return {k: ref_turn.get(k) for k in keys}


def context_for(ep, idx):
    turn = ep['turns'][idx]
    prior = []
    for t in ep['turns'][:idx]:
        for m in (t.get('input') or {}).get('messages') or []:
            prior.append({'role': m.get('role'), 'text': m.get('text'),
                          'ts': m.get('ts')})
    inp = turn.get('input') or {}
    return {
        'persona': ep.get('persona') or {},
        'initial_kb': ep.get('initial_kb') or {},
        'prior_history': prior,
        'current_input': {
            'clock': inp.get('clock'),
            'may_decline': bool(inp.get('may_decline')),
            'note': inp.get('note'),
            'intent': inp.get('intent'),
            'messages': inp.get('messages') or [],
        },
        'story_meta': {'story_id': ep.get('episode_id'),
                       'turn_index': idx + 1,
                       'turns_total': len(ep['turns']),
                       'resumed_from_checkpoint': idx + 1 > 12
                       and len(ep['turns']) > 12},
    }


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--run-base', required=True,
                    help='logs/f3_newmat_live_* 目录（含 RESULT.json 或 *_<story> 输出）')
    ap.add_argument('--outdir', default=OUT)
    ap.add_argument('--tag', default='20261029')
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    run_base = a.run_base if os.path.isabs(a.run_base) else os.path.join(ROOT, a.run_base)
    cards_doc = load(os.path.join(MAT, 'GRADING_CARDS_N01_N02_K01_K02.json'))
    cards = {c['case_id']: c for c in cards_doc['cards']}
    items, private = [], {}
    idx_global = 0
    for sid in STORIES:
        ep = load(os.path.join(ROOT, 'benchmark', 'episodes', '%s.json' % sid))
        ref = load(os.path.join(MAT, sid, 'reference.json'))
        p_dir = os.path.join(run_base, 'parent_%s' % sid)
        c_dir = os.path.join(run_base, 'candidate_%s' % sid)
        for i, turn in enumerate(ep['turns']):
            cid = turn['case_id']
            # 长故事后段在 *_resume 目录
            base_p = p_dir + ('_resume' if i + 1 > 12 and len(ep['turns']) > 12 else '')
            base_c = c_dir + ('_resume' if i + 1 > 12 and len(ep['turns']) > 12 else '')
            p_turn = os.path.join(base_p, 'turn_%02d_%s' % (i + 1, cid))
            c_turn = os.path.join(base_c, 'turn_%02d_%s' % (i + 1, cid))
            p_ans = reply_payload(p_turn)
            c_ans = reply_payload(c_turn)
            order_parent_a = (idx_global % 2 == 0)
            a_ans, b_ans = (p_ans, c_ans) if order_parent_a else (c_ans, p_ans)
            a_arm, b_arm = (('parent', 'candidate') if order_parent_a
                            else ('candidate', 'parent'))
            uid = 'F3:%s' % cid
            payload = {
                'unit_id': uid,
                'context': context_for(ep, i),
                'card': card_for(ref['turns'][cid]),
                'answers': {'A': a_ans, 'B': b_ans},
                'task': ('按九维给 A/B 各自打分，逐条标注可核主张，然后判 '
                         'A/B/tie/uncertain；不猜版本与成本；不预测用户愿不愿发。'),
            }
            items.append({'unit_id': uid, 'kind': 'new_material', 'pair_id': cid,
                          'payload': payload})
            private[uid] = {'story': sid, 'case_id': cid, 'a_arm': a_arm,
                            'b_arm': b_arm, 'position': idx_global + 1,
                            'answer_kinds': {'A': a_ans['kind'],
                                             'B': b_ans['kind']},
                            'answer_sha256': {'A': sha(json.dumps(a_ans, ensure_ascii=False,
                                                                 sort_keys=True)),
                                              'B': sha(json.dumps(b_ans, ensure_ascii=False,
                                                                  sort_keys=True))},
                            'turn_dir': {'parent': os.path.relpath(p_turn, ROOT)
                                         .replace('\\', '/'),
                                         'candidate': os.path.relpath(c_turn, ROOT)
                                         .replace('\\', '/')}}
            idx_global += 1
    outdir = a.outdir if os.path.isabs(a.outdir) else os.path.join(ROOT, a.outdir)
    os.makedirs(outdir, exist_ok=True)
    mpath = os.path.join(outdir, 'F3_MATERIALS.jsonl')
    ppath = os.path.join(outdir, 'F3_PRIVATE_BINDINGS.json')
    with open(mpath, 'w', encoding='utf-8') as f:
        for rec in items:
            f.write(json.dumps(rec, ensure_ascii=False, sort_keys=True) + '\n')
    with open(ppath, 'w', encoding='utf-8') as f:
        json.dump(private, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print('items=%d materials_sha256=%s' % (len(items), sha(open(mpath, encoding='utf-8').read())))
    print('bindings_sha256=%s' % sha(open(ppath, encoding='utf-8').read()))
    print('parent_A=%d candidate_A=%d' % (
        sum(1 for v in private.values() if v['a_arm'] == 'parent'),
        sum(1 for v in private.values() if v['a_arm'] == 'candidate')))
    return 0


if __name__ == '__main__':
    sys.exit(main())
