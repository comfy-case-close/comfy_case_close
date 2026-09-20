package com.fnbx.identity.repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fnbx.identity.entity.BranchMember;
import com.fnbx.shared.enums.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may do what, at which branch.
 *
 * <p>Each row is a role version valid during [assigned_at, revoked_at).
 * Only one version per staff/branch may be live. Closed versions are immutable.
 */
@Repository
public class StaffBranchRoleRepository {

    private final JdbcTemplate jdbc;

    public StaffBranchRoleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<BranchMember> MEMBER = (rs, row) -> new BranchMember(
            rs.getObject("staff_id", UUID.class), rs.getObject("branch_id", UUID.class),
            rs.getString("employee_code"), rs.getString("first_name"), rs.getString("last_name"),
            rs.getString("email"), rs.getBoolean("is_active"), UserRole.valueOf(rs.getString("role")),
            rs.getTimestamp("assigned_at").toInstant(),
            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant());

    /**
     * The live grants a token carries. Inactive branches are joined out rather than
     * filtered in the application: deactivating a branch has to stop conferring
     * authority everywhere at once, and one join is easier to get right than a
     * condition repeated at every call site.
     */
    public Map<UUID, UserRole> rolesFor(UUID staffId) {
        Map<UUID, UserRole> roles = new LinkedHashMap<>();
        jdbc.query("""
            SELECT r.branch_id, r.role FROM identity.staff_branch_role r
            JOIN identity.branch b ON b.branch_id = r.branch_id AND b.business_id = r.business_id
            WHERE r.staff_id = ? AND r.revoked_at IS NULL AND b.is_active
            ORDER BY r.branch_id
            """, rs -> { roles.put(rs.getObject("branch_id", UUID.class), UserRole.valueOf(rs.getString("role"))); },
            staffId);
        return Map.copyOf(roles);
    }

    /** Close the previous version and insert its successor atomically; same-role PUT is a no-op. */
    @Transactional
    public void assign(UUID staffId, UUID branchId, UUID businessId, UserRole role) {
        lockStaff(staffId);
        List<String> current = jdbc.queryForList("""
            SELECT role::text FROM identity.staff_branch_role
             WHERE staff_id = ? AND branch_id = ? AND revoked_at IS NULL
            """, String.class, staffId, branchId);
        if (current.contains(role.name())) return;
        Timestamp boundary = jdbc.queryForObject("""
            SELECT greatest(clock_timestamp(), max(revoked_at), max(assigned_at))
              FROM identity.staff_branch_role WHERE staff_id = ? AND branch_id = ?
            """, Timestamp.class, staffId, branchId);
        jdbc.update("""
            UPDATE identity.staff_branch_role SET revoked_at = ?
             WHERE staff_id = ? AND branch_id = ? AND revoked_at IS NULL
            """, boundary, staffId, branchId);
        jdbc.update("""
            INSERT INTO identity.staff_branch_role (staff_id, branch_id, business_id, role, assigned_at)
            VALUES (?, ?, ?, ?::shared.user_role, ?)
            """, staffId, branchId, businessId, role.name(), boundary);
    }

    /** Use the database clock for validity boundaries, independent of the service clock. */
    @Transactional
    public boolean revoke(UUID staffId, UUID branchId) {
        lockStaff(staffId);
        return jdbc.update("""
            UPDATE identity.staff_branch_role SET revoked_at = greatest(clock_timestamp(), assigned_at)
             WHERE staff_id = ? AND branch_id = ? AND revoked_at IS NULL
            """, staffId, branchId) == 1;
    }

    // A stable parent row also serializes the first assignment, when no version exists yet.
    private void lockStaff(UUID staffId) {
        jdbc.queryForObject("SELECT staff_id FROM identity.staff WHERE staff_id = ? FOR UPDATE",
                UUID.class, staffId);
    }

    /** The roster of one branch, revoked grants included - see {@link BranchMember}. */
    public List<BranchMember> membersOf(UUID branchId, boolean includeRevoked) {
        return jdbc.query("""
            SELECT r.staff_id, r.branch_id, r.role, r.assigned_at, r.revoked_at,
                   s.employee_code, s.first_name, s.last_name, s.email, s.is_active
              FROM identity.staff_branch_role r
              JOIN identity.staff s ON s.staff_id = r.staff_id
             WHERE r.branch_id = ? AND (? OR r.revoked_at IS NULL)
             ORDER BY s.employee_code, r.staff_id, r.assigned_at DESC, r.assignment_id
            """, MEMBER, branchId, includeRevoked);
    }

    public List<BranchMember> membersOf(UUID branchId, boolean includeRevoked, int limit, long offset) {
        return jdbc.query("""
            SELECT r.staff_id, r.branch_id, r.role, r.assigned_at, r.revoked_at,
                   s.employee_code, s.first_name, s.last_name, s.email, s.is_active
              FROM identity.staff_branch_role r
              JOIN identity.staff s ON s.staff_id = r.staff_id
             WHERE r.branch_id = ? AND (? OR r.revoked_at IS NULL)
             ORDER BY s.employee_code, r.staff_id, r.assigned_at DESC, r.assignment_id
             LIMIT ? OFFSET ?
            """, MEMBER, branchId, includeRevoked, limit, offset);
    }

    public long countMembersOf(UUID branchId, boolean includeRevoked) {
        Long total = jdbc.queryForObject("""
            SELECT count(*)
              FROM identity.staff_branch_role r
              JOIN identity.staff s ON s.staff_id = r.staff_id
             WHERE r.branch_id = ? AND (? OR r.revoked_at IS NULL)
            """, Long.class, branchId, includeRevoked);
        return total == null ? 0L : total;
    }
}
