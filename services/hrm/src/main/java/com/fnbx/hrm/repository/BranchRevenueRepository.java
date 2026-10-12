package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.BranchRevenue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRevenueRepository extends JpaRepository<BranchRevenue, UUID> {

    List<BranchRevenue> findByPeriodId(UUID periodId);

    Optional<BranchRevenue> findByPeriodIdAndBranchIdIsNull(UUID periodId);

    Optional<BranchRevenue> findByPeriodIdAndBranchId(UUID periodId, UUID branchId);
}
