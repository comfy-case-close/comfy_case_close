package com.fnbx.hrm.enums;

import java.util.Optional;

public enum ContractTemplateKind {
    LABOR_CONTRACT,
    PROBATION_AGREEMENT;

    public static Optional<ContractTemplateKind> of(ContractKind kind) {
        if (kind == null) {
            return Optional.empty();
        }
        return switch (kind) {
            case PROBATION -> Optional.of(PROBATION_AGREEMENT);
            case FIXED_TERM_FT, INDEFINITE_FT -> Optional.of(LABOR_CONTRACT);
            case PART_TIME_AGREEMENT, SERVICE -> Optional.empty();
        };
    }
}
