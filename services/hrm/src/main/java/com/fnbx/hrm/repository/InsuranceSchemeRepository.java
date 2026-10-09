package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.InsuranceScheme;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InsuranceSchemeRepository extends JpaRepository<InsuranceScheme, UUID> {

    List<InsuranceScheme> findAllByOrderBySchemeCodeAscEffectiveFromDesc();

    @Query("""
           SELECT s FROM InsuranceScheme s
           WHERE s.schemeCode = :schemeCode
             AND s.effectiveFrom <= :asOf
             AND (s.effectiveTo IS NULL OR s.effectiveTo >= :asOf)
           """)
    Optional<InsuranceScheme> findEffective(@Param("schemeCode") String schemeCode, @Param("asOf") LocalDate asOf);

    @Query("""
           SELECT s FROM InsuranceScheme s
           WHERE s.effectiveFrom <= :asOf AND (s.effectiveTo IS NULL OR s.effectiveTo >= :asOf)
           """)
    List<InsuranceScheme> findAllEffective(@Param("asOf") LocalDate asOf);
}
