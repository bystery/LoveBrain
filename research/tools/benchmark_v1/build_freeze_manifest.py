# -*- coding: utf-8 -*-
"""Benchmark V1 Step 3 · Freeze manifest builder (2026-11-10).

Computes three-layer hashes for the full 240-story benchmark:
  Layer 1: per-file SHA-256 (episode.json, reference.json, timeline.json, audit.md)
  Layer 2: per-episode composite hash (concatenation of 4 file hashes)
  Layer 3: dataset manifest hash (over all episode composite hashes + allocation + scorer + arm registry)

Writes DATASET_V1_FROZEN_MANIFEST.json to phase3/.

For HOLDOUT stories, hashes are computed from vault paths (outside repo);
only the hash values enter the repo manifest (content stays in vault).
"""
import argparse
import csv
import hashlib
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(_HERE))
PHASE3 = os.path.join(REPO, "refs", "benchmark_v1_20261107", "phase3")
STORIES = os.path.join(REPO, "benchmark", "stories_v2")
VAULT = "D:/lovebrain-arena_holdout_vault/wave6_holdout"

FREEZE_FILES = ("episode.json", "reference.json", "timeline.json", "audit.md")


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def sha256_str(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def find_story_dir(eid, split, allocation_rows, vault=None):
    """Find the physical directory for a story."""
    # Check common wave directories based on split
    if split == "DEV":
        waves = ["wave4_dev", "wave3", "wave3_ext", "wave2", "wave1",
                 "wave1_ext", "stage1", "stage2", "stage3", "stage4", "stageD"]
    elif split == "SELECTION":
        waves = ["wave5_selection"]
    elif split == "HOLDOUT":
        # HOLDOUT is in the vault
        vd = os.path.join(vault or VAULT, eid)
        if os.path.isdir(vd):
            return vd
        return None
    else:
        waves = []

    for w in waves:
        d = os.path.join(STORIES, w, eid)
        if os.path.isdir(d):
            return d

    # Search all subdirs as fallback
    for w in sorted(os.listdir(STORIES)):
        d = os.path.join(STORIES, w, eid)
        if os.path.isdir(d):
            return d
    return None


def main():
    ap = argparse.ArgumentParser(description="Build DATASET_V1_FROZEN_MANIFEST.json")
    ap.add_argument("--allocation", default=os.path.join(
        PHASE3, "DATASET_V1_ALLOCATION_V3.csv"))
    ap.add_argument("--out", default=os.path.join(
        PHASE3, "DATASET_V1_FROZEN_MANIFEST.json"))
    ap.add_argument("--arm-registry", default=os.path.join(
        PHASE3, "ARM_REGISTRY_FROZEN.json"))
    ap.add_argument("--scorer", default=os.path.join(
        _HERE, "universal_scorer.py"))
    ap.add_argument("--holdout-vault", default=VAULT)
    args = ap.parse_args()

    vault = args.holdout_vault

    # Read allocation
    rows = []
    with open(args.allocation, "r", encoding="utf-8-sig") as f:
        r = csv.DictReader(f)
        for row in r:
            if row.get("split") in ("DEV", "SELECTION", "HOLDOUT"):
                rows.append(row)

    print("Freezing %d stories (DEV+SELECTION+HOLDOUT)..." % len(rows))

    episodes = {}
    missing = []
    split_counts = {"DEV": 0, "SELECTION": 0, "HOLDOUT": 0}

    for row in rows:
        eid = row["episode_id"]
        split = row["split"]
        topic = row.get("primary_topic", "")
        lineage = row.get("lineage_group_id", "")
        twin_group = row.get("twin_group", "")
        twin_role = row.get("twin_role", "")
        turns = row.get("turns", "12")

        sdir = find_story_dir(eid, split, rows, vault=vault)
        if sdir is None:
            missing.append((eid, split))
            continue

        split_counts[split] += 1

        # Layer 1: per-file hashes
        file_hashes = {}
        for fn in FREEZE_FILES:
            fp = os.path.join(sdir, fn)
            if os.path.isfile(fp):
                file_hashes[fn] = sha256_file(fp)
            else:
                file_hashes[fn] = None
                if fn != "audit.md":  # audit.md may not exist for HOLDOUT yet
                    missing.append((eid, "%s/%s" % (split, fn)))

        # Layer 2: episode composite hash
        composite_input = "|".join(
            "%s:%s" % (fn, file_hashes.get(fn) or "MISSING")
            for fn in FREEZE_FILES)
        composite = sha256_str(composite_input)

        episodes[eid] = {
            "split": split,
            "topic": topic,
            "lineage_group_id": lineage,
            "twin_group": twin_group,
            "twin_role": twin_role,
            "turns": int(turns) if turns.isdigit() else 12,
            "story_dir": sdir.replace(REPO + os.sep, "") if sdir.startswith(REPO) else sdir,
            "file_hashes": file_hashes,
            "composite_hash": composite,
        }

    # Layer 3: dataset manifest hash
    all_composites = "|".join(
        "%s:%s" % (eid, episodes[eid]["composite_hash"])
        for eid in sorted(episodes.keys()))
    allocation_hash = sha256_file(args.allocation)
    arm_registry_hash = sha256_file(args.arm_registry) if os.path.isfile(args.arm_registry) else None
    scorer_hash = sha256_file(args.scorer) if os.path.isfile(args.scorer) else None

    dataset_hash = sha256_str(
        "|".join([all_composites, allocation_hash,
                  arm_registry_hash or "", scorer_hash or ""]))

    manifest = {
        "manifest_version": "DATASET_V1_FROZEN_v1",
        "frozen_at": "2026-11-10",
        "freeze_scope": "Phase 3 Step 3 — Benchmark V1 full freeze (DEV 80 + SELECTION 40 + HOLDOUT 120 = 240)",
        "split_counts": split_counts,
        "total_episodes": len(episodes),
        "missing_episodes": [{"eid": m[0], "detail": m[1]} for m in missing],
        "layer_3_dataset_hash": dataset_hash,
        "component_hashes": {
            "allocation_csv": allocation_hash,
            "arm_registry_frozen": arm_registry_hash,
            "universal_scorer": scorer_hash,
        },
        "episodes": episodes,
        "note": "HOLDOUT hashes computed from vault (D:/lovebrain-arena_holdout_vault/); "
                "only hash values enter repo, content stays in vault.",
    }

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)

    print("Frozen: %d episodes (DEV=%d, SEL=%d, HOLDOUT=%d)" % (
        len(episodes), split_counts["DEV"], split_counts["SELECTION"],
        split_counts["HOLDOUT"]))
    print("Missing: %d" % len(missing))
    if missing:
        for m in missing[:10]:
            print("  MISSING: %s (%s)" % (m[0], m[1]))
    print("Dataset hash: %s" % dataset_hash[:32])
    print("-> %s" % args.out)
    return 0 if not missing else 1


if __name__ == "__main__":
    sys.exit(main() or 0)
