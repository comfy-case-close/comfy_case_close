# Deploy to AWS EC2

Single EC2 instance running all 10 services via Docker Compose.
Database: Neon managed PostgreSQL (same as Railway setup — see `docs/railway-neon.md` for Neon configuration).

---

## Architecture on EC2

```
Internet → EC2 :8080 (api-gateway)
               ↓ Docker bridge network (fnbx-prod)
         identity  :8081
         platform  :8082
         files     :8083
         notify    :8084
         integration :8085
         cashclose :8086
         workforce :8087
         inventory :8088
         reporting :8089
               ↓
         Neon PostgreSQL (pooled URL, sslmode=require)
```

Only port 8080 is exposed to the host. All other services communicate over the internal Docker network by name (e.g., `http://cashclose:8086`).

---

## Prerequisites

- An AWS account with an EC2 instance (Amazon Linux 2023 or Ubuntu 22.04, t3.medium or larger recommended)
- A Neon project with the schema applied (see `docs/railway-neon.md`)
- A domain or the EC2 public IP pointed at your frontend's API base URL
- GitHub repository secrets configured (see §5)

---

## 1. Launch and configure the EC2 instance

In the AWS Console:

1. Launch an EC2 instance (Amazon Linux 2023 or Ubuntu 22.04).
2. Instance type: **t3.medium** minimum (2 vCPU, 4 GB RAM). t3.large recommended for production.
3. Storage: at least **20 GB** gp3.
4. Security group inbound rules:

   | Port | Source       | Purpose                        |
   |------|--------------|-------------------------------|
   | 22   | Your IP only | SSH for setup and deploy       |
   | 8080 | 0.0.0.0/0   | api-gateway (public)           |

   If you place a load balancer or Nginx in front, expose 443/80 instead and keep 8080 restricted to the ALB security group.

5. Assign an Elastic IP so the address does not change on instance restart.

---

## 2. Bootstrap the instance

SSH into the instance and run the setup script:

```bash
ssh -i your-key.pem ec2-user@<EC2_IP>
sudo bash -c "REPO_URL=https://github.com/comfy-case-close/comfy_case_close.git \
              BRANCH=main \
              bash <(curl -fsSL https://raw.githubusercontent.com/comfy-case-close/comfy_case_close/main/infra/setup-ec2.sh)"
```

Or copy `infra/setup-ec2.sh` to the instance and run it:

```bash
sudo bash infra/setup-ec2.sh
```

After the script completes, log out and back in so the `docker` group takes effect:

```bash
exit
ssh -i your-key.pem ec2-user@<EC2_IP>
```

---

## 3. Configure environment variables

```bash
cd /opt/fnbx
cp .env.aws.example .env.aws
vim .env.aws          # or nano, whichever you prefer
```

Fill in every value. Pay special attention to:

- `NEON_POOLER_URL` — the **pooled** Neon JDBC URL (ends in `-pooler.aws.neon.tech`).
- `SVC_*_DB_PASSWORD` — one per service role. Set these in Neon first:
  ```sql
  ALTER ROLE svc_identity LOGIN;
  ALTER ROLE svc_identity PASSWORD '<strong-unique-password>';
  -- Repeat for all 9 svc_* roles
  ```
  See `docs/railway-neon.md §3` for the complete procedure.
- `JWT_SECRET` — generate once: `openssl rand -base64 33`. Same value across all services.
- `PLATFORM_ADMIN_KEY` — `openssl rand -base64 33`. Used only by identity.
- `GCS_ENABLED=false` — leave this until GCS is configured.

Do not commit `.env.aws`. It is in `.gitignore`.

---

## 4. Run the database migration

Migration must run against Neon **before** starting any Java service. Neon's schema must already exist; `ddl-auto: validate` means services refuse to start if the schema is missing.

On the EC2 instance:

```bash
cd /opt/fnbx

# Build the Liquibase image (pinned to 4.29.2)
docker build -t fnbx-migrate ./db

# Run migration against Neon (use the DIRECT URL, not the pooler)
docker run --rm \
  -e LIQUIBASE_COMMAND_URL="jdbc:postgresql://<direct-host>.aws.neon.tech/neondb?sslmode=require" \
  -e LIQUIBASE_COMMAND_USERNAME="neondb_owner" \
  -e LIQUIBASE_COMMAND_PASSWORD="<neon-owner-password>" \
  fnbx-migrate
```

The `railway-migrate.sh` entrypoint validates the URL (rejects pooler URLs, rejects missing TLS) then runs `liquibase validate` → `liquibase update` → `liquibase status`.

After migration, verify RLS guards pass (from a machine with `psql` and direct Neon access):

```bash
PGHOST=<direct-host>.aws.neon.tech \
PGPORT=5432 \
PGDATABASE=neondb \
PGUSER=neondb_owner \
PGSSLMODE=require \
PGPASSWORD=<password> \
./db/rls-guard.sh
```

---

## 5. Build and start all services

```bash
cd /opt/fnbx

# Build all images (first run takes ~10–15 min due to Maven downloads)
docker compose -f docker-compose.aws.yml build

# Start all services
docker compose -f docker-compose.aws.yml up -d

# Watch logs
docker compose -f docker-compose.aws.yml logs -f
```

Verify each service started cleanly:

```bash
docker compose -f docker-compose.aws.yml ps
```

All services should show `Up`. If a service exits immediately, check its logs:

```bash
docker compose -f docker-compose.aws.yml logs identity
```

Common startup failures:
- `Schema-validation: missing table` → migration did not run, or `NEON_POOLER_URL` is wrong.
- `Failed to configure DataSource` → wrong DB password or unreachable Neon host.
- `JWT secret must be at least ...` → `JWT_SECRET` is empty or too short.

---

## 6. Configure GitHub Actions for CI/CD

Add these secrets in **GitHub → Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `EC2_HOST` | EC2 public IP or Elastic IP |
| `EC2_USER` | `ubuntu` (Ubuntu) or `ec2-user` (Amazon Linux) |
| `EC2_SSH_KEY` | Contents of the `.pem` private key |
| `NEON_DIRECT_URL` | `jdbc:postgresql://<direct-host>.aws.neon.tech/neondb?sslmode=require` |
| `PG_MIGRATION_USER` | `neondb_owner` |
| `PG_MIGRATION_PASSWORD` | Neon owner password |

After pushing to `main`, the `deploy.yml` workflow:

1. Detects which services changed.
2. Runs Liquibase migration against Neon (if `db/` changed).
3. SSHes into EC2, rebuilds only the changed service, and restarts it — other services keep running.

---

## 7. EC2 security group — SSH hardening

After initial setup, restrict SSH source to GitHub Actions IP ranges or use AWS SSM Session Manager instead of open SSH:

```bash
# Option A: restrict to your static IP only
# Edit the EC2 security group: SSH source = <your-IP>/32

# Option B: use SSM (no open SSH port needed)
# Install the SSM agent (pre-installed on Amazon Linux 2023)
# Attach IAM role with AmazonSSMManagedInstanceCore policy
# Update tools/deploy.sh to use AWS SSM instead of SSH
```

---

## 8. Optional: Nginx reverse proxy with TLS

Put Nginx in front of api-gateway for HTTPS:

```nginx
server {
    listen 443 ssl;
    server_name api.your-domain.com;

    ssl_certificate     /etc/letsencrypt/live/api.your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.your-domain.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Use [Certbot](https://certbot.eff.org/) for a free Let's Encrypt certificate.

---

## 9. Checklist before going live

- [ ] All `svc_*` roles: `rolcanlogin=true`, `rolbypassrls=false`
- [ ] RLS guard script passes all 5 queries
- [ ] `JWT_SECRET` is at least 32 random bytes after Base64 decode
- [ ] `PLATFORM_ADMIN_KEY` set and not empty
- [ ] Services 8081–8089 are NOT reachable from the internet (only 8080)
- [ ] `.env.aws` is not committed to git
- [ ] SMTP or Resend configured and tested
- [ ] `GCS_ENABLED=false` acknowledged until GCS bucket and key are ready
- [ ] EC2 SSH access restricted (not open to 0.0.0.0/0)
