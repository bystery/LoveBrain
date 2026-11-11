"""Bounded independent review: run the actual scheduler with fake HTTP only.
No real network, no edits to worker source or historical raw answers.
"""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import socket
import sys
import uuid
import urllib.request

ROOT=Path('D:/lovebrain-arena')
OUT=Path(__file__).resolve().parent
sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'arena')]
def blocked(*a,**k):
    raise RuntimeError('Independent review: network disabled')
socket.socket.connect=blocked
urllib.request.urlopen=blocked

def load(rel,name):
    spec=importlib.util.spec_from_file_location(name,ROOT/rel)
    mod=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

watched=['tools/exam_interleave_plan.py','arena/isolation/trusted_controller.py',
         'tools/exam_layered_score.py','arena/lb_scorer.py',
         'candidates/reference_product_v1/mechanism/mechanism.py',
         'candidates/v2_recall/mechanism/mechanism.py',
         'candidates/v3_system_slim/mechanism/mechanism.py']
def hashes():
    return {p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in watched}
report={'real_network_calls':0,'before':hashes(),'checks':[],'probes':{}}
for rel,name,hasarg in [('tools/test_annotation_binding.py','wiring_ann',False),
                        ('tools/test_v2_recall.py','wiring_recall',True)]:
    mod=load(rel,name)
    mech=load('candidates/v2_recall/mechanism/mechanism.py','wiring_v2') if hasarg else None
    for fn in mod.CHECKS:
        try:
            with contextlib.redirect_stdout(io.StringIO()):
                detail=fn(mech) if hasarg else fn()
            report['checks'].append({'name':fn.__name__,'pass':True,'detail':detail})
        except Exception as e:
            report['checks'].append({'name':fn.__name__,'pass':False,'error':repr(e)})

ep=load('tools/exam_interleave_plan.py','wiring_scheduler')
plan=ep.emit_plan(str(OUT/'INTERLEAVE_PLAN.json'))
from isolation.trusted_controller import IsolatedRun
orig_step=IsolatedRun.step_one_turn
active={}
dispatches=[]
original_factory=ep.make_fake_urlopen

def observe_step(self,state,*a,**kw):
    turn=state['ep']['turns'][state['next_index']]
    active.clear()
    active.update(candidate=self.candidate,case_id=turn['case_id'],run_id=self.run_id)
    return orig_step(self,state,*a,**kw)

def observing_factory(captured,fault_at=None):
    fake=original_factory(captured,fault_at=fault_at)
    def observe(req,*a,**kw):
        dispatches.append(dict(active))
        return fake(req,*a,**kw)
    return observe

ep.make_fake_urlopen=observing_factory
IsolatedRun.step_one_turn=observe_step

def auth(label,limit=48):
    return {'authorization_id':'AUTH-OFFLINE-REVIEW-'+label+'-'+uuid.uuid4().hex[:8],
            'authorized':True,'status':'ACTIVE',
            'scope':'Independent fake HTTP test only; no real authorization',
            'real_endpoint':{'api_base':ep.PINNED_ENDPOINT,'model':ep.PINNED_MODEL,
                             'temperature':0.7,'max_attempts':limit},
            'budget_ledger':str(OUT/'fake_budgets'/f'{label}.jsonl')}

try:
    dispatches.clear()
    result=ep.execute_plan(plan,mode='capture',out_base=str(OUT/'capture'),keep_tmp=True)
    report['capture']=result
    expected=[{'candidate':ep.ARM_MAP[r['arm']],'case_id':r['case_id']} for r in plan['attempts']]
    actual=[{k:d[k] for k in ('candidate','case_id')} for d in dispatches]
    report['independent_http_dispatches']=list(dispatches)
    report['independent_order_matches_plan']=actual==expected
    report['reports']=ep.reports_from_batch(result,plan,str(OUT/'capture'))

    # Exercise the real live code branch with external fake HTTP. Socket is blocked.
    dispatches.clear()
    captured=[]
    urllib.request.urlopen=observing_factory(captured)
    live_dir=OUT/'live_branch_fake_http_default_retention'
    live_result=ep.execute_plan(plan,mode='live',authorization=auth('live'),out_base=str(live_dir))
    report['probes']['live_branch']={
        'fake_http_dispatches':len(captured),
        'ledger':live_result['ledger_kinds'],
        'reported_real_model_calls':live_result['real_model_calls'],
        'output_exists_after_default_return':live_dir.exists(),
        'note':'Real live branch, external fake HTTP; actual network calls=0'}

    # A 48-call plan with allowance 8 must be refused before any dispatch.
    dispatches.clear()
    captured=[]
    urllib.request.urlopen=observing_factory(captured)
    try:
        limited=ep.execute_plan(plan,mode='live',authorization=auth('insufficient',8),
            out_base=str(OUT/'insufficient_budget'),keep_tmp=True)
        report['probes']['insufficient_budget']={
            'fake_http_dispatches':len(captured),'ledger':limited['ledger_kinds'],
            'stopped':limited['stopped']}
    except Exception as e:
        report['probes']['insufficient_budget']={'fake_http_dispatches':len(captured),'error':repr(e)}

    dispatches.clear()
    fail=ep.execute_plan(plan,mode='capture',fault_at=5,
        out_base=str(OUT/'fault5'),keep_tmp=True)
    report['fault5']={'ledger':fail['ledger_kinds'],'stopped':fail['stopped'],
                       'fake_http_dispatches':len(dispatches)}
finally:
    urllib.request.urlopen=blocked
    IsolatedRun.step_one_turn=orig_step

# Show that cross-arm prompt-length subtraction is not an injection measurement.
probe_dir=OUT/'length_probe'
for side,user in [('parent','P'*5000),('candidate','【回忆】\n'+'C'*2000)]:
    d=probe_dir/side/'turn_01_X_T01'
    d.mkdir(parents=True,exist_ok=True)
    (d/'calls.json').write_text(json.dumps([{'tag':'main','user':user}],ensure_ascii=False),encoding='utf-8')
v2=load('candidates/v2_recall/mechanism/mechanism.py','wiring_delta_v2')
rows,mx=ep._combined_newctx_from_calls(str(probe_dir/'parent'),str(probe_dir/'candidate'),v2)
report['probes']['cross_arm_length_delta']={'reported_added':mx,
    'candidate_recall_utf16':v2.rc.utf16_len('【回忆】\n'+'C'*2000),'rows':rows}

# Prove the slim wrapper changes system assets without changing user composition
# or committed memory when both receive the same prerecorded outputs.
parent=load('candidates/reference_product_v1/mechanism/mechanism.py','wiring_parent')
slim=load('candidates/v3_system_slim/mechanism/mechanism.py','wiring_slim')
class Replay:
    def __init__(self,texts): self.texts=list(texts); self.prompts=[]; self.systems=[]
    def generate(self,system,user,**kw):
        self.systems.append(system); self.prompts.append(user)
        return {'text':self.texts.pop(0)}

rows=[]
for eid in ['E001','E002','E003']:
    episode=json.loads((ROOT/'benchmark/episodes'/f'{eid}.json').read_text(encoding='utf-8'))
    raw=[]
    for i,t in enumerate(episode['turns'],1):
        p=ROOT/'logs/exam1_20261023'/eid/f"turn_{i:02d}_{t['case_id']}"/'calls.json'
        calls=json.loads(p.read_text(encoding='utf-8'))
        raw.append(next(c for c in calls if c.get('tag','main')=='main')['raw_text'])
    recordings=[]
    for name,mod in [('reference_product_v1',parent),('v3_system_slim',slim)]:
        sandbox=OUT/'replay'/name/eid
        gw=Replay(raw)
        h=mod.init(str(ROOT/'candidates'/name/'assets'),copy.deepcopy(episode),gw,str(sandbox))
        for i,t in enumerate(episode['turns'],1):
            ctx=copy.deepcopy(t); ctx['turn_index']=i
            mod.run_turn(h,ctx,gw)
        contents={p.relative_to(sandbox).as_posix():p.read_text(encoding='utf-8')
                  for p in sandbox.rglob('*') if p.is_file()}
        recordings.append((gw,contents))
    pg,cg=recordings[0][0],recordings[1][0]
    for i,t in enumerate(episode['turns']):
        rows.append({'case_id':t['case_id'],'user_prompt_equal':pg.prompts[i]==cg.prompts[i],
                     'system_utf16_parent':parent.rc.utf16_len(pg.systems[i]),
                     'system_utf16_slim':slim.rc.utf16_len(cg.systems[i])})
    report.setdefault('memory_replay',[]).append({'episode':eid,
        'files_equal':recordings[0][1]==recordings[1][1]})
report['slim_replay']=rows
report['after']=hashes()
report['watched_source_unchanged']=report['before']==report['after']
report['checks_passed']=sum(r['pass'] for r in report['checks'])
report['checks_total']=len(report['checks'])
(OUT/'CHECKS.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'checks':[report['checks_passed'],report['checks_total']],
    'capture_order':report['independent_order_matches_plan'],
    'capture_ledger':report['capture']['ledger_kinds'],
    'probes':report['probes'],'fault5':report['fault5'],
    'slim_user_prompts_equal':sum(r['user_prompt_equal'] for r in rows),
    'memory_replay':report['memory_replay'],
    'watched_source_unchanged':report['watched_source_unchanged']},ensure_ascii=False))
