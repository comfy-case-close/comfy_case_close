package com.fnbx.shared.exception;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Common error JSON; success responses remain direct resource DTOs. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ErrorResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
    private int code;
    private String message;
    private String path;
    @Builder.Default
    private List<FieldError> fieldErrors = List.of();
    private Void result;

    public static ErrorResponse of(ErrorCode code, String path) {
        return of(code, code.getMessage(), path, List.of());
    }

    public static ErrorResponse of(ErrorCode code, String message, String path, List<FieldError> fields) {
        return ErrorResponse.builder()
                .timestamp(Instant.now()).code(code.getCode()).message(message).path(path)
                .fieldErrors(fields == null ? List.of() : List.copyOf(fields)).build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldError {
        private String field;
        private String message;
    }
}
