package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.AvailabilitySubmission;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilitySubmissionRepository extends JpaRepository<AvailabilitySubmission, UUID> {

    Optional<AvailabilitySubmission> findByStaffIdAndWeekStart(UUID staffId, LocalDate weekStart);

    List<AvailabilitySubmission> findByWeekStartAndStaffIdIn(LocalDate weekStart, Collection<UUID> staffIds);
}
