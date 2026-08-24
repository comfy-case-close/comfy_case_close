package com.comfy.caseclose.entity;

import com.comfy.caseclose.utils.enums.CashCloseStatus;
import com.comfy.caseclose.utils.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "cash_closes")
public class CashClose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_code", nullable = false, unique = true, length = 60)
    private String referenceCode;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_type_id", nullable = false)
    private ShiftType shiftType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "pos_expected_cash", nullable = false)
    private Long posExpectedCash = 0L;

    @Column(name = "counted_cash", nullable = false)
    private Long countedCash = 0L;

    @Column(name = "withdrawal_amount", nullable = false)
    private Long withdrawalAmount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CashCloseStatus status = CashCloseStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_late", nullable = false)
    private Boolean isLate = false;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
