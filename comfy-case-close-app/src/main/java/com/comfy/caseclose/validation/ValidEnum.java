package com.comfy.caseclose.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a {@code String} field is a valid constant of the given enum.
 *
 * Usage:
 * <pre>
 *   {@literal @}ValidEnum(enumClass = UserRole.class)
 *   private String role;
 * </pre>
 *
 * The check is case-insensitive by default ({@link #ignoreCase} = true) so that
 * callers may send {@code "admin"} and still have it map to {@code UserRole.ADMIN}.
 * Set {@code ignoreCase = false} to require an exact-case match.
 *
 * Null values are considered valid (use {@code @NotBlank} / {@code @NotNull} separately
 * if a value is required).
 */
@Documented
@Constraint(validatedBy = EnumValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEnum {

    Class<? extends Enum<?>> enumClass();

    boolean ignoreCase() default true;

    String message() default "must be one of {enumValues}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
