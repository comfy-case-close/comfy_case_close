package com.fnbx.identity.service;

import java.util.UUID;
import com.fnbx.identity.dto.NewBusiness;
import com.fnbx.identity.dto.NewOwner;
import com.fnbx.identity.repository.*;
import com.fnbx.identity.security.StaffPasswordEncoder;
import com.fnbx.shared.enums.UserRole;
import org.springframework.stereotype.Component;

/** Atomic provisioning primitives. Caller opens the new tenant's transaction. */
@Component
public class BusinessProvisioning {
    private final BusinessRepository businesses;
    private final BranchRepository branches;
    private final StaffRepository staff;
    private final StaffBranchRoleRepository assignments;
    private final StaffPasswordEncoder passwords;

    public BusinessProvisioning(BusinessRepository businesses, BranchRepository branches, StaffRepository staff,
            StaffBranchRoleRepository assignments, StaffPasswordEncoder passwords) {
        this.businesses = businesses; this.branches = branches; this.staff = staff;
        this.assignments = assignments; this.passwords = passwords;
    }

    /** Returns the database-generated first branch ID for the owner's ADMIN grant. */
    public UUID createBusinessWithFirstBranch(UUID businessId, NewBusiness business) {
        businesses.insert(businessId, business.businessCode(), business.businessName(),
                business.businessType(), business.currencyCode(), business.timezone());
        return branches.insert(businessId, business.branchCode(), business.branchName(),
                business.branchAddress(), null, null);
    }

    /** Only called after checking the registration's durable email-verification evidence. */
    public UUID createOwner(UUID businessId, UUID branchId, NewOwner owner, String password) {
        UUID staffId = staff.create(businessId, owner.email(), owner.firstName(), owner.lastName(),
                owner.phone(), passwords.encode(password), "LOCAL", null, true);
        assignments.assign(staffId, branchId, businessId, UserRole.ADMIN);
        return staffId;
    }
}
