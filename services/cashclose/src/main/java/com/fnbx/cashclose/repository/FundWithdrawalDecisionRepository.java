package com.fnbx.cashclose.repository;

import com.fnbx.cashclose.entity.FundWithdrawalDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FundWithdrawalDecisionRepository extends JpaRepository<FundWithdrawalDecision, UUID> {
    List<FundWithdrawalDecision> findByFundWithdrawalIdAndBusinessIdOrderByActedAtAsc(UUID fundWithdrawalId, UUID businessId);
}
