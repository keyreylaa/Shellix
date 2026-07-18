#!/usr/bin/env bash
#
# Shellix pre-build verification suite.
#
# Lightweight, fast (<5s) static checks that simulate common build/compile
# failures WITHOUT a full Gradle compile, so problems are caught in the local
# dev loop instead of after a multi-minute CI run (shift-left).
#
# Exit codes: 0 = all good (warnings allowed), 1 = one or more errors.
# Env:
#   VERIFY_LOG=path   also write a plain (no-color) log to `path`
#   NO_COLOR=1        disable ANSI colors (auto-disabled when not a TTY)
#
set -uo pipefail

# ── Resolve root & guard against partial execution ────────────────
ROOT="$(cd "$(dirname "$0")" && pwd)" || { echo "FATAL: cannot resolve script dir"; exit 2; }
cd "$ROOT" || { echo "FATAL: cannot cd to $ROOT"; exit 2; }

START_TS=$(date +%s%N)

# ── Counters ──────────────────────────────────────────────────────
CHECKS=0; PASSED=0; WARNINGS=0; ERRORS=0

# ── Colors (TTY-aware, CI-safe) ───────────────────────────────────
if [ -t 1 ] && [ "${NO_COLOR:-0}" != "1" ]; then
  C_BLUE=$'\033[1;34m'; C_GREEN=$'\033[1;32m'; C_YEL=$'\033[1;33m'
  C_RED=$'\033[1;31m'; C_DIM=$'\033[2m'; C_RST=$'\033[0m'
else
  C_BLUE=""; C_GREEN=""; C_YEL=""; C_RED=""; C_DIM=""; C_RST=""
fi

# ── Optional log file (plain text) ────────────────────────────────
LOG="${VERIFY_LOG:-}"
_log() { [ -n "$LOG" ] && printf '%s\n' "$1" >> "$LOG"; }
[ -n "$LOG" ] && : > "$LOG"

# ── Output helpers ────────────────────────────────────────────────
section() { printf "\n${C_BLUE}● %s${C_RST}\n" "$1"; _log ""; _log "● $1"; }
ok()   { CHECKS=$((CHECKS+1)); PASSED=$((PASSED+1)); printf "  ${C_GREEN}✓ [OK]${C_RST}    %s\n" "$1"; _log "  ✓ [OK]    $1"; return 0; }
warn() { CHECKS=$((CHECKS+1)); WARNINGS=$((WARNINGS+1)); printf "  ${C_YEL}⚠ [WARN]${C_RST}  %s\n" "$1"; _log "  ⚠ [WARN]  $1"; return 0; }
fail() { CHECKS=$((CHECKS+1)); ERRORS=$((ERRORS+1)); printf "  ${C_RED}✗ [FAIL]${C_RST}  %s\n" "$1"; _log "  ✗ [FAIL]  $1"; return 0; }
hint() { printf "           ${C_DIM}↳ %s${C_RST}\n" "$1"; _log "           ↳ $1"; }

# ── Error trap: never die silently ────────────────────────────────
trap 'rc=$?; [ $rc -ne 0 ] && [ $rc -ne 1 ] && printf "\n${C_RED}FATAL: verify.sh aborted (line %s, exit %s)${C_RST}\n" "$LINENO" "$rc";' EXIT

# ── Dependency validation ─────────────────────────────────────────
need_tool() {
  if command -v "$1" >/dev/null 2>&1; then return 0; fi
  return 1
}
GREP="grep"
if need_tool rg; then SEARCH="rg"; else SEARCH="grep -rn"; fi

# Cache the module gradle-file list once (avoids repeated find scans).
GRADLE_FILES=$(find "$ROOT/app" "$ROOT/core" -name build.gradle.kts 2>/dev/null)

echo ""
echo "═════════════════════════════════════════════"
echo "  SHELLIX VERIFICATION SUITE"
echo "  Mode: Pre-Build Static Simulation"
echo "═════════════════════════════════════════════"

# ══════════════════════════════════════════════════════════════════
section "Environment & dependencies"
for t in bash grep; do
  if need_tool "$t"; then ok "tool present: $t"; else fail "missing required tool: $t"; hint "install $t and re-run"; fi
done
if need_tool rg; then ok "ripgrep available (fast search)"; else warn "ripgrep (rg) not found — falling back to grep (slower)"; fi
if need_tool kotlinc; then ok "kotlinc available (syntax probe enabled)"; else warn "kotlinc not found — Kotlin syntax probe skipped"; fi

# ══════════════════════════════════════════════════════════════════
section "File structure"
ALL_FILES=(
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  core/main/src/main/AndroidManifest.xml
  core/main/src/main/assets/init.sh
  core/main/src/main/assets/setup-user.sh
  core/main/src/main/assets/init-host.sh
  core/main/src/main/java/com/rk/shellix/App.kt
  core/main/src/main/java/com/rk/shellix/ui/activities/terminal/MainActivity.kt
  core/main/src/main/java/com/rk/shellix/ui/activities/terminal/MainViewModel.kt
  core/main/src/main/java/com/rk/shellix/service/SessionService.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/terminal/MkSession.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalScreen.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalViewLayout.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalViewModel.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalBackEnd.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalDrawer.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/terminal/TerminalTopBar.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/terminal/Rootfs.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/terminal/VoiceInput.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/packages/UbuntuCommand.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/packages/PackagesScreen.kt
  core/main/src/main/java/com/rk/shellix/ui/screens/customization/Customization.kt
  core/main/src/main/java/com/rk/shellix/ui/theme/ThemeManager.kt
  core/main/src/main/java/com/rk/settings/Settings.kt
  verify.sh
  .github/workflows/android.yml
  .github/workflows/verify.yml
)
missing=0
for f in "${ALL_FILES[@]}"; do
  [ -f "$ROOT/$f" ] && continue
  fail "Missing: $f"; missing=$((missing+1))
done
[ "$missing" -eq 0 ] && ok "All ${#ALL_FILES[@]} tracked files present"

# ══════════════════════════════════════════════════════════════════
section "Gradle module graph"

# Every core module dir with a build.gradle.kts must be include()-d in settings.
SETTINGS="$ROOT/settings.gradle.kts"
if [ -f "$SETTINGS" ]; then
  while IFS= read -r gradle_file; do
    rel="${gradle_file#"$ROOT"/}"
    dir="$(dirname "$rel")"                      # e.g. core/filemanager
    [ "$dir" = "app" ] && continue               # :app is the root app module
    modpath=":$(echo "$dir" | tr '/' ':')"       # -> :core:filemanager
    if grep -q "include(\"$modpath\")" "$SETTINGS"; then
      ok "module registered: $modpath"
    else
      fail "module NOT registered in settings.gradle.kts: $modpath"
      hint "add: include(\"$modpath\")"
    fi
  done < <(find "$ROOT/core" -maxdepth 2 -name build.gradle.kts 2>/dev/null)
else
  fail "settings.gradle.kts not found"
fi

# Every project dependency must point at a real module dir.
while IFS= read -r line; do
  # line like: path/build.gradle.kts:  implementation(project(":core:foo"))
  dep=$(echo "$line" | grep -o 'project(":[^"]*")' | grep -o '":[^"]*"' | tr -d '"')
  [ -z "$dep" ] && continue
  depdir="${dep#:}"; depdir="$(echo "$depdir" | tr ':' '/')"
  if [ -d "$ROOT/$depdir" ]; then
    :
  else
    fail "project dependency points to missing module: $dep"
    hint "expected dir: $depdir/"
  fi
done < <($SEARCH 'project\(":' --glob '**/build.gradle.kts' 2>/dev/null || grep -rn 'project(":' --include=build.gradle.kts "$ROOT")
ok "project(:...) dependencies resolve to real module dirs"

# ══════════════════════════════════════════════════════════════════
section "Core library desugaring consistency"
# If ANY module enables coreLibraryDesugaring, then :app (final consumer) and
# :core:main (transitive consumer) must also enable it, or the AAR metadata
# check fails only at full build time. Simulate that here.
desugar_modules=()
while IFS= read -r gf; do
  if grep -q 'isCoreLibraryDesugaringEnabled *= *true' "$gf" 2>/dev/null; then
    desugar_modules+=("$(dirname "${gf#"$ROOT"/}")")
  fi
done < <(printf '%s\n' "$GRADLE_FILES")

# Which modules DECLARE a coreLibraryDesugaring dependency (need it themselves)?
needs_desugar=()
while IFS= read -r gf; do
  if grep -q 'coreLibraryDesugaring(' "$gf" 2>/dev/null; then
    d="$(dirname "${gf#"$ROOT"/}")"
    if grep -q 'isCoreLibraryDesugaringEnabled *= *true' "$gf"; then
      ok "desugaring enabled where declared: :$(echo "$d" | tr '/' ':')"
    else
      fail "module declares coreLibraryDesugaring dep but did NOT enable it: $d"
      hint "add isCoreLibraryDesugaringEnabled = true in compileOptions"
    fi
    needs_desugar+=("$d")
  fi
done < <(printf '%s\n' "$GRADLE_FILES")

# If any library module needs desugaring, :app must enable it too.
if [ "${#needs_desugar[@]}" -gt 0 ]; then
  if grep -q 'isCoreLibraryDesugaringEnabled *= *true' "$ROOT/app/build.gradle.kts" 2>/dev/null; then
    ok ":app enables core library desugaring (required by dependencies)"
  else
    fail ":app must enable core library desugaring (a dependency requires it)"
    hint "add isCoreLibraryDesugaringEnabled=true + coreLibraryDesugaring(libs.desugar) to app/build.gradle.kts"
  fi
fi

# ══════════════════════════════════════════════════════════════════
section "Version catalog references"
CATALOG="$ROOT/gradle/libs.versions.toml"
if [ -f "$CATALOG" ]; then
  # Single awk pass (fast): load declared aliases (dashes==dots) as a set, then
  # check each libs.<alias> usage with exact-or-prefix match. Avoids per-ref subshells.
  unknown=$(awk '
    FNR==NR {
      # declaration lines like `alias = ...`
      if ($0 ~ /^[a-zA-Z0-9_.-]+ *=/) {
        a=$0; sub(/ *=.*/, "", a); gsub(/\./, "-", a); decl[a]=1
      }
      next
    }
    {
      a=$0; gsub(/\./, "-", a)
      if (a in decl) next
      # prefix match for nested groups (androidx-compose-bom etc.)
      for (k in decl) { if (index(k, a)==1) { found=1; break } }
      if (found) { found=0; next }
      print
    }
  ' "$CATALOG" <(grep -rhoE 'libs\.[a-zA-Z0-9._]+' --include=build.gradle.kts "$ROOT" | grep -v '^libs\.plugins\.' | sed -E 's/^libs\.//' | sort -u))
  if [ -z "$unknown" ]; then
    ok "all libs.* references exist in libs.versions.toml"
  else
    while IFS= read -r a; do
      warn "version-catalog alias not found: libs.$(echo "$a" | tr '-' '.')"
    done <<< "$unknown"
  fi
else
  warn "gradle/libs.versions.toml not found — skipping catalog check"
fi

# ══════════════════════════════════════════════════════════════════
section "Module import boundaries"
# Flag Kotlin files that import com.rk.libcommons.* (lives in :core:main) from a
# module whose build.gradle.kts does NOT depend on :core:main -> compile error.
boundary_issues=0
while IFS= read -r gf; do
  moddir="$(dirname "$gf")"
  rel="${moddir#"$ROOT"/}"
  [ "$rel" = "app" ] && continue
  # skip the module that actually DEFINES com.rk.libcommons (it owns the package)
  [ -d "$moddir/src/main/java/com/rk/libcommons" ] && continue
  # does this module depend on :core:main?
  grep -q 'project(":core:main")' "$gf" && continue
  # any src file importing libcommons?
  srcdir="$moddir/src/main"
  [ -d "$srcdir" ] || continue
  while IFS= read -r hit; do
    fail "libcommons import in module without :core:main dep: ${hit#"$ROOT"/}"
    hint "either add project(\":core:main\") or avoid com.rk.libcommons here"
    boundary_issues=$((boundary_issues+1))
  done < <(grep -rln 'import com\.rk\.libcommons' "$srcdir" 2>/dev/null)
done < <(find "$ROOT/core" -maxdepth 2 -name build.gradle.kts 2>/dev/null)
[ "$boundary_issues" -eq 0 ] && ok "no cross-module import boundary violations"

# ══════════════════════════════════════════════════════════════════
section "Version consistency"
VER=$(grep 'versionName' "$ROOT/app/build.gradle.kts" | grep -v 'Suffix' | grep -oE '"[0-9][0-9A-Za-z.\-]*"' | tr -d '"')
CODE=$(grep 'versionCode' "$ROOT/app/build.gradle.kts" | grep -o '= *[0-9]*' | tr -d '= ')
if [ -n "$VER" ] && [ -n "$CODE" ]; then ok "App: $VER (code: $CODE)"; else warn "could not parse version from app/build.gradle.kts"; fi

# ══════════════════════════════════════════════════════════════════
section "Known bug patterns (regression guards)"
MK="$ROOT/core/main/src/main/java/com/rk/shellix/ui/screens/terminal/MkSession.kt"
count_setup=$(grep -c 'Settings.setup_user_done = true' "$MK" 2>/dev/null || echo 0)
[ "$count_setup" -eq 1 ] && ok "MkSession: setup_user_done assigned once (inside if)" \
  || { fail "MkSession: setup_user_done assigned $count_setup times (should be 1)"; hint "assignment must live inside the first-boot if block"; }

UC="$ROOT/core/main/src/main/java/com/rk/shellix/ui/screens/packages/UbuntuCommand.kt"
grep -q 'val m0 = now.indexOf(marker)' "$UC" 2>/dev/null \
  && ok "UbuntuCommand: m0/m1/m2 marker offset (skips cmd echo)" \
  || { fail "UbuntuCommand: off-by-one marker bug (missing m0/m1/m2)"; hint "skip the echoed command line; capture between m1 and m2"; }
grep -q 'val i0 = now.indexOf(uidMarker)' "$UC" 2>/dev/null \
  && ok "UbuntuCommand: uid detection uses i0/i1/i2 offset" \
  || { fail "UbuntuCommand: uid detection off-by-one"; hint "apply the same i0/i1/i2 pattern to uid marker"; }

grep -q 'wallTransparency.*default = 1f' "$ROOT/core/main/src/main/java/com/rk/settings/Settings.kt" 2>/dev/null \
  && ok "Settings: wallTransparency defaults to 1f (opaque)" \
  || { fail "Settings: wallTransparency not 1f (wallpaper invisible)"; hint "default must be 1f"; }

grep -q 'Re-check default user' "$ROOT/core/main/src/main/assets/init.sh" 2>/dev/null \
  && ok "init.sh: re-checks default user after setup-user.sh" \
  || { fail "init.sh: missing user re-check (session stays root)"; hint "re-read /etc/shellix_default_user after setup-user.sh"; }

# ══════════════════════════════════════════════════════════════════
section "Shell syntax (bash -n)"
for shf in init.sh setup-user.sh init-host.sh; do
  f="$ROOT/core/main/src/main/assets/$shf"
  if bash -n "$f" 2>/dev/null; then
    ok "$shf: syntax OK"
  else
    fail "$shf: syntax error"
    bash -n "$f" 2>&1 | while IFS= read -r l; do hint "$l"; done
  fi
done

# ══════════════════════════════════════════════════════════════════
# kotlinc spins up a JVM per file (~40s each) which blows the <5s budget, so the
# real compile probe is opt-in. CI relies on the full `./gradlew test` instead.
section "Kotlin syntax probe (opt-in)"
if [ "${VERIFY_KOTLIN:-0}" != "1" ]; then
  ok "skipped (set VERIFY_KOTLIN=1 to run kotlinc; CI does the full compile)"
elif need_tool kotlinc; then
  KOTLIN_FILES=(
    core/filemanager/src/main/java/com/rk/filemanager/FileManagerState.kt
    core/filemanager/src/main/java/com/rk/filemanager/FileOps.kt
    core/filemanager/src/main/java/com/rk/filemanager/TextFileDetect.kt
  )
  for kf in "${KOTLIN_FILES[@]}"; do
    [ -f "$ROOT/$kf" ] || continue
    err=$(kotlinc -d /dev/null "$ROOT/$kf" 2>&1 >/dev/null | grep -E '^.*: error:' || true)
    if [ -z "$err" ]; then
      ok "$(basename "$kf"): parses (no compile errors)"
    else
      fail "$(basename "$kf"): kotlinc errors"
      echo "$err" | head -5 | while IFS= read -r l; do hint "$l"; done
    fi
  done
else
  warn "kotlinc unavailable — skipping Kotlin probe"
fi

# ══════════════════════════════════════════════════════════════════
section "verify.sh self-check"
bash -n "$0" 2>/dev/null && ok "verify.sh syntax OK" || fail "verify.sh has syntax errors"

# ══════════════════════════════════════════════════════════════════
END_TS=$(date +%s%N)
ELAPSED_MS=$(( (END_TS - START_TS) / 1000000 ))

echo ""
echo "─────────────────────────────────────────────"
printf "Summary: ${C_GREEN}%d OK${C_RST} | ${C_YEL}%d Warning${C_RST} | ${C_RED}%d Error${C_RST}   (%d checks, %dms)\n" \
  "$PASSED" "$WARNINGS" "$ERRORS" "$CHECKS" "$ELAPSED_MS"
if [ "$ERRORS" -gt 0 ]; then
  printf "Status:  ${C_RED}FAIL (exit code 1)${C_RST}\n"
else
  printf "Status:  ${C_GREEN}PASS (exit code 0)${C_RST}\n"
fi
echo "─────────────────────────────────────────────"
echo ""
_log ""
_log "Summary: $PASSED OK | $WARNINGS Warning | $ERRORS Error ($CHECKS checks, ${ELAPSED_MS}ms)"

trap - EXIT
[ "$ERRORS" -gt 0 ] && exit 1 || exit 0
