package com.fnbx.hrm.service.scheduling;

import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Branch permissions of the scheduling flow: the store manager arranges, the general manager approves and may do everything. */
@Component
@RequiredArgsConstructor
public class ScheduleAccess {

    private final BranchAccessGuard branchAccess;

    public void requireRead(UUID branchId) {
        requireAny(branchId, Permission.SHIFT_SCHEDULE_READ, Permission.SHIFT_SCHEDULE_EDIT, Permission.SHIFT_SCHEDULE_APPROVE);
    }

    public void requireEdit(UUID branchId) {
        requireAny(branchId, Permission.SHIFT_SCHEDULE_EDIT, Permission.SHIFT_SCHEDULE_APPROVE);
    }

    public void requireApprove(UUID branchId) {
        requireAny(branchId, Permission.SHIFT_SCHEDULE_APPROVE);
    }

    public boolean canApprove(UUID branchId) {
        return branchAccess.effective(branchId).contains(Permission.SHIFT_SCHEDULE_APPROVE);
    }

    public boolean canSeeCost(UUID branchId) {
        return branchAccess.effective(branchId).contains(Permission.LABOR_COST_READ);
    }

    public Set<UUID> readableBranches() {
        Set<UUID> branches = new HashSet<>(branchAccess.branches(Permission.SHIFT_SCHEDULE_READ));
        branches.addAll(branchAccess.branches(Permission.SHIFT_SCHEDULE_EDIT));
        branches.addAll(branchAccess.branches(Permission.SHIFT_SCHEDULE_APPROVE));
        return branches;
    }

    private void requireAny(UUID branchId, Permission... permissions) {
        Set<Permission> held = branchAccess.effective(branchId);
        for (Permission permission : permissions) {
            if (held.contains(permission)) {
                return;
            }
        }
        throw new AccessDeniedException("Branch permission denied");
    }
}
