package com.fnbx.identity.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fnbx.identity.entity.BranchProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Branches of the current tenant.
 *
 * <p>No query here filters on {@code business_id}. RLS already does, and repeating
 * it would suggest isolation is the application's job - see the note on
 * {@code CashCloseRepository}. {@code branch_code} uniqueness, by contrast, is
 * scoped to the business, so it IS visible from inside the tenant and can be
 * checked before the insert as well as by the constraint.
 */
@Repository
public class BranchRepository {

    private final JdbcTemplate jdbc;

    public BranchRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<BranchProfile> MAPPER = (rs, row) -> new BranchProfile(
            rs.getObject("branch_id", UUID.class), rs.getObject("business_id", UUID.class),
            rs.getString("branch_code"), rs.getString("branch_name"), rs.getString("address"),
            rs.getBigDecimal("target_cash_remaining"), rs.getBigDecimal("cash_remaining_tolerance"),
            rs.getBoolean("is_active"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    public UUID insert(UUID businessId, String branchCode, String branchName, String address,
            BigDecimal targetCashRemaining, BigDecimal cashRemainingTolerance) {
        return jdbc.queryForObject("""
            INSERT INTO identity.branch (business_id, branch_code, branch_name, address,
              target_cash_remaining, cash_remaining_tolerance)
            VALUES (?, ?, ?, ?, coalesce(?::numeric, 0), coalesce(?::numeric, 0))
            RETURNING branch_id
            """, UUID.class, businessId, branchCode, branchName, address,
            targetCashRemaining, cashRemainingTolerance);
    }

    public Optional<BranchProfile> find(UUID branchId) {
        return unique(jdbc.query("SELECT * FROM identity.branch WHERE branch_id = ?", MAPPER, branchId));
    }

    public List<BranchProfile> page(boolean includeInactive, int limit, long offset) {
        return jdbc.query("""
            SELECT * FROM identity.branch
             WHERE (? OR is_active)
             ORDER BY branch_code
             LIMIT ? OFFSET ?
            """, MAPPER, includeInactive, limit, offset);
    }

    public long count(boolean includeInactive) {
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM identity.branch
             WHERE (? OR is_active)
            """, Long.class, includeInactive);
        return total == null ? 0L : total;
    }

    /**
     * The branch an owner is provisioned into. Registration creates exactly one, so
     * "first" is unambiguous at the only moment this is called; the tie-break on
     * {@code branch_id} keeps it deterministic if a business ever reaches this path
     * with several.
     */
    public Optional<BranchProfile> firstActive() {
        return unique(jdbc.query(
                "SELECT * FROM identity.branch WHERE is_active ORDER BY created_at, branch_id LIMIT 1", MAPPER));
    }

    public boolean codeExists(String branchCode) {
        return jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM identity.branch WHERE branch_code = ?)", Boolean.class, branchCode);
    }

    public int countActive() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM identity.branch WHERE is_active", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * A null argument leaves its column alone - which also means an address cannot be
     * cleared through this route, the same limitation profile updates already carry.
     * {@code branch_code} is absent: it is part of the generated cash-close code, so
     * renaming it would make older closes unreadable.
     */
    public void update(UUID branchId, String branchName, String address,
            BigDecimal targetCashRemaining, BigDecimal cashRemainingTolerance) {
        jdbc.update("""
            UPDATE identity.branch
               SET branch_name              = coalesce(?, branch_name),
                   address                  = coalesce(?, address),
                   target_cash_remaining    = coalesce(?::numeric, target_cash_remaining),
                   cash_remaining_tolerance = coalesce(?::numeric, cash_remaining_tolerance)
             WHERE branch_id = ?
            """, branchName, address, targetCashRemaining, cashRemainingTolerance, branchId);
    }

    /**
     * Soft delete, for the same reason a business is soft-deleted: every cash close
     * ever filed points here. Live grants at the branch are left untouched and simply
     * stop counting - {@code AuthAccountRepository.branchRoles} joins on
     * {@code b.is_active} - so reactivating a branch restores its roster intact.
     *
     * @return false when the branch was already inactive or does not exist
     */
    public boolean deactivate(UUID branchId) {
        return jdbc.update("UPDATE identity.branch SET is_active = false WHERE branch_id = ? AND is_active",
                branchId) == 1;
    }

    private static <T> Optional<T> unique(List<T> rows) {
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }
}
