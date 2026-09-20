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
}
