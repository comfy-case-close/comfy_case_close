package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.FundWithdrawal;
import com.fnbx.cashclose.enums.FundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Fund withdrawals - grouped by branch and period, not by close. */
public interface FundWithdrawalRepository extends JpaRepository<FundWithdrawal, UUID> {

    List<FundWithdrawal> findByBranchIdOrderByPeriodFromDesc(UUID branchId);

    List<FundWithdrawal> findByBranchIdAndStatus(UUID branchId, FundStatus status);

    /**
     * What the system expects to have been withdrawn in a period: the sum of
     * {@code withdrawalAmount} over approved closes at that branch within the dates.
     *
     * <p>Comparing this against the hand-entered {@code actualReceivedAmount} is
     * the entire point of {@link FundWithdrawal}: a gap means cash went missing in
     * transit between the branch drawer and the owner - a loss no individual close
     * can reveal, because each shift balances on its own.
     */
    @Query("""
           SELECT COALESCE(SUM(c.withdrawalAmount), 0)
             FROM CashClose c
            WHERE c.branchId = :branchId
              AND c.status = com.fnbx.cashclose.enums.CloseStatus.APPROVED
              AND c.businessDate BETWEEN :from AND :to
           """)
    BigDecimal sumApprovedWithdrawals(@Param("branchId") UUID branchId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);
}
