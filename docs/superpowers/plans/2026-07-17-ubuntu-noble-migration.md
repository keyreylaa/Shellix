# Shellix — Ubuntu Noble Migration Implementation Plan

> **For agentic workers:** Use subagent-driven-development or executing-plans to implement task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace the bundled Alpine Linux PRoot rootfs with a downloaded, pre-patched Ubuntu 24.04 (Noble) rootfs, including a first-run setup wizard that creates a sudo user, so Shellix boots into Ubuntu by default.

**Architecture:** Shellix boots a PRoot sandbox over a rootfs stored at `$PREFIX/local/ubuntu`. Currently Alpine is bundled in APK assets and extracted on first-run; we switch to downloading a pre-patched Ubuntu Base tarball (hardlinks converted to symlinks) from a GitHub Release, verify SHA256, extract, then run a setup wizard (username + password + sudo) before first shell.

**Tech Stack:** Kotlin, Jetpack Compose, Termux terminal-view/emulator v0.118.3, PRoot (native libproot.so/libloader.so), bash/apt/glibc userland.

## Global Constraints
- Package: com.rk.shellix ; applicationId com.shellix.terminal
- Do NOT attempt local gradle builds (env broken). Verify via GitHub Actions (.github/workflows/android.yml).
- Drop Alpine entirely. Only Ubuntu Noble + Android shell mode remain.
- Arch mapping: arm64-v8a->arm64, armeabi-v7a->armhf, x86_64->amd64.
- Rootfs downloaded first-run from OUR GitHub Release, SHA256 verified. APK stays slim (remove bundled alpine tarballs).

---

## Task 0: Prepare pre-patched Ubuntu rootfs (USER/OPS action, outside app)
**Files:** none in-app. Produces hosted artifacts.
- [ ] Build a proot-compatible Ubuntu Noble rootfs per arch (arm64, armhf, amd64). Recommended: use proot-distro or debootstrap then convert hardlinks to symlinks; pre-install: bash, sudo, ca-certificates, apt keys; write /etc/resolv.conf (nameserver 8.8.8.8); ensure /etc/apt/sources.list points to ubuntu ports for arm. Tar as ubuntu-noble-<arch>.tar.gz.
- [ ] Upload tarballs + SHA256SUMS to a GitHub Release in the user's repo.
- [ ] Record URLs; they go into Task 3 (RootfsSource).

## Task 1: FileUtil — rename alpine dirs to ubuntu
**Files:** Modify core/main/src/main/java/com/rk/libcommons/FileUtil.kt
- [ ] Rename `fun Context.alpineDir()` -> `fun Context.ubuntuDir()` returning localDir().child("ubuntu").
- [ ] Rename `fun Context.alpineHomeDir()` -> `fun Context.ubuntuHomeDir()` returning ubuntuDir().child("root").
- [ ] Grep for all usages of alpineDir/alpineHomeDir across core/main and update them (MkSession.kt uses alpineHomeDir; AlpineDocumentProvider uses alpineHomeDir).

## Task 2: WorkingMode — replace ALPINE with UBUNTU
**Files:** Modify core/main/src/main/java/com/rk/shellix/ui/screens/settings/Settings.kt and Settings persistence com/rk/settings/Settings.kt
- [ ] In object WorkingMode change `const val ALPINE = 0` -> `const val UBUNTU = 0` (keep ANDROID = 1). Update WorkingModeOption UI label "Alpine" -> "Ubuntu", strings.alpine_desc -> new strings.ubuntu_desc.
- [ ] Grep all references to WorkingMode.ALPINE -> WorkingMode.UBUNTU (MkSession.kt, TerminalScreen.kt add-session dialog, TerminalUtils.getNameOfWorkingMode).
- [ ] Add strings: ubuntu_desc "Ubuntu 24.04 (Noble) Linux environment" in core/resources strings.xml (+ translations optional).

## Task 3: RootfsSource — download URLs + SHA256
**Files:** Create core/main/src/main/java/com/rk/shellix/ui/screens/downloader/RootfsSource.kt
- [ ] Object RootfsSource with fun urlFor(arch: String): String and fun sha256For(arch: String): String, mapping arch (arm64/armhf/amd64) to the GitHub Release download URL + expected checksum from Task 0. Include a data class or map.

## Task 4: SetupScreen — download + verify + extract (replace bundled asset)
**Files:** Modify core/main/src/main/java/com/rk/shellix/ui/screens/downloader/SetupScreen.kt
- [ ] Replace ABI->alpineArch mapping with ABI->ubuntuArch (arm64-v8a->arm64, armeabi-v7a->armhf, x86_64->amd64).
- [ ] Replace `context.assets.open(assetName)` bundled copy with an HTTP download from RootfsSource.urlFor(arch) into context.filesDir.child("ubuntu.tar.gz") using OkHttp/HttpURLConnection with a progress callback (show percent in UI). Verify SHA256 against RootfsSource.sha256For(arch); delete + fail on mismatch.
- [ ] Update UI: show download progress (percent + MB), then "Extracting", then hand to Setup Wizard (Task 6) instead of directly to TerminalScreen.
- [ ] Handle no-network gracefully with retry button.

## Task 5: init scripts — Ubuntu boot
**Files:** Modify core/main/src/main/assets/init.sh and init-host.sh; delete alpine tarball assets.
- [ ] init-host.sh: replace ALPINE_DIR=$PREFIX/local/alpine -> UBUNTU_DIR=$PREFIX/local/ubuntu; tar source $PREFIX/files/alpine.tar.gz -> ubuntu.tar.gz; all `-b $PREFIX/local/alpine/tmp` and `-r $PREFIX/local/alpine` -> ubuntu.
- [ ] init.sh: change `/bin/ash` -> `/bin/bash`; PS1 `\u@reterm` -> `\u@shellix`; keep resolv.conf fallback; keep `source /etc/profile`.
- [ ] Delete assets: alpine-x86_64.tar.gz.rootfs, alpine-aarch64.tar.gz.rootfs, alpine-armhf.tar.gz.rootfs.

## Task 6: Setup Wizard — create sudo user
**Files:** Create core/main/src/main/java/com/rk/shellix/ui/screens/downloader/SetupWizard.kt ; Create asset core/main/src/main/assets/setup-user.sh ; wire into SetupScreen + NavHost.
- [ ] Compose wizard: TextField username (validate lowercase [a-z_][a-z0-9_-]*), password + confirm (min length), Finish button.
- [ ] On finish, run a PendingCommand (via MkSession/SessionService) that executes setup-user.sh inside the Ubuntu proot with env USERNAME/PASSWORD, then set a Settings flag `setup_user_done=true` and Settings `ubuntu_user`.
- [ ] setup-user.sh content: `#!/bin/bash` then: apt-get update -y; apt-get install -y sudo; useradd -m -s /bin/bash "$USERNAME"; usermod -aG sudo "$USERNAME"; echo "$USERNAME:$PASSWORD" | chpasswd; echo "$USERNAME ALL=(ALL) ALL" > /etc/sudoers.d/$USERNAME; chmod 0440 /etc/sudoers.d/$USERNAME.
- [ ] After wizard, MkSession default working dir becomes /home/$USERNAME and init.sh should `su - $USERNAME` (or set HOME) when a user exists. Add logic: if Settings.ubuntu_user set, init.sh execs `su - $user` else root shell.

## Task 7: Rename AlpineDocumentProvider -> UbuntuDocumentProvider
**DONE** - Renamed to UbuntuDocumentProvider.kt; class UbuntuDocumentProvider; import ubuntuHomeDir; manifest updated to com.rk.UbuntuDocumentProvider; no remaining code references.

## Task 8: Cleanup + strings + docs
- [ ] Grep zero remaining "alpine"/"Alpine"/"ash" (case-insensitive) in core/main + app (except historical comments). Update README features (Alpine -> Ubuntu 24.04).
- [ ] Update strings.xml android_desc already Shellix; add ubuntu_desc; remove alpine_desc if unused.

## Verification
- [ ] Push to GitHub; ensure .github/workflows/android.yml builds APK successfully (this is the real compile check).
- [ ] Manual device test: first-run downloads Ubuntu, extracts, wizard creates user, terminal boots into Ubuntu bash as the sudo user, `sudo apt update` works.

---
End of plan.
