package com.fnbx.cashclose.repository;

import com.fnbx.platform.entity.MovementKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Movement kind catalogue. Read only from cashclose-service: the table lives in
 * the {@code platform} schema and the {@code svc_cashclose} role has SELECT there
 * and nothing more.
 *
 * <p>The catalogue is SCD-2, so one {@code kindCode} has several versions over
 * time. Always resolve through {@link #resolveAt} using the close's BUSINESS
 * DATE, never {@code now()} - a 15 Jun close submitted late on 17 Jun still
 * follows the 15 Jun rules.
 */
public interface MovementKindRepository extends JpaRepository<MovementKind, Long> {

    /**
     * The version in force for a kind on a given business date. Tenant-specific
     * entries win over global defaults ({@code businessId IS NULL}).
     */
    @Query("""
           SELECT k FROM MovementKind k
            WHERE k.kindCode = :kindCode
              AND (k.businessId = :businessId OR k.businessId IS NULL)
              AND k.validFrom <= :on
              AND (k.validTo IS NULL OR k.validTo > :on)
            ORDER BY CASE WHEN k.businessId IS NULL THEN 1 ELSE 0 END
           """)
    List<MovementKind> findEffective(@Param("kindCode") String kindCode,
                                     @Param("businessId") UUID businessId,
                                     @Param("on") LocalDate on);

    default Optional<MovementKind> resolveAt(String kindCode, UUID businessId, LocalDate on) {
        return findEffective(kindCode, businessId, on).stream().findFirst();
    }

    /** Options for the entry dropdown, valid on a given business date. */
    @Query("""
           SELECT k FROM MovementKind k
            WHERE (k.businessId = :businessId OR k.businessId IS NULL)
              AND k.validFrom <= :on
              AND (k.validTo IS NULL OR k.validTo > :on)
            ORDER BY k.kindCode
           """)
    List<MovementKind> findAllEffective(@Param("businessId") UUID businessId,
                                        @Param("on") LocalDate on);
}
