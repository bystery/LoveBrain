#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""scripts/check_big_files.sh 的判据自测。

每格断言**退出码 + 输出里点名的理由**——"红"本身不是证据，红错原因的红等于没有闸。
夹具靠闸自己留的三个入口指进来（MAIN_ROOT / BUDGET_DIR / MIN_SCANNED），不碰真仓库。

为什么驱动是 python 而不是 bash：上一版 bash 驱动的六格全部"退出码 0、日志 0 字节"，
那是驱动自己没把子进程跑起来（同一族坑：仪表读错比仪器坏掉更贵）。换成 subprocess
显式传 env、显式收 stdout/stderr，每格的 rc 与输出字节数都打印出来，读不到东西就当场是错的。
"""
import io
import os
import shutil
import subprocess
import sys
import tempfile

REPO = os.getcwd()
GATE = os.path.join(REPO, 'scripts', 'check_big_files.sh')
WORK = os.environ.get('WORK_DIR') or tempfile.mkdtemp(prefix='bigfile-selftest-')

if not os.path.isfile(GATE):
    io.open(sys.stdout.fileno(), 'w', encoding='utf-8', errors='replace').write(
        '[gate] FAIL 找不到 %s（必须在仓库根跑）\n' % GATE)
    sys.exit(1)


def say(msg):
    sys.stdout.buffer.write((msg + '\n').encode('utf-8', 'replace'))
    sys.stdout.flush()


def fixture(rel, lines):
    p = os.path.join(WORK, 'src', rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    io.open(p, 'w', encoding='utf-8', newline='\n').write('// x\n' * lines)


def ledger(name, text):
    p = os.path.join(WORK, 'scripts', name)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(text)


def fresh_env():
    shutil.rmtree(WORK, ignore_errors=True)
    os.makedirs(os.path.join(WORK, 'scripts'), exist_ok=True)
    fixture('keep/Small.kt', 100)
    fixture('keep/Big.kt', 900)
    fixture('keep/Mid.kt', 600)
    ledger('big-file-800.txt', 'keep/Big.kt  # 900 行\n')
    ledger('big-file-500.txt', 'keep/Mid.kt  # 600 行\n')


def run(main_root=None, budget=None, real=False):
    env = dict(os.environ)
    if real:
        env.pop('MAIN_ROOT', None)
        env.pop('BUDGET_DIR', None)
    else:
        env['MAIN_ROOT'] = main_root if main_root is not None else os.path.join(WORK, 'src')
        env['BUDGET_DIR'] = budget if budget is not None else os.path.join(WORK, 'scripts')
        env['MIN_SCANNED'] = '1'
    p = subprocess.run(['bash', GATE], capture_output=True, text=True,
                       encoding='utf-8', errors='replace', env=env, cwd=REPO)
    return p.returncode, (p.stdout or '') + (p.stderr or '')


PASS, FAIL = [], []


def cell(name, want_rc, needle, rc, out):
    ok = (rc == want_rc)
    if ok and needle and needle not in out:
        ok = False
    head = ' | '.join(out.strip().splitlines()[-2:]) if out.strip() else '（无输出）'
    line = '%-4s %s :: rc=%d 期望=%d，输出 %d 字节：%s' % (
        'OK' if ok else 'FAIL', name, rc, want_rc, len(out), head[:220])
    (PASS if ok else FAIL).append(name)
    say('[gate] ' + line)


# C1 登记与实到一致 -> 合规
fresh_env()
cell('C1 登记与实到一致时应当合规（退出码 0）', 0, 'ratchet holds', *run())

# C2 新出现一个 >800 的巨石 -> 红，且点名它自己（不是点名别人）
fresh_env(); fixture('keep/NewGiant.kt', 1200)
cell('C2 新出现的 >800 行文件必须红并点名', 1, 'keep/NewGiant.kt', *run())

# C3 新出现一个 500–800 的 -> 也要红（这一档同样登记着）
fresh_env(); fixture('keep/NewMid.kt', 520)
cell('C3 新出现的 >500 行文件也必须红', 1, 'keep/NewMid.kt', *run())

# C4 还了债不改账本：登记的条目其实已经不跨线 -> 红，并点名那条幽灵
fresh_env(); fixture('keep/Mid.kt', 120)
cell('C4 登记里有幽灵条目时必须红', 1, 'keep/Mid.kt', *run())

# C5 缺登记清单 = 没有闸，必须 CANNOT-VERIFY（不许"零违例通过"）
fresh_env(); os.remove(os.path.join(WORK, 'scripts', 'big-file-500.txt'))
cell('C5 缺登记清单要报 CANNOT-VERIFY 而不是放行', 2, 'CANNOT-VERIFY', *run())

# C6 扫描根接错，必须 CANNOT-VERIFY
fresh_env()
cell('C6 扫描根不存在时不许零违例通过', 2, 'CANNOT-VERIFY',
     *run(main_root=os.path.join(WORK, 'nope')))

# C7 正向对照：拿真仓库跑，必须报出实到扫描数（证明这把尺看得见东西）
cell('C7 对真仓库跑一遍要报出实到扫描数', 0, '扫了 ', *run(real=True))

say('[gate] big-file 自测：%d 格通过 / %d 格失败（夹具目录 %s）' % (len(PASS), len(FAIL), WORK))
if FAIL:
    say('[gate] 失败格：%s' % FAIL)
    sys.exit(1)
