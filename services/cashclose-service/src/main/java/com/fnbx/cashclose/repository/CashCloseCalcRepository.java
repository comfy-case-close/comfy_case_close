package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.CashCloseCalc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Reads the derived figures of a close from {@code cashclose.v_close_calc}.
 *
 * <p>Read only - {@link CashCloseCalc} is {@code @Immutable} over a
 * {@code @Subselect}. Any write is ignored by Hibernate and refused by the DB.
 *
 * <p><b>Java must never re-add these numbers itself.</b> Two implementations of
 * "unexplained difference", one in SQL and one in Java, will eventually disagree,
 * and the owner will see one figure on the dashboard and another in a report.
 * The formula lives in the view; this only reads it.
 */
public interface CashCloseCalcRepository extends JpaRepository<CashCloseCalc, UUID> {
}
