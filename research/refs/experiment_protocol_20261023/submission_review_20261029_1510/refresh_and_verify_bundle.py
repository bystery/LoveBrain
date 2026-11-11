import hashlib,json,shutil,zipfile
from pathlib import Path
HERE=Path(__file__).parent;DEST=HERE/'LoveBrain_research_prototype_20261029'
for name in ['SUBMISSION_REPORT.md','SYSTEM_ABSTRACT.md']:
    shutil.copy2(HERE/name,DEST/'review'/name)
entries={}
for f in sorted(DEST.rglob('*')):
    if f.is_file() and '__pycache__' not in str(f) and f.name!='MANIFEST.json' and f.relative_to(DEST).parts[0]!='runs':
        entries[f.relative_to(DEST).as_posix()]={'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'bytes':f.stat().st_size}
(DEST/'MANIFEST.json').write_text(json.dumps({'files':entries,'excluded':'Generated runs/demo_N01 and pycache are not packaged; transcript included.','claims':'research prototype, not full acceptance'},ensure_ascii=False,indent=2),encoding='utf-8')
z=HERE/'LoveBrain_research_submission_20261029.zip'
if z.exists():z.rename(HERE/'LoveBrain_research_submission_20261029.before_scope_addition.zip')
with zipfile.ZipFile(z,'x',zipfile.ZIP_DEFLATED) as f:
    for rel in entries:f.write(DEST/rel,DEST.name+'/'+rel)
    f.write(DEST/'MANIFEST.json',DEST.name+'/MANIFEST.json')
with zipfile.ZipFile(z) as f:
    assert f.testzip() is None
    for rel,rec in entries.items():assert hashlib.sha256(f.read(DEST.name+'/'+rel)).hexdigest()==rec['sha256'],rel
rec={'zip':str(z),'sha256':hashlib.sha256(z.read_bytes()).hexdigest(),'bytes':z.stat().st_size,'payload_files':len(entries),'portable_mock_exit_code':0,'mock_turns_completed':12,'mock_quality_claim':False,'real_model_calls_this_review':0,'all_payload_hashes_verified':True,'python_tested':'3.12.4 Windows'}
(HERE/'BUNDLE_RESULT.json').write_text(json.dumps(rec,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(rec,ensure_ascii=False))
