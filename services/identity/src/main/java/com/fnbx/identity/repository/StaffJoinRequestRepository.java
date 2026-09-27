package com.fnbx.identity.repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import com.fnbx.identity.entity.JoinRequest;
import com.fnbx.identity.enums.JoinRequestStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Applications to join the current tenant.
 *
 * <p>Rows here hold a password hash until they are decided, which is why the table
 * is readable by {@code svc_identity} alone - every other service role is revoked
 * explicitly in the migration, since the schema-wide read grants would otherwise
 * have included it.
 *
 * <p>Every decision writes its timestamp with the database's {@code now()} rather
 * than the service clock. The table checks {@code decided_at >= requested_at}, and
 * {@code requested_at} was written by the database; comparing two clocks across
 * that constraint is how a perfectly valid approval ends up rejected by a CHECK.
 */
@Repository
public class StaffJoinRequestRepository {

    private final JdbcTemplate jdbc;

    public StaffJoinRequestRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SELECT = "SELECT * FROM identity.staff_join_request ";

    private static final RowMapper<JoinRequest> MAPPER = (rs, row) -> new JoinRequest(
            rs.getObject("join_request_id", UUID.class), rs.getObject("business_id", UUID.class),
            rs.getString("email"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("phone"),
            rs.getString("passcode_hash"), rs.getString("auth_provider"), rs.getString("avatar_url"),
            JoinRequestStatus.valueOf(rs.getString("status")), rs.getTimestamp("requested_at").toInstant(),
            rs.getObject("decided_by", UUID.class),
            rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(),
            rs.getString("decision_note"), rs.getObject("created_staff_id", UUID.class));

    /**
     * Files an application, replacing any request from the same address that is still
     * pending.
     *
     * <p>Replacing rather than refusing matters: the applicant has just proved
     * ownership of the address by OTP, and a stale request they filed weeks ago -
     * possibly with a password they have since forgotten - must not lock them out of
     * applying again. The delete and the insert are one transaction; the partial
     * unique index still arbitrates two genuinely simultaneous submissions, and the
     * loser gets a conflict rather than a duplicate.
     *
     * @param passwordHash null for a Google-originated request, which has no password
     */
    public UUID submit(UUID businessId, String email, String firstName, String lastName, String phone,
            String passwordHash, String authProvider, String avatarUrl) {
        UUID joinRequestId = UUID.randomUUID();
        jdbc.update("""
            DELETE FROM identity.staff_join_request
             WHERE lower(email) = ? AND status = 'PENDING'
            """, email.trim().toLowerCase(Locale.ROOT));
        jdbc.update("""
            INSERT INTO identity.staff_join_request (join_request_id, business_id, email, first_name, last_name,
              phone, passcode_hash, auth_provider, avatar_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, joinRequestId, businessId, email, firstName, lastName, phone, passwordHash, authProvider, avatarUrl);
        return joinRequestId;
    }

    /** Locks the row so two reviewers cannot approve the same application twice. */
    public Optional<JoinRequest> lockById(UUID joinRequestId) {
        return unique(jdbc.query(SELECT + " WHERE join_request_id = ? FOR UPDATE", MAPPER, joinRequestId));
    }

    public Optional<JoinRequest> find(UUID joinRequestId) {
        return unique(jdbc.query(SELECT + " WHERE join_request_id = ?", MAPPER, joinRequestId));
    }

    /** Oldest first: this is a queue, and the person who has waited longest is next. */
    public List<JoinRequest> page(JoinRequestStatus status, int limit, long offset) {
        return jdbc.query(SELECT + """
             WHERE (?::shared.join_request_status IS NULL OR status = ?::shared.join_request_status)
             ORDER BY requested_at, join_request_id
             LIMIT ? OFFSET ?
            """, MAPPER, name(status), name(status), limit, offset);
    }

    public long count(JoinRequestStatus status) {
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM identity.staff_join_request
             WHERE (?::shared.join_request_status IS NULL OR status = ?::shared.join_request_status)
            """, Long.class, name(status), name(status));
        return total == null ? 0L : total;
    }

    /**
     * The hash is cleared in the same statement that records the decision. The
     * credential existed only to be copied into the new staff row; leaving it behind
     * would keep a password hash alive in a table nobody looks at again.
     *
     * @return false when the request was no longer pending, so a double approval
     *         cannot create a second account
     */
    public boolean approve(UUID joinRequestId, UUID decidedBy, UUID createdStaffId, String note) {
        return jdbc.update("""
            UPDATE identity.staff_join_request
               SET status = 'APPROVED', decided_by = ?, decided_at = now(), decision_note = ?,
                   created_staff_id = ?, passcode_hash = NULL
             WHERE join_request_id = ? AND status = 'PENDING'
            """, decidedBy, note, createdStaffId, joinRequestId) == 1;
    }

    /** @return false when the request was no longer pending */
    public boolean reject(UUID joinRequestId, UUID decidedBy, String reason) {
        return jdbc.update("""
            UPDATE identity.staff_join_request
               SET status = 'REJECTED', decided_by = ?, decided_at = now(), decision_note = ?,
                   passcode_hash = NULL
             WHERE join_request_id = ? AND status = 'PENDING'
            """, decidedBy, reason, joinRequestId) == 1;
    }

    private static String name(JoinRequestStatus status) { return status == null ? null : status.name(); }

    private static <T> Optional<T> unique(List<T> rows) {
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }
}
