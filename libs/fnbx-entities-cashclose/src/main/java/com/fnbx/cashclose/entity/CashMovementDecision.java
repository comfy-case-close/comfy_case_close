package com.fnbx.cashclose.entity;

import com.fnbx.cashclose.enums.MovementStatus;
import com.fnbx.cashclose.enums.MovementAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Append-only action and before/after history for one movement. */
@Entity
@Table(schema = "cashclose", name = "cash_movement_decision")
@Getter
@Setter
@NoArgsConstructor
public class CashMovementDecision {

    @Id
    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "movement_id", nullable = false) private UUID movementId;
    @Column(name = "business_id", nullable = false) private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private MovementAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false)
    private MovementStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private MovementStatus newStatus;

    /** The catalogue version this decision endorsed. */
    @Column(name = "kind_sk", nullable = false) private Long kindSk;

    /** The amount this decision endorsed - see "why the figures are snapshotted". */
    @Column(name = "signed_amount", nullable = false) private BigDecimal signedAmount;

    @Column(name = "decided_by") private UUID decidedBy;

    /** When the decision happened. An event has an instant, not a range. */
    @Column(name = "decided_at", insertable = false, updatable = false)
    private Instant decidedAt;

    @Column(name = "note") private String note;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> changes;

    /** Positive magnitude, for display. */
    public BigDecimal absAmount() {
        return signedAmount == null ? BigDecimal.ZERO : signedAmount.abs();
    }
}
