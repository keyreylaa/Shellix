#!/bin/bash
# Shellix Bypass System — prepend-path + seccomp LD_PRELOAD sanitizer
#
# Dipanggil otomatis dari init.sh tiap login. Nambahin semua path tools yg
# umum dipake (bun, cargo, go, npm global, dll) ke $PATH, plus bersihin
# LD_PRELOAD biar tool binary (glibc) gak crash kena Bionic library.
#
# Ponytail: kalo suatu saat Android seccomp makin ketat, tambahin fallback
# ke PROOT_NO_SECCOMP di sini.

SHELLIX_PATH_ENTRIES=(
    "$HOME/.bun/bin"
    "$HOME/.npm-global/bin"
    "$HOME/.cargo/bin"
    "$HOME/go/bin"
    "/usr/local/sbin"
    "/usr/local/bin"
    "/usr/sbin"
    "/usr/bin"
    "/sbin"
    "/bin"
)

# --- PATH dedup ------------------------------------------------------------
for _dir in "${SHELLIX_PATH_ENTRIES[@]}"; do
    case ":$PATH:" in
        *:"$_dir":*) ;;
        *) PATH="$_dir:$PATH" ;;
    esac
done

export PATH

# --- LD_PRELOAD sanitizer ---------------------------------------------------
# Termux injects libtermux-exec-ld-preload.so (Bionic) into every process.
# glibc binaries (ubuntu) crash with it. Strip it when executing native glibc
# tools.
_sanitize_ld_preload() {
    local bin="$1"
    [[ -z "$bin" || ! -f "$bin" || ! -x "$bin" ]] && return

    # Cek kalo binary pake glibc (bukan Bionic)
    local interpreter
    interpreter=$(readelf -l "$bin" 2>/dev/null | awk '/INTERP/{getline; print $NF}' | tr -d '[]')
    [[ "$interpreter" == */ld-linux* ]] || return

    # Ini glibc binary — bersihin LD_PRELOAD
    LD_PRELOAD=""
    export LD_PRELOAD
}

# Hook buat command -v dan exec: kita bersihin LD_PRELOAD otomatis
# sebelum jalanin binary glibc
_shellix_exec_hook() {
    if [[ -n "$LD_PRELOAD" ]]; then
        local cmd="$1"
        local fullpath
        fullpath=$(command -v "$cmd" 2>/dev/null)
        if [[ -n "$fullpath" ]]; then
            _sanitize_ld_preload "$fullpath"
        fi
    fi
}

# Trap DEBUG biar tiap command jalan, kita cek dulu
if [[ -z "$SHELLIX_HOOK_SET" ]]; then
    export SHELLIX_HOOK_SET=1
    # DEBUG trap — jalan tiap user ketik command
    debug_hook() {
        _shellix_exec_hook "$BASH_COMMAND"
    }
    trap debug_hook DEBUG
fi
