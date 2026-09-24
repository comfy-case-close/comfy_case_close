package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.TipPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Append-only payouts; tenant isolation is enforced by RLS. */
public interface TipPayoutRepository extends JpaRepository<TipPayout, UUID> {
    List<TipPayout> findByBranchIdAndPayoutDateBetweenOrderByPayoutDateDescCreatedAtDesc(
            UUID branchId, LocalDate fromDate, LocalDate toDate);

    @Query("""
           SELECT COALESCE(SUM(p.amount), 0) FROM TipPayout p
           WHERE p.branchId = :branchId AND p.payoutDate BETWEEN :fromDate AND :toDate
           """)
    BigDecimal sumInRange(@Param("branchId") UUID branchId,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate);
}
