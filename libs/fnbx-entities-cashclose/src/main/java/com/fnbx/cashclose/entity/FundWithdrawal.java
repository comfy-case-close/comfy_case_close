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

/** One physical cash transfer; corrections and decisions are retained in its decision ledger. */
@Entity
@Table(schema = "cashclose", name = "fund_withdrawal")
@Getter @Setter @NoArgsConstructor
public class FundWithdrawal {
    @Id @Column(name = "fund_withdrawal_id") private UUID fundWithdrawalId;
    @Column(name = "business_id", nullable = false, updatable = false) private UUID businessId;
    @Column(name = "branch_id", nullable = false) private UUID branchId;
    @Column(name = "cash_close_id") private UUID cashCloseId;
    @Enumerated(EnumType.STRING) @Column(name = "from_pot", nullable = false) private CashPot fromPot;
    @Enumerated(EnumType.STRING) @Column(name = "to_pot", nullable = false) private CashPot toPot;
    @Column(nullable = false) private BigDecimal amount;
    @Column(name = "withdrawn_by", nullable = false) private UUID withdrawnBy;
    @Column(name = "withdrawn_at", nullable = false) private Instant withdrawnAt;
    @Column(name = "recorded_by", nullable = false) private UUID recordedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FundStatus status = FundStatus.PENDING;
    @Column private String note;
}
