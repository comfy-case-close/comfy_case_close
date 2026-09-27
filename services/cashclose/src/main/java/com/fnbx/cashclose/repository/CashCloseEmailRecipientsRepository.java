package com.fnbx.cashclose.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read under the submitting transaction's tenant RLS, before any asynchronous handoff. */
@Repository
public class CashCloseEmailRecipientsRepository {
    private final JdbcTemplate jdbc;
    public CashCloseEmailRecipientsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<String> managerEmails(UUID businessId, UUID branchId) {
        return jdbc.queryForList("""
                SELECT DISTINCT trim(s.email)
                FROM identity.staff_branch_position a
                JOIN identity.staff s ON s.staff_id = a.staff_id AND s.business_id = a.business_id
                JOIN identity.staff_position p ON p.position_id = a.position_id AND p.business_id = a.business_id
                JOIN identity.branch b ON b.branch_id = a.branch_id AND b.business_id = a.business_id
                WHERE a.business_id = ? AND a.branch_id = ?
                  AND a.revoked_at IS NULL AND a.assigned_at <= clock_timestamp()
                  AND s.is_active AND p.is_active AND b.is_active
                  AND p.position_code = 'STORE_MANAGER'
                  AND s.email IS NOT NULL AND trim(s.email) <> ''
                """, String.class, businessId, branchId);
    }

    public Submitter submitter(UUID businessId, UUID submitterId) {
        return jdbc.queryForObject("""
                SELECT concat_ws(' ', first_name, last_name), email
                FROM identity.staff
                WHERE business_id = ? AND staff_id = ?
                """, (r, n) -> new Submitter(r.getString(1), r.getString(2)), businessId, submitterId);
    }

    public BranchShift branchShift(UUID businessId, UUID branchId, UUID shiftTypeId) {
        return jdbc.queryForObject("""
                SELECT b.branch_code, t.shift_code
                FROM identity.branch b
                JOIN identity.shift_type t ON t.business_id = b.business_id
                    AND t.shift_type_id = ?
                WHERE b.business_id = ? AND b.branch_id = ?
                """, (r, n) -> new BranchShift(r.getString(1), r.getString(2)),
                shiftTypeId, businessId, branchId);
    }

    public record Submitter(String name, String email) {}
    public record BranchShift(String branchCode, String shiftCode) {}
}
