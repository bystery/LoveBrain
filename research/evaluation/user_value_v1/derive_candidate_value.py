#!/usr/bin/env python3
"""Recompute candidate-level usable-option totals from existing M5 judgments.

This script performs no model calls and no judge calls. It only reads the 24
already-frozen pair judgment JSON files and the two M5 final tally files.

Run from any directory:
  python derive_candidate_value.py

Output:
  candidate-value-derived.json (next to this script)
"""
from __future__ import annotations

import json
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JUDGMENTS = ROOT / "m5-eval"


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def summarize(prefix: str, tally_name: str, lovebrain_arm: str, baseline_arm: str):
    tally = load_json(ROOT / tally_name)
    per_pair = tally["per_pair"]
    if len(per_pair) != 12:
        raise ValueError(f"Expected 12 frozen pairs in {tally_name}; got {len(per_pair)}")

    totals = {
        lovebrain_arm: {"candidate_count": 0, "usable_count": 0},
        baseline_arm: {"candidate_count": 0, "usable_count": 0},
    }

    for index, pair_record in enumerate(per_pair, start=1):
        pair_path = JUDGMENTS / f"{prefix}-{index:02d}.json"
        judgment = load_json(pair_path)
        rotation = pair_record.get("rotation")
        if not isinstance(rotation, dict) or set(rotation) != {"A", "B"}:
            raise ValueError(f"Missing A/B arm rotation in {tally_name}, pair {index}")

        for side in ("A", "B"):
            arm = rotation[side]
            if arm not in totals:
                raise ValueError(f"Unexpected arm {arm!r} in {pair_path}")
            submetric = judgment["submetric2_any_usable"][side]
            usable = submetric.get("usable_candidates", [])
            unusable = submetric.get("unusable_candidates", [])
            totals[arm]["candidate_count"] += len(usable) + len(unusable)
            totals[arm]["usable_count"] += len(usable)

    for arm, values in totals.items():
        count = values["candidate_count"]
        values["usable_rate"] = round(values["usable_count"] / count, 6) if count else None
        values["usable_options_per_case"] = round(values["usable_count"] / 12, 6)

    return tally, totals


def main():
    new_tally, new_totals = summarize("npair2", "m5-final-tally.json", "L2", "B1")
    old_tally, old_totals = summarize("hpair2", "m5h-final-tally.json", "CURRENT", "DIRECT")

    result = {
        "schema_version": "1.0",
        "generated_on": date.today().isoformat(),
        "source": "Existing M5 pair-level judgment JSON files; no model generation and no judge reruns.",
        "method": "candidate_count = len(usable_candidates) + len(unusable_candidates); usable_count = len(usable_candidates), per existing pair judgment JSON.",
        "derived_metric_note": "Supplementary candidate-level totals; the six frozen M5 metrics remain defined by the final tally files.",
        "source_files": {
            "new_generation": ["m5-eval/npair2-01.json through npair2-12.json", "m5-final-tally.json"],
            "historical_fixed_outputs": ["m5-eval/hpair2-01.json through hpair2-12.json", "m5h-final-tally.json"],
        },
        "cohorts": {
            "new_generation": {
                "pairs": 12,
                "lovebrain": new_totals["L2"],
                "baseline": new_totals["B1"],
                "group_preference": new_tally["aggregate"]["submetric6_group_preference"],
                "first_usable": new_tally["aggregate"]["submetric1_first_usable"],
                "directions": new_tally["aggregate"]["submetric3_direction_coverage"],
                "duplicates": new_tally["aggregate"]["submetric4_duplicate_rate"],
                "backup_severe_errors": new_tally["aggregate"]["submetric5_backup_severe_error"],
            },
            "historical_fixed_outputs": {
                "pairs": 12,
                "lovebrain": old_totals["CURRENT"],
                "baseline": old_totals["DIRECT"],
                "group_preference": old_tally["aggregate"]["submetric6_group_preference"],
                "first_usable": old_tally["aggregate"]["submetric1_first_usable"],
                "directions": old_tally["aggregate"]["submetric3_direction_coverage"],
                "duplicates": old_tally["aggregate"]["submetric4_duplicate_rate"],
                "backup_severe_errors": old_tally["aggregate"]["submetric5_backup_severe_error"],
            },
        },
    }

    for cohort in result["cohorts"].values():
        love = cohort["lovebrain"]["usable_count"]
        base = cohort["baseline"]["usable_count"]
        cohort["usable_option_multiplier"] = round(love / base, 6) if base else None

    output = ROOT / "candidate-value-derived.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {output}")
    for name, cohort in result["cohorts"].items():
        l = cohort["lovebrain"]
        b = cohort["baseline"]
        print(f"{name}: LoveBrain {l['usable_count']}/{l['candidate_count']} vs baseline {b['usable_count']}/{b['candidate_count']}; usable option multiplier={cohort['usable_option_multiplier']}x")


if __name__ == "__main__":
    main()
