set -e  # Exit immediately on Failure

if [ -f /etc/shellix_default_user ]; then
  DEFAULT_USER=$(cat /etc/shellix_default_user)
  if id "$DEFAULT_USER" >/dev/null 2>&1; then exec su - "$DEFAULT_USER"; fi
fi

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin
export HOME=/root

if [ ! -s /etc/resolv.conf ]; then
    echo "nameserver 8.8.8.8" > /etc/resolv.conf
fi

# Run first-boot user setup if creds were provided by the app.
# setup-user.sh is copied into $BIN (local/bin) by the app alongside init.
if [ -n "$SETUP_USER" ] && [ -n "$SETUP_PASS" ] && [ ! -f /etc/shellix_default_user ]; then
    if [ -f "$BIN/setup-user.sh" ]; then
        sh "$BIN/setup-user.sh"
    elif [ -f "$PREFIX/files/setup-user.sh" ]; then
        sh "$PREFIX/files/setup-user.sh"
    fi
fi


export PS1='\[\033[01;32m\]\u@shellix\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
# shellcheck disable=SC2034
export PIP_BREAK_SYSTEM_PACKAGES=1

#fix linker warning
if [[ ! -f /linkerconfig/ld.config.txt ]];then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi

if [ "$#" -eq 0 ]; then
    source /etc/profile
export PS1='\[\033[01;32m\]\u@shellix\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
    cd $HOME
    /bin/bash
else
    exec "$@"
fi