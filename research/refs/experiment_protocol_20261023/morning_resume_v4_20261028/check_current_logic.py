"""Network-disabled probes; runs the action-policy test with params writes redirected."""
from pathlib import Path
import builtins
import contextlib
import hashlib
import io
import json
import runpy
import socket
import sys
import tempfile

ROOT=Path(r'D:\lovebrain-arena')
OUT=Path(__file__).resolve().parent
sys.path[:0]=[str(ROOT),str(ROOT/'tools'),str(ROOT/'arena')]


def denied(*args,**kwargs):
    raise RuntimeError('Independent audit forbids network')


socket.socket.connect=denied
socket.create_connection=denied
import run_p07_pilot as P
import normalize_reply_contract as N
import build_b3_simulator_request as S


def main():
    result={'new_real_model_calls':0,'network_blocked':True,'probes':{}}
    cases={
        'top_level_no_reply_with_body':{'action':'no_reply','reason':'忙','response':{'recommended':'你好'}},
        'top_level_no_reply_without_body':{'action':'no_reply','reason':'忙','response':{'recommended':None}},
        'analysis_close_with_body':{'analysis':{'action_taken':'收尾'},'response':{'recommended':'那明天聊'}},
    }
    for key,obj in cases.items():
        normalized=N.normalize_raw(json.dumps(obj,ensure_ascii=False))
        result['probes'][key]={'direct':P.classify_reply(obj,True),
                                'after_common_normalizer':P.classify_reply(normalized['canonical'],True)}
    class Mechanism:
        def __init__(self):self.inputs=[]
        def init(self,*a,**kw):return {}
        def run_turn(self,h,ctx,gw):
            self.inputs.append(ctx)
            return {'reply':json.dumps({'response':{'recommended':'这是已发送回复'},'directions':['1','2','3','4']},ensure_ascii=False)}
    class Gateway:
        def __init__(self):self.requests=[]
        def generate(self,system,user,**kw):
            self.requests.append(kw)
            return {'text':json.dumps({'reply':'','state_delta':{},'internal':'等候','end_dialog':False})}
    sc=json.loads((ROOT/'benchmark/interaction_env_20261026/scenarios.json').read_text(encoding='utf-8'))['scenarios'][0]
    mech=Mechanism();gw=Gateway()
    with tempfile.TemporaryDirectory(prefix='lovebrain-independent-wait-') as tmp:
        P.run_trajectory(mech,'independent_fake',str(ROOT/'candidates/v6_integrated/assets'),sc,
                         'positive',1,2,gw,lambda x:None,tmp,None)
    result['probes']['sim_wait_next_turn']={'harness_calls':len(mech.inputs),
        'second_input_roles':[m['role'] for m in mech.inputs[1]['input']['messages']] if len(mech.inputs)>1 else [],
        'finding':'sim wait causes another harness request without a new HER input; valid only if explicitly registered as proactive-clock action'}
    target=ROOT/'candidates/action_policy_r1/params.json'
    before=target.read_bytes();orig_open=builtins.open
    with tempfile.TemporaryDirectory(prefix='lovebrain-independent-action-') as tmp:
        proxy=Path(tmp)/'params.json';proxy.write_bytes(before)
        def safe_open(file,*args,**kwargs):
            try:matched=Path(file).resolve()==target.resolve()
            except (TypeError,ValueError):matched=False
            return orig_open(proxy if matched else file,*args,**kwargs)
        capture=io.StringIO();code=0
        builtins.open=safe_open
        try:
            with contextlib.redirect_stdout(capture),contextlib.redirect_stderr(capture):
                try:runpy.run_path(str(ROOT/'tools/test_action_policy_r1_contract.py'),run_name='__main__')
                except SystemExit as exc:code=exc.code or 0
        finally:builtins.open=orig_open
    result['action_policy_offline']={'exit_code':code,'output':capture.getvalue(),
        'original_params_sha256':hashlib.sha256(before).hexdigest(),
        'original_params_unchanged':target.read_bytes()==before,
        'params_test_writes_redirected_to_temp':True}
    (OUT/'LOGIC_CHECKS.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
