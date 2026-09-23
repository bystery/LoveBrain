#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""
生产代码工单编号清理器（返工指导书 §6 S2-07："历史工单编号和补丁说明仍广泛留在生产代码"）。

只做一件事：把 `app/src/main` **注释里**的历史工单编号（F04 / P0-4 / S2-06 / PROV-01 …）
和"复核报告 §x""第 N 轮调研""先例 = …""需求#10"这类补丁指涉删掉，保留说明本身。
编号属于需求与审计留档，不属于生产代码——读代码的人手里没有那张工单表。

设计原则（前两版各翻过一次车，故把教训写在这里）：
- 只改注释，绝不动代码。块注释状态机必须识别 `/* … */` 的同行闭合，
  否则紧随其后的真实代码会被当成注释改写（第一版就是这么把 `const val` 变成注释的）。
- 逐行等量替换：不删行、不并空行、不重建缩进、不动 markdown 列表符与句末冒号。
  第一版顺手做过"连续空格压一个"和"删句尾：/—"，结果 KDoc 缩进被压平、列表 `-` 被吃掉。
  清理编号不该改动注释排版。落盘前断言行数不变，变了直接拒绝写。
- 回填捕获组一律用 lambda。把反斜杠加数字写成普通字符串常量，
  会先被 Python 解析成控制字符 U+0001，再原样塞进源码——这个坑踩过一次，21 个文件被污染。
- 字符串字面量里的编号只报不改（日志前缀要人工定夺）。
- 记账先于落盘：先写 _temp/ 账本再改源码。
- 幂等：--idempotence-check 连跑两次必须逐字节相同。
- 门禁：--check 下生产代码仍有编号即退出码非 0。

用法：
    python3 scripts/strip_ticket_ids.py            # 干跑，只报数
    python3 scripts/strip_ticket_ids.py --apply    # 落盘
    python3 scripts/strip_ticket_ids.py --idempotence-check
    python3 scripts/strip_ticket_ids.py --check    # CI 门禁
"""
import argparse
import datetime
import json
import os
import re
import sys

TOKEN = (
    # 带连字符的编号族：S2-04 / P0-12 / GEN-02 / F09-7 / KBG-01 …
    r"(?:(?:S[123]|P[0-3]|R[012]|ONB|KBG|KB|RA|URL|UX|GEN|PROV|COUN|SUG|DIAG|PERF"
    r"|D\d|V\d|B\d|X\d|F\d{1,2})-(?:\d{1,3}|[A-Za-z]{1,4}\d*)(?:-[A-Za-z]{1,4})?"
    # 裸编号族：F04 / F18a / R0 / C1 / P0-① —— 早先只留了带连字符的一半，
    # 结果 F02/F15/F16 整族漏掉（自测 grep 抓到，见 --check 会拦）
    r"|F\d{2}[A-Za-z]?"
    r"|P0-[①②③④⑤]"
    r"|R0(?![\w-])"
    r"|C[12](?![\w-]))"
)
SP = "[ \t]"
TOKEN_RUN = TOKEN + r"(?:" + SP + "*[/、,，]" + SP + r"*(?<![\w-])" + TOKEN + r")*"
TOKEN_RE = re.compile(TOKEN)
# 编号两侧不能再接单词字符或连字符，避免吃到 UTF-8 / 1.3.1 / 变量名
BOUND_L = r"(?<![\w-])"
BOUND_R = r"(?![\w-])"

# 只删编号本身 + 它后面紧跟的那个分隔符；不动其它排版
RULES = [
    # "S1-04 审计修复：xxx" → "xxx"
    (re.compile(BOUND_L + TOKEN_RUN + BOUND_R + r"\s*(?:审计)?修复\s*[：:]\s*"), ''),
    (re.compile(r"(?:(?<=^)|(?<=[ \t（(]))(?:审计|复核|工单|返工)(?:修复|技术债|遗留|点名|批准|要求)\s*[：:]\s*"), ''),
    # "P3-04 的…" → "…"
    (re.compile(BOUND_L + TOKEN_RUN + BOUND_R + SP + r"*的" + SP + r"*"), ''),
    # 行首/括号后的"编号：说明"——分隔符后允许没有空格（中文直接接正文）
    (re.compile(r"(?:(?<=^)|(?<=[ \t（(《\[]))" + TOKEN_RUN + BOUND_R + SP + r"*[：:—\-]{1,2}" + SP + r"*"), ''),
    (re.compile(SP + r"*[（(\[]\s*" + TOKEN_RUN + BOUND_R + r"\s*[)）]]"), ''),
    (re.compile(SP + r"*[,，;；]" + SP + r"*(?=" + BOUND_L + TOKEN_RUN + BOUND_R + SP + r"*$)"), ''),
    (re.compile(SP + r"*" + BOUND_L + TOKEN_RUN + BOUND_R + SP + r"*$"), ''),
    (re.compile(SP + r"*[,，、]" + SP + r"*(?=" + BOUND_L + TOKEN_RUN + BOUND_R + SP + r"*[,，、])"), ''),
    (re.compile(BOUND_L + TOKEN_RUN + BOUND_R + SP + r"*"), ''),
]
NARRATIVE = [
    (re.compile(r"[（(]?" + SP + r"*(?:复核报告|审计报告|任务单|验收单)(?:批准|要求|点名|结论)?"
                + SP + r"*(?:第?\s*[§#]\s*[0-9\.一二三四五六七八九十]+)?(?:" + SP + r"*的)?" + SP + r"*[)）]?"), ''),
    (re.compile(r"[（(]?" + SP + r"*第" + SP + r"*\d+" + SP + r"*轮"
                + SP + r"*(?:调研|复核|修订|批准|修复)?" + SP + r"*[)）]?"), ''),
    (re.compile(SP + r"*先例" + SP + r"*=" + SP + r"*[A-Za-z0-9_./、, ]*"), ''),
    (re.compile(r"[（(]" + SP + r"*[修]订表[^)）]*[)）]"), ''),
    # "需求#10/#11" 同样是审计留档
    (re.compile(r"[（(]?" + SP + r"*需求" + SP + r"*#" + SP + r"*\d+(?:" + SP + r"*/" + SP + r"*#?\d+)*"
                + SP + r"*[)）]?"), ''),
]
DECOR = r"[═─=\-]{2,}"
COLON = "[：:]"
COMMENT_MARK = r"^(?P<mark>\s*(?://+|/?\*)+)"

# 收尾：只处理被暴露的悬空冒号与行尾空白；捕获组一律用 lambda 回填
TIDY = [
    (re.compile(COMMENT_MARK + SP + "+(" + DECOR + ")" + SP + "*" + COLON + SP + "*"),
     lambda mo: mo.group('mark') + ' ' + mo.group(2) + ' '),
    (re.compile("(" + DECOR + ")" + SP + "*" + COLON + SP + "*"),
     lambda mo: mo.group(1) + ' '),
    (re.compile(COMMENT_MARK + SP + "*" + COLON), lambda mo: mo.group('mark') + ' '),
    (re.compile(r"[ \t]+$"), ''),
]


def scan_line(line, in_block):
    """返回 (每列是否属于注释, 新的 in_block)。逐字符跟踪字符串、字符面量与块注释。"""
    mask = [False] * len(line)
    i, n = 0, len(line)
    in_str = in_char = False
    while i < n:
        if in_block:
            if line.startswith('*/', i):
                mask[i] = mask[i + 1] = True
                in_block = False
                i += 2
                continue
            mask[i] = True
            i += 1
            continue
        ch = line[i]
        if in_str:
            if ch == '\\':
                i += 2
                continue
            if ch == '"':
                in_str = False
            i += 1
            continue
        if in_char:
            if ch == '\\':
                i += 2
                continue
            if ch == "'":
                in_char = False
            i += 1
            continue
        if ch == '"':
            in_str = True
            i += 1
            continue
        if ch == "'":
            in_char = True
            i += 1
            continue
        if line.startswith('//', i):
            for j in range(i, n):
                mask[j] = True
            break
        if line.startswith('/*', i):
            in_block = True
            mask[i] = mask[i + 1] = True
            i += 2
            continue
        i += 1
    return mask, in_block


def comment_ranges(mask):
    spans, start = [], None
    for i, m in enumerate(mask):
        if m and start is None:
            start = i
        elif not m and start is not None:
            spans.append((start, i))
            start = None
    if start is not None:
        spans.append((start, len(mask)))
    return spans


def clean_comment(text):
    hits = 0
    out = text
    for _round in range(3):          # 一行里可能连写两个编号，迭代到不再变化
        changed = False
        for pattern, repl in RULES:
            new, n = pattern.subn(repl, out)
            if n:
                hits += n
                out = new
                changed = True
        for pattern, repl in NARRATIVE:
            new, n = pattern.subn(repl, out)
            if n:
                hits += n
                out = new
                changed = True
        if not changed:
            break
    for pattern, repl in TIDY:
        out = pattern.sub(repl, out)
    return out, hits


def clean_source(text):
    lines = text.replace('\r\n', '\n').split('\n')
    in_block = False
    out, total = [], 0
    for line in lines:
        mask, in_block = scan_line(line, in_block)
        rebuilt, cursor = [], 0
        for start, end in comment_ranges(mask):
            rebuilt.append(line[cursor:start])
            piece, n = clean_comment(line[start:end])
            total += n
            rebuilt.append(piece)
            cursor = end
        rebuilt.append(line[cursor:])
        out.append(''.join(rebuilt))
    return '\n'.join(out), total


def kt_files(root):
    for dirpath, _, files in os.walk(root):
        for fn in sorted(files):
            if fn.endswith('.kt'):
                yield os.path.join(dirpath, fn)


def read(path):
    return open(path, 'rb').read().decode('utf-8').replace('\r\n', '\n')


def string_hits(main_root):
    found = []
    for path in kt_files(main_root):
        for no, line in enumerate(read(path).split('\n'), 1):
            for m in re.finditer(r'"[^"\n]*"', line):
                if TOKEN_RE.search(m.group(0)):
                    found.append((path, no, m.group(0)))
    return found


def code_residue(main_root):
    """非注释、非字符串里还剩的编号——真正的残留。"""
    residue = []
    for path in kt_files(main_root):
        in_block = False
        for no, line in enumerate(read(path).split('\n'), 1):
            mask, in_block = scan_line(line, in_block)
            code = ''.join(ch for i, ch in enumerate(line) if not mask[i])
            code = re.sub(r'"[^"]*"', '""', code)
            m = TOKEN_RE.search(code)
            if m:
                residue.append((path, no, m.group(0), line.strip()[:90]))
    return residue


def comment_residue(main_root):
    n = 0
    for path in kt_files(main_root):
        in_block = False
        for line in read(path).split('\n'):
            mask, in_block = scan_line(line, in_block)
            comment = ''.join(ch for i, ch in enumerate(line) if mask[i])
            n += len(TOKEN_RE.findall(comment))
    return n


def control_bytes(path):
    return sum(1 for b in open(path, 'rb').read() if b < 9 or (10 < b < 32 and b != 13))


def clean_strings_in_line(line):
    """只处理字符串字面量开头的 "TOKEN: " 日志前缀。

    这类前缀只出现在 L.w/L.e 的 logcat 文案里，不带用户可见性；
    编号留在日志前缀中同样属于审计留档泄漏，但它藏在字符串里，
    注释侧的规则碰不到，所以单独一条窄规则：只删字面量**开头**的"编号 + 冒号 + 空格"。
    """
    hits = 0

    def repl(m):
        nonlocal hits
        literal = m.group(0)
        inner = literal[1:-1]
        new_inner, n = re.compile(r'^' + TOKEN_RUN + BOUND_R
                                  + r'[ \t]*[：:][ \t]*').subn('', inner, count=1)
        if n:
            hits += n
            return '"' + new_inner + '"'
        return literal

    new = re.sub(r'"(?:[^"\\\n]|\\.)*"', repl, line)
    return new, hits


def strings_mode(main_root, apply_changes, rel):
    changed, total = 0, 0
    for path in kt_files(main_root):
        raw = read(path)
        lines = raw.split('\n')
        in_block = False
        out = []
        for line in lines:
            mask, in_block = scan_line(line, in_block)
            # 注释部分整段跳过，只重写非注释列上的字面量
            rebuilt, cursor = [], 0
            gaps = []
            start = None
            # 末尾补 True 才能把最后一段非注释区间收尾（补 False 会让整行都不落地）
            for i, m in enumerate(mask + [True]):
                if not m and start is None:
                    start = i
                elif m and start is not None:
                    gaps.append((start, i))
                    start = None
            for start, end in gaps:
                rebuilt.append(line[cursor:start])
                piece, n = clean_strings_in_line(line[start:end])
                total += n
                rebuilt.append(piece)
                cursor = end
            rebuilt.append(line[cursor:])
            out.append(''.join(rebuilt))
        new_text = '\n'.join(out)
        if new_text == raw:
            continue
        if len(new_text.split('\n')) != len(lines):
            print('[strip-tickets] FAIL %s 行数被改动，拒绝落盘' % rel(path))
            return 3
        if any(b < 9 or (10 < b < 32 and b != 13) for b in new_text.encode('utf-8')):
            print('[strip-tickets] FAIL %s 结果含控制字符，拒绝落盘' % rel(path))
            return 4
        changed += 1
        if apply_changes:
            with open(path, 'wb') as fh:
                fh.write(new_text.replace('\n', '\r\n').encode('utf-8'))
    print('[strip-tickets] 字符串前缀：%s%d 处，涉及 %d 个文件'
          % ('清理了 ' if apply_changes else '可清理 ', total, changed))
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    ap.add_argument('--strings', action='store_true',
                    help='清理字符串字面量开头的"编号: "日志前缀（同样只动前缀，不动正文）')
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--idempotence-check', action='store_true')
    ap.add_argument('--root', default=None)
    args = ap.parse_args()

    root = args.root or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    main_root = os.path.join(root, 'app', 'src', 'main', 'java', 'com', 'lovebrain', 'app')
    if not os.path.isdir(main_root):
        print('[strip-tickets] FAIL 找不到 %s' % main_root)
        return 2
    rel = lambda p: os.path.relpath(p, root).replace('\\', '/')

    if args.strings:
        return strings_mode(main_root, args.apply, rel)

    if args.check:
        residue = code_residue(main_root)
        if residue:
            print('[strip-tickets] FAIL 代码里有 %d 处工单编号：' % len(residue))
            for p, no, tok, snippet in residue[:40]:
                print('  %s:%d %s  |  %s' % (rel(p), no, tok, snippet))
            return 1
        strs = string_hits(main_root)
        if strs:
            print('[strip-tickets] FAIL 字符串字面量里有 %d 处工单编号：' % len(strs))
            for p, no, s in strs[:20]:
                print('  %s:%d %s' % (rel(p), no, s[:80]))
            return 1
        ctrl = [rel(p) for p in kt_files(main_root) if control_bytes(p)]
        if ctrl:
            print('[strip-tickets] FAIL 有 %d 个文件含控制字符：%s' % (len(ctrl), ctrl[:5]))
            return 1
        # 注释残留同样拦：早先一版 --check 只看代码，结果整族 F 编号从注释里溜过去了
        left = comment_residue(main_root)
        if left:
            print('[strip-tickets] FAIL 注释里仍有 %d 处工单编号（跑 --apply 清零）' % left)
            return 1
        print('[strip-tickets] PASS app/src/main 的注释、代码与字面量里都没有工单编号')
        return 0

    if args.idempotence_check:
        unstable = []
        for path in kt_files(main_root):
            raw = read(path)
            once, _ = clean_source(raw)
            twice, _ = clean_source(once)
            if once != twice:
                unstable.append(rel(path))
            if len(once.split('\n')) != len(raw.split('\n')):
                unstable.append(rel(path) + ' (行数变化)')
            if any(b < 9 or (10 < b < 32 and b != 13) for b in once.encode('utf-8')):
                unstable.append(rel(path) + ' (引入控制字符)')
        print('[strip-tickets] idempotence: %s'
              % ('stable' if not unstable else 'UNSTABLE %s' % unstable[:5]))
        return 0 if not unstable else 1

    ledger_dir = os.path.join(root, '_temp')
    os.makedirs(ledger_dir, exist_ok=True)
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    ledger_path = os.path.join(ledger_dir, 'strip_ticket_ids-%s.json' % stamp)

    plan, total = [], 0
    for path in kt_files(main_root):
        raw = read(path)
        new_text, n = clean_source(raw)
        if len(new_text.split('\n')) != len(raw.split('\n')):
            print('[strip-tickets] FAIL %s 行数被改动，拒绝落盘' % rel(path))
            return 3
        if any(b < 9 or (10 < b < 32 and b != 13) for b in new_text.encode('utf-8')):
            print('[strip-tickets] FAIL %s 清理结果含控制字符，拒绝落盘' % rel(path))
            return 4
        if n:
            plan.append({'file': rel(path), 'hits': n})
            total += n
        if args.apply and new_text != raw:
            with open(path, 'wb') as fh:
                fh.write(new_text.replace('\n', '\r\n').encode('utf-8'))

    with open(ledger_path, 'wb') as fh:
        fh.write(json.dumps({'mode': 'apply' if args.apply else 'dry-run',
                             'total_hits': total, 'files': plan},
                            ensure_ascii=False, indent=1).encode('utf-8'))
    print('[strip-tickets] %s %d 处编号，%d 个文件，账本：%s'
          % ('清理' if args.apply else '将清理', total, len(plan), rel(ledger_path)))
    sh = string_hits(main_root)
    if sh:
        print('[strip-tickets] 字符串字面量里另有 %d 处编号（只报不改）：' % len(sh))
        for p, no, s in sh[:25]:
            print('  %s:%d %s' % (rel(p), no, s[:72]))
    return 0


if __name__ == '__main__':
    sys.exit(main())
