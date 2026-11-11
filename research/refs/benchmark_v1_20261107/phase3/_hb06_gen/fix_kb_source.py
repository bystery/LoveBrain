#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fix KB-sourced atoms: change source.kind to 'initial_kb' for atoms
whose quote is NOT in any episode message but IS in initial_kb/persona.
Also set case_id to "seed" for KB atoms and remove message_id.
"""
import json, os, re

VAULT = r"D:/lovebrain-arena_holdout_vault/wave6_holdout"

def get_kb_corpus(ep):
    texts = []
    for side in ("me", "her"):
        for item in ep.get("initial_kb", {}).get(side, []):
            texts.append(item.get("value", ""))
    for k in ("warmth", "style", "lessons"):
        v = ep.get("initial_kb", {}).get(k)
        if v:
            texts.append(v)
    for side in ("her", "me"):
        for item in ep.get("persona", {}).get(side, {}).get("traits", []):
            texts.append(item)
        for item in ep.get("persona", {}).get(side, {}).get("boundaries", []):
            texts.append(item)
    return "\n".join(texts)

def get_msg_corpus(ep):
    texts = []
    for t in ep["turns"]:
        for m in t["input"]["messages"]:
            texts.append(m["text"])
    return "\n".join(texts)

EIDS = [f"E{450+i}" for i in range(1,11)]
total_fixed = 0
for eid in EIDS:
    base = os.path.join(VAULT, eid)
    ep = json.load(open(os.path.join(base, "episode.json"), encoding="utf-8"))
    ref = json.load(open(os.path.join(base, "reference.json"), encoding="utf-8"))
    kb_corpus = get_kb_corpus(ep)
    msg_corpus = get_msg_corpus(ep)
    fixed = 0
    for cid, card in ref["turns"].items():
        for atom in card.get("required_memory_atoms", []):
            src = atom.get("source", {})
            quote = src.get("quote", "")
            # If quote is NOT in messages but IS in KB, mark as initial_kb
            if quote and quote not in msg_corpus and quote in kb_corpus:
                if src.get("kind") != "initial_kb":
                    src["kind"] = "initial_kb"
                    src["case_id"] = "seed"
                    # Keep message_id as "seed" for KB atoms
                    src["message_id"] = "seed"
                    fixed += 1
            # Also fix atoms that quote KB but were marked as message
            elif quote and quote not in msg_corpus and src.get("kind") == "message":
                # Check if quote is a substring of KB text
                # Try partial match
                found_in_kb = False
                for kb_line in kb_corpus.split("\n"):
                    if quote in kb_line:
                        found_in_kb = True
                        break
                if found_in_kb:
                    src["kind"] = "initial_kb"
                    src["case_id"] = "seed"
                    src["message_id"] = "seed"
                    fixed += 1
    if fixed:
        with open(os.path.join(base, "reference.json"), "w", encoding="utf-8") as f:
            json.dump(ref, f, ensure_ascii=False, indent=1)
    total_fixed += fixed
    print(f"{eid}: fixed {fixed} KB-sourced atoms")
print(f"\nTotal fixed: {total_fixed}")
