"""Nine bounded diagnostics, NOT a replacement benchmark or judge calibration.
Three models x tiny JSON / protected-expression control / representative input.
No Gemini, no hy4; no retries. Re-running a used/closed diagnostic is refused.
"""
import concurrent.futures
import hashlib
import json
import os
import sys
import threading
import time
from pathlib import Path

ROOT = Path('D:/lovebrain-arena')
OUT = Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT))
sys.path.insert(0,str(ROOT/'arena'))
from arena.lb_gateway import ModelGateway, ADAPTERS
from arena.isolation.authorization_budget import AuthorizationBudget, validate_authorization_doc

MODELS = ('dsf','hy3','qwen3.8-flash')
ENDPOINT = 'http://127.0.0.1:17770/v1'
PK = ROOT/'refs/experiment_protocol_20261023/p06_judge_gem_20261027'


def save(p,d):
    p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')


def main():
    low = '--low-concurrency-control' in sys.argv
    count = 1 if low else 3
    out = OUT/('MODEL_SCREEN_LOW' if low else 'MODEL_SCREEN')
    out.mkdir(exist_ok=False)  # historical evidence is never overwritten
    nodes = [json.loads(l) for l in (PK/'P06_NODES.jsonl').read_text(encoding='utf-8').splitlines() if l.strip()]
    largest = max(nodes,key=lambda x:len(json.dumps(x,ensure_ascii=False)))
    body = {'evidence':largest['evidence'],'reference_card':largest['reference_card'],'answer':largest['answer']}
    payload = json.dumps(body,ensure_ascii=False)
    jobs = [
      ('tiny_json','只输出一个JSON对象，不要解释：{"ok":true,"value":7}。','检查JSON输出。'),
      ('fact_boundary','只依据给定原文判断，不许把提议判成已发生。输出JSON：{"past_visit_supported":true或false,"new_proposal_allowed":true或false}。',
       '证据：她说“你上次提过那家汤锅，我一直想吃”。无其他ME经历。判断A“我上次去过那家店”的亲历是否有证据；判断B“我们找天一起去试试”作为新提议是否允许。'),
      ('representative_input','依据材料检查首选中是否存在无据的确定事实；观点、玩笑、新提议不是既成事实。只输出简短JSON，键verdict、quote、reason；verdict只能supported/unsupported/uncertain/na。不要给用户愿发票，不要输出整份九维判词。',payload)
    ]
    if low:jobs=[j for j in jobs if j[0]=='representative_input']
    budgets = {}
    for model in MODELS:
        aid = 'AUTH-P06-FASTSCREEN-'+('LOW-' if low else '')+model.replace('.','_').upper()+'-20261027-01'
        scope = {'scope_id':aid,'purpose':('Low-load control: 1 representative-input diagnostic; new concurrency condition, not retry/score replacement.' if low else 'Exactly 3 diagnostic requests, not P06 ratings; no retry, no candidate generation.'),
                 'model':model,'endpoint':ENDPOINT,'per_model_max_concurrency':count,
                 'forbidden_models':['hy4-preview'],'transport_timeout_s':35}
        ledger = ROOT/'logs/auth_budget'/(aid+'.jsonl')
        assert not ledger.exists(), 'existing diagnostic grant must not be reused'
        auth = {'authorization_id':aid,'authorized':True,'status':'ACTIVE',
                'authorization_basis':'User explicitly requests fast dsf/hy3/qwen3.8-flash, three concurrent each; hy4-preview forbidden; standing permission for real experiments.',
                'real_endpoint':{'api_base':ENDPOINT,'model':model,'temperature':0.7,'max_attempts':count},
                'scope':scope,'budget_ledger':str(ledger)}
        validate_authorization_doc(auth,expected_endpoint=ENDPOINT,expected_model=model,expected_temperature=0.7)
        save(out/(model+'.AUTHORIZATION.json'),auth)
        budgets[model]=AuthorizationBudget(str(ledger),aid,count,scope=scope)
        budgets[model].ensure_batch(count,purpose='fast_model_input_diagnostics')
    os.environ['LB_API_BASE']=ENDPOINT
    os.environ['LB_STREAM_TIMING']='0'
    os.environ['LB_STREAM_USAGE']='0'
    io_lock=threading.Lock()
    def run(model,kind,system,user):
        aid=budgets[model].authorization_id
        at=aid+':'+kind
        with io_lock:budgets[model].reserve(at,purpose='model_readiness_not_quality_rating')
        g=ModelGateway(ADAPTERS['openai_compat'](),event_log_path=str(out/(model+'_'+kind+'.EVENTS.jsonl')))
        g.set_run_context(aid,kind)
        start=time.monotonic()
        rec={'model':model,'kind':kind,'attempt_id':at,'input_chars':len(system)+len(user),
             'input_sha256':hashlib.sha256((system+'\n'+user).encode()).hexdigest()}
        try:
            result=g.generate(system,user,model=model,temperature=0.7,timeout_s=35,tag='diagnostic',attempt_id=at)
            raw=result['text']
            rec.update(transport_ok=True,raw=raw,usage=result.get('usage'),nonempty=bool(raw.strip()))
            try:
                p=json.loads(raw);rec['parsed']=p;rec['json_ok']=isinstance(p,dict)
            except Exception as exc:rec.update(json_ok=False,parse_error=str(exc)[:180])
            if kind=='tiny_json':rec['control_ok']=rec.get('parsed')=={'ok':True,'value':7}
            if kind=='fact_boundary':rec['control_ok']=rec.get('parsed')=={'past_visit_supported':False,'new_proposal_allowed':True}
            with io_lock:budgets[model].record('attempt_completed',at,nonempty=rec['nonempty'],usage=rec['usage'])
        except Exception as exc:
            rec.update(transport_ok=False,json_ok=False,error=type(exc).__name__+': '+str(exc)[:250])
            with io_lock:budgets[model].record('attempt_failed',at,error=rec['error'])
        rec['wall_s']=round(time.monotonic()-start,3)
        save(out/(model+'_'+kind+'.json'),rec)
        print(json.dumps({k:rec.get(k) for k in ['model','kind','transport_ok','nonempty','json_ok','control_ok','wall_s','error']},ensure_ascii=False),flush=True)
        return rec
    results=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3*count) as ex:
        fs=[ex.submit(run,m,*j) for m in MODELS for j in jobs]
        for f in concurrent.futures.as_completed(fs):results.append(f.result())
    summary={'purpose':'Routing/input-load diagnostic only, NOT full-review latency or judge qualification.',
             'representative_input_chars':len(payload),'max_per_model_concurrency':count,'total_concurrency_cap':3*count,
             'gemini_dispatched':0,'hy4_preview_dispatched':0,'rows':results,'budgets':{}}
    for m,b in budgets.items():
        b.close(reason='All planned diagnostics terminal; no retries or refill')
        summary['budgets'][m]=b.summary()
    save(out/'SUMMARY.json',summary)


if __name__=='__main__':main()
