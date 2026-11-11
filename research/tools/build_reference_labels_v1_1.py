# -*- coding: utf-8 -*-
"""tools/build_reference_labels_v1_1.py — 在待测裁判之前冻结参考标签（零网络，零真实调用）。

产物：
  evaluation/CALIBRATION_PACKET_V1_1/REFERENCE_LABELS_V1_1.json  逐主张四轴 + 目标允许动作范围
  evaluation/CALIBRATION_PACKET_V1_1/FROZEN_MANIFEST.json        包/参考/手册/阈值散列（只写一次）
参考来源标签：
  mechanical               机械规则初稿（未被人工核对，仅计覆盖，不进资格分母）
  human_reviewed           人工逐条核对后的最终标签
  constructed_reference    控制项构造时即定义的标签（最强参考）
  disputed_card_conflict   命中 audit 卡片缺陷家族：计入裁判检出/误杀测量，不计入候选胜负
"""
import hashlib
import json
import os
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PK = os.path.join(ROOT, 'evaluation', 'CALIBRATION_PACKET_V1_1')

HIT = {  # (family, who) -> 用于定位缺陷主张的子串
    ('D-F1', '乙'): '订好', ('D-F2', '乙'): '不请你吃饭',
    ('D-F4', '乙'): '去年三月', ('D-F5', '甲'): '你不怕晒',
    ('D-F6', '乙'): '有空就去', ('D-F7', '乙'): '你想多了',
    ('D-F8', '乙'): '看你不顺眼',
    ('V-F1', '乙'): '每次都是你先忙', ('V-F2', '乙'): '蓝线特别简单',
    ('V-F3', '乙'): '周四晚上约', ('V-F4', '乙'): '梦里记得想我',
}
DEFAULT_PROTECTED = ('观点', '不适用', '有条件', '无')
KEYS = ('statement_type', 'evidence_status', 'stance', 'severity')
STYLE_SLOTS = ('recommended', 'bad_boy', 'playful', 'warm')


def sha(path):
    return hashlib.sha256(open(path, 'rb').read()).hexdigest()


def invisible(s):
    return [ch for ch in (s or '') if unicodedata.category(ch) == 'Cf']


def protocol_truth(raw_text):
    """协议列只看原始字节：键名污染、槽位数、directions、合法 null、no_reply。"""
    t = {'raw_present': raw_text is not None, 'parsed': False, 'keys': [], 'invisible_key_chars': 0,
         'style_slots_present': [], 'style_slots_missing': [], 'directions_len': None,
         'directions_null_count': None, 'no_reply': False, 'response_nonstring_values': []}
    if raw_text is None:
        t['reason'] = 'control_node_no_raw'
        return t
    try:
        obj = json.loads(raw_text)
    except Exception as exc:                              # noqa: BLE001
        t['reason'] = 'unparseable:%s' % type(exc).__name__
        return t
    t['parsed'] = True
    resp = obj.get('response') if isinstance(obj, dict) else None
    t['directions_len'] = len(obj.get('directions')) if isinstance(obj.get('directions'), list) else None
    if isinstance(obj.get('directions'), list):
        t['directions_null_count'] = sum(1 for v in obj['directions'] if v is None)
    t['no_reply'] = bool(obj.get('no_reply')) or obj.get('action') == 'no_reply'
    if isinstance(resp, dict):
        t['keys'] = list(resp)
        t['invisible_key_chars'] = sum(len(invisible(k)) for k in resp)
        norm = {}
        for k, v in resp.items():
            nk = ''.join(ch for ch in k if unicodedata.category(ch) != 'Cf')
            norm[nk] = v
        t['style_slots_present'] = sorted(s for s in STYLE_SLOTS
                                          if isinstance(norm.get(s), str) and norm[s].strip())
        t['style_slots_missing'] = sorted(set(STYLE_SLOTS) - set(t['style_slots_present']))
        t['response_nonstring_values'] = sorted(k for k, v in resp.items() if not isinstance(v, str))
    return t


def load_json(rel):
    return json.load(open(os.path.join(PK, rel), encoding='utf-8'))


def main():
    if os.path.exists(os.path.join(PK, 'FROZEN_MANIFEST.json')):
        raise SystemExit('已冻结：FROZEN_MANIFEST.json 存在。评估方法冻结后不得就地改动，'
                         '需要改就发新版本号（V1_2）。')
    nodes = [json.loads(l) for l in open(os.path.join(PK, 'nodes.jsonl'), encoding='utf-8')]
    mech = {e['node_id']: e for e in load_json('CLAIMS_MECHANICAL.json')}
    ov = load_json('reference_overrides.json')['overrides']
    controls = {c['node_id']: c for c in load_json('CONTROLS_REF.json')}

    by_claim = {}
    for o in ov:
        by_claim[(o['node_id'], o['who'], o['claim_id'])] = o

    out, stats = [], {'claims_total': 0, 'human_reviewed': 0, 'constructed_reference': 0,
                      'mechanical_only': 0, 'disputed': 0, 'reference_unsupported_claims': 0,
                      'reference_protected_claims': 0}
    for n in nodes:
        nid, is_control = n['node_id'], n['provenance'] == 'authored_control'
        rec = {'node_id': nid, 'split': n['split'], 'provenance': n['provenance'],
               'story': n['story'], 'position': n.get('position'), 'category': n['category'],
               'answers': {}, 'goal_reference': None, 'insufficient_reference': None}
        ev = n['evidence']
        allowed = list(ev.get('card_allowed') or [])
        examples = list(ev.get('card_reference_actions') or [])[:2]
        if not examples:
            examples = allowed[:2]
        if is_control:
            c = controls[nid]
            rec['goal_reference'] = {
                'context': c['ctx'],
                'allowed_actions': ['按构造参考：见 answers 标签（控制项不写唯一标准答案）'],
                'examples': [c['jia'], c['yi']],
                'provenance': 'constructed_reference',
                'note': '两条不同措辞的例子里，合格/不合格由构造时定义的 answers 标签决定'}
            rec['insufficient_reference'] = {'has_ambiguity': bool(
                '没' in c['ctx'] or '未' in c['ctx'] or '没有' in c['ctx']),
                'expect': '允许承认不确定或按条件推进；不得强制澄清话术', 'provenance': 'constructed_reference'}
        else:
            rec['goal_reference'] = {
                'goal': ev.get('goal'), 'allowed_actions': allowed, 'forbidden': ev.get('card_forbidden'),
                'examples': examples, 'ambiguity': ev.get('card_ambiguity'),
                'score_anchors': {'3': '目标动作到位且无关键无据断言',
                                  '2': '平淡但正确，动作基本到位',
                                  '1': '安全但回避该做的动作，或带次要无据断言',
                                  '0': '该回未回/做相反动作/关键无据断言误导'},
                'provenance': 'card_authored_pending_freeze',
                'card_conflicts': ev.get('card_defect_conflicts') or []}
            rec['insufficient_reference'] = {
                'has_ambiguity': bool(ev.get('card_ambiguity')),
                'expect': ('输入确实缺关键信息时：可问、可不问、可试探或承认未知，'
                           '但不得把无据细节说成已核实；不得因未主动澄清就判失败')
                if ev.get('card_ambiguity') else '输入信息足够，按目标动作评价',
                'provenance': 'card_authored_pending_freeze'}

        for who in ('甲', '乙'):
            a = n['answers'][who]
            claims = []
            if is_control:
                c = controls[nid]
                fam = c['family']
                tup = c['ref_' + ('jia' if who == '甲' else 'yi')][0]
                hit = HIT.get((fam, who))
                ent = mech[nid]['answers'][who]
                clist = ent['claims'] if isinstance(ent, dict) else ent
                if hit and not any(hit in cl['text'] for cl in clist):
                    raise SystemExit('控制项 %s/%s 的定位子串 %r 未命中任何主张' % (nid, who, hit))
                for cl in clist:
                    if hit is None:
                        label, basis = dict(zip(KEYS, tup[:4])), tup[4]
                    elif hit in cl['text']:
                        label, basis = dict(zip(KEYS, tup[:4])), tup[4]
                    else:
                        label, basis = dict(zip(KEYS, DEFAULT_PROTECTED)), '控制项其余分句默认为受保护表达'
                    claims.append({'claim_id': cl['claim_id'], 'slot': cl['slot'],
                                   'text': cl['text'], **label,
                                   'basis': basis, 'reference_provenance': 'constructed_reference',
                                   'disputed': False})
            else:
                ment = mech[nid]['answers'][who]
                for cl in (ment['claims'] if isinstance(ment, dict) else ment):
                    o = by_claim.get((nid, who, cl['claim_id']))
                    if o:
                        item = {'claim_id': cl['claim_id'], 'slot': cl['slot'], 'text': cl['text'],
                                'statement_type': o['statement_type'],
                                'evidence_status': o['evidence_status'], 'stance': o['stance'],
                                'severity': o['severity'], 'scope': o.get('scope'),
                                'subject': o.get('subject'), 'basis': o['basis'],
                                'reference_provenance': 'human_reviewed',
                                'disputed': bool(o.get('disputed')),
                                'conflict_id': o.get('conflict_id')}
                    else:
                        item = dict(cl)
                        item['reference_provenance'] = 'mechanical_only'
                        item['disputed'] = False
                        item['basis'] = cl['rule']
                    claims.append(item)
            stats['claims_total'] += len(claims)
            for citem in claims:
                if citem['reference_provenance'] == 'human_reviewed':
                    stats['human_reviewed'] += 1
                elif citem['reference_provenance'] == 'constructed_reference':
                    stats['constructed_reference'] += 1
                else:
                    stats['mechanical_only'] += 1
                if citem['disputed']:
                    stats['disputed'] += 1
                if citem['evidence_status'] in ('未提供', '冲突'):
                    stats['reference_unsupported_claims'] += 1
                elif citem['severity'] == '无':
                    stats['reference_protected_claims'] += 1
            rec['answers'][who] = {'claims': claims,
                                   'protocol_truth': protocol_truth(a.get('raw_protocol_text')),
                                   'primary': a['primary'], 'backup_count': len(a['backups'])}
        out.append(rec)

    ref_path = os.path.join(PK, 'REFERENCE_LABELS_V1_1.json')
    json.dump({'reference_version': 'REF-V1.1', 'manual_version': 'MANUAL-V1.1',
               'packet_version': 'CALIB-V1.1', 'frozen_before_any_test_judge': True,
               'real_model_calls': 0, 'stats': stats, 'nodes': out},
              open(ref_path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

    man = os.path.join(ROOT, 'evaluation', 'JUDGE_MANUAL_V1_1.md')
    thr = os.path.join(ROOT, 'evaluation', 'JUDGE_QUALITY_THRESHOLDS_DECLARED_V1.md')
    manifest = {'frozen_at': '2026-10-25', 'real_model_calls': 0,
                'hashes': {'REFERENCE_LABELS_V1_1.json': sha(ref_path),
                           'nodes.jsonl': sha(os.path.join(PK, 'nodes.jsonl')),
                           'nodes_public.jsonl': sha(os.path.join(PK, 'nodes_public.jsonl')),
                           'SEALED_KEY_V1_1.json': sha(os.path.join(PK, 'SEALED_KEY_V1_1.json')),
                           'reference_overrides.json': sha(os.path.join(PK, 'reference_overrides.json')),
                           'CONTROLS_REF.json': sha(os.path.join(PK, 'CONTROLS_REF.json')),
                           'JUDGE_MANUAL_V1_1.md': sha(man),
                           'JUDGE_QUALITY_THRESHOLDS_DECLARED_V1.md': sha(thr)},
                'splits': {'dev': sum(1 for r in out if r['split'] == 'dev'),
                            'validation': sum(1 for r in out if r['split'] == 'validation')},
                'stats': stats,
                'rule': '参考、分组、阈值、重复抽样在派发裁判之前冻结；冻结后查看验证集只报告结果，'
                        '不得再用同一验证集把规则调到过线。'}
    json.dump(manifest, open(os.path.join(PK, 'FROZEN_MANIFEST.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('FROZEN nodes=%d dev=%d validation=%d claims=%d human_reviewed=%d constructed=%d '
          'mechanical_only=%d disputed=%d ref_unsupported=%d ref_protected=%d real_model_calls=0'
          % (len(out), manifest['splits']['dev'], manifest['splits']['validation'],
             stats['claims_total'], stats['human_reviewed'], stats['constructed_reference'],
             stats['mechanical_only'], stats['disputed'], stats['reference_unsupported_claims'],
             stats['reference_protected_claims']))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
