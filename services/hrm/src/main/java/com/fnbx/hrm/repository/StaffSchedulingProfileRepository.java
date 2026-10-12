package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.StaffSchedulingProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffSchedulingProfileRepository extends JpaRepository<StaffSchedulingProfile, UUID> {
}
