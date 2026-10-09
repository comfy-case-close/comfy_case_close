package com.fnbx.hrm.repository;

import com.fnbx.hrm.dto.response.EmployeeListRow;
import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.enums.EmploymentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, UUID> {

    @Query(value = """
           SELECT new com.fnbx.hrm.dto.response.EmployeeListRow(
               s.staffId, s.employeeCode, s.nickname, s.firstName, s.lastName, s.email,
               ep.dateOfBirth, ep.hiredOn, ep.terminatedOn, ep.active)
           FROM Staff s
           JOIN EmployeeProfile ep ON ep.staffId = s.staffId
           WHERE (:anySearch = TRUE
                  OR lower(s.employeeCode) LIKE :search
                  OR lower(concat(s.firstName, ' ', s.lastName)) LIKE :search
                  OR lower(coalesce(s.nickname, '')) LIKE :search)
             AND (:anyActive = TRUE
                  OR (:active = TRUE AND ep.terminatedOn IS NULL)
                  OR (:active = FALSE AND ep.terminatedOn IS NOT NULL))
             AND (:anyBranch = TRUE OR EXISTS (
                    SELECT 1 FROM EmploymentAssignment ea
                    WHERE ea.staffId = s.staffId AND ea.defaultBranchId = :branchId AND ea.effectiveTo IS NULL))
             AND (:anyEmploymentType = TRUE OR EXISTS (
                    SELECT 1 FROM EmploymentAssignment ea2
                    WHERE ea2.staffId = s.staffId AND ea2.employmentType = :employmentType AND ea2.effectiveTo IS NULL))
           ORDER BY s.employeeCode
           """,
           countQuery = """
           SELECT count(s)
           FROM Staff s
           JOIN EmployeeProfile ep ON ep.staffId = s.staffId
           WHERE (:anySearch = TRUE
                  OR lower(s.employeeCode) LIKE :search
                  OR lower(concat(s.firstName, ' ', s.lastName)) LIKE :search
                  OR lower(coalesce(s.nickname, '')) LIKE :search)
             AND (:anyActive = TRUE
                  OR (:active = TRUE AND ep.terminatedOn IS NULL)
                  OR (:active = FALSE AND ep.terminatedOn IS NOT NULL))
             AND (:anyBranch = TRUE OR EXISTS (
                    SELECT 1 FROM EmploymentAssignment ea
                    WHERE ea.staffId = s.staffId AND ea.defaultBranchId = :branchId AND ea.effectiveTo IS NULL))
             AND (:anyEmploymentType = TRUE OR EXISTS (
                    SELECT 1 FROM EmploymentAssignment ea2
                    WHERE ea2.staffId = s.staffId AND ea2.employmentType = :employmentType AND ea2.effectiveTo IS NULL))
           """)
    Page<EmployeeListRow> searchFiltered(@Param("anySearch") boolean anySearch,
            @Param("search") String search, @Param("anyBranch") boolean anyBranch,
            @Param("branchId") UUID branchId, @Param("anyEmploymentType") boolean anyEmploymentType,
            @Param("employmentType") EmploymentType employmentType, @Param("anyActive") boolean anyActive,
            @Param("active") Boolean active, Pageable pageable);

    default Page<EmployeeListRow> search(String search, UUID branchId, EmploymentType employmentType,
            Boolean active, Pageable pageable) {
        return searchFiltered(search == null, search, branchId == null, branchId, employmentType == null,
                employmentType, active == null, active, pageable);
    }

    List<EmployeeProfile> findByTerminatedOnIsNull();

    @Query("SELECT count(p) FROM EmployeeProfile p WHERE p.terminatedOn IS NULL AND extract(month from p.dateOfBirth) = :month")
    long countBirthdaysInMonth(@Param("month") int month);

    @Query("""
           SELECT p FROM EmployeeProfile p
           WHERE p.terminatedOn IS NULL AND p.dateOfBirth IS NOT NULL AND extract(month from p.dateOfBirth) = :month
           """)
    List<EmployeeProfile> findActiveBornInMonth(@Param("month") int month);
}
