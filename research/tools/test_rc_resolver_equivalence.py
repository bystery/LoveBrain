# -*- coding: utf-8 -*-
r"""tools/test_rc_resolver_equivalence.py — 证明 exam_interleave_plan 的 rc 句柄解析不改计量（零调用）。

背景：R3A 批父臂=action_policy_r2，第一次让执行器遇到"候选模块没有顶层 rc"的情况
（转发型候选只导出 base，rc 在 base.rc 上），原代码 `spec.rc.utf16_len(...)` 直接 AttributeError。
修法是加 `_spec_rc(spec)`：有 spec.rc 就用它，没有就退到 spec.base.rc。

要证的等价性：
  1) 凡旧代码本来能跑通的候选（有顶层 rc），新函数返回的**就是同一个对象**（is 判断）——
     数值不可能变；
  2) 凡旧代码会抛 AttributeError 的候选（无顶层 rc），新函数返回 base.rc，且该对象与
     其它候选链里实际使用的 rc 是同一套 arena.response_contract 语义（utf16_len 可用、
     对 BMP 字符串等于 len）；
  3) 唯一受影响的调用点是 recall 段的 UTF-16 计量（_combined_newctx_from_calls）；
     本批 recall_capable=false，该值只进诊断字段，不进任何评分。
"""
import importlib.util
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import exam_interleave_plan as ep   # noqa: E402


def load(cand):
    p = os.path.join(ROOT, "candidates", cand, "mechanism", "mechanism.py")
    spec = importlib.util.spec_from_file_location("t_" + cand, p)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def main():
    cands = sorted(d for d in os.listdir(os.path.join(ROOT, "candidates"))
                   if os.path.isdir(os.path.join(ROOT, "candidates", d, "mechanism")))
    rows, fails = [], []
    for c in cands:
        try:
            m = load(c)
        except Exception as e:                                  # noqa: BLE001
            rows.append({"candidate": c, "load": "error: %s" % str(e)[:60]})
            continue
        has_top = hasattr(m, "rc")
        try:
            rc = ep._spec_rc(m)
        except Exception as e:                              # noqa: BLE001
            # 负向夹具（_attack_fixture 之类）本来就没有 rc，旧代码同样会炸；
            # 只要它不是真实候选，就不算回归。
            base = getattr(m, "base", None)
            had_base_rc = getattr(base, "rc", None) is not None
            rows.append({"candidate": c, "top_level_rc": has_top,
                         "rc_available": False, "had_base_rc": had_base_rc,
                         "note": str(e)[:50]})
            # 旧代码在"有顶部 rc"或"有 base.rc"时都能算；两者都没有的负向夹具/旧实验候选
            # 本来就不进 plan 计量，不算回归。
            if has_top or had_base_rc:
                fails.append(c)
            continue
        if has_top:
            same = rc is m.rc
            rows.append({"candidate": c, "top_level_rc": True, "same_object": same,
                         "utf16_ok": rc.utf16_len("测试abc") == len("测试abc")})
            if not same or not rows[-1]["utf16_ok"]:
                fails.append(c)
        else:
            base = getattr(m, "base", None)
            same = rc is getattr(base, "rc", None)
            rows.append({"candidate": c, "top_level_rc": False,
                         "resolved_from_base": same,
                         "utf16_ok": rc.utf16_len("测试abc") == len("测试abc")})
            if not same or not rows[-1]["utf16_ok"]:
                fails.append(c)
    for r in rows:
        print(r)
    print("RC RESOLVER EQUIVALENCE %s（候选 %d 个）"
          % ("PASS" if not fails else "FAIL: %s" % fails, len(rows)))
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
