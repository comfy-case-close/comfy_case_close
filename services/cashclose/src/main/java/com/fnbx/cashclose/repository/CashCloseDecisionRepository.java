package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.CashCloseDecision;
import com.fnbx.cashclose.enums.CloseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decision ledger for closes - insert only.
 *
 * <p>No {@code delete*} or {@code update*} method is declared, and declaring one
 * would fail anyway: {@code REVOKE UPDATE, DELETE ON
 * cashclose.cash_close_decision FROM svc_cashclose} blocks it at the grant layer
 * and {@code trg_close_decision_append_only} at the trigger layer.
 *
 * <p>Three layers for one rule looks excessive until you remember this is the
 * audit ledger - if it can be edited, the whole control system is worthless.
 */
public interface CashCloseDecisionRepository extends JpaRepository<CashCloseDecision, UUID> {

    List<CashCloseDecision> findByCashCloseIdOrderByActedAtAsc(UUID cashCloseId);

    /**
     * The newest row that moved a close into a given status. This is where the
     * approver and approval time come from - {@code cash_close} no longer stores
     * them.
     */
    Optional<CashCloseDecision> findFirstByCashCloseIdAndNewStatusOrderByActedAtDesc(
            UUID cashCloseId, CloseStatus newStatus);

    /** One latest approval per close, including closes that were corrected and approved again. */
    @Query(value = """
            SELECT DISTINCT ON (d.cash_close_id) d.*
            FROM cashclose.cash_close_decision d
            WHERE d.cash_close_id IN :cashCloseIds AND d.new_status = 'APPROVED'
            ORDER BY d.cash_close_id, d.acted_at DESC, d.decision_id DESC
            """, nativeQuery = true)
    List<CashCloseDecision> findLatestApprovals(@Param("cashCloseIds") Collection<UUID> cashCloseIds);
}
