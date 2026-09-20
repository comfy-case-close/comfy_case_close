package com.fnbx.identity.dto.response;

import java.util.Map;
import java.util.UUID;
import com.fnbx.shared.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A staff member as administration endpoints expose them - the result of
 * provisioning an owner or approving a join request.
 *
 * <p>Close to {@link AuthUserResponse} but not the same contract: that one answers
 * "who am I", this one answers "who did I just create". {@code emailVerified} is
 * here and not there, because a provisioning administrator needs to see that the
 * owner has not activated their account yet, whereas a signed-in user necessarily
 * has.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffResponse {

    private UUID id;
    private UUID businessId;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String avatarUrl;
    private boolean active;
    private boolean emailVerified;
    private Map<UUID, UserRole> branchRoles;
}
