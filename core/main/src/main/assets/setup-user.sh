#!/bin/bash
set +e
USERNAME="${SETUP_USER:-shellix}"
PASSWORD="${SETUP_PASS:-shellix}"
if id "$USERNAME" >/dev/null 2>&1; then
  echo "$USERNAME" > /etc/shellix_default_user
  exit 0
fi
# ubuntu-base is minimal: no sudo/nano/curl by default, apt needs update first.
# apt steps are best-effort: a failed network at first boot must NOT prevent the
# user from being created (otherwise the session stays root forever).
apt-get update -y || true
apt-get install -y sudo nano curl wget || true
apt-get upgrade -y || true
useradd -m -s /bin/bash "$USERNAME"
usermod -aG sudo "$USERNAME" || true
echo "$USERNAME:$PASSWORD" | chpasswd
echo "$USERNAME ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/$USERNAME
chmod 0440 /etc/sudoers.d/$USERNAME || true
# Always persist the default user so init.sh execs into it on next boot.
echo "$USERNAME" > /etc/shellix_default_user
