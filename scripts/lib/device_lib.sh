# shellcheck shell=bash
#
# Shared adb helpers for the on-device gate scripts.
#
# Sourced by scripts/write_upgrade_fixture.sh and scripts/assert_upgrade_state.sh.
# Every helper propagates a real device failure; nothing here degrades to a
# warning. Accessing the app data directory of a NON-debuggable (release) APK
# requires a root shell — if neither run-as nor root is available the caller
# dies loudly instead of skipping the assertions.

PKG="${PKG:-com.lovebrain.app}"
DEVICE_MODE=""
DATA_ROOT="/data/data/$PKG"

init_device_mode() {
  require_device
  local uid
  if uid="$(adb shell "run-as $PKG id -u" 2>/dev/null | tr -d '\r')" && [ -n "$uid" ]; then
    DEVICE_MODE="run-as"
    log "app data access via run-as (uid $uid) — installed APK is debuggable"
    return 0
  fi
  log "run-as refused ($PKG is not debuggable) — escalating the adb shell to root"
  if ! adb root >/dev/null 2>&1; then
    log "adb root reported a failure; continuing to check the real shell uid"
  fi
  adb wait-for-device
  local sh_uid
  sh_uid="$(adb shell id -u 2>/dev/null | tr -d '\r')" || sh_uid=""
  if [ "$sh_uid" = "0" ]; then
    DEVICE_MODE="root"
    log "app data access via root shell"
    return 0
  fi
  die "cannot access $DATA_ROOT: $PKG is not debuggable (run-as denied) and the adb shell is not root (id -u returned '${sh_uid:-unknown}'). Use a rootable system image (google_apis, not google_apis_playstore) — the upgrade fixture cannot be written or read any other way."
}

dev_capture() {
  # dev_capture <cmd> — POSIX sh command run with the app data dir as cwd.
  # NOTE: the command string is wrapped in single quotes for `run-as … sh -c`,
  # so it must not itself contain single quotes — assert_safe_path enforces that.
  local cmd="$1"
  if [ "$DEVICE_MODE" = "run-as" ]; then
    adb shell "run-as $PKG sh -c '$cmd'" | tr -d '\r'
  else
    adb shell "cd $DATA_ROOT && $cmd" | tr -d '\r'
  fi
}

assert_safe_path() {
  local p="$1"
  case "$p" in
    *\"* | *\'* | *\$* | *\`* | *\\* | *\ * | *\;* | *\|* | *\&* | *\** | *\?* | *\<* | *\>*)
      die "path contains shell metacharacters and cannot be sent to the device: $p" ;;
  esac
  [ -n "$p" ] || die "empty path passed to a device command"
}


dev_run() {
  # dev_run <cmd> — like dev_capture but the exit status is what matters.
  local cmd="$1"
  if [ "$DEVICE_MODE" = "run-as" ]; then
    adb shell "run-as $PKG sh -c '$cmd'" >/dev/null
  else
    adb shell "cd $DATA_ROOT && $cmd" >/dev/null
  fi
}

device_yes_no() {
  # device_yes_no <cmd> — echoes YES when <cmd> succeeds on device, else NO.
  local cmd="$1" out
  out="$(dev_capture "$cmd && echo YES" )" || out=""
  if printf '%s' "$out" | grep -q '^YES$'; then
    printf 'YES\n'
  else
    printf 'NO\n'
  fi
}

device_push_mode() {
  # device_push_mode <local> <remote-relpath> — stages through /data/local/tmp,
  # which both the shell user and a run-as'd app can read.
  local local_file="$1" rel="$2"
  assert_safe_path "$rel"
  local stage
  stage="$(device_stage_path "$local_file")"
  if ! adb push "$local_file" "$stage" >/dev/null; then
    die "adb push $local_file -> $stage failed"
  fi
  if ! adb shell "chmod 666 $stage" >/dev/null; then
    die "could not make $stage readable by the app"
  fi
  local dir
  dir="$(dirname "$rel")"
  if [ "$dir" != "." ]; then
    assert_safe_path "$dir"
    if ! dev_run "mkdir -p $dir"; then
      die "could not create $DATA_ROOT/$dir on the device"
    fi
  fi
  if ! dev_run "cp $stage $rel"; then
    die "could not copy $stage to $DATA_ROOT/$rel"
  fi
  if ! adb shell "rm -f $stage" >/dev/null; then
    die "could not remove the staged file $stage"
  fi
  fixup_ownership "$rel"
}

device_stage_path() {
  # A collision-free /data/local/tmp path for a local file.
  printf '/data/local/tmp/gate_%s_%s\n' "$$" "$(basename "$1")"
}

device_pull_file() {
  # device_pull_file <rel-path> <local-dest> — copy an app-private file off the
  # device for evidence, via the same /data/local/tmp staging.
  local rel="$1" dest="$2"
  assert_safe_path "$rel"
  local stage
  stage="$(device_stage_path "$dest")"
  if ! dev_run "cp $rel $stage"; then
    die "could not copy $DATA_ROOT/$rel to $stage for extraction"
  fi
  if ! adb shell "chmod 666 $stage" >/dev/null; then
    die "could not make $stage readable"
  fi
  if ! adb pull "$stage" "$dest" >/dev/null; then
    die "adb pull $stage -> $dest failed"
  fi
  adb shell "rm -f $stage" >/dev/null
}

fixup_ownership() {
  # Root-written files land as root:root and the app cannot read them.
  local rel="$1"
  [ "$DEVICE_MODE" = "root" ] || return 0
  assert_safe_path "$rel"
  local owner
  if ! owner="$(adb shell "stat -c %u:%g\ $DATA_ROOT" | tr -d '\r')"; then
    die "cannot stat the ownership of $DATA_ROOT"
  fi
  owner="$(printf '%s' "$owner" | tr -d ' \r')"
  case "$owner" in
    [0-9]*:[0-9]*) ;;
    *) die "unexpected owner string for $DATA_ROOT: '$owner'" ;;
  esac
  if ! dev_run "chown -R $owner $rel"; then
    die "could not chown $DATA_ROOT/$rel to $owner — the app could not read the fixture"
  fi
  if ! dev_run "chmod -R u+rw $rel"; then
    die "could not chmod $DATA_ROOT/$rel"
  fi
  local rc_tool
  rc_tool="$(adb shell "command -v restorecon" | tr -d '\r')" || rc_tool=""
  rc_tool="$(first_nonempty_line "$rc_tool")"
  if [ -n "$rc_tool" ]; then
    if ! dev_run "restorecon -R $rel"; then
      die "restorecon failed for $DATA_ROOT/$rel — SELinux would deny the app access to the fixture"
    fi
  else
    log "restorecon is not available on this image; assuming a permissive policy"
  fi
}

device_install() {
  # device_install <apk> — a fresh install; a failure fails the job. The audited
  # workflow ran `adb install fixtures/*.apk || true`, so a broken fixture APK
  # silently turned the whole upgrade gate into a no-op.
  local apk="$1"
  require_file "$apk" "APK to install"
  log "installing $apk"
  if ! adb install "$apk"; then
    die "adb install failed for $apk"
  fi
  ok "installed $apk"
}

device_upgrade_install() {
  # device_upgrade_install <apk> —覆盖安装: -r keeps /data/data intact, -d allows
  # a downgrade-only debug path. Any failure (SIGNATURE_MISMATCH included) dies.
  local apk="$1"
  require_file "$apk" "candidate APK"
  log "upgrade-installing (preserving data) $apk"
  local out
  if ! out="$(adb install -r -d "$apk" 2>&1)"; then
    printf '%s\n' "$out" >&2
    if printf '%s' "$out" | grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE\|SIGNATURES_DO_NOT_MATCH'; then
      die "upgrade install of $apk was rejected: the candidate is signed with a different certificate than the installed $PKG. Signature continuity is broken (see scripts/verify_signing_continuity.sh)."
    fi
    die "adb install -r failed for $apk — a data-preserving upgrade install did not happen, so the upgrade gate cannot pass"
  fi
  printf '%s\n' "$out"
  ok "upgrade-installed $apk with data preserved"
}

device_current_version_name() {
  local out
  out="$(adb shell "dumpsys package $PKG" 2>/dev/null | tr -d '\r')" ||
    die "dumpsys package $PKG failed — is $PKG installed?"
  first_match 'versionName=[^ ]*' "$out" | sed 's/^versionName=//'
}

device_current_version_code() {
  local out
  out="$(adb shell "dumpsys package $PKG" 2>/dev/null | tr -d '\r')" ||
    die "dumpsys package $PKG failed — is $PKG installed?"
  first_match 'versionCode=[^ ]*' "$out" | sed 's/^versionCode=//; s/[^0-9].*//'
}

device_start_activity() {
  # device_start_activity <pkg/class> — must report a clean start.
  local comp="$1" out
  if ! out="$(adb shell "am start -W -n $comp" 2>&1 | tr -d '\r')"; then
    printf '%s\n' "$out" >&2
    die "am start -W -n $comp failed"
  fi
  printf '%s\n' "$out"
  if printf '%s' "$out" | grep -qE 'Error: |Exception|does not exist|Permission Denial'; then
    printf '%s\n' "$out" >&2
    die "activity $comp did not start cleanly"
  fi
  local status
  status="$(printf '%s' "$out" | sed -n 's/^Status:[[:space:]]*//p')"
  if [ -n "$status" ] && [ "$status" != "ok" ]; then
    die "activity $comp reported Status: $status (expected ok)"
  fi
}

device_resumed_component() {
  local out line comp
  if ! out="$(adb shell "dumpsys activity activities" 2>/dev/null | tr -d '\r')"; then
    die "dumpsys activity activities failed"
  fi
  line="$(first_match 'mResumedActivity.*|ResumedActivity:.*|mFocusedActivity.*' "$out")" || line=""
  [ -n "$line" ] || die "dumpsys reported no resumed activity — the foreground state cannot be verified"
  comp="$(first_match '[A-Za-z0-9_.-]+/[A-Za-z0-9_.$/-]+' "$line")" || comp=""
  [ -n "$comp" ] || die "could not parse the resumed component from: $line"
  printf '%s\n' "$comp"
}

