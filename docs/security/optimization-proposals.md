# Security hardening decisions

The initial port preserves Vakot's 15-minute access tokens, sliding 7-day refresh
tokens, 15-second concurrent-rotation grace, PostgreSQL revocation set, nightly
cleanup and one-day post-expiry retention. The refresh-version fix in section 1 was approved and implemented. Sections 2 and 3 remain proposals only.

## 1. Make account-wide refresh invalidation exact — approved and implemented

Vakot truncates `tokens_valid_from` to whole seconds and rejects refresh tokens
only when `iat < tokens_valid_from`. Tokens minted in the cutoff's own second can
therefore survive password changes, password resets or suspicious-reuse invalidation.

Implemented change: add an integer `refresh_version` to each staff account and include
it in refresh-token claims. Increment it under the existing account lock on password
change/reset or detected reuse. Refresh must match the account's current version.
This replaces time-based invalidation for new tokens and removes the same-second
gap. Normal access-token requests still use signature/claims/expiry alone and remain
stateless. A token pair issued before invalidation loses renewal, even within the
same second; a subsequent password login receives the new version immediately.

Validation: concurrent password-change/refresh tests, same-second reset tests,
repeated old-token replay after a new login, and multiple independent sessions.

## 2. Observe revoked-token cleanup before changing its storage

Proposed first step: publish cleanup run count, deleted-row count, duration and
failure count, plus database-side `pg_total_relation_size` and estimated live/dead
rows. Measure table size periodically, not with `COUNT(*)` on every authentication
request. Alert on repeated failures and on cleanup duration approaching its schedule.
No expiry, refresh-frequency or access-token validation change is needed.

Based on those measurements, a later approved change could run cleanup more often
or delete in bounded batches. Partitioning adds migration/partition-management work.
Redis TTL requires a shared, durable deployment: losing revocation keys before JWT
expiry could make consumed tokens usable again. Neither is enabled by this port.

## 3. Stolen access-token exposure

Immediate revocation requires consulting state or changing the credential model.
Keeping access validation stateless preserves the exposure until expiry (15 minutes
plus Nimbus's default 60-second clock-skew allowance). A shorter access lifetime
reduces that window but increases refresh traffic and revocation rows. Increasing
the lifetime reduces refresh traffic but increases stolen-token exposure.

Recommendation: retain 15 minutes initially, preserve on-demand frontend refresh,
measure actual traffic, and decide the lifetime/storage tradeoff with those data.

## Further production work beyond the reference port

Vakot's process-local verification store needs shared storage before identity is
scaled across replicas. Browser localStorage exposes tokens to successful XSS.
Rate limiting for login/signup/recovery, MFA for privileged staff, signing-key
rotation, and asymmetric signing between identity and resource services deserve a
separate approved hardening change. They are not implemented implicitly here.

References: [OAuth Security BCP, refresh-token protection](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14),
[Spring Security JWT validation](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).
