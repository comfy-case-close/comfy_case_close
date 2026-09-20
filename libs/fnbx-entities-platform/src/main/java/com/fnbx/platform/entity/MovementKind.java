package com.fnbx.platform.entity;

import com.fnbx.shared.enums.EffectType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The single catalogue behind the cash ledger. Replaces three former tables:
 * {@code cash_movement_type}, {@code expense_category} and {@code cash_diff_reason}.
 *
 * <h2>Why they were merged</h2>
 * In Comfy's real data 260 of 311 explanation rows were verbatim copies of a
 * movement row - same amount, note reading "auto from expense: ...", and a reason
 * code mechanically derived from the category ({@code SUPPLY -> SUPPLY_NOT_IN_POS}).
 * Two tables, one fact.
 *
 * <h2>Three flags that decide the arithmetic</h2>
 * <table>
 *   <caption>They live here, not on the movement row</caption>
 *   <tr><th>Flag</th><th>Question it answers</th></tr>
 *   <tr><td>{@link #effectType}</td><td>Did cash actually move, and which way?</td></tr>
 *   <tr><td>{@link #affectsDifference}</td>
 *       <td>Was this already inside the counted cash? (i.e. does it explain the
 *           gap against POS)</td></tr>
 *   <tr><td>{@link #affectsRemaining}</td>
 *       <td>Does this leave the drawer before the cash is handed over?</td></tr>
 * </table>
 *
 * <p>They depend on {@link #kindCode}, not on the movement row's key, so storing
 * them on the fact would break 3NF. Staff never enter them: they pick a kind and
 * the database derives the rest.
 *
 * <p>{@code TIP_IN_DRAWER} is the only kind with both {@code affectsDifference}
 * and {@code affectsRemaining} - the tip is inside the drawer when counted (so it
 * explains the surplus) and is then taken out for staff (so it reduces what is
 * left).
 *
 * <h2>SCD-2: never update in place</h2>
 * Changing a business rule means inserting a new version
 * ({@link #validFrom}/{@link #validTo}), not editing the old row. A movement
 * points at the {@link #kindSk} of the version in force on the close's
 * <b>business date</b>, so:
 * <ul>
 *   <li>a close approved in 2026 is never recomputed under a 2027 rule;</li>
 *   <li>reads need no temporal predicate - just {@code JOIN ON kind_sk}. The only
 *       place a date is compared is at insert time, via
 *       {@code platform.fn_movement_kind_at()}.</li>
 * </ul>
 *
 * <p>{@link #effectType} must stay constant across versions of one
 * {@link #kindCode} - the {@code fn_movement_kind_effect_stable} trigger enforces
 * it. Otherwise {@code GROUP BY kind_code} would sum opposite directions together.
 *
 * <p>{@code businessId} null means a global default every tenant can read;
 * non-null means a tenant-specific kind. See {@code fn_apply_tenant_or_global_rls}.
 */
@Entity
@Table(schema = "platform", name = "movement_kind")
@Getter
@Setter
@NoArgsConstructor
public class MovementKind {

    /** Surrogate key pointing at one specific version, not at a kind code. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "kind_sk")
    private Long kindSk;

    /** Stable across versions. This is what reports group by. */
    @Column(name = "kind_code", nullable = false)
    private String kindCode;

    /** Null means a global default. */
    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "valid_from", nullable = false) private LocalDate validFrom;

    /** Null means this version is still in force. */
    @Column(name = "valid_to")                     private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_type", nullable = false)
    private EffectType effectType;

    @Column(name = "affects_difference", nullable = false) private boolean affectsDifference;
    @Column(name = "affects_remaining",  nullable = false) private boolean affectsRemaining;

    /**
     * "What was it spent on" axis, used by the expense report. Null means this is
     * not an expense at all - borrowing from the drawer, swapping small change or
     * a suspected loss are cash out, but not cost.
     */
    @Column(name = "expense_category")  private String expenseCategory;

    /** "Why the drawer disagrees with POS" axis, used by the shortage report. */
    @Column(name = "diff_reason_group") private String diffReasonGroup;

    @Column(name = "requires_receipt", nullable = false) private boolean requiresReceipt;
    @Column(name = "requires_note",    nullable = false) private boolean requiresNote;

    @Column(name = "display_name", nullable = false) private String displayName;

    /** Is this a real cost (goes to P&L) rather than just a cash movement. */
    public boolean isExpense() { return expenseCategory != null; }

    /** Whether this version applies on the given business date. */
    public boolean isEffectiveOn(LocalDate businessDate) {
        return !businessDate.isBefore(validFrom)
                && (validTo == null || businessDate.isBefore(validTo));
    }
}
