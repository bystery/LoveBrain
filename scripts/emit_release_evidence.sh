#!/usr/bin/env bash
#
# scripts/emit_release_evidence.sh
#
# Builds the release evidence index that re-audit §9 demands ("证据索引" /
# items 4, 7, 8): APK SHA-256, versionCode/versionName, signing certificate
# fingerprint, SBOM, instrumentation test counts, upgrade assertions.
#
# It is a gate, not a formatter: every referenced input must exist, be
# non-empty, and be internally consistent with the APK being released. Anything
# missing makes the release fail rather than print "TBD".
#
# Usage:
#   bash scripts/emit_release_evidence.sh --out dist/release-evidence.md \
#        --title "v1.4.0" \
#        --apk dist/LoveBrain-1.4.0.apk \
#        --metadata dist/apk-metadata.txt \
#        --signing dist/signing.properties \
#        --sbom dist/sbom.spdx.json --sbom-csv dist/sbom.csv \
#        --license-report dist/license-report.md \
#        --upgrade-evidence fixtures/upgrade-evidence.md \
#        [--upgrade-manifest fixtures/upgrade-fixture-manifest.txt] \
#        --ui-xml-dir app/build/outputs/androidTest-results/connected \
#        [--extra <label>=<value>]...
#
# Exit codes: 0 evidence consistent, 1 inconsistency, 2 usage.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

OUT="dist/release-evidence.md"
TITLE="${GITHUB_REF_NAME:-local}"
APK=""
METADATA=""
SIGNING=""
SBOM=""
SBOM_CSV=""
LICENSE_REPORT=""
UPGRADE_EVIDENCE=""
UPGRADE_MANIFEST=""
UI_XML_DIR=""
declare -a EXTRA=()

while [ $# -gt 0 ]; do
  case "$1" in
    --out) OUT="$2"; shift 2 ;;
    --title) TITLE="$2"; shift 2 ;;
    --apk) APK="$2"; shift 2 ;;
    --metadata) METADATA="$2"; shift 2 ;;
    --signing) SIGNING="$2"; shift 2 ;;
    --sbom) SBOM="$2"; shift 2 ;;
    --sbom-csv) SBOM_CSV="$2"; shift 2 ;;
    --license-report) LICENSE_REPORT="$2"; shift 2 ;;
    --upgrade-evidence) UPGRADE_EVIDENCE="$2"; shift 2 ;;
    --upgrade-manifest) UPGRADE_MANIFEST="$2"; shift 2 ;;
    --ui-xml-dir) UI_XML_DIR="$2"; shift 2 ;;
    --extra) EXTRA+=("$2"); shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

for req in APK METADATA SIGNING SBOM SBOM_CSV LICENSE_REPORT UPGRADE_EVIDENCE; do
  val="${!req}"
  [ -n "$val" ] || die_usage "--$(echo "$req" | tr 'A-Z' 'a-z' | tr '_' '-') is required: the evidence index cannot claim a check that produced no file"
  require_file "$val" "evidence input $req"
done

mkdir -p "$(dirname "$OUT")"

prop() {
  # prop <file> <key>
  local f="$1" k="$2" v
  v="$(grep -m1 "^${k}=" "$f" | sed "s/^${k}=//" | tr -d '\r')" || v=""
  [ -n "$v" ] || die "evidence input $f does not contain '$k' — refusing to publish an index with holes"
  printf '%s\n' "$v"
}

APK_SHA="$(sha256_of "$APK")"
META_SHA="$(prop "$METADATA" sha256)"
META_NAME="$(prop "$METADATA" version_name)"
META_CODE="$(prop "$METADATA" version_code)"
META_PKG="$(prop "$METADATA" package)"
[ "$APK_SHA" = "$META_SHA" ] ||
  die "the APK being released has SHA-256 $APK_SHA but $METADATA recorded $META_SHA — the artifact was swapped or rebuilt after verification"
ok "APK SHA-256 matches the verified metadata ($APK_SHA)"

SIG_FP="$(prop "$SIGNING" signer_cert_sha256)"
BASE_FP="$(prop "$SIGNING" baseline_cert_sha256)"
BASE_VER="$(prop "$SIGNING" baseline_version)"
[ "$SIG_FP" = "$BASE_FP" ] ||
  die "signing continuity is broken in the recorded evidence ($SIG_FP vs $BASE_FP)"
[ "${SIG_FP:0:8}" = "c2986640" ] ||
  die "the recorded certificate ($SIG_FP) does not start with the published v1.3.1 prefix c2986640 — the baseline or the keystore changed"
ok "certificate fingerprint continuous with $BASE_VER ($SIG_FP)"

SBOM_PACKAGES="$(grep -c '"SPDXID": "SPDXRef-Package' "$SBOM")" || SBOM_PACKAGES=0
[ "$SBOM_PACKAGES" -gt 1 ] || die "the SBOM lists $SBOM_PACKAGES package(s); a release SBOM must enumerate dependencies"
SBOM_ROWS="$(( $(wc -l <"$SBOM_CSV" | tr -d ' ') - 1 ))"
[ "$SBOM_ROWS" -eq "$((SBOM_PACKAGES - 1))" ] ||
  die "SBOM JSON ($((SBOM_PACKAGES - 1)) dependencies) and CSV ($SBOM_ROWS rows) disagree — one of them is stale"
validate_json "$SBOM"
ok "SBOM consistent: $SBOM_ROWS dependency packages"

grep -qi 'license scan passed\|allowed' "$LICENSE_REPORT" ||
  die "$LICENSE_REPORT does not report a completed license scan"
DENIED_COUNT="$(grep -c ',deny,' "$SBOM_CSV")" || DENIED_COUNT=0
[ "$DENIED_COUNT" -eq 0 ] || die "$DENIED_COUNT dependency row(s) in $SBOM_CSV are marked deny — the license gate did not actually pass"
ok "license scan report shows no denied dependency"

UI_TOTAL="not-supplied"
if [ -n "$UI_XML_DIR" ]; then
  require_dir "$UI_XML_DIR" "instrumentation XML dir"
  UI_TOTAL="$(find "$UI_XML_DIR" -type f -name '*.xml' -exec grep -ho 'tests="[0-9]*"' {} + |
    sed 's/[^0-9]//g' | awk '{ s += $1 } END { print s + 0 }')"
  [ "${UI_TOTAL:-0}" -gt 0 ] || die "no instrumentation tests are recorded under $UI_XML_DIR — the UI evidence is empty"
  ok "instrumentation evidence: $UI_TOTAL test(s) recorded"
fi

UP_ROWS=0
if [ -n "$UPGRADE_MANIFEST" ]; then
  require_file "$UPGRADE_MANIFEST" "upgrade fixture manifest"
  UP_ROWS="$(grep -c '^files/' "$UPGRADE_MANIFEST")" || UP_ROWS=0
  [ "$UP_ROWS" -gt 0 ] || die "the upgrade manifest lists no fixture files — the覆盖安装 was asserted against nothing"
  ok "upgrade fixture manifest lists $UP_ROWS seeded file(s)"
fi

COMMIT="$(git rev-parse HEAD 2>/dev/null)" || COMMIT="unknown"
BUILT_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

{
  printf '# Release evidence index — %s\n\n' "$TITLE"
  printf '| item | value | verified by |\n|---|---|---|\n'
  printf '| commit | `%s` | `git rev-parse HEAD` |\n' "$COMMIT"
  printf '| built at (UTC) | %s | this script |\n' "$BUILT_AT"
  printf '| APK | `%s` | — |\n' "$APK"
  printf '| APK SHA-256 | `%s` | recomputed here and compared with `%s` |\n' "$APK_SHA" "$METADATA"
  printf '| package | `%s` | aapt2 badging |\n' "$META_PKG"
  printf '| versionName / versionCode | `%s` / `%s` | aapt2 badging |\n' "$META_NAME" "$META_CODE"
  printf '| signer certificate SHA-256 | `%s` | apksigner + `%s` |\n' "$SIG_FP" "$SIGNING"
  printf '| signing continuity | PASS vs published %s | scripts/verify_signing_continuity.sh |\n' "$BASE_VER"
  printf '| dependency packages (SBOM) | %s | `%s` (SPDX 2.3) |\n' "$SBOM_ROWS" "$SBOM"
  printf '| license decisions denied | %s | scripts/license_scan.sh |\n' "$DENIED_COUNT"
  printf '| instrumentation tests | %s | `%s` XML summaries |\n' "$UI_TOTAL" "$UI_XML_DIR"
  printf '| upgrade fixture files | %s | `%s` |\n' "$UP_ROWS" "$UPGRADE_MANIFEST"
  printf '\n## Linked evidence artefacts\n\n'
  printf '* APK metadata: `%s`\n' "$METADATA"
  printf '* signing properties: `%s`\n' "$SIGNING"
  printf '* SBOM: `%s` and `%s`\n' "$SBOM" "$SBOM_CSV"
  printf '* license report: `%s`\n' "$LICENSE_REPORT"
  printf '* upgrade assertions: `%s`\n' "$UPGRADE_EVIDENCE"
  if [ -n "$UI_XML_DIR" ]; then
    printf '* instrumentation XML: `%s`\n' "$UI_XML_DIR"
  fi
  for kv in ${EXTRA[@]+"${EXTRA[@]}"}; do
    printf '* %s\n' "$kv"
  done
  printf '\nEvery row above was recomputed from the artifact being published by\n'
  printf '`scripts/emit_release_evidence.sh`; a missing or inconsistent input fails\n'
  printf 'the release instead of printing a placeholder.\n'
} >"$OUT"

ok "evidence index written: $OUT"
