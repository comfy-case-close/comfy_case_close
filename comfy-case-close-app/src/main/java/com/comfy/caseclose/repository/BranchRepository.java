package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByBranchCode(String branchCode);

    boolean existsByBranchCode(String branchCode);

    @Query("SELECT b.id FROM Branch b WHERE b.isActive = true")
    List<Long> findActiveBranchIds();

    @Query("SELECT b.branchName FROM Branch b WHERE b.isActive = true")
    List<String> findActiveBranchNames();
}
