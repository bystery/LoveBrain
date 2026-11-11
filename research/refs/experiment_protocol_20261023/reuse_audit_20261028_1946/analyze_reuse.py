"""Full-text hashing/AST analysis of project source, excluding sealed/private/runtime copies."""
from pathlib import Path
import ast
import collections
import copy
import hashlib
import json
import os
import re

ROOT=Path(r'D:\lovebrain-arena')
OUT=Path(__file__).resolve().parent
EXT={'.py','.md','.kt','.kts','.json','.toml','.yaml','.yml','.js','.ts'}
SKIP={'.git','.gradle','build','dist','node_modules','__pycache__','_archive','archive','_temp',
      'dataset','keystore','keystores','memory','b_line','_reports','batch','staged','_iso_run',
      'logs','runs','isol_runs','images','.npm-cache','backups','volumes'}
SCOPES={
 'lab_code':[ROOT/'arena',ROOT/'tools',ROOT/'candidates'],
 'lab_design':[ROOT/'refs',ROOT/'evaluation'],
 'product':[Path('D:/LoveBrain/app/src')],
 'product_mirror':[Path('D:/TeamWorkspace/products/LoveBrain/app/src')],
 'product_e':[Path('D:/LoveBrain-arena-e/arena-e'),Path('D:/LoveBrain-arena-e/app/src')],
 'governance':[Path('D:/TeamWorkspace/team/projects/lovebrain')],
 'original_skills':[Path('D:/桌面/工作信息/SKILL/恋爱')],
}


def files(base):
    if not base.exists():return
    for root,dirs,fs in os.walk(base,followlinks=False):
        dirs[:]=[n for n in dirs if n not in SKIP and not n.startswith('reuse_audit_')
                  and 'acl_locked' not in n.lower() and not (Path(root)/n).is_symlink()]
        for n in fs:
            p=Path(root)/n
            if p.suffix.lower() in EXT and '.bak' not in n and '.before' not in n:
                yield p


def main():
    data=[];errors=[];fn=[];summary={}
    for label,bases in SCOPES.items():
        total=0;cnt=0
        for base in bases:
            for p in files(base):
                try:
                    bs=p.read_bytes()
                    if len(bs)>3_000_000:continue
                    text=bs.decode('utf-8-sig',errors='replace');cnt+=1;total+=len(bs)
                    row={'scope':label,'path':str(p),'bytes':len(bs),'sha256':hashlib.sha256(bs).hexdigest(),
                         'lines':len(text.splitlines())}
                    if label=='lab_code' and p.suffix=='.py':
                        try:
                            tree=ast.parse(text)
                            row['imports']=[ast.unparse(n) for n in ast.walk(tree) if isinstance(n,(ast.Import,ast.ImportFrom))]
                            row['parent_path_hints']=[line.strip()[:240] for line in text.splitlines()
                                if any(x in line for x in ['PARENT','v6_integrated','v6_goal_context','reference_product_v1','normalize_reply_contract','output_repair'])][:18]
                            for node in ast.walk(tree):
                                if not isinstance(node,(ast.FunctionDef,ast.AsyncFunctionDef)):continue
                                if node.end_lineno-node.lineno<7:continue
                                n=copy.deepcopy(node);n.name='FUNCTION'
                                if n.body and isinstance(n.body[0],ast.Expr) and isinstance(n.body[0].value,ast.Constant) and isinstance(n.body[0].value.value,str):n.body.pop(0)
                                digest=hashlib.sha256(ast.dump(n,include_attributes=False).encode()).hexdigest()
                                fn.append({'path':str(p),'function':node.name,'line':node.lineno,'lines':node.end_lineno-node.lineno+1,'ast_sha':digest})
                        except SyntaxError as exc:errors.append({'path':str(p),'error':str(exc)})
                    data.append(row)
                except OSError as exc:errors.append({'path':str(p),'error':str(exc)})
        summary[label]={'text_files_read':cnt,'bytes_read':total}
    byhash=collections.defaultdict(list)
    for row in data:byhash[row['sha256']].append({'scope':row['scope'],'path':row['path'],'bytes':row['bytes']})
    whole=[{'sha256':h,'files':v} for h,v in byhash.items() if len(v)>1]
    byfn=collections.defaultdict(list)
    for x in fn:byfn[x['ast_sha']].append(x)
    clones=[v for v in byfn.values() if len(set(x['path'] for x in v))>1]
    clones.sort(key=lambda v:sum(x['lines'] for x in v),reverse=True)
    summary['whole_file_clone_groups']=len(whole);summary['ast_clone_groups']=len(clones)
    summary['interpretation']='Shared frozen assets, candidate inheritance and snapshot copies are not automatically redundant work. Semantic reimplementation requires manual evidence.'
    for name,val in [('TEXT_SOURCE_INVENTORY.json',data),('WHOLE_FILE_CLONES.json',whole),('FUNCTION_CLONES.json',clones),('READ_ERRORS.json',errors),('SOURCE_ANALYSIS_SUMMARY.json',summary)]:
        (OUT/name).write_text(json.dumps(val,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False))
    print('top tool clones',json.dumps([[{'path':x['path'],'fn':x['function'],'line':x['line']} for x in g]
                for g in clones if sum('\\tools\\' in x['path'] for x in g)>1][:8],ensure_ascii=False))


if __name__=='__main__':main()
