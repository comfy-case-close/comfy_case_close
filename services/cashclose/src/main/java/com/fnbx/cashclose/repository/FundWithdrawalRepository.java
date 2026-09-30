package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.FundWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface FundWithdrawalRepository extends JpaRepository<FundWithdrawal, UUID>, JpaSpecificationExecutor<FundWithdrawal> {
    Optional<FundWithdrawal> findByCashCloseId(UUID cashCloseId);
}
