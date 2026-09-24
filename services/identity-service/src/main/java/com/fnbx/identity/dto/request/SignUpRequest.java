package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.*;
/** Complete signup in an existing business: activate a legacy account or request staff membership. */
public record SignUpRequest(@NotBlank @Size(max = 32) String businessCode, @NotBlank @Email @Size(max = 255) String email,
                            @NotBlank @Size(max = 128) String signupToken,
                            @NotBlank @Size(min = 8, max = 72)
                            @NotBlank(message = "Password is required") @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[!+,.;<=>?@#-]).*$",
                              message = "must contain an uppercase letter, number and special character (!+-,.;<=>?@#)") String password,
                            @NotBlank(message = "First name is required") @Size(min = 1, max = 200) String firstName,
                            @NotBlank(message = "Last name is required") @Size(min = 1, max = 200) String lastName,
                            @Size(max = 30) String phone) {
    public SignUpRequest {
        firstName = trim(firstName);
        lastName = trim(lastName);
        password = trim(password);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
