package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.Tip;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TipRepository extends JpaRepository<Tip, Long> {

    List<Tip> findByCashCloseId(Long cashCloseId);

    List<Tip> findByCashCloseIdIn(List<Long> cashCloseIds);

    @Query("SELECT COALESCE(SUM(t.amount), 0L) FROM Tip t " +
            "WHERE t.isInsideCashDrawer = :insideDrawer " +
            "AND t.cashClose.businessDate BETWEEN :fromDate AND :toDate " +
            "AND t.cashClose.status NOT IN :excludedStatuses " +
            "AND (:branchId IS NULL OR t.cashClose.branch.id = :branchId)")
    long sumByFlow(
            @Param("insideDrawer") boolean insideDrawer,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("excludedStatuses") List<CashCloseStatus> excludedStatuses,
            @Param("branchId") Long branchId);

    /** Bulk delete, not a derived one — see CashDenominationRepository#deleteByCashCloseId for why. */
    @Modifying
    @Query("delete from Tip c where c.cashClose.id = :cashCloseId")
    void deleteByCashCloseId(@Param("cashCloseId") Long cashCloseId);
}
