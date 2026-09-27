package com.fnbx.identity.dto.response;

import com.fnbx.identity.enums.SignUpOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The result of signup, which now has two endings.
 *
 * <p>One shape for both, because the client cannot know in advance which applies:
 * whether an address belongs to a provisioned account is exactly the thing signup
 * must not reveal before the OTP proves ownership of it. So the caller sends the
 * same request either way and branches on {@link #outcome} - {@code session} is
 * populated for {@code SESSION_ISSUED} and null for {@code PENDING_APPROVAL}.
 *
 * <p>The HTTP status mirrors it (201/200 against 202) for clients that would rather
 * read that, but {@code outcome} is the contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignUpResponse {

    private SignUpOutcome outcome;
    private String message;
    private AuthResponse session;

    public static SignUpResponse session(AuthResponse session) {
        return SignUpResponse.builder()
                .outcome(SignUpOutcome.SESSION_ISSUED)
                .message("Account activated.")
                .session(session)
                .build();
    }

    public static SignUpResponse pending() {
        return SignUpResponse.builder()
                .outcome(SignUpOutcome.PENDING_APPROVAL)
                .message("Your request to join has been submitted and is waiting for approval.")
                .build();
    }
}
