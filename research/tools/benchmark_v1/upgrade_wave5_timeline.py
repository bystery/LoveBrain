# -*- coding: utf-8 -*-
"""Convert wave5_selection timeline.json from simplified format to validator-expected format.

Wave5 timelines use: {episode_id, events:[{turn,ts,event}]}
Validator expects:   {story_id, events:[{event_id,source_turn,description}], turn_clocks:[...], message_index:[...], revisions:[]}

This script reads episode.json to derive turn_clocks and message_index,
and remaps events to the expected schema. Zero model calls.
"""
import json
import os
import sys

BASE = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__)))), "benchmark", "stories_v2", "wave5_selection")


def convert_one(eid):
    ep_path = os.path.join(BASE, eid, "episode.json")
    tl_path = os.path.join(BASE, eid, "timeline.json")
    if not os.path.isfile(ep_path) or not os.path.isfile(tl_path):
        return False, "missing files"
    ep = json.load(open(ep_path, encoding="utf-8"))
    tl = json.load(open(tl_path, encoding="utf-8"))

    # Already in target format?
    if "turn_clocks" in tl and "message_index" in tl and "story_id" in tl:
        return False, "already target format"

    sid = ep.get("episode_id", eid)
    turns = ep.get("turns", [])

    # turn_clocks: one per turn, from input.clock
    turn_clocks = []
    for t in turns:
        clk = t.get("input", {}).get("clock", "")
        turn_clocks.append(clk)

    # message_index: one entry per message across all turns
    message_index = []
    for t in turns:
        cid = t.get("case_id", "")
        msgs = t.get("input", {}).get("messages", [])
        for j, m in enumerate(msgs):
            message_index.append({
                "case_id": cid,
                "message_id": "m%d" % j,
                "speaker": m.get("role", ""),
                "quote": m.get("text", "") or m.get("content", ""),
                "ts": m.get("ts", ""),
            })

    # events: remap {turn, ts, event} -> {event_id, source_turn, description}
    old_events = tl.get("events", [])
    events = []
    for i, ev in enumerate(old_events):
        events.append({
            "event_id": "EV%02d" % (i + 1),
            "source_turn": ev.get("turn", ""),
            "description": ev.get("event", ""),
        })

    new_tl = {
        "story_id": sid,
        "events": events,
        "turn_clocks": turn_clocks,
        "message_index": message_index,
        "revisions": [],
    }

    # Backup original
    bak_path = tl_path + ".wave5orig"
    if not os.path.isfile(bak_path):
        with open(bak_path, "w", encoding="utf-8") as f:
            json.dump(tl, f, ensure_ascii=False, indent=1)

    with open(tl_path, "w", encoding="utf-8") as f:
        json.dump(new_tl, f, ensure_ascii=False, indent=1)

    return True, "converted: %d turns, %d msgs, %d events" % (
        len(turn_clocks), len(message_index), len(events))


def main():
    dirs = sorted([d for d in os.listdir(BASE)
                   if d.startswith("E3") and os.path.isdir(os.path.join(BASE, d))])
    converted = 0
    skipped = 0
    for eid in dirs:
        ok, msg = convert_one(eid)
        if ok:
            converted += 1
            print("  %s: %s" % (eid, msg))
        else:
            skipped += 1
            print("  %s: SKIP (%s)" % (eid, msg))
    print("\n=== %d converted, %d skipped ===" % (converted, skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
