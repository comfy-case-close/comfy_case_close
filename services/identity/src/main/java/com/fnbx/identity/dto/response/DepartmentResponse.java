package com.fnbx.identity.dto.response;

import java.util.UUID;

/** A department as the API exposes it. */
public record DepartmentResponse(UUID departmentId, String departmentCode, String departmentName,
                                 int displayOrder, boolean active) {}
