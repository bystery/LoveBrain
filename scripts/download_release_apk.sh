#!/usr/bin/env bash
#
# scripts/download_release_apk.sh
#
# Downloads a published GitHub Release APK and PROVES it is the real artifact.
#
# Re-audit 2026-09-23 §3: the old-APK download used `|| echo "No v1.3.1 APK
# found, skipping upgrade test"` together with `continue-on-error: true`, so a
# failed download silently turned the whole upgrade gate into a no-op. Here a
# failed / unverified download is a hard failure.
#
# Verifications performed on the downloaded file:
#   * HTTP transport succeeded (curl -f / gh non-zero exit propagate)
#   * file exists, is non-empty and a valid ZIP
#   * SHA-256 equals the value pinned in scripts/signing-baseline.txt (when the
#     requested tag is the pinned reference tag)
#   * versionName inside the APK matches the requested tag
#   * signer certificate matches the pinned release fingerprint
#
# Usage:
#   bash scripts/download_release_apk.sh <tag> <out_dir> [owner/repo]
#        [--pattern '*.apk'] [--expected-sha256 <hex>] [--allow-any-sha256]
#
# Exit codes: 0 downloaded + verified, 1 failure, 2 usage.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"
BASELINE_FILE="$SCRIPT_DIR/signing-baseline.txt"

TAG=""
OUT_DIR=""
REPO="${GITHUB_REPOSITORY:-bystery/LoveBrain}"
PATTERN='*.apk'
EXPECTED_SHA256=""
ALLOW_ANY_SHA256=0

while [ $# -gt 0 ]; do
  case "$1" in
    --pattern) PATTERN="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --expected-sha256) EXPECTED_SHA256="$2"; shift 2 ;;
    --allow-any-sha256) ALLOW_ANY_SHA256=1; shift ;;
    -h | --help) die_usage "see header of $0" ;;
    -*) die_usage "unknown option: $1" ;;
    *)
      if [ -z "$TAG" ]; then TAG="$1"
      elif [ -z "$OUT_DIR" ]; then OUT_DIR="$1"
      else REPO="$1"
      fi
      shift
      ;;
  esac
done

[ -n "$TAG" ] || die_usage "a release tag is required (e.g. v1.3.1)"
[ -n "$OUT_DIR" ] || die_usage "an output directory is required"
printf '%s' "$REPO" | grep -Eq '^[^/]+/[^/]+$' || die_usage "repo must be owner/name, got: $REPO"

mkdir -p "$OUT_DIR"

# If the requested tag is the pinned reference release, its exact SHA-256 is
# mandatory — this is what makes "downloaded v1.3.1" a verifiable claim.
if [ -z "$EXPECTED_SHA256" ] && [ -f "$BASELINE_FILE" ]; then
  ref_tag="$(baseline_value reference_apk_tag "$BASELINE_FILE")"
  ref_sha="$(baseline_value reference_apk_sha256 "$BASELINE_FILE")"
  if [ "$TAG" = "$ref_tag" ] && [ -n "$ref_sha" ]; then
    EXPECTED_SHA256="$ref_sha"
    log "using pinned SHA-256 for $TAG from signing baseline"
  fi
fi
if [ -z "$EXPECTED_SHA256" ] && [ "$ALLOW_ANY_SHA256" -ne 1 ]; then
  die "no pinned SHA-256 for release tag $TAG. Add it to scripts/signing-baseline.txt (reference_apk_sha256) or pass --expected-sha256. Refusing to feed an unverified APK into the upgrade gate."
fi

# Pick the asset URL. gh first (works with a token on private repos), then the
# anonymous REST API via curl. Both paths propagate their exit status.
ASSET_URL=""
ASSET_NAME=""

LINE=""
if command -v gh >/dev/null 2>&1 && [ -n "${GITHUB_TOKEN:-}${GH_TOKEN:-}" ]; then
  log "resolving release assets with gh for $REPO $TAG"
  if LINE="$(gh api "repos/$REPO/releases/tags/$TAG" \
    --jq '.assets[] | select(.name | endswith(".apk")) | [.name, .browser_download_url] | @tsv' \
    2>/dev/null)"; then
    LINE="$(printf '%s\n' "$LINE" | head -1)"
    ASSET_NAME="$(printf '%s' "$LINE" | cut -f1)"
    ASSET_URL="$(printf '%s' "$LINE" | cut -f2)"
  else
    log "gh api lookup failed — falling back to the anonymous REST API"
    LINE=""
  fi
fi

if [ -z "$ASSET_URL" ]; then
  require_cmd curl
  log "resolving release assets with the GitHub REST API for $REPO $TAG"
  API_JSON=""
  if ! API_JSON="$(curl -fsSL --retry 3 --max-time 120 \
    -H 'Accept: application/vnd.github+json' \
    "https://api.github.com/repos/$REPO/releases/tags/$TAG")"; then
    die "GitHub API lookup failed for release $TAG of $REPO — the release is missing, the token lacks permission, or the network errored. The upgrade gate cannot be skipped."
  fi
  URLS="$(all_matches '"browser_download_url":[[:space:]]*"[^"]*"' "$API_JSON" |
    sed 's/^"browser_download_url":[[:space:]]*"//; s/"$//')" || URLS=""
  ASSET_URL="$(first_match 'https://[^" ]*\.apk' "$URLS")"
  if [ -z "$ASSET_URL" ]; then
    ASSET_COUNT="$(printf '%s\n' "$URLS" | grep -c .)" || ASSET_COUNT=0
    die "release $TAG of $REPO exposes no .apk asset ($ASSET_COUNT asset URL(s) found). The upgrade fixture is unavailable, so this job fails instead of skipping."
  fi
  ASSET_NAME="$(basename "$ASSET_URL")"
fi

OUT_FILE="$OUT_DIR/$ASSET_NAME"
log "downloading $ASSET_NAME from $ASSET_URL"
require_cmd curl
DOWNLOAD_OK=0
if curl -fSL --retry 5 --retry-delay 2 --retry-all-errors --max-time 900 \
  ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} \
  -o "$OUT_FILE" "$ASSET_URL"; then
  DOWNLOAD_OK=1
elif command -v gh >/dev/null 2>&1; then
  log "curl failed — retrying through gh release download"
  rm -f "$OUT_FILE"
  if gh release download "$TAG" --repo "$REPO" --pattern "$ASSET_NAME" --dir "$OUT_DIR"; then
    DOWNLOAD_OK=1
  fi
fi
if [ "$DOWNLOAD_OK" -ne 1 ]; then
  die "download of $ASSET_URL failed — the upgrade gate must not run on a missing fixture. Fix connectivity or the release asset; do NOT re-add '|| echo' to make this pass."
fi

require_file "$OUT_FILE" "downloaded APK"
validate_zip "$OUT_FILE"

ACTUAL_SHA256="$(sha256_of "$OUT_FILE")"
log "sha256: $ACTUAL_SHA256"
if [ "$ALLOW_ANY_SHA256" -ne 1 ] && [ -z "$EXPECTED_SHA256" ]; then
  die "internal error: no expected SHA-256 was resolved"
fi
if [ -n "$EXPECTED_SHA256" ] && [ "$ACTUAL_SHA256" != "$(normalize_fingerprint "$EXPECTED_SHA256")" ]; then
  die "downloaded $TAG APK SHA-256 is $ACTUAL_SHA256 but the pinned value is $(normalize_fingerprint "$EXPECTED_SHA256"). Refusing to use a fixture we cannot vouch for."
fi

# The APK must actually BE the tag it claims to be.
EXPECTED_VERSION="${TAG#v}"
bash "$SCRIPT_DIR/check_apk_metadata.sh" "$OUT_FILE" \
  --expected-version-name "$EXPECTED_VERSION" \
  --properties "$OUT_DIR/$ASSET_NAME.metadata.txt"

# And must carry the published release certificate.
if [ -f "$BASELINE_FILE" ]; then
  bash "$SCRIPT_DIR/verify_signing_continuity.sh" "$OUT_FILE" --baseline "$BASELINE_FILE"
fi

ok "release APK downloaded and verified: $OUT_FILE"
printf '%s\n' "$OUT_FILE"
