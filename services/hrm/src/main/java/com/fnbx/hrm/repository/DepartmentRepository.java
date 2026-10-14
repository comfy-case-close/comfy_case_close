package com.fnbx.hrm.repository;

import com.fnbx.identity.entity.Department;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Read-only on purpose: departments live in identity.department and only identity-service writes them
 * (README 4.1). hrm looks them up to check a position profile and to join their names into reports.
 */
public interface DepartmentRepository extends Repository<Department, UUID> {

    Optional<Department> findById(UUID departmentId);
}
