#!/usr/bin/env bash
# Deploy one service to the EC2 instance via SSH.
# Called by .github/workflows/deploy.yml after a successful build.
#
# Usage:
#   ./tools/deploy.sh <service-name> <git-sha>
#
# Required environment variables (set as GitHub Actions secrets):
#   EC2_HOST      — public IP or DNS of the EC2 instance
#   EC2_USER      — SSH user (e.g. ubuntu or ec2-user)
#   EC2_SSH_KEY   — private key content (stored as a GitHub secret)
#
# The script:
#   1. Writes the SSH key to a temp file.
#   2. SSHes into the EC2 instance.
#   3. Pulls the latest code.
#   4. Rebuilds and restarts only the changed service.

set -euo pipefail

SVC="${1:?service name required (e.g. cashclose)}"
SHA="${2:?git sha required}"

: "${EC2_HOST:?EC2_HOST is not set}"
: "${EC2_USER:?EC2_USER is not set}"
: "${EC2_SSH_KEY:?EC2_SSH_KEY is not set}"

APP_DIR=/opt/fnbx
COMPOSE="docker compose -f $APP_DIR/docker-compose.aws.yml"

KEY_FILE=$(mktemp)
chmod 600 "$KEY_FILE"
echo "$EC2_SSH_KEY" > "$KEY_FILE"

SSH="ssh -i $KEY_FILE -o StrictHostKeyChecking=no $EC2_USER@$EC2_HOST"

echo "==> Deploying fnbx/$SVC:$SHA to $EC2_HOST"

$SSH "
  set -euo pipefail
  echo '--- pulling latest code ---'
  git -C $APP_DIR pull --ff-only

  echo '--- building $SVC ---'
  IMAGE_TAG=$SHA $COMPOSE build $SVC

  echo '--- restarting $SVC ---'
  IMAGE_TAG=$SHA $COMPOSE up -d --no-deps $SVC

  echo '--- pruning old images ---'
  docker image prune -f --filter 'until=24h'
"

rm -f "$KEY_FILE"
echo "==> Done: $SVC deployed at $SHA"
