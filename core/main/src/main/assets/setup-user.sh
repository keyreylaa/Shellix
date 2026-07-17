#!/bin/bash
set -e
USERNAME="${SETUP_USER:-shellix}"
PASSWORD="${SETUP_PASS:-shellix}"
if id "$USERNAME" >/dev/null 2>&1; then
  echo "$USERNAME" > /etc/shellix_default_user
  exit 0
fi
# ubuntu-base is minimal: no sudo/nano/curl by default, apt needs update first
apt-get update -y
apt-get install -y sudo nano curl wget
apt-get upgrade -y || true
useradd -m -s /bin/bash "$USERNAME"
usermod -aG sudo "$USERNAME"
echo "$USERNAME:$PASSWORD" | chpasswd
echo "$USERNAME ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/$USERNAME
chmod 0440 /etc/sudoers.d/$USERNAME
echo "$USERNAME" > /etc/shellix_default_user
