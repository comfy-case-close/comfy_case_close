package com.fnbx.cashclose.entity;

import com.fnbx.cashclose.enums.CashPot;
import com.fnbx.cashclose.enums.FundStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One immutable declaration revision of a physical cash transfer. */
@Entity
@Table(schema = "cashclose", name = "fund_withdrawal")
@Getter @Setter @NoArgsConstructor
public class FundWithdrawal {
    @Id @Column(name = "fund_withdrawal_id") private UUID fundWithdrawalId;
    @Column(name = "fund_withdrawal_code", nullable = false) private String fundWithdrawalCode;
    @Column(name = "transfer_id", nullable = false) private UUID transferId;
    @Column(nullable = false) private int revision;
    @Column(name = "supersedes_id") private UUID supersedesId;
    @Column(name = "business_id", nullable = false, updatable = false) private UUID businessId;
    @Column(name = "branch_id", nullable = false) private UUID branchId;
    @Column(name = "cash_close_id") private UUID cashCloseId;
    @Enumerated(EnumType.STRING) @Column(name = "from_pot", nullable = false) private CashPot fromPot;
    @Enumerated(EnumType.STRING) @Column(name = "to_pot", nullable = false) private CashPot toPot;
    @Column(nullable = false) private BigDecimal amount;
    @Column(name = "withdrawn_by", nullable = false) private UUID withdrawnBy;
    @Column(name = "withdrawn_at", nullable = false) private Instant withdrawnAt;
    @Column(name = "recorded_by", nullable = false) private UUID recordedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FundStatus status = FundStatus.PENDING;
    @Column(name = "confirmed_by") private UUID confirmedBy;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "rejected_by") private UUID rejectedBy;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @Column(name = "rejection_reason") private String rejectionReason;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "edit_reason") private String editReason;
    @Column private String note;
}
