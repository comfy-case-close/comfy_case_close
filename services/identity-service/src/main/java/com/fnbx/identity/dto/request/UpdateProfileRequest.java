package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.Size;
public record UpdateProfileRequest(@Size(min = 1, max = 200) String firstName,
                                   @Size(min = 1, max = 200) String lastName,
                                   @Size(max = 30) String phone, @Size(max = 1024) String avatarUrl) {}
