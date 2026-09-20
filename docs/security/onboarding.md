# Onboarding and tenant provisioning

Supersedes the "Onboarding and existing staff" section of
[authentication.md](authentication.md). That document describes the rest of the
session lifecycle (tokens, rotation, recovery) and is unchanged by this refactor.

## 1. Why this changed

Public signup used to create a tenant as a side effect. `POST /auth/signup` with
a null `businessId` inserted a row in `identity.business`, a `MAIN` branch and an
owner staff account holding `ADMIN`, all in one unauthenticated call. `POST
/auth/google` did the same. Three problems:

1. **Anyone on the internet could mint tenants.** No approval, no rate limit
   beyond the OTP cooldown, no record of who authorised it.
2. **Tenant creation was invisible.** It happened inside an authentication
   method, so nothing in the code read as "provisioning a customer".
3. **Joining an existing business was impossible.** With a `businessId`, signup
   only *activated* an account somebody had already inserted by hand. There was
   no path for a new employee to ask for access.

The refactor separates the three concerns that were fused together: creating a
tenant, provisioning its first administrator, and letting an employee join.

## 2. Actors and authority

| Actor | Authenticates with | May do |
|---|---|---|
| Applicant (business) | Owner email OTP | Apply for a tenant in public. Applying creates nothing but a queue entry |
| Platform admin | `X-Platform-Key` header | Review that queue and approve or reject; deactivate a business |
| Business `ADMIN` | Access JWT | Everything an `HR` may do, plus branch create/update/deactivate, business profile update, granting `ADMIN` |
| Business `HR` | Access JWT | Review join requests, assign/change/revoke a staff member's branch role (never `ADMIN`) |
| `MANAGER`, `ACCOUNTANT`, `SHIFT_LEAD`, `STAFF` | Access JWT | Read the branches they are assigned to. No onboarding authority. |
| Applicant | Email OTP proof | Submit one join request per business |

**2.1 Business-wide authority is "any branch".** Every role grant in this system
is scoped to one branch (`identity.staff_branch_role`). Approving a join request
or renaming the business is not a branch-scoped act, so the rule is: *holding
`ADMIN` or `HR` at any active branch of the business confers business-wide
authority.* `AccessPrincipal.requireAnyBranch(ADMIN, HR)` expresses this.

This is deliberately blunt — an `HR` at one branch can approve for all of them —
and it is the right trade for a three-branch brand. The alternative, a
`staff.business_role` column plus a `business_role` JWT claim, changes the token
contract that the gateway and all nine services validate. See §8.1.

**2.2 The platform key is not an account.** `PLATFORM_ADMIN_KEY` is a single
shared secret compared in constant time by `PlatformKeyFilter`, which guards only
the platform routes. It gives no audit trail of *which* administrator acted
and must be rotated by hand. See §8.2 for the upgrade path.

## 3. Flows

### 3.1 Business registration

A business is created only by platform approval of a public, email-verified application.

1. `POST /api/v1/businesses/registrations/start` with `{ownerEmail}` sends a six-digit OTP.
2. `POST /api/v1/businesses/registrations/verify` with `{ownerEmail, otp}` verifies
   the address and returns **200** with `{registrationToken, expiresInMs}`. This
   step creates no registration or account.
3. `POST /api/v1/businesses/registrations` with the business name, first branch,
   owner details and `registrationToken` submits a pending application. It returns
   **202** with `{registrationId, status, message}`. Do not include `otp` here.
4. A platform administrator approves or rejects the application. Approval creates
   the business, first branch, verified owner and `ADMIN` grant and records the
   decision in one transaction. Rejection requires a reason.
5. After commit, approval emails the business code, owner email and generated
   password. The owner signs in directly and is told to change the password in
   account settings. Rejection emails the reason.

There is no public registration read and no direct create-business or provision-owner API.

#### 3.1.1 Generated codes and collision handling

Business and branch codes use an ASCII uppercase name prefix (up to 12 characters),
a hyphen, and 12 cryptographically random Crockford Base32 characters (60 random
bits). Example: `COMFY-7K3M9X2Q8R4T`. Diacritics are removed, Vietnamese Đ maps to
D, punctuation becomes hyphens, and names without ASCII letters/digits use `BIZ`.
Codes are at most 25 characters, immutable and shareable; names remain editable.
This is a random suffix, not a hash of the name.

Business codes are globally unique; branch codes are unique within a business.
Database constraints enforce uniqueness. Generated-code and UUID primary-key
collisions retry up to five attempts, each in a fresh transaction. An approval
collision regenerates the business code and persists the final code on the
registration before emailing it. Unrelated duplicates are not reported as
`BUSINESS_CODE_TAKEN`. Duplicate pending owner addresses still return 2433.

The business UUID is generated before the transaction because RLS needs that
namespace for the insert. No pre-check is needed: it would race anyway. PostgreSQL
creates branch UUIDs; `INSERT ... RETURNING branch_id` provides the ID needed for
the owner's branch grant.

#### 3.1.2 Email verification

The OTP is bound to the normalized owner email and the separate
`BUSINESS_REGISTRATION` purpose. Signup/reset OTPs cannot authorize registration.
It expires after ten minutes, permits five failed attempts, has a 60-second resend
cooldown and is consumed once by `verifyOtp`. Verification issues a random token
valid for 15 minutes, bound to the same normalized email and purpose. Submission
consumes that token once; raw OTPs and signup/reset tokens cannot authorize it.
Missing tokens return HTTP 400; invalid, expired or reused tokens return HTTP 422
with code `2434` (`INVALID_REGISTRATION_TOKEN`). Form validation happens before
token consumption, so invalid fields can be corrected using the same valid token.
A database failure after consumption requires a fresh OTP and verification token.

Submission persists `owner_email_verified_at`. Approval requires that evidence
and creates the owner with `email_verified = true`. Legacy pending applications
have no evidence and cannot be approved: reject them with an explanation and ask
the applicant to resubmit using OTP. Do not backfill them as verified.

OTP and token storage is currently in memory; restarts lose outstanding proofs and multiple
identity instances require a shared verification store.

### 3.2 Owner provisioning — approval

Approval generates a UUID password, retains its plaintext only in memory for the
email, and stores `passwords.encode(password)` on the staff row. Neither the
approval response nor the registration row contains the password. The email
instructs a password change; first-login password change is **not enforced** by
the backend in this version.

Decision delivery is asynchronous and happens after commit. `notified_at` remains
null if delivery fails or the process stops before sending; there is no durable
mail outbox or automatic replay. The platform can give the owner their business
code, then the owner can use Forgot Password to recover through email OTP.

### 3.3 Existing unverified accounts

The staff signup flow still activates pre-existing unverified accounts using an
email OTP and a user-chosen password. Newly approved owners do not need this
extra signup step.

### 3.4 Staff join request — an applicant

The same three calls, for an email with no staff row in that business. Instead of
creating an account, `/auth/signup` inserts a `PENDING`
`identity.staff_join_request` holding the applicant's name, phone and the BCrypt
hash of the password they chose, and answers **202** with
`outcome = PENDING_APPROVAL` and no session.

Storing the hash is what lets the applicant keep the password they already typed
instead of being sent a second setup email after approval. It is a BCrypt hash
under RLS, and it is **nulled out** the moment the request is approved or
rejected.

`/auth/signup/start` sends an OTP whether or not a staff row exists — otherwise
the response would reveal who works there. Submitting a second request while one
is `PENDING` **replaces** it rather than failing, so a stale request cannot lock
somebody out.

### 3.5 Approval — `ADMIN` or `HR`

`POST /api/v1/join-requests/{id}/approve` with `{branchId, role, note?}`.

The branch and role are chosen **in the approval call itself**. An approved
account is therefore immediately usable. Had approval and assignment been
separate steps, an approved user would hold an account that cannot log in — a
bare `403` indistinguishable from a wrong password — until HR happened to get
around to them.

Approval creates the `identity.staff` row from the request (email verified, hash
copied across), inserts the `staff_branch_role`, and stamps the request
`APPROVED` with `decided_by`, `decided_at` and `created_staff_id`.

**An `HR` may not grant `ADMIN`.** Only an `ADMIN` may. Without that rule any HR
could promote a colleague, then be promoted back.

Rejection requires a reason and stamps `REJECTED`. Neither decision can be
applied twice (`2426`).

### 3.6 Branch and role administration

`BranchController` owns everything after registration: create, list, read,
update and deactivate branches, and assign, change or revoke one staff member's
role at one branch. Assignment is a `PUT` on `(branchId, staffId)`. Each role
version has an `assignment_id` and a half-open validity interval
`[assigned_at, revoked_at)`. Changing a role closes the live version and inserts
its successor in one transaction. Repeating the current role is a no-op;
re-assigning a revoked member creates a new version. A partial unique index
allows only one live role per pair, and an exclusion constraint prevents overlap.
The repository serializes assignment and revocation on the staff row, including
when no assignment exists yet. Database timestamps define the boundaries.
Closed rows cannot be edited or deleted. The existing audit stream records the
actor and full before/after versions.

The member endpoint defaults to live grants. `includeRevoked=true` includes
**every version**, so a staff member can occur multiple times; pagination and
counts refer to assignment versions. `assignedAt` now means the start of that
role version, not the first date the person worked at the branch.

Cash-close decision history exposes `actedRole`, the branch role from the
verified JWT used to authorize the action. It is stored in the existing immutable
ledger alongside `actedBy` and `actedAt`. This is deliberately a snapshot of the
authorization evidence: already-issued access tokens retain their grants until
expiry, so a temporal join to the latest assignment can tell a different story.
This change does not introduce immediate access-token revocation or change the
reviewer policy (ADMIN, MANAGER and ACCOUNTANT can review).

Apply the appended migrations before deploying the updated services; coordinate
the rollout because old assignment upserts and decision writers are incompatible
with the new invariants. Existing assignment dates are retained, but may predate
the currently stored role. Previously overwritten role intervals cannot be
reconstructed reliably from those rows. Legacy decisions keep `actedRole=null`;
consult existing `platform.audit_log` events for historical evidence rather than
backfilling guesses. Movement decision ledgers are unchanged by this migration.

Deactivating the **last active branch** is refused (`2428`) for the same reason
§3.1 creates one: it would strip every staff member of every live grant.

### 3.7 Finding a business

All public authentication requests accept `businessCode` instead of `businessId`.
The backend resolves the active business through `shared.fn_find_business_by_code`
before entering its tenant transaction. The restricted SECURITY DEFINER function
remains an internal database primitive; the HTTP lookup endpoint is removed.

Codes select a namespace and authorize nothing. Login gives the same invalid-
credentials response for unknown codes and wrong credentials. Forgot Password
returns its generic message for unknown codes or emails. UUIDs are omitted from
public lookup and approval emails but remain in authenticated responses and JWTs;
authorization depends on verified grants and RLS, never on keeping IDs secret.
A future public code-to-name confirmation can return the name alone if needed;
it is not part of this API.

## 4. API surface

Paths below omit the `/api/v1` prefix that `WebConfig` adds.

### 4.1 `BusinessController` — `/businesses`

| Method/path | Auth | Body / result |
|---|---|---|
| POST `/businesses/registrations/start` | **Public** | `{ownerEmail}` → email OTP |
| POST `/businesses/registrations/verify` | **Public** | `{ownerEmail, otp}` → `{registrationToken, expiresInMs}` |
| POST `/businesses/registrations` | **Public** | `{businessName, businessType?, currencyCode?, timezone?, branchName, branchAddress?, ownerEmail, ownerFirstName, ownerLastName, ownerPhone?, registrationToken}` → 202 `{registrationId, status, message}` |
| GET `/businesses/registrations?status=&page=&size=` | Platform key | → `PagedResponse<BusinessRegistrationResponse>` |
| GET `/businesses/registrations/{id}` | Platform key | → `BusinessRegistrationResponse` |
| POST `/businesses/registrations/{id}/approve` | Platform key | `{note?}` → 201 `BusinessRegistrationResponse`; creates the tenant and emails the owner |
| POST `/businesses/registrations/{id}/reject` | Platform key | `{reason}` → `BusinessRegistrationResponse`; emails the reason verbatim |
| DELETE `/businesses/{businessId}` | Platform key | Deactivates. → 200 `MessageResponse` |
| GET `/businesses/me` | Access JWT, any role | → `BusinessResponse` |
| PATCH `/businesses/me` | Access JWT, `ADMIN` | `{businessName?, businessType?, currencyCode?, timezone?}` → `BusinessResponse` |

Tenant-scoped routes address the caller's own business as `/businesses/me` and
take the ID from the JWT, never from the path — the rule already documented on
`CashCloseController`. `{businessId}` appears only on platform routes, which have
no JWT to read it from.

### 4.2 `JoinRequestController` — `/join-requests`

| Method/path | Auth | Body / result |
|---|---|---|
| GET `/join-requests?status=&page=&size=` | `ADMIN`/`HR` | → `PagedResponse<JoinRequestResponse>` |
| GET `/join-requests/{id}` | `ADMIN`/`HR` | → `JoinRequestResponse` |
| POST `/join-requests/{id}/approve` | `ADMIN`/`HR` | `{branchId, role, note?}` → 201 `StaffResponse` |
| POST `/join-requests/{id}/reject` | `ADMIN`/`HR` | `{reason}` → `JoinRequestResponse` |

### 4.3 `BranchController` — `/branches`

| Method/path | Auth | Body / result |
|---|---|---|
| POST `/branches` | `ADMIN` | `{branchName, address?, targetCashRemaining?, cashRemainingTolerance?}` → 201 `BranchResponse` |
| GET `/branches?includeInactive=` | Any role | → `List<BranchResponse>` |
| GET `/branches/{branchId}` | Any role | → `BranchResponse` |
| PATCH `/branches/{branchId}` | `ADMIN` | Same optional fields → `BranchResponse` |
| DELETE `/branches/{branchId}` | `ADMIN` | Deactivates; refuses the last active branch → `MessageResponse` |
| GET `/branches/{branchId}/staff` | `ADMIN`/`HR`/`MANAGER` | → `List<BranchAssignmentResponse>` |
| PUT `/branches/{branchId}/staff/{staffId}` | `ADMIN`/`HR` | `{role}` → `BranchAssignmentResponse` |
| DELETE `/branches/{branchId}/staff/{staffId}` | `ADMIN`/`HR` | Revokes → `MessageResponse` |

### 4.4 Changed auth routes

Login, signup start/verify/complete, Google sign-in and password-recovery
start/verify/complete all require `businessCode` (max 32 characters). Refresh and
logout still use refresh tokens; authenticated operations use the JWT tenant.
See [authentication.md](authentication.md) for complete request shapes.

`SignUpResponse` is `{outcome, message, session}` where `outcome` is
`SESSION_ISSUED` or `PENDING_APPROVAL` and `session` is the usual `AuthResponse`,
or null when pending. One shape for both branches, because the client cannot know
in advance which applies and telling it beforehand would leak whether an address
already works there.

## 5. Data model

**5.1 `shared.user_role` gains `HR`.** `ALTER TYPE ... ADD VALUE` runs in its own
changeset with `runInTransaction="false"`, so later changesets may use the value.

**5.2 `shared.join_request_status`** — `PENDING`, `APPROVED`, `REJECTED`.

**5.3 `identity.staff_join_request`** — one row per application, carrying the
applicant's details, the BCrypt hash (nulled on decision), the decision audit
columns, and `created_staff_id` linking to the account approval produced. A
partial unique index on `(business_id, lower(email)) WHERE status = 'PENDING'`
allows exactly one live request per address per business.

**5.4 RLS is applied explicitly, not by discovery.** The `900-rls` changeset
loops over every table carrying a `business_id` and is `runOnChange="true"` — but
appending a new changeset to the master file does not change *that* file, so the
loop does not re-run and a table added afterwards ships **with no tenant
isolation at all**. The migration therefore calls
`shared.fn_apply_tenant_rls('identity','staff_join_request')` itself. Any future
identity table must do the same.

**5.5 `identity.business_registration`** — one row per application, holding the
business as applied for, its first branch, the prospective owner, the decision
and what the decision produced, plus `owner_email_verified_at`. No password is
stored on the registration; the credential is generated only at approval.

This is the one table in the schema with **no `business_id` and therefore no RLS
policy**, and that is correct rather than an oversight — a row here belongs to no
tenant, because the tenant is what is being asked for. The `900-rls` loop looks
for a `business_id` column and so skips it, and rls-guard 9.1 stays green for the
same reason. What replaces tenant isolation is the grant: `svc_identity` alone
can read it, every other service role is revoked explicitly, and the only routes
that expose it are behind the platform key.

There is no `decided_by`. The platform administrator authenticates with a shared
key rather than a staff account, so there is no identity to record — the column
to add when §8.2 lands.

## 6. Authorization summary

- Platform key → registration review/approval/rejection and business deactivation.
- `ADMIN` at any active branch → everything below, plus branch structure, business
  profile, and granting `ADMIN`.
- `HR` at any active branch → join-request review, branch role assignment except
  `ADMIN`.
- Branch-scoped roles are unchanged, and a grant in branch A still confers
  nothing in branch B.
- Role changes take effect on the next refresh, as before; an already-issued
  access token keeps its signed snapshot until it expires.

## 7. Breaking changes

1. `POST /auth/signup` no longer returns a token pair unconditionally. Clients
   must read `outcome` and handle `PENDING_APPROVAL` (HTTP 202).
2. `businessCode` replaces `businessId` in every public authentication request.
   Registration and branch creation no longer accept caller-chosen codes. Business
   registration requires `registrationToken`, obtained by exchanging the emailed
   OTP at `/businesses/registrations/verify`.
   The direct business/owner creation and public lookup APIs are removed.
3. `businessName`, `branchName` and `businessType` are gone from the request
   record. Jackson tolerates unknown properties here, so an old client still
   sending them gets **no error** - they are ignored and the call behaves like
   any other signup. Treat this as a silent break rather than a loud one: a
   client that relied on those fields creating a tenant now quietly files a join
   request instead.
4. `AuthAccountRepository.createBusinessOwner` is deleted. Business creation is
   no longer reachable from the authentication service.
5. `AuthService.signup` and `AuthService.google` return `SignUpResponse` rather
   than `AuthResponse`.

## 8. Deliberate limitations and follow-ups

**8.1 Business-level roles.** §2.1 approximates business authority with "any
branch". The clean model is a nullable `staff.business_role` plus a
`business_role` JWT claim honoured by `AccessPrincipal`. That touches
`fnbx-shared`, which every service depends on and CODEOWNERS locks, so it is a
separate change with its own rollout.

**8.2 Platform admin accounts.** A shared key has no per-actor audit and no
revocation. The upgrade is an `identity.platform_admin` table (no `business_id`,
so the RLS loop skips it) and a `type=platform` token — which requires
`TokenClaimsValidator`, `VerifiedTenantFilter` and `AccessPrincipal` to tolerate
tenant-less tokens.

**8.3 Only business registrations are notified.** A registration decision is
emailed by `RegistrationMailer`. Nothing else is: a staff join request being
approved, rejected, or arriving in an ADMIN's queue sends no email, so somebody
has to be watching `/join-requests`.

Both belong in `notify-service`, which already owns alerting. The registration
letter lives in identity for now because identity is the only service permitted
to read `business_registration` — the migration revokes it from everyone else —
so moving the letter means moving that read across a service boundary, which is
a change worth making deliberately rather than as a side effect.

**8.4 Join requests do not expire.** A `PENDING` row lives until it is decided.
The replace-on-resubmit rule in §3.4 keeps that from blocking anyone; a sweeper
can be added if the table grows.

**8.5 The applicant cannot poll their status.** They discover approval by
logging in. A status endpoint would need its own authentication, since the
applicant has no account yet.

## 9. Rollout order

1. Apply the Liquibase changesets: `006-hr-role`, `007-join-request`,
   `008-business-lookup`, `009-business-registration`,
   `010-registration-email-verification`. `HR` must exist before any
   service writes it, which is why 006 runs with `runInTransaction="false"`.
2. Set `PLATFORM_ADMIN_KEY` in the deployment's secret store. Identity refuses to
   start without it, rather than defaulting to something guessable.
3. Configure SMTP (`SMTP_HOST`, `MAIL_FROM`, credentials). Without it a
   registration can still be approved — the business is created either way — but
   the applicant is never told, and `notified_at` stays null on the row.
4. Deploy identity-service and the gateway together — the gateway route gains
   `/api/v1/join-requests/**`. `/api/v1/businesses/**` already covers the
   registration routes.
5. Check the ingress. `POST /api/v1/businesses/registrations` is genuinely public
   and requires an email-verification token. Its `/start` and `/verify` routes are public too; apply ingress
   rate limiting to all three. Review routes must reach
   identity with the `X-Platform-Key` header intact, which the gateway forwards
   rather than strips.
6. Update the frontend: use `businessCode` in authentication, remove code inputs
   from registration/branch creation, add registration OTP start/verify and submit the
   returned `registrationToken` with the application. Preserve handling of pending staff requests.

| Variable | Use |
|---|---|
| `PLATFORM_ADMIN_KEY` | Shared secret for the platform routes — tenant provisioning and the registration queue. At least 32 random characters, distinct per environment. Required; identity will not start without it. |
| `SMTP_*`, `MAIL_FROM` | Already required for OTP delivery; now also carries registration decision letters. |
