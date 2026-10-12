#!/usr/bin/env bash
# One-time setup for the EC2 instance.
# Run as root (or with sudo) on a fresh Amazon Linux 2023 or Ubuntu 22.04 instance.
#
# Usage:
#   sudo bash setup-ec2.sh
#
# After this script completes:
#   1. Log out and back in (or run: newgrp docker) so your user can reach Docker.
#   2. cd /opt/fnbx
#   3. cp .env.aws.example .env.aws && vim .env.aws   # fill in all values
#   4. Follow docs/aws-ec2.md to run the migration and start services.

set -euo pipefail

APP_DIR=/opt/fnbx
APP_USER=${SUDO_USER:-ubuntu}
REPO_URL=${REPO_URL:-https://github.com/comfy-case-close/comfy_case_close.git}
BRANCH=${BRANCH:-dev-service-base}

echo "==> Detecting OS"
if [ -f /etc/os-release ]; then
  . /etc/os-release
  OS_ID=$ID
else
  echo "Cannot detect OS" >&2; exit 1
fi

echo "==> Installing Docker"
case "$OS_ID" in
  amzn)
    dnf install -y docker git
    systemctl enable --now docker
    ;;
  ubuntu|debian)
    apt-get update -q
    apt-get install -y ca-certificates curl gnupg git
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
      | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
      https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
      > /etc/apt/sources.list.d/docker.list
    apt-get update -q
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl enable --now docker
    ;;
  *)
    echo "Unsupported OS: $OS_ID. Install Docker manually." >&2; exit 1 ;;
esac

echo "==> Adding $APP_USER to docker group"
usermod -aG docker "$APP_USER"

echo "==> Cloning repository to $APP_DIR"
if [ -d "$APP_DIR/.git" ]; then
  echo "    Repository already present — skipping clone"
else
  git clone --branch "$BRANCH" "$REPO_URL" "$APP_DIR"
fi
chown -R "$APP_USER:$APP_USER" "$APP_DIR"

echo "==> Creating secrets directory"
mkdir -p "$APP_DIR/secrets"
chown "$APP_USER:$APP_USER" "$APP_DIR/secrets"
chmod 700 "$APP_DIR/secrets"

echo ""
echo "==> Setup complete."
echo ""
echo "Next steps:"
echo "  1. Log out and back in, or run:  newgrp docker"
echo "  2. cd $APP_DIR"
echo "  3. cp .env.aws.example .env.aws"
echo "  4. Fill in all values in .env.aws"
echo "  5. Follow docs/aws-ec2.md to run the migration and start services"
