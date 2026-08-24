package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.Alert;
import com.comfy.caseclose.utils.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    List<Alert> findByStatusAndCashCloseIdIsNotNull(AlertStatus status);

    Page<Alert> findByCashCloseId(Long cashCloseId, Pageable pageable);
}
