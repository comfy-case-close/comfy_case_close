package com.fnbx.identity.repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import com.fnbx.identity.entity.StaffProfile;
import com.fnbx.identity.utils.EmployeeCodeUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The staff directory: who works here, with no credential attached.
 *
 * <p>Separate from {@link AuthAccountRepository}, which exists to check passwords
 * and lock accounts. Provisioning an owner and approving a join request both create
 * people; neither needs to read a hash, so neither loads one.
 */
@Repository
public class StaffRepository {

    private final JdbcTemplate jdbc;

    public StaffRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<StaffProfile> MAPPER = (rs, row) -> new StaffProfile(
            rs.getObject("staff_id", UUID.class), rs.getObject("business_id", UUID.class),
            rs.getString("employee_code"), rs.getString("first_name"), rs.getString("last_name"),
            rs.getString("email"), rs.getString("phone"), rs.getString("avatar_url"),
            rs.getBoolean("is_active"), rs.getBoolean("email_verified"), rs.getString("auth_provider"),
            rs.getTimestamp("created_at").toInstant());

    /**
     * Creates a staff row and allocates its employee code. Grants no branch permission
     * - that is a separate, deliberate act through {@link StaffAccessRepository}.
     *
     * <p>{@code emailVerified} is false for a provisioned owner, who proves the address
     * by activating the account, and true for an approved join request, where the OTP
     * already proved it.
     *
     * <p>Codes are {@code lastword + firstname}, lowercased: Ho + Viet Bach becomes
     * {@code bachho}, then {@code bachho1}. The unique index arbitrates concurrent
     * creators, and {@code DO NOTHING} keeps a collision from aborting the whole
     * transaction. The conflict target names only the employee-code constraint on
     * purpose: an email collision must still propagate rather than spin this loop
     * forever.
     */
    public UUID create(UUID businessId, String email, String firstName, String lastName, String phone,
            String passwordHash, String authProvider, String avatarUrl, boolean emailVerified) {
        UUID staffId = UUID.randomUUID();
        String base = EmployeeCodeUtils.base(firstName, lastName);
        for (long suffix = 0; ; suffix++) {
            String code = suffix == 0 ? base : base + suffix;
            int inserted = jdbc.update("""
                INSERT INTO identity.staff (staff_id, business_id, employee_code, first_name, last_name, email,
                  phone, passcode_hash, passcode_algo, email_verified, auth_provider, avatar_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'bcrypt', ?, ?, ?)
                ON CONFLICT (business_id, employee_code) DO NOTHING
                """, staffId, businessId, code, firstName, lastName, email, phone, passwordHash,
                emailVerified, authProvider, avatarUrl);
            if (inserted == 1) return staffId;
        }
    }

    public Optional<StaffProfile> find(UUID staffId) {
        return unique(jdbc.query("SELECT * FROM identity.staff WHERE staff_id = ?", MAPPER, staffId));
    }

    public boolean emailExists(String email) {
        return jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM identity.staff WHERE lower(email) = ?)",
                Boolean.class, email.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Whether this business already has somebody in charge. Guards owner provisioning,
     * so that route cannot be replayed to install a second owner on a live tenant.
     *
     * <p>A revoked grant or a deactivated person does not count: a business whose only
     * ADMIN was switched off genuinely has nobody in charge, and provisioning is the
     * way back in.
     */
    public boolean hasLiveAdmin() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (
              SELECT 1 FROM identity.staff_business_permission r
              JOIN identity.staff s ON s.staff_id = r.staff_id
              WHERE r.permission_code = 'PERMISSION_GRANT' AND r.revoked_at IS NULL AND r.granted_at<=clock_timestamp() AND s.is_active)
            """, Boolean.class));
    }

    private static <T> Optional<T> unique(List<T> rows) {
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }
}
