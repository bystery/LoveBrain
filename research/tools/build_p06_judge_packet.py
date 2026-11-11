# -*- coding: utf-8 -*-
r"""tools/build_p06_judge_packet.py — P06 三批答卷的评审冻结包（V1_1 手册）。

只在 A/B/C 三批 live 全部完整收口后运行。构建：
  192 条首选节点（A/B/C 各 64，父/候选各 32）+ 最多 24 条固定复评 + 36 条开发控制
  （32 旧 + CTL33-36）+ 32 张语义参考卡 + 私有绑定 + 协议异常清单 + 冻结计划 + 新授权单。

纪律：
- 节点 ID 统一按散列顺序编号（P06N####），不编码批次/臂/控制类别；
- 判词输入不含臂名、批次、成本、未来剧情和 expectation；
- 只把**已识别槽**（response.recommended / bad_boy / playful / warm / directions[0..3]）送判；
  其它键（如 bad_guy / 零宽空格键）记入协议异常，不送判，也不静默丢弃；
- 解析失败或首选为空的答卷不生成节点，计入未评（unknown），不补跑、不重发；
- 冻结后任何输入变化，运行器拒绝启动。

运行：
  D:\anaconda\python.exe tools\build_p06_judge_packet.py --mode check
  D:\anaconda\python.exe tools\build_p06_judge_packet.py --mode build
"""
import argparse
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'p06_judge_20261027')
OLD_PK = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023',
                      'knowledge_quality_independent_review_20261026')
AID = 'AUTH-P06-JUDGE-20261027-01'
CAP = 256
ENDPOINT = 'http://127.0.0.1:17770/v1'
MODEL = 'dsf'
TEMPERATURE = 0.7
DIMS = ['goal', 'factuality', 'attribution', 'time_state', 'boundary',
        'memory_use', 'interaction', 'initiative', 'emotion_relation']
CARD_KEYS = ['families', 'allowed_behaviors', 'failure_conditions',
             'review_guidance', 'unresolved_ambiguities']
KNOW_SLOTS = ['response.bad_boy', 'response.playful', 'response.warm',
              'directions[0]', 'directions[1]', 'directions[2]', 'directions[3]']
KNOWN_RESPONSE = {'recommended', 'bad_boy', 'playful', 'warm'}
NULL_SLOT_VALUES = {'null', 'none', 'nil', 'n/a', 'na', '无', '没有', '空'}
REPEAT_TURNS = {'E006': 5, 'E007': 3, 'E008': 3, 'E012': 7}
WARNING = ('History contains authored real sends, not this model draft being automatically sent. '
           'Current intent/note is private guidance. Later story is absent; existing card is a '
           'development aid, not calibrated truth.')
BATCHES = [
    ('A', 'P06_A_BASE_VS_V5', 'logs/p06_A_live_20261027'),
    ('B', 'P06_B_BASE_VS_V6G', 'logs/p06_B_live_20261027'),
    ('C', 'P06_C_BASE_VS_V6I', 'logs/p06_C_live_20261027'),
]


def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def load_json(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def dump_json(path, obj):
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)


def rel(path):
    return os.path.relpath(path, ROOT).replace('\\', '/')


def check_ready():
    """三批 DONE 必须完整，否则拒绝构建。返回批次证据。"""
    ev, problems = [], []
    for tag, stem, livedir in BATCHES:
        donep = os.path.join(ROOT, livedir + '.DONE.json')
        planp = os.path.join(ROOT, 'refs', 'experiment_protocol_20261023', 'p06_20261027',
                             stem + '.INTERLEAVE_PLAN.json')
        if not os.path.isfile(donep):
            problems.append('批次 %s 缺 DONE: %s' % (tag, rel(donep)))
            continue
        if not os.path.isdir(os.path.join(ROOT, livedir)):
            problems.append('批次 %s 缺 live 目录: %s' % (tag, livedir))
            continue
        done = load_json(donep)
        plan = load_json(planp)
        if done.get('mode') != 'live':
            problems.append('%s DONE.mode=%r' % (tag, done.get('mode')))
        if done.get('started') != 64 or done.get('completed') != 64 or done.get('failed'):
            problems.append('%s 未完整: started=%r completed=%r failed=%r'
                            % (tag, done.get('started'), done.get('completed'), done.get('failed')))
        if done.get('stopped') is not None:
            problems.append('%s stopped=%r' % (tag, done.get('stopped')))
        if done.get('order_verified') is not True or done.get('dispatch_bijection') is not True:
            problems.append('%s 顺序/双射未过' % tag)
        if done.get('real_model_calls') != 64:
            problems.append('%s real_model_calls=%r != 64' % (tag, done.get('real_model_calls')))
        if done.get('plan_sha256') != sha256_file(planp):
            problems.append('%s 计划散列与 DONE 不符' % tag)
        ids = plan['frozen']['identity_closure']
        for arm in ('parent', 'candidate'):
            if done['arm_identity_sha256'][arm] != ids[arm]['identity_sha256']:
                problems.append('%s %s 身份与计划不符' % (tag, arm))
        ev.append({'tag': tag, 'stem': stem, 'live_dir': livedir,
                   'done_path': rel(donep), 'done_sha256': sha256_file(donep),
                   'plan_path': rel(planp), 'plan_sha256': sha256_file(planp),
                   'arm_mapping': done['arm_mapping'],
                   'arm_identity_sha256': done['arm_identity_sha256'],
                   'started': done['started'], 'completed': done['completed'],
                   'failed': done['failed'], 'real_model_calls': done['real_model_calls'],
                   'plan': plan})
    if problems:
        return None, problems
    return ev, []


def load_episodes():
    eps = {}
    for ep in ('E006', 'E007', 'E008', 'E012'):
        eps[ep] = load_json(os.path.join(ROOT, 'benchmark', 'episodes', ep + '.json'))
    return eps


def flatten_history(ep, upto_turn):
    out = []
    for idx, t in enumerate(ep['turns'], 1):
        if idx > upto_turn:
            break
        for mi, m in enumerate(t['input']['messages']):
            out.append({'evidence_id': '%s#m%d' % (t['case_id'], mi),
                        'role': m['role'], 'text': m['text'], 'ts': m['ts']})
    return out


def parse_answer(reply_path):
    """返回 (answer, anomalies, fail_reason)。只认已知槽。"""
    raw = open(reply_path, 'r', encoding='utf-8').read()
    anomalies = []
    try:
        d = json.loads(raw)
    except Exception as exc:                                   # noqa: BLE001
        return None, anomalies, 'json_parse_fail: %s' % str(exc)[:160]
    if not isinstance(d, dict):
        return None, anomalies, 'top_level_not_object'
    resp = d.get('response')
    if not isinstance(resp, dict):
        return None, anomalies, 'response_not_object'
    extra = sorted([k for k in resp.keys() if k not in KNOWN_RESPONSE])
    if extra:
        anomalies.append({'kind': 'unrecognized_response_keys',
                          'keys_repr': [repr(k) for k in extra]})
    primary = resp.get('recommended')
    if not isinstance(primary, str) or not primary.strip():
        return None, anomalies, 'primary_missing_or_empty'
    primary = primary.strip()
    backups = []
    dirs = d.get('directions')
    for slot in KNOW_SLOTS:
        if slot.startswith('response.'):
            val = resp.get(slot.split('.', 1)[1])
        else:
            if not isinstance(dirs, list):
                continue
            i = int(slot[slot.index('[') + 1:-1])
            val = dirs[i] if i < len(dirs) else None
        if val is None:
            continue
        if not isinstance(val, str):
            anomalies.append({'kind': 'slot_not_string', 'slot': slot,
                              'type': type(val).__name__})
            continue
        if not val.strip():
            anomalies.append({'kind': 'slot_empty_string', 'slot': slot})
            continue
        if val.strip().lower() in NULL_SLOT_VALUES:
            # 模型把占位符写成字符串 "null" 等：不是可发正文，按空槽排除（裁判手册也这样处理）
            anomalies.append({'kind': 'slot_null_placeholder_string', 'slot': slot,
                              'value': val.strip()[:20]})
            continue
        if val.strip() == primary:
            anomalies.append({'kind': 'slot_equals_primary', 'slot': slot})
        backups.append({'slot': slot, 'text': val})
    if not isinstance(dirs, list):
        anomalies.append({'kind': 'directions_not_list',
                          'type': type(dirs).__name__})
    else:
        if len(dirs) != 4:
            anomalies.append({'kind': 'directions_len', 'len': len(dirs)})
        for i, x in enumerate(dirs):
            if x is not None and not isinstance(x, str):
                anomalies.append({'kind': 'direction_not_string', 'index': i,
                                  'type': type(x).__name__})
    return {'primary': primary, 'backups': backups}, anomalies, None


def collect(evidence, eps, r2, ref):
    """读取 192 份答卷，生成 primary/repeat 条目 + 库存 + 异常。"""
    entries, inventory, anomalies, missing_cards = [], [], [], set()
    for batch in evidence:
        tag = batch['tag']
        mapping = batch['arm_mapping']
        live = os.path.join(ROOT, batch['live_dir'])
        for at in batch['plan']['attempts']:
            ep_id, turn, case, arm = at['episode'], int(at['turn']), at['case_id'], at['arm']
            cand = mapping[arm]
            ans_dir = os.path.join(live, '%s_%s' % (arm, ep_id), 'out',
                                   'turn_%02d_%s' % (turn, case))
            manp = os.path.join(ans_dir, 'manifest.json')
            rawp = os.path.join(ans_dir, 'reply_raw.txt')
            inv = {'batch': tag, 'episode': ep_id, 'turn': turn, 'case_id': case,
                   'arm': arm, 'candidate': cand, 'answer_dir': rel(ans_dir)}
            if not (os.path.isfile(manp) and os.path.isfile(rawp)):
                inv['status'] = 'missing_output'
                inventory.append(inv)
                continue
            man = load_json(manp)
            if man.get('case_id') != case or man.get('candidate') != cand:
                inv['status'] = 'manifest_mismatch'
                inventory.append(inv)
                continue
            inv.update({'reply_ingest': man.get('reply_ingest'),
                        'score10': man.get('score10'), 'veto': man.get('veto'),
                        'mechanism_error': man.get('mechanism_error'),
                        'audit_errors': man.get('audit_errors'),
                        'tokens': man.get('tokens'),
                        'reply_sha256': sha256_file(rawp)})
            ans, anom, fail = parse_answer(rawp)
            for x in anom:
                x.update({'batch': tag, 'case_id': case, 'arm': arm})
                anomalies.append(x)
            if fail:
                inv['status'] = 'unjudgeable'
                inv['fail_reason'] = fail
                inventory.append(inv)
                continue
            if case not in r2 or case not in ref:
                missing_cards.add(case)
                inv['status'] = 'missing_card'
                inventory.append(inv)
                continue
            ep = eps[ep_id]
            node = {
                'evidence': {
                    'persona': ep['persona'],
                    'initial_kb': ep['initial_kb'],
                    'real_history': flatten_history(ep, turn),
                    'current_input': json.loads(json.dumps(ep['turns'][turn - 1]['input'],
                                                           ensure_ascii=False)),
                    'current_card': {k: r2[case].get(k) for k in CARD_KEYS},
                    'warning': WARNING,
                },
                'reference_card': {k: v for k, v in ref[case].items() if k != 'case_id'},
                'answer': ans,
                '_meta': {'batch': tag, 'arm': arm, 'candidate': cand,
                          'case_id': case, 'turn': turn,
                          'answer_dir': rel(ans_dir),
                          'reply_sha256': inv['reply_sha256'],
                          'arm_identity_sha256': batch['arm_identity_sha256'][arm],
                          'plan_sha256': batch['plan_sha256'],
                          'done_sha256': batch['done_sha256']},
            }
            inv['status'] = 'judgeable'
            inv['node_kind'] = 'primary'
            inventory.append(inv)
            entries.append({'kind': 'primary', 'node': node, 'inv': inv})
    if missing_cards:
        raise SystemExit('参考卡缺案例: %s' % sorted(missing_cards))
    primaries = [e for e in entries if e['kind'] == 'primary']
    repeat_sources = [e for e in primaries
                      if REPEAT_TURNS.get(e['node']['_meta']['case_id'].split('_T')[0])
                      == e['node']['_meta']['turn']]
    for e in repeat_sources:
        n2 = json.loads(json.dumps(e['node'], ensure_ascii=False))
        inv2 = dict(e['inv'])
        inv2['node_kind'] = 'repeat'
        inv2['repeat_of_case'] = e['node']['_meta']['case_id']
        entries.append({'kind': 'repeat', 'node': n2, 'inv': inv2})
    if len(primaries) + sum(1 for x in inventory if x['status'] in ('unjudgeable', 'missing_output', 'manifest_mismatch')) != 192:
        raise SystemExit('首选账不平：judgeable %d + 未评 %d != 192'
                         % (len(primaries), sum(1 for x in inventory
                                                if x['status'] in ('unjudgeable', 'missing_output', 'manifest_mismatch'))))
    return entries, inventory, anomalies


def load_controls():
    old = load_json(os.path.join(OLD_PK, 'QUALITY_CONTROLS_DEV_V1.json'))['controls']
    add = load_json(os.path.join(OLD_PK, 'QUALITY_CONTROLS_ADDENDUM_V1_1.json'))['controls']
    controls = list(old) + list(add)
    ids = [c['control_id'] for c in controls]
    if len(ids) != 36 or len(set(ids)) != 36:
        raise SystemExit('控制应为 36 条且唯一，实得 %d（唯一 %d）' % (len(ids), len(set(ids))))
    for c in controls:
        if not isinstance(c.get('context'), dict) or not isinstance(c.get('answer'), str) \
                or not c['answer'].strip():
            raise SystemExit('控制 %s 结构不合法' % c.get('control_id'))
    return controls


def build(mode):
    ev, problems = check_ready()
    if problems:
        print('NOT READY:')
        for p in problems:
            print('  -', p)
        return 1
    eps = load_episodes()
    r2 = {c['case_id']: c for c in load_json(os.path.join(
        ROOT, 'benchmark', 'c4_screen_20261026',
        'GRADING_CARDS_C4_E006_E007_E008_E012_R2.json'))}
    refdoc = load_json(os.path.join(OLD_PK, 'QUALITY_REFERENCE_CARDS_V1.json'))
    ref = {c['case_id']: c for c in refdoc['cards']}
    controls = load_controls()
    entries, inventory, anomalies = collect(ev, eps, r2, ref)

    # 统一 ID 空间：节点 + 控制都按内容散列排序编号，ID 不含类别信息
    id_items = []
    for e in entries:
        m = e['node']['_meta']
        key = sha256_bytes(('P06ID|N|%s|%s|%s|%s' % (
            m['batch'], m['case_id'], m['arm'], e['kind'])).encode('utf-8'))
        id_items.append((key, 'node', e))
    for c in controls:
        key = sha256_bytes(('P06ID|C|%s' % c['control_id']).encode('utf-8'))
        id_items.append((key, 'control', c))
    id_items.sort(key=lambda x: x[0])
    node_id_by_key = {}       # (batch,case,arm,kind) -> final id
    final_nodes, final_bindings, final_controls = [], [], []
    kind_by_id = {}
    pos = 0
    for key, typ, obj in id_items:
        pos += 1
        nid = 'P06N%04d' % pos
        if typ == 'node':
            e = obj
            m = e['node']['_meta']
            e['node']['_meta']['node_id'] = nid
            e['node']['_meta']['node_kind'] = e['kind']
            final_nodes.append(e['node'])
            final_bindings.append({'node_id': nid, 'node_kind': e['kind'],
                                   'batch': m['batch'], 'arm': m['arm'],
                                   'candidate': m['candidate'], 'case_id': m['case_id'],
                                   'turn': m['turn'], 'answer_dir': m['answer_dir'],
                                   'reply_sha256': m['reply_sha256'],
                                   'arm_identity_sha256': m['arm_identity_sha256'],
                                   'plan_sha256': m['plan_sha256'],
                                   'done_sha256': m['done_sha256']})
            node_id_by_key[(m['batch'], m['case_id'], m['arm'], e['kind'])] = nid
            kind_by_id[nid] = e['kind']
            e['inv']['node_id'] = nid
            e['inv']['node_kind'] = e['kind']
        else:
            c = obj
            final_controls.append({'node_id': nid, 'control_id': c['control_id'],
                                   'group': c.get('group'), 'context': c['context'],
                                   'answer': c['answer'], 'expectation': c.get('expectation')})
            kind_by_id[nid] = 'control'
    # repeat_of：按同一 (batch,case,arm) 的首选绑定
    for b in final_bindings:
        if b['node_kind'] == 'repeat':
            src = node_id_by_key.get((b['batch'], b['case_id'], b['arm'], 'primary'))
            if not src:
                raise SystemExit('复评找不到首选源: %s' % b)
            b['repeat_of'] = src

    planned = len(final_nodes) + len(final_controls)
    if planned > CAP:
        raise SystemExit('计划 %d > 上限 %d' % (planned, CAP))
    # 派发顺序 = 最终 ID 顺序（已由散列打散，类别/批次/臂不聚集）
    plan = [{'kind': kind_by_id['P06N%04d' % i], 'item_id': 'P06N%04d' % i}
            for i in range(1, planned + 1)]
    nonempty_backups = sum(len(n['answer']['backups']) for n in final_nodes)
    unjudgeable = sum(1 for x in inventory if x['status'] != 'judgeable')

    if mode == 'check':
        print('CHECK OK: nodes=%d (primary=%d repeat=%d) controls=%d planned=%d backups=%d '
              'anomalies=%d unjudgeable=%d'
              % (len(final_nodes), len([b for b in final_bindings if b['node_kind'] == 'primary']),
                 len([b for b in final_bindings if b['node_kind'] == 'repeat']),
                 len(final_controls), planned, nonempty_backups, len(anomalies), unjudgeable))
        return 0

    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, 'P06_NODES.jsonl'), 'w', encoding='utf-8') as f:
        for n in final_nodes:
            f.write(json.dumps(n, ensure_ascii=False) + '\n')
    dump_json(os.path.join(OUT, 'P06_CONTROLS.json'),
              {'kind': 'p06_controls_v1_1', 'count': len(final_controls),
               'note': '32 old development controls + CTL33-36 addendum; expectations are offline-only',
               'controls': final_controls})
    dump_json(os.path.join(OUT, 'P06_REFERENCE_CARDS.json'),
              {'kind': 'p06_reference_cards_v1', 'cards_count': len(refdoc['cards']),
               'source': rel(os.path.join(OLD_PK, 'QUALITY_REFERENCE_CARDS_V1.json')),
               'cards': refdoc['cards']})
    dump_json(os.path.join(OUT, 'P06_PRIVATE_BINDINGS.json'), final_bindings)
    dump_json(os.path.join(OUT, 'P06_ANSWER_INVENTORY.json'),
              {'count': len(inventory), 'inventory': inventory})
    dump_json(os.path.join(OUT, 'P06_PROTOCOL_ANOMALIES.json'),
              {'count': len(anomalies), 'anomalies': anomalies})
    manual_src = os.path.join(OLD_PK, 'QUALITY_MANUAL_V1_1.md')
    with open(manual_src, 'r', encoding='utf-8') as f:
        manual_text = f.read()
    with open(os.path.join(OUT, 'QUALITY_MANUAL_V1_1.md'), 'w', encoding='utf-8') as f:
        f.write(manual_text)

    ledger = os.path.join(ROOT, 'logs', 'auth_budget', AID + '.jsonl')
    if os.path.isfile(ledger) and os.path.getsize(ledger) > 0:
        print('拒绝覆盖已有账本: %s' % rel(ledger))
        return 1
    auth = {
        'authorization_id': AID,
        'authorized': True,
        'status': 'ACTIVE',
        'authorization_basis': {
            'kind': 'user_cycle_authorization_20261027',
            'user_quote': '我授权你按该V2任务继续本周期固定dsf的模型实验，按其16,384次新增管理上限逐批建立有限、可核验的授权和预算，生成/裁判/命题或对方模拟分账',
            'interpretation': 'P06 judge batch is a separate judge account under the same cycle cap; harness generations already covered by AUTH-P06-MAX288V2-20261027-01. No new human signature or per-batch approval claimed.',
        },
        'real_endpoint': {'api_base': ENDPOINT, 'model': MODEL,
                          'temperature': TEMPERATURE, 'max_attempts': CAP},
        'budget_ledger': ledger.replace('/', '\\'),
        'scope': {
            'scope_id': 'p06_quality_judge_20261027_v1',
            'purpose': 'P06 three two-arm batches (A base-vs-v5, B base-vs-v6g, C base-vs-v6i), primary answers + fixed repeats + authored development controls; advisory nine-dimensional review, NOT personal willingness prediction, NOT product integration.',
            'endpoint': ENDPOINT,
            'model_alias': MODEL,
            'temperature': TEMPERATURE,
            'automatic_judge_generations': True,
            'automatic_candidate_generations': False,
            'failure_policy': 'Failure consumes quota. No retries, no refill, stop on transport failure. Invalid semantic response is preserved as unassessed, not regenerated.',
            'gate_before_dispatch': 'Freeze nodes, controls, cards, V1.1 manual, private bindings, dispatch plan and input hashes; verify all three harness batches DONE 64/64 first.',
        },
        'planned_attempts': planned,
        'unused_capacity_policy': 'remaining capacity is not retry permission; not a spending target',
        'human_willingness': 'unknown; model forbidden to fill user labels',
    }
    dump_json(os.path.join(OUT, 'AUTHORIZATION_P06_JUDGE.json'), auth)

    manifest = {
        'kind': 'p06_judge_manifest_v1',
        'frozen_at': '2026-10-27',
        'authorization_id': AID,
        'decision_rules': {
            'path': 'refs/experiment_protocol_20261023/p06_judge_20261027/P06_DECISION_RULES.md',
            'sha256': sha256_file(os.path.join(OUT, 'P06_DECISION_RULES.md'))
            if os.path.isfile(os.path.join(OUT, 'P06_DECISION_RULES.md')) else None,
        },
        'batches': [{k: v for k, v in b.items() if k != 'plan'} for b in ev],
        'node_counts': {'primary': len([b for b in final_bindings if b['node_kind'] == 'primary']),
                        'repeat': len([b for b in final_bindings if b['node_kind'] == 'repeat']),
                        'control': len(final_controls), 'total': planned,
                        'nonempty_backups': nonempty_backups},
        'repeat_policy': 'one turn per story per arm per batch: E006_T05, E007_T03, E008_T03, E012_T07',
        'judge_input_rule': 'recognized slots only (response.recommended primary; bad_boy/playful/warm/directions[0..3] backups); unrecognized keys are protocol anomalies, not judged and not silently dropped',
        'blindness': 'node IDs are hash-order sequential and do not encode batch/arm/kind; payload contains no arm, cost, future plot or expectation',
        'unjudgeable_count': unjudgeable,
        'anomaly_count': len(anomalies),
    }
    dump_json(os.path.join(OUT, 'P06_MANIFEST.json'), manifest)

    inputs = ['P06_NODES.jsonl', 'P06_CONTROLS.json', 'P06_REFERENCE_CARDS.json',
              'P06_PRIVATE_BINDINGS.json', 'QUALITY_MANUAL_V1_1.md',
              'P06_MANIFEST.json', 'AUTHORIZATION_P06_JUDGE.json']
    hashes = {f: sha256_file(os.path.join(OUT, f)) for f in inputs}
    plan_doc = {
        'kind': 'p06_quality_plan_v1',
        'frozen_at': '2026-10-27',
        'authorization_id': AID,
        'ledger': rel(ledger),
        'cap': CAP,
        'planned_attempts': planned,
        'unused_capacity_policy': 'remaining is not retry permission; not a spending target',
        'config_identity': {
            'endpoint': ENDPOINT, 'model_alias': MODEL, 'temperature': TEMPERATURE,
            'stream': False,
            'api_config_identity': 'api_cfg:openai_compat_stream_false_v1',
            'timeout_s_engineering': 240,
        },
        'input_hashes_sha256': hashes,
        'counts': manifest['node_counts'],
        'dispatch_order': plan,
        'notes': [
            'V1.1 manual; controls are worker-AI authored development controls, not an independent sealed set',
            'semantic reference cards authored by worker; same-AI self-check registered',
            'no human willingness labels anywhere; AI forbidden to fill W',
            'cross-batch comparisons are exploratory; only within-batch pairs are strict',
        ],
    }
    dump_json(os.path.join(OUT, 'P06_QUALITY_PLAN.json'), plan_doc)
    print('FROZEN: %s' % rel(os.path.join(OUT, 'P06_QUALITY_PLAN.json')))
    print('nodes=%d primary=%d repeat=%d controls=%d planned=%d backups=%d anomalies=%d unjudgeable=%d'
          % (len(final_nodes), manifest['node_counts']['primary'],
             manifest['node_counts']['repeat'], len(final_controls), planned,
             nonempty_backups, len(anomalies), unjudgeable))
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', choices=('check', 'build'), default='check')
    ap.add_argument('--model', default=None,
                    help='裁判模型别名（默认 dsf）。dsf 于 2026-10-27 15:14 上游失效后改用 qwen3.8-flash')
    ap.add_argument('--aid', default=None, help='裁判授权 ID（建新单，不改旧单、不放大旧额度）')
    ap.add_argument('--out', default=None, help='题包输出目录（默认覆盖旧包；建议新目录保留 dsf 中断证据）')
    ap.add_argument('--cap', type=int, default=None, help='授权上限（默认 256）')
    a = ap.parse_args()
    global AID, MODEL, OUT, CAP
    if a.model:
        MODEL = a.model
    if a.aid:
        AID = a.aid
    if a.cap:
        CAP = a.cap
    if a.out:
        OUT = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out)
    sys.exit(build(a.mode))


if __name__ == '__main__':
    main()
