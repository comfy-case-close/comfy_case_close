package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ImportJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
}
