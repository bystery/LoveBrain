# shellcheck shell=bash
#
# Shared helpers for the LoveBrain CI / release gate scripts (scripts/*.sh).
#
# This file is SOURCED, never executed directly. Every gate script starts with
# `set -euo pipefail` and must exit non-zero on any real failure — masking with
# `|| true` / `|| echo` / `continue-on-error` is forbidden by the 2026-09-23
# re-audit (sections 3 and 8).
#
# Target: bash on ubuntu-latest AND bash (Git-Bash/MSYS) on a developer machine.

if [ -z "${BASH_VERSION:-}" ]; then
  printf '[gate][FATAL] gate scripts require bash. Run: bash scripts/%s\n' \
    "${0:-<name>.sh}" >&2
  exit 2
fi

GATE_LOG_PREFIX="${GATE_LOG_PREFIX:-[gate]}"

log() {
  printf '%s %s\n' "$GATE_LOG_PREFIX" "$*" >&2
}

ok() {
  printf '%s  OK   %s\n' "$GATE_LOG_PREFIX" "$*" >&2
}

# warn() is for cosmetic notices only. It NEVER downgrades a real failure to a
# pass — if a check fails, call die() instead.
warn() {
  printf '%s  WARN %s\n' "$GATE_LOG_PREFIX" "$*" >&2
}

die() {
  printf '%s  FAIL %s\n' "$GATE_LOG_PREFIX" "$*" >&2
  exit 1
}

die_usage() {
  printf '%s  USAGE %s\n' "$GATE_LOG_PREFIX" "$*" >&2
  exit 2
}

require_cmd() {
  local c
  for c in "$@"; do
    command -v "$c" >/dev/null 2>&1 || die "required command not found on PATH: $c"
  done
}

require_file() {
  local f="$1" what="${2:-file}"
  [ -f "$f" ] || die "expected $what does not exist: $f"
  [ -s "$f" ] || die "expected $what is empty: $f"
}

require_dir() {
  local d="$1" what="${2:-directory}"
  [ -d "$d" ] || die "expected $what does not exist: $d"
}

# Absolute path of the repository root (works from a checkout or a worktree).
repo_root() {
  local here
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  # scripts/ -> repo root
  (cd "$here/.." && pwd)
}

sdk_root() {
  local root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [ -n "$root" ] || die "ANDROID_HOME (or ANDROID_SDK_ROOT) is not set — cannot locate build-tools. Export the Android SDK path before running this script."
  [ -d "$root" ] || die "ANDROID_HOME points to a non-existent directory: $root"
  printf '%s\n' "$root"
}

# find_build_tool <name> — newest build-tools entry containing <name> (or <name>.bat on Windows).
find_build_tool() {
  local name="$1" root versions v cand
  root="$(sdk_root)"
  versions="$(for d in "$root"/build-tools/*/; do [ -d "$d" ] && basename "$d"; done | sort -Vr)"
  [ -n "$versions" ] || die "no build-tools installed under $root/build-tools"
  for v in $versions; do
    for cand in "$root/build-tools/$v/$name" "$root/build-tools/$v/$name.bat" "$root/build-tools/$v/$name.exe"; do
      if [ -f "$cand" ]; then
        printf '%s\n' "$cand"
        return 0
      fi
    done
  done
  die "build tool '$name' not found in any of: $root/build-tools/{$(printf '%s ' $versions)}"
}

# to_native_path <posix-path> — Windows-native path when running under Git-Bash,
# otherwise the path unchanged (ubuntu-latest).
to_native_path() {
  local p="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$p"
  else
    printf '%s\n' "$p"
  fi
}

# sha256_of_text — 给"被提交进仓库的文本输入"算内容指纹时用这个。
#
# `.gitattributes` 是 `* text=auto`，同一份文本在 Windows 工作树里是 CRLF、在 Linux
# 检出里是 LF。跟着字节走，锁在本机生成、在 CI 上必然对不上（这已经咬过两次：
# prompt 资产锁与锦囊夹具锁）。换行不是内容，所以摘要前先归一成 LF。
# 二进制产物（APK 等）必须继续用 sha256_of——那个要的就是字节级身份。
sha256_of_text() {
  local f="$1"
  require_file "$f" "text input for hashing"
  tr -d '\r' <"$f" | sha256sum | awk '{print $1}'
}

sha256_of() {
  local f="$1"
  require_file "$f" "artifact for hashing"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$f" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$f" | awk '{print $1}'
  elif command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 "$f" | awk '{print $NF}'
  else
    die "no SHA-256 tool available (need sha256sum, shasum or openssl)"
  fi
}

# normalize_fingerprint <raw> — strips colons/whitespace, lowercases.
normalize_fingerprint() {
  printf '%s' "$1" | tr -d ' \t\r\n:' | tr 'A-F' 'a-f'
}

# ── text helpers that are safe under `set -o pipefail` ──────────────────────
# A `grep | sed` pipeline inside $( ) aborts a `set -euo pipefail` script when
# grep simply finds nothing. These helpers turn "no match" into empty output
# with exit status 0, so callers can decide explicitly whether empty is a
# failure. They never hide a real error: they are only used for lookups whose
# absence is a legitimate, handled outcome.

first_match() {
  # first_match <ERE> <text> — first matching token, empty when there is none.
  local regex="$1" text="$2" out
  out="$(printf '%s\n' "$text" | grep -m1 -oE "$regex")" || out=""
  printf '%s\n' "$out"
}

all_matches() {
  local regex="$1" text="$2" out
  out="$(printf '%s\n' "$text" | grep -oE "$regex")" || out=""
  printf '%s\n' "$out"
}

first_value_of() {
  # first_value_of <tag> <text> — value of the first <tag>value</tag>, empty if absent.
  local tag="$1" text="$2" out
  out="$(first_match "<$tag>[^<]*</$tag>" "$text")" || out=""
  printf '%s\n' "$out" | sed 's/<[^>]*>//g'
}

xml_section() {
  # xml_section <tag> <text> — the inner text of the FIRST <tag>…</tag> section,
  # possibly spanning nested tags. Exact "first section" semantics that a greedy
  # `grep -o '<tag>.*</tag>'` cannot give (it would swallow sibling sections).
  local tag="$1" text="$2" out
  out="$(printf '%s' "$text" | awk -v s="<$tag>" -v e="</$tag>" '{
      i = index($0, s)
      if (i > 0) {
        rest = substr($0, i + length(s))
        j = index(rest, e)
        if (j > 0) { print substr(rest, 1, j - 1); exit }
      }
    }')" || out=""
  printf '%s\n' "$out"
}

all_values_of() {
  local tag="$1" text="$2" out
  out="$(all_matches "<$tag>[^<]*</$tag>" "$text")" || out=""
  printf '%s\n' "$out" | sed 's/<[^>]*>//g' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

first_nonempty_line() {
  local text="$1" out
  out="$(printf '%s\n' "$text" | grep -v '^[[:space:]]*$' | sed -n '1p')" || out=""
  printf '%s\n' "$out"
}

# require_value <description> <value> — empty IS a failure here.
require_value() {
  local what="$1" value="$2"
  [ -n "$value" ] || die "could not read $what"
  printf '%s\n' "$value"
}

# ── baseline / policy key-value lookups ─────────────────────────────────────
# baseline_value <key> [file] — prints the value of `key=` from the signing
# baseline, or nothing. Status 0 either way.
baseline_value() {
  local key="$1" file="${2:-$(repo_root)/scripts/signing-baseline.txt}" raw
  [ -f "$file" ] || return 0
  raw="$(grep -m1 "^${key}=" "$file" | sed "s/^${key}=//" | tr -d '\r')" || raw=""
  printf '%s\n' "$raw"
}

# from_native_path <path> — POSIX path when running under Git-Bash.
from_native_path() {
  local p="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$p"
  else
    printf '%s\n' "$p"
  fi
}

# validate_json <file> — parse with a working python, else fall back to a
# structural check. Note: on Windows Git-Bash `python3` can be the Microsoft
# Store alias, which fails without parsing — so we probe before trusting it.
validate_json() {
  local f="$1" c
  require_file "$f" "JSON document"
  for c in python3 python; do
    if command -v "$c" >/dev/null 2>&1 && "$c" -c 'print(1)' >/dev/null 2>&1; then
      if "$c" -c 'import json,sys; json.load(open(sys.argv[1]))' "$f" >/dev/null 2>&1; then
        ok "JSON parses: $(basename "$f")"
        return 0
      fi
      die "generated JSON is not parseable: $f"
    fi
  done
  # No usable python: keep a real (weaker) check rather than skipping silently.
  head -c 1 "$f" | grep -q '{' || die "$f does not start with a JSON object"
  tail -c 20 "$f" | grep -q '}' || die "$f does not end with a JSON object"
  grep -q '"spdxVersion"' "$f" || die "$f is missing the spdxVersion field"
  grep -q '"packages"' "$f" || die "$f is missing the packages array"
  log "no python interpreter available — performed a structural JSON check only"
}

# validate_zip <path> — an APK must be a readable ZIP; a truncated download must
# fail the job instead of being swallowed by `|| echo`.
validate_zip() {
  local f="$1"
  require_file "$f" "APK"
  if command -v unzip >/dev/null 2>&1; then
    unzip -tqq "$(to_native_path "$f")" >/dev/null 2>&1 \
      || die "APK is not a valid ZIP archive (truncated or corrupt download): $f"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c "import sys,zipfile; zipfile.ZipFile(sys.argv[1]).testzip()" "$f" >/dev/null 2>&1 \
      || die "APK is not a valid ZIP archive (truncated or corrupt download): $f"
  else
    die "neither unzip nor python3 available to validate APK integrity: $f"
  fi
  ok "ZIP integrity verified: $(basename "$f")"
}

# adb_or_die — gate scripts must run against a real device; silently skipping is
# exactly the pseudo-gate behaviour the audit rejected.
require_device() {
  command -v adb >/dev/null 2>&1 || die "adb not found on PATH — install platform-tools (ANDROID_HOME/platform-tools)"
  local serials
  serials="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
  [ -n "$serials" ] || die "no online adb device found — the emulator failed to start or died; refusing to pass with zero tests"
  log "device(s) online: $(printf '%s' "$serials" | tr '\n' ' ')"
}

# count_annotations <dir> <grep-regex> — prints the total number of matching lines.
#
# A zero-match search is a legitimate answer, not a failure: grep exits 1 when it
# finds nothing, and under `set -euo pipefail` that used to kill the caller with no
# output at all (CI's ui-test job died precisely because the tree had no @Ignore).
# Callers that must fail closed do so explicitly on the *value*, not on the exit code.
count_annotations() {
  local dir="$1" pattern="$2" total
  if [ ! -d "$dir" ]; then
    printf '0'
    return 0
  fi
  total="$(grep -r --include='*.kt' -c "$pattern" "$dir" 2>/dev/null |
    awk -F: '{ s += $2 } END { print s + 0 }' || true)"
  printf '%s' "${total:-0}"
}
