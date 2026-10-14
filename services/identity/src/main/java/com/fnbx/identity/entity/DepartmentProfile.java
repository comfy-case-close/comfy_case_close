package com.fnbx.identity.entity;

import java.util.UUID;

/** Identity's own row projection for {@code identity.department}. See {@link BusinessProfile}. */
public record DepartmentProfile(UUID departmentId, UUID businessId, String departmentCode, String departmentName,
                                int displayOrder, boolean active) {}
