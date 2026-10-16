package com.fnbx.platform.repository;

import com.fnbx.platform.entity.MovementKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MovementKindRepository extends JpaRepository<MovementKind, Long> {

    /** All versions visible to a tenant, including shared global definitions. */
    @Query("""
            SELECT k FROM MovementKind k
            WHERE k.businessId = :businessId OR k.businessId IS NULL
            ORDER BY k.kindCode, k.validFrom DESC, k.kindSk
            """)
    Page<MovementKind> findAllVisible(@Param("businessId") UUID businessId, Pageable pageable);
}
