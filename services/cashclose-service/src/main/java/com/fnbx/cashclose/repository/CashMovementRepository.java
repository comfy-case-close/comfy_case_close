package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.enums.MovementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /** Every declared TIPS line of one shift, including lines still under review. */
    @Query(value = """
            SELECT m.*
              FROM cashclose.cash_movement m
              JOIN platform.movement_kind k ON k.kind_sk = m.kind_sk
             WHERE m.cash_close_id = :cashCloseId
               AND k.kind_code = 'TIPS'
             ORDER BY m.created_at, m.movement_id
            """, nativeQuery = true)
    List<CashMovement> findShiftTips(@Param("cashCloseId") UUID cashCloseId);

    /**
     * Every ledger line of a batch of closes, for the reports. Replaces dev's
     * three batch reads (movements, diff explanations, tips): callers split the
     * lines by their {@code movement_kind} (kind code, expense category, flags).
     */
    List<CashMovement> findByCashCloseIdIn(Collection<UUID> cashCloseIds);
}
