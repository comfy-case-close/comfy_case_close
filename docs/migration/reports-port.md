# Dashboard reports port from dev

The nine dev report endpoints (`GET /api/v1/reports/kpi`, `by-branch`, `by-date`, `by-shift-type`, `by-employee`, `risk-breakdown`, `issues`, `details`, and `POST /api/v1/reports/monthly-export`) live in **cashclose-service** as `ReportController` → `ReportService` → `ReportServiceImpl`. The gateway route `cashclose-reports` sends exactly those paths there; the older `/api/v1/reports/cash-close/*` and `/api/v1/reports/alerts/*` endpoints stay on reporting-service. Keep that route above `reporting` in the gateway config.

Parameters are dev's: optional `branchId` (now a UUID), `fromDate`, `toDate` (default: first of the month to today), `limit` for issues, `category` for details, `month=yyyy-MM` for the export. There is no `X-Branch-Id` header. Every endpoint needs live `REPORT_READ`. Without `branchId` the report covers every active branch where the caller holds it. A `branchId` outside that set returns 403 (dev returned an empty report). "Today" and future-date checks use the business timezone. A future date or an unknown category returns 422 `INVALID_FILTER`.

Response DTOs keep dev's field names and `long` money values; ids are UUIDs, and `referenceCode` carries `cash_close_code`. Figures follow the new DDL:

| Figure | Source now |
|---|---|
| counted cash, cash difference, unexplained, expense, cash remaining | `cashclose.v_close_calc` (`CashCloseCalc`), never re-added in Java |
| cash difference sign | `counted − POS`: **negative = short** (dev was `POS − counted`) |
| unexplained | `cash_difference − explained − pending` (per close); the day total still sits on the day's last shift, as in dev |
| tips | `cash_movement` lines of kind `TIPS`; "inside drawer" = TIPS lines whose kind `affects_remaining` (all of them today), "separate" = the rest (0 today) |
| bill issue | `UNPAID_BILL` lines |
| operational / other-ops | lines whose kind code is one of dev's reasons: `UNPAID_BILL`, `CUSTOMER_REFUND`, `POS_ERROR`, `MISCOUNT`, `OTHER` (the seed has the first, third and fourth) |
| expense detail | lines whose kind has an `expense_category` (in-shift and end-of-day), labelled by that category |
| risk / warning | derived LOW / MEDIUM / HIGH from the unexplained gap and the thresholds snapshotted at submit; warning = HIGH (no CRITICAL) |
| pending | status `PENDING_REVIEW` only (as in dev; on this branch that is a corrected close) |
| morning / evening count | shift codes `MORNING_CLOSE` / `EVENING_CLOSE`, as in dev — shift types are per business, so these only count when the business uses those codes |

REJECTED lines count as never declared, the same rule `v_close_calc` applies. REJECTED and VOIDED closes are left out, as in dev.
