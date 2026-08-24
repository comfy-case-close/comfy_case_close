package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.CashDiffExplanation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashDiffExplanationRepository extends JpaRepository<CashDiffExplanation, Long> {
    List<CashDiffExplanation> findByCashCloseId(Long cashCloseId);

    List<CashDiffExplanation> findByCashCloseIdIn(List<Long> cashCloseIds);
}
