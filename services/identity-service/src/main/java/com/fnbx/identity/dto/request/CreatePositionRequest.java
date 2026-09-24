package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.NotBlank;
public record CreatePositionRequest(@NotBlank String code,@NotBlank String name) {}
