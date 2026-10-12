package com.fnbx.platform.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** The stored row that supplies one effective configuration value. */
public record ConfigEntryResponse(String configKey, Object configValue, String scope,
                                  String description, UUID updatedBy, OffsetDateTime updatedAt) {}
