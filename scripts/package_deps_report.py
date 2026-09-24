"""实测 app/src/main 的跨层 import，供 PackageDependencyTest 的基线使用。

规则与 PackageDependencyTest 里那份一一对应。默认按"文件 -> 违规 import 列表"
打成 Kotlin 源码片段，直接可以贴进测试。--count 只给总数。
"""
import argparse
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    'app', 'src', 'main', 'java', 'com', 'lovebrain', 'app')

FORBIDDEN = {
    'model': ['android.', 'androidx.', 'com.lovebrain.app.data.', 'com.lovebrain.app.domain.'],
    'domain': ['android.', 'androidx.', 'com.lovebrain.app.ui.', 'com.lovebrain.app.viewmodel.',
               'com.lovebrain.app.data.'],
    'ui': ['com.lovebrain.app.data.', 'java.io.File'],
    'viewmodel': ['java.io.File'],
}


def scan():
    found = {}
    for pkg, prefixes in FORBIDDEN.items():
        base = os.path.join(ROOT, pkg)
        if not os.path.isdir(base):
            continue
        for dirpath, _, files in os.walk(base):
            for fn in files:
                if not fn.endswith('.kt'):
                    continue
                path = os.path.join(dirpath, fn)
                for line in open(path, encoding='utf-8', errors='replace'):
                    t = line.strip()
                    if not t.startswith('import '):
                        continue
                    name = t[len('import '):].split()[0]
                    if any(name.startswith(p) for p in prefixes):
                        rel = pkg + '/' + os.path.relpath(path, base).replace(os.sep, '/')
                        found.setdefault(rel, set()).add(name)
    return {k: sorted(v) for k, v in found.items()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--count', action='store_true')
    args = ap.parse_args()
    found = scan()
    total = sum(len(v) for v in found.values())
    if args.count:
        print(total)
        return 0
    for k in sorted(found):
        print('        "%s" to listOf(%s),' % (k, ', '.join('"%s"' % x for x in found[k])))
    print('        // 合计 %d 条越界 import，分布在 %d 个文件' % (total, len(found)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
