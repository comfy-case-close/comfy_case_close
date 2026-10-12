package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollConfigRepository extends JpaRepository<PayrollConfig, UUID> {

    List<PayrollConfig> findAllByOrderByEffectiveFromDesc();

    @Query("""
           SELECT c FROM PayrollConfig c
           WHERE c.effectiveFrom <= :asOf
           ORDER BY c.effectiveFrom DESC
           """)
    List<PayrollConfig> findEffectiveAsOf(@Param("asOf") LocalDate asOf);

    default Optional<PayrollConfig> findCurrent(LocalDate asOf) {
        List<PayrollConfig> effective = findEffectiveAsOf(asOf);
        return effective.isEmpty() ? Optional.empty() : Optional.of(effective.get(0));
    }
}
