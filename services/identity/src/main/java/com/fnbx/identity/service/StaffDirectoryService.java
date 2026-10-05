package com.fnbx.identity.service;

import com.fnbx.identity.dto.response.AuthUserResponse;
import com.fnbx.identity.dto.response.BranchAccessResponse;
import com.fnbx.identity.dto.response.StaffPositionAccessResponse;
import com.fnbx.identity.exception.OnboardingExceptions;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.utils.PagedResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tenant directory projections protected by branch membership or viewing permission. */
@Service
@Transactional(readOnly = true)
public class StaffDirectoryService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final BranchAccessGuard guard;

    public StaffDirectoryService(JdbcTemplate jdbc, BranchAccessGuard guard) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.guard = guard;
    }

    private static final String STAFF_COLUMNS = """
        SELECT s.staff_id,s.business_id,s.employee_code,s.first_name,s.last_name,
               s.email,s.phone,s.avatar_url,s.is_active FROM identity.staff s
        """;
    private static final RowMapper<AuthUserResponse> PROFILE = (row, number) -> AuthUserResponse.builder()
            .id(row.getObject("staff_id", UUID.class))
            .businessId(row.getObject("business_id", UUID.class))
            .employeeCode(row.getString("employee_code"))
            .firstName(row.getString("first_name"))
            .lastName(row.getString("last_name"))
            .email(row.getString("email"))
            .phone(row.getString("phone"))
            .avatarUrl(row.getString("avatar_url"))
            .active(row.getBoolean("is_active"))
            .build();

    public PagedResponse<AuthUserResponse> list(UUID branchId, boolean includePermissions, int page, int size) {
        requireDirectoryAccess(branchId, includePermissions);
        String where = getWhereQuery(branchId);
        var params = new MapSqlParameterSource("business", TenantContext.current().businessId())
                .addValue("branch", branchId)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        long total = named.queryForObject("SELECT count(*) FROM identity.staff s" + where, params, Long.class);
        List<AuthUserResponse> rows = named.query(
                STAFF_COLUMNS + where + " ORDER BY s.employee_code,s.staff_id LIMIT :limit OFFSET :offset",
                params,
                PROFILE
        );
        attach(rows, branchId, includePermissions);
        int pages = (int) ((total + size - 1) / size);
        boolean last = ((long) page + 1) * size >= total;
        return new PagedResponse<>(rows, page, size, total, pages, last);
    }

    private static String getWhereQuery(UUID branchId) {
        String where = " WHERE s.business_id=:business ";
        if (branchId != null) {
            where += """
                    AND EXISTS (SELECT 1 FROM identity.staff_branch_position a
                      JOIN identity.position p ON p.position_id=a.position_id
                        AND p.business_id=a.business_id AND p.is_active
                      WHERE a.staff_id=s.staff_id AND a.business_id=s.business_id AND a.branch_id=:branch
                        AND a.revoked_at IS NULL AND a.assigned_at<=clock_timestamp())
                    """;
        }
        return where;
    }

    public AuthUserResponse get(UUID staffId, UUID branchId, boolean includePermissions) {
        requireDirectoryAccess(branchId, includePermissions);
        List<AuthUserResponse> rows = jdbc.query(
                STAFF_COLUMNS + " WHERE s.staff_id=? AND s.business_id=?",
                PROFILE,
                staffId,
                TenantContext.current().businessId()
        );
        if (rows.isEmpty()) {
            throw OnboardingExceptions.staffNotFound();
        }
        boolean self = staffId.equals(TenantContext.current().userId());
        attach(rows, branchId, includePermissions);
        AuthUserResponse result = rows.getFirst();
        if (branchId != null && !result.getBranchIds().contains(branchId)) {
            throw OnboardingExceptions.staffNotFound();
        }
        if (self && includePermissions) {
            result.setBusinessPermissions(businessPermissions(staffId));
        }
        return result;
    }

    /** Called only after AuthService establishes the account's identity (including login/refresh). */
    public AuthUserResponse enrichAuthenticatedAccount(AuthUserResponse user) {
        attach(List.of(user), null, true);
        user.setBusinessPermissions(businessPermissions(user.getId()));
        return user;
    }

    private Set<Permission> businessPermissions(UUID staffId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
            SELECT DISTINCT g.permission_code FROM identity.staff_branch_position a
            JOIN identity.staff s ON s.staff_id=a.staff_id AND s.business_id=a.business_id AND s.is_active
            JOIN identity.business b ON b.business_id=a.business_id AND b.is_active
            JOIN identity.position p ON p.position_id=a.position_id AND p.business_id=a.business_id AND p.is_active
            JOIN identity.position_permission g ON g.position_id=p.position_id AND g.business_id=p.business_id
            WHERE a.staff_id=? AND a.revoked_at IS NULL AND a.assigned_at<=clock_timestamp()
              AND g.scope='BUSINESS' AND g.revoked_at IS NULL AND g.granted_at<=clock_timestamp()
            ORDER BY g.permission_code
            """, String.class, staffId).stream().map(Permission::valueOf).toList());
    }

    private static final String ASSIGNMENTS = """
        FROM identity.staff_branch_position a
        JOIN identity.branch b ON b.branch_id=a.branch_id AND b.business_id=a.business_id AND b.is_active
        JOIN identity.position p ON p.position_id=a.position_id AND p.business_id=a.business_id AND p.is_active
        WHERE a.staff_id IN (:ids) AND a.business_id=:business
          AND a.revoked_at IS NULL AND a.assigned_at<=clock_timestamp()
          AND (CAST(:branch AS uuid) IS NULL OR a.branch_id=:branch)
        """;

    private void attach(List<AuthUserResponse> users, UUID branchId, boolean includePermissions) {
        if (users.isEmpty()) {
            return;
        }
        var params = new MapSqlParameterSource("ids", users.stream().map(AuthUserResponse::getId).toList())
                .addValue("business", TenantContext.current().businessId())
                .addValue("branch", branchId);
        Map<UUID, LinkedHashMap<UUID, BranchAccessResponse>> byStaff = new HashMap<>();
        Map<UUID, Map<UUID, StaffPositionAccessResponse>> positionsByStaff = new HashMap<>();
        named.query(
                "SELECT a.staff_id,b.branch_id,b.branch_code,b.branch_name,"
                        + "p.position_id,p.position_code,p.position_name "
                        + ASSIGNMENTS + " ORDER BY b.branch_code,p.position_code,p.position_id",
                params,
                row -> {
                    UUID staffId = row.getObject("staff_id", UUID.class);
                    UUID branch = row.getObject("branch_id", UUID.class);
                    var branches = byStaff.computeIfAbsent(staffId, ignored -> new LinkedHashMap<>());
                    var entry = branches.get(branch);
                    if (entry == null) {
                        entry = new BranchAccessResponse(
                                branch, row.getString("branch_code"), row.getString("branch_name"),
                                new ArrayList<>(), includePermissions ? new LinkedHashSet<>() : null);
                        branches.put(branch, entry);
                    }
                    UUID positionId = row.getObject("position_id", UUID.class);
                    String positionCode = row.getString("position_code");
                    String positionName = row.getString("position_name");
                    StaffPositionAccessResponse position = positionsByStaff
                            .computeIfAbsent(staffId, ignored -> new HashMap<>())
                            .computeIfAbsent(positionId, ignored -> new StaffPositionAccessResponse(
                                    positionId, positionCode, positionName,
                                    includePermissions ? new LinkedHashSet<>() : null));
                    entry.positions().add(position);
                }
        );
        if (includePermissions) {
            named.query(
                """
                SELECT DISTINCT a.staff_id,a.branch_id,p.position_id,g.permission_code
                FROM identity.staff_branch_position a
                JOIN identity.staff s ON s.staff_id=a.staff_id AND s.business_id=a.business_id AND s.is_active
                JOIN identity.business business ON business.business_id=a.business_id AND business.is_active
                JOIN identity.branch b ON b.branch_id=a.branch_id AND b.business_id=a.business_id AND b.is_active
                JOIN identity.position p ON p.position_id=a.position_id AND p.business_id=a.business_id AND p.is_active
                JOIN identity.position_permission g ON g.position_id=p.position_id AND g.business_id=p.business_id
                WHERE a.staff_id IN (:ids) AND a.business_id=:business
                  AND a.revoked_at IS NULL AND a.assigned_at<=clock_timestamp()
                  AND g.revoked_at IS NULL AND g.granted_at<=clock_timestamp()
                  AND (CAST(:branch AS uuid) IS NULL OR a.branch_id=:branch)
                ORDER BY a.staff_id,a.branch_id,p.position_id,g.permission_code
                """,
                params,
                row -> {
                    UUID staffId = row.getObject("staff_id", UUID.class);
                    UUID branchIdForGrant = row.getObject("branch_id", UUID.class);
                    UUID positionId = row.getObject("position_id", UUID.class);
                    Permission permission = Permission.valueOf(row.getString("permission_code"));
                    positionsByStaff.get(staffId).get(positionId).permissions().add(permission);
                    if (permission.scope() == Permission.Scope.BRANCH) {
                        byStaff.get(staffId).get(branchIdForGrant).permissions().add(permission);
                    }
                }
            );
        }
        for (AuthUserResponse user : users) {
            var branches = byStaff.getOrDefault(user.getId(), new LinkedHashMap<>());
            user.setBranchIds(new LinkedHashSet<>(branches.keySet()));
            user.setBranchAccess(List.copyOf(branches.values()));
        }
    }

    private void requireBranch(UUID id) {
        if (!jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM identity.branch WHERE branch_id=? AND is_active)", Boolean.class, id))
            throw OnboardingExceptions.branchNotFound();
    }

    private void requireDirectoryAccess(UUID branchId, boolean includePermissions) {
        guard.requireActiveStaff();
        if (branchId != null) {
            requireBranch(branchId);
            requireBranchAccess(branchId, includePermissions);
            return;
        }
        List<UUID> branches = jdbc.queryForList(
                "SELECT branch_id FROM identity.branch WHERE is_active ORDER BY branch_id", UUID.class);
        if (branches.isEmpty()) {
            throw new AccessDeniedException("Branch access denied");
        }
        for (UUID branch : branches) {
            requireBranchAccess(branch, includePermissions);
        }
    }

    private void requireBranchAccess(UUID branchId, boolean includePermissions) {
        if (includePermissions) {
            guard.require(branchId, Permission.PERMISSION_VIEW);
            return;
        }
        var tenant = TenantContext.current();
        boolean member = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                  SELECT 1 FROM identity.staff_branch_position a
                  JOIN identity.position p ON p.position_id=a.position_id
                    AND p.business_id=a.business_id AND p.is_active
                  WHERE a.staff_id=? AND a.business_id=? AND a.branch_id=?
                    AND a.revoked_at IS NULL AND a.assigned_at<=clock_timestamp()
                )
                """, Boolean.class, tenant.userId(), tenant.businessId(), branchId));
        if (!member) {
            throw new AccessDeniedException("Branch membership required");
        }
    }
}
