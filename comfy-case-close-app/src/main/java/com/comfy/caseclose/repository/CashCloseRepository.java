package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.CashClose;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashCloseRepository extends JpaRepository<CashClose, Long> {
    Page<CashClose> findByBranchIdAndBusinessDateAndStatusNotIn(
            Long branchId,
            LocalDate businessDate,
            List<CashCloseStatus> excludedStatuses,
            Pageable pageable);

    Page<CashClose> findByBranchIdAndStatusNotIn(
            Long branchId,
            List<CashCloseStatus> excludedStatuses,
            Pageable pageable);

    List<CashClose> findByBranchIdAndBusinessDateAndStatusNotIn(
            Long branchId,
            LocalDate businessDate,
            List<CashCloseStatus> excludedStatuses);

    Optional<CashClose> findByReferenceCode(String referenceCode);

    boolean existsByReferenceCode(String referenceCode);

    @Query("SELECT cc FROM CashClose cc WHERE cc.branch.id = :branchId " +
            "AND cc.businessDate = :businessDate " +
            "AND cc.status NOT IN ('VOIDED')")
    List<CashClose> findActiveByBranchAndDate(
            @Param("branchId") Long branchId,
            @Param("businessDate") LocalDate businessDate);

    @Query("SELECT cc FROM CashClose cc " +
            "WHERE cc.businessDate BETWEEN :fromDate AND :toDate " +
            "AND cc.status NOT IN :excludedStatuses " +
            "AND (:branchId IS NULL OR cc.branch.id = :branchId)")
    List<CashClose> findForReport(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("excludedStatuses") List<CashCloseStatus> excludedStatuses,
            @Param("branchId") Long branchId);
}
