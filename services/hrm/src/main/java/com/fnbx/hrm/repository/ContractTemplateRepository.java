package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ContractTemplate;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, UUID> {

    List<ContractTemplate> findAllByOrderByNameAscVersionDesc();

    @Query("""
           SELECT coalesce(max(t.version), 0) FROM ContractTemplate t
           WHERE (:positionId IS NULL AND t.positionId IS NULL) OR t.positionId = :positionId
           """)
    int latestVersion(@Param("positionId") UUID positionId);

    @Query("""
           SELECT t FROM ContractTemplate t
           WHERE t.active = TRUE AND t.positionId = :positionId AND t.effectiveFrom <= :asOf
           ORDER BY t.version DESC
           """)
    List<ContractTemplate> findActiveForPosition(@Param("positionId") UUID positionId, @Param("asOf") LocalDate asOf);

    @Query("""
           SELECT t FROM ContractTemplate t
           WHERE t.active = TRUE AND t.positionId IS NULL AND t.effectiveFrom <= :asOf
           ORDER BY t.version DESC
           """)
    List<ContractTemplate> findActiveGeneral(@Param("asOf") LocalDate asOf);
}
