package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ScheduleGenerationRun;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleGenerationRunRepository extends JpaRepository<ScheduleGenerationRun, UUID> {
}
