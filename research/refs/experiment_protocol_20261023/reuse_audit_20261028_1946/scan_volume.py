"""Directory metadata inventory, not whole-volume content ingestion. No junction follow/hydration."""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import stat
import time


def main():
    ap=argparse.ArgumentParser();ap.add_argument('root');ap.add_argument('out');a=ap.parse_args()
    out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    label=a.root[0].upper();match=re.compile(r'love[ _-]?brain|lovrbrian',re.I)
    s={'volume':a.root,'start':datetime.datetime.now().astimezone().isoformat(),
       'kind':'all_accessible_directory_metadata_scan_not_all_file_contents',
       'directories_enumerated':0,'files_seen':0,'related_files':0,'related_dirs':0,
       'reparse_dirs_skipped':0,'sealed_dirs_not_entered':0,'errors':0,'error_samples':[],
       'reparse_samples':[],'root_project_dirs':[]}
    stack=[a.root];last=time.monotonic()
    with (out/f'{label}_RELATED_PATHS.jsonl').open('w',encoding='utf-8') as f:
        while stack:
            path=stack.pop()
            if ('\\benchmark\\b_line' in path.lower() or '/benchmark/b_line' in path.lower()):
                s['sealed_dirs_not_entered']+=1;continue
            try:
                with os.scandir(path) as it:
                    for e in it:
                        related=bool(match.search(e.path))
                        try:
                            isdir=e.is_dir(follow_symlinks=False)
                            islink=e.is_symlink()
                            if isdir:
                                st=e.stat(follow_symlinks=False)
                                islink=islink or bool(getattr(st,'st_file_attributes',0)&stat.FILE_ATTRIBUTE_REPARSE_POINT)
                                if related:
                                    s['related_dirs']+=1
                                    if not match.search(path):s['root_project_dirs'].append(e.path)
                                if islink:
                                    s['reparse_dirs_skipped']+=1
                                    if len(s['reparse_samples'])<80:s['reparse_samples'].append(e.path)
                                else:stack.append(e.path)
                            else:
                                s['files_seen']+=1
                                if related:
                                    st=e.stat(follow_symlinks=False);s['related_files']+=1
                                    f.write(json.dumps({'path':e.path,'bytes':st.st_size,'mtime':st.st_mtime,
                                                        'attrs':getattr(st,'st_file_attributes',0)},ensure_ascii=False)+'\n')
                        except OSError as exc:
                            s['errors']+=1
                            if len(s['error_samples'])<100:s['error_samples'].append({'path':e.path,'error':str(exc)})
                s['directories_enumerated']+=1
            except OSError as exc:
                s['errors']+=1
                if len(s['error_samples'])<100:s['error_samples'].append({'path':path,'error':str(exc)})
            if time.monotonic()-last>8:
                (out/f'{label}_SCAN_PROGRESS.json').write_text(json.dumps(s,ensure_ascii=False,indent=2),encoding='utf-8');last=time.monotonic()
    s['finished']=datetime.datetime.now().astimezone().isoformat();s['complete_accessible_traversal']=True
    (out/f'{label}_SCAN_SUMMARY.json').write_text(json.dumps(s,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in s.items() if k not in ['reparse_samples','error_samples','root_project_dirs']},ensure_ascii=False))


if __name__=='__main__':main()
