package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.TipPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TipPayoutRepository extends JpaRepository<TipPayout, Long> {

    @Query("SELECT p FROM TipPayout p " +
            "WHERE p.payoutDate BETWEEN :fromDate AND :toDate " +
            "AND (:branchId IS NULL OR p.branch.id = :branchId) " +
            "ORDER BY p.payoutDate DESC, p.id DESC")
    List<TipPayout> findInRange(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("branchId") Long branchId);

    @Query("SELECT COALESCE(SUM(p.amount), 0L) FROM TipPayout p " +
            "WHERE p.payoutDate BETWEEN :fromDate AND :toDate " +
            "AND (:branchId IS NULL OR p.branch.id = :branchId)")
    long sumInRange(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("branchId") Long branchId);
}
