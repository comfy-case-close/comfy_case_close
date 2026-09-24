package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
public record AssignBranchPositionsRequest(@NotEmpty Set<@NotNull UUID> positionIds) {}
