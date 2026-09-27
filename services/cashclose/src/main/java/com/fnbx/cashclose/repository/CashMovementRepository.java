package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.enums.MovementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The shift's cash ledger.
 *
 * <p>This table now covers what used to be three: expenses, difference
 * explanations and tips. Hence no {@code CashDiffExplanationRepository} or
 * {@code TipRepository} - filter on {@code movement_kind.diffReasonGroup}
 * instead of switching tables.
 */
public interface CashMovementRepository extends JpaRepository<CashMovement, UUID>, JpaSpecificationExecutor<CashMovement> {

    List<CashMovement> findByCashCloseId(UUID cashCloseId);

    List<CashMovement> findByCashCloseIdAndApprovalStatus(
            UUID cashCloseId, MovementStatus approvalStatus);

    /**
     * Any line still waiting. A close cannot be approved while one exists - the
     * DB trigger blocks it too, this is only for an earlier, clearer error.
     */
    boolean existsByCashCloseIdAndApprovalStatus(
            UUID cashCloseId, MovementStatus approvalStatus);

    long countByCashCloseId(UUID cashCloseId);

    void deleteByCashCloseId(UUID cashCloseId);

    /**
     * Tip totals for a branch, resolved through the versioned movement-kind
     * catalogue. Only approved closes and approved lines fund a payout; the
     * split schema gives each movement its own approval state. The database RLS
     * policy still enforces the business scope.
     */
    @Query(value = """
            SELECT COALESCE(SUM(abs(m.signed_amount)), 0)
              FROM cashclose.cash_movement m
              JOIN cashclose.cash_close c ON c.cash_close_id = m.cash_close_id
              JOIN platform.movement_kind k ON k.kind_sk = m.kind_sk
             WHERE c.branch_id = :branchId
               AND c.business_date BETWEEN :fromDate AND :toDate
               AND c.status = 'APPROVED'
               AND m.approval_status = 'APPROVED'
               AND k.kind_code = :kindCode
            """, nativeQuery = true)
    BigDecimal sumTipsByKind(@Param("branchId") UUID branchId,
                             @Param("fromDate") LocalDate fromDate,
                             @Param("toDate") LocalDate toDate,
                             @Param("kindCode") String kindCode);
}
