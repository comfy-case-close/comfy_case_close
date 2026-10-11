package com.fnbx.cashclose.entity;

import com.fnbx.cashclose.enums.MovementStatus;
import com.fnbx.shared.enums.EffectType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line in the shift's cash ledger.
 *
 * <p>Merged from three former tables: {@code cash_movement},
 * {@code cash_diff_explanation} and {@code tip}. Anything that makes the drawer
 * disagree with the POS, or takes cash out of it, is a row here: supply
 * purchases, end-of-day parking money, tips, small-change swaps, borrowing to
 * give change, a shift lead topping up a shortfall, an unpaid bill, a POS keying
 * error, a miscount.
 *
 * <h2>Sign convention</h2>
 * <pre>
 *   signedAmount &lt; 0  =&gt; cash OUT   (expense, borrowed from drawer)
 *   signedAmount &gt; 0  =&gt; cash IN    (tip in drawer, staff reimbursement)
 * </pre>
 * Same sign space as {@code cashDifference = countedCash - posExpectedCash}, so
 * {@code unexplained = difference - explained - pending} contains no sign flip.
 * The old convention (shortage is positive) forced every reader to remember an
 * inversion, which is a recurring source of arithmetic bugs.
 *
 * <p>{@code NO_CASH_FLOW} carries both signs: "customer forgot to pay 100k"
 * leaves the drawer short (negative), "POS double-counted" leaves it over
 * (positive), and in neither case did a note actually move.
 *
 * <p>There is no {@code absAmount} column. {@code abs()} in a query costs nothing
 * and a stored copy is one more thing that can disagree with its source.
 *
 * <h2>Why {@link #effectType} is repeated here</h2>
 * It already exists on {@link com.fnbx.platform.entity.MovementKind}. The copy is
 * deliberate: a PostgreSQL {@code CHECK} cannot join to another table, so for the
 * database to reject "an expense with a positive amount" the effect must be on
 * this row. The composite foreign key {@code (kind_sk, effect_type)} makes it
 * impossible for the copy to disagree with the kind - it cannot drift.
 *
 * <h2>Editing and approval</h2>
 * A correction inserts an EDIT decision with before/after values. Its database
 * trigger updates this same row and returns it to PENDING. The enclosing close
 * must be editable. A pending line contributes to pending, not explained.
 */
@Entity
@Table(schema = "cashclose", name = "cash_movement")
@Getter
@Setter
@NoArgsConstructor
public class CashMovement {

    @Id
    @Column(name = "movement_id")
    private UUID movementId;

    @Column(name = "cash_close_id", nullable = false) private UUID cashCloseId;
    @Column(name = "business_id", nullable = false)   private UUID businessId;

    /** Points at one specific SCD-2 version of the catalogue entry. */
    @Column(name = "kind_sk", nullable = false) private Long kindSk;

    /** Locked to the kind by a composite FK - see the class javadoc. */
    @Enumerated(EnumType.STRING)
    @Column(name = "effect_type", nullable = false)
    private EffectType effectType;

    /** Negative = out, positive = in. See the sign convention above. */
    @Column(name = "signed_amount", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal signedAmount;

    /** The employee involved: who received a tip, or who topped up the drawer. */
    @Column(name = "staff_user_id")    private UUID staffUserId;

    /**
     * Free text about the line, including who the money went to.
     *
     * <p>There is deliberately no separate {@code personOrVendor} column. It
     * existed in the old sheet and became a second description field: the real
     * data holds first names ("Su", "Khang"), goods ("Sua + cam + duong"), objects
     * ("Bon cau"), services ("Bao hanh ghe don") and whole sentences ("Anh teo bam
     * lon bill"), plus many blanks. Nothing groupable, and undefined for a tip.
     *
     * <p>Grouping already exists at the right level:
     * {@code movementKind.expenseCategory}. Real per-supplier analysis would need
     * a vendor dimension with a foreign key and a controlled list, not free text.
     */
    @Column(name = "description")      private String description;

    /** A {@code files.stored_file} of the same business - enforced by a composite FK. */
    @Column(name = "receipt_file_id") private UUID receiptFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private MovementStatus approvalStatus = MovementStatus.PENDING;

    /** Who recorded this line; staffUserId is the person involved, if any. */
    @Column(name = "created_by") private UUID createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public boolean isCashOut()  { return effectType == EffectType.CASH_OUT; }
    public boolean isCashIn()   { return effectType == EffectType.CASH_IN; }

    /** Positive magnitude, for display. */
    public BigDecimal absAmount() {
        return signedAmount == null ? BigDecimal.ZERO : signedAmount.abs();
    }
}
