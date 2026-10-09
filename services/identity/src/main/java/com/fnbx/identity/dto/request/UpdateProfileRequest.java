package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
public record UpdateProfileRequest(@NotBlank(message = "First name is required") @Size(min = 1, max = 200) String firstName,
                                   @NotBlank(message = "Last name is required") @Size(min = 1, max = 200) String lastName,
                                   @Size(max = 30) String phone, @Size(max = 1024) String avatarUrl,
                                   Map<@NotNull UUID, @NotNull Set<@NotNull UUID>> branchPositions,
                                   @Size(max = 30) String nickname) {
    public UpdateProfileRequest(String firstName, String lastName, String phone, String avatarUrl) {
        this(firstName, lastName, phone, avatarUrl, null, null);
    }
    public UpdateProfileRequest(String firstName, String lastName, String phone, String avatarUrl,
                                Map<UUID, Set<UUID>> branchPositions) {
        this(firstName, lastName, phone, avatarUrl, branchPositions, null);
    }
    public UpdateProfileRequest {
        firstName = trim(firstName);
        lastName = trim(lastName);
        nickname = trim(nickname);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
