package com.fnbx.identity.dto.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;
public record ApproveJoinRequest(@NotNull UUID branchId,@NotEmpty Set<@NotNull UUID> positionIds,String note) {}
