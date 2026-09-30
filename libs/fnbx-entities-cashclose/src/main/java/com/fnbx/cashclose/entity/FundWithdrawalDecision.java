package com.fnbx.cashclose.entity;

import com.fnbx.cashclose.enums.FundStatus;
import com.fnbx.cashclose.enums.FundWithdrawalAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/** Append-only decisions and before/after correction history for one withdrawal. */
@Entity
@Table(schema = "cashclose", name = "fund_withdrawal_decision")
@Getter @Setter @NoArgsConstructor
public class FundWithdrawalDecision {
    @Id @Column(name = "decision_id") private UUID decisionId;
    @Column(name = "fund_withdrawal_id", nullable = false, updatable = false) private UUID fundWithdrawalId;
    @Column(name = "business_id", nullable = false, updatable = false) private UUID businessId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private FundWithdrawalAction action;
    @Column(name = "acted_by", nullable = false, updatable = false) private UUID actedBy;
    @Setter(AccessLevel.NONE)
    @Column(name = "acted_at", insertable = false, updatable = false) private Instant actedAt;
    @Enumerated(EnumType.STRING) @Column(name = "old_status", nullable = false, updatable = false) private FundStatus oldStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, updatable = false) private FundStatus newStatus;
    @Column(updatable = false) private String note;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false, updatable = false)
    private java.util.Map<String, Object> changes;
}
