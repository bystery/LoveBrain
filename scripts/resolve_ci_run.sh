#!/usr/bin/env bash
#
# scripts/resolve_ci_run.sh
#
# Turn "the CI run of this commit" into ONE workflow-run database ID, assert
# which of its jobs must be green, and hand the id to the next step — which is
# the only thing `gh run download` accepts as its positional argument.
#
# Why this exists (audit 2026-09-30 第1节 , second half): the release workflow
# used to run
#     gh run download "$GITHUB_SHA" --name test-xml-report ...
# `gh run download` takes a **run database id**, not a commit SHA, so that step
# could never fetch anything even when the run was green. The fix is not to
# invent an id: it is to resolve it from
#     (repo, workflow file, head_sha [, event] [, branch])
# and to fail LOUDLY when that key does not name exactly one run. There is
# deliberately no "take the newest / take the first" fallback — a release that
# quietly cites the wrong run's evidence is worse than a red job.
#
# Required jobs are checked by NAME against the resolved run's jobs, so the
# gate can demand exactly the jobs the current release model keeps in CI.
# Since 2026-10-07 (§13.2 slimming) `ui-test` is no longer a push-verify job —
# it lives in manual.yml as a dispatch-only job — and release.yml therefore
# passes `--require-job verify` only. The gate must NOT demand `upgrade-test`:
# since 2026-09-29 the
# signing + 覆盖安装 run locally, so on a push event that job exists only as
# `skipped`. Its conclusion is printed either way, it just does not gate.
#
# Usage:
#   bash scripts/resolve_ci_run.sh --commit <full-sha>
#        [--workflow ci.yml] [--repo owner/name]
#        [--event push] [--branch main] [--run-id <database-id>]
#        [--require-job <job-name>]... [--id-file dist/ci-run-id.txt]
#
# Exit codes:
#   0 a unique run was resolved and every --require-job concluded success
#   1 no run matched / more than one matched / a required job is not green /
#     an explicitly given --run-id does not belong to that commit+workflow
#   2 usage or tooling (no gh, no token, unparseable API answer)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/gate_lib.sh
. "$SCRIPT_DIR/lib/gate_lib.sh"

REPO="${GITHUB_REPOSITORY:-bystery/LoveBrain}"
WORKFLOW="ci.yml"
COMMIT=""
EVENT=""
BRANCH=""
RUN_ID=""
ID_FILE=""
declare -a REQUIRE_JOBS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --commit) COMMIT="$2"; shift 2 ;;
    --workflow) WORKFLOW="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --event) EVENT="$2"; shift 2 ;;
    --branch) BRANCH="$2"; shift 2 ;;
    --run-id) RUN_ID="$2"; shift 2 ;;
    --require-job) REQUIRE_JOBS+=("$2"); shift 2 ;;
    --id-file) ID_FILE="$2"; shift 2 ;;
    -h | --help) die_usage "see header of $0" ;;
    -*) die_usage "unknown option: $1" ;;
    *) die_usage "unexpected argument: $1" ;;
  esac
done

[ -n "$COMMIT" ] || die_usage "--commit <full 40-hex sha> is required (gh's head_sha filter does not accept an abbreviated sha)"
printf '%s' "$COMMIT" | grep -Eq '^[0-9a-f]{40}$' ||
  die_usage "--commit must be a full lowercase 40-hex sha, got: $COMMIT"
printf '%s' "$REPO" | grep -Eq '^[^/]+/[^/]+$' || die_usage "--repo must be owner/name, got: $REPO"
if [ -n "$RUN_ID" ] && ! printf '%s' "$RUN_ID" | grep -Eq '^[0-9]+$'; then
  die_usage "--run-id must be the numeric workflow run database id (run 36730225255, not 85, not a sha), got: $RUN_ID"
fi

require_cmd gh
if [ -z "${GH_TOKEN:-}${GITHUB_TOKEN:-}" ] && ! gh auth status >/dev/null 2>&1; then
  die_unverified "gh has no credential here — the run cannot be resolved, and a release must not proceed on an unverified citation"
fi

RUN_URL_BASE="${GITHUB_SERVER_URL:-https://github.com}/$REPO/actions"

if [ -n "$RUN_ID" ]; then
  # An explicit id is allowed (it is how a human disambiguates a re-run), but it
  # is NOT trusted: it must be a completed run of THIS workflow for THIS commit.
  log "validating explicit run id $RUN_ID against $WORKFLOW / $COMMIT"
  if ! META="$(gh api "repos/$REPO/actions/runs/$RUN_ID" \
    --jq '[.workflow_id, .head_sha, .status, (.conclusion // ""), .name] | @tsv' 2>&1)"; then
    printf '%s  FAIL gh could not read run %s of %s:\n%s\n' \
      "$GATE_LOG_PREFIX" "$RUN_ID" "$REPO" "$(printf '%s\n' "$META" | sed 's/^/          /')" >&2
    exit 1
  fi
  WF_ID="$(printf '%s' "$META" | cut -f1)"
  RUN_SHA="$(printf '%s' "$META" | cut -f2)"
  RUN_STATUS="$(printf '%s' "$META" | cut -f3)"
  RUN_CONCLUSION="$(printf '%s' "$META" | cut -f4)"
  RUN_NAME="$(printf '%s' "$META" | cut -f5)"
  if ! EXPECT_WF_ID="$(gh api "repos/$REPO/actions/workflows/$WORKFLOW" --jq '.id' 2>&1)"; then
    printf '%s  FAIL workflow file %s not found in %s:\n%s\n' \
      "$GATE_LOG_PREFIX" "$WORKFLOW" "$REPO" "$(printf '%s\n' "$EXPECT_WF_ID" | sed 's/^/          /')" >&2
    exit 1
  fi
  [ "$WF_ID" = "$EXPECT_WF_ID" ] ||
    die "run $RUN_ID belongs to workflow id $WF_ID ($RUN_NAME), not $WORKFLOW (id $EXPECT_WF_ID) — refusing to cite another workflow's evidence"
  [ "$RUN_SHA" = "$COMMIT" ] ||
    die "run $RUN_ID is for commit $RUN_SHA but the declared build commit is $COMMIT — the evidence would not describe this artifact"
  [ "$RUN_STATUS" = "completed" ] ||
    die "run $RUN_ID is still $RUN_STATUS — wait for it to finish instead of citing a partial result"
else
  QUERY="repos/$REPO/actions/workflows/$WORKFLOW/runs?per_page=100&head_sha=$COMMIT&status=completed"
  [ -z "$EVENT" ] || QUERY="$QUERY&event=$EVENT"
  [ -z "$BRANCH" ] || QUERY="$QUERY&branch=$BRANCH"
  log "resolving the $WORKFLOW run for commit $COMMIT of $REPO (event='${EVENT:-any}' branch='${BRANCH:-any}')"
  if ! MATCHES="$(gh api "$QUERY" \
    --jq ".workflow_runs[] | select(.head_sha == \"$COMMIT\") | [.id, .run_number, .event, .status, (.conclusion // \"\")] | @tsv" 2>&1)"; then
    printf '%s  FAIL the runs API call errored:\n%s\n' \
      "$GATE_LOG_PREFIX" "$(printf '%s\n' "$MATCHES" | sed 's/^/          /')" >&2
    exit 1
  fi
  MATCH_COUNT="$(printf '%s\n' "$MATCHES" | grep -c '[0-9]' )" || MATCH_COUNT=0
  if [ "$MATCH_COUNT" -eq 0 ]; then
    printf '%s  FAIL no completed %s run exists for commit %s%s%s.\n' \
      "$GATE_LOG_PREFIX" "$WORKFLOW" "$COMMIT" \
      "${EVENT:+ with event $EVENT}" "${BRANCH:+ on branch $BRANCH}" >&2
    printf '%s       Under that filter this commit produced nothing — which is exactly the\n' "$GATE_LOG_PREFIX" >&2
    printf '%s       2026-09-29 model: a tag push does not trigger ci.yml, and only a push to\n' "$GATE_LOG_PREFIX" >&2
    printf '%s       main does. Everything the API does know about this commit:\n' "$GATE_LOG_PREFIX" >&2
    if ANY="$(gh api "repos/$REPO/actions/workflows/$WORKFLOW/runs?per_page=100&head_sha=$COMMIT" \
      --jq ".workflow_runs[] | select(.head_sha == \"$COMMIT\") | [.id, .run_number, .event, .status, (.conclusion // \"\")] | @tsv" 2>&1)"; then
      if [ -n "$ANY" ]; then
        printf '%s\n' "$ANY" | awk -F'\t' '{ printf "         run id %s  #%s  event=%s  %s  %s\n", $1, $2, $3, $4, $5 }' >&2
      else
        printf '         (none at all — was this commit ever pushed to a branch CI watches?)\n' >&2
      fi
    else
      printf '         (the follow-up lookup itself failed: %s)\n' "$ANY" >&2
    fi
    printf '%s       Fix: push the commit to main, or re-dispatch with --event workflow_dispatch,\n' "$GATE_LOG_PREFIX" >&2
    printf '%s       or pass the intended run id explicitly. NOT a silent pass.\n' "$GATE_LOG_PREFIX" >&2
    exit 1
  fi
  if [ "$MATCH_COUNT" -gt 1 ]; then
    printf '%s  FAIL %d completed %s runs share commit %s%s%s — a release must cite ONE run:\n' \
      "$GATE_LOG_PREFIX" "$MATCH_COUNT" "$WORKFLOW" "$COMMIT" \
      "${EVENT:+ (event $EVENT)}" "${BRANCH:+ (branch $BRANCH)}" >&2
    printf '%s\n' "$MATCHES" | awk -F'\t' -v url="$RUN_URL_BASE" \
      '{ printf "         run id %s  #%s  event=%s  %s  %s  %s/runs/%s\n", $1, $2, $3, $4, $5, url, $1 }' >&2
    printf '%s       Pick one — usually the newest — and re-run with --run-id <id>\n' "$GATE_LOG_PREFIX" >&2
    printf '%s       (in the workflow that is the `ci_run` input). Choosing for you is how a\n' "$GATE_LOG_PREFIX" >&2
    printf '%s       wrong-run citation gets published, so this exits 1 instead.\n' "$GATE_LOG_PREFIX" >&2
    exit 1
  fi
  RUN_ID="$(printf '%s' "$MATCHES" | cut -f1)"
  RUN_CONCLUSION="$(printf '%s' "$MATCHES" | cut -f5)"
  log "resolved uniquely: run id $RUN_ID (conclusion: ${RUN_CONCLUSION:-unknown})"
fi

printf '%s  run page: %s/runs/%s\n' "$GATE_LOG_PREFIX" "$RUN_URL_BASE" "$RUN_ID"

# ── required jobs, by name, in the resolved run ──────────────────────────────
if ! JOBS="$(gh api "repos/$REPO/actions/runs/$RUN_ID/jobs?per_page=100" \
  --jq '.jobs[] | [.name, .status, (.conclusion // "")] | @tsv' 2>&1)"; then
  printf '%s  FAIL the jobs API call errored:\n%s\n' \
    "$GATE_LOG_PREFIX" "$(printf '%s\n' "$JOBS" | sed 's/^/          /')" >&2
  exit 1
fi
printf '%s  jobs of run %s:\n' "$GATE_LOG_PREFIX" "$RUN_ID"
printf '%s\n' "$JOBS" | sed 's/^/          /; s/\t/  /g' >&2

if [ "${#REQUIRE_JOBS[@]}" -eq 0 ]; then
  warn "no --require-job given: nothing about this run's quality was asserted, only its identity"
fi
for want in ${REQUIRE_JOBS[@]+"${REQUIRE_JOBS[@]}"}; do
  LINE="$(printf '%s\n' "$JOBS" | awk -F'\t' -v n="$want" '$1 == n { print; exit }')"
  if [ -z "$LINE" ]; then
    printf '%s  FAIL job "%s" does not exist in run %s — a missing gate is not a passed gate.\n' \
      "$GATE_LOG_PREFIX" "$want" "$RUN_ID" >&2
    printf '%s       Jobs that do exist are listed above; the workflow file may have renamed\n' "$GATE_LOG_PREFIX" >&2
    printf '%s       or dropped the step this release still depends on.\n' "$GATE_LOG_PREFIX" >&2
    exit 1
  fi
  JSTATUS="$(printf '%s' "$LINE" | cut -f2)"
  JCONC="$(printf '%s' "$LINE" | cut -f3)"
  if [ "$JCONC" != "success" ]; then
    printf '%s  FAIL job "%s" of run %s is "%s" (%s), not success.\n' \
      "$GATE_LOG_PREFIX" "$want" "$RUN_ID" "${JCONC:-none}" "$JSTATUS" >&2
    printf '%s       This gate covers the jobs CI still owns (build + tests). Signing and the\n' "$GATE_LOG_PREFIX" >&2
    printf '%s       覆盖安装 verification are NOT gated here — they are run locally (checklist\n' "$GATE_LOG_PREFIX" >&2
    printf '%s       steps 4-7) and their records are cross-checked by the release workflow.\n' "$GATE_LOG_PREFIX" >&2
    exit 1
  fi
  ok "required job green: $want"
done

if [ -n "$ID_FILE" ]; then
  mkdir -p "$(dirname "$ID_FILE")"
  printf '%s\n' "$RUN_ID" >"$ID_FILE"
  log "run id written: $ID_FILE"
fi
printf '%s\n' "$RUN_ID"
