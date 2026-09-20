package com.fnbx.shared.exception;

import java.util.Objects;
import lombok.Getter;

/** Expected application failure. Only deliberate, client-safe messages belong here. */
@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        this(errorCode, Objects.requireNonNull(errorCode, "errorCode").getMessage());
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }
}
