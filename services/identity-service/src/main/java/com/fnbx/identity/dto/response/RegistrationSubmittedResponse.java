package com.fnbx.identity.dto.response;

import java.util.UUID;
import com.fnbx.identity.enums.RegistrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public acknowledgement; the application and its decision are never publicly readable. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationSubmittedResponse {

    private UUID registrationId;
    private RegistrationStatus status;
    private String message;

    public static RegistrationSubmittedResponse pending(UUID registrationId) {
        return RegistrationSubmittedResponse.builder()
                .registrationId(registrationId)
                .status(RegistrationStatus.PENDING)
                .message("Your registration has been received. We will email you once it has been reviewed.")
                .build();
    }
}
