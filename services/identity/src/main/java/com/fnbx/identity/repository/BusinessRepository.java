package com.fnbx.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fnbx.identity.entity.BusinessDirectoryEntry;
import com.fnbx.identity.entity.BusinessProfile;
import com.fnbx.shared.enums.BusinessType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The tenant table itself.
 *
 * <h2>Why there is no "is this code taken" query before the insert</h2>
 * {@code business_code} is unique across every tenant, but RLS scopes this table to
 * one. A pre-check therefore cannot see another business's code and would report
 * "free" right before the insert fails. The unique index is the only thing that can
 * answer, so the insert asks it and the service translates the resulting
 * {@code DuplicateKeyException}. That is also race-free, which a pre-check would not
 * be.
 */
@Repository
public class BusinessRepository {

    private final JdbcTemplate jdbc;

    public BusinessRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<BusinessProfile> MAPPER = (rs, row) -> new BusinessProfile(
            rs.getObject("business_id", UUID.class), rs.getString("business_code"), rs.getString("business_name"),
            BusinessType.valueOf(rs.getString("business_type")), rs.getString("currency_code"), rs.getString("timezone"),
            rs.getBoolean("is_active"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    /** Runs in the NEW business's own tenant context, which is what satisfies the RLS WITH CHECK. */
    public void insert(UUID businessId, String businessCode, String businessName, BusinessType type,
            String currencyCode, String timezone) {
        jdbc.update("""
            INSERT INTO identity.business (business_id, business_code, business_name, business_type,
              currency_code, timezone)
            VALUES (?, ?, ?, ?::shared.business_type, ?, ?)
            """, businessId, businessCode, businessName, type.name(), currencyCode, timezone);
    }

    public Optional<BusinessProfile> find(UUID businessId) {
        return unique(jdbc.query("SELECT * FROM identity.business WHERE business_id = ?", MAPPER, businessId));
    }

    /** A deactivated tenant is indistinguishable from a missing one to everything outside. */
    public Optional<BusinessProfile> findActive(UUID businessId) {
        return find(businessId).filter(BusinessProfile::active);
    }

    /**
     * The one cross-tenant read in the service, delegated to
     * {@code shared.fn_find_business_by_code} - a SECURITY DEFINER function that
     * returns two columns for an exact, active code and nothing else. See
     * db/changelog/002-identity/008-business-lookup.sql for why it has to exist.
     */
    public Optional<BusinessDirectoryEntry> lookupByCode(String businessCode) {
        return unique(jdbc.query("SELECT business_id, business_name FROM shared.fn_find_business_by_code(?)",
                (rs, row) -> new BusinessDirectoryEntry(rs.getObject("business_id", UUID.class),
                        rs.getString("business_name")),
                businessCode));
    }

    /**
     * A null argument leaves its column alone. {@code business_code} is absent
     * deliberately: it is the tenant's public handle and does not change.
     */
    public void update(UUID businessId, String businessName, BusinessType type, String currencyCode, String timezone) {
        jdbc.update("""
            UPDATE identity.business
               SET business_name = coalesce(?, business_name),
                   business_type = coalesce(?::shared.business_type, business_type),
                   currency_code = coalesce(?, currency_code),
                   timezone      = coalesce(?, timezone)
             WHERE business_id = ?
            """, businessName, type == null ? null : type.name(), currencyCode, timezone, businessId);
    }

    /**
     * Soft delete. A hard DELETE is not on offer: branches, staff, roles and every
     * cash close ever filed hold foreign keys onto this row, and {@code is_active} is
     * already what {@code requireEnabled} checks on every login and refresh.
     *
     * @return false when the business was already inactive or does not exist
     */
    public boolean deactivate(UUID businessId) {
        return jdbc.update("UPDATE identity.business SET is_active = false WHERE business_id = ? AND is_active",
                businessId) == 1;
    }

    private static <T> Optional<T> unique(List<T> rows) {
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }
}
