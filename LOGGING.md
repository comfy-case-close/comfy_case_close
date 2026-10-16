# Backend logging

Every servlet application loads `RequestLoggingAutoConfiguration` from `fnbx-shared` via
Spring Boot's `AutoConfiguration.imports`. The gateway uses its reactive equivalent;
a servlet filter is deliberately not instantiated in WebFlux. No new dependency is required.

## Configuration and overrides

Each application's `application.yml` keeps its own `spring.application.name` and defaults
`logging.level.com.fnbx` to `INFO`. The console level pattern includes that application name
and `requestId`. The same shared class is instantiated independently in each process.

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name} requestId=%X{requestId:-none}]"
  level:
    com.fnbx: INFO
    # Optional: details for operations in one application
    com.fnbx.hrm: DEBUG
    # Optional: servlet request starts (logger belongs to the shared library)
    com.fnbx.shared.logging.RequestLoggingFilter: DEBUG
```

`fnb.logging.request.enabled=false` disables automatic request-filter registration.
`fnb.logging.operation.enabled=false` disables the shared operation advisor.
A service may supply a `RequestLoggingFilter` bean (including a subclass); the default
backs off. The shared filter is not a component. Its registration runs before security.
The gateway has the equivalent `GatewayRequestLoggingFilter` bean override.
An application can replace the advisor by declaring `operationLoggingAdvisor` as an
infrastructure bean (`@Role(BeanDefinition.ROLE_INFRASTRUCTURE)`).

## Requests and errors

- A safe supplied `X-Request-ID` (1–64 ASCII alphanumeric/underscore/hyphen characters)
  is reused; otherwise a UUID is generated. The header is returned in responses.
- Gateway forwards that ID to downstream services. Its completion log uses a configured
  route ID, not a raw request path. Servlet completions use the MVC route template.
- INFO: HTTP method, route, response status, elapsed milliseconds and service name.
  Security rejections are included, even before a controller is selected (`<unmapped>`).
- DEBUG: request start. Servlet MDC is restored at request exit, including exceptions.
  Gateway sets/restores MDC only while emitting its own boundary/error events; it never
  keeps thread-local MDC across Reactor thread switches. This is correlation, not full
  distributed tracing or automatic MDC propagation through arbitrary reactive callbacks.
- `GlobalExceptionHandler` logs expected failures once at WARN using stable error codes;
  server failures once at ERROR using sanitized exception types/frames. Its existing error
  response contract is preserved. The cashclose-specific duplicate advice was removed.
  Gateway's reactive handler follows the same code/level policy.
- The INFO request and service-operation events contain no bodies or credentials.
  Opt-in DEBUG payload details are described below; they can include business, personal
  and financial fields after mandatory credential redaction.

## API operation logs

251 public operations across 53 classes carry `@LogOperation`. CashCloseServiceImpl keeps
its existing 25 instrumented operations and detailed business events rather than receiving
another generic wrapper. Reporting's four read methods are annotated in its controller,
as that module intentionally has no service layer. Empty scaffold modules still receive
HTTP logging and the shared servlet error advice.

```java
@LogOperation                        // starts/completion at DEBUG
public EmployeeResponse getEmployee(UUID staffId) { /* existing logic */ }

@LogOperation(write = true)           // starts at DEBUG; completion at INFO
@Transactional
public EmployeeResponse updateEmployee(UUID staffId, Request request) { /* existing logic */ }
```

The advisor emits operation/class/method, tenant and actor IDs, elapsed time, and only
allowlisted UUID resource parameters. It never serializes arbitrary arguments/results.
Write completion is emitted after a successful transaction commit: joined transactions
register an `afterCommit` callback; rollback emits no completion. If there is no active
transaction, completion means the method returned successfully, not a database commit.
Nested service operations may each have their own completion event. Duration measures
the intercepted invocation, not a later outer transaction's remaining work.

Failures propagate unchanged. The advisor emits failure type/timing only at DEBUG; it
leaves WARN/ERROR to the HTTP handler. Uncaught failures outside HTTP still need the
scheduler/listener's existing error handling. Existing cashclose job logs are preserved.

Spring proxy interception applies to external calls to public annotated methods.
Private helpers and self-invocations are represented by their enclosing operation; they
are not independently intercepted. This avoids log output inside per-row calculation loops.

Example (timestamps/logger names omitted):

```text
DEBUG [hrm-service requestId=demo-1] Service operation started operation=EmployeeServiceImpl.updateEmployee businessId=... actorId=... resourceIds={staffId=...}
INFO  [hrm-service requestId=demo-1] Service operation completed operation=EmployeeServiceImpl.updateEmployee businessId=... actorId=... resourceIds={staffId=...} durationMs=...
INFO  [hrm-service requestId=demo-1] Request completed method=PUT route=/api/v1/payroll/employees/{staffId} status=200 durationMs=... service=hrm-service
```

## DEBUG request/response JSON

`HttpPayloadLoggingAutoConfiguration` supplies a shared formatter and policy.
`ServletPayloadLoggingAutoConfiguration` registers MVC body advice and a metadata interceptor;
`RequestLoggingFilter` supplies a fallback for responses outside MVC. Each request produces
at most one input and one output payload event, correlated by the existing `requestId`.
No payload code is needed in individual controllers or business services.

```yaml
logging:
  level:
    com.fnbx: INFO
    com.fnbx.shared.logging.HttpPayloadLogger: DEBUG
fnb:
  logging:
    payload:
      enabled: true
      max-request-chars: 16384
      max-response-chars: 16384
      full-response: false
      # Optional additions; these cannot remove built-in credential rules.
      additional-sensitive-fields: [taxCode]
```

Both `enabled=true` and DEBUG for `com.fnbx.shared.logging.HttpPayloadLogger` are required.
`com.fnbx: DEBUG` also enables this logger, request starts and service operation details.
`com.fnbx.cashclose: DEBUG` alone does not enable a logger in `com.fnbx.shared`.
Disabling payload logging or using INFO avoids body serialization by the logger, and keeps
existing INFO request/operation events. No operational event levels were changed.

| Setting (`fnb.logging.payload.*`) | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Separate switch; DEBUG is still required |
| `max-request-chars` | `16384` | Maximum characters printed for the sanitized request event |
| `max-response-chars` | `16384` | Maximum characters printed for the sanitized response event |
| `full-response` | `false` | Print the entire sanitized response, ignoring only its character cap |
| `additional-sensitive-fields` | empty | Extra case-insensitive field names to mask recursively |

The event contains method, MVC route template, headers, request parameters or response status,
and the converted JSON body. Sensitive names are normalized (case, underscores and hyphens)
and masked recursively: passwords/passcodes, OTPs, access/refresh/other tokens, Authorization,
API/platform keys, secrets, credentials, cookies and signatures. Credential values learned from
the request are also removed if echoed in response text. Bearer/JWT text and credential values
in signed URLs/public payslip token paths are scrubbed. Built-in masking cannot be disabled,
even in full-response mode. `PayloadRedactor` is the central place to extend these rules.

Redaction happens **before** truncation. `chars` reports the sanitized event length;
`truncated=true` means only its prefix was printed (the clipped prefix may not be valid JSON).
Actual HTTP bodies are unchanged. The cap limits log output, not JSON conversion memory.

```text
DEBUG [cashclose-service requestId=demo-1] HTTP payload direction=request truncated=false chars=... payload={"method":"POST","route":"/api/v1/example","headers":{"authorization":"[REDACTED]"},"body":{"password":"[REDACTED]","amount":25000}}
DEBUG [cashclose-service requestId=demo-1] HTTP payload direction=response truncated=true chars=24590 payload={"method":"POST","route":"/api/v1/example","status":200,...
INFO  [cashclose-service requestId=demo-1] Request completed method=POST route=/api/v1/example status=200 durationMs=... service=cashclose-service
```

Bodies are logged from MVC-converted objects; malformed JSON and converter failures produce
metadata/omission markers instead of raw bytes. Multipart uploads, PDF/Excel downloads, binary
resources and streams have metadata only; their content is never consumed for logging.
Gateway-forwarded JSON is recorded in the destination service once, avoiding duplicate bodies
at the proxy. JSON errors generated by gateway/security handlers are recorded there; request
bodies rejected before parsing are omitted. Gateway streaming behavior stays unchanged.

## Persistent files and archive

All 11 application YAML files use `fnbx-logback-spring.xml` from the shared library to
write console output and a rolling file. `PersistentRollingPolicy` waits for each retention
pass before another rollover can start, preventing concurrent changes to Logback 1.5's
mutable rolling calendar from deleting recent archives during rapid size rollovers. Local default:
`./logs/<spring.application.name>/application.log` (relative to the process working directory).
`FNB_LOG_DIR` changes the base directory. Archive names:
`archive/application.YYYY-MM-DD.<index>.log.gz`.

Rollover occurs on the first event after midnight in `Asia/Ho_Chi_Minh`, or when the active file
reaches the size threshold. Defaults: **100 MB rollover threshold, 30 days of history, 2 GB of archives
per service**. A single large event can exceed the rollover threshold. Old archives are removed by retention/size policy, including cleanup on startup.
`application.log` is appended across restart and becomes an archive on rollover; it is not a
separate permanent copy of that day's history. Multiple size-based archives can exist per day.

`docker-compose.aws.yml` mounts a separate host directory for each of its 10 existing services:

```text
Host:      /var/log/fnbx/cashclose-service/application.log
Host:      /var/log/fnbx/cashclose-service/archive/application.2026-10-16.0.log.gz
Container: /logs/cashclose-service/...
```

Container deletion/recreation retains these host files. A VM/disk deletion still removes them;
this is persistence on the same host, not off-host backup. HRM already has YAML file logging,
but is not one of the services in the existing AWS Compose deployment.
Docker's separate console history uses the `local` driver with 20 MB × 5 files per container;
use host rolling files for history across container recreation.

In `.env.aws` (see `.env.aws.example`):

```dotenv
FNB_LOG_HOST_DIR=/var/log/fnbx
FNB_LOG_LEVEL=DEBUG
FNB_LOG_PAYLOAD_ENABLED=true
FNB_LOG_PAYLOAD_MAX_REQUEST_CHARS=16384
FNB_LOG_PAYLOAD_MAX_RESPONSE_CHARS=16384
FNB_LOG_PAYLOAD_FULL_RESPONSE=false
FNB_LOG_MAX_FILE_SIZE=100MB
FNB_LOG_MAX_HISTORY=30
FNB_LOG_TOTAL_SIZE_CAP=2GB
```

Set `FNB_LOG_PAYLOAD_FULL_RESPONSE=true` to print full sanitized responses, or
`FNB_LOG_PAYLOAD_ENABLED=false` to stop payload logging while retaining operation details.
The Compose environment maps these to Spring Boot properties; local JVM environment names
are `FNB_LOGGING_PAYLOAD_ENABLED`, `FNB_LOGGING_PAYLOAD_MAXREQUESTCHARS`,
`FNB_LOGGING_PAYLOAD_MAXRESPONSECHARS` and `FNB_LOGGING_PAYLOAD_FULLRESPONSE`.
Changing YAML/environment settings requires restarting/recreating the affected application.
On the VM, rebuild changed images and apply with the deployment workflow or:

```sh
docker compose --env-file .env.aws -f docker-compose.aws.yml up -d --build
```

The host log directories must be writable by the container user. Search on the VM:

```sh
tail -f /var/log/fnbx/cashclose-service/application.log
grep -F 'requestId=YOUR_ID' /var/log/fnbx/cashclose-service/application.log
zgrep -F 'requestId=YOUR_ID' /var/log/fnbx/cashclose-service/archive/*.log.gz
```

## Coverage by class

The table records operations explicitly selected for the shared advisor. WRITE means INFO
completion; READ means DEBUG completion. The existing CashCloseServiceImpl instrumentation
is listed separately above.

| Service | Class | WRITE operations | READ operations |
|---|---|---|---|
| cashclose | [FundWithdrawalServiceImpl](services/cashclose/src/main/java/com/fnbx/cashclose/service/impl/FundWithdrawalServiceImpl.java) | `record`, `correct`, `confirm`, `reject`, `syncCloseWithdrawal` | `list`, `get`, `history`, `describeCloseCorrection`, `requireConfirmed`, `withdrawers` |
| cashclose | [ReportServiceImpl](services/cashclose/src/main/java/com/fnbx/cashclose/service/impl/ReportServiceImpl.java) |  | `kpi`, `byBranch`, `byDate`, `byShiftType`, `byEmployee`, `issues`, `details`, `monthlyExport`, `riskBreakdown` |
| cashclose | [ShiftTipsServiceImpl](services/cashclose/src/main/java/com/fnbx/cashclose/service/impl/ShiftTipsServiceImpl.java) |  | `getShiftTips` |
| files | [FileService](services/files/src/main/java/com/fnbx/files/service/FileService.java) | `upload` | `viewUrl` |
| hrm | [AnnualLeaveServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/AnnualLeaveServiceImpl.java) | `setQuota` | `getQuota`, `getBalance` |
| hrm | [AttendanceCodeServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/AttendanceCodeServiceImpl.java) | `create`, `update` | `list` |
| hrm | [AttendanceServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/AttendanceServiceImpl.java) | `create`, `mark`, `clear`, `submit`, `returnToReview`, `confirm`, `reopen` | `find`, `get`, `payrollPreview` |
| hrm | [AvailabilityServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/AvailabilityServiceImpl.java) | `openWindow`, `closeWindow`, `saveOwn`, `submitOwn`, `copyOwn`, `enterFor`, `remind` | `own`, `overview` |
| hrm | [ContractDocumentServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/ContractDocumentServiceImpl.java) | `generate` | `getBatch`, `batchPdf`, `documentPdf`, `preview`, `listForAssignment` |
| hrm | [ContractImportServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/ContractImportServiceImpl.java) | `validate`, `commit` | `template`, `get` |
| hrm | [ContractTemplateServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/ContractTemplateServiceImpl.java) | `create`, `importDocx`, `setActive`, `installStandard` | `list`, `placeholders`, `preview` |
| hrm | [EmployeeAllowanceServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/EmployeeAllowanceServiceImpl.java) | `create` | `listByEmployee` |
| hrm | [EmployeeServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/EmployeeServiceImpl.java) | `createEmployee`, `updateEmployee`, `terminateEmployee` | `listEmployees`, `overview`, `getEmployee`, `getEmployeeRestricted`, `getPayrollHistory` |
| hrm | [EmploymentAssignmentServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/EmploymentAssignmentServiceImpl.java) | `create`, `update`, `end` | `get`, `listByEmployee` |
| hrm | [InsuranceSchemeServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/InsuranceSchemeServiceImpl.java) | `create` | `list` |
| hrm | [LatePenaltyRuleServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/LatePenaltyRuleServiceImpl.java) | `create` | `list` |
| hrm | [ManualPayItemServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/ManualPayItemServiceImpl.java) | `create`, `update`, `delete` | `list` |
| hrm | [OwnScheduleServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/OwnScheduleServiceImpl.java) |  | `employment`, `myShifts`, `week` |
| hrm | [PayComponentServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayComponentServiceImpl.java) | `update` | `list` |
| hrm | [PayrollAuditLogServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollAuditLogServiceImpl.java) |  | `searchAuditLogs` |
| hrm | [PayrollCalculationServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollCalculationServiceImpl.java) | `runPayroll` | `listRuns`, `getRun`, `getPayrollTable`, `getLineCalculation` |
| hrm | [PayrollConfigServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollConfigServiceImpl.java) | `create` | `list`, `getCurrent` |
| hrm | [PayrollLineServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollLineServiceImpl.java) | `createPayrollLine`, `copyPayrollLines`, `updatePayrollLineNote`, `deletePayrollLine` | `listPayrollLines` |
| hrm | [PayrollPaymentServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollPaymentServiceImpl.java) | `markPaid`, `fail` | `list`, `readBankAccount` |
| hrm | [PayrollPeriodServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollPeriodServiceImpl.java) | `createPayrollPeriod`, `updatePeriodDates`, `lockPeriod`, `unlockPeriod`, `markPayrollPeriodPaid` | `listPayrollPeriods`, `getPayrollPeriod`, `listDecisions` |
| hrm | [PayrollReportServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollReportServiceImpl.java) | `setBranchRevenues` | `getDashboard`, `getBranchLaborCost`, `getEmployeeSummary`, `getSharedCostAllocation`, `getReconciliation` |
| hrm | [PayrollRunRecorder](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollRunRecorder.java) | `startRun`, `finishRun` |  |
| hrm | [PayrollServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollServiceImpl.java) |  | `getOwnPeriods`, `getOwnPayslips`, `getOwnPayslip`, `getOwnTimesheet`, `getOwnAnnualLeaveBalance` |
| hrm | [PayrollValidationIssueServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayrollValidationIssueServiceImpl.java) | `acknowledge` | `list` |
| hrm | [PayslipConfirmationServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayslipConfirmationServiceImpl.java) | `confirmOwn`, `resend`, `remindPending` |  |
| hrm | [PayslipServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PayslipServiceImpl.java) | `generate`, `send`, `sendOne` | `list`, `get`, `listEmailLogs` |
| hrm | [PositionServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PositionServiceImpl.java) | `setProfile` | `list` |
| hrm | [PublicPayslipConfirmationServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/PublicPayslipConfirmationServiceImpl.java) | `confirm`, `dispute` | `view` |
| hrm | [ReferenceDataServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/ReferenceDataServiceImpl.java) |  | `banks`, `provinces` |
| hrm | [SalaryStructureServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/SalaryStructureServiceImpl.java) |  | `preview` |
| hrm | [ScheduleAutoFillServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/ScheduleAutoFillServiceImpl.java) | `autoFill`, `undo` | `run` |
| hrm | [SchedulingConfigServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/SchedulingConfigServiceImpl.java) | `createSlot`, `replaceRequirements`, `saveBranchSetting`, `saveStaffProfile` | `slots`, `requirements`, `branchSetting`, `staffProfile`, `staffProfiles` |
| hrm | [ShiftAssignmentServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/ShiftAssignmentServiceImpl.java) | `add`, `patch`, `remove`, `replace`, `swap`, `batch` |  |
| hrm | [ShiftScheduleServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/ShiftScheduleServiceImpl.java) | `create`, `submit`, `approve`, `returnToDraft` | `list`, `get`, `issues`, `candidates`, `changes`, `summary` |
| hrm | [TimesheetServiceImpl](services/hrm/src/main/java/com/fnbx/hrm/service/impl/TimesheetServiceImpl.java) | `saveCells`, `saveCell`, `deleteCell` | `getGrid`, `previewAttendanceInput` |
| identity | [AccessManagementService](services/identity/src/main/java/com/fnbx/identity/service/AccessManagementService.java) | `createPosition`, `addPositionPermissions`, `removePositionPermission`, `replacePositionPermissions` | `dictionary`, `positions`, `position` |
| identity | [GoogleTokenVerifier](services/identity/src/main/java/com/fnbx/identity/service/GoogleTokenVerifier.java) |  | `verify` |
| identity | [StaffDirectoryService](services/identity/src/main/java/com/fnbx/identity/service/StaffDirectoryService.java) |  | `list`, `get`, `enrichAuthenticatedAccount` |
| identity | [VerificationStore](services/identity/src/main/java/com/fnbx/identity/service/VerificationStore.java) | `issueOtp`, `discardOtp`, `discardOtp`, `consumeOtp`, `issueToken`, `consumeToken` |  |
| identity | [AuthServiceImpl](services/identity/src/main/java/com/fnbx/identity/service/impl/AuthServiceImpl.java) | `login`, `refresh`, `logout`, `changePassword`, `updateProfile`, `startSignup`, `verifySignupOtp`, `signup`, `google`, `forgotPassword`, `verifyResetPasswordOtp`, `resetPassword` | `currentUser` |
| identity | [BranchServiceImpl](services/identity/src/main/java/com/fnbx/identity/service/impl/BranchServiceImpl.java) | `create`, `update`, `deactivate`, `assign`, `revoke` | `list`, `get`, `members` |
| identity | [BusinessRegistrationServiceImpl](services/identity/src/main/java/com/fnbx/identity/service/impl/BusinessRegistrationServiceImpl.java) | `start`, `verifyOtp`, `submit`, `approve`, `reject` | `list`, `get` |
| identity | [BusinessServiceImpl](services/identity/src/main/java/com/fnbx/identity/service/impl/BusinessServiceImpl.java) | `deactivate`, `update` | `current` |
| identity | [DepartmentServiceImpl](services/identity/src/main/java/com/fnbx/identity/service/impl/DepartmentServiceImpl.java) | `create`, `update`, `deactivate` | `list` |
| identity | [JoinRequestServiceImpl](services/identity/src/main/java/com/fnbx/identity/service/impl/JoinRequestServiceImpl.java) | `approve`, `reject` | `list`, `get` |
| platform | [ConfigService](services/platform/src/main/java/com/fnbx/platform/service/ConfigService.java) | `update`, `updateBusiness` | `get`, `getBusiness`, `entries`, `businessEntries` |
| platform | [MovementKindService](services/platform/src/main/java/com/fnbx/platform/service/MovementKindService.java) |  | `list` |
| reporting | [ReportController](services/reporting/src/main/java/com/fnbx/reporting/api/ReportController.java) |  | `unacknowledgedAlerts`, `manualExpectedRatio`, `staffRisk`, `summaryByBranch` |

## Verification

New tests cover servlet filter registration/back-off/disable and auto-configuration discovery,
correlation ID validation/restoration, one sanitized exception log with unchanged responses,
real Spring operation proxies, INFO/DEBUG selection, unchanged return values/exceptions,
transaction commit/rollback/commit failure and outer-transaction behavior. Gateway HTTP tests
cover correlation forwarding, security rejections and replacement of unsafe request IDs.
Run with Java 21:

```sh
mvn clean verify
```

The PostgreSQL suites are opt-in and are skipped by plain Maven when their environment
variables are absent. To execute them too, use Docker and the disposable current-schema runner:

```sh
bash tools/test-logging-postgres.sh -q
```

This creates a local PostgreSQL container, applies the current changelog in order, configures
only test-role credentials, exports all four test database URLs, runs `mvn verify`, then removes
the container. Mail transports remain mocked; no real emails or production DB access is involved.

Payload tests additionally cover recursive/echoed credentials, headers/query parameters,
truncation/full mode, disabled/non-DEBUG behavior, malformed JSON, security errors, and
multipart/binary/stream response integrity. A child JVM loads the actual cashclose YAML to
verify daily/size archive behavior and restart persistence. Test the host bind mount separately:

```sh
bash tools/test-logging-storage.sh
```

Verified on 2026-10-16 with Java 21 and disposable PostgreSQL 16: **459 tests, 0 failures,
0 errors, 0 skipped**, including all 86 opt-in PostgreSQL tests. After the final embedded
rolling-policy adjustment, all 47 shared tests passed again, including retention and preservation
of all 800 events across two JVM runs. The stress scenario also passed six consecutive pairs
of runs. Compose validation confirmed all 10 host log mounts, and the Docker recreation test
preserved both the active file and compressed archive. No production database or VM was changed.
