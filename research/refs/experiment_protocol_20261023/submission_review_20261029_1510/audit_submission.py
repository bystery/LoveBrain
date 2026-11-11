"""Independent read-only recomputation. No gateway/network calls."""
import collections, hashlib, importlib.util, json, math, statistics, sys
from pathlib import Path

ROOT = Path(r'D:\lovebrain-arena')
BASE = ROOT/'refs/experiment_protocol_20261023/final_mainline_review_20261029'
OUT = Path(__file__).parent
sys.path.insert(0,str(ROOT/'tools'))
DIMS = ('goal','factuality','attribution','time_state','boundary','memory_use','interaction','initiative','emotion_relation')
def j(p): return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def jl(p): return [json.loads(s) for s in Path(p).read_text(encoding='utf-8-sig').splitlines() if s.strip()]
def sha(b): return hashlib.sha256(b).hexdigest()
def pct(xs,q):
    a=sorted(xs); return a[min(len(a)-1,math.ceil(len(a)*q)-1)] if a else None
def load(name,path):
    s=importlib.util.spec_from_file_location(name,str(path));m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

# Identify the actual historical implementations, never relabel as r4.
old=ROOT/'refs/experiment_protocol_20261023/s1_action_live_20261028'
hist={}
for b in ('S1','S2','S2R','S3','D1','R3A','R3B'):
    sc=j(old/f'{b}_SCORECARD.json'); hist[b]=sc['arms']
f1=j(BASE/'F1_ANALYSIS.json')
result={'historical_actual_arms':hist,'historical_127_is_r4_vs_original':False}

bind=j(BASE/'F3_PRIVATE_BINDINGS.json')
materials={x['unit_id']:x for x in jl(BASE/'F3_MATERIALS.jsonl')}
ratings=jl(ROOT/'logs/f3_judge_20261029_115709/F3_RATINGS.jsonl')
assert len(bind)==len(materials)==len(ratings)==72
assert len({r['unit_id'] for r in ratings})==72
builder=load('submission_reply_payload',ROOT/'tools/build_f3_blind_materials.py')
wins=collections.Counter(); storywins=collections.defaultdict(collections.Counter)
stats={a:{'prompt':0,'completion':0,'latency_ms':[],'calls':0,'models':collections.Counter(),'protocol_failures':[]} for a in ('parent','candidate')}
paired={d:[] for d in DIMS}; maj={a:[] for a in ('parent','candidate')};n_events=collections.Counter()
all_checks=[]
for r in ratings:
    uid=r['unit_id']; b=bind[uid]; m=materials[uid]['payload'];p=r['parsed']
    v=p.get('verdict');w=b.get('a_arm') if v=='A' else b.get('b_arm') if v=='B' else v
    wins[w]+=1;storywins[b['story']][w]+=1
    sides={b['a_arm']:p['A'],b['b_arm']:p['B']}
    for d in DIMS:
        x=sides['parent'].get('scores',{}).get(d);y=sides['candidate'].get('scores',{}).get(d)
        if type(x) in (int,float) and type(y) in (int,float):paired[d].append((x,y))
    for letter,arm in (('A',b['a_arm']),('B',b['b_arm'])):
        td=ROOT/b['turn_dir'][arm]
        actual=builder.reply_payload(str(td))
        assert actual==m['answers'][letter],(uid,arm,'reply payload differs')
        assert sha(json.dumps(actual,ensure_ascii=False,sort_keys=True).encode())==b['answer_sha256'][letter]
        man=j(td/'manifest.json')
        want='reference_product_v1' if arm=='parent' else 'action_policy_r4_guard_final'
        assert man['candidate']==want,(uid,arm,man['candidate'])
        assert man['case_id']==b['case_id']
        cs=j(td/'calls.json');cs=cs if isinstance(cs,list) else [cs]
        assert len(cs)==1,(uid,'not one call')
        c=cs[0];u=c.get('usage') or {};s=stats[arm]
        s['calls']+=1;s['prompt']+=u.get('prompt') or 0;s['completion']+=u.get('completion') or 0
        s['latency_ms'].append(c['latency_ms']);s['models'][str(c.get('resp_model'))]+=1
        if man.get('veto'):s['protocol_failures'].append({'unit':uid,'veto':man['veto']})
        for e in sides[arm].get('major_errors') or []:maj[arm].append({'unit':uid,'error':e})
        all_checks.append({'unit':uid,'arm':arm,'answer_payload_and_sha_verified':True,'candidate_manifest_verified':True,'raw_file_sha256':sha((td/'calls.json').read_bytes())})
for a,s in stats.items():
    xs=s.pop('latency_ms');s['latency_p50_ms']=statistics.median(xs);s['latency_p95_nearest_rank_ms']=pct(xs,.95);s['latency_sum_ms']=sum(xs);s['latency_max_ms']=max(xs);s['total_tokens']=s['prompt']+s['completion'];s['models']=dict(s['models'])
result['f3']={'pairs':72,'stories':4,'wins':dict(wins),'per_story':{k:dict(v) for k,v in storywins.items()},'paired_dimension_scores':{d:{'n':len(v),'parent':round(statistics.mean(x for x,y in v),4),'candidate':round(statistics.mean(y for x,y in v),4),'paired_delta':round(statistics.mean(y-x for x,y in v),4)} for d,v in paired.items()},'major_error_items':maj,'resource_by_arm':stats,'binding_checks':len(all_checks),'all_bindings_passed':True}
(OUT/'F3_BINDING_CHECKS.json').write_text(json.dumps(all_checks,ensure_ascii=False,indent=2),encoding='utf-8')

cost=j(BASE/'F6_COST_INDEX.json');ledgers=[]
for a in cost['authorizations']:
    es=jl(ROOT/a['ledger']);c=collections.Counter(e.get('kind') for e in es)
    started={e['attempt_id'] for e in es if e.get('kind')=='attempt_started'}
    ended={e.get('attempt_id') for e in es if e.get('kind') in ('attempt_completed','attempt_failed')}
    pending=sorted(started-ended)
    assert c['authorization_closed']>=1,a['authorization_id']
    ledgers.append({'authorization_id':a['authorization_id'],'events':dict(c),'orphan_started_no_terminal':pending,'closed':True})
result['ledger']={'authorizations':ledgers,'started':sum(x['events'].get('attempt_started',0) for x in ledgers),'completed':sum(x['events'].get('attempt_completed',0) for x in ledgers),'failed':sum(x['events'].get('attempt_failed',0) for x in ledgers),'orphan_started_no_terminal':sum(len(x['orphan_started_no_terminal']) for x in ledgers)}

# Independently recompute exact frozen file closure identities.
api=load('submission_api_trial',ROOT/'tools/api_trial.py')
ident=[]
fr=j(BASE/'FINAL_CANDIDATE_FREEZE_20261029.json');f5=j(BASE/'F5_CANDIDATE_FREEZE_20261029.json')
for name,rec in [('action_policy_r4_guard_final',fr['candidates']['action_policy_r4_guard_final']),('action_policy_r4_guard_e3',f5)]:
    cur=api.harness_identity(name)
    ident.append({'candidate':name,'sha256':cur['identity_sha256'],'files':len(cur['files']),'matches_frozen':cur['files']==rec['files'] and cur['identity_sha256']==rec['identity_sha256']})
result['identities']=ident

# Focus on original evidence for the high severity claims, not judge agreement.
selected=[]
for arm,es in maj.items():
    for e in es:
        u=e['unit'];m=materials[u]['payload'];b=bind[u];side='A' if b['a_arm']==arm else 'B'
        selected.append({'unit':u,'arm':arm,'judge_error':e['error'],'answer':m['answers'][side], 'context':m['context'],'card':m['card']})
(OUT/'SEVERE_ERROR_SOURCE_REVIEW.json').write_text(json.dumps(selected,ensure_ascii=False,indent=2),encoding='utf-8')
result['cautions']=['F1 pools original-vs-r1/r2/r3, v6-vs-r1, r2-vs-r3; it is not r4-vs-original evidence.','F3 cards were authored after seeing candidate answers: evaluator-blinded developmental evaluation, not an untouched holdout.','8 unique control pairs in two orders are not 16 independent calibration examples.','F3 serious error items are 8 candidate vs 5 parent; absence of degradation is not established.','More available facts in prompt is not long-term retrieval; capture/fidelity/update remain unmeasured.','An E3 deterministic syntax repair does not require regenerating all historical outputs; replay equivalence and localized live regression can carry scoped evidence.','506 started minus 497 completed minus 5 failed leaves 4 orphan attempts; closed budgets do not manufacture terminal evidence.']
(OUT/'INDEPENDENT_CHECKS.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'f3':{k:v for k,v in result['f3'].items() if k not in ('major_error_items',)},'ledger':{k:v for k,v in result['ledger'].items() if k!='authorizations'},'identities':ident},ensure_ascii=False,indent=2))
