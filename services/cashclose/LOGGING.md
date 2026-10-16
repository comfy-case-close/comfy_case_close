# Cashclose logging

Logging infrastructure is shared across services; see [backend logging](../../LOGGING.md).
This service retains its detailed cash-close business events. Every request receives an
`X-Request-ID` response header. A supplied ID containing 1–64 letters, numbers,
underscores or hyphens is reused; otherwise the service generates a UUID.
The console includes `cashclose-service requestId=...` on logs written while
that request is running. This is a local correlation ID, not distributed tracing.

## What to look for

- **INFO — request completion:** method, route template, HTTP status and duration.
  Requests rejected before MVC (including authentication failures) have
  `route=<unmapped>`. Requests mapped to a controller show a route such as
  `/api/v1/cash-closes/{id}/approve`.
- **INFO — business changes:** close creation/submission/approval/rejection/
  correction/voiding, file attachment, and movement creation/correction/approval/rejection.
  Events include resource, business and actor IDs and run after transaction commit.
  A rollback produces no business-success event.
- **WARN — handled rejection:** a stable numeric error code and its enum name,
  such as `3004 MOVEMENTS_PENDING`. Error JSON keeps the existing format.
  Authentication failures handled by security filters appear in request completion
  logs with status `401` or `403`.
- **ERROR — unexpected controller failure:** exception type, root cause type and stack
  frames from the existing shared handler. Raw exception messages are omitted
  except for its existing JVM-generated null-pointer diagnostic.
- **DEBUG — investigation details:** request start, resource reads, pagination,
  submission rule selection, and shift-sales lookup outcome/duration.

Operational INFO events contain no bodies or credentials. Optional shared DEBUG payload
events include request/response JSON, headers and parameters, including financial amounts
and notes, after mandatory credential masking. Enable
`com.fnbx.shared.logging.HttpPayloadLogger: DEBUG` and `fnb.logging.payload.enabled=true`.
Responses default to 16,384 logged characters; `fnb.logging.payload.full-response=true`
removes that cap while retaining masking. File/binary content is omitted.
See the shared guide for configuration and host archive paths.

## Try one request

With the service running, set `ACCESS_TOKEN` to a valid access token, `BRANCH_ID` to the
branch UUID and `CASH_CLOSE_ID` to a close your account may review, then run:

```sh
curl -i -X POST "http://localhost:8086/api/v1/cash-closes/$CASH_CLOSE_ID/approve" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'X-Request-ID: cashclose-demo-1' \
  -H "X-Branch-Id: $BRANCH_ID" \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Search the cashclose console output for `requestId=cashclose-demo-1`. A successful
approval produces lines like these (timestamps and logger names omitted):

```text
INFO [cashclose-service requestId=cashclose-demo-1] Cash close state changed cashCloseId=... businessId=... branchId=... actorId=... action=APPROVE from=SUBMITTED to=APPROVED
INFO [cashclose-service requestId=cashclose-demo-1] Request completed method=POST route=/api/v1/cash-closes/{id}/approve status=200 durationMs=...
```

If movements are pending, the same request instead produces:

```text
WARN [cashclose-service requestId=cashclose-demo-1] Request rejected errorCode=3004 errorName=MOVEMENTS_PENDING
INFO [cashclose-service requestId=cashclose-demo-1] Request completed method=POST route=/api/v1/cash-closes/{id}/approve status=422 durationMs=...
```

Enable investigation details for cashclose with the runtime setting
`LOGGING_LEVEL_COM_FNBX_CASHCLOSE=DEBUG`. Request-start logs now use the shared logger:
`LOGGING_LEVEL_COM_FNBX_SHARED_LOGGING_REQUESTLOGGINGFILTER=DEBUG`. Normal operation defaults to INFO.
