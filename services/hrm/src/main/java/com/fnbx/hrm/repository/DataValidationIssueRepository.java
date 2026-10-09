package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.DataValidationIssue;
import com.fnbx.hrm.enums.IssueSeverity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataValidationIssueRepository extends JpaRepository<DataValidationIssue, UUID> {

    List<DataValidationIssue> findByPeriodId(UUID periodId);

    List<DataValidationIssue> findByPeriodIdAndSeverity(UUID periodId, IssueSeverity severity);

    long countByPeriodIdAndAcknowledgedFalse(UUID periodId);

    long countByPeriodIdAndSeverityAndAcknowledgedFalse(UUID periodId, IssueSeverity severity);

    void deleteByPeriodId(UUID periodId);
}
