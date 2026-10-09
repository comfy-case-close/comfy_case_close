package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.BranchSchedulingSetting;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchSchedulingSettingRepository extends JpaRepository<BranchSchedulingSetting, UUID> {
}
