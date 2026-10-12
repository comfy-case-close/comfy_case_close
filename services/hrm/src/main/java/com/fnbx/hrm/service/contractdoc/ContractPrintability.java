package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.ContractKind;
import com.fnbx.hrm.enums.EmploymentType;
import java.util.Set;

public final class ContractPrintability {

    private static final Set<ContractKind> KINDS_WITHOUT_STANDARD_PAPER = Set.of(ContractKind.PART_TIME_AGREEMENT, ContractKind.SERVICE);

    private ContractPrintability() {
    }

    public static boolean isPrintable(EmploymentAssignment assignment) {
        ContractKind kind = assignment.getContractKind();
        return assignment.getEmploymentType() == EmploymentType.FULLTIME
                && (kind == null || !KINDS_WITHOUT_STANDARD_PAPER.contains(kind));
    }
}
