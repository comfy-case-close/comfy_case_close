package com.fnbx.hrm.service.contractimport;

import java.util.Map;
import java.util.Optional;

public record ImportSheetRow(int rowNo, Map<ContractImportColumn, String> cells) {

    public Optional<String> cell(ContractImportColumn column) {
        String value = cells.get(column);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
