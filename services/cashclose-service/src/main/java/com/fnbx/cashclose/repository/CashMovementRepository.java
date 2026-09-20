package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.enums.MovementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
