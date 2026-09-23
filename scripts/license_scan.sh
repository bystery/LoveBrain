#!/usr/bin/env bash
#
# scripts/license_scan.sh
#
# REAL dependency license scan + SBOM generation.
#
# Re-audit 2026-09-23 §3: "'Dependency license scan' 其实只是 gradle dependencies,
# 并且带 || true; 既不是许可证扫描,也不是 SBOM 门禁." This script replaces that
# step. It
#
#   1. resolves the runtime classpath graph with Gradle,
#   2. keeps the RESOLVED coordinates (conflict resolution `-> x.y.z` applied),
#   3. locates each module's POM (Gradle module cache first, then the declared
#      Maven repositories: Google Maven + Maven Central),
#   4. reads `<licenses>` out of the POM, following `<parent>` POMs and then
#      license `<url>`s as fallbacks,
#   5. classifies each license against scripts/license-policy.txt,
#   6. writes an SPDX 2.3 SBOM + CSV + markdown report,
#   7. FAILS the build when any module has no provable license or a license the
#      policy does not allow, when the graph is empty, or when an SBOM output is
#      missing/empty/unparseable.
#
# Usage:
#   bash scripts/license_scan.sh [--offline] [--no-network]
#        [--configuration releaseRuntimeClasspath]
#        [--spdx <path>] [--csv <path>] [--report <path>] [--tree <path>]
#        [--policy <path>] [--overrides <path>]
#
#   --offline    pass --offline to Gradle for the dependency tree (offline build)
#   --no-network forbid POM downloads from the repositories (offline dev only);
#                modules whose POM is not already cached then fail the gate
#
# Exit codes: 0 clean, 1 license/policy/SBOM failure, 2 usage/tooling failure.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

POLICY_FILE="$SCRIPT_DIR/license-policy.txt"
OVERRIDES_FILE="$SCRIPT_DIR/license-overrides.txt"
CONFIGURATION="releaseRuntimeClasspath"
SPDX_OUT="dist/sbom.spdx.json"
CSV_OUT="dist/sbom.csv"
REPORT_OUT="dist/license-report.md"
TREE_OUT="build/reports/dependency-tree.txt"
GRADLE_FLAGS=()
NETWORK_OK=1

while [ $# -gt 0 ]; do
  case "$1" in
    --offline) GRADLE_FLAGS+=("--offline"); shift ;;
    --no-network) NETWORK_OK=0; shift ;;
    --configuration) CONFIGURATION="$2"; shift 2 ;;
    --spdx) SPDX_OUT="$2"; shift 2 ;;
    --csv) CSV_OUT="$2"; shift 2 ;;
    --report) REPORT_OUT="$2"; shift 2 ;;
    --tree) TREE_OUT="$2"; shift 2 ;;
    --policy) POLICY_FILE="$2"; shift 2 ;;
    --overrides) OVERRIDES_FILE="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    *) die_usage "unknown option: $1" ;;
  esac
done

require_file "$POLICY_FILE" "license policy"
cd "$ROOT"
[ -x "./gradlew" ] || die "gradlew is not executable — run: chmod +x gradlew"

# Repositories declared in settings.gradle.kts (dependencyResolutionManagement).
REPO_BASES=(
  "https://dl.google.com/dl/android/maven2"
  "https://repo1.maven.org/maven2"
)

POM_STORE="$(mktemp -d)"
cleanup() { rm -rf "$POM_STORE"; }
trap cleanup EXIT

# ── 1. resolve the runtime classpath graph ───────────────────────────────────
mkdir -p "$(dirname "$TREE_OUT")" "$(dirname "$SPDX_OUT")" "$(dirname "$CSV_OUT")" "$(dirname "$REPORT_OUT")"
log "resolving $CONFIGURATION dependency graph"
if ! ./gradlew :app:dependencies --configuration "$CONFIGURATION" --no-daemon \
  ${GRADLE_FLAGS[@]+"${GRADLE_FLAGS[@]}"} >"$TREE_OUT" 2>&1; then
  tail -30 "$TREE_OUT" >&2
  die "gradle :app:dependencies failed for configuration '$CONFIGURATION' — the license scan cannot run"
fi
grep -q '^' "$TREE_OUT" || die "gradle produced an empty dependency tree at $TREE_OUT"

# ── 2. resolved module coordinates ──────────────────────────────────────────
# `+--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 -> 1.9.24` resolves to 1.9.24;
# only the resolved coordinate is shipped, so only that one belongs in the SBOM.
MODULES="$POM_STORE/modules.txt"
awk '
  {
    line = $0
    sub(/\r$/, "", line)
    if (line ~ /project /) next
    if (match(line, /[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+/)) {
      coord = substr(line, RSTART, RLENGTH)
      rest = substr(line, RSTART + RLENGTH)
      if (match(rest, /^[ \t]*->[ \t]*/)) {
        target = substr(rest, RSTART + RLENGTH)
        if (match(target, /^[A-Za-z0-9_.+-]+/)) {
          split(coord, p, ":")
          coord = p[1] ":" p[2] ":" substr(target, 1, RLENGTH)
        }
      }
      print coord
    }
  }
' "$TREE_OUT" | sort -u >"$MODULES"

TOTAL="$(wc -l <"$MODULES" | tr -d ' ')"
log "resolved modules discovered: $TOTAL"
[ "$TOTAL" -gt 0 ] || die "the dependency scan found 0 modules in $TREE_OUT — an empty scan is a FAILURE, not a pass"

# ── 3. module cache + repository POM lookup ─────────────────────────────────
CACHE_ROOT=""
for candidate in "${GRADLE_USER_HOME:-}" "$HOME/.gradle"; do
  [ -n "$candidate" ] || continue
  probe="$(from_native_path "$candidate")"
  if [ -d "$probe/caches/modules-2/files-2.1" ]; then
    CACHE_ROOT="$probe/caches/modules-2/files-2.1"
    break
  fi
done
if [ -n "$CACHE_ROOT" ]; then
  log "module cache: $CACHE_ROOT"
else
  log "no Gradle module cache found — all POMs must come from the declared repositories"
fi

cached_pom() {
  local g="$1" a="$2" v="$3" dir hit
  [ -n "$CACHE_ROOT" ] || return 0
  dir="$CACHE_ROOT/$g/$a/$v"
  [ -d "$dir" ] || return 0
  hit="$(find "$dir" -type f -name "$a-$v.pom" | head -1)"
  [ -n "$hit" ] || hit="$(find "$dir" -type f -name '*.pom' | head -1)"
  printf '%s\n' "$hit"
}

download_pom() {
  # download_pom <group> <artifact> <version> — tries each declared repository.
  local g="$1" a="$2" v="$3" base path out
  path="$(printf '%s' "$g" | tr '.' '/')/$a/$v/$a-$v.pom"
  out="$POM_STORE/${g}_${a}_${v}.pom"
  for base in "${REPO_BASES[@]}"; do
    if curl -fL -s --retry 2 --max-time 60 -o "$out" "$base/$path"; then
      [ -s "$out" ] && { printf '%s\n' "$out"; return 0; }
    fi
    rm -f "$out"
  done
  return 0
}

map_url_to_license() {
  local url="$1" lowered
  lowered="$(printf '%s' "$url" | tr 'A-Z' 'a-z')"
  case "$lowered" in
    *apache.org/licenses/license-2.0* | */apache-2.0*) printf 'Apache License 2.0\n' ;;
    *opensource.org/licenses/mit*) printf 'MIT License\n' ;;
    *opensource.org/licenses/bsd*) printf 'BSD License\n' ;;
    *eclipse.org/legal/epl-2.0* | */epl-2.0*) printf 'Eclipse Public License v2.0\n' ;;
    *eclipse.org/legal/epl-v10* | */epl-1.0*) printf 'Eclipse Public License v1.0\n' ;;
    *cddl.github.io* | */cddl-1.1*) printf 'CDDL 1.1\n' ;;
    *) printf '%s\n' "$url" ;;
  esac
}

# ── 4. policy classification ─────────────────────────────────────────────────
classify_license() {
  local name="$1" lowered action needle id rest
  lowered="$(printf '%s' "$name" | tr 'A-Z' 'a-z')"
  while read -r action needle id rest; do
    case "$action" in
      allow | deny) ;;
      *) continue ;;
    esac
    needle="$(printf '%s' "$needle" | tr 'A-Z' 'a-z' | tr '_' ' ')"
    case "$lowered" in
      *"$needle"*)
        printf '%s %s\n' "$action" "${id:-NOASSERTION}"
        return 0
        ;;
    esac
  done <"$POLICY_FILE"
  printf 'deny NOASSERTION\n'
}

override_for() {
  # A reviewed license for a module whose POM carries no license metadata.
  # Format in license-overrides.txt:  group:artifact|License name as declared
  local coord="$1" line
  [ -f "$OVERRIDES_FILE" ] || return 0
  line="$(grep -m1 -F "^${coord}|" "$OVERRIDES_FILE")"
  [ -n "$line" ] || return 0
  printf '%s\n' "$line" | cut -d'|' -f2- | sed 's/^[[:space:]]*//'
}

# ── 5. scan ─────────────────────────────────────────────────────────────────
ALLOWED=0
DENIED=0
NO_INFO=0
FAILURES=""
CSV_TMP="$POM_STORE/sbom.csv"
printf 'purl,license_name,spdx_id,decision,metadata_source\n' >"$CSV_TMP"

declare -a SBOM_GROUP=() SBOM_ARTIFACT=() SBOM_VERSION=() SBOM_LICENSE=()

while IFS=: read -r G A V; do
  [ -n "${G:-}" ] || continue
  POM="$(cached_pom "$G" "$A" "$V")"
  SOURCE="gradle-cache"
  if [ -z "$POM" ] && [ "$NETWORK_OK" -eq 1 ]; then
    POM="$(download_pom "$G" "$A" "$V")"
    [ -z "$POM" ] || SOURCE="repository-pom"
  fi

  NAME=""
  if [ -n "$POM" ]; then
    FLAT="$(tr -d '\n\r\t' <"$POM")"
    BLOCK="$(xml_section licenses "$FLAT")"
    NAME="$(first_value_of name "$BLOCK")"
    depth=0
    while [ -z "$NAME" ] && [ "$depth" -lt 8 ]; do
      PBLOCK="$(xml_section parent "$FLAT")"
      if [ -z "$PBLOCK" ]; then
        break
      fi
      PG="$(first_value_of groupId "$PBLOCK")"
      PA="$(first_value_of artifactId "$PBLOCK")"
      PV="$(first_value_of version "$PBLOCK")"
      if [ -z "$PG" ] || [ -z "$PA" ] || [ -z "$PV" ]; then
        break
      fi
      PPOM="$(cached_pom "$PG" "$PA" "$PV")"
      if [ -z "$PPOM" ] && [ "$NETWORK_OK" -eq 1 ]; then
        PPOM="$(download_pom "$PG" "$PA" "$PV")"
      fi
      if [ -z "$PPOM" ]; then
        break
      fi
      FLAT="$(tr -d '\n\r\t' <"$PPOM")"
      BLOCK="$(xml_section licenses "$FLAT")"
      NAME="$(first_value_of name "$BLOCK")"
      SOURCE="parent-pom($PG:$PA:$PV)"
      depth=$((depth + 1))
    done
    if [ -z "$NAME" ]; then
      URL="$(first_value_of url "$BLOCK")"
      if [ -n "$URL" ]; then
        NAME="$(map_url_to_license "$URL")"
        SOURCE="$SOURCE+license-url"
      fi
    fi
  fi

  if [ -z "$NAME" ]; then
    OV="$(override_for "$G:$A")"
    if [ -n "$OV" ]; then
      NAME="$OV"
      SOURCE="reviewed-override"
    fi
  fi

  if [ -z "$NAME" ]; then
    NO_INFO=$((NO_INFO + 1))
    FAILURES="$FAILURES
  - $G:$A:$V -> no license metadata (POM ${POM:-not found}, no parent license, no reviewed override)"
    printf 'pkg:maven/%s/%s@%s,"",NOASSERTION,deny,"%s"\n' "$G" "$A" "$V" "${SOURCE:-pom-missing}" >>"$CSV_TMP"
    continue
  fi

  read -r DECISION SPDX_ID <<EOF
$(classify_license "$NAME")
EOF
  SPDX_ID="${SPDX_ID:-NOASSERTION}"
  NAME_CSV="$(printf '%s' "$NAME" | tr -d '"')"
  if [ "$DECISION" = "allow" ]; then
    ALLOWED=$((ALLOWED + 1))
    SBOM_LICENSE+=("$SPDX_ID")
  else
    DENIED=$((DENIED + 1))
    FAILURES="$FAILURES
  - $G:$A:$V -> license '$NAME' is not allowed by $(basename "$POLICY_FILE")"
    SBOM_LICENSE+=("NOASSERTION")
  fi
  SBOM_GROUP+=("$G")
  SBOM_ARTIFACT+=("$A")
  SBOM_VERSION+=("$V")
  printf 'pkg:maven/%s/%s@%s,"%s",%s,%s,%s\n' "$G" "$A" "$V" "$NAME_CSV" "$SPDX_ID" "$DECISION" "$SOURCE" >>"$CSV_TMP"
done <"$MODULES"

mv "$CSV_TMP" "$CSV_OUT"
[ -s "$CSV_OUT" ] || die "SBOM CSV was not produced at $CSV_OUT"

# ── 6. SPDX 2.3 document ─────────────────────────────────────────────────────
CREATED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
APP_VERSION="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
{
  printf '{\n'
  printf '  "spdxVersion": "SPDX-2.3",\n'
  printf '  "dataLicense": "CC0-1.0",\n'
  printf '  "SPDXID": "SPDXRef-DOCUMENT",\n'
  printf '  "name": "LoveBrain-%s",\n' "${APP_VERSION:-unknown}"
  printf '  "documentNamespace": "https://spdx.org/spdxdocs/lovebrain-%s",\n' "${APP_VERSION:-unknown}-$CREATED"
  printf '  "creationInfo": { "created": "%s", "creators": ["Tool: scripts/license_scan.sh"] },\n' "$CREATED"
  printf '  "packages": [\n'
  printf '    { "name": "LoveBrain", "SPDXID": "SPDXRef-Package-app", "versionInfo": "%s", "downloadLocation": "NOASSERTION", "licenseDeclared": "Apache-2.0", "licenseConcluded": "Apache-2.0", "copyrightText": "NOASSERTION" }' "${APP_VERSION:-unknown}"
  i=0
  n=${#SBOM_ARTIFACT[@]}
  while [ "$i" -lt "$n" ]; do
    printf ',\n    { "name": "%s", "SPDXID": "SPDXRef-Package-%d", "versionInfo": "%s", "downloadLocation": "https://repo1.maven.org/maven2/", "licenseDeclared": "%s", "licenseConcluded": "%s", "copyrightText": "NOASSERTION", "externalRefs": [ { "referenceCategory": "PACKAGE-MANAGER", "referenceType": "purl", "referenceLocator": "pkg:maven/%s/%s@%s" } ] }' \
      "${SBOM_ARTIFACT[$i]}" "$i" "${SBOM_VERSION[$i]}" "${SBOM_LICENSE[$i]}" "${SBOM_LICENSE[$i]}" \
      "${SBOM_GROUP[$i]}" "${SBOM_ARTIFACT[$i]}" "${SBOM_VERSION[$i]}"
    i=$((i + 1))
  done
  printf '\n  ],\n'
  printf '  "relationships": [\n'
  printf '    { "spdxElementId": "SPDXRef-DOCUMENT", "relatedSpdxElement": "SPDXRef-Package-app", "relationshipType": "DESCRIBES" }\n'
  printf '  ]\n'
  printf '}\n'
} >"$SPDX_OUT"

[ -s "$SPDX_OUT" ] || die "SPDX SBOM was not produced at $SPDX_OUT"
validate_json "$SPDX_OUT"

# ── 7. report + verdict ──────────────────────────────────────────────────────
{
  printf '# Dependency license scan / SBOM\n\n'
  printf '| item | value |\n|---|---|\n'
  printf '| configuration | `%s` |\n' "$CONFIGURATION"
  printf '| resolved modules | %s |\n' "$TOTAL"
  printf '| license allowed | %s |\n' "$ALLOWED"
  printf '| license denied | %s |\n' "$DENIED"
  printf '| no license metadata | %s |\n' "$NO_INFO"
  printf '| policy file | `%s` |\n' "$POLICY_FILE"
  printf '| SBOM (SPDX 2.3 JSON) | `%s` |\n' "$SPDX_OUT"
  printf '| SBOM (CSV) | `%s` |\n' "$CSV_OUT"
  printf '\nEvery resolved module on the runtime classpath was mapped to its POM (Gradle\n'
  printf 'module cache, then Google Maven / Maven Central) and its `<licenses>` metadata\n'
  printf '(parent POM, then license URL, then a reviewed override entry) was classified\n'
  printf 'against the policy. A module with no provable license fails this gate.\n'
} >"$REPORT_OUT"

log "scanned=$TOTAL allowed=$ALLOWED denied=$DENIED no_license_metadata=$NO_INFO"

if [ "$NO_INFO" -gt 0 ]; then
  printf '%s  FAIL %s module(s) have no provable license:%s\n' "$GATE_LOG_PREFIX" "$NO_INFO" "$FAILURES" >&2
fi
if [ "$DENIED" -gt 0 ]; then
  printf '%s  FAIL %s module(s) violate the license policy:%s\n' "$GATE_LOG_PREFIX" "$DENIED" "$FAILURES" >&2
fi
if [ "$NO_INFO" -gt 0 ] || [ "$DENIED" -gt 0 ]; then
  die "license scan FAILED — see $REPORT_OUT and $CSV_OUT"
fi

ok "license scan passed: $ALLOWED/$TOTAL modules allowed; SBOM at $SPDX_OUT"
