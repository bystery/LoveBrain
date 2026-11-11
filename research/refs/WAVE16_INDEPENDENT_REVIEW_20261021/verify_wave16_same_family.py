"""Offline regression for Wave16's existing fact-authority families.

Usage: python -B -X utf8 verify_wave16_same_family.py PATH_TO_REPOSITORY
Uses local fixed replies only; writes fixtures beside this tool, never candidate sources.
This is a targeted development regression, not an independent held-out benchmark.
"""
import importlib.util,json,pathlib,sys,tempfile
ROOT=pathlib.Path(sys.argv[1]).resolve()
sys.path[:0]=[str(ROOT/'arena'),str(ROOT/'tools')]
s=importlib.util.spec_from_file_location('w16_acceptance_mech',ROOT/'candidates/phase3_facts/mechanism/mechanism.py')
M=importlib.util.module_from_spec(s);s.loader.exec_module(M)
ASSETS=str(ROOT/'candidates/phase3_facts/assets')
OUT=pathlib.Path(tempfile.mkdtemp(prefix='same_family_',dir=pathlib.Path(__file__).resolve().parent))
results=[]
def check(name,passed,detail=None):
    results.append({'test':name,'pass':bool(passed),'detail':detail})
    print(('PASS ' if passed else 'FAIL ')+name,flush=True)
def msg(text,role='HER',ts='2026-10-29 21:30'):return {'role':role,'text':text,'ts':ts}
def fact(text,ids=None):return {'text':text,'source_ids':ids or ['m0']}
def new(name):
    folder=OUT/name;folder.mkdir();return M.init(ASSETS,{},None,str(folder)),folder
class Stub:
    def __init__(self,raw):self.raw=raw;self.user=''
    def generate(self,system,user,**kwargs):
        self.user=user;return {'text':self.raw,'finish':'stop','usage':None}
def turn(h,d,cid,messages,facts=None,ongoing=None):
    raw=json.dumps({'response':{'recommended':'行','bad_boy':'哦','playful':'好','warm':'好的'},
        'directions':['知道了',None,None,None],
        'analysis':{'topic_status':'new','topic_label':'日常','scene_facts':facts or [],'ongoing':ongoing or []}},ensure_ascii=False)
    stub=Stub(raw)
    M.run_turn(h,{'case_id':cid,'input':{'clock':messages[-1]['ts'],'messages':messages},'assets_root':ASSETS},stub)
    p=d/'kv_facts.jsonl'
    fs=[json.loads(ln) for ln in p.read_text(encoding='utf-8').splitlines() if ln.strip()] if p.exists() else []
    evidence={'input':messages,'supplied_facts':facts,'stored':fs,'request_user':stub.user}
    (d/(cid+'.json')).write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
    return fs,stub.user
def active(fs):return [f for f in fs if not f.get('superseded') and f.get('status')!='superseded']
WARN=('未确认','未发生','未核验','未经核验','未经验证','未验证','无法确认','不可信','不得当事实','无证据','待确认','提议')
def qualified_or_absent(user,claim):
    return all(any(w in ln for w in WARN) for ln in user.splitlines() if claim in ln)

h,d=new('valid_clock')
fs,_=turn(h,d,'T1',[msg('到站了',ts='2026-10-29 18:52')],[fact('她18:52说到站了')])
check('真实消息时刻完整通过后不再被拆字误拒',any(f['text']=='她18:52说到站了' for f in fs),fs)
h,d=new('fake_clock')
fs,_=turn(h,d,'T1',[msg('到站了',ts='2026-10-29 20:35')],[fact('她20:29到站了')])
check('日期数字仍不能拼成无出处钟点',not any('20:29' in f['text'] for f in fs),fs)

h,d=new('two_people')
turn(h,d,'S1',[msg('我周五坐G7812','ME'),msg('我也坐G7812')],
     [fact('我周五坐G7812',['m0']),fact('她周五坐G7812',['m1'])])
fs,_=turn(h,d,'S2',[msg('我不是G7812了 我改签成D3145')],[fact('她改签成D3145')])
check('HER改签不抹掉ME仍有效的同车次',any(f['text']=='我周五坐G7812' for f in active(fs)),fs)
check('HER已明确被更正的旧值仍失效',not any(f['text']=='她周五坐G7812' for f in active(fs)),fs)

for label,source,summary in [
    ('summary_already','纪录片找到了 你要看吗','我们已经约定一起看纪录片'),
    ('source_already','纪录片已经找到了 你要看吗','我们约定一起看纪录片')]:
    h,d=new(label)
    turn(h,d,'Q1',[msg(source)],[fact(summary)],
         [{'item':summary,'status':'新出现','state':'待确认'}])
    fs,user=turn(h,d,'Q2',[msg('纪录片的事现在怎么样',ts='2026-10-30 18:00')])
    check('完成词不能替尚未确认的共同活动背书/'+label,
          qualified_or_absent(user,summary),user)

h,d=new('explicit_no')
fs,_=turn(h,d,'N1',[msg('今晚一起看吗'),msg('我没空一起看','ME')],
     [fact('我们约定今晚一起看',['m0','m1'])])
check('明确没空不是共同活动的接受证据',not any(f.get('status')=='confirmed' for f in fs),fs)

h,d=new('true_confirmation')
fs,_=turn(h,d,'P1',[msg('今晚一起看吗'),msg('好 就今晚一起看','ME')],
     [fact('我们已约定今晚一起看',['m0','m1'])])
check('真实接受的正例仍可确认',any(f.get('status')=='confirmed' for f in fs),fs)
h,d=new('true_completed')
fs,_=turn(h,d,'P1',[msg('我们昨晚一起看完那部片了')],[fact('我们昨晚已经一起看完那部片')])
check('共同完成的正例仍可保留',any(f.get('status')=='event' for f in fs),fs)

h,d=new('ongoing_unverified')
turn(h,d,'U1',[msg('到合肥了')],[],[{'item':'她坐G7812到合肥了','status':'新出现','state':'已完成'}])
fs,user=turn(h,d,'U2',[msg('车站出来了',ts='2026-10-30 18:00')])
check('ongoing无出处实体不可作为裸事实注入',qualified_or_absent(user,'她坐G7812到合肥了'),user)

(OUT/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'passed':sum(x['pass'] for x in results),'total':len(results),'artifact_dir':str(OUT)},ensure_ascii=False))
sys.exit(0 if all(x['pass'] for x in results) else 1)
