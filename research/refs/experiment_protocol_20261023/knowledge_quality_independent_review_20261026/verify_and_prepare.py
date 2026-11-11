"""Independent, read-only replay of paid artifacts; never calls a model.

Creates state-observability evidence and a blinded, hash-bound quality packet.
Does not rewrite the worker's V1 results, candidates, scoring rules or budgets.
"""
import base64
import hashlib
import importlib.util
import json
import random
from collections import Counter
from pathlib import Path

ROOT = Path('D:/lovebrain-arena')
OUT = Path(__file__).resolve().parent
BATCHES = ['v3_deletion_live_20261024', 'r2_ctx_live_20261024',
           'v5_ctx_live_20261025', 'newstory96_live_20261026',
           'c4_sweep_a_orig_vs_goal_live_20261026',
           'c4_sweep_b_v5_vs_mem_live_20261026']


def read(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def objsha(d):
    return hashlib.sha256(json.dumps(d, ensure_ascii=False, sort_keys=True).encode()).hexdigest()


def save(name, data):
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')


def state_view(path):
    """JSON only: no pickle, execution, filesystem extraction, or staged writes."""
    state = read(path)
    if isinstance(state.get('tree'), dict):
        files = {k: base64.b64decode(v, validate=True).decode('utf-8')
                 for k, v in state['tree'].items()}
        return {'representation': 'base64_file_tree', 'files': files,
                'events': [], 'structured': {}, 'state': state}
    if isinstance(state.get('events'), list):
        return {'representation': 'real_event_log', 'files': {},
                'events': state['events'], 'structured': state.get('mem', {}), 'state': state}
    raise ValueError('unsupported state representation: ' + str(path))


def message_key(m):
    return (m.get('role'), m.get('text'), m.get('ts'))


def inventory():
    rows = []
    for batch in BATCHES:
        for p in sorted((ROOT / 'logs' / batch).glob('*/out/checkpoint.bin')):
            view = state_view(p)
            ep_path, = p.parent.parent.glob('*.copy.json')
            ep = read(ep_path)
            expected = {message_key(m) for t in ep['turns'] for m in t['input']['messages']
                        if m.get('role') in ('HER', 'ME')}
            actual = {message_key(m) for m in view['events']}
            files_blob = '\n'.join(view['files'].values())
            matches = sum(k in actual for k in expected) if actual else None
            rows.append({'batch': batch, 'role_episode': p.parent.parent.name,
                         'path': str(p), 'sha256': sha(p), 'episode_sha256': sha(ep_path),
                         'turn_count': view['state'].get('turn_count'),
                         'representation': view['representation'],
                         'expected_unique_real_messages': len(expected),
                         'event_exact_role_text_timestamp_matches': matches,
                         'legacy_literal_text_matches': (sum(k[1] in files_blob for k in expected)
                                                        if view['files'] else None),
                         'stored_events': len(view['events']),
                         'file_count': len(view['files']),
                         'structured_counts': {k: len(v) for k, v in view['structured'].items()
                                               if isinstance(v, (dict, list))}})
    c4 = [r for r in rows if r['batch'].startswith('c4_')]
    return {'definition': 'Final-state observability and literal real-message retention; NOT semantic KB completeness, distillation quality or long-horizon reliability.',
            'total_observable_states': len(rows), 'c4_observable_states': len(c4),
            'c4_representations': dict(Counter(r['representation'] for r in c4)),
            'c4_event_retention': {role: {
                'matched': sum(r['event_exact_role_text_timestamp_matches'] for r in c4
                               if r['batch'] == batch and r['role_episode'].startswith(prefix)),
                'expected': sum(r['expected_unique_real_messages'] for r in c4
                                if r['batch'] == batch and r['role_episode'].startswith(prefix))}
                for role, batch, prefix in [
                    ('C2', BATCHES[4], 'candidate_'), ('v5', BATCHES[5], 'parent_'),
                    ('C3', BATCHES[5], 'candidate_')]}, 'rows': rows}


def lexical_probes():
    spec = importlib.util.spec_from_file_location('worker_knowledge_score', ROOT/'tools/knowledge_stage_scorecard.py')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    pairs = [
        ('negation', '她明确说周三晚上可以一起吃晚饭', '她明确说周三晚上不可以一起吃晚饭'),
        ('time', '她约的是十一月十八日下午三点在南门见', '她约的是十一月十九日下午五点在南门见'),
        ('subject', '她妹妹下周在学校准备研究生答辩', '我妹妹下周在学校准备研究生答辩')]
    out = []
    for kind, source, altered in pairs:
        hit = mod.best_entry_hit(mod.norm(source), {'test': ('structured', mod.norm(altered))})
        out.append({'kind': kind, 'source': source, 'contradictory_record': altered,
                    'worker_lexical_hit': hit, 'semantic_correct': False})
    atoms, skipped = mod.load_atoms()
    return {'message_entries_called_atoms': len(atoms), 'excluded_non_message_entries': len(skipped),
            'probes': out, 'all_contradictions_pass_lexical_gate': all(bool(x['worker_lexical_hit']) for x in out)}


def semantic_fact_review():
    """Explicit AI reviewer judgments, not labels inferred from string overlap.

    Scope: all 17 final confirmed facts in C3, excluding tentative records,
    completeness and intermediate-state errors. Full cited events were read.
    """
    cross_history = {
        'bb75335b30ee03f7': ('后摇', [3,4,7,8,9]),
        'cd18e46f3a6b2543': ('汤锅', [3,7,8,9]),
    }
    findings = []
    for p in sorted((ROOT/'logs'/BATCHES[5]).glob('candidate_*/out/checkpoint.bin')):
        state = read(p)
        events = {e['uid']:e for e in state['events']}
        for fid, fact in state['mem']['facts'].items():
            cited = [events[u] for u in fact['src_uids']]
            result = 'supported'
            citation = 'direct_support'
            why = '人物、动作和所述状态与引用真实消息相容；这不是完整性或永久有效性的判决。'
            extra = []
            if fid in cross_history:
                word, uids = cross_history[fid]
                citation = 'needs_uncited_prior_context'
                extra = [events[u] for u in uids if u not in fact['src_uids']]
                why = '条目中的“'+word+'”需要同故事更早消息补足，当前src_uids未覆盖全部内容；全文可支持，引用链不完整。'
            if fid == '46b95529fb9ccc95':
                result = 'unsupported_subject_resolution'
                why = ('上一条HER问的是“你…认识小林…通过读书会”，ME回答“不是读书会 是以前同事介绍”。'
                       '条目却改写成“用户与她…介绍认识”，将ME—小林关系换成ME—HER。来源ID存在不能证明主体改写正确。')
                extra = [events[3]]
            findings.append({'fact_id':fid,'episode':p.parent.parent.name,'state_sha256':sha(p),
                             'stored_fact':fact,'cited_events':cited,'additional_context':extra,
                             'content_support':result,'citation_support':citation,'reason':why})
    assert len(findings) == 17
    out = {'reviewer':'This independent AI reviewer; nonblind development review, not user labels or calibrated ground truth.',
           'scope':'C3 final confirmed facts only; tentatives, completeness, intermediate states and other arms not semantically scored here.',
           'records':17,'content_supported':16,'unsupported_subject_resolution':1,
           'supported_but_citations_need_prior_context':2,'rows':findings}
    save('C3_CONFIRMED_FACT_SEMANTIC_REVIEW.json',out)
    return {k:out[k] for k in ['records','content_supported','unsupported_subject_resolution',
                               'supported_but_citations_need_prior_context']}


def ongoing_probes():
    spec = importlib.util.spec_from_file_location('review_reference_chain', ROOT/'candidates/reference_product_v1/reference_chain.py')
    rc = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rc)
    controls = []
    for name, text in [('约饭（时间待定）','饭什么时候吃'),('计划骑车','周末骑车吧')]:
        controls.append({'item_name':name,'message':text,'keywords':rc.extract_keywords(name),
                         'signals':rc.compute_relevance_signals({'name':name},text,'','')})
    paths = sorted((ROOT/'logs'/BATCHES[4]).glob('parent_*/out/turn_*/calls.json'))
    with_section = 0
    for p in paths:
        calls = read(p)
        calls = calls if isinstance(calls,list) else calls.get('calls',[calls])
        with_section += int(any('【进行中事项】' in c.get('user','') for c in calls))
    result = {'actual_baseline_requests':len(paths),'actual_baseline_requests_with_ongoing_section':with_section,
              'controls':controls,'limit':'Signal controls only, not whole-selector or exact historical replay. No required-retrieval denominator has been established.'}
    save('ONGOING_PROBES.json',result)
    return {'requests':len(paths),'ongoing_section':with_section}


def packet():
    source = ROOT/'refs/experiment_protocol_20261023/c4_independent_review_20261026/RAW_REVIEW_ROWS.json'
    rows = read(source)
    assert len(rows) == 128
    cards_path = ROOT/'benchmark/c4_screen_20261026/GRADING_CARDS_C4_E006_E007_E008_E012_R2.json'
    cards = {c['case_id']: c for c in read(cards_path)}
    rng = random.Random(261026)
    shuffled = rows[:]
    rng.shuffle(shuffled)
    nodes, keys = [], []
    for i, r in enumerate(shuffled):
        td = Path(r['dir'])
        raw = td/'reply_raw.txt'
        assert sha(raw) == r['raw_sha256'], str(raw)
        parsed = read(raw)
        assert parsed['response']['recommended'] == r['primary']
        actual_backups = [{'slot':'response.'+k, 'text':v}
                          for k,v in parsed['response'].items()
                          if k != 'recommended' and isinstance(v,str) and v.strip()]
        actual_backups += [{'slot':'directions[%d]' % j, 'text':v}
                           for j,v in enumerate(parsed.get('directions',[]))
                           if isinstance(v,str) and v.strip()]
        assert actual_backups == r['backups'], str(td)
        ep_path, = td.parent.parent.glob('*.copy.json')
        ep = read(ep_path)
        idx = next(j for j, t in enumerate(ep['turns']) if t['case_id'] == r['case_id'])
        turn = ep['turns'][idx]
        manifest = read(td/'manifest.json')
        assert objsha(turn['input']) == manifest['case_input_sha256'], r['case_id']
        role = td.parent.parent.name.split('_',1)[0]
        plan_name = ('sweep_a_orig_vs_goal' if td.parent.parent.parent.name == BATCHES[4]
                     else 'sweep_b_v5_vs_mem')
        plan_path = ROOT/'refs/experiment_protocol_20261023/c4_screen_20261026'/(plan_name+'.INTERLEAVE_PLAN.json')
        plan = read(plan_path)
        assert plan['arm_mapping'][role] == r['arm'] == manifest['candidate']
        history = []
        seen = set()
        for t in ep['turns'][:idx+1]:
            for mi, m in enumerate(t['input']['messages']):
                if m.get('role') not in ('HER','ME'):
                    continue
                k = message_key(m)
                if k not in seen:
                    history.append({'evidence_id': t['case_id']+'#m'+str(mi), **m})
                    seen.add(k)
        card = cards[r['case_id']]
        evidence = card.get('evidence', [])
        # Do not leak future storyboard or anticipated responses to the reviewer.
        assert all(int(e.get('case_id', r['case_id']).split('_T')[1]) <= idx+1 for e in evidence)
        card_public = {k:card[k] for k in ['families','allowed_behaviors','failure_conditions',
                       'review_guidance','unresolved_ambiguities'] if k in card}
        nid = 'Q%03d' % (i+1)
        node = {'node_id': nid, 'evidence': {
            'persona': ep.get('persona',{}), 'initial_kb': ep.get('initial_kb',{}),
            'real_history': history, 'current_input': turn['input'],
            'current_card': card_public,
            'warning': 'History contains authored real sends, not this model draft being automatically sent. Current intent/note is private guidance. Later story is absent; existing card is a development aid, not calibrated truth.'},
            'answer': {'primary': r['primary'], 'backups': r['backups']}}
        nodes.append(node)
        keys.append({'node_id': nid, 'case_id': r['case_id'], 'arm': r['arm'],
                     'raw_sha256': r['raw_sha256'], 'case_input_sha256': manifest['case_input_sha256'],
                     'run_id': manifest.get('run_id'), 'answer_dir': r['dir'],
                     'harness_identity':plan['frozen']['identity_closure'][role]['identity_sha256'],
                     'frozen_plan_path':str(plan_path),'frozen_plan_sha256':sha(plan_path),
                     'node_sha256': objsha(node), 'snapshot_sha256': sha(ep_path)})
    assert set(Counter(k['case_id'] for k in keys).values()) == {4}
    assert set(Counter(k['arm'] for k in keys).values()) == {32}
    (OUT/'QUALITY_NODES.jsonl').write_text(''.join(json.dumps(n,ensure_ascii=False)+'\n' for n in nodes),encoding='utf-8')
    save('PRIVATE_BINDINGS.json',keys)
    repeat_ids = sorted(rng.sample([n['node_id'] for n in nodes],13))
    save('PACKET_MANIFEST.json', {'purpose': 'Uncalibrated nine-dimensional development review; no user preference prediction.',
         'nodes':128, 'nonempty_backups':sum(len(n['answer']['backups']) for n in nodes),
         'repeat_node_ids':repeat_ids, 'main_plus_repeats':141,
         'controls_required_before_calibration_claim':32,
         'hashes':{p.name:sha(p) for p in [OUT/'QUALITY_NODES.jsonl',OUT/'PRIVATE_BINDINGS.json',cards_path,source]},
         'exposure':'Four exposed stories; no independent test-set or population noninferiority claim.'})
    return {'nodes':len(nodes), 'nonempty_backups':sum(len(n['answer']['backups']) for n in nodes), 'bindings_verified':len(keys)}


def main():
    inv = inventory()
    probes = lexical_probes()
    pkt = packet()
    sem = semantic_fact_review()
    ongoing = ongoing_probes()
    save('STATE_OBSERVABILITY.json', inv)
    save('LEXICAL_COUNTEREXAMPLES.json', probes)
    result = {'observable_states':inv['total_observable_states'], 'c4_states':inv['c4_observable_states'],
              'c4_representations':inv['c4_representations'], 'c4_event_retention':inv['c4_event_retention'],
              'semantic_contradictions_passing_lexical_gate':sum(bool(p['worker_lexical_hit']) for p in probes['probes']),
              'packet':pkt, 'C3_final_fact_semantic_pilot':sem, 'ongoing_prompt_observation':ongoing,
              'model_calls':0}
    save('INDEPENDENT_RESULTS.json',result)
    print(json.dumps(result,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
