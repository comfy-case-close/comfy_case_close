package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.CashDiffExplanation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashDiffExplanationRepository extends JpaRepository<CashDiffExplanation, Long> {
    List<CashDiffExplanation> findByCashCloseId(Long cashCloseId);

    List<CashDiffExplanation> findByCashCloseIdIn(List<Long> cashCloseIds);

    /** Bulk delete, not a derived one — see CashDenominationRepository#deleteByCashCloseId for why. */
    @Modifying
    @Query("delete from CashDiffExplanation c where c.cashClose.id = :cashCloseId")
    void deleteByCashCloseId(@Param("cashCloseId") Long cashCloseId);
}
