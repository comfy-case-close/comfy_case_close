package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    List<CashMovement> findByCashCloseId(Long cashCloseId);

    List<CashMovement> findByCashCloseIdIn(List<Long> cashCloseIds);
}
