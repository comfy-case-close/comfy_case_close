package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record UpdateProfileRequest(@NotBlank(message = "First name is required") @Size(min = 1, max = 200) String firstName,
                                   @NotBlank(message = "Last name is required") @Size(min = 1, max = 200) String lastName,
                                   @Size(max = 30) String phone, @Size(max = 1024) String avatarUrl) {
    public UpdateProfileRequest {
        firstName = trim(firstName);
        lastName = trim(lastName);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
