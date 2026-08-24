package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.CashDenomination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashDenominationRepository extends JpaRepository<CashDenomination, Long> {

    List<CashDenomination> findByCashCloseId(Long cashCloseId);
}
