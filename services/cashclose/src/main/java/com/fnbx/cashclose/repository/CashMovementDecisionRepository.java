package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.CashMovementDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Decision history for cash movement lines. Java inserts actions; the database
 * validates and applies each one. UPDATE/DELETE remain forbidden.
 */
public interface CashMovementDecisionRepository extends JpaRepository<CashMovementDecision, UUID> {

    List<CashMovementDecision> findByMovementIdOrderByDecidedAtAsc(UUID movementId);

    /**
     * Every decision taken on any line of one close, oldest first.
     *
     * <p>Needs a join because the decision row carries no {@code cashCloseId} -
     * that would duplicate what the movement already knows. This is an audit
     * query, not a hot path.
     */
    @Query("""
           SELECT d FROM CashMovementDecision d
             JOIN CashMovement m ON m.movementId = d.movementId
            WHERE m.cashCloseId = :cashCloseId
            ORDER BY d.decidedAt ASC
           """)
    List<CashMovementDecision> findByCashCloseId(@Param("cashCloseId") UUID cashCloseId);
}
