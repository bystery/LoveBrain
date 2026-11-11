"""Read-only snapshot of live evidence plus deterministic reviewer probes.
No model calls, no changes to active source, data, plans, or ledgers.
"""
import datetime
import hashlib
import importlib.util
import json
from collections import Counter
from pathlib import Path

ROOT=Path('D:/lovebrain-arena')
OUT=Path(__file__).parent
def j(p): return json.loads(Path(p).read_text(encoding='utf-8'))
def jl(p): return [json.loads(x) for x in Path(p).read_text(encoding='utf-8').splitlines() if x.strip()]
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def mod(name,path):
    s=importlib.util.spec_from_file_location(name,path)
    m=importlib.util.module_from_spec(s); s.loader.exec_module(m); return m

r={'snapshot_local_time':datetime.datetime.now().isoformat(), 'real_model_calls':0}
r['budgets']={}
for name in ['AUTH-NEWSTORY-96-20261026-01','AUTH-EVAL-JUDGE-20261025-01',
             'AUTH-STREAM-INT-20261025-01','AUTH-HARNESS-PERF-PHASE1-20261025-01']:
    ev=jl(ROOT/'logs/auth_budget'/f'{name}.jsonl')
    counts=Counter(e['kind'] for e in ev)
    start={e['attempt_id'] for e in ev if e['kind']=='attempt_started'}
    end={e['attempt_id'] for e in ev if e['kind'] in ('attempt_completed','attempt_failed')}
    r['budgets'][name]={'counts':dict(counts),'pending_attempt_ids':sorted(start-end),
                       'unique_started':len(start),'duplicate_start_count':counts['attempt_started']-len(start),
                       'last_ts':ev[-1].get('ts')}

p=ROOT/'refs/experiment_protocol_20261023/newstory_96_20261026/INTERLEAVE_PLAN.json'
plan=j(p); expected=dict(plan['frozen']['config_files'])
for ident in plan['frozen']['identity_closure'].values(): expected.update(ident['files'])
r['newstory_frozen']={'plan_sha256':sha(p),'checked':len(expected),'mismatches':[
    k for k,v in expected.items() if not (ROOT/k).is_file() or sha(ROOT/k)!=v]}
r['available_newstory_outputs']=[]
for p in sorted((ROOT/'logs/newstory96_live_20261026').glob('*/out/*/calls.json')):
    calls=j(p)
    for c in calls:
        t=c.get('timing') or {}
        r['available_newstory_outputs'].append({'case':p.parent.name,'run':p.parents[2].name,
            'stream':t.get('stream_requested'),'usage_source':t.get('usage_source'),
            'first_usable_ms':t.get('first_usable_ms'),'first_preview_ms':t.get('first_preview_ms'),
            'all_ready_ms':t.get('all_ready_ms'),'pre_request_ms':t.get('pre_request_ms')})

runner=mod('review_judge_runner',ROOT/'tools/run_judge_batch_v1_1.py')
node={'evidence':{'fixture':True},'answers':{'甲':{'primary':'LEFT-A'},'乙':{'primary':'RIGHT-B'}}}
normal=json.loads(runner.user_payload(node,False)); swapped=json.loads(runner.user_payload(node,True))
r['swap_probe']={'normal':normal,'swapped':swapped,'mapping_equal':normal==swapped,
                 'insertion_order_normal':list(normal['answers']),
                 'insertion_order_swapped':list(swapped['answers'])}
ana=mod('review_irr',ROOT/'tools/analyze_irr_v2.py')
sample={'parsed':{'goal':{'甲':1,'乙':3,'action_basis':{'甲':'A','乙':'B'}},
    'insufficient_info':{'甲':'ok','乙':'wrong'},
    'protocol_anomaly':{'甲':'none','乙':'missing_slot'},'claims':[],'pref':'甲'}}
r['normalization_probe']={'before':sample,'after':ana.normalize_rec(sample)}
ref=j(ROOT/'evaluation/CALIBRATION_PACKET_V1_1/REFERENCE_LABELS_V1_1.json')
r['reference_stats']=ref['stats']
r['goal_calibration']={}
for split,fn in [('dev','IRR_AGREEMENT_V2_dev_debug.json'),('validation','IRR_AGREEMENT_V2_validation_frozen.json')]:
    d=j(ROOT/'evaluation'/fn)
    r['goal_calibration'][split]={'agreement':d['goal']['agreement_pairs'],
         'vs_reference':d['goal'].get('vs_reference_numeric'),
         'qualification':d['qualification']}

es=j(ROOT/'benchmark/positive_capability_20261025/01_EPISODES_P1.json')
r['newstories']=[{'episode':e['episode_id'],'n':len(e['turns']),
    'notes_nonempty':sum(bool(t['input'].get('note')) for t in e['turns']),
    'intent_enabled':sum(bool((t['input'].get('intent') or {}).get('enabled')) for t in e['turns'])}
    for e in es]
rc=mod('review_reference_chain', ROOT/'candidates/reference_product_v1/reference_chain.py')
r['enabled_but_ineffective_intents']=[{'case_id':t['case_id'],'intent':t['input']['intent']}
    for e in es for t in e['turns'] if (t['input'].get('intent') or {}).get('enabled')
    and not rc.is_intent_effective(t['input']['intent'])]
branches=j(ROOT/'benchmark/positive_capability_20261025/03_CONTROLLED_BRANCHES_P1.json')
r['branch_predicates']={b['branch_id']:b['predicates'] for b in branches['branches']}

# D5 uses custom post-completion consumer hooks. Preserve its success but do not
# infer that the hook is present in the ordinary 96-turn runtime.
d5=j(ROOT/'refs/experiment_protocol_20261023/stream_integration_20261025/RUN_live.json')
r['D5']={'started':d5['attempt_started'],'completed':d5['attempt_completed'],
    'consumption_stamps':sum((x.get('timing') or {}).get('first_usable_ms') is not None
                             for x in d5['attempts_detail']),
    'state_isolated_flag':d5['state_isolated']}
(OUT/'INDEPENDENT_RESULTS.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'snapshot':r['snapshot_local_time'],'budgets':r['budgets'],
    'frozen':r['newstory_frozen'],'outputs':len(r['available_newstory_outputs']),
    'usable_stamps':sum(x['first_usable_ms'] is not None for x in r['available_newstory_outputs']),
    'swap_mapping_equal':r['swap_probe']['mapping_equal'],
    'normalization':r['normalization_probe'],'D5':r['D5']},ensure_ascii=False,indent=2))
