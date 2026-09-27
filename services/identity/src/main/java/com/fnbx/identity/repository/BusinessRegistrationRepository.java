package com.fnbx.identity.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fnbx.identity.entity.BusinessRegistration;
import com.fnbx.identity.enums.RegistrationStatus;
import com.fnbx.shared.enums.BusinessType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Applications for a tenant.
 *
 * <p>The one table here with no {@code business_id} and therefore no RLS - a row
 * belongs to no business, because the business is what is being asked for. Nothing
 * filters these queries automatically, so every route that reaches them is behind
 * the platform key, and the grant in the migration keeps every other service role
 * out entirely.
 *
 * <p>Decisions stamp their time with the database's {@code now()} rather than the
 * service clock, for the same reason join requests do: the table checks
 * {@code decided_at >= submitted_at} and the database wrote {@code submitted_at}.
 */
@Repository
public class BusinessRegistrationRepository {

    private final JdbcTemplate jdbc;

    public BusinessRegistrationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SELECT = "SELECT * FROM identity.business_registration ";

    private static final RowMapper<BusinessRegistration> MAPPER = (rs, row) -> new BusinessRegistration(
            rs.getObject("registration_id", UUID.class),
            rs.getString("business_code"), rs.getString("business_name"),
            BusinessType.valueOf(rs.getString("business_type")),
            rs.getString("currency_code"), rs.getString("timezone"),
            rs.getString("branch_code"), rs.getString("branch_name"), rs.getString("branch_address"),
            rs.getString("owner_email"), rs.getString("owner_first_name"), rs.getString("owner_last_name"),
            rs.getString("owner_phone"),
            RegistrationStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("submitted_at").toInstant(),
            rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(),
            rs.getString("decision_note"),
            rs.getObject("created_business_id", UUID.class), rs.getObject("created_staff_id", UUID.class),
            rs.getTimestamp("notified_at") == null ? null : rs.getTimestamp("notified_at").toInstant(),
            rs.getTimestamp("owner_email_verified_at") == null ? null : rs.getTimestamp("owner_email_verified_at").toInstant());

    /** Files an OTP-verified application; an existing pending address is never overwritten. */
    public UUID submit(String businessCode, String businessName, BusinessType businessType, String currencyCode,
            String timezone, String branchCode, String branchName, String branchAddress,
            String ownerEmail, String ownerFirstName, String ownerLastName, String ownerPhone) {
        UUID registrationId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO identity.business_registration (registration_id, business_code, business_name, business_type,
              currency_code, timezone, branch_code, branch_name, branch_address,
              owner_email, owner_first_name, owner_last_name, owner_phone, owner_email_verified_at)
            VALUES (?, ?, ?, ?::shared.business_type, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """, registrationId, businessCode, businessName, businessType.name(), currencyCode, timezone,
            branchCode, branchName, branchAddress, ownerEmail, ownerFirstName, ownerLastName, ownerPhone);
        return registrationId;
    }

    public void updateCodes(UUID registrationId, String businessCode, String branchCode) {
        jdbc.update("UPDATE identity.business_registration SET business_code = ?, branch_code = ? WHERE registration_id = ?",
                businessCode, branchCode, registrationId);
    }

    /** Locks the row so two administrators cannot approve the same application twice. */
    public Optional<BusinessRegistration> lockById(UUID registrationId) {
        return unique(jdbc.query(SELECT + " WHERE registration_id = ? FOR UPDATE", MAPPER, registrationId));
    }

    public Optional<BusinessRegistration> find(UUID registrationId) {
        return unique(jdbc.query(SELECT + " WHERE registration_id = ?", MAPPER, registrationId));
    }

    /** Oldest first: this is a queue, and the applicant who has waited longest is next. */
    public List<BusinessRegistration> page(RegistrationStatus status, int limit, long offset) {
        return jdbc.query(SELECT + """
             WHERE (?::shared.registration_status IS NULL OR status = ?::shared.registration_status)
             ORDER BY submitted_at, registration_id
             LIMIT ? OFFSET ?
            """, MAPPER, name(status), name(status), limit, offset);
    }

    public long count(RegistrationStatus status) {
        return jdbc.queryForObject("""
            SELECT count(*) FROM identity.business_registration
             WHERE (?::shared.registration_status IS NULL OR status = ?::shared.registration_status)
            """, Long.class, name(status), name(status));
    }

    /** @return false when the registration was no longer pending, so a double approval cannot create two tenants */
    public boolean approve(UUID registrationId, UUID businessId, UUID staffId, String note) {
        return jdbc.update("""
            UPDATE identity.business_registration
               SET status = 'APPROVED', decided_at = now(), decision_note = ?,
                   created_business_id = ?, created_staff_id = ?
             WHERE registration_id = ? AND status = 'PENDING'
            """, note, businessId, staffId, registrationId) == 1;
    }

    /** @return false when the registration was no longer pending */
    public boolean reject(UUID registrationId, String reason) {
        return jdbc.update("""
            UPDATE identity.business_registration
               SET status = 'REJECTED', decided_at = now(), decision_note = ?
             WHERE registration_id = ? AND status = 'PENDING'
            """, reason, registrationId) == 1;
    }

    /**
     * Records that the decision letter reached the applicant. Written by the mailer
     * after a successful send, so a decided row still showing null is a visible "this
     * person was never told" rather than a silent one.
     */
    public void markNotified(UUID registrationId, Instant sentAt) {
        jdbc.update("UPDATE identity.business_registration SET notified_at = ? WHERE registration_id = ?",
                Timestamp.from(sentAt), registrationId);
    }

    private static String name(RegistrationStatus status) { return status == null ? null : status.name(); }

    private static <T> Optional<T> unique(List<T> rows) {
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }
}
