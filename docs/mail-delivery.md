# Email delivery

The `dev` SMTP/Resend transport design, message model, classified delivery failures,
three-attempt retry loop and cash-close HTML template now live in this architecture.
`libs/fnbx-mail` contains `EmailService`, `EmailMessage`, `MailDeliveryException`,
`MailTransport`, `SmtpMailTransport` and `ResendMailTransport`. Only identity-service
and cashclose-service import its configuration. No database migrations are needed
for this email change.

## Events and recipients

- Cash-close submission publishes `CashCloseSubmittedEvent` in the same transaction
  as the close and decision. `CashCloseEmailListener` queues work only after commit;
  rollback sends nothing. Rejection by the bounded executor and delivery failure
  cannot undo the submission or change its successful response.
- The immutable event snapshots the actual submitting staff member (not necessarily
  the draft creator), branch, shift, reference, date, status and recipient addresses.
  Recipient queries run before commit under the verified tenant's RLS. No entities,
  JDBC transactions or request security context cross into email threads.
- Recipients are the submitter plus active staff with a live assignment to an active
  position whose **`position_code = STORE_MANAGER`** at the submitted branch.
  `position_name` and security roles do not select recipients. Recipients are
  deduplicated case-insensitively; a submitting manager receives one submitter email.
  Empty addresses are skipped. Receiving a notification does not grant review access.
- Managers receive the configured review link; submitters receive the history link.
  Each recipient gets an individual message, with both plain text and HTML bodies.
  Dynamic values are HTML-escaped by Thymeleaf.
- `OtpMailer` publishes `OtpEmailRequested`; `OtpEmailListener` queues delivery via
  the same `EmailService` for signup, password reset and business-registration OTPs.
  OTP state is in-memory, so its listener uses normal events rather than transaction
  events. Exhausted delivery invalidates that OTP only, preserving any newer code.
  Queue rejection returns the existing delivery-unavailable error. Provider faults
  use the existing five-minute mail-health latch.
- Registration decision letters also publish an event after the service's transaction
  commits and use the same transports. `notified_at` is recorded only after delivery.

The executor has 2–4 threads and a queue of 100 tasks. SMTP and Resend calls have
bounded timeouts. Transient transport failures get at most three attempts; invalid
credentials, invalid messages and other permanent errors do not retry. Logs omit
message bodies, OTPs, passwords, addresses and provider response bodies.

This preserves dev's in-process delivery model: there is no durable outbox or replay
worker. A process crash can lose queued mail, and ambiguous provider timeouts may
produce duplicate delivery on retry. Registration's `notified_at` remains its
existing delivery signal; cash-close submission succeeds even if mail cannot send.

## Configuration

Set these independently on both deployed services:

| Environment variable | Meaning |
| --- | --- |
| `MAIL_PROVIDER` | `smtp` (default) or `resend`; an unknown provider fails startup. |
| `MAIL_FROM` | Sender address accepted by the chosen provider. |
| `MAIL_ENABLED` | Defaults to `true`; `false` disables delivery and makes OTP unavailable. |
| `RESEND_API_KEY` | Required only for Resend; SMTP configuration is unnecessary in this mode. |
| `SMTP_HOST`, `SMTP_PORT` | SMTP server; default port 587. |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP credentials. |
| `SMTP_AUTH` | Defaults to `true`. |
| `SMTP_STARTTLS`, `SMTP_STARTTLS_REQUIRED` | Default to `true`. |

Cashclose-service link settings:

| Environment variable | Default |
| --- | --- |
| `FRONTEND_BASE_URL` | `http://localhost:3000` |
| `CASH_CLOSE_REVIEW_PATH_TEMPLATE` | `/approvals?cashCloseId={id}` |
| `CASH_CLOSE_SUBMITTER_PATH_TEMPLATE` | `/history?cashCloseId={id}` |

Spring properties use `fnb.mail.*` and `fnb.frontend.*`, adapting dev's `app.*`
names to this branch. Existing `MAIL_FROM` and `SMTP_*` settings remain compatible.
Use the deployed frontend URL in production. The `{id}` placeholder contains the
current schema's UUID, not dev's numeric close ID.

## Verification

```sh
# Java 21, Docker, Python 3 and Maven; temporary database is removed on exit.
bash tools/test-mail-integration.sh
```

The runner applies the current SQL changelog in order to its own disposable database
and runs both services' suites. SMTP tests inspect constructed MIME messages; Resend
uses a mock HTTP server. Cash-close integration tests mock `MailTransport`, proving
submission, tenant/branch recipient filtering, links and rendering without sending
real emails. Tests also cover commit versus rollback, queue saturation, recipient
failures, retries, disabled delivery and OTP invalidation. Live provider delivery
still requires valid deployment credentials and a real inbox smoke test.
