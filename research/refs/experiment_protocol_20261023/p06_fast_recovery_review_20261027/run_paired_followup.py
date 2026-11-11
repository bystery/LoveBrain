"""Thin evaluator over existing gateway/budget. No harness regeneration.
--init: freeze qwen paired-study plan and register 120 bounded requests.
--limit N: dispatch up to N NEVER-STARTED units; started units are never retried.
Resumption only handles unstarted independent units, not a refill of failed ones.
"""
import argparse
import collections
import concurrent.futures
import hashlib
import json
import os
import sys
import threading
import time
from pathlib import Path

ROOT=Path('D:/lovebrain-arena')
OUT=Path(__file__).resolve().parent/'PAIRED_FOLLOWUP'
sys.path[:0]=[str(ROOT),str(ROOT/'arena')]
from arena.lb_gateway import ModelGateway,ADAPTERS
from arena.isolation.authorization_budget import AuthorizationBudget,validate_authorization_doc

MODEL='qwen3.8-flash'
ENDPOINT='http://127.0.0.1:17770/v1'
AID='AUTH-P06-PAIRED-QWEN-20261027-01'
DIMS=['goal','factuality','attribution','time_state','boundary','memory_use','interaction','initiative','emotion_relation']
SYSTEM='''你是聊天回复评审者，只依给定截至当前的证据独立评每个可发送槽。不要猜版本或成本，不预测用户愿发。
九维顺序严格固定：goal,factuality,attribution,time_state,boundary,memory_use,interaction,initiative,emotion_relation。
每维0错误/反向、1关键欠缺、2合理完成、3具体实质增益，或null不适用/无法可靠评。null在na中用维名给理由。
只把将无根据细节作确定事实/亲历/已有承诺算事实错误。观点、玩笑、疑问、新提议、未来承诺不能因为没历史证据就判编造。问句/提议仍可能目标不合适，应记目标/分寸问题，不偷换事实错误。职业不证明本次亲历。
人物/日期/事件状态依据原始消息及公开事实；不能用私密目标、未发草稿、后来剧情作双方既成事实。语义主体不因引用编号存在就正确。
重大无据确定事实用major或critical，且该槽factuality<=1；不要因在备选槽就减轻事实严重性。别为收尾强塞钩子，也别把自然调侃全部判保守。合理自然不等于必须短。
按每条槽原文判断，不因它是primary就更高分。必须返回输入中每条可用槽一次，side与slot逐字一致；unavailable槽九维全null，禁止编出不存在的内容。
严格JSON，无代码围栏：{"item_id":"原样","slots":[{"side":"原样","slot":"原样","s":[九个0至3整数或null],"na":{"不适用维名":"理由"},"issues":[{"dimension":"九维之一","severity":"minor|major|critical|uncertain","quote":"该槽逐字片段","evidence":"输入逐字证据或not_supplied","reason":"具体解释"}]}]}。
为降低冗余，问题写issues，正常槽issues可为空；不得为了短输出漏槽。输出的是辅助判断，不代表人类评审或已证明候选更好。'''

def read(p):return json.loads(p.read_text(encoding='utf-8'))
def lines(p):return [json.loads(l) for l in p.read_text(encoding='utf-8').splitlines() if l.strip()] if p.exists() else []
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')

def slots_for(answers):
    out=[]
    for side,a in answers.items():
        out.append({'side':side,'slot':'primary','text':a.get('primary'), 'unavailable':not bool(a.get('primary'))})
        out.extend({'side':side,'slot':b['slot'],'text':b['text'],'unavailable':False} for b in a.get('backups',[]))
    return out

def freeze():
    assert not (OUT/'DISPATCH_PLAN.json').exists(),'Already initialized; use --limit to continue NEVER-STARTED units'
    pairs=lines(OUT/'PAIR_NODES.jsonl');controls=read(OUT/'CONTROLS_PUBLIC.json');plan=read(OUT/'PLAN.json')
    largest=max(pairs,key=lambda p:len(json.dumps(p,ensure_ascii=False)))['pair_id']
    jobs=[{'id':controls[0]['node_id'],'kind':'control'}, {'id':largest,'kind':'pair'}]
    jobs += [{'id':c['node_id'],'kind':'control'} for c in controls[1:]]
    jobs += [{'id':p['pair_id'],'kind':'pair'} for p in pairs if p['pair_id']!=largest]
    jobs += [{'id':p+'-SWAP','source':p,'kind':'swap'} for p in plan['swapped_pair_ids']]
    assert len(jobs)==120
    (OUT/'JUDGE_SYSTEM.txt').write_text(SYSTEM,encoding='utf-8')
    frozen={name:sha(OUT/name) for name in ['PAIR_NODES.jsonl','PRIVATE_PAIR_BINDINGS.json','CONTROLS_PUBLIC.json','CONTROLS_PRIVATE.json','PLAN.json','JUDGE_SYSTEM.txt']}
    save(OUT/'DISPATCH_PLAN.json',{'authorization_id':AID,'model':MODEL,'temperature':0.7,'timeout_s':60,
                                 'max_concurrency':3,'planned':120,'frozen':frozen,'jobs':jobs,
                                 'failure_policy':'No retry. Stop submitting on two consecutive empty/transport failures; pending unstarted units may resume under this unchanged plan. Auditing/identity/budget failure stops globally.'})
    scope={'scope_id':AID,'purpose':'96 complete paired existing-answer units +12 development controls+12 swaps; not Gemini refill and no candidate regeneration.',
           'model':MODEL,'api_base':ENDPOINT,'dispatch_plan_sha256':sha(OUT/'DISPATCH_PLAN.json'),
           'automatic_candidate_generations':False,'forbidden_models':['hy4-preview'],'max_concurrency':3}
    ledger=ROOT/'logs/auth_budget'/(AID+'.jsonl')
    assert not ledger.exists(),'Existing grant must not be reset'
    auth={'authorization_id':AID,'authorized':True,'status':'ACTIVE','authorization_basis':'Current user explicitly requests continuing mainline using fast dsf/qwen3.8-flash/hy3 and forbids hy4-preview; standing real-call authorization.',
          'real_endpoint':{'api_base':ENDPOINT,'model':MODEL,'temperature':0.7,'max_attempts':120},'budget_ledger':str(ledger),'scope':scope}
    validate_authorization_doc(auth,expected_endpoint=ENDPOINT,expected_model=MODEL,expected_temperature=0.7)
    save(OUT/'AUTHORIZATION.json',auth)
    b=AuthorizationBudget(str(ledger),AID,120,scope=scope);b.ensure_batch(120,purpose='new_complete_paired_study')
    print('Registered paired study; used=0; no generation dispatched',flush=True)

def validate(parsed,expected,item_id):
    assert isinstance(parsed,dict) and parsed.get('item_id')==item_id,'item binding'
    got=parsed.get('slots');assert isinstance(got,list),'slots not list'
    assert collections.Counter((r.get('side'),r.get('slot')) for r in got)==collections.Counter((r['side'],r['slot']) for r in expected),'slot coverage'
    for r in got:
        assert isinstance(r.get('s'),list) and len(r['s'])==9,'score array'
        assert all(x is None or type(x) is int and 0<=x<=3 for x in r['s']),'bad score type'
        assert isinstance(r.get('issues'),list) and isinstance(r.get('na'),dict),'issue/NA types'
        assert all(d in r['na'] for d,x in zip(DIMS,r['s']) if x is None),'NA reason missing'

def run(limit,workers):
    assert 1<=workers<=3
    plan=read(OUT/'DISPATCH_PLAN.json');auth=read(OUT/'AUTHORIZATION.json')
    validate_authorization_doc(auth,expected_endpoint=ENDPOINT,expected_model=MODEL,expected_temperature=0.7)
    assert sha(OUT/'DISPATCH_PLAN.json')==auth['scope']['dispatch_plan_sha256']
    assert all(sha(OUT/n)==h for n,h in plan['frozen'].items()),'frozen inputs drift'
    system=(OUT/'JUDGE_SYSTEM.txt').read_text(encoding='utf-8')
    b=AuthorizationBudget(auth['budget_ledger'],AID,120,scope=auth['scope'])
    consumed={x['attempt_id'] for x in lines(Path(auth['budget_ledger'])) if x.get('kind')=='attempt_started'}
    pending=[j for j in plan['jobs'] if AID+':'+j['id'] not in consumed][:limit]
    b.ensure_batch(len(pending),purpose='unstarted independent paired units only')
    pairs={p['pair_id']:p for p in lines(OUT/'PAIR_NODES.jsonl')};controls={c['node_id']:c for c in read(OUT/'CONTROLS_PUBLIC.json')}
    os.environ['LB_API_BASE']=ENDPOINT;os.environ['LB_STREAM_TIMING']='0';os.environ['LB_STREAM_USAGE']='0'
    resultdir=OUT/'RATINGS';resultdir.mkdir(exist_ok=True);lock=threading.Lock()
    def one(job):
        if job['kind']=='control':
            c=controls[job['id']];expected=slots_for({'single':{'primary':c['answer'],'backups':[]}})
            payload={'item_id':job['id'],'evidence':c['evidence'],'slots':expected}
        else:
            p=pairs[job.get('source',job['id'])];ans=p['answers']
            if job['kind']=='swap':ans={'left':ans['right'],'right':ans['left']}
            expected=slots_for(ans);payload={'item_id':job['id'],'evidence':p['evidence'],'reference_card':p['reference_card'],'slots':expected}
        at=AID+':'+job['id'];path=resultdir/(job['id']+'.json');assert not path.exists(),'evidence overwrite'
        with lock:b.reserve(at,purpose=job['kind'])
        gw=ModelGateway(ADAPTERS['openai_compat'](),event_log_path=str(resultdir/(job['id']+'.EVENTS.jsonl')));gw.set_run_context(AID,job['id'])
        user=json.dumps(payload,ensure_ascii=False);start=time.monotonic()
        r={'item_id':job['id'],'kind':job['kind'],'attempt_id':at,'model':MODEL,'input_sha256':hashlib.sha256(user.encode()).hexdigest()}
        try:
            res=gw.generate(system,user,model=MODEL,temperature=0.7,timeout_s=60,tag='paired_quality',attempt_id=at)
            raw=res['text'];r.update(transport_ok=True,nonempty=bool(raw.strip()),raw=raw,usage=res.get('usage'))
            try:
                parsed=json.loads(raw);validate(parsed,expected,job['id']);r.update(schema_ok=True,parsed=parsed)
            except Exception as exc:r.update(schema_ok=False,schema_error=str(exc))
            with lock:b.record('attempt_completed',at,nonempty=r['nonempty'],schema_ok=r['schema_ok'],usage=r.get('usage'))
        except Exception as exc:
            r.update(transport_ok=False,nonempty=False,schema_ok=False,error=type(exc).__name__+': '+str(exc)[:200])
            with lock:b.record('attempt_failed',at,error=r['error'])
        r['wall_s']=round(time.monotonic()-start,3);save(path,r)
        print(json.dumps({k:r.get(k) for k in ['item_id','kind','transport_ok','schema_ok','wall_s','schema_error','error']},ensure_ascii=False),flush=True)
        return r
    # Fixed waves keep the model-specific stop bounded; no automatic retries.
    results=[];streak=0
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as ex:
        for i in range(0,len(pending),workers):
            wave=[ex.submit(one,j) for j in pending[i:i+workers]]
            for f in concurrent.futures.as_completed(wave):
                r=f.result();results.append(r);streak=streak+1 if not r.get('nonempty') else 0
            if streak>=2:print('CIRCUIT_OPEN: no new submissions; preserve pending and do other mainline tasks',flush=True);break
    summary={'submitted_this_invocation':len(results),'assessed_this_invocation':sum(x.get('schema_ok',False) for x in results),'budget':b.summary()}
    save(OUT/'LATEST_INVOCATION.json',summary)
    now={x['attempt_id'] for x in lines(Path(auth['budget_ledger'])) if x.get('kind')=='attempt_started'}
    if len(now)==120:b.close(reason='All planned independent paired units consumed; no retry/refill')
    print(json.dumps(summary,ensure_ascii=False),flush=True)

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('--init',action='store_true');ap.add_argument('--limit',type=int,default=120);ap.add_argument('--workers',type=int,default=3);a=ap.parse_args()
    if a.init:freeze()
    else:run(a.limit,a.workers)
