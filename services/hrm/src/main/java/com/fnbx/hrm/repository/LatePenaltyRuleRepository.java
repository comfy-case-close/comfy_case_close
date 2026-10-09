package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.LatePenaltyRule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LatePenaltyRuleRepository extends JpaRepository<LatePenaltyRule, UUID> {

    List<LatePenaltyRule> findAllByOrderByEffectiveFromDesc();

    @Query("""
           SELECT r FROM LatePenaltyRule r
           WHERE r.ruleCode = :ruleCode
             AND r.effectiveFrom <= :asOf
             AND (r.effectiveTo IS NULL OR r.effectiveTo >= :asOf)
           """)
    Optional<LatePenaltyRule> findEffective(@Param("ruleCode") String ruleCode, @Param("asOf") LocalDate asOf);

    @Query("""
           SELECT r FROM LatePenaltyRule r
           WHERE r.effectiveFrom <= :asOf AND (r.effectiveTo IS NULL OR r.effectiveTo >= :asOf)
           """)
    List<LatePenaltyRule> findAllEffective(@Param("asOf") LocalDate asOf);
}
