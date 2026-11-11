"""Independent v14 review. Only a disposable extracted copy, mocks and localhost.
Never calls icacls, real model endpoints, native window APIs, or sealed B contents.
Runtime ACL methods are replaced with recording guards; no ACL claim is inferred.
"""
from pathlib import Path
import base64
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
import os
import shutil
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = Path(__file__).resolve().parent
ROOT = BASE / 'package'
EV = BASE / 'evidence'
EV.mkdir(exist_ok=True)
sys.dont_write_bytecode = True
sys.path[:0] = [str(ROOT/'arena'), str(ROOT/'tools')]
RESULT = {}

def dump(p, obj):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding='utf-8')

def probe(name, fn):
    try:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            data = fn()
        RESULT[name] = data
        (EV/(name+'.log')).write_text(out.getvalue(),encoding='utf-8')
    except Exception as e:
        import traceback
        RESULT[name] = {'probe_error':type(e).__name__+': '+str(e),'trace':traceback.format_exc()}
    dump(EV/'INDEPENDENT_PROBES.json', RESULT)
    print(name, json.dumps(RESULT[name],ensure_ascii=False)[:2000], flush=True)

def verifier_probes():
    import verify_arena_v1 as v
    sb=EV/'verifier'; sb.mkdir(exist_ok=True)
    realroot,realtool,realauth=v.ROOT,v.run_tool,v.AUTH_FILE
    v.ROOT=str(sb)
    answer={}
    try:
        for name,extra in [('residual_fail',[{'check':'A6a_direct_socket','verdict':'FAIL'}]),
                           ('unexpected_fail',[{'check':'EXTRA_ISOLATION','verdict':'FAIL'}]),
                           ('duplicate_fail_then_pass',[{'check':'L1_parent_deps','verdict':'FAIL'},{'check':'L1_parent_deps','verdict':'PASS'}])]:
            obj=v.Verify('offline',str(sb/'release.json'),str(sb/name))
            vs=[{'check':c,'verdict':'PASS'} for c in sorted(v.MATRIX_EXPECTED_PASS)]+extra
            p=sb/'logs'/f'{name}.json'
            dump(p,{'verdicts':vs,'ledger_attacker':{'note':'not a verified ledger'},'upstream_ledger':{}})
            os.utime(p,(time.time()+1,time.time()+1))
            v.run_tool=lambda *a,**k:(0,'ISOLATION MATRIX -> logs/'+name+'.json')
            ok=obj.g08_g09_matrix()
            answer[name]={'accepted':bool(ok),'gates':obj.gates}
        v.AUTH_FILE=str(sb/'auth.json')
        dump(Path(v.AUTH_FILE),{'authorized':True})
        v.run_tool=lambda *a,**k:(0,'G11 STANDIN PASS')
        obj=v.Verify('integration',str(sb/'release.json'),str(sb/'integration'))
        rc=obj.run_integration()
        answer['integration_without_real_endpoint']={'return_code':rc,'gates':obj.gates,'acceptance':json.loads((sb/'integration/ACCEPTANCE.json').read_text(encoding='utf-8'))}
    finally:
        v.ROOT,v.run_tool,v.AUTH_FILE=realroot,realtool,realauth
    return answer

def review_material_probe():
    import verify_arena_v1 as v
    import review_loop as rl
    mp=ROOT/'benchmark/releases/arena_v1/review_loop/materials/MAT_01.json'
    raw=mp.read_bytes(); obj=json.loads(raw)
    arm=next(iter(obj['原样输出']))
    obj['原样输出'][arm]+='独立审查合成篡改标记'
    try:
        dump(mp,obj)
        vv=v.Verify('offline',str(ROOT/'refs/arena_v1/release.json'),str(EV/'g07'))
        accepted=vv.g07_review_loop()
        mapping=json.loads((mp.parent.parent/'mapping_secret.json').read_text(encoding='utf-8'))
        why=[]
        rl.verify_material_content(str(mp),mapping['mapping']['MAT_01'],why,'review')
        return {'tampered_display_g07_pass':bool(accepted),'actual_content_validator_reasons':why,'gate':vv.gates}
    finally:
        mp.write_bytes(raw)

def runtime_probe():
    from isolation import trusted_controller as tc
    import lb_scorer
    sb=EV/'rt';sb.mkdir(exist_ok=True)
    marker=sb/'imports.jsonl'
    secret=sb/'controller_canary.json'
    dump(secret,{'CANARY_ONLY':'PRIVATE_CONTROLLER_CANARY_20261022'})
    candidate=ROOT/'candidates/zz_review'
    candidate.mkdir(exist_ok=False)
    shutil.copytree(ROOT/'candidates/reference_product_v1/assets',candidate/'assets')
    dump(candidate/'params.json',{})
    code='''import os,json
with open(MARKER,'a',encoding='utf-8') as f:
 f.write(json.dumps({'pid':os.getpid(),'file':__file__})+'\\n')
def init(assets_root,episode_meta,gateway,sandbox): return {'n':0}
def run_turn(h,ctx,gw):
 h['n']+=1
 private=open(SECRET,encoding='utf-8').read()
 reply=gw.generate('sys','user')['text']
 return {'reply':reply,'notes':{'controller_canary_read':private,'n':h['n']}}
def snapshot(h): return json.dumps(h).encode()
def restore(sandbox,snap): return json.loads(snap)
'''.replace('MARKER',repr(str(marker))).replace('SECRET',repr(str(secret)))
    p=candidate/'mechanism/mechanism.py';p.parent.mkdir();p.write_text(code,encoding='utf-8')
    good={'response':{'recommended':'那周末去河边走走吧','bad_boy':'你猜猜我今天去了哪儿','playful':'咖啡续命小分队集合','warm':'今天辛苦啦早点休息'},'directions':['最近在看什么书',None,None,None],'analysis':{'topic_status':'new','topic_label':'日常','scene_facts':[],'ongoing':[]}}
    ep={'episode_id':'REVIEW_RT','turns':[{'case_id':f't{i}','input':{'clock':f'2026-10-22 10:0{i}','messages':[{'role':'HER','text':'今天有点忙'}]},'mock_response':json.dumps(good,ensure_ascii=False),'gold':{}} for i in (1,2)]}
    epfile=sb/'episode.json';dump(epfile,ep)
    run=tc.IsolatedRun(str(sb/'run'),str(sb/'out'),str(sb/'out/control'),'zz_review',quota=2,run_id='review_runtime')
    # Safety guard: record the exact production deny target set, do not change permissions.
    denied=[]
    run.deny_files=lambda paths: denied.extend(map(str,paths)) or run
    run.undeny_files=lambda: None
    try:
        run.stage().start()
        m,proxy=run.run_episode(str(epfile))
        imports=[json.loads(s) for s in marker.read_text(encoding='utf-8').splitlines()]
        manifests=[]; calls=[]
        for td in sorted((sb/'out').glob('turn_*')):
            manifests.append(json.loads((td/'manifest.json').read_text(encoding='utf-8')))
            calls.append(len(json.loads((td/'calls.json').read_text(encoding='utf-8'))))
        notes=(sb/'out/turn_01_t1/mechanism_notes.txt').read_text(encoding='utf-8')
        before=(sb/'out/checkpoint.json').is_file()
        shutil.copytree(sb/'out',sb/'saved_before_stage')
        run.stage()  # Exact same public staging step used before --resume.
        after=(sb/'out/checkpoint.json').is_file()
        return {'controller_pid':os.getpid(),'host_pid':run.proc.pid,'import_events':imports,'candidate_executed_in_controller':any(x['pid']==os.getpid() for x in imports),'controller_private_canary_read': 'PRIVATE_CONTROLLER_CANARY_20261022' in notes,'production_deny_targets':denied,'canary_is_outside_production_deny_set':str(secret) not in denied,'acl_was_not_executed':True,'turn_call_counts':calls,'per_turn_manifest_gateway_calls':[x['gateway_calls'] for x in manifests],'episode_reported_tokens':m['tokens_total'],'last_turn_cumulative_tokens':manifests[-1]['tokens'],'checkpoint_before_stage':before,'checkpoint_after_stage':after,'episode_manifest':m}
    finally:
        run.close()

def quota_audit_probes():
    from isolation.trusted_controller import TrustedProxy
    import lb_gateway as gw
    sb=EV/'proxy';sb.mkdir(exist_ok=True)
    px=TrustedProxy(str(sb),'batch','candidate','episode',1,adapter_name='mock',run_id='same_run')
    px.generate({'system':'sys','user':'one'})
    before=(sb/'attempt_ledger.jsonl').read_text(encoding='utf-8').splitlines()
    px2=TrustedProxy(str(sb),'batch','candidate','episode',1,adapter_name='mock',run_id='same_run')
    accepted,_=px2.generate({'system':'sys','user':'two'})
    events=[json.loads(s) for s in (sb/'attempt_ledger.jsonl').read_text(encoding='utf-8').splitlines()]
    started=[(x.get('run_id'),x.get('case_id'),x.get('attempt_id')) for x in events if x['kind']=='attempt_started']
    denied=sb/'not_a_file';denied.mkdir(exist_ok=True)
    px3=TrustedProxy(str(sb),'batch3','candidate','episode',1,adapter_name='mock',run_id='logfailure')
    px3._backend.event_log_path=str(denied)
    ok,_=px3.generate({'system':'sys','user':'audit log cannot be opened'})
    return {'persisted_events_before_reopen':len(before),'quota_one_second_proxy_request_accepted':accepted,'same_identity_started':started,'duplicate_attempt_keys':len(started)!=len(set(started)),'log_write_failure_request_accepted':ok,'audit_errors':px3.audit_errors,'log_failure_calls_actually_dispatched':len(px3.backend_calls())}

class LocalHandler(BaseHTTPRequestHandler):
    observed=[]
    def log_message(self,*a): pass
    def do_POST(self):
        body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        type(self).observed.append(body)
        data=json.dumps({'id':'local-only','model':'local-stub','choices':[{'message':{'content':'LOCAL_STUB_ONLY'},'finish_reason':'stop'}],'usage':{'prompt_tokens':1,'completion_tokens':1,'total_tokens':2}}).encode()
        self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)

def media_proxy_probe():
    from isolation.trusted_controller import TrustedProxy
    srv=ThreadingHTTPServer(('127.0.0.1',0),LocalHandler)
    th=threading.Thread(target=srv.serve_forever,daemon=True);th.start()
    old={k:os.environ.get(k) for k in ['LB_API_BASE','LB_API_KEY','LB_MODEL']}
    os.environ.update(LB_API_BASE=f'http://127.0.0.1:{srv.server_port}/v1',LB_API_KEY='LOCAL_ONLY',LB_MODEL='locked_expected')
    canary=EV/'media_secret.txt';canary.write_text('PRIVATE_NOT_REGISTERED_AS_MEDIA',encoding='utf-8')
    try:
        p=TrustedProxy(str(EV/'proxy'),'mediabatch','candidate','case',1,adapter_name='openai_compat',run_id='media_probe')
        ok,body=p.generate({'system':'sys','user':'user','images':[str(canary)],'model':'candidate_override','temperature':1.23})
        request=LocalHandler.observed[-1]
        img=request['messages'][-1]['content'][-1]['image_url']['url']
        return {'ok':ok,'dispatched_model':request['model'],'dispatched_temperature':request['temperature'],'unregistered_local_file_read_by_trusted_proxy':base64.b64decode(img.split(',',1)[1]).decode()=='PRIVATE_NOT_REGISTERED_AS_MEDIA','stub_requests':len(LocalHandler.observed),'local_only':True}
    finally:
        srv.shutdown();srv.server_close()
        for k,v in old.items():
            if v is None:os.environ.pop(k,None)
            else:os.environ[k]=v

def network_test_probe():
    import test_isolation_matrix as mat
    import importlib.util
    spec=importlib.util.spec_from_file_location('attack',ROOT/'candidates/_attack_fixture/mechanism/mechanism.py')
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
    # A no-key server returns successful completions to valid HTTP POST.
    LocalHandler.observed=[]
    srv=ThreadingHTTPServer(('127.0.0.1',0),LocalHandler)
    th=threading.Thread(target=srv.serve_forever,daemon=True);th.start()
    try:
        out=mod.run_turn({}, {'input':{'note':json.dumps({'attacks':['A6c'],'upstream_port':srv.server_port})}},None)
        before=len(LocalHandler.observed)
        import urllib.request
        req=urllib.request.Request(f'http://127.0.0.1:{srv.server_port}/v1/chat/completions',data=json.dumps({'model':'local','messages':[]}).encode(),headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=2) as r:
            status=r.status
        return {'attack_fixture_results':out['notes'],'valid_http_requests_reaching_handler_during_attacks':before,'correct_no_key_HTTP_status':status,'correct_request_reached_unprotected_server':len(LocalHandler.observed)==1,'existing_timeout_verdict':mat.judge([{'check':'probe','result':'BLOCKED:TimeoutExpired'}],'probe',('BLOCKED',))}
    finally:
        srv.shutdown();srv.server_close()

def inventory():
    release=json.loads((ROOT/'refs/arena_v1/release.json').read_text(encoding='utf-8'))
    result={'release_files':len(release['files']),'episodes':[]}
    for p in sorted((ROOT/'benchmark/episodes').glob('*.json'))+sorted((ROOT/'benchmark/episodes/dev').glob('*.json')):
        d=json.loads(p.read_text(encoding='utf-8'))
        turns=d.get('turns',[])
        fingerprints=[json.dumps(x.get('input'),ensure_ascii=False,sort_keys=True) for x in turns]
        result['episodes'].append({'file':p.relative_to(ROOT).as_posix(),'episode_id':d.get('episode_id'),'turns':len(turns),'unique_inputs':len(set(fingerprints))})
    coverage=json.loads((ROOT/'benchmark/releases/arena_v1/coverage_matrix.json').read_text(encoding='utf-8'))
    result['coverage_top_keys']=list(coverage)
    result['archived_logs']=[p.relative_to(ROOT).as_posix() for p in (ROOT/'logs').rglob('*') if p.is_file()]
    result['release_contains_START_PROMPT']='START_PROMPT.md' in release['files']
    result['release_contains_iso_run']='tools/iso_run.py' in release['files']
    return result

if __name__=='__main__':
    os.chdir(ROOT)
    for name,fn in [('verifier',verifier_probes),('material_binding',review_material_probe),('runtime',runtime_probe),('quota_audit',quota_audit_probes),('media_proxy',media_proxy_probe),('network_matrix',network_test_probe),('inventory',inventory)]:
        probe(name,fn)
