package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.enums.CloseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for cash closes.
 *
 * <h2>Why no business_id filter in each query</h2>
 * RLS already filters at the database. Adding {@code WHERE business_id = ?} is
 * redundant and, worse, creates the illusion that isolation is the application's
 * job. If someone forgets it in a new query, RLS still holds. That is what
 * defence in depth means: the application can be wrong and the data stays right.
 *
 * <h2>Why cross-schema joins are legitimate here</h2>
 * Service-Based architecture shares one database precisely so services can join
 * instead of calling each other over HTTP. Making a network call to
 * identity-service just to fetch a name throws that advantage away.
 */
public interface CashCloseRepository extends JpaRepository<CashClose, UUID>, JpaSpecificationExecutor<CashClose> {

    Optional<CashClose> findByCashCloseCode(String cashCloseCode);

    List<CashClose> findByBranchIdAndBusinessDateAndStatusNotIn(
            UUID branchId, LocalDate businessDate, List<CloseStatus> excluded);

    List<CashClose> findByStatusInOrderByBusinessDateDesc(List<CloseStatus> statuses);

    /** Closes awaiting review at the given branches. */
    @Query("""
           SELECT c FROM CashClose c
           WHERE c.status IN :statuses
             AND c.branchId IN :branchIds
           ORDER BY c.businessDate DESC, c.submittedAt DESC
           """)
    List<CashClose> findPendingForBranches(@Param("statuses") List<CloseStatus> statuses,
                                           @Param("branchIds") List<UUID> branchIds);

    /**
     * Closes whose expected cash was typed in by hand - an internal control
     * metric. A branch where the POS is permanently "broken" is a branch to look at.
     */
    @Query("""
           SELECT c FROM CashClose c
           WHERE c.expectedCashSource = com.fnbx.cashclose.enums.ExpectedCashSource.MANUAL
             AND c.businessDate BETWEEN :from AND :to
           ORDER BY c.businessDate DESC
           """)
    List<CashClose> findManualExpectedCash(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    boolean existsByBranchIdAndShiftTypeIdAndBusinessDateAndStatusNot(
            UUID branchId, UUID shiftTypeId, LocalDate businessDate, CloseStatus excluded);
}
