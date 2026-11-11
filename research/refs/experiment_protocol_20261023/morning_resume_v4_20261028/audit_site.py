"""Read-only morning inventory. No model/network calls, no original artifact edits."""
from pathlib import Path
import collections
import datetime as dt
import hashlib
import json
import subprocess

ROOT = Path(r'D:\lovebrain-arena')
OUT = Path(__file__).resolve().parent


def read_json(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))


def rows(p):
    result = []
    for i, line in enumerate(p.read_text(encoding='utf-8-sig').splitlines(), 1):
        if line.strip():
            try:
                result.append(json.loads(line))
            except Exception as exc:
                raise RuntimeError(f'{p}:{i}: {exc}')
    return result


def ledger_summary(p):
    data = rows(p)
    starts = [x for x in data if x.get('kind') == 'attempt_started']
    terminals = [x for x in data if x.get('kind') in ('attempt_completed', 'attempt_failed')]
    sc = collections.Counter(x.get('attempt_id') for x in starts)
    tc = collections.Counter(x.get('attempt_id') for x in terminals)
    return dict(path=str(p), events=dict(collections.Counter(x.get('kind') for x in data)),
                scope=data[0], last=data[-1],
                duplicate_started_ids={k:v for k,v in sc.items() if v != 1},
                unsettled_ids=[k for k in sc if not tc[k]],
                duplicate_terminal_ids={k:v for k,v in tc.items() if v != 1},
                mtime=dt.datetime.fromtimestamp(p.stat().st_mtime).astimezone().isoformat())


def traces(folder):
    result = []
    for p in sorted(folder.glob('TRACE_*.json')):
        x = read_json(p)
        result.append(dict(path=str(p), trajectory_id=x.get('trajectory_id'),
                           status=x.get('status'), arm=x.get('arm'),
                           scenario=x.get('scenario'), feedback=x.get('feedback'),
                           repeat=x.get('repeat'), turns=len(x.get('trace', [])),
                           harness_calls=x.get('harness_calls'), sim_calls=x.get('sim_calls'),
                           deliveries=dict(collections.Counter(t.get('candidate_kind')
                                                for t in x.get('trace', []))),
                           sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
    return result


def main():
    report = dict(as_of=dt.datetime.now().astimezone().isoformat(), new_real_model_calls=0)
    names = ['AUTH-P06-RECOVERY-20261027-01', 'AUTH-P07-V2-20261028-01',
             'AUTH-P07-W09-20261028-01', 'AUTH-P07-W03VAL-20261028-01']
    report['budgets'] = {n: ledger_summary(ROOT/'logs/auth_budget'/f'{n}.jsonl')
                         if (ROOT/'logs/auth_budget'/f'{n}.jsonl').exists()
                         else {'ledger_exists': False} for n in names}
    pilot = []
    for folder in sorted((ROOT/'logs').glob('p07_pilot_v2_20261028_*')):
        if folder.is_dir():
            pilot.extend(traces(folder))
    w09 = traces(ROOT/'logs/p07_w09_20261028_a')
    report['pilot_v2'] = dict(traces=pilot, statuses=dict(collections.Counter(x['status'] for x in pilot)))
    complete_by_cell = collections.defaultdict(set)
    for x in w09:
        if x['status'] == 'complete':
            complete_by_cell[(x['scenario'], x['feedback'])].add(x['arm'])
    report['w09'] = dict(traces=w09, statuses=dict(collections.Counter(x['status'] for x in w09)),
                        complete_by_arm=dict(collections.Counter(x['arm'] for x in w09 if x['status']=='complete')),
                        complete_paired_cells=sum(len(arms)>=2 for arms in complete_by_cell.values()),
                        notes='One overwritten complete trace is disclosed in W09_DISPATCH_INCIDENT; current files are authoritative, historical summary retained separately.')
    gw = []
    for folder in [*(ROOT/'logs').glob('p07_pilot_v2_20261028_*'), ROOT/'logs/p07_w09_20261028_a']:
        for p in folder.glob('*.jsonl'):
            data = rows(p)
            started = [x for x in data if x.get('kind')=='attempt_started']
            if started:
                gw.append(dict(path=str(p), counts=dict(collections.Counter(x.get('kind') for x in data)),
                               started_by_model=dict(collections.Counter(x.get('model') for x in started)),
                               attempt_ids=[x.get('attempt_id') for x in started]))
    report['gateway_files'] = gw
    report['stage2'] = {k:read_json(ROOT/'benchmark/stories_v2/stage2/STAGE2_VALIDATION.json').get(k)
                        for k in ['passed','stories_passed','unique_cases','errors','turn_per_story']}
    report['stage2']['independent_regression'] = str(OUT/'STAGE2_INDEPENDENT_VALIDATION.json')
    source_paths = ['tools/run_p07_pilot.py','tools/run_p06_recovery_review.py',
                    'tools/build_b3_simulator_request.py','tools/normalize_reply_contract.py',
                    'tools/validate_story_v2.py','candidates/action_policy_r1/mechanism/mechanism.py',
                    'candidates/action_policy_r1/params.json']
    report['sources'] = {p:dict(sha256=hashlib.sha256((ROOT/p).read_bytes()).hexdigest(),
                              mtime=dt.datetime.fromtimestamp((ROOT/p).stat().st_mtime).astimezone().isoformat()) for p in source_paths}
    cmd = "Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'python|node|bash' } | Select-Object ProcessId,ParentProcessId,Name,CreationDate,CommandLine | ConvertTo-Json -Depth 3"
    cp = subprocess.run(['powershell','-NoProfile','-Command',cmd],capture_output=True,encoding='utf-8',errors='replace')
    try:
        procs = json.loads(cp.stdout or '[]')
        if isinstance(procs,dict): procs=[procs]
        report['lovebrain_processes'] = [x for x in procs if any(w in (x.get('CommandLine') or '').lower()
                    for w in ['run_p07','run_p06','run_next_batch','run_c4','exam_interleave','run_p05'])]
    except Exception as exc:
        report['process_snapshot_error'] = str(exc)
    (OUT/'SITE_AUDIT.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(as_of=report['as_of'],pilot=report['pilot_v2']['statuses'],w09=report['w09']['statuses'],
                          w09_pairs=report['w09']['complete_paired_cells'],active=len(report.get('lovebrain_processes',[])),
                          stage2=report['stage2']),ensure_ascii=False))


if __name__=='__main__':
    main()
