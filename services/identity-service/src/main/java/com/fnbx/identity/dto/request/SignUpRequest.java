package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.*;
/** Complete signup in an existing business: activate a legacy account or request staff membership. */
public record SignUpRequest(@NotBlank @Size(max = 32) String businessCode, @NotBlank @Email @Size(max = 255) String email,
                            @NotBlank @Size(max = 128) String signupToken,
                            @NotBlank @Size(min = 8, max = 72)
                            @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[!+,.;<=>?@#-]).*$",
                              message = "must contain an uppercase letter, number and special character (!+-,.;<=>?@#)") String password,
                            @NotBlank @Size(max = 200) String firstName,
                            @NotBlank @Size(max = 200) String lastName,
                            @Size(max = 30) String phone) {}
