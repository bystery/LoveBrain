"""32-primary diagnostic scoring pilot. Existing paid evidence only; zero network.

Ratings below were authored by the current AI reviewer after reading these outputs.
They are non-blind exploratory judgments, not human labels or calibrated gold.
--prepare writes once. Normal runs never regenerate or silently rebind judgments.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
import statistics
from pathlib import Path

ROOT=Path('D:/lovebrain-arena'); OUT=Path(__file__).parent
SOURCE=ROOT/'refs/experiment_protocol_20261023/c4_independent_review_20261026/RAW_REVIEW_ROWS.json'
ANN=OUT/'ANNOTATIONS.json'
ARMS=['reference_product_v1','v5_purpose_aligned','goal_action_v1','evidence_memory_v1']
CASES=['E006_T02','E006_T05','E007_T02','E007_T03','E008_T03','E008_T06','E012_T03','E012_T07']
PLANS={
 'c4_sweep_a_orig_vs_goal_live_20261026':'sweep_a_orig_vs_goal.INTERLEAVE_PLAN.json',
 'c4_sweep_b_v5_vs_mem_live_20261026':'sweep_b_v5_vs_mem.INTERLEAVE_PLAN.json'}
spec=importlib.util.spec_from_file_location('existing_layer',ROOT/'tools/exam_layered_score.py')
layer=importlib.util.module_from_spec(spec); spec.loader.exec_module(layer)

def sha_text(s): return hashlib.sha256(s.encode('utf-8')).hexdigest()
def sha_file(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def load(p): return json.loads(p.read_text(encoding='utf-8'))

# Each row: facts status, goal 0..3, interaction 0..3, evidence-based rationale.
# Axes are independent: an engaging response can still contain an unsupported fact.
RATINGS={
 'E006_T02':[
  ('pass',3,2,'按她明确要求陪她骂、不做分析；三版被退来自前情。没有把新建议强塞进去。'),
  ('na',3,2,'直接尊重别分析的要求，提供倾听；没有必须外部核验的具体事实。短回复不扣分。'),
  ('pass',3,2,'不分析、允许她继续倾诉；改三版有前情，想砸电脑是情绪夸张而非行为事实。'),
  ('na',3,2,'点头陪听是新态度表达；不要求这类轮次必须留钩子。')],
 'E006_T05':[
  ('uncertain',3,3,'正面谈纠结与决定，并把话头交回她；自述持续几天的个人习惯无画像参照，真实性暂不认证。'),
  ('uncertain',3,3,'有具体观点与回应空间；后来给自己定十分钟/睡一觉规则是新增个人习惯叙事，参考不足。'),
  ('uncertain',3,3,'认真给观点并回扣昨天事件；无需因没有反问而扣目标分。自述自己的经历/习惯真实性未给参照。'),
  ('uncertain',3,3,'有可讨论的判断标准并回问具体困境；能拖几周的自述习惯无法在输入中核实。')],
 'E007_T02':[
  ('na',2,3,'接受她偏好并问由谁挑乐队，动作合格；尚未主动承担筛选/给出下一步的具体安排。'),
  ('na',3,3,'提出筛选后发她看、时间由她挑；是新行动，不当作已订票，也不因主动判越界。'),
  ('na',3,3,'主动筛两三个并询问周六/周日，给她选择；新时间提议不需要历史已约证据。'),
  ('na',3,3,'筛选低音量场次并把日期交给她，具体推进且未冒称已有票。')],
 'E007_T03':[
  ('fail',2,3,'把未知那支乐队说成基本没歌词、安静处多等已知特性；仅有曲风与偏好不足以核实具体乐队。'),
  ('fail',3,3,'主动找场动作成立，但吵不到你是对本次体验的无据保证；未来查找不能洗掉前面保证。'),
  ('uncertain',3,3,'有继续查场与挑时间的行动；不吼可能是类指曲风，也可能在确认具体乐队，范围有歧义，事实维不硬判通过。'),
  ('uncertain',3,3,'有继续推进；不算吵究竟指曲风还是这支乐队/现场不清，事实证据不足，保留不确定。')],
 'E008_T03':[
  ('fail',1,2,'确定否认读书会和我说过，输入没有相识场合或完整过去发言可支持；未处理未知来源。'),
  ('fail',1,2,'小林介绍ME与HER认识有据，但不能推出ME与小林不是读书会认识；有据半句不替无据半句背书。'),
  ('fail',1,2,'我可没说过及推给小林缺依据；疑问形式不消除前面的确定否认。'),
  ('fail',1,2,'明确否认认识场合与我说过，缺来源；应容许不确定/澄清而不是自信补过去。')],
 'E008_T06':[
  ('pass',3,2,'确认下周三、对象为她本人；准确且协作，不需要强行加暧昧话。'),
  ('pass',3,3,'正确抓到单独见面并问下班时间，为实际安排留下有用下一步。'),
  ('pass',3,3,'接受单独见面、询问时间；提前订位置是新提议，不是谎称已经订好。'),
  ('pass',3,2,'对象与时间正确，继续问下班时间；不用强调两遍的语气个人偏好留给用户，不计事实错。')],
 'E012_T03':[
  ('pass',3,3,'以她此前仍在纠结晚餐为由提今晚，属于新邀约，不是声称双方已同意。'),
  ('na',3,3,'给周三/周四由她选，订位是未来行动；不因具体日期无过去来源判编造。'),
  ('fail',3,3,'接邀约与给时间动作成立；再去一次额外断言自己去过，题面只说曾提过那家店。'),
  ('pass',3,3,'承接一起吃饭并给两个可选日期，是新提议；未声称已约定或订位完成。')],
 'E012_T07':[
  ('pass',3,3,'把订位交她、自己准时出现，尊重她的能动性且不赌气撒手。'),
  ('pass',3,3,'明确分工并保留周六约定，不抢安排。'),
  ('pass',3,3,'明确不抢订位，周六七点来自本轮ME真实消息，有出处。'),
  ('pass',3,3,'交回订位权、承担准时到场；简短不减分。')],
}

def context(row):
    d=Path(row['dir']); mf=load(d/'manifest.json')
    base=d.parents[2]; ep=d.parents[1].name.split('_',1)[1]
    snapshot=base/d.parents[1].name/(ep+'.copy.json')
    episode=load(snapshot)
    turn=next(t for t in episode['turns'] if t['case_id']==row['case_id'])
    inp_sha=sha_text(json.dumps(turn.get('input') or {},ensure_ascii=False,sort_keys=True))
    if inp_sha!=mf['case_input_sha256']: raise ValueError('input snapshot mismatch '+str(d))
    plan=load(ROOT/'refs/experiment_protocol_20261023/c4_screen_20261026'/PLANS[base.name])
    role=d.parents[1].name.split('_',1)[0]
    ident=plan['frozen']['identity_closure'][role]['identity_sha256']
    return {'run_id':mf['run_id'],'harness_identity_sha256':ident,'case_input_sha256':inp_sha},snapshot,episode,turn

def prepare():
    if ANN.exists(): raise ValueError('ratings already frozen; refusing overwrite')
    rows=load(SOURCE); index={(x['case_id'],x['arm']):x for x in rows}; anns=[]
    for cid in CASES:
        for arm,(fact,goal,inter,note) in zip(ARMS,RATINGS[cid]):
            row=index[cid,arm]; d=Path(row['dir']); raw=(d/'reply_raw.txt').read_text(encoding='utf-8')
            text=json.loads(raw)['response']['recommended']; ctx,snap,ep,turn=context(row)
            if text!=row['primary'] or sha_text(raw)!=row['raw_sha256']: raise ValueError('review source changed')
            label='fail' if fact=='fail' or goal<2 or inter<2 else ('unrated' if fact=='uncertain' else 'pass')
            anns.append({'case_id':cid,'arm':arm,'slot':'recommended','label':label,
                **ctx,'raw_reply_sha256':sha_text(raw),'slot_text_sha256':sha_text(text),
                'snapshot_file':str(snap),'snapshot_sha256':sha_file(snap),'turn_dir':str(d),
                'text':text,'fact_status':fact,'goal_score':goal,'interaction_score':inter,'note':note,
                'evidence':{'persona':ep['persona'],'initial_kb':ep.get('initial_kb'),
                            'current_input':turn['input'],
                            'prior_case_ids':[t['case_id'] for t in ep['turns'][:ep['turns'].index(turn)]]},
                'KB_completeness':None,'user_willingness':None,'backups_reviewed':False})
    doc={'version':'DIAGNOSTIC-PILOT-1','reviewer_kind':'current_assistant_AI_nonblind',
         'not_human_gold':True,'not_calibrated_benchmark':True,'new_model_calls':0,
         'sampling':'8 purposively selected diagnostic positions, 4 arms; not random/representative',
         'scope':'primary only; scene-specific claims, goal fulfillment, interaction; no KB completeness or willingness claim',
         'source_review_file_sha256':sha_file(SOURCE),'annotations':anns}
    ANN.write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding='utf-8')

def verify(doc):
    # Reuse the project's existing text/context binding matcher.
    idx={}
    for a in doc['annotations']: idx.setdefault((a['case_id'],a['slot']),[]).append(a)
    lookup=dict(doc,_index=idx)
    for a in doc['annotations']:
        d=Path(a['turn_dir']); raw=(d/'reply_raw.txt').read_text(encoding='utf-8')
        text=json.loads(raw)['response']['recommended']
        ctx,snap,_,_=context({'dir':str(d),'case_id':a['case_id']})
        if sha_file(snap)!=a['snapshot_sha256']: raise ValueError('snapshot bytes changed')
        hit=layer._match_annotation(lookup,a['case_id'],'recommended',sha_text(raw),sha_text(text),ctx)
        if hit is None: raise ValueError('unbound rating '+a['case_id']+'/'+a['arm'])
        for k in ['goal_score','interaction_score']:
            if type(a[k]) is not int or not 0<=a[k]<=3: raise ValueError('invalid score '+k)
        if a['fact_status'] not in ['pass','fail','uncertain','na']: raise ValueError('invalid fact status')
    return lookup

def selfcheck(doc,lookup):
    a=doc['annotations'][0]; ctx={k:a[k] for k in ['run_id','harness_identity_sha256','case_input_sha256']}
    def match(c,rh=a['raw_reply_sha256']):
        return layer._match_annotation(lookup,a['case_id'],a['slot'],rh,a['slot_text_sha256'],c)
    assert match(ctx) is not None
    assert match(ctx,'0'*64) is None
    assert match(dict(ctx,case_input_sha256=None)) is None
    assert match(dict(ctx,run_id='different-run')) is None
    return {'valid_binding':True,'changed_reply_rejected':True,
            'missing_input_hash_rejected':True,'different_run_rejected':True}

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--prepare',action='store_true'); args=ap.parse_args()
    if args.prepare: prepare()
    doc=load(ANN); idx=verify(doc); checks=selfcheck(doc,idx)
    result={'scope':doc['scope'],'rating_provenance':doc['reviewer_kind'],
            'not_calibrated_benchmark':True,'selected_positions':len(CASES),
            'rated_primaries':len(doc['annotations']),'new_model_calls':0,'binding_checks':checks,'arms':{}}
    md=['# 32条首选诊断试评：不是完整benchmark成绩','',
        '非盲AI试评；8个特选场景×4臂。分数仅属于本试评，不外推版本排名；备选、知识完整度和个人愿发未评。','',
        '| 版本 | 首选数 | 目标均分/3 | 互动均分/3 | 事实无据失败位置 | 事实不确定位置 | 试评任务通过/失败/未知 |',
        '|---|---:|---:|---:|---:|---:|---|']
    for arm in ARMS:
        rows=[a for a in doc['annotations'] if a['arm']==arm]; n=len(rows)
        s={'n':n,'goal_mean':statistics.mean(a['goal_score'] for a in rows),
           'interaction_mean':statistics.mean(a['interaction_score'] for a in rows),
           'facts_fail':sum(a['fact_status']=='fail' for a in rows),
           'facts_uncertain':sum(a['fact_status']=='uncertain' for a in rows),
           'task_pass':sum(a['label']=='pass' for a in rows),'task_fail':sum(a['label']=='fail' for a in rows),
           'task_unknown':sum(a['label']=='unrated' for a in rows),'KB_completeness':None,'user_willingness':None}
        result['arms'][arm]=s
        md.append('| %s | %d | %.3f | %.3f | %d | %d | %d/%d/%d |'%(arm,n,s['goal_mean'],s['interaction_mean'],
            s['facts_fail'],s['facts_uncertain'],s['task_pass'],s['task_fail'],s['task_unknown']))
    for cid in CASES:
        md+=['','## '+cid]
        for a in doc['annotations']:
            if a['case_id']!=cid: continue
            md+=['','**'+a['arm']+'**：'+a['text'],
                 '事实=%s；目标=%d/3；互动=%d/3；任务=%s。%s'%(a['fact_status'],a['goal_score'],a['interaction_score'],a['label'],a['note'])]
    (OUT/'SCORES.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'SCORECARD.md').write_text('\n'.join(md)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
