package com.comfy.caseclose.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CashDenominationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "12", "999999"})
    @DisplayName("Whole, non-negative quantities are valid")
    void validQuantities(String quantity) {
        assertThat(validator.validate(row(10_000L, quantity))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "2.5", "0.5", "-1", "-2", "-0.5"})
    @DisplayName("Fractional and negative quantities are rejected")
    void invalidQuantities(String quantity) {
        assertThat(validator.validate(row(10_000L, quantity))).isNotEmpty();
    }

    @Test
    @DisplayName("A missing quantity or a non-positive note value is rejected")
    void missingOrNonPositive() {
        CashDenominationRequest missing = new CashDenominationRequest();
        missing.setDenominationValue(10_000L);
        assertThat(validator.validate(missing)).isNotEmpty();

        assertThat(validator.validate(row(0L, "1"))).isNotEmpty();
        assertThat(validator.validate(row(-500L, "1"))).isNotEmpty();
    }

    private CashDenominationRequest row(long value, String quantity) {
        CashDenominationRequest request = new CashDenominationRequest();
        request.setDenominationValue(value);
        request.setQuantity(new BigDecimal(quantity));
        return request;
    }
}
