# Cash withdrawal declarations and confirmation

A cash close keeps its typed `withdrawalAmount`. Its calculated view remains:

```
cashRemaining = countedCash - withdrawalAmount - deductions for affects_remaining movements
```
`countedCash` includes the drawer and tip jar. Confirmation does not change this formula. Correcting the amount immediately recalculates cash remaining; it does not wait for confirmation.

## Close submission and correction

Supply attribution in the existing `figures` object:

```json
{
  "figures": {
    "withdrawalAmount": 1000000,
    "withdrawnBy": "<staff UUID>",
    "withdrawnAt": "2026-09-29T14:00:00Z"
  }
}
```

A positive withdrawal creates one PENDING `DRAWER -> BRANCH_SAFE` declaration in the same transaction as submission. The backend records the authenticated submitting person separately as `recordedBy`. The selected withdrawer must be active in the same business and have live `WITHDRAWAL_RECORD` permission at the branch (directly or through a position).

A new close with a zero/omitted amount has no declaration. The person and time are required for a new positive withdrawal. In a correction, omitted amount/person/time preserve the current values. Any changed withdrawal detail creates a new PENDING revision and retains the prior record as SUPERSEDED, including its confirmation/rejection details. A rejected declaration can be resubmitted through a close correction by supplying the withdrawal figures and an edit reason, even if those figures are unchanged.

Changing a prior withdrawal to zero creates a zero-amount revision that still requires confirmation. This prevents removing an acknowledged withdrawal from the record without acknowledgement of the correction. It corrects a declaration; if money really moved back, record the actual reverse transfer separately.

Corrections use the existing `POST /api/v1/cash-closes/{id}/corrections`; the close returns to PENDING_REVIEW and its decision history contains the before/after withdrawal declaration. Other close edits that leave the withdrawal details unchanged retain its confirmation. Linked withdrawals cannot be edited through the standalone withdrawal correction endpoint.

A close can be approved only if its current linked revision is CONFIRMED and matches `cash_close.withdrawal_amount`. Pending and rejected revisions block approval with error 3019. Existing movement-review rules also apply. Close approval and correction are serialized with withdrawal decisions using the close row lock. Confirmation acknowledges the declared withdrawal; it is not a second cash count.

## APIs

All endpoints use `X-Branch-Id` and the authenticated business.

| Endpoint | Behavior |
|---|---|
| `GET /api/v1/fund-withdrawals` | Paginated current revisions. Filters: `cashCloseId`, `transferId`, `fromDate`, `toDate`, `status`, `includeHistory` (default false). Dates filter actual withdrawal time in the business timezone. Use `includeHistory=true` for superseded revisions. |
| `GET /api/v1/fund-withdrawals/{id}` | Read a specific revision and its decision details. |
| `POST /api/v1/fund-withdrawals` | Record a PENDING standalone transfer, with `cashCloseId=null`. Body: `fromPot`, `toPot`, `amount`, `withdrawnBy`, `withdrawnAt`, optional `note`. |
| `POST /api/v1/fund-withdrawals/{id}/confirm` | Only the named person with live branch withdrawal permission can confirm the current PENDING revision. No body. Repeating confirmation on the same confirmed revision is idempotent. |
| `POST /api/v1/fund-withdrawals/{id}/reject` | Same identity rule; body `{"reason":"..."}`. |
| `POST /api/v1/fund-withdrawals/{id}/corrections` | Standalone transfers only. Complete replacement `amount`, `withdrawnBy`, `withdrawnAt`, `editReason`, optional `note`; preserves pots and creates a fresh PENDING revision. Zero amount corrects a transfer that never occurred. |

FINANCE_READ may read all transfers at its branch. WITHDRAWAL_RECORD users without FINANCE_READ can list/read their own declarations. CLOSE_READ permits reading declarations linked to a specified close at the same branch. Recording and correcting declarations requires WITHDRAWAL_RECORD. The existing configured `FUND_WITHDRAWAL_WARNING_ABS` is retained as a non-blocking warning on standalone create/correction responses. There is no old expected-pot warning because transfers are no longer derived from period totals.

Pots are DRAWER, BRANCH_SAFE, and CENTRAL_SAFE. The first two belong to the selected branch; CENTRAL_SAFE belongs to its business. Safe transfers and reverse directions are supported. Standalone transfers do not retroactively edit a cash close's withdrawal amount or remaining-cash snapshot. Use the close submission/correction workflow for transfers that should affect that close.

## Revision accounting

Each row has its own `fund_withdrawal_id`; one physical transfer has a stable `transfer_id` and increasing `revision`. `supersedes_id` links the preceding revision. Only one revision can be current per transfer, and only one current transfer declaration can link to a close. Amount, attribution, and timestamps on existing declarations cannot be overwritten or deleted.

Do not sum the full history. `cashclose.v_confirmed_fund_transfer` selects the newest **confirmed** revision per transfer. During a pending/rejected correction, the previous confirmed declaration remains effective in that view. Once the correction is confirmed, it replaces that amount. The view is suitable for transfer totals/net movement per pot, not a complete drawer balance: sales, expenses, starting balances, and cash counts are separate.

Example: close count 1,500,000; withdrawal revision 1 is 1,000,000 confirmed; then revision 2 declares 900,000 pending. Close cash remaining immediately becomes 600,000. The confirmed-transfer view remains 1,000,000 until revision 2 is confirmed, then becomes 900,000, never 1,900,000.

Voiding/rejecting a close does not undo a physical transfer. Its transfer history remains. Any reversal of money is a new transfer; an erroneous declaration is corrected through revision and confirmation while the close remains editable.

## Migration and compatibility

Apply migration `007-cashclose-010-withdrawal-confirmation` before starting the updated service. It creates the revised table with tenant RLS, scoped foreign keys, revision constraints, immutable-history guards, and deferred consistency checks between close and withdrawal.

The old period-based table becomes read-only `cashclose.fund_withdrawal_legacy`, preserving any existing data. No withdrawer or confirmation is fabricated for legacy records. Existing positive close amounts need explicit attribution through a correction before they can be newly approved. Legacy rows are excluded from new transfer reporting; reconcile historical records before relying on a complete historical report.

The old GET summary and POST period/actual-received contracts are replaced. `periodType/fromDate/toDate` in POST, system-pot snapshots, system withdrawal amounts, and variance fields are no longer part of the active model. Frontends must use the endpoints/fields above; weekly/monthly reporting filters actual transfer timestamps.
