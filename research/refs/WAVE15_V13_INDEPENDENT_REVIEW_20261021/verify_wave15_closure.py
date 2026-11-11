"""Independent offline acceptance matrix. No model/server calls.

Usage: python -B -X utf8 verify_wave15_closure.py PATH_TO_REPOSITORY
Writes small fixture logs beside this tool; never changes candidate source files.
"""
import importlib.util,json,pathlib,sys,tempfile

ROOT=pathlib.Path(sys.argv[1]).resolve()
sys.path[:0]=[str(ROOT/'arena'),str(ROOT/'tools')]
import lb_assembly

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module

M=load('review_closure_phase3',ROOT/'candidates/phase3_facts/mechanism/mechanism.py')
ASSETS=str(ROOT/'candidates/phase3_facts/assets')
OUT=pathlib.Path(tempfile.mkdtemp(prefix='closure_',dir=pathlib.Path(__file__).resolve().parent))
results=[]

def check(name,ok,detail=None):
    results.append({'test':name,'pass':bool(ok),'detail':detail})
    print(('PASS ' if ok else 'FAIL ')+name,flush=True)

class Stub:
    def __init__(self,raw):self.raw=raw;self.user=''
    def generate(self,system,user,**kwargs):
        self.user=user
        return {'text':self.raw,'finish':'stop','usage':None}

def msg(text,role='HER',ts='2026-10-29 21:30'):
    return {'role':role,'text':text,'ts':ts}

def fact(text,ids=None):return {'text':text,'source_ids':ids or ['m0']}

def new(name):
    folder=OUT/name;folder.mkdir()
    return M.init(ASSETS,{},None,str(folder)),folder

def turn(h,folder,cid,messages,facts,ongoing=None):
    raw=json.dumps({'response':{'recommended':'行','bad_boy':'哦','playful':'好','warm':'好的'},
        'directions':['知道了',None,None,None],
        'analysis':{'topic_status':'new','topic_label':'行程','scene_facts':facts,'ongoing':ongoing or []}},ensure_ascii=False)
    g=Stub(raw)
    M.run_turn(h,{'case_id':cid,'input':{'clock':messages[-1]['ts'],'messages':messages},'assets_root':ASSETS},g)
    path=folder/'kv_facts.jsonl'
    fs=[json.loads(line) for line in path.read_text(encoding='utf-8').splitlines() if line.strip()] if path.exists() else []
    (folder/(cid+'_probe.json')).write_text(json.dumps({'messages':messages,'facts':facts,'stored':fs,'request_user':g.user},ensure_ascii=False,indent=2),encoding='utf-8')
    return fs,g.user

def active(fs):return [f for f in fs if not f.get('superseded') and f.get('status')!='superseded']

# Current positive repair must remain intact.
h,d=new('original_failure')
turn(h,d,'O1',[msg('我坐G7812',ts='2026-10-29 20:30')],[fact('她坐G7812')])
turn(h,d,'O2',[msg('不是这趟 我改签了'),msg('现在坐D3145')],[fact('她现在坐D3145',['m0','m1'])])
fs,_=turn(h,d,'O3',[msg('正点 到合肥了')],[fact('她坐G7812到合肥了')])
check('原始错误到站事实继续被拒',not any('到合肥' in f['text'] and 'G7812' in f['text'] for f in fs))

h,d=new('source_roles')
fs,_=turn(h,d,'SRC',[msg('我坐E708','IDEA'),msg('我现在坐D3145')],[fact('我坐E708'),fact('她现在坐D3145')])
check('IDEA不能冒充m0真实来源',not any('E708' in f['text'] for f in fs),fs)
check('IDEA不占编号且HER正常入库',any('D3145' in f['text'] for f in fs),fs)

for name,newfacts in [
    ('negative_last',[fact('她改签成D3145'),fact('她让我帮看D3145'),fact('她不坐G7812了')]),
    ('positive_last',[fact('她不坐G7812了'),fact('她改签成D3145'),fact('她让我帮看D3145')]),
]:
    h,d=new(name)
    turn(h,d,'C1',[msg('我买的是G7812',ts='2026-10-29 20:30')],[fact('她买的是G7812')])
    fs,_=turn(h,d,'C2',[msg('不是G7812了 我改签成D3145 你帮我看这趟')],newfacts)
    a=active(fs)
    check('同轮纠正不作废新值/'+name,any('D3145' in f['text'] and '不坐D3145' not in f['text'] for f in a),a)

h,d=new('unrelated_history')
turn(h,d,'H1',[msg('我去年坐过D3145',ts='2026-10-28 21:30')],[fact('她去年坐过D3145')])
fs,_=turn(h,d,'H2',[msg('这次不是G7812了 我改签成D3145')],[fact('她这次改签成D3145')])
check('不同事件历史不因共享编号作废',any(f['text']=='她去年坐过D3145' for f in active(fs)),fs)

h,d=new('correction_even_when_summary_rejected')
turn(h,d,'R1',[msg('周五从湖州上车G7812来合肥',ts='2026-10-29 20:30')],[fact('她从湖州坐G7812来合肥')])
turn(h,d,'R2',[msg('刚查了G7812 基本正点','ME'),msg('不是这趟 我改签了'),msg('现在坐D3145')],
     [fact('她改签了 不是G7812',['m1']),fact('她现在坐D3145',['m2'])])
fs,u=turn(h,d,'R3',[msg('我从湖州坐车到合肥了',ts='2026-10-30 18:52')],[])
old_in_prompt='她从湖州坐G7812来合肥' in u
check('真实更正不随摘要拒绝而丢失',not old_in_prompt or any(word in u for word in ['更正','已失效','历史值','superseded']),u)

h,d=new('ongoing_unconfirmed')
turn(h,d,'Q1',[msg('纪录片找到了 你要看吗')],[fact('她问我要不要看纪录片')],
     ongoing=[{'item':'10-29约定一起看纪录片','status':'新出现','state':'她问我要不要看'}])
fs,u=turn(h,d,'Q2',[msg('今天上班真累')],[])
bad_lines=[line for line in u.splitlines() if '约定一起看纪录片' in line and not any(w in line for w in ['未确认','提议','待确认'])]
check('ongoing不绕过邀请未确认约束',not bad_lines,bad_lines)

for name,messages,text,expected in [
    ('offered',[msg('纪录片找到了 你要看吗')],'我们已约定一起看纪录片','offer'),
    ('confirmed',[msg('今晚一起看吗'),msg('好 就今晚一起看','ME')],'我们已约定今晚一起看','confirmed'),
    ('happened',[msg('我们昨晚一起看完那部片了')],'我们昨晚已经一起看完那部片','event'),
]:
    h,d=new(name);fs,_=turn(h,d,'S',[*messages],[fact(text,[f'm{i}' for i in range(len(messages))])])
    if not fs:
        check('状态区分/'+name,False,'合法证据被全部丢弃')
        continue
    status=fs[0].get('status');rendered=M._render_scene_chain(list(enumerate(fs)),False)
    if expected=='offer':ok=status=='offer' and '提议未确认' in rendered
    elif expected=='confirmed':ok=status!='offer' and '提议未确认' not in rendered
    else:ok=status!='offer' and '提议未确认' not in rendered and '意向未发生' not in rendered
    check('状态区分/'+name,ok,{'status':status,'rendered':rendered})

h,d=new('clock_parts')
fs,_=turn(h,d,'T',[msg('正点 到合肥了',ts='2026-10-29 20:35')],[fact('她20:29到合肥')])
check('日期组件不能拼出无出处钟点',not any('20:29' in f['text'] for f in fs),fs)
h,d=new('number_positive')
fs,_=turn(h,d,'N',[msg('我带了两盒',ts='2026-10-30 18:55')],[fact('她带了2盒')])
check('等价两盒到2盒不丢正确事实',bool(fs),fs)

scenes=[
 ('原坏消息',[msg('唉 我领导说项目黄了'),msg('可能要调岗'),msg('烦死了')],'认真沟通'),
 ('原跨消息收尾',[msg('先不聊了'),msg('我去赶班车 明天说')],'收尾'),
 ('领导又来催不是与ME争执',[msg('领导又来催项目了 真烦')],'not_quarrel'),
 ('礼貌问句可以收尾',[msg('我先睡了 明天聊好吗？')],'收尾'),
 ('晚安后重开话题',[msg('晚安'),msg('对了 周末去哪','ME'),msg('去良渚')],'not_closure'),
 ('收尾后HER自己补新事',[msg('先不聊了'),msg('等等 刚才出结果了 我升职了')],'not_closure'),
]
for name,messages,want in scenes:
    got=lb_assembly.infer_scene(messages)
    ok=got!='争执' if want=='not_quarrel' else (got!='收尾' if want=='not_closure' else got==want)
    check('场景/'+name,ok,got)

P=load('review_closure_pc',ROOT/'tools/l3b_parent_child_executor.py')
dep='candidates/phase1_retr_idf/mechanism/mechanism.py'
check('实际检索依赖纳入冻结和快照',dep in P.T.FROZEN_SHA and dep in P.T.FREEZE_FILES)

(OUT/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'passed':sum(r['pass'] for r in results),'total':len(results),'artifact_dir':str(OUT)},ensure_ascii=False))
raise SystemExit(0 if all(r['pass'] for r in results) else 1)
