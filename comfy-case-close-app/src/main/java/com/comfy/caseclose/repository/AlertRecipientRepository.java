package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.AlertRecipient;
import com.comfy.caseclose.entity.AlertRecipientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlertRecipientRepository extends JpaRepository<AlertRecipient, AlertRecipientId> {

    @Query("SELECT ar.user.id FROM AlertRecipient ar WHERE ar.alert.id = :alertId")
    List<Long> findUserIdsByAlertId(@Param("alertId") Long alertId);
}
