# -*- coding: utf-8 -*-
"""check_name_uniqueness.py — 跨篇主角名唯一性检查（Phase 3 批次工具，2026-11-09）。

背景：Phase 2 审计发现跨作者人名重名 12 组（C1/C2 清单；OLD_STORY_FINAL_DISPOSITION §6-4）。
批 2/批 3 简报启用唯一人名池（NAME_POOL_V1.json，HER/ME 逐篇预分配）；本工具是机械复核件：
对指定 wave 目录逐篇读 persona.her/me 名（仅 persona 元数据，不读对话正文），
报告 wave 内重复与对全库其他 wave 主角名的碰撞。

用法：
  python -I tools/benchmark_v1/check_name_uniqueness.py --wave wave5_selection
  python -I tools/benchmark_v1/check_name_uniqueness.py --wave wave6_holdout --json out.json

退出码：0 = 无重复/无碰撞；1 = 有违规。
纯标准库；零模型调用；只读工具（绝不写被查对象）。
"""
import argparse
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
STORIES = os.path.join(REPO, "benchmark", "stories_v2")
SKIP_DIRS = {"_templates", "GOLD_SIDECAR"}


def persona_names(episode_path):
    try:
        with open(episode_path, encoding="utf-8") as f:
            d = json.load(f)
    except Exception as e:  # noqa: BLE001
        return None, "unparseable: %s" % e
    per = d.get("persona") or {}
    out = {}
    for side in ("her", "me"):
        v = per.get(side)
        n = v.get("name") if isinstance(v, dict) else v
        out[side] = n if isinstance(n, str) else None
    return out, None


def main():
    ap = argparse.ArgumentParser(description="wave 主角名唯一性检查（只读）")
    ap.add_argument("--wave", required=True, help="被查 wave 目录名（如 wave5_selection）")
    ap.add_argument("--json", default=None, help="可选：完整报告输出路径")
    args = ap.parse_args()

    wdir = os.path.join(STORIES, args.wave)
    if not os.path.isdir(wdir):
        print("wave 目录不存在: %s" % wdir)
        return 2
    # wave 内主角名（同 lineage_group_id 的孪生对视为同一单元：组内同名列合法）
    wave_names = {}
    ep_group = {}
    problems, warnings = [], []
    for ep in sorted(os.listdir(wdir)):
        p = os.path.join(wdir, ep, "episode.json")
        if not os.path.isfile(p):
            continue
        names, err = persona_names(p)
        exp_p = os.path.join(wdir, ep, "exposure.json")
        if os.path.isfile(exp_p):
            try:
                ep_group[ep] = (json.load(open(exp_p, encoding="utf-8"))
                                .get("lineage_group_id") or ep)
            except Exception:  # noqa: BLE001
                ep_group[ep] = ep
        else:
            ep_group[ep] = ep
        if err:
            problems.append({"episode": ep, "kind": "unparseable", "detail": err})
            continue
        for side, n in names.items():
            if not n:
                problems.append({"episode": ep, "kind": "missing_name", "side": side})
                continue
            wave_names.setdefault(n, []).append("%s.%s" % (ep, side))
    # 全库其他 wave 主角名
    other = {}
    for wave in sorted(os.listdir(STORIES)):
        if wave == args.wave or wave.startswith("_") or wave in SKIP_DIRS:
            continue
        w2 = os.path.join(STORIES, wave)
        if not os.path.isdir(w2):
            continue
        for ep in sorted(os.listdir(w2)):
            p = os.path.join(w2, ep, "episode.json")
            if not os.path.isfile(p):
                continue
            names, _ = persona_names(p)
            if not names:
                continue
            for n in names.values():
                if n:
                    other.setdefault(n, []).append("%s/%s" % (wave, ep))
    def same_group(uses):
        gs = {ep_group.get(u.split(".")[0], u.split(".")[0]) for u in uses}
        return len(gs) == 1
    for n, where in sorted(wave_names.items()):
        if len(where) > 1 and not same_group(where):
            problems.append({"kind": "dup_in_wave", "name": n, "used_by": where})
        if n in other:
            problems.append({"kind": "clash_with_other_waves", "name": n,
                             "used_by_wave": where, "used_by_others": other[n]})
    rep = {"wave": args.wave, "unique_names_in_wave": len(wave_names),
           "n_problems": len(problems), "problems": problems, "warnings": warnings}
    if args.json:
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(rep, f, ensure_ascii=False, indent=1)
    if problems:
        print("check_name_uniqueness: FAIL wave=%s problems=%d" % (args.wave, len(problems)))
        for p in problems:
            print(" -", json.dumps(p, ensure_ascii=False)[:200])
        return 1
    print("check_name_uniqueness: OK wave=%s unique_names=%d（wave 内 0 重复，全库 0 碰撞）"
          % (args.wave, len(wave_names)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
