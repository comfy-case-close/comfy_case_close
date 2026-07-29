package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.UserBranch;
import com.comfy.caseclose.entity.UserBranchId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserBranchRepository extends JpaRepository<UserBranch, UserBranchId> {

    @Query("SELECT ub.branch.id FROM UserBranch ub WHERE ub.user.id = :userId")
    List<Long> findBranchIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT ub.branch.branchName FROM UserBranch ub WHERE ub.user.id = :userId")
    List<String> findBranchNamesByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserBranch ub WHERE ub.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
