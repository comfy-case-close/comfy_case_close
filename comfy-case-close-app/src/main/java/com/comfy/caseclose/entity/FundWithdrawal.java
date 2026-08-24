package com.comfy.caseclose.entity;

import com.comfy.caseclose.utils.enums.FundPeriodType;
import com.comfy.caseclose.utils.enums.FundWithdrawalStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "fund_withdrawals")
public class FundWithdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private FundPeriodType periodType = FundPeriodType.MONTHLY;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Column(name = "system_pot_before", nullable = false)
    private Long systemPotBefore = 0L;

    @Column(name = "system_withdraw_amount", nullable = false)
    private Long systemWithdrawAmount = 0L;

    @Column(name = "actual_received_amount", nullable = false)
    private Long actualReceivedAmount = 0L;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private FundWithdrawalStatus status = FundWithdrawalStatus.POSTED;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
