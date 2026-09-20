# Shared application errors

FNB uses one shared `AppException`, `ErrorCode` catalog, and `ErrorResponse` DTO
in `fnbx-shared`. Service factories express domain intent: `AuthExceptions` in
identity and `CashCloseExceptions` in cash-close. Shared code contains error
metadata and HTTP rendering, not business rules or service dependencies.

This fits the current monorepo and keeps codes consistent. Treat the catalog as a
public contract: adding codes is additive; never reuse numbers or casually change
their HTTP statuses. Independently released services must coordinate catalog updates.
Do not create an empty exception factory for a service until it has domain failures.

## Service usage

```java
public static AppException invalidCredentials() {
    return new AppException(ErrorCode.INVALID_CREDENTIALS);
}

throw AuthExceptions.invalidCredentials();
```

Use the constructor with a custom message only for deliberate client-safe domain
context. Never pass raw database, token-validation, password or infrastructure
exception messages into `AppException`.

## HTTP contract

Successes remain direct DTOs. Application errors, MVC validation/request errors,
and security 401/403 responses use this JSON structure:

```json
{
  "timestamp": "2026-09-09T00:00:00Z",
  "code": 2418,
  "message": "This refresh token was just rotated. Retry with the newest one.",
  "path": "/api/v1/auth/refresh",
  "fieldErrors": [],
  "result": null
}
```

The HTTP status for this example is 409. Validation failures retain code 2000 and
include `{field, message}` entries, never rejected values. Unexpected failures
return a generic 500/code 9999 without exception details. Missing credentials use
401/code 2409; invalid bearer tokens use 401/code 2407. Security challenges retain
`WWW-Authenticate: Bearer`. Protected branch operations remain 403; inaccessible
cash-close resources remain indistinguishable from missing resources (404/code 3012).

`GlobalExceptionHandler` is imported through the shared `WebConfig` in every
servlet service. `ServletSecurityErrorHandler` covers authentication failures before
controller dispatch. The reactive gateway uses `GatewayErrorHandler` for its own
security/routing failures and forwards downstream response bodies without rewriting.
Infrastructure outside the application (ingress/proxy failures), CORS rejections,
and already-committed streaming responses are outside this exception contract.

`AuthException`, the identity-only `ApiResponse`/handler, `DomainException`, and
`NotFoundException` are replaced by the shared types. Framework security exceptions
still use Spring's types and are translated at the HTTP boundary. Startup failures
and internal control-flow exceptions remain ordinary Java exceptions.

## Client compatibility

Authentication error field names and existing numeric codes remain unchanged,
including refresh codes 2407 and 2418. Token rotation, transaction boundaries,
account-wide invalidation and domain rules are unchanged.

Cash-close errors now use the envelope above instead of `ProblemDetail`. Clients
must read `message` rather than `detail` and compare numeric codes rather than
old string codes. Each old domain code retains its enum name below except
`VALIDATION_FAILED`, now `CLOSE_VALIDATION_FAILED` (3003); shared
`DATE_RANGE_INVALID` is now 2007. HTTP 422/404 behavior is preserved.

## Catalog

| ErrorCode | Numeric code | HTTP status |
|---|---:|---|
| `VALIDATION_FAILED` | 2000 | BAD_REQUEST |
| `MALFORMED_REQUEST` | 2001 | BAD_REQUEST |
| `RESOURCE_NOT_FOUND` | 2002 | NOT_FOUND |
| `METHOD_NOT_ALLOWED` | 2003 | METHOD_NOT_ALLOWED |
| `UNSUPPORTED_MEDIA_TYPE` | 2004 | UNSUPPORTED_MEDIA_TYPE |
| `NOT_ACCEPTABLE` | 2005 | NOT_ACCEPTABLE |
| `RESOURCE_CONFLICT` | 2006 | CONFLICT |
| `DATE_RANGE_INVALID` | 2007 | UNPROCESSABLE_ENTITY |
| `EMAIL_ALREADY_EXISTS` | 2401 | CONFLICT |
| `INVALID_CREDENTIALS` | 2402 | UNAUTHORIZED |
| `ACCOUNT_DISABLED` | 2403 | FORBIDDEN |
| `INVALID_OTP` | 2404 | UNPROCESSABLE_ENTITY |
| `OTP_EXPIRED` | 2405 | UNPROCESSABLE_ENTITY |
| `CURRENT_PASSWORD_INCORRECT` | 2406 | UNPROCESSABLE_ENTITY |
| `INVALID_TOKEN` | 2407 | UNAUTHORIZED |
| `INVALID_GOOGLE_TOKEN` | 2408 | UNAUTHORIZED |
| `UNAUTHENTICATED` | 2409 | UNAUTHORIZED |
| `ACCESS_DENIED` | 2410 | FORBIDDEN |
| `EMAIL_NOT_VERIFIED` | 2411 | FORBIDDEN |
| `INVALID_SIGNUP_TOKEN` | 2412 | UNPROCESSABLE_ENTITY |
| `INVALID_PASSWORD_RESET_TOKEN` | 2415 | UNPROCESSABLE_ENTITY |
| `OTP_REQUEST_TOO_SOON` | 2416 | TOO_MANY_REQUESTS |
| `TOO_MANY_OTP_ATTEMPTS` | 2417 | TOO_MANY_REQUESTS |
| `REFRESH_TOKEN_ROTATED` | 2418 | CONFLICT |
| `DELIVERY_UNAVAILABLE` | 2419 | SERVICE_UNAVAILABLE |
| `CLOSE_ALREADY_EXISTS` | 3001 | UNPROCESSABLE_ENTITY |
| `NO_DENOMINATION_COUNT` | 3002 | UNPROCESSABLE_ENTITY |
| `CLOSE_VALIDATION_FAILED` | 3003 | UNPROCESSABLE_ENTITY |
| `MOVEMENTS_PENDING` | 3004 | UNPROCESSABLE_ENTITY |
| `REASON_REQUIRED` | 3005 | UNPROCESSABLE_ENTITY |
| `CLOSE_FROZEN` | 3006 | UNPROCESSABLE_ENTITY |
| `MOVEMENT_DECIDED` | 3007 | UNPROCESSABLE_ENTITY |
| `UNKNOWN_MOVEMENT_KIND` | 3008 | UNPROCESSABLE_ENTITY |
| `ILLEGAL_TRANSITION` | 3009 | UNPROCESSABLE_ENTITY |
| `ILLEGAL_MOVEMENT_TRANSITION` | 3010 | UNPROCESSABLE_ENTITY |
| `INVALID_FILTER` | 3011 | UNPROCESSABLE_ENTITY |
| `CASH_CLOSE_NOT_FOUND` | 3012 | NOT_FOUND |
| `MOVEMENT_NOT_FOUND` | 3013 | NOT_FOUND |
| `SERVICE_UNAVAILABLE` | 9001 | SERVICE_UNAVAILABLE |
| `UNEXPECTED_ERROR` | 9999 | INTERNAL_SERVER_ERROR |
