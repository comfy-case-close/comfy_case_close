package com.fnbx.cashclose.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Append-only payout from a branch's pooled tip jar. */
@Entity
@Table(schema = "cashclose", name = "tip_payout")
@Getter
@Setter
@NoArgsConstructor
public class TipPayout {
    @Id
    @Column(name = "tip_payout_id")
    private UUID tipPayoutId;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "amount", nullable = false, updatable = false, columnDefinition = "shared.d_money_nonneg")
    private BigDecimal amount;

    @Column(name = "payout_date", nullable = false, updatable = false)
    private LocalDate payoutDate;

    @Column(name = "recipient_name", length = 100, updatable = false)
    private String recipientName;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
