package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.utils.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmployeeCodeIgnoreCase(String employeeCode);

    Optional<User> findByEmailIgnoreCase(String email);

    @Transactional(readOnly = true)
    @Query("""
        SELECT DISTINCT TRIM(ub.user.email) FROM UserBranch ub
        WHERE ub.branch.id = :branchId
          AND ub.user.role IN :roles
          AND ub.user.isActive = true
          AND ub.user.email IS NOT NULL
          AND TRIM(ub.user.email) <> ''
        """)
    List<String> findActiveEmailsByBranchIdAndRoles(
            @Param("branchId") Long branchId, @Param("roles") List<UserRole> roles);
}
