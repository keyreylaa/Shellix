# Shellix Audit — Fix & Optimasi

## ✅ Selesai (working tree — belum di-commit)

- [x] **Autofill ANR** — `MainActivity.kt` — `importantForAutofill = NO_EXCLUDE_DESCENDANTS` biar gak freeze 5s tiap sentuh di Realme
- [x] **Build flags** — `CMakeLists.txt` — `-O3` + `-ffunction-sections -fdata-sections` + linker `--gc-sections` (dead code strip)
- [x] **`talloc_free with references` spam** — `execve/exit.c` — `talloc_unlink` ganti `TALLOC_FREE`
- [x] **`rm -rf` Operation not permitted** — `link2symlink.c` — tolerir `ENOENT` pas cleanup chain biar `rm -rf` gak spam error di npm/pnpm/bun

## ❌ Pending — Urutan Pengerjaan

### 1. Wrapper Installer Universal
Bikin script `shellix-prepend-path` yang:
- Nambahin semua path tools ke `$PATH` otomatis: `~/.bun/bin`, `~/.npm-global/bin`, `~/.cargo/bin`, `~/go/bin`, `/usr/local/bin`, `/usr/local/sbin`
- Bersihin `LD_PRELOAD` pas jalan tools biar gak crash kena seccomp SIGSYS / Bionic library conflict
- Fallback ke precompiled binary kalo seccomp blocking syscall vital (`set_robust_list`, dll)

### 2. Update init.sh / setup-user.sh
- Auto-source wrapper tiap login shell
- Tambah hook source ke `/etc/profile` atau `/etc/bash.bashrc`

### 3. Testing
- Verify `rm -rf ~/.bun/install/global` gak spam "Operation not permitted"
- Verify install tools (bun, npm i, pip, go) gak "Bad system call"
- Verify apt install gak error dpkg
