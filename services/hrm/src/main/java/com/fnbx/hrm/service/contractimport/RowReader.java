package com.fnbx.hrm.service.contractimport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/** Typed access to one sheet row that records a problem instead of throwing when a cell cannot be read. */
final class RowReader {

    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d/M/uuuu");

    private final ImportSheetRow row;
    private final ImportEnumParser enums;
    private final List<ImportIssue> issues;

    RowReader(ImportSheetRow row, ImportEnumParser enums, List<ImportIssue> issues) {
        this.row = row;
        this.enums = enums;
        this.issues = issues;
    }

    Optional<String> text(ContractImportColumn column) {
        return row.cell(column);
    }

    String required(ContractImportColumn column) {
        Optional<String> value = row.cell(column);
        if (value.isEmpty()) {
            issues.add(ImportIssue.error("REQUIRED_FIELD", column.label() + " is required"));
        }
        return value.orElse(null);
    }

    LocalDate date(ContractImportColumn column) {
        return row.cell(column).map(value -> parseDate(column, value)).orElse(null);
    }

    LocalDate requiredDate(ContractImportColumn column) {
        required(column);
        return row.cell(column).isPresent() ? date(column) : null;
    }

    BigDecimal money(ContractImportColumn column) {
        return row.cell(column).map(value -> parseMoney(column, value)).orElse(null);
    }

    Short smallNumber(ContractImportColumn column) {
        return row.cell(column).map(value -> parseSmall(column, value)).orElse(null);
    }

    <E extends Enum<E>> E enumValue(Class<E> type, ContractImportColumn column) {
        Optional<String> value = row.cell(column);
        if (value.isEmpty()) {
            return null;
        }
        Optional<E> parsed = enums.parse(type, value.get());
        if (parsed.isEmpty()) {
            issues.add(ImportIssue.error("INVALID_VALUE", column.label() + " has an unknown value: " + value.get()));
        }
        return parsed.orElse(null);
    }

    <E extends Enum<E>> E requiredEnum(Class<E> type, ContractImportColumn column) {
        required(column);
        return row.cell(column).isPresent() ? enumValue(type, column) : null;
    }

    private LocalDate parseDate(ContractImportColumn column, String value) {
        try {
            return value.contains("/") ? LocalDate.parse(value, DAY_MONTH_YEAR) : LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            issues.add(ImportIssue.error("INVALID_VALUE", column.label() + " is not a valid date: " + value));
            return null;
        }
    }

    private BigDecimal parseMoney(ContractImportColumn column, String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            issues.add(ImportIssue.error("INVALID_VALUE", column.label() + " is not a valid amount: " + value));
            return null;
        }
        return new BigDecimal(digits);
    }

    private Short parseSmall(ContractImportColumn column, String value) {
        try {
            return (short) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException ex) {
            issues.add(ImportIssue.error("INVALID_VALUE", column.label() + " is not a valid number: " + value));
            return null;
        }
    }
}
