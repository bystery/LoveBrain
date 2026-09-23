#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
全仓 CancellationException 审计器 + 防回归门禁。

复核报告 §6 S2-07 最后一条的批评是："没有全仓 CancellationException 审计结果和 lint/static rule 防回归"。
这里同时交付两样东西。

一、审计结果
扫 app/src/main 的每一个 `catch (:Exception)` / `catch (:Throwable)` / `runCatching {`，逐个判定：

  PROTECTED      块内（或紧随块尾 4 行内，Kotlin 惯用的 `r.exceptionOrNull() is CancellationException
                 → throw` 写法）显式出现 CancellationException —— 取消信号被显式放行
  WAIVED        站点同行或紧邻上一行写了 `cancel-safe:` + 非空理由 —— 人工复核过的豁免
  SUSPEND-FREE   块体内没有任何挂起点：纯字符串解析 / 纯 java.io 文件读写 / 非挂起工具函数。
                 取消信号不可能从这些调用栈里抛出，吞掉 Exception 不构成吞取消
  NEEDS_REVIEW   以上都不是，且块体内确实存在挂起调用 —— 协程被取消时会被静默吞掉，
                 表现为"取消后继续跑/把取消当成业务失败上报"，--check 下直接失败

挂起点判据不靠猜：先从整个 app/src/main 收集所有 `suspend fun` 的名字，
再叠加协程库与 java 并发包里公认的挂起 API（withContext/delay/withLock/await/collect…）。
块体里出现其中任意一个才算有挂起点。

二、防回归门禁
`--check` 下有 NEEDS_REVIEW 即退出码非 0；扫到 0 个站点同样失败（防止扫描器坏了被当成通过）。

用法：
    python3 scripts/audit_cancellation.py             # 人读汇总
    python3 scripts/audit_cancellation.py --list      # 逐站点判定
    python3 scripts/audit_cancellation.py --report    # markdown 汇总（验收包引用）
    python3 scripts/audit_cancellation.py --check     # CI 门禁
"""
import argparse
import os
import re
import sys

SITE_RE = re.compile(
    r"catch\s*\(\s*\w+\s*:\s*(?:Exception|Throwable|java\.lang\.Exception)\s*\)"
    r"|\brunCatching\s*\{"
)
CANCEL_RE = re.compile(r"CancellationException")
WAIVE_RE = re.compile(r"cancel-safe[:：]\s*(\S.*)")
SUSPEND_DECL_RE = re.compile(r"\bsuspend\s+(?:inline\s+)?fun\s+(?:<[^>]+>\s*)?(?:\w+\s*\.\s*)?(\w+)")
# 调用位：`name(` / `name<` / `name {`。刻意不排除 `a.foo(` 这种带限定的调用——
# knowledgeRepo.readFile(...) 正是要抓的目标。
CALL_RE = re.compile(r"([A-Za-z_]\w*)\s*[(<{]")

# 协程库 / 并发库里确定会挂起的 API（不依赖项目源码收集）
LIBRARY_SUSPEND = {
    'withContext', 'withTimeout', 'withTimeoutOrNull', 'delay', 'launch', 'async',
    'runBlocking', 'coroutineScope', 'supervisorScope', 'join', 'await', 'awaitAll',
    'receive', 'receiveCatching', 'send',
    'collect', 'collectLatest', 'awaitClose', 'ensureActive',
    'withLock', 'withPermit',
    'currentCoroutineContext', 'yield', 'emit', 'first', 'single',
}


def strip_line_comment(line):
    idx = line.find('//')
    return (line[:idx], line[idx:]) if idx >= 0 else (line, '')


def block_end(lines, start_idx):
    """从 start_idx 起第一个 `{` 的配平右括号所在行（含）。

    `} catch (e: Exception) {` 这种行首右括号属于上一个块，不能让深度被它拖成负数，
    否则块尾会被错判成同一行。
    """
    depth, opened = 0, False
    for i in range(start_idx, len(lines)):
        for ch in lines[i]:
            if ch == '{':
                depth += 1
                opened = True
            elif ch == '}' and opened:
                depth -= 1
        if opened and depth <= 0:
            return i
    return min(start_idx + 8, len(lines) - 1)


def kotlin_files(main_root):
    for dirpath, _, files in os.walk(main_root):
        for fn in sorted(files):
            if fn.endswith('.kt'):
                yield os.path.join(dirpath, fn)


def read(path):
    return open(path, 'rb').read().decode('utf-8').replace('\r\n', '\n').split('\n')


def collect_suspend_names(main_root):
    names = set()
    for path in kotlin_files(main_root):
        depth = 0
        for line in read(path):
            code, _ = strip_line_comment(line)
            for m in SUSPEND_DECL_RE.finditer(code):
                names.add(m.group(1))
            depth = 0  # 只做行级收集，不需要块信息
    return names


def body_has_suspend_call(lines, start, end, suspend_names):
    """受保护代码段内是否出现挂起调用。

    只看这一段本身：把块尾之后的行也算进来，会把"下一个函数恰好是 suspend"
    当成当前块的风险点（首轮实现踩过这个坑，虚报了 4 处）。
    """
    if start is None:
        return None
    for i in range(start, min(end, len(lines) - 1) + 1):
        code, _ = strip_line_comment(lines[i])
        for m in CALL_RE.finditer(code):
            name = m.group(1)
            if name in LIBRARY_SUSPEND or name in suspend_names:
                return name
    return None


FUN_HEAD_RE = re.compile(r"^\s*(?:(?:private|internal|public|open|override|abstract|inline|suspend)\s+)*fun\s")
BUILDER_RE = re.compile(
    r"\b(runBlocking|launch|async|withContext|coroutineScope|supervisorScope)\b|\bflow\s*\{"
)


def cancellable_span(lines, site_idx, guard_start, guard_end):
    """受保护代码段是否真的可能被取消。

    满足其一即可：
    - 所在函数声明是 `suspend fun`（非 suspend 函数体内不可能出现裸挂起调用，编译不过）；
    - 从函数头到站点之间出现过协程构建器（`appScope.launch { runCatching { suspendFun() } }`
      这种就在 launch 体内的站点，函数头本身不是 suspend，但一样会吞取消）。
      首轮实现只看被保护段内部，漏掉了这一类，属于漏报，已修正。
    """
    for j in range(site_idx, -1, -1):
        if FUN_HEAD_RE.match(lines[j]):
            if re.search(r"\bsuspend\s+fun\b", lines[j]):
                return True
            return builder_in(lines, j, site_idx)
    return builder_in(lines, 0, site_idx)


def builder_in(lines, start, end):
    if start is None:
        return False
    return bool(BUILDER_RE.search('\n'.join(lines[start:min(end, len(lines) - 1) + 1])))


def matching_try_span(lines, site_idx):
    """catch 站点的危险面在 try 块里：往上找最近的 `try {` 并给出它的跨度。

    只有 `runCatching {` 的站点才是"块体自身"，`catch (e: Exception)` 必须看被保护的那段代码，
    否则 try 里的挂起调用被吞掉就成了盲区。
    """
    for j in range(site_idx, max(site_idx - 400, -1), -1):
        code, _ = strip_line_comment(lines[j])
        if re.search(r"\btry\s*\{", code):
            return j, block_end(lines, j)
    return None, None


def catch_chain_end(lines, try_start, try_end):
    """try/catch/finally 整条链的最后一行。

    同一个 try 上只要挂了 `catch (e: CancellationException)`，后头的 catch (:Exception)
    就不可能吞到取消——必须按整条链判定，否则 probeEndpoint 那种"先重抛再兜底"的
    正确写法会被误报。
    """
    i = try_end
    while i + 1 < len(lines):
        nxt, _c = strip_line_comment(lines[i + 1])
        if re.match(r"^\s*\}\s*(catch|finally)\b", nxt):
            i = block_end(lines, i)
        else:
            break
    return i


def collect(main_root, suspend_names):
    sites = []
    for path in kotlin_files(main_root):
        lines = read(path)
        for i, line in enumerate(lines):
            code, _ = strip_line_comment(line)
            if not SITE_RE.search(code):
                continue
            end = block_end(lines, i)
            body = '\n'.join(lines[i:end + 1])
            above = '\n'.join(lines[max(0, i - 3):i])
            # 紧随其后 2 行：Kotlin 惯用的 `val r = runCatching{...}
            # if (r.exceptionOrNull() is CancellationException) throw ...` 写法。
            # 窗口不放大——放大到 4 行以上会把无关的取消处理算成本站点的保护，制造漏报。
            after = '\n'.join(lines[end + 1:min(len(lines), end + 3)])
            waived = None
            for probe in (line, lines[i - 1] if i > 0 else ''):
                _, comment = strip_line_comment(probe)
                m = WAIVE_RE.search(comment or probe)
                if m and m.group(1).strip():
                    waived = m.group(1).strip()
            is_catch = 'catch' in code
            if is_catch:
                guard_start, guard_end = matching_try_span(lines, i)
                # 同一条 try 链上任何位置显式放行取消，本 catch 就吞不到它
                chain = (chr(10).join(lines[guard_start:catch_chain_end(
                    lines, guard_start, guard_end) + 1]) if guard_start is not None else '')
            else:
                guard_start, guard_end = i, end
                chain = ''
            protected = bool(CANCEL_RE.search(body) or CANCEL_RE.search(above)
                             or CANCEL_RE.search(after) or CANCEL_RE.search(chain))
            susp = (body_has_suspend_call(lines, guard_start, guard_end, suspend_names)
                    if guard_start is not None else None)
            reachable = cancellable_span(lines, i, guard_start, guard_end) if susp else False
            sites.append({
                'path': path, 'line': i + 1, 'end': end,
                'protected': protected, 'waived': waived,
                'suspend_call': susp if reachable else None,
                'unreachable_call': susp if (susp and not reachable) else None,
                'is_catch': is_catch,
            })
    return sites


def verdict(site):
    if site['protected']:
        return 'PROTECTED'
    if site['waived']:
        return 'WAIVED'
    return 'NEEDS_REVIEW' if site['suspend_call'] else 'SUSPEND-FREE'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--report', action='store_true')
    ap.add_argument('--list', action='store_true')
    ap.add_argument('--write', default=None, help='把审计结果写成 markdown 文档（验收包与 CI 产物）')
    ap.add_argument('--root', default=None)
    args = ap.parse_args()

    root = args.root or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    main_root = os.path.join(root, 'app', 'src', 'main', 'java', 'com', 'lovebrain', 'app')
    if not os.path.isdir(main_root):
        print('[cancel-audit] FAIL 找不到源码目录 %s' % main_root)
        return 2

    suspend_names = collect_suspend_names(main_root)
    sites = collect(main_root, suspend_names)
    if not sites:
        print('[cancel-audit] FAIL 扫到 0 个站点：扫描器或路径坏了')
        return 2

    counts = {}
    for s in sites:
        counts[verdict(s)] = counts.get(verdict(s), 0) + 1
    bad = [s for s in sites if verdict(s) == 'NEEDS_REVIEW']
    rel = lambda p: os.path.relpath(p, root).replace('\\', '/')

    if args.report:
        print('共扫描 %d 个吞异常站点（app/src/main，%d 个 suspend 函数名进入挂起判据）：'
              % (len(sites), len(suspend_names)))
        print()
        print('| 判定 | 站点数 | 含义 |')
        print('|---|---:|---|')
        rows = [
            ('PROTECTED', '块内或紧邻上下文显式处理 CancellationException'),
            ('WAIVED', '带 `cancel-safe:` 理由的人工豁免'),
            ('SUSPEND-FREE', '块体内无挂起点，吞掉 Exception 不等于吞掉取消'),
            ('NEEDS_REVIEW', '协程可挂起处吞掉取消信号 —— 必须修'),
        ]
        for k, why in rows:
            print('| %s | %d | %s |' % (k, counts.get(k, 0), why))
        print('| 合计 | %d | |' % len(sites))
    else:
        print('[cancel-audit] 站点 %d：PROTECTED=%d WAIVED=%d SUSPEND-FREE=%d NEEDS_REVIEW=%d'
              % (len(sites), counts.get('PROTECTED', 0), counts.get('WAIVED', 0),
                 counts.get('SUSPEND-FREE', 0), counts.get('NEEDS_REVIEW', 0)))
    if args.list:
        for s in sites:
            print('  %-13s %s:%d%s%s' % (
                verdict(s), rel(s['path']), s['line'],
                '  [挂起点 %s]' % s['suspend_call'] if s['suspend_call'] else '',
                '  <- ' + s['waived'] if s['waived'] else ''))

    if args.write:
        lines = [
            '# 全仓 CancellationException 审计结果',
            '',
            '> 本文件由 `python3 scripts/audit_cancellation.py --write docs/CANCELLATION-AUDIT.md`',
            '> 生成，不要手改；CI 里 `--check` 用的是同一套判据。',
            '',
            '复核报告 §6 S2-07 点名："没有全仓 CancellationException 审计结果和 lint/static rule 防回归"。',
            '这里既是审计结果，也是那条静态规则本身。',
            '',
            '## 判据',
            '',
            '扫描范围：`app/src/main` 下所有 `catch (:Exception)` / `catch (:Throwable)` / `runCatching {`。',
            '',
            '| 判定 | 含义 |',
            '|---|---|',
            '| PROTECTED | 块内、紧邻上下文或同一条 try 链上显式处理 `CancellationException` |',
            '| WAIVED | 站点旁写了 `cancel-safe:` + 非空理由，人工复核过 |',
            '| SUSPEND-FREE | 受保护代码段内没有挂起点，且所在函数不是 suspend：吞异常不等于吞取消 |',
            '| NEEDS_REVIEW | 可挂起处吞掉取消信号 —— `--check` 直接失败 |',
            '',
            '挂起判据由两部分组成：全仓 `suspend fun` 名单（本仓库 %d 个）+ 协程/并发库确定的挂起 API'
            '（withContext / delay / launch / withLock / collect / await / emit …）。'
            % len(suspend_names),
            '',
            '## 结果',
            '',
            '| 判定 | 站点数 |',
            '|---|---:|',
        ]
        for k in ('PROTECTED', 'WAIVED', 'SUSPEND-FREE', 'NEEDS_REVIEW'):
            lines.append('| %s | %d |' % (k, counts.get(k, 0)))
        lines.append('| 合计 | %d |' % len(sites))
        lines += [
            '',
            '`--check` 结论：%s' % ('**PASS**（没有未处置的可挂起吞取消站点）' if not bad
                                     else '**FAIL**（%d 个待处置）' % len(bad)),
            '',
            '## 逐站点',
            '',
            '| 判定 | 位置 | 挂起点 | 豁免理由 |',
            '|---|---|---|---|',
        ]
        for s in sorted(sites, key=lambda x: (x['path'], x['line'])):
            lines.append('| %s | `%s:%d` | %s | %s |' % (
                verdict(s), rel(s['path']), s['line'],
                s['suspend_call'] or '—', (s['waived'] or '—').replace('|', '\\|')))
        out = os.path.join(root, args.write) if not os.path.isabs(args.write) else args.write
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, 'wb') as fh:
            fh.write(('\r\n'.join(lines) + '\r\n').encode('utf-8'))
        print('[cancel-audit] 审计结果已写入 %s（%d 站点）' % (args.write, len(sites)))

    if not args.check:
        return 0
    if bad:
        print('[cancel-audit] FAIL 有 %d 个可挂起站点会吞掉取消信号：' % len(bad))
        for s in bad[:60]:
            print('  %s:%d  [挂起点 %s]' % (rel(s['path']), s['line'], s['suspend_call']))
        return 1
    print('[cancel-audit] PASS 每个可挂起的吞异常站点都显式放行了取消信号')
    return 0


if __name__ == '__main__':
    sys.exit(main())
