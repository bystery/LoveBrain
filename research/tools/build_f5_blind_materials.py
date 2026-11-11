# -*- coding: utf-8 -*-
r"""tools/build_f5_blind_materials.py — F5 复测的 12 对同臂重抽盲材。

对比对象：同一候选（新身份）在 N01 上的两次独立实跑 ——
  frozen  = logs/f3_newmat_live_restart_20261029/candidate_N01（旧身份跑的 12 轮）
  repaired= F5 复测目录（新身份跑的 12 轮）
两臂 harness 只差 E3（只修坏 JSON），因此本盲评回答的是"换了身份重跑一遍，
交付质量有没有系统性变差"，不是组件收益。顺序用固定种子随机（不逐位交替）。

用法：
  PYTHONIOENCODING=utf-8 D:/anaconda/python.exe tools/build_f5_blind_materials.py \
     --f3-run logs/f3_newmat_live_restart_20261029 \
     --f5-run logs/f5_recheck_live2_20261029_133653
"""
import argparse
import hashlib
import importlib.util
import json
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAT = os.path.join(ROOT, 'benchmark', 'stories_v2', 'final_20261029')
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                   'final_mainline_review_20261029')
STORY = 'N01'


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def sha(s):
    return hashlib.sha256((s or '').encode('utf-8')).hexdigest()


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--f3-run', required=True)
    ap.add_argument('--f5-run', required=True)
    ap.add_argument('--seed', type=int, default=20261029)
    ap.add_argument('--swap', action='store_true',
                    help='把已定稿的 A/B 整体互换，用于顺序复测（隔离裁判的位置偏好）')
    ap.add_argument('--suffix', default='', help='输出文件名后缀，如 _SWAP')
    ap.add_argument('--outdir', default=OUT)
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

    spec = importlib.util.spec_from_file_location(
        'bfbm', os.path.join(ROOT, 'tools', 'build_f3_blind_materials.py'))
    bfbm = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bfbm)

    ep = load(os.path.join(MAT, STORY, 'episode.json'))
    ref = load(os.path.join(MAT, STORY, 'reference.json'))
    cards_doc = load(os.path.join(MAT, 'GRADING_CARDS_N01_N02_K01_K02.json'))
    cards = {c['case_id']: c for c in cards_doc['cards']}

    f3_base = a.f3_run if os.path.isabs(a.f3_run) else os.path.join(ROOT, a.f3_run)
    f5_base = a.f5_run if os.path.isabs(a.f5_run) else os.path.join(ROOT, a.f5_run)
    rnd = random.Random(a.seed)

    items, private = [], {}
    for i, turn in enumerate(ep['turns']):
        cid = turn['case_id']
        frozen_dir = os.path.join(f3_base, 'candidate_%s' % STORY,
                                  'turn_%02d_%s' % (i + 1, cid))
        repaired_dir = os.path.join(f5_base, 'candidate_%s' % STORY,
                                    'turn_%02d_%s' % (i + 1, cid))
        f_ans = bfbm.reply_payload(frozen_dir)
        r_ans = bfbm.reply_payload(repaired_dir)
        frozen_is_a = rnd.random() < 0.5
        if a.swap:                       # 顺序复测：与定稿材料整体互换
            frozen_is_a = not frozen_is_a
        A, B = (f_ans, r_ans) if frozen_is_a else (r_ans, f_ans)
        a_arm, b_arm = ('frozen', 'repaired') if frozen_is_a else ('repaired', 'frozen')
        uid = 'F5:%s' % cid
        payload = {
            'unit_id': uid,
            'context': bfbm.context_for(ep, i),
            'card': bfbm.card_for(ref['turns'][cid]),
            'answers': {'A': A, 'B': B},
            'task': ('按九维给 A/B 各自打分，逐条标注可核主张，然后判 '
                     'A/B/tie/uncertain；不猜版本与成本；不预测用户愿不愿发。'),
        }
        items.append({'unit_id': uid, 'kind': 'f5_redraw', 'pair_id': cid,
                      'payload': payload})
        private[uid] = {'story': STORY, 'case_id': cid, 'a_arm': a_arm, 'b_arm': b_arm,
                        'position': i + 1,
                        'answer_kinds': {'A': A['kind'], 'B': B['kind']},
                        'answer_sha256': {
                            'A': sha(json.dumps(A, ensure_ascii=False, sort_keys=True)),
                            'B': sha(json.dumps(B, ensure_ascii=False, sort_keys=True))},
                        'turn_dir': {
                            'frozen': os.path.relpath(frozen_dir, ROOT).replace('\\', '/'),
                            'repaired': os.path.relpath(repaired_dir, ROOT).replace('\\', '/')}}

    outdir = a.outdir if os.path.isabs(a.outdir) else os.path.join(ROOT, a.outdir)
    os.makedirs(outdir, exist_ok=True)
    mpath = os.path.join(outdir, 'F5_MATERIALS%s.jsonl' % a.suffix)
    ppath = os.path.join(outdir, 'F5_PRIVATE_BINDINGS%s.json' % a.suffix)
    with open(mpath, 'w', encoding='utf-8') as f:
        for rec in items:
            f.write(json.dumps(rec, ensure_ascii=False, sort_keys=True) + '\n')
    with open(ppath, 'w', encoding='utf-8') as f:
        json.dump(private, f, ensure_ascii=False, indent=1)
        f.write('\n')
    kinds = {}
    for v in private.values():
        for side in ('A', 'B'):
            kinds[v['answer_kinds'][side]] = kinds.get(v['answer_kinds'][side], 0) + 1
    print('items=%d materials_sha256=%s' % (len(items),
                                            sha(open(mpath, encoding='utf-8').read())))
    print('bindings_sha256=%s' % sha(open(ppath, encoding='utf-8').read()))
    print('frozen_A=%d repaired_A=%d kinds=%s' % (
        sum(1 for v in private.values() if v['a_arm'] == 'frozen'),
        sum(1 for v in private.values() if v['a_arm'] == 'repaired'),
        json.dumps(kinds, sort_keys=True)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
