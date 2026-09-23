#!/usr/bin/env bash
#
# scripts/write_upgrade_fixture.sh
#
# Writes the REAL upgrade fixture with the OLD (v1.3.1) app installed.
#
# Re-audit 2026-09-23 §3/§4: "没有真实'写入升级夹具'; 注释写了, 命令没有做" —
# the old workflow only had a comment (`# Start old version, write fixture data`)
# followed by `adb shell am start … || true`. This script writes genuine
# user-visible knowledge-base content into the installed old app's private data
# directory and then reads it back off the device to prove the write happened.
#
# What it produces:
#   * markdown content appended to the KB files the app itself parses
#     (memory/raw_chat.md, understand/me.md, moment/plan.md)
#   * a standalone memory/upgrade_fixture.txt marker file
#   * fixtures/upgrade-fixture-manifest.txt — path|sentinel|pre-upgrade schema,
#     consumed by scripts/assert_upgrade_state.sh after the覆盖安装
#   * fixtures/pre-upgrade-tree.txt — the on-device data listing before upgrade
#
# Usage:
#   bash scripts/write_upgrade_fixture.sh [--package com.lovebrain.app]
#        [--out-dir fixtures] [--sentinel TEXT] [--kb-name default]
#
# Exit codes: 0 fixture written and verified, 1 failure, 2 usage/tooling.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"
# shellcheck source=lib/device_lib.sh
. "$SCRIPT_DIR/lib/device_lib.sh"

OUT_DIR="fixtures"
SENTINEL="LOVEBRAIN-UPGRADE-FIXTURE-2026-09-23"
KB_NAME=""

while [ $# -gt 0 ]; do
  case "$1" in
    --package) PKG="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --sentinel) SENTINEL="$2"; shift 2 ;;
    --kb-name) KB_NAME="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

case "$SENTINEL" in
  *\"* | *\'* | *" "*) die "the sentinel must not contain quotes or spaces: $SENTINEL" ;;
esac

DATA_ROOT="/data/data/$PKG"
mkdir -p "$OUT_DIR"
MANIFEST="$OUT_DIR/upgrade-fixture-manifest.txt"
TREE="$OUT_DIR/pre-upgrade-tree.txt"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

init_device_mode

# ── the old app must actually have created its data directory ────────────────
if [ "$(device_yes_no "test -d files/knowledge && echo ok")" != "YES" ]; then
  die "files/knowledge does not exist inside $DATA_ROOT — the old app never completed a first run, so there is no old data to upgrade. Start it and wait for it to initialise."
fi

# ── pick (or create) the knowledge base to seed ──────────────────────────────
KB_LIST="$(dev_capture "ls -1 files/knowledge")"
if [ -z "$KB_LIST" ]; then
  die "the old app's files/knowledge directory is empty — first-run seeding did not happen"
fi
if [ -n "$KB_NAME" ]; then
  printf '%s\n' "$KB_LIST" | grep -qxF "$KB_NAME" ||
    die "knowledge base '$KB_NAME' not present in the old app data (found: $(printf '%s' "$KB_LIST" | tr '\n' ' '))"
else
  KB_NAME="$(first_nonempty_line "$KB_LIST")"
  [ -n "$KB_NAME" ] || die "could not determine a knowledge base name from the old app data"
fi
KB_DIR="files/knowledge/$KB_NAME"
log "seeding knowledge base '$KB_NAME' with upgrade fixture data"

# Record the pre-upgrade schema state so the post-upgrade assertion can prove a
# migration actually occurred (v1.3.1 predates the unified .schema_version file).
PRE_SCHEMA="$(dev_capture \"cat $KB_DIR/.schema_version\")"
PRE_SCHEMA="$(printf '%s' "$PRE_SCHEMA" | tr -d ' \r\n')"
if [ -z "$PRE_SCHEMA" ]; then
  PRE_SCHEMA="absent"
fi
LEGACY_V2="$(device_yes_no "test -f $KB_DIR/.migrated_v2 && echo ok")"
LEGACY_V3="$(device_yes_no "test -f $KB_DIR/.migrated_plan_v3 && echo ok")"
log "pre-upgrade schema state: .schema_version=$PRE_SCHEMA .migrated_v2=$LEGACY_V2 .migrated_plan_v3=$LEGACY_V3"

# ── snapshot the data tree before upgrading ─────────────────────────────────
dev_capture "find files -type f | sort" >"$TREE"
[ -s "$TREE" ] || die "could not list the old app's files/ tree — no fixture evidence to keep"
log "pre-upgrade tree: $(wc -l <"$TREE" | tr -d ' ') file(s) -> $TREE"

: >"$MANIFEST"

write_fixture_file() {
  # write_fixture_file <rel-path> <generated-local-file> <description>
  local rel="$1" local_file="$2" desc="$3"
  if [ "$(device_yes_no "test -f '$rel' && echo ok")" = "YES" ]; then
    dev_capture \"cat $rel\" >"$WORK/existing.txt"
  else
    : >"$WORK/existing.txt"
  fi
  cat "$WORK/existing.txt" "$local_file" >"$WORK/combined.txt"
  device_push_mode "$WORK/combined.txt" "$rel"
  # Prove it landed: read it back from the device, not from the local copy.
  if [ "$(device_yes_no "grep -q $SENTINEL $rel && echo ok")" != "YES" ]; then
    die "fixture write could not be verified on the device: $SENTINEL is not present in $rel"
  fi
  printf '%s|%s|%s\n' "$rel" "$SENTINEL" "$desc" >>"$MANIFEST"
  ok "fixture written and read back: $rel ($desc)"
}

cat >"$WORK/chat.md" <<EOF

## $SENTINEL (written by the pre-upgrade fixture, must survive the覆盖安装)
- [2026-09-23 21:00] 她:  fixture-marker-A1 我们上次说到哪儿了
- [2026-09-23 21:01] 我:  fixture-marker-B2 在整理知识库里的旧对话
EOF

cat >"$WORK/me.md" <<EOF

- 说话风格：直接、爱用反问（fixture-marker-C3 $SENTINEL）
EOF

cat >"$WORK/plan.md" <<EOF

## 持续意图（fixture-marker-D4 $SENTINEL）
- [ongoing] 把这次谈话里关于见家长的安排确定下来
EOF

cat >"$WORK/fixture.txt" <<EOF
sentinel=$SENTINEL
written_by=scripts/write_upgrade_fixture.sh
package=$PKG
knowledge_base=$KB_NAME
pre_schema_version=$PRE_SCHEMA
EOF

write_fixture_file "$KB_DIR/memory/raw_chat.md" "$WORK/chat.md" "old chat archive"
write_fixture_file "$KB_DIR/understand/me.md" "$WORK/me.md" "profile of the user"
write_fixture_file "$KB_DIR/moment/plan.md" "$WORK/plan.md" "ongoing intent plan"
write_fixture_file "$KB_DIR/memory/upgrade_fixture.txt" "$WORK/fixture.txt" "standalone fixture marker"

# The plan file must be readable by the app's own parser: a non-empty kb.json
# is part of the fixture contract.
if [ "$(device_yes_no "test -s $KB_DIR/kb.json && echo ok")" != "YES" ]; then
  die "$KB_DIR/kb.json is missing or empty in the old app data — the fixture KB would not load after the upgrade"
fi

printf 'pre_schema_version|%s|\n' "$PRE_SCHEMA" >>"$MANIFEST"
printf 'legacy_marker_v2|%s|\n' "$LEGACY_V2" >>"$MANIFEST"
printf 'legacy_marker_v3|%s|\n' "$LEGACY_V3" >>"$MANIFEST"
printf 'kb_name|%s|\n' "$KB_NAME" >>"$MANIFEST"

FIXTURE_COUNT="$(grep -c '|' "$MANIFEST")" || FIXTURE_COUNT=0
[ "$FIXTURE_COUNT" -ge 4 ] || die "the fixture manifest has fewer than 4 entries ($FIXTURE_COUNT): $MANIFEST"

dev_capture "find files -type f | sort" >"$OUT_DIR/post-fixture-tree.txt"
log "fixture manifest: $MANIFEST"
ok "upgrade fixture written to the old app data and verified on-device"
