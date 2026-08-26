package com.comfy.caseclose.entity;

import com.comfy.caseclose.utils.enums.CashCloseStatus;
import com.comfy.caseclose.utils.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One row per shift-close submission. Two invariants matter for concurrent access, both backed
 * by real DB-level enforcement (not just application-level checks — see the bug hunt that found
 * the app code alone wasn't enough):
 * <ul>
 *   <li>At most one <strong>active</strong> (non-VOIDED) close per branch/date/shift — enforced by
 *       the partial unique index {@code uq_cash_closes_branch_date_shift_active} (liquibase
 *       changeset {@code 1.0.0.6-cash-close-concurrency.xml}), so a race between two submits
 *       can't create duplicates even if the application-level {@code requireNoActiveClose}
 *       pre-check itself races. Partial (not a plain unique constraint) so that voiding a close
 *       actually frees the slot for a fresh submission, matching {@code findActiveByBranchAndDate}
 *       — a plain JPA {@code @UniqueConstraint} can't express the {@code WHERE status <> 'VOIDED'}
 *       clause, so this is documentation of the DB-side invariant, not a generated one
 *       ({@code spring.jpa.hibernate.ddl-auto=none} — this app never generates schema).</li>
 *   <li>Status transitions (approve/reject/void) are protected by {@link #version} — two
 *       reviewers racing to decide the same close will have the loser's commit fail with an
 *       optimistic-lock exception instead of silently overwriting the winner's decision.</li>
 * </ul>
 */
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

    /** Optimistic lock — see the class Javadoc. Hibernate manages this column; never set it by hand. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
