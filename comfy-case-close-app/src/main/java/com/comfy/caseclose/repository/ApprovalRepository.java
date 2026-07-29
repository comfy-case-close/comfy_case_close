package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    List<Approval> findByCashCloseIdOrderByReviewedAtDesc(Long cashCloseId);
}
