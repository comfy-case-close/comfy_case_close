package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.*;
/** Google identity proves email ownership; membership still requires approval. */
public record GoogleAuthRequest(@NotBlank @Size(max = 32) String businessCode, @NotBlank @Size(max = 16384) String idToken) {}
