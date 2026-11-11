"""Review submitted v3 batch as-is. Socket disabled; no paid calls."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import socket
import sys
import types
import urllib.request

ROOT=Path('D:/lovebrain-arena')
OUT=Path(__file__).resolve().parent
sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'arena')]
def blocked(*a,**k): raise RuntimeError('Independent review: real network disabled')
socket.socket.connect=blocked
urllib.request.urlopen=blocked
def load(rel,name):
    spec=importlib.util.spec_from_file_location(name,ROOT/rel)
    mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
    return mod
watched=['tools/exam_interleave_plan.py','tools/build_review_worksheet.py',
    'candidates/reference_product_v1/mechanism/mechanism.py',
    'candidates/v3_system_slim/mechanism/mechanism.py',
    'candidates/v0_minimal_events/mechanism/mechanism.py',
    'benchmark/episodes/E004.json','benchmark/episodes/E005.json']
def hashes(): return {p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in watched}
r={'real_model_calls':0,'before':hashes(),'existing_checks':[]}

# Existing v0 assertions, only fixture-output paths relocated; no report overwrite.
rel='tools/test_v0_minimal.py'
src=(ROOT/rel).read_text(encoding='utf-8').replace('"logs", "_v0tmp"',
    '"refs", "experiment_protocol_20261023", "v3_trial_readiness_review_20261024", "v0_fixtures"')
m=types.ModuleType('review_v0'); m.__file__=str(ROOT/rel)
exec(compile(src,str(ROOT/rel),'exec'),m.__dict__)
for fn in m.CHECKS:
    try:
        with contextlib.redirect_stdout(io.StringIO()): detail=fn()
        r['existing_checks'].append({'name':fn.__name__,'pass':True,'detail':detail})
    except Exception as e:
        r['existing_checks'].append({'name':fn.__name__,'pass':False,'error':repr(e)})

ep=load('tools/exam_interleave_plan.py','review_submitted_batch')
plan=json.loads((ROOT/'refs/experiment_protocol_20261023/v3_deletion_20261024/INTERLEAVE_PLAN.json').read_text(encoding='utf-8'))
ep.verify_frozen_identity(plan)
r['submitted_plan_frozen_matches']=True
execution=ep.execute_plan(plan,mode='capture',out_base=str(OUT/'capture'),keep_tmp=True)
r['capture']={k:execution[k] for k in [
 'attempt_started','attempt_completed','attempt_failed','execution_order_verified',
 'dispatch_bijection','state_isolated','real_model_calls','recall_metrics_applicable',
 'recall_seg_ok','injection_within_budget_ok','evidence_persisted']}
ep4=json.loads((ROOT/'benchmark/episodes/E004.json').read_text(encoding='utf-8'))
seed=ep4['initial_kb']['me.剧集口味']
actual=[]
for arm in ['parent','candidate']:
    cf=OUT/'capture'/f'{arm}_E004'/'out'/'turn_03_E004_T03'/'calls.json'
    calls=json.loads(cf.read_text(encoding='utf-8'))
    main=next(c for c in calls if c.get('tag','main')=='main')
    actual.append({'arm':arm,'seed_value_visible':seed in main['user'],
        'seed_phrase_visible':'甜剧很少看' in main['user']})
r['actual_seed_delivery']=actual

class GW:
    def __init__(self): self.prompts=[]
    def generate(self,system,user,**kw):
        self.prompts.append(user); return {'text':ep._CANNED}
# Validate one minimal mapping correction without editing production data.
fixed=copy.deepcopy(ep4); fixed['initial_kb']={'me':seed}
corrected=[]
for name in ['reference_product_v1','v3_system_slim']:
    mod=load(f'candidates/{name}/mechanism/mechanism.py','seed_'+name)
    gw=GW(); h=mod.init(str(ROOT/'candidates'/name/'assets'),fixed,gw,str(OUT/'seed_fix_proof'/name))
    ctx=copy.deepcopy(fixed['turns'][2]); ctx['turn_index']=3
    mod.run_turn(h,ctx,gw)
    corrected.append({'candidate':name,'seed_visible':seed in gw.prompts[-1]})
r['seed_mapping_fix_component_proof']=corrected

# Mechanical recomputation for all nine newly authored episodes/cards.
imp=load('tools/import_pilot_questions.py','review_new_data_import')
seen_case=set(); seen_ep=set(); counts={'requests':0}; ep_case={}
for n in range(4,13):
    eid=f'E{n:03d}'
    data=json.loads((ROOT/'benchmark/episodes'/f'{eid}.json').read_text(encoding='utf-8'))
    imp.check_episode(data,eid,seen_case,seen_ep,counts)
    ep_case[eid]={t['case_id'] for t in data['turns']}
cards=[]
for fn in ['03_GRADING_CARDS_E004_E006.json','04_GRADING_CARDS_E007_E012.json']:
    cards.extend(json.loads((ROOT/'benchmark/dev_expansion_v01'/fn).read_text(encoding='utf-8')))
cp=OUT/'ALL_NEW_CARDS_EVALUATION_SIDE.json'
cp.write_text(json.dumps(cards,ensure_ascii=False,indent=2),encoding='utf-8')
imp.check_cards(str(cp),72,seen_case,ep_case)
r['new_data_mechanical']={'episodes':len(seen_ep),'requests':counts['requests'],'cards':len(cards),
    'failures':[x for x in imp.HARD if not x[1]]}

# Re-run the earlier no-text probes using their immutable fixture directory.
ws=load('tools/build_review_worksheet.py','review_turn_ledger')
fixture=ROOT/'refs/experiment_protocol_20261023/mainline_independent_review_20261024/worksheet_branches'
rows,_=ws.worksheet_from_exam(str(fixture),{},'fixture')
r['no_text_turn_ledger']={'turns':len({x['case_id'] for x in rows}),
    'rows':len(rows),'statuses':[x['delivery_status'] for x in rows],
    'prefilled_semantic':sum(bool(x['semantic_label']) for x in rows)}

v0=load('candidates/v0_minimal_events/mechanism/mechanism.py','review_v0_direct')
gw=GW(); h=v0.init(str(ROOT/'candidates/v0_minimal_events/assets'),
    {'episode_id':'VISIBLE','persona':{'me':'成年人'},'initial_kb':{'me':'种子哨兵：每周末乐团排练'}},
    gw,str(OUT/'v0_visible'))
v0.run_turn(h,{'case_id':'VISIBLE_T01','turn_index':1,'input':{
    'clock':'2026-12-02 19:58','messages':[{'role':'HER','text':'今天怎么样','ts':'2026-12-02 19:57'}],
    'intent':{'enabled':True,'status':'ACTIVE','text':'目标哨兵：想一起散步'},'note':''}},gw)
user=gw.prompts[-1]
r['v0_external_input_probe']={k:needle in user for k,needle in {
    'intent':'目标哨兵','seed':'种子哨兵','clock':'2026-12-02 19:58',
    'message_ts':'2026-12-02 19:57','source_alias':'m0'}.items()}
r['after']=hashes(); r['watched_source_unchanged']=r['before']==r['after']
(OUT/'CHECKS.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in r.items() if k not in ['before','after','existing_checks']},ensure_ascii=False))
print('v0_existing_checks',sum(x['pass'] for x in r['existing_checks']),len(r['existing_checks']))
