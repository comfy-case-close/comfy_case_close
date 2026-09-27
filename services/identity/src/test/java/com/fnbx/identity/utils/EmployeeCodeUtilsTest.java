package com.fnbx.identity.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class EmployeeCodeUtilsTest {
    @ParameterizedTest
    @CsvSource(value = {
        "Ho|Viet Bach|bachho",
        "HO|VIET BACH|bachho",
        " Ho | Viet   Bach |bachho",
        "Ho Nguyen|Viet Bach|bachhonguyen",
        "Hồ|Việt Bách|báchhồ",
        "Ho|''|ho"
    }, delimiter = '|', ignoreLeadingAndTrailingWhitespace = false)
    void generatesCodeFromFinalLastNameWordAndFirstName(String firstName, String lastName, String expected) {
        assertThat(EmployeeCodeUtils.base(firstName, lastName)).isEqualTo(expected);
    }
}
