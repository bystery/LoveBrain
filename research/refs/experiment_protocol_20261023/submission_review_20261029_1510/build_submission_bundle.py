"""Build a small runnable evidence snapshot; never modify original artifacts."""
import hashlib,json,os,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(r'D:\lovebrain-arena');HERE=Path(__file__).parent
BASE=ROOT/'refs/experiment_protocol_20261023/final_mainline_review_20261029'
DEST=HERE/'LoveBrain_research_prototype_20261029'
if DEST.exists():raise SystemExit('Refuse overwrite existing snapshot')
DEST.mkdir()
def cp(src,rel):
    p=DEST/rel;p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,p)
def j(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
fr=j(BASE/'FINAL_CANDIDATE_FREEZE_20261029.json');e3=j(BASE/'F5_CANDIDATE_FREEZE_20261029.json')
files=set(e3['files'])|set(fr['candidates']['action_policy_r4_guard_final']['files'])
for rel in sorted(files):cp(ROOT/rel,rel)
for p in (ROOT/'arena').rglob('*.py'):
    if '__pycache__' not in str(p):cp(p,p.relative_to(ROOT))
for name in ['iso_run.py','api_trial.py','freeze_identity.py','check_identity_drift.py']:
    cp(ROOT/'tools'/name,'tools/'+name)
cp(ROOT/'benchmark/rubric.md','benchmark/rubric.md')
for sid in ['N01','N02','K01','K02']:
    cp(ROOT/'benchmark/episodes'/f'{sid}.json',f'benchmark/episodes/{sid}.json')
    for name in ['reference.json','timeline.json','episode.json','exposure.json','audit.md']:
        p=ROOT/'benchmark/stories_v2/final_20261029'/sid/name
        if p.exists():cp(p,f'benchmark/stories_v2/final_20261029/{sid}/{name}')
cp(ROOT/'benchmark/stories_v2/final_20261029/GRADING_CARDS_N01_N02_K01_K02.json','benchmark/GRADING_CARDS_F3.json')
for name in ['SUBMISSION_REPORT.md','INDEPENDENT_CHECKS.json','F3_BINDING_CHECKS.json','SEVERE_ERROR_SOURCE_REVIEW.json','E3_INDEPENDENT_REPLAY.json','CORE_FUNCTION_RESTORE.json','CORE_FUNCTION_WRITEBACK.json','WORKER_CORE_CLOSEOUT_NEXT.md']:
    cp(HERE/name,'review/'+name)
cp(HERE/'core_function_probe.py','tools/core_function_probe.py')
cp(BASE/'FINAL_CANDIDATE_FREEZE_20261029.json','review/FROZEN_R4.json')
cp(BASE/'F5_CANDIDATE_FREEZE_20261029.json','review/FROZEN_E3.json')
for name in ['F3_MATERIALS.jsonl','F3_PRIVATE_BINDINGS.json','F3_ANALYSIS.json','F3_KNOWLEDGE_CHAIN.jsonl','F3_KNOWLEDGE_CHAIN_SUMMARY.json','F6_COST_INDEX.json']:
    cp(BASE/name,'evidence/'+name)
cp(ROOT/'logs/f3_judge_20261029_115709/F3_RATINGS.jsonl','evidence/F3_RATINGS.jsonl')
bd=j(BASE/'F3_PRIVATE_BINDINGS.json')
for b in bd.values():
    for arm,rel in b['turn_dir'].items():
        for n in ['calls.json','reply_raw.txt','manifest.json','mechanism_notes.txt','scoring.json','reply_ingest.json']:
            p=ROOT/rel/n
            if p.exists():cp(p,Path('evidence/f3_answers')/arm/b['case_id']/n)
for a in j(BASE/'F6_COST_INDEX.json')['authorizations']:cp(ROOT/a['ledger'],'evidence/closed_ledgers/'+Path(a['ledger']).name)
(DEST/'README.md').write_text('''# LoveBrain 研究原型（2026-10-29）

先读 review/SUBMISSION_REPORT.md。状态：可运行研究原型，完整目标REWORK。
本包不是“全面优于原版”的生产发布；不含原用户聊天导出、API密钥、有效调用授权或巨型历史日志。

## 包含什么

- 冻结r4、差额修复E3和原版的实际候选依赖；保留原目录结构与身份。
- 同一现有runner/网关/预算入口，未另造训练框架。
- 4篇虚构开发故事、72张卡、144份F3实际模型请求/答卷、72份匿名裁判结果。
- 独立重算、错误来源、知识链缺测说明和条件化的E3重放证据。
- 10本关闭预算账本，仅作审计，不可复用调用。

## 在干净目录离线演示

本次实测Windows、Python3.12.4；核心演示不需要第三方Python库。解包进入本目录后：

```
python -X utf8 -B tools/iso_run.py --episode benchmark/episodes/N01.json --candidate action_policy_r4_guard_e3 --adapter mock --quota 12 --out runs/demo_N01
```

结果在runs/demo_N01。mock只证明12轮装配/消费/状态/审计链路，mock分数没有质量意义；不要覆盖已有运行目录。

受控核心功能测试（零网络，使用脚本给定模型输出，非模型能力成绩）：

```
$env:LOVEBRAIN_ROOT=(Get-Location).Path
python -X utf8 -B tools/core_function_probe.py
```

该测试验证真实消息/草稿分离、事实更正、1400CU之外的投影路径、关闭目标与新进程恢复。

## 真实服务

本项目实测服务为OpenAI-compatible的dsf，temperature=0.7。真实调用沿用tools/iso_run.py的openai_compat适配器；必须另提供合法、有限额的授权单，不能使用证据中的已关闭账本。此包不自动发请求、不自动续费。

## 成绩归属

72对F3成绩属于action_policy_r4_guard_final；E3只有局部真实回归和离线等价重放。旧F1混合历史答卷不属于r4对原版。匿名裁判不等于人工验收；故事作者看过候选结果，不是独立未见测试集。

数据为虚构聊天，仍请按导师/学校要求处理公开范围。不要把本包中的prompt或评审卡用于声称独立封存测试。
''',encoding='utf-8')
# No active authorizations are included. Verify executable snapshot, not just filenames.
cmd=[str(Path(sys.executable).with_name('pythonw.exe')),'-X','utf8','-B','tools/iso_run.py','--episode','benchmark/episodes/N01.json','--candidate','action_policy_r4_guard_e3','--adapter','mock','--quota','12','--out','runs/demo_N01']
p=subprocess.run(cmd,cwd=DEST,capture_output=True,text=True,encoding='utf-8',errors='replace',creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),timeout=120)
(HERE/'PORTABLE_SMOKE.txt').write_text(p.stdout+'\nSTDERR\n'+p.stderr,encoding='utf-8')
if p.returncode:raise SystemExit('Portable mock smoke failed; inspect PORTABLE_SMOKE.txt')
cp(HERE/'PORTABLE_SMOKE.txt','review/PORTABLE_SMOKE.txt')
entries={}
for f in sorted(DEST.rglob('*')):
    if f.is_file() and '__pycache__' not in str(f) and not f.relative_to(DEST).parts[0]=='runs':
        entries[f.relative_to(DEST).as_posix()]={'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'bytes':f.stat().st_size}
(DEST/'MANIFEST.json').write_text(json.dumps({'files':entries,'excluded':'Generated runs/demo_N01 and pycache are not packaged; audit transcript is included.','claims':'research prototype, not full acceptance'},ensure_ascii=False,indent=2),encoding='utf-8')
z=HERE/'LoveBrain_research_submission_20261029.zip'
with zipfile.ZipFile(z,'x',zipfile.ZIP_DEFLATED) as f:
    for rel in entries:f.write(DEST/rel,arcname=DEST.name+'/'+rel)
    f.write(DEST/'MANIFEST.json',arcname=DEST.name+'/MANIFEST.json')
record={'zip':str(z),'sha256':hashlib.sha256(z.read_bytes()).hexdigest(),'bytes':z.stat().st_size,'payload_files':len(entries),'portable_mock_exit_code':p.returncode,'real_model_calls':0}
(HERE/'BUNDLE_RESULT.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(record,ensure_ascii=False))
