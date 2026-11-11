"""Read-only source/evidence audit and controlled zero-network driver probes.
Never dispatches a model, edits a candidate, rewrites old ledgers or scores.
"""
import collections
import hashlib
import importlib.util
import json
import sys
import tempfile
from pathlib import Path

ROOT=Path('D:/lovebrain-arena')
OUT=Path(__file__).resolve().parent
sys.path[:0]=[str(ROOT),str(ROOT/'arena'),str(ROOT/'tools')]

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def lines(p):return [json.loads(l) for l in p.read_text(encoding='utf-8').splitlines() if l.strip()]
def load(name,path):
    s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
def save(name,d):(OUT/name).write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')

def main():
    recovery=load('review_recovery',ROOT/'tools/run_p06_recovery_review.py')
    pilot=load('review_pilot',ROOT/'tools/run_p07_pilot.py')
    rc=load('review_ref_chain',ROOT/'candidates/reference_product_v1/reference_chain.py')
    normalizer=load('review_normalizer',ROOT/'tools/normalize_reply_contract.py')
    budget=lines(ROOT/'logs/auth_budget/AUTH-P06-RECOVERY-20261027-01.jsonl')
    events=lines(ROOT/'logs/p06_recovery_review_20261027/REVIEW_GATEWAY.jsonl')
    bs=[e for e in budget if e.get('kind')=='attempt_started']
    gs=[e for e in events if e.get('kind')=='attempt_started']
    b_ids={e['attempt_id'] for e in bs}
    freq=collections.Counter(e['attempt_id'] for e in gs)
    account={'budget_reservations':len(bs),'gateway_started':len(gs),
             'unreserved_extra_gateway_starts':len(gs)-len(bs),
             'reused_attempt_ids':{k:v for k,v in freq.items() if v>1},
             'gateway_ids_missing_from_budget':sorted(set(freq)-b_ids),
             'budget_terminal_counts':dict(collections.Counter(e.get('kind') for e in budget)),
             'gateway_terminal_counts':dict(collections.Counter(e.get('kind') for e in events))}
    unit=read_recovery_unit()
    fake={'item_id':unit['item_id'],'left':[2]*len(unit['dimensions']),
          'right':[2]*len(unit['dimensions']),'issues':[],'na':[]}
    probes={}
    for label,change in [('bad_suffix',{'item_id':unit['item_id'].split('-')[0]+'-WRONG'}),
                         ('missing_id',{'item_id':None}),('bool_scores',{'left':[True]*len(unit['dimensions'])})]:
        v=dict(fake);v.update(change)
        if label=='missing_id':del v['item_id']
        probes[label]=recovery.validate(v,unit)
    goal={'goal':'约她一起看展'}
    probes['current_driver_goal_field_effective']=rc.is_intent_effective(goal)
    probes['canonical_goal_field_effective']=rc.is_intent_effective({'text':'约她一起看展','status':'ACTIVE','enabled':True})
    probes['empty_output_action']=pilot.classify_reply({})
    probes['reply_no_reply_conflict']=pilot.classify_reply({'action':'no_reply','response':{'recommended':'这是正文'},'reason':'不回'})
    class FakeMechanism:
        def __init__(self):self.contexts=[]
        def init(self,*args):return {}
        def run_turn(self,h,ctx,gw):
            self.contexts.append(json.loads(json.dumps(ctx)))
            return {'reply':json.dumps({'response':{'recommended':'我刚刚确实发了这句话'},'directions':[],'analysis':{}},ensure_ascii=False)}
    class FakeGateway:
        def generate(self,*args,**kwargs):return {'text':json.dumps({'reply':'那我知道啦','state_delta':{},'internal':'测试替身','end_dialog':False},ensure_ascii=False)}
    scs=read(ROOT/'benchmark/interaction_env_20261026/scenarios.json')['scenarios'];sc=next(s for s in scs if s['id']=='I2')
    mech=FakeMechanism()
    with tempfile.TemporaryDirectory(prefix='p07_zero_network_') as temp:
        tr=pilot.run_turn_loop(mech,'probe','unused','I2',sc,2,FakeGateway(),lambda e:None,temp)
    probes['second_turn_messages']=mech.contexts[1]['input']['messages']
    probes['actual_sent_ME_replayed_next_turn']=any(m.get('role')=='ME' and m.get('text')=='我刚刚确实发了这句话' for m in probes['second_turn_messages'])
    clean={'response':{'recommended':'好呀','bad_boy':'选项A','bad_boys':'选项B','playful':None,'warm':None},'directions':['一','二','三','四','第五条']}
    norm=normalizer.normalize_raw(json.dumps(clean,ensure_ascii=False))
    probes['consumer_alias_collision']={'before_bad_boy':'选项A','before_bad_boys':'选项B','after_response':norm['canonical']['response'],'repairs':norm['repairs']}
    traces=[]
    for p in sorted((ROOT/'logs/p07_pilot2_20261027').glob('TRACE*.json')):
        d=read(p);trace=d['trace']
        traces.append({'file':str(p),'scenario':d['scenario'],'arm':d['arm'],
                       'turns':len(trace),'sent':sum(t['candidate_kind']=='send' for t in trace),
                       'delivery_failed':sum(t['candidate_kind']=='delivery_failed' for t in trace),
                       'missing_reply_as_ME_text':sum(h.get('who')=='me' and h.get('text')=='（未获回复）' for h in d['final_history']),
                       'primary_texts':[t['sent'] for t in trace]})
    qualify=read(ROOT/'logs/p06_recovery_review_20261027/REVIEW_QUALIFY.json')
    small=lines(ROOT/'logs/p06_recovery_review_20261027/REVIEW_RESULTS.jsonl')
    result={'kind':'independent_max288_v3_audit','model_calls':0,
            'recovery_accounting':account,'recovery_latest_qualification':qualify,
            'recovery_small_batch':{'logical_units':len(small),'schema_ok':sum(bool(x.get('ok')) for x in small),
                                    'longest_logical_unit_ms':max(x.get('ms',0) for x in small)},
            'controlled_probes':probes,'p07_traces':traces,
            'limits':'Controlled probes demonstrate input/consumer/accounting bugs, NOT candidate quality or real human outcomes.'}
    save('INDEPENDENT_REVIEW.json',result)
    print(json.dumps({'model_calls':0,'recovery_budget_reservations':len(bs),'recovery_gateway_started':len(gs),
                      'unreserved_extra_gateway_starts':len(gs)-len(bs),
                      'p07_traces':len(traces),'p07_harness_turns':sum(x['turns'] for x in traces),
                      'goal_field_effective':probes['current_driver_goal_field_effective'],
                      'actual_sent_ME_replayed_next_turn':probes['actual_sent_ME_replayed_next_turn'],
                      'empty_output_classified_as':probes['empty_output_action'],
                      'recovery_small_batch':result['recovery_small_batch']},ensure_ascii=False,indent=2))

def read_recovery_unit():
    p=ROOT/'refs/experiment_protocol_20261023/p06_fast_recovery_review_20261027/RECOVERY_UNITS.jsonl'
    return json.loads(p.read_text(encoding='utf-8').splitlines()[0])

if __name__=='__main__':main()
