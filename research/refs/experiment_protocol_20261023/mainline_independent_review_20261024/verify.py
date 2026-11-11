"""Independent mainline audit; all model transport is fake, socket blocked."""
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
import uuid

ROOT=Path('D:/lovebrain-arena')
OUT=Path(__file__).resolve().parent
sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'arena')]
def block(*a,**k): raise RuntimeError('Independent audit: real network disabled')
socket.socket.connect=block
urllib.request.urlopen=block
def load(rel,name):
    p=ROOT/rel
    spec=importlib.util.spec_from_file_location(name,p)
    mod=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod
watch=['tools/exam_interleave_plan.py','tools/exam_layered_score.py',
       'tools/build_review_worksheet.py','arena/isolation/trusted_controller.py',
       'candidates/v0_minimal_events/mechanism/mechanism.py',
       'candidates/v3_system_slim/mechanism/mechanism.py']
def hashes(): return {p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in watch}
result={'real_model_calls':0,'before':hashes(),'existing_checks':[],'probes':{}}
for rel,name,kind in [
 ('tools/test_annotation_binding.py','review_ann',None),
 ('tools/test_v2_recall.py','review_v2','v2'),
 ('tools/test_v3_slim_replay.py','review_slim',None),
 ('tools/test_v0_minimal.py','review_min','relocate_temp')]:
    if kind=='relocate_temp':
        # Same assertions; relocate only temporary fixture writes to this audit.
        source=(ROOT/rel).read_text(encoding='utf-8').replace(
            '"logs", "_v0tmp"',
            '"refs", "experiment_protocol_20261023", "mainline_independent_review_20261024", "v0_unit_work"')
        mod=types.ModuleType(name); mod.__file__=str(ROOT/rel)
        exec(compile(source,str(ROOT/rel),'exec'),mod.__dict__)
    else: mod=load(rel,name)
    arg=load('candidates/v2_recall/mechanism/mechanism.py','v2_direct') if kind=='v2' else None
    for fn in mod.CHECKS:
        try:
            with contextlib.redirect_stdout(io.StringIO()):
                detail=fn(arg) if kind=='v2' else fn()
            result['existing_checks'].append({'name':fn.__name__,'pass':True,'detail':detail})
        except Exception as e:
            result['existing_checks'].append({'name':fn.__name__,'pass':False,'error':repr(e)})

ep=load('tools/exam_interleave_plan.py','mainline_interleave')
plan=ep.emit_plan(str(OUT/'RECALL_PLAN.json'))
def auth(label,cap=48):
    return {'authorization_id':'OFFLINE-REVIEW-'+label+'-'+uuid.uuid4().hex[:8],
        'authorized':True,'status':'ACTIVE','scope':{'purpose':'Fake HTTP only; not a paid authorization'},
        'real_endpoint':{'api_base':ep.PINNED_ENDPOINT,'model':ep.PINNED_MODEL,
                         'temperature':0.7,'max_attempts':cap},
        'budget_ledger':str(OUT/'fake_budgets'/f'{label}.jsonl')}
http=[]
live=ep.execute_plan(plan,mode='live',authorization=auth('live'),
    out_base=str(OUT/'live_fake'),transport=ep.make_fake_urlopen(http))
result['live_fake']={k:live.get(k) for k in [
    'attempt_started','attempt_completed','attempt_failed','real_dispatched','stub_dispatched',
    'execution_order_verified','dispatch_bijection','evidence_persisted','network']}
result['live_fake']['files_exist']=(OUT/'live_fake').is_dir()
result['live_fake']['all_slots_have_attempt_id']=all(len(r['attempt_ids'])==1 for r in live['exec_events'])
result['live_fake']['http_dispatches']=len(http)
result['reports']=ep.reports_from_batch(live,plan,str(OUT/'live_fake'))

http=[]
try:
    ep.execute_plan(plan,mode='live',authorization=auth('too_small',8),
        out_base=str(OUT/'too_small'),transport=ep.make_fake_urlopen(http))
    result['probes']['insufficient_budget']={'rejected':False,'dispatches':len(http)}
except Exception as e:
    result['probes']['insufficient_budget']={'rejected':True,'dispatches':len(http),'error':repr(e)}

preferred=json.loads((ROOT/'refs/experiment_protocol_20261023/CALL_MANIFEST_V3_DELETION_20261024.json').read_text(encoding='utf-8'))
try:
    ep.execute_plan(preferred,mode='capture',out_base=str(OUT/'preferred_manifest_probe'))
    result['probes']['preferred_manifest']={'accepted':True}
except Exception as e:
    result['probes']['preferred_manifest']={'accepted':False,'error':repr(e)}

v0=load('candidates/v0_minimal_events/mechanism/mechanism.py','review_v0_probes')
class GW:
    def __init__(self): self.calls=[]
    def generate(self,system,user,**kw):
        self.calls.append({'system':system,'user':user})
        return {'text':json.dumps({'response':{'recommended':'收到'},'directions':[None]*4,
            'analysis':{'topic_status':'same','topic_label':'日常','scene_facts':[],'ongoing':[]}},ensure_ascii=False)}
gw=GW()
h=v0.init(str(ROOT/'candidates/v0_minimal_events/assets'),
    {'episode_id':'FAIR_INPUT','persona':{'me':'成年人'},
     'initial_kb':{'me':'初始事实哨兵：我每周末在乐团排练'}},gw,str(OUT/'v0_probe'))
ctx={'case_id':'FAIR_INPUT_T01','turn_index':1,'turn_type':'reply','input':{
    'clock':'2026-12-02 19:58',
    'messages':[{'role':'HER','text':'你这周还想做点什么','ts':'2026-12-02 19:57'}],
    'intent':{'enabled':True,'status':'ACTIVE','text':'当前意图哨兵：想找机会一起出门散步'},
    'note':''}}
v0.run_turn(h,ctx,gw)
user=gw.calls[-1]['user']
result['probes']['v0_visible_inputs']={
    'active_intent_in_request':'当前意图哨兵' in user,
    'initial_kb_in_request':'初始事实哨兵' in user,
    'clock_in_request':'2026-12-02 19:58' in user,
    'message_timestamp_in_request':'2026-12-02 19:57' in user,
    'current_source_alias_m0':'m0' in user,
    'prompt':user}
history=v0._budget_history([{'seq':1,'role':'HER','text':'长'*2000,'ts':''}],v0.MAX_EVENT_HISTORY_CU)
result['probes']['v0_single_long_message']={'history_utf16':v0.rc.utf16_len(history),
    'declared_cap':v0.MAX_EVENT_HISTORY_CU}

worksheet=load('tools/build_review_worksheet.py','review_worksheet')
cards=json.loads((ROOT/'benchmark/dev_pilot_v02/03_GRADING_CARDS.json').read_text(encoding='utf-8'))
rows,_=worksheet.worksheet_from_exam(str(ROOT/'logs/exam1_20261023'),
    {c['case_id']:c for c in cards},'reference')
expected={t['case_id'] for e in ['E001','E002','E003'] for t in json.loads((ROOT/'benchmark/episodes'/f'{e}.json').read_text(encoding='utf-8'))['turns']}
actual={r['case_id'] for r in rows}
result['probes']['worksheet_coverage']={'rows':len(rows),'unique_cases':len(actual),
    'missing_cases':sorted(expected-actual),'prefilled':sum(bool(r['semantic_label']) for r in rows)}

importer=load('tools/import_pilot_questions.py','review_importer')
totals={'requests':0}; seen_case=set(); seen_ep=set(); ep_case={}
exp_cards=ROOT/'benchmark/dev_expansion_v01/03_GRADING_CARDS_E004_E006.json'
for eid in ['E004','E005','E006']:
    episode=json.loads((ROOT/'benchmark/episodes'/f'{eid}.json').read_text(encoding='utf-8'))
    importer.check_episode(episode,eid,seen_case,seen_ep,totals)
    ep_case[eid]={t['case_id'] for t in episode['turns']}
importer.check_cards(str(exp_cards),24,seen_case,ep_case)
result['new_data_mechanical']={'episodes':len(seen_ep),'requests':totals['requests'],
    'hard_failures':[r for r in importer.HARD if not r[1]],'warnings':importer.WARN}
result['after']=hashes()
result['watched_source_unchanged']=result['before']==result['after']
result['existing_checks_passed']=sum(r['pass'] for r in result['existing_checks'])
(OUT/'CHECKS.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'existing_checks':[result['existing_checks_passed'],len(result['existing_checks'])],
    'live_fake':result['live_fake'],'probes':result['probes'],
    'new_data_mechanical':result['new_data_mechanical'],
    'source_unchanged':result['watched_source_unchanged'],'real_calls':0},ensure_ascii=False))
