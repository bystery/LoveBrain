"""Inventory/results audit only; no new model calls, no changes to historical evidence."""
import collections
import json
import pathlib
import statistics

R=pathlib.Path('D:/lovebrain-arena'); O=pathlib.Path(__file__).parent
def read(p): return json.loads(p.read_text(encoding='utf-8'))
spec=[('v3_system_slim','v3_deletion_live_20261024'),
      ('v4_intent_context_r2','r2_ctx_live_20261024'),
      ('v5_purpose_aligned','v5_ctx_live_20261025'),
      ('v5_new_story','newstory96_live_20261026'),
      ('C4_A','c4_sweep_a_orig_vs_goal_live_20261026'),
      ('C4_B','c4_sweep_b_v5_vs_mem_live_20261026')]
result={'new_model_calls':0,'priority':'quality first; cost and latency secondary',
        'warning':'program score is not reply quality; disjoint story sets are not a version ranking',
        'historical_results':[], 'datasets':[]}
for name,batch in spec:
    record={'experiment':name,'batch':batch,'arms':{}}
    for arm in ['parent','candidate']:
        ps=sorted((R/'logs'/batch).glob(arm+'_*/out/*/scoring.json'))
        ds=[read(p) for p in ps]
        record['arms'][arm]={'n':len(ds),'episodes':sorted(set(p.parents[2].name for p in ps)),
            'program_score10_mean':statistics.mean(d['score10'] for d in ds) if ds else None,
            'program_veto_count':sum(bool(d.get('veto')) for d in ds),
            'reply_quality_common_benchmark':None,
            'KB_recoverable_completeness':None,'KB_supported_precision':None,
            'goal_success_rate_validated':None,'personal_willingness':None}
    result['historical_results'].append(record)
for label,paths in [('E001-E003',[R/'benchmark/dev_pilot_v02'/f'E{i:03d}.json' for i in range(1,4)]),
                    ('E004-E012',[R/'benchmark/episodes'/f'E{i:03d}.json' for i in range(4,13)])]:
    es=[read(p) for p in paths]; ts=[t for e in es for t in e['turns']]
    result['datasets'].append({'group':label,'stories':len(es),'requests':len(ts),
        'turns_with_nonempty_program_gold':sum(bool(t.get('gold')) for t in ts),
        'episodes_with_nonempty_program_gold':sum(bool(e.get('gold')) for e in es),
        'role':'exposed development; not independent heldout'})
es=read(R/'benchmark/positive_capability_20261025/01_EPISODES_P1.json')
result['datasets'].append({'group':'P001-P006','stories':len(es),
    'requests':sum(len(e['turns']) for e in es),'role':'developer-authored exposed new-story experiment',
    'ineffective_ADJUSTED_intent_positions':14})
(O/'MEASURED_EVIDENCE.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
