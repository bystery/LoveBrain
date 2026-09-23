#!/usr/bin/env bash
#
# scripts/resolve_release_apk.sh
#
# Hand the next step the ACTUAL path of the release APK Gradle produced.
#
# Why this exists: `:app:assembleRelease` names its output differently depending
# on whether a release keystore was configured —
#   signed     -> app/build/outputs/apk/release/app-release.apk
#   no keystore-> app/build/outputs/apk/release/app-release-unsigned.apk
# Every workflow step that hard-coded app-release.apk therefore fails with
# "file does not exist" on a keystore-less runner, and a step that did
# `cp app-release*.apk …` would silently pick the wrong artifact. The audited
# pipeline hid exactly this class of problem behind permissive fallbacks, so this
# script resolves the path explicitly and fails loudly when the answer is
# ambiguous or when a caller demanded a signed candidate.
#
# Usage:
#   bash scripts/resolve_release_apk.sh [--dir app/build/outputs/apk/release]
#        [--path-file <out>] [--require-signed] [--allow-unsigned]
#
# Exit codes:
#   0 a single candidate was resolved (and, if asked, is signed)
#   1 no APK / more than one / a signed APK was required and it is unsigned
#   2 usage or tooling (no SDK build-tools, …)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

DIR="app/build/outputs/apk/release"
PATH_FILE=""
REQUIRE_SIGNED=0
ALLOW_UNSIGNED=0

while [ $# -gt 0 ]; do
  case "$1" in
    --dir) DIR="$2"; shift 2 ;;
    --path-file) PATH_FILE="$2"; shift 2 ;;
    --require-signed) REQUIRE_SIGNED=1; shift ;;
    --allow-unsigned) ALLOW_UNSIGNED=1; shift ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

if [ "$REQUIRE_SIGNED" -eq 1 ] && [ "$ALLOW_UNSIGNED" -eq 1 ]; then
  die_usage "--require-signed and --allow-unsigned are mutually exclusive"
fi
if [ "$REQUIRE_SIGNED" -eq 0 ] && [ "$ALLOW_UNSIGNED" -eq 0 ]; then
  die_usage "say what this artifact is for: --require-signed (release/upgrade gates) or --allow-unsigned (R8 compile check)"
fi

require_dir "$DIR" "release APK output directory (did ./gradlew :app:assembleRelease run?)"

APKS="$(find "$DIR" -maxdepth 1 -type f -name '*.apk' | sort)"
COUNT="$(printf '%s\n' "$APKS" | grep -c '.' )" || COUNT=0
if [ "$COUNT" -eq 0 ]; then
  printf '%s  FAIL no .apk found under %s\n' "$GATE_LOG_PREFIX" "$DIR" >&2
  printf '%s       :app:assembleRelease did not produce an artifact — look at the build step above;\n' "$GATE_LOG_PREFIX" >&2
  printf '%s       the release/upgrade gate cannot be satisfied by nothing.\n' "$GATE_LOG_PREFIX" >&2
  exit 1
fi
if [ "$COUNT" -gt 1 ]; then
  printf '%s  FAIL %d APKs under %s — refusing to guess which one is the candidate:\n%s\n' \
    "$GATE_LOG_PREFIX" "$COUNT" "$DIR" "$(printf '%s\n' "$APKS" | sed 's/^/          /')" >&2
  printf '%s       clean the directory (rm -rf %s) and rebuild; a gate that installs\n' "$GATE_LOG_PREFIX" "$DIR" >&2
  printf '%s       "whichever APK is there" is exactly the pseudo-gate we are removing.\n' "$GATE_LOG_PREFIX" >&2
  exit 1
fi

APK="$(first_nonempty_line "$APKS")"
log "resolved release APK: $APK"

case "$(basename "$APK")" in
  *-unsigned.apk)
    if [ "$REQUIRE_SIGNED" -eq 1 ]; then
      printf '%s  FAIL the only release artifact is UNSIGNED: %s\n' "$GATE_LOG_PREFIX" "$APK" >&2
      printf '%s       This job needs the SIGNED R8 release APK (the thing users actually\n' "$GATE_LOG_PREFIX" >&2
      printf '%s       upgrade onto). Materialise the keystore first:\n' "$GATE_LOG_PREFIX" >&2
      printf '%s         KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD secrets ->\n' "$GATE_LOG_PREFIX" >&2
      printf '%s         bash scripts/prepare_release_keystore.sh && ./gradlew :app:assembleRelease\n' "$GATE_LOG_PREFIX" >&2
      printf '%s       Failing here is deliberate: an unsigned or debug candidate would let the\n' "$GATE_LOG_PREFIX" >&2
      printf '%s       upgrade gate pass while proving nothing about the shipped artifact.\n' "$GATE_LOG_PREFIX" >&2
      exit 1
    fi
    warn "unsigned release artifact accepted for an R8/compile check only: $APK"
    ;;
  *)
    if [ "$REQUIRE_SIGNED" -eq 1 ]; then
      APKSIGNER="$(find_build_tool apksigner)"
      if ! "$APKSIGNER" verify "$(to_native_path "$APK")" >/dev/null 2>&1; then
        printf '%s  FAIL apksigner rejected %s — the release task produced an unverifiable APK\n' \
          "$GATE_LOG_PREFIX" "$APK" >&2
        exit 1
      fi
      ok "signed release APK verified: $APK"
    fi
    ;;
esac

if [ -n "$PATH_FILE" ]; then
  mkdir -p "$(dirname "$PATH_FILE")"
  printf '%s\n' "$APK" >"$PATH_FILE"
  log "path written: $PATH_FILE"
fi
printf '%s\n' "$APK"
