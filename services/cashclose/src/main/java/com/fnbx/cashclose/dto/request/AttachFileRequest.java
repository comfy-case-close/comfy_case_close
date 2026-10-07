package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** References an existing file owned by files-service; its kind is the file's own. */
public record AttachFileRequest(@NotNull UUID fileId) {}
