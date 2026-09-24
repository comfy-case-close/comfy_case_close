package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.CashDenominationLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * The denomination count - the only source of "how much cash was counted",
 * since {@code cash_close} no longer stores a total.
 */
public interface CashDenominationLineRepository extends JpaRepository<CashDenominationLine, UUID> {
    List<CashDenominationLine> findByCashCloseId(UUID cashCloseId);
    long countByCashCloseId(UUID cashCloseId);

    /**
     * Clears the count so it can be replaced. Deliberately a derived delete rather
     * than a bulk {@code @Modifying} query: the per-row form loads the entities and
     * deletes them one at a time, which lets {@code trg_cash_denomination_line_guard}
     * see each row. A bulk DELETE would still fire the trigger, but would bypass the
     * persistence context and leave stale lines in it for the insert that follows.
     */
    void deleteByCashCloseId(UUID cashCloseId);
}
