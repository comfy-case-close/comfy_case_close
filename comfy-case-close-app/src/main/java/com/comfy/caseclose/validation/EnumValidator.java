package com.comfy.caseclose.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Constraint validator for {@link ValidEnum}.
 *
 * Accepts null (null-safety is the responsibility of {@code @NotNull} / {@code @NotBlank}).
 * Rejects blank strings and any value not matching an enum constant.
 */
public class EnumValidator implements ConstraintValidator<ValidEnum, String> {

    private Set<String> validValues;
    private boolean ignoreCase;
    private String enumValuesMessage;

    @Override
    public void initialize(ValidEnum annotation) {
        ignoreCase = annotation.ignoreCase();
        Enum<?>[] constants = annotation.enumClass().getEnumConstants();
        validValues = Arrays.stream(constants)
                .map(e -> ignoreCase ? e.name().toUpperCase() : e.name())
                .collect(Collectors.toSet());
        enumValuesMessage = Arrays.stream(constants)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null handling belongs to @NotNull / @NotBlank
        }
        if (value.isBlank()) {
            return false;
        }
        String candidate = ignoreCase ? value.strip().toUpperCase() : value.strip();
        boolean valid = validValues.contains(candidate);
        if (!valid) {
            // Replace placeholder with actual enum values in the violation message.
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "must be one of: " + enumValuesMessage
            ).addConstraintViolation();
        }
        return valid;
    }
}
