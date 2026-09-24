package com.fnbx.cashclose.dto.request;

import com.fnbx.shared.enums.FileKind;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** References an existing file owned by files-service; no file bytes in cashclose. */
public record AttachFileRequest(@NotNull UUID fileId, @NotNull FileKind fileKind) {}
