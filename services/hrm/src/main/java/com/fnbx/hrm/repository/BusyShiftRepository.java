package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.BusyShift;
import com.fnbx.hrm.entity.BusyShiftId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusyShiftRepository extends JpaRepository<BusyShift, BusyShiftId> {

    List<BusyShift> findByAvailabilitySubmissionId(UUID availabilitySubmissionId);

    List<BusyShift> findByAvailabilitySubmissionIdIn(Collection<UUID> submissionIds);

    @Modifying
    @Query("DELETE FROM BusyShift b WHERE b.availabilitySubmissionId = :submissionId")
    void deleteBySubmission(@Param("submissionId") UUID submissionId);
}
