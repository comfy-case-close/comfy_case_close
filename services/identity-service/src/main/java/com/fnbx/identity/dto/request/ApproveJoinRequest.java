package com.fnbx.identity.dto.request;

import java.util.UUID;
import com.fnbx.shared.enums.UserRole;
import jakarta.validation.constraints.*;

/**
 * Turns a pending application into a staff account.
 *
 * <p>Branch and role are decided here rather than in a later step, so an approved
 * account is usable the moment it exists. Approving without an assignment would
 * create an account whose every login fails with a bare 403 - indistinguishable
 * from a wrong password - until somebody remembered to assign it. See
 * docs/security/onboarding.md section 3.5.
 */
public record ApproveJoinRequest(
        @NotNull UUID branchId,
        @NotNull UserRole role,
        @Size(max = 500) String note) {}
