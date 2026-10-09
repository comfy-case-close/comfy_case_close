# Cash withdrawals and decision history

A cash close keeps its typed `withdrawalAmount`. Its calculation remains:

```
cashRemaining = countedCash - withdrawalAmount - deductions for affects_remaining movements
```

`countedCash` includes the drawer and tip jar. A correction immediately recalculates cash remaining;
confirmation does not change this formula.

## One stable withdrawal

One physical cash transfer has one `fund_withdrawal_id`. The table stores business, branch,
optional cash close, source/destination pots, amount, withdrawer, `withdrawn_at`, recorder,
current status and note. `withdrawn_at` is the record's date and the default list sort field.
There is no separate code, creation timestamp, transfer ID, revision number, predecessor,
superseded timestamp or correction-reason column. Status is PENDING, CONFIRMED or REJECTED.

`cashclose.fund_withdrawal_decision` is the append-only history for that stable ID:

- CONFIRM: only the named withdrawer with live branch WITHDRAWAL_RECORD permission.
- REJECT: the same identity/permission rule, with a required reason in `note`.
- EDIT: an authorized correction, with its reason in `note` and before/after values in `changes`.

Every entry records actor, database timestamp, old/new status and a JSON `changes` snapshot.
For EDIT the values show the correction. CONFIRM/REJECT retain equal before/after snapshots
of exactly what was acknowledged. Historical values remain intact after later edits.

Example `changes` on an EDIT:

```json
{
  "before": {"amount": 1000000, "withdrawnBy": "<staff UUID>", "withdrawnAt": "2026-09-29T14:00:00Z", "note": null},
  "after": {"amount": 900000, "withdrawnBy": "<staff UUID>", "withdrawnAt": "2026-09-29T14:00:00Z", "note": null}
}
```

The service inserts a decision while holding the close/withdrawal locks. PostgreSQL validates
its old state, actor and snapshots, then applies the state change in the same transaction.
Direct updates cannot bypass the ledger; decision rows cannot be updated or deleted.
Corrections retain the withdrawal ID and original recorder, reset status to PENDING and
require fresh confirmation. A rejected declaration can be reissued with an EDIT and a reason,
even when its figures are unchanged.

## Close submission and correction

Supply attribution in `figures`:

```json
{
  "figures": {
    "withdrawalAmount": 1000000,
    "withdrawnBy": "<staff UUID>",
    "withdrawnAt": "2026-09-29T14:00:00Z"
  }
}
```

A positive withdrawal creates one PENDING DRAWER → BRANCH_SAFE row in the submission
transaction. The authenticated submitter is `recordedBy`. The selected withdrawer must be
active in the same business and have live withdrawal permission at the branch.
A zero/omitted initial amount creates no withdrawal.

Use `POST /api/v1/cash-closes/{id}/corrections` to correct a linked withdrawal. Omitted
amount/person/time retain their values; changing them appends an EDIT to the withdrawal
history. The cash close also records its before/after correction and returns to PENDING_REVIEW.
Close corrections unrelated to the withdrawal keep its existing confirmation.

Correcting the amount to zero still requires confirmation: it corrects an erroneous declaration.
If money physically moved back, record a separate reverse transfer instead.

A close can be approved only when its linked withdrawal is CONFIRMED and its amount matches
`cash_close.withdrawal_amount`. Pending/rejected withdrawals block approval with error 3019.
Database deferred constraints enforce the same rule at transaction commit.

## APIs

All endpoints use `X-Branch-Id` and the authenticated business.

| Endpoint | Behavior |
|---|---|
| `GET /api/v1/fund-withdrawals` | Paginated withdrawals. Filters: `cashCloseId`, `fromDate`, `toDate`, `status`. Dates use withdrawal time in the business timezone; default sort is `withdrawnAt DESC`. |
| `GET /api/v1/fund-withdrawals/{id}` | Current withdrawal values/status. |
| `GET /api/v1/fund-withdrawals/{id}/history` | All CONFIRM, REJECT and EDIT entries in time order, including `changes`. |
| `POST /api/v1/fund-withdrawals` | Create a PENDING standalone transfer (201). Body: `fromPot`, `toPot`, `amount`, `withdrawnBy`, `withdrawnAt`, optional `note`. |
| `POST /api/v1/fund-withdrawals/{id}/confirm` | Confirm PENDING withdrawal. Repeating on CONFIRMED is idempotent. No body. |
| `POST /api/v1/fund-withdrawals/{id}/reject` | Reject PENDING withdrawal. Body: `{"reason":"..."}`. |
| `POST /api/v1/fund-withdrawals/{id}/corrections` | Update a standalone withdrawal (200), keeping its ID/pots. Body: `amount`, `withdrawnBy`, `withdrawnAt`, `editReason`, optional `note`. Appends EDIT and resets PENDING. |

`transferId` and `includeHistory` list parameters are removed; history belongs to `/{id}/history`.
Withdrawal responses omit `code`, `transferId`, `revision`, `supersedesId`, `supersededAt`,
`createdAt` and `editReason`. Correction requests still require `editReason`, stored in the decision note.

FINANCE_READ permits reading all transfers at the branch. WITHDRAWAL_RECORD users without
FINANCE_READ may read their own withdrawals. CLOSE_READ permits reading withdrawals linked
to a specified close at the same branch. History uses the same read authorization as the record.
Recording/correcting standalone transfers requires WITHDRAWAL_RECORD; linked corrections also
follow the cash-close correction authorization. FUND_WITHDRAWAL_WARNING_ABS remains a non-blocking
warning on standalone recording/correction.

Pots are DRAWER, BRANCH_SAFE and CENTRAL_SAFE. The first two belong to the selected branch;
CENTRAL_SAFE belongs to the business. Standalone safe/reverse transfers do not retroactively
change a close's remaining-cash snapshot.

## Confirmed accounting and migration

`cashclose.v_confirmed_fund_transfer` uses the latest CONFIRM snapshot for each stable withdrawal ID.
For example, after confirming 1,000,000 and correcting it to 900,000, the live row is PENDING at
900,000 while confirmed reporting remains 1,000,000. Reconfirmation changes reporting to 900,000.
Amounts are never summed across decisions. A confirmed zero correction removes the erroneous
amount from those totals. This view is a transfer report, not a complete drawer balance.

Apply changeset `cashclose-1.0.0.12-stable-fund-withdrawals` before starting this service version.
It consolidates existing revision chains under their original root withdrawal ID, retains the
latest declaration values, moves corrections into EDIT decisions, and preserves confirmations,
rejections, reasons and their acknowledged values. Old revision-specific IDs are retired.
The separate legacy period table remains read-only and outside this workflow.
