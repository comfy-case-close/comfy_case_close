# FNB authentication

Identity now implements the authentication lifecycle from the inspected Vakot_BE
working tree, adapted to FNB's staff, business and branch model. Cash-close
calculations, state transitions and other business rules are unchanged.

## What is implemented

| Area | Behavior |
|---|---|
| Passwords | BCrypt for new credentials; existing Argon2id hashes remain verifiable. SHA-256 legacy credentials must use email activation/recovery. |
| Access tokens | HS256 JWTs, 15 minutes, verified independently by the gateway and every service. No session/revocation database check during bearer authentication. |
| Refresh tokens | Signed JWTs, 7 days, single-use rotation; only consumed identifiers are stored in PostgreSQL. |
| Concurrent refresh | One successful rotation. Duplicates within 15 seconds receive HTTP 409 / code 2418. They never receive a second token pair. |
| Suspicious reuse | A consumed token replayed after the grace window invalidates every refresh session for that staff account. |
| Exact invalidation | Approved deviation from Vakot: a `refresh_version` claim is checked against the account. Password change/reset and suspicious reuse increment the version atomically. This closes Vakot's same-second cutoff gap. |
| Logout | Idempotently revokes the supplied refresh token. Existing access tokens remain usable until expiry. |
| Cleanup | Nightly at 03:30 in the service JVM's timezone. Delete revocations only after token expiry plus one day, preserving Nimbus's 60-second clock-skew margin. |
| Signup/recovery | Email OTP → short-lived proof → one-time signup/password reset. OTPs expire in 10 minutes; proofs in 15 minutes. Five wrong guesses; 60-second resend cooldown. |
| Google | RS256 signature against Google's cached JWKS, Google issuer, configured audience, expiration and verified email. |
| Authorization | JWT business identity feeds PostgreSQL RLS; signed per-branch grants control cash-close access, decisions and report visibility. |

Seven days is a **sliding refresh-token lifetime**, as in Vakot. Each successful
refresh issues another seven-day token. It is not an absolute seven-day limit on
an active session. Idle clients stop making refresh requests and eventually expire.

The version check affects refresh only. A stolen access token can still authorize
requests for its remaining lifetime, plus the decoder's clock-skew allowance.
`tokens_valid_from` is retained as an informational cutoff; versions decide renewal.

## API contract

Every servlet service imports `com.fnbx.shared.config.WebConfig`, which adds
`/api/v1` to FNB `@RestController` mappings. Controllers declare only resource
paths (`/auth`, `/cash-closes`, `/reports`); do not repeat the prefix there.
Framework and springdoc endpoints retain their own paths. Gateway predicates and
security matchers continue to use the full external URLs, so routing and token
requirements are unchanged.

All routes are under `/api/v1/auth`. POST routes marked public must be sent without
an `Authorization` header; an expired bearer token is rejected by Spring Security
even on a public route. `/me` and `/change-password` require an access token.

| Method/path | Authentication | Body / result |
|---|---|---|
| POST `/login` or `/signin` | Public | `{businessCode, email, password}` → token pair and staff profile |
| POST `/refresh` | Public | `{refreshToken}` → replacement pair |
| POST `/logout` | Public | `{refreshToken}` → message |
| POST `/signup/start` | Public | `{businessCode, email}` → message. `businessCode` is **required** |
| POST `/signup/verify` | Public | `{businessCode, email, otp}` → `{signupToken, expiresInMs}` |
| POST `/signup` | Public | `{businessCode, email, signupToken, password, firstName, lastName, phone?}` → `SignUpResponse`; **201** on activation, **202** when a join request was filed |
| POST `/google` | Public | `{businessCode, idToken}` → `SignUpResponse`. New-business Google signup is gone |
| POST `/forgot-password` | Public | `{businessCode, email}` → same message whether or not the account exists |
| POST `/reset-password/verify` | Public | `{businessCode, email, otp}` → `{resetToken, expiresInMs}` |
| POST `/reset-password` | Public | `{businessCode, email, resetToken, newPassword}` → message |
| POST `/change-password` | Access JWT | `{currentPassword, newPassword}` → message; all refresh sessions invalidated |
| GET `/me` | Access JWT | Current staff profile and live branch assignments |
| PATCH `/me` | Access JWT | Optional `{firstName, lastName, phone, avatarUrl}`; cannot change tenant, roles, email or staff ID |

`email` is required and validated as an email address, then normalized to
lowercase for lookup. Employee codes and the old `username` request field are no
longer accepted for login. `businessCode` selects the credential namespace; the backend resolves its ID.
Selecting this namespace never grants authorization: the
password or verified Google/email proof must still establish the staff identity.
Protected requests take the business and staff ID exclusively from the signed JWT.

The database enforces case-insensitive email uniqueness **within each business**
with `uq_staff_business_email_ci` on `(business_id, lower(email))`. The same email
can still belong to different businesses. Staff without an email remain allowed
in the staff directory but cannot use email login.

Successful auth responses return DTOs directly, matching FNB's cash-close APIs.
Controllers use `ResponseEntity` for HTTP status; signup returns 201 and other
successful routes return 200. For example, login returns:

```json
{
  "accessToken": "<access JWT>",
  "refreshToken": "<refresh JWT>",
  "tokenType": "Bearer",
  "expiresInMs": 900000,
  "user": {
    "id": "<staff UUID>",
    "businessId": "<business UUID>",
    "employeeCode": "bachho",
    "firstName": "Ho",
    "lastName": "Viet Bach",
    "email": "owner@example.com",
    "phone": null,
    "avatarUrl": null,
    "active": true,
    "branchRoles": {"<branch UUID>": "ADMIN"}
  }
}
```

Auth exception/validation responses retain their existing envelope (`timestamp`,
`code`, `message`, `path`, `fieldErrors`, and `result: null`).
The refresh client must distinguish **409/code 2418**
from **401/code 2407**. All services now use the [shared error contract](../error-handling.md);
success responses remain direct DTOs.
Authentication cookies, form login, HTTP Basic and server sessions are disabled.

## Onboarding and existing staff

**This section moved.** See [onboarding and tenant provisioning](onboarding.md)
for the whole flow; what follows is only what an authentication client needs.

Public signup **no longer creates a business**. It used to: a null `businessId`
inserted a tenant, a `MAIN` branch and an owner holding `ADMIN`, all in one
unauthenticated call, and `/auth/google` had the same door. A business is now
provisioned first, behind the platform key, and signup only ever joins one that
already exists.

Every public authentication request selects the business by `businessCode`.
The backend resolves the UUID internally before entering its tenant transaction.
There is no public business lookup endpoint. Newly approved owners receive their
generated password by email and can sign in immediately. See onboarding.md.

Signup has two endings, and the client cannot know in advance which applies -
that is deliberate, because whether an address already works at a business is
exactly what must not be revealed before the OTP proves ownership of it. Both
come back in the same envelope:

```json
{
  "outcome": "SESSION_ISSUED",
  "message": "Account activated.",
  "session": { "accessToken": "...", "refreshToken": "...", "user": { } }
}
```

| `outcome` | HTTP | Meaning |
|---|---|---|
| `SESSION_ISSUED` | 201 (signup), 200 (google) | The address belonged to a provisioned account. It is now active, and `session` holds the usual token pair. |
| `PENDING_APPROVAL` | 202 | The address was unknown. A join request is waiting for an `ADMIN` or `HR`, and `session` is null. |

Branch this on `outcome`, not on the HTTP status alone.

`businessName`, `branchName` and `businessType` are gone from both requests.
Jackson tolerates unknown properties, so a client still sending them gets no
error - they are ignored.

Signup can still activate legacy unverified accounts while preserving their
administrator-assigned branch roles. Newly approved business owners are already
verified and sign in using the generated password emailed after approval. Signup requires nonblank
`firstName` and `lastName` (up to 200 characters each). New staff codes
concatenate the last word of `lastName` and `firstName`, remove whitespace and
lowercase using a locale-independent rule: `Ho` + `Viet Bach` → `bachho`. Accents
are preserved. If that code exists in the same business, insertion tries
`bachho1`, `bachho2`, and so on. The database unique constraint arbitrates
concurrent inserts; only employee-code collisions are retried. Names themselves
are not unique. Existing staff codes remain unchanged during activation and
profile updates.

Passwords for signup follow Vakot's uppercase/digit/special-character rule and
8-72-character range. All new passwords also enforce BCrypt's 72 **UTF-8 byte**
limit, preventing silent truncation of multibyte passwords. Passwords are never
normalized or trimmed.

Google login requires an existing `businessCode`. A verified Google email may
activate an existing unverified account; any old local password is replaced with
an unusable random hash. A verified local account retains its local password when
signing in with Google, matching Vakot. An address with no account in that
business files a join request rather than signing in - a Google email proves
identity, never membership. Google's `family_name` maps to `firstName` and
`given_name` maps to `lastName`, following this application's requested order
(`Ho`, `Viet Bach`). If Google lacks one component, the available name is stored
in `firstName` with an empty `lastName`; if both are missing, the email local
part is used. Codes use the same generator.

Existing staff receive `email_verified=false` from the additive migration. They
must activate their email before local login. For existing BCrypt/Argon2 staff
this is an onboarding requirement, not a rewrite of their current stored hashes.
Recovery resets a password but does not mark an unverified account verified;
such accounts use activation. Email and OTP/proof state are tenant-scoped.

## Authorization boundaries

- Every servlet service imports the shared security configuration. The gateway
  independently validates JWTs and forwards the bearer header. Neither trusts
  client-supplied business/user/role headers; the gateway strips them.
- Access tokens include `uid`, `business_id`, `type` and `branch_roles`.
  A role granted in branch A has no authority in branch B.
- Access and refresh tokens have no top-level summary `role`. `TenantContext`
  carries only business/user IDs. Spring authentication has no flattened role
  authorities; use `AccessPrincipal.requireBranch` or `branches` for authorization,
  never `hasRole('MANAGER')` for a branch-specific action. `UserRole` remains the
  type of each branch grant.
- Any assigned branch role may read and enter cash closes/movements for that
  branch. Approve, reject and reopen require `ADMIN`, `MANAGER` or `ACCOUNTANT`
  **at the resource's branch**. List queries filter to assigned branches; foreign
  close IDs return 404, including movement/history lookups.
- Reports return only branches where the caller has `ADMIN`, `MANAGER` or
  `ACCOUNTANT`. Staff/shift leads cannot read management reports. Alerts without
  a branch are not granted implicitly by a branch assignment.
- Role/business/staff changes take effect on refresh. Already-issued access
  tokens retain their signed snapshot until expiry, as required by stateless auth.
- RLS settings are installed just before the first SQL statement inside an explicit
  transaction, not at connection checkout. They reset at transaction end. Reporting
  reads now use read-only transactions for this reason. This is required with
  PostgreSQL transaction pooling, where [SET LOCAL lasts only for its transaction](https://www.postgresql.org/docs/14/sql-set.html).

Identity repositories use JDBC and explicit account locks; identity does not start
Hibernate. Other services retain their existing persistence models and business
logic. Their future endpoints still need resource-specific role/ownership checks;
authentication alone is not a complete authorization policy.

## Configuration and deployment

Apply Liquibase migrations before deploying services. The new changesets
are `002-identity-authentication`, `002-identity-refresh-version`,
`002-identity-email-uniqueness`, and `002-identity-staff-names`. The existing
role-grants changeset also preserves privacy of the revocation table on reruns.
Use the normal Liquibase migration process for an existing database; `db/apply.sh`
is a fresh-database bootstrap, not a replay-safe replacement for Liquibase.

The email uniqueness migration fails if a business already has case variants of
the same email. Resolve those accounts explicitly before deployment; the migration
does not delete or merge staff. An administrator with access to all tenant rows can
identify conflicts without changing data:

```sql
SELECT business_id, lower(email) AS normalized_email, count(*) AS accounts
FROM identity.staff
WHERE email IS NOT NULL
GROUP BY business_id, lower(email)
HAVING count(*) > 1;
```

The [staff-name migration](../../db/changelog/002-identity/005-staff-names.sql)
adds `first_name`/`last_name`, backfills existing rows, updates dependent report
views and the staff-risk materialized view, then drops `identity.staff.full_name`.
Historical names split at the first word: `Ho Viet Bach` → `Ho` / `Viet Bach`;
a one-word name gets an empty last name. Review this heuristic for existing names
whose intended structure differs. Existing employee codes are preserved. Report
view display-name columns retain their current names and compose both fields.

Run this migration through Liquibase with an administrative migration role that
has all-tenant visibility (`BYPASSRLS` or superuser); it fails explicitly otherwise.
Service database roles remain restricted. The staff-risk cache is rebuilt, with
its restricted owner and tenant-filtered wrapper preserved. Coordinate the schema
and service deployment in a maintenance window: old code referencing `full_name`
cannot run after the column is dropped, and new code requires the new columns.
Frontend signup/profile forms must also send `firstName` and `lastName` and read
those response fields instead of `fullName`.

Set these environment variables through the deployment's secret/config management:

| Variable | Use |
|---|---|
| `JWT_SECRET` | Base64 encoding of at least 32 cryptographically random bytes. Required, same value across identity/gateway/services; use a distinct key per environment. |
| `DB_PASSWORD` | Credential for that service's restricted PostgreSQL role, never a superuser. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact frontend origins; defaults to `http://localhost:3000`, no wildcard support. |
| `GOOGLE_CLIENT_ID` | The frontend's Google OAuth client ID. Empty disables Google login. |
| `PLATFORM_ADMIN_KEY` | Shared secret for registration review and tenant deactivation routes, sent as `X-Platform-Key`. At least 32 random characters, distinct per environment. **Required: identity refuses to start without it**, because a service that booted with an empty key would serve business creation to anyone who found the URL. |
| `MAIL_PROVIDER`, `RESEND_API_KEY` | Choose SMTP (default) or Resend; see [Email delivery](../mail-delivery.md). |
| `SMTP_HOST`, `SMTP_PORT` | SMTP server; default port 587. |
| `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | Mail credentials and sender. Missing mail setup prevents OTP signup/recovery delivery. |
| `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_STARTTLS_REQUIRED` | Default `true`; disable only for a controlled local test mail server. |

JWT issuer defaults to `fnbx-identity`. Override `fnb.security.jwt.issuer` consistently
on all services if needed. The old external-IdP `JWT_ISSUER_URI` setting is replaced
by the local issuer/key configuration. Never reuse a Vakot or Comfy signing key.
Keep service ports private and terminate HTTPS at the ingress.

When rolling out removal of the summary role, deploy the updated gateway and
resource-service validators/filters before identity starts issuing tokens without
that claim. Older valid tokens containing it remain accepted, but it is ignored;
their signed `branch_roles` still determine branch permissions.

`identity.revoked_token` stores UUID `jti`, expiry and revocation time, not raw
refresh tokens or account identifiers. Only `svc_identity` can read/insert/delete
it. Its expiry index supports cleanup. `staff_session` is not used as an access
token denylist or refresh-token allowlist.

The OTP/proof and mail-health stores intentionally remain **process-local**, like
Vakot. Run one identity replica for these flows until a shared store is approved;
restart loses pending codes/proofs and users must request them again. This port
does not claim that the platform has completed a production security audit.

## Frontend: preserve Vakot's lazy refresh behavior

There is no FNB frontend in this repository, so no frontend application was changed.
The backend preserves the behavior needed by Vakot_FE's `auth-session.ts`:

Adapt Vakot's response parsing to the direct success DTO: read `accessToken`,
`refreshToken`, `user`, `signupToken`, and `resetToken` from the JSON root instead
of a `result` wrapper. Error `code` handling stays unchanged.

1. Store the latest token pair/profile together. Send access tokens only in the
   bearer header, and never put tokens in URLs or logs.
2. Immediately before a protected request, use a valid access token. Refresh only
   when it is expiring/expired (Vakot checks 30 seconds early), or reactively after
   one protected-request 401. Do not run an interval that refreshes idle tabs.
3. Share one refresh promise per tab. On a successful refresh, replace the complete
   pair before retrying queued requests. Retry an original request at most once;
   never automatically retry a non-idempotent request after an ambiguous network
   failure.
4. On **409 / 2418**, briefly read shared storage for the different refresh token
   written by the winning tab (Vakot tries five times, 100 ms apart), then adopt
   its pair. Do not replay the consumed token repeatedly.
5. On refresh **401 / 2407**, clear the session and ask for login. Clear after
   password changes/resets as well. Ordinary 403 permission failures do not trigger
   refresh loops.
6. Coordinate logout with any in-flight refresh, revoke the latest stored refresh
   token, and prevent late responses from restoring a locally cleared session.
   Logging out one refresh chain does not revoke unrelated devices.

Vakot_FE stores tokens in localStorage. That remains the reference contract, with
its XSS exposure; a cookie/BFF storage design is separate hardening, not silently
introduced by this backend port.

## Verification and remaining baseline issues

Verified on 2026-09-11 using Java 21 and `tools/test-auth-postgres.sh`: all 22
Maven reactor projects passed `verify`, with 176 tests, no failures, no errors
and no skipped tests. The disposable PostgreSQL 16 run applied all migrations
through `010-registration-email-verification` and passed the RLS guards. It
includes 46 authentication/onboarding integration tests, generated-code collision
retries, separate registration OTP verification and single-use submission tokens,
immediate owner login with the emailed
password, password changes, removed endpoint checks and gateway routing.

Email-only login, case-insensitive uniqueness, concurrent duplicate rejection,
and the same email in separate businesses are also covered.
The centralized API prefix is tested against the original URL, rejected
unprefixed/double-prefixed URLs, and unchanged springdoc route registration.
Shared error tests cover unique codes, HTTP statuses, malformed requests, safe
unexpected-error messages, and servlet/gateway authentication responses.
Authorization tests also verify that new tokens omit the summary role and that
legacy summary claims cannot create global authorities or override branch grants.
Employee-code tests cover matching names, different names producing the same
base, business isolation, eight simultaneous creators, and stable codes after
profile edits. Public signup verifies the split-name JSON contract.
All migrations and PostgreSQL RLS guards passed against the disposable database.

Use Java 21. The machine's `mvn` may otherwise pick a newer JDK incompatible with
the repository's existing Lombok version.

```bash
mvn verify
bash tools/test-auth-postgres.sh
bash tools/test-staff-names-migration.sh
python3 tools/verify.py
```

The PostgreSQL harness creates a disposable database/container, applies all real
migrations, runs the existing RLS guards, enables a test-only `svc_identity` login,
and runs the full Maven verification including the production identity application
with MockMvc. It removes only the container it created. SMTP delivery and live
Google credentials require environment-specific checks; tests replace outbound mail
and use locally signed RSA tokens to verify Google's validation rules.
The separate staff-name migration harness starts from the previous schema with
populated staff and approved cash closes in two businesses, then verifies the
backfill, unchanged employee codes/report totals, view grants, materialized-view
refresh, and analytics-wrapper tenant isolation for `ai_agent`.

The obsolete `UserSession` and `UserBranchRole` mappings are no longer present in
the current worktree. The static validator now passes; the shared identity library
contains the staff entity mappings.

The existing reporting SQL also still reads removed `cash_close` calculated
columns, including `counted_cash` and `cash_difference`. Its branch authorization
was added here, but adapting report calculations to the current schema remains
part of the existing business-data refactor. Report execution against that schema
is therefore not covered by the successful authentication verification.

See [hardening decisions and pending optimization proposals](optimization-proposals.md)
for changes beyond the approved port.

The populated-migration check also exposed an existing analytics permission gap:
`svc_reporting` has grants on analytics objects but lacks `USAGE ON SCHEMA analytics`.
This name refactor preserves those grants and does not change that unrelated
permission setup. The migration harness exercises the scheduled refresh function
as the migration administrator and tenant isolation through the permitted
`ai_agent` wrapper; it does not claim reporting-role analytics access is working.
