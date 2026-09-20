package com.fnbx.identity.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.fnbx.identity.entity.AuthAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Parameterized credential queries under the caller's transaction-local RLS context.
 *
 * <p>Credentials only. Creating people, allocating employee codes and granting
 * branch roles used to live here too - {@code createBusinessOwner} went as far as
 * inserting a whole tenant - and now belong to {@link StaffRepository} and
 * {@link StaffBranchRoleRepository}. Authentication reads and updates accounts; it
 * does not bring them into existence.
 */
@Repository
public class AuthAccountRepository {
    private final JdbcTemplate jdbc;
    public AuthAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SELECT = """
        SELECT s.*, b.is_active AS business_active FROM identity.staff s
        JOIN identity.business b ON b.business_id = s.business_id
        """;
    private static final RowMapper<AuthAccount> MAPPER = (rs, row) -> new AuthAccount(
            rs.getObject("staff_id", UUID.class), rs.getObject("business_id", UUID.class),
            rs.getString("employee_code"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("email"),
            rs.getString("phone"), rs.getString("avatar_url"), rs.getString("passcode_hash"),
            rs.getString("passcode_algo"), rs.getBoolean("is_active"), rs.getBoolean("business_active"),
            rs.getBoolean("email_verified"), rs.getString("auth_provider"), rs.getTimestamp("tokens_valid_from").toInstant(),
            rs.getLong("refresh_version"));

    public Optional<AuthAccount> lockByEmail(String email) {
        return unique(jdbc.query(SELECT + " WHERE lower(s.email) = ? FOR UPDATE OF s",
                MAPPER, email.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<AuthAccount> lockById(UUID id) {
        return unique(jdbc.query(SELECT + " WHERE s.staff_id = ? FOR UPDATE OF s", MAPPER, id));
    }

    public Optional<AuthAccount> findById(UUID id) {
        return unique(jdbc.query(SELECT + " WHERE s.staff_id = ?", MAPPER, id));
    }

    public boolean emailExists(String email) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM identity.staff WHERE lower(email) = ?)", Boolean.class, email);
    }

    public void recordLogin(UUID id, Instant now) {
        jdbc.update("UPDATE identity.staff SET last_login_at = ? WHERE staff_id = ?", Timestamp.from(now), id);
    }

    public void invalidateSessions(UUID id, Instant cutoff) {
        jdbc.update("""
            UPDATE identity.staff SET tokens_valid_from = greatest(tokens_valid_from, ?),
              refresh_version = refresh_version + 1 WHERE staff_id = ?
            """, Timestamp.from(cutoff), id);
    }

    public void changePassword(UUID id, String hash, Instant cutoff) {
        jdbc.update("""
            UPDATE identity.staff SET passcode_hash = ?, passcode_algo = 'bcrypt', auth_provider = 'LOCAL',
              tokens_valid_from = greatest(tokens_valid_from, ?), refresh_version = refresh_version + 1 WHERE staff_id = ?
            """, hash, Timestamp.from(cutoff), id);
    }

    public void verifyEmail(UUID id) {
        jdbc.update("UPDATE identity.staff SET email_verified = true WHERE staff_id = ?", id);
    }

    public void claimWithGoogle(UUID id, String unusablePassword, String firstName, String lastName, String avatarUrl) {
        jdbc.update("""
            UPDATE identity.staff SET passcode_hash = ?, passcode_algo = 'bcrypt', auth_provider = 'GOOGLE',
              email_verified = true, first_name = ?, last_name = ?, avatar_url = ? WHERE staff_id = ?
            """, unusablePassword, firstName, lastName, avatarUrl, id);
    }

    public void updateProfile(UUID id, String firstName, String lastName, String phone, String avatarUrl) {
        jdbc.update("""
            UPDATE identity.staff SET first_name = coalesce(?, first_name), last_name = coalesce(?, last_name),
              phone = coalesce(?, phone),
              avatar_url = coalesce(?, avatar_url) WHERE staff_id = ?
            """, firstName, lastName, phone, avatarUrl, id);
    }

    private static Optional<AuthAccount> unique(List<AuthAccount> accounts) {
        // Fail closed if a lookup ever becomes ambiguous; never select an arbitrary account.
        return accounts.size() == 1 ? Optional.of(accounts.getFirst()) : Optional.empty();
    }
}
