#!/usr/bin/env bash
# 包依赖方向的实测器：把"哪一层 import 了不该 import 的东西"逐条打出来。
#
# 存在的理由：PackageDependencyTest 里的基线数字必须是**某一次真实统计**的结果，
# 不能凭印象写（复核 §9 第 7 条）。改完债务跑一遍，把输出贴回测试里，
# 棘轮才会跟着现实走。
#
# Usage: bash scripts/package_deps_report.sh [--count]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec ${PYTHON:-python3} "$ROOT/scripts/package_deps_report.py" "$@"
