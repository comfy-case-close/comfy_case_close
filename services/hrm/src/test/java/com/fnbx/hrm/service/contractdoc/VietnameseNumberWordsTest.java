package com.fnbx.hrm.service.contractdoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VietnameseNumberWordsTest {

    @Test
    void readsTheAmountsOfTheStandardSalaryTable() {
        assertThat(VietnameseNumberWords.money(new BigDecimal("5600000"))).isEqualTo("Năm triệu sáu trăm nghìn đồng");
        assertThat(VietnameseNumberWords.money(new BigDecimal("800000"))).isEqualTo("Tám trăm nghìn đồng");
        assertThat(VietnameseNumberWords.money(new BigDecimal("6400000"))).isEqualTo("Sáu triệu bốn trăm nghìn đồng");
    }

    @Test
    void usesTheSpecialWordsOfTheTensAndUnits() {
        assertThat(VietnameseNumberWords.of(15)).isEqualTo("mười lăm");
        assertThat(VietnameseNumberWords.of(21)).isEqualTo("hai mươi mốt");
        assertThat(VietnameseNumberWords.of(25)).isEqualTo("hai mươi lăm");
        assertThat(VietnameseNumberWords.of(11)).isEqualTo("mười một");
        assertThat(VietnameseNumberWords.of(5)).isEqualTo("năm");
        assertThat(VietnameseNumberWords.of(105)).isEqualTo("một trăm lẻ năm");
    }

    @Test
    void speaksAnEmptyHundredsPlaceAfterAHigherGroup() {
        assertThat(VietnameseNumberWords.of(1_015_000)).isEqualTo("một triệu không trăm mười lăm nghìn");
        assertThat(VietnameseNumberWords.of(5_605_000)).isEqualTo("năm triệu sáu trăm lẻ năm nghìn");
        assertThat(VietnameseNumberWords.of(1_000_001)).isEqualTo("một triệu không trăm lẻ một");
    }

    @Test
    void readsZeroAndLargeAmounts() {
        assertThat(VietnameseNumberWords.money(BigDecimal.ZERO)).isEqualTo("Không đồng");
        assertThat(VietnameseNumberWords.money(new BigDecimal("1000000000"))).isEqualTo("Một tỷ đồng");
        assertThat(VietnameseNumberWords.money(new BigDecimal("12500000.40"))).isEqualTo("Mười hai triệu năm trăm nghìn đồng");
    }

    @Test
    void writesACountBesideItsLetters() {
        assertThat(VietnameseNumberWords.countWithWords(12)).isEqualTo("12 (mười hai)");
        assertThat(VietnameseNumberWords.countWithWords(60)).isEqualTo("60 (sáu mươi)");
    }

    @Test
    void refusesANegativeAmount() {
        assertThatThrownBy(() -> VietnameseNumberWords.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
