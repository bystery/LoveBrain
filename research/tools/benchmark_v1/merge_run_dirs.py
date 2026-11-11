#!/usr/bin/env python3
"""Merge multiple single-arm run directories into one unified run directory
for aggregate_four_arm_phase3.py consumption."""
import argparse
import json
import os
import shutil
import sys


def main():
    ap = argparse.ArgumentParser(description="Merge single-arm run dirs into one")
    ap.add_argument("--inputs", required=True,
                    help="Comma-separated list of run directories")
    ap.add_argument("--out", required=True,
                    help="Output merged run directory")
    args = ap.parse_args()

    inputs = [d.strip() for d in args.inputs.split(",") if d.strip()]
    os.makedirs(args.out, exist_ok=True)

    # Copy provider_check and plan from first input
    for fn in ["provider_check.json", "API_COMPATIBILITY_REPORT.md", "plan.json"]:
        for inp in inputs:
            src = os.path.join(inp, fn)
            if os.path.isfile(src):
                shutil.copy2(src, os.path.join(args.out, fn))
                break

    total_arms = 0
    total_episodes = 0
    for inp in inputs:
        # Each input has <ARM>/<episode>/ structure
        for arm_dir in sorted(os.listdir(inp)):
            arm_path = os.path.join(inp, arm_dir)
            if not os.path.isdir(arm_path):
                continue
            # Skip non-arm dirs
            if arm_dir.startswith("_") or arm_dir in ("checkpoint.json",):
                continue

            dst_arm = os.path.join(args.out, arm_dir)
            os.makedirs(dst_arm, exist_ok=True)

            for eid in sorted(os.listdir(arm_path)):
                src_ep = os.path.join(arm_path, eid)
                dst_ep = os.path.join(dst_arm, eid)
                if not os.path.isdir(src_ep):
                    continue
                if os.path.isdir(dst_ep):
                    # Already exists (from another run), skip
                    continue
                shutil.copytree(src_ep, dst_ep)
                total_episodes += 1
            total_arms += 1

    print(f"Merged {total_arms} arms, {total_episodes} episodes from {len(inputs)} dirs -> {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
