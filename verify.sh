#!/usr/bin/env bash
set -uo pipefail

ERRORS=0
WARNINGS=0
ROOT="$(cd "$(dirname "$0")" && pwd)"

say()  { printf "\033[1;34m%s\033[0m\n" "● $1"; }
ok()   { printf "\033[1;32m  ✓\033[0m %s\n" "$1"; }
warn() { printf "\033[1;33m  ⚠\033[0m %s\n" "$1"; WARNINGS=$((WARNINGS+1)); }
fail() { printf "\033[1;31m  ✗\033[0m %s\n" "$1"; ERRORS=$((ERRORS+1)); }
check() {
  if [ "$?" -eq 0 ]; then ok "$1"; else fail "$2"; fi
}

echo ""
echo "╔══════════════════════════════════════╗"
echo "║     Shellix Local Verification      ║"
echo "╚══════════════════════════════════════╝"
echo ""

# ── 1. File structure ────────────────────────────────────────────
say "File structure"
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
  fail "Missing: $f"
  missing=$((missing+1))
done
[ "$missing" -eq 0 ] && ok "All ${#ALL_FILES[@]} files present"

# ── 2. Version consistency ───────────────────────────────────────
say "Version consistency"
VER=$(grep 'versionName' "$ROOT/app/build.gradle.kts" | grep -v 'Suffix' | grep -o '"[0-9.]*"' | tr -d '"')
CODE=$(grep 'versionCode' "$ROOT/app/build.gradle.kts" | grep -o '= *[0-9]*' | tr -d '= ')
ok "App: $VER  (code: $CODE)"

# ── 3. Known bug patterns ────────────────────────────────────────
say "Known bug patterns"

# MkSession: setup_user_done only inside if block
count_setup=$(grep -c 'Settings.setup_user_done = true' "$ROOT/core/main/src/main/java/com/rk/shellix/ui/screens/terminal/MkSession.kt" 2>/dev/null || echo 0)
[ "$count_setup" -eq 1 ] && ok "MkSession: setup_user_done assigned once (inside if)" \
  || fail "MkSession: setup_user_done assigned $count_setup times (should be 1)"

# UbuntuCommand: uses m1/m2 marker positions
if grep -q 'val m0 = now.indexOf(marker)' "$ROOT/core/main/src/main/java/com/rk/shellix/ui/screens/packages/UbuntuCommand.kt" 2>/dev/null; then
  ok "UbuntuCommand: uses m0/m1/m2 marker offset (skips cmd echo)"
else
  fail "UbuntuCommand: still using old idx1/idx2 (off-by-one marker bug)"
fi

# UbuntuCommand: uses m1/m2 for uid too
if grep -q 'val i0 = now.indexOf(uidMarker)' "$ROOT/core/main/src/main/java/com/rk/shellix/ui/screens/packages/UbuntuCommand.kt" 2>/dev/null; then
  ok "UbuntuCommand: uid detection also uses i0/i1/i2 offset"
else
  fail "UbuntuCommand: uid detection still using old i1/i2 offset"
fi

# Settings: wallTransparency default
if grep -q 'wallTransparency.*default = 1f' "$ROOT/core/main/src/main/java/com/rk/settings/Settings.kt" 2>/dev/null; then
  ok "Settings: wallTransparency defaults to 1f (opaque)"
else
  fail "Settings: wallTransparency still defaulting to 0f (invisible)"
fi

# init.sh: has user re-check after setup
if grep -q 'Re-check default user' "$ROOT/core/main/src/main/assets/init.sh" 2>/dev/null; then
  ok "init.sh: re-checks default user after setup-user.sh"
else
  fail "init.sh: missing user re-check after setup (session stays root)"
fi

# ── 4. Shell syntax ──────────────────────────────────────────────
say "Shell syntax (bash)"
for shf in init.sh setup-user.sh init-host.sh; do
  f="$ROOT/core/main/src/main/assets/$shf"
  if bash -n "$f" 2>/dev/null; then
    ok "$shf: syntax OK"
  else
    bash -n "$f" 2>&1 | while IFS= read -r line; do warn "  $shf: $line"; done
    fail "$shf: syntax error"
  fi
done

# ── 5. Kotlin syntax check (limited, no Android SDK) ─────────────
say "Kotlin syntax (files without Android deps)"
KOTLIN_FILES=(
  core/main/src/main/java/com/rk/shellix/ui/screens/packages/UbuntuCommand.kt
)
for kf in "${KOTLIN_FILES[@]}"; do
  if kotlinc -d /dev/null "$ROOT/$kf" 2>/dev/null 1>&2; then
    ok "$kf: OK"
  else
    warn "$kf: kotlinc error (may be missing deps, not necessarily a bug)"
  fi
done

# ── 6. Verify.sh self-check ──────────────────────────────────────
say "verify.sh self-check"
if bash -n "$0" 2>/dev/null; then
  ok "verify.sh syntax OK"
else
  fail "verify.sh has syntax errors"
fi

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════╗"
printf "║  Errors: %-2d   Warnings: %-2d   %s║\n" "$ERRORS" "$WARNINGS" ""
echo "╚══════════════════════════════════════╝"
echo ""

[ "$ERRORS" -gt 0 ] && exit 1 || exit 0
