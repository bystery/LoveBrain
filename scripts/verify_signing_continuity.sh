#!/usr/bin/env bash
#
# scripts/verify_signing_continuity.sh
#
# Signing CONTINUITY gate (re-audit 2026-09-23 §3: "Release 工作流不比较 v1.3.1
# 已公开证书指纹 c2986640...f7d，只做当前 APK 自身签名验证").
#
# Verifies that the candidate APK is signed by the SAME certificate that the
# already-published v1.3.1 release was signed with, as pinned in
# scripts/signing-baseline.txt. An unsigned APK, an APK signed with a different
# key, or a missing/placeholder baseline all fail the job — never skipped.
#
# Usage:
#   bash scripts/verify_signing_continuity.sh <candidate.apk> \
#        [--baseline scripts/signing-baseline.txt] \
#        [--reference-apk <published-v1.3.1.apk>] \
#        [--properties <key=value out>] \
#        [--report <markdown out>]
#
# Exit codes: 0 continuous, 1 verification failed, 2 usage, 3 baseline unusable.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

APK=""
BASELINE="$SCRIPT_DIR/signing-baseline.txt"
REFERENCE_APK=""
PROPERTIES=""
REPORT=""

while [ $# -gt 0 ]; do
  case "$1" in
    --baseline) BASELINE="$2"; shift 2 ;;
    --reference-apk) REFERENCE_APK="$2"; shift 2 ;;
    --properties) PROPERTIES="$2"; shift 2 ;;
    --report) REPORT="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    -*) die_usage "unknown option: $1" ;;
    *)
      [ -z "$APK" ] || die_usage "more than one APK argument given"
      APK="$1"
      shift
      ;;
  esac
done

[ -n "$APK" ] || die_usage "a candidate APK path is required"
require_file "$APK" "candidate APK"

if [ ! -f "$BASELINE" ]; then
  printf '%s  FAIL signing baseline is missing: %s\n' "$GATE_LOG_PREFIX" "$BASELINE" >&2
  printf '%s       Signature continuity CANNOT be verified without it. Restore the\n' "$GATE_LOG_PREFIX" >&2
  printf '%s       pinned v1.3.1 certificate fingerprint or stop the release.\n' "$GATE_LOG_PREFIX" >&2
  exit 3
fi

# read_baseline <var_name> <key> — assigns to the caller's variable in the
# CURRENT shell so a `exit 3` below is not swallowed by a command substitution.
read_baseline() {
  local __var="$1" key="$2" raw
  raw="$(grep -m1 "^${key}=" "$BASELINE" | sed "s/^${key}=//" | tr -d '\r')" || raw=""
  if [ -z "$raw" ] || printf '%s' "$raw" | grep -qi 'TODO\|PLACEHOLDER\|UNKNOWN'; then
    printf '%s  FAIL baseline key %s is empty or a placeholder in %s\n' "$GATE_LOG_PREFIX" "$key" "$BASELINE" >&2
    printf '%s       Signature continuity is a REQUIRED gate: refusing to skip it.\n' "$GATE_LOG_PREFIX" >&2
    exit 3
  fi
  printf -v "$__var" '%s' "$raw"
}

read_baseline EXPECTED_SHA256 cert_sha256
read_baseline EXPECTED_SHA1 cert_sha1
read_baseline BASELINE_VERSION baseline_version
EXPECTED_SHA256="$(normalize_fingerprint "$EXPECTED_SHA256")"
EXPECTED_SHA1="$(normalize_fingerprint "$EXPECTED_SHA1")"

printf '%s' "$EXPECTED_SHA256" | grep -Eq '^[0-9a-f]{64}$' \
  || die "baseline cert_sha256 is not 64 hex chars: '$EXPECTED_SHA256'"

APKSIGNER="$(find_build_tool apksigner)"
log "using apksigner: $APKSIGNER"

VERIFY_OUT="$("$APKSIGNER" verify --verbose --print-certs "$(to_native_path "$APK")" 2>&1)" || {
  printf '%s\n' "$VERIFY_OUT" >&2
  die "apksigner rejected the candidate APK — it is unsigned or its signature is invalid. Release APKs must be signed with the published release key; refusing to publish."
}

cert_fp_of() {
  # apksigner prints either "Signer #1 certificate SHA-256 digest: <hex>"
  # (build-tools <= 35) or "V2 Signer: certificate SHA-256 digest: <hex>"
  # (build-tools >= 36/37). Match both, first signer only.
  local out="$1" algo="$2" line raw
  line="$(first_match "[Cc]ertificate ${algo} (digest|fingerprint):[[:space:]]*[0-9A-Fa-f:]+" "$out")"
  [ -n "$line" ] || die "apksigner output did not contain a certificate ${algo} value for the APK"
  raw="$(printf '%s\n' "$line" | sed -E 's/.*(digest|fingerprint):[[:space:]]*//')"
  normalize_fingerprint "$raw"
}

SIGNER_COUNT="$(printf '%s\n' "$VERIFY_OUT" | grep -cE 'certificate DN:')" || SIGNER_COUNT=0
[ "$SIGNER_COUNT" -ge 1 ] || die "apksigner reported no signers for $APK — the APK is unsigned or only carries JAR stub signatures. Release APKs must be signed with the published release key."
if [ "$SIGNER_COUNT" -gt 1 ]; then
  die "candidate APK is signed by $SIGNER_COUNT certificates; the release pipeline must produce exactly one signer"
fi

ACTUAL_SHA256="$(cert_fp_of "$VERIFY_OUT" SHA-256)"
ACTUAL_SHA1="$(cert_fp_of "$VERIFY_OUT" SHA-1)"
DN_LINE="$(first_match '[Cc]ertificate DN:.*$' "$VERIFY_OUT")"
CERT_DN="$(printf '%s\n' "$DN_LINE" | sed 's/.*DN:[[:space:]]*//')"

log "candidate signer : $CERT_DN"
log "candidate SHA-256: $ACTUAL_SHA256"
log "pinned   SHA-256: $EXPECTED_SHA256 ($BASELINE_VERSION)"

if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  printf '%s  FAIL SIGNING CONTINUITY BROKEN\n' "$GATE_LOG_PREFIX" >&2
  printf '%s       candidate APK is signed with a different certificate than the\n' "$GATE_LOG_PREFIX" >&2
  printf '%s       published %s release. Existing users would be unable to upgrade.\n' "$GATE_LOG_PREFIX" "$BASELINE_VERSION" >&2
  printf '%s       expected: %s\n' "$GATE_LOG_PREFIX" "$EXPECTED_SHA256" >&2
  printf '%s       actual  : %s\n' "$GATE_LOG_PREFIX" "$ACTUAL_SHA256" >&2
  exit 1
fi
ok "signature is continuous with published $BASELINE_VERSION (SHA-256 match)"

if [ "$ACTUAL_SHA1" != "$EXPECTED_SHA1" ]; then
  die "SHA-1 fingerprint also differs from the baseline ('$ACTUAL_SHA1' vs '$EXPECTED_SHA1') — the pinned baseline is internally inconsistent and must be corrected"
fi

if [ -n "$REFERENCE_APK" ]; then
  require_file "$REFERENCE_APK" "reference ($BASELINE_VERSION) APK"
  log "cross-checking the baseline against the published artifact: $REFERENCE_APK"
  REF_OUT="$("$APKSIGNER" verify --verbose --print-certs "$(to_native_path "$REFERENCE_APK")" 2>&1)" || {
    printf '%s\n' "$REF_OUT" >&2
    die "the published $BASELINE_VERSION APK itself failed signature verification — the downloaded fixture is corrupt or not the real release artifact"
  }
  REF_SHA256="$(cert_fp_of "$REF_OUT" SHA-256)"
  [ "$REF_SHA256" = "$EXPECTED_SHA256" ] \
    || die "baseline does not match the published $BASELINE_VERSION certificate ('$REF_SHA256'). The baseline is wrong — fix scripts/signing-baseline.txt deliberately, never to silence a red job."
  ok "baseline confirmed against the published $BASELINE_VERSION artifact"
fi

if [ -n "$PROPERTIES" ]; then
  mkdir -p "$(dirname "$PROPERTIES")"
  {
    printf 'signer_cert_sha256=%s\n' "$ACTUAL_SHA256"
    printf 'signer_cert_sha1=%s\n' "$ACTUAL_SHA1"
    printf 'signer_cert_dn=%s\n' "$CERT_DN"
    printf 'baseline_cert_sha256=%s\n' "$EXPECTED_SHA256"
    printf 'baseline_version=%s\n' "$BASELINE_VERSION"
    printf 'signing_continuity=PASS\n'
  } >"$PROPERTIES"
  ok "properties written: $PROPERTIES"
fi

if [ -n "$REPORT" ]; then
  mkdir -p "$(dirname "$REPORT")"
  {
    printf '| Signing continuity | **PASS** vs published %s |\n' "$BASELINE_VERSION"
    printf '| Signer DN | `%s` |\n' "$CERT_DN"
    printf '| Candidate cert SHA-256 | `%s` |\n' "$ACTUAL_SHA256"
    printf '| Candidate cert SHA-1 | `%s` |\n' "$ACTUAL_SHA1"
    printf '| Baseline cert SHA-256 | `%s` |\n' "$EXPECTED_SHA256"
  } >"$REPORT"
  ok "report written: $REPORT"
fi
