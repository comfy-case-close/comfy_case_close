package com.fnbx.cashclose.service;

import com.fnbx.integration.entity.ShiftSales;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lay doanh thu ca tu {@code integration}.
 *
 * <h2>Vi sao KHONG goi HTTP toi integration-service</h2>
 * Sach Ch.13 tr.166 noi thang ve Service-Based: giao tiep giua cac service la
 * <i>"something to definitely avoid with service-based architecture"</i>.
 *
 * <p>Chung database nghia la doc bang SQL truc tiep. {@code svc_cashclose} co
 * {@code GRANT SELECT ON integration.shift_sales} — va KHONG co INSERT/UPDATE.
 * Muon GHI vao {@code integration} thi phai goi API cua integration-service.
 *
 * <p><b>Chia service theo QUYEN GHI, khong chia theo quyen doc.</b>
 *
 * <p>Ta doc {@code revision} lon nhat: POS co the sua so, va moi lan sua ghi
 * mot ban ghi moi thay vi UPDATE (bang la append-only).
 */
@Component
public class ShiftSalesLookup {

    private final EntityManager em;

    public ShiftSalesLookup(EntityManager em) { this.em = em; }

    @Transactional(readOnly = true)
    public Optional<ShiftSales> findLatest(UUID branchId, UUID shiftTypeId, LocalDate businessDate) {
        List<ShiftSales> rows = em.createQuery("""
                SELECT s FROM ShiftSales s
                WHERE s.branchId = :branchId
                  AND s.shiftTypeId = :shiftTypeId
                  AND s.businessDate = :businessDate
                ORDER BY s.revision DESC
                """, ShiftSales.class)
                .setParameter("branchId", branchId)
                .setParameter("shiftTypeId", shiftTypeId)
                .setParameter("businessDate", businessDate)
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst();
    }
}
