package com.fnbx.identity.service;

import com.fnbx.identity.dto.request.CreatePositionRequest;
import com.fnbx.identity.dto.response.PermissionView;
import com.fnbx.identity.dto.response.PositionView;
import com.fnbx.identity.exception.OnboardingExceptions;
import com.fnbx.identity.repository.StaffAccessRepository;
import com.fnbx.identity.repository.StaffRepository;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class AccessManagementService {
    private final BranchAccessGuard guard;
    private final StaffAccessRepository access;
    private final StaffRepository staff;
    private final JdbcTemplate jdbc;

    public AccessManagementService(
            BranchAccessGuard guard,
            StaffAccessRepository access,
            StaffRepository staff,
            JdbcTemplate jdbc
    ) {
        this.guard = guard;
        this.access = access;
        this.staff = staff;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PermissionView> dictionary() {
        guard.requireActiveStaff();
        return jdbc.query(
                "SELECT permission_code,scope,description FROM identity.permission ORDER BY scope,permission_code",
                (row, number) -> new PermissionView(row.getString(1), row.getString(2), row.getString(3))
        );
    }

    @Transactional(readOnly = true)
    public List<PositionView> positions(UUID branchId) {
        guard.requireActiveStaff();
        requireCatalogView(branchId);

        Map<UUID, Set<Permission>> grants = new HashMap<>();
        jdbc.query(
                "SELECT position_id,permission_code FROM identity.position_permission "
                        + "WHERE revoked_at IS NULL AND granted_at<=clock_timestamp() ORDER BY permission_code",
                (RowCallbackHandler) row -> grants
                        .computeIfAbsent(row.getObject(1, UUID.class), id -> new LinkedHashSet<>())
                        .add(Permission.valueOf(row.getString(2)))
        );

        return jdbc.query(
                "SELECT position_id,position_code,position_name,is_active FROM identity.position ORDER BY position_code",
                (row, number) -> new PositionView(
                        row.getObject(1, UUID.class),
                        row.getString(2),
                        row.getString(3),
                        row.getBoolean(4),
                        grants.getOrDefault(row.getObject(1, UUID.class), Set.of())
                )
        );
    }

    @Transactional(readOnly = true)
    public PositionView position(UUID id, UUID branchId) {
        return positions(branchId).stream()
                .filter(position -> position.positionId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Position not found"));
    }

    public PositionView createPosition(CreatePositionRequest request) {
        guard.requireBusiness(Permission.PERMISSION_GRANT);
        UUID id = UUID.randomUUID();
        UUID businessId = TenantContext.current().businessId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String name = request.name().trim();

        jdbc.update(
                "INSERT INTO identity.position(position_id,business_id,position_code,position_name) VALUES(?,?,?,?)",
                id,
                businessId,
                code,
                name
        );
        replacePositionPermissions(id, Set.of(
                Permission.CLOSE_READ,
                Permission.CLOSE_EDIT,
                Permission.CLOSE_SUBMIT,
                Permission.DENOMINATION_WRITE,
                Permission.MOVEMENT_ADD,
                Permission.WITHDRAWAL_RECORD
        ));
        return new PositionView(id, code, name, true);
    }

    public Set<Permission> addPositionPermissions(UUID positionId, Set<Permission> permissions) {
        return changePositionPermissions(positionId, permissions, true);
    }

    public Set<Permission> removePositionPermission(UUID positionId, Permission permission) {
        return changePositionPermissions(positionId, Set.of(permission), false);
    }

    public Set<Permission> replacePositionPermissions(UUID positionId, Set<Permission> permissions) {
        guard.requireBusiness(Permission.PERMISSION_GRANT);
        requirePosition(positionId);
        access.lockBusiness(TenantContext.current().businessId());
        jdbc.queryForObject(
                "SELECT position_id FROM identity.position WHERE position_id=? FOR UPDATE",
                UUID.class,
                positionId
        );

        for (String existing : jdbc.queryForList(
                "SELECT permission_code FROM identity.position_permission WHERE position_id=? AND revoked_at IS NULL",
                String.class,
                positionId
        )) {
            if (!permissions.contains(Permission.valueOf(existing))) {
                jdbc.update(
                        "UPDATE identity.position_permission SET revoked_at=greatest(clock_timestamp(),granted_at) "
                                + "WHERE position_id=? AND permission_code=? AND revoked_at IS NULL",
                        positionId,
                        existing
                );
            }
        }

        for (Permission permission : permissions) {
            jdbc.update(
                    """
                    INSERT INTO identity.position_permission(position_id,business_id,permission_code,scope)
                    VALUES(?,?,?,?)
                    ON CONFLICT(position_id,permission_code) WHERE revoked_at IS NULL DO NOTHING
                    """,
                    positionId,
                    TenantContext.current().businessId(),
                    permission.name(),
                    permission.scope().name()
            );
        }
        if (!staff.hasLiveAdmin()) {
            throw OnboardingExceptions.lastActiveAdmin();
        }
        return Set.copyOf(permissions);
    }

    private Set<Permission> changePositionPermissions(
            UUID positionId,
            Set<Permission> permissions,
            boolean add
    ) {
        guard.requireBusiness(Permission.PERMISSION_GRANT);
        access.lockBusiness(TenantContext.current().businessId());
        requirePosition(positionId);
        jdbc.queryForObject(
                "SELECT position_id FROM identity.position WHERE position_id=? FOR UPDATE",
                UUID.class,
                positionId
        );

        Set<Permission> next = new HashSet<>();
        for (String code : jdbc.queryForList(
                "SELECT permission_code FROM identity.position_permission WHERE position_id=? AND revoked_at IS NULL",
                String.class,
                positionId
        )) {
            next.add(Permission.valueOf(code));
        }
        if (add) {
            next.addAll(permissions);
        } else {
            next.removeAll(permissions);
        }
        return replacePositionPermissions(positionId, next);
    }

    private void requireCatalogView(UUID branchId) {
        if (branchId != null) {
            guard.require(branchId, Permission.PERMISSION_VIEW);
            return;
        }
        List<UUID> branches = jdbc.queryForList(
                "SELECT branch_id FROM identity.branch WHERE is_active ORDER BY branch_id", UUID.class);
        if (branches.isEmpty()) {
            throw new AccessDeniedException("Branch permission denied");
        }
        for (UUID branch : branches) {
            guard.require(branch, Permission.PERMISSION_VIEW);
        }
    }

    private void requirePosition(UUID id) {
        if (!access.activePosition(id)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Select an active position in this business");
        }
    }
}
