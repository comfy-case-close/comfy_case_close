package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.FundWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FundWithdrawalRepository extends JpaRepository<FundWithdrawal, Long> {

    @Query("SELECT fw FROM FundWithdrawal fw " +
            "WHERE fw.status = com.comfy.caseclose.utils.enums.FundWithdrawalStatus.POSTED " +
            "AND fw.periodFrom <= :toDate AND fw.periodTo >= :fromDate " +
            "AND (:branchId IS NULL OR fw.branch.id = :branchId)")
    List<FundWithdrawal> findPostedOverlapping(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("branchId") Long branchId);
}
