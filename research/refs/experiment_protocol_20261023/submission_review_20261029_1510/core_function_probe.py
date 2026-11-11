"""Zero-network end-to-end core functionality probe against frozen E3 harness.

Scripted model output is an explicit fixture, not evidence of model chat quality.
The second phase restores in a different Python process.
"""
import argparse,importlib.util,json,os,subprocess,sys
from pathlib import Path
HERE=Path(__file__).parent
ROOT=Path(os.environ.get('LOVEBRAIN_ROOT',r'D:\lovebrain-arena'))
def load():
    p=ROOT/'candidates/action_policy_r4_guard_e3/mechanism/mechanism.py'
    s=importlib.util.spec_from_file_location('independent_core_e3',p);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
class GW:
    def __init__(self): self.inputs=[];self.next={}
    def generate(self,system,user,**kw):
        self.inputs.append(user)
        d={'response':{'recommended':'离线夹具生成的草稿','bad_boy':None,'playful':None,'warm':None},'directions':[None]*4,'analysis':{'facts':[],'tentatives':[],'updates':[]}}
        d['analysis'].update(self.next);self.next={}
        return {'text':json.dumps(d,ensure_ascii=False),'finish':'stop'}
def ctx(n,text,intent=None):
    return {'turn_index':n,'input':{'clock':f'2026-12-01 {10+n//60:02d}:{n%60:02d}','messages':[{'role':'HER','text':text,'ts':f'2026-12-01 {10+n//60:02d}:{n%60:02d}'}],'intent':intent,'may_decline':False}}
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--restore',action='store_true');a=ap.parse_args()
    m=load();gw=GW();state=HERE/'core_state.json';sandbox=HERE/'core_sandbox';sandbox.mkdir(exist_ok=True)
    if a.restore:
        h=m.restore(str(sandbox),state.read_bytes())
        h2=m.restore(str(sandbox),state.read_bytes())
        h2['flags']=dict(h2.get('flags') or {},evidence_memory=False)
        q=ctx(20,'我过敏要忌口的到底是哪样？')
        m.run_turn(h,q,gw);on=gw.inputs[-1]
        m.run_turn(h2,q,gw);off=gw.inputs[-1]
        history=on.split('# 【真实历史消息',1)[-1].split('# 本轮输入',1)[0]
        checks={'restore_new_process':True,'corrected_fact_in_on_prompt':'芝麻' in on,'old_fact_absent_from_raw_history':'花生' not in history and '芝麻' not in history,'target_absent_when_memory_projection_off':'芝麻' not in off,'closed_goal_not_in_current_prompt':'安排周五去逛旧书摊' not in on,'draft_not_committed':all('离线夹具生成的草稿' not in e.get('text','') for e in h['events']),'one_gateway_call_per_turn':len(gw.inputs)==2}
        (HERE/'CORE_FUNCTION_PROMPTS.json').write_text(json.dumps({'on':on,'off':off},ensure_ascii=False,indent=2),encoding='utf-8')
        (HERE/'CORE_FUNCTION_RESTORE.json').write_text(json.dumps({'checks':checks,'passed':all(checks.values()),'facts':h['mem']['facts'],'measurement':'Mechanism/fixture proof only; not real model retrieval quality'},ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(checks,ensure_ascii=False));return 0 if all(checks.values()) else 1
    assets=ROOT/'candidates/action_policy_r4_guard_e3/assets'
    h=m.init(str(assets),{'persona':{},'initial_kb':{}},gw,str(sandbox))
    s='我对花生过敏，外出吃饭不能加花生。'
    gw.next={'facts':[{'text':s,'subject':'HER','relation':'过敏','quote':s,'src_ids':['m0'],'certainty':'confirmed'}]}
    m.run_turn(h,ctx(1,s,{'enabled':True,'status':'ACTIVE','text':'安排周五去逛旧书摊'}),gw)
    fs=list(h['mem']['facts'].values());assert len(fs)==1,fs
    fid=fs[0]['fact_id'];q='我刚确认过，不能吃的是芝麻，不是花生。'
    gw.next={'updates':[{'fact_id':fid,'action':'correct','new_text':'她对芝麻过敏，不能吃芝麻','subject':'HER','quote':q,'src_ids':['m0']}]}
    m.run_turn(h,ctx(2,q,{'enabled':False,'status':'CLOSED','text':'安排周五去逛旧书摊'}),gw)
    # Real-message fixture history is longer than budget; no target repetition.
    for n in range(3,20):m.run_turn(h,ctx(n,f'今天第{n}项整理记录：'+('整理纸箱、核对书目、擦拭书架、给窗台浇水。'*14)),gw)
    state.write_bytes(m.snapshot(h))
    rec={'gateway_calls':len(gw.inputs),'fixture_turns':19,'memory_records':len(h['mem']['facts']),'facts':h['mem']['facts'],'goal_history':h.get('goal_history')}
    (HERE/'CORE_FUNCTION_WRITEBACK.json').write_text(json.dumps(rec,ensure_ascii=False,indent=2),encoding='utf-8')
    assert len(gw.inputs)==19
    p=subprocess.run([sys.executable,'-X','utf8','-B',str(Path(__file__)),'--restore'],capture_output=True,text=True,encoding='utf-8',creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    print(p.stdout);print(p.stderr);return p.returncode
if __name__=='__main__':raise SystemExit(main())
