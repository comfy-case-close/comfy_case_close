package com.fnbx.hrm.security;

import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Permission rules that {@link BranchAccessGuard} cannot express alone: alternatives and combinations. */
@Component
@RequiredArgsConstructor
public class PayrollAccess {

    private final BranchAccessGuard branchAccess;

    /** Passes when the caller holds at least one of the business-wide permissions. */
    public void requireAnyBusiness(Permission... permissions) {
        Set<Permission> held = branchAccess.effective(null);
        if (Arrays.stream(permissions).noneMatch(held::contains)) {
            throw new AccessDeniedException("Business permission denied");
        }
    }

    /** Passes only when the caller holds every one of the business-wide permissions. */
    public void requireAllBusiness(Permission... permissions) {
        Set<Permission> held = branchAccess.effective(null);
        if (!held.containsAll(Arrays.asList(permissions))) {
            throw new AccessDeniedException("Business permission denied");
        }
    }

    /** Business-wide permission first; otherwise the branch permission at {@code branchId}. */
    public void requireBusinessOrBranch(UUID branchId, Permission businessPermission, Permission branchPermission) {
        if (branchAccess.effective(null).contains(businessPermission)) {
            return;
        }
        branchAccess.require(branchId, branchPermission);
    }

    /** Same as above with several business-wide alternatives. */
    public void requireAnyBusinessOrBranch(UUID branchId, Permission branchPermission, Permission... businessPermissions) {
        Set<Permission> held = branchAccess.effective(null);
        if (Arrays.stream(businessPermissions).anyMatch(held::contains)) {
            return;
        }
        branchAccess.require(branchId, branchPermission);
    }

    /**
     * Passes with any of the business-wide permissions, or with {@code branchPermission} at some branch.
     * For reads that are not about one branch, such as listing the pay periods a branch manager
     * picks the month to look at from.
     */
    public void requireAnyBusinessOrAnyBranch(Permission branchPermission, Permission... businessPermissions) {
        Set<Permission> held = branchAccess.effective(null);
        if (Arrays.stream(businessPermissions).anyMatch(held::contains)) {
            return;
        }
        if (branchAccess.branches(branchPermission).isEmpty()) {
            throw new AccessDeniedException("Permission denied");
        }
    }
}
