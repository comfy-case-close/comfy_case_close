package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.CashDenomination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashDenominationRepository extends JpaRepository<CashDenomination, Long> {

    List<CashDenomination> findByCashCloseId(Long cashCloseId);

    // Bulk delete, not derived: Hibernate flushes inserts before deletes, which would hit
    // uq_cash_denominations_close_value on a replace-then-reinsert in the same transaction.
    @Modifying
    @Query("delete from CashDenomination c where c.cashClose.id = :cashCloseId")
    void deleteByCashCloseId(@Param("cashCloseId") Long cashCloseId);
}
