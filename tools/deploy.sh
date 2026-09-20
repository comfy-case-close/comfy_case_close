#!/usr/bin/env bash
# Cho vao pipeline that (ECS / k8s / systemd). O day chi la cho giu cho.
set -euo pipefail
SVC="${1:?ten service}"; SHA="${2:?git sha}"
echo "==> build image  fnbx/${SVC}:${SHA}"
echo "==> deploy       fnbx/${SVC}:${SHA}"
echo "    LUU Y: migration o db/ PHAI da chay xong truoc buoc nay (ADR-0003 #7)."
